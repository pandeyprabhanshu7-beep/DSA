import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for linear-scan state compression. */
public final class LinearScanStateTrackingContracts {
    static long maximumIncreasingDifference(int[] values) {
        requireArray(values, "values");
        if (values.length < 2) return -1;

        int minimum = values[0];
        long best = -1;
        for (int i = 1; i < values.length; i++) {
            int value = values[i];
            if (value > minimum) best = Math.max(best, (long) value - minimum);
            else if (value < minimum) minimum = value;
        }
        return best;
    }

    static boolean canPlaceFlowers(int[] flowerbed, int requested) {
        requireArray(flowerbed, "flowerbed");
        if (requested < 0) throw new IllegalArgumentException("requested must be non-negative");
        for (int i = 0; i < flowerbed.length; i++) {
            if (flowerbed[i] != 0 && flowerbed[i] != 1) {
                throw new IllegalArgumentException("flowerbed must contain only 0 and 1");
            }
            if (i > 0 && flowerbed[i - 1] == 1 && flowerbed[i] == 1) {
                throw new IllegalArgumentException("existing flowers must not be adjacent");
            }
        }

        int planted = 0;
        boolean previousOccupied = false;
        for (int i = 0; i < flowerbed.length; i++) {
            boolean currentOccupied = flowerbed[i] == 1;
            boolean nextOccupied = i + 1 < flowerbed.length && flowerbed[i + 1] == 1;
            if (!currentOccupied && !previousOccupied && !nextOccupied) {
                planted++;
                previousOccupied = true; // remember the simulated flower without mutating input
            } else {
                previousOccupied = currentOccupied;
            }
        }
        return planted >= requested;
    }

