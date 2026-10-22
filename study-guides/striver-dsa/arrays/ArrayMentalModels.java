import java.util.Arrays;

/** Dependency-free evidence for the Section 1 array contracts. */
public final class ArrayMentalModels {
    static void stableMoveZeroes(int[] a) {
        if (a == null) throw new NullPointerException("a");
        int write = 0;
        for (int value : a) {
            if (value != 0) a[write++] = value;
        }
        while (write < a.length) a[write++] = 0;
    }

    static long[] prefixSums(int[] a) {
        if (a == null) throw new NullPointerException("a");
        long[] prefix = new long[a.length + 1];
        for (int i = 0; i < a.length; i++) prefix[i + 1] = prefix[i] + a[i];
        return prefix;
    }

    static long rangeSum(long[] prefix, int left, int right) {
        if (prefix == null) throw new NullPointerException("prefix");
        if (left < 0 || left > right || right >= prefix.length) {
            throw new IndexOutOfBoundsException("invalid half-open range");
        }
        return prefix[right] - prefix[left];
    }

    private static void rejects(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); }
        catch (Throwable actual) {
            if (type.isInstance(actual)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + actual, actual);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    public static void main(String[] args) {
        int[] original = {4, 1, 7};
        int[] alias = original;
        alias[1] = 9;
        assert original == alias;
        assert Arrays.equals(original, new int[]{4, 9, 7});
        int[] copy = Arrays.copyOf(original, original.length);
        copy[1] = 5;
        assert original != copy;
        assert Arrays.equals(original, new int[]{4, 9, 7});

        int[] mixed = {0, 3, 0, 3, 8};
        stableMoveZeroes(mixed);
        assert Arrays.equals(mixed, new int[]{3, 3, 8, 0, 0});
        int[] allZero = {0, 0, 0};
        stableMoveZeroes(allZero);
        assert Arrays.equals(allZero, new int[]{0, 0, 0});
        int[] noZero = {-2, 4, 4};
        stableMoveZeroes(noZero);
        assert Arrays.equals(noZero, new int[]{-2, 4, 4});
        int[] empty = {};
        stableMoveZeroes(empty);
        assert empty.length == 0;

        long[] prefix = prefixSums(new int[]{1_500_000_000, 1_500_000_000, -1_500_000_000});
        assert Arrays.equals(prefix, new long[]{0L, 1_500_000_000L, 3_000_000_000L, 1_500_000_000L});
        assert rangeSum(prefix, 0, 2) == 3_000_000_000L;
        assert rangeSum(prefix, 1, 3) == 0L;
        assert rangeSum(prefix, 2, 2) == 0L;
        assert Arrays.equals(prefixSums(new int[]{}), new long[]{0L});

        rejects(NullPointerException.class, () -> stableMoveZeroes(null));
        rejects(NullPointerException.class, () -> prefixSums(null));
        rejects(IndexOutOfBoundsException.class, () -> rangeSum(prefix, -1, 2));
        rejects(IndexOutOfBoundsException.class, () -> rangeSum(prefix, 2, 1));
        rejects(IndexOutOfBoundsException.class, () -> rangeSum(prefix, 0, prefix.length));
        System.out.println("ArrayMentalModels: 17 scenario groups passed");
    }
}
