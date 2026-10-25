import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Minimum Size Subarray Sum. */
public final class MinimumSizeSubarrayContracts {
    private static int scenarios;

    private MinimumSizeSubarrayContracts() {}

    static int minSubArrayLen(int target, int[] nums) {
        if (target <= 0) {
            throw new IllegalArgumentException("target must be positive");
        }
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        for (int value : nums) {
            if (value <= 0) {
                throw new IllegalArgumentException("values must be positive");
            }
        }

        int left = 0;
        int best = nums.length + 1;
        long sum = 0L;
        for (int right = 0; right < nums.length; right++) {
            sum += nums[right];
            while (sum >= target) {
                best = Math.min(best, right - left + 1);
                sum -= nums[left++];
            }
        }
        return best == nums.length + 1 ? 0 : best;
    }

    static int oracle(int target, int[] nums) {
        int best = nums.length + 1;
        for (int left = 0; left < nums.length; left++) {
            long sum = 0L;
            for (int right = left; right < nums.length; right++) {
                sum += nums[right];
                if (sum >= target) {
                    best = Math.min(best, right - left + 1);
                    break;
                }
            }
        }
        return best == nums.length + 1 ? 0 : best;
    }

    private static void expect(int expected, int target, int... nums) {
        int[] before = nums.clone();
        int actual = minSubArrayLen(target, nums);
        if (actual != expected) {
            throw new AssertionError("expected " + expected + " but got " + actual
                    + " for target=" + target + ", nums=" + Arrays.toString(nums));
        }
        if (!Arrays.equals(before, nums)) {
            throw new AssertionError("input mutated");
        }
        scenarios++;
    }

    private static void rejects(int target, int[] nums) {
        try {
            minSubArrayLen(target, nums);
            throw new AssertionError("expected invalid input rejection");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    public static void main(String[] args) {
        expect(2, 7, 2, 3, 1, 2, 4, 3);
        expect(1, 7, 7);
        expect(0, 10, 1, 1, 1, 1);
        expect(2, 2_000_000_000, 1_500_000_000, 1_500_000_000);
        expect(0, 1);
        expect(1, 4, 1, 4, 4);
        expect(3, 11, 1, 2, 3, 4, 5);
        expect(1, 10, 10, 1, 1);
        rejects(0, new int[] {1});
        rejects(-1, new int[] {1});
        rejects(1, null);
        rejects(1, new int[] {1, 0, 2});
        rejects(1, new int[] {1, -1, 2});
        scenarios += 2; // randomized oracle agreement and nonmutation properties

        Random random = new Random(209_209_209L);
        int trials = 20_000;
        for (int trial = 0; trial < trials; trial++) {
            int[] nums = new int[random.nextInt(31)];
            for (int i = 0; i < nums.length; i++) {
                nums[i] = 1 + random.nextInt(100_000_000);
            }
            int target = 1 + random.nextInt(Integer.MAX_VALUE);
            int[] before = nums.clone();
            int expected = oracle(target, nums);
            int actual = minSubArrayLen(target, nums);
            if (actual != expected || !Arrays.equals(before, nums)) {
                throw new AssertionError("trial " + trial + " failed for target=" + target
                        + ", nums=" + Arrays.toString(nums) + ": " + actual + " != " + expected);
            }
        }

        System.out.println("Minimum Size Subarray Sum contracts passed: " + scenarios
                + " scenario groups + " + trials + " seeded oracle comparisons");
    }
}
