"""Deterministic, dependency-free retrieval contracts for the offline lab."""

from collections import Counter
from dataclasses import dataclass
import math
import re
from typing import Iterable, Sequence


TOKEN = re.compile(r"[a-z0-9]+")
CITATION = re.compile(r"\[([A-Za-z0-9_-]+)\]")


@dataclass(frozen=True)
class Document:
    id: str
    tenant: str
    revision: int
    text: str


CORPUS = (
    Document(
        "payments-r7",
        "acme",
        7,
        "Payments rollback: restore the previous verified image digest. "
        "Check database migration compatibility before rollback.",
    ),
    Document(
        "cache-r2",
        "acme",
        2,
        "Prevent cache stampede with bounded single flight and randomized TTL. "
        "A Redis lock needs ownership-aware release.",
    ),
    Document(
        "vectors-r3",
        "acme",
        3,
        "Embedding migration requires a new index generation. "
        "Never mix vectors from incompatible embedding models.",
    ),
    Document(
        "private-r1",
        "globex",
        1,
        "Payments rollback payments rollback uses the confidential Globex recovery procedure.",
    ),
)


def terms(text: str) -> Counter[str]:
    return Counter(TOKEN.findall(text.lower()))


def lexical_score(query: str, text: str) -> float:
    """Bag-of-words cosine; deliberately neither BM25 nor neural similarity."""
    query_terms, document_terms = terms(query), terms(text)
    query_norm = math.sqrt(sum(value * value for value in query_terms.values()))
    document_norm = math.sqrt(sum(value * value for value in document_terms.values()))
    if query_norm == 0.0 or document_norm == 0.0:
        return 0.0
    dot = sum(value * document_terms[token] for token, value in query_terms.items())
    return dot / (query_norm * document_norm)


def retrieve(query: str, trusted_tenant: str, top_k: int = 3) -> list[tuple[float, Document]]:
    if not 1 <= top_k <= 20:
        raise ValueError("top_k must be in [1, 20]")
    if not trusted_tenant or not trusted_tenant.strip():
        raise ValueError("trusted tenant is required")

    # Scope first: an ineligible document never becomes a scored candidate.
    eligible = (document for document in CORPUS if document.tenant == trusted_tenant)
    scored = ((lexical_score(query, document.text), document) for document in eligible)
    positive = (pair for pair in scored if pair[0] > 0.0)
    return sorted(positive, key=lambda pair: (-pair[0], pair[1].id))[:top_k]


def cosine(left: Sequence[float], right: Sequence[float]) -> float:
    """Cosine with shape, finiteness, zero-direction and overflow guards."""
    if not left or len(left) != len(right):
        raise ValueError("non-empty equal dimensions required")
    if not all(math.isfinite(value) for value in (*left, *right)):
        raise ValueError("finite vector coordinates required")

    left_scale = max(abs(value) for value in left)
    right_scale = max(abs(value) for value in right)
    if left_scale == 0.0 or right_scale == 0.0:
        raise ValueError("zero vector has no cosine direction")

    scaled_left = [value / left_scale for value in left]
    scaled_right = [value / right_scale for value in right]
    left_square = sum(value * value for value in scaled_left)
    right_square = sum(value * value for value in scaled_right)
    dot = sum(a * b for a, b in zip(scaled_left, scaled_right, strict=True))
    result = dot / math.sqrt(left_square * right_square)
    if not math.isfinite(result):
        raise ValueError("cosine result must be finite")
    return max(-1.0, min(1.0, result))


def rrf(rankings: Iterable[Iterable[str]], rank_constant: int = 60) -> list[tuple[str, float]]:
    if rank_constant < 1:
        raise ValueError("rank constant must be positive")
    scores: Counter[str] = Counter()
    for ranking in rankings:
        seen: set[str] = set()
        rank = 0
        for document_id in ranking:
            if not document_id or document_id in seen:
                continue
            seen.add(document_id)
            rank += 1
            scores[document_id] += 1.0 / (rank_constant + rank)
    return sorted(scores.items(), key=lambda pair: (-pair[1], pair[0]))


