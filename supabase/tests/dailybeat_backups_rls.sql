-- What must be true of the cloud-backup table. The first block is structural (table, key, grants,
-- RLS on). The second block is the one that matters: it signs in as two different users through
-- the same RLS the app uses and proves user B cannot read, overwrite, or delete user A's backup.
-- A named policy that does not actually isolate is worse than none, so we exercise it.
begin;

select plan(15);

-- ---- structure ---------------------------------------------------------------------------------
select has_table('public', 'dailybeat_backups', 'backup table exists');
select col_is_pk('public', 'dailybeat_backups', 'user_id', 'user_id is the primary key');
select policies_are(
    'public',
    'dailybeat_backups',
    array[
        'Users create their own DailyBeat backup',
        'Users delete their own DailyBeat backup',
        'Users read their own DailyBeat backup',
        'Users update their own DailyBeat backup'
    ],
    'only owner-scoped policies exist'
);
select table_privs_are(
    'public', 'dailybeat_backups', 'anon', array[]::text[],
    'anonymous users have no backup privileges'
);
select table_privs_are(
    'public', 'dailybeat_backups', 'authenticated',
    array['DELETE', 'INSERT', 'SELECT', 'UPDATE'],
    'authenticated users have the required backup privileges'
);
select ok(row_security_active('public.dailybeat_backups'::regclass), 'row-level security is active');
select col_has_check('public', 'dailybeat_backups', 'snapshot', 'snapshot has a size/shape check');

-- ---- two real users, exercised through RLS -----------------------------------------------------
-- Seed two auth users and a backup owned by each, as the privileged role (bypasses RLS for setup).
insert into auth.users (id, email) values
    ('11111111-1111-1111-1111-111111111111', 'alice@example.com'),
    ('22222222-2222-2222-2222-222222222222', 'mallory@example.com')
on conflict (id) do nothing;

insert into public.dailybeat_backups (user_id, snapshot) values
    ('11111111-1111-1111-1111-111111111111', '{"owner":"alice"}'),
    ('22222222-2222-2222-2222-222222222222', '{"owner":"mallory"}')
on conflict (user_id) do update set snapshot = excluded.snapshot;

-- Become Mallory: the authenticated role plus a JWT claim carrying her uid, which is what
-- auth.uid() reads inside every policy.
set local role authenticated;
set local "request.jwt.claims" = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';

-- SELECT: Mallory sees exactly her own row, never Alice's.
select is(
    (select count(*)::int from public.dailybeat_backups),
    1,
    'a user reads only their own backup row'
);
select is(
    (select count(*)::int from public.dailybeat_backups
        where user_id = '11111111-1111-1111-1111-111111111111'),
    0,
    'another user''s row is invisible even when named directly'
);

-- UPDATE: Mallory cannot overwrite Alice's snapshot (0 rows affected, RLS filters the target).
with attempted as (
    update public.dailybeat_backups set snapshot = '{"owner":"mallory-was-here"}'
    where user_id = '11111111-1111-1111-1111-111111111111'
    returning 1
)
select is((select count(*)::int from attempted), 0, 'a user cannot update another user''s backup');

-- DELETE: Mallory cannot delete Alice's row.
with attempted as (
    delete from public.dailybeat_backups
    where user_id = '11111111-1111-1111-1111-111111111111'
    returning 1
)
select is((select count(*)::int from attempted), 0, 'a user cannot delete another user''s backup');

-- INSERT: Mallory cannot forge a row owned by Alice (with check rejects it).
select throws_ok(
    $$insert into public.dailybeat_backups (user_id, snapshot)
      values ('11111111-1111-1111-1111-111111111111', '{"forged":true}')$$,
    '42501',
    NULL,
    'a user cannot insert a row owned by someone else'
);

-- Alice's snapshot is untouched after all of Mallory's attempts.
reset role;
select is(
    (select snapshot->>'owner' from public.dailybeat_backups
        where user_id = '11111111-1111-1111-1111-111111111111'),
    'alice',
    'the target row survived every cross-user attempt intact'
);

-- ---- the size cap actually rejects an oversized snapshot ---------------------------------------
select throws_ok(
    $$insert into public.dailybeat_backups (user_id, snapshot)
      values ('33333333-3333-3333-3333-333333333333',
              jsonb_build_object('blob', repeat('x', 13000000)))$$,
    '23514',
    null,
    'a snapshot over the size cap is rejected'
);

-- An ordinary-sized snapshot is still accepted.
select lives_ok(
    $$insert into public.dailybeat_backups (user_id, snapshot)
      values ('33333333-3333-3333-3333-333333333333', '{"day":"ok"}')
      on conflict (user_id) do update set snapshot = excluded.snapshot$$,
    'a normal snapshot is accepted'
);

select * from finish();
rollback;
