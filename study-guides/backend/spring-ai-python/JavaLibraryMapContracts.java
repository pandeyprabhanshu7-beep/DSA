import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Dependency-free boundary contracts for the Java library map. */
public final class JavaLibraryMapContracts {
    enum Need {
        SPRING_MODEL_ADAPTER,
        JAVA_AI_SERVICE,
        MCP_PROTOCOL,
        SPRING_MCP_AUTOCONFIG,
        AUTHORIZATION,
        REACTIVE_STREAMS,
        DTO_VALIDATION,
        RESILIENCE,
        TELEMETRY,
        MULTI_ENGINE_INFERENCE,
        DIRECT_ONNX,
        TRANSACTIONAL_SQL
    }

    enum Tool {
        SPRING_AI,
        LANGCHAIN4J,
        MCP_JAVA_SDK,
        SPRING_AI_MCP_STARTER,
        SPRING_SECURITY,
        REACTOR_WEBFLUX,
        JAKARTA_VALIDATION,
        RESILIENCE4J,
        MICROMETER_OTEL,
        DJL,
        ONNX_RUNTIME_JAVA,
        JDBC
    }

    record FrameworkProbe(
            boolean abstainsWithoutEvidence,
            boolean rejectsUnknownCitation,
            boolean propagatesDeadline,
            boolean mapsTypedFailure) {
        boolean canPromote() {
            return abstainsWithoutEvidence && rejectsUnknownCitation
                    && propagatesDeadline && mapsTypedFailure;
        }
    }

    record InferenceArtifact(
            String modelDigest,
            String format,
            int opset,
            String preprocessingDigest,
            String inputName,
            String inputType,
            String inputShape,
            String runtimeProvider) {
        InferenceArtifact {
            if (Set.of(modelDigest, format, preprocessingDigest, inputName,
                    inputType, inputShape, runtimeProvider).stream().anyMatch(JavaLibraryMapContracts::blank)) {
                throw new IllegalArgumentException("complete inference identity is required");
            }
            if (opset <= 0) throw new IllegalArgumentException("opset must be positive");
        }
    }

    record ServiceEnvelope(
            String schemaVersion,
            String operationId,
            long absoluteDeadlineEpochMs,
            String traceId,
            String capability) {
        ServiceEnvelope {
            if (Set.of(schemaVersion, operationId, traceId, capability).stream()
                    .anyMatch(JavaLibraryMapContracts::blank)) {
                throw new IllegalArgumentException("version, operation, trace and capability are required");
            }
            if (absoluteDeadlineEpochMs <= 0) {
                throw new IllegalArgumentException("absolute deadline is required");
            }
        }

        long remainingMs(long nowEpochMs) {
            return Math.max(0, absoluteDeadlineEpochMs - nowEpochMs);
        }
    }

    static EnumSet<Tool> select(EnumSet<Need> needs) {
        var mapping = Map.ofEntries(
                Map.entry(Need.SPRING_MODEL_ADAPTER, Tool.SPRING_AI),
                Map.entry(Need.JAVA_AI_SERVICE, Tool.LANGCHAIN4J),
                Map.entry(Need.MCP_PROTOCOL, Tool.MCP_JAVA_SDK),
                Map.entry(Need.SPRING_MCP_AUTOCONFIG, Tool.SPRING_AI_MCP_STARTER),
                Map.entry(Need.AUTHORIZATION, Tool.SPRING_SECURITY),
                Map.entry(Need.REACTIVE_STREAMS, Tool.REACTOR_WEBFLUX),
                Map.entry(Need.DTO_VALIDATION, Tool.JAKARTA_VALIDATION),
                Map.entry(Need.RESILIENCE, Tool.RESILIENCE4J),
                Map.entry(Need.TELEMETRY, Tool.MICROMETER_OTEL),
                Map.entry(Need.MULTI_ENGINE_INFERENCE, Tool.DJL),
                Map.entry(Need.DIRECT_ONNX, Tool.ONNX_RUNTIME_JAVA),
                Map.entry(Need.TRANSACTIONAL_SQL, Tool.JDBC));
        var selected = EnumSet.noneOf(Tool.class);
        needs.forEach(need -> selected.add(mapping.get(need)));
        return selected;
    }

    static boolean admitInference(int inFlight, int maximum, long remainingDeadlineMs) {
        if (inFlight < 0 || maximum <= 0) throw new IllegalArgumentException("invalid capacity");
        return inFlight < maximum && remainingDeadlineMs > 0;
    }

    static boolean safeToolCall(boolean requestAuthorized, boolean resourceAuthorized, boolean validDto) {
        return requestAuthorized && resourceAuthorized && validDto;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    public static void main(String[] args) {
        var mcpService = select(EnumSet.of(
                Need.SPRING_MODEL_ADAPTER, Need.MCP_PROTOCOL, Need.SPRING_MCP_AUTOCONFIG,
                Need.AUTHORIZATION, Need.DTO_VALIDATION, Need.TELEMETRY));
        assert mcpService.equals(EnumSet.of(
                Tool.SPRING_AI, Tool.MCP_JAVA_SDK, Tool.SPRING_AI_MCP_STARTER,
                Tool.SPRING_SECURITY, Tool.JAKARTA_VALIDATION, Tool.MICROMETER_OTEL));
        assert !mcpService.contains(Tool.LANGCHAIN4J);
        assert safeToolCall(true, true, true);
        assert !safeToolCall(true, false, true);

        var passingProbe = new FrameworkProbe(true, true, true, true);
        assert passingProbe.canPromote();
        assert !new FrameworkProbe(true, false, true, true).canPromote();

        var directOnnx = select(EnumSet.of(Need.DIRECT_ONNX, Need.TELEMETRY));
        assert directOnnx.equals(EnumSet.of(Tool.ONNX_RUNTIME_JAVA, Tool.MICROMETER_OTEL));
        var portable = select(EnumSet.of(Need.MULTI_ENGINE_INFERENCE, Need.TELEMETRY));
        assert portable.equals(EnumSet.of(Tool.DJL, Tool.MICROMETER_OTEL));
        var artifact = new InferenceArtifact(
                "sha256:abc", "onnx", 19, "sha256:pre", "tokens", "int64",
                "[batch,sequence]", "onnxruntime-cpu:1.24");
        assert artifact.inputName().equals("tokens");
        assert admitInference(3, 4, 25);
        assert !admitInference(4, 4, 25);
        assert !admitInference(0, 4, 0);
        rejects(() -> admitInference(-1, 4, 25));
        rejects(() -> new InferenceArtifact("", "onnx", 19, "pre", "x", "float", "[1]", "cpu"));

        var envelope = new ServiceEnvelope("v3", "classify-42", 10_000, "trace-7", "ticket-v2");
        assert envelope.remainingMs(9_250) == 750;
        assert envelope.remainingMs(10_100) == 0;
        rejects(() -> new ServiceEnvelope("v3", "", 10_000, "trace-7", "ticket-v2"));

        var reactiveSql = select(EnumSet.of(
                Need.REACTIVE_STREAMS, Need.RESILIENCE, Need.TRANSACTIONAL_SQL));
        assert reactiveSql.equals(EnumSet.of(Tool.REACTOR_WEBFLUX, Tool.RESILIENCE4J, Tool.JDBC));

        System.out.println("JavaLibraryMapContracts: 21 scenario groups passed");
    }
}
