import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Dependency-free Java 17 contracts for the three Chapter 21 platform exercises. */
public final class PlatformDesignExercisesContracts {
    private PlatformDesignExercisesContracts() {}

    enum SupportDecision {
        ANSWER, LEXICAL_FALLBACK, ABSTAIN_SCOPE, ABSTAIN_STALE,
        ABSTAIN_DEADLINE, ABSTAIN_UNSUPPORTED_CITATION
    }
    enum RefundDecision {
        EXECUTE, REPLAY, REQUIRE_APPROVAL, CONFLICT, UNKNOWN, DENY_SCOPE
    }
    enum IngestionDecision {
        RUN_BATCH, REPLAY_BATCH, QUARANTINE, PUBLISH, KEEP_PRIOR, CONFLICT
    }

    record SupportRequest(
            String tenant,
            long membershipEpoch,
            long currentMembershipEpoch,
            String evidenceGeneration,
            String activeGeneration,
            long evidenceAclEpoch,
            long currentAclEpoch,
            boolean vectorAvailable,
            boolean lexicalAvailable,
            long remainingMillis,
            long requiredMillis,
            List<String> retrievedEvidenceIds,
            List<String> citedEvidenceIds) {
        SupportRequest {
            required(tenant, "tenant");
            required(evidenceGeneration, "evidenceGeneration");
            required(activeGeneration, "activeGeneration");
            retrievedEvidenceIds = List.copyOf(retrievedEvidenceIds);
            citedEvidenceIds = List.copyOf(citedEvidenceIds);
            nonNegative(membershipEpoch, "membershipEpoch");
            nonNegative(currentMembershipEpoch, "currentMembershipEpoch");
            nonNegative(evidenceAclEpoch, "evidenceAclEpoch");
            nonNegative(currentAclEpoch, "currentAclEpoch");
            nonNegative(remainingMillis, "remainingMillis");
            nonNegative(requiredMillis, "requiredMillis");
        }
    }

    static SupportDecision supportDecision(SupportRequest request) {
        Objects.requireNonNull(request);
        if (request.membershipEpoch() != request.currentMembershipEpoch()) {
            return SupportDecision.ABSTAIN_SCOPE;
        }
        if (!request.evidenceGeneration().equals(request.activeGeneration())
                || request.evidenceAclEpoch() != request.currentAclEpoch()) {
            return SupportDecision.ABSTAIN_STALE;
        }
        if (request.remainingMillis() < request.requiredMillis()) {
            return SupportDecision.ABSTAIN_DEADLINE;
        }
        if (!request.retrievedEvidenceIds().containsAll(request.citedEvidenceIds())) {
            return SupportDecision.ABSTAIN_UNSUPPORTED_CITATION;
        }
        if (request.vectorAvailable()) return SupportDecision.ANSWER;
        if (request.lexicalAvailable()) return SupportDecision.LEXICAL_FALLBACK;
        return SupportDecision.ABSTAIN_DEADLINE;
    }

    record RefundIntent(
            String tenant,
            String customer,
            String order,
            long amountMinor,
            String currency,
            long orderVersion,
            String idempotencyKey,
            String payloadHash) {
        RefundIntent {
            required(tenant, "tenant");
            required(customer, "customer");
            required(order, "order");
            required(currency, "currency");
            required(idempotencyKey, "idempotencyKey");
            required(payloadHash, "payloadHash");
            nonNegative(amountMinor, "amountMinor");
            nonNegative(orderVersion, "orderVersion");
        }
    }

    record Approval(String fingerprint, long expiresAtMillis) {
        Approval {
            required(fingerprint, "fingerprint");
            nonNegative(expiresAtMillis, "expiresAtMillis");
        }
    }

    record RefundRecord(String payloadHash, String fingerprint, boolean completed, boolean unknown) {}

    static final class RefundGateway {
        private final long approvalThresholdMinor;
        private final Map<String, RefundRecord> operations = new HashMap<>();

        RefundGateway(long approvalThresholdMinor) {
            nonNegative(approvalThresholdMinor, "approvalThresholdMinor");
            this.approvalThresholdMinor = approvalThresholdMinor;
        }

