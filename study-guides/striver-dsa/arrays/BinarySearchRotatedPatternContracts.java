import java.util.Arrays;
import java.util.Random;

/** Executable contracts for the Section 16 binary-search / rotated family. */
public final class BinarySearchRotatedPatternContracts {
    private BinarySearchRotatedPatternContracts() {}

    /** Returns the first index whose value is at least target, or nums.length. */
    public static int lowerBound(int[] nums, int target) {
        requireNondecreasing(nums, true);
        int left = 0;
        int right = nums.length; // half-open candidate interval [left,right)
        while (left < right) {
            int middle = left + (right - left) / 2;
            if (nums[middle] >= target) right = middle;
            else left = middle + 1;
        }
        return left;
    }

    /** Returns the minimum of a non-empty rotation of a nondecreasing array. */
    public static int findMinimumWithDuplicates(int[] nums) {
        requireRotatedNondecreasing(nums);
        int left = 0;
        int right = nums.length - 1;
        while (left < right) {
            int middle = left + (right - left) / 2;
            if (nums[middle] < nums[right]) {
                right = middle;
            } else if (nums[middle] > nums[right]) {
                left = middle + 1;
            } else {
                right--; // equality proves only that this endpoint is redundant
            }
        }
        return nums[left];
    }

    /** Returns any strict local peak; conceptual out-of-bounds neighbors are -infinity. */
    public static int findPeakIndex(int[] nums) {
        requireAdjacentUnequal(nums);
        int left = 0;
        int right = nums.length - 1;
        while (left < right) {
            int middle = left + (right - left) / 2;
            if (nums[middle] < nums[middle + 1]) left = middle + 1;
            else right = middle;
        }
        return left;
    }

    /** Returns the sole unpaired value from a sorted odd-length array of adjacent pairs. */
    public static int singleNonDuplicate(int[] nums) {
        requireSingleAmongPairs(nums);
        int left = 0;
        int right = nums.length - 1;
        while (left < right) {
            int middle = left + (right - left) / 2;
            if ((middle & 1) == 1) middle--; // compare an even index with its mate
            if (nums[middle] == nums[middle + 1]) left = middle + 2;
            else right = middle;
        }
        return nums[left];
    }

    private static void requireNondecreasing(int[] nums, boolean emptyAllowed) {
        if (nums == null || (!emptyAllowed && nums.length == 0)) {
            throw new IllegalArgumentException("nums must be non-null"
                    + (emptyAllowed ? "" : " and non-empty"));
        }
        for (int i = 1; i < nums.length; i++) {
            if (nums[i - 1] > nums[i]) {
                throw new IllegalArgumentException("nums must be nondecreasing");
            }
        }
    }

