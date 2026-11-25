import java.util.Random;

public final class OccupationalPressureContracts {
    private static int groups;

    enum DriverScope {
        AI_EXPLICIT,
        AUTOMATION_GENERAL,
        NO_TECHNOLOGY_ATTRIBUTION
    }

    enum Claim {
        PROJECTED_NET_DECLINE,
        AI_CONTRIBUTES_TO_PRESSURE,
        PERMANENT_JOB_ELIMINATION
    }

    record Projection(
            String occupation,
            long employment2025,
            long employment2035,
            long publishedChange,
            double publishedPercent,
            Long annualOpenings,
            DriverScope driverScope) {
        Projection {
            if (occupation == null || occupation.isBlank()) throw new IllegalArgumentException("occupation");
            if (employment2025 < 0 || employment2035 < 0
                    || (annualOpenings != null && annualOpenings < 0)) {
                throw new IllegalArgumentException("counts must be nonnegative");
            }
            if (!Double.isFinite(publishedPercent)) throw new IllegalArgumentException("percent");
            if (driverScope == null) throw new IllegalArgumentException("driverScope");
        }

        long derivedChange() {
            return Math.subtractExact(employment2035, employment2025);
        }

        double derivedPercent() {
            if (employment2025 == 0) throw new IllegalStateException("undefined percentage from zero base");
            return 100.0 * derivedChange() / employment2025;
        }

        boolean publishedCountsAreRoundingConsistent(long toleranceWorkers) {
            if (toleranceWorkers < 0) throw new IllegalArgumentException("tolerance");
            return Math.abs(Math.subtractExact(derivedChange(), publishedChange)) <= toleranceWorkers;
        }
    }

    static boolean supports(Projection evidence, Claim claim) {
        return switch (claim) {
            case PROJECTED_NET_DECLINE -> evidence.publishedChange() < 0;
            case AI_CONTRIBUTES_TO_PRESSURE -> evidence.driverScope() == DriverScope.AI_EXPLICIT;
            case PERMANENT_JOB_ELIMINATION -> false;
        };
    }

    static boolean declineAndOpeningsCanCoexist(Projection evidence) {
        return evidence.publishedChange() < 0
                && evidence.annualOpenings() != null
                && evidence.annualOpenings() > 0;
    }

    static long decadeGrossOpenings(Projection evidence) {
        if (evidence.annualOpenings() == null) throw new IllegalStateException("openings not supplied");
        return Math.multiplyExact(evidence.annualOpenings(), 10);
    }

    static String largerDeclineByAbsoluteCount(Projection left, Projection right) {
        long leftLoss = Math.max(0, Math.negateExact(left.publishedChange()));
        long rightLoss = Math.max(0, Math.negateExact(right.publishedChange()));
        return leftLoss >= rightLoss ? left.occupation() : right.occupation();
    }

    static String largerDeclineByRate(Projection left, Projection right) {
        return left.publishedPercent() <= right.publishedPercent()
                ? left.occupation() : right.occupation();
    }

    private static Projection customerService() {
        return new Projection("customer service representatives", 2_666_000, 2_524_100,
                -141_800, -5.3, 289_500L, DriverScope.AI_EXPLICIT);
    }

    private static Projection bookkeeping() {
        return new Projection("bookkeeping clerks", 1_532_400, 1_446_800,
                -85_600, -5.6, 144_100L, DriverScope.AUTOMATION_GENERAL);
    }

    private static Projection dataEntry() {
        return new Projection("data entry keyers", 131_800, 98_200,
                -33_600, -25.5, null, DriverScope.AI_EXPLICIT);
    }

    private static Projection claimsProcessing() {
        return new Projection("insurance claims and policy processing clerks", 239_100, 234_700,
                -4_400, -1.9, 17_500L, DriverScope.AI_EXPLICIT);
    }

    private static Projection paralegals() {
        return new Projection("paralegals and legal assistants", 404_900, 403_800,
                -1_100, 0.0, 40_800L, DriverScope.AI_EXPLICIT);
    }

    private static Projection translators() {
        return new Projection("interpreters and translators", 73_900, 75_400,
                1_500, 2.0, 6_000L, DriverScope.AI_EXPLICIT);
    }

