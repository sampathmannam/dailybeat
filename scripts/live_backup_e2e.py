"""Fail-closed live Supabase backup round trip for the disposable QA account."""

from __future__ import annotations

import json
import os
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any

MAX_RESPONSE_BYTES = 12 * 1024 * 1024


class LiveBackupError(RuntimeError):
    """A sanitized live-gate failure that never includes credentials or tokens."""


@dataclass(frozen=True)
class Session:
    user_id: str
    access_token: str


class SupabaseQaClient:
    def __init__(self, base_url: str, anonymous_key: str) -> None:
        parsed = urllib.parse.urlsplit(base_url.strip())
        if (
            parsed.scheme != "https"
            or not parsed.hostname
            or parsed.path not in {"", "/"}
            or parsed.query
            or parsed.fragment
        ):
            raise LiveBackupError("SUPABASE_URL must be a plain HTTPS origin.")
        if not anonymous_key.strip() or "\n" in anonymous_key or "\r" in anonymous_key:
            raise LiveBackupError("SUPABASE_ANON_KEY is invalid.")
        self.base_url = urllib.parse.urlunsplit(
            (parsed.scheme, parsed.netloc, "", "", "")
        )
        self.anonymous_key = anonymous_key.strip()

    def sign_in(self, email: str, password: str) -> Session:
        if not email.strip() or not password:
            raise LiveBackupError("Dedicated QA backup credentials are missing.")
        response = self._request(
            "POST",
            "/auth/v1/token?grant_type=password",
            {"email": email.strip(), "password": password},
        )
        try:
            user_id = response["user"]["id"]
            access_token = response["access_token"]
        except (KeyError, TypeError) as error:
            raise LiveBackupError(
                "Cloud backup sign-in returned an invalid session."
            ) from error
        if (
            not isinstance(user_id, str)
            or not user_id
            or not isinstance(access_token, str)
            or not access_token
        ):
            raise LiveBackupError("Cloud backup sign-in returned an invalid session.")
        return Session(user_id=user_id, access_token=access_token)

    def download(self, session: Session) -> dict[str, Any] | None:
        user_id = urllib.parse.quote(session.user_id, safe="")
        response = self._request(
            "GET",
            f"/rest/v1/dailybeat_backups?select=snapshot&user_id=eq.{user_id}&limit=1",
            token=session.access_token,
        )
        if not isinstance(response, list):
            raise LiveBackupError("Cloud backup download returned an invalid response.")
        if not response:
            return None
        snapshot = (
            response[0].get("snapshot") if isinstance(response[0], dict) else None
        )
        if not isinstance(snapshot, dict):
            raise LiveBackupError("Cloud backup download returned an invalid snapshot.")
        return snapshot

    def upload(self, session: Session, snapshot: dict[str, Any]) -> None:
        self._request(
            "POST",
            "/rest/v1/dailybeat_backups?on_conflict=user_id",
            {"user_id": session.user_id, "snapshot": snapshot},
            token=session.access_token,
            prefer="resolution=merge-duplicates,return=minimal",
            expect_json=False,
        )

    def delete(self, session: Session) -> None:
        user_id = urllib.parse.quote(session.user_id, safe="")
        self._request(
            "DELETE",
            f"/rest/v1/dailybeat_backups?user_id=eq.{user_id}",
            token=session.access_token,
            expect_json=False,
        )

    def _request(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
        *,
        token: str | None = None,
        prefer: str | None = None,
        expect_json: bool = True,
    ) -> Any:
        headers = {"apikey": self.anonymous_key, "Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if prefer:
            headers["Prefer"] = prefer
        data = (
            None
            if body is None
            else json.dumps(body, separators=(",", ":")).encode("utf-8")
        )
        request = urllib.request.Request(
            self.base_url + path, data=data, headers=headers, method=method
        )

        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=30) as response:
                    payload = response.read(MAX_RESPONSE_BYTES + 1)
                    if len(payload) > MAX_RESPONSE_BYTES:
                        raise LiveBackupError(
                            "Cloud backup response exceeded the safety limit."
                        )
                    if not expect_json:
                        return None
                    return json.loads(payload.decode("utf-8"))
            except urllib.error.HTTPError as error:
                if error.code in {429, 500, 502, 503, 504} and attempt < 3:
                    time.sleep(2**attempt)
                    continue
                raise LiveBackupError(
                    f"Cloud backup request failed with HTTP {error.code}."
                ) from error
            except (urllib.error.URLError, TimeoutError) as error:
                if attempt < 3:
                    time.sleep(2**attempt)
                    continue
                raise LiveBackupError(
                    "Cloud backup network request failed after retries."
                ) from error
            except (UnicodeDecodeError, json.JSONDecodeError) as error:
                raise LiveBackupError(
                    "Cloud backup returned malformed JSON."
                ) from error
        raise AssertionError("unreachable")


