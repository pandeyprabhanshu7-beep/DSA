import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Jump Game II. */
public final class JumpGameIIContracts {
    private static int groups;
    private static long checks;

    /** Returns the minimum jump count, or -1 when the reusable input is unreachable. */
    static int minimumJumps(int[] nums) {
        if (nums == null || nums.length == 0) {
            throw new IllegalArgumentException("nums must be non-null and non-empty");
        }
        for (int value : nums) {
            if (value < 0) throw new IllegalArgumentException("jump lengths must be non-negative");
        }
        if (nums.length == 1) return 0;

        int jumps = 0;
        int currentEnd = 0;
        int farthest = 0;
        for (int index = 0; index < nums.length - 1; index++) {
            long rawReach = (long) index + nums[index];
            int reach = (int) Math.min(nums.length - 1L, rawReach);
            farthest = Math.max(farthest, reach);

            if (index == currentEnd) {
                if (farthest == currentEnd) return -1;
                jumps++;
                currentEnd = farthest;
                if (currentEnd == nums.length - 1) return jumps;
            }
        }
        return -1;
    }

    /** Independent O(n^2) dynamic-programming definition oracle. */
    private static int quadraticOracle(int[] nums) {
        int unreachable = nums.length + 1;
        int[] best = new int[nums.length];
        Arrays.fill(best, unreachable);
        best[0] = 0;
        for (int from = 0; from < nums.length; from++) {
            if (best[from] == unreachable) continue;
            long rawReach = (long) from + nums[from];
            int end = (int) Math.min(nums.length - 1L, rawReach);
            for (int to = from + 1; to <= end; to++) {
                best[to] = Math.min(best[to], best[from] + 1);
            }
        }
        return best[nums.length - 1] == unreachable ? -1 : best[nums.length - 1];
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
        group("official first", minimumJumps(new int[] {2, 3, 1, 1, 4}) == 2);
        group("official zero inside", minimumJumps(new int[] {2, 3, 0, 1, 4}) == 2);
        group("already at destination", minimumJumps(new int[] {0}) == 0);
        group("single positive", minimumJumps(new int[] {7}) == 0);
        group("one jump", minimumJumps(new int[] {4, 0, 0, 0, 0}) == 1);
        group("unit chain", minimumJumps(new int[] {1, 1, 1, 1}) == 3);
        group("local farthest landing fails", minimumJumps(new int[] {2, 2, 0, 1}) == 2);
        group("zero jumped over", minimumJumps(new int[] {3, 0, 0, 1}) == 1);
        group("unreachable barrier", minimumJumps(new int[] {3, 2, 1, 0, 4}) == -1);
        group("unreachable immediately", minimumJumps(new int[] {0, 1}) == -1);
        group("late stall", minimumJumps(new int[] {2, 1, 0, 1}) == -1);
        group("two layers", minimumJumps(new int[] {2, 1, 1, 1}) == 2);
        group("large reach clamped", minimumJumps(new int[] {Integer.MAX_VALUE, 0}) == 1);
        group("destination value irrelevant", minimumJumps(new int[] {2, 0, 99}) == 1);

        int[] preserved = {2, 3, 1, 1, 4};
        int[] snapshot = preserved.clone();
        minimumJumps(preserved);
        group("input preserved", Arrays.equals(preserved, snapshot));
        group("null rejected", rejects(() -> minimumJumps(null)));
        group("empty rejected", rejects(() -> minimumJumps(new int[] {})));
        group("negative first rejected", rejects(() -> minimumJumps(new int[] {-1})));
        group("negative later rejected", rejects(() -> minimumJumps(new int[] {1, -1})));
        group("deterministic oracle parity", Arrays.asList(
                new int[] {2, 3, 1, 1, 4}, new int[] {2, 2, 0, 1},
                new int[] {3, 2, 1, 0, 4}, new int[] {0})
                .stream().allMatch(values -> minimumJumps(values) == quadraticOracle(values)));
    }

    private static int[] randomArray(Random random) {
        int[] values = new int[1 + random.nextInt(14)];
        for (int index = 0; index < values.length; index++) {
            values[index] = random.nextInt(8);
        }
        return values;
    }

    private static int[] clampReaches(int[] values) {
        int[] result = values.clone();
        for (int index = 0; index < result.length; index++) {
            result[index] = Math.min(result[index], result.length - 1 - index);
        }
        return result;
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x38_4A554D50L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] values = randomArray(random);
            int[] snapshot = values.clone();
            int expected = quadraticOracle(values);
            int actual = minimumJumps(values);

            checks++;
            if (actual != expected) throw new AssertionError("oracle mismatch at trial " + trial);
            checks++;
            if (!Arrays.equals(values, snapshot)) throw new AssertionError("input mutated at trial " + trial);
            checks++;
            if (minimumJumps(values.clone()) != actual) {
                throw new AssertionError("repeat mismatch at trial " + trial);
            }
            checks++;
            if (minimumJumps(clampReaches(values)) != actual) {
                throw new AssertionError("clamp mismatch at trial " + trial);
            }

            int[] strengthened = values.clone();
            int changed = random.nextInt(strengthened.length);
            if (strengthened[changed] < Integer.MAX_VALUE) strengthened[changed]++;
            int stronger = minimumJumps(strengthened);
            checks++;
            if (actual >= 0 && (stronger < 0 || stronger > actual)) {
                throw new AssertionError("monotonicity mismatch at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) throw new AssertionError("expected 20 scenario groups, got " + groups);
        System.out.println("PARITY|OFFICIAL=2|SINGLE=0|BRANCH=2|UNREACHABLE=-1|PRESERVED=true");
        System.out.println("JumpGameIIContracts: 20 scenario groups + 20000 seeded quadratic-oracle comparisons passed; checks=" + checks);
    }
}
