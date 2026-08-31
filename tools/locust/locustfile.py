from locust import HttpUser, between, events, task


SCENARIO_WEIGHTS = {"valid": 8, "rate-limit": 1, "server-error": 1}
MIN_REQUEST_COUNT = 100
MAX_FAILURE_RATIO = 0.0
MAX_P95_MS = 250
MAX_P99_MS = 500


def validate_response(scenario, status_code, body):
    expected_status = {"valid": 200, "rate-limit": 429, "server-error": 500}[scenario]
    if status_code != expected_status:
        return f"{scenario}: expected HTTP {expected_status}, got {status_code}"

    if scenario == "valid":
        try:
            content = body["choices"][0]["message"]["content"]
        except (KeyError, TypeError, IndexError):
            return "valid: expected a choices[0].message.content string"
        if not isinstance(content, str) or not content.strip():
            return "valid: expected non-empty response content"
        return None

    expected_type = {
        "rate-limit": "rate_limit_error",
        "server-error": "server_error",
    }[scenario]
    try:
        error_type = body["error"]["type"]
    except (KeyError, TypeError):
        error_type = None
    if error_type != expected_type:
        return f"{scenario}: expected error type {expected_type}"
    return None


def performance_budget_violations(stats):
    total = stats.total
    p95 = total.get_response_time_percentile(0.95)
    p99 = total.get_response_time_percentile(0.99)
    violations = []
    if total.num_requests < MIN_REQUEST_COUNT:
        violations.append(f"request count {total.num_requests} is below {MIN_REQUEST_COUNT}")
    if total.fail_ratio > MAX_FAILURE_RATIO:
        violations.append(
            f"unexpected failure ratio {total.fail_ratio:.3%} exceeds {MAX_FAILURE_RATIO:.3%}"
        )
    if p95 > MAX_P95_MS:
        violations.append(f"p95 response time {p95} ms exceeds {MAX_P95_MS} ms")
    if p99 > MAX_P99_MS:
        violations.append(f"p99 response time {p99} ms exceeds {MAX_P99_MS} ms")
    return violations


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
