import java.util.Arrays;
import java.util.Random;

/** Executable contracts for the Section 16 binary-search-on-answer family. */
public final class BinarySearchOnAnswerPatternContracts {
    private BinarySearchOnAnswerPatternContracts() {}

    /** Least capacity that ships the positive weights, in order, within the day budget. */
    public static long minimumShipCapacity(int[] weights, int days) {
        requirePositive(weights, "weights");
        if (days < 1 || days > weights.length) {
            throw new IllegalArgumentException("days must be in [1,weights.length]");
        }
        long low = 0;
        long high = 0;
        for (int weight : weights) {
            low = Math.max(low, weight);
            high += weight;
        }
        while (low < high) {
            long middle = low + (high - low) / 2;
            if (shippingDays(weights, middle, days) <= days) high = middle;
            else low = middle + 1;
        }
        return low;
    }

    /** First day on which m disjoint bouquets of k adjacent flowers can be made. */
    public static int minimumBouquetDay(int[] bloomDay, int bouquets, int flowersPerBouquet) {
        requirePositive(bloomDay, "bloomDay");
        if (bouquets < 1 || flowersPerBouquet < 1) {
            throw new IllegalArgumentException("bouquets and flowersPerBouquet must be positive");
        }
        if ((long) bouquets * flowersPerBouquet > bloomDay.length) return -1;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int day : bloomDay) {
            low = Math.min(low, day);
            high = Math.max(high, day);
        }
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (canMakeBouquets(bloomDay, bouquets, flowersPerBouquet, middle)) high = middle;
            else low = middle + 1;
        }
        return low;
    }

    /** Greatest achievable minimum gap when placing balls in distinct basket positions. */
    public static long maximumMagneticDistance(int[] position, int balls) {
        requireDistinctPositions(position, balls);
        int[] sorted = position.clone();
        Arrays.sort(sorted);
        long low = 0;
        long high = (long) sorted[sorted.length - 1] - sorted[0];
        while (low < high) {
            long middle = low + (high - low + 1) / 2; // upper midpoint for greatest feasible
            if (canPlaceBalls(sorted, balls, middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    /** Greatest equal positive allocation per child; returns zero when one each is impossible. */
    public static int maximumCandiesPerChild(int[] candies, long children) {
        requirePositive(candies, "candies");
        if (children < 1) throw new IllegalArgumentException("children must be positive");
        int high = 0;
        long total = 0;
        for (int pile : candies) {
            high = Math.max(high, pile);
            total += pile;
        }
        if (total < children) return 0;
        int low = 1;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (canServeChildren(candies, children, middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private static int shippingDays(int[] weights, long capacity, int stopAfter) {
        int used = 1;
        long load = 0;
        for (int weight : weights) {
            if (load + weight > capacity) {
                used++;
                load = 0;
                if (used > stopAfter) return used;
            }
            load += weight;
        }
        return used;
    }

    private static boolean canMakeBouquets(
            int[] bloomDay, int bouquets, int flowersPerBouquet, int day) {
        int made = 0;
        int consecutive = 0;
        for (int bloom : bloomDay) {
            if (bloom <= day) {
                consecutive++;
                if (consecutive == flowersPerBouquet) {
                    made++;
                    consecutive = 0;
                    if (made == bouquets) return true;
                }
            } else {
                consecutive = 0;
            }
        }
        return false;
    }

    private static boolean canPlaceBalls(int[] sorted, int balls, long distance) {
        int placed = 1;
        long last = sorted[0];
        for (int i = 1; i < sorted.length && placed < balls; i++) {
            if ((long) sorted[i] - last >= distance) {
                placed++;
                last = sorted[i];
            }
        }
        return placed >= balls;
    }

    private static boolean canServeChildren(int[] candies, long children, int perChild) {
        long served = 0;
        for (int pile : candies) {
            served += pile / perChild;
            if (served >= children) return true;
        }
        return false;
    }

    private static void requirePositive(int[] values, String label) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(label + " must be non-null and non-empty");
        }
        for (int value : values) {
            if (value <= 0) throw new IllegalArgumentException(label + " values must be positive");
        }
    }

    private static void requireDistinctPositions(int[] position, int balls) {
        if (position == null || position.length < 2) {
            throw new IllegalArgumentException("position must contain at least two values");
        }
        if (balls < 2 || balls > position.length) {
            throw new IllegalArgumentException("balls must be in [2,position.length]");
        }
        int[] sorted = position.clone();
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i - 1] == sorted[i]) {
                throw new IllegalArgumentException("basket positions must be distinct");
            }
        }
    }

    private static long shipOracle(int[] weights, int days) {
        int n = weights.length;
        int parts = Math.min(days, n);
        long[][] dp = new long[parts + 1][n + 1];
        for (long[] row : dp) Arrays.fill(row, Long.MAX_VALUE / 4);
        dp[0][0] = 0;
        for (int used = 1; used <= parts; used++) {
            for (int end = used; end <= n; end++) {
                long suffix = 0;
                for (int start = end - 1; start >= used - 1; start--) {
                    suffix += weights[start];
                    dp[used][end] = Math.min(dp[used][end],
                            Math.max(dp[used - 1][start], suffix));
                }
            }
        }
        return dp[parts][n];
    }

    private static int bouquetOracle(int[] bloomDay, int bouquets, int flowersPerBouquet) {
        if ((long) bouquets * flowersPerBouquet > bloomDay.length) return -1;
        int maximum = 0;
        for (int value : bloomDay) maximum = Math.max(maximum, value);
        for (int day = 1; day <= maximum; day++) {
            int[] best = new int[bloomDay.length + 1];
            for (int end = 1; end <= bloomDay.length; end++) {
                best[end] = best[end - 1];
                if (end >= flowersPerBouquet) {
                    boolean ready = true;
                    for (int i = end - flowersPerBouquet; i < end; i++) {
                        if (bloomDay[i] > day) ready = false;
                    }
                    if (ready) best[end] = Math.max(best[end],
                            best[end - flowersPerBouquet] + 1);
                }
            }
            if (best[bloomDay.length] >= bouquets) return day;
        }
        throw new AssertionError("validated bouquet case must eventually be feasible");
    }

    private static long magneticOracle(int[] position, int balls) {
        int[] sorted = position.clone();
        Arrays.sort(sorted);
        return choosePositions(sorted, balls, 0, 0, 0, Long.MAX_VALUE, -1);
    }

    private static long choosePositions(int[] sorted, int balls, int index, int chosen,
            long best, long currentMinimum, int previousIndex) {
        if (chosen == balls) return Math.max(best, currentMinimum);
        if (sorted.length - index < balls - chosen) return best;
        for (int i = index; i < sorted.length; i++) {
            long nextMinimum = chosen == 0 ? Long.MAX_VALUE
                    : Math.min(currentMinimum, (long) sorted[i] - sorted[previousIndex]);
            best = choosePositions(sorted, balls, i + 1, chosen + 1,
                    best, nextMinimum, i);
        }
        return best;
    }

    private static int candiesOracle(int[] candies, long children) {
        int maximum = 0;
        for (int pile : candies) maximum = Math.max(maximum, pile);
        int answer = 0;
        for (int perChild = 1; perChild <= maximum; perChild++) {
            long served = 0;
            for (int pile : candies) served += pile / perChild;
            if (served >= children) answer = perChild;
        }
        return answer;
    }

    private static int[] positiveCase(Random random, int maximumLength, int maximumValue) {
        int[] values = new int[1 + random.nextInt(maximumLength)];
        for (int i = 0; i < values.length; i++) values[i] = 1 + random.nextInt(maximumValue);
        return values;
    }

    private static int[] distinctPositions(Random random) {
        int length = 2 + random.nextInt(8);
        int[] pool = new int[32];
        for (int i = 0; i < pool.length; i++) pool[i] = i - 10;
        for (int i = pool.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temporary = pool[i]; pool[i] = pool[j]; pool[j] = temporary;
        }
        return Arrays.copyOf(pool, length);
    }

    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void expectRejected(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label + " should reject invalid input");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    public static void main(String[] args) {
        int groups = 0;
        expect(minimumShipCapacity(new int[] {1,2,3,4,5,6,7,8,9,10}, 5) == 15, "ship official 1"); groups++;
        expect(minimumShipCapacity(new int[] {3,2,2,4,1,4}, 3) == 6, "ship official 2"); groups++;
        expect(minimumShipCapacity(new int[] {1,2,3,1,1}, 4) == 3, "ship official 3"); groups++;
        expect(minimumShipCapacity(new int[] {9}, 1) == 9, "ship singleton"); groups++;
        expect(minimumBouquetDay(new int[] {1,10,3,10,2}, 3, 1) == 3, "bouquet official 1"); groups++;
        expect(minimumBouquetDay(new int[] {1,10,3,10,2}, 3, 2) == -1, "bouquet impossible"); groups++;
        expect(minimumBouquetDay(new int[] {7,7,7,7,12,7,7}, 2, 3) == 12, "bouquet adjacency"); groups++;
        expect(minimumBouquetDay(new int[] {5}, 1, 1) == 5, "bouquet singleton"); groups++;
        expect(maximumMagneticDistance(new int[] {1,2,3,4,7}, 3) == 3, "magnetic official 1"); groups++;
        expect(maximumMagneticDistance(new int[] {5,4,3,2,1,1_000_000_000}, 2) == 999_999_999L, "magnetic official 2"); groups++;
        expect(maximumMagneticDistance(new int[] {10,-2,4,21}, 3) == 11, "magnetic signed unsorted"); groups++;
        expect(maximumMagneticDistance(new int[] {0,4,9}, 3) == 4, "magnetic all baskets"); groups++;
        expect(maximumCandiesPerChild(new int[] {5,8,6}, 3) == 5, "candies official 1"); groups++;
        expect(maximumCandiesPerChild(new int[] {2,5}, 11) == 0, "candies impossible"); groups++;
        expect(maximumCandiesPerChild(new int[] {4,4}, 4) == 2, "candies exact split"); groups++;
        expect(maximumCandiesPerChild(new int[] {9}, 1) == 9, "candies singleton"); groups++;
        expectRejected(() -> minimumShipCapacity(new int[] {1,0}, 1), "nonpositive weight"); groups++;
        expectRejected(() -> minimumShipCapacity(new int[] {1,2}, 3), "too many shipping days"); groups++;
        expectRejected(() -> minimumBouquetDay(new int[] {1,2}, 0, 1), "zero bouquets"); groups++;
        expectRejected(() -> maximumMagneticDistance(new int[] {1,1,2}, 2), "duplicate positions"); groups++;

        Random random = new Random(0xA45E_5EEDL);
        int trials = 20_000;
        int checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int[] weights = positiveCase(random, 8, 12);
            int days = 1 + random.nextInt(weights.length);
            int[] blooms = positiveCase(random, 9, 15);
            int bouquets = 1 + random.nextInt(5);
            int flowers = 1 + random.nextInt(blooms.length);
            int[] positions = distinctPositions(random);
            int balls = 2 + random.nextInt(positions.length - 1);
            int[] candies = positiveCase(random, 8, 15);
            long children = 1 + random.nextInt(80);
            int[] weightsBefore = weights.clone();
            int[] bloomsBefore = blooms.clone();
            int[] positionsBefore = positions.clone();
            int[] candiesBefore = candies.clone();

            expect(minimumShipCapacity(weights, days) == shipOracle(weights, days), "ship oracle"); checks++;
            expect(minimumBouquetDay(blooms, bouquets, flowers)
                    == bouquetOracle(blooms, bouquets, flowers), "bouquet oracle"); checks++;
            expect(maximumMagneticDistance(positions, balls)
                    == magneticOracle(positions, balls), "magnetic oracle"); checks++;
            expect(maximumCandiesPerChild(candies, children)
                    == candiesOracle(candies, children), "candies oracle"); checks++;
            expect(Arrays.equals(weightsBefore, weights) && Arrays.equals(bloomsBefore, blooms)
                    && Arrays.equals(positionsBefore, positions)
                    && Arrays.equals(candiesBefore, candies), "input preservation"); checks++;
        }

        System.out.println("BinarySearchOnAnswerPatternContracts: " + groups
                + " deterministic groups + " + trials + " seeded trials; "
                + checks + " oracle/preservation checks passed.");
    }
}
