begin;

create extension if not exists pgtap with schema extensions;

select plan(274);

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

insert into public.patrolgrid_memberships (
    subdivision_id, user_id, role, display_name, badge_number
) values
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'supervisor', 'Supervisor A', 'SUP-A'),
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'patrol', 'Patrol A1', 'A-101'),
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'patrol', 'Patrol A2', 'A-102'),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004', 'supervisor', 'Supervisor B', 'SUP-B'),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005', 'patrol', 'Patrol B', 'B-201');

insert into public.patrolgrid_route_templates (
    id, subdivision_id, name, default_guidance, route_geojson, created_by
) values
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Route A', 'suggested_route', '{"type":"LineString","coordinates":[[77.5,13.0],[77.51,13.01]]}', '00000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Route B', 'area_coverage', '{"type":"Polygon","coordinates":[[[78.5,14.0],[78.51,14.0],[78.51,14.01],[78.5,14.0]]]}', '00000000-0000-0000-0000-000000000004');

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

select is(
    (
        select relation.relkind
        from pg_catalog.pg_class relation
        join pg_catalog.pg_namespace namespace
          on namespace.oid = relation.relnamespace
        where namespace.nspname = 'public'
          and relation.relname = 'patrolgrid_evidence_session_summaries'
    ),
    'v'::"char",
    'per-session evidence provenance is exposed through a view'
);
select ok(
    (
        select 'security_invoker=true' = any(coalesce(relation.reloptions, '{}'::text[]))
        from pg_catalog.pg_class relation
        join pg_catalog.pg_namespace namespace
          on namespace.oid = relation.relnamespace
        where namespace.nspname = 'public'
          and relation.relname = 'patrolgrid_evidence_session_summaries'
    ),
    'evidence provenance view executes with the caller RLS identity'
);
select is(
    (
        select array_agg(attribute.attname::text order by attribute.attnum)
        from pg_catalog.pg_attribute attribute
        join pg_catalog.pg_class relation on relation.oid = attribute.attrelid
        join pg_catalog.pg_namespace namespace on namespace.oid = relation.relnamespace
        where namespace.nspname = 'public'
          and relation.relname = 'patrolgrid_evidence_session_summaries'
          and attribute.attnum > 0
          and not attribute.attisdropped
    ),
    array[
        'session_id', 'mission_id', 'user_id', 'display_name', 'badge_number',
        'started_at', 'ended_at', 'end_reason', 'app_version', 'track_point_count',
        'first_recorded_at', 'last_recorded_at', 'first_received_at',
        'last_received_at', 'best_accuracy_m', 'worst_accuracy_m'
    ]::text[],
    'evidence provenance view has the exact reviewed source and aggregate columns'
);
select is(
    has_table_privilege('anon', 'public.patrolgrid_evidence_session_summaries', 'SELECT'),
    false,
    'anonymous clients cannot read evidence provenance'
);
select is(
    has_table_privilege('authenticated', 'public.patrolgrid_evidence_session_summaries', 'SELECT'),
    true,
    'authenticated users can read only RLS-scoped evidence provenance'
);
select is(
    has_table_privilege('authenticated', 'public.patrolgrid_evidence_session_summaries', 'INSERT')
    or has_table_privilege('authenticated', 'public.patrolgrid_evidence_session_summaries', 'UPDATE')
    or has_table_privilege('authenticated', 'public.patrolgrid_evidence_session_summaries', 'DELETE'),
    false,
    'authenticated users have no evidence-provenance write grant'
);
select is(
    has_table_privilege('service_role', 'public.patrolgrid_evidence_session_summaries', 'SELECT'),
    true,
    'service role can read the evidence-provenance view'
);
select is(
    has_table_privilege('service_role', 'public.patrolgrid_evidence_session_summaries', 'INSERT')
    or has_table_privilege('service_role', 'public.patrolgrid_evidence_session_summaries', 'UPDATE')
    or has_table_privilege('service_role', 'public.patrolgrid_evidence_session_summaries', 'DELETE'),
    false,
    'service role has no evidence-provenance write grant'
);
select is(
    (
        select coalesce(
            array_agg(
                coalesce(grantee.rolname, 'PUBLIC') || ':' || lower(acl.privilege_type)
                order by coalesce(grantee.rolname, 'PUBLIC'), acl.privilege_type
            ),
            '{}'::text[]
        )
        from pg_catalog.pg_class relation
        join pg_catalog.pg_namespace namespace on namespace.oid = relation.relnamespace
        cross join lateral pg_catalog.aclexplode(relation.relacl) acl
        left join pg_catalog.pg_roles grantee on grantee.oid = acl.grantee
        where namespace.nspname = 'public'
          and relation.relname = 'patrolgrid_evidence_session_summaries'
          and acl.grantee <> relation.relowner
    ),
    array['authenticated:select', 'service_role:select']::text[],
    'only authenticated and service_role receive an explicit client grant on provenance'
);
select ok(
    (
        select attribute.attnotnull
        from pg_catalog.pg_attribute attribute
        join pg_catalog.pg_class relation on relation.oid = attribute.attrelid
        join pg_catalog.pg_namespace namespace on namespace.oid = relation.relnamespace
        where namespace.nspname = 'public'
          and relation.relname = 'patrolgrid_priority_visits'
          and attribute.attname = 'session_id'
    ),
    'priority-visit provenance requires an exact session id'
);
select is(
    (
        select count(*)
        from pg_catalog.pg_constraint constraint_record
        join pg_catalog.pg_class relation on relation.oid = constraint_record.conrelid
        join pg_catalog.pg_namespace namespace on namespace.oid = relation.relnamespace
        where namespace.nspname = 'public'
          and relation.relname = 'patrolgrid_priority_visits'
          and constraint_record.contype = 'f'
          and constraint_record.conname in (
              'patrolgrid_priority_visits_session_source_fkey',
              'patrolgrid_priority_visits_location_mission_fkey'
          )
    ),
    2::bigint,
    'priority visits bind session/user/mission and priority/mission with database FKs'
);
select ok(
    exists (
        select 1
        from pg_catalog.pg_constraint constraint_record
        join pg_catalog.pg_class relation on relation.oid = constraint_record.conrelid
        join pg_catalog.pg_namespace namespace on namespace.oid = relation.relnamespace
        where namespace.nspname = 'public'
          and relation.relname = 'patrolgrid_track_points'
          and constraint_record.contype = 'f'
          and constraint_record.conname = 'patrolgrid_track_points_session_source_fkey'
    ),
    'track points bind their mission and person to the exact referenced session'
);
select ok(
    exists (
        select 1
        from pg_catalog.pg_indexes
        where schemaname = 'public'
          and indexname = 'patrolgrid_sessions_assignment_created'
          and indexdef like '%(mission_id, user_id, created_at DESC)%'
    )
    and exists (
        select 1
        from pg_catalog.pg_indexes
        where schemaname = 'public'
          and indexname = 'patrolgrid_track_points_assignment_count'
          and indexdef like '%(mission_id, user_id)%'
    ),
    'session and cumulative point quota checks use bounded assignment indexes'
);
select ok(
    exists (
        select 1
        from pg_catalog.pg_constraint
        where conname = 'patrolgrid_priority_visits_session_location_key'
          and contype = 'u'
    )
    and not exists (
        select 1
        from pg_catalog.pg_constraint
        where conname = 'patrolgrid_priority_visits_priority_location_id_user_id_key'
    ),
    'priority visits deduplicate per exact session instead of merging patrol sources'
);
select throws_ok(
    $$insert into public.patrolgrid_track_points (
        client_point_id, session_id, mission_id, user_id, sequence_number,
        recorded_at, latitude, longitude, accuracy_m
    ) values (
        '60000000-0000-0000-0000-000000000090',
        '40000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002',
        90, now(), 13.0, 77.5, 8
    )$$,
    '23503',
    'insert or update on table "patrolgrid_track_points" violates foreign key constraint "patrolgrid_track_points_session_source_fkey"',
    'database constraints reject a point attributed to a different person than its session'
);

select is(has_table_privilege('anon', 'public.patrolgrid_missions', 'SELECT'), false, 'anonymous users cannot read missions');
select is(has_table_privilege('anon', 'public.patrolgrid_track_points', 'SELECT'), false, 'anonymous users cannot read route points');
select is(has_table_privilege('authenticated', 'public.patrolgrid_audit_events', 'INSERT'), false, 'clients cannot forge audit events');
select is(
    has_table_privilege('authenticated', 'public.patrolgrid_sessions', 'INSERT')
    or has_table_privilege('authenticated', 'public.patrolgrid_sessions', 'UPDATE'),
    false,
    'authenticated clients cannot directly create or close patrol sessions'
);
select is(
    (
        select count(*)
        from pg_catalog.pg_policies policy
        where policy.schemaname = 'public'
          and policy.tablename = 'patrolgrid_sessions'
          and policy.cmd in ('INSERT', 'UPDATE', 'ALL')
    ),
    0::bigint,
    'patrol sessions expose no authenticated write policies'
);
select ok(
    has_function_privilege(
        'authenticated',
        'public.patrolgrid_start_session(uuid,uuid,uuid,text)',
        'EXECUTE'
    )
    and has_function_privilege(
        'authenticated',
        'public.patrolgrid_end_session(uuid,text)',
        'EXECUTE'
    ),
    'authenticated clients can execute only the narrow session lifecycle workflows'
);
select is(
    has_function_privilege(
        'anon',
        'public.patrolgrid_start_session(uuid,uuid,uuid,text)',
        'EXECUTE'
    )
    or has_function_privilege(
        'anon',
        'public.patrolgrid_end_session(uuid,text)',
        'EXECUTE'
    ),
    false,
    'anonymous clients cannot execute patrol session lifecycle workflows'
);
select is(
    (
        select bool_or(has_table_privilege('authenticated', target.table_name, 'INSERT'))
        from (
            values
                ('public.patrolgrid_track_points'),
                ('public.patrolgrid_priority_visits'),
                ('public.patrolgrid_field_updates')
        ) as target(table_name)
    ),
    false,
    'authenticated clients cannot directly insert any patrol evidence'
);
select is(
    (
        select count(*)
        from pg_catalog.pg_policies policy
        where policy.schemaname = 'public'
          and policy.tablename in (
              'patrolgrid_track_points',
              'patrolgrid_priority_visits',
              'patrolgrid_field_updates'
          )
          and policy.cmd in ('INSERT', 'ALL')
    ),
    0::bigint,
    'evidence tables expose no authenticated insert policies'
);
select ok(
    has_function_privilege('authenticated', 'public.patrolgrid_ingest_track_points(uuid,jsonb)', 'EXECUTE')
    and has_function_privilege('authenticated', 'public.patrolgrid_record_priority_visit(uuid,uuid,uuid,timestamptz,text,double precision,double precision,real,text)', 'EXECUTE')
    and has_function_privilege('authenticated', 'public.patrolgrid_record_field_update(uuid,text,text,timestamptz,uuid,uuid,double precision,double precision)', 'EXECUTE'),
    'authenticated clients can execute the narrow evidence-ingestion workflows'
);
select is(
    has_function_privilege('anon', 'public.patrolgrid_ingest_track_points(uuid,jsonb)', 'EXECUTE')
    or has_function_privilege('anon', 'public.patrolgrid_record_priority_visit(uuid,uuid,uuid,timestamptz,text,double precision,double precision,real,text)', 'EXECUTE')
    or has_function_privilege('anon', 'public.patrolgrid_record_field_update(uuid,text,text,timestamptz,uuid,uuid,double precision,double precision)', 'EXECUTE'),
    false,
    'anonymous clients cannot execute evidence-ingestion workflows'
);
select ok(
    public.patrolgrid_route_geojson_is_valid('{"type":"LineString","coordinates":[[77.5,13.0],[77.51,13.01]]}'::jsonb),
    'bounded route validator accepts a valid LineString'
);
select ok(
    public.patrolgrid_route_geojson_is_valid('{"type":"MultiLineString","coordinates":[[[77.5,13.0],[77.51,13.01]],[[77.6,13.1],[77.61,13.11]]]}'::jsonb),
    'bounded route validator accepts a valid MultiLineString'
);
select ok(
    public.patrolgrid_route_geojson_is_valid('{"type":"Polygon","coordinates":[[[77.5,13.0],[77.6,13.0],[77.6,13.1],[77.5,13.0]]]}'::jsonb),
    'bounded route validator accepts a closed Polygon'
);
select ok(
    public.patrolgrid_route_geojson_is_valid('{"type":"MultiPolygon","coordinates":[[[[77.5,13.0],[77.6,13.0],[77.6,13.1],[77.5,13.0]]]]}'::jsonb),
    'bounded route validator accepts a closed MultiPolygon'
);
select is(
    public.patrolgrid_route_geojson_is_valid('{"type":"Point","coordinates":[77.5,13.0]}'::jsonb),
    false,
    'bounded route validator rejects unsupported geometry types'
);
select is(
    public.patrolgrid_route_geojson_is_valid('{"type":"LineString","coordinates":[[181,13.0],[77.5,91]]}'::jsonb),
    false,
    'bounded route validator rejects coordinates outside geographic ranges'
);
select is(
    public.patrolgrid_route_geojson_is_valid('{"type":"MultiLineString","coordinates":[[77.5,13.0],[77.6,13.1]]}'::jsonb),
    false,
    'bounded route validator rejects malformed coordinate depth'
);
select is(
    public.patrolgrid_route_geojson_is_valid('{"type":"Polygon","coordinates":[[[77.5,13.0],[77.6,13.0],[77.6,13.1],[77.5,13.1]]]}'::jsonb),
    false,
    'bounded route validator rejects unclosed polygon rings'
);
select is(
    public.patrolgrid_route_geojson_is_valid(
        jsonb_build_object(
            'type', 'LineString',
            'coordinates', (select jsonb_agg(jsonb_build_array(77.5, 13.0)) from generate_series(1, 10001))
        )
    ),
    false,
    'bounded route validator rejects more than ten thousand positions'
);
select is(
    public.patrolgrid_route_geojson_is_valid(
        jsonb_build_object('type', repeat('L', 262144), 'coordinates', '[]'::jsonb)
    ),
    false,
    'bounded route validator rejects oversized geometry payloads'
);
select is(
    (
        select bool_or(has_table_privilege('authenticated', target.table_name, target.privilege_name))
        from (
            values
                ('public.patrolgrid_route_templates', 'INSERT'),
                ('public.patrolgrid_route_templates', 'UPDATE'),
                ('public.patrolgrid_route_templates', 'DELETE'),
                ('public.patrolgrid_route_template_priorities', 'INSERT'),
                ('public.patrolgrid_route_template_priorities', 'UPDATE'),
                ('public.patrolgrid_route_template_priorities', 'DELETE')
        ) as target(table_name, privilege_name)
    ),
    false,
    'authenticated clients have no direct route-template write privileges'
);
select is(
    (
        select bool_or(has_table_privilege('authenticated', target.table_name, target.privilege_name))
        from (
            values
                ('public.patrolgrid_units', 'INSERT'),
                ('public.patrolgrid_units', 'UPDATE'),
                ('public.patrolgrid_units', 'DELETE'),
                ('public.patrolgrid_unit_members', 'INSERT'),
                ('public.patrolgrid_unit_members', 'UPDATE'),
                ('public.patrolgrid_unit_members', 'DELETE')
        ) as target(table_name, privilege_name)
    ),
    false,
    'authenticated clients have no direct unit-management write privileges'
);
select is(
    (
        select bool_or(has_table_privilege('authenticated', target.table_name, target.privilege_name))
        from (
            values
                ('public.patrolgrid_missions', 'INSERT'),
                ('public.patrolgrid_missions', 'UPDATE'),
                ('public.patrolgrid_missions', 'DELETE'),
                ('public.patrolgrid_assignments', 'INSERT'),
                ('public.patrolgrid_assignments', 'UPDATE'),
                ('public.patrolgrid_assignments', 'DELETE'),
                ('public.patrolgrid_priority_locations', 'INSERT'),
                ('public.patrolgrid_priority_locations', 'UPDATE'),
                ('public.patrolgrid_priority_locations', 'DELETE')
        ) as target(table_name, privilege_name)
    ),
    false,
    'authenticated clients have no direct mission-control write privileges'
);
select is(
    (
        select count(*)
        from pg_catalog.pg_policies policy
        where policy.schemaname = 'public'
          and policy.tablename in (
              'patrolgrid_route_templates',
              'patrolgrid_route_template_priorities',
              'patrolgrid_units',
              'patrolgrid_unit_members',
              'patrolgrid_missions',
              'patrolgrid_assignments',
              'patrolgrid_priority_locations'
          )
          and policy.cmd in ('INSERT', 'UPDATE', 'DELETE', 'ALL')
    ),
    0::bigint,
    'control-plane tables expose no authenticated write policies'
);
select is(
    has_function_privilege(
        'authenticated',
        'public.patrolgrid_close_expired_sessions_scheduled()',
        'EXECUTE'
    ),
    false,
    'authenticated clients cannot execute the autonomous expiry worker'
);
select is(
    (
        select count(*)
        from cron.job job
        where job.jobname = 'patrolgrid-close-expired-sessions'
          and job.schedule = '*/5 * * * *'
          and job.command = 'select public.patrolgrid_close_expired_sessions_scheduled();'
          and job.active
    ),
    1::bigint,
    'autonomous expired-session closure is scheduled every five minutes'
);

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000002","role":"authenticated"}', true);

