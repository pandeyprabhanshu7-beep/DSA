"""Dependency-free contracts for synchronous answers and replay-safe ingestion jobs."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from hashlib import sha256


class AnswerOutcome(Enum):
    AUTH_REQUIRED = 0
    FORBIDDEN = 1
    INVALID_REQUEST = 2
    OVERLOADED = 3
    DEPENDENCY_UNAVAILABLE = 4
    UPSTREAM_TIMEOUT = 5
    INSUFFICIENT_EVIDENCE = 6
    COMPLETE = 7


class SubmitOutcome(Enum):
    ACCEPTED = "ACCEPTED"
    REPLAY_EXISTING = "REPLAY_EXISTING"
    CONFLICT = "CONFLICT"
    ROLLED_BACK = "ROLLED_BACK"


class ReadOutcome(Enum):
    FOUND = "FOUND"
    NOT_FOUND = "NOT_FOUND"


class JobState(Enum):
    ACCEPTED = 0
    EXTRACTED = 1
    INDEXED = 2
    COMPLETE = 3


@dataclass(frozen=True)
class AnswerRequest:
    authenticated: bool
    authorized: bool
    valid: bool
    capacity: bool
    dependency: bool
    within_deadline: bool
    evidence_sufficient: bool


@dataclass(frozen=True)
class Submission:
    outcome: SubmitOutcome
    job_id: str | None
    status: int


def answer_outcome(request: AnswerRequest) -> AnswerOutcome:
    if not request.authenticated:
        return AnswerOutcome.AUTH_REQUIRED
    if not request.authorized:
        return AnswerOutcome.FORBIDDEN
    if not request.valid:
        return AnswerOutcome.INVALID_REQUEST
    if not request.capacity:
        return AnswerOutcome.OVERLOADED
    if not request.dependency:
        return AnswerOutcome.DEPENDENCY_UNAVAILABLE
    if not request.within_deadline:
        return AnswerOutcome.UPSTREAM_TIMEOUT
    if not request.evidence_sufficient:
        return AnswerOutcome.INSUFFICIENT_EVIDENCE
    return AnswerOutcome.COMPLETE


def status(outcome: AnswerOutcome) -> int:
    return {
        AnswerOutcome.AUTH_REQUIRED: 401,
        AnswerOutcome.FORBIDDEN: 403,
        AnswerOutcome.INVALID_REQUEST: 422,
        AnswerOutcome.OVERLOADED: 429,
        AnswerOutcome.DEPENDENCY_UNAVAILABLE: 503,
        AnswerOutcome.UPSTREAM_TIMEOUT: 504,
        AnswerOutcome.INSUFFICIENT_EVIDENCE: 200,
        AnswerOutcome.COMPLETE: 200,
    }[outcome]


class JobStore:
    def __init__(self) -> None:
        self.keys: dict[tuple[str, str, str], tuple[str, str]] = {}
        self.jobs: dict[str, tuple[str, JobState]] = {}
        self.outbox: list[str] = []
        self.sent: set[str] = set()
        self.applied_stages: set[tuple[str, JobState]] = set()
        self.deliveries = 0
        self.next_job = 1

    @staticmethod
    def _require(value: str | None, maximum: int, name: str) -> None:
        if value is None or not value.strip() or len(value) > maximum:
            raise ValueError(f"invalid {name}")

    def submit(self, tenant: str, operation: str, key: str, payload_hash: str,
               fail_before_commit: bool = False) -> Submission:
        self._require(tenant, 64, "tenant")
        self._require(operation, 64, "operation")
        self._require(key, 128, "idempotency key")
        self._require(payload_hash, 128, "payload hash")
        scope = (tenant, operation, key)
        existing = self.keys.get(scope)
        if existing is not None:
            old_hash, job_id = existing
            return Submission(SubmitOutcome.REPLAY_EXISTING if old_hash == payload_hash
                              else SubmitOutcome.CONFLICT, job_id,
                              202 if old_hash == payload_hash else 409)
        if fail_before_commit:
            return Submission(SubmitOutcome.ROLLED_BACK, None, 503)
        job_id = f"job_{self.next_job}"
        self.next_job += 1
        # These three writes represent one database transaction.
        self.keys[scope] = (payload_hash, job_id)
        self.jobs[job_id] = (tenant, JobState.ACCEPTED)
        self.outbox.append(job_id)
        return Submission(SubmitOutcome.ACCEPTED, job_id, 202)

    def relay(self, job_id: str, crash_after_publish: bool) -> None:
        if job_id not in self.outbox or job_id in self.sent:
            return
        self.deliveries += 1
        if not crash_after_publish:
            self.sent.add(job_id)

    def apply_stage(self, job_id: str, next_state: JobState) -> bool:
        current = self.jobs.get(job_id)
        if current is None:
            return False
        stage_key = (job_id, next_state)
        if stage_key in self.applied_stages:
            return False
        tenant, state = current
        if next_state.value != state.value + 1:
            return False
        self.applied_stages.add(stage_key)
        self.jobs[job_id] = (tenant, next_state)
        return True

    def read(self, verified_tenant: str, job_id: str) -> ReadOutcome:
        job = self.jobs.get(job_id)
        return (ReadOutcome.FOUND if job is not None and job[0] == verified_tenant
                else ReadOutcome.NOT_FOUND)


def payload_hash(canonical_payload: str) -> str:
    return sha256(canonical_payload.encode()).hexdigest()


def main() -> None:
    scenarios = 0
    answer_cases = (
        AnswerRequest(False, False, False, False, False, False, False),
        AnswerRequest(True, False, False, False, False, False, False),
        AnswerRequest(True, True, False, False, False, False, False),
        AnswerRequest(True, True, True, False, False, False, False),
        AnswerRequest(True, True, True, True, False, False, False),
        AnswerRequest(True, True, True, True, True, False, False),
        AnswerRequest(True, True, True, True, True, True, False),
        AnswerRequest(True, True, True, True, True, True, True),
    )
    statuses = (401, 403, 422, 429, 503, 504, 200, 200)
    for request, expected, expected_status in zip(
            answer_cases, AnswerOutcome, statuses, strict=True):
        assert answer_outcome(request) is expected
        assert status(expected) == expected_status
        scenarios += 1

    store = JobStore()
    hash_a = payload_hash("source=payments&revision=r8")
    hash_b = payload_hash("source=payments&revision=r9")
    first = store.submit("tenant-a", "ingest", "key-1", hash_a)
    assert (first.outcome is SubmitOutcome.ACCEPTED and first.status == 202
            and len(store.keys) == len(store.jobs) == len(store.outbox) == 1)
    scenarios += 1
    replay = store.submit("tenant-a", "ingest", "key-1", hash_a)
    assert (replay.outcome is SubmitOutcome.REPLAY_EXISTING
            and replay.job_id == first.job_id and len(store.jobs) == 1)
    scenarios += 1
    conflict = store.submit("tenant-a", "ingest", "key-1", hash_b)
    assert conflict.outcome is SubmitOutcome.CONFLICT and conflict.status == 409
    scenarios += 1
    assert store.submit("tenant-b", "ingest", "key-1", hash_b).outcome is SubmitOutcome.ACCEPTED
    scenarios += 1
    assert store.submit("tenant-a", "reindex", "key-1", hash_b).outcome is SubmitOutcome.ACCEPTED
    scenarios += 1

    rollback = JobStore()
    failed = rollback.submit("tenant-a", "ingest", "key-2", hash_a, True)
    assert (failed.outcome is SubmitOutcome.ROLLED_BACK and not rollback.keys
            and not rollback.jobs and not rollback.outbox)
    scenarios += 1
    assert rollback.submit("tenant-a", "ingest", "key-2", hash_a).outcome is SubmitOutcome.ACCEPTED
    scenarios += 1

    assert first.job_id is not None
    store.relay(first.job_id, True)
    store.relay(first.job_id, False)
    store.relay(first.job_id, False)
    assert store.deliveries == 2 and first.job_id in store.sent
    scenarios += 1
    assert not store.apply_stage(first.job_id, JobState.INDEXED)
    scenarios += 1
    assert store.apply_stage(first.job_id, JobState.EXTRACTED)
    scenarios += 1
    assert not store.apply_stage(first.job_id, JobState.EXTRACTED)
    scenarios += 1
    assert store.apply_stage(first.job_id, JobState.INDEXED)
    scenarios += 1
    assert store.apply_stage(first.job_id, JobState.COMPLETE)
    scenarios += 1
    assert not store.apply_stage(first.job_id, JobState.COMPLETE)
    scenarios += 1
    assert store.read("tenant-a", first.job_id) is ReadOutcome.FOUND
    scenarios += 1
    assert store.read("tenant-b", first.job_id) is ReadOutcome.NOT_FOUND
    scenarios += 1
    assert store.read("tenant-a", "missing") is ReadOutcome.NOT_FOUND
    scenarios += 1

    for key in (None, "", " ", "x" * 129):
        try:
            store.submit("tenant-a", "ingest", key, hash_a)  # type: ignore[arg-type]
            raise AssertionError
        except ValueError:
            scenarios += 1
    assert len(hash_a) == 64 and hash_a == payload_hash("source=payments&revision=r8")
    scenarios += 1

    state = 0x17A91
    ready = 0
    for _ in range(10_000):
        flags = []
        for index in range(7):
            state = (state * 1_664_525 + 1_013_904_223) & 0xFFFFFFFF
            flags.append(state % (index + 3) != 0)
        request = AnswerRequest(*flags)
        actual = answer_outcome(request)
        oracle = next((outcome for outcome, flag in zip(AnswerOutcome, flags, strict=False)
                       if not flag), AnswerOutcome.COMPLETE)
        assert actual is oracle
        ready += int(actual is AnswerOutcome.COMPLETE)

    assert scenarios == 30
    print("PARITY|SYNC=PROVIDER_NEUTRAL|ERROR=PROBLEM_DETAIL|ASYNC=202_LOCATION"
          "|IDEMPOTENCY=SCOPED_HASH|OUTBOX=AT_LEAST_ONCE|WORKER=STAGE_DEDUP"
          f"|READY={ready}")
    print(f"Python durable-API contracts passed: {scenarios} scenario groups "
          "+ 10000 seeded answer trials")


if __name__ == "__main__":
    main()
