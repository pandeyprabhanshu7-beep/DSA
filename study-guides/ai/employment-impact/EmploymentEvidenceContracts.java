import java.util.Random;

/** Evidence-category contracts for AI employment claims. */
public class EmploymentEvidenceContracts {
    enum Metric {
        EXPOSURE,
        TASK_AUTOMATION,
        GROSS_DISPLACEMENT,
        JOB_CREATION,
        NET_CHANGE,
        PERMANENT_JOB_ELIMINATION
    }

    enum Unit { JOBS, WORKERS, FTE_EQUIVALENTS, WORK_HOURS, EMPLOYERS }

    record Claim(
            String source,
            Metric metric,
            Unit unit,
            double value,
            int horizonYear,
            boolean aiOnly,
            String population) {
        Claim {
            require(source != null && !source.isBlank(), "source is required");
            require(metric != null && unit != null, "metric and unit are required");
            require(Double.isFinite(value) && value >= 0.0, "value must be finite and non-negative");
            require(horizonYear >= 2020, "horizon year is required");
            require(population != null && !population.isBlank(), "population is required");
        }
    }

    static long netJobs(long created, long displaced) {
        require(created >= 0 && displaced >= 0, "job counts must be non-negative");
        return Math.subtractExact(created, displaced);
    }

    static boolean supportsPermanentElimination(Claim claim) {
        return claim.metric() == Metric.PERMANENT_JOB_ELIMINATION
                && claim.unit() == Unit.JOBS;
    }

    static boolean comparable(Claim left, Claim right) {
        return left.metric() == right.metric()
                && left.unit() == right.unit()
                && left.horizonYear() == right.horizonYear()
                && left.aiOnly() == right.aiOnly()
                && left.population().equals(right.population());
    }

    static double percent(double numerator, double denominator) {
        require(Double.isFinite(numerator) && numerator >= 0.0, "numerator must be non-negative");
        require(Double.isFinite(denominator) && denominator > 0.0, "denominator must be positive");
        double result = 100.0 * numerator / denominator;
        require(result <= 100.0 + 1e-12, "part cannot exceed whole");
        return result;
    }

    private static int groups;

    private static void deterministicGroups() {
        Claim wefDisplaced = claim("WEF", Metric.GROSS_DISPLACEMENT, Unit.JOBS, 92_000_000, 2030, false,
                "global structural labour market");
        Claim wefCreated = claim("WEF", Metric.JOB_CREATION, Unit.JOBS, 170_000_000, 2030, false,
                "global structural labour market");
        group(netJobs(170_000_000, 92_000_000) == 78_000_000, "WEF net ledger");
        group(!wefDisplaced.aiOnly(), "WEF has multiple drivers");
        group(!supportsPermanentElimination(wefDisplaced), "displacement is not permanent elimination");
        group(wefCreated.metric() == Metric.JOB_CREATION, "creation category retained");
        group(!comparable(wefCreated, wefDisplaced), "creation and displacement are different metrics");

        Claim imfGlobal = claim("IMF", Metric.EXPOSURE, Unit.WORKERS, 40, 2024, true, "global employment percent");
        Claim imfAdvanced = claim("IMF", Metric.EXPOSURE, Unit.WORKERS, 60, 2024, true,
                "advanced-economy employment percent");
        group(!supportsPermanentElimination(imfGlobal), "exposure is not elimination");
        group(!comparable(imfGlobal, imfAdvanced), "different populations are not directly comparable");
        group(percent(40, 100) == 40.0, "global exposure percentage");
        group(percent(60, 100) == 60.0, "advanced exposure percentage");

        Claim iloSome = claim("ILO", Metric.EXPOSURE, Unit.WORKERS, 25, 2025, true, "global employment percent");
        Claim iloHighest = claim("ILO", Metric.EXPOSURE, Unit.WORKERS, 3.3, 2025, true,
                "global employment highest-gradient percent");
        group(!supportsPermanentElimination(iloSome), "ILO exposure is not elimination");
        group(iloHighest.value() < iloSome.value(), "highest gradient is a subset");
        group(!comparable(iloSome, iloHighest), "different population definitions remain distinct");
        group(percent(3.3, 100) == 3.3, "highest-gradient percentage");

        Claim goldman = claim("Goldman Sachs", Metric.EXPOSURE, Unit.FTE_EQUIVALENTS, 300_000_000, 2036,
                true, "global jobs exposed over adoption transition");
        group(!supportsPermanentElimination(goldman), "FTE exposure is not job destruction");
        group(goldman.unit() == Unit.FTE_EQUIVALENTS, "Goldman unit retained");
        group(goldman.metric() == Metric.EXPOSURE, "Goldman metric retained");
        group(goldman.horizonYear() == 2036, "transition horizon retained");

        Claim mckinsey = claim("McKinsey", Metric.TASK_AUTOMATION, Unit.WORK_HOURS, 30, 2030, false,
                "current US work hours percent");
        group(!supportsPermanentElimination(mckinsey), "hours automated are not jobs eliminated");
        group(mckinsey.unit() == Unit.WORK_HOURS, "work-hour unit retained");
        group(!comparable(mckinsey, wefDisplaced), "hours and jobs cannot be subtracted");
    }

    private static Claim claim(String source, Metric metric, Unit unit, double value, int horizon,
                               boolean aiOnly, String population) {
        return new Claim(source, metric, unit, value, horizon, aiOnly, population);
    }

    private static void randomizedChecks() {
        Random random = new Random(0xE71D3ACEL);
        for (int trial = 0; trial < 20_000; trial++) {
            long created = random.nextInt(1_000_000_001);
            long displaced = random.nextInt(1_000_000_001);
            long net = netJobs(created, displaced);
            check(net + displaced == created, "net ledger mismatch");

            double whole = 1.0 + random.nextDouble() * 1_000_000.0;
            double part = random.nextDouble() * whole;
            double share = percent(part, whole);
            check(share >= 0.0 && share <= 100.0, "percentage escaped bounds");

            Claim exposure = claim("trial", Metric.EXPOSURE, Unit.WORKERS, share, 2030, true, "sample");
            check(!supportsPermanentElimination(exposure), "exposure inferred elimination");

            Claim same = claim("other", Metric.EXPOSURE, Unit.WORKERS, 100.0 - share, 2030, true, "sample");
            check(comparable(exposure, same) == comparable(same, exposure), "comparability not symmetric");
        }
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
        System.out.println("PASS groups=" + groups + " trials=20000 evidence checks=80000");
    }
}
