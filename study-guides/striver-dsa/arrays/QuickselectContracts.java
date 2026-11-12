import java.util.Arrays;
import java.util.Random;
import java.util.random.RandomGenerator;

public final class QuickselectContracts {
    private static final long SEED = 0x51EC_7A11_0A5EL;

    private QuickselectContracts() {}

    /** Returns the kth largest value without modifying the caller's array. */
    static int kthLargest(int[] values, int k, long seed) {
        if (values == null) throw new IllegalArgumentException("values must be non-null");
        if (k < 1 || k > values.length) throw new IllegalArgumentException("k must be in [1,n]");
        return kthLargestInPlace(values.clone(), k, new Random(seed));
    }

    /**
     * In-place Quickselect core. Equal values occupy one three-way partition
     * band, so an equal-heavy input does not repeatedly discard one element.
     */
    static int kthLargestInPlace(int[] values, int k, RandomGenerator random) {
        if (values == null) throw new IllegalArgumentException("values must be non-null");
        if (k < 1 || k > values.length) throw new IllegalArgumentException("k must be in [1,n]");
        if (random == null) throw new IllegalArgumentException("random must be non-null");

        int target = k - 1;
        int left = 0;
        int right = values.length - 1;
        while (left <= right) {
            int pivot = values[random.nextInt(left, right + 1)];
            int greaterEnd = left;
            int scan = left;
            int lessStart = right;

            while (scan <= lessStart) {
                if (values[scan] > pivot) {
                    swap(values, greaterEnd++, scan++);
                } else if (values[scan] < pivot) {
                    swap(values, scan, lessStart--);
                } else {
                    scan++;
                }
            }

            if (target < greaterEnd) {
                right = greaterEnd - 1;
            } else if (target > lessStart) {
                left = lessStart + 1;
            } else {
                return pivot;
            }
        }
        throw new AssertionError("validated target must be selected");
    }

    private static void swap(int[] values, int first, int second) {
        int temporary = values[first];
        values[first] = values[second];
        values[second] = temporary;
    }

    private static int sortedOracle(int[] values, int k) {
        int[] copy = values.clone();
        Arrays.sort(copy);
        return copy[copy.length - k];
    }

    private static void expect(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectInvalid(int[] values, int k, RandomGenerator random, String label) {
        try {
            kthLargestInPlace(values, k, random);
            throw new AssertionError(label + " should have failed");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void checkRank(int[] values, int k, int selected, String label) {
        int greater = 0;
        int greaterOrEqual = 0;
        for (int value : values) {
            if (value > selected) greater++;
            if (value >= selected) greaterOrEqual++;
        }
        if (!(greater < k && k <= greaterOrEqual)) {
            throw new AssertionError(label + " violates kth-largest rank");
        }
    }

    private static int deterministicChecks() {
        int groups = 0;
        expect(5, kthLargest(new int[] {3, 2, 1, 5, 6, 4}, 2, 1), "official one"); groups++;
        expect(4, kthLargest(new int[] {3, 2, 3, 1, 2, 4, 5, 5, 6}, 4, 2), "official two"); groups++;
        expect(7, kthLargest(new int[] {7}, 1, 3), "singleton"); groups++;
        expect(9, kthLargest(new int[] {9, 1, 4}, 1, 4), "maximum"); groups++;
        expect(1, kthLargest(new int[] {9, 1, 4}, 3, 5), "minimum"); groups++;
        expect(3, kthLargest(new int[] {1, 2, 3, 4, 5}, 3, 6), "ascending"); groups++;
        expect(3, kthLargest(new int[] {5, 4, 3, 2, 1}, 3, 7), "descending"); groups++;
        expect(4, kthLargest(new int[] {4, 4, 4, 4}, 3, 8), "all equal"); groups++;
        expect(-7, kthLargest(new int[] {-7, -7, -3, -10}, 3, 9), "negative duplicates"); groups++;
        expect(0, kthLargest(new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}, 2, 10), "extremes"); groups++;
        int[] preserved = {8, 2, 8, 1};
        int[] before = preserved.clone();
        expect(8, kthLargest(preserved, 2, 11), "copy wrapper");
        if (!Arrays.equals(before, preserved)) throw new AssertionError("copy wrapper mutated input"); groups++;
        int[] inPlace = {3, 1, 2, 3};
        int[] sortedBefore = inPlace.clone();
        Arrays.sort(sortedBefore);
        expect(3, kthLargestInPlace(inPlace, 2, new Random(12)), "in-place answer");
        int[] sortedAfter = inPlace.clone();
        Arrays.sort(sortedAfter);
        if (!Arrays.equals(sortedBefore, sortedAfter)) throw new AssertionError("in-place multiset changed"); groups++;
        expectInvalid(null, 1, new Random(1), "null values"); groups++;
        expectInvalid(new int[0], 1, new Random(1), "empty values"); groups++;
        expectInvalid(new int[] {1}, 0, new Random(1), "zero k"); groups++;
        expectInvalid(new int[] {1}, -1, new Random(1), "negative k"); groups++;
        expectInvalid(new int[] {1}, 2, new Random(1), "large k"); groups++;
        expectInvalid(new int[] {1}, 1, null, "null random"); groups++;
        int[] duplicates = {2, 1, 2, 1, 2};
        int answer = kthLargest(duplicates, 4, 13);
        checkRank(duplicates, 4, answer, "duplicate rank"); groups++;
        expect(sortedOracle(new int[] {6, 5, 4, 3, 2, 1}, 5),
                kthLargest(new int[] {1, 2, 3, 4, 5, 6}, 5, 14), "permutation"); groups++;
        return groups;
    }

    public static void main(String[] args) {
        int groups = deterministicChecks();
        if (groups != 20) throw new AssertionError("expected 20 deterministic groups");

        Random random = new Random(SEED);
        int trials = 20_000;
        int checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int length = 1 + random.nextInt(80);
            int[] values = new int[length];
            for (int i = 0; i < length; i++) values[i] = random.nextInt(201) - 100;
            int k = 1 + random.nextInt(length);
            int expected = sortedOracle(values, k);
            int[] before = values.clone();

            int selected = kthLargest(values, k, random.nextLong());
            expect(expected, selected, "oracle trial " + trial); checks++;
            if (!Arrays.equals(before, values)) throw new AssertionError("wrapper mutation trial " + trial);
            checks++;

            int[] reversed = values.clone();
            for (int left = 0, right = reversed.length - 1; left < right; left++, right--) {
                swap(reversed, left, right);
            }
            expect(expected, kthLargest(reversed, k, random.nextLong()), "reversal trial " + trial); checks++;
            checkRank(values, k, selected, "rank trial " + trial); checks++;

            int[] inPlace = values.clone();
            int inPlaceAnswer = kthLargestInPlace(inPlace, k, new Random(random.nextLong()));
            int[] sortedAfter = inPlace.clone();
            Arrays.sort(sortedAfter);
            int[] sortedBefore = values.clone();
            Arrays.sort(sortedBefore);
            if (inPlaceAnswer != expected || !Arrays.equals(sortedBefore, sortedAfter)) {
                throw new AssertionError("in-place trial " + trial);
            }
            checks++;
        }

        System.out.println("QuickselectContracts: " + groups + " scenario groups + "
                + trials + " seeded sorted-oracle trials passed; checks=" + checks);
    }
}
