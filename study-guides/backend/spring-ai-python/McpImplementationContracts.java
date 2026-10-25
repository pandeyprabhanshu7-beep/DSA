import java.util.Map;
import java.util.Set;

/** Dependency-free contracts for choosing and enforcing local callbacks, MCP tools and resources. */
public final class McpImplementationContracts {
    private static final String MCP_AUDIENCE = "https://mcp.atlas.example";
    private static final long NOW = 1_800_000_000L;

    enum Exposure { LOCAL_CALLBACK, MCP_TOOL, MCP_RESOURCE }
    enum Transport { STDIO, STREAMABLE_HTTP }
    enum Decision {
        ALLOW, BAD_SCHEMA, UNKNOWN_SERVICE, UNAUTHENTICATED, BAD_AUDIENCE,
        EXPIRED, MISSING_SCOPE, TENANT_DENIED, UNSAFE_STDIO
    }

    record Caller(String tenant, Set<String> scopes, String audience, long expiresAt) {}
    record ToolHints(boolean readOnly, boolean destructive, boolean idempotent, boolean openWorld) {}
    record ToolSpec(String name, Set<String> requiredFields, ToolHints hints) {}
    record Status(String service, int ready, int desired, String source, boolean live) {}
    record Outcome(Decision decision, Status value) {}

    static Exposure chooseExposure(int consumingApplications, boolean standardDiscovery,
                                   boolean addressableDocument) {
        if (consumingApplications < 1) throw new IllegalArgumentException("consumer count");
        if (addressableDocument) return Exposure.MCP_RESOURCE;
        return consumingApplications == 1 && !standardDiscovery
                ? Exposure.LOCAL_CALLBACK : Exposure.MCP_TOOL;
    }

    static Outcome deploymentStatus(ToolSpec spec, Map<String, Object> arguments,
                                    Caller caller, Map<String, String> serviceTenant) {
        if (!spec.name().equals("deployment_status")
                || !arguments.keySet().equals(spec.requiredFields())
                || !(arguments.get("service") instanceof String service)) {
            return new Outcome(Decision.BAD_SCHEMA, null);
        }
        if (!serviceTenant.containsKey(service)) return new Outcome(Decision.UNKNOWN_SERVICE, null);
        Decision auth = authorize(caller, "status:read", serviceTenant.get(service));
        if (auth != Decision.ALLOW) return new Outcome(auth, null);
        return new Outcome(Decision.ALLOW,
                new Status(service, 3, 3, "synthetic-training-fixture", false));
    }

    static Decision readRunbook(String uri, Caller caller, Map<String, String> resourceTenant) {
        if (!uri.matches("runbook://[a-z0-9-]+/[1-9][0-9]*")) return Decision.BAD_SCHEMA;
        String tenant = resourceTenant.get(uri);
        if (tenant == null) return Decision.TENANT_DENIED;
        return authorize(caller, "runbook:read", tenant);
    }

    static Decision transportAdmission(Transport transport, boolean hostSpawned,
                                       boolean logsOnlyToStderr, Caller caller) {
        if (transport == Transport.STDIO) {
            return hostSpawned && logsOnlyToStderr ? Decision.ALLOW : Decision.UNSAFE_STDIO;
        }
        if (caller == null) return Decision.UNAUTHENTICATED;
        if (!MCP_AUDIENCE.equals(caller.audience())) return Decision.BAD_AUDIENCE;
        if (caller.expiresAt() <= NOW) return Decision.EXPIRED;
        return Decision.ALLOW;
    }

    static boolean hintsGrantPermission(ToolHints ignored) {
        return false;
    }