        RefundDecision apply(RefundIntent intent, String authenticatedTenant,
                             String authenticatedCustomer, Approval approval,
                             long nowMillis, boolean responseObserved) {
            Objects.requireNonNull(intent);
            if (!intent.tenant().equals(authenticatedTenant)
                    || !intent.customer().equals(authenticatedCustomer)) {
                return RefundDecision.DENY_SCOPE;
            }
            RefundRecord prior = operations.get(intent.idempotencyKey());
            if (prior != null && !prior.payloadHash().equals(intent.payloadHash())) {
                return RefundDecision.CONFLICT;
            }
            if (prior != null && prior.completed()) return RefundDecision.REPLAY;
            if (prior != null && prior.unknown()) return RefundDecision.UNKNOWN;

            String fingerprint = fingerprint(intent);
            if (intent.amountMinor() > approvalThresholdMinor
                    && (approval == null || !approval.fingerprint().equals(fingerprint)
                    || nowMillis >= approval.expiresAtMillis())) {
                return RefundDecision.REQUIRE_APPROVAL;
            }
            operations.put(intent.idempotencyKey(), new RefundRecord(
                    intent.payloadHash(), fingerprint, responseObserved, !responseObserved));
            return responseObserved ? RefundDecision.EXECUTE : RefundDecision.UNKNOWN;
        }

        void reconcile(String idempotencyKey, boolean completed) {
            RefundRecord prior = Objects.requireNonNull(operations.get(idempotencyKey));
            operations.put(idempotencyKey, new RefundRecord(
                    prior.payloadHash(), prior.fingerprint(), completed, !completed));
        }
    }

    static String fingerprint(RefundIntent value) {
        return String.join("|", value.tenant(), value.customer(), value.order(),
                Long.toString(value.amountMinor()), value.currency(),
                Long.toString(value.orderVersion()), value.payloadHash());
    }

    record Batch(String tenant, String job, int number, int attempts, String generation) {
        Batch {
            required(tenant, "tenant");
            required(job, "job");
            required(generation, "generation");
            if (number < 0 || attempts < 0) throw new IllegalArgumentException("negative batch field");
        }
        String id() { return tenant + ":" + job + ":" + generation + ":" + number; }
    }

    static final class FairIngestion {
        private final Map<String, ArrayDeque<Batch>> queues = new HashMap<>();
        private final ArrayDeque<String> turns = new ArrayDeque<>();
        private final Map<String, Boolean> completed = new HashMap<>();
        private final int maxAttempts;

        FairIngestion(int maxAttempts) {
            if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts");
            this.maxAttempts = maxAttempts;
        }

        void submit(Batch batch) {
            ArrayDeque<Batch> queue = queues.computeIfAbsent(batch.tenant(), ignored -> {
                turns.addLast(batch.tenant());
                return new ArrayDeque<>();
            });
            queue.addLast(batch);
        }

        Batch next() {
            int tenants = turns.size();
            while (tenants-- > 0) {
                String tenant = turns.removeFirst();
                ArrayDeque<Batch> queue = queues.get(tenant);
                if (queue != null && !queue.isEmpty()) {
                    turns.addLast(tenant);
                    return queue.removeFirst();
                }
            }
            return null;
        }

        IngestionDecision complete(Batch batch, boolean parseValid) {
            if (Boolean.TRUE.equals(completed.get(batch.id()))) return IngestionDecision.REPLAY_BATCH;
            if (!parseValid && batch.attempts() >= maxAttempts) return IngestionDecision.QUARANTINE;
            if (!parseValid) return IngestionDecision.RUN_BATCH;
            completed.put(batch.id(), true);
            return IngestionDecision.RUN_BATCH;
        }

        IngestionDecision publish(String expectedActive, String observedActive,
                                  String candidate, int expectedBatches) {
            if (!Objects.equals(expectedActive, observedActive)) return IngestionDecision.CONFLICT;
            long ready = completed.keySet().stream().filter(key -> key.contains(":" + candidate + ":")).count();
            return ready == expectedBatches ? IngestionDecision.PUBLISH : IngestionDecision.KEEP_PRIOR;
        }
    }

