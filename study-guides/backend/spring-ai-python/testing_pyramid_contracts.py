"""Dependency-free contracts for evidence-producing test layers and release gates."""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum


class Layer(Enum):
    UNIT = 0
    ADAPTER = 1
    DATABASE = 2
    EVALUATION = 3
    LOAD = 4
    CLUSTER = 5


class Claim(Enum):
    ALGORITHM = 0
    WIRE_MAPPING = 1
    DATABASE_SCOPE = 2
    TASK_QUALITY = 3
    CAPACITY = 4
    RUNTIME_CONFIG = 5


class ProviderOutcome(Enum):
    SUCCESS = "SUCCESS"
    RATE_LIMITED = "RATE_LIMITED"
    SERVER_ERROR = "SERVER_ERROR"
    INVALID_RESPONSE = "INVALID_RESPONSE"
    TIMEOUT = "TIMEOUT"


class Gate(Enum):
    PROMOTE = "PROMOTE"
    BLOCK_MISSING = "BLOCK_MISSING"
    BLOCK_IDENTITY = "BLOCK_IDENTITY"
    BLOCK_SECURITY = "BLOCK_SECURITY"
    BLOCK_FUNCTIONAL = "BLOCK_FUNCTIONAL"
    BLOCK_QUALITY = "BLOCK_QUALITY"
    BLOCK_LOAD = "BLOCK_LOAD"


@dataclass(frozen=True)
class Row:
    id: str
    tenant: str
    score: float

    def __post_init__(self) -> None:
        if not self.id or not self.tenant or self.score != self.score:
            raise ValueError("invalid row")


@dataclass(frozen=True)
class Evidence:
    layer: Layer
    release_id: str
    fixture_revision: str
    tests: int
    failures: int
    security_failures: int
    metric: int
    threshold: int
    lower_is_better: bool

    def __post_init__(self) -> None:
        if (not self.release_id or not self.fixture_revision or self.tests <= 0
                or self.failures < 0 or self.failures > self.tests
                or self.security_failures < 0
                or self.security_failures > self.failures
                or self.metric < 0 or self.threshold < 0):
            raise ValueError("invalid evidence")

    def metric_passes(self) -> bool:
        return (self.metric <= self.threshold if self.lower_is_better
                else self.metric >= self.threshold)


def minimum_layer(claim: Claim) -> Layer:
    return {
        Claim.ALGORITHM: Layer.UNIT,
        Claim.WIRE_MAPPING: Layer.ADAPTER,
        Claim.DATABASE_SCOPE: Layer.DATABASE,
        Claim.TASK_QUALITY: Layer.EVALUATION,
        Claim.CAPACITY: Layer.LOAD,
        Claim.RUNTIME_CONFIG: Layer.CLUSTER,
    }[claim]


def proves(layer: Layer, claim: Claim) -> bool:
    return layer is minimum_layer(claim)


def provider_outcome(status: int, timed_out: bool, well_formed: bool) -> ProviderOutcome:
    if timed_out:
        return ProviderOutcome.TIMEOUT
    if status == 429:
        return ProviderOutcome.RATE_LIMITED
    if 500 <= status <= 599:
        return ProviderOutcome.SERVER_ERROR
    if 200 <= status <= 299 and well_formed:
        return ProviderOutcome.SUCCESS
    return ProviderOutcome.INVALID_RESPONSE


def scoped_row_ids(verified_tenant: str, rows: tuple[Row, ...], limit: int) -> tuple[str, ...]:
    if not verified_tenant or limit < 1:
        raise ValueError("invalid scope query")
    selected = sorted((row for row in rows if row.tenant == verified_tenant),
                      key=lambda row: (-row.score, row.id))
    return tuple(row.id for row in selected[:limit])


def release_gate(expected_release: str, evidence: list[Evidence]) -> Gate:
    if not expected_release:
        raise ValueError("invalid release gate")
    by_layer: dict[Layer, Evidence] = {}
    for item in evidence:
        if item.layer in by_layer:
            raise ValueError("duplicate layer evidence")
        by_layer[item.layer] = item
    if len(by_layer) != len(Layer):
        return Gate.BLOCK_MISSING
    if any(item.release_id != expected_release for item in evidence):
        return Gate.BLOCK_IDENTITY
    if any(item.security_failures > 0 for item in evidence):
        return Gate.BLOCK_SECURITY
    if any(item.failures > 0 for item in evidence):
        return Gate.BLOCK_FUNCTIONAL
    if not by_layer[Layer.EVALUATION].metric_passes():
        return Gate.BLOCK_QUALITY
    if not by_layer[Layer.LOAD].metric_passes():
        return Gate.BLOCK_LOAD
    return Gate.PROMOTE


def passing_evidence(release: str, quality_bp: int, load_p95_ms: int) -> list[Evidence]:
    result = []
    for layer in Layer:
        metric = quality_bp if layer is Layer.EVALUATION else load_p95_ms if layer is Layer.LOAD else 1
        threshold = 8_500 if layer is Layer.EVALUATION else 900 if layer is Layer.LOAD else 1
        result.append(Evidence(layer, release, "fixture-v15", 100, 0, 0,
                               metric, threshold, layer is Layer.LOAD))
    return result


