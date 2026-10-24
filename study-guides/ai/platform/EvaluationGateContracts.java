import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free contracts for stage-isolated RAG evaluation and release gates. */
public final class EvaluationGateContracts {
    private static final double EPSILON = 1e-12;

    record RetrievalMetrics(double precisionAtK, double recallAtK,
                            double reciprocalRank, double ndcgAtK) {}

    enum StageOutcome {
        PASS_ANSWER,
        PASS_ABSTAIN,
        RETRIEVAL_MISS,
        ANSWER_ERROR,
        GROUNDING_FAILURE,
        AUTHORIZATION_FAILURE,
        OPERATIONAL_FAILURE
    }

    record Slice(String name, int baselineCorrect, int candidateCorrect,
                 int total, boolean hardGate) {
        Slice {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("slice name required");
            if (total < 1 || baselineCorrect < 0 || candidateCorrect < 0
                    || baselineCorrect > total || candidateCorrect > total) {
                throw new IllegalArgumentException("invalid slice counts");
            }
        }
    }

    record GateResult(boolean promote, List<String> reasons) {
        GateResult {
            reasons = List.copyOf(reasons);
        }
    }

    record PairedEvidence(int candidateWins, int baselineWins, int ties,
                          double twoSidedSignPValue) {}

    static RetrievalMetrics retrievalMetrics(List<String> rankedIds,
                                             Map<String, Integer> relevanceGrades,
                                             int k) {
        if (rankedIds == null || relevanceGrades == null) {
            throw new IllegalArgumentException("ranking and judgments are required");
        }
        if (k < 1) throw new IllegalArgumentException("k must be positive");
        for (var entry : relevanceGrades.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                    || entry.getValue() < 0 || entry.getValue() > 30) {
                throw new IllegalArgumentException("grades must be 0..30 for nonblank IDs");
            }
        }

        var seen = new HashSet<String>();
        int distinctRelevantHits = 0;
        int firstRelevantRank = 0;
        double dcg = 0.0;
        int inspected = Math.min(k, rankedIds.size());
        for (int index = 0; index < inspected; index++) {
            String id = rankedIds.get(index);
            if (id == null || id.isBlank()) throw new IllegalArgumentException("ranked ID required");
            int grade = relevanceGrades.getOrDefault(id, 0);
            boolean firstOccurrence = seen.add(id);
            if (firstOccurrence && grade > 0) {
                distinctRelevantHits++;
                if (firstRelevantRank == 0) firstRelevantRank = index + 1;
                dcg += gain(grade) / log2(index + 2.0);
            }
        }

        long relevantCount = relevanceGrades.values().stream().filter(grade -> grade > 0).count();
        double precision = distinctRelevantHits / (double) k; // missing/duplicate slots are failures
        double recall = relevantCount == 0 ? 1.0 : distinctRelevantHits / (double) relevantCount;
        double reciprocalRank = firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank;

