"""Synthetic Supabase load profile for PatrolGrid mission and route APIs."""

from __future__ import annotations

import itertools
import json
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path

from locust import HttpUser, between, events, task


CONTEXT_PATH = Path(
    os.getenv("PATROLGRID_LOAD_CONTEXT", "supabase/.temp/patrolgrid-load-context.json")
)
if not CONTEXT_PATH.exists():
    raise RuntimeError(
        f"Missing {CONTEXT_PATH}. Run tools/patrolgrid/supabase_e2e.py against local Supabase first."
    )
CONTEXT = json.loads(CONTEXT_PATH.read_text(encoding="utf-8"))
SEQUENCES = itertools.count(10_000)


class PatrolGridApiUser(HttpUser):
    host = CONTEXT["url"]
    wait_time = between(0.05, 0.2)

    def on_start(self) -> None:
        self.headers = {
            "apikey": CONTEXT["anon_key"],
            "Authorization": f"Bearer {CONTEXT['access_token']}",
            "Content-Type": "application/json",
        }

    @task(5)
    def read_assigned_mission(self) -> None:
        with self.client.get(
            "/rest/v1/patrolgrid_missions?select=id,title,status,starts_at,ends_at&order=starts_at.desc",
            headers=self.headers,
            name="GET assigned missions",
            catch_response=True,
        ) as response:
            if response.status_code != 200 or len(response.json()) != 1:
                response.failure(f"Expected one assigned mission, got HTTP {response.status_code}")

    @task(4)
    def read_own_recent_route(self) -> None:
        with self.client.get(
            "/rest/v1/patrolgrid_track_points?select=recorded_at,latitude,longitude,accuracy_m"
            "&order=recorded_at.desc&limit=100",
            headers=self.headers,
            name="GET own recent route",
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                response.failure(f"Unexpected HTTP {response.status_code}")

    @task(3)
    def append_route_point(self) -> None:
        sequence = next(SEQUENCES)
        offset = (sequence % 100) / 100_000
        with self.client.post(
            "/rest/v1/patrolgrid_track_points",
            headers={**self.headers, "Prefer": "return=minimal"},
            json={
                "client_point_id": str(uuid.uuid4()),
                "session_id": CONTEXT["session_id"],
                "mission_id": CONTEXT["mission_id"],
                "user_id": CONTEXT["user_id"],
                "sequence_number": sequence,
                "recorded_at": datetime.now(timezone.utc).isoformat(),
                "latitude": 13.0 + offset,
                "longitude": 77.5 + offset,
                "accuracy_m": 8.0,
            },
            name="POST route point",
            catch_response=True,
        ) as response:
            if response.status_code != 201:
                response.failure(f"Unexpected HTTP {response.status_code}: {response.text[:160]}")

    @task(1)
    def submit_field_update(self) -> None:
        with self.client.post(
            "/rest/v1/patrolgrid_field_updates",
            headers={**self.headers, "Prefer": "return=minimal"},
            json={
                "client_update_id": str(uuid.uuid4()),
                "mission_id": CONTEXT["mission_id"],
                "user_id": CONTEXT["user_id"],
                "category": "observation",
                "detail": "Synthetic load-test observation; contains no operational data.",
                "occurred_at": datetime.now(timezone.utc).isoformat(),
            },
            name="POST field update",
            catch_response=True,
        ) as response:
            if response.status_code != 201:
                response.failure(f"Unexpected HTTP {response.status_code}: {response.text[:160]}")


@events.quitting.add_listener
def enforce_service_level(environment, **_kwargs) -> None:
    total = environment.stats.total
    max_failure_ratio = float(os.getenv("PATROLGRID_MAX_FAILURE_RATIO", "0.01"))
    max_p95_ms = int(os.getenv("PATROLGRID_MAX_P95_MS", "750"))
    p95_ms = total.get_response_time_percentile(0.95) or 0
    if total.fail_ratio > max_failure_ratio or p95_ms > max_p95_ms:
        environment.process_exit_code = 1
