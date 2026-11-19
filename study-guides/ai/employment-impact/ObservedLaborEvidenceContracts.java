import java.util.Random;

/** Scale, denominator, and stock-flow contracts for observed AI labor evidence. */
public class ObservedLaborEvidenceContracts {
    enum Scale { ECONOMY_WIDE, SUBGROUP, REGIONAL_FIRMS }

    enum Outcome {
        EMPLOYMENT_LEVEL,
        RELATIVE_EMPLOYMENT_CHANGE,
        LAYOFF_RESPONSE,
        REDUCED_HIRING_RESPONSE,
        EXPECTED_LAYOFF_RESPONSE
    }

    enum Unit { WORKERS, RELATIVE_PERCENT, SHARE_OF_AI_USING_FIRMS }

    enum Design { DESCRIPTIVE, CONTROLLED_COMPARISON, FIRM_SURVEY }

    record Observation(
            String source,
            Scale scale,
            Outcome outcome,
            Unit unit,
            Design design,
            String population,
            String period,
            double value) {
        Observation {
            require(source != null && !source.isBlank(), "source is required");
            require(scale != null && outcome != null && unit != null && design != null,
                    "typed evidence fields are required");
            require(population != null && !population.isBlank(), "population is required");
            require(period != null && !period.isBlank(), "period is required");
            require(Double.isFinite(value) && value >= 0.0, "value must be finite and non-negative");
            if (unit == Unit.RELATIVE_PERCENT || unit == Unit.SHARE_OF_AI_USING_FIRMS) {
                require(value <= 100.0, "percentage value must not exceed 100");
            }
        }
    }

    static boolean canSupportEconomyWideClaim(Observation observation) {
        return observation.scale() == Scale.ECONOMY_WIDE
                && observation.outcome() == Outcome.EMPLOYMENT_LEVEL
                && observation.unit() == Unit.WORKERS;
    }

    static boolean comparable(Observation left, Observation right) {
        return left.scale() == right.scale()
                && left.outcome() == right.outcome()
                && left.unit() == right.unit()
                && left.design() == right.design()
                && left.population().equals(right.population())
                && left.period().equals(right.period());
    }

    static double afterRelativeDecline(double baseline, double percent) {
        require(Double.isFinite(baseline) && baseline >= 0.0, "baseline must be non-negative");
        require(Double.isFinite(percent) && percent >= 0.0 && percent <= 100.0,
                "percent must be in [0,100]");
        return baseline * (1.0 - percent / 100.0);
    }

    static double firmShare(long reportingFirms, long aiUsingFirms) {
        require(reportingFirms >= 0, "reporting firms must be non-negative");
        require(aiUsingFirms > 0, "AI-using firms must be positive");
        require(reportingFirms <= aiUsingFirms, "reporting firms cannot exceed denominator");
        return 100.0 * reportingFirms / aiUsingFirms;
    }

    static long endingEmployment(long start, long hires, long separations) {
        require(start >= 0 && hires >= 0 && separations >= 0, "flows must be non-negative");
        long end = Math.subtractExact(Math.addExact(start, hires), separations);
        require(end >= 0, "separations cannot exceed available employment");
        return end;
    }

    private static int groups;

