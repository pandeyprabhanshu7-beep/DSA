import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Dependency-free Java 17 contracts for durable conversation memory. */
public final class ConversationMemoryContracts {
    private ConversationMemoryContracts() {}

    enum EventType {
        USER_MESSAGE, ASSISTANT_PENDING, ASSISTANT_FINAL, TURN_FAILED,
        APPROVAL, BALANCE_SNAPSHOT, ENVIRONMENT_SELECTION
    }

    enum AppendOutcome { APPENDED, REPLAYED, BLOCK_VERSION }
    enum FinalizeOutcome { FINALIZED, REPLAYED, BLOCK_VERSION, BLOCK_PENDING }
    enum SummaryOutcome { ACCEPTED, BLOCK_RANGE, BLOCK_SOURCE_VERSION,
        BLOCK_SUMMARIZER, BLOCK_AUTHORITY }
    enum DeleteOutcome { COMPLETE, RETAINED_BACKUP, BLOCK_SERVING, PENDING_DERIVED }

    record ConversationKey(String tenant, String conversationId) {
        ConversationKey {
            required(tenant, "tenant");
            required(conversationId, "conversationId");
        }
    }

    record Event(long sequence, String operationKey, EventType type, String content,
                 int tokenCount, boolean authorized) {
        Event {
            if (sequence < 1) throw new IllegalArgumentException("sequence must be positive");
            required(operationKey, "operationKey");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(content, "content");
            if (tokenCount < 0) throw new IllegalArgumentException("tokenCount is negative");
        }
    }

    record Conversation(ConversationKey key, String ownerSubject, long version,
                        long nextSequence, List<Event> events,
                        Map<String, Long> firstSequenceByOperation) {
        Conversation {
            Objects.requireNonNull(key, "key");
            required(ownerSubject, "ownerSubject");
            if (version < 0) throw new IllegalArgumentException("version is negative");
            if (nextSequence < 1) throw new IllegalArgumentException("nextSequence is invalid");
            events = List.copyOf(events);
            firstSequenceByOperation = Map.copyOf(firstSequenceByOperation);
        }
    }

    record AppendResult(AppendOutcome outcome, Conversation conversation,
                        long userSequence, long pendingSequence) {}
    record FinalizeResult(FinalizeOutcome outcome, Conversation conversation,
                          long finalSequence) {}
    record Summary(long startSequence, long endSequence, long sourceVersion,
                   String summarizerVersion, String text,
                   Set<String> claimedAuthoritativeFields) {
        Summary {
            claimedAuthoritativeFields = Set.copyOf(claimedAuthoritativeFields);
        }
    }
    record Context(List<Event> events, int usedTokens) {
        Context { events = List.copyOf(events); }
    }
    record ErasureState(boolean servingDenied, boolean liveEvents,
                        boolean vectorMemory, boolean answerCache,
                        boolean summaries, boolean traces, boolean backupCopies,
                        String backupPolicy) {}

    static Conversation empty(String tenant, String conversationId, String ownerSubject) {
        return new Conversation(new ConversationKey(tenant, conversationId), ownerSubject,
                0, 1, List.of(), Map.of());
    }

    /** Models one short transaction: check scope/version, allocate two sequences, append, commit. */
    static AppendResult appendUserTurn(Conversation current, String verifiedTenant,
                                       String verifiedOwner, long expectedVersion,
                                       String operationKey, String message, int tokenCount) {
        requireIdentity(current, verifiedTenant, verifiedOwner);
        required(operationKey, "operationKey");
        required(message, "message");
        Long prior = current.firstSequenceByOperation().get(operationKey);
        if (prior != null) {
            return new AppendResult(AppendOutcome.REPLAYED, current, prior, prior + 1);
        }
        if (current.version() != expectedVersion) {
            return new AppendResult(AppendOutcome.BLOCK_VERSION, current, -1, -1);
        }

        long userSequence = current.nextSequence();
        long pendingSequence = userSequence + 1;
        List<Event> nextEvents = new ArrayList<>(current.events());
        nextEvents.add(new Event(userSequence, operationKey, EventType.USER_MESSAGE,
                message, tokenCount, true));
        nextEvents.add(new Event(pendingSequence, operationKey,
                EventType.ASSISTANT_PENDING, "", 0, true));
        Map<String, Long> nextOperations = new HashMap<>(current.firstSequenceByOperation());
        nextOperations.put(operationKey, userSequence);
        Conversation next = new Conversation(current.key(), current.ownerSubject(),
                current.version() + 1, pendingSequence + 1, nextEvents, nextOperations);
        return new AppendResult(AppendOutcome.APPENDED, next, userSequence, pendingSequence);
    }

