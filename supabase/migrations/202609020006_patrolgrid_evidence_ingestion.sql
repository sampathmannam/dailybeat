-- Bound route geometry before it can reach mission snapshots or mobile renderers.
create or replace function public.patrolgrid_route_position_is_valid(target_position jsonb)
returns boolean
language plpgsql
immutable
security invoker
set search_path = ''
as $$
declare
    longitude_value numeric;
    latitude_value numeric;
begin
    if target_position is null
       or jsonb_typeof(target_position) <> 'array'
       or jsonb_array_length(target_position) <> 2
       or jsonb_typeof(target_position -> 0) <> 'number'
       or jsonb_typeof(target_position -> 1) <> 'number' then
        return false;
    end if;

    longitude_value := (target_position ->> 0)::numeric;
    latitude_value := (target_position ->> 1)::numeric;
    return longitude_value between -180 and 180
       and latitude_value between -90 and 90;
exception
    when others then
        return false;
end;
$$;

create or replace function public.patrolgrid_route_geojson_is_valid(target_geometry jsonb)
returns boolean
language plpgsql
immutable
security invoker
set search_path = ''
as $$
declare
    geometry_type text;
    coordinates jsonb;
    line_value jsonb;
    polygon_value jsonb;
    ring_value jsonb;
    position_value jsonb;
    position_count integer := 0;
begin
    if target_geometry is null
       or jsonb_typeof(target_geometry) <> 'object'
       or octet_length(target_geometry::text) > 262144
       or not (target_geometry ?& array['type', 'coordinates'])
       or target_geometry - 'type' - 'coordinates' <> '{}'::jsonb
       or jsonb_typeof(target_geometry -> 'type') <> 'string'
       or jsonb_typeof(target_geometry -> 'coordinates') <> 'array' then
        return false;
    end if;

    geometry_type := target_geometry ->> 'type';
    coordinates := target_geometry -> 'coordinates';

    if geometry_type = 'LineString' then
        -- Empty LineString is the bounded sentinel for missions without a route
        -- template. Route templates separately require at least one coordinate.
        if jsonb_array_length(coordinates) = 1 then
            return false;
        end if;
        for position_value in select value from jsonb_array_elements(coordinates)
        loop
            position_count := position_count + 1;
            if position_count > 10000
               or not public.patrolgrid_route_position_is_valid(position_value) then
                return false;
            end if;
        end loop;
    elsif geometry_type = 'MultiLineString' then
        if jsonb_array_length(coordinates) < 1 then
            return false;
        end if;
        for line_value in select value from jsonb_array_elements(coordinates)
        loop
            if jsonb_typeof(line_value) <> 'array'
               or jsonb_array_length(line_value) < 2 then
                return false;
            end if;
            for position_value in select value from jsonb_array_elements(line_value)
            loop
                position_count := position_count + 1;
                if position_count > 10000
                   or not public.patrolgrid_route_position_is_valid(position_value) then
                    return false;
                end if;
            end loop;
        end loop;
    elsif geometry_type = 'Polygon' then
        if jsonb_array_length(coordinates) < 1 then
            return false;
        end if;
        for ring_value in select value from jsonb_array_elements(coordinates)
        loop
            if jsonb_typeof(ring_value) <> 'array'
               or jsonb_array_length(ring_value) < 4
               or ring_value -> 0 <> ring_value -> (jsonb_array_length(ring_value) - 1) then
                return false;
            end if;
            for position_value in select value from jsonb_array_elements(ring_value)
            loop
                position_count := position_count + 1;
                if position_count > 10000
                   or not public.patrolgrid_route_position_is_valid(position_value) then
                    return false;
                end if;
            end loop;
        end loop;
    elsif geometry_type = 'MultiPolygon' then
        if jsonb_array_length(coordinates) < 1 then
            return false;
        end if;
        for polygon_value in select value from jsonb_array_elements(coordinates)
        loop
            if jsonb_typeof(polygon_value) <> 'array'
               or jsonb_array_length(polygon_value) < 1 then
                return false;
            end if;
            for ring_value in select value from jsonb_array_elements(polygon_value)
            loop
                if jsonb_typeof(ring_value) <> 'array'
                   or jsonb_array_length(ring_value) < 4
                   or ring_value -> 0 <> ring_value -> (jsonb_array_length(ring_value) - 1) then
                    return false;
                end if;
                for position_value in select value from jsonb_array_elements(ring_value)
                loop
                    position_count := position_count + 1;
                    if position_count > 10000
                       or not public.patrolgrid_route_position_is_valid(position_value) then
                        return false;
                    end if;
                end loop;
            end loop;
        end loop;
    else
        return false;
    end if;

    return true;
