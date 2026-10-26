import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Dependency-free contracts for the Chapter 13 interview cases.
 *
 * Run with: java -ea InterviewCaseContracts.java
 */
public final class InterviewCaseContracts {
    enum Stage { EXTRACTION, CHUNKING, ELIGIBILITY, RETRIEVAL, RERANK, CONTEXT, GENERATION, NONE }
    enum OperationKind { READ, MUTATION }
    enum OperationStatus { ABSENT, PENDING, COMMITTED, FAILED, UNKNOWN }
    enum RetryDecision { RETRY, RETURN_COMMITTED, RETURN_FAILED, RECONCILE, STOP }
    enum Slice { ERROR_CODE, PARAPHRASE, MULTILINGUAL, MISSING_ANSWER }

    record EvidencePath(boolean extraction, boolean chunking, boolean eligibility,
                        boolean retrieval, boolean rerank, boolean context, boolean generation) {}

    record EmbeddingSpec(String model, int dimensions, String normalization, String metric) {
        EmbeddingSpec {
            Objects.requireNonNull(model);
            Objects.requireNonNull(normalization);
            Objects.requireNonNull(metric);
            if (model.isBlank() || dimensions <= 0 || normalization.isBlank() || metric.isBlank()) {
                throw new IllegalArgumentException("complete embedding specification required");
            }
        }
    }

    record CacheEntry(String tenant, long aclEpoch, long sourceEpoch, boolean dependenciesActive) {
        CacheEntry {
            Objects.requireNonNull(tenant);
            if (tenant.isBlank() || aclEpoch < 0 || sourceEpoch < 0) {
                throw new IllegalArgumentException("invalid cache entry");
            }
        }
    }

    record SliceScore(double recallAtFive, double missingAnswerAbstention) {
        SliceScore {
            if (!Double.isFinite(recallAtFive) || recallAtFive < 0 || recallAtFive > 1
                    || !Double.isFinite(missingAnswerAbstention)
                    || missingAnswerAbstention < 0 || missingAnswerAbstention > 1) {
                throw new IllegalArgumentException("invalid score");
            }
        }
    }

    static Stage firstFailure(EvidencePath path) {
        if (!path.extraction()) return Stage.EXTRACTION;
        if (!path.chunking()) return Stage.CHUNKING;
        if (!path.eligibility()) return Stage.ELIGIBILITY;
        if (!path.retrieval()) return Stage.RETRIEVAL;
        if (!path.rerank()) return Stage.RERANK;
        if (!path.context()) return Stage.CONTEXT;
        if (!path.generation()) return Stage.GENERATION;
        return Stage.NONE;
    }

    static boolean compatible(EmbeddingSpec index, EmbeddingSpec query) {
        return index.equals(query);
    }

    static boolean mayCutOver(long expectedDocuments, long backfilledDocuments,
                              boolean pairedEvaluationPassed, boolean rollbackReady) {
        if (expectedDocuments < 0 || backfilledDocuments < 0 || backfilledDocuments > expectedDocuments) {
            throw new IllegalArgumentException("invalid backfill counts");
        }
        return expectedDocuments == backfilledDocuments && pairedEvaluationPassed && rollbackReady;
    }

    static boolean mayServe(CacheEntry entry, String requestTenant,
                            long currentAclEpoch, long currentSourceEpoch,
                            boolean currentlyAuthorized) {
        Objects.requireNonNull(requestTenant);
        return currentlyAuthorized
                && entry.tenant().equals(requestTenant)
                && entry.aclEpoch() == currentAclEpoch
                && entry.sourceEpoch() == currentSourceEpoch
                && entry.dependenciesActive();
    }

    static RetryDecision afterTimeout(OperationKind kind, boolean deadlineRemaining,
                                      String idempotencyKey, OperationStatus status) {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(status);
        if (!deadlineRemaining) return RetryDecision.STOP;
        if (kind == OperationKind.READ) return RetryDecision.RETRY;
        if (idempotencyKey.isBlank()) return RetryDecision.STOP;
        return switch (status) {
            case COMMITTED -> RetryDecision.RETURN_COMMITTED;
            case FAILED -> RetryDecision.RETURN_FAILED;
            case ABSENT -> RetryDecision.RETRY;
            case PENDING, UNKNOWN -> RetryDecision.RECONCILE;
        };
    }

