import java.util.Arrays;
import java.util.Random;

public final class DifferenceArrayContracts {
    private static final long SEED = 0xD1FF_3A22_4A11L;

    private DifferenceArrayContracts() {}

    /**
     * Applies inclusive, zero-based updates [left, right, delta] to an
     * initially zero-filled array. Validation is intentionally separate from
     * the O(1)-per-update difference-array core.
     */
    static long[] applyRangeAdds(int length, int[][] updates) {
        validate(length, updates);
        return applyRangeAddsCore(length, updates);
    }

    static long[] applyRangeAddsCore(int length, int[][] updates) {
        long[] result = new long[length];
        for (int[] update : updates) {
            int left = update[0];
            int right = update[1];
            long delta = update[2];
            result[left] += delta;
            if (right + 1 < length) result[right + 1] -= delta;
        }
        for (int i = 1; i < length; i++) result[i] += result[i - 1];
        return result;
    }

    private static void validate(int length, int[][] updates) {
        if (length < 0) throw new IllegalArgumentException("length must be non-negative");
        if (updates == null) throw new IllegalArgumentException("updates must be non-null");
        for (int i = 0; i < updates.length; i++) {
            int[] update = updates[i];
            if (update == null || update.length != 3) {
                throw new IllegalArgumentException("update " + i + " must contain [left,right,delta]");
            }
            if (update[0] < 0 || update[0] > update[1] || update[1] >= length) {
                throw new IllegalArgumentException("update " + i + " has an invalid inclusive range");
            }
        }
    }

    private static long[] bruteForce(int length, int[][] updates) {
        long[] answer = new long[length];
        for (int[] update : updates) {
            for (int i = update[0]; i <= update[1]; i++) answer[i] += update[2];
        }
        return answer;
    }

    private static int[][] deepCopy(int[][] updates) {
        int[][] copy = new int[updates.length][];
        for (int i = 0; i < updates.length; i++) {
            copy[i] = updates[i] == null ? null : updates[i].clone();
        }
        return copy;
    }

    private static int[][] reversed(int[][] updates) {
        int[][] copy = deepCopy(updates);
        for (int left = 0, right = copy.length - 1; left < right; left++, right--) {
            int[] temporary = copy[left];
            copy[left] = copy[right];
            copy[right] = temporary;
        }
        return copy;
    }

    private static int[][] splitFirstSplittable(int[][] updates) {
        for (int i = 0; i < updates.length; i++) {
            int left = updates[i][0];
            int right = updates[i][1];
            if (left < right) {
                int middle = left + (right - left) / 2;
                int[][] split = new int[updates.length + 1][3];
                for (int j = 0; j < i; j++) split[j] = updates[j].clone();
                split[i] = new int[] {left, middle, updates[i][2]};
                split[i + 1] = new int[] {middle + 1, right, updates[i][2]};
                for (int j = i + 1; j < updates.length; j++) split[j + 1] = updates[j].clone();
                return split;
            }
        }
        return deepCopy(updates);
    }

    private static int[][] withInverses(int[][] updates) {
        int[][] combined = new int[updates.length * 2][3];
        for (int i = 0; i < updates.length; i++) {
            combined[i] = updates[i].clone();
            combined[updates.length + i] = new int[] {
                    updates[i][0], updates[i][1], -updates[i][2]
            };
        }
        return combined;
    }