select is((select count(*) from public.patrolgrid_subdivisions), 1::bigint, 'patrol sees only its subdivision');
select is((select count(*) from public.patrolgrid_memberships), 1::bigint, 'patrol sees only its own membership');
select is((select count(*) from public.patrolgrid_missions), 1::bigint, 'patrol sees only assigned missions');
select is((select count(*) from public.patrolgrid_assignments), 1::bigint, 'patrol cannot inspect peer assignments');
select is((select count(*) from public.patrolgrid_sessions), 0::bigint, 'patrol cannot inspect a peer session');
select is((select count(*) from public.patrolgrid_track_points), 0::bigint, 'patrol cannot inspect peer route points');
select is(
    (select count(*) from public.patrolgrid_evidence_session_summaries),
    0::bigint,
    'patrol provenance view does not reveal peer or cross-subdivision sources'
);
select is((select count(*) from public.patrolgrid_audit_events), 0::bigint, 'patrol cannot read audit events');
select is((select count(*) from public.patrolgrid_units), 1::bigint, 'patrol sees only its own active unit');
select is((select count(*) from public.patrolgrid_route_templates), 0::bigint, 'patrol cannot enumerate supervisor route templates');
select is((select count(*) from public.patrolgrid_route_template_priorities), 0::bigint, 'patrol cannot enumerate supervisor route-template priorities');
select throws_ok(
    $$insert into public.patrolgrid_units (subdivision_id, name, created_by) values ('10000000-0000-0000-0000-000000000001', 'Forged unit', '00000000-0000-0000-0000-000000000002')$$,
    '42501', 'permission denied for table patrolgrid_units',
    'patrol cannot create a unit'
);

select throws_ok(
    $$insert into public.patrolgrid_missions (subdivision_id, title, starts_at, ends_at, guidance, created_by) values ('10000000-0000-0000-0000-000000000001', 'Forged mission', now(), now() + interval '1 hour', 'area_coverage', '00000000-0000-0000-0000-000000000002')$$,
    '42501', 'permission denied for table patrolgrid_missions',
    'patrol cannot create a mission'
);
select throws_ok(
    $$insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002')$$,
    '42501', 'permission denied for table patrolgrid_assignments',
    'patrol cannot assign itself'
);
select throws_ok(
    $$insert into public.patrolgrid_sessions (mission_id, user_id, installation_id, started_at, app_version) values ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', now(), '1.0-test')$$,
    '42501', 'permission denied for table patrolgrid_sessions',
    'patrol cannot bypass the start workflow with a direct session insert'
);
select throws_ok(
    $$select public.patrolgrid_start_session('40000000-0000-0000-0000-000000000020', '30000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', '1.0-test')$$,
    '42501', 'Session start is not authorized for this mission',
    'patrol cannot start an unassigned mission through the session workflow'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000003', 'observation', 'Before-session update', now(), '40000000-0000-0000-0000-000000000099')$$,
    '42501', 'Field update is not authorized for this session',
    'field evidence is rejected before the patrol user starts a session'
);
select is(
    public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        '50000000-0000-0000-0000-000000000001',
        '1.0-test'
    ),
    '40000000-0000-0000-0000-000000000001'::uuid,
    'assigned patrol starts its own session through the server workflow'
);
select ok(
    (
        select started_at between clock_timestamp() - interval '5 seconds' and clock_timestamp()
        from public.patrolgrid_sessions
        where id = '40000000-0000-0000-0000-000000000001'
    ),
    'session start uses a current server timestamp'
);
select is(
    public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        '50000000-0000-0000-0000-000000000001',
        '1.0-test'
    ),
    '40000000-0000-0000-0000-000000000001'::uuid,
    'an exact session-start retry returns the original session id'
);
select is(
    (
        select count(*)
        from public.patrolgrid_sessions
        where mission_id = '30000000-0000-0000-0000-000000000001'
          and user_id = '00000000-0000-0000-0000-000000000002'
    ),
    1::bigint,
    'session-start retries never create duplicate rows'
);
select is(
    public.patrolgrid_ingest_track_points(
        '40000000-0000-0000-0000-000000000001',
        jsonb_build_array(jsonb_build_object(
            'client_point_id', '60000000-0000-0000-0000-000000000001',
            'sequence_number', 0,
            'recorded_at', now(),
            'latitude', 13.01,
            'longitude', 77.51,
            'accuracy_m', 8
        ))
    ),
    1,
    'patrol can append a point to its open session'
);
select is(
    (select count(*) from public.patrolgrid_evidence_session_summaries),
    1::bigint,
    'patrol provenance exposes exactly its own session source'
);
select is(
    (
        select jsonb_build_object(
            'session_id', source.session_id,
            'mission_id', source.mission_id,
            'user_id', source.user_id,
            'display_name', source.display_name,
            'badge_number', source.badge_number,
            'app_version', source.app_version
        )
        from public.patrolgrid_evidence_session_summaries source
    ),
    jsonb_build_object(
        'session_id', '40000000-0000-0000-0000-000000000001'::uuid,
        'mission_id', '30000000-0000-0000-0000-000000000001'::uuid,
        'user_id', '00000000-0000-0000-0000-000000000002'::uuid,
        'display_name', 'Patrol A1',
        'badge_number', 'A-101',
        'app_version', '1.0-test'
    ),
    'patrol provenance identifies only its own exact person/session/mission source'
);
select ok(
    (
        select source.track_point_count = 1
           and source.first_recorded_at = now()
           and source.last_recorded_at = now()
           and source.first_received_at = now()
           and source.last_received_at = now()
           and source.best_accuracy_m = 8::real
           and source.worst_accuracy_m = 8::real
        from public.patrolgrid_evidence_session_summaries source
        where source.session_id = '40000000-0000-0000-0000-000000000001'
    ),
    'patrol source exposes exact point count/time/accuracy aggregates'
);
select is(
    (
        select count(*)
        from public.patrolgrid_evidence_session_summaries source
        where source.session_id in (
            '40000000-0000-0000-0000-000000000002',
            '40000000-0000-0000-0000-000000000003'
        )
    ),
    0::bigint,
    'patrol cannot infer same-mission peer or other-subdivision source rows'
);
select ok(
    public.patrolgrid_record_field_update(
        '70000000-0000-0000-0000-000000000004',
        'observation',
        'In-session update',
        now(),
        '40000000-0000-0000-0000-000000000001'
    ) is not null,
    'field evidence is accepted inside the patrol session window'
);
select is(
    public.patrolgrid_ingest_track_points(
        '40000000-0000-0000-0000-000000000001',
        (
            select jsonb_build_array(jsonb_build_object(
                'client_point_id', point.client_point_id,
                'sequence_number', point.sequence_number,
                'recorded_at', point.recorded_at,
                'latitude', point.latitude,
                'longitude', point.longitude,
                'accuracy_m', point.accuracy_m
            ))
            from public.patrolgrid_track_points point
            where point.client_point_id = '60000000-0000-0000-0000-000000000001'
        )
    ),
    0,
    'an exact track-batch retry is idempotent'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000001', (select jsonb_build_array(jsonb_build_object('client_point_id', point.client_point_id, 'sequence_number', point.sequence_number, 'recorded_at', point.recorded_at, 'latitude', point.latitude + 0.001, 'longitude', point.longitude, 'accuracy_m', point.accuracy_m)) from public.patrolgrid_track_points point where point.client_point_id = '60000000-0000-0000-0000-000000000001'))$$,
    '22023', 'Track idempotency key was reused with different evidence',
    'a track idempotency key cannot be reused with changed evidence'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000001', (select jsonb_agg(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000099', 'sequence_number', 99, 'recorded_at', now(), 'latitude', 13.01, 'longitude', 77.51, 'accuracy_m', 8)) from generate_series(1, 251)))$$,
    '22023', 'Track batch must contain between 1 and 250 points',
    'track ingestion rejects batches larger than 250 points'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000001', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000098', 'sequence_number', 98, 'recorded_at', now(), 'latitude', 91, 'longitude', 77.51, 'accuracy_m', 5001)))$$,
    '22023', 'Track point is outside accepted evidence bounds',
    'track ingestion rejects invalid coordinates and accuracy'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000001', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000097', 'sequence_number', 97, 'recorded_at', now(), 'latitude', 13.01, 'longitude', 77.51, 'accuracy_m', 8, 'unexpected', true)))$$,
    '22023', 'Track point shape or type is invalid',
    'track ingestion rejects unexpected point fields'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000099', 'observation', repeat('x', 4001), now(), '40000000-0000-0000-0000-000000000001')$$,
    '22023', 'Field update payload is invalid',
    'field ingestion rejects oversized detail payloads'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000098', 'observation', '   ', now(), '40000000-0000-0000-0000-000000000001')$$,
    '22023', 'Field update payload is invalid',
    'field ingestion rejects blank detail payloads'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000001', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000007', 'sequence_number', 2, 'recorded_at', now() - interval '2 hours', 'latitude', 13.02, 'longitude', 77.52, 'accuracy_m', 8)))$$,
    '22023', 'Track point is outside accepted evidence bounds',
    'route evidence from before the patrol session is rejected'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000002', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000004', 'sequence_number', 1, 'recorded_at', now(), 'latitude', 13.02, 'longitude', 77.52, 'accuracy_m', 8)))$$,
    '42501', 'Track ingestion is not authorized for this session',
    'patrol cannot append to a peer session'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000001', 'observation', 'Forged peer update', now(), '40000000-0000-0000-0000-000000000002')$$,
    '42501', 'Field update is not authorized for this session',
    'patrol cannot forge another user field update'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000001', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000005', 'sequence_number', 1, 'recorded_at', now() + interval '1 hour', 'latitude', 13.02, 'longitude', 77.52, 'accuracy_m', 8)))$$,
    '22023', 'Track point is outside accepted evidence bounds',
    'future route points are rejected'
);
select throws_ok(
    $$update public.patrolgrid_sessions set ended_at = now(), end_reason = 'completed' where id = '40000000-0000-0000-0000-000000000001'$$,
    '42501', 'permission denied for table patrolgrid_sessions',
    'patrol cannot bypass the end workflow with a direct session update'
);
select ok(
    public.patrolgrid_end_session(
        '40000000-0000-0000-0000-000000000001',
        'completed'
    ) between clock_timestamp() - interval '5 seconds' and clock_timestamp(),
    'patrol closes its own session with a current server timestamp'
);
select is(
    public.patrolgrid_end_session(
        '40000000-0000-0000-0000-000000000001',
        'completed'
    ),
    (
        select ended_at
        from public.patrolgrid_sessions
        where id = '40000000-0000-0000-0000-000000000001'
    ),
    'session-end retries return the original server timestamp without mutation'
);
select is(
    (
        select end_reason
        from public.patrolgrid_sessions
        where id = '40000000-0000-0000-0000-000000000001'
    ),
    'completed',
    'the session workflow stores the validated patrol end reason'
);
select throws_ok(
    $$select public.patrolgrid_end_session('40000000-0000-0000-0000-000000000002', 'completed')$$,
    '42501', 'Session closure is not authorized',
    'patrol cannot close a peer session through the server workflow'
);
select is(
    public.patrolgrid_ingest_track_points(
        '40000000-0000-0000-0000-000000000001',
        jsonb_build_array(jsonb_build_object(
            'client_point_id', '60000000-0000-0000-0000-000000000006',
            'sequence_number', 1,
            'recorded_at', now(),
            'latitude', 13.02,
            'longitude', 77.52,
            'accuracy_m', 8
        ))
    ),
    1,
    'recently closed sessions accept queued route evidence recorded inside the sealing grace'
);
select ok(
    (
        select source.track_point_count = 2
           and source.first_recorded_at = now()
           and source.last_recorded_at = now()
           and source.first_received_at = now()
           and source.last_received_at = now()
           and source.best_accuracy_m = 8::real
           and source.worst_accuracy_m = 8::real
        from public.patrolgrid_evidence_session_summaries source
        where source.session_id = '40000000-0000-0000-0000-000000000001'
    ),
    'patrol provenance aggregates queued evidence into the same exact session source'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);

