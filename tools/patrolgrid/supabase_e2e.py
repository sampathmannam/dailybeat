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
    if (
        parsed.hostname not in local_hosts
        and os.getenv("PATROLGRID_ALLOW_REMOTE_TEST") != "1"
    ):
        raise RuntimeError(
            "Refusing to seed a remote Supabase project. Set PATROLGRID_ALLOW_REMOTE_TEST=1 "
            "only for an approved disposable staging project."
        )

    run_id = secrets.token_hex(4)
    password = f"Pg!{secrets.token_urlsafe(18)}9z"
    harness = SupabaseHarness(url, anon_key, service_key)

    health = harness.client.get(f"{url}/auth/v1/health", headers={"apikey": anon_key})
    assert_status(health, 200, "Supabase Auth health")

    public_signup = harness.client.post(
        f"{url}/auth/v1/signup",
        headers={"apikey": anon_key, "Content-Type": "application/json"},
        json={
            "email": f"self-register-{run_id}@patrolgrid.test",
            "password": password,
        },
    )
    if public_signup.status_code not in {400, 422}:
        raise AssertionError(
            "public staff registration must be disabled; "
            f"got HTTP {public_signup.status_code}"
        )

    emails = {
        "supervisor": f"supervisor-{run_id}@patrolgrid.test",
        "patrol_1": f"patrol-1-{run_id}@patrolgrid.test",
        "patrol_2": f"patrol-2-{run_id}@patrolgrid.test",
        "load_patrol": f"load-patrol-{run_id}@patrolgrid.test",
        "outsider": f"outsider-{run_id}@patrolgrid.test",
    }
    users = {
        name: harness.create_user(email, password) for name, email in emails.items()
    }
    tokens = {name: harness.sign_in(email, password) for name, email in emails.items()}

    subdivision_id = str(uuid.uuid4())
    route_id = str(uuid.uuid4())
    mission_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc)
    route_geojson = {
        "type": "LineString",
        "coordinates": [[77.5000, 13.0000], [77.5100, 13.0100]],
    }

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
            {
                "subdivision_id": subdivision_id,
                "user_id": users["load_patrol"],
                "role": "patrol",
                "display_name": "Synthetic Load Patrol",
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
            "route_geojson": route_geojson,
            "created_by": users["supervisor"],
        },
    )
    invalid_route = harness.client.post(
        f"{url}/rest/v1/patrolgrid_route_templates",
        headers=harness.service_headers(return_rows=True),
        json={
            "subdivision_id": subdivision_id,
            "name": "Synthetic invalid route",
            "default_guidance": "suggested_route",
            "route_geojson": {
                "type": "LineString",
                "coordinates": [[181.0, 13.0], [77.51, 13.01]],
            },
            "created_by": users["supervisor"],
        },
    )
    assert_status(invalid_route, 400, "reject out-of-range route geometry")
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
    priority_location_id = str(uuid.uuid4())
    harness.service_insert(
        "patrolgrid_priority_locations",
        {
            "id": priority_location_id,
            "mission_id": mission_id,
            "name": "Synthetic mission checkpoint",
            "latitude": 13.005,
            "longitude": 77.505,
            "sort_order": 0,
        },
    )

    anonymous = harness.client.get(
        f"{url}/rest/v1/patrolgrid_missions?select=id", headers={"apikey": anon_key}
    )
    if anonymous.status_code not in {401, 403}:
        raise AssertionError(
            f"anonymous mission read unexpectedly returned {anonymous.status_code}"
        )

    patrol_missions = harness.get(
        "patrolgrid_missions", tokens["patrol_1"], "select=id,title"
    )
    assert_status(patrol_missions, 200, "patrol mission read")
    assert [row["id"] for row in patrol_missions.json()] == [mission_id]

    patrol_membership = harness.get(
        "patrolgrid_memberships", tokens["patrol_1"], "select=user_id,role"
    )
    assert_status(patrol_membership, 200, "patrol membership read")
    assert patrol_membership.json() == [
        {"user_id": users["patrol_1"], "role": "patrol"}
    ]

    outsider_missions = harness.get(
        "patrolgrid_missions", tokens["outsider"], "select=id"
    )
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
        installation_id = str(uuid.uuid4())
        direct_session = harness.insert(
            "patrolgrid_sessions",
            tokens[patrol_name],
            {
                "id": session_id,
                "mission_id": mission_id,
                "user_id": users[patrol_name],
                "installation_id": installation_id,
                "started_at": now.isoformat(),
                "app_version": "integration-test",
            },
        )
        assert_status(direct_session, 403, f"deny direct {patrol_name} session insert")

        session_started_after = datetime.now(timezone.utc)
        session = harness.client.post(
            f"{url}/rest/v1/rpc/patrolgrid_start_session",
            headers=harness.headers(tokens[patrol_name], return_rows=True),
            json={
                "target_session": session_id,
                "target_mission": mission_id,
                "target_installation": installation_id,
                "target_app_version": "integration-test",
            },
        )
        assert_status(session, 200, f"start {patrol_name} session workflow")
        assert session.json() == session_id

        started_session = harness.get(
            "patrolgrid_sessions",
            tokens[patrol_name],
            f"select=id,user_id,started_at&id=eq.{session_id}",
        )
        assert_status(
            started_session, 200, f"read {patrol_name} server-started session"
        )
        started_rows = started_session.json()
        assert len(started_rows) == 1
        assert started_rows[0]["user_id"] == users[patrol_name]
        server_started_at = datetime.fromisoformat(
            started_rows[0]["started_at"].replace("Z", "+00:00")
        )
        assert session_started_after - timedelta(seconds=2) <= server_started_at
        assert server_started_at <= datetime.now(timezone.utc) + timedelta(seconds=2)

        retry = harness.client.post(
            f"{url}/rest/v1/rpc/patrolgrid_start_session",
            headers=harness.headers(tokens[patrol_name], return_rows=True),
            json={
                "target_session": session_id,
                "target_mission": mission_id,
                "target_installation": installation_id,
                "target_app_version": "integration-test",
            },
        )
        assert_status(retry, 200, f"retry {patrol_name} session workflow")
        assert retry.json() == session_id
        point_body = {
            "client_point_id": str(uuid.uuid4()),
            "session_id": session_id,
            "mission_id": mission_id,
            "user_id": users[patrol_name],
            "sequence_number": 0,
            "recorded_at": now.isoformat(),
            "latitude": 13.0 + (index / 1000),
            "longitude": 77.5 + (index / 1000),
            "accuracy_m": 8.0,
        }
        direct_point = harness.insert(
            "patrolgrid_track_points",
            tokens[patrol_name],
            point_body,
        )
        assert_status(direct_point, 403, f"deny direct {patrol_name} route insert")
        point = harness.client.post(
            f"{url}/rest/v1/rpc/patrolgrid_ingest_track_points",
            headers=harness.headers(tokens[patrol_name], return_rows=True),
            json={
                "target_session": session_id,
                "target_points": [
                    {
                        key: value
                        for key, value in point_body.items()
                        if key
                        in {
                            "client_point_id",
                            "sequence_number",
                            "recorded_at",
                            "latitude",
                            "longitude",
                            "accuracy_m",
                        }
                    }
                ],
            },
        )
        assert_status(point, 200, f"ingest {patrol_name} route batch")
        assert point.json() == 1

    visit_id = str(uuid.uuid4())
    visit_at = datetime.now(timezone.utc).isoformat()
    direct_visit = harness.insert(
        "patrolgrid_priority_visits",
        tokens["patrol_1"],
        {
            "id": visit_id,
            "priority_location_id": priority_location_id,
            "mission_id": mission_id,
            "user_id": users["patrol_1"],
            "visited_at": visit_at,
            "method": "manual_with_context",
        },
    )
    assert_status(direct_visit, 403, "deny direct priority-visit insert")
    visit_payload = {
        "target_session": session_ids["patrol_1"],
        "target_visit": visit_id,
        "target_priority_location": priority_location_id,
        "target_visited_at": visit_at,
        "target_method": "manual_with_context",
        "target_note": "Synthetic checkpoint confirmation.",
    }
    priority_visit = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_record_priority_visit",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json=visit_payload,
    )
    assert_status(priority_visit, 200, "record priority visit workflow")
    assert priority_visit.json() == visit_id
    priority_retry = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_record_priority_visit",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json=visit_payload,
    )
    assert_status(priority_retry, 200, "retry priority visit workflow")
    assert priority_retry.json() == visit_id

    own_visit = harness.get(
        "patrolgrid_priority_visits",
        tokens["patrol_1"],
        f"select=id,session_id,user_id&id=eq.{visit_id}",
    )
    assert_status(own_visit, 200, "read exact priority-visit session provenance")
    assert own_visit.json() == [
        {
            "id": visit_id,
            "session_id": session_ids["patrol_1"],
            "user_id": users["patrol_1"],
        }
    ]

    own_sources = harness.get(
        "patrolgrid_evidence_session_summaries",
        tokens["patrol_1"],
        f"select=session_id,user_id,track_point_count&mission_id=eq.{mission_id}",
    )
    assert_status(own_sources, 200, "read own exact-session evidence source")
    assert own_sources.json() == [
        {
            "session_id": session_ids["patrol_1"],
            "user_id": users["patrol_1"],
            "track_point_count": 1,
        }
    ]

    supervisor_sources = harness.get(
        "patrolgrid_evidence_session_summaries",
        tokens["supervisor"],
        f"select=session_id,user_id,track_point_count&mission_id=eq.{mission_id}",
    )
    assert_status(supervisor_sources, 200, "read distinct supervised evidence sources")
    assert {
        (row["session_id"], row["user_id"], row["track_point_count"])
        for row in supervisor_sources.json()
    } == {
        (session_ids["patrol_1"], users["patrol_1"], 1),
        (session_ids["patrol_2"], users["patrol_2"], 1),
    }

    outsider_sources = harness.get(
        "patrolgrid_evidence_session_summaries",
        tokens["outsider"],
        f"select=session_id&mission_id=eq.{mission_id}",
    )
    assert_status(outsider_sources, 200, "read cross-subdivision evidence isolation")
    assert outsider_sources.json() == []

    exact_session_points = harness.get(
        "patrolgrid_track_points",
        tokens["supervisor"],
        "select=session_id,user_id"
        f"&session_id=eq.{session_ids['patrol_1']}",
    )
    assert_status(exact_session_points, 200, "read one exact supervised route trail")
    assert exact_session_points.json() == [
        {
            "session_id": session_ids["patrol_1"],
            "user_id": users["patrol_1"],
        }
    ]

    own_points = harness.get(
        "patrolgrid_track_points", tokens["patrol_1"], "select=user_id"
    )
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

    field_update_id = str(uuid.uuid4())
    field_update_at = datetime.now(timezone.utc).isoformat()
    direct_field_update = harness.insert(
        "patrolgrid_field_updates",
        tokens["patrol_1"],
        {
            "client_update_id": field_update_id,
            "mission_id": mission_id,
            "user_id": users["patrol_1"],
            "category": "observation",
            "detail": "Synthetic gate check completed.",
            "occurred_at": field_update_at,
        },
    )
    assert_status(direct_field_update, 403, "deny direct field-update insert")
    field_update = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_record_field_update",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={
            "target_client_update": field_update_id,
            "target_session": session_ids["patrol_1"],
            "target_category": "observation",
            "target_detail": "Synthetic gate check completed.",
            "target_occurred_at": field_update_at,
        },
    )
    assert_status(field_update, 200, "submit field update workflow")
    assert isinstance(field_update.json(), str)

    direct_close_response = harness.client.patch(
        f"{url}/rest/v1/patrolgrid_sessions?id=eq.{session_ids['patrol_1']}",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={
            "ended_at": datetime.now(timezone.utc).isoformat(),
            "end_reason": "completed",
        },
    )
    assert_status(direct_close_response, 403, "deny direct patrol session update")

    close_requested_after = datetime.now(timezone.utc)
    close_response = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_end_session",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={"target_session": session_ids["patrol_1"], "target_reason": "completed"},
    )
    assert_status(close_response, 200, "close patrol session workflow")
    server_ended_at = datetime.fromisoformat(
        close_response.json().replace("Z", "+00:00")
    )
    assert close_requested_after - timedelta(seconds=2) <= server_ended_at
    assert server_ended_at <= datetime.now(timezone.utc) + timedelta(seconds=2)

    close_retry = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_end_session",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={"target_session": session_ids["patrol_1"], "target_reason": "completed"},
    )
    assert_status(close_retry, 200, "retry patrol session closure")
    assert close_retry.json() == close_response.json()

    peer_close = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_end_session",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={"target_session": session_ids["patrol_2"], "target_reason": "completed"},
    )
    assert_status(peer_close, 403, "deny peer patrol session closure")

    sealed_session_point = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_ingest_track_points",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={
            "target_session": session_ids["patrol_1"],
            "target_points": [
                {
                    "client_point_id": str(uuid.uuid4()),
                    "sequence_number": 1,
                    "recorded_at": datetime.now(timezone.utc).isoformat(),
                    "latitude": 13.003,
                    "longitude": 77.503,
                    "accuracy_m": 8.0,
                }
            ],
        },
    )
    assert_status(sealed_session_point, 200, "recently sealed route upload")
    assert sealed_session_point.json() == 1

    final_close_response = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_end_session",
        headers=harness.headers(tokens["patrol_2"], return_rows=True),
        json={"target_session": session_ids["patrol_2"], "target_reason": "completed"},
    )
    assert_status(final_close_response, 200, "close final patrol session workflow")

    retained_mission = harness.get(
        "patrolgrid_missions",
        tokens["supervisor"],
        f"select=id,status,closed_at,retention_until&id=eq.{mission_id}",
    )
    assert_status(retained_mission, 200, "read server-owned mission retention clock")
    retained_rows = retained_mission.json()
    assert len(retained_rows) == 1
    retained_row = retained_rows[0]
    assert retained_row["status"] == "needs_review"
    mission_closed_at = datetime.fromisoformat(
        retained_row["closed_at"].replace("Z", "+00:00")
    )
    mission_retention_until = datetime.fromisoformat(
        retained_row["retention_until"].replace("Z", "+00:00")
    )
    assert mission_retention_until - mission_closed_at == timedelta(hours=8760)

    prohibited_reopen = harness.client.patch(
        f"{url}/rest/v1/patrolgrid_missions?id=eq.{mission_id}",
        headers=harness.service_headers(return_rows=True),
        json={"status": "active"},
    )
    if prohibited_reopen.is_success:
        raise AssertionError("service-role terminal mission reopen unexpectedly succeeded")
    reopen_error = prohibited_reopen.json()
    assert reopen_error.get("code") == "55000"

    retained_after_reopen = harness.get(
        "patrolgrid_missions",
        tokens["supervisor"],
        f"select=status,closed_at,retention_until&id=eq.{mission_id}",
    )
    assert_status(retained_after_reopen, 200, "verify rejected reopen preserved clock")
    assert retained_after_reopen.json() == [
        {
            "status": "needs_review",
            "closed_at": retained_row["closed_at"],
            "retention_until": retained_row["retention_until"],
        }
    ]

    review_ready_mission = harness.get(
        "patrolgrid_missions",
        tokens["supervisor"],
        f"select=id,status,version&id=eq.{mission_id}",
    )
    assert_status(review_ready_mission, 200, "read review-ready mission")
    review_ready_rows = review_ready_mission.json()
    assert len(review_ready_rows) == 1
    review_ready = review_ready_rows[0]
    assert review_ready["status"] == "needs_review"
    expected_version = review_ready["version"]

    rejected_review = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_submit_review",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={
            "target_mission": mission_id,
            "target_expected_version": expected_version,
            "target_outcome": "approved",
            "target_notes": "Synthetic unauthorized review attempt.",
        },
    )
    assert_status(rejected_review, 400, "patrol supervisor-review denial")

    direct_supervisor_review = harness.insert(
        "patrolgrid_reviews",
        tokens["supervisor"],
        {
            "mission_id": mission_id,
            "reviewer_id": users["supervisor"],
            "outcome": "approved",
            "notes": "Synthetic direct-insert bypass attempt.",
        },
    )
    assert_status(
        direct_supervisor_review, 403, "direct supervisor review insert denial"
    )

    reviews_before_rpc = harness.get(
        "patrolgrid_reviews",
        tokens["supervisor"],
        f"select=id&mission_id=eq.{mission_id}",
    )
    assert_status(reviews_before_rpc, 200, "verify direct review denial")
    assert reviews_before_rpc.json() == []

    needs_context_review = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_submit_review",
        headers=harness.headers(tokens["supervisor"], return_rows=True),
        json={
            "target_mission": mission_id,
            "target_expected_version": expected_version,
            "target_outcome": "needs_context",
            "target_notes": "Explain the synthetic route deviation.",
        },
    )
    assert_status(needs_context_review, 200, "request patrol review context")
    context_request_version = needs_context_review.json()
    assert context_request_version == expected_version + 1

    latest_context_review = harness.get(
        "patrolgrid_reviews",
        tokens["supervisor"],
        f"select=id,outcome,notes&mission_id=eq.{mission_id}"
        "&order=reviewed_at.desc,created_at.desc,id.desc&limit=1",
    )
    assert_status(latest_context_review, 200, "read latest context request")
    latest_context_rows = latest_context_review.json()
    assert len(latest_context_rows) == 1
    latest_context = latest_context_rows[0]
    assert latest_context["outcome"] == "needs_context"
    assert latest_context["notes"] == "Explain the synthetic route deviation."
    context_review_id = latest_context["id"]

    wrong_review_mission_id = str(uuid.uuid4())
    wrong_review_id = str(uuid.uuid4())
    harness.service_insert(
        "patrolgrid_missions",
        {
            "id": wrong_review_mission_id,
            "subdivision_id": subdivision_id,
            "title": "Synthetic unrelated review mission",
            "starts_at": (now - timedelta(hours=2)).isoformat(),
            "ends_at": (now + timedelta(hours=6)).isoformat(),
            "guidance": "area_coverage",
            "instructions": "Synthetic wrong-link fixture only.",
            "status": "needs_review",
            "created_by": users["supervisor"],
        },
    )
    harness.service_insert(
        "patrolgrid_reviews",
        {
            "id": wrong_review_id,
            "mission_id": wrong_review_mission_id,
            "reviewer_id": users["supervisor"],
            "outcome": "needs_context",
            "notes": "Synthetic review belonging to a different mission.",
        },
    )

    missing_review_link = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_record_field_update",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={
            "target_client_update": str(uuid.uuid4()),
            "target_category": "review_context",
            "target_detail": "Synthetic response without a review link.",
            "target_occurred_at": datetime.now(timezone.utc).isoformat(),
        },
    )
    assert_status(missing_review_link, 400, "missing review-context link denial")

    wrong_review_link = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_record_field_update",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={
            "target_client_update": str(uuid.uuid4()),
            "target_review": wrong_review_id,
            "target_category": "review_context",
            "target_detail": "Synthetic response linked to the wrong mission review.",
            "target_occurred_at": datetime.now(timezone.utc).isoformat(),
        },
    )
    assert_status(wrong_review_link, 403, "wrong review-context link denial")

    context_update_id = str(uuid.uuid4())
    context_occurred_at = datetime.now(timezone.utc).isoformat()
    context_update = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_record_field_update",
        headers=harness.headers(tokens["patrol_1"], return_rows=True),
        json={
            "target_client_update": context_update_id,
            "target_review": context_review_id,
            "target_category": "review_context",
            "target_detail": "Synthetic road closure required the route deviation.",
            "target_occurred_at": context_occurred_at,
        },
    )
    assert_status(context_update, 200, "submit linked patrol review context")
    context_record_id = context_update.json()
    context_update_read = harness.get(
        "patrolgrid_field_updates",
        tokens["patrol_1"],
        f"select=id,client_update_id,review_id,category&id=eq.{context_record_id}",
    )
    assert_status(context_update_read, 200, "read linked patrol review context")
    context_update_rows = context_update_read.json()
    assert len(context_update_rows) == 1
    context_update_row = context_update_rows[0]
    assert context_update_row["client_update_id"] == context_update_id
    assert context_update_row["review_id"] == context_review_id
    assert context_update_row["category"] == "review_context"

    stale_approval = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_submit_review",
        headers=harness.headers(tokens["supervisor"], return_rows=True),
        json={
            "target_mission": mission_id,
            "target_expected_version": context_request_version,
            "target_outcome": "approved",
            "target_notes": "Synthetic stale approval attempt.",
        },
    )
    assert_status(stale_approval, 400, "stale supervisor approval denial")

    context_updated_mission = harness.get(
        "patrolgrid_missions",
        tokens["supervisor"],
        f"select=id,status,version&id=eq.{mission_id}",
    )
    assert_status(context_updated_mission, 200, "refresh context-updated mission")
    context_updated_rows = context_updated_mission.json()
    assert len(context_updated_rows) == 1
    context_updated = context_updated_rows[0]
    assert context_updated["status"] == "needs_review"
    refreshed_version = context_updated["version"]
    assert refreshed_version == context_request_version + 1

    approved_review = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_submit_review",
        headers=harness.headers(tokens["supervisor"], return_rows=True),
        json={
            "target_mission": mission_id,
            "target_expected_version": refreshed_version,
            "target_outcome": "approved",
            "target_notes": "Synthetic context and route evidence reviewed.",
        },
    )
    assert_status(approved_review, 200, "approve refreshed mission evidence")
    completed_version = approved_review.json()
    assert completed_version == refreshed_version + 1

    completed_mission = harness.get(
        "patrolgrid_missions",
        tokens["supervisor"],
        f"select=id,status,version&id=eq.{mission_id}",
    )
    assert_status(completed_mission, 200, "read reviewed mission")
    assert completed_mission.json() == [
        {"id": mission_id, "status": "completed", "version": completed_version}
    ]

    reviews = harness.get(
        "patrolgrid_reviews",
        tokens["supervisor"],
        f"select=id,mission_id,reviewer_id,outcome,notes&mission_id=eq.{mission_id}",
    )
    assert_status(reviews, 200, "read atomic review history")
    review_rows = reviews.json()
    assert len(review_rows) == 2
    reviews_by_outcome = {row["outcome"]: row for row in review_rows}
    assert reviews_by_outcome["needs_context"] == {
        "id": context_review_id,
        "mission_id": mission_id,
        "reviewer_id": users["supervisor"],
        "outcome": "needs_context",
        "notes": "Explain the synthetic route deviation.",
    }
    approved_row = reviews_by_outcome["approved"]
    assert approved_row["mission_id"] == mission_id
    assert approved_row["reviewer_id"] == users["supervisor"]
    assert approved_row["notes"] == "Synthetic context and route evidence reviewed."

    audit = harness.get(
        "patrolgrid_audit_events",
        tokens["supervisor"],
        f"select=event_type,actor_id,payload&mission_id=eq.{mission_id}&limit=100",
    )
    assert_status(audit, 200, "supervisor audit read")
    audit_rows = audit.json()
    event_types = {row["event_type"] for row in audit_rows}
    assert "patrolgrid_missions.insert" in event_types
    assert "patrolgrid_sessions.update" in event_types
    assert "patrolgrid_reviews.insert" in event_types
    assert "patrolgrid_field_updates.insert" in event_types
    assert "patrolgrid.track_batch_ingested" in event_types
    assert "patrolgrid.priority_visit_ingested" in event_types
    assert "patrolgrid.field_update_ingested" in event_types
    track_summaries = [
        row
        for row in audit_rows
        if row["event_type"] == "patrolgrid.track_batch_ingested"
    ]
    assert track_summaries
    assert all(
        summary["payload"]["submitted_count"] == 1 for summary in track_summaries
    )
    assert all(
        "latitude" not in json.dumps(summary["payload"]) for summary in track_summaries
    )
    assert all(
        "longitude" not in json.dumps(summary["payload"]) for summary in track_summaries
    )
    assert any(
        row["event_type"] == "patrolgrid_field_updates.insert"
        and row["actor_id"] == users["patrol_1"]
        and row["payload"].get("record_id") == context_update_row["id"]
        for row in audit_rows
    )

    expired_duty_mission_id = str(uuid.uuid4())
    expired_duty_session_id = str(uuid.uuid4())
    expired_duty_end = datetime.now(timezone.utc) - timedelta(minutes=10)
    harness.service_insert(
        "patrolgrid_missions",
        {
            "id": expired_duty_mission_id,
            "subdivision_id": subdivision_id,
            "route_template_id": route_id,
            "title": "Synthetic expired duty mission",
            "starts_at": (expired_duty_end - timedelta(hours=2)).isoformat(),
            "ends_at": expired_duty_end.isoformat(),
            "guidance": "suggested_route",
            "instructions": "Synthetic duty-window cutoff fixture only.",
            "status": "active",
            "created_by": users["supervisor"],
        },
    )
    harness.service_insert(
        "patrolgrid_assignments",
        {
            "mission_id": expired_duty_mission_id,
            "user_id": users["patrol_2"],
            "assigned_by": users["supervisor"],
        },
    )
    harness.service_insert(
        "patrolgrid_sessions",
        {
            "id": expired_duty_session_id,
            "mission_id": expired_duty_mission_id,
            "user_id": users["patrol_2"],
            "installation_id": str(uuid.uuid4()),
            "started_at": (expired_duty_end - timedelta(hours=2)).isoformat(),
            "app_version": "integration-test",
        },
    )

    late_duty_point = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_ingest_track_points",
        headers=harness.headers(tokens["patrol_2"], return_rows=True),
        json={
            "target_session": expired_duty_session_id,
            "target_points": [
                {
                    "client_point_id": str(uuid.uuid4()),
                    "sequence_number": 0,
                    "recorded_at": (
                        expired_duty_end + timedelta(minutes=6)
                    ).isoformat(),
                    "latitude": 13.01,
                    "longitude": 77.51,
                    "accuracy_m": 8.0,
                }
            ],
        },
    )
    assert_status(late_duty_point, 400, "post-duty route point denial")

    close_expired = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_close_expired_sessions",
        headers=harness.headers(tokens["patrol_2"], return_rows=True),
        json={},
    )
    assert_status(close_expired, 200, "close own expired duty sessions")
    assert close_expired.json() == 1

    closed_duty_session = harness.get(
        "patrolgrid_sessions",
        tokens["patrol_2"],
        f"select=ended_at,end_reason&id=eq.{expired_duty_session_id}",
    )
    assert_status(closed_duty_session, 200, "read duty-window session closure")
    closed_duty_rows = closed_duty_session.json()
    assert len(closed_duty_rows) == 1
    assert closed_duty_rows[0]["end_reason"] == "duty_window_ended"
    closed_at = datetime.fromisoformat(
        closed_duty_rows[0]["ended_at"].replace("Z", "+00:00")
    )
    expected_cutoff = expired_duty_end + timedelta(minutes=5)
    assert abs((closed_at - expected_cutoff).total_seconds()) < 0.001

    queued_duty_point = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_ingest_track_points",
        headers=harness.headers(tokens["patrol_2"], return_rows=True),
        json={
            "target_session": expired_duty_session_id,
            "target_points": [
                {
                    "client_point_id": str(uuid.uuid4()),
                    "sequence_number": 0,
                    "recorded_at": (
                        expired_duty_end + timedelta(minutes=4)
                    ).isoformat(),
                    "latitude": 13.01,
                    "longitude": 77.51,
                    "accuracy_m": 8.0,
                }
            ],
        },
    )
    assert_status(queued_duty_point, 200, "sealed offline route upload")
    assert queued_duty_point.json() == 1

    duty_audit = harness.get(
        "patrolgrid_audit_events",
        tokens["supervisor"],
        "select=event_type,actor_id,payload"
        f"&mission_id=eq.{expired_duty_mission_id}"
        "&event_type=eq.patrolgrid.expired_session_closed",
    )
    assert_status(duty_audit, 200, "read expired-duty audit")
    duty_audit_rows = duty_audit.json()
    assert len(duty_audit_rows) == 1
    assert duty_audit_rows[0]["event_type"] == "patrolgrid.expired_session_closed"
    assert duty_audit_rows[0]["actor_id"] == users["patrol_2"]
    assert duty_audit_rows[0]["payload"]["session_id"] == expired_duty_session_id

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

    assigned_mission = harness.get(
        "patrolgrid_missions",
        tokens["supervisor"],
        f"select=id,route_geojson&id=eq.{assigned_mission_id}",
    )
    assert_status(assigned_mission, 200, "assigned mission route snapshot read")
    assert assigned_mission.json() == [
        {"id": assigned_mission_id, "route_geojson": route_geojson}
    ]

    changed_snapshot = harness.client.patch(
        f"{url}/rest/v1/patrolgrid_missions?id=eq.{assigned_mission_id}",
        headers=harness.headers(tokens["supervisor"], return_rows=True),
        json={
            "route_geojson": {
                "type": "LineString",
                "coordinates": [[0.0, 0.0], [1.0, 1.0]],
            }
        },
    )
    assert_status(changed_snapshot, 403, "direct mission snapshot mutation denial")

    snapshot_after_rejection = harness.get(
        "patrolgrid_missions",
        tokens["supervisor"],
        f"select=route_geojson&id=eq.{assigned_mission_id}",
    )
    assert_status(snapshot_after_rejection, 200, "unchanged route snapshot read")
    assert snapshot_after_rejection.json() == [{"route_geojson": route_geojson}]

    load_mission_id = str(uuid.uuid4())
    load_now = datetime.now(timezone.utc)
    harness.service_insert(
        "patrolgrid_missions",
        {
            "id": load_mission_id,
            "subdivision_id": subdivision_id,
            "route_template_id": route_id,
            "title": "Synthetic Locust mission",
            "starts_at": (load_now - timedelta(hours=1)).isoformat(),
            "ends_at": (load_now + timedelta(hours=7)).isoformat(),
            "guidance": "suggested_route",
            "instructions": "Synthetic load-test data only.",
            "status": "assigned",
            "created_by": users["supervisor"],
        },
    )
    harness.service_insert(
        "patrolgrid_assignments",
        {
            "mission_id": load_mission_id,
            "user_id": users["load_patrol"],
            "assigned_by": users["supervisor"],
        },
    )

    load_session_id = str(uuid.uuid4())
    load_session = harness.client.post(
        f"{url}/rest/v1/rpc/patrolgrid_start_session",
        headers=harness.headers(tokens["load_patrol"], return_rows=True),
        json={
            "target_session": load_session_id,
            "target_mission": load_mission_id,
            "target_installation": str(uuid.uuid4()),
            "target_app_version": "locust-test",
        },
    )
    assert_status(load_session, 200, "start load-test session workflow")
    assert load_session.json() == load_session_id

    context_path = Path(
        os.getenv(
            "PATROLGRID_LOAD_CONTEXT", "supabase/.temp/patrolgrid-load-context.json"
        )
    )
    context_path.parent.mkdir(parents=True, exist_ok=True)
    context_path.write_text(
        json.dumps(
            {
                "url": url,
                "anon_key": anon_key,
                "access_token": tokens["load_patrol"],
                "mission_id": load_mission_id,
                "session_id": load_session_id,
                "user_id": users["load_patrol"],
            }
        ),
        encoding="utf-8",
    )
    os.chmod(context_path, 0o600)

    print(
        "PatrolGrid Supabase E2E passed: auth, RLS isolation, immutable route snapshots, "
        "exact-session route and priority-visit provenance, atomic assignment, linked "
        "needs-context review, duty-window enforcement, patrol "
        "writes, server-owned session lifecycle and 365-day retention clock, prohibited "
        "terminal reopen, bounded offline closure, and audit trail."
    )
    print(
        f"Synthetic Locust context written to {context_path} (mode 0600; ignored by git)."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, RuntimeError, httpx.HTTPError) as error:
        print(f"PatrolGrid Supabase E2E failed: {error}", file=sys.stderr)
        raise SystemExit(1)
