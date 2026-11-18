import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Executable contracts for the Section 16 index-placement / marking family. */
public final class IndexPlacementMarkingPatternContracts {
    private IndexPlacementMarkingPatternContracts() {}

    public static int missingNumberByPlacement(int[] nums) {
        requireMissingNumberDomain(nums);
        int[] work = nums.clone();
        int i = 0;
        while (i < work.length) {
            int value = work[i];
            if (value < work.length && work[value] != value) {
                swap(work, i, value);
            } else {
                i++;
            }
        }
        for (i = 0; i < work.length; i++) {
            if (work[i] != i) return i;
        }
        return work.length;
    }

    public static int[] disappearedNumbersByMarking(int[] nums) {
        requireOneToN(nums, false);
        int[] work = nums.clone();
        for (int encoded : work) {
            int index = Math.abs(encoded) - 1;
            if (work[index] > 0) work[index] = -work[index];
        }
        int[] result = new int[work.length];
        int size = 0;
        for (int i = 0; i < work.length; i++) {
            if (work[i] > 0) result[size++] = i + 1;
        }
        return Arrays.copyOf(result, size);
    }

    public static int[] duplicatesByMarking(int[] nums) {
        requireOneToN(nums, true);
        int[] work = nums.clone();
        int[] result = new int[work.length / 2];
        int size = 0;
        for (int encoded : work) {
            int value = Math.abs(encoded);
            int index = value - 1;
            if (work[index] < 0) {
                result[size++] = value;
            } else {
                work[index] = -work[index];
            }
        }
        int[] answer = Arrays.copyOf(result, size);
        Arrays.sort(answer); // deterministic reusable contract; the problem permits any order.
        return answer;
    }

    public static int[] setMismatchByPlacement(int[] nums) {
        requireSetMismatch(nums);
        int[] work = nums.clone();
        int i = 0;
        while (i < work.length) {
            int target = work[i] - 1;
            if (work[i] != work[target]) {
                swap(work, i, target);
            } else {
                i++;
            }
        }
        for (i = 0; i < work.length; i++) {
            if (work[i] != i + 1) return new int[] {work[i], i + 1};
        }
        throw new IllegalStateException("validated mismatch was not found");
    }

