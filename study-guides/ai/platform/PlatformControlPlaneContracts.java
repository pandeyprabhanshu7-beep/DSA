import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Dependency-free contracts for Chapter 14: control/data planes, route
 * admission, verified tenant scope, bounded snapshot use and cache identity.
 *
 * Run with: java -ea PlatformControlPlaneContracts.java
 */
public final class PlatformControlPlaneContracts {
    enum DataPolicy { PUBLIC, INTERNAL, RESTRICTED }
    enum Capability { TEXT_GENERATION, JSON_OUTPUT, TOOL_CALLING }

    record RouteSnapshot(
            String routeId,
            long revision,
            Instant issuedAt,
            Instant validUntil,
            boolean signatureVerified,
            DataPolicy dataPolicy,
            Set<String> allowedRegions,
            Set<Capability> capabilities,
            int maxInputTokens,
            int maxOutputTokens,
            int maxToolCalls,
            Duration deadline,
            String fallbackRoute,
            String promptVersion,
            String indexGeneration) {
        RouteSnapshot {
            Objects.requireNonNull(routeId);
            Objects.requireNonNull(issuedAt);
            Objects.requireNonNull(validUntil);
            Objects.requireNonNull(dataPolicy);
            allowedRegions = Set.copyOf(allowedRegions);
            capabilities = Set.copyOf(capabilities);
            Objects.requireNonNull(deadline);
            Objects.requireNonNull(promptVersion);
            Objects.requireNonNull(indexGeneration);
            if (routeId.isBlank() || revision <= 0 || !validUntil.isAfter(issuedAt)
                    || allowedRegions.isEmpty() || capabilities.isEmpty()
                    || maxInputTokens <= 0 || maxOutputTokens <= 0 || maxToolCalls < 0
                    || deadline.isNegative() || deadline.isZero()
                    || promptVersion.isBlank() || indexGeneration.isBlank()) {
                throw new IllegalArgumentException("invalid route snapshot");
            }
            if (fallbackRoute != null && fallbackRoute.isBlank()) {
                throw new IllegalArgumentException("fallback is null or nonblank");
            }
        }
    }

    record VerifiedScope(String subject, Set<String> memberships,
                         String selectedTenant, long authorizationEpoch) {
        VerifiedScope {
            Objects.requireNonNull(subject);
            memberships = Set.copyOf(memberships);
            Objects.requireNonNull(selectedTenant);
            if (subject.isBlank() || selectedTenant.isBlank() || authorizationEpoch < 0
                    || !memberships.contains(selectedTenant)) {
                throw new IllegalArgumentException("tenant must be a verified membership");
            }
        }
    }

    record RequestContract(DataPolicy dataPolicy, String region,
                           Set<Capability> requires, int inputTokens,
                           int outputTokens, int toolCalls, Duration deadline) {
        RequestContract {
            Objects.requireNonNull(dataPolicy);
            Objects.requireNonNull(region);
            requires = Set.copyOf(requires);
            Objects.requireNonNull(deadline);
            if (region.isBlank() || requires.isEmpty() || inputTokens < 0
                    || outputTokens < 0 || toolCalls < 0
                    || deadline.isNegative() || deadline.isZero()) {
                throw new IllegalArgumentException("invalid request contract");
            }
        }
    }

    record CacheKey(String subject, String tenant, long authorizationEpoch,
                    String routeId, long routeRevision,
                    String promptVersion, String indexGeneration) {
        CacheKey {
            Objects.requireNonNull(subject);
            Objects.requireNonNull(tenant);
            Objects.requireNonNull(routeId);
            Objects.requireNonNull(promptVersion);
            Objects.requireNonNull(indexGeneration);
            if (subject.isBlank() || tenant.isBlank() || authorizationEpoch < 0
                    || routeId.isBlank() || routeRevision <= 0
                    || promptVersion.isBlank() || indexGeneration.isBlank()) {
                throw new IllegalArgumentException("incomplete cache identity");
            }
        }
    }

    static boolean snapshotUsable(RouteSnapshot route, Instant now,
                                  Duration maximumOfflineAge,
                                  long emergencyRevokedThrough) {
        Objects.requireNonNull(route);
        Objects.requireNonNull(now);
        Objects.requireNonNull(maximumOfflineAge);
        if (maximumOfflineAge.isNegative() || emergencyRevokedThrough < 0) {
            throw new IllegalArgumentException("invalid freshness policy");
        }
        Duration age = Duration.between(route.issuedAt(), now);
        return route.signatureVerified()
                && !now.isBefore(route.issuedAt())
                && !now.isAfter(route.validUntil())
                && age.compareTo(maximumOfflineAge) <= 0
                && route.revision() > emergencyRevokedThrough;
    }

