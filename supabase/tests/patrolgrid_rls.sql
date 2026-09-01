begin;

create extension if not exists pgtap with schema extensions;

select plan(48);

insert into auth.users (
    id, aud, role, email, raw_app_meta_data, raw_user_meta_data, created_at, updated_at
) values
    ('00000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'supervisor-a@patrolgrid.test', '{}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'patrol-a1@patrolgrid.test', '{}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'patrol-a2@patrolgrid.test', '{}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'supervisor-b@patrolgrid.test', '{}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000005', 'authenticated', 'authenticated', 'patrol-b@patrolgrid.test', '{}', '{}', now(), now()),
    ('00000000-0000-0000-0000-000000000006', 'authenticated', 'authenticated', 'outsider@patrolgrid.test', '{}', '{}', now(), now());

insert into public.patrolgrid_subdivisions (id, code, name, created_by) values
    ('10000000-0000-0000-0000-000000000001', 'SUB_A', 'Subdivision A', '00000000-0000-0000-0000-000000000001'),
    ('10000000-0000-0000-0000-000000000002', 'SUB_B', 'Subdivision B', '00000000-0000-0000-0000-000000000004');

insert into public.patrolgrid_memberships (subdivision_id, user_id, role, display_name) values
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'supervisor', 'Supervisor A'),
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'patrol', 'Patrol A1'),
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'patrol', 'Patrol A2'),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004', 'supervisor', 'Supervisor B'),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005', 'patrol', 'Patrol B');

insert into public.patrolgrid_route_templates (
    id, subdivision_id, name, default_guidance, route_geojson, created_by
) values
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Route A', 'suggested_route', '{"type":"LineString","coordinates":[[77.5,13.0],[77.51,13.01]]}', '00000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Route B', 'area_coverage', '{"type":"Polygon","coordinates":[]}', '00000000-0000-0000-0000-000000000004');

insert into public.patrolgrid_route_template_priorities (
    id, route_template_id, name, latitude, longitude, sort_order
) values
    ('21000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Priority A', 13.0, 77.5, 0),
    ('21000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 'Priority B', 14.0, 78.5, 0);

insert into public.patrolgrid_units (id, subdivision_id, name, created_by) values
    ('22000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Unit A', '00000000-0000-0000-0000-000000000001'),
    ('22000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Unit B', '00000000-0000-0000-0000-000000000004');

insert into public.patrolgrid_unit_members (unit_id, user_id) values
    ('22000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002'),
    ('22000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003'),
    ('22000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005');

insert into public.patrolgrid_missions (
    id, subdivision_id, route_template_id, title, starts_at, ends_at, guidance, status, created_by
) values
    ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Mission A assigned', now() - interval '1 hour', now() + interval '7 hours', 'suggested_route', 'active', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Mission A unassigned', now() + interval '1 day', now() + interval '1 day 8 hours', 'suggested_route', 'planned', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 'Mission B assigned', now() - interval '1 hour', now() + interval '7 hours', 'area_coverage', 'active', '00000000-0000-0000-0000-000000000004');

insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values
    ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000004');

insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, app_version
) values
    ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000002', now() - interval '30 minutes', '1.0-test'),
    ('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000005', '50000000-0000-0000-0000-000000000003', now() - interval '30 minutes', '1.0-test');

