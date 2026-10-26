import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Range Sum Query — Immutable. */
public final class RangeSumQueryContracts {
    private static int scenarios;

    private RangeSumQueryContracts() {}

    static final class ImmutableRangeSum {
        private final long[] prefix;

        ImmutableRangeSum(int[] nums) {
            if (nums == null) {
                throw new IllegalArgumentException("nums must not be null");
            }
            int[] snapshot = Arrays.copyOf(nums, nums.length);
            prefix = new long[snapshot.length + 1];
            for (int i = 0; i < snapshot.length; i++) {
                prefix[i + 1] = prefix[i] + snapshot[i];
            }
        }

        int size() {
            return prefix.length - 1;
        }

        long sumRange(int left, int right) {
            if (left < 0 || right < left || right >= size()) {
                throw new IndexOutOfBoundsException(
                        "required: 0 <= left <= right < " + size());
            }
            return prefix[right + 1] - prefix[left];
        }
    }

    private static long scan(int[] nums, int left, int right) {
        long sum = 0;
        for (int i = left; i <= right; i++) {
            sum += nums[i];
        }
        return sum;
    }

    private static void expects(long expected, long actual) {
        scenarios++;
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }

    private static void rejects(int[] nums, int left, int right) {
        scenarios++;
        try {
            new ImmutableRangeSum(nums).sumRange(left, right);
            throw new AssertionError("expected invalid range rejection");
        } catch (IndexOutOfBoundsException expected) {
            // Expected reusable-contract behavior.
        }
    }

    private static void rejectsNull() {
        scenarios++;
        try {
            new ImmutableRangeSum(null);
            throw new AssertionError("expected null rejection");
        } catch (IllegalArgumentException expected) {
            // Expected reusable-contract behavior.
        }
    }

    public static void main(String[] args) {
        int[] canonical = {-2, 0, 3, -5, 2, -1};
        ImmutableRangeSum sums = new ImmutableRangeSum(canonical);
        expects(1, sums.sumRange(0, 2));
        expects(-1, sums.sumRange(2, 5));
        expects(-3, sums.sumRange(0, 5));

        expects(7, new ImmutableRangeSum(new int[] {7}).sumRange(0, 0));
        expects(0, new ImmutableRangeSum(new int[] {0, 0, 0}).sumRange(0, 2));
        expects(-6, new ImmutableRangeSum(new int[] {-1, -2, -3}).sumRange(0, 2));

        int[] mutableCallerArray = {4, 5, 6};
        ImmutableRangeSum snapshot = new ImmutableRangeSum(mutableCallerArray);
        mutableCallerArray[0] = 1_000_000;
        mutableCallerArray[2] = -1_000_000;
        expects(15, snapshot.sumRange(0, 2));
        expects(11, snapshot.sumRange(1, 2));

        ImmutableRangeSum wide = new ImmutableRangeSum(new int[] {
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE
        });
        expects(4_294_967_294L, wide.sumRange(0, 1));
        expects(2_147_483_646L, wide.sumRange(0, 2));
        expects(Integer.MIN_VALUE, wide.sumRange(2, 2));

        rejectsNull();
        rejects(new int[] {}, 0, 0);
        rejects(new int[] {1, 2, 3}, -1, 1);
        rejects(new int[] {1, 2, 3}, 0, 3);
        rejects(new int[] {1, 2, 3}, 2, 1);

        scenarios += 2; // randomized oracle agreement and snapshot-isolation properties
        Random random = new Random(303_014L);
        int comparisons = 20_000;
        for (int trial = 0; trial < comparisons; trial++) {
            int[] nums = new int[1 + random.nextInt(40)];
            for (int i = 0; i < nums.length; i++) {
                int selector = random.nextInt(50);
                nums[i] = selector == 0 ? Integer.MIN_VALUE
                        : selector == 1 ? Integer.MAX_VALUE
                        : random.nextInt(2_000_001) - 1_000_000;
            }
            int[] original = nums.clone();
            ImmutableRangeSum actual = new ImmutableRangeSum(nums);
            int left = random.nextInt(nums.length);
            int right = left + random.nextInt(nums.length - left);
            long expected = scan(original, left, right);

            Arrays.fill(nums, 99);
            if (actual.sumRange(left, right) != expected
                    || actual.size() != original.length) {
                throw new AssertionError("trial " + trial + " failed for "
                        + Arrays.toString(original) + " range [" + left + "," + right + "]");
            }
        }

        System.out.println("Range Sum Query contracts passed: " + scenarios
                + " scenario groups + " + comparisons + " seeded oracle comparisons");
    }
}