select is((select count(*) from public.patrolgrid_missions), 2::bigint, 'supervisor sees all missions in its subdivision only');
select is((select count(*) from public.patrolgrid_track_points), 3::bigint, 'supervisor sees route points for its subdivision staff');
select is((select count(distinct subdivision_id) from public.patrolgrid_audit_events), 1::bigint, 'supervisor audit view is subdivision-scoped');
select is((select count(*) from public.patrolgrid_units), 1::bigint, 'supervisor sees units only in its subdivision');
select is((select count(*) from public.patrolgrid_route_templates), 1::bigint, 'supervisor sees route templates in its subdivision');
select is((select count(*) from public.patrolgrid_route_template_priorities), 1::bigint, 'supervisor sees route-template priorities for assignment planning');
select is(
    (select count(*) from public.patrolgrid_evidence_session_summaries),
    2::bigint,
    'supervisor sees each same-subdivision patrol session as a distinct source'
);
select is(
    (
        select jsonb_agg(
            jsonb_build_object(
                'session_id', source.session_id,
                'user_id', source.user_id,
                'display_name', source.display_name,
                'badge_number', source.badge_number,
                'track_point_count', source.track_point_count
            )
            order by source.session_id
        )
        from public.patrolgrid_evidence_session_summaries source
    ),
    jsonb_build_array(
        jsonb_build_object(
            'session_id', '40000000-0000-0000-0000-000000000001'::uuid,
            'user_id', '00000000-0000-0000-0000-000000000002'::uuid,
            'display_name', 'Patrol A1',
            'badge_number', 'A-101',
            'track_point_count', 2
        ),
        jsonb_build_object(
            'session_id', '40000000-0000-0000-0000-000000000002'::uuid,
            'user_id', '00000000-0000-0000-0000-000000000003'::uuid,
            'display_name', 'Patrol A2',
            'badge_number', 'A-102',
            'track_point_count', 1
        )
    ),
    'supervisor provenance never merges two patrol people or session ids into one trail'
);
select ok(
    (
        select source.track_point_count = 2
           and source.first_recorded_at = now()
           and source.last_recorded_at = now()
           and source.first_received_at = now()
           and source.last_received_at = now()
           and source.best_accuracy_m = 8::real
           and source.worst_accuracy_m = 8::real
           and source.started_at = session.started_at
           and source.ended_at = session.ended_at
           and source.end_reason = session.end_reason
        from public.patrolgrid_evidence_session_summaries source
        join public.patrolgrid_sessions session on session.id = source.session_id
        where source.session_id = '40000000-0000-0000-0000-000000000001'
    ),
    'supervisor sees exact aggregates and lifecycle provenance for patrol A1'
);
select ok(
    (
        select source.track_point_count = 1
           and source.first_recorded_at = now() - interval '20 minutes'
           and source.last_recorded_at = now() - interval '20 minutes'
           and source.first_received_at = now()
           and source.last_received_at = now()
           and source.best_accuracy_m = 10::real
           and source.worst_accuracy_m = 10::real
           and source.started_at = now() - interval '30 minutes'
           and source.ended_at is null
           and source.end_reason is null
        from public.patrolgrid_evidence_session_summaries source
        where source.session_id = '40000000-0000-0000-0000-000000000002'
    ),
    'supervisor sees exact received/recorded/accuracy aggregates for patrol A2'
);
select is(
    (
        select count(*)
        from public.patrolgrid_evidence_session_summaries source
        where source.session_id = '40000000-0000-0000-0000-000000000003'
    ),
    0::bigint,
    'subdivision A supervisor cannot see subdivision B evidence sources'
);
select throws_ok(
    $$insert into public.patrolgrid_missions (id, subdivision_id, title, starts_at, ends_at, guidance, created_by) values ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'Supervisor-created mission', now(), now() + interval '2 hours', 'area_coverage', '00000000-0000-0000-0000-000000000001')$$,
    '42501', 'permission denied for table patrolgrid_missions',
    'supervisor mission creation requires a narrow server workflow'
);
select throws_ok(
    $$insert into public.patrolgrid_missions (subdivision_id, title, starts_at, ends_at, guidance, created_by) values ('10000000-0000-0000-0000-000000000002', 'Cross-subdivision mission', now(), now() + interval '2 hours', 'area_coverage', '00000000-0000-0000-0000-000000000001')$$,
    '42501', 'permission denied for table patrolgrid_missions',
    'supervisor cannot bypass mission workflows for another subdivision'
);
select throws_ok(
    $$insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001')$$,
    '42501', 'permission denied for table patrolgrid_assignments',
    'supervisor direct assignment writes require the atomic assignment RPC'
);
select throws_ok(
    $$insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001')$$,
    '42501', 'permission denied for table patrolgrid_assignments',
    'supervisor cannot bypass assignment workflows for another subdivision'
);
select throws_ok(
    $$update public.patrolgrid_missions set subdivision_id = '10000000-0000-0000-0000-000000000002' where id = '30000000-0000-0000-0000-000000000001'$$,
    '42501', 'permission denied for table patrolgrid_missions',
    'supervisor cannot directly mutate mission ownership'
);
select throws_ok(
    $$update public.patrolgrid_units set subdivision_id = '10000000-0000-0000-0000-000000000002' where id = '22000000-0000-0000-0000-000000000001'$$,
    '42501', 'permission denied for table patrolgrid_units',
    'supervisor cannot directly mutate unit ownership'
);
select throws_ok(
    $$update public.patrolgrid_missions set instructions = 'Check the north gate.' where id = '30000000-0000-0000-0000-000000000001'$$,
    '42501', 'permission denied for table patrolgrid_missions',
    'supervisor mission edits require a narrow server workflow'
);
select is((select version from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000001'), 1, 'denied direct edits leave the mission version unchanged');
select throws_ok(
    $$insert into public.patrolgrid_reviews (mission_id, reviewer_id, outcome, notes) values ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'approved', 'Route evidence reviewed.')$$,
    '42501', 'permission denied for table patrolgrid_reviews',
    'supervisor review inserts must use the atomic review RPC'
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
select is(
    (
        select mission.route_geojson
        from public.patrolgrid_missions mission
        where mission.title = 'Route A'
          and mission.created_by = '00000000-0000-0000-0000-000000000001'
    ),
    (
        select route.route_geojson
        from public.patrolgrid_route_templates route
        where route.id = '20000000-0000-0000-0000-000000000001'
    ),
    'atomic assignment copies the selected route geometry into the mission snapshot'
);
select throws_ok(
    $$update public.patrolgrid_missions set route_geojson = '{"type":"LineString","coordinates":[[0,0],[1,1]]}'::jsonb where title = 'Route A' and created_by = '00000000-0000-0000-0000-000000000001'$$,
    '42501', 'permission denied for table patrolgrid_missions',
    'an assigned mission route snapshot cannot be directly changed'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000004","role":"authenticated"}', true);
select is((select count(*) from public.patrolgrid_missions), 1::bigint, 'another supervisor cannot see subdivision A missions');
select is(
    (
        select jsonb_agg(
            jsonb_build_object(
                'session_id', source.session_id,
                'user_id', source.user_id,
                'display_name', source.display_name,
                'badge_number', source.badge_number,
                'track_point_count', source.track_point_count,
                'first_recorded_at', source.first_recorded_at,
                'last_recorded_at', source.last_recorded_at,
                'first_received_at', source.first_received_at,
                'last_received_at', source.last_received_at,
                'best_accuracy_m', source.best_accuracy_m,
                'worst_accuracy_m', source.worst_accuracy_m
            )
            order by source.session_id
        )
        from public.patrolgrid_evidence_session_summaries source
    ),
    jsonb_build_array(jsonb_build_object(
        'session_id', '40000000-0000-0000-0000-000000000003'::uuid,
        'user_id', '00000000-0000-0000-0000-000000000005'::uuid,
        'display_name', 'Patrol B',
        'badge_number', 'B-201',
        'track_point_count', 1,
        'first_recorded_at', now() - interval '20 minutes',
        'last_recorded_at', now() - interval '20 minutes',
        'first_received_at', now(),
        'last_received_at', now(),
        'best_accuracy_m', 10::real,
        'worst_accuracy_m', 10::real
    )),
    'subdivision B supervisor sees only its exact source and aggregates'
);
select is(
    (
        select count(*)
        from public.patrolgrid_evidence_session_summaries source
        where source.session_id in (
            '40000000-0000-0000-0000-000000000001',
            '40000000-0000-0000-0000-000000000002'
        )
    ),
    0::bigint,
    'subdivision B supervisor cannot see subdivision A evidence sources'
);

reset role;
update public.patrolgrid_memberships
set status = 'disabled'
where user_id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is((select count(*) from public.patrolgrid_missions), 0::bigint, 'disabled membership immediately revokes mission access');
select is(
    (select count(*) from public.patrolgrid_evidence_session_summaries),
    0::bigint,
    'disabled membership immediately revokes evidence-source provenance access'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000002', 'observation', 'Disabled account update', now(), '40000000-0000-0000-0000-000000000001')$$,
    '42501', 'Field update is not authorized for this session',
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
select is(
    public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000005',
        '30000000-0000-0000-0000-000000000005',
        '50000000-0000-0000-0000-000000000005',
        '1.0-test'
    ),
    '40000000-0000-0000-0000-000000000005'::uuid,
    'starting the first session through the workflow activates its mission'
);
select is(
    (select status from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000005'),
    'active',
    'mission becomes active after patrol starts'
);
select ok(
    public.patrolgrid_end_session(
        '40000000-0000-0000-0000-000000000005',
        'completed'
    ) is not null,
    'patrol closes the lifecycle test session through the server workflow'
);
select is(
    (select status from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000005'),
    'needs_review',
    'mission waits for human review after its final open session closes'
);

reset role;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
insert into public.patrolgrid_reviews (
    id, mission_id, reviewer_id, outcome, notes, reviewed_at, created_at
) values (
    '80000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000001',
    'needs_context',
    'Historical request used to verify exact review linkage.',
    now() - interval '1 day',
    now() - interval '1 day'
);
insert into public.patrolgrid_missions (
    id, subdivision_id, title, starts_at, ends_at, guidance, status, created_by
) values (
    '30000000-0000-0000-0000-000000000006',
    '10000000-0000-0000-0000-000000000001',
    'Expired context request mission',
    now() - interval '32 days',
    now() - interval '31 days',
    'area_coverage',
    'needs_review',
    '00000000-0000-0000-0000-000000000001'
);
insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values (
    '30000000-0000-0000-0000-000000000006',
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001'
);
insert into public.patrolgrid_reviews (
    id, mission_id, reviewer_id, outcome, notes, reviewed_at, created_at
) values (
    '80000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000006',
    '00000000-0000-0000-0000-000000000001',
    'needs_context',
    'Expired request used to verify the server-time response cap.',
    now() - interval '31 days',
    now() - interval '31 days'
);
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000011', 'review_context', 'Response after request expiry.', now(), null, '80000000-0000-0000-0000-000000000002')$$,
    '22023', 'Mission no longer accepts this review context',
    'review-context requests expire after the thirty-day response window'
);
select throws_ok(
    $$select public.patrolgrid_submit_review('30000000-0000-0000-0000-000000000005', 3, 'approved', 'Patrol users cannot review missions.')$$,
    'P0001', 'PatrolGrid supervisor access required',
    'patrol personnel cannot submit a supervisor review'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000004","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_submit_review('30000000-0000-0000-0000-000000000005', 3, 'approved', 'Cross-subdivision review.')$$,
    'P0001', 'PatrolGrid supervisor access required',
    'a supervisor cannot review another subdivision mission'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_submit_review('30000000-0000-0000-0000-000000000005', 2, 'approved', 'Stale review.')$$,
    'P0001', 'Mission version conflict',
    'a stale mission version cannot submit a review'
);
select is(
    public.patrolgrid_submit_review(
        '30000000-0000-0000-0000-000000000005',
        3,
        'needs_context',
        'Confirm the reason for the route deviation.'
    ),
    4,
    'requesting context returns the incremented mission version'
);
select is(
    (select status from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000005'),
    'needs_review',
    'requesting context keeps the mission open for review'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select throws_ok(
    $$insert into public.patrolgrid_field_updates (client_update_id, mission_id, user_id, review_id, category, detail, occurred_at) values ('70000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', (select review.id from public.patrolgrid_reviews review where review.mission_id = '30000000-0000-0000-0000-000000000005' order by review.reviewed_at desc, review.created_at desc, review.id desc limit 1), 'review_context', 'Forged peer response.', now())$$,
    '42501', 'permission denied for table patrolgrid_field_updates',
    'assigned patrol cannot bypass ingestion to forge another user context response'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000009', 'review_context', 'Missing review link.', now())$$,
    '22023', 'Review context requires only the supervisor review link',
    'context responses require an explicit supervisor review link'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000010', 'review_context', 'Response to stale request.', now(), null, '80000000-0000-0000-0000-000000000001')$$,
    '22023', 'Mission no longer accepts this review context',
    'context responses cannot target an older needs-context request'
);
select ok(
    public.patrolgrid_record_field_update(
        '70000000-0000-0000-0000-000000000006',
        'review_context',
        'Road closure required the documented deviation.',
        now(),
        null,
        (select review.id from public.patrolgrid_reviews review where review.mission_id = '30000000-0000-0000-0000-000000000005' order by review.reviewed_at desc, review.created_at desc, review.id desc limit 1)
    ) is not null,
    'assigned patrol can answer the latest needs-context review after its session closes'
);
select is(
    (
        select count(*)
        from public.patrolgrid_field_updates update_record
        where update_record.mission_id = '30000000-0000-0000-0000-000000000005'
          and update_record.user_id = '00000000-0000-0000-0000-000000000003'
          and update_record.category = 'review_context'
    ),
    1::bigint,
    'the patrol context response is retained as mission evidence'
);
select is(
    (
        select update_record.review_id
        from public.patrolgrid_field_updates update_record
        where update_record.client_update_id = '70000000-0000-0000-0000-000000000006'
    ),
    (
        select review.id
        from public.patrolgrid_reviews review
        where review.mission_id = '30000000-0000-0000-0000-000000000005'
        order by review.reviewed_at desc, review.created_at desc, review.id desc
        limit 1
    ),
    'the context evidence stores the exact latest supervisor request id'
);
select is(
    (select version from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000005'),
    5,
    'a context response increments the mission evidence version'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000005","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000007', 'review_context', 'Cross-subdivision response.', now(), null, '80000000-0000-0000-0000-000000000001')$$,
    '42501', 'Review context is not authorized',
    'patrol from another subdivision cannot answer the review'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_submit_review('30000000-0000-0000-0000-000000000005', 4, 'approved', 'Stale approval after context arrived.')$$,
    'P0001', 'Mission version conflict',
    'context evidence makes the supervisor review version stale'
);
select is(
    public.patrolgrid_submit_review(
        '30000000-0000-0000-0000-000000000005',
        5,
        'approved',
        'Context received and route evidence reviewed.'
    ),
    6,
    'approving a mission returns the incremented mission version'
);
select is(
    (select status from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000005'),
    'completed',
    'approval closes the mission'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000008', 'review_context', 'Late response after approval.', now(), null, (select id from public.patrolgrid_reviews where mission_id = '30000000-0000-0000-0000-000000000005' order by reviewed_at desc, created_at desc, id desc limit 1))$$,
    '22023', 'Mission no longer accepts this review context',
    'completed missions reject additional context responses'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select is(
    (
        select count(*)
        from public.patrolgrid_reviews review
        where review.mission_id = '30000000-0000-0000-0000-000000000005'
          and review.reviewer_id = '00000000-0000-0000-0000-000000000001'
    ),
    3::bigint,
    'review history retains the historical request and both atomic outcomes'
);
select is(
    (
        select count(*)
        from public.patrolgrid_audit_events audit
        where audit.mission_id = '30000000-0000-0000-0000-000000000005'
          and audit.actor_id = '00000000-0000-0000-0000-000000000001'
          and audit.event_type = 'patrolgrid_reviews.insert'
    ),
    3::bigint,
    'each atomic review creates a supervisor-attributed audit event'
);
select is(
    (
        select count(*)
        from public.patrolgrid_audit_events audit
        where audit.mission_id = '30000000-0000-0000-0000-000000000005'
          and audit.actor_id = '00000000-0000-0000-0000-000000000003'
          and audit.event_type = 'patrolgrid_field_updates.insert'
    ),
    1::bigint,
    'the accepted context response creates a patrol-attributed audit event'
);

reset role;
insert into public.patrolgrid_missions (
    id, subdivision_id, title, starts_at, ends_at, guidance, status, created_by
) values
    (
        '30000000-0000-0000-0000-000000000007',
        '10000000-0000-0000-0000-000000000001',
        'Future duty window', now() + interval '1 hour', now() + interval '3 hours',
        'area_coverage', 'assigned', '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000008',
        '10000000-0000-0000-0000-000000000001',
        'Expired subdivision A duty', now() - interval '2 hours', now() - interval '1 hour',
        'area_coverage', 'active', '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000009',
        '10000000-0000-0000-0000-000000000002',
        'Expired subdivision B duty', now() - interval '2 hours', now() - interval '1 hour',
        'area_coverage', 'active', '00000000-0000-0000-0000-000000000004'
    ),
    (
        '30000000-0000-0000-0000-000000000010',
        '10000000-0000-0000-0000-000000000001',
        'Closed duty window', now() - interval '2 hours', now() - interval '1 hour',
        'area_coverage', 'assigned', '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000011',
        '10000000-0000-0000-0000-000000000001',
        'Sealed upload window', now() - interval '27 hours', now() - interval '26 hours',
        'area_coverage', 'completed', '00000000-0000-0000-0000-000000000001'
    );

insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values
    ('30000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000004'),
    ('30000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001');

insert into public.patrolgrid_priority_locations (
    id, mission_id, name, latitude, longitude, sort_order
) values
    (
        '21000000-0000-0000-0000-000000000008',
        '30000000-0000-0000-0000-000000000008',
        'Expired duty priority', 13.1, 77.6, 0
    ),
    (
        '21000000-0000-0000-0000-000000000011',
        '30000000-0000-0000-0000-000000000011',
        'Sealed duty priority', 13.2, 77.7, 0
    );

insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, app_version
) values
    (
        '40000000-0000-0000-0000-000000000007',
        '30000000-0000-0000-0000-000000000008',
        '00000000-0000-0000-0000-000000000003',
        '50000000-0000-0000-0000-000000000007',
        now() - interval '2 hours', '1.0-test'
    ),
    (
        '40000000-0000-0000-0000-000000000008',
        '30000000-0000-0000-0000-000000000008',
        '00000000-0000-0000-0000-000000000002',
        '50000000-0000-0000-0000-000000000008',
        now() - interval '2 hours', '1.0-test'
    ),
    (
        '40000000-0000-0000-0000-000000000009',
        '30000000-0000-0000-0000-000000000009',
        '00000000-0000-0000-0000-000000000005',
        '50000000-0000-0000-0000-000000000009',
        now() - interval '2 hours', '1.0-test'
    );

insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at,
    ended_at, end_reason, app_version
) values (
    '40000000-0000-0000-0000-000000000012',
    '30000000-0000-0000-0000-000000000011',
    '00000000-0000-0000-0000-000000000003',
    '50000000-0000-0000-0000-000000000012',
    now() - interval '27 hours',
    now() - interval '25 hours 55 minutes',
    'duty_window_ended',
    '1.0-test'
);

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_start_session('40000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000007', '50000000-0000-0000-0000-000000000010', '1.0-test')$$,
    '22023', 'Mission duty window is not open',
    'patrol cannot start a session more than fifteen minutes before duty'
);
select throws_ok(
    $$select public.patrolgrid_start_session('40000000-0000-0000-0000-000000000011', '30000000-0000-0000-0000-000000000010', '50000000-0000-0000-0000-000000000011', '1.0-test')$$,
    '22023', 'Mission duty window is not open',
    'patrol cannot start a session after the duty window ends'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000007', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000008', 'sequence_number', 0, 'recorded_at', (select ends_at + interval '6 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'), 'latitude', 13.1, 'longitude', 77.6, 'accuracy_m', 8)))$$,
    '22023', 'Track point is outside accepted evidence bounds',
    'route evidence recorded after the five-minute duty grace is rejected'
);
select throws_ok(
    $$select public.patrolgrid_record_priority_visit('40000000-0000-0000-0000-000000000007', '61000000-0000-0000-0000-000000000008', '21000000-0000-0000-0000-000000000008', (select ends_at + interval '6 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'), 'manual_with_context')$$,
    '22023', 'Priority visit is outside accepted evidence bounds',
    'priority visits recorded after the five-minute duty grace are rejected'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000012', 'observation', 'Late normal field update.', (select ends_at + interval '6 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'), '40000000-0000-0000-0000-000000000007')$$,
    '22023', 'Field update is outside accepted evidence bounds',
    'normal field updates recorded after the five-minute duty grace are rejected'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000012', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000011', 'sequence_number', 0, 'recorded_at', (select ends_at - interval '1 minute' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000011'), 'latitude', 13.2, 'longitude', 77.7, 'accuracy_m', 8)))$$,
    '22023', 'The sealed track upload window has ended',
    'sealed route evidence cannot be uploaded after the twenty-four-hour window'
);
select throws_ok(
    $$select public.patrolgrid_record_priority_visit('40000000-0000-0000-0000-000000000012', '61000000-0000-0000-0000-000000000011', '21000000-0000-0000-0000-000000000011', (select ends_at - interval '1 minute' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000011'), 'manual_with_context')$$,
    '22023', 'Priority visit is outside accepted evidence bounds',
    'sealed priority evidence cannot be uploaded after the twenty-four-hour window'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000013', 'observation', 'Backdated sealed observation.', (select ends_at - interval '1 minute' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000011'), '40000000-0000-0000-0000-000000000012')$$,
    '22023', 'Field update is outside accepted evidence bounds',
    'sealed normal updates cannot be uploaded after the twenty-four-hour window'
);
select is(
    public.patrolgrid_close_expired_sessions(),
    1,
    'patrol expiration refresh closes only its own expired session'
);
select is(
    (select end_reason from public.patrolgrid_sessions where id = '40000000-0000-0000-0000-000000000007'),
    'duty_window_ended',
    'patrol expiration closure records the duty-window end reason'
);
select is(
    (select ended_at from public.patrolgrid_sessions where id = '40000000-0000-0000-0000-000000000007'),
    (select ends_at + interval '5 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'),
    'patrol expiration closure uses the server duty cutoff'
);
select is(
    public.patrolgrid_ingest_track_points(
        '40000000-0000-0000-0000-000000000007',
        jsonb_build_array(jsonb_build_object(
            'client_point_id', '60000000-0000-0000-0000-000000000012',
            'sequence_number', 0,
            'recorded_at', (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'),
            'latitude', 13.1,
            'longitude', 77.6,
            'accuracy_m', 8
        ))
    ),
    1,
    'recently sealed route evidence can finish uploading after auto-close'
);
select is(
    public.patrolgrid_record_priority_visit(
        '40000000-0000-0000-0000-000000000007',
        '61000000-0000-0000-0000-000000000012',
        '21000000-0000-0000-0000-000000000008',
        (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'),
        'manual_with_context'
    ),
    '61000000-0000-0000-0000-000000000012'::uuid,
    'recently sealed priority evidence can finish uploading after auto-close'
);
select is(
    (
        select visit.session_id
        from public.patrolgrid_priority_visits visit
        where visit.id = '61000000-0000-0000-0000-000000000012'
    ),
    '40000000-0000-0000-0000-000000000007'::uuid,
    'priority-visit RPC persists the exact selected patrol session provenance'
);
select ok(
    public.patrolgrid_record_field_update(
        '70000000-0000-0000-0000-000000000014',
        'observation',
        'Queued evidence from before sealing.',
        (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'),
        '40000000-0000-0000-0000-000000000007'
    ) is not null,
    'recently sealed normal updates can finish uploading after auto-close'
);
select is(
    public.patrolgrid_ingest_track_points(
        '40000000-0000-0000-0000-000000000007',
        jsonb_build_array(jsonb_build_object(
            'client_point_id', '60000000-0000-0000-0000-000000000012',
            'sequence_number', 0,
            'recorded_at', (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'),
            'latitude', 13.1,
            'longitude', 77.6,
            'accuracy_m', 8
        ))
    ),
    0,
    'sealed route retries are idempotent'
);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000007', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000012', 'sequence_number', 0, 'recorded_at', (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'), 'latitude', 13.2, 'longitude', 77.6, 'accuracy_m', 8)))$$,
    '22023', 'Track idempotency key was reused with different evidence',
    'sealed route retries cannot alter evidence'
);
select is(
    public.patrolgrid_record_priority_visit(
        '40000000-0000-0000-0000-000000000007',
        '61000000-0000-0000-0000-000000000012',
        '21000000-0000-0000-0000-000000000008',
        (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'),
        'manual_with_context'
    ),
    '61000000-0000-0000-0000-000000000012'::uuid,
    'priority-visit retries are idempotent'
);
select throws_ok(
    $$select public.patrolgrid_record_priority_visit('40000000-0000-0000-0000-000000000007', '61000000-0000-0000-0000-000000000012', '21000000-0000-0000-0000-000000000008', (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'), 'manual_with_context', null, null, null, 'changed evidence')$$,
    '22023', 'Visit idempotency key was reused with different evidence',
    'priority-visit retries cannot alter evidence'
);
select throws_ok(
    $$select public.patrolgrid_record_priority_visit('40000000-0000-0000-0000-000000000012', '61000000-0000-0000-0000-000000000012', '21000000-0000-0000-0000-000000000011', (select ends_at - interval '1 minute' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000011'), 'manual_with_context')$$,
    '22023', 'Visit idempotency key was reused with different evidence',
    'a priority-visit UUID cannot be replayed as evidence from another patrol session'
);

reset role;
select throws_ok(
    $$insert into public.patrolgrid_priority_visits (
        id, session_id, priority_location_id, mission_id, user_id,
        visited_at, method
    ) values (
        '61000000-0000-0000-0000-000000000090',
        '40000000-0000-0000-0000-000000000008',
        '21000000-0000-0000-0000-000000000008',
        '30000000-0000-0000-0000-000000000008',
        '00000000-0000-0000-0000-000000000003',
        (select ends_at + interval '4 minutes' from public.patrolgrid_missions
         where id = '30000000-0000-0000-0000-000000000008'),
        'manual_with_context'
    )$$,
    '23503',
    'insert or update on table "patrolgrid_priority_visits" violates foreign key constraint "patrolgrid_priority_visits_session_source_fkey"',
    'database constraints reject a priority visit attributed to another session person'
);
select lives_ok(
    $$insert into public.patrolgrid_priority_visits (
        id, session_id, priority_location_id, mission_id, user_id, visited_at,
        method, latitude, longitude, accuracy_m, note
    ) values (
        '61000000-0000-0000-0000-000000000013',
        '40000000-0000-0000-0000-000000000008',
        '21000000-0000-0000-0000-000000000008',
        '30000000-0000-0000-0000-000000000008',
        '00000000-0000-0000-0000-000000000002',
        (select ends_at + interval '4 minutes' from public.patrolgrid_missions
         where id = '30000000-0000-0000-0000-000000000008'),
        'manual_with_context', null, null, null, 'Independent session visit'
    )$$,
    'the same priority remains distinct evidence when visited by another exact session source'
);

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select is(
    (
        select count(*)
        from public.patrolgrid_priority_visits visit
        where visit.priority_location_id = '21000000-0000-0000-0000-000000000008'
    ),
    1::bigint,
    'patrol RLS reveals only its own priority-visit session source'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select is(
    (
        select array_agg(visit.session_id order by visit.session_id)
        from public.patrolgrid_priority_visits visit
        where visit.priority_location_id = '21000000-0000-0000-0000-000000000008'
    ),
    array[
        '40000000-0000-0000-0000-000000000007'::uuid,
        '40000000-0000-0000-0000-000000000008'::uuid
    ],
    'supervisor sees same-subdivision priority visits as two distinct session sources'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select is(
    public.patrolgrid_record_field_update(
        '70000000-0000-0000-0000-000000000014',
        'observation',
        'Queued evidence from before sealing.',
        (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'),
        '40000000-0000-0000-0000-000000000007'
    ),
    (
        select update_record.id
        from public.patrolgrid_field_updates update_record
        where update_record.client_update_id = '70000000-0000-0000-0000-000000000014'
    ),
    'field-update retries are idempotent'
);
select throws_ok(
    $$select public.patrolgrid_record_field_update('70000000-0000-0000-0000-000000000014', 'observation', 'Changed queued evidence.', (select ends_at + interval '4 minutes' from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'), '40000000-0000-0000-0000-000000000007')$$,
    '22023', 'Field-update idempotency key was reused with different evidence',
    'field-update retries cannot alter evidence'
);

reset role;
select is(
    (
        select count(distinct audit.event_type)
        from public.patrolgrid_audit_events audit
        where audit.mission_id = '30000000-0000-0000-0000-000000000008'
          and audit.actor_id = '00000000-0000-0000-0000-000000000003'
          and audit.event_type in (
              'patrolgrid.track_batch_ingested',
              'patrolgrid.priority_visit_ingested',
              'patrolgrid.field_update_ingested'
          )
    ),
    3::bigint,
    'each evidence workflow emits a structured batch summary audit'
);
select is(
    exists (
        select 1
        from public.patrolgrid_audit_events audit
        where audit.event_type in (
              'patrolgrid.track_batch_ingested',
              'patrolgrid.priority_visit_ingested',
              'patrolgrid.field_update_ingested'
          )
          and (audit.payload ? 'latitude' or audit.payload ? 'longitude')
    ),
    false,
    'structured evidence audits never retain raw coordinates'
);
select is(
    (select ended_at from public.patrolgrid_sessions where id = '40000000-0000-0000-0000-000000000008'),
    null::timestamptz,
    'patrol expiration refresh cannot close a peer session'
);
select is(
    (
        select count(*)
        from public.patrolgrid_audit_events audit
        where audit.mission_id = '30000000-0000-0000-0000-000000000008'
          and audit.actor_id = '00000000-0000-0000-0000-000000000003'
          and audit.event_type = 'patrolgrid.expired_session_closed'
    ),
    1::bigint,
    'patrol expiration closure creates a dedicated audit event'
);

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000004","role":"authenticated"}', true);
select is(
    public.patrolgrid_close_expired_sessions(),
    1,
    'supervisor expiration refresh closes expired sessions in its subdivision'
);

reset role;
select is(
    (select ended_at from public.patrolgrid_sessions where id = '40000000-0000-0000-0000-000000000008'),
    null::timestamptz,
    'another subdivision supervisor cannot close subdivision A sessions'
);
select is(
    (select end_reason from public.patrolgrid_sessions where id = '40000000-0000-0000-0000-000000000009'),
    'duty_window_ended',
    'subdivision B supervisor closure records the duty-window end reason'
);

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select is(
    public.patrolgrid_close_expired_sessions(),
    1,
    'supervisor expiration refresh closes a peer expired session in its subdivision'
);
select is(
    public.patrolgrid_close_expired_sessions(),
    0,
    'expiration refresh is idempotent after eligible sessions are closed'
);
select is(
    (select end_reason from public.patrolgrid_sessions where id = '40000000-0000-0000-0000-000000000008'),
    'duty_window_ended',
    'supervisor peer closure records the duty-window end reason'
);
select is(
    (select status from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'),
    'needs_review',
    'mission moves to human review after every expired session is closed'
);
select is(
    (
        select count(*)
        from public.patrolgrid_audit_events audit
        where audit.mission_id = '30000000-0000-0000-0000-000000000008'
          and audit.actor_id = '00000000-0000-0000-0000-000000000001'
          and audit.event_type = 'patrolgrid.expired_session_closed'
    ),
    1::bigint,
    'supervisor expiration closure creates a dedicated audit event'
);

reset role;
insert into public.patrolgrid_missions (
    id, subdivision_id, title, starts_at, ends_at, guidance, status, created_by
) values (
    '30000000-0000-0000-0000-000000000012',
    '10000000-0000-0000-0000-000000000001',
    'Scheduler expiry mission', now() - interval '2 hours', now() - interval '1 hour',
    'area_coverage', 'active', '00000000-0000-0000-0000-000000000001'
);
insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values (
    '30000000-0000-0000-0000-000000000012',
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001'
);
insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, app_version
) values (
    '40000000-0000-0000-0000-000000000013',
    '30000000-0000-0000-0000-000000000012',
    '00000000-0000-0000-0000-000000000003',
    '50000000-0000-0000-0000-000000000013',
    now() - interval '2 hours',
    '1.0-test'
);
select is(
    public.patrolgrid_close_expired_sessions_scheduled(),
    1,
    'autonomous worker closes expired sessions without a client identity'
);
select is(
    (select end_reason from public.patrolgrid_sessions where id = '40000000-0000-0000-0000-000000000013'),
    'duty_window_ended',
    'autonomous closure records the duty-window end reason'
);
select is(
    (
        select count(*)
        from public.patrolgrid_audit_events audit
        where audit.mission_id = '30000000-0000-0000-0000-000000000012'
          and audit.actor_id is null
          and audit.event_type = 'patrolgrid.expired_session_closed'
          and audit.payload ->> 'source' = 'scheduler'
          and audit.payload ->> 'session_id' = '40000000-0000-0000-0000-000000000013'
    ),
    1::bigint,
    'autonomous closure audit records a null actor and scheduler source'
);

insert into public.patrolgrid_track_points (
    client_point_id, session_id, mission_id, user_id, sequence_number,
    recorded_at, latitude, longitude, accuracy_m
)
select
    md5('patrolgrid-point-limit-' || series.value::text)::uuid,
    '40000000-0000-0000-0000-000000000007',
    '30000000-0000-0000-0000-000000000008',
    '00000000-0000-0000-0000-000000000003',
    series.value,
    mission.ends_at,
    13.1,
    77.6,
    8
from generate_series(1, 19999) as series(value)
cross join public.patrolgrid_missions mission
where mission.id = '30000000-0000-0000-0000-000000000008';

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points('40000000-0000-0000-0000-000000000007', jsonb_build_array(jsonb_build_object('client_point_id', '60000000-0000-0000-0000-000000000096', 'sequence_number', 20000, 'recorded_at', (select ends_at from public.patrolgrid_missions where id = '30000000-0000-0000-0000-000000000008'), 'latitude', 13.1, 'longitude', 77.6, 'accuracy_m', 8)))$$,
    '54000', 'Track assignment point limit exceeded',
    'track ingestion enforces the twenty-thousand-point assignment/person ceiling'
);
select is(
    public.patrolgrid_ingest_track_points(
        '40000000-0000-0000-0000-000000000007',
        jsonb_build_array(jsonb_build_object(
            'client_point_id', '60000000-0000-0000-0000-000000000012',
            'sequence_number', 0,
            'recorded_at', (select ends_at + interval '4 minutes'
                            from public.patrolgrid_missions
                            where id = '30000000-0000-0000-0000-000000000008'),
            'latitude', 13.1,
            'longitude', 77.6,
            'accuracy_m', 8
        ))
    ),
    0,
    'an exact track retry remains idempotent after the assignment reaches its cap'
);
reset role;

insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, ended_at,
    end_reason, app_version, created_at
) values (
    '40000000-0000-0000-0000-000000000014',
    '30000000-0000-0000-0000-000000000008',
    '00000000-0000-0000-0000-000000000003',
    '50000000-0000-0000-0000-000000000014',
    (select ends_at - interval '30 minutes' from public.patrolgrid_missions
     where id = '30000000-0000-0000-0000-000000000008'),
    (select ends_at from public.patrolgrid_missions
     where id = '30000000-0000-0000-0000-000000000008'),
    'completed',
    '1.0-test',
    now() - interval '2 hours'
);
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_ingest_track_points(
        '40000000-0000-0000-0000-000000000014',
        jsonb_build_array(jsonb_build_object(
            'client_point_id', '60000000-0000-0000-0000-000000000097',
            'sequence_number', 0,
            'recorded_at', (select ends_at - interval '1 minute'
                            from public.patrolgrid_missions
                            where id = '30000000-0000-0000-0000-000000000008'),
            'latitude', 13.1,
            'longitude', 77.6,
            'accuracy_m', 8
        ))
    )$$,
    '54000', 'Track assignment point limit exceeded',
    'a second session cannot reset the assignment/person point ceiling'
);
reset role;

select is(
    has_table_privilege('anon', 'public.patrolgrid_retention_holds', 'SELECT')
    or has_table_privilege('authenticated', 'public.patrolgrid_retention_holds', 'SELECT')
    or has_table_privilege('anon', 'public.patrolgrid_retention_hold_reviews', 'SELECT')
    or has_table_privilege('authenticated', 'public.patrolgrid_retention_hold_reviews', 'SELECT')
    or has_table_privilege('anon', 'public.patrolgrid_retention_runs', 'SELECT')
    or has_table_privilege('authenticated', 'public.patrolgrid_retention_runs', 'SELECT'),
    false,
    'mobile roles cannot read the legal-hold register or retention ledger'
);
select is(
    has_function_privilege(
        'authenticated',
        'public.patrolgrid_place_retention_hold(uuid,text,text,text,text,text,timestamptz,text,text)',
        'EXECUTE'
    )
    or has_function_privilege(
        'authenticated',
        'public.patrolgrid_review_retention_hold(uuid,uuid,timestamptz,text,text)',
        'EXECUTE'
    )
    or has_function_privilege(
        'authenticated',
        'public.patrolgrid_release_retention_hold(uuid,text,text)',
        'EXECUTE'
    ),
    false,
    'authenticated clients cannot place or release retention holds'
);
select is(
    has_function_privilege(
        'authenticated',
        'public.patrolgrid_purge_expired_evidence()',
        'EXECUTE'
    )
    or has_function_privilege(
        'authenticated',
        'public.patrolgrid_run_retention_purge(timestamptz,text,integer)',
        'EXECUTE'
    )
    or has_function_privilege(
        'authenticated',
        'public.patrolgrid_purge_expired_evidence_scheduled()',
        'EXECUTE'
    )
    or has_function_privilege(
        'authenticated',
        'public.patrolgrid_cancel_expired_unstarted_missions_scheduled()',
        'EXECUTE'
    ),
    false,
    'authenticated clients cannot execute any retention worker'
);
select ok(
    has_function_privilege(
        'service_role',
        'public.patrolgrid_place_retention_hold(uuid,text,text,text,text,text,timestamptz,text,text)',
        'EXECUTE'
    )
    and has_function_privilege(
        'service_role',
        'public.patrolgrid_review_retention_hold(uuid,uuid,timestamptz,text,text)',
        'EXECUTE'
    )
    and has_function_privilege(
        'service_role',
        'public.patrolgrid_release_retention_hold(uuid,text,text)',
        'EXECUTE'
    )
    and has_function_privilege(
        'service_role',
        'public.patrolgrid_purge_expired_evidence()',
        'EXECUTE'
    )
    and has_table_privilege(
        'service_role',
        'public.patrolgrid_retention_holds',
        'SELECT'
    )
    and has_table_privilege(
        'service_role',
        'public.patrolgrid_retention_hold_reviews',
        'SELECT'
    )
    and has_table_privilege(
        'service_role',
        'public.patrolgrid_retention_runs',
        'SELECT'
    ),
    'service-role operations can manage holds, read health, and request a real-time purge'
);
select is(
    has_function_privilege(
        'service_role',
        'public.patrolgrid_run_retention_purge(timestamptz,text,integer)',
        'EXECUTE'
    )
    or has_function_privilege(
        'service_role',
        'public.patrolgrid_purge_expired_evidence_scheduled()',
        'EXECUTE'
    )
    or has_function_privilege(
        'service_role',
        'public.patrolgrid_cancel_expired_unstarted_missions_scheduled()',
        'EXECUTE'
    ),
    false,
    'service-role callers cannot forge purge time or scheduler provenance'
);
select is(
    (
        select count(*)
        from pg_catalog.pg_proc procedure_record
        join pg_catalog.pg_namespace namespace_record
          on namespace_record.oid = procedure_record.pronamespace
        cross join lateral pg_catalog.aclexplode(
            coalesce(
                procedure_record.proacl,
                pg_catalog.acldefault('f', procedure_record.proowner)
            )
        ) acl
        where namespace_record.nspname = 'public'
          and procedure_record.proname in (
              'patrolgrid_place_retention_hold',
              'patrolgrid_review_retention_hold',
              'patrolgrid_release_retention_hold',
              'patrolgrid_run_retention_purge',
              'patrolgrid_purge_expired_evidence',
              'patrolgrid_purge_expired_evidence_scheduled',
              'patrolgrid_cancel_expired_unstarted_missions_scheduled'
          )
          and acl.grantee = 0
          and acl.privilege_type = 'EXECUTE'
    ),
    0::bigint,
    'PUBLIC has no execute grant on retention administration functions'
);
select is(
    has_function_privilege(
        'anon',
        'public.patrolgrid_place_retention_hold(uuid,text,text,text,text,text,timestamptz,text,text)',
        'EXECUTE'
    )
    or has_function_privilege(
        'anon',
        'public.patrolgrid_review_retention_hold(uuid,uuid,timestamptz,text,text)',
        'EXECUTE'
    )
    or has_function_privilege(
        'anon',
        'public.patrolgrid_release_retention_hold(uuid,text,text)',
        'EXECUTE'
    )
    or has_function_privilege(
        'anon',
        'public.patrolgrid_purge_expired_evidence()',
        'EXECUTE'
    )
    or has_function_privilege(
        'anon',
        'public.patrolgrid_run_retention_purge(timestamptz,text,integer)',
        'EXECUTE'
    )
    or has_function_privilege(
        'anon',
        'public.patrolgrid_purge_expired_evidence_scheduled()',
        'EXECUTE'
    )
    or has_function_privilege(
        'anon',
        'public.patrolgrid_cancel_expired_unstarted_missions_scheduled()',
        'EXECUTE'
    ),
    false,
    'anonymous clients cannot execute retention administration functions'
);
select is(
    has_table_privilege('service_role', 'public.patrolgrid_retention_holds', 'INSERT')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_holds', 'UPDATE')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_holds', 'DELETE')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_holds', 'TRUNCATE')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_hold_reviews', 'INSERT')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_hold_reviews', 'UPDATE')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_hold_reviews', 'DELETE')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_hold_reviews', 'TRUNCATE')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_runs', 'INSERT')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_runs', 'UPDATE')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_runs', 'DELETE')
    or has_table_privilege('service_role', 'public.patrolgrid_retention_runs', 'TRUNCATE')
    or has_sequence_privilege(
        'service_role',
        'public.patrolgrid_retention_runs_id_seq',
        'USAGE'
    )
    or has_sequence_privilege(
        'service_role',
        'public.patrolgrid_retention_runs_id_seq',
        'SELECT'
    ),
    false,
    'service-role operations cannot directly mutate holds or purge-ledger rows'
);
select is(
    (
        select count(*)
        from pg_catalog.pg_policies policy
        where policy.schemaname = 'public'
          and policy.tablename in (
              'patrolgrid_retention_holds',
              'patrolgrid_retention_hold_reviews',
              'patrolgrid_retention_runs'
          )
    ),
    0::bigint,
    'retention administration tables expose no client RLS policies'
);
select is(
    (
        select count(*)
        from cron.job
        where jobname = 'patrolgrid-retention-purge'
    ),
    1::bigint,
    'hosted Supabase cron contains exactly one named retention worker'
);
select is(
    (
        select schedule
        from cron.job
        where jobname = 'patrolgrid-retention-purge'
    ),
    '*/5 * * * *'::text,
    'retention cleanup runs on a bounded five-minute deletion cadence'
);
select ok(
    (
        select active
           and command = 'select public.patrolgrid_purge_expired_evidence_scheduled();'
        from cron.job
        where jobname = 'patrolgrid-retention-purge'
    ),
    'retention cron is active and calls only the exact owner-only scheduled wrapper'
);
select is(
    (
        select count(*)
        from cron.job
        where jobname = 'patrolgrid-cancel-expired-unstarted'
    ),
    1::bigint,
    'hosted Supabase cron contains one missed-assignment reconciler'
);
select is(
    (
        select schedule
        from cron.job
        where jobname = 'patrolgrid-cancel-expired-unstarted'
    ),
    '*/5 * * * *'::text,
    'missed assignments are reconciled within a bounded five-minute cadence'
);
select ok(
    (
        select active
           and command = 'select public.patrolgrid_cancel_expired_unstarted_missions_scheduled();'
        from cron.job
        where jobname = 'patrolgrid-cancel-expired-unstarted'
    ),
    'missed-assignment cron calls only its owner-only scheduled wrapper'
);
select is(
    (
        select count(*)
        from pg_catalog.pg_indexes
        where schemaname = 'public'
          and indexname in (
              'patrolgrid_sessions_mission',
              'patrolgrid_priority_visits_mission',
              'patrolgrid_audit_events_mission',
              'patrolgrid_retention_holds_mission',
              'patrolgrid_retention_holds_active_review_due',
              'patrolgrid_retention_hold_reviews_hold'
          )
    ),
    6::bigint,
    'purge-critical mission keys and active hold-review deadlines are indexed'
);

insert into public.patrolgrid_missions (
    id, subdivision_id, route_template_id, title, starts_at, ends_at,
    guidance, status, created_by
) values (
    '30000000-0000-0000-0000-000000000200',
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Retention clock lifecycle',
    now() - interval '2 hours',
    now() - interval '1 hour',
    'suggested_route',
    'planned',
    '00000000-0000-0000-0000-000000000001'
);
select ok(
    (
        select closed_at is null and retention_until is null
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000200'
    ),
    'non-terminal missions do not run a retention clock'
);

update public.patrolgrid_missions
set status = 'needs_review'
where id = '30000000-0000-0000-0000-000000000200';
select ok(
    (
        select closed_at is not null
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000200'
    ),
    'first entry to needs-review records the server-side patrol closure time'
);
select ok(
    (
        select retention_until = closed_at + interval '8760 hours'
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000200'
    ),
    'post-patrol evidence expires exactly 365 times 24 hours after closure'
);

create temporary table patrolgrid_retention_clock_state
on commit drop
as
select closed_at, retention_until
from public.patrolgrid_missions
where id = '30000000-0000-0000-0000-000000000200';

update public.patrolgrid_missions
set retention_until = now()
where id = '30000000-0000-0000-0000-000000000200';
select ok(
    (
        select retention_until = closed_at + interval '8760 hours'
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000200'
    ),
    'mission writes cannot shorten or extend the fixed retention clock'
);

update public.patrolgrid_missions
set status = 'completed'
where id = '30000000-0000-0000-0000-000000000200';
select ok(
    (
        select mission.closed_at = clock_state.closed_at
           and mission.retention_until = clock_state.retention_until
        from public.patrolgrid_missions mission
        cross join patrolgrid_retention_clock_state clock_state
        where mission.id = '30000000-0000-0000-0000-000000000200'
    ),
    'needs-review to completed preserves the first patrol-closure clock'
);

update public.patrolgrid_missions
set status = 'needs_review'
where id = '30000000-0000-0000-0000-000000000200';
select ok(
    (
        select mission.closed_at = clock_state.closed_at
           and mission.retention_until = clock_state.retention_until
        from public.patrolgrid_missions mission
        cross join patrolgrid_retention_clock_state clock_state
        where mission.id = '30000000-0000-0000-0000-000000000200'
    ),
    'completed to needs-review cannot restart or extend retention'
);

update public.patrolgrid_missions
set status = 'completed'
where id = '30000000-0000-0000-0000-000000000200';
select ok(
    (
        select mission.closed_at = clock_state.closed_at
           and mission.retention_until = clock_state.retention_until
        from public.patrolgrid_missions mission
        cross join patrolgrid_retention_clock_state clock_state
        where mission.id = '30000000-0000-0000-0000-000000000200'
    ),
    'returning from needs-review to completed keeps the original closure clock'
);

update public.patrolgrid_missions
set status = 'cancelled'
where id = '30000000-0000-0000-0000-000000000200';
select ok(
    (
        select mission.closed_at = clock_state.closed_at
           and mission.retention_until = clock_state.retention_until
        from public.patrolgrid_missions mission
        cross join patrolgrid_retention_clock_state clock_state
        where mission.id = '30000000-0000-0000-0000-000000000200'
    ),
    'completed to cancelled also preserves the first patrol-closure clock'
);

select throws_ok(
    $$update public.patrolgrid_missions
      set status = 'active'
      where id = '30000000-0000-0000-0000-000000000200'$$,
    '55000',
    'Terminal patrol missions cannot be reopened; create a new assignment',
    'terminal missions cannot be reopened to reset their first closure clock'
);

create temporary table patrolgrid_retention_test_runs (
    stage text primary key,
    run_id bigint not null
) on commit drop;

select throws_ok(
    $$select public.patrolgrid_run_retention_purge(
        'infinity'::timestamptz,
        'manual',
        100
    )$$,
    '22023',
    'A valid purge time, source, and batch limit are required',
    'an infinite owner-supplied purge time cannot bypass the 365-day boundary'
);

insert into public.patrolgrid_missions (
    id, subdivision_id, route_template_id, title, starts_at, ends_at,
    guidance, status, created_by
) values (
    '30000000-0000-0000-0000-000000000204',
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Exact retention boundary fixture',
    now() - interval '2 hours', now() - interval '1 hour',
    'suggested_route', 'needs_review',
    '00000000-0000-0000-0000-000000000001'
);
create temporary table patrolgrid_retention_boundary
on commit drop
as
select retention_until
from public.patrolgrid_missions
where id = '30000000-0000-0000-0000-000000000204';

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'boundary-before',
    public.patrolgrid_run_retention_purge(
        (select retention_until - interval '1 microsecond'
         from patrolgrid_retention_boundary),
        'manual',
        100
    )
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000204'
    ),
    1::bigint,
    'mission evidence remains one microsecond before its retention deadline'
);

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'boundary-at',
    public.patrolgrid_run_retention_purge(
        (select retention_until from patrolgrid_retention_boundary),
        'manual',
        100
    )
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000204'
    ),
    0::bigint,
    'mission evidence becomes eligible exactly at its retention deadline'
);

insert into public.patrolgrid_missions (
    id, subdivision_id, route_template_id, title, starts_at, ends_at,
    guidance, status, created_by
) values
    (
        '30000000-0000-0000-0000-000000000201',
        '10000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'Expired evidence purge fixture',
        now() - interval '2 hours', now() - interval '1 hour',
        'suggested_route', 'cancelled',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000202',
        '10000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'Legal hold purge fixture',
        now() - interval '2 hours', now() - interval '1 hour',
        'suggested_route', 'completed',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000203',
        '10000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'Open session fail closed fixture',
        now() - interval '2 hours', now() - interval '1 hour',
        'suggested_route', 'cancelled',
        '00000000-0000-0000-0000-000000000001'
    );

insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values
    (
        '30000000-0000-0000-0000-000000000201',
        '00000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000202',
        '00000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000203',
        '00000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000001'
    );

insert into public.patrolgrid_priority_locations (
    id, mission_id, name, latitude, longitude, sort_order
) values (
    '21000000-0000-0000-0000-000000000201',
    '30000000-0000-0000-0000-000000000201',
    'Retention fixture priority', 13.0, 77.5, 0
);

insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, ended_at,
    end_reason, app_version
) values (
    '40000000-0000-0000-0000-000000000201',
    '30000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000003',
    '50000000-0000-0000-0000-000000000201',
    now() - interval '2 hours', now() - interval '1 hour',
    'cancelled', '1.0-test'
);
insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, app_version
) values (
    '40000000-0000-0000-0000-000000000203',
    '30000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000003',
    '50000000-0000-0000-0000-000000000203',
    now() - interval '2 hours', '1.0-test'
);

insert into public.patrolgrid_track_points (
    client_point_id, session_id, mission_id, user_id, sequence_number,
    recorded_at, latitude, longitude, accuracy_m
) values (
    '60000000-0000-0000-0000-000000000201',
    '40000000-0000-0000-0000-000000000201',
    '30000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000003',
    0, now() - interval '90 minutes', 13.0, 77.5, 8
);
insert into public.patrolgrid_priority_visits (
    id, session_id, priority_location_id, mission_id, user_id, visited_at, method,
    latitude, longitude, accuracy_m, note
) values (
    '61000000-0000-0000-0000-000000000201',
    '40000000-0000-0000-0000-000000000201',
    '21000000-0000-0000-0000-000000000201',
    '30000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000003',
    now() - interval '90 minutes', 'gps', 13.0, 77.5, 8, 'purge fixture'
);
insert into public.patrolgrid_field_updates (
    id, client_update_id, mission_id, user_id, category, detail, occurred_at,
    latitude, longitude
) values (
    '70000000-0000-0000-0000-000000000201',
    '70000000-0000-0000-0000-000000000202',
    '30000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000003',
    'observation', 'Sensitive field note purge fixture',
    now() - interval '90 minutes', 13.0, 77.5
);
insert into public.patrolgrid_reviews (
    id, mission_id, reviewer_id, outcome, notes
) values (
    '80000000-0000-0000-0000-000000000201',
    '30000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000001',
    'approved', 'Sensitive review note purge fixture'
);

create temporary table patrolgrid_hold_test_values
on commit drop
as
select clock_timestamp() + interval '15 days' as review_due_at,
       clock_timestamp() + interval '30 days' as next_review_due_at;

select lives_ok(
    $$select public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-202-A',
        'Preserve while the documented matter remains active.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        (select review_due_at from patrolgrid_hold_test_values),
        'Written closure from the investigating authority',
        'retention-test-admin'
    )$$,
    'database administration can place a documented legal hold'
);
select lives_ok(
    $$select public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-202-B',
        'Preserve independently for the second documented matter.',
        'Court preservation notice 7',
        'Route, observation, review, and audit evidence',
        'District legal liaison',
        (select review_due_at from patrolgrid_hold_test_values),
        'Written withdrawal of the preservation notice',
        'retention-test-admin'
    )$$,
    'one mission can carry multiple independent legal holds'
);
select is(
    public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-202-A',
        'Preserve while the documented matter remains active.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        (select review_due_at from patrolgrid_hold_test_values),
        'Written closure from the investigating authority',
        'retention-test-admin'
    ),
    (
        select id
        from public.patrolgrid_retention_holds
        where mission_id = '30000000-0000-0000-0000-000000000202'
          and hold_reference = 'CASE-RETENTION-202-A'
          and released_at is null
    ),
    'an exact hold retry is idempotent'
);
select throws_ok(
    $$select public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-202-A',
        'A conflicting reason must not replace the active hold.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        (select review_due_at from patrolgrid_hold_test_values),
        'Written closure from the investigating authority',
        'retention-test-admin'
    )$$,
    '22023',
    'Hold reference was reused with different details',
    'a hold reference cannot be silently repurposed'
);
select throws_ok(
    $$select public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-INVALID',
        'This hold has an invalid review deadline.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        clock_timestamp() - interval '1 second',
        'Written closure from the investigating authority',
        'retention-test-admin'
    )$$,
    '22023',
    'Mission and complete legal-hold authority, scope, owner, review, release, and operator details are required',
    'a legal hold requires a future review deadline and complete structured authority'
);
select throws_ok(
    $$select public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-TOO-LATE',
        'This hold exceeds the approved review cadence.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        clock_timestamp() + interval '30 days 1 minute',
        'Written closure from the investigating authority',
        'retention-test-admin'
    )$$,
    '22023',
    'Mission and complete legal-hold authority, scope, owner, review, release, and operator details are required',
    'a new legal hold review must be scheduled within thirty days'
);
select throws_ok(
    $$select public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-INFINITY',
        'This hold has a non-finite review deadline.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        'infinity'::timestamptz,
        'Written closure from the investigating authority',
        'retention-test-admin'
    )$$,
    '22023',
    'Mission and complete legal-hold authority, scope, owner, review, release, and operator details are required',
    'a legal hold cannot use infinity to evade review'
);
select ok(
    (
        select bool_and(
            char_length(authority) >= 3
            and char_length(scope) >= 3
            and char_length(owner) >= 2
            and initial_review_due_at = review_due_at
            and review_due_at > placed_at
            and review_due_at <= placed_at + interval '30 days'
            and char_length(release_condition) >= 3
        )
        from public.patrolgrid_retention_holds
        where mission_id = '30000000-0000-0000-0000-000000000202'
    ),
    'every legal hold structurally records authority, scope, owner, review date, and release condition'
);
select is(
    (
        select count(*)
        from public.patrolgrid_retention_holds
        where mission_id = '30000000-0000-0000-0000-000000000202'
          and released_at is null
    ),
    2::bigint,
    'the mission retains both active legal holds'
);

