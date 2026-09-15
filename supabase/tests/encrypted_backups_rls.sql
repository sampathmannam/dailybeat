-- Encrypted storage has independent owner isolation; legacy clients never write this table.
begin;
select plan(10);
select has_table('public', 'dailybeat_encrypted_backups', 'encrypted backup table exists');
select table_privs_are('public', 'dailybeat_encrypted_backups', 'anon', array[]::text[], 'anonymous access denied');
select table_privs_are('public', 'dailybeat_encrypted_backups', 'authenticated',
    array['DELETE', 'INSERT', 'SELECT', 'UPDATE'], 'no truncate or trigger privileges');
select is((select relrowsecurity from pg_class where oid = 'public.dailybeat_encrypted_backups'::regclass),
    true, 'RLS is enabled');
insert into auth.users (id, email) values
('44444444-4444-4444-4444-444444444444', 'encrypted-a@example.com'),
('55555555-5555-5555-5555-555555555555', 'encrypted-b@example.com')
on conflict (id) do nothing;
insert into public.dailybeat_encrypted_backups (user_id, snapshot) values
('44444444-4444-4444-4444-444444444444',
 '{"format":"dailybeat-encrypted-backup","envelopeVersion":1,"kdf":"PBKDF2-HMAC-SHA256","iterations":600000,"salt":"test","nonce":"test","ciphertext":"AAAAAAAAAAAAAAAAAAAAAAAA"}'),
('55555555-5555-5555-5555-555555555555',
 '{"format":"dailybeat-encrypted-backup","envelopeVersion":1,"kdf":"PBKDF2-HMAC-SHA256","iterations":600000,"salt":"test","nonce":"test","ciphertext":"BBBBBBBBBBBBBBBBBBBBBBBB"}');
set local role authenticated;
set local "request.jwt.claims" = '{"sub":"55555555-5555-5555-5555-555555555555","role":"authenticated"}';
select is((select count(*)::int from public.dailybeat_encrypted_backups), 1, 'only own encrypted row is visible');
with attempted as (
 update public.dailybeat_encrypted_backups set snapshot = snapshot || '{"ciphertext":"CCCCCCCCCCCCCCCCCCCCCCCC"}'
 where user_id = '44444444-4444-4444-4444-444444444444' returning 1
) select is((select count(*)::int from attempted), 0, 'cannot replace another users ciphertext');
with attempted as (
 delete from public.dailybeat_encrypted_backups
 where user_id = '44444444-4444-4444-4444-444444444444' returning 1
) select is((select count(*)::int from attempted), 0, 'cannot delete another users ciphertext');
select throws_ok(
 $$update public.dailybeat_encrypted_backups set snapshot = '{"schemaVersion":2,"diaries":[]}'$$,
 '23514', null, 'readable legacy snapshot cannot downgrade an encrypted row');
select throws_ok(
 $$update public.dailybeat_encrypted_backups set user_id = '66666666-6666-6666-6666-666666666666'$$,
 '42501', null, 'cannot move a row to another owner');
select throws_ok(
 $$update public.dailybeat_encrypted_backups set snapshot = snapshot || jsonb_build_object('ciphertext', repeat('x', 13000000))$$,
 '23514', null, 'oversized encrypted backups are rejected');
select * from finish();
rollback;