    private static Decision authorize(Caller caller, String scope, String resourceTenant) {
        if (caller == null) return Decision.UNAUTHENTICATED;
        if (!MCP_AUDIENCE.equals(caller.audience())) return Decision.BAD_AUDIENCE;
        if (caller.expiresAt() <= NOW) return Decision.EXPIRED;
        if (!caller.scopes().contains(scope)) return Decision.MISSING_SCOPE;
        if (!caller.tenant().equals(resourceTenant)) return Decision.TENANT_DENIED;
        return Decision.ALLOW;
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) {
        int scenarios = 0;
        ToolHints readHints = new ToolHints(true, false, true, false);
        ToolSpec spec = new ToolSpec("deployment_status", Set.of("service"), readHints);
        Map<String, String> serviceTenant = Map.of("payments", "tenant-a", "catalog", "tenant-a");
        Map<String, String> resources = Map.of("runbook://payments/7", "tenant-a");
        Caller reader = new Caller("tenant-a", Set.of("status:read", "runbook:read"),
                MCP_AUDIENCE, NOW + 300);

        check(chooseExposure(1, false, false) == Exposure.LOCAL_CALLBACK); scenarios++;
        check(chooseExposure(2, false, false) == Exposure.MCP_TOOL); scenarios++;
        check(chooseExposure(1, true, false) == Exposure.MCP_TOOL); scenarios++;
        check(chooseExposure(1, false, true) == Exposure.MCP_RESOURCE); scenarios++;
        check(chooseExposure(3, true, true) == Exposure.MCP_RESOURCE); scenarios++;
        try { chooseExposure(0, false, false); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        Outcome ok = deploymentStatus(spec, Map.of("service", "payments"), reader, serviceTenant);
        check(ok.decision() == Decision.ALLOW && ok.value().service().equals("payments")); scenarios++;
        check(!ok.value().live() && ok.value().source().equals("synthetic-training-fixture")); scenarios++;
        check(deploymentStatus(spec, Map.of(), reader, serviceTenant).decision() == Decision.BAD_SCHEMA); scenarios++;
        check(deploymentStatus(spec, Map.of("service", "payments", "extra", "x"), reader, serviceTenant).decision() == Decision.BAD_SCHEMA); scenarios++;
        check(deploymentStatus(spec, Map.of("service", 7), reader, serviceTenant).decision() == Decision.BAD_SCHEMA); scenarios++;
        check(deploymentStatus(spec, Map.of("service", "orders"), reader, serviceTenant).decision() == Decision.UNKNOWN_SERVICE); scenarios++;
        check(deploymentStatus(new ToolSpec("other", Set.of("service"), readHints), Map.of("service", "payments"), reader, serviceTenant).decision() == Decision.BAD_SCHEMA); scenarios++;
        check(deploymentStatus(spec, Map.of("service", "payments"), null, serviceTenant).decision() == Decision.UNAUTHENTICATED); scenarios++;
        check(deploymentStatus(spec, Map.of("service", "payments"), new Caller("tenant-a", Set.of("status:read"), "other", NOW + 1), serviceTenant).decision() == Decision.BAD_AUDIENCE); scenarios++;
        check(deploymentStatus(spec, Map.of("service", "payments"), new Caller("tenant-a", Set.of("status:read"), MCP_AUDIENCE, NOW), serviceTenant).decision() == Decision.EXPIRED); scenarios++;
        check(deploymentStatus(spec, Map.of("service", "payments"), new Caller("tenant-a", Set.of(), MCP_AUDIENCE, NOW + 1), serviceTenant).decision() == Decision.MISSING_SCOPE); scenarios++;
        check(deploymentStatus(spec, Map.of("service", "payments"), new Caller("tenant-b", Set.of("status:read"), MCP_AUDIENCE, NOW + 1), serviceTenant).decision() == Decision.TENANT_DENIED); scenarios++;

        check(readRunbook("runbook://payments/7", reader, resources) == Decision.ALLOW); scenarios++;
        check(readRunbook("runbook://payments/latest", reader, resources) == Decision.BAD_SCHEMA); scenarios++;
        check(readRunbook("https://payments/7", reader, resources) == Decision.BAD_SCHEMA); scenarios++;
        check(readRunbook("runbook://catalog/7", reader, resources) == Decision.TENANT_DENIED); scenarios++;
        check(readRunbook("runbook://payments/7", new Caller("tenant-b", Set.of("runbook:read"), MCP_AUDIENCE, NOW + 1), resources) == Decision.TENANT_DENIED); scenarios++;
        check(readRunbook("runbook://payments/7", new Caller("tenant-a", Set.of(), MCP_AUDIENCE, NOW + 1), resources) == Decision.MISSING_SCOPE); scenarios++;

        check(transportAdmission(Transport.STDIO, true, true, null) == Decision.ALLOW); scenarios++;
        check(transportAdmission(Transport.STDIO, false, true, null) == Decision.UNSAFE_STDIO); scenarios++;
        check(transportAdmission(Transport.STDIO, true, false, null) == Decision.UNSAFE_STDIO); scenarios++;
        check(transportAdmission(Transport.STREAMABLE_HTTP, false, true, null) == Decision.UNAUTHENTICATED); scenarios++;
        check(transportAdmission(Transport.STREAMABLE_HTTP, false, true, reader) == Decision.ALLOW); scenarios++;
        check(transportAdmission(Transport.STREAMABLE_HTTP, false, true, new Caller("tenant-a", Set.of(), "other", NOW + 1)) == Decision.BAD_AUDIENCE); scenarios++;
        check(!hintsGrantPermission(readHints)); scenarios++;
        check(!hintsGrantPermission(new ToolHints(false, true, false, true))); scenarios++;

        long state = 0xC0FFEE;
        int allowed = 0;
        for (int i = 0; i < 10_000; i++) {
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            String service = ((state >>> 8) & 1L) == 1L ? "payments" : "orders";
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            String tenant = ((state >>> 16) & 1L) == 1L ? "tenant-a" : "tenant-b";
            Caller candidate = new Caller(tenant, Set.of("status:read"), MCP_AUDIENCE, NOW + 1);
            Outcome outcome = deploymentStatus(spec, Map.of("service", service), candidate, serviceTenant);
            boolean expected = service.equals("payments") && tenant.equals("tenant-a");
            check((outcome.decision() == Decision.ALLOW) == expected);
            if (expected) allowed++;
        }

        System.out.println("PARITY|LOCAL=CALLBACK|REMOTE=MCP_TOOL|RESOURCE=runbook://payments/7"
                + "|FIXTURE=NONLIVE|HTTP=AUTH_REQUIRED|HINT=NONAUTH|ALLOWED=" + allowed);
        System.out.println("Java MCP implementation contracts passed: " + scenarios
                + " scenario groups + 10000 seeded trials");
    }
}
