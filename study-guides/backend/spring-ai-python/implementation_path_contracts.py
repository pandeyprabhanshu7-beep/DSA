"""Dependency-free contracts for a four-week, evidence-producing implementation path."""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum


class Week(Enum):
    OFFLINE_CORE = 0
    MODEL_RETRIEVAL = 1
    MCP_BOUNDARY = 2
    DISPOSABLE_DEPLOYMENT = 3


class Gate(Enum):
    READY = "READY"
    BLOCK_MISSING = "BLOCK_MISSING"
    BLOCK_IDENTITY = "BLOCK_IDENTITY"
    BLOCK_SECURITY = "BLOCK_SECURITY"
    BLOCK_WEEK1 = "BLOCK_WEEK1"
    BLOCK_WEEK2 = "BLOCK_WEEK2"
    BLOCK_WEEK3 = "BLOCK_WEEK3"
    BLOCK_WEEK4 = "BLOCK_WEEK4"


class RetrievalChoice(Enum):
    LEXICAL = "LEXICAL"
    VECTOR = "VECTOR"
    HYBRID = "HYBRID"
    NO_SAFE_WINNER = "NO_SAFE_WINNER"


class McpOutcome(Enum):
    READY = "READY"
    BLOCK_DISCOVERY = "BLOCK_DISCOVERY"
    BLOCK_SCHEMA = "BLOCK_SCHEMA"
    BLOCK_AUTH = "BLOCK_AUTH"
    BLOCK_TIMEOUT = "BLOCK_TIMEOUT"


class DeployOutcome(Enum):
    READY = "READY"
    BLOCK_RELEASE = "BLOCK_RELEASE"
    BLOCK_STARTUP = "BLOCK_STARTUP"
    BLOCK_READINESS = "BLOCK_READINESS"
    BLOCK_RESILIENCE = "BLOCK_RESILIENCE"
    BLOCK_EVALUATION = "BLOCK_EVALUATION"


@dataclass(frozen=True)
class WeekEvidence:
    week: Week
    release_id: str
    fixture_revision: str
    functional_pass: bool
    security_pass: bool
    score_basis_points: int
    minimum_basis_points: int

    def __post_init__(self) -> None:
        if (not self.release_id or not self.fixture_revision
                or not 0 <= self.score_basis_points <= 10_000
                or not 0 <= self.minimum_basis_points <= 10_000):
            raise ValueError("invalid week evidence")

    def passes(self) -> bool:
        return self.functional_pass and self.score_basis_points >= self.minimum_basis_points


@dataclass(frozen=True)
class RetrievalEvidence:
    release_id: str
    model_revision: str
    corpus_revision: str
    lexical_supported: int
    vector_supported: int
    hybrid_supported: int
    lexical_unsafe: int
    vector_unsafe: int
    hybrid_unsafe: int

    def __post_init__(self) -> None:
        counts = (self.lexical_supported, self.vector_supported, self.hybrid_supported,
                  self.lexical_unsafe, self.vector_unsafe, self.hybrid_unsafe)
        if (not self.release_id or not self.model_revision or not self.corpus_revision
                or any(value < 0 for value in counts)):
            raise ValueError("invalid retrieval evidence")


@dataclass(frozen=True)
class DecisionRecord:
    context: str
    decision: str
    rejected_alternative: str
    reconsider_metric: str

    def __post_init__(self) -> None:
        if any(not value or not value.strip() for value in (
                self.context, self.decision, self.rejected_alternative,
                self.reconsider_metric)):
            raise ValueError("incomplete decision record")


def retrieval_choice(evidence: RetrievalEvidence) -> RetrievalChoice:
    candidates = (
        (RetrievalChoice.LEXICAL, evidence.lexical_supported, evidence.lexical_unsafe),
        (RetrievalChoice.VECTOR, evidence.vector_supported, evidence.vector_unsafe),
        (RetrievalChoice.HYBRID, evidence.hybrid_supported, evidence.hybrid_unsafe),
    )
    safe = [candidate for candidate in candidates if candidate[2] == 0]
    if not safe:
        return RetrievalChoice.NO_SAFE_WINNER
    return max(safe, key=lambda candidate: candidate[1])[0]


