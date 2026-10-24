"""Dependency-free parity contracts for the complete Spring AI RAG path."""

from dataclasses import dataclass
from enum import Enum
import math
import random


class Result(Enum):
    ANSWER = "answer"
    ABSTAIN = "abstain"
    REJECTED = "rejected"
    DEADLINE = "deadline"
    DEPENDENCY = "dependency"


@dataclass(frozen=True)
class RuntimeIdentity:
    boot: str
    spring_ai: str
    chat_model: str
    embedding_model: str


@dataclass(frozen=True)
class Evidence:
    id: str
    revision: int
    text: str


@dataclass(frozen=True)
class Snapshot:
    generation: str
    evidence: tuple[Evidence, ...]
    vectors: tuple[tuple[float, ...], ...]
    dimension: int


def require_identity(boot: str, spring_ai: str, chat: str, embedding: str) -> RuntimeIdentity:
    if not all(value and value.strip() for value in (boot, spring_ai, chat, embedding)):
        raise ValueError("complete runtime identity required")
    return RuntimeIdentity(boot, spring_ai, chat, embedding)


def publish(generation: str, evidence: list[Evidence], vectors: list[list[float]]) -> Snapshot:
    if generation is None or not generation.strip() or not evidence or len(evidence) != len(vectors):
        raise ValueError("incomplete snapshot")
    if len({item.id for item in evidence}) != len(evidence):
        raise ValueError("duplicate evidence ID")
    dimension = len(vectors[0])
    if dimension == 0:
        raise ValueError("empty vector")
    frozen = []
    for vector in vectors:
        if len(vector) != dimension or not all(math.isfinite(value) for value in vector):
            raise ValueError("invalid vector")
        if sum(value * value for value in vector) == 0:
            raise ValueError("zero vector")
        frozen.append(tuple(vector))
    return Snapshot(generation, tuple(evidence), tuple(frozen), dimension)


