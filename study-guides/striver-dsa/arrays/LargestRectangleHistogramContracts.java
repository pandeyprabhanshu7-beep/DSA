import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;

/** Dependency-free executable contracts for Largest Rectangle in Histogram. */
public final class LargestRectangleHistogramContracts {
    private record Entry(int start, int height) {}

    private static int groups;
    private static long checks;

    /** Returns the largest unit-width rectangle without mutating heights. */
    static long largestRectangleAreaLong(int[] heights) {
        if (heights == null) {
            throw new IllegalArgumentException("heights must be non-null");
        }
        for (int height : heights) {
            if (height < 0) {
                throw new IllegalArgumentException("heights must be non-negative");
            }
        }

        Deque<Entry> increasing = new ArrayDeque<>();
        long best = 0L;
        for (int index = 0; index <= heights.length; index++) {
            int current = index == heights.length ? -1 : heights[index];
            int earliestStart = index;
            while (!increasing.isEmpty() && increasing.peek().height() >= current) {
                Entry resolved = increasing.pop();
                best = Math.max(best, (long) resolved.height() * (index - resolved.start()));
                earliestStart = resolved.start();
            }
            if (index < heights.length) {
                increasing.push(new Entry(earliestStart, current));
            }
        }
        return best;
    }

    /** LeetCode-compatible wrapper; official constraints keep the result in int. */
    static int largestRectangleArea(int[] heights) {
        return Math.toIntExact(largestRectangleAreaLong(heights));
    }

    /** Independent O(n^2) definition oracle. */
    private static long quadraticOracle(int[] heights) {
        long best = 0L;
        for (int left = 0; left < heights.length; left++) {
            int minimum = Integer.MAX_VALUE;
            for (int right = left; right < heights.length; right++) {
                minimum = Math.min(minimum, heights[right]);
                best = Math.max(best, (long) minimum * (right - left + 1));
            }
        }
        return best;
    }

    private static boolean rejects(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | ArithmeticException expected) {
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
        group("official six bars", largestRectangleArea(new int[] {2, 1, 5, 6, 2, 3}) == 10);
        group("official two bars", largestRectangleArea(new int[] {2, 4}) == 4);
        group("empty reusable input", largestRectangleArea(new int[] {}) == 0);
        group("zero singleton", largestRectangleArea(new int[] {0}) == 0);
        group("positive singleton", largestRectangleArea(new int[] {7}) == 7);
        group("increasing", largestRectangleArea(new int[] {1, 2, 3, 4}) == 6);
        group("decreasing", largestRectangleArea(new int[] {4, 3, 2, 1}) == 6);
        group("equal plateau", largestRectangleArea(new int[] {2, 2, 2}) == 6);
        group("zero separator", largestRectangleArea(new int[] {2, 0, 2}) == 2);
        group("shallow valley", largestRectangleArea(new int[] {2, 1, 2}) == 3);
        group("wide low rectangle", largestRectangleArea(new int[] {5, 1, 5, 1, 5}) == 5);
        group("cannot cross zero", largestRectangleArea(new int[] {4, 0, 4}) == 4);
        group("isolated tall bar", largestRectangleArea(new int[] {0, 0, 9, 0}) == 9);
        group("all zeroes", largestRectangleArea(new int[] {0, 0, 0}) == 0);
        group("null rejected", rejects(() -> largestRectangleAreaLong(null)));
        group("negative rejected", rejects(() -> largestRectangleAreaLong(new int[] {2, -1, 2})));

        int[] preserved = {2, 1, 5, 6, 2, 3};
        int[] snapshot = preserved.clone();
        largestRectangleAreaLong(preserved);
        group("input preserved", Arrays.equals(preserved, snapshot));

        long wide = 2L * Integer.MAX_VALUE;
        group("long extension", largestRectangleAreaLong(
                new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}) == wide);
        group("official wrapper detects extended overflow", rejects(() -> largestRectangleArea(
                new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE})));
        group("deterministic oracle parity", Arrays.asList(
                new int[] {2, 1, 5, 6, 2, 3}, new int[] {2, 4},
                new int[] {2, 2, 2}, new int[] {4, 0, 4})
                .stream().allMatch(values -> largestRectangleAreaLong(values) == quadraticOracle(values)));
    }

    private static int[] randomHistogram(Random random) {
        int[] heights = new int[random.nextInt(14)];
        for (int index = 0; index < heights.length; index++) {
            heights[index] = random.nextInt(26);
        }
        return heights;
    }

    private static int[] reverse(int[] values) {
        int[] result = values.clone();
        for (int left = 0, right = result.length - 1; left < right; left++, right--) {
            int value = result[left];
            result[left] = result[right];
            result[right] = value;
        }
        return result;
    }

    private static int[] scaleByTwo(int[] values) {
        int[] result = values.clone();
        for (int index = 0; index < result.length; index++) {
            result[index] *= 2;
        }
        return result;
    }

    private static int[] appendZero(int[] values) {
        return Arrays.copyOf(values, values.length + 1);
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x36_48495354L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] heights = randomHistogram(random);
            int[] snapshot = heights.clone();
            long expected = quadraticOracle(heights);
            long actual = largestRectangleAreaLong(heights);

            checks++;
            if (actual != expected) throw new AssertionError("oracle mismatch at trial " + trial);
            checks++;
            if (!Arrays.equals(heights, snapshot)) throw new AssertionError("input mutated at trial " + trial);
            checks++;
            if (largestRectangleAreaLong(heights.clone()) != actual) {
                throw new AssertionError("repeat mismatch at trial " + trial);
            }
            checks++;
            if (largestRectangleAreaLong(reverse(heights)) != actual) {
                throw new AssertionError("reversal mismatch at trial " + trial);
            }
            checks++;
            if (largestRectangleAreaLong(scaleByTwo(heights)) != 2L * actual) {
                throw new AssertionError("scaling mismatch at trial " + trial);
            }
            checks++;
            if (largestRectangleAreaLong(appendZero(heights)) != actual) {
                throw new AssertionError("zero-append mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) throw new AssertionError("expected 20 scenario groups, got " + groups);
        System.out.println("PARITY|AREA=10|PLATEAU=6|EMPTY=0|WIDE=4294967294|PRESERVED=true");
        System.out.println("LargestRectangleHistogramContracts: 20 scenario groups + 20000 seeded quadratic-oracle comparisons passed; checks=" + checks);
    }
}
