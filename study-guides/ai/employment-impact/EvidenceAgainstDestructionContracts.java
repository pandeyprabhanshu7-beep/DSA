import java.util.Random;

/**
 * Executable claim boundaries for Section 7 of the AI employment-impact guide.
 *
 * <p>The contract separates descriptive association, causal headcount evidence,
 * observed task usage, and reliable delegation of a complete workflow.</p>
 */
public final class EvidenceAgainstDestructionContracts {
    private static final int TRIALS = 20_000;
    private static final long SEED = 0xE71D_2026L;

    enum Direction { UNKNOWN, GROWTH, FLAT, DECLINE }

    enum Claim {
        DESCRIPTIVE_ASSOCIATION,
        CAUSAL_HEADCOUNT_EFFECT,
        COMPLETE_WORKFLOW_DELEGATION,
        EXPOSURE_AUTOMATICALLY_DESTROYS_JOBS
    }

    record EvidencePacket(
            boolean exposureObserved,
            boolean outcomeObserved,
            boolean comparatorPresent,
            boolean baselinePresent,
            boolean matchedUnitAndHorizon,
            boolean causalDesign,
            boolean workplaceUseObserved,
            boolean completeWorkflowCoverage,
            boolean reliableSuccess,
            boolean authorizedDeployment,
            Direction headcountDirection) {

        EvidencePacket {
            if (headcountDirection == null) {
                throw new IllegalArgumentException("headcount direction is required");
            }
        }
    }

    static boolean supportsAssociation(EvidencePacket packet) {
        return packet.exposureObserved()
                && packet.outcomeObserved()
                && packet.comparatorPresent()
                && packet.baselinePresent()
                && packet.matchedUnitAndHorizon();
    }

    static boolean supportsCausalHeadcountEffect(EvidencePacket packet) {
        return supportsAssociation(packet)
                && packet.causalDesign()
                && packet.headcountDirection() != Direction.UNKNOWN;
    }

    static boolean supportsCompleteWorkflowDelegation(EvidencePacket packet) {
        return packet.workplaceUseObserved()
                && packet.completeWorkflowCoverage()
                && packet.reliableSuccess()
                && packet.authorizedDeployment();
    }

    static boolean supports(EvidencePacket packet, Claim claim) {
        return switch (claim) {
            case DESCRIPTIVE_ASSOCIATION -> supportsAssociation(packet);
            case CAUSAL_HEADCOUNT_EFFECT -> supportsCausalHeadcountEffect(packet);
            case COMPLETE_WORKFLOW_DELEGATION -> supportsCompleteWorkflowDelegation(packet);
            case EXPOSURE_AUTOMATICALLY_DESTROYS_JOBS -> false;
        };
    }

    private static EvidencePacket pwcCompanyAssociation() {
        return new EvidencePacket(
                true, true, true, true, true, false,
                false, false, false, false, Direction.GROWTH);
    }

    private static EvidencePacket aiSkillPostings() {
        return new EvidencePacket(
                true, true, true, true, true, false,
                false, false, false, false, Direction.GROWTH);
    }

    private static EvidencePacket anthropicTaskUse() {
        return new EvidencePacket(
                true, false, false, true, false, false,
                true, false, true, false, Direction.UNKNOWN);
    }

    private static EvidencePacket delegatedWorkflowTrial() {
        return new EvidencePacket(
                true, true, true, true, true, true,
                true, true, true, true, Direction.DECLINE);
    }