    static boolean isMonotonic(int[] values) {
        requireArray(values, "values");
        boolean sawIncrease = false;
        boolean sawDecrease = false;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[i - 1]) sawIncrease = true;
            if (values[i] < values[i - 1]) sawDecrease = true;
            if (sawIncrease && sawDecrease) return false;
        }
        return true;
    }

    static boolean hasIncreasingTriplet(int[] values) {
        requireArray(values, "values");
        int first = Integer.MAX_VALUE;
        int second = Integer.MAX_VALUE;
        for (int value : values) {
            if (value <= first) first = value;
            else if (value <= second) second = value;
            else return true;
        }
        return false;
    }

    private static void requireArray(int[] values, String name) {
        if (values == null) throw new IllegalArgumentException(name + " must be non-null");
    }

    private static long maximumDifferenceOracle(int[] values) {
        long best = -1;
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                if (values[j] > values[i]) best = Math.max(best, (long) values[j] - values[i]);
            }
        }
        return best;
    }

    private static boolean monotonicOracle(int[] values) {
        boolean nondecreasing = true;
        boolean nonincreasing = true;
        for (int i = 1; i < values.length; i++) {
            nondecreasing &= values[i - 1] <= values[i];
            nonincreasing &= values[i - 1] >= values[i];
        }
        return nondecreasing || nonincreasing;
    }

    private static boolean tripletOracle(int[] values) {
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                for (int k = j + 1; k < values.length; k++) {
                    if (values[i] < values[j] && values[j] < values[k]) return true;
                }
            }
        }
        return false;
    }

    private static boolean flowerOracle(int[] flowerbed, int requested) {
        int[][] memo = new int[flowerbed.length + 1][2];
        for (int[] row : memo) Arrays.fill(row, -1);
        return maximumAdditionalFlowers(flowerbed, 0, false, memo) >= requested;
    }

    private static int maximumAdditionalFlowers(int[] flowerbed, int index,
                                                 boolean previousOccupied, int[][] memo) {
        if (index == flowerbed.length) return 0;
        int state = previousOccupied ? 1 : 0;
        if (memo[index][state] >= 0) return memo[index][state];

        int answer;
        if (flowerbed[index] == 1) {
            answer = maximumAdditionalFlowers(flowerbed, index + 1, true, memo);
        } else {
            answer = maximumAdditionalFlowers(flowerbed, index + 1, false, memo);
            boolean nextOccupied = index + 1 < flowerbed.length && flowerbed[index + 1] == 1;
            if (!previousOccupied && !nextOccupied) {
                answer = Math.max(answer,
                        1 + maximumAdditionalFlowers(flowerbed, index + 1, true, memo));
            }
        }
        return memo[index][state] = answer;
    }

    private static int groups;
    private static long randomizedChecks;

    private static void group(boolean condition, String message) {
        groups++;
        if (!condition) throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        randomizedChecks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void deterministicGroups() {
        group(maximumIncreasingDifference(new int[]{7, 1, 5, 4}) == 4, "difference example 1");
        group(maximumIncreasingDifference(new int[]{9, 4, 3, 2}) == -1, "difference example 2");
        group(maximumIncreasingDifference(new int[]{1, 5, 2, 10}) == 9, "difference example 3");
        group(maximumIncreasingDifference(new int[]{}) == -1, "difference empty extension");
        group(maximumIncreasingDifference(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) == 4_294_967_295L,
                "difference full-width arithmetic");

        group(canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 1), "flowers example 1");
        group(!canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 2), "flowers example 2");
        group(canPlaceFlowers(new int[]{0}, 1), "flowers singleton");
        group(canPlaceFlowers(new int[]{}, 0) && !canPlaceFlowers(new int[]{}, 1), "flowers empty extension");
        boolean invalidBedRejected = false;
        try { canPlaceFlowers(new int[]{1, 1}, 0); }
        catch (IllegalArgumentException expected) { invalidBedRejected = true; }
        group(invalidBedRejected, "flowers invalid bed rejected");

        group(isMonotonic(new int[]{1, 2, 2, 3}), "monotonic example 1");
        group(isMonotonic(new int[]{6, 5, 4, 4}), "monotonic example 2");
        group(!isMonotonic(new int[]{1, 3, 2}), "monotonic example 3");
        group(isMonotonic(new int[]{7, 7, 7}), "monotonic equal values");
        group(isMonotonic(new int[]{}), "monotonic empty extension");

        group(hasIncreasingTriplet(new int[]{1, 2, 3, 4, 5}), "triplet example 1");
        group(!hasIncreasingTriplet(new int[]{5, 4, 3, 2, 1}), "triplet example 2");
        group(hasIncreasingTriplet(new int[]{2, 1, 5, 0, 4, 6}), "triplet example 3");
        group(hasIncreasingTriplet(new int[]{20, 100, 10, 12, 5, 13}), "triplet need not be contiguous");
        boolean nullsRejected = false;
        try {
            maximumIncreasingDifference(null);
        } catch (IllegalArgumentException expected) {
            try {
                isMonotonic(null);
            } catch (IllegalArgumentException alsoExpected) {
                try {
                    hasIncreasingTriplet(null);
                } catch (IllegalArgumentException allExpected) {
                    nullsRejected = true;
                }
            }
        }
        group(nullsRejected, "null arrays rejected");
    }

    private static void randomizedTrials() {
        Random random = new Random(0x51A7E7A6L);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = random.nextInt(13);
            int[] values = new int[length];
            for (int i = 0; i < length; i++) {
                values[i] = switch (random.nextInt(5)) {
                    case 0 -> random.nextInt(13) - 6;
                    case 1 -> random.nextInt();
                    case 2 -> Integer.MIN_VALUE;
                    case 3 -> Integer.MAX_VALUE;
                    default -> i == 0 ? random.nextInt() : values[random.nextInt(i)];
                };
            }
            int[] snapshot = values.clone();

            int[] flowerbed = new int[length];
            for (int i = 0; i < length; i++) {
                if ((i == 0 || flowerbed[i - 1] == 0) && random.nextInt(4) == 0) {
                    flowerbed[i] = 1;
                }
            }
            int requested = random.nextInt(length + 2);
            int[] flowerSnapshot = flowerbed.clone();

            check(maximumIncreasingDifference(values) == maximumDifferenceOracle(values),
                    "difference oracle mismatch at trial " + trial);
            check(isMonotonic(values) == monotonicOracle(values),
                    "monotonic oracle mismatch at trial " + trial);
            check(hasIncreasingTriplet(values) == tripletOracle(values),
                    "triplet oracle mismatch at trial " + trial);
            check(canPlaceFlowers(flowerbed, requested) == flowerOracle(flowerbed, requested),
                    "flower oracle mismatch at trial " + trial);
            check(Arrays.equals(values, snapshot) && Arrays.equals(flowerbed, flowerSnapshot),
                    "input mutated at trial " + trial);
        }
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedTrials();
        if (groups != 20 || randomizedChecks != 100_000) {
            throw new AssertionError("unexpected check totals");
        }
        System.out.printf("LinearScanStateTrackingContracts: %d scenario groups + "
                + "20000 seeded oracle trials passed; checks=%d%n", groups, randomizedChecks);
    }
}