insert into public.patrolgrid_track_points (
    client_point_id, session_id, mission_id, user_id, sequence_number,
    recorded_at, latitude, longitude, accuracy_m
) values
    ('60000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 0, now() - interval '20 minutes', 13.0, 77.5, 10),
    ('60000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000005', 0, now() - interval '20 minutes', 14.0, 78.5, 10);

select is(has_table_privilege('anon', 'public.patrolgrid_missions', 'SELECT'), false, 'anonymous users cannot read missions');
select is(has_table_privilege('anon', 'public.patrolgrid_track_points', 'SELECT'), false, 'anonymous users cannot read route points');
select is(has_table_privilege('authenticated', 'public.patrolgrid_audit_events', 'INSERT'), false, 'clients cannot forge audit events');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000002","role":"authenticated"}', true);

select is((select count(*) from public.patrolgrid_subdivisions), 1::bigint, 'patrol sees only its subdivision');
select is((select count(*) from public.patrolgrid_memberships), 1::bigint, 'patrol sees only its own membership');
select is((select count(*) from public.patrolgrid_missions), 1::bigint, 'patrol sees only assigned missions');
select is((select count(*) from public.patrolgrid_assignments), 1::bigint, 'patrol cannot inspect peer assignments');
select is((select count(*) from public.patrolgrid_sessions), 0::bigint, 'patrol cannot inspect a peer session');
select is((select count(*) from public.patrolgrid_track_points), 0::bigint, 'patrol cannot inspect peer route points');
select is((select count(*) from public.patrolgrid_audit_events), 0::bigint, 'patrol cannot read audit events');
select is((select count(*) from public.patrolgrid_units), 1::bigint, 'patrol sees only its own active unit');
select is((select count(*) from public.patrolgrid_route_template_priorities), 1::bigint, 'patrol sees route priorities only in its subdivision');
select throws_ok(
    $$insert into public.patrolgrid_units (subdivision_id, name, created_by) values ('10000000-0000-0000-0000-000000000001', 'Forged unit', '00000000-0000-0000-0000-000000000002')$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_units"',
    'patrol cannot create a unit'
);

select throws_ok(
    $$insert into public.patrolgrid_missions (subdivision_id, title, starts_at, ends_at, guidance, created_by) values ('10000000-0000-0000-0000-000000000001', 'Forged mission', now(), now() + interval '1 hour', 'area_coverage', '00000000-0000-0000-0000-000000000002')$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_missions"',
    'patrol cannot create a mission'
);
select throws_ok(
    $$insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002')$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_assignments"',
    'patrol cannot assign itself'
);
select throws_ok(
    $$insert into public.patrolgrid_sessions (mission_id, user_id, installation_id, started_at, app_version) values ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', now(), '1.0-test')$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_sessions"',
    'patrol cannot start an unassigned mission'
);
select throws_ok(
    $$insert into public.patrolgrid_field_updates (client_update_id, mission_id, user_id, category, detail, occurred_at) values ('70000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'observation', 'Before-session update', now())$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_field_updates"',
    'field evidence is rejected before the patrol user starts a session'
);
select lives_ok(
    $$insert into public.patrolgrid_sessions (id, mission_id, user_id, installation_id, started_at, app_version) values ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', now(), '1.0-test')$$,
    'assigned patrol can start its own session'
);
select lives_ok(
    $$insert into public.patrolgrid_track_points (client_point_id, session_id, mission_id, user_id, sequence_number, recorded_at, latitude, longitude, accuracy_m) values ('60000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 0, now(), 13.01, 77.51, 8)$$,
    'patrol can append a point to its open session'
);
select lives_ok(
    $$insert into public.patrolgrid_field_updates (client_update_id, mission_id, user_id, category, detail, occurred_at) values ('70000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'observation', 'In-session update', now())$$,
    'field evidence is accepted inside the patrol session window'
);
select throws_ok(
    $$insert into public.patrolgrid_track_points (client_point_id, session_id, mission_id, user_id, sequence_number, recorded_at, latitude, longitude, accuracy_m) values ('60000000-0000-0000-0000-000000000007', '40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 2, now() - interval '2 hours', 13.02, 77.52, 8)$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_track_points"',
    'route evidence from before the patrol session is rejected'
);
select throws_ok(
    $$insert into public.patrolgrid_track_points (client_point_id, session_id, mission_id, user_id, sequence_number, recorded_at, latitude, longitude, accuracy_m) values ('60000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 1, now(), 13.02, 77.52, 8)$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_track_points"',
    'patrol cannot append to a peer session'
);
select throws_ok(
    $$insert into public.patrolgrid_field_updates (client_update_id, mission_id, user_id, category, detail, occurred_at) values ('70000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'observation', 'Forged peer update', now())$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_field_updates"',
    'patrol cannot forge another user field update'
);
select throws_ok(
    $$insert into public.patrolgrid_track_points (client_point_id, session_id, mission_id, user_id, sequence_number, recorded_at, latitude, longitude, accuracy_m) values ('60000000-0000-0000-0000-000000000005', '40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 1, now() + interval '1 hour', 13.02, 77.52, 8)$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_track_points"',
    'future route points are rejected'
);
select lives_ok(
    $$update public.patrolgrid_sessions set ended_at = now(), end_reason = 'completed' where id = '40000000-0000-0000-0000-000000000001'$$,
    'patrol can close its own open session'
);
select throws_ok(
    $$insert into public.patrolgrid_track_points (client_point_id, session_id, mission_id, user_id, sequence_number, recorded_at, latitude, longitude, accuracy_m) values ('60000000-0000-0000-0000-000000000006', '40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 1, now(), 13.02, 77.52, 8)$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_track_points"',
    'closed sessions reject new route points'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);

select is((select count(*) from public.patrolgrid_missions), 2::bigint, 'supervisor sees all missions in its subdivision only');
select is((select count(*) from public.patrolgrid_track_points), 2::bigint, 'supervisor sees route points for its subdivision staff');
select is((select count(distinct subdivision_id) from public.patrolgrid_audit_events), 1::bigint, 'supervisor audit view is subdivision-scoped');
select is((select count(*) from public.patrolgrid_units), 1::bigint, 'supervisor sees units only in its subdivision');
select lives_ok(
    $$insert into public.patrolgrid_missions (id, subdivision_id, title, starts_at, ends_at, guidance, created_by) values ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'Supervisor-created mission', now(), now() + interval '2 hours', 'area_coverage', '00000000-0000-0000-0000-000000000001')$$,
    'supervisor can create a mission in its subdivision'
);
select throws_ok(
    $$insert into public.patrolgrid_missions (subdivision_id, title, starts_at, ends_at, guidance, created_by) values ('10000000-0000-0000-0000-000000000002', 'Cross-subdivision mission', now(), now() + interval '2 hours', 'area_coverage', '00000000-0000-0000-0000-000000000001')$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_missions"',
    'supervisor cannot create in another subdivision'
);
select lives_ok(
    $$insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001')$$,
    'supervisor can assign active patrol staff in its subdivision'
);
select throws_ok(
    $$insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001')$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_assignments"',
    'supervisor cannot assign staff from another subdivision'
);
select throws_ok(
    $$update public.patrolgrid_missions set subdivision_id = '10000000-0000-0000-0000-000000000002' where id = '30000000-0000-0000-0000-000000000001'$$,
    'P0001', 'Immutable patrol mission fields cannot be changed',
    'mission ownership fields are immutable'
);
select throws_ok(
    $$update public.patrolgrid_units set subdivision_id = '10000000-0000-0000-0000-000000000002' where id = '22000000-0000-0000-0000-000000000001'$$,
    'P0001', 'Immutable patrol unit fields cannot be changed',
    'unit ownership fields are immutable'
);
select lives_ok(
    $$update public.patrolgrid_missions set instructions = 'Check the north gate.' where id = '30000000-0000-0000-0000-000000000001'$$,
    'supervisor can update mission instructions'
);
select is((select version from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000001'), 2, 'mission update increments its version');
select lives_ok(
    $$insert into public.patrolgrid_reviews (mission_id, reviewer_id, outcome, notes) values ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'approved', 'Route evidence reviewed.')$$,
    'supervisor can record a review'
);
select lives_ok(
    $$select public.patrolgrid_create_assignment('20000000-0000-0000-0000-000000000001', '22000000-0000-0000-0000-000000000001', 'suggested_route')$$,
    'supervisor can atomically assign a route to an active unit'
);
select is(
    (
        select count(*)
        from public.patrolgrid_assignments assignment
        join public.patrolgrid_missions mission on mission.id = assignment.mission_id
        where mission.title = 'Route A' and mission.created_by = '00000000-0000-0000-0000-000000000001'
    ),
    2::bigint,
    'atomic assignment includes every active patrol member in the selected unit'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000004","role":"authenticated"}', true);
select is((select count(*) from public.patrolgrid_missions), 1::bigint, 'another supervisor cannot see subdivision A missions');

reset role;
update public.patrolgrid_memberships
set status = 'disabled'
where user_id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is((select count(*) from public.patrolgrid_missions), 0::bigint, 'disabled membership immediately revokes mission access');
select throws_ok(
    $$insert into public.patrolgrid_field_updates (client_update_id, mission_id, user_id, category, detail, occurred_at) values ('70000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'observation', 'Disabled account update', now())$$,
    '42501', 'new row violates row-level security policy for table "patrolgrid_field_updates"',
    'disabled patrol cannot submit field updates'
);

reset role;
insert into public.patrolgrid_missions (
    id, subdivision_id, title, starts_at, ends_at, guidance, status, created_by
) values (
    '30000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000001',
    'Lifecycle mission', now() - interval '1 hour', now() + interval '1 hour',
    'suggested_route', 'assigned', '00000000-0000-0000-0000-000000000001'
);
insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values (
    '30000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001'
);
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select lives_ok(
    $$insert into public.patrolgrid_sessions (id, mission_id, user_id, installation_id, started_at, app_version) values ('40000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000005', now(), '1.0-test')$$,
    'starting the first session activates its mission'
);
select is(
    (select status from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000005'),
    'active',
    'mission becomes active after patrol starts'
);
select lives_ok(
    $$update public.patrolgrid_sessions set ended_at = now(), end_reason = 'completed' where id = '40000000-0000-0000-0000-000000000005'$$,
    'patrol can close the lifecycle test session'
);
select is(
    (select status from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000005'),
    'needs_review',
    'mission waits for human review after its final open session closes'
);

select * from finish();
rollback;
