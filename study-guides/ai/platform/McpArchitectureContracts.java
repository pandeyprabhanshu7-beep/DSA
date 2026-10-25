import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Dependency-free Java 17 contracts for MCP host/client/server ownership.
 *
 * <p>This is an architecture lab, not a replacement for an MCP SDK. It makes
 * protocol-era, capability, authority, correlation, and cancellation
 * invariants executable without opening a network connection.
 */
public final class McpArchitectureContracts {
    private static final String MODERN = "2026-07-28";
    private static final String LEGACY = "2025-11-25";

    enum Transport {
        STDIO,
        STREAMABLE_HTTP
    }

    enum RequestState {
        IN_FLIGHT,
        COMPLETED,
        CANCELLED
    }

    enum Decision {
        ALLOW,
        HOST_DENY,
        CONSENT_REQUIRED,
        SERVER_DENY,
        CAPABILITY_MISSING,
        INVALID_PROTOCOL
    }

    record ModernMetadata(
            String protocolVersion,
            String clientName,
            Set<String> clientCapabilities) {
        ModernMetadata {
            clientCapabilities = Set.copyOf(clientCapabilities);
        }
    }

    record ServerDiscovery(
            Set<String> supportedVersions,
            Set<String> capabilities,
            String diagnosticServerName) {
        ServerDiscovery {
            supportedVersions = Set.copyOf(supportedVersions);
            capabilities = Set.copyOf(capabilities);
        }
    }

    record Proposal(String tool, String resource, boolean mutation) {}

    record HostPolicy(
            Set<String> exposedTools,
            boolean userConfirmedMutation,
            int maximumCalls) {
        HostPolicy {
            exposedTools = Set.copyOf(exposedTools);
            if (maximumCalls < 0) {
                throw new IllegalArgumentException("maximumCalls must be non-negative");
            }
        }
    }

    record ServerPolicy(Set<String> allowedResources) {
        ServerPolicy {
            allowedResources = Set.copyOf(allowedResources);
        }
    }

    record DispatchResult(Decision decision, String reason) {}

    static final class RequestLedger {
        private final Map<Long, RequestState> states = new HashMap<>();

        void begin(long requestId) {
            if (requestId < 0 || states.putIfAbsent(requestId, RequestState.IN_FLIGHT) != null) {
                throw new IllegalArgumentException("request ID must be new and non-negative");
            }
        }

        boolean cancel(long requestId, Transport transport) {
            requireTransport(transport);
            return states.replace(requestId, RequestState.IN_FLIGHT, RequestState.CANCELLED);
        }

        boolean complete(long requestId) {
            return states.replace(requestId, RequestState.IN_FLIGHT, RequestState.COMPLETED);
        }

        RequestState state(long requestId) {
            RequestState state = states.get(requestId);
            if (state == null) {
                throw new IllegalArgumentException("unknown request ID");
            }
            return state;
        }

        private static void requireTransport(Transport transport) {
            if (transport == null) {
                throw new IllegalArgumentException("transport is required");
            }
            // STREAMABLE_HTTP closes only this request's response stream.
            // STDIO sends notifications/cancelled for this request ID.
        }
    }

    static boolean validModernRequest(
            ModernMetadata body,
            String mirroredHttpVersion,
            Transport transport) {
        if (body == null
                || !MODERN.equals(body.protocolVersion())
                || body.clientName() == null
                || body.clientName().isBlank()
                || body.clientCapabilities() == null) {
            return false;
        }
        if (transport == Transport.STREAMABLE_HTTP) {
            return MODERN.equals(mirroredHttpVersion);
        }
        return transport == Transport.STDIO && mirroredHttpVersion == null;
    }

    static boolean canUseCapability(ServerDiscovery discovery, String capability) {
        return discovery != null
                && discovery.supportedVersions().contains(MODERN)
                && discovery.capabilities().contains(capability);
    }

