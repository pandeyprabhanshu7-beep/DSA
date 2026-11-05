import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;

/** Dependency-free executable contracts for Shortest Subarray with Sum at Least K. */
public final class ShortestSubarrayAtLeastKContracts {
    private static int groups;
    private static long checks;

    /** Returns the shortest non-empty qualifying length, or -1, without mutating nums. */
    static int shortestSubarray(int[] nums, long k) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        if (k <= 0L) {
            throw new IllegalArgumentException("k must be positive");
        }

        long[] prefix = new long[nums.length + 1];
        for (int index = 0; index < nums.length; index++) {
            prefix[index + 1] = prefix[index] + nums[index];
        }

        Deque<Integer> increasing = new ArrayDeque<>();
        int best = nums.length + 1;
        for (int right = 0; right <= nums.length; right++) {
            while (!increasing.isEmpty()
                    && prefix[right] - prefix[increasing.peekFirst()] >= k) {
                best = Math.min(best, right - increasing.removeFirst());
            }
            while (!increasing.isEmpty()
                    && prefix[increasing.peekLast()] >= prefix[right]) {
                increasing.removeLast();
            }
            increasing.addLast(right);
        }
        return best <= nums.length ? best : -1;
    }

    /** Independent O(n^2) definition oracle. */
    private static int quadraticOracle(int[] nums, long k) {
        int best = nums.length + 1;
        for (int left = 0; left < nums.length; left++) {
            long sum = 0L;
            for (int right = left; right < nums.length; right++) {
                sum += nums[right];
                if (sum >= k) {
                    best = Math.min(best, right - left + 1);
                }
            }
        }
        return best <= nums.length ? best : -1;
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
        group("official singleton", shortestSubarray(new int[] {1}, 1L) == 1);
        group("official absent", shortestSubarray(new int[] {1, 2}, 4L) == -1);
        group("official signed example", shortestSubarray(new int[] {2, -1, 2}, 3L) == 3);
        group("front and back pops", shortestSubarray(new int[] {84, -37, 32, 40, 95}, 167L) == 3);
        group("qualifying singleton later", shortestSubarray(new int[] {-8, 7}, 7L) == 1);
        group("positive pair", shortestSubarray(new int[] {1, 2, 3}, 5L) == 2);
        group("equal prefixes", shortestSubarray(new int[] {0, 0, 5}, 5L) == 1);
        group("negative prefix discarded", shortestSubarray(new int[] {-5, 6}, 6L) == 1);
        group("all negative absent", shortestSubarray(new int[] {-1, -2, -3}, 1L) == -1);
        group("zeros absent", shortestSubarray(new int[] {0, 0, 0}, 1L) == -1);
        group("duplicate positives", shortestSubarray(new int[] {1, 1, 1}, 2L) == 2);
        group("late singleton beats prefix", shortestSubarray(new int[] {3, -2, 5}, 4L) == 1);
        group("near miss", shortestSubarray(new int[] {2, -1, 2}, 4L) == -1);
        group("long prefix extension", shortestSubarray(
                new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE},
                2L * Integer.MAX_VALUE) == 2);

        int[] preserved = {84, -37, 32, 40, 95};
        int[] snapshot = preserved.clone();
        shortestSubarray(preserved, 167L);
        group("input preserved", Arrays.equals(preserved, snapshot));
        group("null rejected", rejects(() -> shortestSubarray(null, 1L)));
        group("empty rejected", rejects(() -> shortestSubarray(new int[] {}, 1L)));
        group("zero k rejected", rejects(() -> shortestSubarray(new int[] {1}, 0L)));
        group("negative k rejected", rejects(() -> shortestSubarray(new int[] {1}, -1L)));
        group("deterministic oracle parity", Arrays.asList(
                new int[] {1}, new int[] {1, 2}, new int[] {2, -1, 2},
                new int[] {84, -37, 32, 40, 95})
                .stream().allMatch(values -> shortestSubarray(values, 3L)
                        == quadraticOracle(values, 3L)));
    }

    private static int[] randomArray(Random random) {
        int[] values = new int[1 + random.nextInt(13)];
        for (int index = 0; index < values.length; index++) {
            values[index] = random.nextInt(31) - 15;
        }
        return values;
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

    private static void randomizedEvidence() {
        Random random = new Random(0x37_53484F52L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] values = randomArray(random);
            int[] snapshot = values.clone();
            long k = 1L + random.nextInt(60);
            int expected = quadraticOracle(values, k);
            int actual = shortestSubarray(values, k);

            checks++;
            if (actual != expected) throw new AssertionError("oracle mismatch at trial " + trial);
            checks++;
            if (!Arrays.equals(values, snapshot)) throw new AssertionError("input mutated at trial " + trial);
            checks++;
            if (shortestSubarray(values.clone(), k) != actual) {
                throw new AssertionError("repeat mismatch at trial " + trial);
            }
            checks++;
            if (shortestSubarray(reverse(values), k) != actual) {
                throw new AssertionError("reversal mismatch at trial " + trial);
            }
            checks++;
            if (shortestSubarray(scaleByTwo(values), 2L * k) != actual) {
                throw new AssertionError("scaling mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) throw new AssertionError("expected 20 scenario groups, got " + groups);
        System.out.println("PARITY|OFFICIAL=3|ABSENT=-1|SIGNED=3|MIXED=3|PRESERVED=true");
        System.out.println("ShortestSubarrayAtLeastKContracts: 20 scenario groups + 20000 seeded quadratic-oracle comparisons passed; checks=" + checks);
    }
}
