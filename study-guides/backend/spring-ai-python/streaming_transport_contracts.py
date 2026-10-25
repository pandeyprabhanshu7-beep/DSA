"""Dependency-free contracts for SSE/WebSocket streaming, replay and completion."""

from __future__ import annotations

from enum import Enum


class Transport(Enum):
    SSE = "SSE"
    WEBSOCKET = "WEBSOCKET"


class Resume(Enum):
    START_LIVE = "START_LIVE"
    REPLAY = "REPLAY"
    RESET_REQUIRED = "RESET_REQUIRED"
    BAD_CURSOR = "BAD_CURSOR"


class Enqueue(Enum):
    ACCEPT = "ACCEPT"
    OVERFLOW_CLOSE_1013 = "OVERFLOW_CLOSE_1013"
    BAD_LIMIT = "BAD_LIMIT"


class Completion(Enum):
    PROVISIONAL = "PROVISIONAL"
    VALIDATED_FINAL = "VALIDATED_FINAL"
    INVALID_FINAL = "INVALID_FINAL"
    INCOMPLETE = "INCOMPLETE"
    ABANDONED = "ABANDONED"


def choose_transport(client_commands: bool, binary_messages: bool) -> Transport:
    return Transport.WEBSOCKET if client_commands or binary_messages else Transport.SSE


def _require_field(name: str, value: str) -> None:
    if "\n" in value or "\r" in value or "\0" in value:
        raise ValueError(name)


def encode_sse(event: str, event_id: str, data: str) -> str:
    _require_field("event", event)
    _require_field("id", event_id)
    if "\0" in data:
        raise ValueError("NUL data")
    normalized = data.replace("\r\n", "\n").replace("\r", "\n")
    output = []
    if event:
        output.append(f"event: {event}\n")
    if event_id:
        output.append(f"id: {event_id}\n")
    output.extend(f"data: {line}\n" for line in normalized.split("\n"))
    output.append("\n")
    return "".join(output)


def resume(last_event_id: str | None, first_retained: int, latest: int) -> Resume:
    if first_retained < 1 or latest < first_retained:
        raise ValueError("window")
    if last_event_id is None or last_event_id == "":
        return Resume.START_LIVE
    if not last_event_id.isascii() or not last_event_id.isdigit() or last_event_id.startswith("0"):
        return Resume.BAD_CURSOR
    cursor = int(last_event_id)
    if cursor > 9_223_372_036_854_775_807:
        return Resume.BAD_CURSOR
    if cursor < first_retained - 1 or cursor > latest:
        return Resume.RESET_REQUIRED
    return Resume.START_LIVE if cursor == latest else Resume.REPLAY


def enqueue(queued_frames: int, max_frames: int) -> Enqueue:
    if queued_frames < 0 or max_frames < 1:
        return Enqueue.BAD_LIMIT
    return Enqueue.ACCEPT if queued_frames < max_frames else Enqueue.OVERFLOW_CLOSE_1013


def completion(disconnected: bool, budget_exceeded: bool, stream_ended: bool,
               citations_valid: bool, business_valid: bool) -> Completion:
    if disconnected:
        return Completion.ABANDONED
    if budget_exceeded:
        return Completion.INCOMPLETE
    if not stream_ended:
        return Completion.PROVISIONAL
    return Completion.VALIDATED_FINAL if citations_valid and business_valid else Completion.INVALID_FINAL


def disconnect_proves_side_effect_rolled_back() -> bool:
    return False


def main() -> None:
    scenarios = 0

    checks = [
        choose_transport(False, False) is Transport.SSE,
        choose_transport(True, False) is Transport.WEBSOCKET,
        choose_transport(False, True) is Transport.WEBSOCKET,
        choose_transport(True, True) is Transport.WEBSOCKET,
        encode_sse("token", "7", "hello") == "event: token\nid: 7\ndata: hello\n\n",
        encode_sse("message", "8", "a\nb") == "event: message\nid: 8\ndata: a\ndata: b\n\n",
        encode_sse("", "", "") == "data: \n\n",
        "data: final\n" in encode_sse("done", "9", "ok\r\nfinal"),
    ]
    for check in checks:
        assert check
        scenarios += 1
    for args in [("bad\nevent", "1", "x"), ("token", "1\r2", "x"), ("token", "1", "x\0y")]:
        try:
            encode_sse(*args)
            raise AssertionError
        except ValueError:
            scenarios += 1

    checks = [
        resume(None, 5, 10) is Resume.START_LIVE,
        resume("", 5, 10) is Resume.START_LIVE,
        resume("10", 5, 10) is Resume.START_LIVE,
        resume("4", 5, 10) is Resume.REPLAY,
        resume("7", 5, 10) is Resume.REPLAY,
        resume("3", 5, 10) is Resume.RESET_REQUIRED,
        resume("11", 5, 10) is Resume.RESET_REQUIRED,
        resume("0", 5, 10) is Resume.BAD_CURSOR,
        resume("7x", 5, 10) is Resume.BAD_CURSOR,
        resume("999999999999999999999", 5, 10) is Resume.BAD_CURSOR,
    ]
    for check in checks:
        assert check
        scenarios += 1
    try:
        resume("1", 0, 4)
        raise AssertionError
    except ValueError:
        scenarios += 1

    checks = [
        enqueue(0, 4) is Enqueue.ACCEPT,
        enqueue(3, 4) is Enqueue.ACCEPT,
        enqueue(4, 4) is Enqueue.OVERFLOW_CLOSE_1013,
        enqueue(7, 4) is Enqueue.OVERFLOW_CLOSE_1013,
        enqueue(-1, 4) is Enqueue.BAD_LIMIT,
        enqueue(0, 0) is Enqueue.BAD_LIMIT,
        completion(False, False, False, False, False) is Completion.PROVISIONAL,
        completion(False, False, True, True, True) is Completion.VALIDATED_FINAL,
        completion(False, False, True, False, True) is Completion.INVALID_FINAL,
        completion(False, False, True, True, False) is Completion.INVALID_FINAL,
        completion(False, True, False, False, False) is Completion.INCOMPLETE,
        completion(True, False, False, False, False) is Completion.ABANDONED,
        completion(True, True, True, True, True) is Completion.ABANDONED,
        not disconnect_proves_side_effect_rolled_back(),
    ]
    for check in checks:
        assert check
        scenarios += 1

    state = 0x51E5EED
    accepted = 0
    for _ in range(10_000):
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        maximum = 1 + ((state >> 8) % 64)
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        queued = (state >> 16) % 96
        outcome = enqueue(queued, maximum)
        assert (outcome is Enqueue.ACCEPT) == (queued < maximum)
        accepted += int(outcome is Enqueue.ACCEPT)

    print(f"PARITY|ONE_WAY=SSE|DUPLEX=WEBSOCKET|RESUME=REPLAY|OVERFLOW=CLOSE_1013|DISCONNECT=ABANDONED|FINAL=VALIDATED|ACCEPTED={accepted}")
    print(f"Python streaming transport contracts passed: {scenarios} scenario groups + 10000 seeded trials")


if __name__ == "__main__":
    main()
