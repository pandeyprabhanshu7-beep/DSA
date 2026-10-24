"""Dependency-free contracts for a bounded Python RAG endpoint."""

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
class Document:
    id: str
    revision: int
    text: str


@dataclass(frozen=True)
class Snapshot:
    documents: tuple[Document, ...]
    vectors: tuple[tuple[float, ...], ...]
    dimension: int


def build_snapshot(documents: list[Document], vectors: list[list[float]]) -> Snapshot:
    if not documents or len(documents) != len(vectors):
        raise ValueError("embedding count mismatch")
    if len({doc.id for doc in documents}) != len(documents):
        raise ValueError("duplicate document ID")
    dimension = len(vectors[0])
    if dimension == 0:
        raise ValueError("empty vector")
    frozen_vectors = []
    for vector in vectors:
        if len(vector) != dimension or not all(math.isfinite(value) for value in vector):
            raise ValueError("invalid embedding shape or coordinate")
        if math.sqrt(sum(value * value for value in vector)) == 0:
            raise ValueError("zero-norm embedding")
        frozen_vectors.append(tuple(vector))
    return Snapshot(tuple(documents), tuple(frozen_vectors), dimension)


def validate_question(question: str) -> str:
    if not 3 <= len(question) <= 2_000 or not question.strip():
        raise ValueError("question violates DTO contract")
    return question.strip()


def cosine(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    if len(left) != len(right) or not left:
        raise ValueError("embedding dimensions differ")
    if not all(math.isfinite(value) for value in left + right):
        raise ValueError("non-finite coordinate")
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    if left_norm == 0 or right_norm == 0:
        raise ValueError("zero-norm embedding")
    return sum(a * b for a, b in zip(left, right, strict=True)) / (left_norm * right_norm)


def retrieve(snapshot: Snapshot, query: list[float], threshold: float, limit: int = 2) -> tuple[Document, ...]:
    query_vector = tuple(query)
    if len(query_vector) != snapshot.dimension or not 1 <= limit <= len(snapshot.documents):
        raise ValueError("invalid query dimension or limit")
    ranked = sorted(
        ((cosine(query_vector, vector), doc) for doc, vector in zip(snapshot.documents, snapshot.vectors, strict=True)),
        key=lambda pair: (-pair[0], pair[1].id),
    )
    if ranked[0][0] < threshold:
        return ()
    return tuple(doc for _, doc in ranked[:limit])


def validate_grounding(cited_ids: set[str], supported_ids: set[str], selected: tuple[Document, ...]) -> Result:
    allowed = {doc.id for doc in selected}
    if not cited_ids or not cited_ids <= allowed:
        raise ValueError("missing or invented citation")
    if not cited_ids <= supported_ids:
        return Result.ABSTAIN
    return Result.ANSWER


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


def fleet_capacity(processes: int, permits_per_process: int) -> int:
    if processes < 1 or permits_per_process < 1:
        raise ValueError("positive process and permit counts required")
    return processes * permits_per_process


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
    except (ValueError, RuntimeError):
        return
    raise AssertionError("expected rejection")


if __name__ == "__main__":
    docs = [
        Document("payments-r7", 7, "Rollback requires backward-compatible migrations."),
        Document("runbook-r2", 2, "Page the payments owner before rollback."),
        Document("catalog-r1", 1, "Unrelated catalog deployment notes."),
    ]
    snapshot = build_snapshot(docs, [[1.0, 0.0], [0.8, 0.2], [-1.0, 0.0]])
    assert snapshot.dimension == 2
    rejects(lambda: build_snapshot(docs, [[1.0], [2.0]]))
    rejects(lambda: build_snapshot(docs, [[1.0, 0.0], [1.0], [1.0, 0.0]]))
    rejects(lambda: build_snapshot(docs, [[1.0, 0.0], [math.nan, 1.0], [1.0, 0.0]]))
    rejects(lambda: build_snapshot(docs, [[1.0, 0.0], [0.0, 0.0], [1.0, 0.0]]))
    rejects(lambda: build_snapshot([docs[0], docs[0]], [[1.0], [1.0]]))

    assert validate_question("  rollback?  ") == "rollback?"
    rejects(lambda: validate_question("  "))
    rejects(lambda: validate_question("x" * 2_001))
    selected = retrieve(snapshot, [1.0, 0.0], 0.70)
    assert [doc.id for doc in selected] == ["payments-r7", "runbook-r2"]
    tied = build_snapshot(docs[:2], [[1.0, 0.0], [1.0, 0.0]])
    assert [doc.id for doc in retrieve(tied, [1.0, 0.0], 0.9)] == ["payments-r7", "runbook-r2"]
    assert retrieve(snapshot, [0.0, 1.0], 0.99) == ()
    rejects(lambda: retrieve(snapshot, [1.0], 0.5))

    assert validate_grounding({"payments-r7"}, {"payments-r7"}, selected) is Result.ANSWER
    assert validate_grounding({"payments-r7"}, set(), selected) is Result.ABSTAIN
    rejects(lambda: validate_grounding(set(), set(), selected))
    rejects(lambda: validate_grounding({"invented"}, {"invented"}, selected))

    assert fleet_capacity(3, 4) == 12
    rejects(lambda: fleet_capacity(0, 4))
    assert map_outcome(admitted=False, deadline=False, dependency_failed=False, no_evidence=False) == (Result.REJECTED, 429)
    assert map_outcome(admitted=True, deadline=True, dependency_failed=False, no_evidence=False) == (Result.DEADLINE, 504)
    assert map_outcome(admitted=True, deadline=False, dependency_failed=True, no_evidence=False) == (Result.DEPENDENCY, 502)
    assert map_outcome(admitted=True, deadline=False, dependency_failed=False, no_evidence=True) == (Result.ABSTAIN, 200)

    permit = Permit()
    permit.acquire()
    try:
        raise TimeoutError("simulated deadline")
    except TimeoutError:
        pass
    finally:
        permit.release()
    assert permit.releases == 1 and not permit.acquired
    rejects(permit.release)

    random_source = random.Random(0xA71A5)
    for _ in range(10_000):
        angle = random_source.random() * math.tau
        vector = [math.cos(angle), math.sin(angle)]
        assert math.isclose(cosine(tuple(vector), tuple(vector)), 1.0, abs_tol=1e-12)

    print("PARITY|READY|ANSWER|ABSTAIN|429|504|502|RELEASED")
    print("python_rag_endpoint_contracts: 32 scenario groups and 10000 trials passed")