    private static void deterministicGroups() {
        Observation yale = observation("Yale Budget Lab", Scale.ECONOMY_WIDE,
                Outcome.EMPLOYMENT_LEVEL, Unit.WORKERS, Design.DESCRIPTIVE,
                "United States labor market", "Nov 2022-Jul 2025", 0);
        group(canSupportEconomyWideClaim(yale), "broad observation stays broad");
        group(yale.scale() == Scale.ECONOMY_WIDE, "Yale scale retained");
        group(yale.design() == Design.DESCRIPTIVE, "Yale design retained");
        group(yale.value() == 0.0, "no discernible break encoded without a layoff count");
        group(yale.outcome() != Outcome.LAYOFF_RESPONSE, "aggregate result is not a firm response");

        Observation stanford = observation("Stanford SIEPR", Scale.SUBGROUP,
                Outcome.RELATIVE_EMPLOYMENT_CHANGE, Unit.RELATIVE_PERCENT,
                Design.CONTROLLED_COMPARISON,
                "ages 22-25 in most AI-exposed occupations", "post-GenAI study window", 13);
        group(!canSupportEconomyWideClaim(stanford), "subgroup cannot prove economy-wide shock");
        group(close(afterRelativeDecline(100, stanford.value()), 87), "relative index arithmetic");
        group(close(afterRelativeDecline(40, stanford.value()), 34.8), "relative baseline arithmetic");
        group(stanford.unit() == Unit.RELATIVE_PERCENT, "relative unit retained");
        group(stanford.design() == Design.CONTROLLED_COMPARISON, "controlled design retained");

        Observation layoffs = observation("New York Fed", Scale.REGIONAL_FIRMS,
                Outcome.LAYOFF_RESPONSE, Unit.SHARE_OF_AI_USING_FIRMS, Design.FIRM_SURVEY,
                "AI-using service firms in New York and Northern New Jersey", "prior six months, Aug 2025", 1);
        Observation fewerHires = observation("New York Fed", Scale.REGIONAL_FIRMS,
                Outcome.REDUCED_HIRING_RESPONSE, Unit.SHARE_OF_AI_USING_FIRMS, Design.FIRM_SURVEY,
                "AI-using service firms in New York and Northern New Jersey", "prior six months, Aug 2025", 12);
        Observation expectedLayoffs = observation("New York Fed", Scale.REGIONAL_FIRMS,
                Outcome.EXPECTED_LAYOFF_RESPONSE, Unit.SHARE_OF_AI_USING_FIRMS, Design.FIRM_SURVEY,
                "AI-using service firms in New York and Northern New Jersey", "next six months, Aug 2025", 13);
        group(!canSupportEconomyWideClaim(layoffs), "regional firm share is not economy-wide");
        group(!comparable(layoffs, fewerHires), "layoffs and fewer hires are different outcomes");
        group(!comparable(layoffs, expectedLayoffs), "realized and expected responses differ");
        group(fewerHires.value() > layoffs.value(), "reduced hiring exceeded reported layoffs");
        group(layoffs.unit() == Unit.SHARE_OF_AI_USING_FIRMS, "firm denominator retained");

        group(endingEmployment(100, 20, 18) == 102, "normal hiring flow");
        group(endingEmployment(100, 10, 18) == 92, "reduced hiring flow");
        group(close(firmShare(1, 100), 1), "one-percent firm share");
        group(close(firmShare(12, 100), 12), "twelve-percent firm share");
        group(!comparable(yale, stanford), "aggregate and subgroup observations remain distinct");
    }

    private static Observation observation(String source, Scale scale, Outcome outcome, Unit unit,
                                           Design design, String population, String period, double value) {
        return new Observation(source, scale, outcome, unit, design, population, period, value);
    }

    private static void randomizedChecks() {
        Random random = new Random(0x0B5E7EDEL);
        for (int trial = 0; trial < 20_000; trial++) {
            double baseline = random.nextDouble() * 1_000_000.0;
            double decline = random.nextDouble() * 100.0;
            double after = afterRelativeDecline(baseline, decline);
            check(after >= 0.0 && after <= baseline + 1e-9, "relative decline escaped baseline");

            long totalFirms = 1L + random.nextInt(1_000_000);
            long reportingFirms = random.nextLong(totalFirms + 1L);
            double share = firmShare(reportingFirms, totalFirms);
            check(share >= 0.0 && share <= 100.0, "firm share escaped bounds");

            long start = 1L + random.nextInt(1_000_000);
            long hires = random.nextInt(1_000_000);
            long separations = random.nextLong(start + hires + 1L);
            long end = endingEmployment(start, hires, separations);
            check(end + separations == start + hires, "employment stock did not conserve");

            Observation subgroup = observation("trial", Scale.SUBGROUP,
                    Outcome.RELATIVE_EMPLOYMENT_CHANGE, Unit.RELATIVE_PERCENT,
                    Design.CONTROLLED_COMPARISON, "trial subgroup", "trial period", decline);
            check(!canSupportEconomyWideClaim(subgroup), "subgroup generalized to economy");
        }
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) < 1e-9;
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
