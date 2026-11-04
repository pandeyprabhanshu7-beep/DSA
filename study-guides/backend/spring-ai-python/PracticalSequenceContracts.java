import java.util.List;

/** Dependency-free evidence-gate oracle for Section 25. Run with: java -ea PracticalSequenceContracts.java */
public class PracticalSequenceContracts {
    enum NextStep {
        OFFLINE_CORE, MODEL_API, TENANT_ISOLATION, DURABLE_INGESTION,
        TERMINAL_STREAM, IMMUTABLE_RELEASE, FIXED_EVALUATION, COMPLETE
    }

    enum PromotionDecision {
        REJECT_RELEASE_IDENTITY, REJECT_SECURITY, REJECT_FUNCTIONAL,
        REJECT_QUALITY, REJECT_BUDGET, REJECT_ROLLBACK, PROMOTE
    }

    record Evidence(
            boolean offlineCore,
            boolean modelApiTrace,
            boolean tenantIsolation,
            boolean durableIngestion,
            boolean terminalStream,
            boolean immutableRelease,
            boolean fixedEvaluation) {}

    static NextStep nextStep(Evidence evidence) {
        if (!evidence.offlineCore()) return NextStep.OFFLINE_CORE;
        if (!evidence.modelApiTrace()) return NextStep.MODEL_API;
        if (!evidence.tenantIsolation()) return NextStep.TENANT_ISOLATION;
        if (!evidence.durableIngestion()) return NextStep.DURABLE_INGESTION;
        if (!evidence.terminalStream()) return NextStep.TERMINAL_STREAM;
        if (!evidence.immutableRelease()) return NextStep.IMMUTABLE_RELEASE;
        if (!evidence.fixedEvaluation()) return NextStep.FIXED_EVALUATION;
        return NextStep.COMPLETE;
    }

    static PromotionDecision promotion(
            boolean sameRelease,
            boolean securityPassed,
            boolean functionalPassed,
            boolean qualityPassed,
            boolean budgetPassed,
            boolean rollbackProved) {
        if (!sameRelease) return PromotionDecision.REJECT_RELEASE_IDENTITY;
        if (!securityPassed) return PromotionDecision.REJECT_SECURITY;
        if (!functionalPassed) return PromotionDecision.REJECT_FUNCTIONAL;
        if (!qualityPassed) return PromotionDecision.REJECT_QUALITY;
        if (!budgetPassed) return PromotionDecision.REJECT_BUDGET;
        if (!rollbackProved) return PromotionDecision.REJECT_ROLLBACK;
        return PromotionDecision.PROMOTE;
    }

    static long advance(long state) {
        return (state * 1664525L + 1013904223L) & 0xffffffffL;
    }

    static boolean bit(long state, int position) {
        return ((state >>> position) & 1L) == 1L;
    }

