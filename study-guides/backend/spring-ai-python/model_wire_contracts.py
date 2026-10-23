"""Dependency-free transport, retry, validation, and capacity contracts."""

from dataclasses import dataclass
from enum import Enum
import math
from urllib.parse import urlsplit


MAX_BODY_BYTES = 1_048_576
JSON_TYPES = {"application/json", "application/problem+json"}


class Stage(Enum):
    BEFORE_SEND = "before_send"
    RESPONSE = "response"
    READ_TIMEOUT_AFTER_SEND = "read_timeout_after_send"


class Decision(Enum):
    RETRY = "retry"
    DO_NOT_RETRY = "do_not_retry"
    FAIL = "fail"


class Outcome(Enum):
    ACCEPTED = "accepted"
    INCOMPLETE = "incomplete"


@dataclass(frozen=True)
class Budget:
    total_ms: int
    pool_ms: int
    connect_ms: int
    write_ms: int
    read_ms: int

    def __post_init__(self) -> None:
        phases = (self.pool_ms, self.connect_ms, self.write_ms, self.read_ms)
        if self.total_ms <= 0 or any(value <= 0 for value in phases):
            raise ValueError("all timeout budgets must be positive")
        if sum(phases) > self.total_ms:
            raise ValueError("phase budgets exceed total deadline")


@dataclass(frozen=True)
class RequestSpec:
    base_uri: str
    model: str
    max_output_tokens: int
    has_tools: bool
    request_id: str

    def __post_init__(self) -> None:
        validate_base_uri(self.base_uri)
        if not self.model or not self.model.strip():
            raise ValueError("model is required")
        if not 1 <= self.max_output_tokens <= 4096:
            raise ValueError("max output tokens must be in [1, 4096]")
        if not (8 <= len(self.request_id) <= 64) or not all(
            character.isalnum() or character in "_-" for character in self.request_id
        ):
            raise ValueError("stable request ID is required")


@dataclass(frozen=True)
class Attempt:
    stage: Stage
    status: int
    retry_after_ms: int
    bytes_sent: bool


@dataclass(frozen=True)
class RetryResult:
    decision: Decision
    delay_ms: int
    reason: str


@dataclass(frozen=True)
class ProviderResponse:
    status: int
    content_type: str
    body_bytes: int
    done: bool
    done_reason: str
    content: str
    prompt_tokens: int
    output_tokens: int
    total_duration_ns: int
    model: str


@dataclass(frozen=True)
class Validated:
    outcome: Outcome
    total_tokens: int
    estimated_microdollars: int


def validate_base_uri(uri: str) -> None:
    parsed = urlsplit(uri)
    if not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("base URI must be an origin without credentials, query, or fragment")
    loopback = parsed.hostname.lower() in {"localhost", "127.0.0.1", "::1"}
    if parsed.scheme.lower() != "https" and not (parsed.scheme.lower() == "http" and loopback):
        raise ValueError("remote model endpoints require HTTPS")


def _backoff(attempt: int, retry_after_ms: int) -> int:
    if retry_after_ms < 0:
        raise ValueError("Retry-After cannot be negative")
    exponential = min(8_000, 250 << min(attempt - 1, 5))
    return max(retry_after_ms, exponential)  # caller adds bounded jitter before sleeping


def retry(
    observation: Attempt,
    attempt: int,
    max_attempts: int,
    pure_inference: bool,
    allow_unknown_outcome_replay: bool,
) -> RetryResult:
    if attempt < 1 or max_attempts < 1:
        raise ValueError("attempt counts must be positive")
    if attempt >= max_attempts:
        return RetryResult(Decision.DO_NOT_RETRY, 0, "attempt cap reached")
    if observation.stage is Stage.BEFORE_SEND and not observation.bytes_sent:
        return RetryResult(Decision.RETRY, _backoff(attempt, observation.retry_after_ms), "request not sent")
    if observation.stage is Stage.RESPONSE and observation.status in {429, 503}:
        if not pure_inference:
            return RetryResult(Decision.DO_NOT_RETRY, 0, "operation may have effects")
        return RetryResult(Decision.RETRY, _backoff(attempt, observation.retry_after_ms), "transient response")
    if observation.stage is Stage.READ_TIMEOUT_AFTER_SEND:
        if pure_inference and allow_unknown_outcome_replay:
            return RetryResult(
                Decision.RETRY,
                _backoff(attempt, observation.retry_after_ms),
                "explicitly accepted duplicate cost and nondeterminism",
            )
        return RetryResult(Decision.DO_NOT_RETRY, 0, "outcome unknown after send")
    return RetryResult(Decision.FAIL, 0, "non-retryable response")


