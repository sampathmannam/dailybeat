from locust import HttpUser, between, task


class DeepSeekCompatibleUser(HttpUser):
    """Synthetic load for DailyBeat's OpenAI-compatible cloud request contract."""

    host = "http://127.0.0.1:19090"
    wait_time = between(0.1, 0.3)

    @task
    def generate_daily_report(self):
        payload = {
            "model": "deepseek-chat",
            "temperature": 0.2,
            "messages": [
                {"role": "system", "content": "Create a concise daily report."},
                {
                    "role": "user",
                    "content": "Synthetic visits: Home, Office, Home. No personal data.",
                },
            ],
        }
        with self.client.post(
            "/v1/chat/completions",
            headers={
                "Authorization": "Bearer synthetic-load-test-key",
                "Content-Type": "application/json",
            },
            json=payload,
            name="POST /v1/chat/completions",
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                response.failure(f"Unexpected HTTP {response.status_code}")
                return
            try:
                content = response.json()["choices"][0]["message"]["content"].strip()
            except (KeyError, TypeError, ValueError, IndexError) as error:
                response.failure(f"Invalid OpenAI-compatible response: {error}")
                return
            if not content:
                response.failure("Cloud response content was empty")
