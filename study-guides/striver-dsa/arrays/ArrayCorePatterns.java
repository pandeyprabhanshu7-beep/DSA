import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;

/** Dependency-free executable evidence for Section 4's first four core-pattern contracts. */
public final class ArrayCorePatterns {
    private static int explicitChecks;

    static int[] twoSumIndices(int[] input, long target) {
        if (input == null) throw new NullPointerException("input");
        Map<Long, Integer> firstIndex = new HashMap<>();
        for (int right = 0; right < input.length; right++) {
            long need = target - input[right];
            Integer left = firstIndex.get(need);
            if (left != null) return new int[]{left, right};
            firstIndex.putIfAbsent((long) input[right], right);
        }
        return new int[0];
    }

    static long maxSingleTradeProfit(int[] prices) {
        if (prices == null) throw new NullPointerException("prices");
        if (prices.length < 2) return 0L;
        int minimum = prices[0];
        long best = 0L;
        for (int sell = 1; sell < prices.length; sell++) {
            best = Math.max(best, (long) prices[sell] - minimum);
            minimum = Math.min(minimum, prices[sell]);
        }
        return best;
    }

    static long maxSubarraySum(int[] input) {
        if (input == null) throw new NullPointerException("input");
        if (input.length == 0) throw new IllegalArgumentException("non-empty input required");
        long endingHere = input[0];
        long best = input[0];
        for (int i = 1; i < input.length; i++) {
            endingHere = Math.max((long) input[i], endingHere + input[i]);
            best = Math.max(best, endingHere);
        }
        return best;
    }

    static OptionalInt majorityElement(int[] input) {
        if (input == null) throw new NullPointerException("input");
        if (input.length == 0) return OptionalInt.empty();
        int candidate = 0;
        int balance = 0;
        for (int value : input) {
            if (balance == 0) candidate = value;
            balance += value == candidate ? 1 : -1;
        }
        int occurrences = 0;
        for (int value : input) if (value == candidate) occurrences++;
        return occurrences > input.length / 2 ? OptionalInt.of(candidate) : OptionalInt.empty();
    }

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

    private static boolean pairExists(int[] input, long target) {
        for (int i = 0; i < input.length; i++) {
            for (int j = i + 1; j < input.length; j++) {
                if ((long) input[i] + input[j] == target) return true;
            }
        }
        return false;
    }

    private static long bruteProfit(int[] prices) {
        long best = 0L;
        for (int buy = 0; buy < prices.length; buy++) {
            for (int sell = buy + 1; sell < prices.length; sell++) {
                best = Math.max(best, (long) prices[sell] - prices[buy]);
            }
        }
        return best;
    }

    private static long bruteMaxSubarray(int[] input) {
        long best = Long.MIN_VALUE;
        for (int left = 0; left < input.length; left++) {
            long sum = 0L;
            for (int right = left; right < input.length; right++) {
                sum += input[right];
                best = Math.max(best, sum);
            }
        }
        return best;
    }

    private static OptionalInt bruteMajority(int[] input) {
        for (int candidate : input) {
            int count = 0;
            for (int value : input) if (value == candidate) count++;
            if (count > input.length / 2) return OptionalInt.of(candidate);
        }
        return OptionalInt.empty();
    }

    private static void validatePair(int[] input, long target, int[] answer) {
        boolean exists = pairExists(input, target);
        if (answer.length == 0) {
            if (exists) throw new AssertionError("missed pair");
            return;
        }
        if (answer.length != 2 || answer[0] < 0 || answer[0] >= answer[1]
                || answer[1] >= input.length
                || (long) input[answer[0]] + input[answer[1]] != target) {
            throw new AssertionError("invalid pair " + Arrays.toString(answer));
        }
    }

    public static void main(String[] args) {
        check(Arrays.equals(twoSumIndices(new int[]{2, 7, 11, 15}, 9L), new int[]{0, 1}));
        check(Arrays.equals(twoSumIndices(new int[]{3, 3}, 6L), new int[]{0, 1}));
        check(Arrays.equals(twoSumIndices(new int[]{1, 2, 1, 3}, 4L), new int[]{0, 3}));
        check(twoSumIndices(new int[]{1}, 2L).length == 0);
        check(twoSumIndices(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 4_294_967_294L).length == 2);
        check(twoSumIndices(new int[]{Integer.MIN_VALUE, -1}, -2_147_483_649L).length == 2);
        rejects(NullPointerException.class, () -> twoSumIndices(null, 0L));

        check(maxSingleTradeProfit(new int[]{7, 1, 5, 3, 6, 4}) == 5L);
        check(maxSingleTradeProfit(new int[]{7, 6, 4, 3, 1}) == 0L);
        check(maxSingleTradeProfit(new int[]{5}) == 0L);
        check(maxSingleTradeProfit(new int[0]) == 0L);
        check(maxSingleTradeProfit(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) == 4_294_967_295L);
        check(maxSingleTradeProfit(new int[]{3, 3, 3}) == 0L);
        rejects(NullPointerException.class, () -> maxSingleTradeProfit(null));

        check(maxSubarraySum(new int[]{-2, 1, -3, 4, -1, 2, 1, -5, 4}) == 6L);
        check(maxSubarraySum(new int[]{-8, -3, -5}) == -3L);
        check(maxSubarraySum(new int[]{0}) == 0L);
        check(maxSubarraySum(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}) == 4_294_967_294L);
        check(maxSubarraySum(new int[]{5, -10, 6}) == 6L);
        rejects(IllegalArgumentException.class, () -> maxSubarraySum(new int[0]));
        rejects(NullPointerException.class, () -> maxSubarraySum(null));

        check(majorityElement(new int[]{2, 2, 1, 1, 1, 2, 2}).orElseThrow() == 2);
        check(majorityElement(new int[]{3}).orElseThrow() == 3);
        check(majorityElement(new int[]{1, 2, 3}).isEmpty());
        check(majorityElement(new int[]{1, 1, 2, 2}).isEmpty());
        check(majorityElement(new int[0]).isEmpty());
        check(majorityElement(new int[]{-1, -1, -1, 2, 3}).orElseThrow() == -1);
        rejects(NullPointerException.class, () -> majorityElement(null));

        Random random = new Random(0xA44A7L);
        for (int round = 0; round < 10_000; round++) {
            int length = random.nextInt(26);
            int[] sample = new int[length];
            for (int i = 0; i < length; i++) sample[i] = random.nextInt(41) - 20;
            long target = random.nextInt(81) - 40;
            validatePair(sample, target, twoSumIndices(sample, target));
            if (maxSingleTradeProfit(sample) != bruteProfit(sample)) throw new AssertionError("profit oracle");
            if (length > 0 && maxSubarraySum(sample) != bruteMaxSubarray(sample)) {
                throw new AssertionError("Kadane oracle");
            }
            if (!majorityElement(sample).equals(bruteMajority(sample))) {
                throw new AssertionError("majority oracle");
            }
        }
        System.out.println("ArrayCorePatterns: " + explicitChecks
                + " explicit scenario groups + 40,000 randomized oracle comparisons passed");
    }
}
