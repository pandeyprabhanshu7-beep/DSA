import java.util.Arrays;
import java.util.Random;

public class RotateArrayContracts {
    private record Metrics(int normalizedShift, int swaps) {}

    private static int scenarios;

    static void rotateRight(int[] nums, int k) {
        rotateRightMeasured(nums, k);
    }

    static Metrics rotateRightMeasured(int[] nums, int k) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        if (k < 0) {
            throw new IllegalArgumentException("k must be non-negative");
        }
        if (nums.length < 2) {
            return new Metrics(0, 0);
        }

        int shift = k % nums.length;
        if (shift == 0) {
            return new Metrics(0, 0);
        }

        int swaps = 0;
        swaps += reverse(nums, 0, nums.length - 1);
        swaps += reverse(nums, 0, shift - 1);
        swaps += reverse(nums, shift, nums.length - 1);
        return new Metrics(shift, swaps);
    }

    private static int reverse(int[] nums, int left, int right) {
        int swaps = 0;
        while (left < right) {
            int temporary = nums[left];
            nums[left] = nums[right];
            nums[right] = temporary;
            left++;
            right--;
            swaps++;
        }
        return swaps;
    }

    private static int[] oracle(int[] input, int k) {
        if (input.length == 0) {
            return new int[0];
        }
        int shift = k % input.length;
        int[] expected = new int[input.length];
        for (int i = 0; i < input.length; i++) {
            int destination = (int) (((long) i + shift) % input.length);
            expected[destination] = input[i];
        }
        return expected;
    }

    private static void check(int[] input, int k, int... expected) {
        int[] actual = input.clone();
        int[] sameReference = actual;
        Metrics metrics = rotateRightMeasured(actual, k);

        assert actual == sameReference : "rotation replaced the caller array";
        assert Arrays.equals(actual, expected)
                : "expected " + Arrays.toString(expected) + " but got "
                + Arrays.toString(actual) + " for " + Arrays.toString(input)
                + " and k=" + k;
        int expectedShift = input.length < 2 ? 0 : k % input.length;
        assert metrics.normalizedShift() == expectedShift : "wrong normalized shift";
        assert metrics.swaps() <= input.length : "at-most-n swap bound exceeded";
        assert sameMultiset(input, actual) : "rotation changed component multiplicities";
        scenarios++;
    }

    private static boolean sameMultiset(int[] left, int[] right) {
        int[] sortedLeft = left.clone();
        int[] sortedRight = right.clone();
        Arrays.sort(sortedLeft);
        Arrays.sort(sortedRight);
        return Arrays.equals(sortedLeft, sortedRight);
    }

    private static void expectNullRejected() {
        try {
            rotateRight(null, 3);
            throw new AssertionError("null input should be rejected");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    private static void expectNegativeRejectedWithoutMutation(int... input) {
        int[] before = input.clone();
        try {
            rotateRight(input, -1);
            throw new AssertionError("negative k should be rejected");
        } catch (IllegalArgumentException expected) {
            assert Arrays.equals(input, before) : "validation failure mutated input";
            scenarios++;
        }
    }

    public static void main(String[] args) {
        check(new int[] {1, 2, 3, 4, 5, 6, 7}, 3, 5, 6, 7, 1, 2, 3, 4);
        check(new int[] {-1, -100, 3, 99}, 2, 3, 99, -1, -100);
        check(new int[] {1, 2, 3, 4, 5}, 7, 4, 5, 1, 2, 3);
        check(new int[] {}, 9);
        check(new int[] {42}, Integer.MAX_VALUE, 42);
        check(new int[] {1, 2, 3}, 0, 1, 2, 3);
        check(new int[] {1, 2, 3}, 3, 1, 2, 3);
        check(new int[] {1, 2, 3}, 6, 1, 2, 3);
        check(new int[] {1, 2, 3}, 1, 3, 1, 2);
        check(new int[] {1, 2, 3}, 2, 2, 3, 1);
        check(new int[] {1, 2}, 1, 2, 1);
        check(new int[] {7, 7, 7, 7}, 3, 7, 7, 7, 7);
        check(new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}, 1,
                Integer.MAX_VALUE, Integer.MIN_VALUE, 0);
        check(new int[] {5, -5, 5, -5, 0}, 4, -5, 5, -5, 0, 5);
        check(new int[] {9, 8, 7, 6}, Integer.MAX_VALUE, 8, 7, 6, 9);
        check(new int[] {0, 1, 2, 3, 4, 5}, 4, 2, 3, 4, 5, 0, 1);
        check(new int[] {-3, -2, -1}, 5, -2, -1, -3);
        check(new int[] {10, 20, 30, 40, 50, 60, 70, 80}, 5,
                40, 50, 60, 70, 80, 10, 20, 30);
        expectNullRejected();
        expectNegativeRejectedWithoutMutation(1, 2, 3);

        Random random = new Random(0x20A77A25L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] input = new int[random.nextInt(101)];
            for (int i = 0; i < input.length; i++) {
                input[i] = random.nextInt();
            }
            int k = random.nextInt(Integer.MAX_VALUE);
            int[] expected = oracle(input, k);
            int[] actual = input.clone();
            Metrics metrics = rotateRightMeasured(actual, k);

            assert Arrays.equals(actual, expected)
                    : "oracle mismatch for " + Arrays.toString(input) + " and k=" + k;
            int expectedShift = input.length < 2 ? 0 : k % input.length;
            assert metrics.normalizedShift() == expectedShift
                    : "random normalized shift mismatch";
            assert metrics.swaps() <= input.length : "random swap bound exceeded";
            assert sameMultiset(input, actual) : "random multiplicities changed";
        }

        System.out.println("Rotate Array contracts passed: " + scenarios
                + " scenario groups + 20000 seeded destination-index oracle comparisons");
    }
}
