from dataclasses import dataclass
from enum import Enum, auto


class CacheDecision(Enum):
    HIT = auto()
    SCOPE_MISS = auto()
    REVISION_MISS = auto()
    REVOKED = auto()


class ToolDecision(Enum):
    EXECUTE = auto()
    INVALID_IDENTITY = auto()
    DENY_SCOPE = auto()
    INVALID_PAYLOAD = auto()


class StreamDecision(Enum):
    OPEN = auto()
    COMPLETE = auto()
    FAILED = auto()
    INCOMPLETE = auto()


class NetworkDecision(Enum):
    READY = auto()
    WRONG_NAMESPACE = auto()
    DNS = auto()
    TCP = auto()
    AUTH = auto()
    PROTOCOL = auto()


class PackStatus(Enum):
    READY = auto()
    NO_EVIDENCE = auto()
    INVALID = auto()
    OVER_BUDGET = auto()


class IngestDecision(Enum):
    UPSERT = auto()
    NOOP = auto()
    NEW_GENERATION = auto()
    REJECT_UNSTABLE_ID = auto()


@dataclass(frozen=True)
class PackResult:
    status: PackStatus
    used: int
    selected: int
    skipped: int


def cache(same_tenant: bool, same_authorization_revision: bool,
          same_index_generation: bool, evidence_visible: bool) -> CacheDecision:
    if not same_tenant:
        return CacheDecision.SCOPE_MISS
    if not same_authorization_revision or not same_index_generation:
        return CacheDecision.REVISION_MISS
    if not evidence_visible:
        return CacheDecision.REVOKED
    return CacheDecision.HIT


def tool(identity_verified: bool, account_in_trusted_scope: bool,
         payload_valid: bool) -> ToolDecision:
    if not identity_verified:
        return ToolDecision.INVALID_IDENTITY
    if not account_in_trusted_scope:
        return ToolDecision.DENY_SCOPE
    return ToolDecision.EXECUTE if payload_valid else ToolDecision.INVALID_PAYLOAD


def stream(complete_event: bool, failed_event: bool,
           connection_closed: bool) -> StreamDecision:
    if complete_event and failed_event:
        raise ValueError("conflicting terminal events")
    if complete_event:
        return StreamDecision.COMPLETE
    if failed_event:
        return StreamDecision.FAILED
    return StreamDecision.INCOMPLETE if connection_closed else StreamDecision.OPEN


def network(localhost: bool, same_pod: bool, dns: bool, tcp: bool,
            authenticated: bool, protocol: bool) -> NetworkDecision:
    if localhost and not same_pod:
        return NetworkDecision.WRONG_NAMESPACE
    if not localhost and not dns:
        return NetworkDecision.DNS
    if not tcp:
        return NetworkDecision.TCP
    if not authenticated:
        return NetworkDecision.AUTH
    return NetworkDecision.READY if protocol else NetworkDecision.PROTOCOL


def pack(context_window: int, fixed_reservations: int, question_tokens: int,
         *passage_tokens: int) -> PackResult:
    if (context_window <= 0 or fixed_reservations < 0 or question_tokens <= 0
            or any(value <= 0 for value in passage_tokens)):
        return PackResult(PackStatus.INVALID, 0, 0, 0)
    base = fixed_reservations + question_tokens
    if base > context_window:
        return PackResult(PackStatus.OVER_BUDGET, 0, 0, 0)
    used = base
    selected = 0
    skipped = 0
    for tokens in passage_tokens:
        if tokens <= context_window - used:
            used += tokens
            selected += 1
        else:
            skipped += 1
    status = PackStatus.READY if selected else PackStatus.NO_EVIDENCE
    return PackResult(status, used, selected, skipped)


def ingest(deterministic_id: bool, same_source_revision: bool,
           existing_same_content: bool) -> IngestDecision:
    if not deterministic_id:
        return IngestDecision.REJECT_UNSTABLE_ID
    if not same_source_revision:
        return IngestDecision.NEW_GENERATION
    return IngestDecision.NOOP if existing_same_content else IngestDecision.UPSERT


def next_state(state: int) -> int:
    return (state * 1_664_525 + 1_013_904_223) & 0xFFFF_FFFF


