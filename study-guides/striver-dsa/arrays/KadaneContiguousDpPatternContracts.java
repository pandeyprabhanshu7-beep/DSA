import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for four contiguous dynamic-programming mechanisms. */
public final class KadaneContiguousDpPatternContracts {
    static long maximumSubarraySum(int[] values) {
        requireNonEmpty(values);
        long ending = values[0];
        long best = ending;
        for (int i = 1; i < values.length; i++) {
            ending = Math.max(values[i], Math.addExact(ending, values[i]));
            best = Math.max(best, ending);
        }
        return best;
    }

    static long maximumProductSubarrayExact(int[] values) {
        requireNonEmpty(values);
        long maximumEnding = values[0];
        long minimumEnding = values[0];
        long best = maximumEnding;
        for (int i = 1; i < values.length; i++) {
            long value = values[i];
            long fromMaximum = Math.multiplyExact(maximumEnding, value);
            long fromMinimum = Math.multiplyExact(minimumEnding, value);
            maximumEnding = Math.max(value, Math.max(fromMaximum, fromMinimum));
            minimumEnding = Math.min(value, Math.min(fromMaximum, fromMinimum));
            best = Math.max(best, maximumEnding);
        }
        return best;
    }

    static long maximumSubarrayWithAtMostOneDeletion(int[] values) {
        requireNonEmpty(values);
        long keep = values[0];
        long drop = Long.MIN_VALUE;
        long best = keep;
        for (int i = 1; i < values.length; i++) {
            long value = values[i];
            long nextDrop = Math.max(keep,
                    drop == Long.MIN_VALUE ? Long.MIN_VALUE : Math.addExact(drop, value));
            long nextKeep = Math.max(value, Math.addExact(keep, value));
            keep = nextKeep;
            drop = nextDrop;
            best = Math.max(best, Math.max(keep, drop));
        }
        return best;
    }

    static long maximumTwoFixedNonOverlapping(int[] values, int firstLength, int secondLength) {
        requireNonEmpty(values);
        if (firstLength <= 0 || secondLength <= 0
                || (long) firstLength + secondLength > values.length) {
            throw new IllegalArgumentException("positive lengths must fit without overlap");
        }
        long[] prefix = new long[values.length + 1];
        for (int i = 0; i < values.length; i++) {
            prefix[i + 1] = Math.addExact(prefix[i], values[i]);
        }
        return Math.max(bestOrdered(prefix, firstLength, secondLength),
                bestOrdered(prefix, secondLength, firstLength));
    }

    private static long bestOrdered(long[] prefix, int leftLength, int rightLength) {
        int length = prefix.length - 1;
        long bestLeft = range(prefix, 0, leftLength);
        long best = Long.MIN_VALUE;
        for (int rightStart = leftLength; rightStart + rightLength <= length; rightStart++) {
            bestLeft = Math.max(bestLeft, range(prefix, rightStart - leftLength, rightStart));
            long right = range(prefix, rightStart, rightStart + rightLength);
            best = Math.max(best, Math.addExact(bestLeft, right));
        }
        return best;
    }

    private static long range(long[] prefix, int left, int rightExclusive) {
        return prefix[rightExclusive] - prefix[left];
    }

