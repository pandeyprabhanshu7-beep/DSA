import java.util.HashSet;
import java.util.Set;

/** Dependency-free contracts for SSE answer completion, replay and cleanup. */
public final class SseCompletionContracts {
    enum Outcome { OPEN, COMPLETE, ERROR, INCOMPLETE }
    enum Replay { LIVE, REPLAY, RESET_REQUIRED, BAD_CURSOR }

    static final class StreamState {
        private Outcome outcome = Outcome.OPEN;
        private long lastSequence;

        void data(long sequence) {
            if (outcome != Outcome.OPEN || sequence != lastSequence + 1) {
                throw new IllegalStateException("illegal data transition");
            }
            lastSequence = sequence;
        }

        void terminal(long sequence, Outcome terminal) {
            if (terminal != Outcome.COMPLETE && terminal != Outcome.ERROR) {
                throw new IllegalArgumentException("terminal");
            }
            if (outcome != Outcome.OPEN || sequence != lastSequence + 1) {
                throw new IllegalStateException("illegal terminal transition");
            }
            lastSequence = sequence;
            outcome = terminal;
        }

        void close() {
            if (outcome == Outcome.OPEN) outcome = Outcome.INCOMPLETE;
        }

        Outcome outcome() { return outcome; }
        long lastSequence() { return lastSequence; }
    }

    static final class Permit {
        private boolean released;
        private int releases;

        boolean release() {
            if (released) return false;
            released = true;
            releases++;
            return true;
        }

        int releases() { return releases; }
    }

    static final class ActionLedger {
        private final Set<String> applied = new HashSet<>();

        boolean apply(String actionId) {
            if (actionId == null || actionId.isBlank()) throw new IllegalArgumentException("actionId");
            return applied.add(actionId);
        }
    }

    static Replay replay(String lastEventId, long firstRetained, long latest) {
        if (firstRetained < 1 || latest < firstRetained) throw new IllegalArgumentException("window");
        if (lastEventId == null || lastEventId.isEmpty()) return Replay.LIVE;
        if (!lastEventId.matches("[1-9][0-9]*")) return Replay.BAD_CURSOR;
        final long cursor;
        try { cursor = Long.parseLong(lastEventId); }
        catch (NumberFormatException overflow) { return Replay.BAD_CURSOR; }
        if (cursor < firstRetained - 1 || cursor > latest) return Replay.RESET_REQUIRED;
        return cursor == latest ? Replay.LIVE : Replay.REPLAY;
    }

    static String frame(String event, long id, String key, String value) {
        if (!event.matches("[a-z][a-z0-9_-]*") || id < 1 || key == null || key.isEmpty()) {
            throw new IllegalArgumentException("frame field");
        }
        return "event: " + event + "\nid: " + id + "\ndata: {\"" + json(key)
                + "\":\"" + json(value) + "\"}\n\n";
    }

    private static String json(String value) {
        if (value == null) throw new IllegalArgumentException("null JSON value");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) {
        int scenarios = 0;

        check(frame("delta", 1, "text", "a\nb").equals(
                "event: delta\nid: 1\ndata: {\"text\":\"a\\nb\"}\n\n")); scenarios++;
        check(frame("complete", 2, "status", "ok").contains("\"status\":\"ok\"")); scenarios++;
        check(frame("error", 3, "code", "bad\\\"quote").contains("bad\\\\\\\"quote")); scenarios++;
        try { frame("bad\nevent", 1, "x", "y"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { frame("delta", 0, "x", "y"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        StreamState complete = new StreamState();
        complete.data(1); complete.data(2); complete.terminal(3, Outcome.COMPLETE); complete.close();
        check(complete.outcome() == Outcome.COMPLETE && complete.lastSequence() == 3); scenarios++;
        StreamState failed = new StreamState();
        failed.data(1); failed.terminal(2, Outcome.ERROR); failed.close();
        check(failed.outcome() == Outcome.ERROR); scenarios++;
        StreamState truncated = new StreamState();
        truncated.data(1); truncated.close();
        check(truncated.outcome() == Outcome.INCOMPLETE); scenarios++;
        StreamState empty = new StreamState(); empty.close();
        check(empty.outcome() == Outcome.INCOMPLETE); scenarios++;
        try { complete.terminal(4, Outcome.ERROR); throw new AssertionError(); }
        catch (IllegalStateException expected) { scenarios++; }
        try { truncated.data(2); throw new AssertionError(); }
        catch (IllegalStateException expected) { scenarios++; }
        try { new StreamState().data(2); throw new AssertionError(); }
        catch (IllegalStateException expected) { scenarios++; }

        check(replay(null, 5, 10) == Replay.LIVE); scenarios++;
        check(replay("10", 5, 10) == Replay.LIVE); scenarios++;
        check(replay("4", 5, 10) == Replay.REPLAY); scenarios++;
        check(replay("7", 5, 10) == Replay.REPLAY); scenarios++;
        check(replay("3", 5, 10) == Replay.RESET_REQUIRED); scenarios++;
        check(replay("11", 5, 10) == Replay.RESET_REQUIRED); scenarios++;
        check(replay("0", 5, 10) == Replay.BAD_CURSOR); scenarios++;
        check(replay("7x", 5, 10) == Replay.BAD_CURSOR); scenarios++;

        ActionLedger actions = new ActionLedger();
        check(actions.apply("tool-r42-1")); scenarios++;
        check(!actions.apply("tool-r42-1")); scenarios++;
        check(actions.apply("tool-r42-2")); scenarios++;
        try { actions.apply(" "); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        Permit permit = new Permit();
        check(permit.release()); scenarios++;
        check(!permit.release()); scenarios++;
        check(!permit.release()); scenarios++;
        check(permit.releases() == 1); scenarios++;
        Permit timeoutPermit = new Permit();
        check(timeoutPermit.release() && !timeoutPermit.release() && timeoutPermit.releases() == 1); scenarios++;
        check(new Permit().releases() == 0); scenarios++;

        long state = 0x5EED18L;
        int completeCount = 0;
        for (int i = 0; i < 10_000; i++) {
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            int deltas = (int) ((state >>> 8) % 8);
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            int terminal = (int) ((state >>> 16) % 3);
            StreamState stream = new StreamState();
            for (int n = 1; n <= deltas; n++) stream.data(n);
            if (terminal == 1) stream.terminal(deltas + 1L, Outcome.COMPLETE);
            if (terminal == 2) stream.terminal(deltas + 1L, Outcome.ERROR);
            stream.close();
            Outcome expected = terminal == 1 ? Outcome.COMPLETE
                    : terminal == 2 ? Outcome.ERROR : Outcome.INCOMPLETE;
            check(stream.outcome() == expected);
            Permit trialPermit = new Permit();
            check(trialPermit.release() && !trialPermit.release() && trialPermit.releases() == 1);
            if (expected == Outcome.COMPLETE) completeCount++;
        }

        System.out.println("PARITY|FRAMING=SSE_JSON|TERMINAL=EXACTLY_ONCE|CLOSE=INCOMPLETE"
                + "|REPLAY=ORDERED|ACTION=IDEMPOTENT|PERMIT=RELEASE_ONCE|COMPLETE=" + completeCount);
        System.out.println("Java SSE completion contracts passed: " + scenarios
                + " scenario groups + 10000 seeded trials");
    }
}