select throws_ok(
    $$select public.patrolgrid_review_retention_hold(
        '90000000-0000-0000-0000-000000000211',
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-A'),
        clock_timestamp() + interval '30 days 1 minute',
        'This review exceeds the approved cadence.',
        'retention-review-admin'
    )$$,
    '22023',
    'Next retention-hold review date must be finite and within 30 days',
    'hold review rescheduling cannot exceed thirty days'
);
select throws_ok(
    $$select public.patrolgrid_review_retention_hold(
        '90000000-0000-0000-0000-000000000212',
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-A'),
        'infinity'::timestamptz,
        'This review attempts a non-finite cadence.',
        'retention-review-admin'
    )$$,
    '22023',
    'Next retention-hold review date must be finite and within 30 days',
    'hold review rescheduling cannot use infinity'
);

select lives_ok(
    $$select public.patrolgrid_review_retention_hold(
        '90000000-0000-0000-0000-000000000201',
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-A'
           and released_at is null),
        (select next_review_due_at
         from patrolgrid_hold_test_values),
        'Documented review confirms that preservation must continue.',
        'retention-review-admin'
    )$$,
    'service-role administration can review and reschedule an active hold without releasing it'
);
select ok(
    (
        select hold.review_due_at = review_record.next_review_due_at
           and review_record.previous_review_due_at = test_value.review_due_at
           and review_record.next_review_due_at = test_value.next_review_due_at
           and review_record.review_reason = 'Documented review confirms that preservation must continue.'
           and review_record.reviewed_by = 'retention-review-admin'
           and review_record.reviewed_at is not null
           and hold.released_at is null
        from public.patrolgrid_retention_holds hold
        join public.patrolgrid_retention_hold_reviews review_record
          on review_record.hold_id = hold.id
        cross join patrolgrid_hold_test_values test_value
        where review_record.id = '90000000-0000-0000-0000-000000000201'
    ),
    'hold review history records prior/new dates, reviewer, reason, timestamp, and continuous active state'
);
select is(
    public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-202-A',
        'Preserve while the documented matter remains active.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        (select review_due_at from patrolgrid_hold_test_values),
        'Written closure from the investigating authority',
        'retention-test-admin'
    ),
    (
        select id from public.patrolgrid_retention_holds
        where mission_id = '30000000-0000-0000-0000-000000000202'
          and hold_reference = 'CASE-RETENTION-202-A'
    ),
    'original placement retry remains idempotent after review changes the current due date'
);
select is(
    public.patrolgrid_review_retention_hold(
        '90000000-0000-0000-0000-000000000201',
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-A'),
        (select next_review_due_at
         from patrolgrid_hold_test_values),
        'Documented review confirms that preservation must continue.',
        'retention-review-admin'
    ),
    (
        select reviewed_at
        from public.patrolgrid_retention_hold_reviews
        where id = '90000000-0000-0000-0000-000000000201'
    ),
    'an exact hold-review retry is idempotent'
);
select throws_ok(
    $$select public.patrolgrid_review_retention_hold(
        '90000000-0000-0000-0000-000000000201',
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-A'),
        (select next_review_due_at
         from patrolgrid_hold_test_values),
        'A conflicting review reason must not replace history.',
        'different-review-admin'
    )$$,
    '22023',
    'Hold review idempotency key was reused with different details',
    'a hold-review idempotency key cannot be reused with conflicting details'
);

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'first',
    public.patrolgrid_run_retention_purge(
        clock_timestamp() + interval '366 days',
        'manual',
        100
    )
);