exception
    when others then
        return false;
end;
$$;

alter table public.patrolgrid_route_templates
add constraint patrolgrid_route_templates_bounded_geometry_check
check (
    public.patrolgrid_route_geojson_is_valid(route_geojson)
    and jsonb_array_length(route_geojson -> 'coordinates') > 0
);

alter table public.patrolgrid_missions
add constraint patrolgrid_missions_bounded_geometry_check
check (public.patrolgrid_route_geojson_is_valid(route_geojson));

-- A client batch contains only point evidence. Mission and user ownership are
-- derived from the locked session row.
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

    select count(*) into existing_count
    from public.patrolgrid_track_points point
    where point.session_id = target_session;

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
                raise exception using errcode = '54000', message = 'Track session point limit exceeded';
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

create or replace function public.patrolgrid_record_priority_visit(
    target_session uuid,
    target_visit uuid,
    target_priority_location uuid,
    target_visited_at timestamptz,
    target_method text,
    target_latitude double precision default null,
    target_longitude double precision default null,
    target_accuracy real default null,
    target_note text default ''
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_id uuid := auth.uid();
    context_record record;
    existing_visit public.patrolgrid_priority_visits%rowtype;
    server_now timestamptz := clock_timestamp();
begin
    if caller_id is null then
        raise exception using errcode = '28000', message = 'Authentication is required';
    end if;
    if target_session is null
       or target_visit is null
       or target_priority_location is null
       or target_visited_at is null
       or target_method is null
       or target_method not in ('gps', 'manual_with_context')
       or target_note is null
       or char_length(target_note) > 2000
       or octet_length(target_note) > 8000
       or ((target_latitude is null) <> (target_longitude is null))
       or (target_latitude is not null and target_latitude not between -90 and 90)
       or (target_longitude is not null and target_longitude not between -180 and 180)
       or (target_accuracy is not null and target_accuracy not between 0 and 5000)
       or (target_accuracy is not null and target_latitude is null) then
        raise exception using errcode = '22023', message = 'Priority visit payload is invalid';
    end if;

    select session.mission_id,
           session.started_at,
           session.ended_at,
           mission.subdivision_id,
           mission.ends_at
    into context_record
    from public.patrolgrid_sessions session
    join public.patrolgrid_missions mission on mission.id = session.mission_id
    join public.patrolgrid_priority_locations location
      on location.id = target_priority_location
     and location.mission_id = mission.id
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
        raise exception using errcode = '42501', message = 'Priority visit is not authorized for this session';
    end if;

    select visit.* into existing_visit
    from public.patrolgrid_priority_visits visit
    where visit.id = target_visit;
    if found then
        if existing_visit.priority_location_id <> target_priority_location
           or existing_visit.mission_id <> context_record.mission_id
           or existing_visit.user_id <> caller_id
           or existing_visit.visited_at <> target_visited_at
           or existing_visit.method <> target_method
           or existing_visit.latitude is distinct from target_latitude
           or existing_visit.longitude is distinct from target_longitude
           or existing_visit.accuracy_m is distinct from target_accuracy
           or existing_visit.note <> target_note then
            raise exception using errcode = '22023', message = 'Visit idempotency key was reused with different evidence';
        end if;
        return existing_visit.id;
    end if;

    select visit.* into existing_visit
    from public.patrolgrid_priority_visits visit
    where visit.priority_location_id = target_priority_location
      and visit.user_id = caller_id;
    if found then
        return existing_visit.id;
    end if;

    if server_now > coalesce(context_record.ended_at, context_record.ends_at) + interval '24 hours'
       or target_visited_at < context_record.started_at - interval '5 minutes'
       or target_visited_at > (
           least(coalesce(context_record.ended_at, context_record.ends_at), context_record.ends_at)
           + interval '5 minutes'
       )
       or target_visited_at > server_now + interval '5 minutes' then
        raise exception using errcode = '22023', message = 'Priority visit is outside accepted evidence bounds';
    end if;

    insert into public.patrolgrid_priority_visits (
        id, priority_location_id, mission_id, user_id, visited_at,
        method, latitude, longitude, accuracy_m, note
    ) values (
        target_visit, target_priority_location, context_record.mission_id, caller_id,
        target_visited_at, target_method, target_latitude, target_longitude,
        target_accuracy, target_note
    );

    insert into public.patrolgrid_audit_events (
        subdivision_id, mission_id, actor_id, event_type, payload
    ) values (
        context_record.subdivision_id,
        context_record.mission_id,
        caller_id,
        'patrolgrid.priority_visit_ingested',
        jsonb_build_object(
            'session_id', target_session,
            'visit_id', target_visit,
            'priority_location_id', target_priority_location,
            'visited_at', target_visited_at,
            'method', target_method
        )
    );

    return target_visit;
end;
$$;

create or replace function public.patrolgrid_record_field_update(
    target_client_update uuid,
    target_category text,
    target_detail text,
    target_occurred_at timestamptz,
    target_session uuid default null,
    target_review uuid default null,
    target_latitude double precision default null,
    target_longitude double precision default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_id uuid := auth.uid();
    context_record record;
    existing_update public.patrolgrid_field_updates%rowtype;
    inserted_update_id uuid;
    server_now timestamptz := clock_timestamp();
begin
    if caller_id is null then
        raise exception using errcode = '28000', message = 'Authentication is required';
    end if;
    if target_client_update is null
       or target_category is null
       or target_category not in ('observation', 'operational_deviation', 'safety_event', 'review_context')
       or target_detail is null
       or char_length(target_detail) not between 1 and 4000
       or btrim(target_detail) = ''
       or octet_length(target_detail) > 16000
       or target_occurred_at is null
       or target_occurred_at > server_now + interval '5 minutes'
       or ((target_latitude is null) <> (target_longitude is null))
       or (target_latitude is not null and target_latitude not between -90 and 90)
       or (target_longitude is not null and target_longitude not between -180 and 180) then
        raise exception using errcode = '22023', message = 'Field update payload is invalid';
    end if;

    if target_category = 'review_context' then
        if target_session is not null or target_review is null then
            raise exception using errcode = '22023', message = 'Review context requires only the supervisor review link';
        end if;

        select mission.id as mission_id,
               mission.subdivision_id,
               null::timestamptz as started_at,
               null::timestamptz as ended_at,
               mission.ends_at,
               mission.status,
               review.reviewed_at,
               review.outcome
        into context_record
        from public.patrolgrid_reviews review
        join public.patrolgrid_missions mission on mission.id = review.mission_id
        join public.patrolgrid_memberships membership
          on membership.subdivision_id = mission.subdivision_id
         and membership.user_id = caller_id
         and membership.role = 'patrol'
         and membership.status = 'active'
        join public.patrolgrid_assignments assignment
          on assignment.mission_id = mission.id
         and assignment.user_id = caller_id
        where review.id = target_review;

        if not found then
            raise exception using errcode = '42501', message = 'Review context is not authorized';
        end if;
    else
        if target_session is null or target_review is not null then
            raise exception using errcode = '22023', message = 'Operational field evidence requires only a patrol session';
        end if;

        select session.mission_id,
               mission.subdivision_id,
               session.started_at,
               session.ended_at,
               mission.ends_at,
               mission.status,
               null::timestamptz as reviewed_at,
               null::text as outcome
        into context_record
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
            raise exception using errcode = '42501', message = 'Field update is not authorized for this session';
        end if;
    end if;

    select update_record.* into existing_update
    from public.patrolgrid_field_updates update_record
    where update_record.user_id = caller_id
      and update_record.client_update_id = target_client_update;
    if found then
        if existing_update.mission_id <> context_record.mission_id
           or existing_update.category <> target_category
           or existing_update.detail <> target_detail
           or existing_update.occurred_at <> target_occurred_at
           or existing_update.review_id is distinct from target_review
           or existing_update.latitude is distinct from target_latitude
           or existing_update.longitude is distinct from target_longitude then
            raise exception using errcode = '22023', message = 'Field-update idempotency key was reused with different evidence';
        end if;
        return existing_update.id;
    end if;

    if target_category = 'review_context' then
        if context_record.status <> 'needs_review'
           or context_record.outcome <> 'needs_context'
           or target_review <> (
               select review.id
               from public.patrolgrid_reviews review
               where review.mission_id = context_record.mission_id
               order by review.reviewed_at desc, review.created_at desc, review.id desc
               limit 1
           )
           or target_occurred_at < context_record.reviewed_at - interval '5 minutes'
           or target_occurred_at > context_record.reviewed_at + interval '30 days 5 minutes'
           or server_now > context_record.reviewed_at + interval '30 days 5 minutes' then
            raise exception using errcode = '22023', message = 'Mission no longer accepts this review context';
        end if;
    elsif server_now > coalesce(context_record.ended_at, context_record.ends_at) + interval '24 hours'
       or target_occurred_at < context_record.started_at - interval '5 minutes'
       or target_occurred_at > (
           least(coalesce(context_record.ended_at, context_record.ends_at), context_record.ends_at)
           + interval '5 minutes'
       ) then
        raise exception using errcode = '22023', message = 'Field update is outside accepted evidence bounds';
    end if;

    insert into public.patrolgrid_field_updates (
        client_update_id, mission_id, user_id, review_id, category,
        detail, occurred_at, latitude, longitude
    ) values (
        target_client_update, context_record.mission_id, caller_id, target_review,
        target_category, target_detail, target_occurred_at, target_latitude, target_longitude
    ) returning id into inserted_update_id;

    insert into public.patrolgrid_audit_events (
        subdivision_id, mission_id, actor_id, event_type, payload
    ) values (
        context_record.subdivision_id,
        context_record.mission_id,
        caller_id,
        'patrolgrid.field_update_ingested',
        jsonb_strip_nulls(jsonb_build_object(
            'record_id', inserted_update_id,
            'client_update_id', target_client_update,
            'session_id', target_session,
            'review_id', target_review,
            'category', target_category,
            'occurred_at', target_occurred_at
        ))
    );

    return inserted_update_id;
end;
$$;

revoke all on function public.patrolgrid_ingest_track_points(uuid, jsonb) from public;
revoke all on function public.patrolgrid_record_priority_visit(
    uuid, uuid, uuid, timestamptz, text, double precision, double precision, real, text
) from public;
revoke all on function public.patrolgrid_record_field_update(
    uuid, text, text, timestamptz, uuid, uuid, double precision, double precision
) from public;

grant execute on function public.patrolgrid_ingest_track_points(uuid, jsonb) to authenticated;
grant execute on function public.patrolgrid_record_priority_visit(
    uuid, uuid, uuid, timestamptz, text, double precision, double precision, real, text
) to authenticated;
grant execute on function public.patrolgrid_record_field_update(
    uuid, text, text, timestamptz, uuid, uuid, double precision, double precision
) to authenticated;

revoke insert on public.patrolgrid_track_points from authenticated;
revoke insert on public.patrolgrid_priority_visits from authenticated;
revoke insert on public.patrolgrid_field_updates from authenticated;
revoke usage, select on sequence public.patrolgrid_track_points_id_seq from authenticated;

drop policy if exists "Patrol appends track points"
on public.patrolgrid_track_points;
drop policy if exists "Patrol records priority visits"
on public.patrolgrid_priority_visits;
drop policy if exists "Patrol records field updates"
on public.patrolgrid_field_updates;
