import importlib.util
from pathlib import Path
import base64
import json
import pytest

spec=importlib.util.spec_from_file_location('deletion_drill',Path(__file__).parents[1]/'live_account_deletion_drill.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


def key(**changes):
    body={'role':'service_role','ref':m.PROJECT,**changes}
    return 'header.'+base64.urlsafe_b64encode(json.dumps(body).encode()).decode().rstrip('=')+'.signature'


def test_wrong_project_or_role_is_rejected_before_requests():
    for token in (key(ref='another-project'),key(role='authenticated'),'bad'):
        with pytest.raises(m.DrillError): m.Client('public',token)


def test_cleanup_never_deletes_account_without_exact_marker_and_identity(monkeypatch):
    c=m.Client('public',key());f=m.make_fixture();calls=[]
    monkeypatch.setattr(c,'checked',lambda *a,**k:calls.append((a,k)))
    for user in ({'id':f['id'],'email':f['email'],'app_metadata':{'dailybeat_drill':'wrong'}},
                 {'id':f['id'],'email':'another@example.invalid','app_metadata':{'dailybeat_drill':'ours'}}):
        monkeypatch.setattr(c,'admin_user',lambda _,u=user:u)
        with pytest.raises(m.DrillError,match='Cleanup refused'):c.cleanup(f,'ours')
    assert calls==[]


def test_scoped_cleanup_is_idempotent_after_success(monkeypatch):
    c=m.Client('public',key());f=m.make_fixture();calls=[]
    monkeypatch.setattr(c,'admin_user',lambda _:None)
    monkeypatch.setattr(c,'checked',lambda *a,**k:calls.append(a))
    c.cleanup(f,'ours');assert calls==[]


def test_journal_does_not_persist_fixture_passwords_or_tokens():
    fixture=m.make_fixture();state=m.public_state([fixture],'ours')
    assert fixture['password'] not in json.dumps(state)
    assert state['fixtures'][0]['id']==fixture['id']
    assert state['fixtures'][0]['archive_id']==fixture['archive_id']


def test_scoped_cleanup_verifies_account_is_gone(monkeypatch):
    c=m.Client('public',key());f=m.make_fixture();calls=[]
    user={'id':f['id'],'email':f['email'],'app_metadata':{'dailybeat_drill':'ours'}}
    monkeypatch.setattr(c,'admin_user',lambda _:user)
    monkeypatch.setattr(c,'checked',lambda *a,**k:calls.append(a))
    with pytest.raises(m.DrillError,match='did not remove'):c.cleanup(f,'ours')
    assert calls==[('DELETE','/auth/v1/admin/users/'+f['id'])]