        List<Integer> ideal = relevanceGrades.values().stream()
                .filter(grade -> grade > 0)
                .sorted((left, right) -> Integer.compare(right, left))
                .limit(k)
                .toList();
        double idealDcg = 0.0;
        for (int index = 0; index < ideal.size(); index++) {
            idealDcg += gain(ideal.get(index)) / log2(index + 2.0);
        }
        double ndcg = idealDcg == 0.0 ? 1.0 : dcg / idealDcg;
        return new RetrievalMetrics(precision, recall, reciprocalRank, ndcg);
    }

    static StageOutcome classify(boolean eligibleEvidenceExists,
                                 boolean relevantEvidenceRetrieved,
                                 boolean answerProduced,
                                 boolean answerSupported,
                                 boolean authorizationPassed,
                                 boolean withinOperationalBudget) {
        if (!authorizationPassed) return StageOutcome.AUTHORIZATION_FAILURE;
        if (!withinOperationalBudget) return StageOutcome.OPERATIONAL_FAILURE;
        if (eligibleEvidenceExists && !relevantEvidenceRetrieved) return StageOutcome.RETRIEVAL_MISS;
        if (!eligibleEvidenceExists) {
            return answerProduced ? StageOutcome.GROUNDING_FAILURE : StageOutcome.PASS_ABSTAIN;
        }
        if (!answerProduced) return StageOutcome.ANSWER_ERROR;
        return answerSupported ? StageOutcome.PASS_ANSWER : StageOutcome.GROUNDING_FAILURE;
    }

    static GateResult releaseGate(int baselineCorrect, int candidateCorrect, int total,
                                  int minimumAbsoluteGain, List<Slice> slices) {
        if (total < 1 || baselineCorrect < 0 || candidateCorrect < 0
                || baselineCorrect > total || candidateCorrect > total
                || minimumAbsoluteGain < 0 || slices == null) {
            throw new IllegalArgumentException("invalid release inputs");
        }
        var reasons = new ArrayList<String>();
        if (candidateCorrect - baselineCorrect < minimumAbsoluteGain) {
            reasons.add("overall gain below " + minimumAbsoluteGain);
        }
        for (Slice slice : slices) {
            if (slice.hardGate() && slice.candidateCorrect() < slice.baselineCorrect()) {
                reasons.add("hard slice regressed: " + slice.name());
            }
        }
        return new GateResult(reasons.isEmpty(), reasons);
    }

    static PairedEvidence pairedEvidence(boolean[] baseline, boolean[] candidate) {
        if (baseline == null || candidate == null || baseline.length != candidate.length) {
            throw new IllegalArgumentException("paired arrays must have equal length");
        }
        int wins = 0;
        int losses = 0;
        int ties = 0;
        for (int index = 0; index < baseline.length; index++) {
            if (candidate[index] == baseline[index]) ties++;
            else if (candidate[index]) wins++;
            else losses++;
        }
        int changed = wins + losses;
        if (changed > 1024) throw new IllegalArgumentException("teaching implementation supports at most 1024 changed pairs");
        return new PairedEvidence(wins, losses, ties, twoSidedSignPValue(wins, losses));
    }

    private static double twoSidedSignPValue(int wins, int losses) {
        int n = wins + losses;
        if (n == 0) return 1.0;
        int tail = Math.min(wins, losses);
        double probability = Math.scalb(1.0, -n); // P(X = 0) for X ~ Binomial(n, 0.5)
        double cumulative = probability;
        for (int successes = 0; successes < tail; successes++) {
            probability *= (n - successes) / (double) (successes + 1);
            cumulative += probability;
        }
        return Math.min(1.0, 2.0 * cumulative);
    }

    private static double gain(int grade) {
        return Math.scalb(1.0, grade) - 1.0;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private static void close(double actual, double expected) {
        assert Math.abs(actual - expected) <= EPSILON : actual + " != " + expected;
    }

    private static boolean[] pairedBooleans(int candidateWins, int baselineWins, int ties, boolean candidate) {
        int length = candidateWins + baselineWins + ties;
        boolean[] values = new boolean[length];
        int index = 0;
        if (candidate) {
            for (int count = 0; count < candidateWins; count++) values[index++] = true;
            index += baselineWins;
            for (int count = 0; count < ties; count++) values[index++] = count % 2 == 0;
        } else {
            index += candidateWins;
            for (int count = 0; count < baselineWins; count++) values[index++] = true;
            for (int count = 0; count < ties; count++) values[index++] = count % 2 == 0;
        }
        return values;
    }

    public static void main(String[] args) {
        var canonical = retrievalMetrics(
                List.of("X", "A", "Y", "C", "Z"),
                Map.of("A", 1, "B", 1, "C", 1), 5);
        close(canonical.precisionAtK(), 0.4);
        close(canonical.recallAtK(), 2.0 / 3.0);
        close(canonical.reciprocalRank(), 0.5);
        close(canonical.ndcgAtK(),
                (1.0 / log2(3.0) + 1.0 / log2(5.0)) / (1.0 + 1.0 / log2(3.0) + 1.0 / log2(4.0)));

        var duplicate = retrievalMetrics(List.of("A", "A", "B"), Map.of("A", 1, "B", 1), 3);
        close(duplicate.precisionAtK(), 2.0 / 3.0);
        close(duplicate.recallAtK(), 1.0);
        close(duplicate.reciprocalRank(), 1.0);
        close(duplicate.ndcgAtK(), (1.0 + 1.0 / log2(4.0)) / (1.0 + 1.0 / log2(3.0)));

        var graded = retrievalMetrics(List.of("medium", "high", "low"),
                Map.of("high", 3, "medium", 2, "low", 1), 3);
        assert graded.ndcgAtK() < 1.0 && graded.ndcgAtK() > 0.8;

        assert classify(true, true, true, true, true, true) == StageOutcome.PASS_ANSWER;
        assert classify(false, false, false, false, true, true) == StageOutcome.PASS_ABSTAIN;
        assert classify(true, false, false, false, true, true) == StageOutcome.RETRIEVAL_MISS;
        assert classify(true, true, false, false, true, true) == StageOutcome.ANSWER_ERROR;
        assert classify(true, true, true, false, true, true) == StageOutcome.GROUNDING_FAILURE;
        assert classify(true, true, true, true, false, true) == StageOutcome.AUTHORIZATION_FAILURE;
        assert classify(true, true, true, true, true, false) == StageOutcome.OPERATIONAL_FAILURE;

        var promote = releaseGate(90, 92, 100, 2,
                List.of(new Slice("authorization", 10, 10, 10, true)));
        assert promote.promote();
        var blocked = releaseGate(90, 92, 100, 2,
                List.of(new Slice("authorization", 10, 9, 10, true)));
        assert !blocked.promote() && blocked.reasons().contains("hard slice regressed: authorization");
        assert !releaseGate(90, 91, 100, 2, List.of()).promote();

        var weakPair = pairedEvidence(pairedBooleans(3, 1, 96, false), pairedBooleans(3, 1, 96, true));
        assert weakPair.candidateWins() == 3 && weakPair.baselineWins() == 1 && weakPair.ties() == 96;
        close(weakPair.twoSidedSignPValue(), 0.625);
        var strongerPair = pairedEvidence(pairedBooleans(9, 1, 90, false), pairedBooleans(9, 1, 90, true));
        close(strongerPair.twoSidedSignPValue(), 22.0 / 1024.0);
        close(pairedEvidence(new boolean[]{true, false}, new boolean[]{true, false}).twoSidedSignPValue(), 1.0);

        var random = new Random(0xE7A1L);
        for (int trial = 0; trial < 10_000; trial++) {
            int relevantCount = 1 + random.nextInt(8);
            var judgments = new HashMap<String, Integer>();
            for (int id = 0; id < relevantCount; id++) judgments.put("R" + id, 1 + random.nextInt(3));
            var ranking = new ArrayList<String>();
            int returned = random.nextInt(13);
            for (int rank = 0; rank < returned; rank++) {
                ranking.add(random.nextBoolean() ? "R" + random.nextInt(relevantCount) : "N" + random.nextInt(8));
            }
            int k = 1 + random.nextInt(10);
            RetrievalMetrics metrics = retrievalMetrics(ranking, judgments, k);
            assert metrics.precisionAtK() >= 0.0 && metrics.precisionAtK() <= 1.0;
            assert metrics.recallAtK() >= 0.0 && metrics.recallAtK() <= 1.0;
            assert metrics.reciprocalRank() >= 0.0 && metrics.reciprocalRank() <= 1.0;
            assert metrics.ndcgAtK() >= 0.0 && metrics.ndcgAtK() <= 1.0 + EPSILON;
        }

        System.out.println("PARITY|P@5=0.4|R@5=0.666667|MRR=0.5|PAIR=3-1|p=0.625|AUTH_BLOCKED");
        System.out.println("EvaluationGateContracts: 16 scenario groups + 10,000 randomized metric checks passed");
    }
}
