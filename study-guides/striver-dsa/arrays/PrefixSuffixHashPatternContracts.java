import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Dependency-free executable contracts for prefix, suffix, hash, and difference boundaries. */
public final class PrefixSuffixHashPatternContracts {
    static final class PrefixRangeSums {
        private final long[] prefix;

        PrefixRangeSums(int[] values) {
            requireArray(values);
            prefix = new long[values.length + 1];
            for (int i = 0; i < values.length; i++) {
                prefix[i + 1] = Math.addExact(prefix[i], values[i]);
            }
        }

        long rangeSum(int left, int right) {
            if (left < 0 || left > right || right >= prefix.length - 1) {
                throw new IllegalArgumentException("require 0 <= left <= right < length");
            }
            return prefix[right + 1] - prefix[left];
        }

        int length() {
            return prefix.length - 1;
        }
    }

    static long[] exclusiveProductsExact(int[] values) {
        requireArray(values);
        long[] answer = new long[values.length];
        long leftProduct = 1;
        for (int i = 0; i < values.length; i++) {
            answer[i] = leftProduct;
            leftProduct = Math.multiplyExact(leftProduct, values[i]);
        }
        long rightProduct = 1;
        for (int i = values.length - 1; i >= 0; i--) {
            answer[i] = Math.multiplyExact(answer[i], rightProduct);
            rightProduct = Math.multiplyExact(rightProduct, values[i]);
        }
        return answer;
    }

    static long countSubarraysEqualTo(int[] values, long target) {
        requireArray(values);
        Map<Long, Long> frequencies = new HashMap<>();
        frequencies.put(0L, 1L);
        long prefix = 0;
        long count = 0;
        for (int value : values) {
            prefix = Math.addExact(prefix, value);
            count = Math.addExact(count, frequencies.getOrDefault(prefix - target, 0L));
            frequencies.merge(prefix, 1L, Math::addExact);
        }
        return count;
    }

    static long[] applyInclusiveRangeAdds(int length, int[][] updates) {
        if (length < 0) throw new IllegalArgumentException("length must be nonnegative");
        if (updates == null) throw new IllegalArgumentException("updates must be non-null");
        for (int[] update : updates) {
            if (update == null || update.length != 3) {
                throw new IllegalArgumentException("each update must be [left,right,delta]");
            }
            if (update[0] < 0 || update[0] > update[1] || update[1] >= length) {
                throw new IllegalArgumentException("update range is outside the output");
            }
        }

        long[] difference = new long[length + 1];
        for (int[] update : updates) {
            int left = update[0];
            int afterRight = update[1] + 1;
            long delta = update[2];
            difference[left] = Math.addExact(difference[left], delta);
            difference[afterRight] = Math.subtractExact(difference[afterRight], delta);
        }
        long[] answer = new long[length];
        long running = 0;
        for (int i = 0; i < length; i++) {
            running = Math.addExact(running, difference[i]);
            answer[i] = running;
        }
        return answer;
    }

    private static void requireArray(int[] values) {
        if (values == null) throw new IllegalArgumentException("values must be non-null");
    }

    private static long directRange(int[] values, int left, int right) {
        long sum = 0;
        for (int i = left; i <= right; i++) sum += values[i];
        return sum;
    }

    private static long[] productOracle(int[] values) {
        long[] answer = new long[values.length];
        for (int excluded = 0; excluded < values.length; excluded++) {
            long product = 1;
            for (int i = 0; i < values.length; i++) {
                if (i != excluded) product = Math.multiplyExact(product, values[i]);
            }
            answer[excluded] = product;
        }
        return answer;
    }

    private static long subarrayOracle(int[] values, long target) {
        long count = 0;
        for (int left = 0; left < values.length; left++) {
            long sum = 0;
            for (int right = left; right < values.length; right++) {
                sum += values[right];
                if (sum == target) count++;
            }
        }
        return count;
    }

    private static long[] updateOracle(int length, int[][] updates) {
        long[] answer = new long[length];
        for (int[] update : updates) {
            for (int i = update[0]; i <= update[1]; i++) answer[i] += update[2];
        }
        return answer;
    }

