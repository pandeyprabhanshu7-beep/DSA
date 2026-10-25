"""Dependency-free contracts for strict structured-output acceptance."""

from __future__ import annotations

import json
from dataclasses import dataclass
from enum import Enum
from typing import Callable, Iterable


FIELDS = {"service", "severity", "summary", "evidence_ids"}
SEVERITIES = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
MAX_BYTES = 2_048
MAX_SUMMARY = 240
MAX_EVIDENCE = 8


class Status(Enum):
    ACCEPTED = "ACCEPTED"
    SYNTAX_INVALID = "SYNTAX_INVALID"
    SCHEMA_INVALID = "SCHEMA_INVALID"
    BUSINESS_INVALID = "BUSINESS_INVALID"
    EXHAUSTED = "EXHAUSTED"


@dataclass(frozen=True)
class IncidentSummary:
    service: str
    severity: str
    summary: str
    evidence_ids: tuple[str, ...]


@dataclass(frozen=True)
class Outcome:
    status: Status
    reason: str
    attempts: int
    value: IncidentSummary | None = None


def _no_duplicate_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate field: {key}")
        result[key] = value
    return result


def parse_and_validate(
    raw: str,
    *,
    allowed_services: set[str],
    allowed_evidence: set[str],
) -> Outcome:
    if len(raw.encode("utf-8")) > MAX_BYTES:
        return Outcome(Status.SYNTAX_INVALID, "response exceeds byte limit", 1)
    try:
        obj = json.loads(
            raw,
            object_pairs_hook=_no_duplicate_object,
            parse_constant=lambda value: (_ for _ in ()).throw(
                ValueError(f"non-JSON number: {value}")
            ),
        )
    except (json.JSONDecodeError, UnicodeError, ValueError) as exc:
        return Outcome(Status.SYNTAX_INVALID, str(exc), 1)

    if type(obj) is not dict:
        return Outcome(Status.SCHEMA_INVALID, "root must be an object", 1)
    keys = set(obj)
    if keys != FIELDS:
        return Outcome(Status.SCHEMA_INVALID, "required/unknown fields", 1)
    if any(type(obj[name]) is not str for name in ("service", "severity", "summary")):
        return Outcome(Status.SCHEMA_INVALID, "string field type", 1)
    evidence = obj["evidence_ids"]
    if type(evidence) is not list or any(type(item) is not str for item in evidence):
        return Outcome(Status.SCHEMA_INVALID, "evidence_ids must be strings", 1)

    service = obj["service"]
    severity = obj["severity"]
    summary = obj["summary"]
    if not 1 <= len(service) <= 64 or severity not in SEVERITIES:
        return Outcome(Status.SCHEMA_INVALID, "service/severity constraint", 1)
    if summary != summary.strip() or not 1 <= len(summary) <= MAX_SUMMARY:
        return Outcome(Status.SCHEMA_INVALID, "summary constraint", 1)
    if not 1 <= len(evidence) <= MAX_EVIDENCE or len(set(evidence)) != len(evidence):
        return Outcome(Status.SCHEMA_INVALID, "evidence cardinality/uniqueness", 1)
    if any(not item or len(item) > 64 for item in evidence):
        return Outcome(Status.SCHEMA_INVALID, "evidence ID constraint", 1)

    candidate = IncidentSummary(service, severity, summary, tuple(evidence))
    if candidate.service not in allowed_services:
        return Outcome(Status.BUSINESS_INVALID, "service outside trusted scope", 1)
    if not set(candidate.evidence_ids) <= allowed_evidence:
        return Outcome(Status.BUSINESS_INVALID, "evidence outside supplied set", 1)
    return Outcome(Status.ACCEPTED, "accepted", 1, candidate)


def validate_with_repair(
    attempts: Iterable[str],
    *,
    allowed_services: set[str],
    allowed_evidence: set[str],
    max_attempts: int,
    budget_ms: int,
    attempt_cost_ms: int = 20,
    reserve_ms: int = 10,
) -> Outcome:
    if max_attempts < 1 or budget_ms < 0 or attempt_cost_ms < 1 or reserve_ms < 0:
        raise ValueError("invalid repair policy")
    used = 0
    last: Outcome | None = None
    for raw in attempts:
        if used >= max_attempts or budget_ms - used * attempt_cost_ms < attempt_cost_ms + reserve_ms:
            break
        used += 1
        last = parse_and_validate(
            raw,
            allowed_services=allowed_services,
            allowed_evidence=allowed_evidence,
        )
        if last.status is Status.ACCEPTED:
            return Outcome(last.status, last.reason, used, last.value)
    reason = "no valid fully revalidated output" if last is None else last.reason
    return Outcome(Status.EXHAUSTED, reason, used)


def _json(service: str, severity: str, summary: str, evidence: list[str]) -> str:
    return json.dumps(
        {"service": service, "severity": severity, "summary": summary, "evidence_ids": evidence},
        separators=(",", ":"),
    )


def _expect(status: Status, raw: str, services: set[str], evidence: set[str]) -> None:
    assert parse_and_validate(
        raw, allowed_services=services, allowed_evidence=evidence
    ).status is status


