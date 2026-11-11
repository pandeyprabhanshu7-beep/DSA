import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Arrays problem 44. */
public final class PivotIndexContracts {
    private static int groups;
    private static long randomizedChecks;

    private PivotIndexContracts() {}

    /** Returns the leftmost balance index, or -1 when none exists. */
    static int pivotIndex(int[] nums) {
        if (nums == null) throw new IllegalArgumentException("nums must be non-null");

        long total = 0;
        for (int value : nums) total += value;

        long left = 0;
        for (int i = 0; i < nums.length; i++) {
            long right = total - left - nums[i];
            if (left == right) return i;
            left += nums[i];
        }
        return -1;
    }

    /** Independent definition oracle: it deliberately re-sums both sides of every index. */
    static int quadraticPivotOracle(int[] nums) {
        if (nums == null) throw new IllegalArgumentException("nums must be non-null");
        for (int pivot = 0; pivot < nums.length; pivot++) {
            long left = 0;
            long right = 0;
            for (int i = 0; i < pivot; i++) left += nums[i];
            for (int i = pivot + 1; i < nums.length; i++) right += nums[i];
            if (left == right) return pivot;
        }
        return -1;
    }

    /** Rightmost definition oracle, used to verify the reversal relation. */
    static int quadraticRightmostPivotOracle(int[] nums) {
        if (nums == null) throw new IllegalArgumentException("nums must be non-null");
        for (int pivot = nums.length - 1; pivot >= 0; pivot--) {
            long left = 0;
            long right = 0;
            for (int i = 0; i < pivot; i++) left += nums[i];
            for (int i = pivot + 1; i < nums.length; i++) right += nums[i];
            if (left == right) return pivot;
        }
        return -1;
    }

    public static void main(String[] args) {
        deterministicScenarios();
        randomizedOracleChecks();
        System.out.println("PivotIndexContracts: " + groups
                + " scenario groups + 20000 seeded quadratic-oracle trials passed; checks="
                + randomizedChecks);
    }

    private static void deterministicScenarios() {
        group("official interior pivot", () -> eq(3,
                pivotIndex(new int[] {1, 7, 3, 6, 5, 6})));
        group("official absent pivot", () -> eq(-1,
                pivotIndex(new int[] {1, 2, 3})));
        group("official left edge pivot", () -> eq(0,
                pivotIndex(new int[] {2, 1, -1})));
        group("right edge pivot", () -> eq(2,
                pivotIndex(new int[] {1, -1, 0})));
        group("singleton", () -> eq(0, pivotIndex(new int[] {42})));
        group("empty reusable extension", () -> eq(-1, pivotIndex(new int[0])));
        group("all zeros chooses leftmost", () -> eq(0,
                pivotIndex(new int[] {0, 0, 0})));
        group("multiple interior pivots choose leftmost", () -> eq(1,
                pivotIndex(new int[] {-1, 0, 0, -1})));
        group("negative values", () -> eq(2,
                pivotIndex(new int[] {-2, 5, 0, 3, 0})));
        group("no pivot with mixed signs", () -> eq(-1,
                pivotIndex(new int[] {1, -2, 3})));
        group("widened positive total", () -> eq(1,
                pivotIndex(new int[] {Integer.MAX_VALUE, 0, Integer.MAX_VALUE})));
        group("widened negative total", () -> eq(1,
                pivotIndex(new int[] {Integer.MIN_VALUE, 0, Integer.MIN_VALUE})));
        group("official maximum length", () -> {
            int[] input = new int[10_000];
            eq(0, pivotIndex(input));
        });
        group("input preservation", () -> {
            int[] input = {1, 7, 3, 6, 5, 6};
            int[] before = input.clone();
            pivotIndex(input);
            if (!Arrays.equals(before, input)) throw new AssertionError("input mutated");
        });
        group("independent deterministic oracle", () -> {
            int[] input = {4, -2, -2, 0, 4};
            eq(quadraticPivotOracle(input), pivotIndex(input));
        });
        group("positive scaling invariance", () -> {
            int[] input = {1, 7, 3, 6, 5, 6};
            eq(pivotIndex(input), pivotIndex(scaled(input, 7)));
        });
        group("negation invariance", () -> {
            int[] input = {2, 1, -1};
            eq(pivotIndex(input), pivotIndex(scaled(input, -1)));
        });
        group("reversal maps rightmost to leftmost", () -> {
            int[] input = {-1, 0, 0, -1};
            eq(input.length - 1 - quadraticRightmostPivotOracle(input),
                    pivotIndex(reversed(input)));
        });
        group("repeatability", () -> {
            int[] input = {1, -1, 0};
            eq(pivotIndex(input), pivotIndex(input));
        });
        group("null rejection", () -> expectIllegal(() -> pivotIndex(null)));
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x7242026L);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = random.nextInt(33);
            int[] input = new int[n];
            for (int i = 0; i < n; i++) input[i] = random.nextInt(2001) - 1000;

            int[] before = input.clone();
            int expected = quadraticPivotOracle(input);
            int actual = pivotIndex(input);
            check(expected == actual, "oracle mismatch");
            check(Arrays.equals(before, input), "input mutated");

            int scale = 1 + random.nextInt(20);
            check(actual == pivotIndex(scaled(input, scale)),
                    "positive scaling changed the pivot");
            check(actual == pivotIndex(scaled(input, -1)),
                    "negation changed the pivot");

            int rightmost = quadraticRightmostPivotOracle(input);
            int expectedReversed = rightmost < 0 ? -1 : n - 1 - rightmost;
            check(expectedReversed == pivotIndex(reversed(input)),
                    "reversal relation failed");
        }
    }

    private static int[] scaled(int[] input, int factor) {
        int[] result = input.clone();
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.multiplyExact(result[i], factor);
        }
        return result;
    }

    private static int[] reversed(int[] input) {
        int[] result = input.clone();
        for (int left = 0, right = result.length - 1; left < right; left++, right--) {
            int temporary = result[left];
            result[left] = result[right];
            result[right] = temporary;
        }
        return result;
    }

    private static void group(String name, Runnable scenario) {
        scenario.run();
        groups++;
    }

    private static void eq(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        randomizedChecks++;
        if (!condition) throw new AssertionError(message);
    }
}
