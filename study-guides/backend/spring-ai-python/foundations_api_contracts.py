from dataclasses import dataclass
from enum import Enum, auto


class RetrievalMode(Enum):
    LEXICAL = auto()
    SEMANTIC = auto()
    HYBRID = auto()


class Capability(Enum):
    TOOLS = auto()
    JSON_SCHEMA = auto()
    STREAMING = auto()
    CANCELLATION = auto()
    USAGE = auto()


class Outcome(Enum):
    ANSWER = auto()
    NO_ANSWER = auto()
    UPSTREAM_FAILURE = auto()
    INVALID_OUTPUT = auto()
    FORBIDDEN = auto()


@dataclass(frozen=True)
class Provider:
    id: str
    capabilities: frozenset[Capability]

    def __post_init__(self) -> None:
        if self.id is None or not self.id.strip():
            raise ValueError("blank provider ID")
        object.__setattr__(self, "capabilities", frozenset(self.capabilities))


@dataclass(frozen=True)
class AnswerCandidate:
    provider_available: bool
    evidence_sufficient: bool
    parsed: bool
    schema_valid: bool
    domain_valid: bool
    authorized: bool


def retrieval_mode(exact_identifier: bool, semantic_intent: bool) -> RetrievalMode:
    if exact_identifier and semantic_intent:
        return RetrievalMode.HYBRID
    if exact_identifier:
        return RetrievalMode.LEXICAL
    return RetrievalMode.SEMANTIC


def admit(providers: tuple[Provider, ...], required: frozenset[Capability]) -> Provider:
    if not providers:
        raise ValueError("no providers")
    immutable_required = frozenset(required)
    for provider in providers:
        if immutable_required <= provider.capabilities:
            return provider
    raise ValueError("no capable provider")


def classify(candidate: AnswerCandidate) -> Outcome:
    if not candidate.provider_available:
        return Outcome.UPSTREAM_FAILURE
    if not candidate.evidence_sufficient:
        return Outcome.NO_ANSWER
    if not (candidate.parsed and candidate.schema_valid and candidate.domain_valid):
        return Outcome.INVALID_OUTPUT
    if not candidate.authorized:
        return Outcome.FORBIDDEN
    return Outcome.ANSWER


def next_state(state: int) -> int:
    return (state * 1_664_525 + 1_013_904_223) & 0xFFFF_FFFF


def main() -> None:
    scenarios = 0

    def check(condition: bool) -> None:
        nonlocal scenarios
        assert condition
        scenarios += 1

    def rejects(action) -> None:
        nonlocal scenarios
        try:
            action()
            raise AssertionError("expected rejection")
        except ValueError:
            scenarios += 1

    check(retrieval_mode(True, False) is RetrievalMode.LEXICAL)
    check(retrieval_mode(False, True) is RetrievalMode.SEMANTIC)
    check(retrieval_mode(False, False) is RetrievalMode.SEMANTIC)
    check(retrieval_mode(True, True) is RetrievalMode.HYBRID)

    portable = Provider("portable", frozenset({
        Capability.TOOLS, Capability.JSON_SCHEMA, Capability.USAGE}))
    streaming = Provider("streaming", frozenset({
        Capability.TOOLS, Capability.STREAMING, Capability.CANCELLATION,
        Capability.USAGE}))
    complete = Provider("complete", frozenset(Capability))
    ordered = (portable, streaming, complete)
    check(admit(ordered, frozenset({Capability.TOOLS})).id == "portable")
    check(admit(ordered, frozenset({Capability.STREAMING})).id == "streaming")
    check(admit(ordered, frozenset({Capability.JSON_SCHEMA, Capability.CANCELLATION})).id == "complete")
    check(admit(ordered, frozenset()).id == "portable")
    check(portable.capabilities == frozenset({Capability.TOOLS, Capability.JSON_SCHEMA, Capability.USAGE}))
    check(complete.capabilities == frozenset(Capability))

    check(classify(AnswerCandidate(True, True, True, True, True, True)) is Outcome.ANSWER)
    check(classify(AnswerCandidate(True, False, False, False, False, False)) is Outcome.NO_ANSWER)
    check(classify(AnswerCandidate(False, True, True, True, True, True)) is Outcome.UPSTREAM_FAILURE)
    check(classify(AnswerCandidate(True, True, False, True, True, True)) is Outcome.INVALID_OUTPUT)
    check(classify(AnswerCandidate(True, True, True, False, True, True)) is Outcome.INVALID_OUTPUT)
    check(classify(AnswerCandidate(True, True, True, True, False, True)) is Outcome.INVALID_OUTPUT)
    check(classify(AnswerCandidate(True, True, True, True, True, False)) is Outcome.FORBIDDEN)
    check(classify(AnswerCandidate(False, False, False, False, False, False)) is Outcome.UPSTREAM_FAILURE)

    rejects(lambda: Provider("", frozenset()))
    rejects(lambda: Provider(" ", frozenset()))
    rejects(lambda: Provider(None, frozenset()))  # type: ignore[arg-type]
    rejects(lambda: admit((), frozenset()))
    rejects(lambda: admit((portable, streaming), frozenset({Capability.JSON_SCHEMA, Capability.CANCELLATION})))
    rejects(lambda: admit((portable,), frozenset({Capability.STREAMING})))

    state = 0x21F0A11D
    for _ in range(10_000):
        state = next_state(state); provider_up = bool(state & 1)
        state = next_state(state); evidence = bool(state & 1)
        state = next_state(state); parsed = bool(state & 1)
        state = next_state(state); schema = bool(state & 1)
        state = next_state(state); domain = bool(state & 1)
        state = next_state(state); authorized = bool(state & 1)
        candidate = AnswerCandidate(provider_up, evidence, parsed, schema, domain, authorized)
        if not provider_up:
            expected = Outcome.UPSTREAM_FAILURE
        elif not evidence:
            expected = Outcome.NO_ANSWER
        elif not (parsed and schema and domain):
            expected = Outcome.INVALID_OUTPUT
        elif not authorized:
            expected = Outcome.FORBIDDEN
        else:
            expected = Outcome.ANSWER
        assert classify(candidate) is expected

    matrix = ordered
    state = 0xC4A8B1E5
    values = tuple(Capability)
    for _ in range(10_000):
        state = next_state(state)
        required = frozenset(value for bit, value in enumerate(values) if state & (1 << bit))
        expected = next((provider for provider in matrix if required <= provider.capabilities), None)
        try:
            actual = admit(matrix, required)
            assert actual == expected
        except ValueError:
            assert expected is None

    assert scenarios == 24, scenarios
    print("PARITY|RETRIEVAL=LEXICAL,SEMANTIC,HYBRID|CAPABILITIES=ADMITTED|OUTCOMES=ANSWER,NO_ANSWER,UPSTREAM_FAILURE,INVALID_OUTPUT,FORBIDDEN|ORDER=DETERMINISTIC")
    print("Foundations/API contracts passed: 24 scenario groups + 20000 seeded trials")


if __name__ == "__main__":
    main()