    private static void requireMissingNumberDomain(int[] nums) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        boolean[] seen = new boolean[nums.length + 1];
        for (int value : nums) {
            if (value < 0 || value > nums.length || seen[value]) {
                throw new IllegalArgumentException("values must be distinct and in [0,n]");
            }
            seen[value] = true;
        }
    }

    private static void requireOneToN(int[] nums, boolean atMostTwice) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        int[] counts = atMostTwice ? new int[nums.length + 1] : null;
        for (int value : nums) {
            if (value < 1 || value > nums.length) {
                throw new IllegalArgumentException("values must be in [1,n]");
            }
            if (atMostTwice && ++counts[value] > 2) {
                throw new IllegalArgumentException("duplicate drill permits each value at most twice");
            }
        }
    }

    private static void requireSetMismatch(int[] nums) {
        if (nums == null || nums.length < 2) {
            throw new IllegalArgumentException("nums must contain at least two values");
        }
        int[] counts = new int[nums.length + 1];
        for (int value : nums) {
            if (value < 1 || value > nums.length) {
                throw new IllegalArgumentException("values must be in [1,n]");
            }
            counts[value]++;
        }
        int missing = 0;
        int duplicated = 0;
        for (int value = 1; value <= nums.length; value++) {
            if (counts[value] == 0) missing++;
            else if (counts[value] == 2) duplicated++;
            else if (counts[value] != 1) {
                throw new IllegalArgumentException("exactly one value must occur twice");
            }
        }
        if (missing != 1 || duplicated != 1) {
            throw new IllegalArgumentException("exactly one value must be missing and one duplicated");
        }
    }

    private static void swap(int[] values, int left, int right) {
        int temporary = values[left];
        values[left] = values[right];
        values[right] = temporary;
    }

    private static int missingOracle(int[] nums) {
        boolean[] seen = new boolean[nums.length + 1];
        for (int value : nums) seen[value] = true;
        for (int value = 0; value <= nums.length; value++) {
            if (!seen[value]) return value;
        }
        throw new AssertionError("oracle domain invalid");
    }

    private static int[] disappearedOracle(int[] nums) {
        int[] counts = counts(nums);
        return valuesWithCount(counts, 0);
    }

    private static int[] duplicatesOracle(int[] nums) {
        int[] counts = counts(nums);
        return valuesWithCount(counts, 2);
    }

    private static int[] setMismatchOracle(int[] nums) {
        int[] counts = counts(nums);
        int duplicate = -1;
        int missing = -1;
        for (int value = 1; value < counts.length; value++) {
            if (counts[value] == 0) missing = value;
            if (counts[value] == 2) duplicate = value;
        }
        return new int[] {duplicate, missing};
    }

    private static int[] counts(int[] nums) {
        int[] counts = new int[nums.length + 1];
        for (int value : nums) counts[value]++;
        return counts;
    }

    private static int[] valuesWithCount(int[] counts, int wanted) {
        int[] result = new int[counts.length - 1];
        int size = 0;
        for (int value = 1; value < counts.length; value++) {
            if (wanted == 2 ? counts[value] == 2 : counts[value] == wanted) {
                result[size++] = value;
            }
        }
        return Arrays.copyOf(result, size);
    }

    private static void shuffle(int[] values, Random random) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            swap(values, i, j);
        }
    }

    private static int[] missingCase(int n, int missing, Random random) {
        int[] values = new int[n];
        int index = 0;
        for (int value = 0; value <= n; value++) {
            if (value != missing) values[index++] = value;
        }
        shuffle(values, random);
        return values;
    }

    private static int[] atMostTwiceCase(int n, Random random) {
        List<Integer> values = new ArrayList<>();
        int[] count = new int[n + 1];
        while (values.size() < n) {
            int value = 1 + random.nextInt(n);
            if (count[value] < 2) {
                count[value]++;
                values.add(value);
            }
        }
        int[] result = values.stream().mapToInt(Integer::intValue).toArray();
        shuffle(result, random);
        return result;
    }

    private static int[] mismatchCase(int n, int missing, int duplicate, Random random) {
        int[] values = new int[n];
        for (int i = 0; i < n; i++) values[i] = i + 1;
        values[missing - 1] = duplicate;
        shuffle(values, random);
        return values;
    }

    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void expectArray(int[] expected, int[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " expected=" + Arrays.toString(expected)
                    + " actual=" + Arrays.toString(actual));
        }
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
        expect(missingNumberByPlacement(new int[] {3, 0, 1}) == 2, "missing official 1"); groups++;
        expect(missingNumberByPlacement(new int[] {0, 1}) == 2, "missing n"); groups++;
        expect(missingNumberByPlacement(new int[] {1}) == 0, "missing zero"); groups++;
        expectArray(new int[] {5, 6}, disappearedNumbersByMarking(new int[] {4,3,2,7,8,2,3,1}), "disappeared official"); groups++;
        expectArray(new int[] {2}, disappearedNumbersByMarking(new int[] {1,1}), "disappeared singleton gap"); groups++;
        expectArray(new int[] {}, disappearedNumbersByMarking(new int[] {1,2,3}), "disappeared none"); groups++;
        expectArray(new int[] {2,3}, duplicatesByMarking(new int[] {4,3,2,7,8,2,3,1}), "duplicates official"); groups++;
        expectArray(new int[] {1}, duplicatesByMarking(new int[] {1,1,2}), "duplicates one"); groups++;
        expectArray(new int[] {}, duplicatesByMarking(new int[] {1}), "duplicates none"); groups++;
        expectArray(new int[] {2,3}, setMismatchByPlacement(new int[] {1,2,2,4}), "mismatch official"); groups++;
        expectArray(new int[] {1,2}, setMismatchByPlacement(new int[] {1,1}), "mismatch pair"); groups++;
        expectArray(new int[] {4,1}, setMismatchByPlacement(new int[] {4,2,3,4}), "mismatch endpoints"); groups++;
        int[] preserved = {4,3,2,7,8,2,3,1};
        int[] snapshot = preserved.clone();
        disappearedNumbersByMarking(preserved);
        duplicatesByMarking(preserved);
        expectArray(snapshot, preserved, "public methods preserve input"); groups++;
        expectRejected(() -> missingNumberByPlacement(null), "missing null"); groups++;
        expectRejected(() -> missingNumberByPlacement(new int[] {0,0}), "missing duplicate"); groups++;
        expectRejected(() -> disappearedNumbersByMarking(new int[] {0,1}), "disappeared domain"); groups++;
        expectRejected(() -> duplicatesByMarking(new int[] {1,1,1}), "duplicates multiplicity"); groups++;
        expectRejected(() -> setMismatchByPlacement(new int[] {1}), "mismatch length"); groups++;
        expectRejected(() -> setMismatchByPlacement(new int[] {1,2,3}), "mismatch absent"); groups++;
        expectRejected(() -> setMismatchByPlacement(new int[] {1,1,1}), "mismatch shape"); groups++;

        Random random = new Random(0x1A2B3C4DL);
        int trials = 20_000;
        int checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int n = 2 + random.nextInt(8);
            int[] missing = missingCase(n, random.nextInt(n + 1), random);
            int[] disappeared = new int[n];
            for (int i = 0; i < n; i++) disappeared[i] = 1 + random.nextInt(n);
            int[] duplicates = atMostTwiceCase(n, random);
            int missingValue = 1 + random.nextInt(n);
            int duplicateValue;
            do duplicateValue = 1 + random.nextInt(n); while (duplicateValue == missingValue);
            int[] mismatch = mismatchCase(n, missingValue, duplicateValue, random);
            int[] m0 = missing.clone(), d0 = disappeared.clone(), u0 = duplicates.clone(), s0 = mismatch.clone();

            expect(missingNumberByPlacement(missing) == missingOracle(missing), "random missing"); checks++;
            expectArray(disappearedOracle(disappeared), disappearedNumbersByMarking(disappeared), "random disappeared"); checks++;
            expectArray(duplicatesOracle(duplicates), duplicatesByMarking(duplicates), "random duplicates"); checks++;
            expectArray(setMismatchOracle(mismatch), setMismatchByPlacement(mismatch), "random mismatch"); checks++;
            expect(Arrays.equals(m0, missing) && Arrays.equals(d0, disappeared)
                    && Arrays.equals(u0, duplicates) && Arrays.equals(s0, mismatch), "random preservation"); checks++;
        }
        System.out.println("IndexPlacementMarkingPatternContracts: " + groups
                + " scenario groups + " + trials + " seeded oracle trials passed; checks=" + checks);
    }
}