def mcp_outcome(discovered: bool, schema_valid: bool,
                authorized_negative_passed: bool, timeout_bounded: bool) -> McpOutcome:
    if not discovered:
        return McpOutcome.BLOCK_DISCOVERY
    if not schema_valid:
        return McpOutcome.BLOCK_SCHEMA
    if not authorized_negative_passed:
        return McpOutcome.BLOCK_AUTH
    if not timeout_bounded:
        return McpOutcome.BLOCK_TIMEOUT
    return McpOutcome.READY


def deploy_outcome(same_release: bool, startup_passed: bool, readiness_passed: bool,
                   outage_and_overload_passed: bool, evaluation_linked: bool) -> DeployOutcome:
    if not same_release:
        return DeployOutcome.BLOCK_RELEASE
    if not startup_passed:
        return DeployOutcome.BLOCK_STARTUP
    if not readiness_passed:
        return DeployOutcome.BLOCK_READINESS
    if not outage_and_overload_passed:
        return DeployOutcome.BLOCK_RESILIENCE
    if not evaluation_linked:
        return DeployOutcome.BLOCK_EVALUATION
    return DeployOutcome.READY


def release_gate(expected_release: str, evidence: list[WeekEvidence]) -> Gate:
    if not expected_release:
        raise ValueError("invalid release gate")
    by_week: dict[Week, WeekEvidence] = {}
    for item in evidence:
        if item.week in by_week:
            raise ValueError("duplicate week evidence")
        by_week[item.week] = item
    if len(by_week) != len(Week):
        return Gate.BLOCK_MISSING
    if any(item.release_id != expected_release for item in evidence):
        return Gate.BLOCK_IDENTITY
    if any(not item.security_pass for item in evidence):
        return Gate.BLOCK_SECURITY
    failures = (Gate.BLOCK_WEEK1, Gate.BLOCK_WEEK2, Gate.BLOCK_WEEK3, Gate.BLOCK_WEEK4)
    for week, failure in zip(Week, failures):
        if not by_week[week].passes():
            return failure
    return Gate.READY


def passing_evidence(release: str) -> list[WeekEvidence]:
    return [WeekEvidence(week, release, "atlas-path-v16", True, True, 9_000, 8_500)
            for week in Week]


