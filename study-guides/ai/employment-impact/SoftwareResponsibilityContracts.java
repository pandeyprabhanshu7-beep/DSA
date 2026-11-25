import java.util.Random;

/**
 * Executable claim boundaries for Section 6 of the AI employment-impact guide.
 *
 * <p>The contract separates bounded coding assistance, retained production
 * responsibility, and a current employment projection. Productivity is never
 * converted directly into a headcount conclusion.</p>
 */
public final class SoftwareResponsibilityContracts {
    private static final int TRIALS = 20_000;
    private static final long SEED = 0x50F7_2026L;

    enum Direction { UNKNOWN, GROWTH, FLAT, DECLINE }

    enum Claim {
        TASK_ASSISTANCE,
        HUMAN_RESPONSIBILITY_RETAINED,
        PROJECTED_GROWTH,
        HEADCOUNT_REDUCTION_FROM_PRODUCTIVITY
    }

    record SoftwarePacket(
            boolean digitallyBoundedTask,
            boolean requirementsOwnership,
            boolean securityApproval,
            boolean productionAuthority,
            boolean incidentOwnership,
            boolean crossSystemDecision,
            boolean measuredProductivityGain,
            Direction projectionDirection,
            String projectionGeography,
            String projectionHorizon,
            String occupationCode) {

        SoftwarePacket {
            if (projectionDirection == null) {
                throw new IllegalArgumentException("projection direction is required");
            }
        }
    }

    static boolean retainsHumanResponsibility(SoftwarePacket packet) {
        return packet.requirementsOwnership()
                || packet.securityApproval()
                || packet.productionAuthority()
                || packet.incidentOwnership()
                || packet.crossSystemDecision();
    }

    static boolean hasProjectionMetadata(SoftwarePacket packet) {
        return nonBlank(packet.projectionGeography())
                && nonBlank(packet.projectionHorizon())
                && nonBlank(packet.occupationCode());
    }

    static boolean supportsGrowthClaim(SoftwarePacket packet) {
        return packet.projectionDirection() == Direction.GROWTH
                && hasProjectionMetadata(packet);
    }

