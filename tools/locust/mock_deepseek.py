"""Local synthetic DeepSeek-compatible endpoint for Locust contract testing."""

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


SCENARIOS = {
    "valid": (
        200,
        {"choices": [{"message": {"content": "Synthetic daily report [V1] [E1]."}}]},
    ),
    "invalid-citations": (
        200,
        {"choices": [{"message": {"content": "Synthetic daily report [V99]."}}]},
    ),
    "empty": (200, {"choices": [{"message": {"content": ""}}]}),
    "rate-limit": (
        429,
        {"error": {"type": "rate_limit_error", "message": "Synthetic rate limit."}},
    ),
    "server-error": (
        500,
        {"error": {"type": "server_error", "message": "Synthetic server error."}},
    ),
}


class DeepSeekCompatibleHandler(BaseHTTPRequestHandler):
    def _send_json(self, status, payload, *, retry_after=None):
        response = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        if retry_after is not None:
            self.send_header("Retry-After", retry_after)
        self.end_headers()
        self.wfile.write(response)

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length", "0"))
        try:
            payload = json.loads(self.rfile.read(length))
        except (json.JSONDecodeError, UnicodeDecodeError):
            self._send_json(
                400,
                {"error": {"type": "invalid_request", "message": "Invalid JSON."}},
            )
            return

        scenario = self.headers.get("X-DailyBeat-Scenario")
        if scenario not in SCENARIOS:
            self._send_json(
                400,
                {
                    "error": {
                        "type": "invalid_scenario",
                        "message": "Unknown DailyBeat scenario.",
                    }
                },
            )
            return

        messages = payload.get("messages", [])
        valid_request = (
            self.headers.get("Authorization", "").startswith("Bearer ")
            and payload.get("model") == "deepseek-chat"
            and payload.get("temperature") == 0.2
            and len(messages) == 2
            and messages[0].get("role") == "system"
            and messages[1].get("role") == "user"
        )
        if not valid_request:
            self._send_json(
                400,
                {
                    "error": {
                        "type": "invalid_request",
                        "message": "Request does not match DailyBeat cloud contract.",
                    }
                },
            )
            return

        status, response = SCENARIOS[scenario]
        self._send_json(
            status,
            response,
            retry_after="1" if scenario == "rate-limit" else None,
        )

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=19090)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), DeepSeekCompatibleHandler)
    print(f"DailyBeat mock listening on http://127.0.0.1:{server.server_port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
