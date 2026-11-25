import java.util.Random;

/**
 * Executable claim boundaries for Section 8 of the AI employment-impact guide.
 *
 * <p>The contract treats a severe downside as a conditional scenario. Longer-task
 * capability, reliable operation, affordable integration, workflow redesign and
 * weak demand absorption must align; physical reach broadens the affected scope
 * but is not, by itself, evidence of job loss.</p>
 */
public final class DownsideScenarioContracts {
    private static final int TRIALS = 20_000;
    private static final long SEED = 0xD0A5_1DE8L;

    record ScenarioPacket(
            boolean longHorizonCapability,
            boolean reliableEndToEnd,
            boolean acceptableConsequentialRisk,
            boolean affordableIntegration,
            boolean workflowRedesign,
            boolean physicalReach,
            boolean weakDemandAbsorption) {
    }

    static double compoundSuccess(double independentStepSuccess, int steps) {
        requireProbability(independentStepSuccess, "step success");
        if (steps < 1) {
            throw new IllegalArgumentException("steps must be positive");
        }
        return Math.pow(independentStepSuccess, steps);
    }

    static double workflowSuccess(double... stageSuccess) {
        if (stageSuccess == null || stageSuccess.length == 0) {
            throw new IllegalArgumentException("at least one stage is required");
        }
        double product = 1.0;
        for (double probability : stageSuccess) {
            requireProbability(probability, "stage success");
            product *= probability;
        }
        return product;
    }

    static double expectedFailures(long decisions, double failureProbability) {
        if (decisions < 0) {
            throw new IllegalArgumentException("decisions must be non-negative");
        }
        requireProbability(failureProbability, "failure probability");
        return decisions * failureProbability;
    }

    static double laborFactor(double outputDemandGrowth, double productivityGrowth) {
        if (!Double.isFinite(outputDemandGrowth) || outputDemandGrowth <= -1.0) {
            throw new IllegalArgumentException("demand growth must be finite and greater than -1");
        }
        if (!Double.isFinite(productivityGrowth) || productivityGrowth <= -1.0) {
            throw new IllegalArgumentException("productivity growth must be finite and greater than -1");
        }
        return (1.0 + outputDemandGrowth) / (1.0 + productivityGrowth);
    }

    static boolean supportsSevereDownside(ScenarioPacket packet) {
        return packet.longHorizonCapability()
                && packet.reliableEndToEnd()
                && packet.acceptableConsequentialRisk()
                && packet.affordableIntegration()
                && packet.workflowRedesign()
                && packet.weakDemandAbsorption();
    }

    static boolean physicalReachAmplifiesScope(ScenarioPacket packet) {
        return supportsSevereDownside(packet) && packet.physicalReach();
    }

    private static void requireProbability(double probability, String label) {
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException(label + " must be finite and in [0, 1]");
        }
    }

    private static void deterministicGroups() {
        int groups = 0;

        check(close(compoundSuccess(0.99, 100), 0.3660323412732292)); groups++;
        check(close(compoundSuccess(1.0, 50), 1.0)); groups++;
        check(close(compoundSuccess(0.0, 5), 0.0)); groups++;
        expectIllegal(() -> compoundSuccess(1.01, 2)); groups++;
        expectIllegal(() -> compoundSuccess(0.9, 0)); groups++;

        check(close(workflowSuccess(0.98, 0.97, 0.95, 0.99), 0.8940393)); groups++;
        check(close(workflowSuccess(1.0), 1.0)); groups++;
        expectIllegal(() -> workflowSuccess()); groups++;
        expectIllegal(() -> workflowSuccess(0.9, -0.1)); groups++;

        check(close(expectedFailures(1_000_000, 0.001), 1_000.0)); groups++;
        check(close(expectedFailures(0, 1.0), 0.0)); groups++;
        expectIllegal(() -> expectedFailures(-1, 0.1)); groups++;

        check(close(laborFactor(0.05, 0.20), 0.875)); groups++;
        check(close(laborFactor(0.30, 0.20), 1.0833333333333333)); groups++;
        expectIllegal(() -> laborFactor(-1.0, 0.2)); groups++;

        ScenarioPacket severe = new ScenarioPacket(true, true, true, true, true, false, true);
        ScenarioPacket broadened = new ScenarioPacket(true, true, true, true, true, true, true);
        ScenarioPacket reliableButNotAdopted = new ScenarioPacket(true, true, true, false, false, true, true);
        check(supportsSevereDownside(severe)); groups++;
        check(supportsSevereDownside(broadened)); groups++;
        check(physicalReachAmplifiesScope(broadened)); groups++;
        check(!physicalReachAmplifiesScope(severe)); groups++;
        check(!supportsSevereDownside(reliableButNotAdopted)); groups++;

        check(groups == 20);
    }

    private static void randomizedTrials() {
        Random random = new Random(SEED);
        int checks = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            double probability = random.nextDouble();
            int shorter = 1 + random.nextInt(100);
            int longer = shorter + random.nextInt(100);
            double shortSuccess = compoundSuccess(probability, shorter);
            double longSuccess = compoundSuccess(probability, longer);
            check(shortSuccess >= 0.0 && shortSuccess <= 1.0); checks++;
            check(longSuccess <= shortSuccess + 1e-12); checks++;

            double demand = -0.5 + random.nextDouble() * 2.0;
            double productivity = -0.5 + random.nextDouble() * 2.0;
            check(close(laborFactor(demand, productivity),
                    (1.0 + demand) / (1.0 + productivity))); checks++;

            ScenarioPacket oneFactor = switch (random.nextInt(7)) {
                case 0 -> new ScenarioPacket(true, false, false, false, false, false, false);
                case 1 -> new ScenarioPacket(false, true, false, false, false, false, false);
                case 2 -> new ScenarioPacket(false, false, true, false, false, false, false);
                case 3 -> new ScenarioPacket(false, false, false, true, false, false, false);
                case 4 -> new ScenarioPacket(false, false, false, false, true, false, false);
                case 5 -> new ScenarioPacket(false, false, false, false, false, true, false);
                default -> new ScenarioPacket(false, false, false, false, false, false, true);
            };
            check(!supportsSevereDownside(oneFactor)); checks++;
        }
        check(checks == TRIALS * 4);
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) <= 1e-12 * Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("contract failed");
        }
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedTrials();
        System.out.println("PASS groups=20 trials=20000 checks=80000");
    }
}
