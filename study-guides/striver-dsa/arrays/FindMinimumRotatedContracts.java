import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Arrays problem 46. */
public final class FindMinimumRotatedContracts {
    private static int groups;
    private static long randomizedChecks;

    private FindMinimumRotatedContracts() {}

    /** Validated reusable boundary; the official problem already guarantees this shape. */
    static int findMin(int[] nums) {
        validateStrictRotation(nums);
        return findMinCore(nums);
    }

    /** Trusted-input logarithmic core. */
    static int findMinCore(int[] nums) {
        int left = 0;
        int right = nums.length - 1;
        while (left < right) {
            int middle = left + (right - left) / 2;
            if (nums[middle] > nums[right]) left = middle + 1;
            else right = middle;
        }
        return nums[left];
    }

    static SearchResult findMinInspected(int[] nums) {
        int left = 0;
        int right = nums.length - 1;
        int inspections = 0;
        while (left < right) {
            int middle = left + (right - left) / 2;
            inspections++;
            if (nums[middle] > nums[right]) left = middle + 1;
            else right = middle;
        }
        return new SearchResult(nums[left], inspections);
    }

    /** Linear reusable validation is intentionally separate from the O(log n) core. */
    static void validateStrictRotation(int[] nums) {
        if (nums == null) throw new IllegalArgumentException("nums must be non-null");
        if (nums.length == 0) throw new IllegalArgumentException("nums must be non-empty");
        int drops = 0;
        for (int i = 1; i < nums.length; i++) {
            if (nums[i - 1] == nums[i]) {
                throw new IllegalArgumentException("nums must contain unique values");
            }
            if (nums[i - 1] > nums[i] && ++drops > 1) {
                throw new IllegalArgumentException("nums must be a rotated increasing array");
            }
        }
        if (drops == 1 && nums[nums.length - 1] >= nums[0]) {
            throw new IllegalArgumentException("nums must be a rotated increasing array");
        }
    }

    static int linearOracle(int[] nums) {
        int minimum = nums[0];
        for (int value : nums) minimum = Math.min(minimum, value);
        return minimum;
    }

    record SearchResult(int value, int inspections) {}

    public static void main(String[] args) {
        deterministicScenarios();
        randomizedOracleChecks();
        System.out.println("FindMinimumRotatedContracts: " + groups
                + " scenario groups + 20000 seeded linear-oracle trials passed; checks="
                + randomizedChecks);
    }

    private static void deterministicScenarios() {
        group("official example one", () -> eq(1, findMin(new int[] {3, 4, 5, 1, 2})));
        group("official example two", () -> eq(0, findMin(new int[] {4, 5, 6, 7, 0, 1, 2})));
        group("official unrotated example", () -> eq(11, findMin(new int[] {11, 13, 15, 17})));
        group("singleton", () -> eq(-7, findMin(new int[] {-7})));
        group("two values unrotated", () -> eq(1, findMin(new int[] {1, 9})));
        group("two values rotated", () -> eq(1, findMin(new int[] {9, 1})));
        group("pivot near left", () -> eq(-9, findMin(new int[] {8, -9, -2, 3})));
        group("pivot at right", () -> eq(-5, findMin(new int[] {-1, 2, 7, -5})));
        group("all negative", () -> eq(-10, findMin(new int[] {-4, -2, -10, -8})));
        group("full int endpoints", () -> eq(Integer.MIN_VALUE,
                findMin(new int[] {0, Integer.MAX_VALUE, Integer.MIN_VALUE, -1})));
        group("input preservation", () -> {
            int[] input = {6, 8, 1, 3, 5};
            int[] before = input.clone();
            findMin(input);
            if (!Arrays.equals(before, input)) throw new AssertionError("input mutated");
        });
        group("validated-core parity", () -> {
            int[] input = {30, 40, 50, 5, 10, 20};
            eq(findMinCore(input), findMin(input));
        });
        group("independent deterministic oracle", () -> {
            int[] input = {5, 7, 9, -3, 0, 2};
            eq(linearOracle(input), findMin(input));
        });
        group("logarithmic inspection bound", () -> {
            SearchResult result = findMinInspected(new int[] {7, 9, 12, 15, 1, 3, 5});
            eq(1, result.value());
            if (result.inspections() > ceilLog2(7)) throw new AssertionError("too many inspections");
        });
        group("null rejection", () -> expectIllegal(() -> findMin(null)));
        group("empty rejection", () -> expectIllegal(() -> findMin(new int[0])));
        group("adjacent duplicate rejection", () -> expectIllegal(
                () -> findMin(new int[] {1, 2, 2, 3})));
        group("boundary duplicate rejection", () -> expectIllegal(
                () -> findMin(new int[] {2, 3, 1, 2})));
        group("two descents rejection", () -> expectIllegal(
                () -> findMin(new int[] {4, 1, 3, 2})));
        group("bad wrap boundary rejection", () -> expectIllegal(
                () -> findMin(new int[] {3, 5, 1, 4})));
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x1532026L);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = 1 + random.nextInt(64);
            int[] sorted = new int[n];
            sorted[0] = -2_000 + random.nextInt(501);
            for (int i = 1; i < n; i++) sorted[i] = sorted[i - 1] + 1 + random.nextInt(7);
            int[] input = rotateLeft(sorted, random.nextInt(n));
            int[] before = input.clone();

            SearchResult inspected = findMinInspected(input);
            int actual = findMin(input);
            check(actual == linearOracle(input), "oracle mismatch");
            check(Arrays.equals(before, input), "input mutated");
            check(actual == findMinCore(input), "wrapper/core mismatch");
            check(inspected.value() == actual, "instrumented search mismatch");
            check(inspected.inspections() <= ceilLog2(n), "inspection bound exceeded");
        }
    }

    private static int[] rotateLeft(int[] sorted, int offset) {
        int[] result = new int[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            result[i] = sorted[(i + offset) % sorted.length];
        }
        return result;
    }

    private static int ceilLog2(int n) {
        return n <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(n - 1);
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
