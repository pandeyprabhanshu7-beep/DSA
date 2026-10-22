import java.util.EnumSet;
import java.util.Objects;

/** Dependency-free decision contracts for the Python library map. */
public final class LibrarySelectionContracts {
    enum Need {
        NUMERICAL_MATH,
        CLASSICAL_BASELINE,
        LOCAL_PRETRAINED_MODEL,
        HTTP_API,
        TYPED_VALIDATION,
        EXPLICIT_ASYNC_PROVIDER,
        DURABLE_BRANCHING
    }

    enum Tool {
        NUMPY,
        SCIKIT_LEARN,
        PYTORCH,
        TRANSFORMERS,
        FASTAPI,
        PYDANTIC,
        HTTPX,
        LANGGRAPH
    }

    record ModelArtifact(
            String repository,
            String modelRevision,
            String tokenizerRevision,
            String runtimeVersion) {
        ModelArtifact {
            if (blank(repository) || blank(modelRevision) || blank(tokenizerRevision)
                    || blank(runtimeVersion)) {
                throw new IllegalArgumentException("artifact and runtime identities must be pinned");
            }
        }
    }

    record TimeoutBudget(int connectMs, int readMs, int poolMs) {
        TimeoutBudget {
            if (connectMs <= 0 || readMs <= 0 || poolMs <= 0) {
                throw new IllegalArgumentException("every timeout phase must be positive");
            }
        }

        int maximumWaitMs() {
            return Math.addExact(Math.addExact(connectMs, readMs), poolMs);
        }
    }

    record Effect(String operationId, boolean alreadyApplied) {
        Effect {
            if (blank(operationId)) throw new IllegalArgumentException("operation ID is required");
        }

        boolean shouldExecute() {
            return !alreadyApplied;
        }
    }

    static EnumSet<Tool> select(EnumSet<Need> needs) {
        Objects.requireNonNull(needs, "needs");
        var tools = EnumSet.noneOf(Tool.class);
        if (needs.contains(Need.NUMERICAL_MATH)) tools.add(Tool.NUMPY);
        if (needs.contains(Need.CLASSICAL_BASELINE)) {
            tools.add(Tool.NUMPY);
            tools.add(Tool.SCIKIT_LEARN);
        }
        if (needs.contains(Need.LOCAL_PRETRAINED_MODEL)) {
            tools.add(Tool.PYTORCH);
            tools.add(Tool.TRANSFORMERS);
        }
        if (needs.contains(Need.HTTP_API)) tools.add(Tool.FASTAPI);
        if (needs.contains(Need.TYPED_VALIDATION)) tools.add(Tool.PYDANTIC);
        if (needs.contains(Need.EXPLICIT_ASYNC_PROVIDER)) tools.add(Tool.HTTPX);
        if (needs.contains(Need.DURABLE_BRANCHING)) tools.add(Tool.LANGGRAPH);
        return tools;
    }

    static boolean evaluationIsClean(boolean splitBeforeFit, boolean preprocessingFitOnTrainOnly) {
        return splitBeforeFit && preprocessingFitOnTrainOnly;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | ArithmeticException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    public static void main(String[] args) {
        var baseline = select(EnumSet.of(Need.CLASSICAL_BASELINE));
        assert baseline.equals(EnumSet.of(Tool.NUMPY, Tool.SCIKIT_LEARN));
        assert !baseline.contains(Tool.TRANSFORMERS);
        assert evaluationIsClean(true, true);
        assert !evaluationIsClean(false, true);
        assert !evaluationIsClean(true, false);

        var localModel = select(EnumSet.of(Need.LOCAL_PRETRAINED_MODEL));
        assert localModel.equals(EnumSet.of(Tool.PYTORCH, Tool.TRANSFORMERS));
        var artifact = new ModelArtifact("org/model", "abc123", "abc123", "pytorch:2.14");
        assert artifact.modelRevision().equals(artifact.tokenizerRevision());
        rejects(() -> new ModelArtifact("org/model", "", "abc123", "pytorch:2.14"));

        var api = select(EnumSet.of(Need.HTTP_API, Need.TYPED_VALIDATION, Need.EXPLICIT_ASYNC_PROVIDER));
        assert api.equals(EnumSet.of(Tool.FASTAPI, Tool.PYDANTIC, Tool.HTTPX));
        var budget = new TimeoutBudget(250, 2_000, 100);
        assert budget.maximumWaitMs() == 2_350;
        rejects(() -> new TimeoutBudget(0, 2_000, 100));
        rejects(() -> new TimeoutBudget(Integer.MAX_VALUE, 1, 1).maximumWaitMs());

        var workflow = select(EnumSet.of(Need.DURABLE_BRANCHING, Need.EXPLICIT_ASYNC_PROVIDER));
        assert workflow.equals(EnumSet.of(Tool.HTTPX, Tool.LANGGRAPH));
        assert new Effect("refund-42", false).shouldExecute();
        assert !new Effect("refund-42", true).shouldExecute();
        rejects(() -> new Effect(" ", false));

        System.out.println("LibrarySelectionContracts: 16 scenario groups passed");
    }
}
