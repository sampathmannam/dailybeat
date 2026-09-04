-- PatrolGrid retains mission evidence for exactly 365 x 24 hours after patrol
-- work first closes. needs_review is post-patrol closure even though supervisor
-- review remains open; moving among needs_review/completed/cancelled never
-- extends the clock. Terminal missions cannot be reopened; a new duty requires a
-- new assignment and mission clock.
alter table public.patrolgrid_missions
add column closed_at timestamptz;

-- Older rows did not record the terminal transition. Use the later of the
-- scheduled duty end and last recorded update as a conservative migration
-- anchor so existing evidence is never deleted early.
update public.patrolgrid_missions
set closed_at = greatest(ends_at, updated_at),
    retention_until = greatest(ends_at, updated_at) + interval '8760 hours'
where status in ('needs_review', 'completed', 'cancelled');

update public.patrolgrid_missions
set closed_at = null,
    retention_until = null
where status not in ('needs_review', 'completed', 'cancelled')
  and (closed_at is not null or retention_until is not null);

alter table public.patrolgrid_missions
add constraint patrolgrid_missions_retention_state_check
check (
    (
        status in ('needs_review', 'completed', 'cancelled')
        and closed_at is not null
        and retention_until = closed_at + interval '8760 hours'
    )
    or (
        status not in ('needs_review', 'completed', 'cancelled')
        and closed_at is null
        and retention_until is null
    )
);

create index patrolgrid_missions_retention_due
on public.patrolgrid_missions(retention_until, id)
where retention_until is not null;

-- These indexes serve both dependency checks and bounded purge deletes. Without
-- them, a growing evidence history would force full-table scans every five minutes.
create index patrolgrid_sessions_mission
on public.patrolgrid_sessions(mission_id);

create index patrolgrid_priority_visits_mission
on public.patrolgrid_priority_visits(mission_id);

create index patrolgrid_audit_events_mission
on public.patrolgrid_audit_events(mission_id)
where mission_id is not null;

create or replace function public.patrolgrid_set_mission_retention()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if tg_op = 'UPDATE'
       and old.status in ('needs_review', 'completed', 'cancelled')
       and new.status not in ('needs_review', 'completed', 'cancelled') then
        raise exception using
            errcode = '55000',
            message = 'Terminal patrol missions cannot be reopened; create a new assignment';
    end if;

    if new.status in ('needs_review', 'completed', 'cancelled') then
        if tg_op = 'INSERT'
           or old.status not in ('needs_review', 'completed', 'cancelled')
           or old.closed_at is null then
            new.closed_at := clock_timestamp();
        else
            new.closed_at := old.closed_at;
        end if;
        new.retention_until := new.closed_at + interval '8760 hours';
    else
        new.closed_at := null;
        new.retention_until := null;
    end if;

    return new;
end;
$$;

revoke all on function public.patrolgrid_set_mission_retention() from public;

create trigger patrolgrid_set_mission_retention
before insert or update of status, closed_at, retention_until
on public.patrolgrid_missions
for each row execute function public.patrolgrid_set_mission_retention();

