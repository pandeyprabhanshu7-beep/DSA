import java.util.Objects;
import java.util.Random;

/** Dependency-free capacity, release-tuple, and canary-gate contracts for Chapter 20. */
public final class CapacityReleaseContracts {
    private static int scenarios;

    enum Admission {
        ADMIT,
        LEASE_EXPIRED,
        FLEET_TOKEN_BUDGET,
        TENANT_LIMIT,
        QUEUE_LIMIT,
        DEADLINE
    }

    enum RolloutDecision { PROMOTE, PAUSE, ROLLBACK }

    record AdmissionRequest(
            long nowMillis,
            long leaseExpiresMillis,
            long fleetTokensRemaining,
            long promptTokens,
            long maxOutputTokens,
            long reservedToolTokens,
            int tenantInFlight,
            int tenantLimit,
            int queueDepth,
            int queueLimit,
            long deadlineRemainingMillis,
            long estimatedServiceMillis) {}

    record ReleaseManifest(
            String imageDigest,
            String promptRevision,
            String modelRoute,
            String embeddingSpace,
            String indexGeneration,
            String toolSchema,
            String evaluationDataset) {
        ReleaseManifest {
            Objects.requireNonNull(imageDigest);
            Objects.requireNonNull(promptRevision);
            Objects.requireNonNull(modelRoute);
            Objects.requireNonNull(embeddingSpace);
            Objects.requireNonNull(indexGeneration);
            Objects.requireNonNull(toolSchema);
            Objects.requireNonNull(evaluationDataset);
        }

        boolean complete() {
            return !imageDigest.isBlank()
                    && !promptRevision.isBlank()
                    && !modelRoute.isBlank()
                    && !embeddingSpace.isBlank()
                    && !indexGeneration.isBlank()
                    && !toolSchema.isBlank()
                    && !evaluationDataset.isBlank();
        }
    }

    record CanaryEvidence(
            boolean manifestComplete,
            boolean rollbackTupleReady,
            int errorBudgetRemainingBasisPoints,
            int retrievalRecallBasisPoints,
            int supportedAnswerBasisPoints,
            int criticalSliceBasisPoints,
            int capacityRejectionBasisPoints,
            long timeToFirstTokenP99Millis) {}

    static long activeRequestEstimate(long requestsPerSecond, long meanServiceSeconds) {
        requireNonNegative(requestsPerSecond, "requestsPerSecond");
        requireNonNegative(meanServiceSeconds, "meanServiceSeconds");
        return Math.multiplyExact(requestsPerSecond, meanServiceSeconds);
    }

    static long tokenRate(long requestsPerSecond, long inputTokens, long outputTokens) {
        requireNonNegative(requestsPerSecond, "requestsPerSecond");
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        return Math.multiplyExact(requestsPerSecond, Math.addExact(inputTokens, outputTokens));
    }

    static long replicaLocalCap(long replicas, long localLimit) {
        requireNonNegative(replicas, "replicas");
        requireNonNegative(localLimit, "localLimit");
        return Math.multiplyExact(replicas, localLimit);
    }

    static Admission admit(AdmissionRequest request) {
        Objects.requireNonNull(request);
        requireNonNegative(request.fleetTokensRemaining(), "fleetTokensRemaining");
        requireNonNegative(request.promptTokens(), "promptTokens");
        requireNonNegative(request.maxOutputTokens(), "maxOutputTokens");
        requireNonNegative(request.reservedToolTokens(), "reservedToolTokens");
        requireNonNegative(request.tenantInFlight(), "tenantInFlight");
        requirePositive(request.tenantLimit(), "tenantLimit");
        requireNonNegative(request.queueDepth(), "queueDepth");
        requirePositive(request.queueLimit(), "queueLimit");
        requireNonNegative(request.deadlineRemainingMillis(), "deadlineRemainingMillis");
        requireNonNegative(request.estimatedServiceMillis(), "estimatedServiceMillis");

        if (request.nowMillis() >= request.leaseExpiresMillis()) return Admission.LEASE_EXPIRED;
        long reserved = Math.addExact(
                Math.addExact(request.promptTokens(), request.maxOutputTokens()),
                request.reservedToolTokens());
        if (reserved > request.fleetTokensRemaining()) return Admission.FLEET_TOKEN_BUDGET;
        if (request.tenantInFlight() >= request.tenantLimit()) return Admission.TENANT_LIMIT;
        if (request.queueDepth() >= request.queueLimit()) return Admission.QUEUE_LIMIT;
        if (request.deadlineRemainingMillis() < request.estimatedServiceMillis()) {
            return Admission.DEADLINE;
        }
        return Admission.ADMIT;
    }

