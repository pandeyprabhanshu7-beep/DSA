import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class PrefixXorContracts {
    private static final long SEED = 0x5052_4546_4958L;

    private PrefixXorContracts() {}

    static int[] xorQueries(int[] values, int[][] queries) {
        validateQueries(values, queries);
        int[] prefix = new int[values.length + 1];
        for (int i = 0; i < values.length; i++) {
            prefix[i + 1] = prefix[i] ^ values[i];
        }
        int[] answers = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            answers[i] = prefix[queries[i][1] + 1] ^ prefix[queries[i][0]];
        }
        return answers;
    }

    static long countSubarraysWithXor(int[] values, int target) {
        if (values == null) throw new IllegalArgumentException("values must be non-null");
        Map<Integer, Integer> priorFrequency = new HashMap<>();
        priorFrequency.put(0, 1);
        int prefix = 0;
        long count = 0;
        for (int value : values) {
            prefix ^= value;
            count += priorFrequency.getOrDefault(prefix ^ target, 0);
            priorFrequency.merge(prefix, 1, Integer::sum);
        }
        return count;
    }

    private static void validateQueries(int[] values, int[][] queries) {
        if (values == null) throw new IllegalArgumentException("values must be non-null");
        if (queries == null) throw new IllegalArgumentException("queries must be non-null");
        for (int[] query : queries) {
            if (query == null || query.length != 2) {
                throw new IllegalArgumentException("every query must contain [left,right]");
            }
            int left = query[0], right = query[1];
            if (left < 0 || left > right || right >= values.length) {
                throw new IllegalArgumentException("query must satisfy 0 <= left <= right < n");
            }
        }
    }

    private static int[] directQueries(int[] values, int[][] queries) {
        int[] answers = new int[queries.length];
        for (int q = 0; q < queries.length; q++) {
            int xor = 0;
            for (int i = queries[q][0]; i <= queries[q][1]; i++) xor ^= values[i];
            answers[q] = xor;
        }
        return answers;
    }

    private static long quadraticCount(int[] values, int target) {
        long count = 0;
        for (int left = 0; left < values.length; left++) {
            int xor = 0;
            for (int right = left; right < values.length; right++) {
                xor ^= values[right];
                if (xor == target) count++;
            }
        }
        return count;
    }

    private static void expect(int[] expected, int[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " expected=" + Arrays.toString(expected)
                    + " actual=" + Arrays.toString(actual));
        }
    }

    private static void expect(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectInvalid(int[] values, int[][] queries, String label) {
        try {
            xorQueries(values, queries);
            throw new AssertionError(label + " should have failed");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static int deterministicChecks() {
        int groups = 0;
        expect(new int[] {2,7,14,8}, xorQueries(new int[] {1,3,4,8},
                new int[][] {{0,1},{1,2},{0,3},{3,3}}), "official range one"); groups++;
        expect(new int[] {8,0,4,4}, xorQueries(new int[] {4,8,2,10},
                new int[][] {{2,3},{1,3},{0,0},{0,3}}), "official range two"); groups++;
        expect(new int[] {7}, xorQueries(new int[] {7}, new int[][] {{0,0}}), "singleton"); groups++;
        expect(new int[] {0,0}, xorQueries(new int[] {-1,0,-1},
                new int[][] {{0,2},{1,1}}), "signed full width"); groups++;
        expect(new int[0], xorQueries(new int[0], new int[0][]), "empty"); groups++;
        expect(new int[] {0,3,3}, xorQueries(new int[] {1,2,3},
                new int[][] {{0,2},{0,1},{2,2}}), "boundary queries"); groups++;
        expect(4, countSubarraysWithXor(new int[] {4,2,2,6,4}, 6), "classic count"); groups++;
        expect(2, countSubarraysWithXor(new int[] {5,5,5}, 0), "zero target"); groups++;
        expect(2, countSubarraysWithXor(new int[] {-1,0,-1}, 0), "signed count"); groups++;
        expect(10, countSubarraysWithXor(new int[] {0,0,0,0}, 0), "all zeros"); groups++;
        expect(1, countSubarraysWithXor(new int[] {42}, 42), "singleton hit"); groups++;
        expect(0, countSubarraysWithXor(new int[] {1,2,4}, 8), "no match"); groups++;
        expect(1, countSubarraysWithXor(
                new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}, -1), "full width"); groups++;
        int[] rangeInput = {9,8,7}; int[] rangeBefore = rangeInput.clone();
        xorQueries(rangeInput, new int[][] {{0,2}});
        if (!Arrays.equals(rangeBefore, rangeInput)) throw new AssertionError("range mutation"); groups++;
        int[] countInput = {9,8,7}; int[] countBefore = countInput.clone();
        countSubarraysWithXor(countInput, 6);
        if (!Arrays.equals(countBefore, countInput)) throw new AssertionError("count mutation"); groups++;
        expectInvalid(null, new int[0][], "null values"); groups++;
        expectInvalid(new int[0], null, "null queries"); groups++;
        expectInvalid(new int[] {1}, new int[][] {null}, "null query row"); groups++;
        expectInvalid(new int[] {1,2}, new int[][] {{-1,1}}, "negative left"); groups++;
        expectInvalid(new int[] {1,2}, new int[][] {{1,0},{0,2}}, "bad bounds"); groups++;
        return groups;
    }

    public static void main(String[] args) {
        int groups = deterministicChecks();
        if (groups != 20) throw new AssertionError("expected 20 deterministic groups");
        Random random = new Random(SEED);
        int trials = 20_000;
        int checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int n = random.nextInt(61);
            int[] values = new int[n];
            for (int i = 0; i < n; i++) values[i] = random.nextInt();
            int queryCount = n == 0 ? 0 : 1 + random.nextInt(20);
            int[][] queries = new int[queryCount][2];
            for (int q = 0; q < queryCount; q++) {
                int a = random.nextInt(n), b = random.nextInt(n);
                queries[q][0] = Math.min(a, b);
                queries[q][1] = Math.max(a, b);
            }
            int target = random.nextInt();
            if (n > 0 && random.nextBoolean()) {
                int left = random.nextInt(n), right = left + random.nextInt(n - left);
                target = directQueries(values, new int[][] {{left,right}})[0];
            }

            int[] before = values.clone();
            expect(directQueries(values, queries), xorQueries(values, queries),
                    "range oracle trial " + trial); checks++;
            expect(quadraticCount(values, target), countSubarraysWithXor(values, target),
                    "count oracle trial " + trial); checks++;
            if (!Arrays.equals(before, values)) throw new AssertionError("mutation trial " + trial);
            checks++;

            int[] reversed = before.clone();
            for (int left = 0, right = n - 1; left < right; left++, right--) {
                int t = reversed[left]; reversed[left] = reversed[right]; reversed[right] = t;
            }
            expect(countSubarraysWithXor(before, target), countSubarraysWithXor(reversed, target),
                    "reversal trial " + trial); checks++;

            if (n == 0) {
                expect(new int[0], xorQueries(values, new int[0][]), "empty split trial " + trial);
            } else {
                int left = random.nextInt(n), right = left + random.nextInt(n - left);
                int middle = left + random.nextInt(right - left + 1);
                int whole = directQueries(values, new int[][] {{left,right}})[0];
                int first = directQueries(values, new int[][] {{left,middle}})[0];
                int second = middle == right ? 0
                        : directQueries(values, new int[][] {{middle + 1,right}})[0];
                if (whole != (first ^ second)) throw new AssertionError("split trial " + trial);
            }
            checks++;
        }
        System.out.println("PrefixXorContracts: " + groups + " scenario groups + " + trials
                + " seeded direct-oracle trials passed; checks=" + checks);
    }
}
