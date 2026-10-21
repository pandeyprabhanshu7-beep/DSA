"""Dependency-free mirror of the Java layer-selection contract."""
from dataclasses import dataclass
from enum import Enum, auto


class Need(Enum):
    HTTP_API = auto(); MODEL_CALL = auto(); RETRIEVAL = auto()
    OUTPUT_VALIDATION = auto(); TOOLS = auto(); DURABLE_BRANCHING = auto()


class Layer(Enum):
    WEB = auto(); MODEL_ADAPTER = auto(); RETRIEVAL_ADAPTER = auto()
    VALIDATOR = auto(); TOOL_ADAPTER = auto(); WORKFLOW_ENGINE = auto()


MAPPING = {
    Need.HTTP_API: Layer.WEB,
    Need.MODEL_CALL: Layer.MODEL_ADAPTER,
    Need.RETRIEVAL: Layer.RETRIEVAL_ADAPTER,
    Need.OUTPUT_VALIDATION: Layer.VALIDATOR,
    Need.TOOLS: Layer.TOOL_ADAPTER,
    Need.DURABLE_BRANCHING: Layer.WORKFLOW_ENGINE,
}


@dataclass(frozen=True)
class ReleaseIdentity:
    framework_version: str
    runtime_version: str
    model_revision: str
    operator: str

    def __post_init__(self) -> None:
        if not all(value and value.strip() for value in self.__dict__.values()):
            raise ValueError("all four release identities are required")


def select(needs: set[Need]) -> set[Layer]:
    return {MAPPING[need] for need in needs}


def framework_escalation_justified(
    duplicated_integrations: int, durable_branching: bool, human_review: bool
) -> bool:
    if duplicated_integrations < 0:
        raise ValueError("negative integration count")
    return duplicated_integrations >= 3 or durable_branching or human_review


if __name__ == "__main__":
    assert select({Need.HTTP_API, Need.MODEL_CALL, Need.OUTPUT_VALIDATION}) == {
        Layer.WEB, Layer.MODEL_ADAPTER, Layer.VALIDATOR
    }
    assert Layer.RETRIEVAL_ADAPTER in select({Need.MODEL_CALL, Need.RETRIEVAL})
    assert Layer.WORKFLOW_ENGINE in select({Need.MODEL_CALL, Need.DURABLE_BRANCHING})
    assert not framework_escalation_justified(1, False, False)
    assert framework_escalation_justified(3, False, False)
    assert framework_escalation_justified(1, True, True)
    identity = ReleaseIdentity("fastapi:0.120", "ollama:0.12", "org/model@abc123", "team-a")
    assert identity.model_revision.endswith("abc123")
    for invalid in (
        lambda: ReleaseIdentity("fastapi:0.120", "", "model", "team-a"),
        lambda: framework_escalation_justified(-1, False, False),
    ):
        try:
            invalid()
            raise AssertionError("expected ValueError")
        except ValueError:
            pass
    print("layer_selection_contracts.py: 9 scenario groups passed")
