import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Dependency-free executable contracts for the main two-pointer motion patterns. */
public final class TwoPointerPatternContracts {
    static List<List<Integer>> threeSum(int[] values) {
        requireArray(values, "values");
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        List<List<Integer>> answer = new ArrayList<>();
        for (int anchor = 0; anchor + 2 < sorted.length; anchor++) {
            if (anchor > 0 && sorted[anchor] == sorted[anchor - 1]) continue;
            int left = anchor + 1;
            int right = sorted.length - 1;
            while (left < right) {
                long sum = (long) sorted[anchor] + sorted[left] + sorted[right];
                if (sum < 0) {
                    left++;
                } else if (sum > 0) {
                    right--;
                } else {
                    answer.add(List.of(sorted[anchor], sorted[left], sorted[right]));
                    int leftValue = sorted[left];
                    int rightValue = sorted[right];
                    while (left < right && sorted[left] == leftValue) left++;
                    while (left < right && sorted[right] == rightValue) right--;
                }
            }
        }
        return answer;
    }

    static void moveZeroes(int[] values) {
        requireArray(values, "values");
        int write = 0;
        for (int value : values) {
            if (value != 0) values[write++] = value;
        }
        while (write < values.length) values[write++] = 0;
    }

    static int removeDuplicates(int[] sorted) {
        requireSorted(sorted);
        if (sorted.length == 0) return 0;
        int write = 1;
        for (int read = 1; read < sorted.length; read++) {
            if (sorted[read] != sorted[write - 1]) sorted[write++] = sorted[read];
        }
        return write;
    }

    static long[] sortedSquares(int[] sorted) {
        requireSorted(sorted);
        long[] answer = new long[sorted.length];
        int left = 0;
        int right = sorted.length - 1;
        for (int write = answer.length - 1; write >= 0; write--) {
            long leftSquare = (long) sorted[left] * sorted[left];
            long rightSquare = (long) sorted[right] * sorted[right];
            if (leftSquare > rightSquare) {
                answer[write] = leftSquare;
                left++;
            } else {
                answer[write] = rightSquare;
                right--;
            }
        }
        return answer;
    }

    private static void requireArray(int[] values, String name) {
        if (values == null) throw new IllegalArgumentException(name + " must be non-null");
    }

