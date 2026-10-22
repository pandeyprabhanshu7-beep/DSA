import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Java 17 source-file program for enforceable model-mechanics contracts. */
public final class ModelMechanics {
    record ContextBudget(int contextWindow,
                         int fixedInputTokens,
                         int maxOutputTokens,
                         int safetyReserveTokens) {
        ContextBudget {
            if (contextWindow <= 0 || fixedInputTokens < 0 || maxOutputTokens < 0
                    || safetyReserveTokens < 0) {
                throw new IllegalArgumentException("token counts must be non-negative");
            }
            if ((long) fixedInputTokens + maxOutputTokens + safetyReserveTokens > contextWindow) {
                throw new IllegalArgumentException("fixed input, output, and reserve exceed context");
            }
        }

        int evidenceCapacity() {
            return contextWindow - fixedInputTokens - maxOutputTokens - safetyReserveTokens;
        }
    }

    record EvidenceChunk(String id, int encodedTokens, double relevance) {
        EvidenceChunk {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("chunk id required");
            if (encodedTokens <= 0) throw new IllegalArgumentException("encoded token count must be positive");
            if (!Double.isFinite(relevance)) throw new IllegalArgumentException("relevance must be finite");
        }
    }

    record Admission(List<EvidenceChunk> admitted, int usedTokens, int capacityTokens) {
        Admission {
            admitted = List.copyOf(admitted);
        }
    }

    record EmbeddingIdentity(String modelRevision,
                             int dimensions,
                             String normalization,
                             String preprocessing,
                             long indexGeneration) {
        EmbeddingIdentity {
            if (modelRevision == null || modelRevision.isBlank()) {
                throw new IllegalArgumentException("embedding model revision required");
            }
            if (dimensions <= 0) throw new IllegalArgumentException("dimensions must be positive");
            Objects.requireNonNull(normalization, "normalization");
            Objects.requireNonNull(preprocessing, "preprocessing");
            if (indexGeneration < 0) throw new IllegalArgumentException("generation must be non-negative");
        }
    }

    record GenerationConfig(String modelRevision,
                            boolean sampling,
                            double temperature,
                            Long seed,
                            String backendFingerprint) {
        GenerationConfig {
            if (modelRevision == null || modelRevision.isBlank()) {
                throw new IllegalArgumentException("generation model revision required");
            }
            if (!Double.isFinite(temperature) || temperature < 0.0) {
                throw new IllegalArgumentException("temperature must be finite and non-negative");
            }
            if (!sampling && temperature != 0.0) {
                throw new IllegalArgumentException("record temperature as zero when sampling is disabled");
            }
            if (backendFingerprint == null || backendFingerprint.isBlank()) {
                throw new IllegalArgumentException("backend fingerprint required");
            }
        }
    }

    record OutputEvaluation(boolean schemaValid,
                            boolean claimsSupported,
                            boolean citationsAllowed,
                            boolean taskPassed) {
        boolean accepted() {
            return schemaValid && claimsSupported && citationsAllowed && taskPassed;
        }
    }