select ok(
    (
        select run.source = 'manual'
           and run.eligible_missions > 0
           and run.missions_deleted = run.eligible_missions
        from public.patrolgrid_retention_runs run
        join patrolgrid_retention_test_runs test_run on test_run.run_id = run.id
        where test_run.stage = 'first'
    ),
    'a completed purge records its source and exact aggregate mission count'
);
select cmp_ok(
    (
        select held_missions_skipped
        from public.patrolgrid_retention_runs
        where id = (
            select run_id from patrolgrid_retention_test_runs where stage = 'first'
        )
    ),
    '>=',
    1,
    'the purge ledger records expired missions deferred by legal hold'
);
select cmp_ok(
    (
        select overdue_hold_reviews
        from public.patrolgrid_retention_runs
        where id = (
            select run_id from patrolgrid_retention_test_runs where stage = 'first'
        )
    ),
    '>=',
    2,
    'the non-identifying health ledger flags both overdue active hold reviews'
);
select cmp_ok(
    (
        select open_sessions_skipped
        from public.patrolgrid_retention_runs
        where id = (
            select run_id from patrolgrid_retention_test_runs where stage = 'first'
        )
    ),
    '>=',
    1,
    'the purge ledger records inconsistent terminal missions deferred fail closed'
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000201'
    ),
    0::bigint,
    'an unheld expired mission is removed'
);
select is(
    (
        select
            (select count(*) from public.patrolgrid_track_points where mission_id = '30000000-0000-0000-0000-000000000201')
          + (select count(*) from public.patrolgrid_priority_visits where mission_id = '30000000-0000-0000-0000-000000000201')
          + (select count(*) from public.patrolgrid_field_updates where mission_id = '30000000-0000-0000-0000-000000000201')
          + (select count(*) from public.patrolgrid_reviews where mission_id = '30000000-0000-0000-0000-000000000201')
          + (select count(*) from public.patrolgrid_sessions where mission_id = '30000000-0000-0000-0000-000000000201')
          + (select count(*) from public.patrolgrid_assignments where mission_id = '30000000-0000-0000-0000-000000000201')
          + (select count(*) from public.patrolgrid_priority_locations where mission_id = '30000000-0000-0000-0000-000000000201')
    ),
    0::bigint,
    'dependent evidence and personal assignment data are purged in dependency order'
);
select is(
    (
        select count(*)
        from public.patrolgrid_audit_events audit
        where audit.mission_id = '30000000-0000-0000-0000-000000000201'
           or audit.payload ->> 'record_id' = '30000000-0000-0000-0000-000000000201'
    ),
    0::bigint,
    'mission-linked and identifier-bearing deletion audits are purged'
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000202'
    ),
    1::bigint,
    'an active legal hold prevents mission deletion past the normal deadline'
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000203'
    ),
    1::bigint,
    'an anomalous open session makes retention deletion fail closed'
);

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'second',
    public.patrolgrid_run_retention_purge(
        clock_timestamp() + interval '366 days',
        'manual',
        100
    )
);
select is(
    (
        select missions_deleted
        from public.patrolgrid_retention_runs
        where id = (
            select run_id from patrolgrid_retention_test_runs where stage = 'second'
        )
    ),
    0,
    'repeating a purge after eligible evidence is gone is idempotent'
);

