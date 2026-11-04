import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/** Dependency-free executable contracts for Insert Interval. */
public final class InsertIntervalContracts {
    private static final Comparator<int[]> BY_START_THEN_END = Comparator
            .comparingInt((int[] row) -> row[0])
            .thenComparingInt(row -> row[1]);

    private static int groups;
    private static long checks;

    static int[][] insertInterval(int[][] intervals, int[] newInterval) {
        validateNormalized(intervals);
        validateInterval(newInterval, "newInterval");

        int[][] result = new int[intervals.length + 1][2];
        int read = 0;
        int write = 0;
        int mergedStart = newInterval[0];
        int mergedEnd = newInterval[1];

        while (read < intervals.length && intervals[read][1] < mergedStart) {
            result[write++] = intervals[read++].clone();
        }
        while (read < intervals.length && intervals[read][0] <= mergedEnd) {
            mergedStart = Math.min(mergedStart, intervals[read][0]);
            mergedEnd = Math.max(mergedEnd, intervals[read][1]);
            read++;
        }
        result[write++] = new int[] {mergedStart, mergedEnd};
        while (read < intervals.length) {
            result[write++] = intervals[read++].clone();
        }
        return Arrays.copyOf(result, write);
    }

    private static void validateNormalized(int[][] intervals) {
        if (intervals == null) {
            throw new IllegalArgumentException("intervals must not be null");
        }
        for (int index = 0; index < intervals.length; index++) {
            validateInterval(intervals[index], "intervals[" + index + "]");
            if (index > 0 && intervals[index - 1][1] >= intervals[index][0]) {
                throw new IllegalArgumentException("intervals must be sorted and non-overlapping");
            }
        }
    }

    private static void validateInterval(int[] interval, String name) {
        if (interval == null || interval.length != 2 || interval[0] > interval[1]) {
            throw new IllegalArgumentException(name + " must be [start,end]");
        }
    }

    private static int[][] deepCopy(int[][] intervals) {
        int[][] copy = new int[intervals.length][];
        for (int index = 0; index < intervals.length; index++) {
            copy[index] = intervals[index] == null ? null : intervals[index].clone();
        }
        return copy;
    }