    static boolean compatible(RouteSnapshot route, RequestContract request) {
        Objects.requireNonNull(route);
        Objects.requireNonNull(request);
        return route.dataPolicy().ordinal() >= request.dataPolicy().ordinal()
                && route.allowedRegions().contains(request.region())
                && route.capabilities().containsAll(request.requires())
                && request.inputTokens() <= route.maxInputTokens()
                && request.outputTokens() <= route.maxOutputTokens()
                && request.toolCalls() <= route.maxToolCalls()
                && request.deadline().compareTo(route.deadline()) <= 0;
    }

    static Optional<String> chooseRoute(RouteSnapshot primary,
                                        RouteSnapshot fallback,
                                        RequestContract request,
                                        Instant now,
                                        Duration maximumOfflineAge,
                                        long emergencyRevokedThrough) {
        if (snapshotUsable(primary, now, maximumOfflineAge, emergencyRevokedThrough)
                && compatible(primary, request)) {
            return Optional.of(primary.routeId());
        }
        if (primary.fallbackRoute() == null || fallback == null
                || !primary.fallbackRoute().equals(fallback.routeId())) {
            return Optional.empty();
        }
        return snapshotUsable(fallback, now, maximumOfflineAge, emergencyRevokedThrough)
                && compatible(fallback, request)
                ? Optional.of(fallback.routeId()) : Optional.empty();
    }

    static CacheKey cacheKey(VerifiedScope scope, RouteSnapshot route) {
        return new CacheKey(scope.subject(), scope.selectedTenant(),
                scope.authorizationEpoch(), route.routeId(), route.revision(),
                route.promptVersion(), route.indexGeneration());
    }

    static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    static RouteSnapshot route(String id, long revision, Instant issued,
                               boolean signed, DataPolicy policy,
                               Set<String> regions, Set<Capability> capabilities,
                               String fallback) {
        return new RouteSnapshot(id, revision, issued, issued.plusSeconds(900), signed,
                policy, regions, capabilities, 12_000, 1_000, 2,
                Duration.ofSeconds(12), fallback, "rollback-v12", "runbooks-g43");
    }

    static RequestContract request(DataPolicy policy, String region,
                                   Set<Capability> capabilities,
                                   int input, int output, int tools, int seconds) {
        return new RequestContract(policy, region, capabilities, input, output,
                tools, Duration.ofSeconds(seconds));
    }