    private static void requireRotatedNondecreasing(int[] nums) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        int descents = 0;
        for (int i = 1; i < nums.length; i++) {
            if (nums[i - 1] > nums[i]) descents++;
        }
        if (descents > 1 || (descents == 1 && nums[nums.length - 1] > nums[0])) {
            throw new IllegalArgumentException("nums must be a rotation of a nondecreasing array");
        }
    }

    private static void requireAdjacentUnequal(int[] nums) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        for (int i = 1; i < nums.length; i++) {
            if (nums[i - 1] == nums[i]) {
                throw new IllegalArgumentException("adjacent peak-search values must differ");
            }
        }
    }

    private static void requireSingleAmongPairs(int[] nums) {
        requireNondecreasing(nums, false);
        if ((nums.length & 1) == 0) {
            throw new IllegalArgumentException("paired domain must have odd length");
        }
        int singletons = 0;
        for (int i = 0; i < nums.length;) {
            int start = i;
            while (i < nums.length && nums[i] == nums[start]) i++;
            int count = i - start;
            if (count == 1) singletons++;
            else if (count != 2) {
                throw new IllegalArgumentException("each non-single value must occur exactly twice");
            }
        }
        if (singletons != 1) {
            throw new IllegalArgumentException("exactly one value must occur once");
        }
    }

    private static int lowerBoundOracle(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] >= target) return i;
        }
        return nums.length;
    }

    private static int minimumOracle(int[] nums) {
        int answer = nums[0];
        for (int value : nums) answer = Math.min(answer, value);
        return answer;
    }

    private static boolean isStrictPeak(int[] nums, int index) {
        return index >= 0 && index < nums.length
                && (index == 0 || nums[index] > nums[index - 1])
                && (index == nums.length - 1 || nums[index] > nums[index + 1]);
    }

    private static int singleOracle(int[] nums) {
        int answer = 0;
        for (int value : nums) answer ^= value;
        return answer;
    }

    private static int[] sortedCase(Random random) {
        int[] values = new int[random.nextInt(33)];
        int current = random.nextInt(21) - 10;
        for (int i = 0; i < values.length; i++) {
            current += random.nextInt(4);
            values[i] = current;
        }
        return values;
    }

    private static int[] rotatedCase(Random random) {
        int length = 1 + random.nextInt(32);
        int[] sorted = new int[length];
        int current = random.nextInt(21) - 10;
        for (int i = 0; i < length; i++) {
            current += random.nextInt(3);
            sorted[i] = current;
        }
        int pivot = random.nextInt(length);
        int[] rotated = new int[length];
        for (int i = 0; i < length; i++) rotated[i] = sorted[(i + pivot) % length];
        return rotated;
    }

    private static int[] peakCase(Random random) {
        int[] values = new int[1 + random.nextInt(32)];
        values[0] = random.nextInt(41) - 20;
        for (int i = 1; i < values.length; i++) {
            int delta;
            do delta = random.nextInt(11) - 5; while (delta == 0);
            values[i] = values[i - 1] + delta;
        }
        return values;
    }

    private static int[] pairedCase(Random random) {
        int pairs = random.nextInt(16);
        int singletonRank = random.nextInt(pairs + 1);
        int[] values = new int[pairs * 2 + 1];
        int index = 0;
        int value = random.nextInt(11) - 5;
        for (int rank = 0; rank <= pairs; rank++) {
            value += 1 + random.nextInt(3);
            values[index++] = value;
            if (rank != singletonRank) values[index++] = value;
        }
        return values;
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
        expect(lowerBound(new int[] {1,3,5,6}, 5) == 2, "insert found"); groups++;
        expect(lowerBound(new int[] {1,3,5,6}, 2) == 1, "insert middle"); groups++;
        expect(lowerBound(new int[] {1,3,5,6}, 7) == 4, "insert end"); groups++;
        expect(lowerBound(new int[] {}, 7) == 0, "insert empty reusable contract"); groups++;
        expect(findMinimumWithDuplicates(new int[] {3,4,5,1,2}) == 1, "rotated minimum"); groups++;
        expect(findMinimumWithDuplicates(new int[] {2,2,2,0,1,2}) == 0, "duplicate minimum"); groups++;
        expect(findMinimumWithDuplicates(new int[] {11,13,15,17}) == 11, "unrotated minimum"); groups++;
        expect(findMinimumWithDuplicates(new int[] {1}) == 1, "singleton minimum"); groups++;
        expect(findPeakIndex(new int[] {1,2,3,1}) == 2, "peak official one"); groups++;
        int peak = findPeakIndex(new int[] {1,2,1,3,5,6,4});
        expect(peak == 1 || peak == 5, "peak official two"); groups++;
        expect(findPeakIndex(new int[] {4,3,2,1}) == 0, "peak left boundary"); groups++;
        expect(findPeakIndex(new int[] {1}) == 0, "peak singleton"); groups++;
        expect(singleNonDuplicate(new int[] {1,1,2,3,3,4,4,8,8}) == 2, "single official one"); groups++;
        expect(singleNonDuplicate(new int[] {3,3,7,7,10,11,11}) == 10, "single official two"); groups++;
        expect(singleNonDuplicate(new int[] {9}) == 9, "single singleton"); groups++;
        expect(singleNonDuplicate(new int[] {-3,-3,-1,2,2}) == -1, "single signed"); groups++;
        expectRejected(() -> lowerBound(new int[] {2,1}, 1), "unsorted lower bound"); groups++;
        expectRejected(() -> findMinimumWithDuplicates(new int[] {3,1,2,0}), "invalid rotation"); groups++;
        expectRejected(() -> findPeakIndex(new int[] {1,1}), "peak equality"); groups++;
        expectRejected(() -> singleNonDuplicate(new int[] {1,1,2,2}), "even paired input"); groups++;

        Random random = new Random(0xB1A2_5EEDL);
        int trials = 20_000;
        int checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int[] sorted = sortedCase(random);
            int[] rotated = rotatedCase(random);
            int[] peaks = peakCase(random);
            int[] paired = pairedCase(random);
            int[] sortedBefore = sorted.clone();
            int[] rotatedBefore = rotated.clone();
            int[] peaksBefore = peaks.clone();
            int[] pairedBefore = paired.clone();
            int target = random.nextInt(61) - 30;

            expect(lowerBound(sorted, target) == lowerBoundOracle(sorted, target), "lower oracle"); checks++;
            expect(findMinimumWithDuplicates(rotated) == minimumOracle(rotated), "minimum oracle"); checks++;
            expect(isStrictPeak(peaks, findPeakIndex(peaks)), "peak oracle predicate"); checks++;
            expect(singleNonDuplicate(paired) == singleOracle(paired), "single oracle"); checks++;
            expect(Arrays.equals(sortedBefore, sorted)
                    && Arrays.equals(rotatedBefore, rotated)
                    && Arrays.equals(peaksBefore, peaks)
                    && Arrays.equals(pairedBefore, paired), "input preservation"); checks++;
        }

        System.out.println("BinarySearchRotatedPatternContracts: " + groups
                + " deterministic groups + " + trials + " seeded trials; "
                + checks + " oracle/preservation checks passed.");
    }
}
