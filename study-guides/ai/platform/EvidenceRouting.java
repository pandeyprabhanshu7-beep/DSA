import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Java 17 source-file program for choosing evidence mechanisms safely. */
public final class EvidenceRouting {
    enum Mechanism { PROMPTING, RAG, FINE_TUNING, TOOL_CALLING }
    enum Effect { READ_ONLY, MUTATION }

    record Need(boolean approvedChangingEvidence,
                boolean liveState,
                boolean stableRepeatedBehavior,
                Effect effect) {
        Need {
            Objects.requireNonNull(effect, "effect");
        }
    }

    record Invocation(boolean authorized,
                      boolean confirmed,
                      String idempotencyKey) {}

    record Plan(EnumSet<Mechanism> mechanisms, List<String> safeguards) {
        Plan {
            mechanisms = EnumSet.copyOf(mechanisms);
            safeguards = List.copyOf(safeguards);
        }
    }

    static Plan route(Need need) {
        var mechanisms = EnumSet.of(Mechanism.PROMPTING);
        var safeguards = new java.util.ArrayList<String>();

        if (need.approvedChangingEvidence()) {
            mechanisms.add(Mechanism.RAG);
            safeguards.add("authorize-before-retrieval");
            safeguards.add("cite-source-revision");
        }
        if (need.liveState() || need.effect() == Effect.MUTATION) {
            mechanisms.add(Mechanism.TOOL_CALLING);
            safeguards.add("validate-tool-result");
        }
        if (need.stableRepeatedBehavior()) {
            mechanisms.add(Mechanism.FINE_TUNING);
            safeguards.add("held-out-evaluation");
        }
        if (need.effect() == Effect.MUTATION) {
            safeguards.add("server-side-authorization");
            safeguards.add("exact-action-confirmation");
            safeguards.add("idempotency-and-reconciliation");
            safeguards.add("postcondition-read");
        }
        return new Plan(mechanisms, safeguards);
    }

    static void authorizeMutation(Invocation invocation) {
        if (!invocation.authorized()) {
            throw new SecurityException("principal is not authorized");
        }
        if (!invocation.confirmed()) {
            throw new IllegalStateException("exact action was not confirmed");
        }
        if (invocation.idempotencyKey() == null || invocation.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotency key is required");
        }
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int groups = 0;

        var operations = route(new Need(true, true, false, Effect.READ_ONLY));
        expect(operations.mechanisms().equals(EnumSet.of(
                Mechanism.PROMPTING, Mechanism.RAG, Mechanism.TOOL_CALLING)),
                "procedure + health needs RAG and a live tool");
        expect(!operations.mechanisms().contains(Mechanism.FINE_TUNING),
                "current state must not be put into weights");
        groups += 2;

        var extractionBaseline = route(new Need(false, false, false, Effect.READ_ONLY));
        var extractionOptimized = route(new Need(false, false, true, Effect.READ_ONLY));
        expect(extractionBaseline.mechanisms().equals(EnumSet.of(Mechanism.PROMPTING)),
                "start stable extraction with a prompt baseline");
        expect(extractionOptimized.mechanisms().contains(Mechanism.FINE_TUNING),
                "stable repeated behavior may add fine-tuning after evals");
        expect(extractionOptimized.safeguards().contains("held-out-evaluation"),
                "fine-tuning requires held-out evaluation");
        groups += 3;

        var policy = route(new Need(true, false, false, Effect.READ_ONLY));
        expect(policy.mechanisms().contains(Mechanism.RAG), "current policy needs retrieval");
        expect(policy.safeguards().contains("authorize-before-retrieval"),
                "policy retrieval must be authorization filtered");
        expect(policy.safeguards().contains("cite-source-revision"),
                "policy answer must cite its revision");
        groups += 3;

        var mutation = route(new Need(false, false, false, Effect.MUTATION));
        expect(mutation.mechanisms().contains(Mechanism.TOOL_CALLING),
                "a mutation requires a controlled tool");
        expect(mutation.safeguards().contains("postcondition-read"),
                "a mutation requires a postcondition read");
        authorizeMutation(new Invocation(true, true, "rollback:payments:sha256-abc"));
        groups += 3;

        try {
            authorizeMutation(new Invocation(false, true, "op-1"));
            throw new AssertionError("unauthorized mutation was accepted");
        } catch (SecurityException expected) { groups++; }
        try {
            authorizeMutation(new Invocation(true, false, "op-2"));
            throw new AssertionError("unconfirmed mutation was accepted");
        } catch (IllegalStateException expected) { groups++; }
        try {
            authorizeMutation(new Invocation(true, true, " "));
            throw new AssertionError("mutation without idempotency was accepted");
        } catch (IllegalArgumentException expected) { groups++; }

        System.out.println("EvidenceRouting: " + groups + " scenario groups passed");
    }
}

