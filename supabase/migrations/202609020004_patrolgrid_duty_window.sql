alter table public.patrolgrid_sessions
drop constraint patrolgrid_sessions_end_reason_check;

alter table public.patrolgrid_sessions
add constraint patrolgrid_sessions_end_reason_check
check (
    end_reason is null
    or end_reason in (
        'completed',
        'relieved',
        'cancelled',
        'device_issue',
        'duty_window_ended'
    )
);

drop policy if exists "Assigned patrol starts sessions"
on public.patrolgrid_sessions;

-- Personnel may acknowledge duty up to 15 minutes early. A new session can
-- never begin after the mission duty window has ended.
create policy "Assigned patrol starts sessions"
on public.patrolgrid_sessions for insert to authenticated
with check (
    user_id = auth.uid()
    and public.patrolgrid_is_assigned(mission_id)
    and started_at <= now() + interval '5 minutes'
    and exists (
        select 1
        from public.patrolgrid_missions mission
        where mission.id = patrolgrid_sessions.mission_id
          and mission.status in ('assigned', 'active')
          and now() >= mission.starts_at - interval '15 minutes'
          and now() <= mission.ends_at
          and patrolgrid_sessions.started_at >= mission.starts_at - interval '15 minutes'
          and patrolgrid_sessions.started_at <= mission.ends_at
    )
);

drop policy if exists "Patrol appends track points"
on public.patrolgrid_track_points;

create policy "Patrol appends track points"
on public.patrolgrid_track_points for insert to authenticated
with check (
    user_id = auth.uid()
    and public.patrolgrid_is_assigned(mission_id)
    and exists (
        select 1
        from public.patrolgrid_sessions session
        join public.patrolgrid_missions mission on mission.id = session.mission_id
        where session.id = patrolgrid_track_points.session_id
          and session.mission_id = patrolgrid_track_points.mission_id
          and session.user_id = auth.uid()
          and patrolgrid_track_points.recorded_at >= session.started_at - interval '5 minutes'
          and patrolgrid_track_points.recorded_at <= (
              least(coalesce(session.ended_at, mission.ends_at), mission.ends_at)
              + interval '5 minutes'
          )
          and now() <= (
              coalesce(session.ended_at, mission.ends_at) + interval '24 hours'
          )
    )
    and recorded_at <= now() + interval '5 minutes'
);

drop policy if exists "Patrol records priority visits"
on public.patrolgrid_priority_visits;

create policy "Patrol records priority visits"
on public.patrolgrid_priority_visits for insert to authenticated
with check (
    user_id = auth.uid()
    and public.patrolgrid_is_assigned(mission_id)
    and exists (
        select 1
        from public.patrolgrid_priority_locations location
        where location.id = patrolgrid_priority_visits.priority_location_id
          and location.mission_id = patrolgrid_priority_visits.mission_id
    )
    and exists (
        select 1
        from public.patrolgrid_sessions session
        join public.patrolgrid_missions mission on mission.id = session.mission_id
        where session.mission_id = patrolgrid_priority_visits.mission_id
          and session.user_id = auth.uid()
          and patrolgrid_priority_visits.visited_at >= session.started_at - interval '5 minutes'
          and patrolgrid_priority_visits.visited_at <= (
              least(coalesce(session.ended_at, mission.ends_at), mission.ends_at)
              + interval '5 minutes'
          )
          and now() <= (
              coalesce(session.ended_at, mission.ends_at) + interval '24 hours'
          )
    )
    and visited_at <= now() + interval '5 minutes'
);

drop policy if exists "Patrol records field updates"
on public.patrolgrid_field_updates;

create policy "Patrol records field updates"
on public.patrolgrid_field_updates for insert to authenticated
with check (
    user_id = auth.uid()
    and public.patrolgrid_is_assigned(mission_id)
    and occurred_at <= now() + interval '5 minutes'
    and (
        (
            category in ('observation', 'operational_deviation', 'safety_event')
            and exists (
                select 1
                from public.patrolgrid_sessions session
                join public.patrolgrid_missions mission on mission.id = session.mission_id
                where session.mission_id = patrolgrid_field_updates.mission_id
                  and session.user_id = auth.uid()
                  and patrolgrid_field_updates.occurred_at >= session.started_at - interval '5 minutes'
                  and patrolgrid_field_updates.occurred_at <= (
                      least(coalesce(session.ended_at, mission.ends_at), mission.ends_at)
                      + interval '5 minutes'
                  )
                  and now() <= (
                      coalesce(session.ended_at, mission.ends_at) + interval '24 hours'
                  )
            )
        )
        or (
            category = 'review_context'
            and exists (
                select 1
                from public.patrolgrid_missions mission
                where mission.id = patrolgrid_field_updates.mission_id
                  and mission.status = 'needs_review'
                  and exists (
                      select 1
                      from public.patrolgrid_reviews review
                      where review.id = patrolgrid_field_updates.review_id
                        and review.id = (
                          select latest_review.id
                          from public.patrolgrid_reviews latest_review
                          where latest_review.mission_id = mission.id
                          order by latest_review.reviewed_at desc,
                                   latest_review.created_at desc,
                                   latest_review.id desc
                          limit 1
                      )
                        and review.outcome = 'needs_context'
                        and patrolgrid_field_updates.occurred_at >= review.reviewed_at - interval '5 minutes'
                        and patrolgrid_field_updates.occurred_at <= review.reviewed_at + interval '30 days 5 minutes'
                        and now() <= review.reviewed_at + interval '30 days 5 minutes'
                  )
            )
        )
    )
);

create or replace function public.patrolgrid_close_expired_sessions()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_id uuid := auth.uid();
    expired_session record;
    effective_end timestamptz;
    closed_count integer := 0;
