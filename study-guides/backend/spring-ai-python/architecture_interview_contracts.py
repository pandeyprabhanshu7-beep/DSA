from enum import Enum, auto


class KnowledgeDecision(Enum):
    READY = auto()
    DENY_SCOPE = auto()
    NO_EVIDENCE = auto()
    STALE_GENERATION = auto()
    INVALID_CITATIONS = auto()


class RouteDecision(Enum):
    ROUTE = auto()
    REJECT_POLICY = auto()
    REJECT_CAPABILITY = auto()
    REJECT_BUDGET = auto()
    REJECT_DEADLINE = auto()
    NO_HEALTHY_ROUTE = auto()


class ChangeDecision(Enum):
    EXECUTE = auto()
    REAPPROVE = auto()
    REVALIDATE = auto()
    DENY_SCOPE = auto()
    RECONCILE = auto()


class IngestDecision(Enum):
    ACCEPT = auto()
    REJECT_IDENTITY = auto()
    RETRY_STAGE = auto()
    QUARANTINE = auto()
    PUBLISH = auto()


def knowledge(scope_verified: bool, authorized_evidence: bool,
              generation_current: bool, citations_valid: bool) -> KnowledgeDecision:
    if not scope_verified:
        return KnowledgeDecision.DENY_SCOPE
    if not authorized_evidence:
        return KnowledgeDecision.NO_EVIDENCE
    if not generation_current:
        return KnowledgeDecision.STALE_GENERATION
    return KnowledgeDecision.READY if citations_valid else KnowledgeDecision.INVALID_CITATIONS


def route(policy_approved: bool, capability_match: bool, within_budget: bool,
          deadline_feasible: bool, healthy_route: bool) -> RouteDecision:
    if not policy_approved:
        return RouteDecision.REJECT_POLICY
    if not capability_match:
        return RouteDecision.REJECT_CAPABILITY
    if not within_budget:
        return RouteDecision.REJECT_BUDGET
    if not deadline_feasible:
        return RouteDecision.REJECT_DEADLINE
    return RouteDecision.ROUTE if healthy_route else RouteDecision.NO_HEALTHY_ROUTE


def change(approval_matches_digest: bool, preconditions_current: bool,
           scope_authorized: bool, prior_effect_unknown: bool) -> ChangeDecision:
    if not approval_matches_digest:
        return ChangeDecision.REAPPROVE
    if not scope_authorized:
        return ChangeDecision.DENY_SCOPE
    if prior_effect_unknown:
        return ChangeDecision.RECONCILE
    return ChangeDecision.EXECUTE if preconditions_current else ChangeDecision.REVALIDATE


def ingest(immutable_identity: bool, stage_complete: bool, poison_failure: bool,
           generation_validated: bool) -> IngestDecision:
    if not immutable_identity:
        return IngestDecision.REJECT_IDENTITY
    if poison_failure:
        return IngestDecision.QUARANTINE
    if not stage_complete:
        return IngestDecision.RETRY_STAGE
    return IngestDecision.PUBLISH if generation_validated else IngestDecision.ACCEPT


def next_state(state: int) -> int:
    return (state * 1_664_525 + 1_013_904_223) & 0xFFFF_FFFF


def main() -> None:
    scenarios = 0

    def check(condition: bool) -> None:
        nonlocal scenarios
        assert condition
        scenarios += 1

    check(knowledge(False, True, True, True) is KnowledgeDecision.DENY_SCOPE)
    check(knowledge(True, False, True, True) is KnowledgeDecision.NO_EVIDENCE)
    check(knowledge(True, True, False, True) is KnowledgeDecision.STALE_GENERATION)
    check(knowledge(True, True, True, False) is KnowledgeDecision.INVALID_CITATIONS)
    check(knowledge(True, True, True, True) is KnowledgeDecision.READY)

    check(route(False, True, True, True, True) is RouteDecision.REJECT_POLICY)
    check(route(True, False, True, True, True) is RouteDecision.REJECT_CAPABILITY)
    check(route(True, True, False, True, True) is RouteDecision.REJECT_BUDGET)
    check(route(True, True, True, False, True) is RouteDecision.REJECT_DEADLINE)
    check(route(True, True, True, True, False) is RouteDecision.NO_HEALTHY_ROUTE)
    check(route(True, True, True, True, True) is RouteDecision.ROUTE)

    check(change(False, True, True, False) is ChangeDecision.REAPPROVE)
    check(change(True, True, False, False) is ChangeDecision.DENY_SCOPE)
    check(change(True, True, True, True) is ChangeDecision.RECONCILE)
    check(change(True, False, True, False) is ChangeDecision.REVALIDATE)
    check(change(True, True, True, False) is ChangeDecision.EXECUTE)

    check(ingest(False, True, False, True) is IngestDecision.REJECT_IDENTITY)
    check(ingest(True, False, False, False) is IngestDecision.RETRY_STAGE)
    check(ingest(True, False, True, False) is IngestDecision.QUARANTINE)
    check(ingest(True, True, False, False) is IngestDecision.ACCEPT)
    check(ingest(True, True, False, True) is IngestDecision.PUBLISH)

    state = 0xA2417E11
    for _ in range(10_000):
        state = next_state(state); scope = bool(state & 1)
        state = next_state(state); evidence = bool(state & 1)
        state = next_state(state); generation = bool(state & 1)
        state = next_state(state); citations = bool(state & 1)
        expected = (KnowledgeDecision.DENY_SCOPE if not scope else
                    KnowledgeDecision.NO_EVIDENCE if not evidence else
                    KnowledgeDecision.STALE_GENERATION if not generation else
                    KnowledgeDecision.READY if citations else KnowledgeDecision.INVALID_CITATIONS)
        assert knowledge(scope, evidence, generation, citations) is expected

    for _ in range(10_000):
        state = next_state(state); policy = bool(state & 1)
        state = next_state(state); capability = bool(state & 1)
        state = next_state(state); budget = bool(state & 1)
        state = next_state(state); deadline = bool(state & 1)
        state = next_state(state); healthy = bool(state & 1)
        expected = (RouteDecision.REJECT_POLICY if not policy else
                    RouteDecision.REJECT_CAPABILITY if not capability else
                    RouteDecision.REJECT_BUDGET if not budget else
                    RouteDecision.REJECT_DEADLINE if not deadline else
                    RouteDecision.ROUTE if healthy else RouteDecision.NO_HEALTHY_ROUTE)
        assert route(policy, capability, budget, deadline, healthy) is expected

    assert scenarios == 21, scenarios
    print("PARITY|KNOWLEDGE=AUTHORIZED|ROUTE=CAPABILITY_GATED|CHANGE=DIGEST_BOUND|INGEST=GENERATION_PUBLISHED")
    print("Architecture interview contracts passed: 21 scenario groups + 20000 seeded trials")


if __name__ == "__main__":
    main()
