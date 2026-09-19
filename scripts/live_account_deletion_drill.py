#!/usr/bin/env python3
"""Explicit, bounded production deletion drill using only newly generated fixture accounts."""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

PROJECT = 'mrhffxtuzxqzcqicchoj'
ORIGIN = f'https://{PROJECT}.supabase.co'
TABLES = ('dailybeat_backups', 'dailybeat_encrypted_backups', 'dailybeat_backup_versions')


class DrillError(RuntimeError):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def claims(token):
    try:
        part = token.split('.')[1]
        return json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))
    except (IndexError, ValueError, UnicodeDecodeError) as error:
        raise DrillError('Invalid token structure') from error


class Client:
    def __init__(self, public_key, admin_key):
        c = claims(admin_key)
        if c.get('role') != 'service_role' or c.get('ref') != PROJECT:
            raise DrillError('The admin credential must belong to the approved project')
        self.public_key, self.admin_key = public_key, admin_key

    def request(self, method, path, body=None, *, token=None, admin=False):
        if not path.startswith(('/auth/v1/', '/rest/v1/', '/functions/v1/delete-account')) or '\r' in path or '\n' in path:
            raise DrillError('Unsupported request path')
        key = self.admin_key if admin else self.public_key
        headers = {'apikey': key, 'content-type':'application/json'}
        if admin or token:
            headers['authorization'] = 'Bearer ' + (self.admin_key if admin else token)
        payload = None if body is None else json.dumps(body, separators=(',', ':')).encode()
        req = urllib.request.Request(ORIGIN + path, data=payload, headers=headers, method=method)
        try:
            response = urllib.request.build_opener(NoRedirect()).open(req, timeout=30)
        except urllib.error.HTTPError as response:
            status, data = response.code, response.read(1048577)
        except (urllib.error.URLError, TimeoutError) as error:
            raise DrillError('Network request failed; run scoped cleanup from the saved state') from error
        else:
            with response:
                status, data = response.status, response.read(1048577)
        if len(data) > 1048576:
            raise DrillError('Unexpectedly large response')
        try:
            result = json.loads(data) if data else None
        except (ValueError, UnicodeDecodeError):
            result = None
        return status, result

    def checked(self, method, path, body=None, *, expected=(200, 201, 204), **kwargs):
        status, result = self.request(method, path, body, **kwargs)
        if status not in expected:
            raise DrillError(f'Unexpected HTTP {status} during {method} ' + path.split('?')[0])
        return result

    def admin_user(self, user_id):
        if str(uuid.UUID(user_id)) != user_id:
            raise DrillError('Invalid fixture ID')
        status, user = self.request('GET', '/auth/v1/admin/users/' + user_id, admin=True)
        if status == 404:
            return None
        if status != 200 or not isinstance(user, dict):
            raise DrillError('Cannot verify fixture ownership')
        return user

    def cleanup(self, fixture, marker):
        user = self.admin_user(fixture['id'])
        if user is None:
            return
        if user.get('id') != fixture['id'] or user.get('email') != fixture['email'] or user.get('app_metadata', {}).get('dailybeat_drill') != marker:
            raise DrillError('Cleanup refused: account is not this invocation\'s fixture')
        self.checked('DELETE', '/auth/v1/admin/users/' + fixture['id'], admin=True)
        if self.admin_user(fixture['id']) is not None:
            raise DrillError('Fixture cleanup did not remove the account')

    def sign_in(self, fixture):
        result = self.checked('POST', '/auth/v1/token?grant_type=password', {'email': fixture['email'], 'password': fixture['password']})
        if result.get('user', {}).get('id') != fixture['id'] or claims(result['access_token']).get('role') != 'authenticated':
            raise DrillError('Unexpected fixture session identity')
        return result


def make_fixture():
    uid = str(uuid.uuid4())
    return {'id': uid, 'email': f'dailybeat-delete-drill-{uid}@example.invalid', 'password': 'Aa1!' + secrets.token_urlsafe(40), 'archive_id': str(uuid.uuid4())}