    static Admission admitEvidence(ContextBudget budget, List<EvidenceChunk> candidates) {
        var ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble(EvidenceChunk::relevance).reversed()
                .thenComparing(EvidenceChunk::id));
        var admitted = new ArrayList<EvidenceChunk>();
        int used = 0;
        for (var chunk : ranked) {
            if ((long) used + chunk.encodedTokens() <= budget.evidenceCapacity()) {
                admitted.add(chunk);
                used += chunk.encodedTokens();
            }
        }
        return new Admission(admitted, used, budget.evidenceCapacity());
    }

    static void requireCompatible(EmbeddingIdentity indexed, EmbeddingIdentity query) {
        if (!indexed.equals(query)) {
            throw new IllegalArgumentException("query and index embedding identities differ");
        }
    }

    static double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) {
            throw new IllegalArgumentException("vectors must have the same positive dimension");
        }
        double dot = 0.0;
        double leftSquared = 0.0;
        double rightSquared = 0.0;
        for (int i = 0; i < left.length; i++) {
            if (!Double.isFinite(left[i]) || !Double.isFinite(right[i])) {
                throw new IllegalArgumentException("vectors must contain finite values");
            }
            dot += left[i] * right[i];
            leftSquared += left[i] * left[i];
            rightSquared += right[i] * right[i];
        }
        if (leftSquared == 0.0 || rightSquared == 0.0) {
            throw new IllegalArgumentException("cosine is undefined for a zero vector");
        }
        return dot / (Math.sqrt(leftSquared) * Math.sqrt(rightSquared));
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void expectClose(double actual, double expected, String message) {
        expect(Math.abs(actual - expected) < 1e-12, message + ": " + actual);
    }

    public static void main(String[] args) {
        int groups = 0;

        var budget = new ContextBudget(16_000, 3_500, 3_000, 1_500);
        expect(budget.evidenceCapacity() == 8_000, "evidence capacity must reserve output and safety margin");
        groups++;

        var admission = admitEvidence(budget, List.of(
                new EvidenceChunk("runbook-r7", 4_800, 0.92),
                new EvidenceChunk("incident-42", 3_500, 0.88),
                new EvidenceChunk("old-runbook", 2_600, 0.41)));
        expect(admission.admitted().stream().map(EvidenceChunk::id).toList()
                        .equals(List.of("runbook-r7", "old-runbook")),
                "ranked admission must skip an oversized chunk without exceeding the budget");
        expect(admission.usedTokens() == 7_400 && admission.usedTokens() <= admission.capacityTokens(),
                "admission must remain within the encoded-token budget");
        groups += 2;

        try {
            new ContextBudget(4_000, 2_000, 1_500, 600);
            throw new AssertionError("overcommitted fixed budget was accepted");
        } catch (IllegalArgumentException expected) { groups++; }

        expectClose(cosine(new double[]{1.0, 0.0}, new double[]{0.8, 0.6}), 0.8,
                "cosine should measure direction, not vector length");
        expectClose(cosine(new double[]{1.0, 0.0}, new double[]{0.0, 1.0}), 0.0,
                "orthogonal vectors should have zero cosine");
        groups += 2;

        try {
            cosine(new double[]{0.0, 0.0}, new double[]{1.0, 0.0});
            throw new AssertionError("zero vector was accepted");
        } catch (IllegalArgumentException expected) { groups++; }

        var generation7 = new EmbeddingIdentity("embed-v3@2026-09-01", 1_536,
                "L2", "utf8+nfc", 7);
        requireCompatible(generation7, generation7);
        groups++;
        try {
            requireCompatible(generation7, new EmbeddingIdentity("embed-v3@2026-09-01", 1_536,
                    "L2", "utf8+nfc", 8));
            throw new AssertionError("cross-generation comparison was accepted");
        } catch (IllegalArgumentException expected) { groups++; }

        var greedy = new GenerationConfig("answer-v5", false, 0.0, 73L, "backend-a9");
        expect(!greedy.sampling() && greedy.seed() == 73L, "release metadata must remain observable");
        groups++;
        try {
            new GenerationConfig("answer-v5", false, 0.7, 73L, "backend-a9");
            throw new AssertionError("inactive temperature was recorded as effective");
        } catch (IllegalArgumentException expected) { groups++; }

        var validParaphrase = new OutputEvaluation(true, true, true, true);
        var fluentUnsupportedAnswer = new OutputEvaluation(true, false, true, true);
        expect(validParaphrase.accepted(), "a supported semantic equivalent should pass");
        expect(!fluentUnsupportedAnswer.accepted(), "fluent unsupported output must fail");
        groups += 2;

        System.out.println("ModelMechanics: " + groups + " scenario groups passed");
    }
}
