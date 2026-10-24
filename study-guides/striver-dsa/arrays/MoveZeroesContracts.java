import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Section 4, problem 5: Move Zeroes. */
public final class MoveZeroesContracts {
    record MoveStats(int nonZeroCount, int assignments) {}

    /**
     * Stably compacts non-zero values in place, then clears the suffix.
     * The assignment count includes only writes that change an array cell.
     */
    static MoveStats moveZeroes(int[] values) {
        if (values == null) throw new NullPointerException("values");
        int write = 0;
        int assignments = 0;
        for (int read = 0; read < values.length; read++) {
            if (values[read] == 0) continue;
            if (write != read) {
                values[write] = values[read];
                assignments++;
            }
            write++;
        }
        for (int clear = write; clear < values.length; clear++) {
            if (values[clear] != 0) {
                values[clear] = 0;
                assignments++;
            }
        }
        return new MoveStats(write, assignments);
    }

    private static int[] reference(int[] input) {
        int[] output = new int[input.length];
        int write = 0;
        for (int value : input) if (value != 0) output[write++] = value;
        return output;
    }

    private static int explicitChecks;

    private static void check(boolean condition) {
        explicitChecks++;
        if (!condition) throw new AssertionError("explicit check " + explicitChecks + " failed");
    }

    private static void rejects(Class<? extends Throwable> type, Runnable action) {
        explicitChecks++;
        try {
            action.run();
        } catch (Throwable actual) {
            if (type.isInstance(actual)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + actual, actual);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static MoveStats verify(int[] input) {
        int[] expected = reference(input);
        int[] actual = input.clone();
        MoveStats stats = moveZeroes(actual);
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError(Arrays.toString(input) + " -> " + Arrays.toString(actual)
                    + ", expected " + Arrays.toString(expected));
        }
        int nonZeroCount = 0;
        for (int value : input) if (value != 0) nonZeroCount++;
        if (stats.nonZeroCount() != nonZeroCount) throw new AssertionError("non-zero count");
        if (stats.assignments() < 0 || stats.assignments() > input.length) {
            throw new AssertionError("assignment bound: " + stats.assignments());
        }
        return stats;
    }

    public static void main(String[] args) {
        int[] canonical = {0, 1, 0, 3, 12};
        MoveStats canonicalStats = moveZeroes(canonical);
        check(Arrays.equals(canonical, new int[]{1, 3, 12, 0, 0}));
        check(canonicalStats.equals(new MoveStats(3, 5)));

        int[] alreadyCompact = {4, -1, 7};
        MoveStats compactStats = moveZeroes(alreadyCompact);
        check(Arrays.equals(alreadyCompact, new int[]{4, -1, 7}));
        check(compactStats.assignments() == 0);

        int[] allZeroes = {0, 0, 0};
        MoveStats zeroStats = moveZeroes(allZeroes);
        check(Arrays.equals(allZeroes, new int[]{0, 0, 0}));
        check(zeroStats.equals(new MoveStats(0, 0)));

        int[] stableDuplicates = {0, -1, -1, 0, 2};
        MoveStats stableStats = moveZeroes(stableDuplicates);
        check(Arrays.equals(stableDuplicates, new int[]{-1, -1, 2, 0, 0}));
        check(stableStats.nonZeroCount() == 3);

        check(verify(new int[0]).equals(new MoveStats(0, 0)));
        check(verify(new int[]{0, 1, 0, 2, 0, 3}).assignments() <= 6);
        rejects(NullPointerException.class, () -> moveZeroes(null));

        Random random = new Random(0x5A3B0L);
        for (int round = 0; round < 20_000; round++) {
            int[] sample = new int[random.nextInt(65)];
            for (int i = 0; i < sample.length; i++) {
                sample[i] = random.nextInt(5) == 0 ? random.nextInt(11) - 5 : 0;
            }
            verify(sample);
        }
        System.out.println("MoveZeroesContracts: " + explicitChecks
                + " explicit scenario groups + 20,000 randomized oracle comparisons passed");
        System.out.println("PARITY|1,3,12,0,0|assignments=5|stable=true");
    }
}
