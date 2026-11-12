import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

/** Dependency-free executable contracts for coordinate compression. */
public final class CoordinateCompressionContracts {
    record Compression(int[] ranks, int[] valuesByRank) {
        Compression {
            ranks = ranks.clone();
            valuesByRank = valuesByRank.clone();
        }

        @Override public int[] ranks() { return ranks.clone(); }
        @Override public int[] valuesByRank() { return valuesByRank.clone(); }
    }

    static Compression compress(int[] values) {
        if (values == null) throw new IllegalArgumentException("values must be non-null");

        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int uniqueCount = 0;
        for (int value : sorted) {
            if (uniqueCount == 0 || sorted[uniqueCount - 1] != value) {
                sorted[uniqueCount++] = value;
            }
        }

        int[] valuesByRank = Arrays.copyOf(sorted, uniqueCount);
        int[] ranks = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            int position = Arrays.binarySearch(valuesByRank, values[i]);
            if (position < 0) throw new AssertionError("original value missing from dictionary");
            ranks[i] = position + 1;
        }
        return new Compression(ranks, valuesByRank);
    }

    static int[] restore(Compression compression) {
        if (compression == null) throw new IllegalArgumentException("compression must be non-null");
        int[] ranks = compression.ranks();
        int[] dictionary = compression.valuesByRank();
        int[] restored = new int[ranks.length];
        for (int i = 0; i < ranks.length; i++) {
            int rank = ranks[i];
            if (rank < 1 || rank > dictionary.length) {
                throw new IllegalArgumentException("rank outside dictionary: " + rank);
            }
            restored[i] = dictionary[rank - 1];
        }
        return restored;
    }

    private static Compression oracle(int[] values) {
        TreeSet<Integer> ordered = new TreeSet<>();
        for (int value : values) ordered.add(value);
        int[] dictionary = new int[ordered.size()];
        Map<Integer, Integer> rank = new HashMap<>();
        int index = 0;
        for (int value : ordered) {
            dictionary[index] = value;
            rank.put(value, index + 1);
            index++;
        }
        int[] ranks = new int[values.length];
        for (int i = 0; i < values.length; i++) ranks[i] = rank.get(values[i]);
        return new Compression(ranks, dictionary);
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
        group(Arrays.equals(compress(new int[]{40, 10, 20, 30}).ranks(), new int[]{4, 1, 2, 3}), "official example 1");
        group(Arrays.equals(compress(new int[]{100, 100, 100}).ranks(), new int[]{1, 1, 1}), "official example 2");
        group(Arrays.equals(compress(new int[]{37, 12, 28, 9, 100, 56, 80, 5, 12}).ranks(), new int[]{5, 3, 4, 2, 8, 6, 7, 1, 3}), "official example 3");
        group(compress(new int[]{}).ranks().length == 0, "empty ranks");
        group(compress(new int[]{}).valuesByRank().length == 0, "empty dictionary");
        group(Arrays.equals(compress(new int[]{-7}).ranks(), new int[]{1}), "singleton");
        group(Arrays.equals(compress(new int[]{-10, 0, -10, 5}).ranks(), new int[]{1, 2, 1, 3}), "negative duplicate");
        group(Arrays.equals(compress(new int[]{Integer.MAX_VALUE, 0, Integer.MIN_VALUE}).ranks(), new int[]{3, 2, 1}), "full-width values");
        group(Arrays.equals(compress(new int[]{1, 2, 3, 4}).ranks(), new int[]{1, 2, 3, 4}), "ascending");
        group(Arrays.equals(compress(new int[]{4, 3, 2, 1}).ranks(), new int[]{4, 3, 2, 1}), "descending");
        group(Arrays.equals(compress(new int[]{10, 1000, 10, -50}).valuesByRank(), new int[]{-50, 10, 1000}), "sorted unique dictionary");
        group(Arrays.equals(restore(compress(new int[]{9, -4, 9, 2})), new int[]{9, -4, 9, 2}), "round trip");
        int[] original = {8, 1, 8, 3};
        int[] snapshot = original.clone();
        compress(original);
        group(Arrays.equals(original, snapshot), "input preservation");
        group(Arrays.equals(compress(new int[]{-1_000_000_000, 1_000_000_000}).ranks(), new int[]{1, 2}), "large gap removed");
        group(compress(new int[]{2, 2, 1, 1}).valuesByRank().length == 2, "duplicates deduplicated");
        group(Arrays.equals(compress(new int[]{5, 1, 5, 3}).ranks(), new int[]{3, 1, 3, 2}), "equal values share rank");
        group(Arrays.equals(compress(new int[]{5, 1, 5, 3}).valuesByRank(), new int[]{1, 3, 5}), "ranks are gap-free");
        boolean nullRejected = false;
        try { compress(null); } catch (IllegalArgumentException expected) { nullRejected = true; }
        group(nullRejected, "null rejected");
        boolean badRankRejected = false;
        try { restore(new Compression(new int[]{2}, new int[]{7})); } catch (IllegalArgumentException expected) { badRankRejected = true; }
        group(badRankRejected, "invalid rank rejected");
        Compression defensive = compress(new int[]{4, 2});
        int[] leaked = defensive.ranks();
        leaked[0] = 99;
        group(defensive.ranks()[0] == 2, "defensive result access");
    }

    private static void randomizedTrials() {
        Random random = new Random(0xC00D1A7EL);
        for (int trial = 0; trial < 20_000; trial++) {
            int length = random.nextInt(33);
            int[] values = new int[length];
            for (int i = 0; i < length; i++) {
                int mode = random.nextInt(5);
                values[i] = switch (mode) {
                    case 0 -> random.nextInt(11) - 5;
                    case 1 -> random.nextInt();
                    case 2 -> Integer.MIN_VALUE;
                    case 3 -> Integer.MAX_VALUE;
                    default -> i == 0 ? random.nextInt() : values[random.nextInt(i)];
                };
            }

            int[] snapshot = values.clone();
            Compression actual = compress(values);
            Compression expected = oracle(values);
            check(Arrays.equals(actual.ranks(), expected.ranks()), "rank oracle mismatch at trial " + trial);
            check(Arrays.equals(actual.valuesByRank(), expected.valuesByRank()), "dictionary oracle mismatch at trial " + trial);
            check(Arrays.equals(values, snapshot), "input mutated at trial " + trial);
            check(Arrays.equals(restore(actual), values), "round trip mismatch at trial " + trial);

            int[] reversed = values.clone();
            for (int l = 0, r = reversed.length - 1; l < r; l++, r--) {
                int temporary = reversed[l]; reversed[l] = reversed[r]; reversed[r] = temporary;
            }
            int[] reversedRanks = actual.ranks();
            for (int l = 0, r = reversedRanks.length - 1; l < r; l++, r--) {
                int temporary = reversedRanks[l]; reversedRanks[l] = reversedRanks[r]; reversedRanks[r] = temporary;
            }
            check(Arrays.equals(compress(reversed).ranks(), reversedRanks), "reversal mismatch at trial " + trial);
        }
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedTrials();
        if (groups != 20 || randomizedChecks != 100_000) throw new AssertionError("unexpected check totals");
        System.out.printf("CoordinateCompressionContracts: %d scenario groups + 20000 seeded oracle trials passed; checks=%d%n", groups, randomizedChecks);
    }
}
