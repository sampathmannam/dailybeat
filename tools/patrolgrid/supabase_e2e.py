#!/usr/bin/env python3
"""Exercise PatrolGrid through Supabase Auth and PostgREST with real JWT/RLS checks.

The fixture creates synthetic data and intentionally refuses remote hosts unless
PATROLGRID_ALLOW_REMOTE_TEST=1 is set. It never prints credentials or access tokens.
"""

from __future__ import annotations

import json
import os
import secrets
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlparse

import httpx


def require_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value.rstrip("/") if name == "SUPABASE_URL" else value


def assert_status(response: httpx.Response, expected: int, label: str) -> None:
    if response.status_code != expected:
        safe_body = response.text[:600]
        raise AssertionError(
            f"{label}: expected HTTP {expected}, got {response.status_code}: {safe_body}"
        )


class SupabaseHarness:
    def __init__(self, url: str, anon_key: str, service_key: str) -> None:
        self.url = url
        self.anon_key = anon_key
        self.service_key = service_key
        self.client = httpx.Client(timeout=15.0)

    def headers(self, token: str, *, return_rows: bool = False) -> dict[str, str]:
        headers = {
            "apikey": self.anon_key,
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        }
        if return_rows:
            headers["Prefer"] = "return=representation"
        return headers

    def service_headers(self, *, return_rows: bool = False) -> dict[str, str]:
        headers = {
            "apikey": self.service_key,
            "Authorization": f"Bearer {self.service_key}",
            "Content-Type": "application/json",
        }
        if return_rows:
            headers["Prefer"] = "return=representation"
        return headers

    def create_user(self, email: str, password: str) -> str:
        response = self.client.post(
            f"{self.url}/auth/v1/admin/users",
            headers=self.service_headers(),
            json={"email": email, "password": password, "email_confirm": True},
        )
        assert_status(response, 200, "create synthetic user")
        return response.json()["id"]

    def sign_in(self, email: str, password: str) -> str:
        response = self.client.post(
            f"{self.url}/auth/v1/token?grant_type=password",
            headers={"apikey": self.anon_key, "Content-Type": "application/json"},
            json={"email": email, "password": password},
        )
        assert_status(response, 200, "sign in synthetic user")
        return response.json()["access_token"]

    def service_insert(self, table: str, body: object) -> list[dict[str, object]]:
        response = self.client.post(
            f"{self.url}/rest/v1/{table}",
            headers=self.service_headers(return_rows=True),
            json=body,
        )
        assert_status(response, 201, f"seed {table}")
        return response.json()

    def get(self, table: str, token: str, query: str = "select=*") -> httpx.Response:
        return self.client.get(
            f"{self.url}/rest/v1/{table}?{query}",
            headers=self.headers(token),
        )

    def insert(self, table: str, token: str, body: object) -> httpx.Response:
        return self.client.post(
            f"{self.url}/rest/v1/{table}",
            headers=self.headers(token, return_rows=True),
            json=body,
        )


