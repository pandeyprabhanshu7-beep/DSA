import java.util.Random;

/** Contracts for keeping an authored AI-employment scenario range separate from evidence. */
public class ScenarioRangeContracts {
    enum Metric { EXPOSURE, GROSS_DISPLACEMENT, GROSS_ELIMINATION, NET_CHANGE }
    enum Unit { JOBS, WORKERS, WORK_HOURS }
    enum Population { GLOBAL, UNITED_STATES, SURVEYED_FIRMS }
    enum Provenance { INSTITUTION_ESTIMATE, AUTHOR_SYNTHESIS }

    record Range(
            long minimum,
            long maximum,
            Metric metric,
            Unit unit,
            Population population,
            int horizonYear,
            boolean aiOnly,
            Provenance provenance) {
        Range {
            require(minimum >= 0, "minimum must be non-negative");
            require(maximum >= minimum, "maximum must be at least minimum");
            require(metric != null && unit != null && population != null,
                    "metric, unit and population are required");
            require(horizonYear >= 2020, "horizon is required");
            require(provenance != null, "provenance is required");
        }
    }

    static Range centralPlanningRange() {
        return new Range(
                30_000_000L,
                80_000_000L,
                Metric.GROSS_ELIMINATION,
                Unit.JOBS,
                Population.GLOBAL,
                2030,
                true,
                Provenance.AUTHOR_SYNTHESIS);
    }

    static boolean isPlanningScenario(Range range) {
        return range.provenance() == Provenance.AUTHOR_SYNTHESIS;
    }

    static boolean isInstitutionEstimate(Range range) {
        return range.provenance() == Provenance.INSTITUTION_ESTIMATE;
    }

    static boolean comparable(Range left, Range right) {
        return left.metric() == right.metric()
                && left.unit() == right.unit()
                && left.population() == right.population()
                && left.horizonYear() == right.horizonYear()
                && left.aiOnly() == right.aiOnly()
                && left.provenance() == right.provenance();
    }

    static boolean fitsInside(long outerMinimum, long outerMaximum, Range inner) {
        require(outerMinimum >= 0 && outerMaximum >= outerMinimum, "outer range is invalid");
        return inner.minimum() >= outerMinimum && inner.maximum() <= outerMaximum;
    }

    static double elapsedShare(int startYear, int observationYear, int endYear) {
        require(startYear < endYear, "start must precede end");
        require(observationYear >= startYear && observationYear <= endYear,
                "observation must be inside the interval");
        return (double) (observationYear - startYear) / (endYear - startYear);
    }

    static long linearizedCount(long total, double elapsedShare, boolean linearAssumptionDeclared) {
        require(total >= 0, "total must be non-negative");
        require(Double.isFinite(elapsedShare) && elapsedShare >= 0.0 && elapsedShare <= 1.0,
                "elapsed share must be in [0,1]");
        require(linearAssumptionDeclared, "linear timing is an assumption, not evidence");
        return Math.round(total * elapsedShare);
    }

    static long exposureAsElimination(long exposed, boolean explicitOutcomeModel) {
        require(exposed >= 0, "exposure must be non-negative");
        require(explicitOutcomeModel, "exposure cannot be relabeled as elimination");
        return exposed;
    }

    private static int groups;

    private static void deterministicGroups() {
        Range central = centralPlanningRange();
        group(central.minimum() == 30_000_000L, "central lower bound");
        group(central.maximum() == 80_000_000L, "central upper bound");
        group(central.minimum() < central.maximum(), "central interval is non-degenerate");
        group(central.metric() == Metric.GROSS_ELIMINATION, "gross metric retained");
        group(central.unit() == Unit.JOBS, "job unit retained");
        group(central.population() == Population.GLOBAL, "global population retained");
        group(central.horizonYear() == 2030, "2030 horizon retained");
        group(central.aiOnly(), "AI-only scope retained");
        group(isPlanningScenario(central), "range is author synthesis");
        group(!isInstitutionEstimate(central), "range is not an institution estimate");
        group(fitsInside(0, 92_000_000L, central), "range fits chosen WEF compatibility ceiling");

        Range quotedWef = new Range(92_000_000L, 92_000_000L, Metric.GROSS_DISPLACEMENT,
                Unit.JOBS, Population.GLOBAL, 2030, false, Provenance.INSTITUTION_ESTIMATE);
        group(isInstitutionEstimate(quotedWef), "WEF provenance retained");
        group(!comparable(central, quotedWef), "AI-only elimination and multi-driver displacement differ");
        group(!quotedWef.aiOnly(), "WEF frame remains multi-driver");
        group(quotedWef.metric() != Metric.GROSS_ELIMINATION, "displacement is not elimination");

        double fourOfTenYears = elapsedShare(2026, 2030, 2036);
        group(close(fourOfTenYears, 0.4), "four of ten years elapsed");
        group(linearizedCount(70_000_000L, fourOfTenYears, true) == 28_000_000L,
                "declared linear illustration");
        group(rejects(() -> linearizedCount(70_000_000L, fourOfTenYears, false)),
                "undeclared linear timing rejected");
        group(rejects(() -> exposureAsElimination(300_000_000L, false)),
                "exposure relabeling rejected");
        group(exposureAsElimination(300_000_000L, true) == 300_000_000L,
                "explicit downstream model required");
    }

    private static void randomizedChecks() {
        Random random = new Random(0x5CE0A810L);
        for (int trial = 0; trial < 20_000; trial++) {
            long lower = random.nextLong(100_000_001L);
            long upper = lower + random.nextLong(100_000_001L);
            Range range = new Range(lower, upper, Metric.GROSS_ELIMINATION, Unit.JOBS,
                    Population.GLOBAL, 2030, true, Provenance.AUTHOR_SYNTHESIS);
            check(range.minimum() <= range.maximum(), "range order failed");

            long padding = random.nextLong(100_000_001L);
            check(fitsInside(Math.max(0L, lower - padding), upper + padding, range),
                    "containing interval rejected");

            int start = 2020 + random.nextInt(20);
            int duration = 1 + random.nextInt(20);
            int elapsed = random.nextInt(duration + 1);
            double share = elapsedShare(start, start + elapsed, start + duration);
            check(share >= 0.0 && share <= 1.0, "elapsed share escaped bounds");

            long total = random.nextLong(1_000_000_001L);
            long linearized = linearizedCount(total, share, true);
            check(linearized >= 0L && linearized <= total, "linearized count escaped total");
        }
    }

    private static boolean rejects(Runnable operation) {
        try {
            operation.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) < 1e-12;
    }

    private static void group(boolean condition, String message) {
        check(condition, message);
        groups++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedChecks();
        System.out.println("PASS groups=" + groups + " trials=20000 scenario checks=80000");
    }
}
