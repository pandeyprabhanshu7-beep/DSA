import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * Dependency-free contracts for the guide's sorted-squares implementation.
 * Run with: java -ea study-guides/striver-dsa/arrays/SortedSquaresContracts.java
 */
public final class SortedSquaresContracts {
    private static int scenarios;

    public static void main(String[] args) {
        explicitContracts();
        randomizedOracle();
        System.out.println("PASS SortedSquaresContracts scenarios=" + scenarios
                + " randomized=20000");
        System.out.println("PARITY|MIXED|NEGATIVE|DUPLICATES|LONG|SORTED|IMMUTABLE|REJECTED");
    }

    static long[] sortedSquares(int[] values) {
        Objects.requireNonNull(values, "values");
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] > values[i]) {
                throw new IllegalArgumentException("input must be sorted");
            }
        }

        long[] output = new long[values.length];
        int left = 0;
        int right = values.length - 1;
        for (int write = output.length - 1; write >= 0; write--) {
            long leftSquare = square(values[left]);
            long rightSquare = square(values[right]);
            if (leftSquare > rightSquare) {
                output[write] = leftSquare;
                left++;
            } else {
                output[write] = rightSquare;
                right--;
            }
        }
        return output;
    }

    private static long square(int value) {
        return (long) value * value;
    }

    private static void explicitContracts() {
        check(new int[]{-4, -1, 0, 3, 10}, new long[]{0, 1, 9, 16, 100});
        check(new int[]{-7, -3, -1}, new long[]{1, 9, 49});
        check(new int[]{0, 2, 3}, new long[]{0, 4, 9});
        check(new int[]{-3, -2, 2, 3}, new long[]{4, 4, 9, 9});
        check(new int[]{-1, 1}, new long[]{1, 1});
        check(new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE},
                new long[]{0L, 4_611_686_014_132_420_609L, 4_611_686_018_427_387_904L});
        check(new int[0], new long[0]);
        check(new int[]{0}, new long[]{0});
        check(new int[]{Integer.MIN_VALUE},
                new long[]{4_611_686_018_427_387_904L});

        expect(NullPointerException.class, () -> sortedSquares(null));
        expect(IllegalArgumentException.class, () -> sortedSquares(new int[]{0, -1}));
        scenarios += 2;

        int[] input = {-2, 1};
        int[] snapshot = input.clone();
        long[] first = sortedSquares(input);
        require(Arrays.equals(input, snapshot), "input must remain unchanged");
        require(first != sortedSquares(input), "each call returns a new array");
        scenarios += 2;

        long[] extremes = sortedSquares(
                new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
        require(extremes[0] >= 0 && extremes[0] <= extremes[1],
                "extreme squares must remain nonnegative and ordered");
        scenarios++;
    }

    private static void randomizedOracle() {
        Random random = new Random(977_2026L);
        for (int round = 0; round < 20_000; round++) {
            int length = random.nextInt(65);
            int[] input = new int[length];
            for (int i = 0; i < length; i++) input[i] = random.nextInt();
            Arrays.sort(input);
            int[] snapshot = input.clone();

            long[] expected = new long[length];
            for (int i = 0; i < length; i++) expected[i] = square(input[i]);
            Arrays.sort(expected);

            long[] actual = sortedSquares(input);
            require(Arrays.equals(expected, actual), "oracle mismatch");
            require(Arrays.equals(snapshot, input), "random input mutated");
            require(actual.length == input.length, "length changed");
            for (int i = 0; i < actual.length; i++) {
                require(actual[i] >= 0, "negative square");
                if (i > 0) require(actual[i - 1] <= actual[i], "not sorted");
            }
        }
    }

    private static void check(int[] input, long[] expected) {
        int[] snapshot = input.clone();
        require(Arrays.equals(expected, sortedSquares(input)), "explicit mismatch");
        require(Arrays.equals(snapshot, input), "explicit input mutated");
        scenarios++;
    }

    private static void expect(
            Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            require(type.isInstance(error), "wrong exception: " + error);
            return;
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
