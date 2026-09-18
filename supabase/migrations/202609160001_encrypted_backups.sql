-- Deploy before enabling encrypted cloud backup in a public release.
-- Separate storage prevents an older app from overwriting ciphertext with a readable snapshot.
-- This migration neither reads nor deletes legacy backups.
create table public.dailybeat_encrypted_backups (
    user_id uuid primary key references auth.users(id) on delete cascade,
    snapshot jsonb not null,
    updated_at timestamptz not null default now(),
    constraint dailybeat_encrypted_backup_envelope check (
        jsonb_typeof(snapshot) = 'object'
        and snapshot ?& array['format', 'envelopeVersion', 'kdf', 'iterations', 'salt', 'nonce', 'ciphertext']
        and snapshot->>'format' = 'dailybeat-encrypted-backup'
        and snapshot->>'envelopeVersion' = '1'
        and snapshot->>'kdf' = 'PBKDF2-HMAC-SHA256'
        and snapshot->>'iterations' = '600000'
        and jsonb_typeof(snapshot->'ciphertext') = 'string'
        and length(snapshot->>'ciphertext') >= 24
    ),
    constraint dailybeat_encrypted_backup_size check (octet_length(snapshot::text) <= 12582912)
);
alter table public.dailybeat_encrypted_backups enable row level security;
revoke all on table public.dailybeat_encrypted_backups from public, anon, authenticated;
grant select, insert, update, delete on table public.dailybeat_encrypted_backups to authenticated;
create policy "Read own encrypted backup" on public.dailybeat_encrypted_backups
for select to authenticated using ((select auth.uid()) = user_id);
create policy "Create own encrypted backup" on public.dailybeat_encrypted_backups
for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "Update own encrypted backup" on public.dailybeat_encrypted_backups
for update to authenticated using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);
create policy "Delete own encrypted backup" on public.dailybeat_encrypted_backups
for delete to authenticated using ((select auth.uid()) = user_id);
create trigger set_dailybeat_encrypted_backup_updated_at before update
on public.dailybeat_encrypted_backups for each row
execute function public.set_dailybeat_backup_updated_at();
