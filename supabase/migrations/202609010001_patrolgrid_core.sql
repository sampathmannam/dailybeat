create extension if not exists pgcrypto with schema extensions;

create table public.patrolgrid_subdivisions (
    id uuid primary key default gen_random_uuid(),
    code text not null unique check (code ~ '^[A-Z0-9_-]{2,32}$'),
    name text not null check (char_length(name) between 2 and 120),
    timezone text not null default 'Asia/Kolkata' check (char_length(timezone) between 3 and 64),
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.patrolgrid_memberships (
    subdivision_id uuid not null references public.patrolgrid_subdivisions(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null check (role in ('supervisor', 'patrol')),
    display_name text not null check (char_length(display_name) between 2 and 120),
    badge_number text check (badge_number is null or char_length(badge_number) between 1 and 40),
    status text not null default 'active' check (status in ('active', 'disabled')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (subdivision_id, user_id)
);

create unique index patrolgrid_one_active_subdivision_per_user
on public.patrolgrid_memberships(user_id)
where status = 'active';

create table public.patrolgrid_route_templates (
    id uuid primary key default gen_random_uuid(),
    subdivision_id uuid not null references public.patrolgrid_subdivisions(id) on delete cascade,
    name text not null check (char_length(name) between 2 and 160),
    default_guidance text not null check (default_guidance in ('suggested_route', 'area_coverage')),
    route_geojson jsonb not null check (jsonb_typeof(route_geojson) = 'object'),
    default_start_time time not null default '22:00',
    default_duration_minutes integer not null default 240 check (default_duration_minutes between 30 and 720),
    is_active boolean not null default true,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index patrolgrid_route_templates_subdivision
on public.patrolgrid_route_templates(subdivision_id, is_active);

create table public.patrolgrid_route_template_priorities (
    id uuid primary key default gen_random_uuid(),
    route_template_id uuid not null references public.patrolgrid_route_templates(id) on delete cascade,
    name text not null check (char_length(name) between 2 and 160),
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    radius_m integer not null default 75 check (radius_m between 25 and 500),
    sort_order integer not null check (sort_order between 0 and 1000),
    required boolean not null default true,
    created_at timestamptz not null default now(),
    unique (route_template_id, sort_order)
);

create table public.patrolgrid_units (
    id uuid primary key default gen_random_uuid(),
    subdivision_id uuid not null references public.patrolgrid_subdivisions(id) on delete cascade,
    name text not null check (char_length(name) between 2 and 120),
    is_active boolean not null default true,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (subdivision_id, name)
);

create table public.patrolgrid_unit_members (
    unit_id uuid not null references public.patrolgrid_units(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (unit_id, user_id)
);

create index patrolgrid_unit_members_user on public.patrolgrid_unit_members(user_id);

create table public.patrolgrid_missions (
    id uuid primary key default gen_random_uuid(),
    subdivision_id uuid not null references public.patrolgrid_subdivisions(id) on delete cascade,
    route_template_id uuid references public.patrolgrid_route_templates(id) on delete set null,
    title text not null check (char_length(title) between 2 and 160),
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    guidance text not null check (guidance in ('suggested_route', 'area_coverage')),
    instructions text not null default '' check (char_length(instructions) <= 4000),
    status text not null default 'planned'
        check (status in ('planned', 'assigned', 'active', 'completed', 'needs_review', 'cancelled')),
    version integer not null default 1 check (version > 0),
    retention_until timestamptz,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (ends_at > starts_at)
);

create index patrolgrid_missions_subdivision_window
on public.patrolgrid_missions(subdivision_id, starts_at desc);

create table public.patrolgrid_assignments (
    mission_id uuid not null references public.patrolgrid_missions(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete restrict,
    assigned_by uuid not null references auth.users(id),
    assigned_at timestamptz not null default now(),
    acknowledged_at timestamptz,
    primary key (mission_id, user_id)
);

create index patrolgrid_assignments_user
on public.patrolgrid_assignments(user_id, assigned_at desc);

create table public.patrolgrid_priority_locations (
    id uuid primary key default gen_random_uuid(),
    mission_id uuid not null references public.patrolgrid_missions(id) on delete cascade,
    name text not null check (char_length(name) between 2 and 160),
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    radius_m integer not null default 75 check (radius_m between 25 and 500),
    sort_order integer not null check (sort_order between 0 and 1000),
    required boolean not null default true,
    created_at timestamptz not null default now(),
    unique (mission_id, sort_order)
);

create table public.patrolgrid_sessions (
    id uuid primary key default gen_random_uuid(),
    mission_id uuid not null references public.patrolgrid_missions(id) on delete restrict,
    user_id uuid not null references auth.users(id) on delete restrict,
    installation_id uuid not null,
    started_at timestamptz not null,
    ended_at timestamptz,
    end_reason text check (end_reason is null or end_reason in ('completed', 'relieved', 'cancelled', 'device_issue')),
    app_version text not null check (char_length(app_version) between 1 and 40),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (ended_at is null or ended_at >= started_at)
);

create unique index patrolgrid_one_open_session_per_assignment
on public.patrolgrid_sessions(mission_id, user_id)
where ended_at is null;

create table public.patrolgrid_track_points (
    id bigint generated always as identity primary key,
    client_point_id uuid not null,
    session_id uuid not null references public.patrolgrid_sessions(id) on delete restrict,
    mission_id uuid not null references public.patrolgrid_missions(id) on delete restrict,
    user_id uuid not null references auth.users(id) on delete restrict,
    sequence_number integer not null check (sequence_number >= 0),
    recorded_at timestamptz not null,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    accuracy_m real not null check (accuracy_m >= 0 and accuracy_m <= 5000),
    created_at timestamptz not null default now(),
    unique (user_id, client_point_id),
    unique (session_id, sequence_number)
);

create index patrolgrid_track_points_mission_time
on public.patrolgrid_track_points(mission_id, recorded_at);

create table public.patrolgrid_priority_visits (
    id uuid primary key default gen_random_uuid(),
    priority_location_id uuid not null references public.patrolgrid_priority_locations(id) on delete restrict,
    mission_id uuid not null references public.patrolgrid_missions(id) on delete restrict,
    user_id uuid not null references auth.users(id) on delete restrict,
    visited_at timestamptz not null,
    method text not null check (method in ('gps', 'manual_with_context')),
    latitude double precision check (latitude is null or latitude between -90 and 90),
    longitude double precision check (longitude is null or longitude between -180 and 180),
    accuracy_m real check (accuracy_m is null or (accuracy_m >= 0 and accuracy_m <= 5000)),
    note text not null default '' check (char_length(note) <= 2000),
    created_at timestamptz not null default now(),
    unique (priority_location_id, user_id)
);

create table public.patrolgrid_field_updates (
    id uuid primary key default gen_random_uuid(),
    client_update_id uuid not null,
    mission_id uuid not null references public.patrolgrid_missions(id) on delete restrict,
    user_id uuid not null references auth.users(id) on delete restrict,
    category text not null check (category in ('observation', 'operational_deviation', 'safety_event')),
    detail text not null check (char_length(detail) between 1 and 4000),
    occurred_at timestamptz not null,
    latitude double precision check (latitude is null or latitude between -90 and 90),
    longitude double precision check (longitude is null or longitude between -180 and 180),
    created_at timestamptz not null default now(),
    unique (user_id, client_update_id)
);

create index patrolgrid_field_updates_mission_time
on public.patrolgrid_field_updates(mission_id, occurred_at desc);

create table public.patrolgrid_reviews (
    id uuid primary key default gen_random_uuid(),
    mission_id uuid not null references public.patrolgrid_missions(id) on delete restrict,
    reviewer_id uuid not null references auth.users(id) on delete restrict,
    outcome text not null check (outcome in ('approved', 'needs_context', 'technically_inconclusive')),
    notes text not null default '' check (char_length(notes) <= 4000),
    reviewed_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index patrolgrid_reviews_mission_time
on public.patrolgrid_reviews(mission_id, reviewed_at desc);

create table public.patrolgrid_audit_events (
    id bigint generated always as identity primary key,
    subdivision_id uuid not null references public.patrolgrid_subdivisions(id) on delete restrict,
    mission_id uuid references public.patrolgrid_missions(id) on delete restrict,
    actor_id uuid references auth.users(id) on delete set null,
    event_type text not null check (char_length(event_type) between 2 and 80),
    payload jsonb not null default '{}'::jsonb check (jsonb_typeof(payload) = 'object'),
    occurred_at timestamptz not null default now()
);

create index patrolgrid_audit_events_subdivision_time
on public.patrolgrid_audit_events(subdivision_id, occurred_at desc);

create or replace function public.patrolgrid_is_active_member(target_subdivision uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.patrolgrid_memberships membership
        where membership.subdivision_id = target_subdivision
          and membership.user_id = auth.uid()
          and membership.status = 'active'
    );
$$;

create or replace function public.patrolgrid_is_supervisor(target_subdivision uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.patrolgrid_memberships membership
        where membership.subdivision_id = target_subdivision
          and membership.user_id = auth.uid()
          and membership.role = 'supervisor'
          and membership.status = 'active'
    );
$$;

create or replace function public.patrolgrid_is_assigned(target_mission uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.patrolgrid_assignments assignment
        join public.patrolgrid_missions mission on mission.id = assignment.mission_id
        join public.patrolgrid_memberships membership
          on membership.subdivision_id = mission.subdivision_id
         and membership.user_id = assignment.user_id
        where assignment.mission_id = target_mission
          and assignment.user_id = auth.uid()
          and membership.status = 'active'
    );
$$;

create or replace function public.patrolgrid_can_access_mission(target_mission uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.patrolgrid_missions mission
        where mission.id = target_mission
          and (
            public.patrolgrid_is_supervisor(mission.subdivision_id)
            or public.patrolgrid_is_assigned(mission.id)
          )
    );
$$;

create or replace function public.patrolgrid_is_supervisor_for_mission(target_mission uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.patrolgrid_missions mission
        where mission.id = target_mission
          and public.patrolgrid_is_supervisor(mission.subdivision_id)
    );
$$;

create or replace function public.patrolgrid_is_unit_member(target_unit uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.patrolgrid_unit_members unit_member
        join public.patrolgrid_units unit on unit.id = unit_member.unit_id
        join public.patrolgrid_memberships membership
          on membership.subdivision_id = unit.subdivision_id
         and membership.user_id = unit_member.user_id
        where unit_member.unit_id = target_unit
          and unit_member.user_id = auth.uid()
          and unit.is_active
          and membership.status = 'active'
    );
$$;

create or replace function public.patrolgrid_create_assignment(
    target_route_template uuid,
    target_unit uuid,
    target_guidance text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    route public.patrolgrid_route_templates;
    unit public.patrolgrid_units;
    subdivision public.patrolgrid_subdivisions;
    mission_id uuid;
    mission_start timestamptz;
    assigned_count integer;
begin
    if target_guidance not in ('suggested_route', 'area_coverage') then
        raise exception 'Invalid patrol guidance';
    end if;

    select * into strict route
    from public.patrolgrid_route_templates
    where id = target_route_template and is_active;

    select * into strict unit
    from public.patrolgrid_units
    where id = target_unit and is_active;

    if route.subdivision_id <> unit.subdivision_id
       or not public.patrolgrid_is_supervisor(route.subdivision_id) then
        raise exception 'Route and unit must belong to the supervisor subdivision';
    end if;

    select * into strict subdivision
    from public.patrolgrid_subdivisions
    where id = route.subdivision_id;

    perform 1 from pg_catalog.pg_timezone_names where name = subdivision.timezone;
    if not found then
        raise exception 'Subdivision timezone is invalid';
    end if;

    mission_start := (
        ((now() at time zone subdivision.timezone)::date + route.default_start_time)
        at time zone subdivision.timezone
    );
    if mission_start < now() + interval '15 minutes' then
        mission_start := mission_start + interval '1 day';
    end if;

    insert into public.patrolgrid_missions (
        subdivision_id,
        route_template_id,
        title,
        starts_at,
        ends_at,
        guidance,
        instructions,
        status,
        created_by
    ) values (
        route.subdivision_id,
        route.id,
        route.name,
        mission_start,
        mission_start + make_interval(mins => route.default_duration_minutes),
        target_guidance,
        'Follow the mission priorities; use field judgment and record operational deviations.',
        'assigned',
        auth.uid()
    ) returning id into mission_id;

    insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by)
    select mission_id, unit_member.user_id, auth.uid()
    from public.patrolgrid_unit_members unit_member
    join public.patrolgrid_memberships membership
      on membership.subdivision_id = route.subdivision_id
     and membership.user_id = unit_member.user_id
     and membership.role = 'patrol'
     and membership.status = 'active'
    where unit_member.unit_id = unit.id;

    get diagnostics assigned_count = row_count;
    if assigned_count = 0 then
        raise exception 'The selected unit has no active patrol personnel';
    end if;

    insert into public.patrolgrid_priority_locations (
        mission_id, name, latitude, longitude, radius_m, sort_order, required
    )
    select mission_id, priority.name, priority.latitude, priority.longitude,
           priority.radius_m, priority.sort_order, priority.required
    from public.patrolgrid_route_template_priorities priority
    where priority.route_template_id = route.id
    order by priority.sort_order;

    return mission_id;
exception
    when no_data_found then
        raise exception 'Route template or patrol unit is unavailable';
end;
$$;

revoke all on function public.patrolgrid_is_active_member(uuid) from public;
revoke all on function public.patrolgrid_is_supervisor(uuid) from public;
revoke all on function public.patrolgrid_is_assigned(uuid) from public;
revoke all on function public.patrolgrid_can_access_mission(uuid) from public;
revoke all on function public.patrolgrid_is_supervisor_for_mission(uuid) from public;
revoke all on function public.patrolgrid_is_unit_member(uuid) from public;
revoke all on function public.patrolgrid_create_assignment(uuid, uuid, text) from public;
grant execute on function public.patrolgrid_is_active_member(uuid) to authenticated;
grant execute on function public.patrolgrid_is_supervisor(uuid) to authenticated;
grant execute on function public.patrolgrid_is_assigned(uuid) to authenticated;
grant execute on function public.patrolgrid_can_access_mission(uuid) to authenticated;
grant execute on function public.patrolgrid_is_supervisor_for_mission(uuid) to authenticated;
grant execute on function public.patrolgrid_is_unit_member(uuid) to authenticated;
grant execute on function public.patrolgrid_create_assignment(uuid, uuid, text) to authenticated;

create or replace function public.patrolgrid_touch_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger patrolgrid_touch_subdivision
before update on public.patrolgrid_subdivisions
for each row execute function public.patrolgrid_touch_updated_at();

create trigger patrolgrid_touch_membership
before update on public.patrolgrid_memberships
for each row execute function public.patrolgrid_touch_updated_at();

create trigger patrolgrid_touch_route
before update on public.patrolgrid_route_templates
for each row execute function public.patrolgrid_touch_updated_at();

create trigger patrolgrid_touch_unit
before update on public.patrolgrid_units
for each row execute function public.patrolgrid_touch_updated_at();

create trigger patrolgrid_touch_mission
before update on public.patrolgrid_missions
for each row execute function public.patrolgrid_touch_updated_at();

create trigger patrolgrid_touch_session
before update on public.patrolgrid_sessions
for each row execute function public.patrolgrid_touch_updated_at();

create or replace function public.patrolgrid_protect_route_update()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id <> old.id
       or new.subdivision_id <> old.subdivision_id
       or new.created_by <> old.created_by
       or new.created_at <> old.created_at then
        raise exception 'Immutable patrol route fields cannot be changed';
    end if;
    return new;
end;
$$;

create trigger patrolgrid_protect_route
before update on public.patrolgrid_route_templates
for each row execute function public.patrolgrid_protect_route_update();

create or replace function public.patrolgrid_protect_unit_update()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id <> old.id
       or new.subdivision_id <> old.subdivision_id
       or new.created_by <> old.created_by
       or new.created_at <> old.created_at then
        raise exception 'Immutable patrol unit fields cannot be changed';
    end if;
    return new;
end;
$$;

create trigger patrolgrid_protect_unit
before update on public.patrolgrid_units
for each row execute function public.patrolgrid_protect_unit_update();

create or replace function public.patrolgrid_protect_mission_update()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id <> old.id
       or new.subdivision_id <> old.subdivision_id
       or new.created_by <> old.created_by
       or new.created_at <> old.created_at then
        raise exception 'Immutable patrol mission fields cannot be changed';
    end if;
    new.version := old.version + 1;
    return new;
end;
$$;

create trigger patrolgrid_protect_mission
before update on public.patrolgrid_missions
for each row execute function public.patrolgrid_protect_mission_update();

create or replace function public.patrolgrid_validate_mission_route()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.route_template_id is not null and not exists (
        select 1
        from public.patrolgrid_route_templates route
        where route.id = new.route_template_id
          and route.subdivision_id = new.subdivision_id
          and route.is_active
    ) then
        raise exception 'Mission route must be active and belong to the same subdivision';
    end if;
    return new;
end;
$$;

create trigger patrolgrid_validate_mission_route
before insert or update of route_template_id, subdivision_id on public.patrolgrid_missions
for each row execute function public.patrolgrid_validate_mission_route();

create or replace function public.patrolgrid_protect_priority_update()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id <> old.id
       or new.mission_id <> old.mission_id
       or new.created_at <> old.created_at then
        raise exception 'Immutable priority location fields cannot be changed';
    end if;
    return new;
end;
$$;

create trigger patrolgrid_protect_priority
before update on public.patrolgrid_priority_locations
for each row execute function public.patrolgrid_protect_priority_update();

create or replace function public.patrolgrid_protect_session_update()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id <> old.id
       or new.mission_id <> old.mission_id
       or new.user_id <> old.user_id
       or new.installation_id <> old.installation_id
       or new.started_at <> old.started_at
       or new.app_version <> old.app_version then
        raise exception 'Immutable patrol session fields cannot be changed';
    end if;
    if old.ended_at is not null and row(new.ended_at, new.end_reason) is distinct from row(old.ended_at, old.end_reason) then
        raise exception 'A closed patrol session cannot be changed';
    end if;
    return new;
end;
$$;

create trigger patrolgrid_protect_session
before update on public.patrolgrid_sessions
for each row execute function public.patrolgrid_protect_session_update();

create or replace function public.patrolgrid_roll_mission_status()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        update public.patrolgrid_missions
        set status = 'active'
        where id = new.mission_id and status = 'assigned';
    elsif old.ended_at is null
       and new.ended_at is not null
       and not exists (
           select 1
           from public.patrolgrid_sessions session
           where session.mission_id = new.mission_id
             and session.ended_at is null
       ) then
        update public.patrolgrid_missions
        set status = 'needs_review'
        where id = new.mission_id and status = 'active';
    end if;
    return new;
end;
$$;

create trigger patrolgrid_roll_mission_status
after insert or update of ended_at on public.patrolgrid_sessions
for each row execute function public.patrolgrid_roll_mission_status();

create or replace function public.patrolgrid_write_audit_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    mission_record public.patrolgrid_missions;
    event_subdivision uuid;
    event_mission uuid;
    record_key text;
begin
    if tg_table_name = 'patrolgrid_missions' then
        if tg_op = 'DELETE' then
            event_subdivision := old.subdivision_id;
            event_mission := null;
        else
            event_subdivision := new.subdivision_id;
            event_mission := new.id;
        end if;
        record_key := case when tg_op = 'DELETE' then old.id::text else new.id::text end;
    elsif tg_table_name = 'patrolgrid_route_templates' then
        if tg_op = 'DELETE' then
            event_subdivision := old.subdivision_id;
            record_key := old.id::text;
        else
            event_subdivision := new.subdivision_id;
            record_key := new.id::text;
        end if;
    elsif tg_table_name = 'patrolgrid_assignments' then
        if tg_op = 'DELETE' then
            event_mission := old.mission_id;
            record_key := old.mission_id::text || ':' || old.user_id::text;
        else
            event_mission := new.mission_id;
            record_key := new.mission_id::text || ':' || new.user_id::text;
        end if;
        select * into mission_record from public.patrolgrid_missions where id = event_mission;
        event_subdivision := mission_record.subdivision_id;
    else
        if tg_op = 'DELETE' then
            event_mission := old.mission_id;
        else
            event_mission := new.mission_id;
        end if;
        select * into mission_record from public.patrolgrid_missions where id = event_mission;
        event_subdivision := mission_record.subdivision_id;
        record_key := case when tg_op = 'DELETE' then old.id::text else new.id::text end;
    end if;
    insert into public.patrolgrid_audit_events (
        subdivision_id,
        mission_id,
        actor_id,
        event_type,
        payload
    ) values (
        event_subdivision,
        event_mission,
        auth.uid(),
        tg_table_name || '.' || lower(tg_op),
        jsonb_build_object('record_id', record_key)
    );
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

create trigger patrolgrid_audit_mission
after insert or update or delete on public.patrolgrid_missions
for each row execute function public.patrolgrid_write_audit_event();

create trigger patrolgrid_audit_route
after insert or update or delete on public.patrolgrid_route_templates
for each row execute function public.patrolgrid_write_audit_event();

create trigger patrolgrid_audit_assignment
after insert or delete on public.patrolgrid_assignments
for each row execute function public.patrolgrid_write_audit_event();

create trigger patrolgrid_audit_priority
after insert or update or delete on public.patrolgrid_priority_locations
for each row execute function public.patrolgrid_write_audit_event();

create trigger patrolgrid_audit_session
after insert or update on public.patrolgrid_sessions
for each row execute function public.patrolgrid_write_audit_event();

create trigger patrolgrid_audit_review
after insert on public.patrolgrid_reviews
for each row execute function public.patrolgrid_write_audit_event();

alter table public.patrolgrid_subdivisions enable row level security;
alter table public.patrolgrid_memberships enable row level security;
alter table public.patrolgrid_route_templates enable row level security;
alter table public.patrolgrid_route_template_priorities enable row level security;
alter table public.patrolgrid_units enable row level security;
alter table public.patrolgrid_unit_members enable row level security;
alter table public.patrolgrid_missions enable row level security;
alter table public.patrolgrid_assignments enable row level security;
alter table public.patrolgrid_priority_locations enable row level security;
alter table public.patrolgrid_sessions enable row level security;
alter table public.patrolgrid_track_points enable row level security;
alter table public.patrolgrid_priority_visits enable row level security;
alter table public.patrolgrid_field_updates enable row level security;
alter table public.patrolgrid_reviews enable row level security;
alter table public.patrolgrid_audit_events enable row level security;

revoke all on all tables in schema public from anon;
revoke all on all sequences in schema public from anon;

grant all on public.patrolgrid_subdivisions to service_role;
grant all on public.patrolgrid_memberships to service_role;
grant all on public.patrolgrid_route_templates to service_role;
grant all on public.patrolgrid_route_template_priorities to service_role;
grant all on public.patrolgrid_units to service_role;
grant all on public.patrolgrid_unit_members to service_role;
grant all on public.patrolgrid_missions to service_role;
grant all on public.patrolgrid_assignments to service_role;
grant all on public.patrolgrid_priority_locations to service_role;
grant all on public.patrolgrid_sessions to service_role;
grant all on public.patrolgrid_track_points to service_role;
grant all on public.patrolgrid_priority_visits to service_role;
grant all on public.patrolgrid_field_updates to service_role;
grant all on public.patrolgrid_reviews to service_role;
grant all on public.patrolgrid_audit_events to service_role;
grant usage, select on sequence public.patrolgrid_track_points_id_seq to service_role;
grant usage, select on sequence public.patrolgrid_audit_events_id_seq to service_role;

grant select on public.patrolgrid_subdivisions to authenticated;
grant select on public.patrolgrid_memberships to authenticated;
grant select, insert, update, delete on public.patrolgrid_route_templates to authenticated;
grant select, insert, update, delete on public.patrolgrid_route_template_priorities to authenticated;
grant select, insert, update, delete on public.patrolgrid_units to authenticated;
grant select, insert, delete on public.patrolgrid_unit_members to authenticated;
grant select, insert, update, delete on public.patrolgrid_missions to authenticated;
grant select, insert, delete on public.patrolgrid_assignments to authenticated;
grant select, insert, update, delete on public.patrolgrid_priority_locations to authenticated;
grant select, insert, update on public.patrolgrid_sessions to authenticated;
grant select, insert on public.patrolgrid_track_points to authenticated;
grant usage, select on sequence public.patrolgrid_track_points_id_seq to authenticated;
grant select, insert on public.patrolgrid_priority_visits to authenticated;
grant select, insert on public.patrolgrid_field_updates to authenticated;
grant select, insert on public.patrolgrid_reviews to authenticated;
grant select on public.patrolgrid_audit_events to authenticated;

create policy "Members read their subdivision"
on public.patrolgrid_subdivisions for select to authenticated
using (public.patrolgrid_is_active_member(id));

create policy "Members read visible memberships"
on public.patrolgrid_memberships for select to authenticated
using (
    user_id = auth.uid()
    or public.patrolgrid_is_supervisor(subdivision_id)
);

create policy "Members read route templates"
on public.patrolgrid_route_templates for select to authenticated
using (public.patrolgrid_is_active_member(subdivision_id));

create policy "Supervisors create route templates"
on public.patrolgrid_route_templates for insert to authenticated
with check (public.patrolgrid_is_supervisor(subdivision_id) and created_by = auth.uid());

create policy "Supervisors update route templates"
on public.patrolgrid_route_templates for update to authenticated
using (public.patrolgrid_is_supervisor(subdivision_id))
with check (public.patrolgrid_is_supervisor(subdivision_id));

create policy "Supervisors delete route templates"
on public.patrolgrid_route_templates for delete to authenticated
using (public.patrolgrid_is_supervisor(subdivision_id));

create policy "Members read route template priorities"
on public.patrolgrid_route_template_priorities for select to authenticated
using (
    exists (
        select 1 from public.patrolgrid_route_templates route
        where route.id = patrolgrid_route_template_priorities.route_template_id
          and public.patrolgrid_is_active_member(route.subdivision_id)
    )
);

create policy "Supervisors manage route template priorities"
on public.patrolgrid_route_template_priorities for all to authenticated
using (
    exists (
        select 1 from public.patrolgrid_route_templates route
        where route.id = patrolgrid_route_template_priorities.route_template_id
          and public.patrolgrid_is_supervisor(route.subdivision_id)
    )
)
with check (
    exists (
        select 1 from public.patrolgrid_route_templates route
        where route.id = patrolgrid_route_template_priorities.route_template_id
          and public.patrolgrid_is_supervisor(route.subdivision_id)
    )
);

create policy "Authorized users read units"
on public.patrolgrid_units for select to authenticated
using (
    public.patrolgrid_is_supervisor(subdivision_id)
    or public.patrolgrid_is_unit_member(id)
);

create policy "Supervisors manage units"
on public.patrolgrid_units for all to authenticated
using (public.patrolgrid_is_supervisor(subdivision_id))
with check (public.patrolgrid_is_supervisor(subdivision_id) and created_by = auth.uid());

create policy "Authorized users read unit members"
on public.patrolgrid_unit_members for select to authenticated
using (
    user_id = auth.uid()
    or exists (
        select 1 from public.patrolgrid_units unit
        where unit.id = patrolgrid_unit_members.unit_id
          and public.patrolgrid_is_supervisor(unit.subdivision_id)
    )
);

create policy "Supervisors manage unit members"
on public.patrolgrid_unit_members for all to authenticated
using (
    exists (
        select 1 from public.patrolgrid_units unit
        where unit.id = patrolgrid_unit_members.unit_id
          and public.patrolgrid_is_supervisor(unit.subdivision_id)
    )
)
with check (
    exists (
        select 1
        from public.patrolgrid_units unit
        join public.patrolgrid_memberships membership
          on membership.subdivision_id = unit.subdivision_id
         and membership.user_id = patrolgrid_unit_members.user_id
         and membership.role = 'patrol'
         and membership.status = 'active'
        where unit.id = patrolgrid_unit_members.unit_id
          and public.patrolgrid_is_supervisor(unit.subdivision_id)
    )
);

create policy "Authorized users read missions"
on public.patrolgrid_missions for select to authenticated
using (
    public.patrolgrid_is_supervisor(subdivision_id)
    or public.patrolgrid_is_assigned(id)
);

create policy "Supervisors create missions"
on public.patrolgrid_missions for insert to authenticated
with check (public.patrolgrid_is_supervisor(subdivision_id) and created_by = auth.uid());

create policy "Supervisors update missions"
on public.patrolgrid_missions for update to authenticated
using (public.patrolgrid_is_supervisor(subdivision_id))
with check (public.patrolgrid_is_supervisor(subdivision_id));

create policy "Supervisors delete planned missions"
on public.patrolgrid_missions for delete to authenticated
using (public.patrolgrid_is_supervisor(subdivision_id) and status = 'planned');

create policy "Authorized users read assignments"
on public.patrolgrid_assignments for select to authenticated
using (
    user_id = auth.uid()
    or public.patrolgrid_is_supervisor_for_mission(mission_id)
);

create policy "Supervisors create assignments"
on public.patrolgrid_assignments for insert to authenticated
with check (
    assigned_by = auth.uid()
    and exists (
        select 1
        from public.patrolgrid_missions mission
        join public.patrolgrid_memberships membership
          on membership.subdivision_id = mission.subdivision_id
         and membership.user_id = patrolgrid_assignments.user_id
         and membership.role = 'patrol'
         and membership.status = 'active'
        where mission.id = patrolgrid_assignments.mission_id
          and public.patrolgrid_is_supervisor(mission.subdivision_id)
    )
);

create policy "Supervisors delete assignments"
on public.patrolgrid_assignments for delete to authenticated
using (
    exists (
        select 1 from public.patrolgrid_missions mission
        where mission.id = patrolgrid_assignments.mission_id
          and public.patrolgrid_is_supervisor(mission.subdivision_id)
    )
);

create policy "Authorized users read priority locations"
on public.patrolgrid_priority_locations for select to authenticated
using (public.patrolgrid_can_access_mission(mission_id));

create policy "Supervisors manage priority locations"
on public.patrolgrid_priority_locations for all to authenticated
using (
    exists (
        select 1 from public.patrolgrid_missions mission
        where mission.id = patrolgrid_priority_locations.mission_id
          and public.patrolgrid_is_supervisor(mission.subdivision_id)
    )
)
with check (
    exists (
        select 1 from public.patrolgrid_missions mission
        where mission.id = patrolgrid_priority_locations.mission_id
          and public.patrolgrid_is_supervisor(mission.subdivision_id)
    )
);

create policy "Authorized users read sessions"
on public.patrolgrid_sessions for select to authenticated
using (user_id = auth.uid() or public.patrolgrid_is_supervisor_for_mission(mission_id));

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
          and now() >= mission.starts_at - interval '12 hours'
          and now() <= mission.ends_at + interval '24 hours'
    )
);

create policy "Patrol closes own sessions"
on public.patrolgrid_sessions for update to authenticated
using (user_id = auth.uid() and ended_at is null)
with check (user_id = auth.uid() and ended_at is not null and end_reason is not null);

create policy "Authorized users read track points"
on public.patrolgrid_track_points for select to authenticated
using (user_id = auth.uid() or public.patrolgrid_is_supervisor_for_mission(mission_id));

create policy "Patrol appends track points"
on public.patrolgrid_track_points for insert to authenticated
with check (
    user_id = auth.uid()
    and public.patrolgrid_is_assigned(mission_id)
    and exists (
        select 1 from public.patrolgrid_sessions session
        where session.id = patrolgrid_track_points.session_id
          and session.mission_id = patrolgrid_track_points.mission_id
          and session.user_id = auth.uid()
          and session.ended_at is null
          and patrolgrid_track_points.recorded_at >= session.started_at - interval '5 minutes'
    )
    and recorded_at <= now() + interval '5 minutes'
);

create policy "Authorized users read priority visits"
on public.patrolgrid_priority_visits for select to authenticated
using (user_id = auth.uid() or public.patrolgrid_is_supervisor_for_mission(mission_id));

create policy "Patrol records priority visits"
on public.patrolgrid_priority_visits for insert to authenticated
with check (
    user_id = auth.uid()
    and public.patrolgrid_is_assigned(mission_id)
    and exists (
        select 1 from public.patrolgrid_priority_locations location
        where location.id = patrolgrid_priority_visits.priority_location_id
          and location.mission_id = patrolgrid_priority_visits.mission_id
    )
    and exists (
        select 1 from public.patrolgrid_sessions session
        where session.mission_id = patrolgrid_priority_visits.mission_id
          and session.user_id = auth.uid()
          and patrolgrid_priority_visits.visited_at >= session.started_at - interval '5 minutes'
          and (
              session.ended_at is null
              or patrolgrid_priority_visits.visited_at <= session.ended_at + interval '5 minutes'
          )
    )
    and visited_at <= now() + interval '5 minutes'
);

create policy "Authorized users read field updates"
on public.patrolgrid_field_updates for select to authenticated
using (user_id = auth.uid() or public.patrolgrid_is_supervisor_for_mission(mission_id));

create policy "Patrol records field updates"
on public.patrolgrid_field_updates for insert to authenticated
with check (
    user_id = auth.uid()
    and public.patrolgrid_is_assigned(mission_id)
    and exists (
        select 1 from public.patrolgrid_sessions session
        where session.mission_id = patrolgrid_field_updates.mission_id
          and session.user_id = auth.uid()
          and patrolgrid_field_updates.occurred_at >= session.started_at - interval '5 minutes'
          and (
              session.ended_at is null
              or patrolgrid_field_updates.occurred_at <= session.ended_at + interval '5 minutes'
          )
    )
    and occurred_at <= now() + interval '5 minutes'
);

create policy "Authorized users read reviews"
on public.patrolgrid_reviews for select to authenticated
using (public.patrolgrid_can_access_mission(mission_id));

create policy "Supervisors create reviews"
on public.patrolgrid_reviews for insert to authenticated
with check (
    reviewer_id = auth.uid()
    and exists (
        select 1 from public.patrolgrid_missions mission
        where mission.id = patrolgrid_reviews.mission_id
          and public.patrolgrid_is_supervisor(mission.subdivision_id)
    )
);

create policy "Supervisors read audit events"
on public.patrolgrid_audit_events for select to authenticated
using (public.patrolgrid_is_supervisor(subdivision_id));
