import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Maximum Product Subarray. */
public final class MaximumProductSubarrayContracts {
    private static int groups;
    private static long checks;

    /**
     * Returns the largest product of a non-empty contiguous subarray.
     *
     * <p>Precondition: every subarray product is representable as an {@code int},
     * matching the primary problem contract. The input is not modified.</p>
     */
    static int maxProduct(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must be non-null and non-empty");
        }

        int maxEnding = values[0];
        int minEnding = values[0];
        int best = values[0];
        for (int index = 1; index < values.length; index++) {
            int value = values[index];
            int previousMax = maxEnding;
            int previousMin = minEnding;
            maxEnding = Math.max(value,
                    Math.max(previousMax * value, previousMin * value));
            minEnding = Math.min(value,
                    Math.min(previousMax * value, previousMin * value));
            best = Math.max(best, maxEnding);
        }
        return best;
    }

    /** Independent O(n^2) definition oracle using widened arithmetic. */
    private static int quadraticOracle(int[] values) {
        long best = Long.MIN_VALUE;
        for (int left = 0; left < values.length; left++) {
            long product = 1L;
            for (int right = left; right < values.length; right++) {
                product *= values[right];
                if (product < Integer.MIN_VALUE || product > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("test violates 32-bit product precondition");
                }
                best = Math.max(best, product);
            }
        }
        return (int) best;
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
        group("official positive run", maxProduct(new int[] {2, 3, -2, 4}) == 6);
        group("official zero separator", maxProduct(new int[] {-2, 0, -1}) == 0);
        group("negative flips minimum", maxProduct(new int[] {-2, 3, -4}) == 24);
        group("positive singleton", maxProduct(new int[] {7}) == 7);
        group("negative singleton", maxProduct(new int[] {-7}) == -7);
        group("zero singleton", maxProduct(new int[] {0}) == 0);
        group("all zeroes", maxProduct(new int[] {0, 0, 0}) == 0);
        group("even negative count", maxProduct(new int[] {-2, -3, -4, -5}) == 120);
        group("odd negative count chooses suffix", maxProduct(new int[] {-2, -3, -4}) == 12);
        group("duplicate values", maxProduct(new int[] {2, 2, -1, 2}) == 4);
        group("multiple zero-delimited segments",
                maxProduct(new int[] {-2, 0, -3, -4, 0, 5}) == 12);
        group("all positive", maxProduct(new int[] {1, 2, 3, 4}) == 24);
        group("leading zero", maxProduct(new int[] {0, -2, -3}) == 6);
        group("trailing zero", maxProduct(new int[] {-2, -3, 0}) == 6);
        group("noncontiguous values cannot combine", maxProduct(new int[] {-2, 0, -1}) != 2);

        int[] preserved = {-2, 3, -4};
        int[] snapshot = preserved.clone();
        maxProduct(preserved);
        group("input preserved", Arrays.equals(preserved, snapshot));
        group("null rejected", rejects(() -> maxProduct(null)));
        group("empty rejected", rejects(() -> maxProduct(new int[] {})));
        group("oracle parity on deterministic catalog", Arrays.asList(
                new int[] {2, 3, -2, 4}, new int[] {-2, 0, -1},
                new int[] {-2, 3, -4}, new int[] {-1, -2, -9, -6})
                .stream().allMatch(values -> maxProduct(values) == quadraticOracle(values)));
        group("large legal product", maxProduct(
                new int[] {10, 10, 10, 10, 10, 10, 10, 10, 10}) == 1_000_000_000);
    }

    private static int[] randomArray(Random random) {
        int[] values = new int[1 + random.nextInt(10)];
        for (int index = 0; index < values.length; index++) {
            values[index] = random.nextInt(9) - 4;
        }
        return values;
    }

    private static void reverse(int[] values) {
        for (int left = 0, right = values.length - 1; left < right; left++, right--) {
            int value = values[left];
            values[left] = values[right];
            values[right] = value;
        }
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x34_4D5053L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] values = randomArray(random);
            int[] snapshot = values.clone();
            int expected = quadraticOracle(values);
            int actual = maxProduct(values);

            checks++;
            if (actual != expected) {
                throw new AssertionError("oracle mismatch at trial " + trial);
            }
            checks++;
            if (!Arrays.equals(values, snapshot)) {
                throw new AssertionError("input mutated at trial " + trial);
            }
            checks++;
            if (maxProduct(values.clone()) != actual) {
                throw new AssertionError("repeat mismatch at trial " + trial);
            }
            checks++;
            int[] reversed = values.clone();
            reverse(reversed);
            if (maxProduct(reversed) != actual) {
                throw new AssertionError("reversal mismatch at trial " + trial);
            }
            checks++;
            int[] withZero = Arrays.copyOf(values, values.length + 1);
            if (maxProduct(withZero) != Math.max(actual, 0)) {
                throw new AssertionError("zero-append metamorphic mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|OFFICIAL1=6|OFFICIAL2=0|NEGATIVE_FLIP=24|LARGE=1000000000|PRESERVED=true");
        System.out.println("MaximumProductSubarrayContracts: 20 scenario groups + 20000 seeded quadratic-oracle comparisons passed; checks=" + checks);
    }
}
