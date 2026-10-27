import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Dependency-free executable evidence for Subarray Sum Equals K. */
public final class SubarraySumEqualsKContracts {
    private static int scenarios;

    private SubarraySumEqualsKContracts() {}

    static long countSubarrays(int[] nums, int target) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }

        Map<Long, Long> prefixFrequency = new HashMap<>();
        prefixFrequency.put(0L, 1L);
        long prefix = 0L;
        long count = 0L;

        for (int value : nums) {
            prefix += value;
            count += prefixFrequency.getOrDefault(prefix - target, 0L);
            prefixFrequency.merge(prefix, 1L, Long::sum);
        }
        return count;
    }

    private static long quadraticOracle(int[] nums, int target) {
        long count = 0L;
        for (int left = 0; left < nums.length; left++) {
            long sum = 0L;
            for (int right = left; right < nums.length; right++) {
                sum += nums[right];
                if (sum == target) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void expects(long expected, int[] nums, int target) {
        scenarios++;
        long actual = countSubarrays(nums, target);
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but got " + actual
                    + " for " + Arrays.toString(nums) + " and target " + target);
        }
    }

    private static void rejectsNull() {
        scenarios++;
        try {
            countSubarrays(null, 0);
            throw new AssertionError("expected null rejection");
        } catch (IllegalArgumentException expected) {
            // Expected reusable-contract behavior.
        }
    }

    public static void main(String[] args) {
        expects(2L, new int[] {1, 1, 1}, 2);
        expects(3L, new int[] {1, -1, 0}, 0);
        expects(4L, new int[] {3, 4, 7, 2, -3, 1, 4, 2}, 7);
        expects(0L, new int[] {}, 0);
        expects(6L, new int[] {0, 0, 0}, 0);
        expects(2L, new int[] {Integer.MAX_VALUE, 1, -1}, Integer.MAX_VALUE);
        expects(1L, new int[] {Integer.MIN_VALUE}, Integer.MIN_VALUE);
        expects(0L, new int[] {1, 2, 3}, 99);
        expects(2L, new int[] {1, 2, 3}, 3);
        expects(3L, new int[] {-1, -1, 1}, -1);
        expects(6L, new int[] {0, 0, 1, 0}, 1);
        expects(1L, new int[] {5}, 5);
        expects(0L, new int[] {5}, 0);
        expects(1L, new int[] {-5}, -5);
        expects(3L, new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE,
                -Integer.MAX_VALUE}, Integer.MAX_VALUE);
        rejectsNull();

        int[] caller = {3, 4, 7, 2, -3, 1, 4, 2};
        int[] snapshot = caller.clone();
        countSubarrays(caller, 7);
        scenarios++;
        if (!Arrays.equals(snapshot, caller)) {
            throw new AssertionError("input was mutated");
        }

        scenarios++; // randomized oracle agreement
        Random random = new Random(560_016L);
        int comparisons = 20_000;
        for (int trial = 0; trial < comparisons; trial++) {
            int[] nums = new int[random.nextInt(16)];
            for (int i = 0; i < nums.length; i++) {
                nums[i] = random.nextInt(13) - 6;
            }
            int target = random.nextInt(31) - 15;
            int[] original = nums.clone();
            long expected = quadraticOracle(nums, target);
            long actual = countSubarrays(nums, target);
            if (expected != actual || !Arrays.equals(original, nums)) {
                throw new AssertionError("trial " + trial + " failed for "
                        + Arrays.toString(original) + " and target " + target
                        + ": expected " + expected + " but got " + actual);
            }
        }

        System.out.println("Subarray Sum Equals K contracts passed: " + scenarios
                + " scenario groups + " + comparisons + " seeded oracle comparisons");
    }
}
