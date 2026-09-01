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