def valid_test_snapshot(marker: str, now_ms: int) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "createdAtMs": now_ms,
        "events": [
            {
                "id": 1,
                "timestamp": now_ms,
                "type": "manual",
                "rawText": marker,
                "placeName": None,
                "latitude": None,
                "longitude": None,
                "peopleMentioned": None,
                "caseNumbers": None,
                "sourceId": None,
            }
        ],
        "places": [],
        "diaries": [{"dateKey": "2026-09-08", "text": marker, "updatedAt": now_ms}],
        "visits": [],
        "settings": {
            "officerName": "DailyBeat QA",
            "gpsCaptureEnabled": True,
            "callLogEnabled": False,
            "cloudLlmEnabled": True,
            "cloudProvider": "deepseek",
            "cloudModel": "deepseek-chat",
            "cloudBaseUrl": "",
            "autoEveningReport": True,
            "autoMiddayPulse": False,
            "supervisorName": "",
        },
    }


def run_live_round_trip(client: SupabaseQaClient, email: str, password: str) -> None:
    session = client.sign_in(email, password)
    original = client.download(session)
    marker = f"DailyBeat live backup gate {secrets.token_hex(12)}"
    candidate = valid_test_snapshot(marker, int(time.time() * 1000))
    mutation_started = False
    primary_error: Exception | None = None

    try:
        # A response can be lost after the server commits an upsert, so cleanup must run once the
        # mutation starts even when upload() ultimately reports a network failure.
        mutation_started = True
        client.upload(session, candidate)
        downloaded = client.download(session)
        if downloaded != candidate:
            raise LiveBackupError(
                "Cloud backup round trip did not return the uploaded snapshot."
            )
    except Exception as error:
        primary_error = error
        raise
    finally:
        if mutation_started:
            try:
                if original is None:
                    client.delete(session)
                else:
                    client.upload(session, original)
                if client.download(session) != original:
                    raise LiveBackupError(
                        "Cloud backup QA cleanup verification failed."
                    )
            except Exception:
                if primary_error is not None:
                    raise LiveBackupError(
                        "Cloud backup round trip failed and the QA snapshot cleanup also failed."
                    ) from None
                else:
                    raise


def main() -> int:
    required = {
        name: os.environ.get(name, "")
        for name in (
            "SUPABASE_URL",
            "SUPABASE_ANON_KEY",
            "DAILYBEAT_BACKUP_TEST_EMAIL",
            "DAILYBEAT_BACKUP_TEST_PASSWORD",
        )
    }
    missing = [name for name, value in required.items() if not value]
    if missing:
        raise LiveBackupError(
            "Missing required live-backup configuration: " + ", ".join(missing)
        )

    client = SupabaseQaClient(required["SUPABASE_URL"], required["SUPABASE_ANON_KEY"])
    run_live_round_trip(
        client,
        required["DAILYBEAT_BACKUP_TEST_EMAIL"],
        required["DAILYBEAT_BACKUP_TEST_PASSWORD"],
    )
    print("Live cloud backup upload/download/cleanup round trip passed.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except LiveBackupError as error:
        raise SystemExit(f"Live backup gate failed: {error}") from None
