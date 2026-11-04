import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Koko Eating Bananas. */
public final class KokoEatingContracts {
    private record SearchResult(int speed, int feasibilityChecks) {}

    private static int groups;
    private static long checks;

    static int minEatingSpeed(int[] piles, int hoursAvailable) {
        return searchWithEvidence(piles, hoursAvailable).speed();
    }

    private static SearchResult searchWithEvidence(int[] piles, int hoursAvailable) {
        validate(piles, hoursAvailable);
        int low = 1;
        int high = 0;
        for (int pile : piles) {
            high = Math.max(high, pile);
        }

        int feasibilityChecks = 0;
        while (low < high) {
            int mid = low + ((high - low) >>> 1);
            feasibilityChecks++;
            if (finishesWithin(piles, hoursAvailable, mid)) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return new SearchResult(low, feasibilityChecks);
    }

    static boolean finishesWithin(int[] piles, int hoursAvailable, int speed) {
        if (speed <= 0) {
            throw new IllegalArgumentException("speed must be positive");
        }
        long required = 0;
        for (int pile : piles) {
            required += 1L + (pile - 1L) / speed;
            if (required > hoursAvailable) {
                return false;
            }
        }
        return true;
    }

    private static void validate(int[] piles, int hoursAvailable) {
        if (piles == null || piles.length == 0) {
            throw new IllegalArgumentException("piles must be non-null and non-empty");
        }
        for (int pile : piles) {
            if (pile <= 0) {
                throw new IllegalArgumentException("every pile must be positive");
            }
        }
        if (hoursAvailable < piles.length) {
            throw new IllegalArgumentException("hours must be at least the pile count");
        }
    }

    private static int bruteForceOracle(int[] piles, int hoursAvailable) {
        int maximum = Arrays.stream(piles).max().orElseThrow();
        for (int speed = 1; speed <= maximum; speed++) {
            long required = 0;
            for (int pile : piles) {
                required += (pile + (long) speed - 1L) / speed;
            }
            if (required <= hoursAvailable) {
                return speed;
            }
        }
        throw new AssertionError("validated input must have a feasible speed");
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
        group("canonical", minEatingSpeed(new int[] {3, 6, 7, 11}, 8) == 4);
        group("one hour per pile", minEatingSpeed(new int[] {30, 11, 23, 4, 20}, 5) == 30);
        group("one spare hour", minEatingSpeed(new int[] {30, 11, 23, 4, 20}, 6) == 23);
        group("single pile exact", minEatingSpeed(new int[] {9}, 3) == 3);
        group("single pile slack", minEatingSpeed(new int[] {9}, 20) == 1);
        group("all ones", minEatingSpeed(new int[] {1, 1, 1, 1}, 4) == 1);
        group("equal piles", minEatingSpeed(new int[] {8, 8, 8}, 6) == 4);
        group("maximum integer piles", minEatingSpeed(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 3) == Integer.MAX_VALUE);
        group("maximum speed feasible", finishesWithin(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 2, Integer.MAX_VALUE));
        group("one below maximum infeasible", !finishesWithin(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 3, Integer.MAX_VALUE - 1));
        group("canonical speed three infeasible", !finishesWithin(new int[] {3, 6, 7, 11}, 8, 3));
        group("canonical speed four feasible", finishesWithin(new int[] {3, 6, 7, 11}, 8, 4));
        group("monotonic larger speed", finishesWithin(new int[] {3, 6, 7, 11}, 8, 11));

        int[] preserved = {3, 6, 7, 11};
        int[] snapshot = preserved.clone();
        minEatingSpeed(preserved, 8);
        group("input preserved", Arrays.equals(preserved, snapshot));

        expectIllegalArgument("null rejected", () -> minEatingSpeed(null, 8));
        expectIllegalArgument("empty rejected", () -> minEatingSpeed(new int[0], 8));
        expectIllegalArgument("zero pile rejected", () -> minEatingSpeed(new int[] {3, 0, 4}, 4));
        expectIllegalArgument("negative pile rejected", () -> minEatingSpeed(new int[] {3, -1, 4}, 4));
        expectIllegalArgument("infeasible hours rejected", () -> minEatingSpeed(new int[] {3, 6, 7}, 2));
        expectIllegalArgument("zero speed rejected", () -> finishesWithin(new int[] {3}, 1, 0));
    }

    private static void randomizedEvidence() {
        Random random = new Random(20261104L);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = 1 + random.nextInt(8);
            int[] piles = new int[length];
            for (int index = 0; index < length; index++) {
                piles[index] = 1 + random.nextInt(100);
            }
            int hoursAvailable = length + random.nextInt(31);
            int[] snapshot = piles.clone();
            SearchResult actual = searchWithEvidence(piles, hoursAvailable);
            int expected = bruteForceOracle(piles, hoursAvailable);
            int maximum = Arrays.stream(piles).max().orElseThrow();
            int logarithmicBound = maximum == 1 ? 0 : 32 - Integer.numberOfLeadingZeros(maximum - 1);

            checks += 5;
            if (actual.speed() != expected) {
                throw new AssertionError("oracle mismatch trial=" + trial);
            }
            if (!Arrays.equals(piles, snapshot)) {
                throw new AssertionError("input mutated trial=" + trial);
            }
            if (!finishesWithin(piles, hoursAvailable, actual.speed())) {
                throw new AssertionError("reported speed infeasible trial=" + trial);
            }
            if (actual.speed() > 1 && finishesWithin(piles, hoursAvailable, actual.speed() - 1)) {
                throw new AssertionError("reported speed not minimal trial=" + trial);
            }
            if (actual.feasibilityChecks() > logarithmicBound) {
                throw new AssertionError("binary-search bound trial=" + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|CANONICAL=4|H_EQ_N=30|H_PLUS_ONE=23|MAX_SAFE=true|MINIMAL=true");
        System.out.println("KokoEatingContracts: 20 scenario groups + 20000 seeded brute-force comparisons passed; checks=" + checks);
    }
}
