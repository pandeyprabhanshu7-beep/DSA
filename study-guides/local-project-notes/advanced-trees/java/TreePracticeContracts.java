import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.TreeMap;

/** Executable practice gates for Advanced Trees Section 15. */
public final class TreePracticeContracts {
    private static final int TRIALS = 20_000;
    private static final long SEED = 0x15_2026L;

    enum Structure {
        RED_BLACK_TREE, BINARY_HEAP, FENWICK_TREE, SEGMENT_TREE, B_PLUS_TREE
    }

    record Requirement(
            boolean orderedMap,
            boolean predecessorSuccessor,
            boolean topK,
            boolean arbitrarySearch,
            boolean pointUpdates,
            boolean prefixInvertible,
            boolean rangeUpdates,
            boolean richIntervalState,
            boolean diskPages,
            boolean rangeScan) {}

    static Structure choose(Requirement r) {
        int matches = 0;
        Structure answer = null;
        if (r.orderedMap() && r.predecessorSuccessor()) {
            matches++;
            answer = Structure.RED_BLACK_TREE;
        }
        if (r.topK() && !r.arbitrarySearch()) {
            matches++;
            answer = Structure.BINARY_HEAP;
        }
        if (r.pointUpdates() && r.prefixInvertible() && !r.rangeUpdates()) {
            matches++;
            answer = Structure.FENWICK_TREE;
        }
        if (r.rangeUpdates() || r.richIntervalState()) {
            matches++;
            answer = Structure.SEGMENT_TREE;
        }
        if (r.diskPages() && r.rangeScan()) {
            matches++;
            answer = Structure.B_PLUS_TREE;
        }
        if (matches != 1) {
            throw new IllegalArgumentException("requirement packet must select exactly one structure");
        }
        return answer;
    }

    static int[] topK(int[] values, int k) {
        if (k < 0) throw new IllegalArgumentException("k must be nonnegative");
        PriorityQueue<Integer> heap = new PriorityQueue<>();
        for (int value : values) {
            if (heap.size() < k) {
                heap.offer(value);
            } else if (k > 0 && value > heap.peek()) {
                heap.poll();
                heap.offer(value);
            }
        }
        int[] result = new int[heap.size()];
        for (int i = 0; i < result.length; i++) result[i] = heap.poll();
        return result;
    }

    static final class FenwickAssignments {
        private final long[] bit;
        private final int[] values;

        FenwickAssignments(int[] initial) {
            values = initial.clone();
            bit = new long[initial.length + 1];
            for (int i = 0; i < initial.length; i++) add(i, initial[i]);
        }

        void set(int index, int value) {
            checkIndex(index);
            long delta = (long) value - values[index];
            values[index] = value;
            add(index, delta);
        }

        long rangeSum(int left, int right) {
            if (left < 0 || right < left || right >= values.length) {
                throw new IndexOutOfBoundsException("invalid inclusive range");
            }
            return prefix(right) - prefix(left - 1);
        }

        private void add(int zeroBased, long delta) {
            for (int i = zeroBased + 1; i < bit.length; i += i & -i) bit[i] += delta;
        }

        private long prefix(int zeroBased) {
            long sum = 0;
            for (int i = zeroBased + 1; i > 0; i -= i & -i) sum += bit[i];
            return sum;
        }

        private void checkIndex(int index) {
            if (index < 0 || index >= values.length) throw new IndexOutOfBoundsException(index);
        }
    }

    private static Requirement orderedMap() {
        return new Requirement(true, true, false, false, false, false, false, false, false, false);
    }

    private static Requirement topK() {
        return new Requirement(false, false, true, false, false, false, false, false, false, false);
    }

    private static Requirement fenwick() {
        return new Requirement(false, false, false, false, true, true, false, false, false, false);
    }

    private static Requirement segmentTree() {
        return new Requirement(false, false, false, false, false, false, true, true, false, false);
    }

    private static Requirement bPlusTree() {
        return new Requirement(false, false, false, false, false, false, false, false, true, true);
    }

