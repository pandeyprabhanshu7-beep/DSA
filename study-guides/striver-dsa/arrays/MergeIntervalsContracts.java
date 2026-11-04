import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Dependency-free executable contracts for Merge Intervals. */
public final class MergeIntervalsContracts {
    private static final Comparator<int[]> BY_START_THEN_END = Comparator
            .comparingInt((int[] row) -> row[0])
            .thenComparingInt(row -> row[1]);

    private static int groups;
    private static long checks;

    static int[][] mergeIntervals(int[][] intervals) {
        int[][] sorted = validatedCopy(intervals);
        Arrays.sort(sorted, BY_START_THEN_END);

        int[][] merged = new int[sorted.length][2];
        int size = 0;
        for (int[] current : sorted) {
            if (size == 0 || current[0] > merged[size - 1][1]) {
                merged[size][0] = current[0];
                merged[size][1] = current[1];
                size++;
            } else {
                merged[size - 1][1] = Math.max(merged[size - 1][1], current[1]);
            }
        }
        return Arrays.copyOf(merged, size);
    }

    private static int[][] validatedCopy(int[][] intervals) {
        if (intervals == null) {
            throw new IllegalArgumentException("intervals must not be null");
        }
        int[][] copy = new int[intervals.length][2];
        for (int index = 0; index < intervals.length; index++) {
            int[] row = intervals[index];
            if (row == null || row.length != 2 || row[0] > row[1]) {
                throw new IllegalArgumentException("each row must be [start,end]");
            }
            copy[index][0] = row[0];
            copy[index][1] = row[1];
        }
        return copy;
    }

