import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Random;

/** Dependency-free contracts for stable in-place deduplication of a sorted int array. */
public final class RemoveDuplicatesContracts {
    record Result(int uniqueCount, int changedAssignments) {}

    static Result removeDuplicates(int[] values) {
        if (values == null) throw new IllegalArgumentException("values must not be null");

        // Validate the reusable API's precondition before mutation. The interview problem guarantees it.
        for (int index = 1; index < values.length; index++) {
            if (values[index] < values[index - 1]) {
                throw new IllegalArgumentException("values must be sorted nondecreasingly");
            }
        }
        if (values.length == 0) return new Result(0, 0);

        int write = 1;
        int changedAssignments = 0;
        for (int read = 1; read < values.length; read++) {
            if (values[read] == values[write - 1]) continue;
            if (write != read) {
                values[write] = values[read];
                changedAssignments++;
            }
            write++;
        }
        return new Result(write, changedAssignments);
    }

    static int[] oracle(int[] sorted) {
        var values = new LinkedHashSet<Integer>();
        for (int value : sorted) values.add(value);
        int[] result = new int[values.size()];
        int index = 0;
        for (int value : values) result[index++] = value;
        return result;
    }

    static void assertCase(int[] input, int[] expectedPrefix, int expectedAssignments) {
        int[] working = input.clone();
        Result result = removeDuplicates(working);
        assert result.uniqueCount() == expectedPrefix.length;
        assert result.changedAssignments() == expectedAssignments;
        assert Arrays.equals(Arrays.copyOf(working, result.uniqueCount()), expectedPrefix);
        assert result.changedAssignments() <= Math.max(0, result.uniqueCount() - 1);
        for (int index = 1; index < result.uniqueCount(); index++) {
            assert working[index - 1] < working[index];
        }
    }

    static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    public static void main(String[] args) {
        assertCase(new int[]{0, 0, 1, 1, 1, 2, 2, 3, 3, 4}, new int[]{0, 1, 2, 3, 4}, 4);
        assertCase(new int[]{-7, -7, -7}, new int[]{-7}, 0);
        assertCase(new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE},
                new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}, 0);
        assertCase(new int[]{}, new int[]{}, 0);
        assertCase(new int[]{5}, new int[]{5}, 0);
        assertCase(new int[]{-2, -2, -1, -1, 0, 0}, new int[]{-2, -1, 0}, 2);

        rejects(() -> removeDuplicates(null));
        int[] unsorted = {1, 0, 1};
        int[] before = unsorted.clone();
        rejects(() -> removeDuplicates(unsorted));
        assert Arrays.equals(unsorted, before); // validation happens before any write

        var random = new Random(0xD3D0B1L);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = random.nextInt(65);
            int[] input = new int[length];
            for (int index = 0; index < length; index++) input[index] = random.nextInt(31) - 15;
            if (length > 0 && trial % 97 == 0) input[random.nextInt(length)] = Integer.MIN_VALUE;
            if (length > 0 && trial % 101 == 0) input[random.nextInt(length)] = Integer.MAX_VALUE;
            Arrays.sort(input);

            int[] expected = oracle(input);
            int[] working = input.clone();
            Result result = removeDuplicates(working);
            assert result.uniqueCount() == expected.length;
            assert Arrays.equals(Arrays.copyOf(working, result.uniqueCount()), expected);
            assert result.changedAssignments() <= Math.max(0, result.uniqueCount() - 1);
        }

        System.out.println("PARITY|k=5|prefix=0,1,2,3,4|assignments=4|suffix=ignored");
        System.out.println("RemoveDuplicatesContracts: 13 scenario groups + 20,000 randomized oracle comparisons passed");
    }
}
