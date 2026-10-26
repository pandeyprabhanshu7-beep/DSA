import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency-free contracts for boundary-first observability and diagnosis. */
public final class ObservabilityBoundaryContracts {
    enum Boundary { QUEUE, CONNECTION_POOL, RETRIEVAL, TOOL, MODEL, OUTPUT }
    enum RetrievalDiagnosis {
        EXTRACTION_MISS, RETRIEVAL_MISS, AUTHORIZATION_FILTERED,
        CONTEXT_TRUNCATION, GENERATION_CITATION_MISS, COMPLETE
    }
    enum IncidentAction { CONTINUE, STOP_AND_ISOLATE }

    record Latency(long queueMs, long poolMs, long retrievalMs,
                   long toolMs, long modelMs, long outputMs) {
        Latency {
            if (queueMs < 0 || poolMs < 0 || retrievalMs < 0
                    || toolMs < 0 || modelMs < 0 || outputMs < 0) {
                throw new IllegalArgumentException("negative latency");
            }
        }
    }

    record ReleaseEvidence(String releaseId, String modelRevision,
                           String promptVersion, String indexGeneration,
                           int candidateCount, int authorizedCount,
                           int citedCount, String completionReason) {
        ReleaseEvidence {
            if (releaseId == null || releaseId.isBlank()
                    || modelRevision == null || modelRevision.isBlank()
                    || promptVersion == null || promptVersion.isBlank()
                    || indexGeneration == null || indexGeneration.isBlank()
                    || candidateCount < 0 || authorizedCount < 0
                    || authorizedCount > candidateCount || citedCount < 0
                    || citedCount > authorizedCount
                    || completionReason == null || completionReason.isBlank()) {
                throw new IllegalArgumentException("invalid release evidence");
            }
        }
    }

    record RuntimeConfig(String modelRevision, String promptHash,
                         double temperature, int tokenLimit,
                         String embeddingRevision, List<String> candidateIds) {
        RuntimeConfig {
            if (modelRevision == null || modelRevision.isBlank()
                    || promptHash == null || promptHash.isBlank()
                    || !Double.isFinite(temperature) || temperature < 0
                    || tokenLimit <= 0 || embeddingRevision == null
                    || embeddingRevision.isBlank() || candidateIds == null) {
                throw new IllegalArgumentException("invalid runtime config");
            }
            candidateIds = List.copyOf(candidateIds);
        }
    }

    static Boundary dominant(Latency latency) {
        long[] values = {latency.queueMs(), latency.poolMs(), latency.retrievalMs(),
                latency.toolMs(), latency.modelMs(), latency.outputMs()};
        Boundary[] boundaries = Boundary.values();
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return boundaries[best];
    }

    static RetrievalDiagnosis retrievalDiagnosis(boolean extracted,
                                                  int candidates,
                                                  int authorized,
                                                  boolean includedInContext,
                                                  boolean cited) {
        if (candidates < 0 || authorized < 0 || authorized > candidates) {
            throw new IllegalArgumentException("invalid retrieval counts");
        }
        if (!extracted) return RetrievalDiagnosis.EXTRACTION_MISS;
        if (candidates == 0) return RetrievalDiagnosis.RETRIEVAL_MISS;
        if (authorized == 0) return RetrievalDiagnosis.AUTHORIZATION_FILTERED;
        if (!includedInContext) return RetrievalDiagnosis.CONTEXT_TRUNCATION;
        if (!cited) return RetrievalDiagnosis.GENERATION_CITATION_MISS;
        return RetrievalDiagnosis.COMPLETE;
    }

    static IncidentAction tenantAction(String requestTenant,
                                       Set<String> returnedTenants) {
        if (requestTenant == null || requestTenant.isBlank() || returnedTenants == null) {
            throw new IllegalArgumentException("tenant evidence");
        }
        return returnedTenants.stream().allMatch(requestTenant::equals)
                ? IncidentAction.CONTINUE : IncidentAction.STOP_AND_ISOLATE;
    }

    static boolean toolVisible(boolean portHealthy, boolean registered,
                               boolean modeCompatible, boolean sdkCompatible) {
        return portHealthy && registered && modeCompatible && sdkCompatible;
    }

    static String firstParityDifference(RuntimeConfig javaConfig,
                                        RuntimeConfig pythonConfig) {
        if (!javaConfig.modelRevision().equals(pythonConfig.modelRevision())) return "modelRevision";
        if (!javaConfig.promptHash().equals(pythonConfig.promptHash())) return "promptHash";
        if (Double.compare(javaConfig.temperature(), pythonConfig.temperature()) != 0) return "temperature";
        if (javaConfig.tokenLimit() != pythonConfig.tokenLimit()) return "tokenLimit";
        if (!javaConfig.embeddingRevision().equals(pythonConfig.embeddingRevision())) return "embeddingRevision";
        if (!javaConfig.candidateIds().equals(pythonConfig.candidateIds())) return "candidateIds";
        return "none";
    }

    static boolean metricLabelAllowed(String key) {
        return Set.of("model.class", "release.cohort", "outcome", "boundary").contains(key);
    }

    static Map<String, String> traceContent(boolean explicitlyEnabled,
                                            boolean protectedSink,
                                            String prompt,
                                            String completion) {
        if (!explicitlyEnabled || !protectedSink) return Map.of();
        return Map.of("prompt", prompt, "completion", completion);
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }

