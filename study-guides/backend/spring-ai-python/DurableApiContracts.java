import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency-free contracts for synchronous answers and replay-safe ingestion jobs. */
public final class DurableApiContracts {
    enum AnswerOutcome { AUTH_REQUIRED, FORBIDDEN, INVALID_REQUEST, OVERLOADED,
        DEPENDENCY_UNAVAILABLE, UPSTREAM_TIMEOUT, INSUFFICIENT_EVIDENCE, COMPLETE }
    enum SubmitOutcome { ACCEPTED, REPLAY_EXISTING, CONFLICT, ROLLED_BACK }
    enum ReadOutcome { FOUND, NOT_FOUND }
    enum JobState { ACCEPTED, EXTRACTED, INDEXED, COMPLETE }

    record AnswerRequest(boolean authenticated, boolean authorized, boolean valid,
                         boolean capacity, boolean dependency, boolean withinDeadline,
                         boolean evidenceSufficient) {}
    record Submission(SubmitOutcome outcome, String jobId, int status) {}
    record IdempotencyRecord(String payloadHash, String jobId) {}
    record Job(String tenant, JobState state) {}

    static AnswerOutcome answerOutcome(AnswerRequest request) {
        if (!request.authenticated()) return AnswerOutcome.AUTH_REQUIRED;
        if (!request.authorized()) return AnswerOutcome.FORBIDDEN;
        if (!request.valid()) return AnswerOutcome.INVALID_REQUEST;
        if (!request.capacity()) return AnswerOutcome.OVERLOADED;
        if (!request.dependency()) return AnswerOutcome.DEPENDENCY_UNAVAILABLE;
        if (!request.withinDeadline()) return AnswerOutcome.UPSTREAM_TIMEOUT;
        if (!request.evidenceSufficient()) return AnswerOutcome.INSUFFICIENT_EVIDENCE;
        return AnswerOutcome.COMPLETE;
    }

    static int status(AnswerOutcome outcome) {
        return switch (outcome) {
            case AUTH_REQUIRED -> 401;
            case FORBIDDEN -> 403;
            case INVALID_REQUEST -> 422;
            case OVERLOADED -> 429;
            case DEPENDENCY_UNAVAILABLE -> 503;
            case UPSTREAM_TIMEOUT -> 504;
            case INSUFFICIENT_EVIDENCE, COMPLETE -> 200;
        };
    }

    static final class JobStore {
        final Map<String, IdempotencyRecord> keys = new HashMap<>();
        final Map<String, Job> jobs = new HashMap<>();
        final List<String> outbox = new ArrayList<>();
        final Set<String> sent = new HashSet<>();
        final Set<String> appliedStages = new HashSet<>();
        int deliveries;
        int nextJob = 1;

        Submission submit(String tenant, String operation, String key, String payloadHash,
                          boolean failBeforeCommit) {
            require(tenant, 64, "tenant");
            require(operation, 64, "operation");
            require(key, 128, "idempotency key");
            require(payloadHash, 128, "payload hash");
            String scope = tenant + "\u001f" + operation + "\u001f" + key;
            IdempotencyRecord existing = keys.get(scope);
            if (existing != null) {
                return existing.payloadHash().equals(payloadHash)
                        ? new Submission(SubmitOutcome.REPLAY_EXISTING, existing.jobId(), 202)
                        : new Submission(SubmitOutcome.CONFLICT, existing.jobId(), 409);
            }
            if (failBeforeCommit) return new Submission(SubmitOutcome.ROLLED_BACK, null, 503);
            String jobId = "job_" + nextJob++;
            // These three writes represent one database transaction.
            keys.put(scope, new IdempotencyRecord(payloadHash, jobId));
            jobs.put(jobId, new Job(tenant, JobState.ACCEPTED));
            outbox.add(jobId);
            return new Submission(SubmitOutcome.ACCEPTED, jobId, 202);
        }

        void relay(String jobId, boolean crashAfterPublish) {
            if (!outbox.contains(jobId) || sent.contains(jobId)) return;
            deliveries++;
            if (!crashAfterPublish) sent.add(jobId);
        }

        boolean applyStage(String jobId, JobState next) {
            Job current = jobs.get(jobId);
            if (current == null) return false;
            String stageKey = jobId + "\u001f" + next;
            if (!appliedStages.add(stageKey)) return false;
            if (next.ordinal() != current.state().ordinal() + 1) {
                appliedStages.remove(stageKey);
                return false;
            }
            jobs.put(jobId, new Job(current.tenant(), next));
            return true;
        }

        ReadOutcome read(String verifiedTenant, String jobId) {
            Job job = jobs.get(jobId);
            return job != null && job.tenant().equals(verifiedTenant)
                    ? ReadOutcome.FOUND : ReadOutcome.NOT_FOUND;
        }

        private static void require(String value, int maximum, String name) {
            if (value == null || value.isBlank() || value.length() > maximum) {
                throw new IllegalArgumentException("invalid " + name);
            }
        }
    }

    static String payloadHash(String canonicalPayload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }

