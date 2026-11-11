import java.util.Arrays;
import java.util.Random;
import java.util.TreeSet;

/** Dependency-free executable evidence for Arrays problem 42. */
public final class TwoSumSortedContracts {
    private static int groups;
    private static long randomizedChecks;

    private TwoSumSortedContracts() {}

    /**
     * Trusted core: numbers is nondecreasing and has exactly one distinct-index
     * solution. Returns the official one-based indices without mutating input.
     */
    static int[] twoSumSorted(int[] numbers, int target) {
        requireUsable(numbers);
        int left = 0;
        int right = numbers.length - 1;
        while (left < right) {
            long sum = (long) numbers[left] + numbers[right];
            if (sum == target) return new int[] {left + 1, right + 1};
            if (sum < target) left++;
            else right--;
        }
        throw new IllegalArgumentException("no distinct-index pair sums to target");
    }

    /** Defensive boundary that also proves sortedness and exactly one index pair. */
    static int[] twoSumSortedValidated(int[] numbers, int target) {
        requireUsable(numbers);
        for (int i = 1; i < numbers.length; i++) {
            if (numbers[i] < numbers[i - 1]) {
                throw new IllegalArgumentException("numbers must be nondecreasing");
            }
        }
        if (countSolutionsUpToTwo(numbers, target) != 1) {
            throw new IllegalArgumentException("expected exactly one distinct-index solution");
        }
        return twoSumSorted(numbers, target);
    }

    private static int countSolutionsUpToTwo(int[] numbers, int target) {
        int left = 0;
        int right = numbers.length - 1;
        int solutions = 0;
        while (left < right) {
            long sum = (long) numbers[left] + numbers[right];
            if (sum < target) {
                left++;
            } else if (sum > target) {
                right--;
            } else if (numbers[left] == numbers[right]) {
                return right - left == 1 && solutions == 0 ? 1 : 2;
            } else {
                int leftValue = numbers[left];
                int rightValue = numbers[right];
                int leftCount = 0;
                int rightCount = 0;
                while (left <= right && numbers[left] == leftValue) {
                    left++;
                    leftCount++;
                }
                while (right >= left && numbers[right] == rightValue) {
                    right--;
                    rightCount++;
                }
                if ((long) leftCount * rightCount > 1 || ++solutions > 1) return 2;
            }
        }
        return solutions;
    }

    private static void requireUsable(int[] numbers) {
        if (numbers == null || numbers.length < 2) {
            throw new IllegalArgumentException("numbers must contain at least two values");
        }
    }

    /** Independent direct-definition oracle; it intentionally uses no pointers. */
    static int[] quadraticUniqueOracle(int[] numbers, int target) {
        int[] answer = null;
        for (int i = 0; i < numbers.length; i++) {
            for (int j = i + 1; j < numbers.length; j++) {
                if ((long) numbers[i] + numbers[j] == target) {
                    if (answer != null) {
                        throw new IllegalArgumentException("oracle found multiple solutions");
                    }
                    answer = new int[] {i + 1, j + 1};
                }
            }
        }
        if (answer == null) throw new IllegalArgumentException("oracle found no solution");
        return answer;
    }

    public static void main(String[] args) {
        deterministicScenarios();
        randomizedOracleChecks();
        System.out.println("TwoSumSortedContracts: " + groups
                + " scenario groups + 20000 seeded quadratic-oracle trials passed; checks="
                + randomizedChecks);
    }

