import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

public final class TreeMistakeContracts {
    private static int groups;

    record Node(int key, Node left, Node right) {}
    enum Rotation { RIGHT, LEFT_RIGHT, LEFT, RIGHT_LEFT }
    enum Color { RED, BLACK }
    enum BPlusRole { INTERNAL_SEPARATOR, LEAF_RECORD }
    record PushResult(long parentTag, long leftTag, long rightTag) {}

    static boolean isStrictBst(Node root) {
        return inside(root, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private static boolean inside(Node node, long lowExclusive, long highExclusive) {
        if (node == null) return true;
        if (node.key() <= lowExclusive || node.key() >= highExclusive) return false;
        return inside(node.left(), lowExclusive, node.key())
                && inside(node.right(), node.key(), highExclusive);
    }

    static Rotation rotationFor(int rootBalance, int heavyChildBalance) {
        if (rootBalance > 1) {
            return heavyChildBalance >= 0 ? Rotation.RIGHT : Rotation.LEFT_RIGHT;
        }
        if (rootBalance < -1) {
            return heavyChildBalance <= 0 ? Rotation.LEFT : Rotation.RIGHT_LEFT;
        }
        throw new IllegalArgumentException("root is not unbalanced");
    }

    static Color normalizeRoot(Color ignoredCurrentColor) {
        return Color.BLACK;
    }

    static boolean mustSplitBeforeDescent(int childKeyCount, int maximumKeys) {
        if (maximumKeys < 1 || childKeyCount < 0 || childKeyCount > maximumKeys) {
            throw new IllegalArgumentException("invalid B-tree occupancy");
        }
        return childKeyCount == maximumKeys;
    }

    static BPlusRole internalEntryRole() {
        return BPlusRole.INTERNAL_SEPARATOR;
    }

    static BPlusRole leafEntryRole() {
        return BPlusRole.LEAF_RECORD;
    }

    static List<Integer> sortedDrain(PriorityQueue<Integer> queue) {
        PriorityQueue<Integer> copy = new PriorityQueue<>(queue);
        List<Integer> result = new ArrayList<>(copy.size());
        while (!copy.isEmpty()) result.add(copy.remove());
        return List.copyOf(result);
    }

    static final class Trie {
        private static final class TrieNode {
            final TrieNode[] children = new TrieNode[26];
            boolean terminal;
        }

        private final TrieNode root = new TrieNode();

        void add(String word) {
            TrieNode node = root;
            for (int i = 0; i < word.length(); i++) {
                int slot = word.charAt(i) - 'a';
                if (slot < 0 || slot >= 26) throw new IllegalArgumentException("lowercase a-z only");
                if (node.children[slot] == null) node.children[slot] = new TrieNode();
                node = node.children[slot];
            }
            node.terminal = true;
        }

        boolean containsWord(String value) {
            TrieNode node = find(value);
            return node != null && node.terminal;
        }

        boolean containsPrefix(String value) {
            return find(value) != null;
        }

        private TrieNode find(String value) {
            TrieNode node = root;
            for (int i = 0; i < value.length() && node != null; i++) {
                int slot = value.charAt(i) - 'a';
                if (slot < 0 || slot >= 26) return null;
                node = node.children[slot];
            }
            return node;
        }
    }

    static long applyRangeSum(long currentSum, long delta, int segmentLength) {
        if (segmentLength <= 0) throw new IllegalArgumentException("segment must be non-empty");
        return Math.addExact(currentSum, Math.multiplyExact(delta, segmentLength));
    }

    static PushResult pushAndClear(long parentTag, long leftTag, long rightTag) {
        return new PushResult(
                0,
                Math.addExact(leftTag, parentTag),
                Math.addExact(rightTag, parentTag));
    }

    static final class Fenwick {
        private final long[] tree;

        Fenwick(int size) {
            if (size <= 0) throw new IllegalArgumentException("size must be positive");
            tree = new long[size + 1];
        }

        void addAtZeroBased(int index, long delta) {
            if (index < 0 || index >= size()) throw new IndexOutOfBoundsException(index);
            for (int i = index + 1; i < tree.length; i += i & -i) {
                tree[i] = Math.addExact(tree[i], delta);
            }
        }

        long prefixThroughZeroBased(int index) {
            if (index < -1 || index >= size()) throw new IndexOutOfBoundsException(index);
            long sum = 0;
            for (int i = index + 1; i > 0; i -= i & -i) sum = Math.addExact(sum, tree[i]);
            return sum;
        }

        int size() {
            return tree.length - 1;
        }
    }

    private static void group(boolean condition, String message) {
        groups++;
        if (!condition) throw new AssertionError(message);
    }

    private static void deterministicGroups() {
        group(isStrictBst(null), "empty tree is valid");
        group(isStrictBst(new Node(0, new Node(Integer.MIN_VALUE, null, null),
                new Node(Integer.MAX_VALUE, null, null))), "full int domain remains usable");
        group(!isStrictBst(new Node(10, new Node(5, null, null),
                new Node(15, new Node(6, null, null), null))), "ancestor bounds catch local-order trap");
        group(!isStrictBst(new Node(2, new Node(2, null, null), null)), "strict BST rejects duplicates");

        group(rotationFor(2, 1) == Rotation.RIGHT, "LL repair");
        group(rotationFor(2, -1) == Rotation.LEFT_RIGHT, "LR repair");
        group(rotationFor(-2, -1) == Rotation.LEFT, "RR repair");
        group(rotationFor(-2, 1) == Rotation.RIGHT_LEFT, "RL repair");
        group(rotationFor(2, 0) == Rotation.RIGHT, "deletion uses child balance, including zero");
        group(normalizeRoot(Color.RED) == Color.BLACK, "red-black root normalization");

        group(mustSplitBeforeDescent(5, 5), "full B-tree child splits before descent");
        group(!mustSplitBeforeDescent(4, 5), "non-full child may receive descent");
        group(internalEntryRole() == BPlusRole.INTERNAL_SEPARATOR
                && leafEntryRole() == BPlusRole.LEAF_RECORD, "B+ routing and records remain distinct");

        PriorityQueue<Integer> queue = new PriorityQueue<>(Arrays.asList(7, 1, 5, 2));
        group(sortedDrain(queue).equals(List.of(1, 2, 5, 7)) && queue.size() == 4,
                "ordered output requires repeated removal from an owned copy");
        Trie trie = new Trie();
        trie.add("tree");
        group(trie.containsPrefix("tr") && !trie.containsWord("tr") && trie.containsWord("tree"),
                "prefix reachability is not word membership");

        group(applyRangeSum(10, 3, 4) == 22, "range sum scales delta by segment length");
        group(pushAndClear(5, 2, -1).equals(new PushResult(0, 7, 4)), "lazy push clears parent tag");
        Fenwick fenwick = new Fenwick(5);
        fenwick.addAtZeroBased(0, 4);
        group(fenwick.prefixThroughZeroBased(0) == 4, "zero-based API converts once");
        fenwick.addAtZeroBased(4, 6);
        group(fenwick.prefixThroughZeroBased(3) == 4 && fenwick.prefixThroughZeroBased(4) == 10,
                "Fenwick prefix boundaries are explicit");
        boolean rejected = false;
        try {
            fenwick.addAtZeroBased(-1, 1);
        } catch (IndexOutOfBoundsException expected) {
            rejected = true;
        }
        group(rejected, "invalid Fenwick API index is rejected before lowbit loop");
    }

    private static long repeatedRangeAddition(long start, long delta, int length) {
        long result = start;
        for (int i = 0; i < length; i++) result = Math.addExact(result, delta);
        return result;
    }

    private static void randomizedChecks() {
        Random random = new Random(0x13C0FFEE);
        Fenwick fenwick = new Fenwick(64);
        long[] values = new long[64];
        long checks = 0;
        for (int trial = 0; trial < 20_000; trial++) {
            int root = random.nextInt(2_000_001) - 1_000_000;
            Node ancestorTrap = new Node(root, null,
                    new Node(root + 2, new Node(root - 1, null, null), null));
            if (isStrictBst(ancestorTrap)) throw new AssertionError("random ancestor trap accepted");
            checks++;

            int rootBalance = random.nextBoolean() ? 2 : -2;
            int childBalance = random.nextInt(3) - 1;
            Rotation actual = rotationFor(rootBalance, childBalance);
            Rotation expected = rootBalance > 0
                    ? (childBalance >= 0 ? Rotation.RIGHT : Rotation.LEFT_RIGHT)
                    : (childBalance <= 0 ? Rotation.LEFT : Rotation.RIGHT_LEFT);
            if (actual != expected) throw new AssertionError("rotation classification mismatch");
            checks++;

            int length = random.nextInt(32) + 1;
            long start = random.nextInt(20_001) - 10_000;
            long delta = random.nextInt(201) - 100;
            if (applyRangeSum(start, delta, length) != repeatedRangeAddition(start, delta, length)) {
                throw new AssertionError("range aggregate mismatch");
            }
            checks++;

            int updateIndex = random.nextInt(values.length);
            long updateDelta = random.nextInt(101) - 50;
            values[updateIndex] = Math.addExact(values[updateIndex], updateDelta);
            fenwick.addAtZeroBased(updateIndex, updateDelta);
            int queryIndex = random.nextInt(values.length);
            long expectedPrefix = 0;
            for (int i = 0; i <= queryIndex; i++) expectedPrefix = Math.addExact(expectedPrefix, values[i]);
            if (fenwick.prefixThroughZeroBased(queryIndex) != expectedPrefix) {
                throw new AssertionError("Fenwick prefix mismatch");
            }
            checks++;
        }
        if (checks != 80_000) throw new AssertionError("unexpected check count " + checks);
        System.out.println("PASS groups=" + groups + " trials=20000 checks=" + checks);
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedChecks();
    }
}
