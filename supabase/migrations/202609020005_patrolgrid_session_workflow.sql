-- Session lifecycle writes are intentionally exposed only through these narrow
-- server workflows. The client supplies an idempotency key, never a timestamp.
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

    -- An exact retry remains successful even if a later request has already
    -- closed the session. Reusing another user's UUID or changing its immutable
    -- inputs is rejected.
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

create or replace function public.patrolgrid_end_session(
    target_session uuid,
    target_reason text
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_id uuid := auth.uid();
    session_record record;
    server_ended_at timestamptz := clock_timestamp();
    effective_ended_at timestamptz;
    effective_reason text;
begin
    if caller_id is null then
        raise exception using
            errcode = '28000',
            message = 'Authentication is required';
    end if;

    if target_session is null then
        raise exception using
            errcode = '22023',
            message = 'Session is required';
    end if;

    if target_reason is null
       or target_reason not in (
           'completed',
           'relieved',
           'cancelled',
           'device_issue',
           'duty_window_ended'
       ) then
        raise exception using
            errcode = '22023',
            message = 'Unsupported patrol session end reason';
    end if;

    select session.id,
           session.started_at,
           session.ended_at,
           session.end_reason,
           mission.ends_at
    into session_record
    from public.patrolgrid_sessions session
    join public.patrolgrid_missions mission
      on mission.id = session.mission_id
    join public.patrolgrid_memberships membership
      on membership.subdivision_id = mission.subdivision_id
     and membership.user_id = caller_id
     and membership.role = 'patrol'
     and membership.status = 'active'
    where session.id = target_session
      and session.user_id = caller_id
    for update of session;

    if not found then
        raise exception using
            errcode = '42501',
            message = 'Session closure is not authorized';
    end if;

    if session_record.ended_at is not null then
        return session_record.ended_at;
    end if;

    if target_reason = 'duty_window_ended'
       and server_ended_at < session_record.ends_at then
        raise exception using
            errcode = '22023',
            message = 'Mission duty window has not ended';
    end if;

    if server_ended_at >= session_record.ends_at + interval '5 minutes' then
        effective_ended_at := greatest(
            session_record.started_at,
            session_record.ends_at + interval '5 minutes'
        );
        effective_reason := 'duty_window_ended';
    else
        effective_ended_at := greatest(session_record.started_at, server_ended_at);
        effective_reason := target_reason;
    end if;

    update public.patrolgrid_sessions
    set ended_at = effective_ended_at,
        end_reason = effective_reason
    where id = session_record.id
      and ended_at is null;

    return effective_ended_at;
end;
$$;

revoke all on function public.patrolgrid_start_session(uuid, uuid, uuid, text) from public;
revoke all on function public.patrolgrid_end_session(uuid, text) from public;
grant execute on function public.patrolgrid_start_session(uuid, uuid, uuid, text) to authenticated;
grant execute on function public.patrolgrid_end_session(uuid, text) to authenticated;

-- Session rows remain readable through RLS, but their lifecycle is no longer a
-- general PostgREST table-write surface.
revoke insert, update on public.patrolgrid_sessions from authenticated;

drop policy if exists "Assigned patrol starts sessions"
on public.patrolgrid_sessions;
drop policy if exists "Patrol closes own sessions"
on public.patrolgrid_sessions;
