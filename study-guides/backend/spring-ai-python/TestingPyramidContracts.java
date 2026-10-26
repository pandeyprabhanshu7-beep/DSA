import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency-free contracts for evidence-producing test layers and release gates. */
public final class TestingPyramidContracts {
    enum Layer { UNIT, ADAPTER, DATABASE, EVALUATION, LOAD, CLUSTER }
    enum Claim { ALGORITHM, WIRE_MAPPING, DATABASE_SCOPE, TASK_QUALITY, CAPACITY, RUNTIME_CONFIG }
    enum ProviderOutcome { SUCCESS, RATE_LIMITED, SERVER_ERROR, INVALID_RESPONSE, TIMEOUT }
    enum Gate { PROMOTE, BLOCK_MISSING, BLOCK_IDENTITY, BLOCK_SECURITY, BLOCK_FUNCTIONAL, BLOCK_QUALITY, BLOCK_LOAD }

    record Row(String id, String tenant, double score) {
        Row {
            if (id == null || id.isBlank() || tenant == null || tenant.isBlank()
                    || !Double.isFinite(score)) {
                throw new IllegalArgumentException("invalid row");
            }
        }
    }

    record Evidence(Layer layer, String releaseId, String fixtureRevision,
                    int tests, int failures, int securityFailures,
                    int metric, int threshold, boolean lowerIsBetter) {
        Evidence {
            if (layer == null || releaseId == null || releaseId.isBlank()
                    || fixtureRevision == null || fixtureRevision.isBlank()
                    || tests <= 0 || failures < 0 || failures > tests
                    || securityFailures < 0 || securityFailures > failures
                    || metric < 0 || threshold < 0) {
                throw new IllegalArgumentException("invalid evidence");
            }
        }

        boolean metricPasses() {
            return lowerIsBetter ? metric <= threshold : metric >= threshold;
        }
    }

    static Layer minimumLayer(Claim claim) {
        return switch (claim) {
            case ALGORITHM -> Layer.UNIT;
            case WIRE_MAPPING -> Layer.ADAPTER;
            case DATABASE_SCOPE -> Layer.DATABASE;
            case TASK_QUALITY -> Layer.EVALUATION;
            case CAPACITY -> Layer.LOAD;
            case RUNTIME_CONFIG -> Layer.CLUSTER;
        };
    }

    static boolean proves(Layer layer, Claim claim) {
        return layer == minimumLayer(claim);
    }

    static ProviderOutcome providerOutcome(int status, boolean timedOut, boolean wellFormed) {
        if (timedOut) return ProviderOutcome.TIMEOUT;
        if (status == 429) return ProviderOutcome.RATE_LIMITED;
        if (status >= 500 && status <= 599) return ProviderOutcome.SERVER_ERROR;
        if (status >= 200 && status <= 299 && wellFormed) return ProviderOutcome.SUCCESS;
        return ProviderOutcome.INVALID_RESPONSE;
    }

    static List<String> scopedRowIds(String verifiedTenant, List<Row> rows, int limit) {
        if (verifiedTenant == null || verifiedTenant.isBlank() || rows == null || limit < 1) {
            throw new IllegalArgumentException("invalid scope query");
        }
        return rows.stream()
                .filter(row -> row.tenant().equals(verifiedTenant))
                .sorted((left, right) -> {
                    int byScore = -Double.compare(left.score(), right.score());
                    return byScore != 0 ? byScore : left.id().compareTo(right.id());
                })
                .limit(limit)
                .map(Row::id)
                .toList();
    }

    static Gate releaseGate(String expectedRelease, List<Evidence> evidence) {
        if (expectedRelease == null || expectedRelease.isBlank() || evidence == null) {
            throw new IllegalArgumentException("invalid release gate");
        }
        Map<Layer, Evidence> byLayer = new EnumMap<>(Layer.class);
        for (Evidence item : evidence) {
            if (byLayer.put(item.layer(), item) != null) {
                throw new IllegalArgumentException("duplicate layer evidence");
            }
        }
        if (byLayer.size() != Layer.values().length) return Gate.BLOCK_MISSING;
        if (evidence.stream().anyMatch(item -> !item.releaseId().equals(expectedRelease))) {
            return Gate.BLOCK_IDENTITY;
        }
        if (evidence.stream().anyMatch(item -> item.securityFailures() > 0)) {
            return Gate.BLOCK_SECURITY;
        }
        if (evidence.stream().anyMatch(item -> item.failures() > 0)) {
            return Gate.BLOCK_FUNCTIONAL;
        }
        if (!byLayer.get(Layer.EVALUATION).metricPasses()) return Gate.BLOCK_QUALITY;
        if (!byLayer.get(Layer.LOAD).metricPasses()) return Gate.BLOCK_LOAD;
        return Gate.PROMOTE;
    }

    static List<Evidence> passingEvidence(String release, int qualityBp, int loadP95Ms) {
        List<Evidence> result = new ArrayList<>();
        for (Layer layer : Layer.values()) {
            int metric = layer == Layer.EVALUATION ? qualityBp
                    : layer == Layer.LOAD ? loadP95Ms : 1;
            int threshold = layer == Layer.EVALUATION ? 8_500
                    : layer == Layer.LOAD ? 900 : 1;
            result.add(new Evidence(layer, release, "fixture-v15", 100, 0, 0,
                    metric, threshold, layer == Layer.LOAD));
        }
        return result;
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }

