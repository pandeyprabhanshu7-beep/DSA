import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Median of Two Sorted Arrays. */
public final class MedianTwoSortedArraysContracts {
    private static int groups;
    private static long checks;

    /** O(log(min(m,n))) core. Inputs are required to be sorted nondecreasingly. */
    static double findMedianSortedArrays(int[] first, int[] second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("arrays must be non-null");
        }
        if (first.length == 0 && second.length == 0) {
            throw new IllegalArgumentException("at least one value is required");
        }

        int[] a = first;
        int[] b = second;
        if (a.length > b.length) {
            a = second;
            b = first;
        }

        int m = a.length;
        int n = b.length;
        long total = (long) m + n;
        int leftSize = (int) ((total + 1L) / 2L);
        int low = 0;
        int high = m;

        while (low <= high) {
            int cutA = low + (high - low) / 2;
            int cutB = leftSize - cutA;

            int leftA = cutA == 0 ? Integer.MIN_VALUE : a[cutA - 1];
            int rightA = cutA == m ? Integer.MAX_VALUE : a[cutA];
            int leftB = cutB == 0 ? Integer.MIN_VALUE : b[cutB - 1];
            int rightB = cutB == n ? Integer.MAX_VALUE : b[cutB];

            if (leftA <= rightB && leftB <= rightA) {
                int leftMax = Math.max(leftA, leftB);
                if ((total & 1L) == 1L) {
                    return leftMax;
                }
                int rightMin = Math.min(rightA, rightB);
                return ((long) leftMax + rightMin) / 2.0;
            }
            if (leftA > rightB) {
                high = cutA - 1;
            } else {
                low = cutA + 1;
            }
        }
        throw new IllegalArgumentException("no valid partition; inputs must be sorted");
    }

    /** Reusable defensive boundary; sortedness checks add O(m+n) time. */
    static double findMedianSortedArraysValidated(int[] first, int[] second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("arrays must be non-null");
        }
        validateSorted(first);
        validateSorted(second);
        return findMedianSortedArrays(first, second);
    }

    private static void validateSorted(int[] values) {
        for (int index = 1; index < values.length; index++) {
            if (values[index - 1] > values[index]) {
                throw new IllegalArgumentException("arrays must be sorted nondecreasingly");
            }
        }
    }

    /** Independent O(m+n) merge-selection oracle. */
    private static double mergeMedianOracle(int[] a, int[] b) {
        int total = a.length + b.length;
        int firstMiddle = (total - 1) / 2;
        int secondMiddle = total / 2;
        int indexA = 0;
        int indexB = 0;
        int previous = 0;
        int current = 0;

        for (int merged = 0; merged <= secondMiddle; merged++) {
            previous = current;
            if (indexB == b.length
                    || (indexA < a.length && a[indexA] <= b[indexB])) {
                current = a[indexA++];
            } else {
                current = b[indexB++];
            }
        }
        if (firstMiddle == secondMiddle) {
            return current;
        }
        return ((long) previous + current) / 2.0;
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
        group("official odd", findMedianSortedArrays(
                new int[] {1, 3}, new int[] {2}) == 2.0);
        group("official even", findMedianSortedArrays(
                new int[] {1, 2}, new int[] {3, 4}) == 2.5);
        group("first empty", findMedianSortedArrays(
                new int[] {}, new int[] {1}) == 1.0);
        group("second empty", findMedianSortedArrays(
                new int[] {2, 3}, new int[] {}) == 2.5);
        group("singleton pair", findMedianSortedArrays(
                new int[] {1}, new int[] {2}) == 1.5);
        group("unequal lengths", findMedianSortedArrays(
                new int[] {1, 2}, new int[] {3, 4, 5}) == 3.0);
        group("duplicates", findMedianSortedArrays(
                new int[] {1, 2, 2}, new int[] {2, 2, 3}) == 2.0);
        group("negative values", findMedianSortedArrays(
                new int[] {-5, -3}, new int[] {-4, -2, -1}) == -3.0);
        group("zeros", findMedianSortedArrays(
                new int[] {0, 0}, new int[] {0, 0}) == 0.0);
        group("interleaved", findMedianSortedArrays(
                new int[] {1, 4, 7}, new int[] {2, 3, 5, 6}) == 4.0);
        group("first entirely before second", findMedianSortedArrays(
                new int[] {1, 2, 3}, new int[] {10, 11}) == 3.0);
        group("second entirely before first", findMedianSortedArrays(
                new int[] {10, 11}, new int[] {1, 2, 3}) == 3.0);
        group("opposite integer extremes", findMedianSortedArrays(
                new int[] {Integer.MIN_VALUE}, new int[] {Integer.MAX_VALUE}) == -0.5);
        group("maximum average does not overflow", findMedianSortedArrays(
                new int[] {Integer.MAX_VALUE}, new int[] {Integer.MAX_VALUE})
                == Integer.MAX_VALUE);
        group("minimum average does not overflow", findMedianSortedArrays(
                new int[] {Integer.MIN_VALUE}, new int[] {Integer.MIN_VALUE})
                == Integer.MIN_VALUE);

        int[] preservedA = {1, 3, 5};
        int[] preservedB = {2, 4, 6};
        int[] snapshotA = preservedA.clone();
        int[] snapshotB = preservedB.clone();
        findMedianSortedArrays(preservedA, preservedB);
        group("inputs preserved", Arrays.equals(preservedA, snapshotA)
                && Arrays.equals(preservedB, snapshotB));
        group("swap symmetry", findMedianSortedArrays(
                new int[] {1, 2, 8}, new int[] {3, 4})
                == findMedianSortedArrays(new int[] {3, 4}, new int[] {1, 2, 8}));
        group("validated wrapper parity", findMedianSortedArraysValidated(
                new int[] {-2, 7}, new int[] {0, 4, 9}) == 4.0);
        group("null rejected", rejects(() -> findMedianSortedArrays(null, new int[] {1}))
                && rejects(() -> findMedianSortedArrays(new int[] {1}, null)));
        group("empty and unsorted rejected", rejects(
                () -> findMedianSortedArrays(new int[] {}, new int[] {}))
                && rejects(() -> findMedianSortedArraysValidated(
                        new int[] {2, 1}, new int[] {3}))); 
    }

    private static int[] randomSortedArray(Random random, int length) {
        int[] values = new int[length];
        for (int index = 0; index < length; index++) {
            int draw = random.nextInt(30);
            if (draw == 0) {
                values[index] = Integer.MIN_VALUE;
            } else if (draw == 1) {
                values[index] = Integer.MAX_VALUE;
            } else {
                values[index] = random.nextInt(81) - 40;
            }
        }
        Arrays.sort(values);
        return values;
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x32_4D4544L);
        for (int trial = 0; trial < 20_000; trial++) {
            int lengthA = random.nextInt(21);
            int lengthB = random.nextInt(21);
            if (lengthA + lengthB == 0) {
                lengthB = 1;
            }
            int[] a = randomSortedArray(random, lengthA);
            int[] b = randomSortedArray(random, lengthB);
            int[] snapshotA = a.clone();
            int[] snapshotB = b.clone();
            double expected = mergeMedianOracle(a, b);
            double actual = findMedianSortedArrays(a, b);

            checks++;
            if (Double.compare(actual, expected) != 0) {
                throw new AssertionError("oracle mismatch at trial " + trial);
            }
            checks++;
            if (!Arrays.equals(a, snapshotA)) {
                throw new AssertionError("first input mutated at trial " + trial);
            }
            checks++;
            if (!Arrays.equals(b, snapshotB)) {
                throw new AssertionError("second input mutated at trial " + trial);
            }
            checks++;
            if (Double.compare(actual, findMedianSortedArrays(b, a)) != 0) {
                throw new AssertionError("swap mismatch at trial " + trial);
            }
            checks++;
            if (Double.compare(actual, findMedianSortedArraysValidated(a, b)) != 0) {
                throw new AssertionError("validated mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|ODD=2.0|EVEN=2.5|EMPTY=1.0|EXTREMES=-0.5|PRESERVED=true");
        System.out.println("MedianTwoSortedArraysContracts: 20 scenario groups + 20000 seeded merge-oracle comparisons passed; checks=" + checks);
    }
}