def main() -> int:
    url = require_env("SUPABASE_URL")
    anon_key = require_env("SUPABASE_ANON_KEY")
    service_key = require_env("SUPABASE_SERVICE_ROLE_KEY")
    parsed = urlparse(url)
    local_hosts = {"127.0.0.1", "localhost", "10.0.2.2"}
    if parsed.hostname not in local_hosts and os.getenv("PATROLGRID_ALLOW_REMOTE_TEST") != "1":
        raise RuntimeError(
            "Refusing to seed a remote Supabase project. Set PATROLGRID_ALLOW_REMOTE_TEST=1 "
            "only for an approved disposable staging project."
        )

    run_id = secrets.token_hex(4)
    password = f"Pg!{secrets.token_urlsafe(18)}9z"
    harness = SupabaseHarness(url, anon_key, service_key)

    health = harness.client.get(f"{url}/auth/v1/health", headers={"apikey": anon_key})
    assert_status(health, 200, "Supabase Auth health")

    emails = {
        "supervisor": f"supervisor-{run_id}@patrolgrid.test",
        "patrol_1": f"patrol-1-{run_id}@patrolgrid.test",
        "patrol_2": f"patrol-2-{run_id}@patrolgrid.test",
        "outsider": f"outsider-{run_id}@patrolgrid.test",
    }
    users = {name: harness.create_user(email, password) for name, email in emails.items()}
    tokens = {name: harness.sign_in(email, password) for name, email in emails.items()}

    subdivision_id = str(uuid.uuid4())
    route_id = str(uuid.uuid4())
    mission_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc)

    harness.service_insert(
        "patrolgrid_subdivisions",
        {
            "id": subdivision_id,
            "code": f"LOAD_{run_id.upper()}",
            "name": f"Synthetic subdivision {run_id}",
            "created_by": users["supervisor"],
        },
    )
    harness.service_insert(
        "patrolgrid_memberships",
        [
            {
                "subdivision_id": subdivision_id,
                "user_id": users["supervisor"],
                "role": "supervisor",
                "display_name": "Synthetic Supervisor",
            },
            {
                "subdivision_id": subdivision_id,
                "user_id": users["patrol_1"],
                "role": "patrol",
                "display_name": "Synthetic Patrol One",
            },
            {
                "subdivision_id": subdivision_id,
                "user_id": users["patrol_2"],
                "role": "patrol",
                "display_name": "Synthetic Patrol Two",
            },
        ],
    )
    harness.service_insert(
        "patrolgrid_route_templates",
        {
            "id": route_id,
            "subdivision_id": subdivision_id,
            "name": "Synthetic load route",
            "default_guidance": "suggested_route",
            "route_geojson": {
                "type": "LineString",
                "coordinates": [[77.5000, 13.0000], [77.5100, 13.0100]],
            },
            "created_by": users["supervisor"],
        },
    )
    harness.service_insert(
        "patrolgrid_route_template_priorities",
        {
            "route_template_id": route_id,
            "name": "Synthetic bus stand",
            "latitude": 13.005,
            "longitude": 77.505,
            "sort_order": 0,
        },
    )
    unit_id = str(uuid.uuid4())
    harness.service_insert(
        "patrolgrid_units",
        {
            "id": unit_id,
            "subdivision_id": subdivision_id,
            "name": "Synthetic Unit Two",
            "created_by": users["supervisor"],
        },
    )
    harness.service_insert(
        "patrolgrid_unit_members",
        {"unit_id": unit_id, "user_id": users["patrol_2"]},
    )
    harness.service_insert(
        "patrolgrid_missions",
        {
            "id": mission_id,
            "subdivision_id": subdivision_id,
            "route_template_id": route_id,
            "title": "Synthetic integration mission",
            "starts_at": (now - timedelta(hours=1)).isoformat(),
            "ends_at": (now + timedelta(hours=7)).isoformat(),
            "guidance": "suggested_route",
            "instructions": "Synthetic test data only.",
            "status": "assigned",
            "created_by": users["supervisor"],
        },
    )
    harness.service_insert(
        "patrolgrid_assignments",
        [
            {
                "mission_id": mission_id,
                "user_id": users["patrol_1"],
                "assigned_by": users["supervisor"],
            },
            {
                "mission_id": mission_id,
                "user_id": users["patrol_2"],
                "assigned_by": users["supervisor"],
            },
        ],
    )

    anonymous = harness.client.get(
        f"{url}/rest/v1/patrolgrid_missions?select=id", headers={"apikey": anon_key}
    )
    if anonymous.status_code not in {401, 403}:
        raise AssertionError(f"anonymous mission read unexpectedly returned {anonymous.status_code}")

    patrol_missions = harness.get("patrolgrid_missions", tokens["patrol_1"], "select=id,title")
    assert_status(patrol_missions, 200, "patrol mission read")
    assert [row["id"] for row in patrol_missions.json()] == [mission_id]

    patrol_membership = harness.get(
        "patrolgrid_memberships", tokens["patrol_1"], "select=user_id,role"
    )
    assert_status(patrol_membership, 200, "patrol membership read")
    assert patrol_membership.json() == [{"user_id": users["patrol_1"], "role": "patrol"}]

    outsider_missions = harness.get("patrolgrid_missions", tokens["outsider"], "select=id")
    assert_status(outsider_missions, 200, "outsider mission read")
    assert outsider_missions.json() == []

    forged_mission = harness.insert(
        "patrolgrid_missions",
        tokens["patrol_1"],
        {
            "subdivision_id": subdivision_id,
            "title": "Forged mission",
            "starts_at": now.isoformat(),
            "ends_at": (now + timedelta(hours=1)).isoformat(),
            "guidance": "area_coverage",
            "created_by": users["patrol_1"],
        },
    )
    assert_status(forged_mission, 403, "patrol mission creation denial")

    session_ids: dict[str, str] = {}
    for index, patrol_name in enumerate(("patrol_1", "patrol_2"), start=1):
        session_id = str(uuid.uuid4())
        session_ids[patrol_name] = session_id
        session = harness.insert(
            "patrolgrid_sessions",
            tokens[patrol_name],
            {
                "id": session_id,
                "mission_id": mission_id,
                "user_id": users[patrol_name],
                "installation_id": str(uuid.uuid4()),
                "started_at": now.isoformat(),
                "app_version": "integration-test",
            },
        )
        assert_status(session, 201, f"start {patrol_name} session")
        point = harness.insert(
            "patrolgrid_track_points",
            tokens[patrol_name],
            {
                "client_point_id": str(uuid.uuid4()),
                "session_id": session_id,
                "mission_id": mission_id,
                "user_id": users[patrol_name],
                "sequence_number": 0,
                "recorded_at": now.isoformat(),
                "latitude": 13.0 + (index / 1000),
                "longitude": 77.5 + (index / 1000),
                "accuracy_m": 8.0,
            },
        )
        assert_status(point, 201, f"append {patrol_name} route point")

    own_points = harness.get("patrolgrid_track_points", tokens["patrol_1"], "select=user_id")
    assert_status(own_points, 200, "patrol route read")
    assert own_points.json() == [{"user_id": users["patrol_1"]}]

    supervisor_points = harness.get(
        "patrolgrid_track_points", tokens["supervisor"], "select=user_id"
    )
    assert_status(supervisor_points, 200, "supervisor route read")
    assert {row["user_id"] for row in supervisor_points.json()} == {
        users["patrol_1"],
        users["patrol_2"],
    }

    field_update = harness.insert(
        "patrolgrid_field_updates",
        tokens["patrol_1"],
        {
            "client_update_id": str(uuid.uuid4()),
            "mission_id": mission_id,
            "user_id": users["patrol_1"],
            "category": "observation",
            "detail": "Synthetic gate check completed.",
            "occurred_at": now.isoformat(),
        },
    )
    assert_status(field_update, 201, "submit field update")

    close_response = harness.client.patch(
        f"{url}/rest/v1/patrolgrid_sessions?id=eq.{session_ids['patrol_1']}",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={"ended_at": datetime.now(timezone.utc).isoformat(), "end_reason": "completed"},
    )
    assert_status(close_response, 200, "close patrol session")

    rejected_point = harness.insert(
        "patrolgrid_track_points",
        tokens["patrol_1"],
        {
            "client_point_id": str(uuid.uuid4()),
            "session_id": session_ids["patrol_1"],
            "mission_id": mission_id,
            "user_id": users["patrol_1"],
            "sequence_number": 1,
            "recorded_at": datetime.now(timezone.utc).isoformat(),
            "latitude": 13.003,
            "longitude": 77.503,
            "accuracy_m": 8.0,
        },
    )
    assert_status(rejected_point, 403, "closed-session route point denial")

    audit = harness.get(
        "patrolgrid_audit_events", tokens["supervisor"], "select=event_type&limit=100"
    )
    assert_status(audit, 200, "supervisor audit read")
    event_types = {row["event_type"] for row in audit.json()}
    assert "patrolgrid_missions.insert" in event_types
    assert "patrolgrid_sessions.update" in event_types

    assignment = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_create_assignment",
        headers=harness.headers(tokens["supervisor"], return_rows=True),
        json={
            "target_route_template": route_id,
            "target_unit": unit_id,
            "target_guidance": "area_coverage",
        },
    )
    assert_status(assignment, 200, "atomic supervisor assignment")
    assigned_mission_id = assignment.json()
    generated_assignments = harness.get(
        "patrolgrid_assignments",
        tokens["supervisor"],
        f"select=user_id&mission_id=eq.{assigned_mission_id}",
    )
    assert_status(generated_assignments, 200, "atomic assignment verification")
    assert generated_assignments.json() == [{"user_id": users["patrol_2"]}]

    load_session_id = str(uuid.uuid4())
    load_session = harness.insert(
        "patrolgrid_sessions",
        tokens["patrol_1"],
        {
            "id": load_session_id,
            "mission_id": mission_id,
            "user_id": users["patrol_1"],
            "installation_id": str(uuid.uuid4()),
            "started_at": datetime.now(timezone.utc).isoformat(),
            "app_version": "locust-test",
        },
    )
    assert_status(load_session, 201, "start load-test session")

    context_path = Path(
        os.getenv("PATROLGRID_LOAD_CONTEXT", "supabase/.temp/patrolgrid-load-context.json")
    )
    context_path.parent.mkdir(parents=True, exist_ok=True)
    context_path.write_text(
        json.dumps(
            {
                "url": url,
                "anon_key": anon_key,
                "access_token": tokens["patrol_1"],
                "mission_id": mission_id,
                "session_id": load_session_id,
                "user_id": users["patrol_1"],
            }
        ),
        encoding="utf-8",
    )
    os.chmod(context_path, 0o600)

    print("PatrolGrid Supabase E2E passed: auth, RLS isolation, atomic assignment, patrol writes, session closure, and audit trail.")
    print(f"Synthetic Locust context written to {context_path} (mode 0600; ignored by git).")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, RuntimeError, httpx.HTTPError) as error:
        print(f"PatrolGrid Supabase E2E failed: {error}", file=sys.stderr)
        raise SystemExit(1)