    public static void main(String[] args) {
        int scenarios = 0;
        for (Claim claim : Claim.values()) {
            check(proves(minimumLayer(claim), claim)); scenarios++;
        }
        check(!proves(Layer.UNIT, Claim.DATABASE_SCOPE)); scenarios++;
        check(!proves(Layer.ADAPTER, Claim.TASK_QUALITY)); scenarios++;
        check(!proves(Layer.DATABASE, Claim.RUNTIME_CONFIG)); scenarios++;

        check(providerOutcome(200, false, true) == ProviderOutcome.SUCCESS); scenarios++;
        check(providerOutcome(204, false, true) == ProviderOutcome.SUCCESS); scenarios++;
        check(providerOutcome(200, false, false) == ProviderOutcome.INVALID_RESPONSE); scenarios++;
        check(providerOutcome(400, false, true) == ProviderOutcome.INVALID_RESPONSE); scenarios++;
        check(providerOutcome(429, false, false) == ProviderOutcome.RATE_LIMITED); scenarios++;
        check(providerOutcome(503, false, false) == ProviderOutcome.SERVER_ERROR); scenarios++;
        check(providerOutcome(200, true, true) == ProviderOutcome.TIMEOUT); scenarios++;

        List<Row> rows = List.of(
                new Row("p-low", "payments", 0.71),
                new Row("exec", "executive", 0.99),
                new Row("p-high", "payments", 0.91),
                new Row("p-tie", "payments", 0.91));
        check(scopedRowIds("payments", rows, 2).equals(List.of("p-high", "p-tie"))); scenarios++;
        check(scopedRowIds("executive", rows, 2).equals(List.of("exec"))); scenarios++;
        check(scopedRowIds("unknown", rows, 2).isEmpty()); scenarios++;
        try { scopedRowIds("payments", rows, 0); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        List<Evidence> passing = passingEvidence("r15", 8_900, 720);
        check(releaseGate("r15", passing) == Gate.PROMOTE); scenarios++;
        check(releaseGate("r15", passing.subList(0, 5)) == Gate.BLOCK_MISSING); scenarios++;
        List<Evidence> wrongIdentity = new ArrayList<>(passing);
        wrongIdentity.set(1, new Evidence(Layer.ADAPTER, "r14", "fixture-v15", 100, 0, 0, 1, 1, false));
        check(releaseGate("r15", wrongIdentity) == Gate.BLOCK_IDENTITY); scenarios++;
        List<Evidence> security = new ArrayList<>(passing);
        security.set(2, new Evidence(Layer.DATABASE, "r15", "fixture-v15", 100, 1, 1, 1, 1, false));
        check(releaseGate("r15", security) == Gate.BLOCK_SECURITY); scenarios++;
        List<Evidence> functional = new ArrayList<>(passing);
        functional.set(0, new Evidence(Layer.UNIT, "r15", "fixture-v15", 100, 1, 0, 1, 1, false));
        check(releaseGate("r15", functional) == Gate.BLOCK_FUNCTIONAL); scenarios++;
        check(releaseGate("r15", passingEvidence("r15", 8_499, 720)) == Gate.BLOCK_QUALITY); scenarios++;
        check(releaseGate("r15", passingEvidence("r15", 8_900, 901)) == Gate.BLOCK_LOAD); scenarios++;
        try {
            List<Evidence> duplicate = new ArrayList<>(passing);
            duplicate.add(passing.get(0));
            releaseGate("r15", duplicate);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) { scenarios++; }
        try { new Evidence(Layer.UNIT, "r15", "f", 1, 2, 0, 1, 1, false); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        long state = 0x15C0DEL;
        int promoted = 0;
        Set<Gate> observed = new HashSet<>();
        for (int trial = 0; trial < 10_000; trial++) {
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            int quality = (int) (state % 10_001);
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            int load = (int) (state % 2_001);
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            boolean failed = state % 37 == 0;
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            boolean securityFailed = state % 101 == 0;
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            boolean wrongRelease = state % 97 == 0;

            List<Evidence> evidence = passingEvidence("r15", quality, load);
            if (failed || securityFailed) {
                evidence.set(0, new Evidence(Layer.UNIT, "r15", "fixture-v15", 100,
                        1, securityFailed ? 1 : 0, 1, 1, false));
            }
            if (wrongRelease) {
                Evidence old = evidence.get(1);
                evidence.set(1, new Evidence(old.layer(), "other", old.fixtureRevision(),
                        old.tests(), old.failures(), old.securityFailures(), old.metric(),
                        old.threshold(), old.lowerIsBetter()));
            }
            Gate actual = releaseGate("r15", evidence);
            Gate oracle = wrongRelease ? Gate.BLOCK_IDENTITY
                    : securityFailed ? Gate.BLOCK_SECURITY
                    : failed ? Gate.BLOCK_FUNCTIONAL
                    : quality < 8_500 ? Gate.BLOCK_QUALITY
                    : load > 900 ? Gate.BLOCK_LOAD : Gate.PROMOTE;
            check(actual == oracle);
            observed.add(actual);
            if (actual == Gate.PROMOTE) promoted++;
        }

        check(scenarios == 29);
        check(observed.containsAll(Set.of(Gate.PROMOTE, Gate.BLOCK_IDENTITY,
                Gate.BLOCK_SECURITY, Gate.BLOCK_FUNCTIONAL, Gate.BLOCK_QUALITY,
                Gate.BLOCK_LOAD)));
        System.out.println("PARITY|FAST=UNIT_ADAPTER|DB=REAL_PGVECTOR|QUALITY=FIXED_DATASET"
                + "|LOAD=SLO_THRESHOLDS|CLUSTER=PROBES|RELEASE=EVIDENCE_GATED"
                + "|PROMOTED=" + promoted);
        System.out.println("Java testing-pyramid contracts passed: " + scenarios
                + " scenario groups + 10000 seeded release-gate trials");
    }
}