    /** Models a second short transaction after the model call has completed. */
    static FinalizeResult finalizeAssistant(Conversation current, String verifiedTenant,
                                            String verifiedOwner, long expectedVersion,
                                            String operationKey, String response,
                                            int tokenCount) {
        requireIdentity(current, verifiedTenant, verifiedOwner);
        required(operationKey, "operationKey");
        required(response, "response");
        for (Event event : current.events()) {
            if (event.operationKey().equals(operationKey)
                    && event.type() == EventType.ASSISTANT_FINAL) {
                return new FinalizeResult(FinalizeOutcome.REPLAYED, current, event.sequence());
            }
        }
        if (current.version() != expectedVersion) {
            return new FinalizeResult(FinalizeOutcome.BLOCK_VERSION, current, -1);
        }
        boolean pending = current.events().stream().anyMatch(event ->
                event.operationKey().equals(operationKey)
                        && event.type() == EventType.ASSISTANT_PENDING);
        if (!pending) {
            return new FinalizeResult(FinalizeOutcome.BLOCK_PENDING, current, -1);
        }
        long sequence = current.nextSequence();
        List<Event> nextEvents = new ArrayList<>(current.events());
        nextEvents.add(new Event(sequence, operationKey, EventType.ASSISTANT_FINAL,
                response, tokenCount, true));
        Conversation next = new Conversation(current.key(), current.ownerSubject(),
                current.version() + 1, sequence + 1, nextEvents,
                current.firstSequenceByOperation());
        return new FinalizeResult(FinalizeOutcome.FINALIZED, next, sequence);
    }

