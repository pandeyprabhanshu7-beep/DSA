import java.util.Random;

/**
 * Executable claim boundaries for Section 5 of the AI employment-impact guide.
 *
 * <p>This is deliberately not a job-safety score. It separates bounded task
 * assistance, a retained human responsibility loop, and an employment outcome
 * supported by projection metadata.</p>
 */
public final class RelativeResistanceContracts {
    private static final int TRIALS = 20_000;
    private static final long SEED = 0x5AFE_2026L;

    enum Direction { UNKNOWN, GROWTH, FLAT, DECLINE }

    enum Claim {
        TASK_ASSISTANCE,
        HUMAN_LOOP_RETAINED,
        PROJECTED_GROWTH,
        PERMANENT_JOB_ELIMINATION
    }

    record WorkPacket(
            boolean digitallyBoundedTask,
            boolean physicalExecution,
            boolean accountableApproval,
            boolean severeConsequence,
            boolean trustOwnership,
            boolean crossSystemCoordination,
            Direction projectionDirection,
            String projectionGeography,
            String projectionHorizon) {

        WorkPacket {
            if (projectionDirection == null) {
                throw new IllegalArgumentException("projection direction is required");
            }
        }
    }

    static boolean retainsHumanLoop(WorkPacket work) {
        return work.physicalExecution()
                || work.accountableApproval()
                || work.severeConsequence()
                || work.trustOwnership()
                || work.crossSystemCoordination();
    }

    static boolean hasProjectionMetadata(WorkPacket work) {
        return nonBlank(work.projectionGeography())
                && nonBlank(work.projectionHorizon());
    }

    static boolean supportsGrowthClaim(WorkPacket work) {
        return work.projectionDirection() == Direction.GROWTH
                && hasProjectionMetadata(work);
    }

    static boolean supports(WorkPacket work, Claim claim) {
        return switch (claim) {
            case TASK_ASSISTANCE -> work.digitallyBoundedTask();
            case HUMAN_LOOP_RETAINED -> retainsHumanLoop(work);
            case PROJECTED_GROWTH -> supportsGrowthClaim(work);
            case PERMANENT_JOB_ELIMINATION -> false;
        };
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static WorkPacket electrician() {
        return new WorkPacket(
                true, true, true, true, true, true,
                Direction.GROWTH, "United States", "2025-2035");
    }

    private static WorkPacket paramedic() {
        return new WorkPacket(
                true, true, true, true, true, true,
                Direction.UNKNOWN, null, null);
    }

    private static WorkPacket securityAnalyst() {
        return new WorkPacket(
                true, false, true, true, true, true,
                Direction.GROWTH, "United States", "2025-2035");
    }

    private static WorkPacket architectureDecision() {
        return new WorkPacket(
                true, false, true, true, true, true,
                Direction.UNKNOWN, null, null);
    }

    private static int deterministicGroups() {
        int groups = 0;

        WorkPacket electrician = electrician();
        require(supports(electrician, Claim.TASK_ASSISTANCE), "electrician lookup is assistable"); groups++;
        require(retainsHumanLoop(electrician), "electrician execution retains a human loop"); groups++;
        require(supportsGrowthClaim(electrician), "electrician growth claim has metadata"); groups++;
        require(!supports(electrician, Claim.PERMANENT_JOB_ELIMINATION), "growth is not elimination"); groups++;

        WorkPacket paramedic = paramedic();
        require(supports(paramedic, Claim.TASK_ASSISTANCE), "protocol retrieval is assistable"); groups++;
        require(retainsHumanLoop(paramedic), "paramedic workflow retains a human loop"); groups++;
        require(!supportsGrowthClaim(paramedic), "work context is not a growth projection"); groups++;
        require(!supports(paramedic, Claim.PERMANENT_JOB_ELIMINATION), "context is not elimination"); groups++;

        WorkPacket security = securityAnalyst();
        require(supports(security, Claim.TASK_ASSISTANCE), "alert enrichment is assistable"); groups++;
        require(retainsHumanLoop(security), "production authority retains a human loop"); groups++;
        require(supportsGrowthClaim(security), "security growth claim has metadata"); groups++;
        require(!supports(security, Claim.PERMANENT_JOB_ELIMINATION), "AI demand is not immunity or elimination"); groups++;

        WorkPacket architecture = architectureDecision();
        require(supports(architecture, Claim.TASK_ASSISTANCE), "option generation is assistable"); groups++;
        require(retainsHumanLoop(architecture), "architecture ownership retains a human loop"); groups++;
        require(!supportsGrowthClaim(architecture), "role design is not an occupation projection"); groups++;
        require(!supports(architecture, Claim.PERMANENT_JOB_ELIMINATION), "role design is not elimination"); groups++;

        WorkPacket digitalOnly = new WorkPacket(
                true, false, false, false, false, false,
                Direction.UNKNOWN, null, null);
        require(supports(digitalOnly, Claim.TASK_ASSISTANCE), "bounded digital task is assistable"); groups++;
        require(!retainsHumanLoop(digitalOnly), "no resistant feature means no retained-loop evidence"); groups++;

        WorkPacket missingHorizon = new WorkPacket(
                false, true, false, false, false, false,
                Direction.GROWTH, "United States", null);
        require(!supportsGrowthClaim(missingHorizon), "growth requires a horizon"); groups++;

        WorkPacket decline = new WorkPacket(
                false, true, true, true, false, false,
                Direction.DECLINE, "United States", "2025-2035");
        require(retainsHumanLoop(decline) && !supportsGrowthClaim(decline),
                "human loop does not imply employment growth"); groups++;

        require(groups == 20, "expected exactly 20 deterministic groups");
        return groups;
    }

    private static long randomizedChecks() {
        Random random = new Random(SEED);
        long checks = 0;

        for (int trial = 0; trial < TRIALS; trial++) {
            boolean physical = random.nextBoolean();
            boolean approval = random.nextBoolean();
            boolean consequence = random.nextBoolean();
            boolean trust = random.nextBoolean();
            boolean coordination = random.nextBoolean();
            Direction direction = Direction.values()[random.nextInt(Direction.values().length)];
            String geography = random.nextBoolean() ? "United States" : null;
            String horizon = random.nextBoolean() ? "2025-2035" : null;

            WorkPacket work = new WorkPacket(
                    random.nextBoolean(), physical, approval, consequence, trust,
                    coordination, direction, geography, horizon);

            boolean expectedLoop = physical || approval || consequence || trust || coordination;
            require(retainsHumanLoop(work) == expectedLoop, "human-loop disjunction"); checks++;

            require(!supports(work, Claim.PERMANENT_JOB_ELIMINATION),
                    "feature packet cannot support permanent elimination"); checks++;

            boolean expectedGrowth = direction == Direction.GROWTH
                    && nonBlank(geography) && nonBlank(horizon);
            require(supportsGrowthClaim(work) == expectedGrowth,
                    "growth requires direction, geography and horizon"); checks++;

            if (retainsHumanLoop(work) && !expectedGrowth) {
                require(!supports(work, Claim.PROJECTED_GROWTH),
                        "retained responsibility cannot manufacture growth");
            } else {
                require(supports(work, Claim.PROJECTED_GROWTH) == expectedGrowth,
                        "projected-growth result remains exact");
            }
            checks++;
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