select lives_ok(
    $$select public.patrolgrid_release_retention_hold(
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-A'
           and released_at is null),
        'Documented preservation requirement ended.',
        'retention-test-admin'
    )$$,
    'database administration can release a documented legal hold'
);
select is(
    public.patrolgrid_release_retention_hold(
        (
            select id
            from public.patrolgrid_retention_holds
            where mission_id = '30000000-0000-0000-0000-000000000202'
              and hold_reference = 'CASE-RETENTION-202-A'
        ),
        'Documented preservation requirement ended.',
        'retention-test-admin'
    ),
    (
        select released_at
        from public.patrolgrid_retention_holds
        where mission_id = '30000000-0000-0000-0000-000000000202'
          and hold_reference = 'CASE-RETENTION-202-A'
    ),
    'an exact hold-release retry is idempotent'
);
select is(
    public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-202-A',
        'Preserve while the documented matter remains active.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        (select review_due_at from patrolgrid_hold_test_values),
        'Written closure from the investigating authority',
        'retention-test-admin'
    ),
    (
        select id from public.patrolgrid_retention_holds
        where mission_id = '30000000-0000-0000-0000-000000000202'
          and hold_reference = 'CASE-RETENTION-202-A'
    ),
    'original placement retry returns the same hold id after release'
);
select throws_ok(
    $$select public.patrolgrid_place_retention_hold(
        '30000000-0000-0000-0000-000000000202',
        'CASE-RETENTION-202-A',
        'A released reference cannot be repurposed.',
        'District standing order 42',
        'All mission evidence and associated audit records',
        'Subdivision A records officer',
        (select review_due_at from patrolgrid_hold_test_values),
        'Written closure from the investigating authority',
        'retention-test-admin'
    )$$,
    '22023',
    'Hold reference was reused with different details',
    'released placement idempotency key cannot create or repurpose a hold'
);
select throws_ok(
    $$select public.patrolgrid_release_retention_hold(
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-A'),
        'A conflicting release reason must not replace the recorded reason.',
        'different-retention-admin'
    )$$,
    '22023',
    'Released hold retry conflicts with recorded release details',
    'a conflicting repeated release is rejected rather than silently accepted'
);
select throws_ok(
    $$select public.patrolgrid_review_retention_hold(
        '90000000-0000-0000-0000-000000000202',
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-A'),
        clock_timestamp() + interval '90 days',
        'A released hold must not be rescheduled.',
        'retention-review-admin'
    )$$,
    '55000',
    'Released retention holds cannot be reviewed',
    'released holds cannot be revived through the review workflow'
);
select lives_ok(
    $$select public.patrolgrid_release_retention_hold(
        (select id from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202'
           and hold_reference = 'CASE-RETENTION-202-B'
           and released_at is null),
        'The second documented preservation requirement ended.',
        'retention-test-admin'
    )$$,
    'the mission becomes purgeable only after its second independent hold is released'
);

