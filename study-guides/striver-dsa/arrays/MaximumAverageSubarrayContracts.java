import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Arrays problem 43. */
public final class MaximumAverageSubarrayContracts {
    private static int groups;
    private static long randomizedChecks;

    private MaximumAverageSubarrayContracts() {}

    /** Returns the best sum among all contiguous windows of exactly k values. */
    static long maximumWindowSum(int[] nums, int k) {
        requireWindow(nums, k);
        long window = 0;
        for (int i = 0; i < k; i++) window += nums[i];
        long best = window;
        for (int right = k; right < nums.length; right++) {
            window += (long) nums[right] - nums[right - k];
            best = Math.max(best, window);
        }
        return best;
    }

    /** Official result form; division is floating-point because one operand is double. */
    static double findMaxAverage(int[] nums, int k) {
        return maximumWindowSum(nums, k) / (double) k;
    }

    private static void requireWindow(int[] nums, int k) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        if (k < 1 || k > nums.length) {
            throw new IllegalArgumentException("k must satisfy 1 <= k <= nums.length");
        }
    }

    /** Independent definition oracle: it deliberately re-sums every k-window. */
    static long quadraticWindowOracle(int[] nums, int k) {
        requireWindow(nums, k);
        long best = Long.MIN_VALUE;
        for (int left = 0; left + k <= nums.length; left++) {
            long candidate = 0;
            for (int i = left; i < left + k; i++) candidate += nums[i];
            best = Math.max(best, candidate);
        }
        return best;
    }

    public static void main(String[] args) {
        deterministicScenarios();
        randomizedOracleChecks();
        System.out.println("MaximumAverageSubarrayContracts: " + groups
                + " scenario groups + 20000 seeded direct-window-oracle trials passed; checks="
                + randomizedChecks);
    }

    private static void deterministicScenarios() {
        group("official example one", () -> close(12.75,
                findMaxAverage(new int[] {1, 12, -5, -6, 50, 3}, 4)));
        group("official example two", () -> close(5.0,
                findMaxAverage(new int[] {5}, 1)));
        group("all negative", () -> close(-3.5,
                findMaxAverage(new int[] {-8, -3, -4, -9}, 2)));
        group("k one chooses maximum element", () -> close(9.0,
                findMaxAverage(new int[] {-4, 9, 1, 2}, 1)));
        group("k equals length", () -> close(2.5,
                findMaxAverage(new int[] {1, 2, 3, 4}, 4)));
        group("zero average", () -> close(0.0,
                findMaxAverage(new int[] {-2, 2, -3, 3}, 2)));
        group("equal best windows", () -> close(3.0,
                findMaxAverage(new int[] {2, 4, 2, 4}, 2)));
        group("best final window", () -> close(8.0,
                findMaxAverage(new int[] {-10, 1, 2, 7, 9}, 2)));
        group("best initial window", () -> close(8.0,
                findMaxAverage(new int[] {9, 7, 2, 1, -10}, 2)));
        group("official maximum magnitude", () -> close(10_000.0,
                findMaxAverage(new int[] {10_000, 10_000, -10_000}, 2)));
        group("widened positive accumulation", () -> eq(4_294_967_294L,
                maximumWindowSum(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, -1}, 2)));
        group("widened negative accumulation", () -> eq(-4_294_967_296L,
                maximumWindowSum(new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE}, 2)));
        group("input preservation", () -> {
            int[] input = {1, 12, -5, -6, 50, 3};
            int[] before = input.clone();
            findMaxAverage(input, 4);
            if (!Arrays.equals(before, input)) throw new AssertionError("input mutated");
        });
        group("independent deterministic oracle", () -> {
            int[] input = {6, -3, 5, 1, -2, 8};
            eq(quadraticWindowOracle(input, 3), maximumWindowSum(input, 3));
        });
        group("reversal invariance", () -> {
            int[] input = {6, -3, 5, 1, -2, 8};
            eq(maximumWindowSum(input, 3), maximumWindowSum(reversed(input), 3));
        });
        group("translation changes average exactly", () -> {
            int[] input = {-5, 3, 7, -2, 4};
            close(findMaxAverage(input, 3) + 11.0,
                    findMaxAverage(translated(input, 11), 3));
        });
        group("null rejection", () -> expectIllegal(() -> findMaxAverage(null, 1)));
        group("empty rejection", () -> expectIllegal(
                () -> findMaxAverage(new int[0], 1)));
        group("zero k rejection", () -> expectIllegal(
                () -> findMaxAverage(new int[] {1, 2}, 0)));
        group("oversized k rejection", () -> expectIllegal(
                () -> findMaxAverage(new int[] {1, 2}, 3)));
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x6432026L);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = 1 + random.nextInt(32);
            int k = 1 + random.nextInt(n);
            int[] input = new int[n];
            for (int i = 0; i < n; i++) input[i] = random.nextInt(2_000_001) - 1_000_000;

            int[] before = input.clone();
            long expected = quadraticWindowOracle(input, k);
            long actual = maximumWindowSum(input, k);
            check(expected == actual, "oracle mismatch");
            check(Arrays.equals(before, input), "input mutated");
            check(Double.compare(findMaxAverage(input, k), actual / (double) k) == 0,
                    "average conversion mismatch");
            check(actual == maximumWindowSum(reversed(input), k),
                    "reversal changed the best sum");

            int offset = random.nextInt(2001) - 1000;
            int[] shifted = translated(input, offset);
            check(maximumWindowSum(shifted, k) == actual + (long) offset * k,
                    "translation relation failed");
        }
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

    private static int[] translated(int[] input, int offset) {
        int[] result = input.clone();
        for (int i = 0; i < result.length; i++) result[i] = Math.addExact(result[i], offset);
        return result;
    }

    private static void group(String name, Runnable scenario) {
        scenario.run();
        groups++;
    }

    private static void eq(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void close(double expected, double actual) {
        if (Math.abs(expected - actual) > 1e-12) {
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
