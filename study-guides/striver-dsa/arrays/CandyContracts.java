import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Candy. */
public final class CandyContracts {
    private static int groups;
    private static long checks;

    /** Returns the minimum candy total while preserving {@code ratings}. */
    static long minimumCandies(int[] ratings) {
        if (ratings == null || ratings.length == 0) {
            throw new IllegalArgumentException("ratings must be non-null and non-empty");
        }

        int[] left = new int[ratings.length];
        Arrays.fill(left, 1);
        for (int index = 1; index < ratings.length; index++) {
            if (ratings[index] > ratings[index - 1]) {
                left[index] = left[index - 1] + 1;
            }
        }

        long total = 0L;
        int right = 1;
        for (int index = ratings.length - 1; index >= 0; index--) {
            if (index < ratings.length - 1 && ratings[index] > ratings[index + 1]) {
                right++;
            } else {
                right = 1;
            }
            total += Math.max(left[index], right);
        }
        return total;
    }

    /** Official-compatible checked narrowing; official bounds fit in {@code int}. */
    static int candy(int[] ratings) {
        return Math.toIntExact(minimumCandies(ratings));
    }

    /** Independent O(n^2) fixed-point definition oracle for small arrays. */
    private static long relaxationOracle(int[] ratings) {
        int[] candies = new int[ratings.length];
        Arrays.fill(candies, 1);
        boolean changed;
        do {
            changed = false;
            for (int index = 1; index < ratings.length; index++) {
                if (ratings[index] > ratings[index - 1]
                        && candies[index] <= candies[index - 1]) {
                    candies[index] = candies[index - 1] + 1;
                    changed = true;
                }
                if (ratings[index - 1] > ratings[index]
                        && candies[index - 1] <= candies[index]) {
                    candies[index - 1] = candies[index] + 1;
                    changed = true;
                }
            }
        } while (changed);

        long total = 0L;
        for (int value : candies) total += value;
        return total;
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
        if (!condition) throw new AssertionError("group failed: " + name);
    }

    private static void deterministicEvidence() {
        group("official valley", candy(new int[] {1, 0, 2}) == 5);
        group("official equality", candy(new int[] {1, 2, 2}) == 4);
        group("singleton", candy(new int[] {9}) == 1);
        group("all equal", candy(new int[] {4, 4, 4, 4}) == 4);
        group("strictly increasing", candy(new int[] {1, 2, 3, 4}) == 10);
        group("strictly decreasing", candy(new int[] {4, 3, 2, 1}) == 10);
        group("mountain", candy(new int[] {1, 2, 3, 2, 1}) == 9);
        group("valley", candy(new int[] {3, 2, 1, 2, 3}) == 11);
        group("short peak", candy(new int[] {1, 3, 2}) == 4);
        group("equal breaks chain", candy(new int[] {1, 2, 2, 3}) == 6);
        group("repeated valleys", candy(new int[] {2, 1, 2, 1, 2}) == 8);
        group("zero is ordinary rating", candy(new int[] {0, 1, 0}) == 4);
        group("negative ratings reusable", candy(new int[] {-1, -2, -3}) == 6);
        group("integer extremes", candy(new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) == 6);
        group("long reusable total", minimumCandies(new int[] {5, 4, 3, 2, 1}) == 15L);

        int[] preserved = {1, 0, 2, 2, 3};
        int[] snapshot = preserved.clone();
        minimumCandies(preserved);
        group("input preserved", Arrays.equals(preserved, snapshot));
        group("null rejected", rejects(() -> minimumCandies(null)));
        group("empty rejected", rejects(() -> minimumCandies(new int[] {})));
        group("reversal invariant", minimumCandies(new int[] {1, 3, 2, 2, 5})
                == minimumCandies(new int[] {5, 2, 2, 3, 1}));
        group("deterministic oracle parity", Arrays.asList(
                new int[] {1, 0, 2}, new int[] {1, 2, 2},
                new int[] {1, 2, 3, 2, 1}, new int[] {3, 2, 1, 2, 3})
                .stream().allMatch(values -> minimumCandies(values) == relaxationOracle(values)));
    }

    private static int[] randomArray(Random random) {
        int[] values = new int[1 + random.nextInt(14)];
        for (int index = 0; index < values.length; index++) {
            values[index] = random.nextInt(11) - 5;
        }
        return values;
    }

    private static int[] reversed(int[] values) {
        int[] result = values.clone();
        for (int left = 0, right = result.length - 1; left < right; left++, right--) {
            int temporary = result[left];
            result[left] = result[right];
            result[right] = temporary;
        }
        return result;
    }

    private static int[] affine(int[] values) {
        int[] result = values.clone();
        for (int index = 0; index < result.length; index++) result[index] = 2 * result[index] + 17;
        return result;
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x39_43414E4459L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] values = randomArray(random);
            int[] snapshot = values.clone();
            long expected = relaxationOracle(values);
            long actual = minimumCandies(values);

            checks++;
            if (actual != expected) throw new AssertionError("oracle mismatch at trial " + trial);
            checks++;
            if (!Arrays.equals(values, snapshot)) throw new AssertionError("input mutated at trial " + trial);
            checks++;
            if (minimumCandies(values.clone()) != actual) {
                throw new AssertionError("repeat mismatch at trial " + trial);
            }
            checks++;
            if (minimumCandies(reversed(values)) != actual) {
                throw new AssertionError("reversal mismatch at trial " + trial);
            }
            checks++;
            if (minimumCandies(affine(values)) != actual) {
                throw new AssertionError("order-transform mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) throw new AssertionError("expected 20 scenario groups, got " + groups);
        System.out.println("PARITY|OFFICIAL=5|EQUALITY=4|MOUNTAIN=9|VALLEY=11|PRESERVED=true");
        System.out.println("CandyContracts: 20 scenario groups + 20000 seeded relaxation-oracle comparisons passed; checks=" + checks);
    }
}