def _ceil_per_million(tokens: int, rate: int) -> int:
    return (tokens * rate + 999_999) // 1_000_000


def validate(
    response: ProviderResponse,
    expected_model: str,
    input_rate_per_million: int,
    output_rate_per_million: int,
) -> Validated:
    if response.status != 200:
        raise ValueError("expected HTTP 200")
    media_type = response.content_type.split(";", 1)[0].strip().lower()
    if media_type not in JSON_TYPES:
        raise ValueError("JSON content type required")
    if not 1 <= response.body_bytes <= MAX_BODY_BYTES:
        raise ValueError("response body size outside bounds")
    if not response.done:
        raise ValueError("complete response required")
    if not response.content or not response.content.strip():
        raise ValueError("non-empty model content required")
    if response.model != expected_model:
        raise ValueError("unexpected model identity")
    if min(response.prompt_tokens, response.output_tokens, response.total_duration_ns) < 0:
        raise ValueError("usage and duration must be nonnegative")
    if min(input_rate_per_million, output_rate_per_million) < 0:
        raise ValueError("rates must be nonnegative")
    if response.done_reason == "stop":
        outcome = Outcome.ACCEPTED
    elif response.done_reason == "length":
        outcome = Outcome.INCOMPLETE
    else:
        raise ValueError("unknown completion reason")
    cost = _ceil_per_million(response.prompt_tokens, input_rate_per_million)
    cost += _ceil_per_million(response.output_tokens, output_rate_per_million)
    return Validated(outcome, response.prompt_tokens + response.output_tokens, cost)


def required_in_flight(requests_per_second: float, latency_seconds: float) -> int:
    if not math.isfinite(requests_per_second) or not math.isfinite(latency_seconds):
        raise ValueError("positive finite rate and latency required")
    if requests_per_second <= 0 or latency_seconds <= 0:
        raise ValueError("positive finite rate and latency required")
    required = math.ceil(requests_per_second * latency_seconds)
    if required > 2_147_483_647:
        raise ValueError("capacity exceeds integer range")
    return required


def rejects(action) -> None:
    try:
        action()
    except (ValueError, OverflowError):
        return
    raise AssertionError("expected rejection")