-- Holds are deliberately server-managed. The mobile roles receive neither
-- table access nor function execution. Released hold history is retained with
-- the mission and is removed only when that mission's evidence is purged.
create table public.patrolgrid_retention_holds (
    id uuid primary key default gen_random_uuid(),
    mission_id uuid not null
        references public.patrolgrid_missions(id) on delete restrict,
    hold_reference text not null
        check (char_length(btrim(hold_reference)) between 2 and 160),
    reason text not null
        check (char_length(btrim(reason)) between 3 and 2000),
    authority text not null
        check (char_length(btrim(authority)) between 3 and 500),
    scope text not null
        check (char_length(btrim(scope)) between 3 and 1000),
    owner text not null
        check (char_length(btrim(owner)) between 2 and 160),
    initial_review_due_at timestamptz not null,
    review_due_at timestamptz not null,
    release_condition text not null
        check (char_length(btrim(release_condition)) between 3 and 1000),
    placed_by text not null
        check (char_length(btrim(placed_by)) between 2 and 160),
    placed_at timestamptz not null default clock_timestamp(),
    released_by text
        check (released_by is null or char_length(btrim(released_by)) between 2 and 160),
    release_reason text
        check (release_reason is null or char_length(btrim(release_reason)) between 3 and 2000),
    released_at timestamptz,
    check (
        (released_at is null and released_by is null and release_reason is null)
        or (released_at is not null and released_by is not null and release_reason is not null)
    ),
    check (
        pg_catalog.isfinite(initial_review_due_at)
        and initial_review_due_at > placed_at
        and initial_review_due_at <= placed_at + interval '30 days'
    ),
    check (pg_catalog.isfinite(review_due_at) and review_due_at > placed_at),
    check (released_at is null or released_at >= placed_at)
);

create index patrolgrid_retention_holds_mission
on public.patrolgrid_retention_holds(mission_id);

-- A reference is an idempotency key for its full lifetime, including after release.
-- A later independent hold must use a new documented reference.
create unique index patrolgrid_unique_hold_reference
on public.patrolgrid_retention_holds(mission_id, hold_reference);

create index patrolgrid_retention_holds_active_mission
on public.patrolgrid_retention_holds(mission_id)
where released_at is null;

create index patrolgrid_retention_holds_active_review_due
on public.patrolgrid_retention_holds(review_due_at)
where released_at is null;

-- Reviews extend only the administrative review deadline; they never release
-- the hold or alter the mission-retention clock. The append-only history makes
-- every reschedule attributable and idempotent.
create table public.patrolgrid_retention_hold_reviews (
    id uuid primary key,
    hold_id uuid not null
        references public.patrolgrid_retention_holds(id) on delete restrict,
    previous_review_due_at timestamptz not null,
    next_review_due_at timestamptz not null,
    review_reason text not null
        check (char_length(btrim(review_reason)) between 3 and 2000),
    reviewed_by text not null
        check (char_length(btrim(reviewed_by)) between 2 and 160),
    reviewed_at timestamptz not null,
    check (
        pg_catalog.isfinite(next_review_due_at)
        and next_review_due_at > reviewed_at
        and next_review_due_at <= reviewed_at + interval '30 days'
    )
);

create index patrolgrid_retention_hold_reviews_hold
on public.patrolgrid_retention_hold_reviews(hold_id);

-- This ledger intentionally contains aggregate counts only. It proves what a
-- purge did without becoming a second store of mission IDs, user IDs, notes,
-- installation IDs, coordinates, or other evidence.
create table public.patrolgrid_retention_runs (
    id bigint generated always as identity primary key,
    source text not null check (source in ('manual', 'scheduler')),
    as_of timestamptz not null,
    started_at timestamptz not null,
    completed_at timestamptz not null,
    batch_limit integer not null check (batch_limit between 1 and 100),
    eligible_missions integer not null check (eligible_missions >= 0),
    held_missions_skipped integer not null check (held_missions_skipped >= 0),
    open_sessions_skipped integer not null check (open_sessions_skipped >= 0),
    track_points_deleted bigint not null check (track_points_deleted >= 0),
    priority_visits_deleted bigint not null check (priority_visits_deleted >= 0),
    field_updates_deleted bigint not null check (field_updates_deleted >= 0),
    reviews_deleted bigint not null check (reviews_deleted >= 0),
    sessions_deleted bigint not null check (sessions_deleted >= 0),
    assignments_deleted bigint not null check (assignments_deleted >= 0),
    priority_locations_deleted bigint not null check (priority_locations_deleted >= 0),
    audit_events_deleted bigint not null check (audit_events_deleted >= 0),
    hold_reviews_deleted bigint not null check (hold_reviews_deleted >= 0),
    released_holds_deleted bigint not null check (released_holds_deleted >= 0),
    missions_deleted integer not null check (missions_deleted >= 0),
    remaining_deletable_backlog integer not null
        check (remaining_deletable_backlog >= 0),
    oldest_deletable_backlog_age_seconds bigint
        check (
            oldest_deletable_backlog_age_seconds is null
            or oldest_deletable_backlog_age_seconds >= 0
        ),
    overdue_hold_reviews integer not null check (overdue_hold_reviews >= 0),
    check (completed_at >= started_at),
    check (missions_deleted = eligible_missions),
    check (
        (
            remaining_deletable_backlog = 0
            and oldest_deletable_backlog_age_seconds is null
        )
        or (
            remaining_deletable_backlog > 0
            and oldest_deletable_backlog_age_seconds is not null
        )
    )
);

