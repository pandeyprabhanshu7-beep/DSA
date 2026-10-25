import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Dependency-free contracts for choosing and migrating an AI platform stack.
 *
 * Run with: java -ea StackSelectionContracts.java
 */
public final class StackSelectionContracts {
    enum Capability { REGION, TENANT_FILTER, DELETE_PROOF, METRICS, EXPORT, PRIVATE_NETWORK }
    enum Ownership { PROVIDER, TEAM }
    enum MigrationState { PREPARE, DUAL_WRITE, SHADOW, CANARY, CUTOVER, ROLLBACK, COMPLETE }

    record Candidate(String name, Set<Capability> capabilities,
                     Ownership infrastructure, Ownership sourceQuality,
                     Ownership authorization, Ownership evaluation) {
        Candidate {
            Objects.requireNonNull(name);
            capabilities = Set.copyOf(capabilities);
            Objects.requireNonNull(infrastructure);
            Objects.requireNonNull(sourceQuality);
            Objects.requireNonNull(authorization);
            Objects.requireNonNull(evaluation);
        }
    }

    record Workload(String corpusRevision, int queryCount, int concurrentRequests,
                    Set<Capability> hardRequirements) {
        Workload {
            Objects.requireNonNull(corpusRevision);
            if (queryCount <= 0 || concurrentRequests <= 0) {
                throw new IllegalArgumentException("positive workload required");
            }
            hardRequirements = Set.copyOf(hardRequirements);
        }
    }

    record Evaluation(double criticalRecall, double acceptedAnswerRate,
                      long p95Millis, long monthlyCostCents, long acceptedAnswers,
                      int operatorHours, boolean revokeAndDeletePassed) {
        Evaluation {
            if (!Double.isFinite(criticalRecall) || criticalRecall < 0 || criticalRecall > 1
                    || !Double.isFinite(acceptedAnswerRate) || acceptedAnswerRate < 0 || acceptedAnswerRate > 1
                    || p95Millis < 0 || monthlyCostCents < 0 || acceptedAnswers <= 0 || operatorHours < 0) {
                throw new IllegalArgumentException("invalid evaluation");
            }
        }

        BigDecimal centsPerAcceptedAnswer() {
            return BigDecimal.valueOf(monthlyCostCents)
                    .divide(BigDecimal.valueOf(acceptedAnswers), 6, RoundingMode.HALF_UP);
        }
    }

    record Gate(double minimumCriticalRecall, double minimumAcceptedAnswerRate,
                long maximumP95Millis, int maximumOperatorHours) {
        Gate {
            if (minimumCriticalRecall < 0 || minimumCriticalRecall > 1
                    || minimumAcceptedAnswerRate < 0 || minimumAcceptedAnswerRate > 1
                    || maximumP95Millis < 0 || maximumOperatorHours < 0) {
                throw new IllegalArgumentException("invalid gate");
            }
        }
    }

    record Decision(boolean qualified, List<String> reasons) {
        Decision { reasons = List.copyOf(reasons); }
    }

    static Decision qualify(Candidate candidate, Workload workload, Evaluation evaluation, Gate gate) {
        var reasons = new java.util.ArrayList<String>();
        var missing = EnumSet.noneOf(Capability.class);
        missing.addAll(workload.hardRequirements());
        missing.removeAll(candidate.capabilities());
        if (!missing.isEmpty()) reasons.add("missing=" + missing);
        if (!evaluation.revokeAndDeletePassed()) reasons.add("revoke-delete");
        if (evaluation.criticalRecall() < gate.minimumCriticalRecall()) reasons.add("critical-recall");
        if (evaluation.acceptedAnswerRate() < gate.minimumAcceptedAnswerRate()) reasons.add("accepted-answer-rate");
        if (evaluation.p95Millis() > gate.maximumP95Millis()) reasons.add("p95");
        if (evaluation.operatorHours() > gate.maximumOperatorHours()) reasons.add("operator-hours");
        return new Decision(reasons.isEmpty(), reasons);
    }

