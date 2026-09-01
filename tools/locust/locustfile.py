from locust import HttpUser, between, events, task

from tools.locust.contracts import (
    SCENARIO_WEIGHTS,
    performance_budget_violations,
    validate_response,
)


@events.quitting.add_listener
def enforce_performance_budget(environment, **_kwargs):
    violations = performance_budget_violations(environment.stats)
    if violations:
        environment.process_exit_code = 1
        for violation in violations:
            print(f"PERFORMANCE BUDGET FAILED: {violation}")
    else:
        print(
            "PERFORMANCE BUDGET PASSED: at least 100 requests, zero unexpected "
            "failures, p95 <= 250 ms, p99 <= 500 ms"
        )


class DeepSeekCompatibleUser(HttpUser):
    """Synthetic load for DailyBeat's OpenAI-compatible cloud request contract."""

    host = "http://127.0.0.1:19090"
    wait_time = between(0.1, 0.3)

    def request_scenario(self, scenario):
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
                "X-DailyBeat-Scenario": scenario,
            },
            json=payload,
            name=f"POST /v1/chat/completions [{scenario}]",
            catch_response=True,
        ) as response:
            try:
                body = response.json()
            except ValueError:
                response.failure(f"{scenario}: expected JSON response")
                return
            failure = validate_response(scenario, response.status_code, body)
            if failure:
                response.failure(failure)
            else:
                response.success()

    @task(SCENARIO_WEIGHTS["valid"])
    def generate_valid_report(self):
        self.request_scenario("valid")

    @task(SCENARIO_WEIGHTS["rate-limit"])
    def receive_expected_rate_limit(self):
        self.request_scenario("rate-limit")

    @task(SCENARIO_WEIGHTS["server-error"])
    def receive_expected_server_error(self):
        self.request_scenario("server-error")