comment on column public.patrolgrid_retention_runs.remaining_deletable_backlog is
'Overdue missions still deletable after this batch; active holds and open-session anomalies are excluded and counted separately.';
comment on column public.patrolgrid_retention_runs.oldest_deletable_backlog_age_seconds is
'Age beyond deadline of the oldest remaining deletable mission; contains no mission or staff identifier.';

alter table public.patrolgrid_retention_holds enable row level security;
alter table public.patrolgrid_retention_hold_reviews enable row level security;
alter table public.patrolgrid_retention_runs enable row level security;

revoke all on public.patrolgrid_retention_holds from public;
revoke all on public.patrolgrid_retention_holds from anon;
revoke all on public.patrolgrid_retention_holds from authenticated;
revoke all on public.patrolgrid_retention_holds from service_role;
revoke all on public.patrolgrid_retention_hold_reviews from public;
revoke all on public.patrolgrid_retention_hold_reviews from anon;
revoke all on public.patrolgrid_retention_hold_reviews from authenticated;
revoke all on public.patrolgrid_retention_hold_reviews from service_role;
revoke all on public.patrolgrid_retention_runs from public;
revoke all on public.patrolgrid_retention_runs from anon;
revoke all on public.patrolgrid_retention_runs from authenticated;
revoke all on public.patrolgrid_retention_runs from service_role;
revoke all on sequence public.patrolgrid_retention_runs_id_seq from public;
revoke all on sequence public.patrolgrid_retention_runs_id_seq from anon;
revoke all on sequence public.patrolgrid_retention_runs_id_seq from authenticated;
revoke all on sequence public.patrolgrid_retention_runs_id_seq from service_role;

grant select on public.patrolgrid_retention_holds to service_role;
grant select on public.patrolgrid_retention_hold_reviews to service_role;
grant select on public.patrolgrid_retention_runs to service_role;