    /** Selects whole, authorized, terminal messages without changing the audit history. */
    static Context selectContext(Conversation current, String verifiedTenant,
                                 String verifiedOwner, int tokenBudget) {
        requireIdentity(current, verifiedTenant, verifiedOwner);
        if (tokenBudget < 0) throw new IllegalArgumentException("tokenBudget is negative");
        List<Event> reversed = new ArrayList<>();
        int used = 0;
        for (int i = current.events().size() - 1; i >= 0; i--) {
            Event event = current.events().get(i);
            boolean eligible = event.authorized()
                    && (event.type() == EventType.USER_MESSAGE
                    || event.type() == EventType.ASSISTANT_FINAL);
            if (!eligible) continue;
            if (event.tokenCount() > tokenBudget - used) continue;
            reversed.add(event);
            used += event.tokenCount();
        }
        List<Event> ordered = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) ordered.add(reversed.get(i));
        return new Context(ordered, used);
    }

    /** A summary is derived data: it must name its source range/version and cannot grant authority. */
    static SummaryOutcome validateSummary(Conversation current, Summary summary) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(summary, "summary");
        if (summary.sourceVersion() != current.version()) {
            return SummaryOutcome.BLOCK_SOURCE_VERSION;
        }
        if (summary.summarizerVersion() == null || summary.summarizerVersion().isBlank()) {
            return SummaryOutcome.BLOCK_SUMMARIZER;
        }
        if (!summary.claimedAuthoritativeFields().isEmpty()) {
            return SummaryOutcome.BLOCK_AUTHORITY;
        }
        if (summary.startSequence() < 1 || summary.endSequence() < summary.startSequence()) {
            return SummaryOutcome.BLOCK_RANGE;
        }
        Set<Long> present = new HashSet<>();
        for (Event event : current.events()) present.add(event.sequence());
        for (long sequence = summary.startSequence(); sequence <= summary.endSequence(); sequence++) {
            if (!present.contains(sequence)) return SummaryOutcome.BLOCK_RANGE;
        }
        return SummaryOutcome.ACCEPTED;
    }

    /** Live denial happens first; completion waits for every live/derived serving copy. */
    static DeleteOutcome assessDeletion(ErasureState state) {
        Objects.requireNonNull(state, "state");
        if (!state.servingDenied()) return DeleteOutcome.BLOCK_SERVING;
        if (state.liveEvents() || state.vectorMemory() || state.answerCache()
                || state.summaries() || state.traces()) {
            return DeleteOutcome.PENDING_DERIVED;
        }
        if (state.backupCopies()) {
            if (state.backupPolicy() == null || state.backupPolicy().isBlank()) {
                return DeleteOutcome.PENDING_DERIVED;
            }
            return DeleteOutcome.RETAINED_BACKUP;
        }
        return DeleteOutcome.COMPLETE;
    }

    private static void requireIdentity(Conversation conversation, String tenant, String owner) {
        Objects.requireNonNull(conversation, "conversation");
        if (!conversation.key().tenant().equals(required(tenant, "verifiedTenant"))) {
            throw new SecurityException("tenant mismatch");
        }
        if (!conversation.ownerSubject().equals(required(owner, "verifiedOwner"))) {
            throw new SecurityException("owner mismatch");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value;
    }

    private static int scenarios;

    private static void group(Runnable check) {
        check.run();
        scenarios++;
    }

    private static void truth(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) return;
            throw new AssertionError("expected " + expected + ", got " + actual, actual);
        }
        throw new AssertionError("expected " + expected.getSimpleName());
    }

    public static void main(String[] args) {
        Conversation base = empty("tenant-a", "conversation-7", "alice");
        AppendResult first = appendUserTurn(base, "tenant-a", "alice", 0,
                "turn-1", "Do not deploy production", 5);
        group(() -> truth(first.outcome() == AppendOutcome.APPENDED, "first append"));
        group(() -> truth(first.userSequence() == 1 && first.pendingSequence() == 2,
                "contiguous allocation"));
        group(() -> truth(first.conversation().version() == 1
                && first.conversation().nextSequence() == 3, "version advance"));
        group(() -> truth(appendUserTurn(first.conversation(), "tenant-a", "alice", 99,
                "turn-1", "different retry body", 9).outcome() == AppendOutcome.REPLAYED,
                "stable operation replay"));
        group(() -> truth(appendUserTurn(first.conversation(), "tenant-a", "alice", 0,
                "turn-2", "race", 1).outcome() == AppendOutcome.BLOCK_VERSION,
                "optimistic version fence"));
        group(() -> rejects(SecurityException.class, () -> appendUserTurn(base,
                "tenant-b", "alice", 0, "x", "m", 1)));
        group(() -> rejects(SecurityException.class, () -> appendUserTurn(base,
                "tenant-a", "mallory", 0, "x", "m", 1)));

        FinalizeResult finalized = finalizeAssistant(first.conversation(), "tenant-a", "alice",
                1, "turn-1", "Acknowledged; production is blocked", 6);
        group(() -> truth(finalized.outcome() == FinalizeOutcome.FINALIZED,
                "finalize after model call"));
        group(() -> truth(finalized.finalSequence() == 3
                && finalized.conversation().version() == 2, "append-only finalization"));
        group(() -> truth(finalizeAssistant(finalized.conversation(), "tenant-a", "alice",
                0, "turn-1", "retry", 1).outcome() == FinalizeOutcome.REPLAYED,
                "finalization replay"));
        group(() -> truth(finalizeAssistant(finalized.conversation(), "tenant-a", "alice",
                2, "missing", "response", 1).outcome() == FinalizeOutcome.BLOCK_PENDING,
                "pending ownership"));
        group(() -> truth(finalizeAssistant(first.conversation(), "tenant-a", "alice",
                0, "turn-1", "late", 1).outcome() == FinalizeOutcome.BLOCK_VERSION,
                "finalize version fence"));

        Context context = selectContext(finalized.conversation(), "tenant-a", "alice", 11);
        group(() -> truth(context.events().size() == 2 && context.usedTokens() == 11,
                "bounded terminal context"));
        group(() -> truth(context.events().get(0).sequence() == 1
                && context.events().get(1).sequence() == 3, "chronological context"));
        group(() -> truth(selectContext(finalized.conversation(), "tenant-a", "alice", 6)
                .events().size() == 1, "whole-message budget"));
        group(() -> truth(selectContext(first.conversation(), "tenant-a", "alice", 100)
                .events().stream().noneMatch(e -> e.type() == EventType.ASSISTANT_PENDING),
                "pending excluded"));
        group(() -> rejects(IllegalArgumentException.class, () ->
                selectContext(base, "tenant-a", "alice", -1)));

        Summary valid = new Summary(1, 3, 2, "summarizer@4",
                "Production deployment remains blocked", Set.of());
        group(() -> truth(validateSummary(finalized.conversation(), valid)
                == SummaryOutcome.ACCEPTED, "summary provenance"));
        group(() -> truth(validateSummary(finalized.conversation(), new Summary(1, 3, 1,
                "summarizer@4", "text", Set.of())) == SummaryOutcome.BLOCK_SOURCE_VERSION,
                "stale summary source"));
        group(() -> truth(validateSummary(finalized.conversation(), new Summary(1, 3, 2,
                "", "text", Set.of())) == SummaryOutcome.BLOCK_SUMMARIZER,
                "summarizer identity"));
        group(() -> truth(validateSummary(finalized.conversation(), new Summary(1, 4, 2,
                "summarizer@4", "text", Set.of())) == SummaryOutcome.BLOCK_RANGE,
                "exact event range"));
        group(() -> truth(validateSummary(finalized.conversation(), new Summary(1, 3, 2,
                "summarizer@4", "Approved for production", Set.of("approval")))
                == SummaryOutcome.BLOCK_AUTHORITY, "summary cannot grant approval"));

        group(() -> truth(assessDeletion(new ErasureState(false, true, true, true,
                true, true, true, "30-day expiry")) == DeleteOutcome.BLOCK_SERVING,
                "deny serving first"));
        group(() -> truth(assessDeletion(new ErasureState(true, false, true, false,
                false, false, false, "")) == DeleteOutcome.PENDING_DERIVED,
                "vector cleanup"));
        group(() -> truth(assessDeletion(new ErasureState(true, false, false, false,
                false, true, false, "")) == DeleteOutcome.PENDING_DERIVED,
                "trace cleanup"));
        group(() -> truth(assessDeletion(new ErasureState(true, false, false, false,
                false, false, true, "30-day encrypted backup expiry"))
                == DeleteOutcome.RETAINED_BACKUP, "declared backup state"));
        group(() -> truth(assessDeletion(new ErasureState(true, false, false, false,
                false, false, true, "")) == DeleteOutcome.PENDING_DERIVED,
                "undeclared backup is not complete"));
        group(() -> truth(assessDeletion(new ErasureState(true, false, false, false,
                false, false, false, "")) == DeleteOutcome.COMPLETE,
                "complete live erasure"));

        Random random = new Random(0xC0FFEE17L);
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            int firstTokens = 1 + random.nextInt(20);
            AppendResult appended = appendUserTurn(base, "tenant-a", "alice", 0,
                    "trial-" + i, "message-" + i, firstTokens);
            FinalizeResult done = finalizeAssistant(appended.conversation(), "tenant-a",
                    "alice", 1, "trial-" + i, "answer-" + i, 1 + random.nextInt(20));
            Context selected = selectContext(done.conversation(), "tenant-a", "alice",
                    random.nextInt(41));
            int oracleTokens = selected.events().stream().mapToInt(Event::tokenCount).sum();
            if (oracleTokens != selected.usedTokens()) throw new AssertionError("token oracle");
            long prior = 0;
            for (Event event : done.conversation().events()) {
                if (event.sequence() <= prior) throw new AssertionError("sequence oracle");
                prior = event.sequence();
            }
            if (done.conversation().events().size() != 3) throw new AssertionError("audit oracle");
        }

        System.out.println("SCENARIOS=" + scenarios + "|TRIALS=" + trials
                + "|AUDIT=APPEND_ONLY|CONTEXT=AUTHORIZED_BOUNDED|TURN=SHORT_TX"
                + "|SUMMARY=DERIVED|AUTHORITY=TYPED_RECORD|DELETE=GRAPH");
    }
}
