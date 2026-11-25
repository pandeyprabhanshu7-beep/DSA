import java.util.Random;

/** Executable contracts for the guide's bottom-line claim discipline. */
public final class BottomLineContracts {
    enum ClaimKind { OBSERVED, ASSOCIATION, FORECAST, CONDITIONAL_SCENARIO, ADVICE }

    record ClaimPacket(
            ClaimKind kind,
            String metric,
            String unit,
            String population,
            String horizon,
            boolean conditionsNamed,
            boolean matchedEvidence,
            boolean distributionReported,
            boolean revisionTriggerNamed) {}

    static boolean supportsDefensibleClaim(ClaimPacket packet) {
        if (packet == null
                || packet.kind() == null
                || blank(packet.metric())
                || blank(packet.unit())
                || blank(packet.population())
                || blank(packet.horizon())
                || !packet.matchedEvidence()) {
            return false;
        }
        if (packet.kind() == ClaimKind.CONDITIONAL_SCENARIO) {
            return packet.conditionsNamed() && packet.revisionTriggerNamed();
        }
        return packet.kind() != ClaimKind.FORECAST || packet.revisionTriggerNamed();
    }

    static long netJobs(long created, long displaced) {
        if (created < 0 || displaced < 0) {
            throw new IllegalArgumentException("gross flows must be nonnegative");
        }
        return Math.subtractExact(created, displaced);
    }

    static long unmatchedTransition(long displaced, long directlyMatched) {
        if (displaced < 0 || directlyMatched < 0 || directlyMatched > displaced) {
            throw new IllegalArgumentException("match must be within displaced flow");
        }
        return displaced - directlyMatched;
    }

    static int responsibilityScore(
            boolean outcomeOwnership,
            boolean judgment,
            boolean relationships,
            boolean physicalExecution,
            boolean accountability) {
        int score = 0;
        if (outcomeOwnership) score++;
        if (judgment) score++;
        if (relationships) score++;
        if (physicalExecution) score++;
        if (accountability) score++;
        return score;
    }

    static String roleDesignPriority(double exposedTaskShare, int responsibilityScore) {
        if (!Double.isFinite(exposedTaskShare)
                || exposedTaskShare < 0.0
                || exposedTaskShare > 1.0
                || responsibilityScore < 0
                || responsibilityScore > 5) {
            throw new IllegalArgumentException("invalid role-design inputs");
        }
        if (exposedTaskShare >= 0.60 && responsibilityScore <= 1) return "REDESIGN_NOW";
        if (exposedTaskShare >= 0.30 || responsibilityScore <= 2) return "AUGMENT_AND_EXTEND";
        return "MONITOR_AND_LEARN";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public static void main(String[] args) {
        int groups = 0;

        ClaimPacket observed = new ClaimPacket(
                ClaimKind.OBSERVED, "employment change", "workers", "US ages 22-25",
                "2022-2025", false, true, true, false);
        check(supportsDefensibleClaim(observed), "observed packet"); groups++;

        ClaimPacket conditional = new ClaimPacket(
                ClaimKind.CONDITIONAL_SCENARIO, "gross displacement", "jobs", "global",
                "2035", true, true, true, true);
        check(supportsDefensibleClaim(conditional), "conditional packet"); groups++;

        check(!supportsDefensibleClaim(new ClaimPacket(
                ClaimKind.CONDITIONAL_SCENARIO, "gross displacement", "jobs", "global",
                "2035", false, true, true, true)), "condition required"); groups++;

        check(!supportsDefensibleClaim(new ClaimPacket(
                ClaimKind.CONDITIONAL_SCENARIO, "gross displacement", "jobs", "global",
                "2035", true, true, true, false)), "revision trigger required"); groups++;

        check(!supportsDefensibleClaim(new ClaimPacket(
                ClaimKind.FORECAST, "net change", "jobs", "global", "2030",
                true, true, true, false)), "forecast revision trigger"); groups++;

        check(!supportsDefensibleClaim(new ClaimPacket(
                ClaimKind.OBSERVED, "", "workers", "US", "2026",
                false, true, true, false)), "metric required"); groups++;

        check(!supportsDefensibleClaim(new ClaimPacket(
                ClaimKind.OBSERVED, "employment", "workers", "US", "2026",
                false, false, true, false)), "matched evidence required"); groups++;

        check(netJobs(170_000_000L, 92_000_000L) == 78_000_000L, "WEF ledger"); groups++;
        check(netJobs(130, 100) == 30, "illustrative ledger"); groups++;
        check(netJobs(100, 130) == -30, "negative net"); groups++;
        expectIllegalArgument(() -> netJobs(-1, 0), "negative creation"); groups++;
        expectIllegalArgument(() -> netJobs(0, -1), "negative displacement"); groups++;
        check(unmatchedTransition(100, 30) == 70, "transition mismatch"); groups++;
        check(unmatchedTransition(100, 100) == 0, "full direct match"); groups++;
        expectIllegalArgument(() -> unmatchedTransition(10, 11), "overmatch"); groups++;
        check(responsibilityScore(true, true, true, true, true) == 5, "full responsibility"); groups++;
        check(responsibilityScore(false, false, false, false, false) == 0, "no retained axis"); groups++;
        check(roleDesignPriority(0.75, 1).equals("REDESIGN_NOW"), "redesign priority"); groups++;
        check(roleDesignPriority(0.45, 4).equals("AUGMENT_AND_EXTEND"), "augment priority"); groups++;
        expectIllegalArgument(() -> roleDesignPriority(1.01, 3), "invalid exposure"); groups++;

        Random random = new Random(0xB0770A1L);
        int trials = 20_000;
        long checks = 0;
        for (int i = 0; i < trials; i++) {
            long created = random.nextInt(2_000_001);
            long displaced = random.nextInt(2_000_001);
            long net = netJobs(created, displaced);
            check(net + displaced == created, "net identity"); checks++;

            long matched = displaced == 0 ? 0 : random.nextLong(displaced + 1);
            long unmatched = unmatchedTransition(displaced, matched);
            check(unmatched >= 0 && unmatched <= displaced, "transition bounds"); checks++;

            boolean[] axes = new boolean[5];
            int expectedScore = 0;
            for (int j = 0; j < axes.length; j++) {
                axes[j] = random.nextBoolean();
                if (axes[j]) expectedScore++;
            }
            int score = responsibilityScore(axes[0], axes[1], axes[2], axes[3], axes[4]);
            check(score == expectedScore && score >= 0 && score <= 5, "responsibility bounds"); checks++;

            ClaimPacket missingCondition = new ClaimPacket(
                    ClaimKind.CONDITIONAL_SCENARIO, "gross displacement", "jobs", "global",
                    "2035", false, true, random.nextBoolean(), true);
            check(!supportsDefensibleClaim(missingCondition), "conditional scenario guard"); checks++;
        }

        System.out.printf("PASS groups=%d trials=%d checks=%d%n", groups, trials, checks);
    }
}