create or replace function public.patrolgrid_place_retention_hold(
    target_mission uuid,
    target_hold_reference text,
    target_reason text,
    target_authority text,
    target_scope text,
    target_owner text,
    target_review_due_at timestamptz,
    target_release_condition text,
    target_placed_by text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    existing_hold public.patrolgrid_retention_holds%rowtype;
    new_hold_id uuid;
    placed_at_value timestamptz;
begin
    if target_mission is null
       or target_hold_reference is null
       or char_length(btrim(target_hold_reference)) not between 2 and 160
       or target_reason is null
       or char_length(btrim(target_reason)) not between 3 and 2000
       or target_authority is null
       or char_length(btrim(target_authority)) not between 3 and 500
       or target_scope is null
       or char_length(btrim(target_scope)) not between 3 and 1000
       or target_owner is null
       or char_length(btrim(target_owner)) not between 2 and 160
       or target_review_due_at is null
       or target_release_condition is null
       or char_length(btrim(target_release_condition)) not between 3 and 1000
       or target_placed_by is null
       or char_length(btrim(target_placed_by)) not between 2 and 160 then
        raise exception using
            errcode = '22023',
            message = 'Mission and complete legal-hold authority, scope, owner, review, release, and operator details are required';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('patrolgrid-retention', 0)
    );

    perform 1
    from public.patrolgrid_missions mission
    where mission.id = target_mission
    for update;

    if not found then
        raise exception using
            errcode = 'P0002',
            message = 'Mission is unavailable';
    end if;

    select hold.*
    into existing_hold
    from public.patrolgrid_retention_holds hold
    where hold.mission_id = target_mission
      and hold.hold_reference = btrim(target_hold_reference)
    for update;

    if found then
        if existing_hold.reason <> btrim(target_reason)
           or existing_hold.authority <> btrim(target_authority)
           or existing_hold.scope <> btrim(target_scope)
           or existing_hold.owner <> btrim(target_owner)
           or existing_hold.initial_review_due_at <> target_review_due_at
           or existing_hold.release_condition <> btrim(target_release_condition)
           or existing_hold.placed_by <> btrim(target_placed_by) then
            raise exception using
                errcode = '22023',
                message = 'Hold reference was reused with different details';
        end if;
        return existing_hold.id;
    end if;

    -- An exact retry remains idempotent even after its review date. Only a new
    -- hold must start with a future administrative review deadline.
    placed_at_value := clock_timestamp();
    if not pg_catalog.isfinite(target_review_due_at)
       or target_review_due_at <= placed_at_value
       or target_review_due_at > placed_at_value + interval '30 days' then
        raise exception using
            errcode = '22023',
            message = 'Mission and complete legal-hold authority, scope, owner, review, release, and operator details are required';
    end if;

    insert into public.patrolgrid_retention_holds (
        mission_id,
        hold_reference,
        reason,
        authority,
        scope,
        owner,
        initial_review_due_at,
        review_due_at,
        release_condition,
        placed_by,
        placed_at
    ) values (
        target_mission,
        btrim(target_hold_reference),
        btrim(target_reason),
        btrim(target_authority),
        btrim(target_scope),
        btrim(target_owner),
        target_review_due_at,
        target_review_due_at,
        btrim(target_release_condition),
        btrim(target_placed_by),
        placed_at_value
    )
    returning id into new_hold_id;

    return new_hold_id;
end;
$$;

create or replace function public.patrolgrid_review_retention_hold(
    target_review uuid,
    target_hold uuid,
    target_next_review_due_at timestamptz,
    target_review_reason text,
    target_reviewed_by text
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    hold_record public.patrolgrid_retention_holds%rowtype;
    existing_review public.patrolgrid_retention_hold_reviews%rowtype;
    reviewed_at_value timestamptz;
begin
    if target_review is null
       or target_hold is null
       or target_next_review_due_at is null
       or target_review_reason is null
       or char_length(btrim(target_review_reason)) not between 3 and 2000
       or target_reviewed_by is null
       or char_length(btrim(target_reviewed_by)) not between 2 and 160 then
        raise exception using
            errcode = '22023',
            message = 'Review id, active hold, next review date, reason, and reviewer are required';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('patrolgrid-retention', 0)
    );

    select review.*
    into existing_review
    from public.patrolgrid_retention_hold_reviews review
    where review.id = target_review
    for update;

    if found then
        if existing_review.hold_id <> target_hold
           or existing_review.next_review_due_at <> target_next_review_due_at
           or existing_review.review_reason <> btrim(target_review_reason)
           or existing_review.reviewed_by <> btrim(target_reviewed_by) then
            raise exception using
                errcode = '22023',
                message = 'Hold review idempotency key was reused with different details';
        end if;
        return existing_review.reviewed_at;
    end if;

    select hold.*
    into hold_record
    from public.patrolgrid_retention_holds hold
    where hold.id = target_hold
    for update;

    if not found then
        raise exception using
            errcode = 'P0002',
            message = 'Retention hold is unavailable';
    end if;

    if hold_record.released_at is not null then
        raise exception using
            errcode = '55000',
            message = 'Released retention holds cannot be reviewed';
    end if;

    reviewed_at_value := clock_timestamp();
    if not pg_catalog.isfinite(target_next_review_due_at)
       or target_next_review_due_at <= reviewed_at_value
       or target_next_review_due_at > reviewed_at_value + interval '30 days' then
        raise exception using
            errcode = '22023',
            message = 'Next retention-hold review date must be finite and within 30 days';
    end if;

    insert into public.patrolgrid_retention_hold_reviews (
        id,
        hold_id,
        previous_review_due_at,
        next_review_due_at,
        review_reason,
        reviewed_by,
        reviewed_at
    ) values (
        target_review,
        target_hold,
        hold_record.review_due_at,
        target_next_review_due_at,
        btrim(target_review_reason),
        btrim(target_reviewed_by),
        reviewed_at_value
    );

    update public.patrolgrid_retention_holds
    set review_due_at = target_next_review_due_at
    where id = target_hold
      and released_at is null;

    if not found then
        raise exception using
            errcode = '40001',
            message = 'Retention hold changed during review';
    end if;

    return reviewed_at_value;