    static DispatchResult dispatch(
            Proposal modelProposal,
            HostPolicy host,
            ServerPolicy server,
            ServerDiscovery discovery,
            ModernMetadata metadata,
            String mirroredHttpVersion,
            Transport transport,
            int callsAlreadyStarted) {
        if (!validModernRequest(metadata, mirroredHttpVersion, transport)) {
            return new DispatchResult(Decision.INVALID_PROTOCOL, "missing or inconsistent request metadata");
        }
        if (!canUseCapability(discovery, "tools")) {
            return new DispatchResult(Decision.CAPABILITY_MISSING, "server did not advertise tools");
        }
        if (modelProposal == null
                || host == null
                || !host.exposedTools().contains(modelProposal.tool())
                || callsAlreadyStarted >= host.maximumCalls()) {
            return new DispatchResult(Decision.HOST_DENY, "proposal is outside host policy");
        }
        if (modelProposal.mutation() && !host.userConfirmedMutation()) {
            return new DispatchResult(Decision.CONSENT_REQUIRED, "mutation lacks host confirmation");
        }
        if (server == null || !server.allowedResources().contains(modelProposal.resource())) {
            return new DispatchResult(Decision.SERVER_DENY, "server rejected the concrete resource");
        }
        return new DispatchResult(Decision.ALLOW, "host admitted and server authorized");
    }

    static boolean validLegacyLifecycle(
            String requestedVersion,
            boolean initializeResponseReceived,
            boolean initializedNotificationSent) {
        return LEGACY.equals(requestedVersion)
                && initializeResponseReceived
                && initializedNotificationSent;
    }