    static boolean qualifies(Map<Slice, SliceScore> scores,
                             double minimumRecall, double minimumAbstention) {
        if (!Double.isFinite(minimumRecall) || minimumRecall < 0 || minimumRecall > 1
                || !Double.isFinite(minimumAbstention) || minimumAbstention < 0 || minimumAbstention > 1) {
            throw new IllegalArgumentException("invalid thresholds");
        }
        if (!scores.keySet().containsAll(java.util.Set.of(Slice.values()))) return false;
        for (Slice slice : Slice.values()) {
            SliceScore score = scores.get(slice);
            if (score == null) return false;
            if (slice == Slice.MISSING_ANSWER) {
                if (score.missingAnswerAbstention() < minimumAbstention) return false;
            } else if (score.recallAtFive() < minimumRecall) {
                return false;
            }
        }
        return true;
    }

    static EnumMap<Slice, SliceScore> scores(double code, double paraphrase,
                                              double multilingual, double abstention) {
        var result = new EnumMap<Slice, SliceScore>(Slice.class);
        result.put(Slice.ERROR_CODE, new SliceScore(code, 0));
        result.put(Slice.PARAPHRASE, new SliceScore(paraphrase, 0));
        result.put(Slice.MULTILINGUAL, new SliceScore(multilingual, 0));
        result.put(Slice.MISSING_ANSWER, new SliceScore(0, abstention));
        return result;
    }

    static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        int scenarios = 0;
        var goodPath = new EvidencePath(true, true, true, true, true, true, true);
        expect(firstFailure(goodPath) == Stage.NONE, "complete path"); scenarios++;
        expect(firstFailure(new EvidencePath(false, false, false, false, false, false, false)) == Stage.EXTRACTION, "extract first"); scenarios++;
        expect(firstFailure(new EvidencePath(true, false, false, false, false, false, false)) == Stage.CHUNKING, "chunk first"); scenarios++;
        expect(firstFailure(new EvidencePath(true, true, false, false, false, false, false)) == Stage.ELIGIBILITY, "eligibility first"); scenarios++;
        expect(firstFailure(new EvidencePath(true, true, true, false, false, false, false)) == Stage.RETRIEVAL, "retrieval first"); scenarios++;
        expect(firstFailure(new EvidencePath(true, true, true, true, false, false, false)) == Stage.RERANK, "rerank first"); scenarios++;
        expect(firstFailure(new EvidencePath(true, true, true, true, true, false, false)) == Stage.CONTEXT, "context first"); scenarios++;
        expect(firstFailure(new EvidencePath(true, true, true, true, true, true, false)) == Stage.GENERATION, "generation first"); scenarios++;

        var a = new EmbeddingSpec("model-a", 768, "l2", "cosine");
        var b = new EmbeddingSpec("model-b", 1024, "l2", "cosine");
        var paddedA = new EmbeddingSpec("model-a", 1024, "l2", "cosine");
        expect(compatible(a, a), "same generation compatible"); scenarios++;
        expect(!compatible(a, b), "different models incompatible"); scenarios++;
        expect(!compatible(paddedA, b), "padding does not create compatibility"); scenarios++;
        expect(!mayCutOver(1_000, 999, true, true), "partial backfill blocks"); scenarios++;
        expect(!mayCutOver(1_000, 1_000, false, true), "evaluation blocks"); scenarios++;
        expect(!mayCutOver(1_000, 1_000, true, false), "rollback readiness blocks"); scenarios++;
        expect(mayCutOver(1_000, 1_000, true, true), "validated cutover"); scenarios++;