    /** Independent quadratic fixed-point oracle: repeatedly union any overlapping pair. */
    private static int[][] repeatedPairOracle(int[][] intervals) {
        int[][] copy = validatedCopy(intervals);
        List<int[]> components = new ArrayList<>();
        for (int[] row : copy) {
            components.add(row);
        }

        boolean changed;
        do {
            changed = false;
            outer:
            for (int left = 0; left < components.size(); left++) {
                for (int right = left + 1; right < components.size(); right++) {
                    int[] a = components.get(left);
                    int[] b = components.get(right);
                    if (a[0] <= b[1] && b[0] <= a[1]) {
                        components.set(left, new int[] {Math.min(a[0], b[0]), Math.max(a[1], b[1])});
                        components.remove(right);
                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed);

        components.sort(BY_START_THEN_END);
        return components.toArray(new int[0][]);
    }

    private static boolean isCanonical(int[][] intervals) {
        for (int index = 0; index < intervals.length; index++) {
            int[] row = intervals[index];
            if (row == null || row.length != 2 || row[0] > row[1]) {
                return false;
            }
            if (index > 0 && intervals[index - 1][1] >= row[0]) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameIntegerPointCoverage(int[][] input, int[][] output, int minimum, int maximum) {
        for (int point = minimum; point <= maximum; point++) {
            if (covers(input, point) != covers(output, point)) {
                return false;
            }
        }
        return true;
    }

    private static boolean covers(int[][] intervals, int point) {
        for (int[] row : intervals) {
            if (row[0] <= point && point <= row[1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownsFreshRows(int[][] input, int[][] output) {
        for (int[] out : output) {
            for (int[] in : input) {
                if (out == in) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void group(String name, boolean condition) {
        groups++;
        checks++;
        if (!condition) {
            throw new AssertionError("group failed: " + name);
        }
    }

    private static void expectIllegalArgument(String name, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        group(name, rejected);
    }

    private static void deterministicEvidence() {
        group("canonical", Arrays.deepEquals(
                mergeIntervals(new int[][] {{1, 3}, {2, 6}, {8, 10}, {15, 18}}),
                new int[][] {{1, 6}, {8, 10}, {15, 18}}));
        group("touching closed endpoints", Arrays.deepEquals(
                mergeIntervals(new int[][] {{1, 4}, {4, 5}}), new int[][] {{1, 5}}));
        group("transitive chain", Arrays.deepEquals(
                mergeIntervals(new int[][] {{4, 7}, {1, 4}, {2, 3}, {6, 8}}), new int[][] {{1, 8}}));
        group("nested intervals", Arrays.deepEquals(
                mergeIntervals(new int[][] {{1, 10}, {2, 3}, {4, 8}}), new int[][] {{1, 10}}));
        group("empty reusable input", mergeIntervals(new int[0][]).length == 0);
        group("singleton", Arrays.deepEquals(mergeIntervals(new int[][] {{3, 3}}), new int[][] {{3, 3}}));
        group("unsorted disjoint", Arrays.deepEquals(
                mergeIntervals(new int[][] {{8, 9}, {-3, -2}, {2, 4}}),
                new int[][] {{-3, -2}, {2, 4}, {8, 9}}));
        group("duplicate intervals", Arrays.deepEquals(
                mergeIntervals(new int[][] {{2, 5}, {2, 5}, {2, 5}}), new int[][] {{2, 5}}));
        group("zero-length absorbed", Arrays.deepEquals(
                mergeIntervals(new int[][] {{0, 0}, {-1, 0}, {0, 1}}), new int[][] {{-1, 1}}));
        group("full width endpoints", Arrays.deepEquals(
                mergeIntervals(new int[][] {{Integer.MAX_VALUE, Integer.MAX_VALUE},
                        {Integer.MIN_VALUE, -1}, {-1, 0}}),
                new int[][] {{Integer.MIN_VALUE, 0}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}));

        int[][] preserved = {{4, 7}, {1, 4}, {2, 3}, {6, 8}};
        int[][] snapshot = validatedCopy(preserved);
        int[][] result = mergeIntervals(preserved);
        group("input preserved", Arrays.deepEquals(preserved, snapshot));
        group("output rows owned", ownsFreshRows(preserved, result));
        group("canonical result", isCanonical(result));
        group("same-start deterministic", Arrays.deepEquals(
                mergeIntervals(new int[][] {{1, 9}, {1, 2}, {1, 5}}), new int[][] {{1, 9}}));

        expectIllegalArgument("null matrix rejected", () -> mergeIntervals(null));
        expectIllegalArgument("null row rejected", () -> mergeIntervals(new int[][] {null}));
        expectIllegalArgument("short row rejected", () -> mergeIntervals(new int[][] {{1}}));
        expectIllegalArgument("long row rejected", () -> mergeIntervals(new int[][] {{1, 2, 3}}));
        expectIllegalArgument("reversed endpoints rejected", () -> mergeIntervals(new int[][] {{5, 4}}));
        group("extreme comparator order", Arrays.deepEquals(
                mergeIntervals(new int[][] {{Integer.MAX_VALUE, Integer.MAX_VALUE},
                        {Integer.MIN_VALUE, Integer.MIN_VALUE}}),
                new int[][] {{Integer.MIN_VALUE, Integer.MIN_VALUE},
                        {Integer.MAX_VALUE, Integer.MAX_VALUE}}));
    }

    private static void randomizedEvidence() {
        Random random = new Random(20261104L);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = random.nextInt(9);
            int[][] intervals = new int[length][2];
            for (int index = 0; index < length; index++) {
                int a = random.nextInt(41) - 20;
                int b = random.nextInt(41) - 20;
                intervals[index][0] = Math.min(a, b);
                intervals[index][1] = Math.max(a, b);
            }
            int[][] snapshot = validatedCopy(intervals);
            int[][] actual = mergeIntervals(intervals);
            int[][] expected = repeatedPairOracle(intervals);

            checks += 5;
            if (!Arrays.deepEquals(actual, expected)) {
                throw new AssertionError("oracle mismatch trial=" + trial
                        + " actual=" + Arrays.deepToString(actual)
                        + " expected=" + Arrays.deepToString(expected));
            }
            if (!Arrays.deepEquals(intervals, snapshot)) {
                throw new AssertionError("input mutated trial=" + trial);
            }
            if (!isCanonical(actual)) {
                throw new AssertionError("non-canonical output trial=" + trial);
            }
            if (!sameIntegerPointCoverage(intervals, actual, -20, 20)) {
                throw new AssertionError("coverage mismatch trial=" + trial);
            }
            if (!ownsFreshRows(intervals, actual)) {
                throw new AssertionError("output aliases input trial=" + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|CANONICAL=1:6,8:10,15:18|TOUCH=1:5|CHAIN=1:8|WIDE_SAFE=true|PRESERVED=true");
        System.out.println("MergeIntervalsContracts: 20 scenario groups + 20000 seeded repeated-pair comparisons passed; checks=" + checks);
    }
}
