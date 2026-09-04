-- One simultaneously open session is not a storage quota: a modified client
-- could repeatedly close/restart while a teammate keeps the mission active.
-- Bound both short bursts and the lifetime source count for one assignment.
create index patrolgrid_sessions_assignment_created
on public.patrolgrid_sessions(mission_id, user_id, created_at desc);

create index patrolgrid_track_points_assignment_count
on public.patrolgrid_track_points(mission_id, user_id);

create or replace function public.patrolgrid_start_session(
    target_session uuid,
    target_mission uuid,
    target_installation uuid,
    target_app_version text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_id uuid := auth.uid();
    mission_record record;
    existing_session public.patrolgrid_sessions%rowtype;
    server_started_at timestamptz := clock_timestamp();
    assignment_session_count integer;
    recent_session_count integer;
begin
    if caller_id is null then
        raise exception using
            errcode = '28000',
            message = 'Authentication is required';
    end if;

    if target_session is null
       or target_mission is null
       or target_installation is null then
        raise exception using
            errcode = '22023',
            message = 'Session, mission, and installation are required';
    end if;

    if target_app_version is null
       or char_length(btrim(target_app_version)) not between 1 and 40 then
        raise exception using
            errcode = '22023',
            message = 'App version must contain between 1 and 40 characters';
    end if;

    -- This mission lock already protects status/lifecycle transitions. It also
    -- serializes the quota count and insertion so two boundary requests cannot
    -- both pass. Session creation is rare, so the short mission-wide lock is
    -- preferable to an advisory-lock contract that maintenance could omit.
    select mission.id,
           mission.status,
           mission.starts_at,
           mission.ends_at
    into mission_record
    from public.patrolgrid_missions mission
    join public.patrolgrid_memberships membership
      on membership.subdivision_id = mission.subdivision_id
     and membership.user_id = caller_id
     and membership.role = 'patrol'
     and membership.status = 'active'
    join public.patrolgrid_assignments assignment
      on assignment.mission_id = mission.id
     and assignment.user_id = caller_id
    where mission.id = target_mission
    for update of mission;

    if not found then
        raise exception using
            errcode = '42501',
            message = 'Session start is not authorized for this mission';
    end if;

    -- Idempotent retries and recovery of the one open source are deliberately
    -- exempt from the quota: they create no new evidence container.
    select session.*
    into existing_session
    from public.patrolgrid_sessions session
    where session.id = target_session
    for update;

    if found then
        if existing_session.mission_id <> target_mission
           or existing_session.user_id <> caller_id
           or existing_session.installation_id <> target_installation
           or existing_session.app_version <> btrim(target_app_version) then
            raise exception using
                errcode = '22023',
                message = 'Session idempotency key was already used with different inputs';
        end if;
        return existing_session.id;
    end if;

    if mission_record.status not in ('assigned', 'active')
       or server_started_at < mission_record.starts_at - interval '15 minutes'
       or server_started_at > mission_record.ends_at then
        raise exception using
            errcode = '22023',
            message = 'Mission duty window is not open';
    end if;

    select session.*
    into existing_session
    from public.patrolgrid_sessions session
    where session.mission_id = target_mission
      and session.user_id = caller_id
      and session.ended_at is null
    for update;

    if found then
        if existing_session.installation_id <> target_installation then
            raise exception using
                errcode = '55000',
                message = 'An open session already exists on another installation';
        end if;
        return existing_session.id;
    end if;

    select count(*),
           count(*) filter (
               where session.created_at >= server_started_at - interval '15 minutes'
           )
    into assignment_session_count, recent_session_count
    from public.patrolgrid_sessions session
    where session.mission_id = target_mission
      and session.user_id = caller_id;

    if assignment_session_count >= 16 then
        raise exception using
            errcode = '54000',
            message = 'Patrol assignment session limit exceeded';
    end if;
    if recent_session_count >= 4 then
        raise exception using
            errcode = '54000',
            message = 'Patrol session restart rate limit exceeded';
    end if;

    insert into public.patrolgrid_sessions (
        id,
        mission_id,
        user_id,
        installation_id,
        started_at,
        app_version
    ) values (
        target_session,
        target_mission,
        caller_id,
        target_installation,
        server_started_at,
        btrim(target_app_version)
    );

    return target_session;
end;
$$;

-- Restate the complete intended ACL after replacing a SECURITY DEFINER function.
revoke all on function public.patrolgrid_start_session(uuid, uuid, uuid, text) from public;
revoke all on function public.patrolgrid_start_session(uuid, uuid, uuid, text) from anon;
revoke all on function public.patrolgrid_start_session(uuid, uuid, uuid, text) from authenticated;
revoke all on function public.patrolgrid_start_session(uuid, uuid, uuid, text) from service_role;
grant execute on function public.patrolgrid_start_session(uuid, uuid, uuid, text) to authenticated;

-- The point ceiling must follow the assignment/person, not the session. Otherwise
-- each permitted restart would reset the 20,000-row allowance. A transaction-level
-- advisory lock serializes old-session and current-session uploads for the same
-- mission/person without blocking another patrol person on the mission.
create or replace function public.patrolgrid_ingest_track_points(
    target_session uuid,
    target_points jsonb
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_id uuid := auth.uid();
    session_record record;
    point_value jsonb;
    point_client_id uuid;
    point_sequence integer;
    point_recorded_at timestamptz;
    point_latitude double precision;
    point_longitude double precision;
    point_accuracy real;
    existing_point public.patrolgrid_track_points%rowtype;
    submitted_count integer;
    inserted_count integer := 0;
    duplicate_count integer := 0;
    existing_count integer;
    minimum_recorded_at timestamptz;
    maximum_recorded_at timestamptz;
    server_now timestamptz := clock_timestamp();
begin
    if caller_id is null then
        raise exception using errcode = '28000', message = 'Authentication is required';
    end if;
    if target_session is null
       or target_points is null
       or jsonb_typeof(target_points) <> 'array'
       or octet_length(target_points::text) > 262144 then
        raise exception using errcode = '22023', message = 'Track batch payload is invalid or too large';
    end if;

    submitted_count := jsonb_array_length(target_points);
    if submitted_count not between 1 and 250 then
        raise exception using errcode = '22023', message = 'Track batch must contain between 1 and 250 points';
    end if;

    if exists (
        select 1
        from jsonb_array_elements(target_points) point
        group by point ->> 'client_point_id'
        having count(*) > 1
    ) or exists (
        select 1
        from jsonb_array_elements(target_points) point
        group by point ->> 'sequence_number'
        having count(*) > 1
    ) then
        raise exception using errcode = '22023', message = 'Track batch contains duplicate idempotency keys';
    end if;

    select session.id,
           session.mission_id,
           session.user_id,
           session.started_at,
           session.ended_at,
           mission.subdivision_id,
           mission.ends_at
    into session_record
    from public.patrolgrid_sessions session
    join public.patrolgrid_missions mission on mission.id = session.mission_id
    join public.patrolgrid_memberships membership
      on membership.subdivision_id = mission.subdivision_id
     and membership.user_id = caller_id
     and membership.role = 'patrol'
     and membership.status = 'active'
    join public.patrolgrid_assignments assignment
      on assignment.mission_id = mission.id
     and assignment.user_id = caller_id
    where session.id = target_session
      and session.user_id = caller_id
    for update of session;

    if not found then
        raise exception using errcode = '42501', message = 'Track ingestion is not authorized for this session';
    end if;

    if server_now > coalesce(session_record.ended_at, session_record.ends_at) + interval '24 hours' then
        raise exception using errcode = '22023', message = 'The sealed track upload window has ended';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            session_record.mission_id::text || ':' || caller_id::text,
            0
        )
    );

    select count(*) into existing_count
    from public.patrolgrid_track_points point
    where point.mission_id = session_record.mission_id
      and point.user_id = caller_id;

    for point_value in select value from jsonb_array_elements(target_points)
    loop
        if jsonb_typeof(point_value) <> 'object'
           or not (point_value ?& array[
               'client_point_id', 'sequence_number', 'recorded_at',
               'latitude', 'longitude', 'accuracy_m'
           ])
           or point_value
                - 'client_point_id' - 'sequence_number' - 'recorded_at'
                - 'latitude' - 'longitude' - 'accuracy_m' <> '{}'::jsonb
           or jsonb_typeof(point_value -> 'client_point_id') <> 'string'
           or jsonb_typeof(point_value -> 'sequence_number') <> 'number'
           or (point_value ->> 'sequence_number') !~ '^[0-9]+$'
           or jsonb_typeof(point_value -> 'recorded_at') <> 'string'
           or jsonb_typeof(point_value -> 'latitude') <> 'number'
           or jsonb_typeof(point_value -> 'longitude') <> 'number'
           or jsonb_typeof(point_value -> 'accuracy_m') <> 'number' then
            raise exception using errcode = '22023', message = 'Track point shape or type is invalid';
        end if;

        begin
            point_client_id := (point_value ->> 'client_point_id')::uuid;
            point_sequence := (point_value ->> 'sequence_number')::integer;
            point_recorded_at := (point_value ->> 'recorded_at')::timestamptz;
            point_latitude := (point_value ->> 'latitude')::double precision;
            point_longitude := (point_value ->> 'longitude')::double precision;
            point_accuracy := (point_value ->> 'accuracy_m')::real;
        exception
            when others then
                raise exception using errcode = '22023', message = 'Track point value is invalid';
        end;

        if point_sequence < 0
           or point_latitude not between -90 and 90
           or point_longitude not between -180 and 180
           or point_accuracy not between 0 and 5000
           or point_recorded_at < session_record.started_at - interval '5 minutes'
           or point_recorded_at > (
               least(coalesce(session_record.ended_at, session_record.ends_at), session_record.ends_at)
               + interval '5 minutes'
           )
           or point_recorded_at > server_now + interval '5 minutes' then
            raise exception using errcode = '22023', message = 'Track point is outside accepted evidence bounds';
        end if;

        select point.* into existing_point
        from public.patrolgrid_track_points point
        where point.user_id = caller_id
          and point.client_point_id = point_client_id;

        if found then
            if existing_point.session_id <> target_session
               or existing_point.mission_id <> session_record.mission_id
               or existing_point.sequence_number <> point_sequence
               or existing_point.recorded_at <> point_recorded_at
               or existing_point.latitude <> point_latitude
               or existing_point.longitude <> point_longitude
               or existing_point.accuracy_m <> point_accuracy then
                raise exception using errcode = '22023', message = 'Track idempotency key was reused with different evidence';
            end if;
            duplicate_count := duplicate_count + 1;
        else
            if exists (
                select 1 from public.patrolgrid_track_points point
                where point.session_id = target_session
                  and point.sequence_number = point_sequence
            ) then
                raise exception using errcode = '22023', message = 'Track sequence number was reused with different evidence';
            end if;
            if existing_count + inserted_count >= 20000 then
                raise exception using errcode = '54000', message = 'Track assignment point limit exceeded';
            end if;

            insert into public.patrolgrid_track_points (
                client_point_id, session_id, mission_id, user_id, sequence_number,
                recorded_at, latitude, longitude, accuracy_m
            ) values (
                point_client_id, target_session, session_record.mission_id, caller_id,
                point_sequence, point_recorded_at, point_latitude, point_longitude, point_accuracy
            );
            inserted_count := inserted_count + 1;
        end if;

        minimum_recorded_at := least(coalesce(minimum_recorded_at, point_recorded_at), point_recorded_at);
        maximum_recorded_at := greatest(coalesce(maximum_recorded_at, point_recorded_at), point_recorded_at);
    end loop;

    insert into public.patrolgrid_audit_events (
        subdivision_id, mission_id, actor_id, event_type, payload
    ) values (
        session_record.subdivision_id,
        session_record.mission_id,
        caller_id,
        'patrolgrid.track_batch_ingested',
        jsonb_build_object(
            'session_id', target_session,
            'submitted_count', submitted_count,
            'inserted_count', inserted_count,
            'duplicate_count', duplicate_count,
            'recorded_from', minimum_recorded_at,
            'recorded_to', maximum_recorded_at
        )
    );

    return inserted_count;
end;
$$;

revoke all on function public.patrolgrid_ingest_track_points(uuid, jsonb) from public;
revoke all on function public.patrolgrid_ingest_track_points(uuid, jsonb) from anon;
revoke all on function public.patrolgrid_ingest_track_points(uuid, jsonb) from authenticated;
revoke all on function public.patrolgrid_ingest_track_points(uuid, jsonb) from service_role;
grant execute on function public.patrolgrid_ingest_track_points(uuid, jsonb) to authenticated;