begin
    if caller_id is null then
        raise exception 'Authentication is required';
    end if;

    if not exists (
        select 1
        from public.patrolgrid_memberships membership
        where membership.user_id = caller_id
          and membership.status = 'active'
    ) then
        raise exception 'PatrolGrid active membership required';
    end if;

    for expired_session in
        select session.id,
               session.started_at,
               mission.id as mission_id,
               mission.subdivision_id,
               mission.ends_at + interval '5 minutes' as duty_cutoff
        from public.patrolgrid_sessions session
        join public.patrolgrid_missions mission on mission.id = session.mission_id
        join public.patrolgrid_memberships membership
          on membership.subdivision_id = mission.subdivision_id
         and membership.user_id = caller_id
         and membership.status = 'active'
        where session.ended_at is null
          and now() >= mission.ends_at + interval '5 minutes'
          and (
              (membership.role = 'patrol' and session.user_id = caller_id)
              or membership.role = 'supervisor'
          )
        order by session.started_at, session.id
        for update of session
    loop
        effective_end := greatest(expired_session.started_at, expired_session.duty_cutoff);

        update public.patrolgrid_sessions
        set ended_at = effective_end,
            end_reason = 'duty_window_ended'
        where id = expired_session.id
          and ended_at is null;

        if found then
            insert into public.patrolgrid_audit_events (
                subdivision_id,
                mission_id,
                actor_id,
                event_type,
                payload
            ) values (
                expired_session.subdivision_id,
                expired_session.mission_id,
                caller_id,
                'patrolgrid.expired_session_closed',
                jsonb_build_object(
                    'session_id', expired_session.id,
                    'duty_cutoff', expired_session.duty_cutoff,
                    'ended_at', effective_end,
                    'source', 'snapshot_refresh'
                )
            );
            closed_count := closed_count + 1;
        end if;
    end loop;

    return closed_count;
end;
$$;

revoke all on function public.patrolgrid_close_expired_sessions() from public;
grant execute on function public.patrolgrid_close_expired_sessions() to authenticated;

create extension if not exists pg_cron;

create or replace function public.patrolgrid_close_expired_sessions_scheduled()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    expired_session record;
    effective_end timestamptz;
    closed_count integer := 0;
begin
    for expired_session in
        select session.id,
               session.started_at,
               mission.id as mission_id,
               mission.subdivision_id,
               mission.ends_at + interval '5 minutes' as duty_cutoff
        from public.patrolgrid_sessions session
        join public.patrolgrid_missions mission on mission.id = session.mission_id
        where session.ended_at is null
          and now() >= mission.ends_at + interval '5 minutes'
        order by session.started_at, session.id
        for update of session
    loop
        effective_end := greatest(expired_session.started_at, expired_session.duty_cutoff);

        update public.patrolgrid_sessions
        set ended_at = effective_end,
            end_reason = 'duty_window_ended'
        where id = expired_session.id
          and ended_at is null;

        if found then
            insert into public.patrolgrid_audit_events (
                subdivision_id,
                mission_id,
                actor_id,
                event_type,
                payload
            ) values (
                expired_session.subdivision_id,
                expired_session.mission_id,
                null,
                'patrolgrid.expired_session_closed',
                jsonb_build_object(
                    'session_id', expired_session.id,
                    'duty_cutoff', expired_session.duty_cutoff,
                    'ended_at', effective_end,
                    'source', 'scheduler'
                )
            );
            closed_count := closed_count + 1;
        end if;
    end loop;

    return closed_count;
end;
$$;

revoke all on function public.patrolgrid_close_expired_sessions_scheduled() from public;
revoke all on function public.patrolgrid_close_expired_sessions_scheduled() from anon;
revoke all on function public.patrolgrid_close_expired_sessions_scheduled() from authenticated;
revoke all on function public.patrolgrid_close_expired_sessions_scheduled() from service_role;

select cron.schedule(
    'patrolgrid-close-expired-sessions',
    '*/5 * * * *',
    $cron$select public.patrolgrid_close_expired_sessions_scheduled();$cron$
);

-- The mobile client currently reads control-plane data and creates assignments
-- only through patrolgrid_create_assignment. Keep broad table writes unavailable
-- until an operation has its own validated, audited RPC.
revoke insert, update, delete on public.patrolgrid_route_templates from authenticated;
revoke insert, update, delete on public.patrolgrid_route_template_priorities from authenticated;
revoke insert, update, delete on public.patrolgrid_units from authenticated;
revoke insert, update, delete on public.patrolgrid_unit_members from authenticated;
revoke insert, update, delete on public.patrolgrid_missions from authenticated;
revoke insert, update, delete on public.patrolgrid_assignments from authenticated;
revoke insert, update, delete on public.patrolgrid_priority_locations from authenticated;

drop policy if exists "Supervisors create route templates"
on public.patrolgrid_route_templates;
drop policy if exists "Supervisors update route templates"
on public.patrolgrid_route_templates;
drop policy if exists "Supervisors delete route templates"
on public.patrolgrid_route_templates;
drop policy if exists "Supervisors manage route template priorities"
on public.patrolgrid_route_template_priorities;
drop policy if exists "Supervisors manage units"
on public.patrolgrid_units;
drop policy if exists "Supervisors manage unit members"
on public.patrolgrid_unit_members;
drop policy if exists "Supervisors create missions"
on public.patrolgrid_missions;
drop policy if exists "Supervisors update missions"
on public.patrolgrid_missions;
drop policy if exists "Supervisors delete planned missions"
on public.patrolgrid_missions;
drop policy if exists "Supervisors create assignments"
on public.patrolgrid_assignments;
drop policy if exists "Supervisors delete assignments"
on public.patrolgrid_assignments;
drop policy if exists "Supervisors manage priority locations"
on public.patrolgrid_priority_locations;