def main() -> None:
    services = {"payments"}
    evidence = {"evt-17", "log-22"}
    valid = _json("payments", "HIGH", "Payment errors rose after deploy.", ["evt-17", "log-22"])

    scenarios: list[Callable[[], None]] = [
        lambda: _expect(Status.ACCEPTED, valid, services, evidence),
        lambda: _expect(Status.SYNTAX_INVALID, "{", services, evidence),
        lambda: _expect(Status.SYNTAX_INVALID, valid[:-1] + ',"service":"x"}', services, evidence),
        lambda: _expect(Status.SYNTAX_INVALID, "x" * (MAX_BYTES + 1), services, evidence),
        lambda: _expect(Status.SYNTAX_INVALID, valid + " trailing", services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, "[]", services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, "{}", services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, valid[:-1] + ',"auto_resolve":true}', services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("", "HIGH", "x", ["evt-17"]), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("x" * 65, "HIGH", "x", ["evt-17"]), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "URGENT", "x", ["evt-17"]), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "HIGH", "", ["evt-17"]), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "HIGH", " x", ["evt-17"]), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "HIGH", "x" * 241, ["evt-17"]), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "HIGH", "x", []), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "HIGH", "x", ["evt-17"] * 2), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "HIGH", "x", [str(i) for i in range(9)]), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "HIGH", "x", [""]), services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, _json("payments", "HIGH", "x", ["x" * 65]), services, evidence),
        lambda: _expect(Status.BUSINESS_INVALID, _json("orders", "HIGH", "x", ["evt-17"]), services, evidence),
        lambda: _expect(Status.BUSINESS_INVALID, _json("payments", "HIGH", "x", ["foreign"]), services, evidence),
        lambda: _expect(Status.ACCEPTED, _json("payments", "LOW", "x", ["evt-17"]), services, evidence),
        lambda: _expect(Status.ACCEPTED, _json("payments", "MEDIUM", "x", ["evt-17"]), services, evidence),
        lambda: _expect(Status.ACCEPTED, _json("payments", "CRITICAL", "x", ["evt-17"]), services, evidence),
        lambda: assert_repair(Status.ACCEPTED, ["{", valid], services, evidence, 2, 60, 2),
        lambda: assert_repair(Status.EXHAUSTED, ["{", valid], services, evidence, 1, 60, 1),
        lambda: assert_repair(Status.EXHAUSTED, ["{", valid], services, evidence, 2, 25, 0),
        lambda: assert_repair(Status.EXHAUSTED, [], services, evidence, 2, 60, 0),
        lambda: assert_repair(Status.EXHAUSTED, [_json("orders", "HIGH", "x", ["evt-17"])], services, evidence, 2, 60, 1),
        lambda: assert_policy_error(0, 60),
        lambda: assert_policy_error(2, -1),
        lambda: _expect(Status.SCHEMA_INVALID, '{"service":1,"severity":"HIGH","summary":"x","evidence_ids":["evt-17"]}', services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, '{"service":"payments","severity":"HIGH","summary":"x","evidence_ids":"evt-17"}', services, evidence),
        lambda: _expect(Status.SCHEMA_INVALID, '{"service":"payments","severity":"HIGH","summary":"x","evidence_ids":[1]}', services, evidence),
        lambda: _expect(Status.ACCEPTED, _json("payments", "HIGH", "x", ["log-22", "evt-17"]), services, evidence),
        lambda: _expect(Status.BUSINESS_INVALID, _json("payments", "HIGH", "x", ["evt-17", "foreign"]), services, evidence),
    ]
    for scenario in scenarios:
        scenario()

    state = 0x5EED
    accepted = 0
    for _ in range(10_000):
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        service = "payments" if (state >> 16) & 1 else "orders"
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        cited = "evt-17" if ((state >> 8) % 3) else "foreign"
        outcome = parse_and_validate(
            _json(service, "HIGH", "x", [cited]),
            allowed_services=services,
            allowed_evidence=evidence,
        )
        expected = service == "payments" and cited == "evt-17"
        assert (outcome.status is Status.ACCEPTED) == expected
        accepted += int(expected)

    print(f"PARITY|ACCEPTED|SYNTAX_INVALID|SCHEMA_INVALID|BUSINESS_INVALID|EXHAUSTED|{accepted}")
    print(f"Python structured-output contracts passed: {len(scenarios)} scenario groups + 10000 seeded trials")


def assert_repair(
    status: Status,
    raws: list[str],
    services: set[str],
    evidence: set[str],
    limit: int,
    budget: int,
    attempts: int,
) -> None:
    outcome = validate_with_repair(
        raws,
        allowed_services=services,
        allowed_evidence=evidence,
        max_attempts=limit,
        budget_ms=budget,
    )
    assert outcome.status is status and outcome.attempts == attempts


def assert_policy_error(limit: int, budget: int) -> None:
    try:
        validate_with_repair(
            [],
            allowed_services={"payments"},
            allowed_evidence={"evt-17"},
            max_attempts=limit,
            budget_ms=budget,
        )
    except ValueError:
        return
    raise AssertionError("invalid policy accepted")


if __name__ == "__main__":
    main()