def main() -> None:
    scenarios = 0
    DecisionRecord("lexical misses paraphrases", "measure hybrid retrieval",
                   "change the model and retriever together", "recall@5 < 0.90")
    scenarios += 1
    incomplete = ("", None, "  ", "")
    for index, value in enumerate(incomplete):
        fields = ["context", "decision", "alternative", "metric"]
        fields[index] = value
        try:
            DecisionRecord(*fields)
            raise AssertionError
        except ValueError:
            scenarios += 1

    retrieval_cases = (
        (RetrievalEvidence("r16", "m1", "c1", 60, 78, 91, 0, 0, 0), RetrievalChoice.HYBRID),
        (RetrievalEvidence("r16", "m1", "c1", 88, 80, 85, 0, 0, 0), RetrievalChoice.LEXICAL),
        (RetrievalEvidence("r16", "m1", "c1", 60, 92, 91, 0, 1, 0), RetrievalChoice.HYBRID),
        (RetrievalEvidence("r16", "m1", "c1", 60, 92, 91, 1, 1, 1), RetrievalChoice.NO_SAFE_WINNER),
    )
    for evidence, expected in retrieval_cases:
        assert retrieval_choice(evidence) is expected
        scenarios += 1

    mcp_cases = (
        ((True, True, True, True), McpOutcome.READY),
        ((False, True, True, True), McpOutcome.BLOCK_DISCOVERY),
        ((True, False, True, True), McpOutcome.BLOCK_SCHEMA),
        ((True, True, False, True), McpOutcome.BLOCK_AUTH),
        ((True, True, True, False), McpOutcome.BLOCK_TIMEOUT),
    )
    for args, expected in mcp_cases:
        assert mcp_outcome(*args) is expected
        scenarios += 1

    deploy_cases = (
        ((True, True, True, True, True), DeployOutcome.READY),
        ((False, True, True, True, True), DeployOutcome.BLOCK_RELEASE),
        ((True, False, True, True, True), DeployOutcome.BLOCK_STARTUP),
        ((True, True, False, True, True), DeployOutcome.BLOCK_READINESS),
        ((True, True, True, False, True), DeployOutcome.BLOCK_RESILIENCE),
        ((True, True, True, True, False), DeployOutcome.BLOCK_EVALUATION),
    )
    for args, expected in deploy_cases:
        assert deploy_outcome(*args) is expected
        scenarios += 1

    passing = passing_evidence("r16")
    assert release_gate("r16", passing) is Gate.READY
    scenarios += 1
    assert release_gate("r16", passing[:3]) is Gate.BLOCK_MISSING
    scenarios += 1
    wrong = list(passing)
    wrong[1] = replace(wrong[1], release_id="r15")
    assert release_gate("r16", wrong) is Gate.BLOCK_IDENTITY
    scenarios += 1
    security = list(passing)
    security[2] = replace(security[2], security_pass=False)
    assert release_gate("r16", security) is Gate.BLOCK_SECURITY
    scenarios += 1
    failure_gates = (Gate.BLOCK_WEEK1, Gate.BLOCK_WEEK2, Gate.BLOCK_WEEK3, Gate.BLOCK_WEEK4)
    for index, expected in enumerate(failure_gates):
        failed = list(passing)
        failed[index] = replace(failed[index], functional_pass=False)
        assert release_gate("r16", failed) is expected
        scenarios += 1
    try:
        release_gate("r16", passing + [passing[0]])
        raise AssertionError
    except ValueError:
        scenarios += 1
    try:
        release_gate("", passing)
        raise AssertionError
    except ValueError:
        scenarios += 1
    try:
        WeekEvidence(Week.OFFLINE_CORE, "r16", "fixture", True, True, 10_001, 8_500)
        raise AssertionError
    except ValueError:
        scenarios += 1

    state = 0x16A71A5
    ready = 0
    observed: set[Gate] = set()
    for _ in range(10_000):
        evidence = passing_evidence("r16")
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        wrong_identity = state % 97 == 0
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        security_failure = state % 89 == 0
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        failed_week = state % 9 - 5
        if wrong_identity:
            evidence[1] = replace(evidence[1], release_id="other")
        if security_failure:
            evidence[2] = replace(evidence[2], security_pass=False)
        if failed_week >= 0:
            evidence[failed_week] = replace(evidence[failed_week], functional_pass=False)
        actual = release_gate("r16", evidence)
        oracle = (Gate.BLOCK_IDENTITY if wrong_identity else
                  Gate.BLOCK_SECURITY if security_failure else
                  failure_gates[failed_week] if failed_week >= 0 else Gate.READY)
        assert actual is oracle
        observed.add(actual)
        ready += int(actual is Gate.READY)

    assert scenarios == 31
    assert {Gate.READY, Gate.BLOCK_IDENTITY, Gate.BLOCK_SECURITY,
            Gate.BLOCK_WEEK1, Gate.BLOCK_WEEK2, Gate.BLOCK_WEEK3,
            Gate.BLOCK_WEEK4} <= observed
    print("PARITY|W1=OFFLINE_BASELINE|W2=MODEL_RETRIEVAL|W3=MCP_BOUNDARY"
          "|W4=DISPOSABLE_DEPLOYMENT|ADR=MEASURED|PROMOTION=EVIDENCE_CHAIN"
          f"|READY={ready}")
    print(f"Python implementation-path contracts passed: {scenarios} scenario groups"
          " + 10000 seeded evidence-chain trials")


if __name__ == "__main__":
    main()