    private static void requireSorted(int[] values) {
        requireArray(values, "values");
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[i - 1]) {
                throw new IllegalArgumentException("values must be sorted nondecreasing");
            }
        }
    }

    private static List<List<Integer>> threeSumOracle(int[] values) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                for (int k = j + 1; k < values.length; k++) {
                    if ((long) values[i] + values[j] + values[k] == 0) {
                        int[] triple = {values[i], values[j], values[k]};
                        Arrays.sort(triple);
                        keys.add(triple[0] + "," + triple[1] + "," + triple[2]);
                    }
                }
            }
        }
        List<List<Integer>> answer = new ArrayList<>();
        keys.forEach(key -> {
            String[] parts = key.split(",");
            answer.add(List.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])));
        });
        answer.sort((a, b) -> {
            for (int i = 0; i < 3; i++) {
                int comparison = Integer.compare(a.get(i), b.get(i));
                if (comparison != 0) return comparison;
            }
            return 0;
        });
        return answer;
    }

    private static int[] moveZeroesOracle(int[] values) {
        int[] answer = new int[values.length];
        int write = 0;
        for (int value : values) if (value != 0) answer[write++] = value;
        return answer;
    }

    private static int[] uniqueOracle(int[] sorted) {
        if (sorted.length == 0) return new int[0];
        int[] buffer = new int[sorted.length];
        int size = 1;
        buffer[0] = sorted[0];
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] != buffer[size - 1]) buffer[size++] = sorted[i];
        }
        return Arrays.copyOf(buffer, size);
    }

    private static long[] squaresOracle(int[] sorted) {
        long[] answer = new long[sorted.length];
        for (int i = 0; i < sorted.length; i++) answer[i] = (long) sorted[i] * sorted[i];
        Arrays.sort(answer);
        return answer;
    }

    private static int deterministicGroups() {
        checkThreeSum(new int[]{-1, 0, 1, 2, -1, -4},
                List.of(List.of(-1, -1, 2), List.of(-1, 0, 1)));
        checkThreeSum(new int[]{0, 1, 1}, List.of());
        checkThreeSum(new int[]{0, 0, 0}, List.of(List.of(0, 0, 0)));
        checkThreeSum(new int[]{-2, 0, 0, 2, 2}, List.of(List.of(-2, 0, 2)));
        checkThreeSum(new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 1},
                List.of(List.of(Integer.MIN_VALUE, 1, Integer.MAX_VALUE)));

        checkMove(new int[]{0, 1, 0, 3, 12}, new int[]{1, 3, 12, 0, 0});
        checkMove(new int[]{0}, new int[]{0});
        checkMove(new int[]{1, 2, 3}, new int[]{1, 2, 3});
        checkMove(new int[]{0, 0, 0}, new int[]{0, 0, 0});
        checkMove(new int[]{0, -1, 0, -1}, new int[]{-1, -1, 0, 0});

        checkUnique(new int[]{1, 1, 2}, new int[]{1, 2});
        checkUnique(new int[]{0, 0, 1, 1, 1, 2, 2, 3, 3, 4}, new int[]{0, 1, 2, 3, 4});
        checkUnique(new int[]{}, new int[]{});
        checkUnique(new int[]{7}, new int[]{7});
        checkUnique(new int[]{-2, -2, -2, 4, 4}, new int[]{-2, 4});

        checkSquares(new int[]{-4, -1, 0, 3, 10}, new long[]{0, 1, 9, 16, 100});
        checkSquares(new int[]{-7, -3, 2, 3, 11}, new long[]{4, 9, 9, 49, 121});
        checkSquares(new int[]{}, new long[]{});
        checkSquares(new int[]{-2}, new long[]{4});
        checkSquares(new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE},
                new long[]{0, 4_611_686_014_132_420_609L, 4_611_686_018_427_387_904L});

        expectFailure(() -> threeSum(null));
        expectFailure(() -> moveZeroes(null));
        expectFailure(() -> removeDuplicates(new int[]{2, 1}));
        expectFailure(() -> sortedSquares(new int[]{0, -1}));
        return 20;
    }

    private static long randomizedChecks() {
        Random random = new Random(0x2B017E2L);
        long checks = 0;
        for (int trial = 0; trial < 20_000; trial++) {
            int[] raw = new int[random.nextInt(10)];
            for (int i = 0; i < raw.length; i++) raw[i] = random.nextInt(11) - 5;
            int[] original = raw.clone();
            List<List<Integer>> actualTriples = new ArrayList<>(threeSum(raw));
            actualTriples.sort((a, b) -> {
                for (int i = 0; i < 3; i++) {
                    int comparison = Integer.compare(a.get(i), b.get(i));
                    if (comparison != 0) return comparison;
                }
                return 0;
            });
            require(actualTriples.equals(threeSumOracle(raw)), "3Sum oracle mismatch");
            checks++;
            require(Arrays.equals(raw, original), "3Sum mutated its caller input");
            checks++;

            int[] moving = new int[random.nextInt(15)];
            for (int i = 0; i < moving.length; i++) moving[i] = random.nextInt(5) - 2;
            int[] expectedMove = moveZeroesOracle(moving);
            moveZeroes(moving);
            require(Arrays.equals(moving, expectedMove), "move-zeroes oracle mismatch");
            checks++;

            int[] sorted = new int[random.nextInt(15)];
            for (int i = 0; i < sorted.length; i++) sorted[i] = random.nextInt(13) - 6;
            Arrays.sort(sorted);
            int[] unique = uniqueOracle(sorted);
            int[] compacting = sorted.clone();
            int size = removeDuplicates(compacting);
            require(size == unique.length
                    && Arrays.equals(Arrays.copyOf(compacting, size), unique),
                    "remove-duplicates oracle mismatch");
            checks++;

            int[] preserved = sorted.clone();
            require(Arrays.equals(sortedSquares(sorted), squaresOracle(sorted))
                    && Arrays.equals(sorted, preserved), "sorted-squares oracle mismatch");
            checks++;
        }
        return checks;
    }

    private static void checkThreeSum(int[] input, List<List<Integer>> expected) {
        int[] original = input.clone();
        require(threeSum(input).equals(expected), "unexpected 3Sum result");
        require(Arrays.equals(input, original), "3Sum must preserve input");
    }

    private static void checkMove(int[] input, int[] expected) {
        moveZeroes(input);
        require(Arrays.equals(input, expected), "unexpected move-zeroes result");
    }

    private static void checkUnique(int[] input, int[] expectedPrefix) {
        int size = removeDuplicates(input);
        require(size == expectedPrefix.length
                && Arrays.equals(Arrays.copyOf(input, size), expectedPrefix),
                "unexpected unique prefix");
    }

    private static void checkSquares(int[] input, long[] expected) {
        int[] original = input.clone();
        require(Arrays.equals(sortedSquares(input), expected), "unexpected sorted squares");
        require(Arrays.equals(input, original), "sorted squares must preserve input");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int groups = deterministicGroups();
        long checks = randomizedChecks();
        System.out.printf("TwoPointerPatternContracts: %d scenario groups + 20000 seeded oracle trials passed; checks=%d%n",
                groups, checks);
    }
}