    static boolean supports(SoftwarePacket packet, Claim claim) {
        return switch (claim) {
            case TASK_ASSISTANCE -> packet.digitallyBoundedTask();
            case HUMAN_RESPONSIBILITY_RETAINED -> retainsHumanResponsibility(packet);
            case PROJECTED_GROWTH -> supportsGrowthClaim(packet);
            case HEADCOUNT_REDUCTION_FROM_PRODUCTIVITY -> false;
        };
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static SoftwarePacket ticketToPullRequest() {
        return new SoftwarePacket(
                true, false, true, false, false, false, true,
                Direction.UNKNOWN, null, null, null);
    }

    private static SoftwarePacket productionIncident() {
        return new SoftwarePacket(
                true, true, true, true, true, true, true,
                Direction.UNKNOWN, null, null, null);
    }

    private static SoftwarePacket architectureDecision() {
        return new SoftwarePacket(
                true, true, true, true, true, true, false,
                Direction.UNKNOWN, null, null, null);
    }

    private static SoftwarePacket aiIntegration() {
        return new SoftwarePacket(
                true, true, true, true, true, true, true,
                Direction.GROWTH, "United States", "2025-2035", "15-1252");
    }

    private static int deterministicGroups() {
        int groups = 0;

        SoftwarePacket ticket = ticketToPullRequest();
        require(supports(ticket, Claim.TASK_ASSISTANCE), "ticket implementation is assistable"); groups++;
        require(ticket.measuredProductivityGain(), "ticket example can carry a measured gain"); groups++;
        require(!supports(ticket, Claim.HEADCOUNT_REDUCTION_FROM_PRODUCTIVITY),
                "productivity does not determine headcount"); groups++;
        require(!supportsGrowthClaim(ticket), "task evidence is not a projection"); groups++;

        SoftwarePacket incident = productionIncident();
        require(supports(incident, Claim.TASK_ASSISTANCE), "incident diagnosis is assistable"); groups++;
        require(retainsHumanResponsibility(incident), "incident authority retains responsibility"); groups++;
        require(!supportsGrowthClaim(incident), "incident ownership is not growth evidence"); groups++;
        require(!supports(incident, Claim.HEADCOUNT_REDUCTION_FROM_PRODUCTIVITY),
                "incident assistance is not headcount evidence"); groups++;

        SoftwarePacket architecture = architectureDecision();
        require(supports(architecture, Claim.TASK_ASSISTANCE), "option generation is assistable"); groups++;
        require(retainsHumanResponsibility(architecture), "architecture decision retains responsibility"); groups++;
        require(!supportsGrowthClaim(architecture), "role design is not an occupation projection"); groups++;
        require(!supports(architecture, Claim.HEADCOUNT_REDUCTION_FROM_PRODUCTIVITY),
                "architecture assistance is not headcount evidence"); groups++;

        SoftwarePacket integration = aiIntegration();
        require(supports(integration, Claim.TASK_ASSISTANCE), "AI integration has assistable tasks"); groups++;
        require(retainsHumanResponsibility(integration), "AI integration retains production responsibility"); groups++;
        require(supportsGrowthClaim(integration), "growth claim has occupation, geography and horizon"); groups++;
        require(!supports(integration, Claim.HEADCOUNT_REDUCTION_FROM_PRODUCTIVITY),
                "projection and productivity remain separate"); groups++;

        SoftwarePacket missingOccupation = new SoftwarePacket(
                true, false, false, false, false, false, true,
                Direction.GROWTH, "United States", "2025-2035", null);
        require(!supportsGrowthClaim(missingOccupation), "growth requires an occupation code"); groups++;

        SoftwarePacket missingHorizon = new SoftwarePacket(
                true, true, false, false, false, false, false,
                Direction.GROWTH, "United States", null, "15-1252");
        require(!supportsGrowthClaim(missingHorizon), "growth requires a horizon"); groups++;

        SoftwarePacket decline = new SoftwarePacket(
                false, true, true, true, true, true, false,
                Direction.DECLINE, "United States", "2025-2035", "15-1252");
        require(retainsHumanResponsibility(decline) && !supportsGrowthClaim(decline),
                "retained responsibility does not imply growth"); groups++;

        require(!supports(new SoftwarePacket(
                        false, false, false, false, false, false, false,
                        Direction.UNKNOWN, null, null, null), Claim.TASK_ASSISTANCE),
                "unbounded evidence cannot establish task assistance"); groups++;

        require(groups == 20, "expected exactly 20 deterministic groups");
        return groups;
    }

    private static long randomizedChecks() {
        Random random = new Random(SEED);
        long checks = 0;

        for (int trial = 0; trial < TRIALS; trial++) {
            boolean requirements = random.nextBoolean();
            boolean security = random.nextBoolean();
            boolean production = random.nextBoolean();
            boolean incident = random.nextBoolean();
            boolean crossSystem = random.nextBoolean();
            Direction direction = Direction.values()[random.nextInt(Direction.values().length)];
            String geography = random.nextBoolean() ? "United States" : null;
            String horizon = random.nextBoolean() ? "2025-2035" : null;
            String occupation = random.nextBoolean() ? "15-1252" : null;

            SoftwarePacket packet = new SoftwarePacket(
                    random.nextBoolean(), requirements, security, production, incident,
                    crossSystem, random.nextBoolean(), direction, geography, horizon, occupation);

            boolean expectedResponsibility = requirements || security || production || incident || crossSystem;
            require(retainsHumanResponsibility(packet) == expectedResponsibility,
                    "responsibility is an exact disjunction"); checks++;

            require(!supports(packet, Claim.HEADCOUNT_REDUCTION_FROM_PRODUCTIVITY),
                    "productivity never determines headcount"); checks++;

            boolean expectedGrowth = direction == Direction.GROWTH
                    && nonBlank(geography) && nonBlank(horizon) && nonBlank(occupation);
            require(supportsGrowthClaim(packet) == expectedGrowth,
                    "growth requires direction and exact metadata"); checks++;

            require(supports(packet, Claim.TASK_ASSISTANCE) == packet.digitallyBoundedTask(),
                    "task assistance remains bounded to task evidence"); checks++;
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
