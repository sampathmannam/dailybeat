from locust import HttpUser, between, task


class DeepSeekCompatibleUser(HttpUser):
    """Synthetic load for DailyBeat's OpenAI-compatible cloud request contract."""

    host = "http://127.0.0.1:19090"
    wait_time = between(0.1, 0.3)

    def _generate(self, *, system_prompt: str, user_content: str, request_name: str):
        payload = {
            "model": "deepseek-chat",
            "temperature": 0.2,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_content},
            ],
        }
        with self.client.post(
            "/v1/chat/completions",
            headers={
                "Authorization": "Bearer synthetic-load-test-key",
                "Content-Type": "application/json",
            },
            json=payload,
            name=request_name,
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

    @task(3)
    def generate_daily_report(self):
        self._generate(
            system_prompt="Create a concise daily report.",
            user_content="Synthetic visits: Home, Office, Home. No personal data.",
            request_name="POST /v1/chat/completions [daily]",
        )

    @task(1)
    def generate_weekly_feed_rollup(self):
        synthetic_days = "\n".join(
            f"Day {day}: Station, Court, Patrol; 3 stops; {12 + day}.5 km."
            for day in range(1, 8)
        )
        self._generate(
            system_prompt="Create a concise weekly rollup from the journey feed.",
            user_content=f"Synthetic week only; no personal data.\n{synthetic_days}",
            request_name="POST /v1/chat/completions [weekly feed]",
        )
