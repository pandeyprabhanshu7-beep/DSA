import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Count Inversions. */
public final class CountInversionsContracts {
    private static int groups;
    private static long checks;

    /** Returns the inversion count without mutating {@code values}. */
    static long countInversions(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("values must be non-null");
        }
        int[] working = values.clone();
        return countAndSort(working, new int[working.length], 0, working.length);
    }

    /** Counts and sorts the half-open range [low, high). */
    private static long countAndSort(int[] values, int[] buffer, int low, int high) {
        if (high - low <= 1) {
            return 0L;
        }
        int middle = low + (high - low) / 2;
        long count = countAndSort(values, buffer, low, middle)
                + countAndSort(values, buffer, middle, high);

        int left = low;
        int right = middle;
        int output = low;
        while (left < middle && right < high) {
            if (values[left] <= values[right]) {
                buffer[output++] = values[left++];
            } else {
                buffer[output++] = values[right++];
                count += middle - left;
            }
        }
        while (left < middle) {
            buffer[output++] = values[left++];
        }
        while (right < high) {
            buffer[output++] = values[right++];
        }
        System.arraycopy(buffer, low, values, low, high - low);
        return count;
    }

    /** Independent O(n^2) definition oracle. */
    private static long quadraticOracle(int[] values) {
        long count = 0L;
        for (int left = 0; left < values.length; left++) {
            for (int right = left + 1; right < values.length; right++) {
                if (values[left] > values[right]) {
                    count++;
                }
            }
        }
        return count;
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
        group("canonical", countInversions(new int[] {2, 4, 1, 3, 5}) == 3L);
        group("HackerRank sorted duplicates",
                countInversions(new int[] {1, 1, 1, 2, 2}) == 0L);
        group("HackerRank mixed", countInversions(new int[] {2, 1, 3, 1, 2}) == 4L);
        group("empty", countInversions(new int[] {}) == 0L);
        group("singleton", countInversions(new int[] {7}) == 0L);
        group("two sorted", countInversions(new int[] {1, 2}) == 0L);
        group("two reversed", countInversions(new int[] {2, 1}) == 1L);
        group("already sorted", countInversions(new int[] {-3, -1, 0, 5}) == 0L);
        group("reverse sorted", countInversions(new int[] {5, 4, 3, 2, 1}) == 10L);
        group("all equal", countInversions(new int[] {4, 4, 4, 4}) == 0L);
        group("equal values do not count", countInversions(new int[] {3, 3, 1}) == 2L);
        group("negative values", countInversions(new int[] {-1, -3, -2}) == 2L);
        group("integer extremes", countInversions(
                new int[] {Integer.MAX_VALUE, 0, Integer.MIN_VALUE}) == 3L);
        group("alternating", countInversions(new int[] {3, 1, 3, 1}) == 3L);
        group("one displaced minimum", countInversions(new int[] {2, 3, 4, 1}) == 3L);

        int[] preserved = {2, 4, 1, 3, 5};
        int[] snapshot = preserved.clone();
        countInversions(preserved);
        group("input preserved", Arrays.equals(preserved, snapshot));
        group("null rejected", rejects(() -> countInversions(null)));

        int size = 100_000;
        int[] descending = new int[size];
        for (int index = 0; index < size; index++) {
            descending[index] = size - index;
        }
        long maximum = (long) size * (size - 1L) / 2L;
        group("long result exceeds int", countInversions(descending) == maximum
                && maximum == 4_999_950_000L);

        int[] relation = {4, 1, 4, 2, 3};
        int unequalPairs = 0;
        for (int left = 0; left < relation.length; left++) {
            for (int right = left + 1; right < relation.length; right++) {
                if (relation[left] != relation[right]) unequalPairs++;
            }
        }
        int[] reversed = relation.clone();
        for (int left = 0, right = reversed.length - 1; left < right; left++, right--) {
            int value = reversed[left];
            reversed[left] = reversed[right];
            reversed[right] = value;
        }
        group("reverse complement over unequal pairs",
                countInversions(relation) + countInversions(reversed) == unequalPairs);
        group("oracle parity on deterministic catalog", Arrays.asList(
                new int[] {}, new int[] {1}, new int[] {2, 1},
                new int[] {2, 4, 1, 3, 5}, new int[] {3, 3, 1})
                .stream().allMatch(values -> countInversions(values) == quadraticOracle(values)));
    }

    private static int[] randomArray(Random random, int length) {
        int[] values = new int[length];
        for (int index = 0; index < length; index++) {
            int draw = random.nextInt(30);
            if (draw == 0) values[index] = Integer.MIN_VALUE;
            else if (draw == 1) values[index] = Integer.MAX_VALUE;
            else values[index] = random.nextInt(15) - 7;
        }
        return values;
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x33_494E56L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] values = randomArray(random, random.nextInt(25));
            int[] snapshot = values.clone();
            long expected = quadraticOracle(values);
            long actual = countInversions(values);

            checks++;
            if (actual != expected) {
                throw new AssertionError("oracle mismatch at trial " + trial);
            }
            checks++;
            if (!Arrays.equals(values, snapshot)) {
                throw new AssertionError("input mutated at trial " + trial);
            }
            checks++;
            long maximum = (long) values.length * (values.length - 1L) / 2L;
            if (actual < 0L || actual > maximum) {
                throw new AssertionError("count outside legal range at trial " + trial);
            }
            checks++;
            int[] sorted = values.clone();
            Arrays.sort(sorted);
            if (countInversions(sorted) != 0L) {
                throw new AssertionError("sorted array has inversions at trial " + trial);
            }
            checks++;
            if (countInversions(values.clone()) != actual) {
                throw new AssertionError("repeat mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|CANONICAL=3|HACKERRANK=4|REVERSE5=10|MAX100K=4999950000|PRESERVED=true");
        System.out.println("CountInversionsContracts: 20 scenario groups + 20000 seeded quadratic-oracle comparisons passed; checks=" + checks);
    }
}