end;
$$;

create or replace function public.patrolgrid_release_retention_hold(
    target_hold uuid,
    target_release_reason text,
    target_released_by text
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    hold_record public.patrolgrid_retention_holds%rowtype;
    effective_released_at timestamptz := clock_timestamp();
begin
    if target_hold is null
       or target_release_reason is null
       or char_length(btrim(target_release_reason)) not between 3 and 2000
       or target_released_by is null
       or char_length(btrim(target_released_by)) not between 2 and 160 then
        raise exception using
            errcode = '22023',
            message = 'Hold, release reason, and operator are required';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('patrolgrid-retention', 0)
    );

    select hold.*
    into hold_record
    from public.patrolgrid_retention_holds hold
    where hold.id = target_hold
    for update;

    if not found then
        raise exception using
            errcode = 'P0002',
            message = 'Retention hold is unavailable';
    end if;

    if hold_record.released_at is not null then
        if hold_record.release_reason <> btrim(target_release_reason)
           or hold_record.released_by <> btrim(target_released_by) then
            raise exception using
                errcode = '22023',
                message = 'Released hold retry conflicts with recorded release details';
        end if;
        return hold_record.released_at;
    end if;

    update public.patrolgrid_retention_holds
    set released_by = btrim(target_released_by),
        release_reason = btrim(target_release_reason),
        released_at = effective_released_at
    where id = target_hold;

    return effective_released_at;
end;
$$;

revoke all on function public.patrolgrid_place_retention_hold(uuid, text, text, text, text, text, timestamptz, text, text)
from public;
revoke all on function public.patrolgrid_place_retention_hold(uuid, text, text, text, text, text, timestamptz, text, text)
from anon;
revoke all on function public.patrolgrid_place_retention_hold(uuid, text, text, text, text, text, timestamptz, text, text)
from authenticated;
revoke all on function public.patrolgrid_place_retention_hold(uuid, text, text, text, text, text, timestamptz, text, text)
from service_role;
revoke all on function public.patrolgrid_review_retention_hold(uuid, uuid, timestamptz, text, text)
from public;
revoke all on function public.patrolgrid_review_retention_hold(uuid, uuid, timestamptz, text, text)
from anon;
revoke all on function public.patrolgrid_review_retention_hold(uuid, uuid, timestamptz, text, text)
from authenticated;
revoke all on function public.patrolgrid_review_retention_hold(uuid, uuid, timestamptz, text, text)
from service_role;
revoke all on function public.patrolgrid_release_retention_hold(uuid, text, text)
from public;
revoke all on function public.patrolgrid_release_retention_hold(uuid, text, text)
from anon;
revoke all on function public.patrolgrid_release_retention_hold(uuid, text, text)
from authenticated;
revoke all on function public.patrolgrid_release_retention_hold(uuid, text, text)
from service_role;