    static boolean validStdioFrame(String frame, boolean writtenToStdout) {
        if (!writtenToStdout || frame == null || frame.isBlank()) {
            return false;
        }
        String withoutDelimiter = frame.endsWith("\n")
                ? frame.substring(0, frame.length() - 1)
                : frame;
        return !withoutDelimiter.contains("\n")
                && withoutDelimiter.startsWith("{")
                && withoutDelimiter.endsWith("}");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int explicitScenarios() {
        int scenarios = 0;
        ModernMetadata modern = new ModernMetadata(MODERN, "atlas", Set.of());
        ServerDiscovery tools = new ServerDiscovery(
                Set.of(MODERN), Set.of("tools", "resources"), "self-reported-name");
        HostPolicy readHost = new HostPolicy(Set.of("deployment_status"), false, 2);
        ServerPolicy payments = new ServerPolicy(Set.of("payments"));

        expect(validModernRequest(modern, MODERN, Transport.STREAMABLE_HTTP), "HTTP metadata");
        scenarios++;
        expect(validModernRequest(modern, null, Transport.STDIO), "stdio metadata");
        scenarios++;
        expect(!validModernRequest(modern, LEGACY, Transport.STREAMABLE_HTTP), "header mismatch");
        scenarios++;
        expect(!validModernRequest(
                new ModernMetadata(LEGACY, "atlas", Set.of()), null, Transport.STDIO),
                "legacy is not a modern envelope");
        scenarios++;
        expect(canUseCapability(tools, "tools"), "tools advertised");
        scenarios++;
        expect(!canUseCapability(tools, "prompts"), "prompts absent");
        scenarios++;

        Proposal read = new Proposal("deployment_status", "payments", false);
        expect(dispatch(read, readHost, payments, tools, modern, MODERN,
                Transport.STREAMABLE_HTTP, 0).decision() == Decision.ALLOW, "allowed read");
        scenarios++;
        expect(dispatch(new Proposal("shell", "payments", false), readHost, payments, tools,
                modern, MODERN, Transport.STREAMABLE_HTTP, 0).decision() == Decision.HOST_DENY,
                "hidden tool");
        scenarios++;
        expect(dispatch(new Proposal("deployment_status", "other-team", false), readHost, payments,
                tools, modern, MODERN, Transport.STREAMABLE_HTTP, 0).decision()
                == Decision.SERVER_DENY, "resource authorization");
        scenarios++;
        expect(dispatch(new Proposal("deployment_status", "payments", true), readHost, payments,
                tools, modern, MODERN, Transport.STREAMABLE_HTTP, 0).decision()
                == Decision.CONSENT_REQUIRED, "mutation consent");
        scenarios++;
        expect(dispatch(read, readHost, payments, tools, modern, MODERN,
                Transport.STREAMABLE_HTTP, 2).decision() == Decision.HOST_DENY, "call budget");
        scenarios++;
        ServerDiscovery resourcesOnly = new ServerDiscovery(
                Set.of(MODERN), Set.of("resources"), "diagnostic");
        expect(dispatch(read, readHost, payments, resourcesOnly, modern, MODERN,
                Transport.STREAMABLE_HTTP, 0).decision() == Decision.CAPABILITY_MISSING,
                "capability gate");
        scenarios++;
        expect(dispatch(read, readHost, payments, tools, modern, LEGACY,
                Transport.STREAMABLE_HTTP, 0).decision() == Decision.INVALID_PROTOCOL,
                "mismatched metadata");
        scenarios++;

        HostPolicy confirmed = new HostPolicy(Set.of("deploy"), true, 1);
        expect(dispatch(new Proposal("deploy", "payments", true), confirmed, payments, tools,
                modern, MODERN, Transport.STREAMABLE_HTTP, 0).decision() == Decision.ALLOW,
                "confirmed mutation still server-authorized");
        scenarios++;

        expect(validLegacyLifecycle(LEGACY, true, true), "complete legacy handshake");
        scenarios++;
        expect(!validLegacyLifecycle(LEGACY, true, false), "legacy notification missing");
        scenarios++;
        expect(!validLegacyLifecycle(MODERN, true, true), "eras stay separate");
        scenarios++;

        expect(validStdioFrame("{\"jsonrpc\":\"2.0\"}\n", true), "valid stdio frame");
        scenarios++;
        expect(!validStdioFrame("log line\n{\"jsonrpc\":\"2.0\"}\n", true), "stdout pollution");
        scenarios++;
        expect(!validStdioFrame("diagnostic", false), "stderr is not a protocol frame");
        scenarios++;

        RequestLedger ledger = new RequestLedger();
        ledger.begin(41);
        expect(ledger.cancel(41, Transport.STREAMABLE_HTTP), "cancel active HTTP request");
        scenarios++;
        expect(!ledger.complete(41), "discard late result");
        scenarios++;
        expect(ledger.state(41) == RequestState.CANCELLED, "terminal cancellation");
        scenarios++;
        expect(!ledger.cancel(41, Transport.STDIO), "no second terminal transition");
        scenarios++;
        return scenarios;
    }

    private static int seededTrials() {
        Random random = new Random(0x4d435032303236L);
        ModernMetadata metadata = new ModernMetadata(MODERN, "seeded-client", Set.of());
        ServerDiscovery discovery = new ServerDiscovery(
                Set.of(MODERN), Set.of("tools"), "untrusted-diagnostic");
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            boolean exposed = random.nextBoolean();
            boolean confirmed = random.nextBoolean();
            boolean serverAllowed = random.nextBoolean();
            boolean mutation = random.nextBoolean();
            boolean metadataMatches = random.nextBoolean();
            HostPolicy host = new HostPolicy(
                    exposed ? Set.of("operation") : Set.of(), confirmed, 1);
            ServerPolicy server = new ServerPolicy(
                    serverAllowed ? Set.of("resource") : Set.of());
            DispatchResult result = dispatch(
                    new Proposal("operation", "resource", mutation),
                    host,
                    server,
                    discovery,
                    metadata,
                    metadataMatches ? MODERN : LEGACY,
                    Transport.STREAMABLE_HTTP,
                    0);
            boolean shouldAllow = exposed
                    && (!mutation || confirmed)
                    && serverAllowed
                    && metadataMatches;
            expect((result.decision() == Decision.ALLOW) == shouldAllow,
                    "seeded authority composition at trial " + i);

            RequestLedger ledger = new RequestLedger();
            long requestId = 1_000_000L + i;
            ledger.begin(requestId);
            boolean cancel = random.nextBoolean();
            if (cancel) {
                expect(ledger.cancel(requestId, random.nextBoolean()
                        ? Transport.STDIO : Transport.STREAMABLE_HTTP), "seeded cancel");
                expect(!ledger.complete(requestId), "late completion after cancel");
            } else {
                expect(ledger.complete(requestId), "seeded completion");
                expect(!ledger.cancel(requestId, Transport.STDIO), "cancel after completion");
            }
        }
        return trials;
    }

    public static void main(String[] args) {
        int scenarios = explicitScenarios();
        int trials = seededTrials();
        expect(scenarios == 24, "explicit scenario count");
        expect(trials == 10_000, "seeded trial count");
        System.out.println("SCENARIOS=" + scenarios + "|SEEDED_TRIALS=" + trials);
        System.out.println(
                "PARITY|ERA=MODERN|TOOLS=YES|MODEL=PROPOSE_ONLY|HOST=ALLOW"
                        + "|SERVER=DENY|CANCEL=41|LATE=DISCARD|TRANSPORT=HTTP");
    }
}
