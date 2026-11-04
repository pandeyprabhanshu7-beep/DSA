import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Search in Rotated Sorted Array. */
public final class SearchRotatedContracts {
    private record SearchResult(int index, int inspections) {}

    private static int groups;
    private static long checks;

    static int searchRotatedDistinct(int[] nums, int target) {
        return searchWithEvidence(nums, target).index();
    }

    private static SearchResult searchWithEvidence(int[] nums, int target) {
        if (nums == null) {
            throw new IllegalArgumentException("nums must not be null");
        }
        int left = 0;
        int right = nums.length - 1;
        int inspections = 0;
        while (left <= right) {
            inspections++;
            int mid = left + ((right - left) >>> 1);
            if (nums[mid] == target) {
                return new SearchResult(mid, inspections);
            }
            if (nums[left] <= nums[mid]) {
                if (nums[left] <= target && target < nums[mid]) {
                    right = mid - 1;
                } else {
                    left = mid + 1;
                }
            } else if (nums[mid] < target && target <= nums[right]) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return new SearchResult(-1, inspections);
    }

    private static int linearOracle(int[] nums, int target) {
        for (int index = 0; index < nums.length; index++) {
            if (nums[index] == target) {
                return index;
            }
        }
        return -1;
    }

    private static int[] rotateLeft(int[] sorted, int distance) {
        int[] rotated = new int[sorted.length];
        for (int index = 0; index < sorted.length; index++) {
            rotated[index] = sorted[(index + distance) % sorted.length];
        }
        return rotated;
    }

    private static void group(String name, boolean condition) {
        groups++;
        checks++;
        if (!condition) {
            throw new AssertionError("group failed: " + name);
        }
    }

    private static void expectIllegalArgument(String name, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        group(name, rejected);
    }

    private static void deterministicEvidence() {
        int[] canonical = {4, 5, 6, 7, 0, 1, 2};
        group("canonical target", searchRotatedDistinct(canonical, 0) == 4);
        group("canonical absent", searchRotatedDistinct(canonical, 3) == -1);
        group("unrotated", searchRotatedDistinct(new int[] {-10, -2, 3, 9, 14}, 9) == 3);
        group("singleton present", searchRotatedDistinct(new int[] {1}, 1) == 0);
        group("singleton absent", searchRotatedDistinct(new int[] {1}, 0) == -1);
        group("two element rotated low", searchRotatedDistinct(new int[] {3, -4}, -4) == 1);
        group("two element rotated high", searchRotatedDistinct(new int[] {3, -4}, 3) == 0);
        group("pivot value", searchRotatedDistinct(canonical, 0) == 4);
        group("before pivot", searchRotatedDistinct(canonical, 7) == 3);
        group("after pivot", searchRotatedDistinct(canonical, 2) == 6);
        group("left endpoint", searchRotatedDistinct(canonical, 4) == 0);
        group("right endpoint", searchRotatedDistinct(canonical, 2) == canonical.length - 1);
        group("absent below minimum", searchRotatedDistinct(canonical, -1) == -1);
        group("absent above maximum", searchRotatedDistinct(canonical, 8) == -1);
        group("empty extension", searchRotatedDistinct(new int[0], 9) == -1);
        expectIllegalArgument("null rejected", () -> searchRotatedDistinct(null, 1));

        int[] fullWidth = {0, Integer.MAX_VALUE, Integer.MIN_VALUE, -1};
        group("full width minimum", searchRotatedDistinct(fullWidth, Integer.MIN_VALUE) == 2);
        group("full width maximum", searchRotatedDistinct(fullWidth, Integer.MAX_VALUE) == 1);

        int[] fixed = {-20, -8, -1, 0, 4, 11, 29, 80};
        boolean everyRotation = true;
        for (int distance = 0; distance < fixed.length; distance++) {
            int[] rotated = rotateLeft(fixed, distance);
            for (int target : fixed) {
                everyRotation &= searchRotatedDistinct(rotated, target) == linearOracle(rotated, target);
            }
        }
        group("every fixed rotation", everyRotation);

        int[] preserved = canonical.clone();
        searchRotatedDistinct(canonical, 6);
        group("input preserved", Arrays.equals(canonical, preserved));
    }

    private static void randomizedEvidence() {
        Random random = new Random(20261104L);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = 1 + random.nextInt(80);
            int start = random.nextInt(100_001) - 50_000;
            int[] sorted = new int[length];
            int value = start;
            for (int index = 0; index < length; index++) {
                value += 1 + random.nextInt(7);
                sorted[index] = value;
            }
            int[] rotated = rotateLeft(sorted, random.nextInt(length));
            int[] snapshot = rotated.clone();
            int target;
            if (random.nextBoolean()) {
                target = rotated[random.nextInt(length)];
            } else {
                target = random.nextBoolean() ? sorted[0] - 1 : sorted[length - 1] + 1;
            }

            SearchResult actual = searchWithEvidence(rotated, target);
            int expected = linearOracle(rotated, target);
            checks += 3;
            if (actual.index() != expected) {
                throw new AssertionError("oracle mismatch trial=" + trial);
            }
            if (!Arrays.equals(rotated, snapshot)) {
                throw new AssertionError("input mutated trial=" + trial);
            }
            int logarithmicBound = 32 - Integer.numberOfLeadingZeros(length);
            if (actual.inspections() > logarithmicBound) {
                throw new AssertionError("inspection bound trial=" + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("SearchRotatedContracts: 20 scenario groups + 20000 seeded trials passed; checks=" + checks);
    }
}