    static boolean sameReleaseTuple(ReleaseManifest left, ReleaseManifest right) {
        return Objects.equals(left, right);
    }

    static RolloutDecision decide(CanaryEvidence evidence) {
        Objects.requireNonNull(evidence);
        validateBasisPoints(evidence.errorBudgetRemainingBasisPoints());
        validateBasisPoints(evidence.retrievalRecallBasisPoints());
        validateBasisPoints(evidence.supportedAnswerBasisPoints());
        validateBasisPoints(evidence.criticalSliceBasisPoints());
        validateBasisPoints(evidence.capacityRejectionBasisPoints());
        requireNonNegative(evidence.timeToFirstTokenP99Millis(), "timeToFirstTokenP99Millis");

        if (!evidence.manifestComplete() || !evidence.rollbackTupleReady()) {
            return RolloutDecision.PAUSE;
        }
        if (evidence.errorBudgetRemainingBasisPoints() == 0
                || evidence.retrievalRecallBasisPoints() < 9_000
                || evidence.supportedAnswerBasisPoints() < 8_100
                || evidence.criticalSliceBasisPoints() < 9_000
                || evidence.capacityRejectionBasisPoints() > 100
                || evidence.timeToFirstTokenP99Millis() > 3_000) {
            return RolloutDecision.ROLLBACK;
        }
        return RolloutDecision.PROMOTE;
    }

