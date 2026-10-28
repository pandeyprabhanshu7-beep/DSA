import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Dependency-free Java 17 contracts for private model invocation across provider boundaries. */
public final class PrivateModelRouteContracts {
    private PrivateModelRouteContracts() {}

    enum Provider { AWS_BEDROCK, AZURE_FOUNDRY, GCP_VERTEX, INDEPENDENT_VLLM }
    enum Decision {
        ALLOW,
        DENY_IDENTITY,
        DENY_AUDIENCE,
        DENY_DNS,
        DENY_TLS,
        DENY_NETWORK,
        DENY_ENDPOINT_POLICY,
        DENY_DATA_ACTION,
        DENY_ROLE_SEPARATION,
        DENY_REGION,
        DENY_CAPABILITY
    }

    record Route(String id, Provider provider, String model, String region,
                 String runtimePrincipal, String deploymentPrincipal,
                 String dataAudience, String operation) {
        Route {
            required(id, "id");
            Objects.requireNonNull(provider, "provider");
            required(model, "model");
            required(region, "region");
            required(runtimePrincipal, "runtimePrincipal");
            required(deploymentPrincipal, "deploymentPrincipal");
            required(dataAudience, "dataAudience");
            required(operation, "operation");
        }
    }

    record Evidence(boolean temporaryWorkloadIdentity,
                    String tokenAudience,
                    boolean privateDns,
                    boolean tlsHostnameValid,
                    boolean privateNetworkPath,
                    boolean endpointPolicyAllows,
                    boolean dataActionAllows,
                    boolean separateRuntimeAndDeploymentRoles,
                    boolean approvedRegion,
                    boolean requiredCapability) {
        Evidence {
            required(tokenAudience, "tokenAudience");
        }
    }

    record OutcomeCost(long providerMicros, long platformMicros,
                       long operatorMicros, long networkMicros,
                       long observabilityMicros, long reviewAndFailureMicros,
                       long acceptedOutcomes) {
        OutcomeCost {
            if (providerMicros < 0 || platformMicros < 0 || operatorMicros < 0
                    || networkMicros < 0 || observabilityMicros < 0
                    || reviewAndFailureMicros < 0 || acceptedOutcomes <= 0) {
                throw new IllegalArgumentException("invalid cost input");
            }
        }

        long totalMicros() {
            long total = Math.addExact(providerMicros, platformMicros);
            total = Math.addExact(total, operatorMicros);
            total = Math.addExact(total, networkMicros);
            total = Math.addExact(total, observabilityMicros);
            return Math.addExact(total, reviewAndFailureMicros);
        }

        long microsPerAcceptedOutcome() {
            return totalMicros() / acceptedOutcomes;
        }
    }

    record FailoverPolicy(List<Provider> admittedProviders,
                          List<String> admittedRegions,
                          List<String> admittedCapabilities,
                          long maxMicrosPerAcceptedOutcome) {
        FailoverPolicy {
            admittedProviders = List.copyOf(admittedProviders);
            admittedRegions = List.copyOf(admittedRegions);
            admittedCapabilities = List.copyOf(admittedCapabilities);
            if (maxMicrosPerAcceptedOutcome < 0) {
                throw new IllegalArgumentException("negative budget");
            }
        }
    }

