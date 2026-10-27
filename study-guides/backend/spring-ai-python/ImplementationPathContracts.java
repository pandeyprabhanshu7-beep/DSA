import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency-free contracts for a four-week, evidence-producing implementation path. */
public final class ImplementationPathContracts {
    enum Week { OFFLINE_CORE, MODEL_RETRIEVAL, MCP_BOUNDARY, DISPOSABLE_DEPLOYMENT }
    enum Gate { READY, BLOCK_MISSING, BLOCK_IDENTITY, BLOCK_SECURITY,
        BLOCK_WEEK1, BLOCK_WEEK2, BLOCK_WEEK3, BLOCK_WEEK4 }
    enum RetrievalChoice { LEXICAL, VECTOR, HYBRID, NO_SAFE_WINNER }
    enum McpOutcome { READY, BLOCK_DISCOVERY, BLOCK_SCHEMA, BLOCK_AUTH, BLOCK_TIMEOUT }
    enum DeployOutcome { READY, BLOCK_RELEASE, BLOCK_STARTUP, BLOCK_READINESS,
        BLOCK_RESILIENCE, BLOCK_EVALUATION }

    record WeekEvidence(Week week, String releaseId, String fixtureRevision,
                        boolean functionalPass, boolean securityPass,
                        int scoreBasisPoints, int minimumBasisPoints) {
        WeekEvidence {
            if (week == null || releaseId == null || releaseId.isBlank()
                    || fixtureRevision == null || fixtureRevision.isBlank()
                    || scoreBasisPoints < 0 || scoreBasisPoints > 10_000
                    || minimumBasisPoints < 0 || minimumBasisPoints > 10_000) {
                throw new IllegalArgumentException("invalid week evidence");
            }
        }

        boolean passes() {
            return functionalPass && scoreBasisPoints >= minimumBasisPoints;
        }
    }

    record RetrievalEvidence(String releaseId, String modelRevision, String corpusRevision,
                             int lexicalSupported, int vectorSupported, int hybridSupported,
                             int lexicalUnsafe, int vectorUnsafe, int hybridUnsafe) {
        RetrievalEvidence {
            if (releaseId == null || releaseId.isBlank()
                    || modelRevision == null || modelRevision.isBlank()
                    || corpusRevision == null || corpusRevision.isBlank()
                    || lexicalSupported < 0 || vectorSupported < 0 || hybridSupported < 0
                    || lexicalUnsafe < 0 || vectorUnsafe < 0 || hybridUnsafe < 0) {
                throw new IllegalArgumentException("invalid retrieval evidence");
            }
        }
    }

