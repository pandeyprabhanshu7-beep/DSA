public class ArchitectureInterviewContracts {
    private static int scenarios;

    enum KnowledgeDecision { READY, DENY_SCOPE, NO_EVIDENCE, STALE_GENERATION, INVALID_CITATIONS }
    enum RouteDecision { ROUTE, REJECT_POLICY, REJECT_CAPABILITY, REJECT_BUDGET, REJECT_DEADLINE, NO_HEALTHY_ROUTE }
    enum ChangeDecision { EXECUTE, REAPPROVE, REVALIDATE, DENY_SCOPE, RECONCILE }
    enum IngestDecision { ACCEPT, REJECT_IDENTITY, RETRY_STAGE, QUARANTINE, PUBLISH }

    static KnowledgeDecision knowledge(boolean scopeVerified, boolean authorizedEvidence,
                                       boolean generationCurrent, boolean citationsValid) {
        if (!scopeVerified) return KnowledgeDecision.DENY_SCOPE;
        if (!authorizedEvidence) return KnowledgeDecision.NO_EVIDENCE;
        if (!generationCurrent) return KnowledgeDecision.STALE_GENERATION;
        return citationsValid ? KnowledgeDecision.READY : KnowledgeDecision.INVALID_CITATIONS;
    }

    static RouteDecision route(boolean policyApproved, boolean capabilityMatch,
                               boolean withinBudget, boolean deadlineFeasible,
                               boolean healthyRoute) {
        if (!policyApproved) return RouteDecision.REJECT_POLICY;
        if (!capabilityMatch) return RouteDecision.REJECT_CAPABILITY;
        if (!withinBudget) return RouteDecision.REJECT_BUDGET;
        if (!deadlineFeasible) return RouteDecision.REJECT_DEADLINE;
        return healthyRoute ? RouteDecision.ROUTE : RouteDecision.NO_HEALTHY_ROUTE;
    }

    static ChangeDecision change(boolean approvalMatchesDigest, boolean preconditionsCurrent,
                                 boolean scopeAuthorized, boolean priorEffectUnknown) {
        if (!approvalMatchesDigest) return ChangeDecision.REAPPROVE;
        if (!scopeAuthorized) return ChangeDecision.DENY_SCOPE;
        if (priorEffectUnknown) return ChangeDecision.RECONCILE;
        return preconditionsCurrent ? ChangeDecision.EXECUTE : ChangeDecision.REVALIDATE;
    }

    static IngestDecision ingest(boolean immutableIdentity, boolean stageComplete,
                                 boolean poisonFailure, boolean generationValidated) {
        if (!immutableIdentity) return IngestDecision.REJECT_IDENTITY;
        if (poisonFailure) return IngestDecision.QUARANTINE;
        if (!stageComplete) return IngestDecision.RETRY_STAGE;
        return generationValidated ? IngestDecision.PUBLISH : IngestDecision.ACCEPT;
    }

    static long next(long state) {
        return (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
    }

    static void check(boolean condition) {
        assert condition;
        scenarios++;
    }

    public static void main(String[] args) {
        check(knowledge(false, true, true, true) == KnowledgeDecision.DENY_SCOPE);
        check(knowledge(true, false, true, true) == KnowledgeDecision.NO_EVIDENCE);
        check(knowledge(true, true, false, true) == KnowledgeDecision.STALE_GENERATION);
        check(knowledge(true, true, true, false) == KnowledgeDecision.INVALID_CITATIONS);
        check(knowledge(true, true, true, true) == KnowledgeDecision.READY);

        check(route(false, true, true, true, true) == RouteDecision.REJECT_POLICY);
        check(route(true, false, true, true, true) == RouteDecision.REJECT_CAPABILITY);
        check(route(true, true, false, true, true) == RouteDecision.REJECT_BUDGET);
        check(route(true, true, true, false, true) == RouteDecision.REJECT_DEADLINE);
        check(route(true, true, true, true, false) == RouteDecision.NO_HEALTHY_ROUTE);
        check(route(true, true, true, true, true) == RouteDecision.ROUTE);

        check(change(false, true, true, false) == ChangeDecision.REAPPROVE);
        check(change(true, true, false, false) == ChangeDecision.DENY_SCOPE);
        check(change(true, true, true, true) == ChangeDecision.RECONCILE);
        check(change(true, false, true, false) == ChangeDecision.REVALIDATE);
        check(change(true, true, true, false) == ChangeDecision.EXECUTE);

        check(ingest(false, true, false, true) == IngestDecision.REJECT_IDENTITY);
        check(ingest(true, false, false, false) == IngestDecision.RETRY_STAGE);
        check(ingest(true, false, true, false) == IngestDecision.QUARANTINE);
        check(ingest(true, true, false, false) == IngestDecision.ACCEPT);
        check(ingest(true, true, false, true) == IngestDecision.PUBLISH);

        long state = 0xA2417E11L;
        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); boolean scope = (state & 1) != 0;
            state = next(state); boolean evidence = (state & 1) != 0;
            state = next(state); boolean generation = (state & 1) != 0;
            state = next(state); boolean citations = (state & 1) != 0;
            KnowledgeDecision expected = !scope ? KnowledgeDecision.DENY_SCOPE
                    : !evidence ? KnowledgeDecision.NO_EVIDENCE
                    : !generation ? KnowledgeDecision.STALE_GENERATION
                    : citations ? KnowledgeDecision.READY : KnowledgeDecision.INVALID_CITATIONS;
            assert knowledge(scope, evidence, generation, citations) == expected;
        }

        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); boolean policy = (state & 1) != 0;
            state = next(state); boolean capability = (state & 1) != 0;
            state = next(state); boolean budget = (state & 1) != 0;
            state = next(state); boolean deadline = (state & 1) != 0;
            state = next(state); boolean healthy = (state & 1) != 0;
            RouteDecision expected = !policy ? RouteDecision.REJECT_POLICY
                    : !capability ? RouteDecision.REJECT_CAPABILITY
                    : !budget ? RouteDecision.REJECT_BUDGET
                    : !deadline ? RouteDecision.REJECT_DEADLINE
                    : healthy ? RouteDecision.ROUTE : RouteDecision.NO_HEALTHY_ROUTE;
            assert route(policy, capability, budget, deadline, healthy) == expected;
        }

        assert scenarios == 21 : "scenario count " + scenarios;
        System.out.println("PARITY|KNOWLEDGE=AUTHORIZED|ROUTE=CAPABILITY_GATED|CHANGE=DIGEST_BOUND|INGEST=GENERATION_PUBLISHED");
        System.out.println("Architecture interview contracts passed: 21 scenario groups + 20000 seeded trials");
    }
}