    public static void main(String[] args) {
        int scenarios = 0;
        AnswerOutcome[] expected = AnswerOutcome.values();
        AnswerRequest[] answerCases = {
            new AnswerRequest(false, false, false, false, false, false, false),
            new AnswerRequest(true, false, false, false, false, false, false),
            new AnswerRequest(true, true, false, false, false, false, false),
            new AnswerRequest(true, true, true, false, false, false, false),
            new AnswerRequest(true, true, true, true, false, false, false),
            new AnswerRequest(true, true, true, true, true, false, false),
            new AnswerRequest(true, true, true, true, true, true, false),
            new AnswerRequest(true, true, true, true, true, true, true)
        };
        int[] statuses = {401, 403, 422, 429, 503, 504, 200, 200};
        for (int index = 0; index < answerCases.length; index++) {
            check(answerOutcome(answerCases[index]) == expected[index]);
            check(status(expected[index]) == statuses[index]);
            scenarios++;
        }

        JobStore store = new JobStore();
        String hashA = payloadHash("source=payments&revision=r8");
        String hashB = payloadHash("source=payments&revision=r9");
        Submission first = store.submit("tenant-a", "ingest", "key-1", hashA, false);
        check(first.outcome() == SubmitOutcome.ACCEPTED && first.status() == 202
                && store.keys.size() == 1 && store.jobs.size() == 1 && store.outbox.size() == 1);
        scenarios++;
        Submission replay = store.submit("tenant-a", "ingest", "key-1", hashA, false);
        check(replay.outcome() == SubmitOutcome.REPLAY_EXISTING
                && replay.jobId().equals(first.jobId()) && store.jobs.size() == 1);
        scenarios++;
        Submission conflict = store.submit("tenant-a", "ingest", "key-1", hashB, false);
        check(conflict.outcome() == SubmitOutcome.CONFLICT && conflict.status() == 409
                && store.jobs.size() == 1);
        scenarios++;
        check(store.submit("tenant-b", "ingest", "key-1", hashB, false).outcome()
                == SubmitOutcome.ACCEPTED); scenarios++;
        check(store.submit("tenant-a", "reindex", "key-1", hashB, false).outcome()
                == SubmitOutcome.ACCEPTED); scenarios++;

        JobStore rollback = new JobStore();
        Submission failed = rollback.submit("tenant-a", "ingest", "key-2", hashA, true);
        check(failed.outcome() == SubmitOutcome.ROLLED_BACK && rollback.keys.isEmpty()
                && rollback.jobs.isEmpty() && rollback.outbox.isEmpty()); scenarios++;
        check(rollback.submit("tenant-a", "ingest", "key-2", hashA, false).outcome()
                == SubmitOutcome.ACCEPTED); scenarios++;

        store.relay(first.jobId(), true);
        store.relay(first.jobId(), false);
        store.relay(first.jobId(), false);
        check(store.deliveries == 2 && store.sent.contains(first.jobId())); scenarios++;
        check(!store.applyStage(first.jobId(), JobState.INDEXED)); scenarios++;
        check(store.applyStage(first.jobId(), JobState.EXTRACTED)); scenarios++;
        check(!store.applyStage(first.jobId(), JobState.EXTRACTED)); scenarios++;
        check(store.applyStage(first.jobId(), JobState.INDEXED)); scenarios++;
        check(store.applyStage(first.jobId(), JobState.COMPLETE)); scenarios++;
        check(!store.applyStage(first.jobId(), JobState.COMPLETE)); scenarios++;
        check(store.read("tenant-a", first.jobId()) == ReadOutcome.FOUND); scenarios++;
        check(store.read("tenant-b", first.jobId()) == ReadOutcome.NOT_FOUND); scenarios++;
        check(store.read("tenant-a", "missing") == ReadOutcome.NOT_FOUND); scenarios++;

        String[] invalid = {null, "", " ", "x".repeat(129)};
        for (String key : invalid) {
            try {
                store.submit("tenant-a", "ingest", key, hashA, false);
                throw new AssertionError();
            } catch (IllegalArgumentException expectedError) { scenarios++; }
        }
        check(hashA.length() == 64 && hashA.equals(payloadHash("source=payments&revision=r8")));
        scenarios++;

        long state = 0x17A91L;
        int ready = 0;
        for (int trial = 0; trial < 10_000; trial++) {
            boolean[] flags = new boolean[7];
            for (int index = 0; index < flags.length; index++) {
                state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
                flags[index] = state % (index + 3) != 0;
            }
            AnswerRequest request = new AnswerRequest(flags[0], flags[1], flags[2], flags[3],
                    flags[4], flags[5], flags[6]);
            AnswerOutcome actual = answerOutcome(request);
            AnswerOutcome oracle = !flags[0] ? AnswerOutcome.AUTH_REQUIRED
                    : !flags[1] ? AnswerOutcome.FORBIDDEN
                    : !flags[2] ? AnswerOutcome.INVALID_REQUEST
                    : !flags[3] ? AnswerOutcome.OVERLOADED
                    : !flags[4] ? AnswerOutcome.DEPENDENCY_UNAVAILABLE
                    : !flags[5] ? AnswerOutcome.UPSTREAM_TIMEOUT
                    : !flags[6] ? AnswerOutcome.INSUFFICIENT_EVIDENCE : AnswerOutcome.COMPLETE;
            check(actual == oracle);
            if (actual == AnswerOutcome.COMPLETE) ready++;
        }

        check(scenarios == 30);
        System.out.println("PARITY|SYNC=PROVIDER_NEUTRAL|ERROR=PROBLEM_DETAIL|ASYNC=202_LOCATION"
                + "|IDEMPOTENCY=SCOPED_HASH|OUTBOX=AT_LEAST_ONCE|WORKER=STAGE_DEDUP"
                + "|READY=" + ready);
        System.out.println("Java durable-API contracts passed: " + scenarios
                + " scenario groups + 10000 seeded answer trials");
    }
}
