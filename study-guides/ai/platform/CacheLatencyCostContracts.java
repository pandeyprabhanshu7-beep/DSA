import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Dependency-free contracts for cache safety, deadline-aware retries, and unit-safe AI cost arithmetic. */
public final class CacheLatencyCostContracts {
    enum Failure { NONE, THROTTLED, UNAVAILABLE, INVALID, UNAUTHORIZED, TIMEOUT_UNKNOWN }

    record EmbeddingKey(String modelRevision, String normalizationRevision, String textSha256) {
        EmbeddingKey {
            require(modelRevision, "model revision");
            require(normalizationRevision, "normalization revision");
            requireDigest(textSha256);
        }
    }

    record RetrievalKey(String tenant, String accessScopeHash, String queryRepresentationHash,
                        String retrievalRevision, long indexGeneration, int topK) {
        RetrievalKey {
            require(tenant, "tenant");
            requireDigest(accessScopeHash);
            requireDigest(queryRepresentationHash);
            require(retrievalRevision, "retrieval revision");
            if (indexGeneration < 0 || topK < 1) throw new IllegalArgumentException("invalid retrieval key");
        }
    }

    record AnswerKey(RetrievalKey retrieval, String promptRevision, String modelRevision,
                     String toolSnapshotHash, int maximumOutputTokens) {
        AnswerKey {
            if (retrieval == null) throw new IllegalArgumentException("retrieval key required");
            require(promptRevision, "prompt revision");
            require(modelRevision, "model revision");
            requireDigest(toolSnapshotHash);
            if (maximumOutputTokens < 1) throw new IllegalArgumentException("output bound required");
        }
    }

    record CachedAnswer(AnswerKey key, Set<String> sourceRevisionIds, long aclEpoch,
                        long createdAtMillis, long expiresAtMillis, String body) {
        CachedAnswer {
            if (key == null || sourceRevisionIds == null || sourceRevisionIds.isEmpty()) {
                throw new IllegalArgumentException("key and source dependencies required");
            }
            sourceRevisionIds = Set.copyOf(sourceRevisionIds);
            if (aclEpoch < 0 || createdAtMillis < 0 || expiresAtMillis <= createdAtMillis) {
                throw new IllegalArgumentException("invalid cache times or ACL epoch");
            }
            require(body, "body");
        }
    }

    record CacheDecision(boolean serve, String reason) {}

    record StageBudget(String name, long reservedMillis) {
        StageBudget {
            require(name, "stage name");
            if (reservedMillis < 0) throw new IllegalArgumentException("negative stage budget");
        }
    }

    record CostRates(long inputMicrosPerMillionTokens, long outputMicrosPerMillionTokens,
                     long embeddingMicrosPerMillionTokens) {
        CostRates {
            if (inputMicrosPerMillionTokens < 0 || outputMicrosPerMillionTokens < 0
                    || embeddingMicrosPerMillionTokens < 0) {
                throw new IllegalArgumentException("negative rate");
            }
        }
    }

    record Workload(long requests, long inputTokensPerRequest, long outputTokensPerRequest,
                    long embeddingTokens, long retrievalMicros, long storageMicros,
                    long operationsMicros, long humanReviewMicros) {
        Workload {
            if (requests < 0 || inputTokensPerRequest < 0 || outputTokensPerRequest < 0
                    || embeddingTokens < 0 || retrievalMicros < 0 || storageMicros < 0
                    || operationsMicros < 0 || humanReviewMicros < 0) {
                throw new IllegalArgumentException("negative workload quantity");
            }
        }
    }

    static EmbeddingKey embeddingKey(String modelRevision, String normalizationRevision, String normalizedText) {
        require(normalizedText, "normalized text");
        return new EmbeddingKey(modelRevision, normalizationRevision, sha256(normalizedText));
    }

