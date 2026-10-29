from dataclasses import dataclass
from enum import Enum, auto


class Runtime(Enum):
    PYTHON = auto()
    JAVA = auto()


class WorkClass(Enum):
    ASYNC_IO = auto()
    BLOCKING_IO = auto()
    CPU_BOUND = auto()


class Dispatch(Enum):
    EVENT_LOOP = auto()
    BOUNDED_THREAD = auto()
    VIRTUAL_THREAD = auto()
    PROCESS_WORKER = auto()
    REJECTED = auto()


class RetryDecision(Enum):
    NOT_OWNER = auto()
    NON_REPLAYABLE = auto()
    EXHAUSTED = auto()
    DEADLINE = auto()
    RETRY = auto()


class RetrievalDecision(Enum):
    READY = auto()
    UNPUBLISHED = auto()
    IDENTITY_MISMATCH = auto()
    PREFILTER_REQUIRED = auto()
    SCOPE_MISMATCH = auto()


class ToolDecision(Enum):
    EXECUTE = auto()
    REPLAY = auto()
    CONFLICT = auto()
    RECONCILE = auto()


class LoopDecision(Enum):
    CONTINUE = auto()
    WALL_LIMIT = auto()
    TOKEN_LIMIT = auto()
    CALL_LIMIT = auto()
    REPEAT_LIMIT = auto()
    TOOL_LIMIT = auto()


@dataclass(frozen=True)
class LoopBudget:
    wall_used: int
    wall_limit: int
    tokens_used: int
    token_limit: int
    calls_used: int
    call_limit: int
    repeated_calls: int
    repeat_limit: int
    tool_calls: int
    tool_limit: int


def dispatch(runtime: Runtime, work: WorkClass, dependency_permit: bool, queue_slot: bool) -> Dispatch:
    if not dependency_permit or not queue_slot:
        return Dispatch.REJECTED
    if work is WorkClass.ASYNC_IO:
        return Dispatch.EVENT_LOOP
    if work is WorkClass.CPU_BOUND:
        return Dispatch.PROCESS_WORKER
    return Dispatch.BOUNDED_THREAD if runtime is Runtime.PYTHON else Dispatch.VIRTUAL_THREAD


def retry(owner: bool, replay_safe: bool, attempt: int, max_attempts: int,
          now_ms: int, delay_ms: int, deadline_ms: int) -> RetryDecision:
    if not owner:
        return RetryDecision.NOT_OWNER
    if not replay_safe:
        return RetryDecision.NON_REPLAYABLE
    if attempt >= max_attempts:
        return RetryDecision.EXHAUSTED
    if delay_ms < 0 or now_ms > deadline_ms - delay_ms:
        return RetryDecision.DEADLINE
    return RetryDecision.RETRY


def retrieval(published: bool, same_model: bool, same_preprocessing: bool,
              tenant_prefilter: bool, every_result_in_scope: bool) -> RetrievalDecision:
    if not published:
        return RetrievalDecision.UNPUBLISHED
    if not same_model or not same_preprocessing:
        return RetrievalDecision.IDENTITY_MISMATCH
    if not tenant_prefilter:
        return RetrievalDecision.PREFILTER_REQUIRED
    if not every_result_in_scope:
        return RetrievalDecision.SCOPE_MISMATCH
    return RetrievalDecision.READY


def tool(record_exists: bool, same_payload: bool, remote_outcome_known: bool) -> ToolDecision:
    if not record_exists:
        return ToolDecision.EXECUTE
    if not same_payload:
        return ToolDecision.CONFLICT
    return ToolDecision.REPLAY if remote_outcome_known else ToolDecision.RECONCILE


def loop(budget: LoopBudget) -> LoopDecision:
    if budget.wall_used >= budget.wall_limit:
        return LoopDecision.WALL_LIMIT
    if budget.tokens_used >= budget.token_limit:
        return LoopDecision.TOKEN_LIMIT
    if budget.calls_used >= budget.call_limit:
        return LoopDecision.CALL_LIMIT
    if budget.repeated_calls >= budget.repeat_limit:
        return LoopDecision.REPEAT_LIMIT
    if budget.tool_calls >= budget.tool_limit:
        return LoopDecision.TOOL_LIMIT
    return LoopDecision.CONTINUE


def next_state(state: int) -> int:
    return (state * 1_664_525 + 1_013_904_223) & 0xFFFF_FFFF


