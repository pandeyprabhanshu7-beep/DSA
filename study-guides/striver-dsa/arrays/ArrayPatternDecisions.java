import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Dependency-free executable evidence for Section 2's pattern-selection contracts. */
public final class ArrayPatternDecisions {
    static long countSubarraysWithSum(int[] a, long target) {
        if (a == null) throw new NullPointerException("a");
        Map<Long, Integer> frequency = new HashMap<>();
        frequency.put(0L, 1);
        long prefix = 0L;
        long answer = 0L;
        for (int value : a) {
            prefix += value;
            answer += frequency.getOrDefault(prefix - target, 0);
            frequency.merge(prefix, 1, Integer::sum);
        }
        return answer;
    }

    static int[] sortedPairIndices(int[] sorted, long target) {
        if (sorted == null) throw new NullPointerException("sorted");
        requireNondecreasing(sorted);
        int left = 0;
        int right = sorted.length - 1;
        while (left < right) {
            long sum = (long) sorted[left] + sorted[right];
            if (sum == target) return new int[]{left, right};
            if (sum < target) left++;
            else right--;
        }
        return new int[0];
    }

    static int minLengthAtLeast(int[] nonnegative, long target) {
        if (nonnegative == null) throw new NullPointerException("nonnegative");
        if (target <= 0) throw new IllegalArgumentException("target must be positive");
        for (int value : nonnegative) {
            if (value < 0) throw new IllegalArgumentException("negative value");
        }
        int best = nonnegative.length + 1;
        int left = 0;
        long sum = 0L;
        for (int right = 0; right < nonnegative.length; right++) {
            sum += nonnegative[right];
            while (sum >= target) {
                best = Math.min(best, right - left + 1);
                sum -= nonnegative[left++];
            }
        }
        return best == nonnegative.length + 1 ? 0 : best;
    }

    static int firstMissingPositive(int[] a) {
        if (a == null) throw new NullPointerException("a");
        int i = 0;
        while (i < a.length) {
            int value = a[i];
            if (value >= 1 && value <= a.length && a[value - 1] != value) {
                int tmp = a[value - 1];
                a[value - 1] = value;
                a[i] = tmp;
            } else {
                i++;
            }
        }
        for (i = 0; i < a.length; i++) {
            if (a[i] != i + 1) return i + 1;
        }
        return a.length + 1;
    }

    private static void requireNondecreasing(int[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[i - 1] > a[i]) {
                throw new IllegalArgumentException("input must be nondecreasing");
            }
        }
    }

    private static void rejects(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (type.isInstance(actual)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + actual, actual);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    public static void main(String[] args) {
        assert countSubarraysWithSum(new int[]{3, 4, -7, 1, 3, 3, 1, -4}, 7L) == 4L;
        assert countSubarraysWithSum(new int[]{0, 0, 0}, 0L) == 6L;
        assert countSubarraysWithSum(new int[]{}, 0L) == 0L;
        assert countSubarraysWithSum(new int[]{Integer.MAX_VALUE, 1, -Integer.MAX_VALUE}, 1L) == 2L;

        int[] sorted = {-3, 1, 4, 6, 8, 13};
        assert Arrays.equals(sortedPairIndices(sorted, 10L), new int[]{0, 5});
        assert Arrays.equals(sortedPairIndices(sorted, 9L), new int[]{1, 4});
        assert sortedPairIndices(sorted, 100L).length == 0;
        assert Arrays.equals(
                sortedPairIndices(new int[]{Integer.MAX_VALUE - 1, Integer.MAX_VALUE}, 4_294_967_293L),
                new int[]{0, 1});

        assert minLengthAtLeast(new int[]{2, 3, 1, 2, 4, 3}, 7L) == 2;
        assert minLengthAtLeast(new int[]{1, 1, 1}, 5L) == 0;
        assert minLengthAtLeast(new int[]{7}, 7L) == 1;
        assert minLengthAtLeast(new int[]{}, 7L) == 0;

        int[] mixed = {3, 4, -1, 1};
        assert firstMissingPositive(mixed) == 2;
        assert Arrays.equals(mixed, new int[]{1, -1, 3, 4});
        assert firstMissingPositive(new int[]{1, 2, 0}) == 3;
        assert firstMissingPositive(new int[]{1, 1}) == 2;
        assert firstMissingPositive(new int[]{7, 8, 9, 11, 12}) == 1;
        assert firstMissingPositive(new int[]{}) == 1;

        rejects(NullPointerException.class, () -> countSubarraysWithSum(null, 0L));
        rejects(NullPointerException.class, () -> sortedPairIndices(null, 0L));
        rejects(IllegalArgumentException.class, () -> sortedPairIndices(new int[]{2, 1}, 3L));
        rejects(NullPointerException.class, () -> minLengthAtLeast(null, 1L));
        rejects(IllegalArgumentException.class, () -> minLengthAtLeast(new int[]{1}, 0L));
        rejects(IllegalArgumentException.class, () -> minLengthAtLeast(new int[]{1, -1}, 1L));
        rejects(NullPointerException.class, () -> firstMissingPositive(null));
        System.out.println("ArrayPatternDecisions: 26 scenario groups passed");
    }
}