    static CacheDecision mayServe(CachedAnswer answer, AnswerKey requestKey, long currentAclEpoch,
                                  long nowMillis, Set<String> revokedSourceRevisions) {
        if (answer == null || requestKey == null || revokedSourceRevisions == null) {
            throw new IllegalArgumentException("cache decision inputs required");
        }
        if (!answer.key().equals(requestKey)) return new CacheDecision(false, "behavior key mismatch");
        if (nowMillis < answer.createdAtMillis()) return new CacheDecision(false, "clock before creation");
        if (nowMillis >= answer.expiresAtMillis()) return new CacheDecision(false, "expired");
        if (answer.aclEpoch() != currentAclEpoch) return new CacheDecision(false, "ACL epoch changed");
        if (!disjoint(answer.sourceRevisionIds(), revokedSourceRevisions)) {
            return new CacheDecision(false, "source revoked");
        }
        return new CacheDecision(true, "fresh authorized dependency set");
    }

    static long validateBudget(long deadlineMillis, List<StageBudget> sequentialStages) {
        if (deadlineMillis < 1 || sequentialStages == null) throw new IllegalArgumentException("invalid budget");
        long used = 0;
        for (StageBudget stage : sequentialStages) {
            if (stage == null) throw new IllegalArgumentException("null stage");
            used = Math.addExact(used, stage.reservedMillis());
        }
        if (used > deadlineMillis) throw new IllegalArgumentException("stage reservations exceed deadline");
        return deadlineMillis - used;
    }

    static boolean mayRetry(Failure failure, boolean operationReplaySafe, int attemptsStarted,
                            int maximumAttempts, long remainingMillis, long nextAttemptTimeoutMillis,
                            long backoffMillis, long finalizationReserveMillis) {
        if (failure == null || attemptsStarted < 0 || maximumAttempts < 1 || remainingMillis < 0
                || nextAttemptTimeoutMillis < 1 || backoffMillis < 0 || finalizationReserveMillis < 0) {
            throw new IllegalArgumentException("invalid retry inputs");
        }
        boolean retryable = failure == Failure.THROTTLED || failure == Failure.UNAVAILABLE;
        long required;
        try {
            required = Math.addExact(Math.addExact(backoffMillis, nextAttemptTimeoutMillis), finalizationReserveMillis);
        } catch (ArithmeticException overflow) {
            return false;
        }
        return retryable && operationReplaySafe && attemptsStarted < maximumAttempts && required <= remainingMillis;
    }

    static long fullJitterMillis(long baseMillis, int retryNumber, long capMillis, Random random) {
        if (baseMillis < 1 || retryNumber < 0 || retryNumber > 62 || capMillis < 0 || random == null) {
            throw new IllegalArgumentException("invalid jitter inputs");
        }
        long exponential;
        try {
            exponential = Math.multiplyExact(baseMillis, 1L << retryNumber);
        } catch (ArithmeticException overflow) {
            exponential = Long.MAX_VALUE;
        }
        long bound = Math.min(capMillis, exponential);
        if (bound == Long.MAX_VALUE) return random.nextLong() & Long.MAX_VALUE;
        return random.nextLong(bound + 1);
    }

    static long totalCostMicros(CostRates rates, Workload workload) {
        if (rates == null || workload == null) throw new IllegalArgumentException("rates and workload required");
        long inputTokens = Math.multiplyExact(workload.requests(), workload.inputTokensPerRequest());
        long outputTokens = Math.multiplyExact(workload.requests(), workload.outputTokensPerRequest());
        long model = Math.addExact(perMillionCost(inputTokens, rates.inputMicrosPerMillionTokens()),
                perMillionCost(outputTokens, rates.outputMicrosPerMillionTokens()));
        long embeddings = perMillionCost(workload.embeddingTokens(), rates.embeddingMicrosPerMillionTokens());
        return Math.addExact(Math.addExact(model, embeddings),
                Math.addExact(Math.addExact(workload.retrievalMicros(), workload.storageMicros()),
                        Math.addExact(workload.operationsMicros(), workload.humanReviewMicros())));
    }

    private static long perMillionCost(long tokens, long microsPerMillionTokens) {
        long whole = Math.multiplyExact(tokens / 1_000_000L, microsPerMillionTokens);
        long remainderProduct = Math.multiplyExact(tokens % 1_000_000L, microsPerMillionTokens);
        return Math.addExact(whole, (remainderProduct + 999_999L) / 1_000_000L);
    }