        var cache = new CacheEntry("tenant-a", 12, 42, true);
        expect(mayServe(cache, "tenant-a", 12, 42, true), "fresh authorized cache"); scenarios++;
        expect(!mayServe(cache, "tenant-b", 12, 42, true), "tenant mismatch"); scenarios++;
        expect(!mayServe(cache, "tenant-a", 13, 42, true), "acl epoch mismatch"); scenarios++;
        expect(!mayServe(cache, "tenant-a", 12, 43, true), "source epoch mismatch"); scenarios++;
        expect(!mayServe(cache, "tenant-a", 12, 42, false), "current denial wins"); scenarios++;
        expect(!mayServe(new CacheEntry("tenant-a", 12, 42, false), "tenant-a", 12, 42, true), "inactive dependency"); scenarios++;

        expect(afterTimeout(OperationKind.READ, true, "", OperationStatus.UNKNOWN) == RetryDecision.RETRY, "read retry"); scenarios++;
        expect(afterTimeout(OperationKind.MUTATION, true, "op-7", OperationStatus.COMMITTED) == RetryDecision.RETURN_COMMITTED, "return commit"); scenarios++;
        expect(afterTimeout(OperationKind.MUTATION, true, "op-7", OperationStatus.FAILED) == RetryDecision.RETURN_FAILED, "return failure"); scenarios++;
        expect(afterTimeout(OperationKind.MUTATION, true, "op-7", OperationStatus.ABSENT) == RetryDecision.RETRY, "confirmed absent retry"); scenarios++;
        expect(afterTimeout(OperationKind.MUTATION, true, "op-7", OperationStatus.PENDING) == RetryDecision.RECONCILE, "pending reconcile"); scenarios++;
        expect(afterTimeout(OperationKind.MUTATION, true, "op-7", OperationStatus.UNKNOWN) == RetryDecision.RECONCILE, "unknown reconcile"); scenarios++;
        expect(afterTimeout(OperationKind.MUTATION, true, "", OperationStatus.ABSENT) == RetryDecision.STOP, "missing key stops"); scenarios++;
        expect(afterTimeout(OperationKind.MUTATION, false, "op-7", OperationStatus.ABSENT) == RetryDecision.STOP, "deadline stops"); scenarios++;

        expect(qualifies(scores(.91, .93, .90, .96), .90, .95), "all slices qualify"); scenarios++;
        expect(!qualifies(scores(.89, .99, .99, .99), .90, .95), "error-code regression blocks"); scenarios++;
        expect(!qualifies(scores(.99, .99, .89, .99), .90, .95), "multilingual regression blocks"); scenarios++;
        expect(!qualifies(scores(.99, .99, .99, .94), .90, .95), "abstention regression blocks"); scenarios++;
        var incomplete = scores(.99, .99, .99, .99);
        incomplete.remove(Slice.PARAPHRASE);
        expect(!qualifies(incomplete, .90, .95), "missing slice blocks"); scenarios++;

        try { mayCutOver(10, 11, true, true); throw new AssertionError("impossible backfill accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { new EmbeddingSpec("", 768, "l2", "cosine"); throw new AssertionError("empty model accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }

        Random random = new Random(0x13C0FFEE);
        int trials = 10_000;
        Stage[] ordered = { Stage.EXTRACTION, Stage.CHUNKING, Stage.ELIGIBILITY, Stage.RETRIEVAL,
                Stage.RERANK, Stage.CONTEXT, Stage.GENERATION };
        for (int i = 0; i < trials; i++) {
            boolean[] pass = new boolean[7];
            int first = 7;
            for (int j = 0; j < pass.length; j++) {
                pass[j] = random.nextBoolean();
                if (!pass[j] && first == 7) first = j;
            }
            Stage actual = firstFailure(new EvidencePath(pass[0], pass[1], pass[2], pass[3], pass[4], pass[5], pass[6]));
            Stage oracle = first == 7 ? Stage.NONE : ordered[first];
            expect(actual == oracle, "stage oracle " + i);
        }

        expect(scenarios == 36, "scenario count");
        System.out.println("SCENARIOS=36|TRIALS=10000|DIAGNOSIS=FIRST_FAILED_STAGE|EMBEDDINGS=GENERATION_FENCED|CACHE=AUTH_EPOCH|MUTATION=RECONCILE_FIRST|RETRIEVAL=SLICE_GATED");
    }
}
