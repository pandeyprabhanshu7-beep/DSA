import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for first/last target positions. */
public final class SearchRangeContracts {
    private record Bounds(int first, int last, int lowerInspections, int upperInspections) {}

    private static int groups;
    private static long checks;

    static int[] searchRange(int[] values, int target) {
        Bounds bounds = searchWithEvidence(values, target);
        return new int[] {bounds.first(), bounds.last()};
    }

    static int[] validatedSearchRange(int[] values, int target) {
        requireSorted(values);
        return searchRange(values, target);
    }

    private static Bounds searchWithEvidence(int[] values, int target) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        int[] lower = boundary(values, target, false);
        int first = lower[0];
        if (first == values.length || values[first] != target) {
            return new Bounds(-1, -1, lower[1], 0);
        }
        int[] upper = boundary(values, target, true);
        return new Bounds(first, upper[0] - 1, lower[1], upper[1]);
    }

    /** First index with value >= target, or values.length. */
    static int lowerBound(int[] values, int target) {
        return boundary(values, target, false)[0];
    }

    /** First index with value > target, or values.length. */
    static int upperBound(int[] values, int target) {
        return boundary(values, target, true)[0];
    }

    private static int[] boundary(int[] values, int target, boolean strict) {
        int low = 0;
        int high = values.length;
        int inspections = 0;
        while (low < high) {
            inspections++;
            int mid = low + ((high - low) >>> 1);
            boolean belongsRight = strict ? values[mid] > target : values[mid] >= target;
            if (belongsRight) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return new int[] {low, inspections};
    }

    private static void requireSorted(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        for (int index = 1; index < values.length; index++) {
            if (values[index - 1] > values[index]) {
                throw new IllegalArgumentException("values must be sorted nondecreasing");
            }
        }
    }

    private static int[] linearOracle(int[] values, int target) {
        int first = -1;
        int last = -1;
        for (int index = 0; index < values.length; index++) {
            if (values[index] == target) {
                if (first < 0) {
                    first = index;
                }
                last = index;
            }
        }
        return new int[] {first, last};
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
        int[] canonical = {5, 7, 7, 8, 8, 10};
        group("canonical range", Arrays.equals(searchRange(canonical, 8), new int[] {3, 4}));
        group("canonical absent", Arrays.equals(searchRange(canonical, 6), new int[] {-1, -1}));
        group("all equal", Arrays.equals(searchRange(new int[] {2, 2, 2, 2}, 2), new int[] {0, 3}));
        group("empty", Arrays.equals(searchRange(new int[0], 8), new int[] {-1, -1}));
        group("singleton present", Arrays.equals(searchRange(new int[] {4}, 4), new int[] {0, 0}));
        group("singleton absent", Arrays.equals(searchRange(new int[] {4}, 5), new int[] {-1, -1}));
        group("below minimum", Arrays.equals(searchRange(canonical, 4), new int[] {-1, -1}));
        group("above maximum", Arrays.equals(searchRange(canonical, 11), new int[] {-1, -1}));
        group("left endpoint duplicates", Arrays.equals(searchRange(new int[] {-3, -3, -1, 2}, -3), new int[] {0, 1}));
        group("right endpoint duplicates", Arrays.equals(searchRange(new int[] {-3, 2, 9, 9}, 9), new int[] {2, 3}));
        group("minimum integer", Arrays.equals(searchRange(new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, 0}, Integer.MIN_VALUE), new int[] {0, 1}));
        group("maximum integer", Arrays.equals(searchRange(new int[] {0, Integer.MAX_VALUE, Integer.MAX_VALUE}, Integer.MAX_VALUE), new int[] {1, 2}));
        group("lower boundary", lowerBound(canonical, 8) == 3);
        group("upper boundary", upperBound(canonical, 8) == 5);
        group("insertion at start", lowerBound(canonical, Integer.MIN_VALUE) == 0);
        group("insertion at end", upperBound(canonical, Integer.MAX_VALUE) == canonical.length);
        int[] snapshot = canonical.clone();
        searchRange(canonical, 7);
        group("input preserved", Arrays.equals(canonical, snapshot));
        group("validated sorted", Arrays.equals(validatedSearchRange(canonical, 7), new int[] {1, 2}));
        expectIllegalArgument("unsorted rejected", () -> validatedSearchRange(new int[] {2, 1, 2}, 2));
        expectIllegalArgument("null rejected", () -> searchRange(null, 1));
    }

    private static void randomizedEvidence() {
        Random random = new Random(20261104L);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = random.nextInt(81);
            int[] values = new int[length];
            int value = random.nextInt(41) - 20;
            for (int index = 0; index < length; index++) {
                value += random.nextInt(3);
                values[index] = value;
            }
            int target;
            int choice = random.nextInt(5);
            if (choice == 0 && length > 0) {
                target = values[random.nextInt(length)];
            } else if (choice == 1) {
                target = Integer.MIN_VALUE;
            } else if (choice == 2) {
                target = Integer.MAX_VALUE;
            } else {
                target = random.nextInt(121) - 60;
            }

            int[] snapshot = values.clone();
            Bounds actual = searchWithEvidence(values, target);
            int[] expected = linearOracle(values, target);
            int logarithmicBound = length == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(length);
            checks += 4;
            if (actual.first() != expected[0] || actual.last() != expected[1]) {
                throw new AssertionError("oracle mismatch trial=" + trial);
            }
            if (!Arrays.equals(values, snapshot)) {
                throw new AssertionError("input mutated trial=" + trial);
            }
            if (actual.lowerInspections() > logarithmicBound || actual.upperInspections() > logarithmicBound) {
                throw new AssertionError("inspection bound trial=" + trial);
            }
            if ((actual.first() < 0) != (actual.last() < 0)) {
                throw new AssertionError("partial absent range trial=" + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|INTERVAL=HALF_OPEN|FIRST=LOWER_BOUND|LAST=UPPER_BOUND_MINUS_ONE|ABSENT=-1,-1|TARGET_PLUS_ONE=AVOIDED");
        System.out.println("SearchRangeContracts: 20 scenario groups + 20000 seeded oracle comparisons passed; checks=" + checks);
    }
}
