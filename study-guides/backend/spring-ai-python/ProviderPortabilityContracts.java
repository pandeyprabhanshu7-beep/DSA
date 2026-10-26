import java.util.EnumSet;
import java.util.Set;

/** Dependency-free contracts for provider capability admission, retries and migration. */
public final class ProviderPortabilityContracts {
    enum Feature { TEXT, STREAM, TOOLS, STRICT_JSON, EMBEDDING }
    enum Boundary { PORTABLE, PROVIDER_EXTENSION }
    enum Observation { NOT_SENT, REJECTED, ACCEPTED_UNKNOWN, COMPLETED }
    enum Next { RETRY, RECONCILE, RETURN, FAIL }

    record Provider(String id, Set<Feature> features, boolean workloadIdentity,
                    boolean idempotencyKey, String modelRevision) {
        Provider {
            if (id == null || id.isBlank() || features == null || modelRevision == null
                    || modelRevision.isBlank()) throw new IllegalArgumentException("provider");
            features = Set.copyOf(features);
        }
    }

    record Request(String operationId, Set<Feature> required, Boundary boundary,
                   String extensionOwner) {
        Request {
            if (operationId == null || operationId.isBlank() || required == null || boundary == null) {
                throw new IllegalArgumentException("request");
            }
            required = Set.copyOf(required);
            if (boundary == Boundary.PORTABLE && extensionOwner != null) {
                throw new IllegalArgumentException("portable extension");
            }
            if (boundary == Boundary.PROVIDER_EXTENSION
                    && (extensionOwner == null || extensionOwner.isBlank())) {
                throw new IllegalArgumentException("extension owner");
            }
        }
    }

    static boolean admits(Provider provider, Request request) {
        return provider.workloadIdentity()
                && provider.features().containsAll(request.required())
                && (request.boundary() == Boundary.PORTABLE
                    || provider.id().equals(request.extensionOwner()));
    }

    static Provider choose(Provider primary, Provider alternate, Request request) {
        if (admits(primary, request)) return primary;
        if (admits(alternate, request)) return alternate;
        throw new IllegalStateException("no qualified provider");
    }

    static Next after(Observation observation, boolean replaySafe, boolean providerDedupe) {
        return switch (observation) {
            case NOT_SENT, REJECTED -> Next.RETRY;
            case ACCEPTED_UNKNOWN -> replaySafe && providerDedupe ? Next.RETRY : Next.RECONCILE;
            case COMPLETED -> Next.RETURN;
        };
    }

    static boolean promote(boolean contractPass, double quality, double minQuality,
                           double p95Millis, double maxP95Millis,
                           double costPerSuccess, double maxCostPerSuccess) {
        if (!Double.isFinite(quality) || !Double.isFinite(minQuality)
                || !Double.isFinite(p95Millis) || !Double.isFinite(maxP95Millis)
                || !Double.isFinite(costPerSuccess) || !Double.isFinite(maxCostPerSuccess)
                || minQuality < 0 || maxP95Millis <= 0 || maxCostPerSuccess <= 0) {
            throw new IllegalArgumentException("gate");
        }
        return contractPass && quality >= minQuality
                && p95Millis <= maxP95Millis && costPerSuccess <= maxCostPerSuccess;
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }

    public static void main(String[] args) {
        int scenarios = 0;
        Provider managed = new Provider("managed", EnumSet.of(
                Feature.TEXT, Feature.STREAM, Feature.TOOLS, Feature.STRICT_JSON), true, true, "m-17");
        Provider local = new Provider("local", EnumSet.of(
                Feature.TEXT, Feature.STREAM, Feature.EMBEDDING), true, false, "sha256:abc");
        Provider keyOnly = new Provider("key-only", EnumSet.of(Feature.TEXT), false, true, "m-2");

        Request portable = new Request("op-1", EnumSet.of(Feature.TEXT, Feature.STREAM),
                Boundary.PORTABLE, null);
        Request tools = new Request("op-2", EnumSet.of(Feature.TEXT, Feature.TOOLS),
                Boundary.PORTABLE, null);
        Request extension = new Request("op-3", EnumSet.of(Feature.TEXT),
                Boundary.PROVIDER_EXTENSION, "managed");
        check(admits(managed, portable)); scenarios++;
        check(admits(local, portable)); scenarios++;
        check(!admits(local, tools)); scenarios++;
        check(!admits(keyOnly, new Request("op-4", Set.of(Feature.TEXT), Boundary.PORTABLE, null))); scenarios++;
        check(admits(managed, extension)); scenarios++;
        check(!admits(local, extension)); scenarios++;
        check(choose(managed, local, portable) == managed); scenarios++;
        check(choose(local, managed, tools) == managed); scenarios++;
        try {
            choose(local, keyOnly, tools); throw new AssertionError();
        } catch (IllegalStateException expected) { scenarios++; }
        try {
            new Request("bad", Set.of(Feature.TEXT), Boundary.PORTABLE, "managed");
            throw new AssertionError();
        } catch (IllegalArgumentException expected) { scenarios++; }
        try {
            new Request("bad", Set.of(Feature.TEXT), Boundary.PROVIDER_EXTENSION, null);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) { scenarios++; }

        check(after(Observation.NOT_SENT, false, false) == Next.RETRY); scenarios++;
        check(after(Observation.REJECTED, false, false) == Next.RETRY); scenarios++;
        check(after(Observation.ACCEPTED_UNKNOWN, false, true) == Next.RECONCILE); scenarios++;
        check(after(Observation.ACCEPTED_UNKNOWN, true, false) == Next.RECONCILE); scenarios++;
        check(after(Observation.ACCEPTED_UNKNOWN, true, true) == Next.RETRY); scenarios++;
        check(after(Observation.COMPLETED, false, false) == Next.RETURN); scenarios++;

        check(promote(true, .86, .82, 700, 800, .018, .020)); scenarios++;
        check(!promote(false, .86, .82, 700, 800, .018, .020)); scenarios++;
        check(!promote(true, .80, .82, 700, 800, .018, .020)); scenarios++;
        check(!promote(true, .86, .82, 900, 800, .018, .020)); scenarios++;
        check(!promote(true, .86, .82, 700, 800, .024, .020)); scenarios++;
        try {
            promote(true, Double.NaN, .82, 700, 800, .018, .020); throw new AssertionError();
        } catch (IllegalArgumentException expected) { scenarios++; }

        long state = 0x13ADA7L;
        int promoted = 0;
        for (int i = 0; i < 10_000; i++) {
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            double quality = .70 + ((state >>> 8) % 2500) / 10_000.0;
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            double latency = 300 + ((state >>> 12) % 801);
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            double cost = .005 + ((state >>> 10) % 3000) / 100_000.0;
            boolean actual = promote(true, quality, .82, latency, 800, cost, .020);
            boolean oracle = quality >= .82 && latency <= 800 && cost <= .020;
            check(actual == oracle);
            if (actual) promoted++;
        }

        System.out.println("PARITY|BOUNDARY=PORTABLE_CORE|EXTENSIONS=OWNED|IDENTITY=WORKLOAD"
                + "|UNKNOWN=RECONCILE|MIGRATION=EVALUATED|PROMOTED=" + promoted);
        System.out.println("Java provider portability contracts passed: " + scenarios
                + " scenario groups + 10000 seeded trials");
    }
}
