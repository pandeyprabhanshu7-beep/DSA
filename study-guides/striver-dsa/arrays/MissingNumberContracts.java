import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Missing Number. */
public final class MissingNumberContracts {
    private static int groups;
    private static long checks;

    /**
     * Returns the missing value under the official distinct-[0,n] precondition.
     * The reusable extension accepts an empty array and returns 0.
     */
    static int missingNumber(int[] nums) {
        if (nums == null) throw new IllegalArgumentException("nums must be non-null");
        int missing = nums.length;
        for (int index = 0; index < nums.length; index++) {
            missing ^= index ^ nums[index];
        }
        return missing;
    }

    /** Fail-fast boundary whose validation deliberately uses O(n) extra space. */
    static int missingNumberValidated(int[] nums) {
        if (nums == null) throw new IllegalArgumentException("nums must be non-null");
        boolean[] seen = new boolean[nums.length + 1];
        for (int value : nums) {
            if (value < 0 || value > nums.length || seen[value]) {
                throw new IllegalArgumentException("values must be distinct and within [0,n]");
            }
            seen[value] = true;
        }
        return missingNumber(nums);
    }

    /** Independent widened arithmetic definition oracle for valid inputs. */
    private static int arithmeticOracle(int[] nums) {
        long difference = (long) nums.length * (nums.length + 1L) / 2L;
        for (int value : nums) difference -= value;
        return Math.toIntExact(difference);
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

    private static int[] validInput(int n, int missing) {
        int[] values = new int[n];
        int write = 0;
        for (int value = 0; value <= n; value++) {
            if (value != missing) values[write++] = value;
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

    private static void shuffle(int[] values, Random random) {
        for (int index = values.length - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            int temporary = values[index];
            values[index] = values[other];
            values[other] = temporary;
        }
    }

    private static void deterministicEvidence() {
        group("official first", missingNumber(new int[] {3, 0, 1}) == 2);
        group("official missing n", missingNumber(new int[] {0, 1}) == 2);
        group("official larger", missingNumber(new int[] {9, 6, 4, 2, 3, 5, 7, 0, 1}) == 8);
        group("empty reusable extension", missingNumber(new int[] {}) == 0);
        group("singleton missing zero", missingNumber(new int[] {1}) == 0);
        group("singleton missing one", missingNumber(new int[] {0}) == 1);
        group("missing zero", missingNumber(new int[] {1, 2, 3}) == 0);
        group("missing upper endpoint", missingNumber(new int[] {0, 1, 2, 3}) == 4);
        group("unsorted middle", missingNumber(new int[] {4, 2, 1, 0}) == 3);
        group("two values", missingNumber(new int[] {2, 0}) == 1);
        group("late middle", missingNumber(new int[] {0, 1, 2, 4}) == 3);
        group("official maximum n", missingNumber(validInput(10_000, 6_789)) == 6_789);

        int[] preserved = {9, 6, 4, 2, 3, 5, 7, 0, 1};
        int[] snapshot = preserved.clone();
        missingNumber(preserved);
        group("input preserved", Arrays.equals(preserved, snapshot));
        group("order irrelevant", missingNumber(new int[] {0, 4, 1, 3})
                == missingNumber(new int[] {3, 1, 4, 0}));
        group("validated empty", missingNumberValidated(new int[] {}) == 0);
        group("null rejected", rejects(() -> missingNumber(null)));
        group("negative rejected", rejects(() -> missingNumberValidated(new int[] {0, -1})));
        group("above n rejected", rejects(() -> missingNumberValidated(new int[] {0, 3})));
        group("duplicate rejected", rejects(() -> missingNumberValidated(new int[] {0, 1, 1})));
        group("deterministic oracle parity", Arrays.asList(
                new int[] {3, 0, 1}, new int[] {0, 1}, new int[] {1}, new int[] {})
                .stream().allMatch(values -> missingNumber(values) == arithmeticOracle(values)));
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x40_4D495353L);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = random.nextInt(65);
            int expected = random.nextInt(n + 1);
            int[] values = validInput(n, expected);
            shuffle(values, random);
            int[] snapshot = values.clone();
            int actual = missingNumber(values);

            checks++;
            if (actual != arithmeticOracle(values)) {
                throw new AssertionError("oracle mismatch at trial " + trial);
            }
            checks++;
            if (!Arrays.equals(values, snapshot)) throw new AssertionError("input mutated at trial " + trial);
            checks++;
            if (missingNumber(reversed(values)) != actual) {
                throw new AssertionError("reversal mismatch at trial " + trial);
            }
            checks++;
            if (missingNumberValidated(values) != actual) {
                throw new AssertionError("validated mismatch at trial " + trial);
            }
            int[] reshuffled = values.clone();
            shuffle(reshuffled, random);
            checks++;
            if (missingNumber(reshuffled) != actual) {
                throw new AssertionError("shuffle mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) throw new AssertionError("expected 20 scenario groups, got " + groups);
        System.out.println("PARITY|OFFICIAL=2|MISSING_N=2|MISSING_ZERO=0|EMPTY=0|PRESERVED=true");
        System.out.println("MissingNumberContracts: 20 scenario groups + 20000 seeded arithmetic-oracle comparisons passed; checks=" + checks);
    }
}