    private static void expect(long[] expected, long[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " expected=" + Arrays.toString(expected)
                    + " actual=" + Arrays.toString(actual));
        }
    }

    private static void expectInvalid(int length, int[][] updates, String label) {
        try {
            applyRangeAdds(length, updates);
            throw new AssertionError(label + " should have failed");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static int deterministicChecks() {
        int groups = 0;
        int[][] official = {{0, 1, 10}, {1, 2, 20}, {1, 4, 25}};
        expect(new long[] {10, 55, 45, 25, 25}, applyRangeAdds(5, official), "flight bookings"); groups++;
        expect(new long[] {5, 8, 4, 3, 3}, applyRangeAdds(5,
                new int[][] {{0, 2, 5}, {1, 4, 3}, {2, 2, -4}}), "overlap"); groups++;
        expect(new long[] {5, -2, -2, 3}, applyRangeAdds(4,
                new int[][] {{0, 3, -2}, {0, 0, 7}, {3, 3, 5}}), "edges"); groups++;
        expect(new long[] {4294967294L, 4294967294L, 4294967294L}, applyRangeAdds(3,
                new int[][] {{0, 2, Integer.MAX_VALUE}, {0, 2, Integer.MAX_VALUE}}), "widening"); groups++;
        expect(new long[0], applyRangeAdds(0, new int[0][]), "empty"); groups++;
        expect(new long[] {0, 0, 0}, applyRangeAdds(3, new int[0][]), "no updates"); groups++;
        expect(new long[] {-7}, applyRangeAdds(1, new int[][] {{0, 0, -7}}), "singleton"); groups++;
        expect(new long[] {3, 0, 0}, applyRangeAdds(3, new int[][] {{0, 0, 3}}), "left point"); groups++;
        expect(new long[] {0, 0, 3}, applyRangeAdds(3, new int[][] {{2, 2, 3}}), "right point"); groups++;
        expect(new long[] {3, 3, 3}, applyRangeAdds(3, new int[][] {{0, 2, 3}}), "full range"); groups++;
        int[][] preserved = {{0, 1, 2}, {1, 1, -1}};
        int[][] before = deepCopy(preserved);
        applyRangeAdds(2, preserved);
        if (!Arrays.deepEquals(before, preserved)) throw new AssertionError("updates mutated"); groups++;
        expect(applyRangeAdds(5, official), applyRangeAdds(5, reversed(official)), "order independence"); groups++;
        expect(applyRangeAdds(5, official), applyRangeAdds(5, splitFirstSplittable(official)), "split relation"); groups++;
        expect(new long[5], applyRangeAdds(5, withInverses(official)), "inverse relation"); groups++;
        expectInvalid(-1, new int[0][], "negative length"); groups++;
        expectInvalid(1, null, "null updates"); groups++;
        expectInvalid(1, new int[][] {null}, "null update"); groups++;
        expectInvalid(1, new int[][] {{0, 0}}, "wrong arity"); groups++;
        expectInvalid(2, new int[][] {{-1, 0, 1}}, "negative left"); groups++;
        expectInvalid(2, new int[][] {{1, 0, 1}}, "reversed range"); groups++;
        return groups;
    }

    public static void main(String[] args) {
        int groups = deterministicChecks();
        if (groups != 20) throw new AssertionError("expected 20 deterministic groups");

        Random random = new Random(SEED);
        int trials = 20_000;
        int checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int length = random.nextInt(41);
            int updateCount = length == 0 ? 0 : random.nextInt(51);
            int[][] updates = new int[updateCount][3];
            for (int i = 0; i < updateCount; i++) {
                int left = random.nextInt(length);
                int right = left + random.nextInt(length - left);
                int delta = random.nextInt(2_000_001) - 1_000_000;
                updates[i] = new int[] {left, right, delta};
            }

            int[][] before = deepCopy(updates);
            long[] answer = applyRangeAdds(length, updates);
            expect(bruteForce(length, updates), answer, "oracle trial " + trial); checks++;
            if (!Arrays.deepEquals(before, updates)) throw new AssertionError("mutation trial " + trial);
            checks++;
            expect(answer, applyRangeAdds(length, reversed(updates)), "permutation trial " + trial); checks++;
            expect(answer, applyRangeAdds(length, splitFirstSplittable(updates)), "split trial " + trial); checks++;
            expect(new long[length], applyRangeAdds(length, withInverses(updates)), "inverse trial " + trial); checks++;
        }

        System.out.println("DifferenceArrayContracts: " + groups + " scenario groups + "
                + trials + " seeded brute-force trials passed; checks=" + checks);
    }
}
