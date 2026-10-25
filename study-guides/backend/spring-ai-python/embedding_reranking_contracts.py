"""Dependency-free contracts for embedding retrieval, reranking and runtime parity."""

from __future__ import annotations

from enum import Enum
from math import isfinite, sqrt


class Baseline(Enum):
    CLASSICAL_FIRST = "CLASSICAL_FIRST"
    EMBEDDING_RETRIEVAL = "EMBEDDING_RETRIEVAL"


class Deploy(Enum):
    PYTHON_SERVICE = "PYTHON_SERVICE"
    JVM_ONNX = "JVM_ONNX"
    REJECT = "REJECT"


def cosine(left: list[float], right: list[float]) -> float:
    if not left or len(left) != len(right):
        raise ValueError("dimensions")
    if not all(isfinite(value) for value in left + right):
        raise ValueError("non-finite")
    left_norm = sum(value * value for value in left)
    right_norm = sum(value * value for value in right)
    if left_norm == 0 or right_norm == 0:
        raise ValueError("zero norm")
    return sum(a * b for a, b in zip(left, right, strict=True)) / sqrt(left_norm * right_norm)


def top_k(documents: list[list[float]], query: list[float], k: int) -> list[int]:
    if k < 1 or k > len(documents):
        raise ValueError("k")
    scored = [(cosine(vector, query), index) for index, vector in enumerate(documents)]
    return [index for _, index in sorted(scored, key=lambda item: (-item[0], item[1]))[:k]]


def rerank(candidate_ids: list[int], pair_scores: list[float], limit: int) -> list[int]:
    if not candidate_ids or len(candidate_ids) != len(pair_scores) or not 1 <= limit <= len(candidate_ids):
        raise ValueError("rerank")
    if any(candidate < 0 for candidate in candidate_ids) or not all(isfinite(score) for score in pair_scores):
        raise ValueError("candidate")
    pairs = zip(pair_scores, candidate_ids, strict=True)
    return [candidate for _, candidate in sorted(pairs, key=lambda item: (-item[0], item[1]))[:limit]]


def choose_baseline(stable_labels: bool, generation_required: bool) -> Baseline:
    return Baseline.CLASSICAL_FIRST if stable_labels and not generation_required else Baseline.EMBEDDING_RETRIEVAL


def honest_split(split_before_fit: bool, related_groups_separated: bool, test_used_for_tuning: bool) -> bool:
    return split_before_fit and related_groups_separated and not test_used_for_tuning


def deployment(exact_preprocessing: bool, exact_tensor_contract: bool, parity_passed: bool,
               supported_operators: bool, jvm_required: bool) -> Deploy:
    if not jvm_required:
        return Deploy.PYTHON_SERVICE
    return Deploy.JVM_ONNX if all(
        (exact_preprocessing, exact_tensor_contract, parity_passed, supported_operators)
    ) else Deploy.REJECT


def promote(candidate_quality: float, baseline_quality: float, min_gain: float,
            p95_millis: float, max_p95_millis: float) -> bool:
    if not isfinite(candidate_quality) or not isfinite(baseline_quality) or min_gain < 0 or max_p95_millis <= 0:
        raise ValueError("gate")
    return candidate_quality - baseline_quality >= min_gain and p95_millis <= max_p95_millis


def main() -> None:
    scenarios = 0
    checks = [
        abs(cosine([1, 0], [1, 0]) - 1) < 1e-12,
        abs(cosine([1, 0], [0, 1])) < 1e-12,
        abs(cosine([1, 1], [1, 0]) - sqrt(0.5)) < 1e-12,
    ]
    for check in checks:
        assert check
        scenarios += 1
    for left, right in [([], []), ([1], [1, 2]), ([0, 0], [1, 0]), ([float("nan")], [1])]:
        try:
            cosine(left, right)
            raise AssertionError
        except ValueError:
            scenarios += 1

    documents = [[1, 0], [0.8, 0.2], [0, 1], [1, 0]]
    assert top_k(documents, [1, 0], 3) == [0, 3, 1]; scenarios += 1
    try:
        top_k(documents, [1, 0], 0)
        raise AssertionError
    except ValueError:
        scenarios += 1
    assert rerank([0, 3, 1], [0.2, 0.9, 0.9], 2) == [1, 3]; scenarios += 1
    for ids, scores in [([0], []), ([-1], [0.4])]:
        try:
            rerank(ids, scores, 1)
            raise AssertionError
        except ValueError:
            scenarios += 1

    checks = [
        choose_baseline(True, False) is Baseline.CLASSICAL_FIRST,
        choose_baseline(False, False) is Baseline.EMBEDDING_RETRIEVAL,
        choose_baseline(True, True) is Baseline.EMBEDDING_RETRIEVAL,
        honest_split(True, True, False),
        not honest_split(False, True, False),
        not honest_split(True, False, False),
        not honest_split(True, True, True),
        deployment(True, True, True, True, False) is Deploy.PYTHON_SERVICE,
        deployment(True, True, True, True, True) is Deploy.JVM_ONNX,
        deployment(False, True, True, True, True) is Deploy.REJECT,
        deployment(True, False, True, True, True) is Deploy.REJECT,
        deployment(True, True, False, True, True) is Deploy.REJECT,
        deployment(True, True, True, False, True) is Deploy.REJECT,
        promote(0.84, 0.80, 0.03, 95, 100),
        not promote(0.82, 0.80, 0.03, 95, 100),
        not promote(0.84, 0.80, 0.03, 105, 100),
    ]
    for check in checks:
        assert check
        scenarios += 1
    try:
        promote(float("nan"), 0.8, 0.03, 10, 100)
        raise AssertionError
    except ValueError:
        scenarios += 1

    state = 0xE12BED
    promoted = 0
    for _ in range(10_000):
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        baseline = 0.50 + ((state >> 8) % 4000) / 10_000
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        candidate = 0.50 + ((state >> 8) % 5000) / 10_000
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        latency = 20 + ((state >> 12) % 181)
        actual = promote(candidate, baseline, 0.025, latency, 120)
        oracle = candidate - baseline >= 0.025 and latency <= 120
        assert actual == oracle
        promoted += int(actual)

    print("PARITY|FIRST=CLASSICAL_BASELINE|RETRIEVAL=BI_ENCODER|RERANK=CROSS_ENCODER"
          f"|SPLIT=BEFORE_FIT|JVM=PARITY_GATED|PROMOTED={promoted}")
    print(f"Python embedding and reranking contracts passed: {scenarios} scenario groups + 10000 seeded trials")


if __name__ == "__main__":
    main()
