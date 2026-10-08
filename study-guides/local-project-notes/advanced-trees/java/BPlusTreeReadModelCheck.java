import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

public final class BPlusTreeReadModelCheck {
    private static long assertions;

    public static void main(String[] args) {
        deterministicCases();
        invalidInputs();
        randomizedOracleChecks();
        System.out.println("BPlusTreeReadModel checks passed: " + assertions + " assertions");
    }

    private static void deterministicCases() {
        int[] keys = {5, 9, 12, 18, 24, 31, 44, 52};
        String[] values = valuesFor(keys);
        BPlusTreeReadModel tree = BPlusTreeReadModel.bulkLoad(keys, values, 3, 3);
        equal(8, tree.size(), "size");
        equal(3, tree.leafCount(), "leaf count");
        equal(2, tree.height(), "height");
        equal(Optional.of("v24"), tree.get(24), "found key");
        equal(Optional.empty(), tree.get(25), "missing key");
        equal(Optional.of("v5"), tree.get(5), "minimum key");
        equal(Optional.of("v52"), tree.get(52), "maximum key");
        equal(List.of(12, 18, 24, 31), keys(tree.range(10, 40, 99)), "cross-leaf range");
        equal(List.of(18, 24), keys(tree.range(18, 24, 99)), "inclusive bounds");
        equal(List.of(12, 18), keys(tree.range(10, 40, 2)), "limit");
        equal(List.of(), keys(tree.range(53, 90, 9)), "range after maximum");
        equal(List.of(), keys(tree.range(-9, 4, 9)), "range before minimum");
        equal(List.of(), keys(tree.range(5, 52, 0)), "zero limit");

        BPlusTreeReadModel extremes = BPlusTreeReadModel.bulkLoad(
                new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE},
                new String[] {"min", "zero", "max"}, 2, 2);
        equal(Optional.of("min"), extremes.get(Integer.MIN_VALUE), "minimum int");
        equal(Optional.of("max"), extremes.get(Integer.MAX_VALUE), "maximum int");
        equal(List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE),
                keys(extremes.range(Integer.MIN_VALUE, Integer.MAX_VALUE, 10)), "extreme range");

        BPlusTreeReadModel empty = BPlusTreeReadModel.bulkLoad(new int[0], new String[0], 2, 2);
        equal(0, empty.size(), "empty size");
        equal(Optional.empty(), empty.get(7), "empty lookup");
        equal(List.of(), keys(empty.range(-1, 1, 3)), "empty range");
    }

    private static void invalidInputs() {
        throwsType(IllegalArgumentException.class,
                () -> BPlusTreeReadModel.bulkLoad(new int[] {1}, new String[0], 2, 2));
        throwsType(IllegalArgumentException.class,
                () -> BPlusTreeReadModel.bulkLoad(new int[] {2, 1}, valuesFor(2, 1), 2, 2));
        throwsType(IllegalArgumentException.class,
                () -> BPlusTreeReadModel.bulkLoad(new int[] {1, 1}, valuesFor(1, 1), 2, 2));
        throwsType(IllegalArgumentException.class,
                () -> BPlusTreeReadModel.bulkLoad(new int[] {1}, valuesFor(1), 1, 2));
        throwsType(IllegalArgumentException.class,
                () -> BPlusTreeReadModel.bulkLoad(new int[] {1}, valuesFor(1), 2, 1));
        BPlusTreeReadModel tree = BPlusTreeReadModel.bulkLoad(new int[] {1}, valuesFor(1), 2, 2);
        throwsType(IllegalArgumentException.class, () -> tree.range(2, 1, 1));
        throwsType(IllegalArgumentException.class, () -> tree.range(1, 2, -1));
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0xB17E_2026L);
        long pointQueries = 0;
        long rangeQueries = 0;
        for (int trial = 0; trial < 1_000; trial++) {
            TreeMap<Integer, String> oracle = new TreeMap<>();
            int wanted = random.nextInt(250);
            while (oracle.size() < wanted) {
                int key = random.nextInt(4_001) - 2_000;
                oracle.put(key, "v" + key);
            }
            int[] keys = oracle.keySet().stream().mapToInt(Integer::intValue).toArray();
            String[] values = oracle.values().toArray(String[]::new);
            int leafCapacity = 2 + random.nextInt(15);
            int fanout = 2 + random.nextInt(11);
            BPlusTreeReadModel tree = BPlusTreeReadModel.bulkLoad(keys, values, leafCapacity, fanout);
            equal(oracle.size(), tree.size(), "random size");

            for (int q = 0; q < 100; q++) {
                int key = random.nextInt(4_401) - 2_200;
                equal(Optional.ofNullable(oracle.get(key)), tree.get(key), "random point lookup");
                pointQueries++;
            }

            for (int q = 0; q < 50; q++) {
                int a = random.nextInt(4_401) - 2_200;
                int b = random.nextInt(4_401) - 2_200;
                int from = Math.min(a, b);
                int to = Math.max(a, b);
                int limit = random.nextInt(35);
                List<Integer> expected = oracle.subMap(from, true, to, true).keySet().stream()
                        .limit(limit).toList();
                equal(expected, keys(tree.range(from, to, limit)), "random range lookup");
                rangeQueries++;
            }
        }
        equal(100_000L, pointQueries, "point query count");
        equal(50_000L, rangeQueries, "range query count");
    }

    private static String[] valuesFor(int... keys) {
        String[] values = new String[keys.length];
        for (int i = 0; i < keys.length; i++) values[i] = "v" + keys[i];
        return values;
    }

    private static List<Integer> keys(List<BPlusTreeReadModel.Entry> entries) {
        List<Integer> result = new ArrayList<>(entries.size());
        for (BPlusTreeReadModel.Entry entry : entries) result.add(entry.key());
        return result;
    }

    private static void equal(Object expected, Object actual, String label) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void throwsType(Class<? extends Throwable> type, Runnable action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("expected " + type.getSimpleName());
        } catch (Throwable error) {
            if (!type.isInstance(error)) throw error;
        }
    }
}
