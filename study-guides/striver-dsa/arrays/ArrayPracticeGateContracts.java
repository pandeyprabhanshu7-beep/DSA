import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Executable contracts for Section 20's final array-practice gate. */
public final class ArrayPracticeGateContracts {
    private ArrayPracticeGateContracts() {}

    static int lowerBound(int[] sorted, int target) {
        require(sorted != null, "array must not be null");
        for (int i = 1; i < sorted.length; i++) {
            require(sorted[i - 1] <= sorted[i], "array must be sorted");
        }
        int left = 0, right = sorted.length;
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (sorted[mid] >= target) right = mid;
            else left = mid + 1;
        }
        return left;
    }

    static long countExactSum(int[] values, long target) {
        require(values != null, "array must not be null");
        Map<Long, Long> frequency = new HashMap<>();
        frequency.put(0L, 1L);
        long prefix = 0, count = 0;
        for (int value : values) {
            prefix += value;
            count += frequency.getOrDefault(prefix - target, 0L);
            frequency.merge(prefix, 1L, Long::sum);
        }
        return count;
    }

    static int[] maxSlidingWindow(int[] values, int width) {
        require(values != null, "array must not be null");
        require(width > 0 && width <= values.length, "width must be in [1,n]");
        int[] answer = new int[values.length - width + 1];
        Deque<Integer> candidates = new ArrayDeque<>();
        for (int right = 0; right < values.length; right++) {
            while (!candidates.isEmpty() && candidates.peekFirst() <= right - width) {
                candidates.removeFirst();
            }
            while (!candidates.isEmpty()
                    && values[candidates.peekLast()] <= values[right]) {
                candidates.removeLast();
            }
            candidates.addLast(right);
            if (right >= width - 1) {
                answer[right - width + 1] = values[candidates.peekFirst()];
            }
        }
        return answer;
    }

    static long minimizeLargestSplit(int[] values, int groups) {
        require(values != null && values.length > 0, "array must be nonempty");
        require(groups > 0 && groups <= values.length, "groups must be in [1,n]");
        long low = 0, high = 0;
        for (int value : values) {
            require(value >= 0, "split search requires nonnegative values");
            low = Math.max(low, value);
            high += value;
        }
        while (low < high) {
            long capacity = low + (high - low) / 2;
            if (groupsNeeded(values, capacity) <= groups) high = capacity;
            else low = capacity + 1;
        }
        return low;
    }

    private static int groupsNeeded(int[] values, long capacity) {
        int groups = 1;
        long used = 0;
        for (int value : values) {
            if (used + value > capacity) {
                groups++;
                used = value;
            } else {
                used += value;
            }
        }
        return groups;
    }

    private static int linearLowerBound(int[] sorted, int target) {
        int i = 0;
        while (i < sorted.length && sorted[i] < target) i++;
        return i;
    }

    private static long quadraticExactCount(int[] values, long target) {
        long count = 0;
        for (int left = 0; left < values.length; left++) {
            long sum = 0;
            for (int right = left; right < values.length; right++) {
                sum += values[right];
                if (sum == target) count++;
            }
        }
        return count;
    }

    private static int[] directWindowMax(int[] values, int width) {
        int[] answer = new int[values.length - width + 1];
        for (int left = 0; left + width <= values.length; left++) {
            int best = values[left];
            for (int i = left + 1; i < left + width; i++) best = Math.max(best, values[i]);
            answer[left] = best;
        }
        return answer;
    }

    private static long exactSplitDp(int[] values, int groups) {
        int n = values.length;
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + values[i];
        long inf = Long.MAX_VALUE / 4;
        long[][] dp = new long[groups + 1][n + 1];
        for (long[] row : dp) Arrays.fill(row, inf);
        dp[0][0] = 0;
        for (int g = 1; g <= groups; g++) {
            for (int end = g; end <= n; end++) {
                for (int cut = g - 1; cut < end; cut++) {
                    long candidate = Math.max(dp[g - 1][cut], prefix[end] - prefix[cut]);
                    dp[g][end] = Math.min(dp[g][end], candidate);
                }
            }
        }
        return dp[groups][n];
    }

    private static void deterministicGroups() {
        check(lowerBound(new int[] {}, 4) == 0, "empty lower bound");
        check(lowerBound(new int[] {1, 3, 3, 7}, 3) == 1, "first equal");
        check(lowerBound(new int[] {1, 3, 3, 7}, 5) == 3, "middle insertion");
        check(lowerBound(new int[] {1, 3, 3, 7}, 9) == 4, "end insertion");
        expectFailure(() -> lowerBound(new int[] {2, 1}, 1));

        check(countExactSum(new int[] {2, -2, 2}, 2) == 3, "signed exact sum");
        check(countExactSum(new int[] {0, 0}, 0) == 3, "zero boundaries");
        check(countExactSum(new int[] {-1, -1}, -1) == 2, "negative target");
        check(countExactSum(new int[] {}, 7) == 0, "empty exact sum");
        check(countExactSum(new int[] {Integer.MAX_VALUE, 1, -Integer.MAX_VALUE}, 1) == 2,
                "widened prefixes");

        check(Arrays.equals(maxSlidingWindow(new int[] {4, 4, 2, 5}, 2), new int[] {4, 4, 5}),
                "duplicate window maxima");
        check(Arrays.equals(maxSlidingWindow(new int[] {-3}, 1), new int[] {-3}), "singleton window");
        check(Arrays.equals(maxSlidingWindow(new int[] {3, 1, 2}, 3), new int[] {3}), "full window");
        check(Arrays.equals(maxSlidingWindow(new int[] {1, 2, 3}, 1), new int[] {1, 2, 3}),
                "unit windows");
        expectFailure(() -> maxSlidingWindow(new int[] {1}, 0));

        check(minimizeLargestSplit(new int[] {7, 2, 5, 10, 8}, 2) == 18, "canonical split");
        check(minimizeLargestSplit(new int[] {1, 2, 3, 4, 5}, 2) == 9, "practice split");
        check(minimizeLargestSplit(new int[] {1, 2, 3}, 1) == 6, "one group");
        check(minimizeLargestSplit(new int[] {1, 2, 3}, 3) == 3, "one item per group");
        expectFailure(() -> minimizeLargestSplit(new int[] {1, -1}, 1));
    }

    private static void seededTrials() {
        Random random = new Random(0x20A11CE5L);
        for (int trial = 0; trial < 20_000; trial++) {
            int sortedLength = random.nextInt(13);
            int[] sorted = new int[sortedLength];
            int next = random.nextInt(5) - 8;
            for (int i = 0; i < sortedLength; i++) {
                next += random.nextInt(3);
                sorted[i] = next;
            }
            int target = random.nextInt(25) - 10;
            int[] sortedSnapshot = sorted.clone();
            check(lowerBound(sorted, target) == linearLowerBound(sorted, target)
                    && Arrays.equals(sorted, sortedSnapshot), "lower-bound oracle");

            int signedLength = random.nextInt(11);
            int[] signed = new int[signedLength];
            for (int i = 0; i < signedLength; i++) signed[i] = random.nextInt(13) - 6;
            long sumTarget = random.nextInt(21) - 10;
            int[] signedSnapshot = signed.clone();
            check(countExactSum(signed, sumTarget) == quadraticExactCount(signed, sumTarget)
                    && Arrays.equals(signed, signedSnapshot), "exact-sum oracle");

            int windowLength = 1 + random.nextInt(10);
            int[] windowValues = new int[windowLength];
            for (int i = 0; i < windowLength; i++) windowValues[i] = random.nextInt(31) - 15;
            int width = 1 + random.nextInt(windowLength);
            int[] windowSnapshot = windowValues.clone();
            check(Arrays.equals(maxSlidingWindow(windowValues, width), directWindowMax(windowValues, width))
                    && Arrays.equals(windowValues, windowSnapshot), "window oracle");

            int splitLength = 1 + random.nextInt(9);
            int[] splitValues = new int[splitLength];
            for (int i = 0; i < splitLength; i++) splitValues[i] = random.nextInt(16);
            int groups = 1 + random.nextInt(splitLength);
            int[] splitSnapshot = splitValues.clone();
            check(minimizeLargestSplit(splitValues, groups) == exactSplitDp(splitValues, groups)
                    && Arrays.equals(splitValues, splitSnapshot), "split oracle");
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected contract rejection.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        deterministicGroups();
        seededTrials();
        System.out.println("PASS groups=20 trials=20000 oracle/preservation checks=80000");
    }
}
