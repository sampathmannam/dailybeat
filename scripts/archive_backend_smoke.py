#!/usr/bin/env python3
"""Exercise archive RPCs through local Auth/PostgREST using a disposable account, then delete it."""
import json, sys, uuid, urllib.request, urllib.error, urllib.parse


def main(config):
    base = config["API_URL"]
    url = urllib.parse.urlparse(base)
    if url.scheme != "http" or url.hostname not in {"127.0.0.1", "localhost"}:
        raise ValueError("This smoke test must run against an isolated local Supabase stack")
    anon, admin = config["ANON_KEY"], config["SERVICE_ROLE_KEY"]
    def request(path, token, payload=None, method=None):
        data = None if payload is None else json.dumps(payload).encode()
        req = urllib.request.Request(base+path,data=data,method=method or ("GET" if data is None else "POST"),
            headers={"apikey":anon,"Authorization":"Bearer "+token,"Content-Type":"application/json"})
        try:
            with urllib.request.urlopen(req,timeout=30) as response:
                body = response.read(); return response.status, json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            return error.code, None
    password = uuid.uuid4().hex + uuid.uuid4().hex
    email = f"dailybeat-archive-{uuid.uuid4().hex}@example.invalid"
    status,user = request("/auth/v1/admin/users",admin,{"email":email,"password":password,"email_confirm":True})
    assert status in {200,201}, "Could not create local fixture"
    try:
        status,session=request("/auth/v1/token?grant_type=password",anon,{"email":email,"password":password})
        assert status==200
        token=session["access_token"]
        for _ in range(6):
            identifier=str(uuid.uuid4())
            assert request("/rest/v1/rpc/begin_dailybeat_backup",token,{"backup_id":identifier})[0]==204
            part={"backup_id":identifier,"part_index":0,"part_payload":{"nonce":"fixture","ciphertext":"encrypted-fixture"}}
            assert request("/rest/v1/rpc/put_dailybeat_backup_part",token,part)[0]==204
            assert request("/rest/v1/rpc/put_dailybeat_backup_part",token,part)[0]==204
            manifest={"backup_id":identifier,"backup_manifest":{"format":"dailybeat-archive","version":1,"salt":"fixture","sealed":{}},"part_count":2}
            assert request("/rest/v1/rpc/publish_dailybeat_backup",token,manifest)[0]>=400
            manifest["part_count"]=1
            assert request("/rest/v1/rpc/publish_dailybeat_backup",token,manifest)[0]==204
            assert request("/rest/v1/rpc/publish_dailybeat_backup",token,manifest)[0]==204
            # A cleanup after an uncertain publish response must preserve the completed version.
            assert request(f"/rest/v1/dailybeat_backup_versions?id=eq.{identifier}&manifest=is.null",token,method="DELETE")[0]==204
        status,versions=request("/rest/v1/dailybeat_backup_versions?select=id,manifest&manifest=not.is.null",token)
        assert status==200 and len(versions)==5, "Completed-version retention failed"
        assert request("/rest/v1/dailybeat_backup_versions?select=id",anon)[0] in {401,403}
        print("Archive HTTP smoke passed: login, upload, retry, publish, retention, safe cleanup, anonymous denial")
    finally:
        status,_=request("/auth/v1/admin/users/"+user["id"],admin,method="DELETE")
        assert status in {200,204}, "Local fixture cleanup failed"

if __name__=="__main__": main(json.load(sys.stdin))