    /** Independent baseline: append, sort, then perform a general merge. */
    private static int[][] appendSortMergeOracle(int[][] intervals, int[] newInterval) {
        int[][] all = new int[intervals.length + 1][2];
        for (int index = 0; index < intervals.length; index++) {
            all[index] = intervals[index].clone();
        }
        all[intervals.length] = newInterval.clone();
        Arrays.sort(all, BY_START_THEN_END);

        int[][] merged = new int[all.length][2];
        int size = 0;
        for (int[] current : all) {
            if (size == 0 || merged[size - 1][1] < current[0]) {
                merged[size++] = current.clone();
            } else {
                merged[size - 1][1] = Math.max(merged[size - 1][1], current[1]);
            }
        }
        return Arrays.copyOf(merged, size);
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

    private static boolean sameCoverage(int[][] intervals, int[] added, int[][] output,
            int minimum, int maximum) {
        for (int point = minimum; point <= maximum; point++) {
            boolean expected = covers(intervals, point) || covers(added, point);
            if (expected != covers(output, point)) {
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

    private static boolean covers(int[] interval, int point) {
        return interval[0] <= point && point <= interval[1];
    }

    private static boolean ownsFreshRows(int[][] input, int[] added, int[][] output) {
        for (int[] out : output) {
            if (out == added) {
                return false;
            }
            for (int[] in : input) {
                if (out == in) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean rejects(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void group(String name, boolean condition) {
        groups++;
        checks++;
        if (!condition) {
            throw new AssertionError("group failed: " + name);
        }
    }

    private static void deterministicEvidence() {
        group("official one-component merge", Arrays.deepEquals(
                insertInterval(new int[][] {{1, 3}, {6, 9}}, new int[] {2, 5}),
                new int[][] {{1, 5}, {6, 9}}));
        group("official bridge", Arrays.deepEquals(
                insertInterval(new int[][] {{1, 2}, {3, 5}, {6, 7}, {8, 10}, {12, 16}},
                        new int[] {4, 8}),
                new int[][] {{1, 2}, {3, 10}, {12, 16}}));
        group("before all", Arrays.deepEquals(
                insertInterval(new int[][] {{5, 7}, {9, 11}}, new int[] {1, 2}),
                new int[][] {{1, 2}, {5, 7}, {9, 11}}));
        group("after all", Arrays.deepEquals(
                insertInterval(new int[][] {{5, 7}, {9, 11}}, new int[] {12, 14}),
                new int[][] {{5, 7}, {9, 11}, {12, 14}}));
        group("empty input", Arrays.deepEquals(
                insertInterval(new int[0][], new int[] {-3, -3}), new int[][] {{-3, -3}}));
        group("new contained", Arrays.deepEquals(
                insertInterval(new int[][] {{1, 10}, {20, 30}}, new int[] {3, 4}),
                new int[][] {{1, 10}, {20, 30}}));
        group("new contains components", Arrays.deepEquals(
                insertInterval(new int[][] {{2, 3}, {5, 7}}, new int[] {1, 8}),
                new int[][] {{1, 8}}));
        group("touches left only", Arrays.deepEquals(
                insertInterval(new int[][] {{1, 2}, {5, 7}}, new int[] {2, 4}),
                new int[][] {{1, 4}, {5, 7}}));
        group("touches both and bridges", Arrays.deepEquals(
                insertInterval(new int[][] {{1, 2}, {4, 5}}, new int[] {2, 4}),
                new int[][] {{1, 5}}));
        group("zero-length inserted", Arrays.deepEquals(
                insertInterval(new int[][] {{-2, -1}, {1, 2}}, new int[] {0, 0}),
                new int[][] {{-2, -1}, {0, 0}, {1, 2}}));
        group("full-width bridge", Arrays.deepEquals(
                insertInterval(new int[][] {{Integer.MIN_VALUE, -10}, {10, Integer.MAX_VALUE}},
                        new int[] {-10, 10}),
                new int[][] {{Integer.MIN_VALUE, Integer.MAX_VALUE}}));

        int[][] preserved = {{1, 2}, {3, 5}, {6, 7}, {8, 10}, {12, 16}};
        int[] added = {4, 8};
        int[][] snapshot = deepCopy(preserved);
        int[] addedSnapshot = added.clone();
        int[][] result = insertInterval(preserved, added);
        group("input matrix preserved", Arrays.deepEquals(preserved, snapshot));
        group("new interval preserved", Arrays.equals(added, addedSnapshot));
        group("output rows owned", ownsFreshRows(preserved, added, result));
        group("canonical result", isCanonical(result));

        group("null arguments rejected", rejects(() -> insertInterval(null, new int[] {1, 2}))
                && rejects(() -> insertInterval(new int[0][], null)));
        group("malformed existing rows rejected", rejects(
                () -> insertInterval(new int[][] {null}, new int[] {1, 2}))
                && rejects(() -> insertInterval(new int[][] {{1}}, new int[] {2, 3}))
                && rejects(() -> insertInterval(new int[][] {{1, 2, 3}}, new int[] {4, 5}))
                && rejects(() -> insertInterval(new int[][] {{5, 4}}, new int[] {1, 2})));
        group("malformed new interval rejected", rejects(
                () -> insertInterval(new int[0][], new int[] {1}))
                && rejects(() -> insertInterval(new int[0][], new int[] {1, 2, 3})));
        group("reversed new interval rejected", rejects(
                () -> insertInterval(new int[0][], new int[] {5, 4})));
        group("non-normalized input rejected", rejects(
                () -> insertInterval(new int[][] {{1, 4}, {4, 5}}, new int[] {8, 9}))
                && rejects(() -> insertInterval(new int[][] {{5, 6}, {1, 2}}, new int[] {8, 9}))
                && rejects(() -> insertInterval(new int[][] {{1, 5}, {3, 7}}, new int[] {8, 9})));
    }

    private static int[][] randomNormalized(Random random) {
        int length = random.nextInt(9);
        int[][] intervals = new int[length][2];
        int cursor = -25 + random.nextInt(5);
        for (int index = 0; index < length; index++) {
            int start = cursor;
            int end = start + random.nextInt(6);
            intervals[index][0] = start;
            intervals[index][1] = end;
            cursor = end + 2 + random.nextInt(4);
        }
        return intervals;
    }

    private static void randomizedEvidence() {
        Random random = new Random(20261104L ^ 29L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[][] intervals = randomNormalized(random);
            int first = random.nextInt(61) - 30;
            int second = random.nextInt(61) - 30;
            int[] added = {Math.min(first, second), Math.max(first, second)};
            int[][] snapshot = deepCopy(intervals);
            int[] addedSnapshot = added.clone();

            int[][] actual = insertInterval(intervals, added);
            int[][] expected = appendSortMergeOracle(intervals, added);

            checks += 5;
            if (!Arrays.deepEquals(actual, expected)) {
                throw new AssertionError("oracle mismatch trial=" + trial
                        + " input=" + Arrays.deepToString(intervals)
                        + " added=" + Arrays.toString(added)
                        + " actual=" + Arrays.deepToString(actual)
                        + " expected=" + Arrays.deepToString(expected));
            }
            if (!Arrays.deepEquals(intervals, snapshot) || !Arrays.equals(added, addedSnapshot)) {
                throw new AssertionError("input mutated trial=" + trial);
            }
            if (!isCanonical(actual)) {
                throw new AssertionError("non-canonical output trial=" + trial);
            }
            if (!sameCoverage(intervals, added, actual, -35, 35)) {
                throw new AssertionError("coverage mismatch trial=" + trial);
            }
            if (!ownsFreshRows(intervals, added, actual)) {
                throw new AssertionError("output aliases caller storage trial=" + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|CANONICAL=1:5,6:9|BRIDGE=1:2,3:10,12:16|EMPTY=-3:-3|WIDE_SAFE=true|PRESERVED=true");
        System.out.println("InsertIntervalContracts: 20 scenario groups + 20000 seeded append-sort-merge comparisons passed; checks=" + checks);
    }
}
