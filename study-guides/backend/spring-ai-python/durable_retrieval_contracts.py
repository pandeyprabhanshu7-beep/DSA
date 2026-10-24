"""Dependency-free parity contracts for generation-safe, scoped durable retrieval."""

from dataclasses import dataclass
from enum import Enum
import math
import random


class State(Enum):
    BUILDING = "building"
    READY = "ready"
    ACTIVE = "active"
    RETIRED = "retired"


@dataclass(frozen=True)
class Generation:
    id: str
    version: int
    state: State
    dimensions: int
    expected_chunks: int


@dataclass(frozen=True)
class Chunk:
    tenant: str
    generation: str
    id: str
    revision: int
    vector: tuple[float, ...]


@dataclass(frozen=True)
class Ranked:
    id: str
    score: float


@dataclass(frozen=True)
class QueryPlan:
    sql: str
    parameters: tuple[object, ...]
    statement_timeout_ms: int


scenario_checks = 0


def check(condition: bool) -> None:
    global scenario_checks
    if not condition:
        raise AssertionError("scenario check failed")
    scenario_checks += 1


def rejects(action) -> None:
    global scenario_checks
    try:
        action()
    except (ValueError, RuntimeError, OverflowError):
        scenario_checks += 1
        return
    raise AssertionError("expected rejection")


def promote(current: Generation, candidate: Generation, actual_chunks: int) -> Generation:
    if (
        candidate.state is not State.READY
        or candidate.version <= current.version
        or candidate.dimensions < 1
        or actual_chunks != candidate.expected_chunks
    ):
        raise ValueError("candidate is not promotable")
    return Generation(candidate.id, candidate.version, State.ACTIVE, candidate.dimensions, candidate.expected_chunks)


def vector_literal(vector: tuple[float, ...], dimensions: int) -> str:
    if len(vector) != dimensions or dimensions < 1 or not all(math.isfinite(value) for value in vector):
        raise ValueError("invalid dimensions or coordinates")
    if not any(value != 0.0 for value in vector):
        raise ValueError("zero vector")
    return "[" + ",".join(format(value, ".9g") for value in vector) + "]"


def build_plan(
    vector: tuple[float, ...],
    dimensions: int,
    trusted_tenant: str,
    active_generation: str,
    limit: int,
    statement_timeout_ms: int,
) -> QueryPlan:
    if (
        not trusted_tenant
        or not active_generation
        or not 1 <= limit <= 50
        or not 1 <= statement_timeout_ms <= 5_000
    ):
        raise ValueError("invalid query bounds")
    sql = (
        "SELECT id, revision, embedding <=> ?::vector AS distance "
        "FROM rag_chunk WHERE tenant_id = ? AND generation = ? "
        "ORDER BY distance, id, revision LIMIT ?"
    )
    return QueryPlan(
        sql,
        (vector_literal(vector, dimensions), trusted_tenant, active_generation, limit),
        statement_timeout_ms,
    )


