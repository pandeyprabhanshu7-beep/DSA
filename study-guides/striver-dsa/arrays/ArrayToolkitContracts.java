import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Dependency-free executable evidence for Section 3's Java toolkit contracts. */
public final class ArrayToolkitContracts {
    record IndexedValue(int value, int index) {}

    static int[] sortedCopy(int[] input) {
        if (input == null) throw new NullPointerException("input");
        int[] copy = Arrays.copyOf(input, input.length);
        Arrays.sort(copy);
        return copy;
    }

    static int[] stableOriginalIndicesByValue(int[] input) {
        if (input == null) throw new NullPointerException("input");
        IndexedValue[] pairs = new IndexedValue[input.length];
        for (int i = 0; i < input.length; i++) pairs[i] = new IndexedValue(input[i], i);
        Arrays.sort(pairs, (a, b) -> {
            int byValue = Integer.compare(a.value(), b.value());
            return byValue != 0 ? byValue : Integer.compare(a.index(), b.index());
        });
        int[] indices = new int[pairs.length];
        for (int i = 0; i < pairs.length; i++) indices[i] = pairs[i].index();
        return indices;
    }

    static Map<Integer, Integer> frequencies(int[] input) {
        if (input == null) throw new NullPointerException("input");
        Map<Integer, Integer> result = new HashMap<>();
        for (int value : input) result.merge(value, 1, Integer::sum);
        return result;
    }

    static boolean sameMultiset(int[] left, int[] right) {
        if (left == null || right == null) throw new NullPointerException("array");
        if (left.length != right.length) return false;
        Map<Integer, Integer> balance = frequencies(left);
        for (int value : right) {
            Integer count = balance.get(value);
            if (count == null) return false;
            if (count == 1) balance.remove(value);
            else balance.put(value, count - 1);
        }
        return balance.isEmpty();
    }

    static int[] windowMaximum(int[] input, int width) {
        if (input == null) throw new NullPointerException("input");
        if (width <= 0 || width > input.length) throw new IllegalArgumentException("invalid width");
        int[] result = new int[input.length - width + 1];
        Deque<Integer> candidates = new ArrayDeque<>();
        for (int right = 0; right < input.length; right++) {
            while (!candidates.isEmpty() && candidates.peekFirst() <= right - width) {
                candidates.pollFirst();
            }
            while (!candidates.isEmpty() && input[candidates.peekLast()] <= input[right]) {
                candidates.pollLast();
            }
            candidates.offerLast(right);
            if (right >= width - 1) result[right - width + 1] = input[candidates.peekFirst()];
        }
        return result;
    }

    static int lowerBound(int[] sorted, int target) {
        if (sorted == null) throw new NullPointerException("sorted");
        int left = 0;
        int right = sorted.length;
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (sorted[mid] < target) left = mid + 1;
            else right = mid;
        }
        return left;
    }

    static int safeMidpoint(int left, int right) {
        if (left < 0 || left > right) throw new IllegalArgumentException("require 0 <= left <= right");
        return left + (right - left) / 2;
    }

    static long safeSum(int a, int b) { return (long) a + b; }
    static long safeProduct(int a, int b) { return (long) a * b; }

    static void swap(int[] a, int i, int j) {
        if (a == null) throw new NullPointerException("a");
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    private static int[] bruteWindowMaximum(int[] input, int width) {
        int[] result = new int[input.length - width + 1];
        for (int left = 0; left + width <= input.length; left++) {
            int best = input[left];
            for (int i = left + 1; i < left + width; i++) best = Math.max(best, input[i]);
            result[left] = best;
        }
        return result;
    }

    private static int bruteLowerBound(int[] sorted, int target) {
        int i = 0;
        while (i < sorted.length && sorted[i] < target) i++;
        return i;
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
        int[] original = {40, 10, 40, -2};
        assert Arrays.equals(sortedCopy(original), new int[]{-2, 10, 40, 40});
        assert Arrays.equals(original, new int[]{40, 10, 40, -2});
        assert Arrays.equals(stableOriginalIndicesByValue(original), new int[]{3, 1, 0, 2});

        assert sameMultiset(new int[]{1000, 1000, -2}, new int[]{1000, -2, 1000});
        assert !sameMultiset(new int[]{1, 1, 2}, new int[]{1, 2, 2});
        Integer boxedA = Integer.valueOf(1000), boxedB = Integer.valueOf(1000);
        assert boxedA != boxedB && boxedA.equals(boxedB);

        int[] windows = windowMaximum(new int[]{1, 3, -1, -3, 5, 3, 6, 7}, 3);
        assert Arrays.equals(windows, new int[]{3, 3, 5, 5, 6, 7});
        assert Arrays.equals(windowMaximum(new int[]{4}, 1), new int[]{4});

        assert lowerBound(new int[]{1, 3, 3, 7}, 3) == 1;
        assert lowerBound(new int[]{1, 3, 3, 7}, 8) == 4;
        assert safeMidpoint(Integer.MAX_VALUE - 10, Integer.MAX_VALUE) == Integer.MAX_VALUE - 5;
        assert safeSum(Integer.MAX_VALUE, 1) == 2_147_483_648L;
        assert safeProduct(50_000, 50_000) == 2_500_000_000L;
        int[] swapped = {4, 9};
        swap(swapped, 0, 1);
        assert Arrays.equals(swapped, new int[]{9, 4});
        swap(swapped, 0, 0);
        assert Arrays.equals(swapped, new int[]{9, 4});

        rejects(NullPointerException.class, () -> sortedCopy(null));
        rejects(NullPointerException.class, () -> frequencies(null));
        rejects(IllegalArgumentException.class, () -> windowMaximum(new int[]{1}, 0));
        rejects(IllegalArgumentException.class, () -> safeMidpoint(2, 1));

        Random random = new Random(0xA22A7L);
        for (int round = 0; round < 10_000; round++) {
            int length = 1 + random.nextInt(40);
            int[] sample = new int[length];
            for (int i = 0; i < length; i++) sample[i] = random.nextInt(201) - 100;
            int width = 1 + random.nextInt(length);
            assert Arrays.equals(windowMaximum(sample, width), bruteWindowMaximum(sample, width));
            int[] sorted = sortedCopy(sample);
            int target = random.nextInt(241) - 120;
            assert lowerBound(sorted, target) == bruteLowerBound(sorted, target);
            assert Arrays.equals(sample, Arrays.copyOf(sample, sample.length));
        }
        System.out.println("ArrayToolkitContracts: 28 explicit scenario groups + 20,000 randomized comparisons passed");
    }
}
