import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Split Array Largest Sum. */
public final class SplitArrayLargestSumContracts {
    private record SearchResult(long limit, int feasibilityChecks) {}

    private static int groups;
    private static long checks;

    static long splitArray(int[] values, int requestedGroups) {
        return searchWithEvidence(values, requestedGroups).limit();
    }

    private static SearchResult searchWithEvidence(int[] values, int requestedGroups) {
        validate(values, requestedGroups);
        long low = 0;
        long high = 0;
        for (int value : values) {
            low = Math.max(low, value);
            high += value;
        }

        int feasibilityChecks = 0;
        while (low < high) {
            long mid = low + ((high - low) >>> 1);
            feasibilityChecks++;
            if (fitsInAtMost(values, requestedGroups, mid)) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return new SearchResult(low, feasibilityChecks);
    }

    static boolean fitsInAtMost(int[] values, int maximumGroups, long limit) {
        if (values == null || values.length == 0 || maximumGroups <= 0 || limit < 0) {
            throw new IllegalArgumentException("invalid feasibility inputs");
        }
        int groupsUsed = 1;
        long current = 0;
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("values must be non-negative");
            }
            if (value > limit) {
                return false;
            }
            if (current > limit - value) {
                if (++groupsUsed > maximumGroups) {
                    return false;
                }
                current = value;
            } else {
                current += value;
            }
        }
        return true;
    }

    private static void validate(int[] values, int requestedGroups) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must be non-null and non-empty");
        }
        if (requestedGroups <= 0 || requestedGroups > values.length) {
            throw new IllegalArgumentException("require 1 <= groups <= values.length");
        }
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("values must be non-negative");
            }
        }
    }

    private static long exactPartitionOracle(int[] values, int requestedGroups) {
        int length = values.length;
        long[] prefix = new long[length + 1];
        for (int index = 0; index < length; index++) {
            prefix[index + 1] = prefix[index] + values[index];
        }

        long infinity = Long.MAX_VALUE;
        long[][] best = new long[requestedGroups + 1][length + 1];
        for (long[] row : best) {
            Arrays.fill(row, infinity);
        }
        best[0][0] = 0;
        for (int groupsUsed = 1; groupsUsed <= requestedGroups; groupsUsed++) {
            for (int end = groupsUsed; end <= length; end++) {
                for (int cut = groupsUsed - 1; cut < end; cut++) {
                    if (best[groupsUsed - 1][cut] == infinity) {
                        continue;
                    }
                    long finalGroup = prefix[end] - prefix[cut];
                    long candidate = Math.max(best[groupsUsed - 1][cut], finalGroup);
                    best[groupsUsed][end] = Math.min(best[groupsUsed][end], candidate);
                }
            }
        }
        return best[requestedGroups][length];
    }

    private static int logarithmicBound(int[] values) {
        long maximum = 0;
        long total = 0;
        for (int value : values) {
            maximum = Math.max(maximum, value);
            total += value;
        }
        long candidates = total - maximum + 1;
        int bound = 0;
        while (candidates > 1) {
            candidates = (candidates + 1) >>> 1;
            bound++;
        }
        return bound;
    }

    private static void group(String name, boolean condition) {
        groups++;
        checks++;
        if (!condition) {
            throw new AssertionError("group failed: " + name);
        }
    }

    private static void expectIllegalArgument(String name, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        group(name, rejected);
    }

    private static void deterministicEvidence() {
        group("canonical", splitArray(new int[] {7, 2, 5, 10, 8}, 2) == 18);
        group("official increasing", splitArray(new int[] {1, 2, 3, 4, 5}, 2) == 9);
        group("one group", splitArray(new int[] {1, 2, 3, 4, 5}, 1) == 15);
        group("one per value", splitArray(new int[] {1, 4, 4}, 3) == 4);
        group("zero values exact groups", splitArray(new int[] {0, 0, 0, 0}, 3) == 0);
        group("repeated values", splitArray(new int[] {5, 5, 5, 5}, 3) == 10);
        group("full width one group", splitArray(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 1) == 4_294_967_294L);
        group("full width two groups", splitArray(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 2) == Integer.MAX_VALUE);
        group("canonical predecessor infeasible", !fitsInAtMost(new int[] {7, 2, 5, 10, 8}, 2, 17));
        group("canonical optimum feasible", fitsInAtMost(new int[] {7, 2, 5, 10, 8}, 2, 18));
        group("monotonic larger limit", fitsInAtMost(new int[] {7, 2, 5, 10, 8}, 2, 19));
        group("below maximum rejected by predicate", !fitsInAtMost(new int[] {1, 4, 4}, 3, 3));

        int[] preserved = {7, 2, 5, 10, 8};
        int[] snapshot = preserved.clone();
        splitArray(preserved, 2);
        group("input preserved", Arrays.equals(preserved, snapshot));

        expectIllegalArgument("null rejected", () -> splitArray(null, 1));
        expectIllegalArgument("empty rejected", () -> splitArray(new int[0], 1));
        expectIllegalArgument("zero groups rejected", () -> splitArray(new int[] {1}, 0));
        expectIllegalArgument("too many groups rejected", () -> splitArray(new int[] {1, 2}, 3));
        expectIllegalArgument("negative value rejected", () -> splitArray(new int[] {1, -1, 2}, 2));
        expectIllegalArgument("negative limit rejected", () -> fitsInAtMost(new int[] {0}, 1, -1));

        SearchResult evidence = searchWithEvidence(new int[] {7, 2, 5, 10, 8}, 2);
        group("search bound", evidence.feasibilityChecks() <= logarithmicBound(new int[] {7, 2, 5, 10, 8}));
    }

    private static void randomizedEvidence() {
        Random random = new Random(20261104L);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = 1 + random.nextInt(9);
            int[] values = new int[length];
            for (int index = 0; index < length; index++) {
                values[index] = random.nextInt(26);
            }
            int requestedGroups = 1 + random.nextInt(length);
            int[] snapshot = values.clone();
            SearchResult actual = searchWithEvidence(values, requestedGroups);
            long expected = exactPartitionOracle(values, requestedGroups);

            checks += 5;
            if (actual.limit() != expected) {
                throw new AssertionError("oracle mismatch trial=" + trial + " actual=" + actual.limit() + " expected=" + expected);
            }
            if (!fitsInAtMost(values, requestedGroups, actual.limit())) {
                throw new AssertionError("reported limit infeasible trial=" + trial);
            }
            if (actual.limit() > 0 && fitsInAtMost(values, requestedGroups, actual.limit() - 1)) {
                throw new AssertionError("reported limit not minimal trial=" + trial);
            }
            if (!Arrays.equals(values, snapshot)) {
                throw new AssertionError("input mutated trial=" + trial);
            }
            if (actual.feasibilityChecks() > logarithmicBound(values)) {
                throw new AssertionError("binary-search bound trial=" + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|CANONICAL=18|INCREASING=9|K1=15|KN=4|ZEROS=0|WIDE=4294967294");
        System.out.println("SplitArrayLargestSumContracts: 20 scenario groups + 20000 seeded exact-DP comparisons passed; checks=" + checks);
    }
}