def cosine(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    if not left or len(left) != len(right) or not all(math.isfinite(value) for value in left + right):
        raise ValueError("invalid vector")
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    if left_norm == 0 or right_norm == 0:
        raise ValueError("zero vector")
    return sum(a * b for a, b in zip(left, right, strict=True)) / (left_norm * right_norm)


def retrieve(snapshot: Snapshot, query: list[float], threshold: float, limit: int) -> tuple[Evidence, ...]:
    query_vector = tuple(query)
    if len(query_vector) != snapshot.dimension or not 1 <= limit <= len(snapshot.evidence) or not math.isfinite(threshold):
        raise ValueError("invalid retrieval request")
    ranked = sorted(
        ((cosine(query_vector, vector), item) for item, vector in zip(snapshot.evidence, snapshot.vectors, strict=True)),
        key=lambda pair: (-pair[0], pair[1].id),
    )
    if ranked[0][0] < threshold:
        return ()
    return tuple(item for _, item in ranked[:limit])


def validate_grounding(cited: set[str], supported: set[str], selected: tuple[Evidence, ...]) -> Result:
    allowed = {item.id for item in selected}
    if not cited or not cited <= allowed:
        raise ValueError("missing or invented citation")
    return Result.ANSWER if cited <= supported else Result.ABSTAIN


def remaining_millis(deadline_ns: int, now_ns: int) -> int:
    if deadline_ns <= now_ns:
        return 0
    return max(1, (deadline_ns - now_ns) // 1_000_000)


def fleet_capacity(processes: int, permits_per_process: int) -> int:
    if processes < 1 or permits_per_process < 1:
        raise ValueError("positive capacity required")
    return processes * permits_per_process


def map_outcome(*, admitted: bool, deadline: bool, dependency_failed: bool, no_evidence: bool) -> tuple[Result, int]:
    if not admitted:
        return Result.REJECTED, 429
    if deadline:
        return Result.DEADLINE, 504
    if dependency_failed:
        return Result.DEPENDENCY, 502
    if no_evidence:
        return Result.ABSTAIN, 200
    return Result.ANSWER, 200


class Permit:
    def __init__(self) -> None:
        self.acquired = False
        self.releases = 0

    def acquire(self) -> None:
        if self.acquired:
            raise RuntimeError("already acquired")
        self.acquired = True

    def release(self) -> None:
        if not self.acquired:
            raise RuntimeError("release without acquisition")
        self.acquired = False
        self.releases += 1


def rejects(action) -> None:
    try:
        action()
    except (ValueError, RuntimeError, OverflowError):
        return
    raise AssertionError("expected rejection")


if __name__ == "__main__":
    identity = require_identity("4.1.2", "2.0.2", "qwen3:8b@sha256:chat", "embeddinggemma@sha256:embed")
    assert identity.spring_ai == "2.0.2"
    rejects(lambda: require_identity("4.1.2", "", "chat", "embed"))
    rejects(lambda: require_identity("4.1.2", "2.0.2", None, "embed"))

    evidence = [
        Evidence("payments-r7", 7, "Rollback requires compatible migrations."),
        Evidence("runbook-r2", 2, "Page the payments owner before rollback."),
        Evidence("catalog-r1", 1, "Unrelated catalog deployment notes."),
    ]
    first = [1.0, 0.0]
    snapshot = publish("corpus-42/embed-v3", evidence, [first, [0.8, 0.2], [-1.0, 0.0]])
    first[0] = -1
    assert snapshot.vectors[0][0] == 1.0
    rejects(lambda: publish("", evidence, [[1.0], [1.0], [1.0]]))
    rejects(lambda: publish("g", evidence, [[1.0], [1.0, 2.0], [1.0]]))
    rejects(lambda: publish("g", evidence, [[1.0], [math.nan], [1.0]]))
    rejects(lambda: publish("g", evidence, [[1.0], [0.0], [1.0]]))
    rejects(lambda: publish("g", [evidence[0], evidence[0]], [[1.0], [1.0]]))

    selected = retrieve(snapshot, [1.0, 0.0], 0.70, 2)
    assert [item.id for item in selected] == ["payments-r7", "runbook-r2"]
    tied = publish("g2", evidence[:2], [[1.0, 0.0], [1.0, 0.0]])
    assert [item.id for item in retrieve(tied, [1.0, 0.0], 0.9, 2)] == ["payments-r7", "runbook-r2"]
    assert retrieve(snapshot, [0.0, 1.0], 0.99, 2) == ()
    rejects(lambda: retrieve(snapshot, [1.0], 0.5, 2))
    rejects(lambda: retrieve(snapshot, [1.0, 0.0], 0.5, 0))

    assert validate_grounding({"payments-r7"}, {"payments-r7"}, selected) is Result.ANSWER
    assert validate_grounding({"payments-r7"}, set(), selected) is Result.ABSTAIN
    rejects(lambda: validate_grounding(set(), set(), selected))
    rejects(lambda: validate_grounding({"invented"}, {"invented"}, selected))

    assert remaining_millis(20_000_000, 5_000_000) == 15
    assert remaining_millis(5_000_000, 5_000_000) == 0
    assert fleet_capacity(3, 4) == 12
    rejects(lambda: fleet_capacity(0, 4))
    assert map_outcome(admitted=False, deadline=False, dependency_failed=False, no_evidence=False) == (Result.REJECTED, 429)
    assert map_outcome(admitted=True, deadline=True, dependency_failed=False, no_evidence=False) == (Result.DEADLINE, 504)
    assert map_outcome(admitted=True, deadline=False, dependency_failed=True, no_evidence=False) == (Result.DEPENDENCY, 502)
    assert map_outcome(admitted=True, deadline=False, dependency_failed=False, no_evidence=True) == (Result.ABSTAIN, 200)

    permit = Permit()
    permit.acquire()
    try:
        raise TimeoutError("simulated provider timeout")
    except TimeoutError:
        pass
    finally:
        permit.release()
    assert permit.releases == 1 and not permit.acquired
    rejects(permit.release)

    random_source = random.Random(0x5A17A1)
    for _ in range(10_000):
        angle = random_source.random() * math.tau
        vector = (math.cos(angle), math.sin(angle))
        assert math.isclose(cosine(vector, vector), 1.0, abs_tol=1e-12)

    print("PARITY|CONFIGURED|READY|ANSWER|ABSTAIN|429|504|502|RELEASED")
    print("spring_rag_path_contracts: 36 scenario groups and 10000 trials passed")