    public static void main(String[] args) {
        int scenarios = 0;
        check(dominant(new Latency(900, 40, 100, 0, 300, 10)) == Boundary.QUEUE); scenarios++;
        check(dominant(new Latency(10, 800, 100, 0, 300, 10)) == Boundary.CONNECTION_POOL); scenarios++;
        check(dominant(new Latency(10, 20, 700, 0, 300, 10)) == Boundary.RETRIEVAL); scenarios++;
        check(dominant(new Latency(10, 20, 30, 650, 300, 10)) == Boundary.TOOL); scenarios++;
        check(dominant(new Latency(10, 20, 30, 0, 900, 10)) == Boundary.MODEL); scenarios++;
        check(dominant(new Latency(10, 20, 30, 0, 40, 900)) == Boundary.OUTPUT); scenarios++;
        try { new Latency(-1, 0, 0, 0, 0, 0); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        check(retrievalDiagnosis(false, 0, 0, false, false) == RetrievalDiagnosis.EXTRACTION_MISS); scenarios++;
        check(retrievalDiagnosis(true, 0, 0, false, false) == RetrievalDiagnosis.RETRIEVAL_MISS); scenarios++;
        check(retrievalDiagnosis(true, 4, 0, false, false) == RetrievalDiagnosis.AUTHORIZATION_FILTERED); scenarios++;
        check(retrievalDiagnosis(true, 4, 3, false, false) == RetrievalDiagnosis.CONTEXT_TRUNCATION); scenarios++;
        check(retrievalDiagnosis(true, 4, 3, true, false) == RetrievalDiagnosis.GENERATION_CITATION_MISS); scenarios++;
        check(retrievalDiagnosis(true, 4, 3, true, true) == RetrievalDiagnosis.COMPLETE); scenarios++;
        try { retrievalDiagnosis(true, 2, 3, true, true); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        check(tenantAction("payments", Set.of("payments")) == IncidentAction.CONTINUE); scenarios++;
        check(tenantAction("payments", Set.of("payments", "executive")) == IncidentAction.STOP_AND_ISOLATE); scenarios++;
        check(!toolVisible(true, false, true, true)); scenarios++;
        check(!toolVisible(true, true, false, true)); scenarios++;
        check(!toolVisible(true, true, true, false)); scenarios++;
        check(toolVisible(true, true, true, true)); scenarios++;

        RuntimeConfig base = new RuntimeConfig("m-17", "sha256:p", 0.0, 800,
                "embed-v4", List.of("d1", "d7"));
        check(firstParityDifference(base, base).equals("none")); scenarios++;
        check(firstParityDifference(base, new RuntimeConfig("m-18", "sha256:p", 0.0, 800, "embed-v4", List.of("d1", "d7"))).equals("modelRevision")); scenarios++;
        check(firstParityDifference(base, new RuntimeConfig("m-17", "sha256:q", 0.0, 800, "embed-v4", List.of("d1", "d7"))).equals("promptHash")); scenarios++;
        check(firstParityDifference(base, new RuntimeConfig("m-17", "sha256:p", 0.2, 800, "embed-v4", List.of("d1", "d7"))).equals("temperature")); scenarios++;
        check(firstParityDifference(base, new RuntimeConfig("m-17", "sha256:p", 0.0, 900, "embed-v4", List.of("d1", "d7"))).equals("tokenLimit")); scenarios++;
        check(firstParityDifference(base, new RuntimeConfig("m-17", "sha256:p", 0.0, 800, "embed-v5", List.of("d1", "d7"))).equals("embeddingRevision")); scenarios++;
        check(firstParityDifference(base, new RuntimeConfig("m-17", "sha256:p", 0.0, 800, "embed-v4", List.of("d7", "d1"))).equals("candidateIds")); scenarios++;

        check(metricLabelAllowed("outcome")); scenarios++;
        check(metricLabelAllowed("release.cohort")); scenarios++;
        check(!metricLabelAllowed("user.id")); scenarios++;
        check(!metricLabelAllowed("document.id")); scenarios++;
        check(!metricLabelAllowed("prompt.text")); scenarios++;
        check(traceContent(false, true, "secret", "answer").isEmpty()); scenarios++;
        check(traceContent(true, false, "secret", "answer").isEmpty()); scenarios++;
        check(traceContent(true, true, "approved", "answer").size() == 2); scenarios++;
        new ReleaseEvidence("r14", "m-17", "p12", "g43", 8, 5, 2, "stop"); scenarios++;
        try { new ReleaseEvidence("r14", "m-17", "p12", "g43", 2, 3, 1, "stop"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        long state = 0x14B0A7L;
        int modelDominant = 0;
        for (int i = 0; i < 10_000; i++) {
            long[] v = new long[6];
            for (int j = 0; j < v.length; j++) {
                state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
                v[j] = (state >>> 10) % 2_000;
            }
            Latency latency = new Latency(v[0], v[1], v[2], v[3], v[4], v[5]);
            int oracle = 0;
            for (int j = 1; j < v.length; j++) if (v[j] > v[oracle]) oracle = j;
            check(dominant(latency) == Boundary.values()[oracle]);
            if (oracle == Boundary.MODEL.ordinal()) modelDominant++;
        }

        check(scenarios == 37);
        System.out.println("PARITY|TRACE=BOUNDARY_FIRST|METRICS=BOUNDED_LABELS|CONTENT=DEFAULT_OFF"
                + "|TENANT_LEAK=STOP|TOOLS=REGISTRATION_PROVED|MISMATCH=CONFIG_FIRST"
                + "|MODEL_DOMINANT=" + modelDominant);
        System.out.println("Java observability contracts passed: " + scenarios
                + " scenario groups + 10000 seeded trials");
    }
}
