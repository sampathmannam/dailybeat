alter table public.patrolgrid_missions
add column route_geojson jsonb not null
default '{"type":"LineString","coordinates":[]}'::jsonb
check (jsonb_typeof(route_geojson) = 'object');

update public.patrolgrid_missions mission
set route_geojson = route.route_geojson
from public.patrolgrid_route_templates route
where mission.route_template_id = route.id;

create or replace function public.patrolgrid_snapshot_mission_route()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.route_template_id is not null then
        select route.route_geojson
        into strict new.route_geojson
        from public.patrolgrid_route_templates route
        where route.id = new.route_template_id
          and route.subdivision_id = new.subdivision_id
          and route.is_active;
    end if;
    return new;
exception
    when no_data_found then
        raise exception 'Mission route must be active and belong to the same subdivision';
end;
$$;

create trigger patrolgrid_snapshot_mission_route
before insert on public.patrolgrid_missions
for each row execute function public.patrolgrid_snapshot_mission_route();

create or replace function public.patrolgrid_protect_mission_update()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.id <> old.id
       or new.subdivision_id <> old.subdivision_id
       or new.route_template_id is distinct from old.route_template_id
       or new.route_geojson is distinct from old.route_geojson
       or new.created_by <> old.created_by
       or new.created_at <> old.created_at then
        raise exception 'Immutable patrol mission fields cannot be changed';
    end if;
    new.version := old.version + 1;
    return new;
end;
$$;

drop policy if exists "Members read route templates"
on public.patrolgrid_route_templates;

create policy "Supervisors read route templates"
on public.patrolgrid_route_templates for select to authenticated
using (public.patrolgrid_is_supervisor(subdivision_id));

drop policy if exists "Members read route template priorities"
on public.patrolgrid_route_template_priorities;

create policy "Supervisors read route template priorities"
on public.patrolgrid_route_template_priorities for select to authenticated
using (
    exists (
        select 1
        from public.patrolgrid_route_templates route
        where route.id = patrolgrid_route_template_priorities.route_template_id
          and public.patrolgrid_is_supervisor(route.subdivision_id)
    )
);