def public_state(fixtures, marker):
    return {'project': PROJECT, 'marker': marker, 'fixtures': [{k:v for k,v in f.items() if k != 'password'} for f in fixtures]}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def backup_rows(client, fixture, token):
    result = {t: client.checked('GET', f'/rest/v1/{t}?user_id=eq.{fixture["id"]}', token=token) for t in TABLES}
    result['dailybeat_backup_parts'] = client.checked('GET', f'/rest/v1/dailybeat_backup_parts?version_id=eq.{fixture["archive_id"]}', token=token)
    return result


def put_fixtures(client, fixture, token):
    marker = {'drill': 'synthetic deletion cascade fixture'}
    client.checked('POST', '/rest/v1/dailybeat_backups', {'user_id':fixture['id'], 'snapshot':marker}, token=token)
    envelope = {'format':'dailybeat-encrypted-backup', 'envelopeVersion':1, 'kdf':'PBKDF2-HMAC-SHA256', 'iterations':600000,
                'salt':base64.b64encode(secrets.token_bytes(16)).decode(), 'nonce':base64.b64encode(secrets.token_bytes(12)).decode(),
                'ciphertext':base64.b64encode(secrets.token_bytes(64)).decode()}
    # Opaque fixture bytes test cascading deletion, not cryptography; native recovery CI tests crypto.
    client.checked('POST', '/rest/v1/dailybeat_encrypted_backups', {'user_id':fixture['id'], 'snapshot':envelope}, token=token)
    client.checked('POST', '/rest/v1/rpc/begin_dailybeat_backup', {'backup_id':fixture['archive_id']}, token=token)
    client.checked('POST', '/rest/v1/rpc/put_dailybeat_backup_part', {'backup_id':fixture['archive_id'], 'part_index':0,
                   'part_payload':{'nonce':envelope['nonce'], 'ciphertext':envelope['ciphertext']}}, token=token)
    client.checked('POST', '/rest/v1/rpc/publish_dailybeat_backup', {'backup_id':fixture['archive_id'], 'part_count':1,
                   'backup_manifest':{'format':'dailybeat-archive', 'version':1, 'salt':envelope['salt'], 'sealed':marker}}, token=token)


