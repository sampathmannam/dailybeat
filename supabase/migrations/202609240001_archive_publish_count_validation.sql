-- SQL comparisons against NULL do not evaluate to true. Reject a missing part count
-- explicitly before either publishing an archive or treating publication as a retry.
-- This changes no existing rows and retains the same owner-only RPC permissions.
create or replace function public.publish_dailybeat_backup(backup_id uuid, backup_manifest jsonb, part_count integer) returns void
language plpgsql security definer set search_path = '' as $$
declare owner_id uuid := auth.uid(); existing jsonb; actual_count integer; last_index integer;
begin
    if owner_id is null then raise exception 'Authentication required'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(owner_id::text, 0));
    select manifest into existing from public.dailybeat_backup_versions where id = backup_id and user_id = owner_id for update;
    if not found then raise exception 'Backup unavailable'; end if;
    if part_count is null or part_count not between 1 and 2048 then
        raise exception 'Backup pages are incomplete';
    end if;
    if existing is not null then
        if existing = backup_manifest then return; end if;
        raise exception 'Backup is already published';
    end if;
    if backup_manifest->>'format' is distinct from 'dailybeat-archive' or backup_manifest->>'version' is distinct from '1'
       or not (backup_manifest ?& array['salt','sealed']) then raise exception 'Invalid archive manifest'; end if;
    select count(*), max(part_index) into actual_count, last_index from public.dailybeat_backup_parts where version_id = backup_id;
    if actual_count <> part_count or last_index is distinct from part_count - 1 then
        raise exception 'Backup pages are incomplete';
    end if;
    update public.dailybeat_backup_versions set manifest = backup_manifest where id = backup_id;
    -- Retention changes only after a complete replacement has been committed.
    delete from public.dailybeat_backup_versions where id in (
        select id from public.dailybeat_backup_versions where user_id = owner_id and manifest is not null
        order by created_at desc, id desc offset 5
    );
end $$;

revoke all on function public.publish_dailybeat_backup(uuid,jsonb,integer) from public, anon, authenticated;
grant execute on function public.publish_dailybeat_backup(uuid,jsonb,integer) to authenticated;