    private static void validateBasisPoints(int value) {
        if (value < 0 || value > 10_000) throw new IllegalArgumentException("basis points");
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name);
    }

    private static void check(boolean condition) {
        scenarios++;
        if (!condition) throw new AssertionError("scenario " + scenarios);
    }

    private static void throwsArithmetic(Runnable action) {
        try {
            action.run();
            check(false);
        } catch (ArithmeticException expected) {
            check(true);
        }
    }

    private static AdmissionRequest request(
            long now,
            long expiry,
            long tokens,
            long prompt,
            long output,
            long tools,
            int tenantActive,
            int tenantLimit,
            int queueDepth,
            int queueLimit,
            long deadline,
            long service) {
        return new AdmissionRequest(now, expiry, tokens, prompt, output, tools,
                tenantActive, tenantLimit, queueDepth, queueLimit, deadline, service);
    }

    private static ReleaseManifest manifest(
            String image, String prompt, String route, String embedding,
            String index, String tools, String evaluation) {
        return new ReleaseManifest(image, prompt, route, embedding, index, tools, evaluation);
    }

    private static CanaryEvidence evidence(
            boolean complete,
            boolean rollback,
            int budget,
            int retrieval,
            int answer,
            int critical,
            int rejection,
            long ttft) {
        return new CanaryEvidence(complete, rollback, budget, retrieval, answer, critical, rejection, ttft);
    }

    private static Admission admissionOracle(AdmissionRequest r) {
        if (r.nowMillis() >= r.leaseExpiresMillis()) return Admission.LEASE_EXPIRED;
        long required = r.promptTokens() + r.maxOutputTokens() + r.reservedToolTokens();
        if (required > r.fleetTokensRemaining()) return Admission.FLEET_TOKEN_BUDGET;
        if (r.tenantInFlight() >= r.tenantLimit()) return Admission.TENANT_LIMIT;
        if (r.queueDepth() >= r.queueLimit()) return Admission.QUEUE_LIMIT;
        if (r.deadlineRemainingMillis() < r.estimatedServiceMillis()) return Admission.DEADLINE;
        return Admission.ADMIT;
    }

    private static RolloutDecision rolloutOracle(CanaryEvidence e) {
        if (!(e.manifestComplete() && e.rollbackTupleReady())) return RolloutDecision.PAUSE;
        boolean hardGate = e.errorBudgetRemainingBasisPoints() > 0
                && e.retrievalRecallBasisPoints() >= 9_000
                && e.supportedAnswerBasisPoints() >= 8_100
                && e.criticalSliceBasisPoints() >= 9_000
                && e.capacityRejectionBasisPoints() <= 100
                && e.timeToFirstTokenP99Millis() <= 3_000;
        return hardGate ? RolloutDecision.PROMOTE : RolloutDecision.ROLLBACK;
    }

    private static void explicitScenarios() {
        check(activeRequestEstimate(20, 4) == 80);
        check(tokenRate(20, 2_000, 400) == 48_000);
        check(replicaLocalCap(10, 8) == 80);
        check(replicaLocalCap(20, 8) == 160);
        throwsArithmetic(() -> activeRequestEstimate(Long.MAX_VALUE, 2));

        AdmissionRequest baseline = request(100, 200, 7_200, 2_000, 400, 0,
                2, 8, 3, 10, 5_000, 4_000);
        check(admit(baseline) == Admission.ADMIT);
        check(admit(request(200, 200, 7_200, 2_000, 400, 0, 2, 8, 3, 10, 5_000, 4_000)) == Admission.LEASE_EXPIRED);
        check(admit(request(100, 200, 2_399, 2_000, 400, 0, 2, 8, 3, 10, 5_000, 4_000)) == Admission.FLEET_TOKEN_BUDGET);
        check(admit(request(100, 200, 7_200, 2_000, 400, 0, 8, 8, 3, 10, 5_000, 4_000)) == Admission.TENANT_LIMIT);
        check(admit(request(100, 200, 7_200, 2_000, 400, 0, 2, 8, 10, 10, 5_000, 4_000)) == Admission.QUEUE_LIMIT);
        check(admit(request(100, 200, 7_200, 2_000, 400, 0, 2, 8, 3, 10, 3_999, 4_000)) == Admission.DEADLINE);
        check(admit(request(200, 200, 0, 2_000, 400, 0, 8, 8, 10, 10, 1, 4_000)) == Admission.LEASE_EXPIRED);
        check(admit(request(100, 200, 2_400, 2_000, 400, 0, 2, 8, 3, 10, 5_000, 4_000)) == Admission.ADMIT);
        check(admit(request(100, 200, 7_200, 2_000, 400, 0, 7, 8, 3, 10, 5_000, 4_000)) == Admission.ADMIT);
        check(admit(request(100, 200, 7_200, 2_000, 400, 0, 2, 8, 9, 10, 5_000, 4_000)) == Admission.ADMIT);
        check(admit(request(100, 200, 7_200, 2_000, 400, 0, 2, 8, 3, 10, 4_000, 4_000)) == Admission.ADMIT);
        try {
            admit(request(100, 200, 7_200, -1, 400, 0, 2, 8, 3, 10, 5_000, 4_000));
            check(false);
        } catch (IllegalArgumentException expected) {
            check(true);
        }

        ReleaseManifest stable = manifest("img@sha256:a", "prompt-20", "route-7",
                "embed-3", "index-43", "tools-5", "eval-9");
        check(stable.complete() && sameReleaseTuple(stable, stable));
        check(!sameReleaseTuple(stable, manifest("img@sha256:b", "prompt-20", "route-7", "embed-3", "index-43", "tools-5", "eval-9")));
        check(!sameReleaseTuple(stable, manifest("img@sha256:a", "prompt-21", "route-7", "embed-3", "index-43", "tools-5", "eval-9")));
        check(!sameReleaseTuple(stable, manifest("img@sha256:a", "prompt-20", "route-8", "embed-3", "index-43", "tools-5", "eval-9")));
        check(!sameReleaseTuple(stable, manifest("img@sha256:a", "prompt-20", "route-7", "embed-4", "index-43", "tools-5", "eval-9")));
        check(!sameReleaseTuple(stable, manifest("img@sha256:a", "prompt-20", "route-7", "embed-3", "index-44", "tools-5", "eval-9")));
        check(!sameReleaseTuple(stable, manifest("img@sha256:a", "prompt-20", "route-7", "embed-3", "index-43", "tools-6", "eval-9")));
        check(!sameReleaseTuple(stable, manifest("img@sha256:a", "prompt-20", "route-7", "embed-3", "index-43", "tools-5", "eval-10")));

        check(decide(evidence(true, true, 500, 9_000, 8_100, 9_000, 100, 3_000)) == RolloutDecision.PROMOTE);
        check(decide(evidence(false, true, 500, 9_000, 8_100, 9_000, 100, 3_000)) == RolloutDecision.PAUSE);
        check(decide(evidence(true, false, 500, 9_000, 8_100, 9_000, 100, 3_000)) == RolloutDecision.PAUSE);
        check(decide(evidence(true, true, 0, 9_000, 8_100, 9_000, 100, 3_000)) == RolloutDecision.ROLLBACK);
        check(decide(evidence(true, true, 500, 8_999, 8_100, 9_000, 100, 3_000)) == RolloutDecision.ROLLBACK);
        check(decide(evidence(true, true, 500, 9_000, 8_100, 8_999, 100, 3_000)) == RolloutDecision.ROLLBACK);
    }

    private static void seededOracleTrials() {
        Random random = new Random(20_261_028L);
        for (int trial = 0; trial < 10_000; trial++) {
            long now = random.nextInt(5_000);
            AdmissionRequest request = request(
                    now,
                    random.nextInt(5_500),
                    random.nextInt(12_000),
                    random.nextInt(4_000),
                    random.nextInt(2_000),
                    random.nextInt(1_000),
                    random.nextInt(12),
                    1 + random.nextInt(12),
                    random.nextInt(20),
                    1 + random.nextInt(20),
                    random.nextInt(8_000),
                    random.nextInt(8_000));
            if (admit(request) != admissionOracle(request)) throw new AssertionError("admission trial " + trial);

            String suffix = Integer.toString(random.nextInt(4));
            ReleaseManifest left = manifest("i" + suffix, "p" + suffix, "r" + suffix,
                    "e" + suffix, "x" + suffix, "t" + suffix, "d" + suffix);
            boolean mutate = random.nextBoolean();
            ReleaseManifest right = mutate
                    ? manifest("i" + suffix, "p" + suffix, "r" + suffix,
                            "e" + suffix, "x" + suffix, "t" + suffix, "different")
                    : left;
            if (sameReleaseTuple(left, right) != (!mutate)) throw new AssertionError("tuple trial " + trial);

            CanaryEvidence evidence = evidence(
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextInt(10_001),
                    random.nextInt(10_001),
                    random.nextInt(10_001),
                    random.nextInt(10_001),
                    random.nextInt(10_001),
                    random.nextInt(6_001));
            if (decide(evidence) != rolloutOracle(evidence)) throw new AssertionError("rollout trial " + trial);
        }
    }

    public static void main(String[] args) {
        explicitScenarios();
        if (scenarios != 31) throw new AssertionError("expected 31 scenarios, got " + scenarios);
        seededOracleTrials();
        System.out.println("SCENARIOS=31|TRIALS=10000|CAPACITY=FLEET_LEASED|LOAD=TOKENS_NOT_REQUESTS|RELEASE=TUPLE_ATOMIC|SLO=STAGE_AND_SLICE|ROLLOUT=ABORTABLE");
    }
}
