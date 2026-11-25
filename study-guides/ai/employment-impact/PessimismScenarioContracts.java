import java.util.Random;

/**
 * Executable claim boundaries for Section 9 of the AI employment-impact guide.
 *
 * <p>The contract separates task assistance, retained human authority, demand
 * rebound, new-work creation and adoption timing. Each can justify a bounded
 * downward revision, but none proves that AI has no displacement effect.</p>
 */
public final class PessimismScenarioContracts {
    private static final int TRIALS = 20_000;
    private static final long SEED = 0x0F71_1A57L;

    record RevisionPacket(
            boolean matchedHorizonAndUnit,
            boolean unreliableWithoutReview,
            boolean measuredHumanAiComplementarity,
            boolean retainedHumanAuthority,
            boolean measuredDemandRebound,
            boolean measuredWorkerAbsorption,
            boolean slowObservedAdoption) {
    }

    static double acceptedOutputsPerHour(
            double attemptsPerHour, double completionRate, double acceptanceRate) {
        requireNonNegativeFinite(attemptsPerHour, "attempts per hour");
        requireProbability(completionRate, "completion rate");
        requireProbability(acceptanceRate, "acceptance rate");
        return attemptsPerHour * completionRate * acceptanceRate;
    }

    static double demandMultiplier(double priceReduction, double demandElasticity) {
        requireProbabilityBelowOne(priceReduction, "price reduction");
        requireNonNegativeFinite(demandElasticity, "demand elasticity");
        return Math.pow(1.0 - priceReduction, -demandElasticity);
    }

    static double reboundLaborFactor(
            double priceReduction, double demandElasticity, double productivityGrowth) {
        if (!Double.isFinite(productivityGrowth) || productivityGrowth <= -1.0) {
            throw new IllegalArgumentException("productivity growth must be finite and greater than -1");
        }
        return demandMultiplier(priceReduction, demandElasticity) / (1.0 + productivityGrowth);
    }

    static double realizedDisplacementShare(
            double exposedTaskShare, double adoptionShare, double removalShare) {
        requireProbability(exposedTaskShare, "exposed task share");
        requireProbability(adoptionShare, "adoption share");
        requireProbability(removalShare, "removal share");
        return exposedTaskShare * adoptionShare * removalShare;
    }

    static long netJobs(long createdJobs, long displacedJobs) {
        if (createdJobs < 0 || displacedJobs < 0) {
            throw new IllegalArgumentException("gross job flows must be non-negative");
        }
        return Math.subtractExact(createdJobs, displacedJobs);
    }

    static int downwardRevisionSignals(RevisionPacket packet) {
        int signals = 0;
        if (packet.unreliableWithoutReview()) signals++;
        if (packet.measuredHumanAiComplementarity()) signals++;
        if (packet.retainedHumanAuthority()) signals++;
        if (packet.measuredDemandRebound()) signals++;
        if (packet.measuredWorkerAbsorption()) signals++;
        if (packet.slowObservedAdoption()) signals++;
        return signals;
    }

    static boolean supportsDownwardRevision(RevisionPacket packet) {
        return packet.matchedHorizonAndUnit() && downwardRevisionSignals(packet) > 0;
    }

    private static void requireProbability(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + " must be finite and in [0, 1]");
        }
    }

    private static void requireProbabilityBelowOne(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value >= 1.0) {
            throw new IllegalArgumentException(label + " must be finite and in [0, 1)");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void deterministicGroups() {
        int groups = 0;

        check(close(acceptedOutputsPerHour(10.0, 0.9, 0.8), 7.2)); groups++;
        check(close(acceptedOutputsPerHour(0.0, 1.0, 1.0), 0.0)); groups++;
        expectIllegal(() -> acceptedOutputsPerHour(-1.0, 0.9, 0.9)); groups++;
        expectIllegal(() -> acceptedOutputsPerHour(1.0, 1.1, 0.9)); groups++;

        check(close(demandMultiplier(0.20, 1.5), 1.3975424859373686)); groups++;
        check(close(demandMultiplier(0.0, 5.0), 1.0)); groups++;
        expectIllegal(() -> demandMultiplier(1.0, 1.0)); groups++;
        expectIllegal(() -> demandMultiplier(0.2, -1.0)); groups++;

        check(close(reboundLaborFactor(0.20, 1.5, 0.25), 1.118033988749895)); groups++;
        check(close(reboundLaborFactor(0.20, 0.5, 0.25), 0.8944271909999159)); groups++;
        expectIllegal(() -> reboundLaborFactor(0.2, 1.0, -1.0)); groups++;

        check(close(realizedDisplacementShare(0.60, 0.20, 0.50), 0.06)); groups++;
        check(close(realizedDisplacementShare(1.0, 1.0, 1.0), 1.0)); groups++;
        expectIllegal(() -> realizedDisplacementShare(0.5, 1.1, 0.5)); groups++;

        check(netJobs(170_000_000L, 92_000_000L) == 78_000_000L); groups++;
        expectIllegal(() -> netJobs(-1, 0)); groups++;

        RevisionPacket matched = new RevisionPacket(true, false, true, true, false, false, false);
        RevisionPacket mismatched = new RevisionPacket(false, true, true, true, true, true, true);
        RevisionPacket empty = new RevisionPacket(true, false, false, false, false, false, false);
        check(downwardRevisionSignals(matched) == 2); groups++;
        check(supportsDownwardRevision(matched)); groups++;
        check(!supportsDownwardRevision(mismatched)); groups++;
        check(!supportsDownwardRevision(empty)); groups++;

        check(groups == 20);
    }

    private static void randomizedTrials() {
        Random random = new Random(SEED);
        int checks = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            double attempts = random.nextDouble() * 100.0;
            double completion = random.nextDouble();
            double acceptance = random.nextDouble();
            double accepted = acceptedOutputsPerHour(attempts, completion, acceptance);
            check(accepted >= 0.0 && accepted <= attempts + 1e-12); checks++;

            double exposed = random.nextDouble();
            double adoption = random.nextDouble();
            double removal = random.nextDouble();
            double realized = realizedDisplacementShare(exposed, adoption, removal);
            check(realized <= exposed + 1e-12); checks++;

            double priceReduction = random.nextDouble() * 0.8;
            double elasticity = random.nextDouble() * 3.0;
            check(demandMultiplier(priceReduction, elasticity) >= 1.0 - 1e-12); checks++;

            boolean[] flags = new boolean[6];
            int expected = 0;
            for (int i = 0; i < flags.length; i++) {
                flags[i] = random.nextBoolean();
                if (flags[i]) expected++;
            }
            RevisionPacket packet = new RevisionPacket(true, flags[0], flags[1], flags[2],
                    flags[3], flags[4], flags[5]);
            check(downwardRevisionSignals(packet) == expected); checks++;
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
        if (!condition) throw new AssertionError("contract failed");
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedTrials();
        System.out.println("PASS groups=20 trials=20000 checks=80000");
    }
}