    private static int deterministicGroups() {
        checkRange(new int[]{-2, 0, 3, -5, 2, -1}, 0, 2, 1);
        checkRange(new int[]{-2, 0, 3, -5, 2, -1}, 2, 5, -1);
        checkRange(new int[]{7}, 0, 0, 7);
        checkRange(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 0, 1, 4_294_967_294L);
        checkRange(new int[]{4, -4, 9, -9}, 1, 3, -4);

        checkProducts(new int[]{1, 2, 3, 4}, new long[]{24, 12, 8, 6});
        checkProducts(new int[]{-1, 1, 0, -3, 3}, new long[]{0, 0, 9, 0, 0});
        checkProducts(new int[]{0, 0, 2}, new long[]{0, 0, 0});
        checkProducts(new int[]{5}, new long[]{1});
        checkProducts(new int[]{}, new long[]{});

        checkCount(new int[]{1, 1, 1}, 2, 2);
        checkCount(new int[]{1, 2, 3}, 3, 2);
        checkCount(new int[]{1, -1, 0}, 0, 3);
        checkCount(new int[]{0, 0, 0}, 0, 6);
        checkCount(new int[]{Integer.MAX_VALUE, 1, -Integer.MAX_VALUE}, 1, 2);

        checkUpdates(5, new int[][]{{0, 1, 10}, {1, 2, 20}, {1, 4, 25}},
                new long[]{10, 55, 45, 25, 25});
        checkUpdates(2, new int[][]{{0, 1, 10}, {1, 1, 15}}, new long[]{10, 25});
        checkUpdates(4, new int[][]{}, new long[]{0, 0, 0, 0});
        checkUpdates(1, new int[][]{{0, 0, -7}, {0, 0, 12}}, new long[]{5});
        checkUpdates(0, new int[][]{}, new long[]{});

        expectFailure(() -> new PrefixRangeSums(null));
        expectFailure(() -> new PrefixRangeSums(new int[]{}).rangeSum(0, 0));
        expectFailure(() -> new PrefixRangeSums(new int[]{1}).rangeSum(1, 0));
        expectFailure(() -> exclusiveProductsExact(null));
        expectArithmeticFailure(() -> exclusiveProductsExact(
                new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 2}));
        expectFailure(() -> countSubarraysEqualTo(null, 0));
        expectFailure(() -> applyInclusiveRangeAdds(-1, new int[][]{}));
        expectFailure(() -> applyInclusiveRangeAdds(2, null));
        expectFailure(() -> applyInclusiveRangeAdds(2, new int[][]{{0, 2, 1}}));
        expectFailure(() -> applyInclusiveRangeAdds(2, new int[][]{{0, 1}}));
        return 20;
    }

    private static long randomizedChecks() {
        Random random = new Random(0x50EFA55EL);
        long checks = 0;
        for (int trial = 0; trial < 20_000; trial++) {
            int[] rangeValues = randomArray(random, 1 + random.nextInt(12), -20, 20);
            int[] rangeCopy = rangeValues.clone();
            PrefixRangeSums ranges = new PrefixRangeSums(rangeValues);
            int left = random.nextInt(rangeValues.length);
            int right = left + random.nextInt(rangeValues.length - left);
            require(ranges.rangeSum(left, right) == directRange(rangeValues, left, right)
                    && Arrays.equals(rangeValues, rangeCopy), "range oracle or preservation mismatch");
            checks++;
            long singletonTotal = 0;
            for (int i = 0; i < ranges.length(); i++) singletonTotal += ranges.rangeSum(i, i);
            require(singletonTotal == ranges.rangeSum(0, ranges.length() - 1), "range partition mismatch");
            checks++;

            int[] productValues = randomArray(random, random.nextInt(9), -3, 3);
            int[] productCopy = productValues.clone();
            require(Arrays.equals(exclusiveProductsExact(productValues), productOracle(productValues))
                    && Arrays.equals(productValues, productCopy), "product oracle or preservation mismatch");
            checks++;

            int[] countValues = randomArray(random, random.nextInt(13), -5, 5);
            long target = -10 + random.nextInt(21);
            require(countSubarraysEqualTo(countValues, target) == subarrayOracle(countValues, target),
                    "subarray-count oracle mismatch");
            checks++;

            int length = random.nextInt(11);
            int updateCount = length == 0 ? 0 : random.nextInt(9);
            int[][] updates = new int[updateCount][3];
            for (int i = 0; i < updateCount; i++) {
                int updateLeft = random.nextInt(length);
                int updateRight = updateLeft + random.nextInt(length - updateLeft);
                updates[i] = new int[]{updateLeft, updateRight, -10 + random.nextInt(21)};
            }
            require(Arrays.equals(applyInclusiveRangeAdds(length, updates), updateOracle(length, updates)),
                    "difference oracle mismatch");
            checks++;
        }
        return checks;
    }

    private static int[] randomArray(Random random, int length, int low, int high) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = low + random.nextInt(high - low + 1);
        return values;
    }

    private static void checkRange(int[] input, int left, int right, long expected) {
        int[] copy = input.clone();
        PrefixRangeSums sums = new PrefixRangeSums(input);
        require(sums.rangeSum(left, right) == expected, "unexpected range sum");
        require(Arrays.equals(input, copy), "range preprocessing must preserve input");
    }

    private static void checkProducts(int[] input, long[] expected) {
        int[] copy = input.clone();
        require(Arrays.equals(exclusiveProductsExact(input), expected), "unexpected exclusive products");
        require(Arrays.equals(input, copy), "exclusive products must preserve input");
    }

    private static void checkCount(int[] input, long target, long expected) {
        require(countSubarraysEqualTo(input, target) == expected, "unexpected subarray count");
    }

    private static void checkUpdates(int length, int[][] updates, long[] expected) {
        require(Arrays.equals(applyInclusiveRangeAdds(length, updates), expected), "unexpected range additions");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectArithmeticFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected ArithmeticException");
        } catch (ArithmeticException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int groups = deterministicGroups();
        long checks = randomizedChecks();
        System.out.printf("PrefixSuffixHashPatternContracts: %d scenario groups + 20000 seeded oracle trials passed; checks=%d%n",
                groups, checks);
    }
}
