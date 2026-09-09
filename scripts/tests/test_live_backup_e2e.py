import pytest

from scripts.live_backup_e2e import (
    LiveBackupError,
    SupabaseQaClient,
    run_live_round_trip,
    valid_test_snapshot,
)


class FakeClient:
    def __init__(self, original):
        self.snapshot = original
        self.uploads = []
        self.deletes = 0

    def sign_in(self, email, password):
        assert email and password
        return object()

    def download(self, session):
        return self.snapshot

    def upload(self, session, snapshot):
        self.uploads.append(snapshot)
        self.snapshot = snapshot

    def delete(self, session):
        self.deletes += 1
        self.snapshot = None


class FailingDownloadClient(FakeClient):
    def __init__(self, original):
        super().__init__(original)
        self.downloads = 0

    def download(self, session):
        self.downloads += 1
        if self.downloads == 2:
            raise LiveBackupError("simulated download failure")
        return self.snapshot


def test_live_round_trip_restores_an_existing_qa_backup():
    original = {"existing": "dedicated QA snapshot"}
    client = FakeClient(original)

    run_live_round_trip(client, "qa@example.com", "secret")

    assert client.snapshot == original
    assert len(client.uploads) == 2
    assert client.deletes == 0


def test_live_round_trip_removes_its_fixture_when_no_backup_existed():
    client = FakeClient(None)

    run_live_round_trip(client, "qa@example.com", "secret")

    assert client.snapshot is None
    assert len(client.uploads) == 1
    assert client.deletes == 1


def test_live_fixture_is_accepted_by_the_android_snapshot_shape():
    snapshot = valid_test_snapshot("marker", 1234)

    assert snapshot["schemaVersion"] == 1
    assert snapshot["events"][0]["rawText"] == "marker"
    assert snapshot["diaries"][0]["text"] == "marker"
    assert snapshot["settings"]["callLogEnabled"] is False


def test_live_round_trip_restores_the_original_after_a_verification_failure():
    original = {"existing": "dedicated QA snapshot"}
    client = FailingDownloadClient(original)

    with pytest.raises(LiveBackupError, match="simulated download failure"):
        run_live_round_trip(client, "qa@example.com", "secret")

    assert client.snapshot == original
    assert len(client.uploads) == 2


@pytest.mark.parametrize(
    "url",
    [
        "http://example.supabase.co",
        "https://example.supabase.co/rest/v1",
        "https://example.supabase.co?unsafe=true",
    ],
)
def test_client_rejects_non_origin_supabase_urls(url):
    with pytest.raises(LiveBackupError, match="plain HTTPS origin"):
        SupabaseQaClient(url, "anon-key")