if __name__ == "__main__":
    budget = Budget(20_000, 250, 2_000, 1_000, 16_750)
    assert budget.read_ms == 16_750
    request = RequestSpec("http://127.0.0.1:11434", "qwen3:8b", 120, False, "req_01HZX9A2")
    assert urlsplit(request.base_uri).port == 11434
    RequestSpec("https://models.example.com", "qwen3:8b", 120, False, "req_01HZX9A3")
    rejects(lambda: Budget(19_999, 250, 2_000, 1_000, 16_750))
    rejects(lambda: Budget(20_000, 0, 2_000, 1_000, 17_000))
    rejects(lambda: RequestSpec("http://models.example.com", "qwen3:8b", 120, False, "req_01HZX9A4"))
    rejects(lambda: RequestSpec("https://user@models.example.com", "qwen3:8b", 120, False, "req_01HZX9A5"))
    rejects(lambda: RequestSpec("https://models.example.com?token=x", "qwen3:8b", 120, False, "req_01HZX9A6"))
    rejects(lambda: RequestSpec("https://models.example.com", " ", 120, False, "req_01HZX9A7"))

    before_send = retry(Attempt(Stage.BEFORE_SEND, 0, 0, False), 1, 3, False, False)
    assert before_send == RetryResult(Decision.RETRY, 250, "request not sent")
    throttled = retry(Attempt(Stage.RESPONSE, 429, 1_000, True), 1, 3, True, False)
    assert throttled == RetryResult(Decision.RETRY, 1_000, "transient response")
    assert retry(Attempt(Stage.RESPONSE, 503, 0, True), 1, 3, False, False).decision is Decision.DO_NOT_RETRY
    assert retry(Attempt(Stage.READ_TIMEOUT_AFTER_SEND, 0, 0, True), 1, 3, True, False).decision is Decision.DO_NOT_RETRY
    assert retry(Attempt(Stage.READ_TIMEOUT_AFTER_SEND, 0, 0, True), 1, 3, True, True).decision is Decision.RETRY
    assert retry(Attempt(Stage.RESPONSE, 500, 0, True), 1, 3, True, True).decision is Decision.FAIL
    assert retry(Attempt(Stage.RESPONSE, 429, 5_000, True), 3, 3, True, True).decision is Decision.DO_NOT_RETRY
    rejects(lambda: retry(Attempt(Stage.RESPONSE, 429, -1, True), 1, 3, True, True))

    accepted_response = ProviderResponse(
        200, "application/json; charset=utf-8", 640, True, "stop", "billing", 900, 100,
        1_850_000_000, "qwen3:8b"
    )
    accepted = validate(accepted_response, "qwen3:8b", 2_000_000, 8_000_000)
    assert accepted == Validated(Outcome.ACCEPTED, 1_000, 2_600)
    incomplete = validate(
        ProviderResponse(200, "application/json", 640, True, "length", "partial", 900, 120,
                         1_900_000_000, "qwen3:8b"),
        "qwen3:8b", 2_000_000, 8_000_000,
    )
    assert incomplete.outcome is Outcome.INCOMPLETE
    rejects(lambda: validate(ProviderResponse(500, "application/json", 10, True, "stop", "x", 1, 1, 1,
                                               "qwen3:8b"), "qwen3:8b", 1, 1))
    rejects(lambda: validate(ProviderResponse(200, "text/html", 10, True, "stop", "x", 1, 1, 1,
                                               "qwen3:8b"), "qwen3:8b", 1, 1))
    rejects(lambda: validate(ProviderResponse(200, "application/json", MAX_BODY_BYTES + 1, True, "stop", "x",
                                               1, 1, 1, "qwen3:8b"), "qwen3:8b", 1, 1))
    rejects(lambda: validate(ProviderResponse(200, "application/json", 10, False, "stop", "x", 1, 1, 1,
                                               "qwen3:8b"), "qwen3:8b", 1, 1))
    rejects(lambda: validate(ProviderResponse(200, "application/json", 10, True, "stop", " ", 1, 1, 1,
                                               "qwen3:8b"), "qwen3:8b", 1, 1))
    rejects(lambda: validate(ProviderResponse(200, "application/json", 10, True, "stop", "x", 1, 1, 1,
                                               "other"), "qwen3:8b", 1, 1))
    rejects(lambda: validate(ProviderResponse(200, "application/json", 10, True, "safety", "x", 1, 1, 1,
                                               "qwen3:8b"), "qwen3:8b", 1, 1))
    rejects(lambda: validate(ProviderResponse(200, "application/json", 10, True, "stop", "x", -1, 1, 1,
                                               "qwen3:8b"), "qwen3:8b", 1, 1))

    in_flight = required_in_flight(50, 8)
    assert in_flight == 400
    assert required_in_flight(0.1, 0.1) == 1
    rejects(lambda: required_in_flight(0, 8))
    rejects(lambda: required_in_flight(math.nan, 8))

    print("PARITY|RETRY_1000|ACCEPTED|INCOMPLETE|400|2600")
    print("model_wire_contracts.py: 30 scenario groups passed")