update public.patrolgrid_sessions
set ended_at = now() - interval '1 hour',
    end_reason = 'cancelled'
where id = '40000000-0000-0000-0000-000000000203';

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'third',
    public.patrolgrid_run_retention_purge(
        clock_timestamp() + interval '366 days',
        'manual',
        100
    )
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where id in (
            '30000000-0000-0000-0000-000000000202',
            '30000000-0000-0000-0000-000000000203'
        )
    ),
    0::bigint,
    'released and internally consistent expired missions purge on the next run'
);
select is(
    (
        (select count(*)
         from public.patrolgrid_retention_holds
         where mission_id = '30000000-0000-0000-0000-000000000202')
        +
        (select count(*)
         from public.patrolgrid_retention_hold_reviews
         where id = '90000000-0000-0000-0000-000000000201')
    ),
    0::bigint,
    'released hold details and their review history are removed with the expired mission'
);
select is(
    (
        select missions_deleted
        from public.patrolgrid_retention_runs
        where id = (
            select run_id from patrolgrid_retention_test_runs where stage = 'third'
        )
    ),
    2,
    'the final aggregate ledger records both newly eligible deletions'
);
select ok(
    (
        select released_holds_deleted = 2
           and hold_reviews_deleted = 1
        from public.patrolgrid_retention_runs
        where id = (
            select run_id from patrolgrid_retention_test_runs where stage = 'third'
        )
    ),
    'the aggregate ledger records both holds and their review-history deletion'
);

insert into public.patrolgrid_missions (
    id, subdivision_id, route_template_id, title, starts_at, ends_at,
    guidance, status, created_by
)
select
    md5('patrolgrid-retention-backlog-' || series.value::text)::uuid,
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Retention backlog fixture ' || series.value::text,
    now() - interval '2 hours',
    now() - interval '1 hour',
    'suggested_route',
    'cancelled',
    '00000000-0000-0000-0000-000000000001'