def cosine(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    if not left or len(left) != len(right) or not all(math.isfinite(value) for value in left + right):
        raise ValueError("invalid vector")
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    if left_norm == 0 or right_norm == 0:
        raise ValueError("zero vector")
    return sum(a * b for a, b in zip(left, right, strict=True)) / (left_norm * right_norm)


def exact_search(
    rows: list[Chunk], query: tuple[float, ...], trusted_tenant: str, active_generation: str, limit: int
) -> tuple[Ranked, ...]:
    if not trusted_tenant or not active_generation or not 1 <= limit <= 50:
        raise ValueError("invalid search")
    ranked = [
        Ranked(row.id, cosine(query, row.vector))
        for row in rows
        if row.tenant == trusted_tenant and row.generation == active_generation
    ]
    ranked.sort(key=lambda item: (-item.score, item.id))
    return tuple(ranked[:limit])


def recall_at_k(exact: tuple[Ranked, ...], approximate: tuple[str, ...], k: int) -> float:
    if k < 1 or not exact:
        raise ValueError("baseline required")
    denominator = min(k, len(exact))
    expected = {item.id for item in exact[:denominator]}
    return len(expected & set(approximate[:k])) / denominator


def ann_gate(
    exact: tuple[Ranked, ...], approximate: tuple[str, ...], k: int, minimum_recall: float, minimum_rows: int
) -> bool:
    if not 0 <= minimum_recall <= 1 or not 1 <= minimum_rows <= k:
        raise ValueError("invalid gate")
    return len(approximate) >= minimum_rows and recall_at_k(exact, approximate, k) >= minimum_recall


def statement_budget(deadline_ns: int, now_ns: int, configured_cap_ms: int) -> int:
    if configured_cap_ms < 1 or deadline_ns <= now_ns:
        raise ValueError("no budget")
    return min(configured_cap_ms, max(1, (deadline_ns - now_ns) // 1_000_000))


class Lease:
    def __init__(self) -> None:
        self.held = False
        self.releases = 0

    def acquire(self) -> None:
        if self.held:
            raise RuntimeError("already held")
        self.held = True

    def release(self) -> None:
        if not self.held:
            raise RuntimeError("release without acquisition")
        self.held = False
        self.releases += 1


def independent_oracle(
    rows: list[Chunk], query: tuple[float, ...], tenant: str, generation: str, limit: int
) -> tuple[Ranked, ...]:
    ranked: list[Ranked] = []
    for row in rows:
        if row.tenant != tenant or row.generation != generation:
            continue
        dot = sum(a * b for a, b in zip(query, row.vector, strict=True))
        q2 = sum(value * value for value in query)
        v2 = sum(value * value for value in row.vector)
        ranked.append(Ranked(row.id, dot / math.sqrt(q2 * v2)))
    ranked.sort(key=lambda item: (-item.score, item.id))
    return tuple(ranked[:limit])


if __name__ == "__main__":
    active = Generation("corpus-42", 42, State.ACTIVE, 3, 3)
    ready = Generation("corpus-43", 43, State.READY, 3, 3)
    promoted = promote(active, ready, 3)
    check(promoted.id == "corpus-43")
    check(promoted.state is State.ACTIVE)
    rejects(lambda: promote(active, Generation("partial", 43, State.BUILDING, 3, 3), 3))
    rejects(lambda: promote(active, ready, 2))
    rejects(lambda: promote(active, Generation("old", 42, State.READY, 3, 3), 3))

    check(vector_literal((1.0, 0.25, -2.0), 3).startswith("["))
    rejects(lambda: vector_literal((1.0, 2.0), 3))
    rejects(lambda: vector_literal((0.0, 0.0, 0.0), 3))
    rejects(lambda: vector_literal((1.0, math.nan, 2.0), 3))

    hostile = "payments' OR TRUE --"
    plan = build_plan((1.0, 0.0, 0.0), 3, hostile, "corpus-43", 5, 250)
    check(hostile not in plan.sql)
    check(plan.parameters[1] == hostile)
    check(plan.parameters == ("[1,0,0]", hostile, "corpus-43", 5))
    check(plan.statement_timeout_ms == 250)
    rejects(lambda: build_plan((1.0, 0.0, 0.0), 3, "payments", "corpus-43", 0, 250))
    rejects(lambda: build_plan((1.0, 0.0, 0.0), 3, "payments", "corpus-43", 5, 0))

    rows = [
        Chunk("payments", "corpus-43", "payments-r8", 8, (1.0, 0.0, 0.0)),
        Chunk("payments", "corpus-43", "runbook-r3", 3, (0.9, 0.1, 0.0)),
        Chunk("payments", "corpus-42", "old-r7", 7, (1.0, 0.0, 0.0)),
        Chunk("catalog", "corpus-43", "catalog-secret", 9, (1.0, 0.0, 0.0)),
    ]
    exact = exact_search(rows, (1.0, 0.0, 0.0), "payments", "corpus-43", 2)
    check([item.id for item in exact] == ["payments-r8", "runbook-r3"])
    check(all(item.id != "catalog-secret" for item in exact))
    check(exact_search(rows, (1.0, 0.0, 0.0), "unknown", "corpus-43", 2) == ())
    rejects(lambda: exact_search(rows, (1.0, 0.0), "payments", "corpus-43", 2))

    ties = [
        Chunk("payments", "corpus-43", "b", 1, (1.0, 0.0, 0.0)),
        Chunk("payments", "corpus-43", "a", 1, (1.0, 0.0, 0.0)),
    ]
    check([item.id for item in exact_search(ties, (1.0, 0.0, 0.0), "payments", "corpus-43", 2)] == ["a", "b"])

    check(recall_at_k(exact, ("payments-r8", "runbook-r3"), 2) == 1.0)
    check(recall_at_k(exact, ("payments-r8",), 2) == 0.5)
    check(ann_gate(exact, ("payments-r8", "runbook-r3"), 2, 0.95, 2))
    check(not ann_gate(exact, ("payments-r8",), 2, 0.95, 2))
    rejects(lambda: ann_gate(exact, (), 2, 1.1, 1))

    check(statement_budget(1_000_000_000, 100_000_000, 250) == 250)
    check(statement_budget(200_000_000, 100_000_000, 250) == 100)
    rejects(lambda: statement_budget(100, 100, 250))
    rejects(lambda: statement_budget(200, 100, 0))

    lease = Lease()
    lease.acquire()
    check(lease.held)
    lease.release()
    check(not lease.held and lease.releases == 1)
    rejects(lease.release)
    lease.acquire()
    try:
        raise TimeoutError("simulated statement timeout")
    except TimeoutError:
        check(lease.held)
    finally:
        lease.release()
    check(not lease.held and lease.releases == 2)

    random_source = random.Random(0xD00AB1E)
    for _ in range(10_000):
        query = tuple(random_source.random() + 0.01 for _ in range(3))
        sample: list[Chunk] = []
        for index in range(8):
            sample.append(
                Chunk(
                    "payments",
                    "corpus-43",
                    f"p-{index}",
                    1,
                    tuple(random_source.random() + 0.01 for _ in range(3)),
                )
            )
            sample.append(Chunk("catalog", "corpus-43", f"secret-{index}", 1, query))
        actual = exact_search(sample, query, "payments", "corpus-43", 4)
        oracle = independent_oracle(sample, query, "payments", "corpus-43", 4)
        if [item.id for item in actual] != [item.id for item in oracle]:
            raise AssertionError("oracle mismatch")
        if any(item.id.startswith("secret-") for item in actual):
            raise AssertionError("scope leak")

    print("PARITY|GEN=corpus-43|SCOPE=payments|TOP=payments-r8|RECALL=1.000|POOL=RELEASED|TIMEOUT=250")
    print(f"durable_retrieval_contracts: {scenario_checks} scenario groups and 10000 trials passed")
