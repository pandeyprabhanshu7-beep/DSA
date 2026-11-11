import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Dependency-free executable contracts for Single Number. */
public final class SingleNumberContracts {
    private static int groups;
    private static long checks;

    /** Assumes the official one-single/all-other-values-exactly-twice precondition. */
    static int singleNumber(int[] nums) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        int unique = 0;
        for (int value : nums) unique ^= value;
        return unique;
    }

    /** Fail-fast boundary whose multiplicity validation deliberately uses O(n) space. */
    static int singleNumberValidated(int[] nums) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        Map<Integer, Integer> frequencies = new HashMap<>();
        for (int value : nums) frequencies.merge(value, 1, Integer::sum);
        int singles = 0;
        for (int frequency : frequencies.values()) {
            if (frequency == 1) singles++;
            else if (frequency != 2) {
                throw new IllegalArgumentException("every non-single value must appear exactly twice");
            }
        }
        if (singles != 1) {
            throw new IllegalArgumentException("exactly one value must appear once");
        }
        return singleNumber(nums);
    }

    /** Independent sorted-copy oracle for valid inputs. */
    private static int sortedOracle(int[] nums) {
        int[] copy = nums.clone();
        Arrays.sort(copy);
        for (int index = 0; index < copy.length - 1; index += 2) {
            if (copy[index] != copy[index + 1]) return copy[index];
        }
        return copy[copy.length - 1];
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

    private static int[] validInput(int pairCount, Random random) {
        Set<Integer> used = new HashSet<>();
        int unique;
        do unique = random.nextInt(); while (!used.add(unique));
        int[] values = new int[2 * pairCount + 1];
        values[0] = unique;
        for (int pair = 0; pair < pairCount; pair++) {
            int value;
            do value = random.nextInt(); while (!used.add(value));
            values[2 * pair + 1] = value;
            values[2 * pair + 2] = value;
        }
        shuffle(values, random);
        return values;
    }

    private static void deterministicEvidence() {
        group("official first", singleNumber(new int[] {2, 2, 1}) == 1);
        group("official second", singleNumber(new int[] {4, 1, 2, 1, 2}) == 4);
        group("official singleton", singleNumber(new int[] {1}) == 1);
        group("negative single", singleNumber(new int[] {-7, 4, 4}) == -7);
        group("negative pairs", singleNumber(new int[] {-2, -2, -9, 6, 6}) == -9);
        group("zero single", singleNumber(new int[] {5, 0, 5}) == 0);
        group("zero pair", singleNumber(new int[] {0, Integer.MIN_VALUE, 0}) == Integer.MIN_VALUE);
        group("full width max", singleNumber(new int[] {3, Integer.MAX_VALUE, 3}) == Integer.MAX_VALUE);
        group("full width min", singleNumber(new int[] {Integer.MIN_VALUE, 8, 8}) == Integer.MIN_VALUE);
        group("many pairs", singleNumber(new int[] {9, 1, 8, 2, 9, 3, 8, 2, 3}) == 1);

        int[] preserved = {4, 1, 2, 1, 2};
        int[] snapshot = preserved.clone();
        singleNumber(preserved);
        group("input preserved", Arrays.equals(preserved, snapshot));
        group("order irrelevant", singleNumber(new int[] {6, 4, 6, 2, 2})
                == singleNumber(new int[] {2, 6, 4, 2, 6}));
        group("validated official", singleNumberValidated(new int[] {4, 1, 2, 1, 2}) == 4);
        group("null rejected", rejects(() -> singleNumber(null)));
        group("empty rejected", rejects(() -> singleNumber(new int[] {})));
        group("no single rejected", rejects(() -> singleNumberValidated(new int[] {1, 1})));
        group("two singles rejected", rejects(() -> singleNumberValidated(new int[] {1, 2, 3, 3})));
        group("triplicate rejected", rejects(() -> singleNumberValidated(new int[] {1, 1, 1, 2})));
        group("four copies rejected", rejects(() -> singleNumberValidated(new int[] {1, 1, 1, 1, 2})));
        group("deterministic oracle parity", Arrays.asList(
                new int[] {2, 2, 1}, new int[] {4, 1, 2, 1, 2}, new int[] {-7})
                .stream().allMatch(values -> singleNumber(values) == sortedOracle(values)));
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x41_53494E47L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] values = validInput(random.nextInt(65), random);
            int[] snapshot = values.clone();
            int actual = singleNumber(values);

            checks++;
            if (actual != sortedOracle(values)) throw new AssertionError("oracle mismatch at trial " + trial);
            checks++;
            if (!Arrays.equals(values, snapshot)) throw new AssertionError("input mutated at trial " + trial);
            checks++;
            if (singleNumber(reversed(values)) != actual) {
                throw new AssertionError("reversal mismatch at trial " + trial);
            }
            checks++;
            if (singleNumberValidated(values) != actual) {
                throw new AssertionError("validated mismatch at trial " + trial);
            }
            int[] reshuffled = values.clone();
            shuffle(reshuffled, random);
            checks++;
            if (singleNumber(reshuffled) != actual) {
                throw new AssertionError("shuffle mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) throw new AssertionError("expected 20 scenario groups, got " + groups);
        System.out.println("PARITY|FIRST=1|SECOND=4|SINGLETON=1|NEGATIVE=-7|ZERO=0|PRESERVED=true");
        System.out.println("SingleNumberContracts: 20 scenario groups + 20000 seeded sorted-oracle trials passed; checks=" + checks);
    }
}