    static long requiredConcurrency(long requestsPerSecond, long serviceMillis, int safetyPercent) {
        if (requestsPerSecond < 0 || serviceMillis < 0 || safetyPercent < 100) {
            throw new IllegalArgumentException("invalid capacity input");
        }
        long inFlightNumerator = Math.multiplyExact(requestsPerSecond, serviceMillis);
        long scaled = Math.multiplyExact(inFlightNumerator, safetyPercent);
        return Math.floorDiv(Math.addExact(scaled, 99_999), 100_000);
    }

    static Set<MigrationState> allowedAfter(MigrationState state) {
        return switch (state) {
            case PREPARE -> Set.of(MigrationState.DUAL_WRITE);
            case DUAL_WRITE -> Set.of(MigrationState.SHADOW, MigrationState.ROLLBACK);
            case SHADOW -> Set.of(MigrationState.CANARY, MigrationState.ROLLBACK);
            case CANARY -> Set.of(MigrationState.CUTOVER, MigrationState.ROLLBACK);
            case CUTOVER -> Set.of(MigrationState.COMPLETE, MigrationState.ROLLBACK);
            case ROLLBACK, COMPLETE -> Set.of();
        };
    }

    static boolean canAdvance(MigrationState from, MigrationState to,
                              boolean corpusMatches, boolean aclMatches,
                              boolean deleteProofPassed, boolean sliceGatesPassed) {
        return allowedAfter(from).contains(to)
                && corpusMatches && aclMatches && deleteProofPassed && sliceGatesPassed;
    }

    static Candidate managed() {
        return new Candidate("managed", EnumSet.of(Capability.REGION, Capability.TENANT_FILTER,
                Capability.DELETE_PROOF, Capability.METRICS, Capability.EXPORT),
                Ownership.PROVIDER, Ownership.TEAM, Ownership.TEAM, Ownership.TEAM);
    }

    static Candidate independent() {
        return new Candidate("independent", EnumSet.allOf(Capability.class),
                Ownership.TEAM, Ownership.TEAM, Ownership.TEAM, Ownership.TEAM);
    }

    static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        int scenarios = 0;
        var requirements = EnumSet.of(Capability.REGION, Capability.TENANT_FILTER,
                Capability.DELETE_PROOF, Capability.METRICS, Capability.EXPORT);
        var workload = new Workload("support-corpus@42", 1_000, 120, requirements);
        var gate = new Gate(0.90, 0.78, 4_000, 120);
        var managedEval = new Evaluation(0.94, 0.82, 2_700, 1_200_000, 120_000, 38, true);
        var independentEval = new Evaluation(0.96, 0.80, 2_200, 800_000, 70_000, 92, true);

        expect(qualify(managed(), workload, managedEval, gate).qualified(), "managed qualifies"); scenarios++;
        expect(qualify(independent(), workload, independentEval, gate).qualified(), "independent qualifies"); scenarios++;
        expect(managed().infrastructure() == Ownership.PROVIDER, "managed infrastructure ownership"); scenarios++;
        expect(managed().authorization() == Ownership.TEAM, "managed authorization ownership"); scenarios++;
        expect(independent().infrastructure() == Ownership.TEAM, "independent infrastructure ownership"); scenarios++;
        expect(independent().sourceQuality() == Ownership.TEAM, "source quality always team-owned"); scenarios++;