    record DecisionRecord(String context, String decision, String rejectedAlternative,
                          String reconsiderMetric) {
        DecisionRecord {
            if (blank(context) || blank(decision) || blank(rejectedAlternative)
                    || blank(reconsiderMetric)) {
                throw new IllegalArgumentException("incomplete decision record");
            }
        }
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static RetrievalChoice retrievalChoice(RetrievalEvidence evidence) {
        if (evidence == null) throw new IllegalArgumentException("missing retrieval evidence");
        int best = -1;
        RetrievalChoice choice = RetrievalChoice.NO_SAFE_WINNER;
        int[] supported = { evidence.lexicalSupported(), evidence.vectorSupported(),
                evidence.hybridSupported() };
        int[] unsafe = { evidence.lexicalUnsafe(), evidence.vectorUnsafe(), evidence.hybridUnsafe() };
        RetrievalChoice[] choices = { RetrievalChoice.LEXICAL, RetrievalChoice.VECTOR,
                RetrievalChoice.HYBRID };
        for (int index = 0; index < choices.length; index++) {
            if (unsafe[index] == 0 && supported[index] > best) {
                best = supported[index];
                choice = choices[index];
            }
        }
        return choice;
    }

    static McpOutcome mcpOutcome(boolean discovered, boolean schemaValid,
                                 boolean authorizedNegativePassed, boolean timeoutBounded) {
        if (!discovered) return McpOutcome.BLOCK_DISCOVERY;
        if (!schemaValid) return McpOutcome.BLOCK_SCHEMA;
        if (!authorizedNegativePassed) return McpOutcome.BLOCK_AUTH;
        if (!timeoutBounded) return McpOutcome.BLOCK_TIMEOUT;
        return McpOutcome.READY;
    }

    static DeployOutcome deployOutcome(boolean sameRelease, boolean startupPassed,
                                       boolean readinessPassed, boolean outageAndOverloadPassed,
                                       boolean evaluationLinked) {
        if (!sameRelease) return DeployOutcome.BLOCK_RELEASE;
        if (!startupPassed) return DeployOutcome.BLOCK_STARTUP;
        if (!readinessPassed) return DeployOutcome.BLOCK_READINESS;
        if (!outageAndOverloadPassed) return DeployOutcome.BLOCK_RESILIENCE;
        if (!evaluationLinked) return DeployOutcome.BLOCK_EVALUATION;
        return DeployOutcome.READY;
    }

    static Gate releaseGate(String expectedRelease, List<WeekEvidence> evidence) {
        if (blank(expectedRelease) || evidence == null) {
            throw new IllegalArgumentException("invalid release gate");
        }
        Map<Week, WeekEvidence> byWeek = new EnumMap<>(Week.class);
        for (WeekEvidence item : evidence) {
            if (byWeek.put(item.week(), item) != null) {
                throw new IllegalArgumentException("duplicate week evidence");
            }
        }
        if (byWeek.size() != Week.values().length) return Gate.BLOCK_MISSING;
        if (evidence.stream().anyMatch(item -> !item.releaseId().equals(expectedRelease))) {
            return Gate.BLOCK_IDENTITY;
        }
        if (evidence.stream().anyMatch(item -> !item.securityPass())) return Gate.BLOCK_SECURITY;
        for (Week week : Week.values()) {
            if (!byWeek.get(week).passes()) {
                return switch (week) {
                    case OFFLINE_CORE -> Gate.BLOCK_WEEK1;
                    case MODEL_RETRIEVAL -> Gate.BLOCK_WEEK2;
                    case MCP_BOUNDARY -> Gate.BLOCK_WEEK3;
                    case DISPOSABLE_DEPLOYMENT -> Gate.BLOCK_WEEK4;
                };
            }
        }
        return Gate.READY;
    }

    static List<WeekEvidence> passingEvidence(String release) {
        List<WeekEvidence> result = new ArrayList<>();
        for (Week week : Week.values()) {
            result.add(new WeekEvidence(week, release, "atlas-path-v16", true, true,
                    9_000, 8_500));
        }
        return result;
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }

    public static void main(String[] args) {
        int scenarios = 0;

        new DecisionRecord("lexical misses paraphrases", "measure hybrid retrieval",
                "change the model and retriever together", "recall@5 < 0.90"); scenarios++;
        String[] incomplete = { "", null, "  ", "" };
        for (int index = 0; index < incomplete.length; index++) {
            try {
                new DecisionRecord(index == 0 ? incomplete[index] : "context",
                        index == 1 ? incomplete[index] : "decision",
                        index == 2 ? incomplete[index] : "alternative",
                        index == 3 ? incomplete[index] : "metric");
                throw new AssertionError();
            } catch (IllegalArgumentException expected) { scenarios++; }
        }

        check(retrievalChoice(new RetrievalEvidence("r16", "m1", "c1",
                60, 78, 91, 0, 0, 0)) == RetrievalChoice.HYBRID); scenarios++;
        check(retrievalChoice(new RetrievalEvidence("r16", "m1", "c1",
                88, 80, 85, 0, 0, 0)) == RetrievalChoice.LEXICAL); scenarios++;
        check(retrievalChoice(new RetrievalEvidence("r16", "m1", "c1",
                60, 92, 91, 0, 1, 0)) == RetrievalChoice.HYBRID); scenarios++;
        check(retrievalChoice(new RetrievalEvidence("r16", "m1", "c1",
                60, 92, 91, 1, 1, 1)) == RetrievalChoice.NO_SAFE_WINNER); scenarios++;

        check(mcpOutcome(true, true, true, true) == McpOutcome.READY); scenarios++;
        check(mcpOutcome(false, true, true, true) == McpOutcome.BLOCK_DISCOVERY); scenarios++;
        check(mcpOutcome(true, false, true, true) == McpOutcome.BLOCK_SCHEMA); scenarios++;
        check(mcpOutcome(true, true, false, true) == McpOutcome.BLOCK_AUTH); scenarios++;
        check(mcpOutcome(true, true, true, false) == McpOutcome.BLOCK_TIMEOUT); scenarios++;

        check(deployOutcome(true, true, true, true, true) == DeployOutcome.READY); scenarios++;
        check(deployOutcome(false, true, true, true, true) == DeployOutcome.BLOCK_RELEASE); scenarios++;
        check(deployOutcome(true, false, true, true, true) == DeployOutcome.BLOCK_STARTUP); scenarios++;
        check(deployOutcome(true, true, false, true, true) == DeployOutcome.BLOCK_READINESS); scenarios++;
        check(deployOutcome(true, true, true, false, true) == DeployOutcome.BLOCK_RESILIENCE); scenarios++;
        check(deployOutcome(true, true, true, true, false) == DeployOutcome.BLOCK_EVALUATION); scenarios++;

        List<WeekEvidence> passing = passingEvidence("r16");
        check(releaseGate("r16", passing) == Gate.READY); scenarios++;
        check(releaseGate("r16", passing.subList(0, 3)) == Gate.BLOCK_MISSING); scenarios++;
        List<WeekEvidence> wrongRelease = new ArrayList<>(passing);
        WeekEvidence old = wrongRelease.get(1);
        wrongRelease.set(1, new WeekEvidence(old.week(), "r15", old.fixtureRevision(),
                old.functionalPass(), old.securityPass(), old.scoreBasisPoints(), old.minimumBasisPoints()));
        check(releaseGate("r16", wrongRelease) == Gate.BLOCK_IDENTITY); scenarios++;
        List<WeekEvidence> security = new ArrayList<>(passing);
        old = security.get(2);
        security.set(2, new WeekEvidence(old.week(), old.releaseId(), old.fixtureRevision(),
                true, false, old.scoreBasisPoints(), old.minimumBasisPoints()));
        check(releaseGate("r16", security) == Gate.BLOCK_SECURITY); scenarios++;
        for (int index = 0; index < Week.values().length; index++) {
            List<WeekEvidence> failed = new ArrayList<>(passing);
            old = failed.get(index);
            failed.set(index, new WeekEvidence(old.week(), old.releaseId(), old.fixtureRevision(),
                    false, true, old.scoreBasisPoints(), old.minimumBasisPoints()));
            check(releaseGate("r16", failed) == Gate.values()[Gate.BLOCK_WEEK1.ordinal() + index]);
            scenarios++;
        }
        try {
            List<WeekEvidence> duplicate = new ArrayList<>(passing);
            duplicate.add(passing.get(0));
            releaseGate("r16", duplicate);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) { scenarios++; }
        try {
            releaseGate(" ", passing);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) { scenarios++; }
        try {
            new WeekEvidence(Week.OFFLINE_CORE, "r16", "fixture", true, true,
                    10_001, 8_500);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) { scenarios++; }

        long state = 0x16A71A5L;
        int ready = 0;
        Set<Gate> observed = new HashSet<>();
        for (int trial = 0; trial < 10_000; trial++) {
            List<WeekEvidence> evidence = passingEvidence("r16");
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            boolean wrongIdentity = state % 97 == 0;
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            boolean securityFailure = state % 89 == 0;
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            int failedWeek = (int) (state % 9) - 5;

            if (wrongIdentity) {
                old = evidence.get(1);
                evidence.set(1, new WeekEvidence(old.week(), "other", old.fixtureRevision(),
                        old.functionalPass(), old.securityPass(), old.scoreBasisPoints(), old.minimumBasisPoints()));
            }
            if (securityFailure) {
                old = evidence.get(2);
                evidence.set(2, new WeekEvidence(old.week(), old.releaseId(), old.fixtureRevision(),
                        old.functionalPass(), false, old.scoreBasisPoints(), old.minimumBasisPoints()));
            }
            if (failedWeek >= 0) {
                old = evidence.get(failedWeek);
                evidence.set(failedWeek, new WeekEvidence(old.week(), old.releaseId(), old.fixtureRevision(),
                        false, old.securityPass(), old.scoreBasisPoints(), old.minimumBasisPoints()));
            }
            Gate actual = releaseGate("r16", evidence);
            Gate oracle = wrongIdentity ? Gate.BLOCK_IDENTITY
                    : securityFailure ? Gate.BLOCK_SECURITY
                    : failedWeek == 0 ? Gate.BLOCK_WEEK1
                    : failedWeek == 1 ? Gate.BLOCK_WEEK2
                    : failedWeek == 2 ? Gate.BLOCK_WEEK3
                    : failedWeek == 3 ? Gate.BLOCK_WEEK4 : Gate.READY;
            check(actual == oracle);
            observed.add(actual);
            if (actual == Gate.READY) ready++;
        }

        check(scenarios == 31);
        check(observed.containsAll(Set.of(Gate.READY, Gate.BLOCK_IDENTITY, Gate.BLOCK_SECURITY,
                Gate.BLOCK_WEEK1, Gate.BLOCK_WEEK2, Gate.BLOCK_WEEK3, Gate.BLOCK_WEEK4)));
        System.out.println("PARITY|W1=OFFLINE_BASELINE|W2=MODEL_RETRIEVAL|W3=MCP_BOUNDARY"
                + "|W4=DISPOSABLE_DEPLOYMENT|ADR=MEASURED|PROMOTION=EVIDENCE_CHAIN"
                + "|READY=" + ready);
        System.out.println("Java implementation-path contracts passed: " + scenarios
                + " scenario groups + 10000 seeded evidence-chain trials");
    }
}