def main() -> None:
    scenarios = 0

    def check(condition: bool) -> None:
        nonlocal scenarios
        assert condition
        scenarios += 1

    check(dispatch(Runtime.PYTHON, WorkClass.ASYNC_IO, True, True) is Dispatch.EVENT_LOOP)
    check(dispatch(Runtime.PYTHON, WorkClass.BLOCKING_IO, True, True) is Dispatch.BOUNDED_THREAD)
    check(dispatch(Runtime.JAVA, WorkClass.BLOCKING_IO, True, True) is Dispatch.VIRTUAL_THREAD)
    check(dispatch(Runtime.PYTHON, WorkClass.CPU_BOUND, True, True) is Dispatch.PROCESS_WORKER)
    check(dispatch(Runtime.JAVA, WorkClass.CPU_BOUND, True, True) is Dispatch.PROCESS_WORKER)
    check(dispatch(Runtime.JAVA, WorkClass.BLOCKING_IO, False, True) is Dispatch.REJECTED)
    check(dispatch(Runtime.PYTHON, WorkClass.BLOCKING_IO, True, False) is Dispatch.REJECTED)

    check(retry(False, True, 0, 3, 100, 20, 200) is RetryDecision.NOT_OWNER)
    check(retry(True, False, 0, 3, 100, 20, 200) is RetryDecision.NON_REPLAYABLE)
    check(retry(True, True, 3, 3, 100, 20, 200) is RetryDecision.EXHAUSTED)
    check(retry(True, True, 1, 3, 190, 20, 200) is RetryDecision.DEADLINE)
    check(retry(True, True, 1, 3, 100, 20, 200) is RetryDecision.RETRY)
    check(retry(True, True, 1, 3, 100, -1, 200) is RetryDecision.DEADLINE)

    check(retrieval(False, True, True, True, True) is RetrievalDecision.UNPUBLISHED)
    check(retrieval(True, False, True, True, True) is RetrievalDecision.IDENTITY_MISMATCH)
    check(retrieval(True, True, False, True, True) is RetrievalDecision.IDENTITY_MISMATCH)
    check(retrieval(True, True, True, False, True) is RetrievalDecision.PREFILTER_REQUIRED)
    check(retrieval(True, True, True, True, False) is RetrievalDecision.SCOPE_MISMATCH)
    check(retrieval(True, True, True, True, True) is RetrievalDecision.READY)

    check(tool(False, True, True) is ToolDecision.EXECUTE)
    check(tool(True, True, True) is ToolDecision.REPLAY)
    check(tool(True, False, True) is ToolDecision.CONFLICT)
    check(tool(True, True, False) is ToolDecision.RECONCILE)

    check(loop(LoopBudget(1, 10, 1, 10, 1, 10, 1, 10, 1, 10)) is LoopDecision.CONTINUE)
    check(loop(LoopBudget(10, 10, 1, 10, 1, 10, 1, 10, 1, 10)) is LoopDecision.WALL_LIMIT)
    check(loop(LoopBudget(1, 10, 10, 10, 1, 10, 1, 10, 1, 10)) is LoopDecision.TOKEN_LIMIT)
    check(loop(LoopBudget(1, 10, 1, 10, 10, 10, 1, 10, 1, 10)) is LoopDecision.CALL_LIMIT)
    check(loop(LoopBudget(1, 10, 1, 10, 1, 10, 10, 10, 1, 10)) is LoopDecision.REPEAT_LIMIT)
    check(loop(LoopBudget(1, 10, 1, 10, 1, 10, 1, 10, 10, 10)) is LoopDecision.TOOL_LIMIT)

    state = 0xC022A11D
    for _ in range(10_000):
        state = next_state(state); owner = bool(state & 1)
        state = next_state(state); safe = bool(state & 1)
        state = next_state(state); attempt = state % 5
        state = next_state(state); maximum = 1 + state % 4
        state = next_state(state); now = state % 1_000
        state = next_state(state); delay = state % 300
        state = next_state(state); deadline = state % 1_300
        expected = (RetryDecision.NOT_OWNER if not owner else
                    RetryDecision.NON_REPLAYABLE if not safe else
                    RetryDecision.EXHAUSTED if attempt >= maximum else
                    RetryDecision.DEADLINE if now > deadline - delay else RetryDecision.RETRY)
        assert retry(owner, safe, attempt, maximum, now, delay, deadline) is expected

    for _ in range(10_000):
        state = next_state(state); published = bool(state & 1)
        state = next_state(state); model = bool(state & 1)
        state = next_state(state); preprocessing = bool(state & 1)
        state = next_state(state); prefilter = bool(state & 1)
        state = next_state(state); scoped = bool(state & 1)
        expected = (RetrievalDecision.UNPUBLISHED if not published else
                    RetrievalDecision.IDENTITY_MISMATCH if not (model and preprocessing) else
                    RetrievalDecision.PREFILTER_REQUIRED if not prefilter else
                    RetrievalDecision.SCOPE_MISMATCH if not scoped else RetrievalDecision.READY)
        assert retrieval(published, model, preprocessing, prefilter, scoped) is expected

    assert scenarios == 29, scenarios
    print("PARITY|PY_BLOCKING=BOUNDED_THREAD|JAVA_BLOCKING=VIRTUAL_THREAD|DEPENDENCY=GATED|RETRY=DEADLINE_BOUND|MIGRATION=IDENTITY_PINNED|ANN=PREFILTERED|TOOL=RECONCILE|LOOP=BOUNDED")
    print("Concurrency/data/tools contracts passed: 29 scenario groups + 20000 seeded trials")


if __name__ == "__main__":
    main()
