-- Metadata only: no user IDs, diary content, backup payloads or credentials are read.
with objects as (
  select 'table:' || c.relname as component, jsonb_build_object(
    'rls', c.relrowsecurity,
    'columns', (select jsonb_agg(jsonb_build_array(a.attname,format_type(a.atttypid,a.atttypmod),a.attnotnull,pg_get_expr(d.adbin,d.adrelid)) order by a.attnum)
      from pg_attribute a left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum
      where a.attrelid=c.oid and a.attnum>0 and not a.attisdropped),
    'constraints',(select jsonb_agg(jsonb_build_array(conname,convalidated,pg_get_constraintdef(oid)) order by conname) from pg_constraint where conrelid=c.oid),
    'indexes',(select jsonb_agg(pg_get_indexdef(indexrelid) order by pg_get_indexdef(indexrelid)) from pg_index where indrelid=c.oid),
    'policies',(select jsonb_agg(jsonb_build_array(policyname,permissive,roles,cmd,qual,with_check) order by policyname) from pg_policies where schemaname='public' and tablename=c.relname),
    'triggers',(select jsonb_agg(pg_get_triggerdef(oid) order by tgname) from pg_trigger where tgrelid=c.oid and not tgisinternal),
    'grants',(select jsonb_agg(jsonb_build_array(r,p,has_table_privilege(r,c.oid,p)) order by r,p) from unnest(array['anon','authenticated']) r cross join unnest(array['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','TRIGGER','REFERENCES']) p)
  ) as definition
  from pg_class c join pg_namespace n on n.oid=c.relnamespace
  where n.nspname='public' and c.relkind='r' and c.relname in ('dailybeat_backups','dailybeat_encrypted_backups','dailybeat_backup_versions','dailybeat_backup_parts')
  union all
  select 'function:' || p.proname, jsonb_build_object('body',regexp_replace(btrim(p.prosrc),'\s+',' ','g'),'config',p.proconfig,
    'security_definer',p.prosecdef,'arguments',pg_get_function_identity_arguments(p.oid),'result',pg_get_function_result(p.oid),
    'anon_execute',has_function_privilege('anon',p.oid,'EXECUTE'),'auth_execute',has_function_privilege('authenticated',p.oid,'EXECUTE'))
  from pg_proc p join pg_namespace n on n.oid=p.pronamespace
  where n.nspname='public' and p.proname in ('set_dailybeat_backup_updated_at','begin_dailybeat_backup','put_dailybeat_backup_part','publish_dailybeat_backup')
)
select component, md5(definition::text) as fingerprint from objects order by component;
