"""Dependency-free contracts for boundary-first observability and diagnosis."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class Boundary(Enum):
    QUEUE = 0
    CONNECTION_POOL = 1
    RETRIEVAL = 2
    TOOL = 3
    MODEL = 4
    OUTPUT = 5


class RetrievalDiagnosis(Enum):
    EXTRACTION_MISS = "EXTRACTION_MISS"
    RETRIEVAL_MISS = "RETRIEVAL_MISS"
    AUTHORIZATION_FILTERED = "AUTHORIZATION_FILTERED"
    CONTEXT_TRUNCATION = "CONTEXT_TRUNCATION"
    GENERATION_CITATION_MISS = "GENERATION_CITATION_MISS"
    COMPLETE = "COMPLETE"


class IncidentAction(Enum):
    CONTINUE = "CONTINUE"
    STOP_AND_ISOLATE = "STOP_AND_ISOLATE"


@dataclass(frozen=True)
class Latency:
    queue_ms: int
    pool_ms: int
    retrieval_ms: int
    tool_ms: int
    model_ms: int
    output_ms: int

    def __post_init__(self) -> None:
        if min(self.values()) < 0:
            raise ValueError("negative latency")

    def values(self) -> tuple[int, ...]:
        return (self.queue_ms, self.pool_ms, self.retrieval_ms,
                self.tool_ms, self.model_ms, self.output_ms)


@dataclass(frozen=True)
class ReleaseEvidence:
    release_id: str
    model_revision: str
    prompt_version: str
    index_generation: str
    candidate_count: int
    authorized_count: int
    cited_count: int
    completion_reason: str

    def __post_init__(self) -> None:
        if (not self.release_id or not self.model_revision or not self.prompt_version
                or not self.index_generation or not self.completion_reason
                or self.candidate_count < 0 or self.authorized_count < 0
                or self.authorized_count > self.candidate_count or self.cited_count < 0
                or self.cited_count > self.authorized_count):
            raise ValueError("invalid release evidence")


@dataclass(frozen=True)
class RuntimeConfig:
    model_revision: str
    prompt_hash: str
    temperature: float
    token_limit: int
    embedding_revision: str
    candidate_ids: tuple[str, ...]

    def __post_init__(self) -> None:
        if (not self.model_revision or not self.prompt_hash or self.temperature < 0
                or self.temperature != self.temperature or self.token_limit <= 0
                or not self.embedding_revision):
            raise ValueError("invalid runtime config")


def dominant(latency: Latency) -> Boundary:
    values = latency.values()
    best = 0
    for index in range(1, len(values)):
        if values[index] > values[best]:
            best = index
    return list(Boundary)[best]


def retrieval_diagnosis(extracted: bool, candidates: int, authorized: int,
                        included_in_context: bool, cited: bool) -> RetrievalDiagnosis:
    if candidates < 0 or authorized < 0 or authorized > candidates:
        raise ValueError("invalid retrieval counts")
    if not extracted:
        return RetrievalDiagnosis.EXTRACTION_MISS
    if candidates == 0:
        return RetrievalDiagnosis.RETRIEVAL_MISS
    if authorized == 0:
        return RetrievalDiagnosis.AUTHORIZATION_FILTERED
    if not included_in_context:
        return RetrievalDiagnosis.CONTEXT_TRUNCATION
    if not cited:
        return RetrievalDiagnosis.GENERATION_CITATION_MISS
    return RetrievalDiagnosis.COMPLETE


def tenant_action(request_tenant: str, returned_tenants: frozenset[str]) -> IncidentAction:
    if not request_tenant:
        raise ValueError("tenant evidence")
    return (IncidentAction.CONTINUE if returned_tenants == frozenset((request_tenant,))
            or not returned_tenants else IncidentAction.STOP_AND_ISOLATE)


def tool_visible(port_healthy: bool, registered: bool,
                 mode_compatible: bool, sdk_compatible: bool) -> bool:
    return port_healthy and registered and mode_compatible and sdk_compatible


def first_parity_difference(java: RuntimeConfig, python: RuntimeConfig) -> str:
    for name in ("model_revision", "prompt_hash", "temperature", "token_limit",
                 "embedding_revision", "candidate_ids"):
        if getattr(java, name) != getattr(python, name):
            return {"model_revision": "modelRevision", "prompt_hash": "promptHash",
                    "token_limit": "tokenLimit", "embedding_revision": "embeddingRevision",
                    "candidate_ids": "candidateIds"}.get(name, name)
    return "none"


def metric_label_allowed(key: str) -> bool:
    return key in {"model.class", "release.cohort", "outcome", "boundary"}


def trace_content(explicitly_enabled: bool, protected_sink: bool,
                  prompt: str, completion: str) -> dict[str, str]:
    return ({"prompt": prompt, "completion": completion}
            if explicitly_enabled and protected_sink else {})


def main() -> None:
    scenarios = 0
    for latency, expected in (
        (Latency(900, 40, 100, 0, 300, 10), Boundary.QUEUE),
        (Latency(10, 800, 100, 0, 300, 10), Boundary.CONNECTION_POOL),
        (Latency(10, 20, 700, 0, 300, 10), Boundary.RETRIEVAL),
        (Latency(10, 20, 30, 650, 300, 10), Boundary.TOOL),
        (Latency(10, 20, 30, 0, 900, 10), Boundary.MODEL),
        (Latency(10, 20, 30, 0, 40, 900), Boundary.OUTPUT),
    ):
        assert dominant(latency) is expected
        scenarios += 1
    try:
        Latency(-1, 0, 0, 0, 0, 0)
        raise AssertionError
    except ValueError:
        scenarios += 1

    cases = (
        ((False, 0, 0, False, False), RetrievalDiagnosis.EXTRACTION_MISS),
        ((True, 0, 0, False, False), RetrievalDiagnosis.RETRIEVAL_MISS),
        ((True, 4, 0, False, False), RetrievalDiagnosis.AUTHORIZATION_FILTERED),
        ((True, 4, 3, False, False), RetrievalDiagnosis.CONTEXT_TRUNCATION),
        ((True, 4, 3, True, False), RetrievalDiagnosis.GENERATION_CITATION_MISS),
        ((True, 4, 3, True, True), RetrievalDiagnosis.COMPLETE),
    )
    for args, expected in cases:
        assert retrieval_diagnosis(*args) is expected
        scenarios += 1
    try:
        retrieval_diagnosis(True, 2, 3, True, True)
        raise AssertionError
    except ValueError:
        scenarios += 1

    checks = (
        tenant_action("payments", frozenset(("payments",))) is IncidentAction.CONTINUE,
        tenant_action("payments", frozenset(("payments", "executive"))) is IncidentAction.STOP_AND_ISOLATE,
        not tool_visible(True, False, True, True),
        not tool_visible(True, True, False, True),
        not tool_visible(True, True, True, False),
        tool_visible(True, True, True, True),
    )
    for check in checks:
        assert check
        scenarios += 1

    base = RuntimeConfig("m-17", "sha256:p", 0.0, 800, "embed-v4", ("d1", "d7"))
    parity = (
        (base, "none"),
        (RuntimeConfig("m-18", "sha256:p", 0.0, 800, "embed-v4", ("d1", "d7")), "modelRevision"),
        (RuntimeConfig("m-17", "sha256:q", 0.0, 800, "embed-v4", ("d1", "d7")), "promptHash"),
        (RuntimeConfig("m-17", "sha256:p", 0.2, 800, "embed-v4", ("d1", "d7")), "temperature"),
        (RuntimeConfig("m-17", "sha256:p", 0.0, 900, "embed-v4", ("d1", "d7")), "tokenLimit"),
        (RuntimeConfig("m-17", "sha256:p", 0.0, 800, "embed-v5", ("d1", "d7")), "embeddingRevision"),
        (RuntimeConfig("m-17", "sha256:p", 0.0, 800, "embed-v4", ("d7", "d1")), "candidateIds"),
    )
    for other, expected in parity:
        assert first_parity_difference(base, other) == expected
        scenarios += 1

    checks = (
        metric_label_allowed("outcome"), metric_label_allowed("release.cohort"),
        not metric_label_allowed("user.id"), not metric_label_allowed("document.id"),
        not metric_label_allowed("prompt.text"),
        not trace_content(False, True, "secret", "answer"),
        not trace_content(True, False, "secret", "answer"),
        len(trace_content(True, True, "approved", "answer")) == 2,
    )
    for check in checks:
        assert check
        scenarios += 1
    ReleaseEvidence("r14", "m-17", "p12", "g43", 8, 5, 2, "stop")
    scenarios += 1
    try:
        ReleaseEvidence("r14", "m-17", "p12", "g43", 2, 3, 1, "stop")
        raise AssertionError
    except ValueError:
        scenarios += 1

    state = 0x14B0A7
    model_dominant = 0
    for _ in range(10_000):
        values = []
        for _ in range(6):
            state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
            values.append((state >> 10) % 2_000)
        latency = Latency(*values)
        oracle = 0
        for index in range(1, len(values)):
            if values[index] > values[oracle]:
                oracle = index
        assert dominant(latency) is list(Boundary)[oracle]
        model_dominant += int(oracle == Boundary.MODEL.value)

    assert scenarios == 37
    print("PARITY|TRACE=BOUNDARY_FIRST|METRICS=BOUNDED_LABELS|CONTENT=DEFAULT_OFF"
          "|TENANT_LEAK=STOP|TOOLS=REGISTRATION_PROVED|MISMATCH=CONFIG_FIRST"
          f"|MODEL_DOMINANT={model_dominant}")
    print(f"Python observability contracts passed: {scenarios} scenario groups + 10000 seeded trials")


if __name__ == "__main__":
    main()