def validate_citations(answer: str, allowed_ids: set[str]) -> set[str]:
    cited = set(CITATION.findall(answer))
    if not cited or not cited.issubset(allowed_ids):
        raise ValueError("missing or unknown evidence citation")
    return cited  # Membership is necessary, but does not prove entailment.


def evidence_or_abstain(query: str, trusted_tenant: str) -> tuple[str, tuple[str, ...]]:
    evidence = retrieve(query, trusted_tenant)
    if not evidence:
        return "NO_EVIDENCE", ()
    return "EVIDENCE", tuple(document.id for _, document in evidence)


def rejects(action) -> None:
    try:
        action()
    except ValueError:
        return
    raise AssertionError("expected ValueError")


if __name__ == "__main__":
    acme = retrieve("payments rollback", "acme")
    assert [document.id for _, document in acme] == ["payments-r7"]
    assert all(document.tenant == "acme" for _, document in acme)
    # The Globex text repeats both query terms, but is never scored for Acme.
    assert lexical_score("payments rollback", CORPUS[3].text) > acme[0][0]
    assert "private-r1" not in {document.id for _, document in acme}
    assert evidence_or_abstain("payments rollback", "unknown") == ("NO_EVIDENCE", ())
    assert evidence_or_abstain("quasar", "acme") == ("NO_EVIDENCE", ())
    assert retrieve("payments rollback", "acme", 1)[0][1].id == "payments-r7"
    rejects(lambda: retrieve("payments", "acme", 0))
    rejects(lambda: retrieve("payments", "acme", 21))
    rejects(lambda: retrieve("payments", " "))

    repeated = terms("rollback rollback payments")
    assert repeated == Counter({"rollback": 2, "payments": 1})
    assert lexical_score("", "payments") == 0.0
    payment_score = lexical_score("payments rollback", CORPUS[0].text)
    assert payment_score > 0.0
    assert payment_score <= 1.0

    assert math.isclose(cosine([1.0, 0.0], [1.0, 0.0]), 1.0)
    assert math.isclose(cosine([1.0, 0.0], [0.0, 1.0]), 0.0)
    assert math.isclose(cosine([1.0, 0.0], [-1.0, 0.0]), -1.0)
    assert math.isclose(cosine([1e308, 1e308], [1e308, 1e308]), 1.0)
    rejects(lambda: cosine([], []))
    rejects(lambda: cosine([1.0], [1.0, 2.0]))
    rejects(lambda: cosine([0.0, 0.0], [1.0, 0.0]))
    rejects(lambda: cosine([math.nan], [1.0]))
    rejects(lambda: cosine([math.inf], [1.0]))

    fused = rrf(
        [
            ["payments-r7", "cache-r2", "payments-r7"],
            ["cache-r2", "payments-r7", "vectors-r3"],
        ]
    )
    assert [document_id for document_id, _ in fused] == [
        "cache-r2",
        "payments-r7",
        "vectors-r3",
    ]
    assert math.isclose(fused[0][1], 1 / 61 + 1 / 62)
    assert math.isclose(fused[1][1], 1 / 61 + 1 / 62)
    assert rrf([[]]) == []
    rejects(lambda: rrf([["payments-r7"]], 0))

    allowed = {document.id for _, document in retrieve("payments rollback", "acme")}
    assert validate_citations("Rollback from the verified digest [payments-r7].", allowed) == {
        "payments-r7"
    }
    rejects(lambda: validate_citations("Rollback now.", allowed))
    rejects(lambda: validate_citations("Use the private runbook [private-r1].", allowed))

    parity = ",".join(document_id for document_id, _ in fused)
    print(f"PARITY|payments-r7|NO_EVIDENCE|{parity}|1")
    print("offline_retrieval_lab_contracts.py: 31 scenario groups passed")