        var missingDelete = new Candidate("weak", EnumSet.of(Capability.REGION, Capability.TENANT_FILTER,
                Capability.METRICS, Capability.EXPORT), Ownership.PROVIDER, Ownership.TEAM,
                Ownership.TEAM, Ownership.TEAM);
        Decision missingDecision = qualify(missingDelete, workload, managedEval, gate);
        expect(!missingDecision.qualified(), "hard capability rejects"); scenarios++;
        expect(missingDecision.reasons().toString().contains("DELETE_PROOF"), "missing capability named"); scenarios++;
        expect(!qualify(managed(), workload, new Evaluation(0.89, 0.82, 2_700, 1, 1, 38, true), gate).qualified(), "slice recall gate"); scenarios++;
        expect(!qualify(managed(), workload, new Evaluation(0.94, 0.77, 2_700, 1, 1, 38, true), gate).qualified(), "answer gate"); scenarios++;
        expect(!qualify(managed(), workload, new Evaluation(0.94, 0.82, 4_001, 1, 1, 38, true), gate).qualified(), "latency gate"); scenarios++;
        expect(!qualify(managed(), workload, new Evaluation(0.94, 0.82, 2_700, 1, 1, 121, true), gate).qualified(), "ops gate"); scenarios++;
        expect(!qualify(managed(), workload, new Evaluation(0.94, 0.82, 2_700, 1, 1, 38, false), gate).qualified(), "delete proof gate"); scenarios++;

        expect(managedEval.centsPerAcceptedAnswer().equals(new BigDecimal("10.000000")), "managed unit cost"); scenarios++;
        expect(independentEval.centsPerAcceptedAnswer().equals(new BigDecimal("11.428571")), "independent unit cost"); scenarios++;
        expect(managedEval.centsPerAcceptedAnswer().compareTo(independentEval.centsPerAcceptedAnswer()) < 0,
                "lower invoice is not lower accepted-answer cost"); scenarios++;
        expect(requiredConcurrency(30, 2_000, 200) == 120, "capacity trace"); scenarios++;
        expect(requiredConcurrency(1, 1, 100) == 1, "capacity rounds up"); scenarios++;
        try { requiredConcurrency(Long.MAX_VALUE, 2, 100); throw new AssertionError("overflow accepted"); }
        catch (ArithmeticException expected) { scenarios++; }

        expect(allowedAfter(MigrationState.PREPARE).equals(Set.of(MigrationState.DUAL_WRITE)), "prepare edge"); scenarios++;
        expect(allowedAfter(MigrationState.SHADOW).containsAll(Set.of(MigrationState.CANARY, MigrationState.ROLLBACK)), "shadow edges"); scenarios++;
        expect(allowedAfter(MigrationState.COMPLETE).isEmpty(), "terminal edge"); scenarios++;
        expect(canAdvance(MigrationState.SHADOW, MigrationState.CANARY, true, true, true, true), "validated advance"); scenarios++;
        expect(!canAdvance(MigrationState.SHADOW, MigrationState.CANARY, true, false, true, true), "acl mismatch blocks"); scenarios++;
        expect(!canAdvance(MigrationState.SHADOW, MigrationState.CANARY, true, true, false, true), "delete mismatch blocks"); scenarios++;
        expect(!canAdvance(MigrationState.SHADOW, MigrationState.CANARY, true, true, true, false), "slice mismatch blocks"); scenarios++;
        expect(!canAdvance(MigrationState.SHADOW, MigrationState.COMPLETE, true, true, true, true), "skipped edge blocks"); scenarios++;

        try { new Workload("x", 0, 1, Set.of()); throw new AssertionError("empty workload accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { new Evaluation(Double.NaN, .8, 1, 1, 1, 1, true); throw new AssertionError("NaN accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }

        Random random = new Random(0x5A17C0DEL);
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            long rps = random.nextInt(20_000);
            long millis = random.nextInt(20_000);
            int safety = 100 + random.nextInt(301);
            long actual = requiredConcurrency(rps, millis, safety);
            long numerator = Math.multiplyExact(Math.multiplyExact(rps, millis), safety);
            long oracle = numerator == 0 ? 0 : 1 + (numerator - 1) / 100_000;
            expect(actual == oracle, "capacity oracle " + i);
        }

        expect(scenarios == 29, "scenario count");
        System.out.println("SCENARIOS=29|TRIALS=10000|DECISION=QUALIFY_THEN_SCORE|MANAGED=TEAM_OWNS_POLICY|INDEPENDENT=TEAM_OWNS_OPERATIONS|MIGRATION=SHADOW_CANARY");
    }
}