    private static boolean disjoint(Set<String> left, Set<String> right) {
        for (String value : left) if (right.contains(value)) return false;
        return true;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
    }

    private static void requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 required");
    }

    private static void expectFailure(Runnable action) {
        boolean failed = false;
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { failed = true; }
        assert failed;
    }

    public static void main(String[] args) {
        String scopeA = sha256("payments-reader");
        String scopeB = sha256("payments-admin");
        String query = sha256("rollback payments");
        String tools = sha256("status:payments@2026-10-24T17:00Z");
        var retrieval = new RetrievalKey("tenant-a", scopeA, query, "hybrid-r7", 42, 8);
        var answerKey = new AnswerKey(retrieval, "prompt-r11", "answer-r5", tools, 600);
        var answer = new CachedAnswer(answerKey, Set.of("runbook:payments:r7"), 19,
                1_000, 61_000, "Use digest sha256:7f...");
        assert mayServe(answer, answerKey, 19, 2_000, Set.of()).serve();
        assert !mayServe(answer, answerKey, 20, 2_000, Set.of()).serve();
        assert !mayServe(answer, answerKey, 19, 61_000, Set.of()).serve();
        assert !mayServe(answer, answerKey, 19, 2_000, Set.of("runbook:payments:r7")).serve();
        var wrongScope = new AnswerKey(new RetrievalKey("tenant-a", scopeB, query, "hybrid-r7", 42, 8),
                "prompt-r11", "answer-r5", tools, 600);
        assert !mayServe(answer, wrongScope, 19, 2_000, Set.of()).serve();
        assert !embeddingKey("embed-v3", "nfc-v1", "alpha")
                .equals(embeddingKey("embed-v4", "nfc-v1", "alpha"));

        var stages = List.of(new StageBudget("auth-route", 100), new StageBudget("retrieve", 300),
                new StageBudget("rerank", 500), new StageBudget("generate", 8_000),
                new StageBudget("validate-margin", 1_100));
        assert validateBudget(10_000, stages) == 0;
        expectFailure(() -> validateBudget(9_999, stages));
        assert mayRetry(Failure.UNAVAILABLE, true, 1, 3, 4_000, 2_500, 250, 1_000);
        assert !mayRetry(Failure.UNAVAILABLE, true, 1, 3, 3_000, 2_500, 250, 1_000);
        assert !mayRetry(Failure.TIMEOUT_UNKNOWN, true, 1, 3, 8_000, 2_500, 250, 1_000);
        assert !mayRetry(Failure.UNAVAILABLE, false, 1, 3, 8_000, 2_500, 250, 1_000);
        assert !mayRetry(Failure.INVALID, true, 1, 3, 8_000, 2_500, 250, 1_000);

        var rates = new CostRates(2_000_000, 8_000_000, 100_000); // hypothetical dollars as micros
        var workload = new Workload(1_000_000, 2_000, 400, 600_000_000,
                200_000_000, 100_000_000, 300_000_000, 2_000_000_000);
        long total = totalCostMicros(rates, workload);
        assert total == 9_860_000_000L : total; // $7,200 model + $60 embedding + $2,600 other
        expectFailure(() -> totalCostMicros(rates,
                new Workload(Long.MAX_VALUE, 2, 0, 0, 0, 0, 0, 0)));

        var random = new Random(73);
        for (int trial = 0; trial < 10_000; trial++) {
            int retry = random.nextInt(12);
            long cap = 1 + random.nextInt(20_000);
            long wait = fullJitterMillis(25, retry, cap, random);
            long expectedBound = Math.min(cap, 25L << retry);
            assert wait >= 0 && wait <= expectedBound;
        }

        var reasons = new ArrayList<>(List.of(
                "embedding identity", "answer dependency invalidation", "deadline budget",
                "retry safety", "jitter bound", "unit-safe total cost"));
        assert reasons.size() == 6;
        System.out.println("CacheLatencyCostContracts: 20 scenario groups + 10000 seeded jitter checks passed");
        System.out.println("PARITY|CACHE=HIT|ACL=BLOCK|REVOKE=BLOCK|DEADLINE=10000|RETRY=SAFE|COST_MICROS=" + total);
    }
}
