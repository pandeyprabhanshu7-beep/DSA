import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class LongestConsecutiveContracts {
    private static int scenarios;

    static int longestConsecutive(int[] nums) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }

        Set<Integer> values = new HashSet<>();
        for (int value : nums) {
            values.add(value);
        }

        int best = 0;
        for (int value : values) {
            if (value != Integer.MIN_VALUE && values.contains(value - 1)) {
                continue;
            }

            int current = value;
            int length = 1;
            while (current != Integer.MAX_VALUE && values.contains(current + 1)) {
                current++;
                length++;
            }
            best = Math.max(best, length);
        }
        return best;
    }

    private static int sortedOracle(int[] nums) {
        int[] sorted = nums.clone();
        Arrays.sort(sorted);
        if (sorted.length == 0) {
            return 0;
        }

        int best = 1;
        int length = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == sorted[i - 1]) {
                continue;
            }
            if ((long) sorted[i] == (long) sorted[i - 1] + 1L) {
                length++;
            } else {
                length = 1;
            }
            best = Math.max(best, length);
        }
        return best;
    }

    private static void check(int expected, int... nums) {
        int[] before = nums.clone();
        int actual = longestConsecutive(nums);
        assert actual == expected : "expected " + expected + " but got " + actual
                + " for " + Arrays.toString(nums);
        assert Arrays.equals(nums, before) : "input was mutated";
        scenarios++;
    }

    private static void expectRejected() {
        try {
            longestConsecutive(null);
            throw new AssertionError("null input should be rejected");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    private static int randomValue(Random random) {
        return switch (random.nextInt(12)) {
            case 0 -> Integer.MIN_VALUE;
            case 1 -> Integer.MIN_VALUE + 1;
            case 2 -> Integer.MAX_VALUE - 1;
            case 3 -> Integer.MAX_VALUE;
            default -> random.nextInt(41) - 20;
        };
    }

    public static void main(String[] args) {
        check(4, 100, 4, 200, 1, 3, 2);
        check(9, 0, 3, 7, 2, 5, 8, 4, 6, 0, 1);
        check(5, -2, -1, 0, 1, 2, 50);
        check(2, Integer.MIN_VALUE, Integer.MIN_VALUE + 1,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
        check(0);
        check(1, 7);
        check(1, 7, 7, 7);
        check(4, 4, 3, 2, 1);
        check(3, -3, -2, -1);
        check(1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        check(3, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 2);
        check(3, Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
        check(3, 10, 11, 12, 100, 101);
        check(4, 8, 6, 7, 5, 5, 8);
        check(4, -1, 5, 2, 4, 3, 0);
        check(2, 1, 2, 4, 5, 7);
        check(1, -10, 0, 10);
        check(5, 3, 1, 2, 5, 4, 3, 2);
        check(4, Integer.MAX_VALUE, 0, 2, 1, 3);
        expectRejected();

        Random random = new Random(0x18C0FFEE);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] nums = new int[random.nextInt(41)];
            for (int i = 0; i < nums.length; i++) {
                nums[i] = randomValue(random);
            }
            int[] before = nums.clone();
            int expected = sortedOracle(nums);
            int actual = longestConsecutive(nums);
            assert actual == expected : "oracle mismatch: expected " + expected
                    + " but got " + actual + " for " + Arrays.toString(nums);
            assert Arrays.equals(nums, before) : "random input was mutated";
        }

        System.out.println("Longest Consecutive Sequence contracts passed: "
                + scenarios + " scenario groups + 20000 seeded oracle comparisons");
    }
}
