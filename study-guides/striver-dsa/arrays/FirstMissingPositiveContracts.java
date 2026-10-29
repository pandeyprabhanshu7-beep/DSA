import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class FirstMissingPositiveContracts {
    private record Result(int answer, int swaps) {}

    private static int scenarios;

    static int firstMissingPositive(int[] nums) {
        return firstMissingPositiveMeasured(nums).answer();
    }

    static Result firstMissingPositiveMeasured(int[] nums) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        int swaps = 0;
        for (int i = 0; i < nums.length; i++) {
            while (nums[i] >= 1 && nums[i] <= nums.length) {
                int home = nums[i] - 1;
                if (nums[home] == nums[i]) {
                    break;
                }
                int temporary = nums[i];
                nums[i] = nums[home];
                nums[home] = temporary;
                swaps++;
            }
        }
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] != i + 1) {
                return new Result(i + 1, swaps);
            }
        }
        return new Result(nums.length + 1, swaps);
    }

    private static int setOracle(int[] nums) {
        Set<Integer> values = new HashSet<>();
        for (int value : nums) {
            if (value > 0) {
                values.add(value);
            }
        }
        int candidate = 1;
        while (values.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private static void assertPostcondition(int[] placed, int answer) {
        for (int value = 1; value < answer; value++) {
            assert placed[value - 1] == value : "smaller candidate is not home";
        }
        if (answer <= placed.length) {
            assert placed[answer - 1] != answer : "reported candidate is present at home";
        }
    }

    private static void check(int[] input, int expected) {
        int[] sameReference = input;
        int[] before = input.clone();
        int oracle = setOracle(before);
        Result result = firstMissingPositiveMeasured(input);

        assert input == sameReference : "implementation replaced the caller array";
        assert result.answer() == expected : "expected " + expected + " but got " + result.answer();
        assert result.answer() == oracle : "independent oracle disagreement";
        assert result.swaps() <= input.length : "permanent-home swap bound exceeded";
        int[] sortedBefore = before.clone();
        int[] sortedAfter = input.clone();
        Arrays.sort(sortedBefore);
        Arrays.sort(sortedAfter);
        assert Arrays.equals(sortedBefore, sortedAfter) : "component multiset changed";
        assertPostcondition(input, result.answer());
        scenarios++;
    }

    private static void rejectsNull() {
        try {
            firstMissingPositive(null);
            throw new AssertionError("null should be rejected");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    private static int randomComponent(Random random, int length) {
        return switch (random.nextInt(8)) {
            case 0 -> Integer.MIN_VALUE;
            case 1 -> Integer.MAX_VALUE;
            case 2 -> 0;
            case 3 -> 1;
            case 4 -> length;
            default -> random.nextInt(4 * length + 9) - length - 4;
        };
    }

    public static void main(String[] args) {
        check(new int[] {3, 4, -1, 1}, 2);
        check(new int[] {1, 2, 0}, 3);
        check(new int[] {7, 8, 9, 11, 12}, 1);
        check(new int[] {1, 1, 2, 2}, 3);
        check(new int[] {}, 1);
        check(new int[] {1}, 2);
        check(new int[] {2}, 1);
        check(new int[] {2, 1}, 3);
        check(new int[] {2, 2}, 1);
        check(new int[] {-1, -2, -3}, 1);
        check(new int[] {0, 0, 0}, 1);
        check(new int[] {1, 2, 3, 4, 5}, 6);
        check(new int[] {5, 4, 3, 2, 1}, 6);
        check(new int[] {2, 3, 4, 5, 1}, 6);
        check(new int[] {2, 3, 4, 5, 6}, 1);
        check(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}, 1);
        check(new int[] {1, Integer.MIN_VALUE, 2, Integer.MAX_VALUE}, 3);
        check(new int[] {4, 3, 4, 1, 2}, 5);
        check(new int[] {2, 1, 1, 4, 3}, 5);
        rejectsNull();

        Random random = new Random(0xF1A57A55L);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = random.nextInt(81);
            int[] input = new int[length];
            for (int i = 0; i < input.length; i++) {
                input[i] = randomComponent(random, length);
            }
            int[] before = input.clone();
            int expected = setOracle(before);
            Result result = firstMissingPositiveMeasured(input);
            assert result.answer() == expected
                    : "random oracle mismatch for " + Arrays.toString(before);
            assert result.swaps() <= input.length : "random swap bound";
            assertPostcondition(input, result.answer());
            int[] sortedBefore = before.clone();
            int[] sortedAfter = input.clone();
            Arrays.sort(sortedBefore);
            Arrays.sort(sortedAfter);
            assert Arrays.equals(sortedBefore, sortedAfter) : "random multiset changed";
        }

        Result trace = firstMissingPositiveMeasured(new int[] {3, 4, -1, 1});
        assert trace.answer() == 2;
        assert trace.swaps() == 3;

        System.out.println("First Missing Positive contracts passed: " + scenarios
                + " scenario groups + 20000 seeded HashSet-oracle comparisons");
    }
}
