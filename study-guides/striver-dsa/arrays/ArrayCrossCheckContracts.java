import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Executable contracts for the final Markdown-only cross-check supplement. */
public class ArrayCrossCheckContracts {
    private static int groups;

    static long countExactSubarrays(int[] values, long target) {
        require(values != null, "values must not be null");
        Map<Long, Long> frequencies = new HashMap<>();
        frequencies.put(0L, 1L);
        long prefix = 0L;
        long count = 0L;
        for (int value : values) {
            prefix += value;
            count += frequencies.getOrDefault(prefix - target, 0L);
            frequencies.merge(prefix, 1L, Long::sum);
        }
        return count;
    }

    static boolean hasSortedPair(int[] sorted, long target) {
        require(sorted != null, "sorted must not be null");
        for (int i = 1; i < sorted.length; i++) {
            require(sorted[i - 1] <= sorted[i], "input must be nondecreasing");
        }
        int left = 0;
        int right = sorted.length - 1;
        while (left < right) {
            long sum = (long) sorted[left] + sorted[right];
            if (sum == target) return true;
            if (sum < target) left++;
            else right--;
        }
        return false;
    }

    static long[] longPrefixSums(int[] values) {
        require(values != null, "values must not be null");
        long[] result = new long[values.length + 1];
        for (int i = 0; i < values.length; i++) {
            result[i + 1] = result[i] + values[i];
        }
        return result;
    }

    static int[] maxSlidingWindow(int[] values, int width) {
        require(values != null, "values must not be null");
        require(width >= 1 && width <= values.length, "width must be in [1,n]");
        int[] result = new int[values.length - width + 1];
        ArrayDeque<Integer> candidates = new ArrayDeque<>();
        for (int right = 0; right < values.length; right++) {
            while (!candidates.isEmpty() && candidates.peekFirst() <= right - width) {
                candidates.removeFirst();
            }
            while (!candidates.isEmpty() && values[candidates.peekLast()] <= values[right]) {
                candidates.removeLast();
            }
            candidates.addLast(right);
            if (right >= width - 1) result[right - width + 1] = values[candidates.peekFirst()];
        }
        return result;
    }

    private static long countExactSubarraysOracle(int[] values, long target) {
        long count = 0L;
        for (int left = 0; left < values.length; left++) {
            long sum = 0L;
            for (int right = left; right < values.length; right++) {
                sum += values[right];
                if (sum == target) count++;
            }
        }
        return count;
    }

    private static boolean hasPairOracle(int[] values, long target) {
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                if ((long) values[i] + values[j] == target) return true;
            }
        }
        return false;
    }

    private static long[] prefixOracle(int[] values) {
        long[] result = new long[values.length + 1];
        BigInteger sum = BigInteger.ZERO;
        for (int i = 0; i < values.length; i++) {
            sum = sum.add(BigInteger.valueOf(values[i]));
            result[i + 1] = sum.longValueExact();
        }
        return result;
    }

    private static int[] windowOracle(int[] values, int width) {
        int[] result = new int[values.length - width + 1];
        for (int left = 0; left + width <= values.length; left++) {
            int best = values[left];
            for (int i = left + 1; i < left + width; i++) best = Math.max(best, values[i]);
            result[left] = best;
        }
        return result;
    }

    private static void deterministicGroups() {
        exactGroup(new int[]{2, -1, 3}, 1, 1);
        exactGroup(new int[]{2, -2, 2}, 2, 3);
        exactGroup(new int[]{0, 0, 0}, 0, 6);
        exactGroup(new int[]{Integer.MAX_VALUE, 1, -Integer.MAX_VALUE}, 1, 2);
        exactGroup(new int[]{}, 0, 0);

        pairGroup(new int[]{1, 2, 4, 7, 11}, 9, true);
        pairGroup(new int[]{-5, -1, 3, 8}, 7, true);
        pairGroup(new int[]{1, 1, 1}, 3, false);
        pairGroup(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 4294967294L, true);
        pairGroup(new int[]{}, 0, false);

        prefixGroup(new int[]{Integer.MAX_VALUE, 1, -Integer.MAX_VALUE});
        prefixGroup(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE});
        prefixGroup(new int[]{5});
        prefixGroup(new int[]{0, 0, 0});
        prefixGroup(new int[]{});

        windowGroup(new int[]{5, 5, 1}, 2, new int[]{5, 5});
        windowGroup(new int[]{1, 3, -1, -3, 5, 3, 6, 7}, 3, new int[]{3, 3, 5, 5, 6, 7});
        windowGroup(new int[]{-4}, 1, new int[]{-4});
        windowGroup(new int[]{4, 2, 12, 3}, 4, new int[]{12});
        invalidGroup();
    }

    private static void exactGroup(int[] values, long target, long expected) {
        int[] before = values.clone();
        check(countExactSubarrays(values, target) == expected, "exact deterministic mismatch");
        check(Arrays.equals(values, before), "exact mutated input");
        groups++;
    }

    private static void pairGroup(int[] values, long target, boolean expected) {
        int[] before = values.clone();
        check(hasSortedPair(values, target) == expected, "pair deterministic mismatch");
        check(Arrays.equals(values, before), "pair mutated input");
        groups++;
    }

    private static void prefixGroup(int[] values) {
        int[] before = values.clone();
        check(Arrays.equals(longPrefixSums(values), prefixOracle(values)), "prefix deterministic mismatch");
        check(Arrays.equals(values, before), "prefix mutated input");
        groups++;
    }

    private static void windowGroup(int[] values, int width, int[] expected) {
        int[] before = values.clone();
        check(Arrays.equals(maxSlidingWindow(values, width), expected), "window deterministic mismatch");
        check(Arrays.equals(values, before), "window mutated input");
        groups++;
    }

    private static void invalidGroup() {
        expectIllegal(() -> hasSortedPair(new int[]{2, 1}, 3));
        expectIllegal(() -> maxSlidingWindow(new int[]{1}, 0));
        expectIllegal(() -> maxSlidingWindow(new int[]{1}, 2));
        expectIllegal(() -> countExactSubarrays(null, 0));
        groups++;
    }

    private static void randomizedChecks() {
        Random random = new Random(0xC2055C0DEL);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = 1 + random.nextInt(12);
            int[] values = new int[n];
            for (int i = 0; i < n; i++) values[i] = random.nextInt(31) - 15;
            int[] before = values.clone();
            long target = random.nextInt(61) - 30;

            check(countExactSubarrays(values, target) == countExactSubarraysOracle(values, target),
                    "random exact mismatch");
            check(Arrays.equals(values, before), "random exact mutation");

            int[] sorted = values.clone();
            Arrays.sort(sorted);
            int[] sortedBefore = sorted.clone();
            check(hasSortedPair(sorted, target) == hasPairOracle(sorted, target), "random pair mismatch");
            check(Arrays.equals(sorted, sortedBefore), "random pair mutation");

            check(Arrays.equals(longPrefixSums(values), prefixOracle(values)), "random prefix mismatch");
            check(Arrays.equals(values, before), "random prefix mutation");

            int width = 1 + random.nextInt(n);
            check(Arrays.equals(maxSlidingWindow(values, width), windowOracle(values, width)),
                    "random window mismatch");
            check(Arrays.equals(values, before), "random window mutation");
        }
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
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
        randomizedChecks();
        System.out.println("PASS groups=" + groups
                + " trials=20000 oracle/preservation checks=80000");
    }
}
