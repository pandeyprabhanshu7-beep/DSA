public class ConcurrencyDataToolsContracts {
    private static int scenarios;

    enum Runtime { PYTHON, JAVA }
    enum WorkClass { ASYNC_IO, BLOCKING_IO, CPU_BOUND }
    enum Dispatch { EVENT_LOOP, BOUNDED_THREAD, VIRTUAL_THREAD, PROCESS_WORKER, REJECTED }
    enum RetryDecision { NOT_OWNER, NON_REPLAYABLE, EXHAUSTED, DEADLINE, RETRY }
    enum RetrievalDecision { READY, UNPUBLISHED, IDENTITY_MISMATCH, PREFILTER_REQUIRED, SCOPE_MISMATCH }
    enum ToolDecision { EXECUTE, REPLAY, CONFLICT, RECONCILE }
    enum LoopDecision { CONTINUE, WALL_LIMIT, TOKEN_LIMIT, CALL_LIMIT, REPEAT_LIMIT, TOOL_LIMIT }

    record LoopBudget(int wallUsed, int wallLimit, int tokensUsed, int tokenLimit,
                      int callsUsed, int callLimit, int repeatedCalls, int repeatLimit,
                      int toolCalls, int toolLimit) {}

    static Dispatch dispatch(Runtime runtime, WorkClass work, boolean dependencyPermit,
                             boolean queueSlot) {
        if (!dependencyPermit || !queueSlot) return Dispatch.REJECTED;
        if (work == WorkClass.ASYNC_IO) return Dispatch.EVENT_LOOP;
        if (work == WorkClass.CPU_BOUND) return Dispatch.PROCESS_WORKER;
        return runtime == Runtime.PYTHON ? Dispatch.BOUNDED_THREAD : Dispatch.VIRTUAL_THREAD;
    }

    static RetryDecision retry(boolean owner, boolean replaySafe, int attempt, int maxAttempts,
                               long nowMillis, long delayMillis, long deadlineMillis) {
        if (!owner) return RetryDecision.NOT_OWNER;
        if (!replaySafe) return RetryDecision.NON_REPLAYABLE;
        if (attempt >= maxAttempts) return RetryDecision.EXHAUSTED;
        if (delayMillis < 0 || nowMillis > deadlineMillis - delayMillis) return RetryDecision.DEADLINE;
        return RetryDecision.RETRY;
    }

    static RetrievalDecision retrieval(boolean published, boolean sameModel,
                                       boolean samePreprocessing, boolean tenantPrefilter,
                                       boolean everyResultInScope) {
        if (!published) return RetrievalDecision.UNPUBLISHED;
        if (!sameModel || !samePreprocessing) return RetrievalDecision.IDENTITY_MISMATCH;
        if (!tenantPrefilter) return RetrievalDecision.PREFILTER_REQUIRED;
        if (!everyResultInScope) return RetrievalDecision.SCOPE_MISMATCH;
        return RetrievalDecision.READY;
    }

    static ToolDecision tool(boolean recordExists, boolean samePayload,
                             boolean remoteOutcomeKnown) {
        if (!recordExists) return ToolDecision.EXECUTE;
        if (!samePayload) return ToolDecision.CONFLICT;
        return remoteOutcomeKnown ? ToolDecision.REPLAY : ToolDecision.RECONCILE;
    }

    static LoopDecision loop(LoopBudget budget) {
        if (budget.wallUsed() >= budget.wallLimit()) return LoopDecision.WALL_LIMIT;
        if (budget.tokensUsed() >= budget.tokenLimit()) return LoopDecision.TOKEN_LIMIT;
        if (budget.callsUsed() >= budget.callLimit()) return LoopDecision.CALL_LIMIT;
        if (budget.repeatedCalls() >= budget.repeatLimit()) return LoopDecision.REPEAT_LIMIT;
        if (budget.toolCalls() >= budget.toolLimit()) return LoopDecision.TOOL_LIMIT;
        return LoopDecision.CONTINUE;
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("check failed");
        scenarios++;
    }

    private static long next(long state) {
        return (state * 1_664_525L + 1_013_904_223L) & 0xffff_ffffL;
    }

    public static void main(String[] args) {
        check(dispatch(Runtime.PYTHON, WorkClass.ASYNC_IO, true, true) == Dispatch.EVENT_LOOP);
        check(dispatch(Runtime.PYTHON, WorkClass.BLOCKING_IO, true, true) == Dispatch.BOUNDED_THREAD);
        check(dispatch(Runtime.JAVA, WorkClass.BLOCKING_IO, true, true) == Dispatch.VIRTUAL_THREAD);
        check(dispatch(Runtime.PYTHON, WorkClass.CPU_BOUND, true, true) == Dispatch.PROCESS_WORKER);
        check(dispatch(Runtime.JAVA, WorkClass.CPU_BOUND, true, true) == Dispatch.PROCESS_WORKER);
        check(dispatch(Runtime.JAVA, WorkClass.BLOCKING_IO, false, true) == Dispatch.REJECTED);
        check(dispatch(Runtime.PYTHON, WorkClass.BLOCKING_IO, true, false) == Dispatch.REJECTED);

        check(retry(false, true, 0, 3, 100, 20, 200) == RetryDecision.NOT_OWNER);
        check(retry(true, false, 0, 3, 100, 20, 200) == RetryDecision.NON_REPLAYABLE);
        check(retry(true, true, 3, 3, 100, 20, 200) == RetryDecision.EXHAUSTED);
        check(retry(true, true, 1, 3, 190, 20, 200) == RetryDecision.DEADLINE);
        check(retry(true, true, 1, 3, 100, 20, 200) == RetryDecision.RETRY);
        check(retry(true, true, 1, 3, 100, -1, 200) == RetryDecision.DEADLINE);

        check(retrieval(false, true, true, true, true) == RetrievalDecision.UNPUBLISHED);
        check(retrieval(true, false, true, true, true) == RetrievalDecision.IDENTITY_MISMATCH);
        check(retrieval(true, true, false, true, true) == RetrievalDecision.IDENTITY_MISMATCH);
        check(retrieval(true, true, true, false, true) == RetrievalDecision.PREFILTER_REQUIRED);
        check(retrieval(true, true, true, true, false) == RetrievalDecision.SCOPE_MISMATCH);
        check(retrieval(true, true, true, true, true) == RetrievalDecision.READY);

        check(tool(false, true, true) == ToolDecision.EXECUTE);
        check(tool(true, true, true) == ToolDecision.REPLAY);
        check(tool(true, false, true) == ToolDecision.CONFLICT);
        check(tool(true, true, false) == ToolDecision.RECONCILE);

        check(loop(new LoopBudget(1, 10, 1, 10, 1, 10, 1, 10, 1, 10)) == LoopDecision.CONTINUE);
        check(loop(new LoopBudget(10, 10, 1, 10, 1, 10, 1, 10, 1, 10)) == LoopDecision.WALL_LIMIT);
        check(loop(new LoopBudget(1, 10, 10, 10, 1, 10, 1, 10, 1, 10)) == LoopDecision.TOKEN_LIMIT);
        check(loop(new LoopBudget(1, 10, 1, 10, 10, 10, 1, 10, 1, 10)) == LoopDecision.CALL_LIMIT);
        check(loop(new LoopBudget(1, 10, 1, 10, 1, 10, 10, 10, 1, 10)) == LoopDecision.REPEAT_LIMIT);
        check(loop(new LoopBudget(1, 10, 1, 10, 1, 10, 1, 10, 10, 10)) == LoopDecision.TOOL_LIMIT);

        long state = 0xC022A11DL;
        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); boolean owner = (state & 1) != 0;
            state = next(state); boolean safe = (state & 1) != 0;
            state = next(state); int attempt = (int) (state % 5);
            state = next(state); int max = 1 + (int) (state % 4);
            state = next(state); long now = state % 1_000;
            state = next(state); long delay = state % 300;
            state = next(state); long deadline = state % 1_300;
            RetryDecision expected = !owner ? RetryDecision.NOT_OWNER
                    : !safe ? RetryDecision.NON_REPLAYABLE
                    : attempt >= max ? RetryDecision.EXHAUSTED
                    : now > deadline - delay ? RetryDecision.DEADLINE : RetryDecision.RETRY;
            assert retry(owner, safe, attempt, max, now, delay, deadline) == expected;
        }

        for (int trial = 0; trial < 10_000; trial++) {
            state = next(state); boolean published = (state & 1) != 0;
            state = next(state); boolean model = (state & 1) != 0;
            state = next(state); boolean preprocessing = (state & 1) != 0;
            state = next(state); boolean prefilter = (state & 1) != 0;
            state = next(state); boolean scoped = (state & 1) != 0;
            RetrievalDecision expected = !published ? RetrievalDecision.UNPUBLISHED
                    : !(model && preprocessing) ? RetrievalDecision.IDENTITY_MISMATCH
                    : !prefilter ? RetrievalDecision.PREFILTER_REQUIRED
                    : !scoped ? RetrievalDecision.SCOPE_MISMATCH : RetrievalDecision.READY;
            assert retrieval(published, model, preprocessing, prefilter, scoped) == expected;
        }

        assert scenarios == 29 : "scenario count " + scenarios;
        System.out.println("PARITY|PY_BLOCKING=BOUNDED_THREAD|JAVA_BLOCKING=VIRTUAL_THREAD|DEPENDENCY=GATED|RETRY=DEADLINE_BOUND|MIGRATION=IDENTITY_PINNED|ANN=PREFILTERED|TOOL=RECONCILE|LOOP=BOUNDED");
        System.out.println("Concurrency/data/tools contracts passed: 29 scenario groups + 20000 seeded trials");
    }
}
