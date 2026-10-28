"""Dependency-free contracts for SSE answer completion, replay and cleanup."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from enum import Enum


class Outcome(Enum):
    OPEN = "OPEN"
    COMPLETE = "COMPLETE"
    ERROR = "ERROR"
    INCOMPLETE = "INCOMPLETE"


class Replay(Enum):
    LIVE = "LIVE"
    REPLAY = "REPLAY"
    RESET_REQUIRED = "RESET_REQUIRED"
    BAD_CURSOR = "BAD_CURSOR"


@dataclass
class StreamState:
    outcome: Outcome = Outcome.OPEN
    last_sequence: int = 0

    def data(self, sequence: int) -> None:
        if self.outcome is not Outcome.OPEN or sequence != self.last_sequence + 1:
            raise RuntimeError("illegal data transition")
        self.last_sequence = sequence

    def terminal(self, sequence: int, terminal: Outcome) -> None:
        if terminal not in (Outcome.COMPLETE, Outcome.ERROR):
            raise ValueError("terminal")
        if self.outcome is not Outcome.OPEN or sequence != self.last_sequence + 1:
            raise RuntimeError("illegal terminal transition")
        self.last_sequence = sequence
        self.outcome = terminal

    def close(self) -> None:
        if self.outcome is Outcome.OPEN:
            self.outcome = Outcome.INCOMPLETE


@dataclass
class Permit:
    released: bool = False
    releases: int = 0

    def release(self) -> bool:
        if self.released:
            return False
        self.released = True
        self.releases += 1
        return True


class ActionLedger:
    def __init__(self) -> None:
        self._applied: set[str] = set()

    def apply(self, action_id: str) -> bool:
        if action_id is None or not action_id.strip():
            raise ValueError("actionId")
        if action_id in self._applied:
            return False
        self._applied.add(action_id)
        return True


def replay(last_event_id: str | None, first_retained: int, latest: int) -> Replay:
    if first_retained < 1 or latest < first_retained:
        raise ValueError("window")
    if last_event_id is None or last_event_id == "":
        return Replay.LIVE
    if not last_event_id.isascii() or not last_event_id.isdigit() or last_event_id.startswith("0"):
        return Replay.BAD_CURSOR
    cursor = int(last_event_id)
    if cursor > 9_223_372_036_854_775_807:
        return Replay.BAD_CURSOR
    if cursor < first_retained - 1 or cursor > latest:
        return Replay.RESET_REQUIRED
    return Replay.LIVE if cursor == latest else Replay.REPLAY


def frame(event: str, event_id: int, key: str, value: str) -> str:
    if not re.fullmatch(r"[a-z][a-z0-9_-]*", event) or event_id < 1 or not key:
        raise ValueError("frame field")
    body = json.dumps({key: value}, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event}\nid: {event_id}\ndata: {body}\n\n"


def main() -> None:
    scenarios = 0

    checks = [
        frame("delta", 1, "text", "a\nb") == 'event: delta\nid: 1\ndata: {"text":"a\\nb"}\n\n',
        '"status":"ok"' in frame("complete", 2, "status", "ok"),
        'bad\\\\\\"quote' in frame("error", 3, "code", 'bad\\"quote'),
    ]
    for check in checks:
        assert check
        scenarios += 1
    for args in [("bad\nevent", 1, "x", "y"), ("delta", 0, "x", "y")]:
        try:
            frame(*args)
            raise AssertionError
        except ValueError:
            scenarios += 1

    complete = StreamState()
    complete.data(1); complete.data(2); complete.terminal(3, Outcome.COMPLETE); complete.close()
    assert complete.outcome is Outcome.COMPLETE and complete.last_sequence == 3; scenarios += 1
    failed = StreamState()
    failed.data(1); failed.terminal(2, Outcome.ERROR); failed.close()
    assert failed.outcome is Outcome.ERROR; scenarios += 1
    truncated = StreamState()
    truncated.data(1); truncated.close()
    assert truncated.outcome is Outcome.INCOMPLETE; scenarios += 1
    empty = StreamState(); empty.close()
    assert empty.outcome is Outcome.INCOMPLETE; scenarios += 1
    try:
        complete.terminal(4, Outcome.ERROR)
        raise AssertionError
    except RuntimeError:
        scenarios += 1
    try:
        truncated.data(2)
        raise AssertionError
    except RuntimeError:
        scenarios += 1
    try:
        StreamState().data(2)
        raise AssertionError
    except RuntimeError:
        scenarios += 1

    checks = [
        replay(None, 5, 10) is Replay.LIVE,
        replay("10", 5, 10) is Replay.LIVE,
        replay("4", 5, 10) is Replay.REPLAY,
        replay("7", 5, 10) is Replay.REPLAY,
        replay("3", 5, 10) is Replay.RESET_REQUIRED,
        replay("11", 5, 10) is Replay.RESET_REQUIRED,
        replay("0", 5, 10) is Replay.BAD_CURSOR,
        replay("7x", 5, 10) is Replay.BAD_CURSOR,
    ]
    for check in checks:
        assert check
        scenarios += 1

    actions = ActionLedger()
    assert actions.apply("tool-r42-1"); scenarios += 1
    assert not actions.apply("tool-r42-1"); scenarios += 1
    assert actions.apply("tool-r42-2"); scenarios += 1
    try:
        actions.apply(" ")
        raise AssertionError
    except ValueError:
        scenarios += 1

    permit = Permit()
    assert permit.release(); scenarios += 1
    assert not permit.release(); scenarios += 1
    assert not permit.release(); scenarios += 1
    assert permit.releases == 1; scenarios += 1
    timeout_permit = Permit()
    assert timeout_permit.release() and not timeout_permit.release() and timeout_permit.releases == 1
    scenarios += 1
    assert Permit().releases == 0; scenarios += 1

    state = 0x5EED18
    complete_count = 0
    for _ in range(10_000):
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        deltas = (state >> 8) % 8
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        terminal = (state >> 16) % 3
        stream = StreamState()
        for sequence in range(1, deltas + 1):
            stream.data(sequence)
        if terminal == 1:
            stream.terminal(deltas + 1, Outcome.COMPLETE)
        if terminal == 2:
            stream.terminal(deltas + 1, Outcome.ERROR)
        stream.close()
        expected = Outcome.COMPLETE if terminal == 1 else Outcome.ERROR if terminal == 2 else Outcome.INCOMPLETE
        assert stream.outcome is expected
        trial_permit = Permit()
        assert trial_permit.release() and not trial_permit.release() and trial_permit.releases == 1
        complete_count += int(expected is Outcome.COMPLETE)

    print("PARITY|FRAMING=SSE_JSON|TERMINAL=EXACTLY_ONCE|CLOSE=INCOMPLETE"
          f"|REPLAY=ORDERED|ACTION=IDEMPOTENT|PERMIT=RELEASE_ONCE|COMPLETE={complete_count}")
    print(f"Python SSE completion contracts passed: {scenarios} scenario groups + 10000 seeded trials")


if __name__ == "__main__":
    main()