    private static void group(boolean condition, String message) {
        groups++;
        if (!condition) throw new AssertionError(message);
    }

    private static void deterministicGroups() {
        Projection customer = customerService();
        group(supports(customer, Claim.PROJECTED_NET_DECLINE), "customer-service decline");
        group(customer.publishedCountsAreRoundingConsistent(100), "rounded BLS counts remain consistent");
        group(declineAndOpeningsCanCoexist(customer), "replacement openings coexist with contraction");
        group(supports(customer, Claim.AI_CONTRIBUTES_TO_PRESSURE), "BLS explicitly names AI");
        group(!supports(customer, Claim.PERMANENT_JOB_ELIMINATION), "projection is not permanent elimination");

        Projection books = bookkeeping();
        group(books.derivedChange() == -85_600, "bookkeeping absolute change");
        group(!supports(books, Claim.AI_CONTRIBUTES_TO_PRESSURE), "general automation is not AI-only evidence");
        group(decadeGrossOpenings(books) == 1_441_000, "openings are a gross flow over time");

        Projection entry = dataEntry();
        group(largerDeclineByAbsoluteCount(customer, entry).equals(customer.occupation()),
                "absolute loss ranking retains employment scale");
        group(largerDeclineByRate(customer, entry).equals(entry.occupation()),
                "rate ranking answers a different question");
        group(entry.derivedChange() == -33_600, "data-entry change");
        group(supports(entry, Claim.AI_CONTRIBUTES_TO_PRESSURE), "data-entry factor explicitly names AI");

        Projection claims = claimsProcessing();
        group(claims.derivedChange() == -4_400, "claims-processing change");
        group(supports(claims, Claim.AI_CONTRIBUTES_TO_PRESSURE), "claims factor explicitly names AI");
        group(declineAndOpeningsCanCoexist(claims), "claims replacement flow");

        Projection legal = paralegals();
        group(legal.publishedPercent() == 0.0 && legal.publishedChange() < 0,
                "rounded zero percent can coexist with a small numeric decline");
        group(decadeGrossOpenings(legal) == 408_000, "paralegal openings are not net growth");

        Projection language = translators();
        group(!supports(language, Claim.PROJECTED_NET_DECLINE), "AI-exposed translation still projects growth");
        group(supports(language, Claim.AI_CONTRIBUTES_TO_PRESSURE), "AI can change tasks without net decline");
        group(!supports(language, Claim.PERMANENT_JOB_ELIMINATION), "task change is not occupation elimination");
    }

    private static void randomizedChecks() {
        Random random = new Random(0xB1A52035L);
        long checks = 0;
        for (int trial = 0; trial < 20_000; trial++) {
            long start = random.nextLong(1, 10_000_001);
            long change = random.nextLong(-start, 1_000_001);
            long target = Math.addExact(start, change);
            long roundingOffset = random.nextLong(-99, 100);
            long reported = Math.addExact(change, roundingOffset);
            double percent = 100.0 * change / start;
            long openings = random.nextLong(0, 500_001);
            DriverScope driver = DriverScope.values()[random.nextInt(DriverScope.values().length)];
            Projection evidence = new Projection("generated occupation", start, target, reported,
                    percent, openings, driver);

            if (!evidence.publishedCountsAreRoundingConsistent(99)) {
                throw new AssertionError("rounding tolerance mismatch");
            }
            checks++;
            if (Double.compare(evidence.derivedPercent(), percent) != 0) {
                throw new AssertionError("percentage mismatch");
            }
            checks++;
            boolean coexist = reported < 0 && openings > 0;
            if (declineAndOpeningsCanCoexist(evidence) != coexist) {
                throw new AssertionError("stock/flow coexistence mismatch");
            }
            checks++;
            if (supports(evidence, Claim.PERMANENT_JOB_ELIMINATION)) {
                throw new AssertionError("projection promoted to permanent elimination");
            }
            checks++;
        }
        if (checks != 80_000) throw new AssertionError("unexpected check count " + checks);
        System.out.println("PASS groups=" + groups + " trials=20000 checks=" + checks);
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedChecks();
    }
}