from generate_series(1, 27) as series(value);

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'batch-first',
    public.patrolgrid_run_retention_purge(
        clock_timestamp() + interval '8784 hours',
        'manual',
        25
    )
);
select ok(
    (
        select batch_limit = 25
           and eligible_missions = 25
           and missions_deleted = 25
           and remaining_deletable_backlog = 2
           and oldest_deletable_backlog_age_seconds >= 86400
        from public.patrolgrid_retention_runs
        where id = (
            select run_id
            from patrolgrid_retention_test_runs
            where stage = 'batch-first'
        )
    ),
    'a production-sized batch deletes 25 and exposes the remaining non-identifying backlog age'
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where title like 'Retention backlog fixture %'
    ),
    2::bigint,
    'the bounded worker leaves exactly two of twenty-seven due missions for the next run'
);

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'batch-second',
    public.patrolgrid_run_retention_purge(
        clock_timestamp() + interval '8784 hours',
        'manual',
        25
    )
);
select ok(
    (
        select eligible_missions = 2
           and missions_deleted = 2
           and remaining_deletable_backlog = 0
           and oldest_deletable_backlog_age_seconds is null
        from public.patrolgrid_retention_runs
        where id = (
            select run_id
            from patrolgrid_retention_test_runs
            where stage = 'batch-second'
        )
    ),
    'the next bounded run drains the backlog and clears its oldest-age signal'
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where title like 'Retention backlog fixture %'
    ),
    0::bigint,
    'the second production batch removes every remaining due fixture'
);

-- Prove the scheduled wrapper's exact 2,000-mission drain envelope rather than
-- relying only on its source text. The single remaining mission is also the
-- aggregate backlog signal operations must alert on.
insert into public.patrolgrid_missions (
    id, subdivision_id, route_template_id, title, starts_at, ends_at,
    guidance, status, created_by
)
select
    md5('patrolgrid-scheduler-capacity-' || series.value::text)::uuid,
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Scheduled retention capacity fixture ' || series.value::text,
    clock_timestamp() - interval '2 hours',
    clock_timestamp() - interval '1 hour',
    'suggested_route',
    'cancelled',
    '00000000-0000-0000-0000-000000000001'
from generate_series(1, 2001) as series(value);

alter table public.patrolgrid_missions
disable trigger patrolgrid_set_mission_retention;
update public.patrolgrid_missions
set closed_at = statement_timestamp() - interval '8761 hours',
    retention_until = statement_timestamp() - interval '1 hour'
where title like 'Scheduled retention capacity fixture %';
alter table public.patrolgrid_missions
enable trigger patrolgrid_set_mission_retention;

create temporary table patrolgrid_scheduler_capacity_start
on commit drop
as select coalesce(max(id), 0) as previous_run_id
from public.patrolgrid_retention_runs;

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'scheduler-capacity-first',
    public.patrolgrid_purge_expired_evidence_scheduled()
);
select is(
    (
        select count(*)
        from public.patrolgrid_missions
        where title like 'Scheduled retention capacity fixture %'
    ),
    1::bigint,
    'one scheduled invocation deletes exactly its 2,000-mission capacity envelope'
);
select ok(
    (
        select count(*) = 20 and sum(missions_deleted) = 2000
        from public.patrolgrid_retention_runs
        where id > (select previous_run_id from patrolgrid_scheduler_capacity_start)
          and source = 'scheduler'
    )
    and (
        select remaining_deletable_backlog = 1
           and oldest_deletable_backlog_age_seconds >= 3600
        from public.patrolgrid_retention_runs
        where id = (
            select run_id
            from patrolgrid_retention_test_runs
            where stage = 'scheduler-capacity-first'
        )
    ),
    'capacity exhaustion emits an aggregate remaining-backlog count and age'
);

insert into patrolgrid_retention_test_runs(stage, run_id) values (
    'scheduler-capacity-second',
    public.patrolgrid_purge_expired_evidence_scheduled()
);
select ok(
    not exists (
        select 1
        from public.patrolgrid_missions
        where title like 'Scheduled retention capacity fixture %'
    )
    and (
        select eligible_missions = 1
           and missions_deleted = 1
           and remaining_deletable_backlog = 0
           and oldest_deletable_backlog_age_seconds is null
        from public.patrolgrid_retention_runs
        where id = (
            select run_id
            from patrolgrid_retention_test_runs
            where stage = 'scheduler-capacity-second'
        )
    ),
    'the next five-minute scheduled invocation drains the signalled overflow'
);

select is(
    (
        select count(*)
        from patrolgrid_retention_test_runs test_run
        join public.patrolgrid_retention_runs run on run.id = test_run.run_id
    ),
    (select count(*) from patrolgrid_retention_test_runs),
    'every recorded aggregate purge result survives evidence deletion'
);
select is(
    (
        select count(*)
        from information_schema.columns column_record
        where column_record.table_schema = 'public'
          and column_record.table_name = 'patrolgrid_retention_runs'
          and column_record.column_name in (
              'mission_id', 'user_id', 'actor_id', 'payload', 'coordinates', 'notes'
          )
    ),
    0::bigint,
    'the durable purge ledger cannot retain mission or staff evidence fields'
);
select cmp_ok(
    (
        select audit_events_deleted
        from public.patrolgrid_retention_runs
        where id = (
            select run_id from patrolgrid_retention_test_runs where stage = 'first'
        )
    ),
    '>',
    0::bigint,
    'the purge ledger confirms audit-related personal data was deleted'
);

insert into public.patrolgrid_missions (
    id, subdivision_id, route_template_id, title, starts_at, ends_at,
    guidance, status, created_by
) values
    (
        '30000000-0000-0000-0000-000000000290',
        '10000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'Expired unstarted assignment',
        clock_timestamp() - interval '2 hours',
        clock_timestamp() - interval '6 minutes',
        'suggested_route', 'assigned',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000291',
        '10000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'Assignment still inside closure grace',
        clock_timestamp() - interval '1 hour',
        clock_timestamp() - interval '4 minutes',
        'suggested_route', 'assigned',
        '00000000-0000-0000-0000-000000000001'
    );

select lives_ok(
    $$select public.patrolgrid_cancel_expired_unstarted_missions_scheduled()$$,
    'owner-only scheduler reconciles expired assignments without a patrol session'
);
select ok(
    (
        select status = 'cancelled'
           and closed_at is not null
           and retention_until = closed_at + interval '8760 hours'
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000290'
    ),
    'expired assigned mission with no session auto-cancels and receives one retention clock'
);
select is(
    (
        select status
        from public.patrolgrid_missions
        where id = '30000000-0000-0000-0000-000000000291'
    ),
    'assigned'::text,
    'unstarted mission remains assigned until the full five-minute closure grace elapses'
);

-- A modified client must not multiply evidence containers or reset the point
-- allowance by repeatedly closing and restarting while a teammate keeps a
-- multi-person mission active.
update public.patrolgrid_memberships
set status = 'active'
where subdivision_id = '10000000-0000-0000-0000-000000000001'
  and user_id = '00000000-0000-0000-0000-000000000002';
insert into public.patrolgrid_missions (
    id, subdivision_id, title, starts_at, ends_at, guidance, status, created_by
) values
    (
        '30000000-0000-0000-0000-000000000040',
        '10000000-0000-0000-0000-000000000001',
        'Session lifetime quota', now() - interval '1 hour', now() + interval '2 hours',
        'area_coverage', 'active', '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000041',
        '10000000-0000-0000-0000-000000000001',
        'Session rolling quota', now() - interval '1 hour', now() + interval '2 hours',
        'area_coverage', 'active', '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000042',
        '10000000-0000-0000-0000-000000000001',
        'Open session recovery at quota', now() - interval '1 hour', now() + interval '2 hours',
        'area_coverage', 'active', '00000000-0000-0000-0000-000000000001'
    );

insert into public.patrolgrid_assignments (mission_id, user_id, assigned_by) values
    ('30000000-0000-0000-0000-000000000040', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000040', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000041', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000042', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001');

insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, ended_at,
    end_reason, app_version, created_at
) values (
    '40000000-0000-0000-0000-000000000040',
    '30000000-0000-0000-0000-000000000040',
    '00000000-0000-0000-0000-000000000002',
    '50000000-0000-0000-0000-000000000040',
    now() - interval '50 minutes', now() - interval '49 minutes',
    'device_issue', '1.0-test', now() - interval '1 day'
);
insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, ended_at,
    end_reason, app_version, created_at
)
select
    md5('patrolgrid-session-cap-' || series.value::text)::uuid,
    '30000000-0000-0000-0000-000000000040',
    '00000000-0000-0000-0000-000000000002',
    md5('patrolgrid-session-cap-install-' || series.value::text)::uuid,
    now() - interval '45 minutes' + make_interval(secs => series.value),
    now() - interval '44 minutes' + make_interval(secs => series.value),
    'device_issue', '1.0-test', now() - interval '1 day'
from generate_series(2, 16) as series(value);

insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, ended_at,
    end_reason, app_version, created_at
)
select
    md5('patrolgrid-open-recovery-closed-' || series.value::text)::uuid,
    '30000000-0000-0000-0000-000000000042',
    '00000000-0000-0000-0000-000000000002',
    md5('patrolgrid-open-recovery-install-' || series.value::text)::uuid,
    now() - interval '50 minutes' + make_interval(secs => series.value),
    now() - interval '49 minutes' + make_interval(secs => series.value),
    'device_issue', '1.0-test', now() - interval '1 day'
from generate_series(1, 15) as series(value);
insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, app_version, created_at
) values (
    '40000000-0000-0000-0000-000000000042',
    '30000000-0000-0000-0000-000000000042',
    '00000000-0000-0000-0000-000000000002',
    '50000000-0000-0000-0000-000000000042',
    now() - interval '5 minutes', '1.0-test', now() - interval '5 minutes'
);

insert into public.patrolgrid_sessions (
    id, mission_id, user_id, installation_id, started_at, ended_at,
    end_reason, app_version, created_at
)
select
    md5('patrolgrid-session-burst-' || series.value::text)::uuid,
    '30000000-0000-0000-0000-000000000041',
    '00000000-0000-0000-0000-000000000002',
    md5('patrolgrid-session-burst-install-' || series.value::text)::uuid,
    now() - interval '10 minutes' + make_interval(secs => series.value),
    now() - interval '9 minutes' + make_interval(secs => series.value),
    'device_issue', '1.0-test', now() - make_interval(mins => series.value)
from generate_series(1, 4) as series(value);

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select throws_ok(
    $$select public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000043',
        '30000000-0000-0000-0000-000000000040',
        '50000000-0000-0000-0000-000000000043',
        '1.0-test'
    )$$,
    '54000', 'Patrol assignment session limit exceeded',
    'a seventeenth evidence session is rejected for one mission/person'
);
select is(
    public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000040',
        '30000000-0000-0000-0000-000000000040',
        '50000000-0000-0000-0000-000000000040',
        '1.0-test'
    ),
    '40000000-0000-0000-0000-000000000040'::uuid,
    'an exact closed-session retry remains idempotent at the lifetime cap'
);
select is(
    (
        select count(*)
        from public.patrolgrid_sessions
        where mission_id = '30000000-0000-0000-0000-000000000040'
          and user_id = '00000000-0000-0000-0000-000000000002'
    ),
    16::bigint,
    'lifetime-cap denial and retry create no extra session row'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select is(
    public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000044',
        '30000000-0000-0000-0000-000000000040',
        '50000000-0000-0000-0000-000000000044',
        '1.0-test'
    ),
    '40000000-0000-0000-0000-000000000044'::uuid,
    'one patrol person cannot consume a teammate session quota'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is(
    public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000045',
        '30000000-0000-0000-0000-000000000042',
        '50000000-0000-0000-0000-000000000042',
        '1.0-test'
    ),
    '40000000-0000-0000-0000-000000000042'::uuid,
    'recovering the existing open session remains available at the lifetime cap'
);
select is(
    (
        select count(*)
        from public.patrolgrid_sessions
        where mission_id = '30000000-0000-0000-0000-000000000042'
          and user_id = '00000000-0000-0000-0000-000000000002'
    ),
    16::bigint,
    'open-session recovery at the cap creates no new evidence source'
);
select throws_ok(
    $$select public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000041',
        '30000000-0000-0000-0000-000000000041',
        '50000000-0000-0000-0000-000000000041',
        '1.0-test'
    )$$,
    '54000', 'Patrol session restart rate limit exceeded',
    'a fifth newly created session inside fifteen minutes is rejected'
);
select is(
    (
        select count(*)
        from public.patrolgrid_sessions
        where mission_id = '30000000-0000-0000-0000-000000000041'
          and user_id = '00000000-0000-0000-0000-000000000002'
    ),
    4::bigint,
    'rolling-rate denial creates no session row'
);

reset role;
update public.patrolgrid_sessions
set created_at = now() - interval '16 minutes'
where mission_id = '30000000-0000-0000-0000-000000000041'
  and user_id = '00000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is(
    public.patrolgrid_start_session(
        '40000000-0000-0000-0000-000000000041',
        '30000000-0000-0000-0000-000000000041',
        '50000000-0000-0000-0000-000000000041',
        '1.0-test'
    ),
    '40000000-0000-0000-0000-000000000041'::uuid,
    'a new session is allowed after the rolling fifteen-minute window expires'
);
reset role;

select ok(
    (
        select rowsecurity
        from pg_catalog.pg_tables
        where schemaname = 'public' and tablename = 'patrolgrid_retention_holds'
    )
    and (
        select rowsecurity
        from pg_catalog.pg_tables
        where schemaname = 'public' and tablename = 'patrolgrid_retention_hold_reviews'
    )
    and (
        select rowsecurity
        from pg_catalog.pg_tables
        where schemaname = 'public' and tablename = 'patrolgrid_retention_runs'
    ),
    'legal-hold, hold-review, and purge-ledger tables have row-level security enabled'
);

select * from finish();
rollback;
