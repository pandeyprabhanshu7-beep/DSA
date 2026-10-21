import java.util.EnumSet;
import java.util.Objects;

/** Dependency-free contracts for selecting the narrowest useful AI application layer. */
public final class LayerSelectionContracts {
    enum Need { HTTP_API, MODEL_CALL, RETRIEVAL, OUTPUT_VALIDATION, TOOLS, DURABLE_BRANCHING }
    enum Layer { WEB, MODEL_ADAPTER, RETRIEVAL_ADAPTER, VALIDATOR, TOOL_ADAPTER, WORKFLOW_ENGINE }

    record ReleaseIdentity(
            String frameworkVersion,
            String runtimeVersion,
            String modelRevision,
            String operator) {
        ReleaseIdentity {
            if (frameworkVersion == null || frameworkVersion.isBlank()
                    || runtimeVersion == null || runtimeVersion.isBlank()
                    || modelRevision == null || modelRevision.isBlank()
                    || operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("all four release identities are required");
            }
        }
    }

    static EnumSet<Layer> select(EnumSet<Need> needs) {
        Objects.requireNonNull(needs, "needs");
        var layers = EnumSet.noneOf(Layer.class);
        if (needs.contains(Need.HTTP_API)) layers.add(Layer.WEB);
        if (needs.contains(Need.MODEL_CALL)) layers.add(Layer.MODEL_ADAPTER);
        if (needs.contains(Need.RETRIEVAL)) layers.add(Layer.RETRIEVAL_ADAPTER);
        if (needs.contains(Need.OUTPUT_VALIDATION)) layers.add(Layer.VALIDATOR);
        if (needs.contains(Need.TOOLS)) layers.add(Layer.TOOL_ADAPTER);
        if (needs.contains(Need.DURABLE_BRANCHING)) layers.add(Layer.WORKFLOW_ENGINE);
        return layers;
    }

    static boolean frameworkEscalationJustified(
            int duplicatedIntegrations, boolean durableBranching, boolean humanReview) {
        if (duplicatedIntegrations < 0) throw new IllegalArgumentException("negative integration count");
        return duplicatedIntegrations >= 3 || durableBranching || humanReview;
    }

    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        assert select(EnumSet.of(Need.HTTP_API, Need.MODEL_CALL, Need.OUTPUT_VALIDATION))
                .equals(EnumSet.of(Layer.WEB, Layer.MODEL_ADAPTER, Layer.VALIDATOR));
        assert select(EnumSet.of(Need.HTTP_API, Need.MODEL_CALL, Need.RETRIEVAL, Need.OUTPUT_VALIDATION))
                .equals(EnumSet.of(Layer.WEB, Layer.MODEL_ADAPTER, Layer.RETRIEVAL_ADAPTER, Layer.VALIDATOR));
        assert select(EnumSet.of(Need.MODEL_CALL, Need.TOOLS, Need.DURABLE_BRANCHING))
                .containsAll(EnumSet.of(Layer.MODEL_ADAPTER, Layer.TOOL_ADAPTER, Layer.WORKFLOW_ENGINE));
        assert !frameworkEscalationJustified(1, false, false);
        assert frameworkEscalationJustified(3, false, false);
        assert frameworkEscalationJustified(1, true, true);
        var id = new ReleaseIdentity("spring-ai:2.0.1", "ollama:0.12", "org/model@abc123", "team-a");
        assert id.modelRevision().endsWith("abc123");
        rejects(() -> new ReleaseIdentity("spring-ai:2.0.1", "", "model", "team-a"));
        rejects(() -> frameworkEscalationJustified(-1, false, false));
        System.out.println("LayerSelectionContracts: 9 scenario groups passed");
    }
}