    public static void main(String[] args) {
        int scenarios = 0;
        Instant issued = Instant.parse("2026-10-26T08:00:00Z");
        Instant now = issued.plusSeconds(120);
        Set<Capability> textJson = Set.of(Capability.TEXT_GENERATION, Capability.JSON_OUTPUT);
        RouteSnapshot primary = route("ops-primary", 17, issued, true,
                DataPolicy.RESTRICTED, Set.of("us-east-1"), textJson, "ops-fallback");
        RouteSnapshot fallback = route("ops-fallback", 18, issued, true,
                DataPolicy.RESTRICTED, Set.of("us-east-1"), textJson, null);
        RequestContract normal = request(DataPolicy.RESTRICTED, "us-east-1",
                textJson, 8_000, 700, 0, 10);

        expect(snapshotUsable(primary, now, Duration.ofMinutes(5), 16), "valid offline snapshot"); scenarios++;
        expect(!snapshotUsable(primary, now, Duration.ofMinutes(5), 17), "emergency revocation"); scenarios++;
        expect(!snapshotUsable(primary, issued.minusSeconds(1), Duration.ofMinutes(5), 0), "future snapshot"); scenarios++;
        expect(!snapshotUsable(primary, issued.plusSeconds(301), Duration.ofMinutes(5), 0), "offline age"); scenarios++;
        expect(!snapshotUsable(primary, issued.plusSeconds(901), Duration.ofHours(1), 0), "validity expiry"); scenarios++;
        RouteSnapshot unsigned = route("unsigned", 19, issued, false,
                DataPolicy.RESTRICTED, Set.of("us-east-1"), textJson, null);
        expect(!snapshotUsable(unsigned, now, Duration.ofMinutes(5), 0), "signature required"); scenarios++;

        expect(compatible(primary, normal), "compatible primary"); scenarios++;
        expect(!compatible(primary, request(DataPolicy.RESTRICTED, "eu-west-1", textJson, 8_000, 700, 0, 10)), "region blocks"); scenarios++;
        expect(!compatible(primary, request(DataPolicy.RESTRICTED, "us-east-1", Set.of(Capability.TOOL_CALLING), 8_000, 700, 1, 10)), "capability blocks"); scenarios++;
        expect(!compatible(primary, request(DataPolicy.RESTRICTED, "us-east-1", textJson, 12_001, 700, 0, 10)), "input budget blocks"); scenarios++;
        expect(!compatible(primary, request(DataPolicy.RESTRICTED, "us-east-1", textJson, 8_000, 1_001, 0, 10)), "output budget blocks"); scenarios++;
        expect(!compatible(primary, request(DataPolicy.RESTRICTED, "us-east-1", textJson, 8_000, 700, 3, 10)), "tool budget blocks"); scenarios++;
        expect(!compatible(primary, request(DataPolicy.RESTRICTED, "us-east-1", textJson, 8_000, 700, 0, 13)), "deadline blocks"); scenarios++;
        RouteSnapshot internal = route("internal", 19, issued, true,
                DataPolicy.INTERNAL, Set.of("us-east-1"), textJson, null);
        expect(!compatible(internal, normal), "data policy blocks"); scenarios++;

        expect(chooseRoute(primary, fallback, normal, now, Duration.ofMinutes(5), 0).orElseThrow().equals("ops-primary"), "choose primary"); scenarios++;
        expect(chooseRoute(primary, fallback, normal, now, Duration.ofMinutes(5), 17).orElseThrow().equals("ops-fallback"), "revoked primary uses approved fallback"); scenarios++;
        RouteSnapshot wrongRegionFallback = route("ops-fallback", 18, issued, true,
                DataPolicy.RESTRICTED, Set.of("eu-west-1"), textJson, null);
        expect(chooseRoute(primary, wrongRegionFallback, normal, now, Duration.ofMinutes(5), 17).isEmpty(), "incompatible fallback fails closed"); scenarios++;
        expect(chooseRoute(primary, null, normal, now, Duration.ofMinutes(5), 17).isEmpty(), "missing fallback fails closed"); scenarios++;
        RouteSnapshot noFallback = route("solo", 17, issued, true,
                DataPolicy.RESTRICTED, Set.of("us-east-1"), textJson, null);
        expect(chooseRoute(noFallback, fallback, normal, now, Duration.ofMinutes(5), 17).isEmpty(), "unlisted fallback rejected"); scenarios++;

        VerifiedScope scope = new VerifiedScope("user-7", Set.of("payments", "platform"), "payments", 42);
        expect(scope.selectedTenant().equals("payments"), "verified tenant selected"); scenarios++;
        try { new VerifiedScope("user-7", Set.of("platform"), "payments", 42); throw new AssertionError("injected tenant accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { new VerifiedScope("user-7", Set.of("payments"), "payments", -1); throw new AssertionError("negative auth epoch accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }

        CacheKey key = cacheKey(scope, primary);
        expect(key.tenant().equals("payments"), "cache tenant"); scenarios++;
        expect(key.authorizationEpoch() == 42, "cache auth epoch"); scenarios++;
        expect(key.routeRevision() == 17, "cache route revision"); scenarios++;
        expect(!key.equals(new CacheKey("user-7", "payments", 43, "ops-primary", 17, "rollback-v12", "runbooks-g43")), "revocation separates cache"); scenarios++;
        expect(!key.equals(new CacheKey("user-7", "platform", 42, "ops-primary", 17, "rollback-v12", "runbooks-g43")), "tenant separates cache"); scenarios++;
        expect(!key.equals(new CacheKey("user-7", "payments", 42, "ops-primary", 18, "rollback-v12", "runbooks-g43")), "route revision separates cache"); scenarios++;
        expect(!key.equals(new CacheKey("user-7", "payments", 42, "ops-primary", 17, "rollback-v13", "runbooks-g43")), "prompt separates cache"); scenarios++;
        expect(!key.equals(new CacheKey("user-7", "payments", 42, "ops-primary", 17, "rollback-v12", "runbooks-g44")), "index separates cache"); scenarios++;

        try { route("bad", 0, issued, true, DataPolicy.INTERNAL, Set.of("us-east-1"), textJson, null); throw new AssertionError("zero revision accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { request(DataPolicy.INTERNAL, "us-east-1", textJson, -1, 1, 0, 1); throw new AssertionError("negative tokens accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { snapshotUsable(primary, now, Duration.ofSeconds(-1), 0); throw new AssertionError("negative age accepted"); }
        catch (IllegalArgumentException expected) { scenarios++; }

        Random random = new Random(0x14C0FFEE);
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            int input = random.nextInt(15_000);
            int output = random.nextInt(1_500);
            int tools = random.nextInt(5);
            int seconds = random.nextInt(20) + 1;
            String region = random.nextBoolean() ? "us-east-1" : "eu-west-1";
            DataPolicy policy = random.nextBoolean() ? DataPolicy.RESTRICTED : DataPolicy.INTERNAL;
            Set<Capability> required = random.nextBoolean() ? textJson : Set.of(Capability.TOOL_CALLING);
            RequestContract generated = request(policy, region, required, input, output, tools, seconds);
            boolean oracle = policy.ordinal() <= DataPolicy.RESTRICTED.ordinal()
                    && region.equals("us-east-1") && textJson.containsAll(required)
                    && input <= 12_000 && output <= 1_000 && tools <= 2 && seconds <= 12;
            expect(compatible(primary, generated) == oracle, "route oracle " + i);
        }

        expect(scenarios == 33, "scenario count");
        System.out.println("SCENARIOS=33|TRIALS=10000|PLANES=SNAPSHOT_BOUNDED|ROUTING=CAPABILITY_GATED|TENANCY=VERIFIED_SCOPE|CACHE=AUTH_AND_RELEASE_FENCED");
    }
}
