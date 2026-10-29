import java.util.Arrays;
import java.util.Random;

public class NextPermutationContracts {
    private record Metrics(int pivot, int successor, int swaps) {}

    private static final class OracleState {
        int[] smallest;
        int[] bestGreater;
    }

    private static int scenarios;

    static void nextPermutation(int[] nums) {
        nextPermutationMeasured(nums);
    }

    static Metrics nextPermutationMeasured(int[] nums) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        if (nums.length < 2) {
            return new Metrics(-1, -1, 0);
        }

        int pivot = nums.length - 2;
        while (pivot >= 0 && nums[pivot] >= nums[pivot + 1]) {
            pivot--;
        }

        int successor = -1;
        int swaps = 0;
        if (pivot >= 0) {
            successor = nums.length - 1;
            while (nums[successor] <= nums[pivot]) {
                successor--;
            }
            swap(nums, pivot, successor);
            swaps++;
        }

        int left = pivot + 1;
        int right = nums.length - 1;
        while (left < right) {
            swap(nums, left, right);
            left++;
            right--;
            swaps++;
        }
        return new Metrics(pivot, successor, swaps);
    }

    private static void swap(int[] nums, int left, int right) {
        int temporary = nums[left];
        nums[left] = nums[right];
        nums[right] = temporary;
    }

    private static int compareLexicographically(int[] left, int[] right) {
        for (int i = 0; i < left.length; i++) {
            int comparison = Integer.compare(left[i], right[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int[] exhaustiveOracle(int[] input) {
        if (input.length < 2) {
            return input.clone();
        }
        OracleState state = new OracleState();
        int[] permutation = input.clone();
        enumerate(permutation, 0, input, state);
        return state.bestGreater == null ? state.smallest : state.bestGreater;
    }

    private static void enumerate(int[] permutation, int fixed, int[] input,
                                  OracleState state) {
        if (fixed == permutation.length) {
            if (state.smallest == null
                    || compareLexicographically(permutation, state.smallest) < 0) {
                state.smallest = permutation.clone();
            }
            if (compareLexicographically(permutation, input) > 0
                    && (state.bestGreater == null
                    || compareLexicographically(permutation, state.bestGreater) < 0)) {
                state.bestGreater = permutation.clone();
            }
            return;
        }
        for (int candidate = fixed; candidate < permutation.length; candidate++) {
            boolean duplicateAtDepth = false;
            for (int prior = fixed; prior < candidate; prior++) {
                if (permutation[prior] == permutation[candidate]) {
                    duplicateAtDepth = true;
                    break;
                }
            }
            if (duplicateAtDepth) {
                continue;
            }
            swap(permutation, fixed, candidate);
            enumerate(permutation, fixed + 1, input, state);
            swap(permutation, fixed, candidate);
        }
    }

    private static void check(int[] input, int... expected) {
        int[] actual = input.clone();
        int[] sameReference = actual;
        Metrics metrics = nextPermutationMeasured(actual);

        assert actual == sameReference : "implementation replaced the caller array";
        assert Arrays.equals(actual, expected)
                : "expected " + Arrays.toString(expected) + " but got "
                + Arrays.toString(actual) + " for " + Arrays.toString(input);
        assert Arrays.equals(actual, exhaustiveOracle(input)) : "oracle disagreement";
        assert sameMultiset(input, actual) : "component multiplicities changed";
        assert metrics.swaps() <= (input.length / 2) + 1 : "swap bound exceeded";
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
            nextPermutation(null);
            throw new AssertionError("null input should be rejected");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    public static void main(String[] args) {
        check(new int[] {1, 2, 3}, 1, 3, 2);
        check(new int[] {3, 2, 1}, 1, 2, 3);
        check(new int[] {1, 1, 5}, 1, 5, 1);
        check(new int[] {1, 4, 3, 2}, 2, 1, 3, 4);
        check(new int[] {});
        check(new int[] {42}, 42);
        check(new int[] {1, 2}, 2, 1);
        check(new int[] {2, 1}, 1, 2);
        check(new int[] {7, 7, 7}, 7, 7, 7);
        check(new int[] {1, 3, 2}, 2, 1, 3);
        check(new int[] {2, 3, 1}, 3, 1, 2);
        check(new int[] {-1, 0, -2}, 0, -2, -1);
        check(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE},
                Integer.MAX_VALUE, Integer.MIN_VALUE);
        check(new int[] {1, 2, 2, 3}, 1, 2, 3, 2);
        check(new int[] {3, 1, 3, 2}, 3, 2, 1, 3);
        check(new int[] {0, 2, 1, 0}, 1, 0, 0, 2);
        check(new int[] {2, 2, 0, 1}, 2, 2, 1, 0);
        check(new int[] {4, 1, 3, 3, 2}, 4, 2, 1, 3, 3);
        check(new int[] {1, 0, 1}, 1, 1, 0);
        expectNullRejected();

        Random random = new Random(0x21A77A25L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] input = new int[random.nextInt(7)];
            for (int i = 0; i < input.length; i++) {
                input[i] = random.nextInt(7) - 3;
            }
            int[] expected = exhaustiveOracle(input);
            int[] actual = input.clone();
            Metrics metrics = nextPermutationMeasured(actual);

            assert Arrays.equals(actual, expected)
                    : "random oracle mismatch for " + Arrays.toString(input);
            assert sameMultiset(input, actual) : "random multiplicities changed";
            assert metrics.swaps() <= (input.length / 2) + 1
                    : "random swap bound exceeded";
        }

        int[] trace = {1, 4, 3, 2};
        Metrics traceMetrics = nextPermutationMeasured(trace);
        assert traceMetrics.pivot() == 0 : "trace pivot should be index 0";
        assert traceMetrics.successor() == 3 : "trace successor should be index 3";

        System.out.println("Next Permutation contracts passed: " + scenarios
                + " scenario groups + 20000 seeded exhaustive-permutation oracle comparisons");
    }
}
