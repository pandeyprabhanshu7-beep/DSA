import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Arrays problem 45. */
public final class MaximumConsecutiveOnesIIIContracts {
    private static int groups;
    private static long randomizedChecks;

    private MaximumConsecutiveOnesIIIContracts() {}

    /** Validated reusable boundary; the official problem already guarantees these conditions. */
    static int longestOnes(int[] nums, int k) {
        if (nums == null) throw new IllegalArgumentException("nums must be non-null");
        if (k < 0) throw new IllegalArgumentException("k must be non-negative");
        for (int value : nums) {
            if (value != 0 && value != 1) {
                throw new IllegalArgumentException("nums must contain only 0 and 1");
            }
        }
        return longestOnesCore(nums, k);
    }

    /** Linear trusted-input core: the window is repaired until it contains at most k zeros. */
    static int longestOnesCore(int[] nums, int k) {
        int left = 0;
        int zeros = 0;
        int best = 0;
        for (int right = 0; right < nums.length; right++) {
            if (nums[right] == 0) zeros++;
            while (zeros > k) {
                if (nums[left] == 0) zeros--;
                left++;
            }
            best = Math.max(best, right - left + 1);
        }
        return best;
    }

    /** Independent definition oracle: enumerate every start and extend every binary interval. */
    static int quadraticOracle(int[] nums, int k) {
        if (nums == null) throw new IllegalArgumentException("nums must be non-null");
        if (k < 0) throw new IllegalArgumentException("k must be non-negative");
        int best = 0;
        for (int left = 0; left < nums.length; left++) {
            int zeros = 0;
            for (int right = left; right < nums.length; right++) {
                if (nums[right] == 0) zeros++;
                if (zeros <= k) best = Math.max(best, right - left + 1);
            }
        }
        return best;
    }

    public static void main(String[] args) {
        deterministicScenarios();
        randomizedOracleChecks();
        System.out.println("MaximumConsecutiveOnesIIIContracts: " + groups
                + " scenario groups + 20000 seeded quadratic-oracle trials passed; checks="
                + randomizedChecks);
    }

    private static void deterministicScenarios() {
        group("official example one", () -> eq(6, longestOnes(
                new int[] {1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0}, 2)));
        group("official example two", () -> eq(10, longestOnes(new int[] {
                0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1}, 3)));
        group("zero budget", () -> eq(3,
                longestOnes(new int[] {0, 1, 1, 1, 0, 1}, 0)));
        group("budget covers every zero", () -> eq(5,
                longestOnes(new int[] {1, 0, 0, 1, 0}, 3)));
        group("budget exceeds every zero", () -> eq(4,
                longestOnes(new int[] {1, 0, 1, 1}, 99)));
        group("all zeros limited budget", () -> eq(2,
                longestOnes(new int[] {0, 0, 0, 0}, 2)));
        group("all zeros zero budget", () -> eq(0,
                longestOnes(new int[] {0, 0, 0}, 0)));
        group("all ones", () -> eq(4,
                longestOnes(new int[] {1, 1, 1, 1}, 0)));
        group("single zero flippable", () -> eq(1, longestOnes(new int[] {0}, 1)));
        group("single zero not flippable", () -> eq(0, longestOnes(new int[] {0}, 0)));
        group("single one", () -> eq(1, longestOnes(new int[] {1}, 0)));
        group("empty reusable extension", () -> eq(0, longestOnes(new int[0], 0)));
        group("alternating values", () -> eq(5,
                longestOnes(new int[] {1, 0, 1, 0, 1, 0, 1}, 2)));
        group("best window is a suffix", () -> eq(5,
                longestOnes(new int[] {0, 0, 0, 1, 0, 1, 1}, 2)));
        group("input preservation", () -> {
            int[] input = {1, 0, 0, 1, 1};
            int[] before = input.clone();
            longestOnes(input, 1);
            if (!Arrays.equals(before, input)) throw new AssertionError("input mutated");
        });
        group("validated-core parity", () -> {
            int[] input = {1, 0, 1, 0, 0, 1};
            eq(longestOnesCore(input, 2), longestOnes(input, 2));
        });
        group("independent deterministic oracle", () -> {
            int[] input = {0, 1, 0, 1, 1, 0};
            eq(quadraticOracle(input, 1), longestOnes(input, 1));
        });
        group("null rejection", () -> expectIllegal(() -> longestOnes(null, 0)));
        group("negative budget rejection", () -> expectIllegal(
                () -> longestOnes(new int[] {1, 0}, -1)));
        group("non-binary rejection", () -> expectIllegal(
                () -> longestOnes(new int[] {1, 2, 0}, 1)));
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x10042026L);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = random.nextInt(41);
            int[] input = new int[n];
            int zeroCount = 0;
            for (int i = 0; i < n; i++) {
                input[i] = random.nextBoolean() ? 1 : 0;
                if (input[i] == 0) zeroCount++;
            }
            int k = random.nextInt(n + 3);
            int[] before = input.clone();

            int actual = longestOnes(input, k);
            check(actual == quadraticOracle(input, k), "oracle mismatch");
            check(Arrays.equals(before, input), "input mutated");
            check(actual == longestOnes(reversed(input), k), "reversal changed length");
            check(longestOnes(input, k + 1) >= actual, "larger budget reduced answer");
            check(actual == longestOnes(input, Math.min(k, zeroCount)),
                    "budget clamp changed answer");
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