def run(client, state_file, report_file):
    fixtures, marker = [make_fixture(), make_fixture()], 'dailybeat-' + secrets.token_hex(24)
    # Journal only our preselected UUIDs and a marker before any mutation; no password/token is persisted.
    os.umask(0o077)
    with state_file.open('x') as f:
        json.dump(public_state(fixtures, marker), f)
    result = {'project': PROJECT, 'fixture_accounts': 2, 'real_user_accounts_touched': False, 'checks': []}
    try:
        for fixture in fixtures:
            if client.admin_user(fixture['id']) is not None:
                raise DrillError('Fixture UUID already exists')
            user = client.checked('POST', '/auth/v1/admin/users', {'id':fixture['id'], 'email':fixture['email'],
                'password':fixture['password'], 'email_confirm':True, 'app_metadata':{'dailybeat_drill':marker}}, admin=True)
            if user.get('id') != fixture['id']:
                raise DrillError('Admin API did not preserve the preselected fixture UUID')
        sessions = [client.sign_in(f) for f in fixtures]
        for f,s in zip(fixtures,sessions): put_fixtures(client,f,s['access_token'])
        before = [digest(backup_rows(client,f,s['access_token'])) for f,s in zip(fixtures,sessions)]
        for token in (None, 'invalid.fixture.token'):
            code,_ = client.request('POST','/functions/v1/delete-account', {}, token=token)
            if code != 401: raise DrillError('Unauthenticated or forged deletion was not denied')
        result['checks'].append('missing and forged authentication denied')
        password_times = [a['timestamp'] for a in claims(sessions[0]['access_token']).get('amr',[]) if a.get('method')=='password']
        if not password_times: raise DrillError('The real session has no password authentication record')
        until = max(password_times) + 310
        if until-time.time() > 360: raise DrillError('Unexpected authentication clock skew')
        print('Fixtures created. Waiting for the real password authentication to age past five minutes.', flush=True)
        while time.time() < until:
            time.sleep(min(30, until-time.time()))
        refreshed = client.checked('POST','/auth/v1/token?grant_type=refresh_token', {'refresh_token':sessions[0]['refresh_token']})
        code,_ = client.request('POST','/functions/v1/delete-account', {'user_id':fixtures[1]['id']}, token=refreshed['access_token'])
        if code != 401: raise DrillError('A refreshed old-password session was allowed to delete')
        for f,s,d in zip(fixtures,sessions,before):
            if client.admin_user(f['id']) is None or digest(backup_rows(client,f,s['access_token'])) != d:
                raise DrillError('Denied deletion changed fixture data')
        result['checks'].append('refreshed old-password session denied with both accounts and all backup rows unchanged')
        fresh = client.sign_in(fixtures[0])
        code,_ = client.request('POST','/functions/v1/delete-account', {'user_id':fixtures[1]['id']}, token=fresh['access_token'])
        if code != 200: raise DrillError(f'Fresh-password deletion failed with HTTP {code}')
        if client.admin_user(fixtures[0]['id']) is not None: raise DrillError('Deleted fixture account still exists')
        for table in TABLES:
            if client.checked('GET',f'/rest/v1/{table}?user_id=eq.{fixtures[0]["id"]}',admin=True): raise DrillError('Account deletion left backup rows')
        if client.checked('GET',f'/rest/v1/dailybeat_backup_parts?version_id=eq.{fixtures[0]["archive_id"]}',admin=True): raise DrillError('Account deletion left archive parts')
        if client.admin_user(fixtures[1]['id']) is None or digest(backup_rows(client,fixtures[1],sessions[1]['access_token'])) != before[1]:
            raise DrillError('The unrelated sentinel account was changed')
        for grant,body in [('password',{'email':fixtures[0]['email'],'password':fixtures[0]['password']}),
                           ('refresh_token',{'refresh_token':fresh['refresh_token']})]:
            code,_ = client.request('POST','/auth/v1/token?grant_type='+grant,body)
            if code < 400: raise DrillError('Deleted account can still sign in or refresh')
        result['checks'].extend(['fresh password deletes only the verified caller despite a different body user_id',
                                'legacy backup, encrypted backup, archive versions and parts cascade deleted',
                                'sentinel account and backup payloads unchanged', 'deleted account cannot sign in or refresh'])
        print('Deletion and cascade checks passed. Verifying scoped cleanup.',flush=True)
    finally:
        errors=[]
        for f in fixtures:
            try:client.cleanup(f,marker)
            except Exception as error:errors.append(type(error).__name__)
        result['cleanup_verified']=not errors
        report_file.write_text(json.dumps(result,indent=2)+'\n')
        if errors:raise DrillError('Fixture cleanup needs attention; use the saved state before running another drill')
    result['result']='passed'
    report_file.write_text(json.dumps(result,indent=2)+'\n')
    print('Production deletion drill passed; both disposable accounts and their fixture backups are removed.',flush=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--allow-production-project',required=True,choices=[PROJECT])
    parser.add_argument('--public-key-file',type=Path,required=True)
    parser.add_argument('--admin-key-file',type=Path,required=True)
    parser.add_argument('--state-file',type=Path,required=True)
    parser.add_argument('--report-file',type=Path,required=True)
    parser.add_argument('--cleanup-only',action='store_true')
    args=parser.parse_args()
    client=Client(args.public_key_file.read_text().strip(),args.admin_key_file.read_text().strip())
    if args.cleanup_only:
        state=json.loads(args.state_file.read_text())
        if state['project']!=PROJECT:raise DrillError('Unexpected saved project')
        for f in state['fixtures']:client.cleanup(f,state['marker'])
        print('Scoped fixture cleanup verified.')
    else:run(client,args.state_file,args.report_file)


if __name__=='__main__':
    try:main()
    except DrillError as error:raise SystemExit('Deletion drill failed: '+str(error)) from None
