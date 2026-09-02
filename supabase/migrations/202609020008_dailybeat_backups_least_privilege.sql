-- Supabase installations can apply permissive default table privileges to
-- authenticated when a public-schema table is created. Reset this legacy
-- table's ACL explicitly so backup clients only receive the operations used by
-- the owner-scoped RLS policies.
revoke all on table public.dailybeat_backups from authenticated;
grant select, insert, update, delete on table public.dailybeat_backups to authenticated;