grant execute on function public.patrolgrid_place_retention_hold(uuid, text, text, text, text, text, timestamptz, text, text)
to service_role;
grant execute on function public.patrolgrid_review_retention_hold(uuid, uuid, timestamptz, text, text)
to service_role;
grant execute on function public.patrolgrid_release_retention_hold(uuid, text, text)
to service_role;

-- This worker is owner-only. Public wrappers supply the real current time and a
-- fixed source, while database-owner tests can inject a future timestamp.
create or replace function public.patrolgrid_run_retention_purge(
    target_as_of timestamptz,
    target_source text,
    target_batch_limit integer
)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    started_at_value timestamptz := clock_timestamp();
    target_missions uuid[] := '{}'::uuid[];
    target_mission_keys text[] := '{}'::text[];
    run_id bigint;
    eligible_count integer := 0;
    held_count integer := 0;
    open_count integer := 0;
    track_count bigint := 0;
    visit_count bigint := 0;
    update_count bigint := 0;
    review_count bigint := 0;
    session_count bigint := 0;
    assignment_count bigint := 0;
    priority_count bigint := 0;
    audit_count bigint := 0;
    generated_audit_count bigint := 0;
    hold_review_count bigint := 0;
    released_hold_count bigint := 0;
    mission_count integer := 0;
    remaining_backlog_count integer := 0;
    oldest_backlog_seconds bigint;
    overdue_hold_review_count integer := 0;
