-- Immutable, paged encrypted backups. Deploy before shipping the archive-capable client.
create table public.dailybeat_backup_versions (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    manifest jsonb,
    constraint backup_manifest_size check (manifest is null or octet_length(manifest::text) <= 262144)
);
create index on public.dailybeat_backup_versions(user_id, created_at desc);
create table public.dailybeat_backup_parts (
    version_id uuid not null references public.dailybeat_backup_versions(id) on delete cascade,
    part_index integer not null check (part_index between 0 and 2047),
    payload jsonb not null check (jsonb_typeof(payload) = 'object' and payload ?& array['nonce','ciphertext']
        and jsonb_typeof(payload->'nonce') = 'string' and jsonb_typeof(payload->'ciphertext') = 'string'
        and octet_length(payload::text) <= 1048576),
    primary key(version_id, part_index)
);
alter table public.dailybeat_backup_versions enable row level security;
alter table public.dailybeat_backup_parts enable row level security;
revoke all on public.dailybeat_backup_versions, public.dailybeat_backup_parts from public, anon, authenticated;
grant select, delete on public.dailybeat_backup_versions to authenticated;
grant select on public.dailybeat_backup_parts to authenticated;
create policy "Read own archive versions" on public.dailybeat_backup_versions for select to authenticated using (user_id = (select auth.uid()));
create policy "Delete own archive versions" on public.dailybeat_backup_versions for delete to authenticated using (user_id = (select auth.uid()));
create policy "Read own archive parts" on public.dailybeat_backup_parts for select to authenticated using (
    exists(select 1 from public.dailybeat_backup_versions v where v.id = version_id and v.user_id = (select auth.uid()))
);

create function public.begin_dailybeat_backup(backup_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare owner_id uuid := auth.uid();
begin
    if owner_id is null then raise exception 'Authentication required'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(owner_id::text, 0));
    if exists(select 1 from public.dailybeat_backup_versions where id = backup_id and user_id = owner_id) then return; end if;
    delete from public.dailybeat_backup_versions where user_id = owner_id and manifest is null and created_at < now() - interval '1 day';
    if (select count(*) from public.dailybeat_backup_versions where user_id = owner_id and manifest is null) >= 2 then
        raise exception 'Two uploads are already pending; retry later or delete cloud backups';
    end if;
    insert into public.dailybeat_backup_versions(id, user_id) values (backup_id, owner_id);
end $$;

create function public.put_dailybeat_backup_part(backup_id uuid, part_index integer, part_payload jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare owner_id uuid := auth.uid(); existing jsonb; published jsonb; total_bytes bigint;
begin
    if owner_id is null then raise exception 'Authentication required'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(owner_id::text, 0));
    select manifest into published from public.dailybeat_backup_versions where id = backup_id and user_id = owner_id for update;
    if not found then raise exception 'Backup unavailable'; end if;
    select payload into existing from public.dailybeat_backup_parts p where p.version_id = backup_id and p.part_index = put_dailybeat_backup_part.part_index;
    if existing is not null then
        if existing = part_payload then return; end if;
        raise exception 'Backup parts are immutable';
    end if;
    if published is not null then raise exception 'Backup is already published'; end if;
    select coalesce(sum(octet_length(payload::text)),0) into total_bytes from public.dailybeat_backup_parts where version_id = backup_id;
    if total_bytes + octet_length(part_payload::text) > 67108864 then raise exception 'Backup quota exceeded'; end if;
    insert into public.dailybeat_backup_parts values (backup_id, part_index, part_payload);
end $$;

create function public.publish_dailybeat_backup(backup_id uuid, backup_manifest jsonb, part_count integer) returns void
language plpgsql security definer set search_path = '' as $$
declare owner_id uuid := auth.uid(); existing jsonb; actual_count integer; last_index integer;
begin
    if owner_id is null then raise exception 'Authentication required'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(owner_id::text, 0));
    select manifest into existing from public.dailybeat_backup_versions where id = backup_id and user_id = owner_id for update;
    if not found then raise exception 'Backup unavailable'; end if;
    if existing is not null then
        if existing = backup_manifest then return; end if;
        raise exception 'Backup is already published';
    end if;
    if backup_manifest->>'format' is distinct from 'dailybeat-archive' or backup_manifest->>'version' is distinct from '1'
       or not (backup_manifest ?& array['salt','sealed']) then raise exception 'Invalid archive manifest'; end if;
    select count(*), max(part_index) into actual_count, last_index from public.dailybeat_backup_parts where version_id = backup_id;
    if part_count not between 1 and 2048 or actual_count <> part_count or last_index <> part_count - 1 then
        raise exception 'Backup pages are incomplete';
    end if;
    update public.dailybeat_backup_versions set manifest = backup_manifest where id = backup_id;
    -- Retention changes only after a complete replacement has been committed.
    delete from public.dailybeat_backup_versions where id in (
        select id from public.dailybeat_backup_versions where user_id = owner_id and manifest is not null
        order by created_at desc, id desc offset 5
    );
end $$;
revoke all on function public.begin_dailybeat_backup(uuid), public.put_dailybeat_backup_part(uuid,integer,jsonb),
    public.publish_dailybeat_backup(uuid,jsonb,integer) from public, anon, authenticated;
grant execute on function public.begin_dailybeat_backup(uuid), public.put_dailybeat_backup_part(uuid,integer,jsonb),
    public.publish_dailybeat_backup(uuid,jsonb,integer) to authenticated;