    private static void deterministicScenarios() {
        group("official example one", () -> eq(new int[] {1, 2},
                twoSumSorted(new int[] {2, 7, 11, 15}, 9)));
        group("official example two", () -> eq(new int[] {1, 3},
                twoSumSorted(new int[] {2, 3, 4}, 6)));
        group("official example three", () -> eq(new int[] {1, 2},
                twoSumSorted(new int[] {-1, 0}, -1)));
        group("duplicate values use distinct indices", () -> eq(new int[] {1, 2},
                twoSumSortedValidated(new int[] {1, 1, 3, 4}, 2)));
        group("mixed signs", () -> eq(new int[] {2, 4},
                twoSumSortedValidated(new int[] {-10, -3, 0, 4, 9}, 1)));
        group("all negative", () -> eq(new int[] {2, 3},
                twoSumSortedValidated(new int[] {-10, -6, -3, -1}, -9)));
        group("two zeroes", () -> eq(new int[] {2, 3},
                twoSumSortedValidated(new int[] {-1, 0, 0, 2}, 0)));
        group("widen before positive overflow", () -> eq(new int[] {1, 2},
                twoSumSortedValidated(
                        new int[] {1, 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}, 3)));
        group("full-width endpoints", () -> eq(new int[] {1, 3},
                twoSumSortedValidated(
                        new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}, -1)));
        group("middle pair", () -> eq(new int[] {3, 4},
                twoSumSortedValidated(new int[] {1, 3, 4, 5, 7}, 9)));
        group("endpoint pair", () -> eq(new int[] {1, 4},
                twoSumSortedValidated(new int[] {1, 4, 6, 10}, 11)));
        group("input preservation", () -> {
            int[] input = {-5, -2, 1, 4, 8};
            int[] before = input.clone();
            twoSumSorted(input, 6);
            eq(before, input);
        });
        group("validated and core parity", () -> {
            int[] input = {-8, -1, 3, 6, 11};
            eq(twoSumSorted(input, 5), twoSumSortedValidated(input, 5));
        });
        group("unsorted boundary rejection", () -> expectIllegal(
                () -> twoSumSortedValidated(new int[] {2, 1, 3}, 4)));
        group("absent pair rejection", () -> expectIllegal(
                () -> twoSumSortedValidated(new int[] {1, 2, 3}, 99)));
        group("multiple pair rejection", () -> expectIllegal(
                () -> twoSumSortedValidated(new int[] {1, 1, 2, 2}, 3)));
        group("null rejection", () -> expectIllegal(() -> twoSumSorted(null, 0)));
        group("empty rejection", () -> expectIllegal(() -> twoSumSorted(new int[0], 0)));
        group("singleton rejection", () -> expectIllegal(() -> twoSumSorted(new int[] {5}, 10)));
        group("independent deterministic oracle", () -> {
            int[] input = {-5, -2, 1, 4, 8};
            eq(quadraticUniqueOracle(input, 6), twoSumSortedValidated(input, 6));
        });
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x1672026L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] input;
            int target;
            int[] expected;
            while (true) {
                int n = 2 + random.nextInt(15);
                TreeSet<Integer> values = new TreeSet<>();
                while (values.size() < n) values.add(random.nextInt(4001) - 2000);
                input = values.stream().mapToInt(Integer::intValue).toArray();
                int first = random.nextInt(n - 1);
                int second = first + 1 + random.nextInt(n - first - 1);
                target = input[first] + input[second];
                try {
                    expected = quadraticUniqueOracle(input, target);
                    break;
                } catch (IllegalArgumentException multiple) {
                    // Retry until the direct definition confirms the official promise.
                }
            }

            int[] before = input.clone();
            int[] actual = twoSumSorted(input, target);
            check(Arrays.equals(expected, actual), "oracle mismatch");
            check(Arrays.equals(before, input), "input mutated");
            check(Arrays.equals(actual, twoSumSortedValidated(input, target)),
                    "validated/core mismatch");
            check(actual[0] < actual[1]
                            && (long) input[actual[0] - 1] + input[actual[1] - 1] == target,
                    "invalid one-based pair");

            int[] transformed = Arrays.stream(input).map(value -> 2 * value + 1).toArray();
            int transformedTarget = 2 * target + 2;
            check(Arrays.equals(expected, twoSumSortedValidated(transformed, transformedTarget)),
                    "order-preserving affine transform changed indices");
        }
    }

    private static void group(String name, Runnable scenario) {
        scenario.run();
        groups++;
    }

    private static void eq(int[] expected, int[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("expected=" + Arrays.toString(expected)
                    + " actual=" + Arrays.toString(actual));
        }
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        randomizedChecks++;
        if (!condition) throw new AssertionError(message);
    }
}