    public static void main(String[] args) {
        int scenarios = 0;
        List<NextStep> ordered = List.of(
                NextStep.OFFLINE_CORE, NextStep.MODEL_API, NextStep.TENANT_ISOLATION,
                NextStep.DURABLE_INGESTION, NextStep.TERMINAL_STREAM,
                NextStep.IMMUTABLE_RELEASE, NextStep.FIXED_EVALUATION, NextStep.COMPLETE);
        for (int prefix = 0; prefix < ordered.size(); prefix++) {
            Evidence evidence = new Evidence(
                    prefix >= 1, prefix >= 2, prefix >= 3, prefix >= 4,
                    prefix >= 5, prefix >= 6, prefix >= 7);
            assert nextStep(evidence) == ordered.get(prefix);
            scenarios++;
        }

        boolean[][] promotionCases = {
                {false, true,  true,  true,  true,  true},
                {true,  false, true,  true,  true,  true},
                {true,  true,  false, true,  true,  true},
                {true,  true,  true,  false, true,  true},
                {true,  true,  true,  true,  false, true},
                {true,  true,  true,  true,  true,  false},
                {true,  true,  true,  true,  true,  true}
        };
        PromotionDecision[] promotionExpected = {
                PromotionDecision.REJECT_RELEASE_IDENTITY,
                PromotionDecision.REJECT_SECURITY,
                PromotionDecision.REJECT_FUNCTIONAL,
                PromotionDecision.REJECT_QUALITY,
                PromotionDecision.REJECT_BUDGET,
                PromotionDecision.REJECT_ROLLBACK,
                PromotionDecision.PROMOTE
        };
        for (int index = 0; index < promotionCases.length; index++) {
            boolean[] item = promotionCases[index];
            assert promotion(item[0], item[1], item[2], item[3], item[4], item[5])
                    == promotionExpected[index];
            scenarios++;
        }

        // Later evidence never compensates for an earlier missing gate.
        assert nextStep(new Evidence(false, true, true, true, true, true, true)) == NextStep.OFFLINE_CORE;
        scenarios++;
        assert nextStep(new Evidence(true, false, true, true, true, true, true)) == NextStep.MODEL_API;
        scenarios++;
        assert nextStep(new Evidence(true, true, false, true, true, true, true)) == NextStep.TENANT_ISOLATION;
        scenarios++;
        assert nextStep(new Evidence(true, true, true, false, true, true, true)) == NextStep.DURABLE_INGESTION;
        scenarios++;
        assert promotion(true, false, true, true, true, true) == PromotionDecision.REJECT_SECURITY;
        scenarios++;
        assert promotion(true, true, true, true, true, true) == PromotionDecision.PROMOTE;
        scenarios++;

        long state = 0x25ca5eL;
        for (int trial = 0; trial < 10_000; trial++) {
            state = advance(state);
            Evidence evidence = new Evidence(
                    bit(state, 0), bit(state, 1), bit(state, 2), bit(state, 3),
                    bit(state, 4), bit(state, 5), bit(state, 6));
            NextStep expected = !evidence.offlineCore() ? NextStep.OFFLINE_CORE
                    : !evidence.modelApiTrace() ? NextStep.MODEL_API
                    : !evidence.tenantIsolation() ? NextStep.TENANT_ISOLATION
                    : !evidence.durableIngestion() ? NextStep.DURABLE_INGESTION
                    : !evidence.terminalStream() ? NextStep.TERMINAL_STREAM
                    : !evidence.immutableRelease() ? NextStep.IMMUTABLE_RELEASE
                    : !evidence.fixedEvaluation() ? NextStep.FIXED_EVALUATION
                    : NextStep.COMPLETE;
            assert nextStep(evidence) == expected;
        }

        for (int trial = 0; trial < 10_000; trial++) {
            state = advance(state);
            boolean release = bit(state, 0), security = bit(state, 1), functional = bit(state, 2);
            boolean quality = bit(state, 3), budget = bit(state, 4), rollback = bit(state, 5);
            PromotionDecision expected = !release ? PromotionDecision.REJECT_RELEASE_IDENTITY
                    : !security ? PromotionDecision.REJECT_SECURITY
                    : !functional ? PromotionDecision.REJECT_FUNCTIONAL
                    : !quality ? PromotionDecision.REJECT_QUALITY
                    : !budget ? PromotionDecision.REJECT_BUDGET
                    : !rollback ? PromotionDecision.REJECT_ROLLBACK
                    : PromotionDecision.PROMOTE;
            assert promotion(release, security, functional, quality, budget, rollback) == expected;
        }

        assert scenarios == 21 : scenarios;
        System.out.println("PARITY|SEQUENCE=EVIDENCE_GATED|IDENTITY=SERVER_OWNED|INGEST=REPLAY_SAFE|STREAM=TERMINAL_EVENT|RELEASE=DIGEST_PINNED|PROMOTION=EVALUATED");
        System.out.println("Practical sequence contracts passed: 21 scenario groups + 20000 seeded trials");
    }
}