    private static int deterministicGroups() {
        int groups = 0;
        require(choose(orderedMap()) == Structure.RED_BLACK_TREE, "ordered map"); groups++;
        require(choose(topK()) == Structure.BINARY_HEAP, "top K"); groups++;
        require(choose(fenwick()) == Structure.FENWICK_TREE, "Fenwick"); groups++;
        require(choose(segmentTree()) == Structure.SEGMENT_TREE, "segment tree"); groups++;
        require(choose(bPlusTree()) == Structure.B_PLUS_TREE, "B+ tree"); groups++;

        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(40, "A"); map.put(20, "B"); map.put(60, "C"); map.put(10, "D"); map.put(30, "E");
        require(map.size() == 5, "five distinct keys"); groups++;
        map.put(20, "F"); require(map.size() == 5 && map.get(20).equals("F"), "replace duplicate key"); groups++;
        require(map.higherKey(30) == 40, "higher key"); groups++;
        require(map.floorKey(35) == 30, "floor key"); groups++;

        require(Arrays.equals(topK(new int[]{8, 1, 7, 3, 9, 2, 9}, 3), new int[]{8, 9, 9}), "top-three trace"); groups++;
        require(topK(new int[]{1, 2}, 0).length == 0, "zero winners"); groups++;
        require(Arrays.equals(topK(new int[]{-2, -2, -5}, 2), new int[]{-2, -2}), "negative duplicates"); groups++;
        require(Arrays.equals(topK(new int[]{3, 1}, 5), new int[]{1, 3}), "k above n"); groups++;

        FenwickAssignments fw = new FenwickAssignments(new int[]{2, 1, 5, 3, 4});
        require(fw.rangeSum(1, 3) == 9, "initial range"); groups++;
        fw.set(2, 8); require(fw.rangeSum(1, 3) == 12, "assignment delta"); groups++;
        fw.set(2, 8); require(fw.rangeSum(0, 4) == 18, "idempotent assignment"); groups++;
        fw.set(0, -2); require(fw.rangeSum(0, 0) == -2, "negative point"); groups++;

        expectIllegal(() -> choose(new Requirement(false, false, false, false, false, false, false, false, false, false)));
        groups++;
        expectIllegal(() -> choose(new Requirement(true, true, true, false, false, false, false, false, false, false)));
        groups++;
        expectIllegal(() -> topK(new int[]{1}, -1));
        groups++;

        require(groups == 20, "exact deterministic group count");
        return groups;
    }

    private static long randomizedChecks() {
        Random random = new Random(SEED);
        long checks = 0;
        Requirement[] requirements = {orderedMap(), topK(), fenwick(), segmentTree(), bPlusTree()};
        Structure[] structures = Structure.values();

        for (int trial = 0; trial < TRIALS; trial++) {
            Map<Integer, Integer> raw = new HashMap<>();
            TreeMap<Integer, Integer> ordered = new TreeMap<>();
            int entries = 1 + random.nextInt(30);
            for (int i = 0; i < entries; i++) {
                int key = random.nextInt(81) - 40;
                int value = random.nextInt();
                raw.put(key, value);
                ordered.put(key, value);
            }
            int probe = random.nextInt(91) - 45;
            require(equal(ordered.floorKey(probe), linearFloor(raw, probe)), "TreeMap floor oracle"); checks++;

            int n = random.nextInt(35);
            int[] values = new int[n];
            for (int i = 0; i < n; i++) values[i] = random.nextInt(101) - 50;
            int k = random.nextInt(n + 4);
            require(Arrays.equals(topK(values, k), sortedTopK(values, k)), "top-K sort oracle"); checks++;

            int length = 1 + random.nextInt(24);
            int[] array = new int[length];
            for (int i = 0; i < length; i++) array[i] = random.nextInt(101) - 50;
            FenwickAssignments fenwick = new FenwickAssignments(array);
            int index = random.nextInt(length);
            int replacement = random.nextInt(101) - 50;
            fenwick.set(index, replacement);
            array[index] = replacement;
            int left = random.nextInt(length);
            int right = left + random.nextInt(length - left);
            require(fenwick.rangeSum(left, right) == linearSum(array, left, right), "Fenwick array oracle"); checks++;

            int choice = random.nextInt(requirements.length);
            require(choose(requirements[choice]) == structures[choice], "selector template oracle"); checks++;
        }

        require(checks == 80_000L, "exact randomized check count");
        return checks;
    }

    private static Integer linearFloor(Map<Integer, Integer> map, int probe) {
        Integer answer = null;
        for (int key : map.keySet()) if (key <= probe && (answer == null || key > answer)) answer = key;
        return answer;
    }

    private static int[] sortedTopK(int[] values, int k) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int count = Math.min(k, sorted.length);
        return Arrays.copyOfRange(sorted, sorted.length - count, sorted.length);
    }

    private static long linearSum(int[] values, int left, int right) {
        long sum = 0;
        for (int i = left; i <= right; i++) sum += values[i];
        return sum;
    }

    private static boolean equal(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int groups = deterministicGroups();
        long checks = randomizedChecks();
        System.out.printf("PASS groups=%d trials=%d checks=%d%n", groups, TRIALS, checks);
    }
}
