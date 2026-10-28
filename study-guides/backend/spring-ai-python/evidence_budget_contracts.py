from dataclasses import dataclass


@dataclass(frozen=True)
class Reservations:
    context_window: int
    output: int
    instructions: int
    history: int
    safety: int
    serialization: int


@dataclass(frozen=True)
class Passage:
    id: str
    tokens: int


def evidence_budget(reservations: Reservations) -> int:
    parts = (
        reservations.context_window,
        reservations.output,
        reservations.instructions,
        reservations.history,
        reservations.safety,
        reservations.serialization,
    )
    if any(part < 0 for part in parts):
        raise ValueError("token reservations must be nonnegative")
    remaining = reservations.context_window - sum(parts[1:])
    if remaining < 0:
        raise ValueError("reservations exceed context")
    return remaining


def pack(passages: tuple[Passage, ...], budget: int) -> tuple[Passage, ...]:
    if budget < 0:
        raise ValueError("negative budget")
    ids: set[str] = set()
    for passage in passages:
        if not passage.id or not passage.id.strip():
            raise ValueError("blank passage ID")
        if passage.tokens <= 0:
            raise ValueError("invalid token count")
        if passage.id in ids:
            raise ValueError("duplicate passage ID")
        ids.add(passage.id)
    selected: list[Passage] = []
    remaining = budget
    for passage in passages:
        if passage.tokens <= remaining:
            selected.append(passage)
            remaining -= passage.tokens
    return tuple(selected)


def serialized_prompt_fits(context_window: int, reserved_output: int,
                           actual_input_tokens: int) -> bool:
    if context_window < 0 or reserved_output < 0 or actual_input_tokens < 0:
        raise ValueError("token counts must be nonnegative")
    return reserved_output + actual_input_tokens <= context_window


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

    canonical = Reservations(4096, 900, 300, 500, 196, 0)
    check(evidence_budget(canonical) == 2200)
    ranked = (Passage("a", 1400), Passage("b", 1100), Passage("c", 700))
    check(tuple(p.id for p in pack(ranked, 2200)) == ("a", "c"))
    check(sum(p.tokens for p in pack(ranked, 2200)) == 2100)
    check(tuple(p.id for p in pack(ranked, 2500)) == ("a", "b"))
    check(tuple(p.id for p in pack(ranked, 3200)) == ("a", "b", "c"))
    check(len(pack((Passage("exact", 100),), 100)) == 1)
    check(not pack((Passage("too-large", 101),), 100))
    check(not pack((Passage("one", 1),), 0))
    check(not pack((), 0))
    check(evidence_budget(Reservations(100, 20, 20, 20, 20, 20)) == 0)
    check(evidence_budget(Reservations(4096, 900, 300, 500, 196, 120)) == 2080)
    check(serialized_prompt_fits(4096, 900, 3196))
    check(not serialized_prompt_fits(4096, 900, 3197))
    check(not serialized_prompt_fits((1 << 63) - 1, 1, (1 << 63) - 1))
    check(tuple(p.id for p in pack((Passage("p1", 70), Passage("p2", 40),
                                     Passage("p3", 30)), 100)) == ("p1", "p3"))
    check(tuple(p.id for p in pack((Passage("first", 60), Passage("second", 60)), 60)) == ("first",))
    check(tuple(p.id for p in pack((Passage("small", 1),
                                     Passage("large", (1 << 63) - 1)),
                                    (1 << 63) - 1)) == ("small",))
    check(ranked == (Passage("a", 1400), Passage("b", 1100), Passage("c", 700)))

    rejects(lambda: evidence_budget(Reservations(-1, 0, 0, 0, 0, 0)))
    rejects(lambda: evidence_budget(Reservations(1, -1, 0, 0, 0, 0)))
    rejects(lambda: evidence_budget(Reservations(10, 11, 0, 0, 0, 0)))
    rejects(lambda: evidence_budget(Reservations((1 << 63) - 1, 0, 0, 0, 0, -1)))
    rejects(lambda: pack((), -1))
    rejects(lambda: pack((Passage("a", 0),), 100))
    rejects(lambda: pack((Passage("a", -1),), 100))
    rejects(lambda: pack((Passage("a", 1), Passage("a", 2)), 100))
    rejects(lambda: pack((Passage("", 1),), 100))
    rejects(lambda: pack((Passage(" ", 1),), 100))
    rejects(lambda: pack((Passage(None, 1),), 100))  # type: ignore[arg-type]
    rejects(lambda: serialized_prompt_fits(-1, 0, 0))
    rejects(lambda: serialized_prompt_fits(10, -1, 0))
    rejects(lambda: serialized_prompt_fits(10, 0, -1))

    state = 0x20E71DEA
    for _ in range(10_000):
        state = next_state(state); budget = state % 900
        state = next_state(state); count = 1 + state % 12
        values: list[Passage] = []
        for index in range(count):
            state = next_state(state)
            values.append(Passage(f"p{index}", 1 + state % 300))
        actual = pack(tuple(values), budget)
        expected: list[str] = []
        oracle_remaining = budget
        for value in values:
            if value.tokens <= oracle_remaining:
                expected.append(value.id)
                oracle_remaining -= value.tokens
        assert [p.id for p in actual] == expected
        assert sum(p.tokens for p in actual) <= budget
        assert len({p.id for p in actual}) == len(actual)

    assert scenarios == 32, scenarios
    print("PARITY|BUDGET=CHECKED|ORDER=RANKED|SKIP=OVERSIZE|COUNTS=POSITIVE|IDS=UNIQUE|SERIALIZED=RECOUNTED|OVERFLOW=REJECTED|SELECTED=a,c")
    print("Evidence budget contracts passed: 32 scenario groups + 10000 seeded packing trials")


if __name__ == "__main__":
    main()
