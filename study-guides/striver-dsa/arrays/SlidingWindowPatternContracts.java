import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Dependency-free executable contracts for four sliding-window mechanisms. */
public final class SlidingWindowPatternContracts {
    static long maximumFixedWindowSum(int[] values, int k) {
        requireArray(values);
        requireWindow(values.length, k);
        long window = 0;
        for (int i = 0; i < k; i++) window += values[i];
        long best = window;
        for (int right = k; right < values.length; right++) {
            window += (long) values[right] - values[right - k];
            best = Math.max(best, window);
        }
        return best;
    }

    static int minimumSizeAtLeast(long target, int[] positive) {
        requireArray(positive);
        if (target <= 0) throw new IllegalArgumentException("target must be positive");
        for (int value : positive) {
            if (value <= 0) throw new IllegalArgumentException("values must be positive");
        }
        long sum = 0;
        int left = 0;
        int best = Integer.MAX_VALUE;
        for (int right = 0; right < positive.length; right++) {
            sum += positive[right];
            while (sum >= target) {
                best = Math.min(best, right - left + 1);
                sum -= positive[left++];
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    static int longestAtMostKDistinct(int[] values, int k) {
        requireArray(values);
        if (k < 0) throw new IllegalArgumentException("k must be nonnegative");
        Map<Integer, Integer> frequencies = new HashMap<>();
        int left = 0;
        int best = 0;
        for (int right = 0; right < values.length; right++) {
            frequencies.merge(values[right], 1, Integer::sum);
            while (frequencies.size() > k) {
                int outgoing = values[left++];
                int remaining = frequencies.get(outgoing) - 1;
                if (remaining == 0) frequencies.remove(outgoing);
                else frequencies.put(outgoing, remaining);
            }
            best = Math.max(best, right - left + 1);
        }
        return best;
    }

    static int[] maximumEveryWindow(int[] values, int k) {
        requireArray(values);
        requireWindow(values.length, k);
        int[] answer = new int[values.length - k + 1];
        Deque<Integer> deque = new ArrayDeque<>();
        for (int right = 0; right < values.length; right++) {
            while (!deque.isEmpty() && deque.peekFirst() <= right - k) deque.removeFirst();
            while (!deque.isEmpty() && values[deque.peekLast()] <= values[right]) deque.removeLast();
            deque.addLast(right);
            if (right >= k - 1) answer[right - k + 1] = values[deque.peekFirst()];
        }
        return answer;
    }

    private static void requireArray(int[] values) {
        if (values == null) throw new IllegalArgumentException("values must be non-null");
    }

    private static void requireWindow(int length, int k) {
        if (k <= 0 || k > length) throw new IllegalArgumentException("require 1 <= k <= length");
    }

    private static long fixedOracle(int[] values, int k) {
        long best = Long.MIN_VALUE;
        for (int left = 0; left + k <= values.length; left++) {
            long sum = 0;
            for (int i = left; i < left + k; i++) sum += values[i];
            best = Math.max(best, sum);
        }
        return best;
    }

    private static int minimumOracle(long target, int[] values) {
        int best = Integer.MAX_VALUE;
        for (int left = 0; left < values.length; left++) {
            long sum = 0;
            for (int right = left; right < values.length; right++) {
                sum += values[right];
                if (sum >= target) {
                    best = Math.min(best, right - left + 1);
                    break;
                }
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static int distinctOracle(int[] values, int k) {
        int best = 0;
        for (int left = 0; left < values.length; left++) {
            Map<Integer, Integer> seen = new HashMap<>();
            for (int right = left; right < values.length; right++) {
                seen.merge(values[right], 1, Integer::sum);
                if (seen.size() > k) break;
                best = Math.max(best, right - left + 1);
            }
        }
        return best;
    }

    private static int[] maximumOracle(int[] values, int k) {
        int[] answer = new int[values.length - k + 1];
        for (int left = 0; left + k <= values.length; left++) {
            int best = values[left];
            for (int i = left + 1; i < left + k; i++) best = Math.max(best, values[i]);
            answer[left] = best;
        }
        return answer;
    }

    private static int deterministicGroups() {
        checkFixed(new int[]{1, 12, -5, -6, 50, 3}, 4, 51);
        checkFixed(new int[]{5}, 1, 5);
        checkFixed(new int[]{-8, -3, -6}, 2, -9);
        checkFixed(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 2, 4_294_967_294L);
        checkFixed(new int[]{4, -1, 2, 7}, 4, 12);

        checkMinimum(7, new int[]{2, 3, 1, 2, 4, 3}, 2);
        checkMinimum(4, new int[]{1, 4, 4}, 1);
        checkMinimum(11, new int[]{1, 1, 1, 1, 1, 1, 1, 1}, 0);
        checkMinimum(5, new int[]{}, 0);
        checkMinimum(4_294_967_294L, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 2);

        checkDistinct(new int[]{1, 2, 1}, 2, 3);
        checkDistinct(new int[]{0, 1, 2, 2}, 2, 3);
        checkDistinct(new int[]{1, 2, 3, 2, 2}, 2, 4);
        checkDistinct(new int[]{4, 4, 4}, 1, 3);
        checkDistinct(new int[]{1, 2}, 0, 0);

        checkMaximum(new int[]{1, 3, -1, -3, 5, 3, 6, 7}, 3, new int[]{3, 3, 5, 5, 6, 7});
        checkMaximum(new int[]{1}, 1, new int[]{1});
        checkMaximum(new int[]{9, 8, 7}, 2, new int[]{9, 8});
        checkMaximum(new int[]{-4, -2, -5}, 2, new int[]{-2, -2});
        checkMaximum(new int[]{2, 2, 2}, 2, new int[]{2, 2});

        expectFailure(() -> maximumFixedWindowSum(null, 1));
        expectFailure(() -> maximumFixedWindowSum(new int[]{1}, 0));
        expectFailure(() -> minimumSizeAtLeast(0, new int[]{1}));
        expectFailure(() -> minimumSizeAtLeast(1, new int[]{1, 0}));
        expectFailure(() -> longestAtMostKDistinct(new int[]{1}, -1));
        expectFailure(() -> maximumEveryWindow(new int[]{1}, 2));
        return 20;
    }

    private static long randomizedChecks() {
        Random random = new Random(0x51D1D1A6L);
        long checks = 0;
        for (int trial = 0; trial < 20_000; trial++) {
            int[] fixed = randomArray(random, 1 + random.nextInt(12), -10, 10);
            int[] fixedCopy = fixed.clone();
            int fixedK = 1 + random.nextInt(fixed.length);
            require(maximumFixedWindowSum(fixed, fixedK) == fixedOracle(fixed, fixedK), "fixed oracle mismatch");
            checks++;
            require(Arrays.equals(fixed, fixedCopy), "fixed window mutated input");
            checks++;

            int[] positive = randomArray(random, random.nextInt(13), 1, 10);
            long target = 1 + random.nextInt(70);
            require(minimumSizeAtLeast(target, positive) == minimumOracle(target, positive), "minimum oracle mismatch");
            checks++;

            int[] distinct = randomArray(random, random.nextInt(13), -3, 3);
            int distinctK = random.nextInt(6);
            require(longestAtMostKDistinct(distinct, distinctK) == distinctOracle(distinct, distinctK), "distinct oracle mismatch");
            checks++;

            int[] maximum = randomArray(random, 1 + random.nextInt(12), -20, 20);
            int maximumK = 1 + random.nextInt(maximum.length);
            require(Arrays.equals(maximumEveryWindow(maximum, maximumK), maximumOracle(maximum, maximumK)), "maximum oracle mismatch");
            checks++;
        }
        return checks;
    }

    private static int[] randomArray(Random random, int length, int low, int high) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = low + random.nextInt(high - low + 1);
        return values;
    }

    private static void checkFixed(int[] input, int k, long expected) {
        int[] copy = input.clone();
        require(maximumFixedWindowSum(input, k) == expected, "unexpected fixed-window sum");
        require(Arrays.equals(input, copy), "fixed window must preserve input");
    }

    private static void checkMinimum(long target, int[] input, int expected) {
        require(minimumSizeAtLeast(target, input) == expected, "unexpected minimum length");
    }

    private static void checkDistinct(int[] input, int k, int expected) {
        require(longestAtMostKDistinct(input, k) == expected, "unexpected at-most-k length");
    }

    private static void checkMaximum(int[] input, int k, int[] expected) {
        int[] copy = input.clone();
        require(Arrays.equals(maximumEveryWindow(input, k), expected), "unexpected window maxima");
        require(Arrays.equals(input, copy), "window maximum must preserve input");
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
        System.out.printf("SlidingWindowPatternContracts: %d scenario groups + 20000 seeded oracle trials passed; checks=%d%n",
                groups, checks);
    }
}