    private static int deterministicGroups() {
        int groups = 0;

        EvidencePacket pwc = pwcCompanyAssociation();
        require(supportsAssociation(pwc), "PwC packet supports association"); groups++;
        require(!supportsCausalHeadcountEffect(pwc), "association is not causation"); groups++;
        require(!supportsCompleteWorkflowDelegation(pwc), "company comparison is not workflow evidence"); groups++;
        require(!supports(pwc, Claim.EXPOSURE_AUTOMATICALLY_DESTROYS_JOBS),
                "positive association contradicts automatic destruction"); groups++;

        EvidencePacket skills = aiSkillPostings();
        require(supports(skills, Claim.DESCRIPTIVE_ASSOCIATION), "posting trend has a comparator"); groups++;
        require(!supports(skills, Claim.CAUSAL_HEADCOUNT_EFFECT), "posting growth is descriptive"); groups++;
        require(!supports(skills, Claim.COMPLETE_WORKFLOW_DELEGATION), "skills are not delegation"); groups++;
        require(!supports(skills, Claim.EXPOSURE_AUTOMATICALLY_DESTROYS_JOBS),
                "skill demand is not automatic destruction"); groups++;

        EvidencePacket use = anthropicTaskUse();
        require(use.workplaceUseObserved(), "Anthropic packet contains observed work use"); groups++;
        require(!supportsAssociation(use), "task use is not a matched headcount comparison"); groups++;
        require(!supportsCompleteWorkflowDelegation(use), "task success is not full workflow delegation"); groups++;
        require(!supports(use, Claim.EXPOSURE_AUTOMATICALLY_DESTROYS_JOBS),
                "task use cannot prove automatic destruction"); groups++;

        EvidencePacket trial = delegatedWorkflowTrial();
        require(supportsAssociation(trial), "trial has descriptive prerequisites"); groups++;
        require(supportsCausalHeadcountEffect(trial), "trial has a causal design and direction"); groups++;
        require(supportsCompleteWorkflowDelegation(trial), "trial covers full reliable authorized workflow"); groups++;
        require(!supports(trial, Claim.EXPOSURE_AUTOMATICALLY_DESTROYS_JOBS),
                "even a bounded causal result is not an automatic universal law"); groups++;

        EvidencePacket missingComparator = new EvidencePacket(
                true, true, false, true, true, true,
                true, true, true, true, Direction.DECLINE);
        require(!supportsAssociation(missingComparator), "association requires a comparator"); groups++;

        EvidencePacket unreliable = new EvidencePacket(
                true, true, true, true, true, false,
                true, true, false, true, Direction.UNKNOWN);
        require(!supportsCompleteWorkflowDelegation(unreliable), "delegation requires reliability"); groups++;

        EvidencePacket unauthorized = new EvidencePacket(
                false, false, false, false, false, false,
                true, true, true, false, Direction.UNKNOWN);
        require(!supportsCompleteWorkflowDelegation(unauthorized), "delegation requires authority"); groups++;

        EvidencePacket unmatched = new EvidencePacket(
                true, true, true, true, false, true,
                false, false, false, false, Direction.DECLINE);
        require(!supportsCausalHeadcountEffect(unmatched), "causal claim requires matched unit and horizon"); groups++;

        require(groups == 20, "expected exactly 20 deterministic groups");
        return groups;
    }

    private static long randomizedChecks() {
        Random random = new Random(SEED);
        long checks = 0;

        for (int trial = 0; trial < TRIALS; trial++) {
            boolean exposure = random.nextBoolean();
            boolean outcome = random.nextBoolean();
            boolean comparator = random.nextBoolean();
            boolean baseline = random.nextBoolean();
            boolean matched = random.nextBoolean();
            boolean causal = random.nextBoolean();
            boolean workplace = random.nextBoolean();
            boolean complete = random.nextBoolean();
            boolean reliable = random.nextBoolean();
            boolean authorized = random.nextBoolean();
            Direction direction = Direction.values()[random.nextInt(Direction.values().length)];

            EvidencePacket packet = new EvidencePacket(
                    exposure, outcome, comparator, baseline, matched, causal,
                    workplace, complete, reliable, authorized, direction);

            boolean expectedAssociation = exposure && outcome && comparator && baseline && matched;
            require(supportsAssociation(packet) == expectedAssociation,
                    "association prerequisites remain exact"); checks++;

            boolean expectedCausal = expectedAssociation && causal && direction != Direction.UNKNOWN;
            require(supportsCausalHeadcountEffect(packet) == expectedCausal,
                    "causal headcount gate remains exact"); checks++;

            boolean expectedDelegation = workplace && complete && reliable && authorized;
            require(supportsCompleteWorkflowDelegation(packet) == expectedDelegation,
                    "workflow delegation gate remains exact"); checks++;

            require(!supports(packet, Claim.EXPOSURE_AUTOMATICALLY_DESTROYS_JOBS),
                    "exposure never proves automatic destruction"); checks++;
        }

        require(checks == 80_000L, "expected exactly 80,000 randomized checks");
        return checks;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        int groups = deterministicGroups();
        long checks = randomizedChecks();
        System.out.printf("PASS groups=%d trials=%d checks=%d%n", groups, TRIALS, checks);
    }
}
