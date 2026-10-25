import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/** Dependency-free executable evidence for Section 4, problem 8: 3Sum. */
public final class ThreeSumContracts {
    record Triplet(int first, int second, int third)
            implements Comparable<Triplet> {
        Triplet {
            if (first > second || second > third) {
                throw new IllegalArgumentException("triplet must be sorted");
            }
        }

        @Override
        public int compareTo(Triplet other) {
            int firstOrder = Integer.compare(first, other.first);
            if (firstOrder != 0) return firstOrder;
            int secondOrder = Integer.compare(second, other.second);
            return secondOrder != 0
                    ? secondOrder
                    : Integer.compare(third, other.third);
        }
    }

    static List<Triplet> threeSum(int[] input) {
        Objects.requireNonNull(input, "input");
        int[] values = input.clone();
        Arrays.sort(values);
        List<Triplet> result = new ArrayList<>();

        for (int anchor = 0; anchor + 2 < values.length; anchor++) {
            if (anchor > 0 && values[anchor] == values[anchor - 1]) continue;
            if (values[anchor] > 0) break;

            long smallest = (long) values[anchor]
                    + values[anchor + 1] + values[anchor + 2];
            if (smallest > 0L) break;
            long largest = (long) values[anchor]
                    + values[values.length - 2] + values[values.length - 1];
            if (largest < 0L) continue;

            int left = anchor + 1;
            int right = values.length - 1;
            while (left < right) {
                long sum = (long) values[anchor] + values[left] + values[right];
                if (sum < 0L) {
                    left++;
                } else if (sum > 0L) {
                    right--;
                } else {
                    result.add(new Triplet(
                            values[anchor], values[left], values[right]));
                    int leftValue = values[left];
                    int rightValue = values[right];
                    while (left < right && values[left] == leftValue) left++;
                    while (left < right && values[right] == rightValue) right--;
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<Triplet> cubicOracle(int[] input) {
        Set<Triplet> expected = new TreeSet<>();
        for (int first = 0; first < input.length; first++) {
            for (int second = first + 1; second < input.length; second++) {
                for (int third = second + 1; third < input.length; third++) {
                    if ((long) input[first] + input[second] + input[third] != 0L) {
                        continue;
                    }
                    int[] values = {input[first], input[second], input[third]};
                    Arrays.sort(values);
                    expected.add(new Triplet(values[0], values[1], values[2]));
                }
            }
        }
        return List.copyOf(expected);
    }

    private static int scenarios;

    private static void check(int[] input, List<Triplet> expected) {
        int[] snapshot = input.clone();
        List<Triplet> actual = threeSum(input);
        require(actual.equals(expected), "expected " + expected + " but got " + actual);
        require(Arrays.equals(input, snapshot), "input was mutated");
        require(new TreeSet<>(actual).size() == actual.size(), "duplicate output");
        for (int index = 1; index < actual.size(); index++) {
            require(actual.get(index - 1).compareTo(actual.get(index)) < 0,
                    "output is not strict lexicographic order");
        }
        scenarios++;
    }

    private static void explicitContracts() {
        check(new int[]{-1, 0, 1, 2, -1, -4}, List.of(
                new Triplet(-1, -1, 2),
                new Triplet(-1, 0, 1)));
        check(new int[]{0, 0, 0, 0}, List.of(new Triplet(0, 0, 0)));
        check(new int[]{-2, 0, 0, 2, 2}, List.of(new Triplet(-2, 0, 2)));
        check(new int[]{1, 2, 3}, List.of());
        check(new int[]{}, List.of());
        check(new int[]{0, 0}, List.of());
        check(new int[]{
                Integer.MIN_VALUE, Integer.MIN_VALUE, 0}, List.of());
        check(new int[]{
                Integer.MIN_VALUE, 1, Integer.MAX_VALUE}, List.of(
                new Triplet(Integer.MIN_VALUE, 1, Integer.MAX_VALUE)));
        check(new int[]{-4, -2, -2, -2, 0, 1, 2, 2, 3, 4}, List.of(
                new Triplet(-4, 0, 4),
                new Triplet(-4, 1, 3),
                new Triplet(-4, 2, 2),
                new Triplet(-2, -2, 4),
                new Triplet(-2, 0, 2)));

        try {
            threeSum(null);
            throw new AssertionError("expected NullPointerException");
        } catch (NullPointerException expected) {
            scenarios++;
        }
    }

    private static void randomizedOracle() {
        Random random = new Random(0x3_5A_2026L);
        for (int round = 0; round < 20_000; round++) {
            int[] input = new int[random.nextInt(13)];
            for (int index = 0; index < input.length; index++) {
                input[index] = random.nextInt(21) - 10;
            }
            if (input.length > 0 && round % 97 == 0) {
                input[random.nextInt(input.length)] = Integer.MIN_VALUE;
            }
            if (input.length > 0 && round % 101 == 0) {
                input[random.nextInt(input.length)] = Integer.MAX_VALUE;
            }
            int[] snapshot = input.clone();
            List<Triplet> actual = threeSum(input);
            List<Triplet> expected = cubicOracle(input);
            require(actual.equals(expected),
                    "oracle mismatch " + Arrays.toString(input)
                            + ": expected " + expected + " but got " + actual);
            require(Arrays.equals(input, snapshot), "random input was mutated");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        explicitContracts();
        randomizedOracle();
        System.out.println("ThreeSumContracts: " + scenarios
                + " explicit scenario groups + 20,000 randomized cubic-oracle comparisons passed");
        System.out.println("PARITY|TRIPLETS=-1,-1,2;-1,0,1"
                + "|UNIQUE=true|IMMUTABLE=true|WIDE_SUM=true");
    }
}