    record ReleaseTuple(String application, String prompt, String modelRoute,
                        String embeddingSpace, String indexGeneration,
                        String toolSchema, String evaluationSet, String policyRevision) {
        ReleaseTuple {
            for (String value : List.of(application, prompt, modelRoute, embeddingSpace,
                    indexGeneration, toolSchema, evaluationSet, policyRevision)) required(value, "release field");
        }
    }

    static boolean rollbackReady(ReleaseTuple running, ReleaseTuple candidate) {
        return running != null && candidate != null && !running.equals(candidate);
    }

    private static int scenarios;
    private static void group(Runnable check) { check.run(); scenarios++; }
    private static void truth(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value;
    }
    private static void nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " is negative");
    }

    static SupportRequest support(boolean vector, boolean lexical) {
        return new SupportRequest("t-a", 7, 7, "g-4", "g-4", 9, 9,
                vector, lexical, 4_000, 2_000, List.of("e-1", "e-2"), List.of("e-1"));
    }

    public static void main(String[] args) {
        group(() -> truth(supportDecision(support(true, true)) == SupportDecision.ANSWER, "vector answer"));
        group(() -> truth(supportDecision(support(false, true)) == SupportDecision.LEXICAL_FALLBACK, "fallback"));
        group(() -> truth(supportDecision(new SupportRequest("t-a", 6, 7, "g-4", "g-4", 9, 9,
                true, true, 4_000, 2_000, List.of("e-1"), List.of("e-1"))) == SupportDecision.ABSTAIN_SCOPE, "scope"));
        group(() -> truth(supportDecision(new SupportRequest("t-a", 7, 7, "g-3", "g-4", 9, 9,
                true, true, 4_000, 2_000, List.of("e-1"), List.of("e-1"))) == SupportDecision.ABSTAIN_STALE, "generation"));
        group(() -> truth(supportDecision(new SupportRequest("t-a", 7, 7, "g-4", "g-4", 8, 9,
                true, true, 4_000, 2_000, List.of("e-1"), List.of("e-1"))) == SupportDecision.ABSTAIN_STALE, "acl"));
        group(() -> truth(supportDecision(new SupportRequest("t-a", 7, 7, "g-4", "g-4", 9, 9,
                true, true, 1_000, 2_000, List.of("e-1"), List.of("e-1"))) == SupportDecision.ABSTAIN_DEADLINE, "deadline"));
        group(() -> truth(supportDecision(new SupportRequest("t-a", 7, 7, "g-4", "g-4", 9, 9,
                true, true, 4_000, 2_000, List.of("e-1"), List.of("e-2"))) == SupportDecision.ABSTAIN_UNSUPPORTED_CITATION, "citation"));

        RefundIntent low = new RefundIntent("t-a", "c-1", "o-8", 500, "USD", 3, "idem-1", "sha-a");
        RefundGateway gateway = new RefundGateway(1_000);
        group(() -> truth(gateway.apply(low, "t-a", "c-1", null, 10, true) == RefundDecision.EXECUTE, "low execute"));
        group(() -> truth(gateway.apply(low, "t-a", "c-1", null, 11, true) == RefundDecision.REPLAY, "replay"));
        group(() -> truth(gateway.apply(new RefundIntent("t-a", "c-1", "o-8", 600, "USD", 3, "idem-1", "sha-b"),
                "t-a", "c-1", null, 12, true) == RefundDecision.CONFLICT, "key conflict"));
        group(() -> truth(gateway.apply(low, "t-b", "c-1", null, 13, true) == RefundDecision.DENY_SCOPE, "tenant"));
        RefundIntent high = new RefundIntent("t-a", "c-1", "o-9", 5_000, "USD", 4, "idem-2", "sha-c");
        group(() -> truth(gateway.apply(high, "t-a", "c-1", null, 14, true) == RefundDecision.REQUIRE_APPROVAL, "approval"));
        Approval approval = new Approval(fingerprint(high), 100);
        group(() -> truth(gateway.apply(high, "t-a", "c-1", approval, 14, false) == RefundDecision.UNKNOWN, "lost response"));
        group(() -> truth(gateway.apply(high, "t-a", "c-1", approval, 15, true) == RefundDecision.UNKNOWN, "unknown stays unknown"));
        gateway.reconcile("idem-2", true);
        group(() -> truth(gateway.apply(high, "t-a", "c-1", approval, 16, true) == RefundDecision.REPLAY, "reconciled"));
        RefundIntent changed = new RefundIntent("t-a", "c-1", "o-9", 5_001, "USD", 4, "idem-3", "sha-d");
        group(() -> truth(gateway.apply(changed, "t-a", "c-1", approval, 17, true) == RefundDecision.REQUIRE_APPROVAL, "approval binds payload"));
        RefundIntent expiring = new RefundIntent("t-a", "c-1", "o-10", 5_000, "USD", 1, "idem-4", "sha-e");
        group(() -> truth(gateway.apply(expiring, "t-a", "c-1",
                new Approval(fingerprint(expiring), 17), 17, true)
                == RefundDecision.REQUIRE_APPROVAL, "expired approval"));

        FairIngestion ingestion = new FairIngestion(3);
        for (int i = 0; i < 4; i++) ingestion.submit(new Batch("large", "j-big", i, 1, "g-2"));
        ingestion.submit(new Batch("small", "j-small", 0, 1, "g-9"));
        group(() -> truth(ingestion.next().tenant().equals("large"), "first tenant"));
        group(() -> truth(ingestion.next().tenant().equals("small"), "quiet tenant turn"));
        Batch good = new Batch("small", "j-small", 0, 1, "g-9");
        group(() -> truth(ingestion.complete(good, true) == IngestionDecision.RUN_BATCH, "complete"));
        group(() -> truth(ingestion.complete(good, true) == IngestionDecision.REPLAY_BATCH, "batch replay"));
        group(() -> truth(ingestion.complete(new Batch("large", "bad", 0, 3, "g-x"), false)
                == IngestionDecision.QUARANTINE, "poison bounded"));
        group(() -> truth(ingestion.publish("g-8", "g-8", "g-9", 1) == IngestionDecision.PUBLISH, "publish ready"));
        group(() -> truth(ingestion.publish("g-8", "g-7", "g-9", 1) == IngestionDecision.CONFLICT, "cas conflict"));
        group(() -> truth(ingestion.publish("g-8", "g-8", "g-10", 1) == IngestionDecision.KEEP_PRIOR, "keep prior"));

        ReleaseTuple v1 = new ReleaseTuple("app@1", "prompt@4", "route@2", "embed@7", "idx@9", "tools@3", "eval@5", "policy@8");
        ReleaseTuple v2 = new ReleaseTuple("app@2", "prompt@4", "route@2", "embed@7", "idx@9", "tools@3", "eval@5", "policy@8");
        group(() -> truth(rollbackReady(v1, v2), "rollback tuple"));
        group(() -> truth(!rollbackReady(v1, v1), "same tuple is not rollback"));

        Random random = new Random(21);
        group(() -> {
            for (int i = 0; i < 10_000; i++) {
                boolean scope = random.nextBoolean();
                boolean current = random.nextBoolean();
                boolean enough = random.nextBoolean();
                SupportDecision decision = supportDecision(new SupportRequest("t", scope ? 1 : 0, 1,
                        current ? "g" : "old", "g", current ? 2 : 1, 2,
                        true, true, enough ? 10 : 0, 5, List.of("e"), List.of("e")));
                if (!scope) truth(decision == SupportDecision.ABSTAIN_SCOPE, "random scope");
                else if (!current) truth(decision == SupportDecision.ABSTAIN_STALE, "random freshness");
                else if (!enough) truth(decision == SupportDecision.ABSTAIN_DEADLINE, "random deadline");
                else truth(decision == SupportDecision.ANSWER, "random answer");
            }
        });

        System.out.println("PlatformDesignExercisesContracts: " + scenarios
                + " scenario groups + 10000 seeded trials passed");
    }
}
