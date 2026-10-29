import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class FindDuplicateContracts {
    private record Result(int duplicate, int phaseOneMoves, int phaseTwoMoves) {}

    private static int scenarios;

    static int findDuplicate(int[] nums) {
        return findDuplicateMeasured(nums).duplicate();
    }

    static Result findDuplicateMeasured(int[] nums) {
        validateShapeAndRange(nums);

        int slow = nums[0];
        int fast = nums[0];
        int phaseOneMoves = 0;
        do {
            slow = nums[slow];
            fast = nums[nums[fast]];
            phaseOneMoves++;
        } while (slow != fast);

        slow = nums[0];
        int phaseTwoMoves = 0;
        while (slow != fast) {
            slow = nums[slow];
            fast = nums[fast];
            phaseTwoMoves++;
        }
        return new Result(slow, phaseOneMoves, phaseTwoMoves);
    }

    private static void validateShapeAndRange(int[] nums) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        if (nums.length < 2) {
            throw new IllegalArgumentException("nums must contain n+1 values");
        }
        int n = nums.length - 1;
        for (int value : nums) {
            if (value < 1 || value > n) {
                throw new IllegalArgumentException("every value must be in [1,n]");
            }
        }
    }

    private static int setOracle(int[] nums) {
        Set<Integer> seen = new HashSet<>();
        for (int value : nums) {
            if (!seen.add(value)) {
                return value;
            }
        }
        throw new IllegalArgumentException("valid input must contain a duplicate");
    }

    private static void check(int[] input, int expected) {
        int[] before = input.clone();
        int[] sameReference = input;
        Result result = findDuplicateMeasured(input);

        assert input == sameReference : "implementation replaced the caller array";
        assert Arrays.equals(input, before) : "implementation mutated the input";
        assert result.duplicate() == expected
                : "expected " + expected + " but got " + result.duplicate();
        assert result.duplicate() == setOracle(input) : "independent oracle disagreement";
        assert result.phaseOneMoves() <= input.length : "phase one move bound exceeded";
        assert result.phaseTwoMoves() <= input.length : "phase two move bound exceeded";
        scenarios++;
    }

    private static void rejects(int[] input) {
        try {
            findDuplicate(input);
            throw new AssertionError("invalid input should be rejected");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    private static int[] randomValidInput(Random random) {
        int n = 1 + random.nextInt(60);
        int duplicate = 1 + random.nextInt(n);
        int multiplicity = 2 + random.nextInt(n);
        int[] nums = new int[n + 1];
        int write = 0;
        for (int copy = 0; copy < multiplicity; copy++) {
            nums[write++] = duplicate;
        }
        for (int value = 1; value <= n && write < nums.length; value++) {
            if (value != duplicate) {
                nums[write++] = value;
            }
        }
        for (int i = nums.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temporary = nums[i];
            nums[i] = nums[j];
            nums[j] = temporary;
        }
        return nums;
    }

    public static void main(String[] args) {
        check(new int[] {1, 3, 4, 2, 2}, 2);
        check(new int[] {3, 1, 3, 4, 2}, 3);
        check(new int[] {3, 3, 3, 3, 3}, 3);
        check(new int[] {1, 4, 6, 3, 2, 5, 6}, 6);
        check(new int[] {1, 1}, 1);
        check(new int[] {2, 1, 2}, 2);
        check(new int[] {4, 1, 2, 3, 4}, 4);
        check(new int[] {1, 2, 3, 4, 1}, 1);
        check(new int[] {2, 2, 2}, 2);
        check(new int[] {5, 4, 3, 2, 1, 5}, 5);
        check(new int[] {2, 4, 1, 3, 2}, 2);
        check(new int[] {6, 1, 5, 4, 3, 2, 6}, 6);
        check(new int[] {1, 5, 2, 4, 3, 5}, 5);
        check(new int[] {3, 2, 1, 3}, 3);
        check(new int[] {1, 2, 1}, 1);
        rejects(null);
        rejects(new int[] {});
        rejects(new int[] {1});
        rejects(new int[] {0, 1});
        rejects(new int[] {1, 3, 1});

        Random random = new Random(0xD0B1CA7EL);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] input = randomValidInput(random);
            int[] before = input.clone();
            Result result = findDuplicateMeasured(input);
            assert result.duplicate() == setOracle(input)
                    : "random oracle mismatch for " + Arrays.toString(input);
            assert Arrays.equals(input, before) : "random input mutated";
            assert result.phaseOneMoves() <= input.length : "random phase one bound";
            assert result.phaseTwoMoves() <= input.length : "random phase two bound";
        }

        Result trace = findDuplicateMeasured(new int[] {1, 3, 4, 2, 2});
        assert trace.duplicate() == 2;
        assert trace.phaseOneMoves() == 2;
        assert trace.phaseTwoMoves() == 2;

        System.out.println("Find Duplicate contracts passed: " + scenarios
                + " scenario groups + 20000 seeded set-oracle comparisons");
    }
}
