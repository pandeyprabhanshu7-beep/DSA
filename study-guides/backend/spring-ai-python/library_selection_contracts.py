"""Dependency-free mirror of the Python-library selection contracts."""
from dataclasses import dataclass
from enum import Enum, auto


class Need(Enum):
    NUMERICAL_MATH = auto()
    CLASSICAL_BASELINE = auto()
    LOCAL_PRETRAINED_MODEL = auto()
    HTTP_API = auto()
    TYPED_VALIDATION = auto()
    EXPLICIT_ASYNC_PROVIDER = auto()
    DURABLE_BRANCHING = auto()


class Tool(Enum):
    NUMPY = auto()
    SCIKIT_LEARN = auto()
    PYTORCH = auto()
    TRANSFORMERS = auto()
    FASTAPI = auto()
    PYDANTIC = auto()
    HTTPX = auto()
    LANGGRAPH = auto()


@dataclass(frozen=True)
class ModelArtifact:
    repository: str
    model_revision: str
    tokenizer_revision: str
    runtime_version: str

    def __post_init__(self) -> None:
        if not all(value and value.strip() for value in self.__dict__.values()):
            raise ValueError("artifact and runtime identities must be pinned")


@dataclass(frozen=True)
class TimeoutBudget:
    connect_ms: int
    read_ms: int
    pool_ms: int

    def __post_init__(self) -> None:
        if min(self.connect_ms, self.read_ms, self.pool_ms) <= 0:
            raise ValueError("every timeout phase must be positive")

    @property
    def maximum_wait_ms(self) -> int:
        return self.connect_ms + self.read_ms + self.pool_ms


@dataclass(frozen=True)
class Effect:
    operation_id: str
    already_applied: bool

    def __post_init__(self) -> None:
        if not self.operation_id or not self.operation_id.strip():
            raise ValueError("operation ID is required")

    @property
    def should_execute(self) -> bool:
        return not self.already_applied


def select(needs: set[Need]) -> set[Tool]:
    tools: set[Tool] = set()
    if Need.NUMERICAL_MATH in needs:
        tools.add(Tool.NUMPY)
    if Need.CLASSICAL_BASELINE in needs:
        tools.update({Tool.NUMPY, Tool.SCIKIT_LEARN})
    if Need.LOCAL_PRETRAINED_MODEL in needs:
        tools.update({Tool.PYTORCH, Tool.TRANSFORMERS})
    if Need.HTTP_API in needs:
        tools.add(Tool.FASTAPI)
    if Need.TYPED_VALIDATION in needs:
        tools.add(Tool.PYDANTIC)
    if Need.EXPLICIT_ASYNC_PROVIDER in needs:
        tools.add(Tool.HTTPX)
    if Need.DURABLE_BRANCHING in needs:
        tools.add(Tool.LANGGRAPH)
    return tools


def evaluation_is_clean(split_before_fit: bool, preprocessing_fit_on_train_only: bool) -> bool:
    return split_before_fit and preprocessing_fit_on_train_only


if __name__ == "__main__":
    baseline = select({Need.CLASSICAL_BASELINE})
    assert baseline == {Tool.NUMPY, Tool.SCIKIT_LEARN}
    assert Tool.TRANSFORMERS not in baseline
    assert evaluation_is_clean(True, True)
    assert not evaluation_is_clean(False, True)
    assert not evaluation_is_clean(True, False)

    local_model = select({Need.LOCAL_PRETRAINED_MODEL})
    assert local_model == {Tool.PYTORCH, Tool.TRANSFORMERS}
    artifact = ModelArtifact("org/model", "abc123", "abc123", "pytorch:2.14")
    assert artifact.model_revision == artifact.tokenizer_revision

    api = select({Need.HTTP_API, Need.TYPED_VALIDATION, Need.EXPLICIT_ASYNC_PROVIDER})
    assert api == {Tool.FASTAPI, Tool.PYDANTIC, Tool.HTTPX}
    budget = TimeoutBudget(250, 2_000, 100)
    assert budget.maximum_wait_ms == 2_350

    workflow = select({Need.DURABLE_BRANCHING, Need.EXPLICIT_ASYNC_PROVIDER})
    assert workflow == {Tool.HTTPX, Tool.LANGGRAPH}
    assert Effect("refund-42", False).should_execute
    assert not Effect("refund-42", True).should_execute

    invalid = (
        lambda: ModelArtifact("org/model", "", "abc123", "pytorch:2.14"),
        lambda: TimeoutBudget(0, 2_000, 100),
        lambda: Effect(" ", False),
    )
    for action in invalid:
        try:
            action()
            raise AssertionError("expected ValueError")
        except ValueError:
            pass

    print("library_selection_contracts.py: 15 scenario groups passed")
