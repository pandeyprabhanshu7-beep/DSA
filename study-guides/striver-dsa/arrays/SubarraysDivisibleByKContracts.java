import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Dependency-free executable evidence for Subarrays Divisible by K. */
public final class SubarraysDivisibleByKContracts {
    private static int scenarios;

    private SubarraysDivisibleByKContracts() {}

    static long countDivisibleSubarrays(int[] nums, int modulus) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        if (modulus <= 0) {
            throw new IllegalArgumentException("modulus must be positive");
        }

        Map<Integer, Long> remainderFrequency = new HashMap<>();
        remainderFrequency.put(0, 1L);
        int remainder = 0;
        long count = 0L;

        for (int value : nums) {
            remainder = Math.floorMod((long) remainder + value, modulus);
            count += remainderFrequency.getOrDefault(remainder, 0L);
            remainderFrequency.merge(remainder, 1L, Long::sum);
        }
        return count;
    }

    private static long quadraticOracle(int[] nums, int modulus) {
        long count = 0L;
        for (int left = 0; left < nums.length; left++) {
            long sum = 0L;
            for (int right = left; right < nums.length; right++) {
                sum += nums[right];
                if (Math.floorMod(sum, modulus) == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void expects(long expected, int[] nums, int modulus) {
        scenarios++;
        long actual = countDivisibleSubarrays(nums, modulus);
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but got " + actual
                    + " for " + Arrays.toString(nums) + " and modulus " + modulus);
        }
    }

    private static void rejects(int[] nums, int modulus) {
        scenarios++;
        try {
            countDivisibleSubarrays(nums, modulus);
            throw new AssertionError("expected rejection for modulus " + modulus);
        } catch (IllegalArgumentException expected) {
            // Expected reusable-contract behavior.
        }
    }

    public static void main(String[] args) {
        expects(7L, new int[] {4, 5, 0, -2, -3, 1}, 5);
        expects(2L, new int[] {-1, 2, 9}, 2);
        expects(6L, new int[] {0, 0, 0}, 7);
        expects(0L, new int[] {}, 7);
        expects(0L, new int[] {5}, 9);
        expects(1L, new int[] {5}, 5);
        expects(1L, new int[] {-5}, 5);
        expects(2L, new int[] {1, 1, 1}, 2);
        expects(3L, new int[] {1, 2, 3}, 3);
        expects(3L, new int[] {Integer.MAX_VALUE, 1, -1}, Integer.MAX_VALUE);
        expects(1L, new int[] {Integer.MIN_VALUE}, 2);
        expects(0L, new int[] {Integer.MAX_VALUE}, 2);
        expects(6L, new int[] {-4, 5, 0, -2, -3, 1}, 5);
        expects(1L, new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 2);
        rejects(new int[] {1}, 0);
        rejects(new int[] {1}, -3);
        rejects(null, 5);

        int[] caller = {4, 5, 0, -2, -3, 1};
        int[] snapshot = caller.clone();
        countDivisibleSubarrays(caller, 5);
        scenarios++;
        if (!Arrays.equals(snapshot, caller)) {
            throw new AssertionError("input was mutated");
        }

        scenarios++; // randomized oracle agreement
        Random random = new Random(974_017L);
        int comparisons = 20_000;
        for (int trial = 0; trial < comparisons; trial++) {
            int[] nums = new int[random.nextInt(17)];
            for (int i = 0; i < nums.length; i++) {
                int selector = random.nextInt(200);
                if (selector == 0) {
                    nums[i] = Integer.MIN_VALUE;
                } else if (selector == 1) {
                    nums[i] = Integer.MAX_VALUE;
                } else {
                    nums[i] = random.nextInt(41) - 20;
                }
            }
            int modulus = 1 + random.nextInt(25);
            int[] original = nums.clone();
            long expected = quadraticOracle(nums, modulus);
            long actual = countDivisibleSubarrays(nums, modulus);
            if (expected != actual || !Arrays.equals(original, nums)) {
                throw new AssertionError("trial " + trial + " failed for "
                        + Arrays.toString(original) + " and modulus " + modulus
                        + ": expected " + expected + " but got " + actual);
            }
        }

        System.out.println("Subarrays Divisible by K contracts passed: " + scenarios
                + " scenario groups + " + comparisons + " seeded oracle comparisons");
    }
}