def main() -> None:
    scenarios = 0
    for claim in Claim:
        assert proves(minimum_layer(claim), claim)
        scenarios += 1
    for result in (
        not proves(Layer.UNIT, Claim.DATABASE_SCOPE),
        not proves(Layer.ADAPTER, Claim.TASK_QUALITY),
        not proves(Layer.DATABASE, Claim.RUNTIME_CONFIG),
    ):
        assert result
        scenarios += 1

    provider_cases = (
        ((200, False, True), ProviderOutcome.SUCCESS),
        ((204, False, True), ProviderOutcome.SUCCESS),
        ((200, False, False), ProviderOutcome.INVALID_RESPONSE),
        ((400, False, True), ProviderOutcome.INVALID_RESPONSE),
        ((429, False, False), ProviderOutcome.RATE_LIMITED),
        ((503, False, False), ProviderOutcome.SERVER_ERROR),
        ((200, True, True), ProviderOutcome.TIMEOUT),
    )
    for args, expected in provider_cases:
        assert provider_outcome(*args) is expected
        scenarios += 1

    rows = (
        Row("p-low", "payments", 0.71), Row("exec", "executive", 0.99),
        Row("p-high", "payments", 0.91), Row("p-tie", "payments", 0.91),
    )
    assert scoped_row_ids("payments", rows, 2) == ("p-high", "p-tie")
    scenarios += 1
    assert scoped_row_ids("executive", rows, 2) == ("exec",)
    scenarios += 1
    assert scoped_row_ids("unknown", rows, 2) == ()
    scenarios += 1
    try:
        scoped_row_ids("payments", rows, 0)
        raise AssertionError
    except ValueError:
        scenarios += 1

    passing = passing_evidence("r15", 8_900, 720)
    assert release_gate("r15", passing) is Gate.PROMOTE
    scenarios += 1
    assert release_gate("r15", passing[:5]) is Gate.BLOCK_MISSING
    scenarios += 1
    wrong_identity = list(passing)
    wrong_identity[1] = replace(wrong_identity[1], release_id="r14")
    assert release_gate("r15", wrong_identity) is Gate.BLOCK_IDENTITY
    scenarios += 1
    security = list(passing)
    security[2] = replace(security[2], failures=1, security_failures=1)
    assert release_gate("r15", security) is Gate.BLOCK_SECURITY
    scenarios += 1
    functional = list(passing)
    functional[0] = replace(functional[0], failures=1)
    assert release_gate("r15", functional) is Gate.BLOCK_FUNCTIONAL
    scenarios += 1
    assert release_gate("r15", passing_evidence("r15", 8_499, 720)) is Gate.BLOCK_QUALITY
    scenarios += 1
    assert release_gate("r15", passing_evidence("r15", 8_900, 901)) is Gate.BLOCK_LOAD
    scenarios += 1
    try:
        release_gate("r15", passing + [passing[0]])
        raise AssertionError
    except ValueError:
        scenarios += 1
    try:
        Evidence(Layer.UNIT, "r15", "f", 1, 2, 0, 1, 1, False)
        raise AssertionError
    except ValueError:
        scenarios += 1

    state = 0x15C0DE
    promoted = 0
    observed: set[Gate] = set()
    for _ in range(10_000):
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        quality = state % 10_001
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        load = state % 2_001
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        failed = state % 37 == 0
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        security_failed = state % 101 == 0
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        wrong_release = state % 97 == 0

        evidence = passing_evidence("r15", quality, load)
        if failed or security_failed:
            evidence[0] = replace(evidence[0], failures=1,
                                  security_failures=1 if security_failed else 0)
        if wrong_release:
            evidence[1] = replace(evidence[1], release_id="other")
        actual = release_gate("r15", evidence)
        oracle = (Gate.BLOCK_IDENTITY if wrong_release else
                  Gate.BLOCK_SECURITY if security_failed else
                  Gate.BLOCK_FUNCTIONAL if failed else
                  Gate.BLOCK_QUALITY if quality < 8_500 else
                  Gate.BLOCK_LOAD if load > 900 else Gate.PROMOTE)
        assert actual is oracle
        observed.add(actual)
        promoted += int(actual is Gate.PROMOTE)

    assert scenarios == 29
    assert {Gate.PROMOTE, Gate.BLOCK_IDENTITY, Gate.BLOCK_SECURITY,
            Gate.BLOCK_FUNCTIONAL, Gate.BLOCK_QUALITY, Gate.BLOCK_LOAD} <= observed
    print("PARITY|FAST=UNIT_ADAPTER|DB=REAL_PGVECTOR|QUALITY=FIXED_DATASET"
          "|LOAD=SLO_THRESHOLDS|CLUSTER=PROBES|RELEASE=EVIDENCE_GATED"
          f"|PROMOTED={promoted}")
    print(f"Python testing-pyramid contracts passed: {scenarios} scenario groups"
          " + 10000 seeded release-gate trials")


if __name__ == "__main__":
    main()
