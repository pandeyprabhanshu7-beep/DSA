import java.util.Arrays;
import java.util.Random;

public class SortColorsContracts {
    private record Metrics(int inspections, int swaps) {}

    private static int scenarios;

    static void sortColors(int[] nums) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        for (int value : nums) {
            if (value < 0 || value > 2) {
                throw new IllegalArgumentException("every value must be 0, 1, or 2");
            }
        }
        sortColorsCore(nums);
    }

    static Metrics sortColorsCore(int[] nums) {
        int low = 0;
        int scan = 0;
        int high = nums.length - 1;
        int inspections = 0;
        int swaps = 0;

        while (scan <= high) {
            inspections++;
            if (nums[scan] == 0) {
                swap(nums, low, scan);
                swaps++;
                low++;
                scan++;
            } else if (nums[scan] == 1) {
                scan++;
            } else {
                swap(nums, scan, high);
                swaps++;
                high--;
            }
        }
        return new Metrics(inspections, swaps);
    }

    private static void swap(int[] nums, int left, int right) {
        int temporary = nums[left];
        nums[left] = nums[right];
        nums[right] = temporary;
    }

    private static void check(int... input) {
        int[] actual = input.clone();
        int[] expected = input.clone();
        Arrays.sort(expected);

        Metrics metrics = sortColorsCore(actual);
        assert Arrays.equals(actual, expected)
                : "expected " + Arrays.toString(expected) + " but got "
                + Arrays.toString(actual) + " for " + Arrays.toString(input);
        assert metrics.inspections() == input.length
                : "each element should be classified exactly once";
        assert metrics.swaps() <= input.length : "too many swaps";
        assert sameCounts(input, actual) : "color multiplicities changed";
        scenarios++;
    }

    private static boolean sameCounts(int[] left, int[] right) {
        int[] leftCounts = new int[3];
        int[] rightCounts = new int[3];
        for (int value : left) {
            leftCounts[value]++;
        }
        for (int value : right) {
            rightCounts[value]++;
        }
        return Arrays.equals(leftCounts, rightCounts);
    }

    private static void expectNullRejected() {
        try {
            sortColors(null);
            throw new AssertionError("null input should be rejected");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    private static void expectInvalidRejectedWithoutMutation(int... input) {
        int[] before = input.clone();
        try {
            sortColors(input);
            throw new AssertionError("invalid color should be rejected");
        } catch (IllegalArgumentException expected) {
            assert Arrays.equals(input, before) : "validation failure mutated input";
            scenarios++;
        }
    }

    public static void main(String[] args) {
        check(2, 0, 2, 1, 1, 0);
        check(1, 2, 0);
        check(0, 0, 1, 1, 2, 2);
        check(2, 2, 2);
        check();
        check(0);
        check(1);
        check(2);
        check(2, 1, 0);
        check(0, 2, 1);
        check(1, 0, 2);
        check(0, 0, 0);
        check(1, 1, 1);
        check(2, 2, 2, 0, 0, 1);
        check(0, 2, 0, 2, 0, 2);
        check(1, 2, 1, 0, 1, 2, 0);
        check(2, 0, 1, 2, 0, 1, 2, 0, 1);
        expectNullRejected();
        expectInvalidRejectedWithoutMutation(2, 0, 3, 1);
        expectInvalidRejectedWithoutMutation(-1, 0, 1, 2);

        Random random = new Random(0x19C01025L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] input = new int[random.nextInt(101)];
            for (int i = 0; i < input.length; i++) {
                input[i] = random.nextInt(3);
            }

            int[] actual = input.clone();
            int[] expected = input.clone();
            Arrays.sort(expected);
            Metrics metrics = sortColorsCore(actual);

            assert Arrays.equals(actual, expected)
                    : "oracle mismatch for " + Arrays.toString(input);
            assert metrics.inspections() == input.length
                    : "random classification count mismatch";
            assert metrics.swaps() <= input.length : "random swap bound exceeded";
            assert sameCounts(input, actual) : "random multiplicities changed";
        }

        System.out.println("Sort Colors contracts passed: " + scenarios
                + " scenario groups + 20000 seeded Arrays.sort oracle comparisons");
    }
}
