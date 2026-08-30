"""Local synthetic DeepSeek-compatible endpoint for Locust contract testing."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class DeepSeekCompatibleHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length))
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
            self.send_error(400, "Request does not match DailyBeat cloud contract")
            return

        response = json.dumps(
            {"choices": [{"message": {"content": "Synthetic daily report."}}]},
        ).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 19090), DeepSeekCompatibleHandler).serve_forever()