begin
    if target_as_of is null
       or not pg_catalog.isfinite(target_as_of)
       or target_source not in ('manual', 'scheduler')
       or target_batch_limit is null
       or target_batch_limit not between 1 and 100 then
        raise exception using
            errcode = '22023',
            message = 'A valid purge time, source, and batch limit are required';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('patrolgrid-retention', 0)
    );

    select count(distinct mission.id)::integer
    into held_count
    from public.patrolgrid_missions mission
    where mission.status in ('needs_review', 'completed', 'cancelled')
      and mission.retention_until <= target_as_of
      and exists (
          select 1
          from public.patrolgrid_retention_holds hold
          where hold.mission_id = mission.id
            and hold.released_at is null
      );

    select count(*)::integer
    into open_count
    from public.patrolgrid_missions mission
    where mission.status in ('needs_review', 'completed', 'cancelled')
      and mission.retention_until <= target_as_of
      and not exists (
          select 1
          from public.patrolgrid_retention_holds hold
          where hold.mission_id = mission.id
            and hold.released_at is null
      )
      and exists (
          select 1
          from public.patrolgrid_sessions session
          where session.mission_id = mission.id
            and session.ended_at is null
      );

    select coalesce(array_agg(candidate.id order by candidate.id), '{}'::uuid[])
    into target_missions
    from (
        select mission.id
        from public.patrolgrid_missions mission
        where mission.status in ('needs_review', 'completed', 'cancelled')
          and mission.retention_until <= target_as_of
          and not exists (
              select 1
              from public.patrolgrid_retention_holds hold
              where hold.mission_id = mission.id
                and hold.released_at is null
          )
          and not exists (
              select 1
              from public.patrolgrid_sessions session
              where session.mission_id = mission.id
                and session.ended_at is null
          )
        order by mission.retention_until, mission.id
        limit target_batch_limit
        for update of mission
    ) candidate;

    eligible_count := coalesce(pg_catalog.array_length(target_missions, 1), 0);
    select coalesce(array_agg(mission_id::text), '{}'::text[])
    into target_mission_keys
    from unnest(target_missions) mission_id;

    delete from public.patrolgrid_track_points
    where mission_id = any(target_missions);
    get diagnostics track_count = row_count;

    delete from public.patrolgrid_priority_visits
    where mission_id = any(target_missions);
    get diagnostics visit_count = row_count;

    delete from public.patrolgrid_field_updates
    where mission_id = any(target_missions);
    get diagnostics update_count = row_count;

    delete from public.patrolgrid_reviews
    where mission_id = any(target_missions);
    get diagnostics review_count = row_count;

    delete from public.patrolgrid_sessions
    where mission_id = any(target_missions);
    get diagnostics session_count = row_count;

    -- Assignment and priority deletion triggers create ordinary audit rows.
    -- Delete these parents before clearing all mission-linked audit data.
    delete from public.patrolgrid_assignments
    where mission_id = any(target_missions);
    get diagnostics assignment_count = row_count;

    delete from public.patrolgrid_priority_locations
    where mission_id = any(target_missions);
    get diagnostics priority_count = row_count;

    delete from public.patrolgrid_audit_events
    where mission_id = any(target_missions);
    get diagnostics audit_count = row_count;

    delete from public.patrolgrid_retention_hold_reviews review
    using public.patrolgrid_retention_holds hold
    where review.hold_id = hold.id
      and hold.mission_id = any(target_missions)
      and hold.released_at is not null;
    get diagnostics hold_review_count = row_count;

    delete from public.patrolgrid_retention_holds
    where mission_id = any(target_missions)
      and released_at is not null;
    get diagnostics released_hold_count = row_count;

    delete from public.patrolgrid_missions
    where id = any(target_missions);
    get diagnostics mission_count = row_count;

    -- The normal mission-delete audit trigger has no mission FK by design. Its
    -- record key would nevertheless retain a per-mission identifier, so replace
    -- those fresh rows with the aggregate, non-identifying run ledger below.
    delete from public.patrolgrid_audit_events audit
    where audit.event_type = 'patrolgrid_missions.delete'
      and audit.payload ->> 'record_id' = any(target_mission_keys);
    get diagnostics generated_audit_count = row_count;
    audit_count := audit_count + generated_audit_count;

    select count(*)::integer,
           case
               when count(*) = 0 then null
               else greatest(
                   0,
                   floor(
                       extract(epoch from target_as_of - min(mission.retention_until))
                   )::bigint
               )
           end
    into remaining_backlog_count, oldest_backlog_seconds
    from public.patrolgrid_missions mission
    where mission.status in ('needs_review', 'completed', 'cancelled')
      and mission.retention_until <= target_as_of
      and not exists (
          select 1
          from public.patrolgrid_retention_holds hold
          where hold.mission_id = mission.id
            and hold.released_at is null
      )
      and not exists (
          select 1
          from public.patrolgrid_sessions session
          where session.mission_id = mission.id
            and session.ended_at is null
      );

    select count(*)::integer
    into overdue_hold_review_count
    from public.patrolgrid_retention_holds hold
    where hold.released_at is null
      and hold.review_due_at <= target_as_of;

    if mission_count <> eligible_count then
        raise exception using
            errcode = '40001',
            message = 'Retention purge candidate count changed during deletion';
    end if;

    insert into public.patrolgrid_retention_runs (
        source,
        as_of,
        started_at,
        completed_at,
        batch_limit,
        eligible_missions,
        held_missions_skipped,
        open_sessions_skipped,
        track_points_deleted,
        priority_visits_deleted,
        field_updates_deleted,
        reviews_deleted,
        sessions_deleted,
        assignments_deleted,
        priority_locations_deleted,
        audit_events_deleted,
        hold_reviews_deleted,
        released_holds_deleted,
        missions_deleted,
        remaining_deletable_backlog,
        oldest_deletable_backlog_age_seconds,
        overdue_hold_reviews
    ) values (
        target_source,
        target_as_of,
        started_at_value,
        clock_timestamp(),
        target_batch_limit,
        eligible_count,
        held_count,
        open_count,
        track_count,
        visit_count,
        update_count,
        review_count,
        session_count,
        assignment_count,
        priority_count,
        audit_count,
        hold_review_count,
        released_hold_count,
        mission_count,
        remaining_backlog_count,
        oldest_backlog_seconds,
        overdue_hold_review_count
    )
    returning id into run_id;

    return run_id;
