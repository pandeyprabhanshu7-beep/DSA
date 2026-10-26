import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;

/** Dependency-free executable evidence for the Sliding Window Maximum section. */
public final class SlidingWindowMaximumContracts {
    private static int scenarios;

    private SlidingWindowMaximumContracts() {}

    static int[] maxSlidingWindow(int[] nums, int k) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        if (k < 1 || k > nums.length) {
            throw new IllegalArgumentException("k must be in [1, nums.length]");
        }

        int[] maxima = new int[nums.length - k + 1];
        Deque<Integer> candidates = new ArrayDeque<>();
        int output = 0;

        for (int right = 0; right < nums.length; right++) {
            while (!candidates.isEmpty() && candidates.peekFirst() <= right - k) {
                candidates.removeFirst();
            }
            while (!candidates.isEmpty()
                    && nums[candidates.peekLast()] <= nums[right]) {
                candidates.removeLast();
            }
            candidates.addLast(right);

            if (right >= k - 1) {
                maxima[output++] = nums[candidates.peekFirst()];
            }
        }
        return maxima;
    }

    static int[] oracle(int[] nums, int k) {
        int[] maxima = new int[nums.length - k + 1];
        for (int start = 0; start + k <= nums.length; start++) {
            int maximum = nums[start];
            for (int i = start + 1; i < start + k; i++) {
                maximum = Math.max(maximum, nums[i]);
            }
            maxima[start] = maximum;
        }
        return maxima;
    }

    private static void expect(int[] expected, int k, int... nums) {
        int[] before = nums.clone();
        int[] actual = maxSlidingWindow(nums, k);
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("expected " + Arrays.toString(expected)
                    + " but got " + Arrays.toString(actual)
                    + " for k=" + k + " and " + Arrays.toString(nums));
        }
        if (!Arrays.equals(before, nums)) {
            throw new AssertionError("input mutated");
        }
        scenarios++;
    }

    private static void rejects(int[] nums, int k) {
        try {
            maxSlidingWindow(nums, k);
            throw new AssertionError("expected rejection for k=" + k);
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    public static void main(String[] args) {
        expect(new int[] {3, 3, 5, 5, 6, 7}, 3, 1, 3, -1, -3, 5, 3, 6, 7);
        expect(new int[] {1}, 1, 1);
        expect(new int[] {9, 8, 7}, 3, 9, 8, 7, 6, 5);
        expect(new int[] {5, 5, 5}, 2, 5, 5, 5, 5);
        expect(new int[] {4, 4, 4}, 3, 4, 1, 4, 1, 4);
        expect(new int[] {-1, -2}, 2, -1, -3, -2);
        expect(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 2,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        expect(new int[] {3, 1, 2}, 1, 3, 1, 2);
        expect(new int[] {9}, 5, 9, 8, 7, 6, 5);
        expect(new int[] {2, 2, 2, 2}, 2, 2, 1, 2, 1, 2);
        rejects(null, 1);
        rejects(new int[0], 0);
        rejects(new int[] {1}, 0);
        rejects(new int[] {1}, 2);
        scenarios += 2; // randomized oracle agreement and nonmutation properties

        Random random = new Random(90_400_013L);
        int trials = 20_000;
        for (int trial = 0; trial < trials; trial++) {
            int[] nums = new int[1 + random.nextInt(35)];
            for (int i = 0; i < nums.length; i++) {
                int selector = random.nextInt(40);
                nums[i] = selector == 0 ? Integer.MIN_VALUE
                        : selector == 1 ? Integer.MAX_VALUE
                        : random.nextInt(21) - 10;
            }
            int k = 1 + random.nextInt(nums.length);
            int[] before = nums.clone();
            int[] expected = oracle(nums, k);
            int[] actual = maxSlidingWindow(nums, k);
            if (!Arrays.equals(expected, actual) || !Arrays.equals(before, nums)) {
                throw new AssertionError("trial " + trial + " failed for k=" + k
                        + " and " + Arrays.toString(nums));
            }
        }

        System.out.println("Sliding Window Maximum contracts passed: " + scenarios
                + " scenario groups + " + trials + " seeded oracle comparisons");
    }
}