    private static void requireNonEmpty(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must be non-null and non-empty");
        }
    }

    private static long sumOracle(int[] values) {
        long best = Long.MIN_VALUE;
        for (int left = 0; left < values.length; left++) {
            long sum = 0;
            for (int right = left; right < values.length; right++) {
                sum += values[right];
                best = Math.max(best, sum);
            }
        }
        return best;
    }

    private static long productOracle(int[] values) {
        long best = Long.MIN_VALUE;
        for (int left = 0; left < values.length; left++) {
            long product = 1;
            for (int right = left; right < values.length; right++) {
                product = Math.multiplyExact(product, values[right]);
                best = Math.max(best, product);
            }
        }
        return best;
    }

    private static long deletionOracle(int[] values) {
        long best = sumOracle(values);
        for (int left = 0; left < values.length; left++) {
            for (int right = left; right < values.length; right++) {
                long total = 0;
                for (int i = left; i <= right; i++) total += values[i];
                for (int deleted = left; deleted <= right; deleted++) {
                    if (left == right) continue;
                    best = Math.max(best, total - values[deleted]);
                }
            }
        }
        return best;
    }

    private static long twoWindowOracle(int[] values, int firstLength, int secondLength) {
        long best = Long.MIN_VALUE;
        for (int first = 0; first + firstLength <= values.length; first++) {
            long firstSum = directRange(values, first, first + firstLength);
            for (int second = 0; second + secondLength <= values.length; second++) {
                if (first + firstLength <= second || second + secondLength <= first) {
                    best = Math.max(best,
                            Math.addExact(firstSum, directRange(values, second, second + secondLength)));
                }
            }
        }
        return best;
    }

    private static long directRange(int[] values, int left, int rightExclusive) {
        long sum = 0;
        for (int i = left; i < rightExclusive; i++) sum += values[i];
        return sum;
    }

    private static int deterministicGroups() {
        checkSum(new int[]{-2, 1, -3, 4, -1, 2, 1, -5, 4}, 6);
        checkSum(new int[]{5}, 5);
        checkSum(new int[]{-8, -3, -6}, -3);
        checkSum(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 4_294_967_294L);
        checkSum(new int[]{0, -1, 0}, 0);

        checkProduct(new int[]{2, 3, -2, 4}, 6);
        checkProduct(new int[]{-2, 0, -1}, 0);
        checkProduct(new int[]{-2, 3, -4}, 24);
        checkProduct(new int[]{-1}, -1);
        checkProduct(new int[]{Integer.MAX_VALUE, 2}, 4_294_967_294L);

        checkDeletion(new int[]{1, -2, 0, 3}, 4);
        checkDeletion(new int[]{1, -2, -2, 3}, 3);
        checkDeletion(new int[]{-1, -1, -1}, -1);
        checkDeletion(new int[]{5}, 5);
        checkDeletion(new int[]{8, -100, 9}, 17);

        checkTwo(new int[]{0, 6, 5, 2, 2, 5, 1, 9, 4}, 1, 2, 20);
        checkTwo(new int[]{3, 8, 1, 3, 2, 1, 8, 9, 0}, 3, 2, 29);
        checkTwo(new int[]{2, 1, 5, 6, 0, 9, 5, 0, 3, 8}, 4, 3, 31);
        checkTwo(new int[]{-5, -1, -3, -2}, 1, 1, -3);
        checkTwo(new int[]{1, 2}, 1, 1, 3);

        expectFailure(() -> maximumSubarraySum(null));
        expectFailure(() -> maximumSubarraySum(new int[]{}));
        expectFailure(() -> maximumProductSubarrayExact(new int[]{}));
        expectArithmeticFailure(() -> maximumProductSubarrayExact(
                new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}));
        expectFailure(() -> maximumSubarrayWithAtMostOneDeletion(new int[]{}));
        expectFailure(() -> maximumTwoFixedNonOverlapping(new int[]{1}, 1, 1));
        expectFailure(() -> maximumTwoFixedNonOverlapping(new int[]{1, 2}, 0, 1));
        return 20;
    }

    private static long randomizedChecks() {
        Random random = new Random(0xCAD4AEL);
        long checks = 0;
        for (int trial = 0; trial < 20_000; trial++) {
            int[] sums = randomArray(random, 1 + random.nextInt(10), -10, 10);
            int[] sumsCopy = sums.clone();
            require(maximumSubarraySum(sums) == sumOracle(sums), "sum oracle mismatch");
            checks++;
            require(maximumSubarrayWithAtMostOneDeletion(sums) == deletionOracle(sums),
                    "deletion oracle mismatch");
            checks++;

            int[] products = randomArray(random, 1 + random.nextInt(8), -3, 3);
            int[] productsCopy = products.clone();
            require(maximumProductSubarrayExact(products) == productOracle(products),
                    "product oracle mismatch");
            checks++;

            int[] windows = randomArray(random, 2 + random.nextInt(9), -10, 10);
            int[] windowsCopy = windows.clone();
            int firstLength = 1 + random.nextInt(windows.length - 1);
            int secondLength = 1 + random.nextInt(windows.length - firstLength);
            require(maximumTwoFixedNonOverlapping(windows, firstLength, secondLength)
                            == twoWindowOracle(windows, firstLength, secondLength),
                    "two-window oracle mismatch");
            checks++;

            require(Arrays.equals(sums, sumsCopy) && Arrays.equals(products, productsCopy)
                            && Arrays.equals(windows, windowsCopy),
                    "a contract mutated caller input");
            checks++;
        }
        return checks;
    }

    private static int[] randomArray(Random random, int length, int low, int high) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = low + random.nextInt(high - low + 1);
        return values;
    }

    private static void checkSum(int[] input, long expected) {
        require(maximumSubarraySum(input) == expected, "unexpected maximum sum");
    }

    private static void checkProduct(int[] input, long expected) {
        require(maximumProductSubarrayExact(input) == expected, "unexpected maximum product");
    }

    private static void checkDeletion(int[] input, long expected) {
        require(maximumSubarrayWithAtMostOneDeletion(input) == expected, "unexpected deletion result");
    }

    private static void checkTwo(int[] input, int firstLength, int secondLength, long expected) {
        require(maximumTwoFixedNonOverlapping(input, firstLength, secondLength) == expected,
                "unexpected two-window result");
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
        System.out.printf("KadaneContiguousDpPatternContracts: %d scenario groups + 20000 seeded oracle trials passed; checks=%d%n",
                groups, checks);
    }
}
