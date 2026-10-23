"""Dependency-free mirror of the Java-library-map boundary contracts."""
from dataclasses import dataclass
from enum import Enum, auto


class Need(Enum):
    SPRING_MODEL_ADAPTER = auto()
    JAVA_AI_SERVICE = auto()
    MCP_PROTOCOL = auto()
    SPRING_MCP_AUTOCONFIG = auto()
    AUTHORIZATION = auto()
    REACTIVE_STREAMS = auto()
    DTO_VALIDATION = auto()
    RESILIENCE = auto()
    TELEMETRY = auto()
    MULTI_ENGINE_INFERENCE = auto()
    DIRECT_ONNX = auto()
    TRANSACTIONAL_SQL = auto()


class Tool(Enum):
    SPRING_AI = auto()
    LANGCHAIN4J = auto()
    MCP_JAVA_SDK = auto()
    SPRING_AI_MCP_STARTER = auto()
    SPRING_SECURITY = auto()
    REACTOR_WEBFLUX = auto()
    JAKARTA_VALIDATION = auto()
    RESILIENCE4J = auto()
    MICROMETER_OTEL = auto()
    DJL = auto()
    ONNX_RUNTIME_JAVA = auto()
    JDBC = auto()


@dataclass(frozen=True)
class FrameworkProbe:
    abstains_without_evidence: bool
    rejects_unknown_citation: bool
    propagates_deadline: bool
    maps_typed_failure: bool

    @property
    def can_promote(self) -> bool:
        return all(self.__dict__.values())


@dataclass(frozen=True)
class InferenceArtifact:
    model_digest: str
    format: str
    opset: int
    preprocessing_digest: str
    input_name: str
    input_type: str
    input_shape: str
    runtime_provider: str

    def __post_init__(self) -> None:
        strings = (
            self.model_digest,
            self.format,
            self.preprocessing_digest,
            self.input_name,
            self.input_type,
            self.input_shape,
            self.runtime_provider,
        )
        if not all(value and value.strip() for value in strings):
            raise ValueError("complete inference identity is required")
        if self.opset <= 0:
            raise ValueError("opset must be positive")


@dataclass(frozen=True)
class ServiceEnvelope:
    schema_version: str
    operation_id: str
    absolute_deadline_epoch_ms: int
    trace_id: str
    capability: str

    def __post_init__(self) -> None:
        strings = (self.schema_version, self.operation_id, self.trace_id, self.capability)
        if not all(value and value.strip() for value in strings):
            raise ValueError("version, operation, trace and capability are required")
        if self.absolute_deadline_epoch_ms <= 0:
            raise ValueError("absolute deadline is required")

    def remaining_ms(self, now_epoch_ms: int) -> int:
        return max(0, self.absolute_deadline_epoch_ms - now_epoch_ms)


MAPPING = {
    Need.SPRING_MODEL_ADAPTER: Tool.SPRING_AI,
    Need.JAVA_AI_SERVICE: Tool.LANGCHAIN4J,
    Need.MCP_PROTOCOL: Tool.MCP_JAVA_SDK,
    Need.SPRING_MCP_AUTOCONFIG: Tool.SPRING_AI_MCP_STARTER,
    Need.AUTHORIZATION: Tool.SPRING_SECURITY,
    Need.REACTIVE_STREAMS: Tool.REACTOR_WEBFLUX,
    Need.DTO_VALIDATION: Tool.JAKARTA_VALIDATION,
    Need.RESILIENCE: Tool.RESILIENCE4J,
    Need.TELEMETRY: Tool.MICROMETER_OTEL,
    Need.MULTI_ENGINE_INFERENCE: Tool.DJL,
    Need.DIRECT_ONNX: Tool.ONNX_RUNTIME_JAVA,
    Need.TRANSACTIONAL_SQL: Tool.JDBC,
}


def select(needs: set[Need]) -> set[Tool]:
    return {MAPPING[need] for need in needs}


def admit_inference(in_flight: int, maximum: int, remaining_deadline_ms: int) -> bool:
    if in_flight < 0 or maximum <= 0:
        raise ValueError("invalid capacity")
    return in_flight < maximum and remaining_deadline_ms > 0


def safe_tool_call(request_authorized: bool, resource_authorized: bool, valid_dto: bool) -> bool:
    return request_authorized and resource_authorized and valid_dto


if __name__ == "__main__":
    mcp_service = select(
        {
            Need.SPRING_MODEL_ADAPTER,
            Need.MCP_PROTOCOL,
            Need.SPRING_MCP_AUTOCONFIG,
            Need.AUTHORIZATION,
            Need.DTO_VALIDATION,
            Need.TELEMETRY,
        }
    )
    assert mcp_service == {
        Tool.SPRING_AI,
        Tool.MCP_JAVA_SDK,
        Tool.SPRING_AI_MCP_STARTER,
        Tool.SPRING_SECURITY,
        Tool.JAKARTA_VALIDATION,
        Tool.MICROMETER_OTEL,
    }
    assert Tool.LANGCHAIN4J not in mcp_service
    assert safe_tool_call(True, True, True)
    assert not safe_tool_call(True, False, True)

    assert FrameworkProbe(True, True, True, True).can_promote
    assert not FrameworkProbe(True, False, True, True).can_promote

    assert select({Need.DIRECT_ONNX, Need.TELEMETRY}) == {
        Tool.ONNX_RUNTIME_JAVA,
        Tool.MICROMETER_OTEL,
    }
    assert select({Need.MULTI_ENGINE_INFERENCE, Need.TELEMETRY}) == {
        Tool.DJL,
        Tool.MICROMETER_OTEL,
    }
    artifact = InferenceArtifact(
        "sha256:abc", "onnx", 19, "sha256:pre", "tokens", "int64",
        "[batch,sequence]", "onnxruntime-cpu:1.24",
    )
    assert artifact.input_name == "tokens"
    assert admit_inference(3, 4, 25)
    assert not admit_inference(4, 4, 25)
    assert not admit_inference(0, 4, 0)

    envelope = ServiceEnvelope("v3", "classify-42", 10_000, "trace-7", "ticket-v2")
    assert envelope.remaining_ms(9_250) == 750
    assert envelope.remaining_ms(10_100) == 0

    assert select({Need.REACTIVE_STREAMS, Need.RESILIENCE, Need.TRANSACTIONAL_SQL}) == {
        Tool.REACTOR_WEBFLUX,
        Tool.RESILIENCE4J,
        Tool.JDBC,
    }

    invalid = (
        lambda: admit_inference(-1, 4, 25),
        lambda: InferenceArtifact("", "onnx", 19, "pre", "x", "float", "[1]", "cpu"),
        lambda: ServiceEnvelope("v3", "", 10_000, "trace-7", "ticket-v2"),
    )
    for action in invalid:
        try:
            action()
            raise AssertionError("expected ValueError")
        except ValueError:
            pass

    print("java_library_map_contracts.py: 21 scenario groups passed")
