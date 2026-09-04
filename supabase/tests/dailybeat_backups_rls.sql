begin;

select plan(6);

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
    'public',
    'dailybeat_backups',
    'anon',
    array[]::text[],
    'anonymous users have no backup privileges'
);
select table_privs_are(
    'public',
    'dailybeat_backups',
    'authenticated',
    array['DELETE', 'INSERT', 'SELECT', 'UPDATE'],
    'authenticated users have the required backup privileges'
);
select is(
    (
        select relrowsecurity
        from pg_class
        where oid = 'public.dailybeat_backups'::regclass
    ),
    true,
    'row-level security is active'
);

select * from finish();
rollback;
