import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Maximum Sum Circular Subarray. */
public final class MaximumSumCircularSubarrayContracts {
    private static int groups;
    private static long checks;

    /** Returns the maximum sum of a non-empty circular subarray without mutation. */
    static long maxCircularSubarraySum(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must be non-null and non-empty");
        }

        long total = values[0];
        long maxEnding = values[0];
        long minEnding = values[0];
        long maxSubarray = values[0];
        long minSubarray = values[0];

        for (int index = 1; index < values.length; index++) {
            long value = values[index];
            maxEnding = Math.max(value, maxEnding + value);
            minEnding = Math.min(value, minEnding + value);
            maxSubarray = Math.max(maxSubarray, maxEnding);
            minSubarray = Math.min(minSubarray, minEnding);
            total += value;
        }

        if (maxSubarray < 0L) {
            return maxSubarray;
        }
        return Math.max(maxSubarray, total - minSubarray);
    }

    /** LeetCode-compatible wrapper; official constraints keep the result in int. */
    static int maxSubarraySumCircular(int[] values) {
        return Math.toIntExact(maxCircularSubarraySum(values));
    }

    /** Independent O(n^2) circular definition oracle. */
    private static long quadraticOracle(int[] values) {
        long best = Long.MIN_VALUE;
        for (int start = 0; start < values.length; start++) {
            long sum = 0L;
            for (int length = 1; length <= values.length; length++) {
                sum += values[(start + length - 1) % values.length];
                best = Math.max(best, sum);
            }
        }
        return best;
    }

    private static boolean rejects(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | ArithmeticException expected) {
            return true;
        }
    }

    private static void group(String name, boolean condition) {
        groups++;
        checks++;
        if (!condition) {
            throw new AssertionError("group failed: " + name);
        }
    }

    private static void deterministicEvidence() {
        group("official non-wrapping", maxSubarraySumCircular(new int[] {1, -2, 3, -2}) == 3);
        group("official wrapping", maxSubarraySumCircular(new int[] {5, -3, 5}) == 10);
        group("official all negative", maxSubarraySumCircular(new int[] {-3, -2, -3}) == -2);
        group("positive singleton", maxSubarraySumCircular(new int[] {7}) == 7);
        group("negative singleton", maxSubarraySumCircular(new int[] {-7}) == -7);
        group("zero singleton", maxSubarraySumCircular(new int[] {0}) == 0);
        group("all positive", maxSubarraySumCircular(new int[] {1, 2, 3, 4}) == 10);
        group("all zeroes", maxSubarraySumCircular(new int[] {0, 0, 0}) == 0);
        group("zero beats negatives", maxSubarraySumCircular(new int[] {-2, 0, -3}) == 0);
        group("ordinary Kadane wins", maxSubarraySumCircular(new int[] {-2, 4, -1, 3, -5}) == 6);
        group("wrap excludes minimum middle", maxSubarraySumCircular(new int[] {8, -9, 9, -8, 7}) == 16);
        group("full circle legal", maxSubarraySumCircular(new int[] {3, 1, 2}) == 6);
        group("cannot reuse fixed buffer", maxSubarraySumCircular(new int[] {5, -3, 5}) != 15);
        group("ties remain values", maxSubarraySumCircular(new int[] {2, -2, 2, -2}) == 2);

        int[] preserved = {5, -3, 5};
        int[] snapshot = preserved.clone();
        maxCircularSubarraySum(preserved);
        group("input preserved", Arrays.equals(preserved, snapshot));
        group("null rejected", rejects(() -> maxCircularSubarraySum(null)));
        group("empty rejected", rejects(() -> maxCircularSubarraySum(new int[] {})));

        long wide = 2L * Integer.MAX_VALUE;
        group("long extension", maxCircularSubarraySum(
                new int[] {Integer.MAX_VALUE, -1, Integer.MAX_VALUE}) == wide);
        group("official wrapper detects extended overflow", rejects(() -> maxSubarraySumCircular(
                new int[] {Integer.MAX_VALUE, -1, Integer.MAX_VALUE})));
        group("oracle parity on deterministic catalog", Arrays.asList(
                new int[] {1, -2, 3, -2}, new int[] {5, -3, 5},
                new int[] {-3, -2, -3}, new int[] {8, -9, 9, -8, 7})
                .stream().allMatch(values -> maxCircularSubarraySum(values) == quadraticOracle(values)));
    }

    private static int[] randomArray(Random random) {
        int[] values = new int[1 + random.nextInt(12)];
        for (int index = 0; index < values.length; index++) {
            values[index] = random.nextInt(41) - 20;
        }
        return values;
    }

    private static int[] reverse(int[] values) {
        int[] result = values.clone();
        for (int left = 0, right = result.length - 1; left < right; left++, right--) {
            int value = result[left];
            result[left] = result[right];
            result[right] = value;
        }
        return result;
    }

    private static int[] rotateLeftOne(int[] values) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[(index + 1) % values.length];
        }
        return result;
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x35_435243L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] values = randomArray(random);
            int[] snapshot = values.clone();
            long expected = quadraticOracle(values);
            long actual = maxCircularSubarraySum(values);

            checks++;
            if (actual != expected) throw new AssertionError("oracle mismatch at trial " + trial);
            checks++;
            if (!Arrays.equals(values, snapshot)) throw new AssertionError("input mutated at trial " + trial);
            checks++;
            if (maxCircularSubarraySum(values.clone()) != actual) {
                throw new AssertionError("repeat mismatch at trial " + trial);
            }
            checks++;
            if (maxCircularSubarraySum(reverse(values)) != actual) {
                throw new AssertionError("reversal mismatch at trial " + trial);
            }
            checks++;
            if (maxCircularSubarraySum(rotateLeftOne(values)) != actual) {
                throw new AssertionError("rotation mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) throw new AssertionError("expected 20 scenario groups, got " + groups);
        System.out.println("PARITY|NONWRAP=3|WRAP=10|ALL_NEGATIVE=-2|WIDE=4294967294|PRESERVED=true");
        System.out.println("MaximumSumCircularSubarrayContracts: 20 scenario groups + 20000 seeded quadratic-oracle comparisons passed; checks=" + checks);
    }
}
