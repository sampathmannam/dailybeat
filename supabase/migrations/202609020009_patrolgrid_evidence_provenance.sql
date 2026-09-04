-- Priority visits created before this migration carried mission/user provenance,
-- but not the exact patrol session that produced them. Resolve only an unambiguous
-- session whose accepted evidence window contains the visit. Any ambiguous or
-- orphaned historical evidence aborts the migration instead of being guessed.
alter table public.patrolgrid_priority_visits
add column session_id uuid;

do $$
begin
    if exists (
        select 1
        from public.patrolgrid_priority_visits visit
        where (
            select count(*)
            from public.patrolgrid_sessions session
            join public.patrolgrid_missions mission
              on mission.id = session.mission_id
            where session.mission_id = visit.mission_id
              and session.user_id = visit.user_id
              and visit.visited_at >= session.started_at - interval '5 minutes'
              and visit.visited_at <= (
                  least(
                      coalesce(session.ended_at, mission.ends_at),
                      mission.ends_at
                  ) + interval '5 minutes'
              )
        ) <> 1
    ) then
        raise exception using
            errcode = '23514',
            message = 'Priority-visit evidence cannot be mapped to exactly one patrol session';
    end if;
end;
$$;

update public.patrolgrid_priority_visits visit
set session_id = (
    select session.id
    from public.patrolgrid_sessions session
    join public.patrolgrid_missions mission
      on mission.id = session.mission_id
    where session.mission_id = visit.mission_id
      and session.user_id = visit.user_id
      and visit.visited_at >= session.started_at - interval '5 minutes'
      and visit.visited_at <= (
          least(coalesce(session.ended_at, mission.ends_at), mission.ends_at)
          + interval '5 minutes'
      )
);

alter table public.patrolgrid_sessions
add constraint patrolgrid_sessions_id_mission_user_key
unique (id, mission_id, user_id);

alter table public.patrolgrid_priority_locations
add constraint patrolgrid_priority_locations_id_mission_key
unique (id, mission_id);

-- A point must carry the same mission/user source as its referenced session.
-- The ingestion RPC already enforces this, but the FK also protects imports and
-- service-role maintenance from silently misattributing a route trail.
alter table public.patrolgrid_track_points
add constraint patrolgrid_track_points_session_source_fkey
    foreign key (session_id, mission_id, user_id)
    references public.patrolgrid_sessions (id, mission_id, user_id)
    on delete restrict;

alter table public.patrolgrid_priority_visits
alter column session_id set not null,
drop constraint patrolgrid_priority_visits_priority_location_id_user_id_key,
add constraint patrolgrid_priority_visits_session_source_fkey
    foreign key (session_id, mission_id, user_id)
    references public.patrolgrid_sessions (id, mission_id, user_id)
    on delete restrict,
add constraint patrolgrid_priority_visits_location_mission_fkey
    foreign key (priority_location_id, mission_id)
    references public.patrolgrid_priority_locations (id, mission_id)
    on delete restrict,
add constraint patrolgrid_priority_visits_session_location_key
    unique (session_id, priority_location_id);

comment on column public.patrolgrid_priority_visits.session_id is
'Exact patrol session that produced this priority-visit evidence.';

-- Keep the public RPC signature stable while making the session an immutable
-- part of both insertion and idempotency. A second source may independently
-- visit the same priority; changed evidence within one source is rejected.
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
    where visit.id = target_visit
       or (
           visit.session_id = target_session
           and visit.priority_location_id = target_priority_location
       )
    order by (visit.id = target_visit) desc
    limit 1;

    if found then
        if existing_visit.session_id <> target_session
           or existing_visit.priority_location_id <> target_priority_location
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
        id, session_id, priority_location_id, mission_id, user_id, visited_at,
        method, latitude, longitude, accuracy_m, note
    ) values (
        target_visit, target_session, target_priority_location,
        context_record.mission_id, caller_id, target_visited_at, target_method,
        target_latitude, target_longitude, target_accuracy, target_note
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

-- CREATE OR REPLACE preserves a function ACL. Restate the intended boundary so
-- an accidental historical grant cannot survive this security-definer update.
revoke all on function public.patrolgrid_record_priority_visit(
    uuid, uuid, uuid, timestamptz, text, double precision, double precision, real, text
) from public;
revoke all on function public.patrolgrid_record_priority_visit(
    uuid, uuid, uuid, timestamptz, text, double precision, double precision, real, text
) from anon;
revoke all on function public.patrolgrid_record_priority_visit(
    uuid, uuid, uuid, timestamptz, text, double precision, double precision, real, text
) from authenticated;
revoke all on function public.patrolgrid_record_priority_visit(
    uuid, uuid, uuid, timestamptz, text, double precision, double precision, real, text
) from service_role;
grant execute on function public.patrolgrid_record_priority_visit(
    uuid, uuid, uuid, timestamptz, text, double precision, double precision, real, text
) to authenticated;

-- Supervisors must review each patrol session as a distinct evidence source.
-- SECURITY INVOKER preserves the underlying session, membership, and track-point
-- RLS policies instead of allowing the view owner to bypass them.
create view public.patrolgrid_evidence_session_summaries
with (security_invoker = true)
as
select
    session.id as session_id,
    session.mission_id,
    session.user_id,
    membership.display_name,
    membership.badge_number,
    session.started_at,
    session.ended_at,
    session.end_reason,
    session.app_version,
    count(point.id)::integer as track_point_count,
    min(point.recorded_at) as first_recorded_at,
    max(point.recorded_at) as last_recorded_at,
    min(point.created_at) as first_received_at,
    max(point.created_at) as last_received_at,
    min(point.accuracy_m) as best_accuracy_m,
    max(point.accuracy_m) as worst_accuracy_m
from public.patrolgrid_sessions session
join public.patrolgrid_missions mission
  on mission.id = session.mission_id
join public.patrolgrid_memberships membership
  on membership.subdivision_id = mission.subdivision_id
 and membership.user_id = session.user_id
left join public.patrolgrid_track_points point
  on point.session_id = session.id
group by
    session.id,
    session.mission_id,
    session.user_id,
    membership.display_name,
    membership.badge_number,
    session.started_at,
    session.ended_at,
    session.end_reason,
    session.app_version;

comment on view public.patrolgrid_evidence_session_summaries is
'RLS-scoped per-session route provenance; sessions are never merged into one trail.';

revoke all on public.patrolgrid_evidence_session_summaries from public;
revoke all on public.patrolgrid_evidence_session_summaries from anon;
revoke all on public.patrolgrid_evidence_session_summaries from authenticated;
revoke all on public.patrolgrid_evidence_session_summaries from service_role;
grant select on public.patrolgrid_evidence_session_summaries to authenticated;
grant select on public.patrolgrid_evidence_session_summaries to service_role;
