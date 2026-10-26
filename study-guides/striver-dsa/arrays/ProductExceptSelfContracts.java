import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Product of Array Except Self. */
public final class ProductExceptSelfContracts {
    private static int scenarios;

    private ProductExceptSelfContracts() {}

    static int[] productExceptSelf(int[] nums) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        if (nums.length < 2) {
            throw new IllegalArgumentException("nums must contain at least two values");
        }

        int[] answer = new int[nums.length];
        int prefix = 1;
        for (int i = 0; i < nums.length; i++) {
            answer[i] = prefix;
            prefix = Math.multiplyExact(prefix, nums[i]);
        }

        int suffix = 1;
        for (int i = nums.length - 1; i >= 0; i--) {
            answer[i] = Math.multiplyExact(answer[i], suffix);
            suffix = Math.multiplyExact(suffix, nums[i]);
        }
        return answer;
    }

    private static int[] quadraticOracle(int[] nums) {
        int[] answer = new int[nums.length];
        for (int excluded = 0; excluded < nums.length; excluded++) {
            int product = 1;
            for (int i = 0; i < nums.length; i++) {
                if (i != excluded) {
                    product = Math.multiplyExact(product, nums[i]);
                }
            }
            answer[excluded] = product;
        }
        return answer;
    }

    private static void expects(int[] expected, int[] actual) {
        scenarios++;
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("expected " + Arrays.toString(expected)
                    + " but got " + Arrays.toString(actual));
        }
    }

    private static void rejects(int[] nums) {
        scenarios++;
        try {
            productExceptSelf(nums);
            throw new AssertionError("expected invalid input or overflow rejection");
        } catch (IllegalArgumentException | ArithmeticException expected) {
            // Expected reusable-contract behavior.
        }
    }

    public static void main(String[] args) {
        expects(new int[] {24, 12, 8, 6},
                productExceptSelf(new int[] {1, 2, 3, 4}));
        expects(new int[] {0, 0, 9, 0, 0},
                productExceptSelf(new int[] {-1, 1, 0, -3, 3}));
        expects(new int[] {0, 0, 0},
                productExceptSelf(new int[] {0, 4, 0}));
        expects(new int[] {-60, -40, 30, 24},
                productExceptSelf(new int[] {-2, -3, 4, 5}));
        expects(new int[] {7, 5}, productExceptSelf(new int[] {5, 7}));
        expects(new int[] {1, 1, 1, 1}, productExceptSelf(new int[] {1, 1, 1, 1}));
        expects(new int[] {6, 3, 2}, productExceptSelf(new int[] {1, 2, 3}));
        expects(new int[] {4, 4, 4}, productExceptSelf(new int[] {2, 2, 2}));
        expects(new int[] {1, Integer.MAX_VALUE},
                productExceptSelf(new int[] {Integer.MAX_VALUE, 1}));
        expects(new int[] {1, Integer.MIN_VALUE},
                productExceptSelf(new int[] {Integer.MIN_VALUE, 1}));
        expects(new int[] {0, -Integer.MAX_VALUE, 0},
                productExceptSelf(new int[] {Integer.MAX_VALUE, 0, -1}));

        rejects(null);
        rejects(new int[] {});
        rejects(new int[] {9});
        rejects(new int[] {100_000, 100_000, 2});

        int[] caller = {-1, 1, 0, -3, 3};
        int[] snapshot = caller.clone();
        productExceptSelf(caller);
        scenarios++;
        if (!Arrays.equals(snapshot, caller)) {
            throw new AssertionError("input was mutated");
        }

        scenarios++; // randomized oracle agreement
        Random random = new Random(303_015L);
        int comparisons = 20_000;
        for (int trial = 0; trial < comparisons; trial++) {
            int[] nums = new int[2 + random.nextInt(10)];
            for (int i = 0; i < nums.length; i++) {
                nums[i] = random.nextInt(7) - 3;
            }
            int[] original = nums.clone();
            int[] expected = quadraticOracle(nums);
            int[] actual = productExceptSelf(nums);
            if (!Arrays.equals(expected, actual) || !Arrays.equals(original, nums)) {
                throw new AssertionError("trial " + trial + " failed for "
                        + Arrays.toString(original) + ": expected "
                        + Arrays.toString(expected) + " but got "
                        + Arrays.toString(actual));
            }
        }

        System.out.println("Product Except Self contracts passed: " + scenarios
                + " scenario groups + " + comparisons + " seeded oracle comparisons");
    }
}
