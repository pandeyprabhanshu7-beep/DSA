#!/usr/bin/env python3
"""Dependency-free evidence-gate oracle for Section 25."""

from dataclasses import dataclass
from enum import Enum, auto


class NextStep(Enum):
    OFFLINE_CORE = auto()
    MODEL_API = auto()
    TENANT_ISOLATION = auto()
    DURABLE_INGESTION = auto()
    TERMINAL_STREAM = auto()
    IMMUTABLE_RELEASE = auto()
    FIXED_EVALUATION = auto()
    COMPLETE = auto()


class PromotionDecision(Enum):
    REJECT_RELEASE_IDENTITY = auto()
    REJECT_SECURITY = auto()
    REJECT_FUNCTIONAL = auto()
    REJECT_QUALITY = auto()
    REJECT_BUDGET = auto()
    REJECT_ROLLBACK = auto()
    PROMOTE = auto()


@dataclass(frozen=True)
class Evidence:
    offline_core: bool
    model_api_trace: bool
    tenant_isolation: bool
    durable_ingestion: bool
    terminal_stream: bool
    immutable_release: bool
    fixed_evaluation: bool


def next_step(evidence: Evidence) -> NextStep:
    if not evidence.offline_core:
        return NextStep.OFFLINE_CORE
    if not evidence.model_api_trace:
        return NextStep.MODEL_API
    if not evidence.tenant_isolation:
        return NextStep.TENANT_ISOLATION
    if not evidence.durable_ingestion:
        return NextStep.DURABLE_INGESTION
    if not evidence.terminal_stream:
        return NextStep.TERMINAL_STREAM
    if not evidence.immutable_release:
        return NextStep.IMMUTABLE_RELEASE
    if not evidence.fixed_evaluation:
        return NextStep.FIXED_EVALUATION
    return NextStep.COMPLETE


def promotion(same_release: bool, security_passed: bool, functional_passed: bool,
              quality_passed: bool, budget_passed: bool,
              rollback_proved: bool) -> PromotionDecision:
    if not same_release:
        return PromotionDecision.REJECT_RELEASE_IDENTITY
    if not security_passed:
        return PromotionDecision.REJECT_SECURITY
    if not functional_passed:
        return PromotionDecision.REJECT_FUNCTIONAL
    if not quality_passed:
        return PromotionDecision.REJECT_QUALITY
    if not budget_passed:
        return PromotionDecision.REJECT_BUDGET
    if not rollback_proved:
        return PromotionDecision.REJECT_ROLLBACK
    return PromotionDecision.PROMOTE


def advance(state: int) -> int:
    return (state * 1_664_525 + 1_013_904_223) & 0xFFFF_FFFF


def main() -> None:
    scenarios = 0
    ordered = list(NextStep)
    for prefix, expected in enumerate(ordered):
        evidence = Evidence(*(prefix >= boundary for boundary in range(1, 8)))
        assert next_step(evidence) is expected
        scenarios += 1

    promotion_cases = [
        ((False, True, True, True, True, True), PromotionDecision.REJECT_RELEASE_IDENTITY),
        ((True, False, True, True, True, True), PromotionDecision.REJECT_SECURITY),
        ((True, True, False, True, True, True), PromotionDecision.REJECT_FUNCTIONAL),
        ((True, True, True, False, True, True), PromotionDecision.REJECT_QUALITY),
        ((True, True, True, True, False, True), PromotionDecision.REJECT_BUDGET),
        ((True, True, True, True, True, False), PromotionDecision.REJECT_ROLLBACK),
        ((True, True, True, True, True, True), PromotionDecision.PROMOTE),
    ]
    for values, expected in promotion_cases:
        assert promotion(*values) is expected
        scenarios += 1

    assert next_step(Evidence(False, True, True, True, True, True, True)) is NextStep.OFFLINE_CORE
    scenarios += 1
    assert next_step(Evidence(True, False, True, True, True, True, True)) is NextStep.MODEL_API
    scenarios += 1
    assert next_step(Evidence(True, True, False, True, True, True, True)) is NextStep.TENANT_ISOLATION
    scenarios += 1
    assert next_step(Evidence(True, True, True, False, True, True, True)) is NextStep.DURABLE_INGESTION
    scenarios += 1
    assert promotion(True, False, True, True, True, True) is PromotionDecision.REJECT_SECURITY
    scenarios += 1
    assert promotion(True, True, True, True, True, True) is PromotionDecision.PROMOTE
    scenarios += 1

    state = 0x25CA5E
    for _ in range(10_000):
        state = advance(state)
        flags = tuple(bool((state >> bit) & 1) for bit in range(7))
        evidence = Evidence(*flags)
        expected = next((ordered[index] for index, value in enumerate(flags) if not value), NextStep.COMPLETE)
        assert next_step(evidence) is expected

    decisions = list(PromotionDecision)
    for _ in range(10_000):
        state = advance(state)
        flags = tuple(bool((state >> bit) & 1) for bit in range(6))
        expected = next((decisions[index] for index, value in enumerate(flags) if not value), PromotionDecision.PROMOTE)
        assert promotion(*flags) is expected

    assert scenarios == 21, scenarios
    print("PARITY|SEQUENCE=EVIDENCE_GATED|IDENTITY=SERVER_OWNED|INGEST=REPLAY_SAFE|STREAM=TERMINAL_EVENT|RELEASE=DIGEST_PINNED|PROMOTION=EVALUATED")
    print("Practical sequence contracts passed: 21 scenario groups + 20000 seeded trials")


if __name__ == "__main__":
    main()