def main() -> None:
    scenarios = 0

    def check(condition: bool) -> None:
        nonlocal scenarios
        assert condition
        scenarios += 1

    check(cache(False, True, True, True) is CacheDecision.SCOPE_MISS)
    check(cache(True, False, True, True) is CacheDecision.REVISION_MISS)
    check(cache(True, True, False, True) is CacheDecision.REVISION_MISS)
    check(cache(True, True, True, False) is CacheDecision.REVOKED)
    check(cache(True, True, True, True) is CacheDecision.HIT)

    check(tool(False, True, True) is ToolDecision.INVALID_IDENTITY)
    check(tool(True, False, True) is ToolDecision.DENY_SCOPE)
    check(tool(True, True, False) is ToolDecision.INVALID_PAYLOAD)
    check(tool(True, True, True) is ToolDecision.EXECUTE)

    check(stream(False, False, False) is StreamDecision.OPEN)
    check(stream(False, False, True) is StreamDecision.INCOMPLETE)
    check(stream(True, False, True) is StreamDecision.COMPLETE)
    check(stream(False, True, True) is StreamDecision.FAILED)
    try:
        stream(True, True, False)
        check(False)
    except ValueError:
        check(True)

    check(network(True, False, False, False, False, False) is NetworkDecision.WRONG_NAMESPACE)
    check(network(False, False, False, False, False, False) is NetworkDecision.DNS)
    check(network(False, False, True, False, False, False) is NetworkDecision.TCP)
    check(network(False, False, True, True, False, False) is NetworkDecision.AUTH)
    check(network(False, False, True, True, True, False) is NetworkDecision.PROTOCOL)
    check(network(False, False, True, True, True, True) is NetworkDecision.READY)
    check(network(True, True, False, True, True, True) is NetworkDecision.READY)

    check(pack(100, 20, 10, 30, 40) == PackResult(PackStatus.READY, 100, 2, 0))
    check(pack(100, 20, 10, 80, 20) == PackResult(PackStatus.READY, 50, 1, 1))
    check(pack(100, 20, 10, 80) == PackResult(PackStatus.NO_EVIDENCE, 30, 0, 1))
    check(pack(100, 95, 10, 1).status is PackStatus.OVER_BUDGET)
    check(pack(0, 0, 1, 1).status is PackStatus.INVALID)
    check(pack(100, 0, 10, 0).status is PackStatus.INVALID)

    check(ingest(False, True, False) is IngestDecision.REJECT_UNSTABLE_ID)
    check(ingest(True, False, False) is IngestDecision.NEW_GENERATION)
    check(ingest(True, True, True) is IngestDecision.NOOP)
    check(ingest(True, True, False) is IngestDecision.UPSERT)

    state = 0xD23B6A11
    for _ in range(10_000):
        state = next_state(state); tenant = bool(state & 1)
        state = next_state(state); authorization = bool(state & 1)
        state = next_state(state); generation = bool(state & 1)
        state = next_state(state); visible = bool(state & 1)
        expected = (CacheDecision.SCOPE_MISS if not tenant else
                    CacheDecision.REVISION_MISS if not (authorization and generation) else
                    CacheDecision.REVOKED if not visible else CacheDecision.HIT)
        assert cache(tenant, authorization, generation, visible) is expected

    for _ in range(10_000):
        state = next_state(state); stable = bool(state & 1)
        state = next_state(state); revision = bool(state & 1)
        state = next_state(state); same = bool(state & 1)
        expected = (IngestDecision.REJECT_UNSTABLE_ID if not stable else
                    IngestDecision.NEW_GENERATION if not revision else
                    IngestDecision.NOOP if same else IngestDecision.UPSERT)
        assert ingest(stable, revision, same) is expected

    assert scenarios == 31, scenarios
    print("PARITY|CACHE=SCOPED|STREAM=TERMINAL_EVENT|NETWORK=LAYERED|PACK=QUESTION_RESERVED|INGEST=IDEMPOTENT|TOOL=AUTHORIZED")
    print("Debugging scenarios contracts passed: 31 scenario groups + 20000 seeded trials")


if __name__ == "__main__":
    main()