end;
$$;

create or replace function public.patrolgrid_purge_expired_evidence()
returns bigint
language sql
security definer
set search_path = ''
as $$
    select public.patrolgrid_run_retention_purge(
        clock_timestamp(),
        'manual',
        25
    );
$$;

create or replace function public.patrolgrid_purge_expired_evidence_scheduled()
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    last_run_id bigint;
    remaining_count integer;
begin
    -- Drain a bounded 2,000-mission envelope per five-minute invocation. Each
    -- 100-mission batch has its own aggregate ledger row; holds and open-session
    -- anomalies are excluded from the deletable backlog.
    for batch_number in 1..20 loop
        last_run_id := public.patrolgrid_run_retention_purge(
            clock_timestamp(),
            'scheduler',
            100
        );
        select run.remaining_deletable_backlog
        into remaining_count
        from public.patrolgrid_retention_runs run
        where run.id = last_run_id;
        exit when remaining_count = 0;
    end loop;
    return last_run_id;
end;
$$;

-- Assigned duty windows with no session would otherwise remain non-terminal forever and
-- never acquire a retention clock. A short grace accommodates clock/network jitter; the
-- scheduler then cancels only missions that have never created any session.
create or replace function public.patrolgrid_cancel_expired_unstarted_missions_scheduled()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    cancelled_count integer;
begin
    update public.patrolgrid_missions mission
    set status = 'cancelled'
    where mission.status = 'assigned'
      and mission.ends_at + interval '5 minutes' <= clock_timestamp()
      and not exists (
          select 1
          from public.patrolgrid_sessions session
          where session.mission_id = mission.id
      );
    get diagnostics cancelled_count = row_count;
    return cancelled_count;
end;
$$;

revoke all on function public.patrolgrid_run_retention_purge(timestamptz, text, integer)
from public;
revoke all on function public.patrolgrid_run_retention_purge(timestamptz, text, integer)
from anon;
revoke all on function public.patrolgrid_run_retention_purge(timestamptz, text, integer)
from authenticated;
revoke all on function public.patrolgrid_run_retention_purge(timestamptz, text, integer)
from service_role;
revoke all on function public.patrolgrid_purge_expired_evidence() from public;
revoke all on function public.patrolgrid_purge_expired_evidence() from anon;
revoke all on function public.patrolgrid_purge_expired_evidence() from authenticated;
revoke all on function public.patrolgrid_purge_expired_evidence() from service_role;
revoke all on function public.patrolgrid_purge_expired_evidence_scheduled() from public;
revoke all on function public.patrolgrid_purge_expired_evidence_scheduled() from anon;
revoke all on function public.patrolgrid_purge_expired_evidence_scheduled() from authenticated;
revoke all on function public.patrolgrid_purge_expired_evidence_scheduled() from service_role;
revoke all on function public.patrolgrid_cancel_expired_unstarted_missions_scheduled() from public;
revoke all on function public.patrolgrid_cancel_expired_unstarted_missions_scheduled() from anon;
revoke all on function public.patrolgrid_cancel_expired_unstarted_missions_scheduled() from authenticated;
revoke all on function public.patrolgrid_cancel_expired_unstarted_missions_scheduled() from service_role;

grant execute on function public.patrolgrid_purge_expired_evidence()
to service_role;

-- Named jobs are upserted by pg_cron, so reapplying the desired definitions does
-- not create duplicates.
select cron.schedule(
    'patrolgrid-retention-purge',
    '*/5 * * * *',
    $cron$select public.patrolgrid_purge_expired_evidence_scheduled();$cron$
);

select cron.schedule(
    'patrolgrid-cancel-expired-unstarted',
    '*/5 * * * *',
    $cron$select public.patrolgrid_cancel_expired_unstarted_missions_scheduled();$cron$
);
