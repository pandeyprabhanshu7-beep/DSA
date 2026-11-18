import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Executable contracts for the Section 16 interval family. */
public final class IntervalPatternContracts {
    private IntervalPatternContracts() {}

    /** Union of closed intervals; endpoint contact counts as overlap. */
    public static int[][] mergeClosed(int[][] intervals) {
        int[][] sorted = validatedCopy(intervals, true, true);
        if (sorted.length == 0) return sorted;
        Arrays.sort(sorted, Comparator.comparingInt((int[] x) -> x[0])
                .thenComparingInt(x -> x[1]));
        List<int[]> merged = new ArrayList<>();
        int start = sorted[0][0];
        int end = sorted[0][1];
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i][0] <= end) end = Math.max(end, sorted[i][1]);
            else {
                merged.add(new int[] {start, end});
                start = sorted[i][0];
                end = sorted[i][1];
            }
        }
        merged.add(new int[] {start, end});
        return merged.toArray(int[][]::new);
    }

    /** Minimum removals so half-open intervals no longer overlap; touching is legal. */
    public static int minimumRemovals(int[][] intervals) {
        int[][] sorted = validatedCopy(intervals, false, true);
        Arrays.sort(sorted, Comparator.comparingInt((int[] x) -> x[1])
                .thenComparingInt(x -> x[0]));
        int kept = 0;
        long lastEnd = Long.MIN_VALUE;
        for (int[] interval : sorted) {
            if (interval[0] >= lastEnd) {
                kept++;
                lastEnd = interval[1];
            }
        }
        return sorted.length - kept;
    }

    /** Minimum vertical arrows that hit every closed balloon interval. */
    public static int minimumArrows(int[][] balloons) {
        int[][] sorted = validatedCopy(balloons, false, true);
        Arrays.sort(sorted, Comparator.comparingInt((int[] x) -> x[1])
                .thenComparingInt(x -> x[0]));
        int arrows = 0;
        long shot = Long.MIN_VALUE;
        for (int[] balloon : sorted) {
            if (arrows == 0 || balloon[0] > shot) {
                arrows++;
                shot = balloon[1];
            }
        }
        return arrows;
    }

    /** Maximum simultaneous half-open meetings, equal to the minimum room count. */
    public static int minimumMeetingRooms(int[][] meetings) {
        int[][] copy = validatedCopy(meetings, false, true);
        int n = copy.length;
        int[] starts = new int[n];
        int[] ends = new int[n];
        for (int i = 0; i < n; i++) {
            starts[i] = copy[i][0];
            ends[i] = copy[i][1];
        }
        Arrays.sort(starts);
        Arrays.sort(ends);
        int startIndex = 0;
        int endIndex = 0;
        int active = 0;
        int maximum = 0;
        while (startIndex < n) {
            if (ends[endIndex] <= starts[startIndex]) {
                active--;
                endIndex++;
            } else {
                active++;
                maximum = Math.max(maximum, active);
                startIndex++;
            }
        }
        return maximum;
    }

    /** Intersections of two sorted, internally disjoint lists of closed intervals. */
    public static int[][] intersectClosed(int[][] first, int[][] second) {
        int[][] a = validatedOrderedDisjoint(first);
        int[][] b = validatedOrderedDisjoint(second);
        List<int[]> answer = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            int start = Math.max(a[i][0], b[j][0]);
            int end = Math.min(a[i][1], b[j][1]);
            if (start <= end) answer.add(new int[] {start, end});
            if (a[i][1] < b[j][1]) i++;
            else j++;
        }
        return answer.toArray(int[][]::new);
    }

    /** Finite common free intervals inside the union's outer busy bounds. */
    public static int[][] employeeFreeTime(int[][][] schedules) {
        if (schedules == null || schedules.length == 0) {
            throw new IllegalArgumentException("schedules must contain an employee");
        }
        List<int[]> busy = new ArrayList<>();
        for (int[][] schedule : schedules) {
            for (int[] interval : validatedOrderedDisjoint(schedule)) busy.add(interval.clone());
        }
        if (busy.isEmpty()) return new int[0][2];
        busy.sort(Comparator.comparingInt((int[] x) -> x[0]).thenComparingInt(x -> x[1]));
        List<int[]> free = new ArrayList<>();
        int end = busy.get(0)[1];
        for (int i = 1; i < busy.size(); i++) {
            int[] next = busy.get(i);
            if (next[0] > end) free.add(new int[] {end, next[0]});
            end = Math.max(end, next[1]);
        }
        return free.toArray(int[][]::new);
    }

    private static int[][] validatedCopy(int[][] intervals, boolean allowPoint, boolean allowEmpty) {
        if (intervals == null || (!allowEmpty && intervals.length == 0)) {
            throw new IllegalArgumentException("interval array violates size contract");
        }
        int[][] copy = new int[intervals.length][2];
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == null || intervals[i].length != 2
                    || (allowPoint ? intervals[i][0] > intervals[i][1]
                                   : intervals[i][0] >= intervals[i][1])) {
                throw new IllegalArgumentException("each interval must have a valid boundary pair");
            }
            copy[i] = intervals[i].clone();
        }
        return copy;
    }

    private static int[][] validatedOrderedDisjoint(int[][] intervals) {
        int[][] copy = validatedCopy(intervals, false, true);
        for (int i = 1; i < copy.length; i++) {
            if (copy[i - 1][1] >= copy[i][0]) {
                throw new IllegalArgumentException("list must be sorted and internally disjoint");
            }
        }
        return copy;
    }

    private static int[][] randomIntervals(Random random, int maximumLength) {
        int[][] result = new int[random.nextInt(maximumLength + 1)][2];
        for (int i = 0; i < result.length; i++) {
            int start = random.nextInt(17) - 8;
            result[i] = new int[] {start, start + 1 + random.nextInt(6)};
        }
        return result;
    }

    private static int[][] randomDisjoint(Random random) {
        int[][] result = new int[random.nextInt(6)][2];
        int cursor = random.nextInt(5) - 8;
        for (int i = 0; i < result.length; i++) {
            int start = cursor + random.nextInt(3);
            int end = start + 1 + random.nextInt(4);
            result[i] = new int[] {start, end};
            cursor = end + 1 + random.nextInt(3);
        }
        return result;
    }

    private static int[][] mergeOracle(int[][] intervals) {
        int n = intervals.length;
        boolean[] seen = new boolean[n];
        List<int[]> components = new ArrayList<>();
        for (int seed = 0; seed < n; seed++) {
            if (seen[seed]) continue;
            int low = intervals[seed][0];
            int high = intervals[seed][1];
            seen[seed] = true;
            boolean changed;
            do {
                changed = false;
                for (int i = 0; i < n; i++) {
                    if (!seen[i] && intervals[i][0] <= high && low <= intervals[i][1]) {
                        seen[i] = true;
                        low = Math.min(low, intervals[i][0]);
                        high = Math.max(high, intervals[i][1]);
                        changed = true;
                    }
                }
            } while (changed);
            components.add(new int[] {low, high});
        }
        components.sort(Comparator.comparingInt(x -> x[0]));
        return components.toArray(int[][]::new);
    }

    private static int removalOracle(int[][] intervals) {
        int best = 0;
        for (int mask = 0; mask < (1 << intervals.length); mask++) {
            List<int[]> chosen = new ArrayList<>();
            for (int i = 0; i < intervals.length; i++) if ((mask & (1 << i)) != 0) chosen.add(intervals[i]);
            chosen.sort(Comparator.comparingInt(x -> x[0]));
            boolean legal = true;
            for (int i = 1; i < chosen.size(); i++) if (chosen.get(i)[0] < chosen.get(i - 1)[1]) legal = false;
            if (legal) best = Math.max(best, chosen.size());
        }
        return intervals.length - best;
    }

    private static int arrowsOracle(int[][] intervals) {
        if (intervals.length == 0) return 0;
        int[] points = Arrays.stream(intervals).flatMapToInt(x -> Arrays.stream(x)).distinct().toArray();
        int best = intervals.length;
        for (int mask = 0; mask < (1 << points.length); mask++) {
            int used = Integer.bitCount(mask);
            if (used >= best) continue;
            boolean all = true;
            for (int[] interval : intervals) {
                boolean hit = false;
                for (int p = 0; p < points.length; p++) {
                    if ((mask & (1 << p)) != 0 && interval[0] <= points[p] && points[p] <= interval[1]) hit = true;
                }
                if (!hit) all = false;
            }
            if (all) best = used;
        }
        return best;
    }

    private static int roomsOracle(int[][] meetings) {
        int best = 0;
        for (int[] event : meetings) {
            int time = event[0];
            int active = 0;
            for (int[] meeting : meetings) if (meeting[0] <= time && time < meeting[1]) active++;
            best = Math.max(best, active);
        }
        return best;
    }

    private static int[][] intersectionOracle(int[][] a, int[][] b) {
        List<int[]> answer = new ArrayList<>();
        for (int[] left : a) for (int[] right : b) {
            int start = Math.max(left[0], right[0]);
            int end = Math.min(left[1], right[1]);
            if (start <= end) answer.add(new int[] {start, end});
        }
        answer.sort(Comparator.comparingInt((int[] x) -> x[0]).thenComparingInt(x -> x[1]));
        return answer.toArray(int[][]::new);
    }

    private static int[][] freeTimeOracle(int[][][] schedules) {
        List<Integer> endpoints = new ArrayList<>();
        for (int[][] schedule : schedules) for (int[] x : schedule) {
            endpoints.add(x[0]); endpoints.add(x[1]);
        }
        if (endpoints.isEmpty()) return new int[0][2];
        int[] values = endpoints.stream().mapToInt(Integer::intValue).distinct().sorted().toArray();
        List<int[]> answer = new ArrayList<>();
        for (int i = 0; i + 1 < values.length; i++) {
            int left = values[i];
            int right = values[i + 1];
            int doubledMiddle = left + right;
            boolean busy = false;
            for (int[][] schedule : schedules) for (int[] x : schedule) {
                if (2L * x[0] < doubledMiddle && doubledMiddle < 2L * x[1]) busy = true;
            }
            if (!busy && left < right) {
                if (!answer.isEmpty() && answer.get(answer.size() - 1)[1] == left) answer.get(answer.size() - 1)[1] = right;
                else answer.add(new int[] {left, right});
            }
        }
        return answer.toArray(int[][]::new);
    }

    private static int[][] clone2d(int[][] values) {
        return Arrays.stream(values).map(int[]::clone).toArray(int[][]::new);
    }

    private static int[][][] clone3d(int[][][] values) {
        return Arrays.stream(values).map(IntervalPatternContracts::clone2d).toArray(int[][][]::new);
    }

    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void expectRejected(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label + " should reject invalid input");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    public static void main(String[] args) {
        int groups = 0;
        expect(Arrays.deepEquals(mergeClosed(new int[][] {{1,3},{2,6},{8,10},{15,18}}), new int[][] {{1,6},{8,10},{15,18}}), "merge official"); groups++;
        expect(Arrays.deepEquals(mergeClosed(new int[][] {{1,4},{4,5}}), new int[][] {{1,5}}), "merge touching"); groups++;
        expect(Arrays.deepEquals(mergeClosed(new int[0][2]), new int[0][2]), "merge empty extension"); groups++;
        expectRejected(() -> mergeClosed(new int[][] {{3,2}}), "merge reversed"); groups++;
        expect(minimumRemovals(new int[][] {{1,2},{2,3},{3,4},{1,3}}) == 1, "removals official"); groups++;
        expect(minimumRemovals(new int[][] {{1,2},{2,3}}) == 0, "removals touching"); groups++;
        expect(minimumRemovals(new int[][] {{1,2},{1,2},{1,2}}) == 2, "removals duplicates"); groups++;
        expectRejected(() -> minimumRemovals(new int[][] {{2,2}}), "removals zero length"); groups++;
        expect(minimumArrows(new int[][] {{10,16},{2,8},{1,6},{7,12}}) == 2, "arrows official"); groups++;
        expect(minimumArrows(new int[][] {{1,2},{2,3},{3,4},{4,5}}) == 2, "arrows touching"); groups++;
        expect(minimumArrows(new int[0][2]) == 0, "arrows empty extension"); groups++;
        expect(minimumArrows(new int[][] {{Integer.MIN_VALUE,-1},{0,Integer.MAX_VALUE}}) == 2, "arrows full width"); groups++;
        expect(minimumMeetingRooms(new int[][] {{0,30},{5,10},{15,20}}) == 2, "rooms canonical"); groups++;
        expect(minimumMeetingRooms(new int[][] {{7,10},{2,4}}) == 1, "rooms disjoint"); groups++;
        expect(minimumMeetingRooms(new int[][] {{1,5},{5,9}}) == 1, "rooms endpoint reuse"); groups++;
        expect(minimumMeetingRooms(new int[0][2]) == 0, "rooms empty"); groups++;
        expect(Arrays.deepEquals(intersectClosed(new int[][] {{0,2},{5,10},{13,23},{24,25}}, new int[][] {{1,5},{8,12},{15,24},{25,26}}), new int[][] {{1,2},{5,5},{8,10},{15,23},{24,24},{25,25}}), "intersection official"); groups++;
        expect(Arrays.deepEquals(intersectClosed(new int[][] {{1,3},{5,9}}, new int[0][2]), new int[0][2]), "intersection empty"); groups++;
        expect(Arrays.deepEquals(employeeFreeTime(new int[][][] {{{1,2},{5,6}},{{1,3}},{{4,10}}}), new int[][] {{3,4}}), "free time canonical"); groups++;
        expectRejected(() -> intersectClosed(new int[][] {{1,4},{3,5}}, new int[0][2]), "unordered overlap"); groups++;

        Random random = new Random(0x1A7E_2A15L);
        int trials = 20_000;
        int checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int[][] intervals = randomIntervals(random, 7);
            int[][] first = randomDisjoint(random);
            int[][] second = randomDisjoint(random);
            int employees = 1 + random.nextInt(4);
            int[][][] schedules = new int[employees][][];
            for (int i = 0; i < employees; i++) schedules[i] = randomDisjoint(random);
            int[][] before = clone2d(intervals);
            int[][] firstBefore = clone2d(first);
            int[][] secondBefore = clone2d(second);
            int[][][] schedulesBefore = clone3d(schedules);

            expect(Arrays.deepEquals(mergeClosed(intervals), mergeOracle(intervals)) && Arrays.deepEquals(intervals, before), "merge oracle/preservation"); checks++;
            expect(minimumRemovals(intervals) == removalOracle(intervals) && Arrays.deepEquals(intervals, before), "removal oracle/preservation"); checks++;
            expect(minimumArrows(intervals) == arrowsOracle(intervals) && Arrays.deepEquals(intervals, before), "arrows oracle/preservation"); checks++;
            expect(minimumMeetingRooms(intervals) == roomsOracle(intervals) && Arrays.deepEquals(intervals, before), "rooms oracle/preservation"); checks++;
            expect(Arrays.deepEquals(intersectClosed(first, second), intersectionOracle(first, second)) && Arrays.deepEquals(first, firstBefore) && Arrays.deepEquals(second, secondBefore), "intersection oracle/preservation"); checks++;
            expect(Arrays.deepEquals(employeeFreeTime(schedules), freeTimeOracle(schedules)) && Arrays.deepEquals(schedules, schedulesBefore), "free-time oracle/preservation"); checks++;
        }
        System.out.println("IntervalPatternContracts: " + groups + " deterministic groups + "
                + trials + " seeded trials; " + checks + " oracle/preservation checks passed.");
    }
}
