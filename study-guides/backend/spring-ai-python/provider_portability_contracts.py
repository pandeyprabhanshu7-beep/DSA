"""Dependency-free contracts for provider capability admission, retries and migration."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from math import isfinite


class Feature(Enum):
    TEXT = "TEXT"
    STREAM = "STREAM"
    TOOLS = "TOOLS"
    STRICT_JSON = "STRICT_JSON"
    EMBEDDING = "EMBEDDING"


class Boundary(Enum):
    PORTABLE = "PORTABLE"
    PROVIDER_EXTENSION = "PROVIDER_EXTENSION"


class Observation(Enum):
    NOT_SENT = "NOT_SENT"
    REJECTED = "REJECTED"
    ACCEPTED_UNKNOWN = "ACCEPTED_UNKNOWN"
    COMPLETED = "COMPLETED"


class Next(Enum):
    RETRY = "RETRY"
    RECONCILE = "RECONCILE"
    RETURN = "RETURN"


@dataclass(frozen=True)
class Provider:
    id: str
    features: frozenset[Feature]
    workload_identity: bool
    idempotency_key: bool
    model_revision: str

    def __post_init__(self) -> None:
        if not self.id or not self.model_revision:
            raise ValueError("provider")


@dataclass(frozen=True)
class Request:
    operation_id: str
    required: frozenset[Feature]
    boundary: Boundary
    extension_owner: str | None = None

    def __post_init__(self) -> None:
        if not self.operation_id:
            raise ValueError("request")
        if self.boundary is Boundary.PORTABLE and self.extension_owner is not None:
            raise ValueError("portable extension")
        if self.boundary is Boundary.PROVIDER_EXTENSION and not self.extension_owner:
            raise ValueError("extension owner")


def admits(provider: Provider, request: Request) -> bool:
    return (provider.workload_identity
            and provider.features.issuperset(request.required)
            and (request.boundary is Boundary.PORTABLE or provider.id == request.extension_owner))


def choose(primary: Provider, alternate: Provider, request: Request) -> Provider:
    for provider in (primary, alternate):
        if admits(provider, request):
            return provider
    raise RuntimeError("no qualified provider")


def after(observation: Observation, replay_safe: bool, provider_dedupe: bool) -> Next:
    if observation in (Observation.NOT_SENT, Observation.REJECTED):
        return Next.RETRY
    if observation is Observation.ACCEPTED_UNKNOWN:
        return Next.RETRY if replay_safe and provider_dedupe else Next.RECONCILE
    return Next.RETURN


def promote(contract_pass: bool, quality: float, min_quality: float,
            p95_millis: float, max_p95_millis: float,
            cost_per_success: float, max_cost_per_success: float) -> bool:
    values = (quality, min_quality, p95_millis, max_p95_millis, cost_per_success, max_cost_per_success)
    if not all(isfinite(value) for value in values) or min_quality < 0 or max_p95_millis <= 0 or max_cost_per_success <= 0:
        raise ValueError("gate")
    return (contract_pass and quality >= min_quality
            and p95_millis <= max_p95_millis and cost_per_success <= max_cost_per_success)


def main() -> None:
    scenarios = 0
    managed = Provider("managed", frozenset((Feature.TEXT, Feature.STREAM, Feature.TOOLS,
                                               Feature.STRICT_JSON)), True, True, "m-17")
    local = Provider("local", frozenset((Feature.TEXT, Feature.STREAM, Feature.EMBEDDING)),
                     True, False, "sha256:abc")
    key_only = Provider("key-only", frozenset((Feature.TEXT,)), False, True, "m-2")

    portable = Request("op-1", frozenset((Feature.TEXT, Feature.STREAM)), Boundary.PORTABLE)
    tools = Request("op-2", frozenset((Feature.TEXT, Feature.TOOLS)), Boundary.PORTABLE)
    extension = Request("op-3", frozenset((Feature.TEXT,)), Boundary.PROVIDER_EXTENSION, "managed")
    checks = [
        admits(managed, portable), admits(local, portable), not admits(local, tools),
        not admits(key_only, Request("op-4", frozenset((Feature.TEXT,)), Boundary.PORTABLE)),
        admits(managed, extension), not admits(local, extension),
        choose(managed, local, portable) is managed, choose(local, managed, tools) is managed,
    ]
    for check in checks:
        assert check
        scenarios += 1
    try:
        choose(local, key_only, tools)
        raise AssertionError
    except RuntimeError:
        scenarios += 1
    for bad in (
        lambda: Request("bad", frozenset((Feature.TEXT,)), Boundary.PORTABLE, "managed"),
        lambda: Request("bad", frozenset((Feature.TEXT,)), Boundary.PROVIDER_EXTENSION),
    ):
        try:
            bad()
            raise AssertionError
        except ValueError:
            scenarios += 1

    checks = [
        after(Observation.NOT_SENT, False, False) is Next.RETRY,
        after(Observation.REJECTED, False, False) is Next.RETRY,
        after(Observation.ACCEPTED_UNKNOWN, False, True) is Next.RECONCILE,
        after(Observation.ACCEPTED_UNKNOWN, True, False) is Next.RECONCILE,
        after(Observation.ACCEPTED_UNKNOWN, True, True) is Next.RETRY,
        after(Observation.COMPLETED, False, False) is Next.RETURN,
        promote(True, .86, .82, 700, 800, .018, .020),
        not promote(False, .86, .82, 700, 800, .018, .020),
        not promote(True, .80, .82, 700, 800, .018, .020),
        not promote(True, .86, .82, 900, 800, .018, .020),
        not promote(True, .86, .82, 700, 800, .024, .020),
    ]
    for check in checks:
        assert check
        scenarios += 1
    try:
        promote(True, float("nan"), .82, 700, 800, .018, .020)
        raise AssertionError
    except ValueError:
        scenarios += 1

    state = 0x13ADA7
    promoted = 0
    for _ in range(10_000):
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        quality = .70 + ((state >> 8) % 2500) / 10_000
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        latency = 300 + ((state >> 12) % 801)
        state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
        cost = .005 + ((state >> 10) % 3000) / 100_000
        actual = promote(True, quality, .82, latency, 800, cost, .020)
        oracle = quality >= .82 and latency <= 800 and cost <= .020
        assert actual == oracle
        promoted += int(actual)

    print("PARITY|BOUNDARY=PORTABLE_CORE|EXTENSIONS=OWNED|IDENTITY=WORKLOAD"
          f"|UNKNOWN=RECONCILE|MIGRATION=EVALUATED|PROMOTED={promoted}")
    print(f"Python provider portability contracts passed: {scenarios} scenario groups + 10000 seeded trials")


if __name__ == "__main__":
    main()
