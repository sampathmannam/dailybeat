#!/usr/bin/env python3
"""Restore synthetic DailyBeat/Auth records into a newly created local database only."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class DrillError(RuntimeError):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_target(origin: str, container: str):
    if not re.fullmatch(r'supabase_db_dailybeat-hardening-[0-9]+', container):
        raise DrillError('Only the isolated DailyBeat hardening container is allowed')
    url=urllib.parse.urlsplit(origin)
    if url.scheme!='http' or url.hostname not in {'127.0.0.1','localhost'} or url.username or url.password or url.path or url.query or url.fragment:
        raise DrillError('Only an isolated loopback API is allowed')


def run(status_file: Path, container: str, output: Path):
    config=json.loads(status_file.read_text())
    origin=config['API_URL'].rstrip('/')
    validate_target(origin, container)
    public=config['ANON_KEY'];admin=config['SERVICE_ROLE_KEY']
    uid=str(uuid.uuid4());archive=str(uuid.uuid4());marker='dailybeat-local-drill-'+secrets.token_hex(20)
    email=f'{marker}@example.invalid';password='Aa1!'+secrets.token_urlsafe(40)
    database='dailybeat_restore_'+uuid.uuid4().hex
    os.umask(0o077);output.mkdir(parents=True,exist_ok=False)
    report={'environment':'isolated local Supabase, synthetic data only','remote_project_accessed':False,'restore_database':database,'result':'failed'}
    created=False;attempted_user=False

    def request(method,path,body=None,token=None,privileged=False,allow_missing=False):
        headers={'apikey':admin if privileged else public,'content-type':'application/json'}
        if token or privileged:headers['authorization']='Bearer '+(admin if privileged else token)
        req=urllib.request.Request(origin+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        try:
            with urllib.request.build_opener(NoRedirect()).open(req,timeout=30) as response:
                data=response.read(1048577);status=response.status
        except urllib.error.HTTPError as error:
            if allow_missing and error.code == 404:
                return None
            raise DrillError(f'Local HTTP {error.code} during {method} '+path.split('?')[0]) from None
        if len(data)>1048576:raise DrillError('Unexpected response size')
        return json.loads(data) if data else None

    def sql(db,text,role='postgres'):
        return subprocess.check_output(['docker','exec','-i',container,'psql','-X','-v','ON_ERROR_STOP=1','-U',role,'-d',db,'-A','-t'],input=text,text=True,stderr=subprocess.PIPE).strip()

    fingerprint_sql='\n'.join([
        "select 'users|'||md5(coalesce(jsonb_agg(to_jsonb(t) order by id)::text,'[]')) from auth.users t where id='"+uid+"';",
        "select 'identities|'||md5(coalesce(jsonb_agg(to_jsonb(t) order by id)::text,'[]')) from auth.identities t where user_id='"+uid+"';",
        *["select '"+table+"|'||md5(coalesce(jsonb_agg(to_jsonb(t) order by "+order+")::text,'[]')) from public."+table+" t where "+column+"='"+value+"';"
          for table,order,column,value in [('dailybeat_backups','user_id','user_id',uid),('dailybeat_encrypted_backups','user_id','user_id',uid),
                ('dailybeat_backup_versions','id','user_id',uid),('dailybeat_backup_parts','part_index','version_id',archive)]]])
    try:
        if sql('postgres',"select count(*) from pg_database where datname='"+database+"';")!='0':raise DrillError('Target database already exists')
        attempted_user=True
        user=request('POST','/auth/v1/admin/users',{'id':uid,'email':email,'password':password,'email_confirm':True,'app_metadata':{'dailybeat_drill':marker}},privileged=True)
        if user.get('id')!=uid:raise DrillError('Unexpected local fixture identity')
        if sql('postgres',"select count(*) from auth.users where id='"+uid+"';") != '1':
            raise DrillError('API and Docker container do not address the same isolated database')
        session=request('POST','/auth/v1/token?grant_type=password',{'email':email,'password':password});token=session['access_token']
        request('POST','/rest/v1/dailybeat_backups',{'user_id':uid,'snapshot':{'synthetic':True,'notes':[{'index':i,'text':'Recovery fixture'} for i in range(100)]}},token)
        envelope={'format':'dailybeat-encrypted-backup','envelopeVersion':1,'kdf':'PBKDF2-HMAC-SHA256','iterations':600000,'salt':'synthetic','nonce':'synthetic','ciphertext':'synthetic-opaque-fixture-'*64}
        request('POST','/rest/v1/dailybeat_encrypted_backups',{'user_id':uid,'snapshot':envelope},token)
        request('POST','/rest/v1/rpc/begin_dailybeat_backup',{'backup_id':archive},token)
        for i in range(8):request('POST','/rest/v1/rpc/put_dailybeat_backup_part',{'backup_id':archive,'part_index':i,'part_payload':{'nonce':'synthetic','ciphertext':str(i)+'-synthetic-page-'*4096}},token)
        request('POST','/rest/v1/rpc/publish_dailybeat_backup',{'backup_id':archive,'part_count':8,'backup_manifest':{'format':'dailybeat-archive','version':1,'salt':'synthetic','sealed':{'fixture':True}}},token)
        before=sql('postgres',fingerprint_sql)
        schema=Path(__file__).with_name('production_schema_fingerprint.sql').read_text()
        source_schema=sql('postgres',schema)
        start=time.monotonic();dump=output/'synthetic-database.dump'
        with dump.open('wb') as handle:
            subprocess.run(['docker','exec',container,'pg_dump','-U','postgres','-d','postgres','--format=custom','--schema=public','--schema=auth','--schema=supabase_migrations'],stdout=handle,stderr=subprocess.PIPE,check=True)
        report['dump_seconds']=round(time.monotonic()-start,3)
        sql('postgres','CREATE DATABASE '+database+' TEMPLATE template0;');created=True
        # The target is brand new; pg_restore --clean tries dropping policies on absent tables.
        sql(database,'DROP SCHEMA public;')
        start=time.monotonic()
        with dump.open('rb') as handle:
            # Restoring Auth default privileges needs the existing local cluster superuser.
            # No role or permission is created, and no remote database is accepted.
            # Preserve original ownership: --no-owner would remove Auth's implicit owner grants.
            subprocess.run(['docker','exec','-i',container,'pg_restore','-U','supabase_admin','--dbname='+database,'--single-transaction','--exit-on-error'],stdin=handle,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
        report['restore_seconds']=round(time.monotonic()-start,3)
        if before!=sql(database,fingerprint_sql):raise DrillError('Restored Auth or backup payload differs')
        if source_schema!=sql(database,schema):raise DrillError('Restored schema, RLS, grants or functions differ')
        try:
            auth_access=sql(database,"BEGIN; SET LOCAL ROLE supabase_auth_admin; SELECT count(*) FROM auth.users WHERE id='"+uid+"'; UPDATE auth.users SET updated_at=updated_at WHERE id='"+uid+"'; ROLLBACK;",role='supabase_admin')
        except subprocess.CalledProcessError:
            raise DrillError('Restored Auth service role lacks required table access') from None
        if '\n1\n' not in '\n'+auth_access+'\n' or 'UPDATE 1' not in auth_access:
            raise DrillError('Restored Auth service role cannot read and update its fixture')
        visible=sql(database,"BEGIN; SET LOCAL ROLE authenticated; SELECT set_config('request.jwt.claim.sub','"+uid+"',true); SELECT count(*) FROM public.dailybeat_backup_parts; ROLLBACK;")
        if '\n8\n' not in '\n'+visible+'\n':raise DrillError('Restored owner cannot read all archive pages')
        denied=sql(database,"BEGIN; SET LOCAL ROLE authenticated; SELECT set_config('request.jwt.claim.sub','"+str(uuid.uuid4())+"',true); SELECT count(*) FROM public.dailybeat_backup_parts; ROLLBACK;")
        if '\n0\n' not in '\n'+denied+'\n':raise DrillError('Restored RLS exposed another owner\'s pages')
        report.update(result='passed',schema_and_grants_match=True,auth_and_backup_payloads_match=True,archive_pages_restored=8,
                      owner_access_verified=True,cross_user_access_denied=True,dump_sha256=hashlib.sha256(dump.read_bytes()).hexdigest(),
                      auth_service_role_access_verified=True,
                      limitations='Synthetic application/Auth schema restore on a pre-provisioned local Supabase cluster; not a production backup, provider outage drill or production RTO guarantee.')
    finally:
        cleanup_errors=[]
        if created:
            try:
                sql('postgres','DROP DATABASE '+database+' WITH (FORCE);')
            except Exception:
                cleanup_errors.append('restore database cleanup failed')
        if attempted_user:
            try:
                user=request('GET','/auth/v1/admin/users/'+uid,privileged=True,allow_missing=True)
                if user is not None:
                    if user.get('id')!=uid or user.get('email')!=email or user.get('app_metadata',{}).get('dailybeat_drill')!=marker:
                        raise DrillError('Refusing cleanup of an unexpected account')
                    request('DELETE','/auth/v1/admin/users/'+uid,privileged=True)
                    if request('GET','/auth/v1/admin/users/'+uid,privileged=True,allow_missing=True) is not None:
                        raise DrillError('Local fixture remains after cleanup')
            except Exception:
                cleanup_errors.append('fixture cleanup failed')
        report['local_fixture_cleanup_completed']=not cleanup_errors
        if cleanup_errors:
            report['result']='failed'
            report['cleanup_errors']=cleanup_errors
        (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        if cleanup_errors:
            raise DrillError('Local cleanup needs attention; inspect the private report before retrying')
    print(json.dumps(report,indent=2))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--local-status',required=True,type=Path);p.add_argument('--container',required=True);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
    try:run(a.local_status,a.container,a.output)
    except subprocess.CalledProcessError:
        raise SystemExit('Local database command failed; inspect the isolated database and private report.') from None
    except DrillError as error:
        raise SystemExit(str(error)) from None


if __name__=='__main__':main()
