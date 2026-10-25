/** Dependency-free contracts for SSE/WebSocket streaming, replay, backpressure and completion. */
public final class StreamingTransportContracts {
    enum Transport { SSE, WEBSOCKET }
    enum Resume { START_LIVE, REPLAY, RESET_REQUIRED, BAD_CURSOR }
    enum Enqueue { ACCEPT, OVERFLOW_CLOSE_1013, BAD_LIMIT }
    enum Completion { PROVISIONAL, VALIDATED_FINAL, INVALID_FINAL, INCOMPLETE, ABANDONED }

    static Transport chooseTransport(boolean clientCommands, boolean binaryMessages) {
        return clientCommands || binaryMessages ? Transport.WEBSOCKET : Transport.SSE;
    }

    static String encodeSse(String event, String id, String data) {
        requireField("event", event);
        requireField("id", id);
        if (data.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL data");
        String normalized = data.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder();
        if (!event.isEmpty()) out.append("event: ").append(event).append('\n');
        if (!id.isEmpty()) out.append("id: ").append(id).append('\n');
        for (String line : normalized.split("\n", -1)) {
            out.append("data: ").append(line).append('\n');
        }
        return out.append('\n').toString();
    }

    private static void requireField(String name, String value) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name);
        }
    }

    static Resume resume(String lastEventId, long firstRetained, long latest) {
        if (firstRetained < 1 || latest < firstRetained) throw new IllegalArgumentException("window");
        if (lastEventId == null || lastEventId.isEmpty()) return Resume.START_LIVE;
        if (!lastEventId.matches("[1-9][0-9]*")) return Resume.BAD_CURSOR;
        final long cursor;
        try { cursor = Long.parseLong(lastEventId); }
        catch (NumberFormatException overflow) { return Resume.BAD_CURSOR; }
        if (cursor < firstRetained - 1 || cursor > latest) return Resume.RESET_REQUIRED;
        return cursor == latest ? Resume.START_LIVE : Resume.REPLAY;
    }

    static Enqueue enqueue(int queuedFrames, int maxFrames) {
        if (queuedFrames < 0 || maxFrames < 1) return Enqueue.BAD_LIMIT;
        return queuedFrames < maxFrames ? Enqueue.ACCEPT : Enqueue.OVERFLOW_CLOSE_1013;
    }

    static Completion completion(boolean disconnected, boolean budgetExceeded,
                                 boolean streamEnded, boolean citationsValid,
                                 boolean businessValid) {
        if (disconnected) return Completion.ABANDONED;
        if (budgetExceeded) return Completion.INCOMPLETE;
        if (!streamEnded) return Completion.PROVISIONAL;
        return citationsValid && businessValid
                ? Completion.VALIDATED_FINAL : Completion.INVALID_FINAL;
    }

    static boolean disconnectProvesSideEffectRolledBack() {
        return false;
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) {
        int scenarios = 0;

        check(chooseTransport(false, false) == Transport.SSE); scenarios++;
        check(chooseTransport(true, false) == Transport.WEBSOCKET); scenarios++;
        check(chooseTransport(false, true) == Transport.WEBSOCKET); scenarios++;
        check(chooseTransport(true, true) == Transport.WEBSOCKET); scenarios++;

        check(encodeSse("token", "7", "hello").equals(
                "event: token\nid: 7\ndata: hello\n\n")); scenarios++;
        check(encodeSse("message", "8", "a\nb").equals(
                "event: message\nid: 8\ndata: a\ndata: b\n\n")); scenarios++;
        check(encodeSse("", "", "").equals("data: \n\n")); scenarios++;
        check(encodeSse("done", "9", "ok\r\nfinal").contains("data: final\n")); scenarios++;
        try { encodeSse("bad\nevent", "1", "x"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { encodeSse("token", "1\r2", "x"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }
        try { encodeSse("token", "1", "x\0y"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        check(resume(null, 5, 10) == Resume.START_LIVE); scenarios++;
        check(resume("", 5, 10) == Resume.START_LIVE); scenarios++;
        check(resume("10", 5, 10) == Resume.START_LIVE); scenarios++;
        check(resume("4", 5, 10) == Resume.REPLAY); scenarios++;
        check(resume("7", 5, 10) == Resume.REPLAY); scenarios++;
        check(resume("3", 5, 10) == Resume.RESET_REQUIRED); scenarios++;
        check(resume("11", 5, 10) == Resume.RESET_REQUIRED); scenarios++;
        check(resume("0", 5, 10) == Resume.BAD_CURSOR); scenarios++;
        check(resume("7x", 5, 10) == Resume.BAD_CURSOR); scenarios++;
        check(resume("999999999999999999999", 5, 10) == Resume.BAD_CURSOR); scenarios++;
        try { resume("1", 0, 4); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { scenarios++; }

        check(enqueue(0, 4) == Enqueue.ACCEPT); scenarios++;
        check(enqueue(3, 4) == Enqueue.ACCEPT); scenarios++;
        check(enqueue(4, 4) == Enqueue.OVERFLOW_CLOSE_1013); scenarios++;
        check(enqueue(7, 4) == Enqueue.OVERFLOW_CLOSE_1013); scenarios++;
        check(enqueue(-1, 4) == Enqueue.BAD_LIMIT); scenarios++;
        check(enqueue(0, 0) == Enqueue.BAD_LIMIT); scenarios++;

        check(completion(false, false, false, false, false) == Completion.PROVISIONAL); scenarios++;
        check(completion(false, false, true, true, true) == Completion.VALIDATED_FINAL); scenarios++;
        check(completion(false, false, true, false, true) == Completion.INVALID_FINAL); scenarios++;
        check(completion(false, false, true, true, false) == Completion.INVALID_FINAL); scenarios++;
        check(completion(false, true, false, false, false) == Completion.INCOMPLETE); scenarios++;
        check(completion(true, false, false, false, false) == Completion.ABANDONED); scenarios++;
        check(completion(true, true, true, true, true) == Completion.ABANDONED); scenarios++;
        check(!disconnectProvesSideEffectRolledBack()); scenarios++;

        long state = 0x51E5EEDL;
        int accepted = 0;
        for (int i = 0; i < 10_000; i++) {
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            int max = 1 + (int) ((state >>> 8) % 64);
            state = (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
            int queued = (int) ((state >>> 16) % 96);
            Enqueue outcome = enqueue(queued, max);
            check((outcome == Enqueue.ACCEPT) == (queued < max));
            if (outcome == Enqueue.ACCEPT) accepted++;
        }

        System.out.println("PARITY|ONE_WAY=SSE|DUPLEX=WEBSOCKET|RESUME=REPLAY"
                + "|OVERFLOW=CLOSE_1013|DISCONNECT=ABANDONED|FINAL=VALIDATED|ACCEPTED=" + accepted);
        System.out.println("Java streaming transport contracts passed: " + scenarios
                + " scenario groups + 10000 seeded trials");
    }
}