    static Decision admit(Route route, Evidence evidence) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.temporaryWorkloadIdentity()) return Decision.DENY_IDENTITY;
        if (!route.dataAudience().equals(evidence.tokenAudience())) return Decision.DENY_AUDIENCE;
        if (!evidence.privateDns()) return Decision.DENY_DNS;
        if (!evidence.tlsHostnameValid()) return Decision.DENY_TLS;
        if (!evidence.privateNetworkPath()) return Decision.DENY_NETWORK;
        if (!evidence.endpointPolicyAllows()) return Decision.DENY_ENDPOINT_POLICY;
        if (!evidence.dataActionAllows()) return Decision.DENY_DATA_ACTION;
        if (!evidence.separateRuntimeAndDeploymentRoles()
                || route.runtimePrincipal().equals(route.deploymentPrincipal())) {
            return Decision.DENY_ROLE_SEPARATION;
        }
        if (!evidence.approvedRegion()) return Decision.DENY_REGION;
        if (!evidence.requiredCapability()) return Decision.DENY_CAPABILITY;
        return Decision.ALLOW;
    }

    static boolean mayFailOver(Route candidate, Evidence evidence,
                               OutcomeCost cost, FailoverPolicy policy) {
        return admit(candidate, evidence) == Decision.ALLOW
                && policy.admittedProviders().contains(candidate.provider())
                && policy.admittedRegions().contains(candidate.region())
                && policy.admittedCapabilities().contains(candidate.operation())
                && cost.microsPerAcceptedOutcome() <= policy.maxMicrosPerAcceptedOutcome();
    }

    static Evidence allow(String audience) {
        return new Evidence(true, audience, true, true, true, true, true,
                true, true, true);
    }

    static Evidence with(Evidence base, int gate, boolean value) {
        return new Evidence(
                gate == 0 ? value : base.temporaryWorkloadIdentity(),
                base.tokenAudience(),
                gate == 1 ? value : base.privateDns(),
                gate == 2 ? value : base.tlsHostnameValid(),
                gate == 3 ? value : base.privateNetworkPath(),
                gate == 4 ? value : base.endpointPolicyAllows(),
                gate == 5 ? value : base.dataActionAllows(),
                gate == 6 ? value : base.separateRuntimeAndDeploymentRoles(),
                gate == 7 ? value : base.approvedRegion(),
                gate == 8 ? value : base.requiredCapability());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value;
    }

    private static int scenarios;
    private static void group(Runnable check) { check.run(); scenarios++; }
    private static void truth(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Route aws = new Route("route-aws", Provider.AWS_BEDROCK,
                "anthropic.claude", "us-east-1", "eks:sa/assistant",
                "ci:deploy-models", "bedrock-runtime", "InvokeModel");
        Route azure = new Route("route-azure", Provider.AZURE_FOUNDRY,
                "support-gpt", "eastus2", "mi:assistant-api",
                "sp:foundry-deployer", "https://ai.azure.com/.default", "chat.completions");
        Route gcp = new Route("route-gcp", Provider.GCP_VERTEX,
                "support-gemini", "us-central1", "gsa:assistant-runtime",
                "gsa:release-deployer", "https://www.googleapis.com/auth/cloud-platform", "predict");
        Route local = new Route("route-local", Provider.INDEPENDENT_VLLM,
                "llama-3.3", "dc-1", "spiffe:assistant-runtime",
                "spiffe:model-deployer", "vllm-inference", "chat.completions");

        group(() -> truth(admit(aws, allow(aws.dataAudience())) == Decision.ALLOW, "AWS allow"));
        group(() -> truth(admit(azure, allow(azure.dataAudience())) == Decision.ALLOW, "Azure allow"));
        group(() -> truth(admit(gcp, allow(gcp.dataAudience())) == Decision.ALLOW, "GCP allow"));
        group(() -> truth(admit(local, allow(local.dataAudience())) == Decision.ALLOW, "vLLM allow"));

        Evidence awsGood = allow(aws.dataAudience());
        group(() -> truth(admit(aws, with(awsGood, 0, false)) == Decision.DENY_IDENTITY, "identity"));
        group(() -> truth(admit(aws, new Evidence(true, "bedrock-control", true, true,
                true, true, true, true, true, true)) == Decision.DENY_AUDIENCE, "audience"));
        group(() -> truth(admit(aws, with(awsGood, 1, false)) == Decision.DENY_DNS, "DNS"));
        group(() -> truth(admit(aws, with(awsGood, 2, false)) == Decision.DENY_TLS, "TLS"));
        group(() -> truth(admit(aws, with(awsGood, 3, false)) == Decision.DENY_NETWORK, "network"));
        group(() -> truth(admit(aws, with(awsGood, 4, false)) == Decision.DENY_ENDPOINT_POLICY,
                "endpoint policy"));
        group(() -> truth(admit(aws, with(awsGood, 5, false)) == Decision.DENY_DATA_ACTION,
                "data action"));
        group(() -> truth(admit(aws, with(awsGood, 6, false)) == Decision.DENY_ROLE_SEPARATION,
                "role separation"));
        group(() -> truth(admit(aws, with(awsGood, 7, false)) == Decision.DENY_REGION, "region"));
        group(() -> truth(admit(aws, with(awsGood, 8, false)) == Decision.DENY_CAPABILITY,
                "capability"));
        Route sharedRole = new Route("bad", Provider.AWS_BEDROCK, "m", "us-east-1",
                "iam:shared", "iam:shared", "bedrock-runtime", "InvokeModel");
        group(() -> truth(admit(sharedRole, allow(sharedRole.dataAudience()))
                == Decision.DENY_ROLE_SEPARATION, "same principal"));

        group(() -> truth(admit(aws, new Evidence(true, aws.dataAudience(), true, true,
                true, true, false, true, true, true)) == Decision.DENY_DATA_ACTION,
                "private path is not authorization"));
        group(() -> truth(admit(azure, new Evidence(true, "https://management.azure.com/.default",
                true, true, true, true, true, true, true, true)) == Decision.DENY_AUDIENCE,
                "management token not data token"));
        group(() -> truth(admit(gcp, new Evidence(true, gcp.dataAudience(), true, true,
                true, false, true, true, true, true)) == Decision.DENY_ENDPOINT_POLICY,
                "PSC allowlist"));
        group(() -> truth(admit(local, new Evidence(true, local.dataAudience(), true, true,
                true, true, false, true, true, true)) == Decision.DENY_DATA_ACTION,
                "private vLLM still needs gateway authorization"));

        OutcomeCost managed = new OutcomeCost(700_000, 100_000, 100_000,
                30_000, 20_000, 50_000, 100);
        OutcomeCost selfHosted = new OutcomeCost(250_000, 150_000, 420_000,
                40_000, 40_000, 100_000, 100);
        group(() -> truth(managed.totalMicros() == 1_000_000, "managed total"));
        group(() -> truth(managed.microsPerAcceptedOutcome() == 10_000, "managed unit cost"));
        group(() -> truth(selfHosted.microsPerAcceptedOutcome() == 10_000,
                "operator cost remains visible"));
        group(() -> {
            try {
                new OutcomeCost(Long.MAX_VALUE, 1, 0, 0, 0, 0, 1).totalMicros();
                throw new AssertionError("overflow accepted");
            } catch (ArithmeticException expected) { /* expected */ }
        });
        group(() -> {
            try {
                new OutcomeCost(1, 1, 1, 1, 1, 1, 0);
                throw new AssertionError("zero accepted outcomes");
            } catch (IllegalArgumentException expected) { /* expected */ }
        });

        FailoverPolicy policy = new FailoverPolicy(
                List.of(Provider.AWS_BEDROCK, Provider.GCP_VERTEX),
                List.of("us-east-1", "us-central1"),
                List.of("InvokeModel", "predict"), 10_000);
        group(() -> truth(mayFailOver(gcp, allow(gcp.dataAudience()), managed, policy),
                "compatible fallback"));
        group(() -> truth(!mayFailOver(azure, allow(azure.dataAudience()), managed, policy),
                "provider not admitted"));
        group(() -> truth(!mayFailOver(gcp, with(allow(gcp.dataAudience()), 7, false), managed, policy),
                "region evidence required"));
        OutcomeCost expensive = new OutcomeCost(1_100_000, 0, 0, 0, 0, 0, 100);
        group(() -> truth(!mayFailOver(gcp, allow(gcp.dataAudience()), expensive, policy),
                "accepted-outcome budget"));
        Route wrongCapability = new Route("route-gcp-embed", Provider.GCP_VERTEX,
                "embed", "us-central1", "gsa:assistant-runtime", "gsa:release-deployer",
                gcp.dataAudience(), "embedContent");
        group(() -> truth(!mayFailOver(wrongCapability, allow(gcp.dataAudience()), managed, policy),
                "capability not admitted"));
        group(() -> truth(!mayFailOver(local, allow(local.dataAudience()), managed, policy),
                "independent route not silent fallback"));

        long seed = 0x19C10DL;
        Random random = new Random(seed);
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            int failedGate = random.nextInt(10) - 1;
            Evidence evidence = allow(aws.dataAudience());
            if (failedGate >= 0 && failedGate <= 8) evidence = with(evidence, failedGate, false);
            if (failedGate == 9) {
                evidence = new Evidence(true, "control-plane", true, true, true, true,
                        true, true, true, true);
            }
            Decision decision = admit(aws, evidence);
            truth((failedGate == -1) == (decision == Decision.ALLOW), "seeded conjunction " + i);
        }
        group(() -> truth(seed == 0x19C10DL, "stable seed"));

        truth(scenarios == 31, "scenario count " + scenarios);
        System.out.println("SCENARIOS=" + scenarios + "|TRIALS=" + trials
                + "|PRIVATE=CONNECTIVITY_NOT_AUTHORITY|IDENTITY=WORKLOAD_SCOPED"
                + "|PLANE=DATA_NOT_MANAGEMENT|ROLE=RUNTIME_NOT_DEPLOY|COST=ACCEPTED_OUTCOME");
    }
}
