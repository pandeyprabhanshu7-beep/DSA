import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;
import java.util.TreeSet;

/** Executable contracts for the advanced-tree easy/medium/hard problem ladder. */
public class TreeProblemLadderContracts {
    static final class Node {
        final int key;
        Node left;
        Node right;

        Node(int key) {
            this.key = key;
        }
    }

    private static final class Fenwick {
        private final int[] tree;

        Fenwick(int size) {
            tree = new int[size + 1];
        }

        void add(int oneBasedIndex, int delta) {
            if (oneBasedIndex <= 0 || oneBasedIndex >= tree.length) {
                throw new IllegalArgumentException("Fenwick index out of range");
            }
            for (int i = oneBasedIndex; i < tree.length; i += i & -i) tree[i] += delta;
        }

        int prefixSum(int oneBasedIndex) {
            if (oneBasedIndex < 0 || oneBasedIndex >= tree.length) {
                throw new IllegalArgumentException("Fenwick prefix out of range");
            }
            int sum = 0;
            for (int i = oneBasedIndex; i > 0; i -= i & -i) sum += tree[i];
            return sum;
        }
    }

    static boolean isValidBst(Node root) {
        return within(root, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private static boolean within(Node node, long lowExclusive, long highExclusive) {
        if (node == null) return true;
        if (node.key <= lowExclusive || node.key >= highExclusive) return false;
        return within(node.left, lowExclusive, node.key)
                && within(node.right, node.key, highExclusive);
    }

    static int kthSmallest(Node root, int k) {
        if (root == null) throw new IllegalArgumentException("root is required");
        if (k <= 0) throw new IllegalArgumentException("k must be positive and one-based");
        ArrayDeque<Node> stack = new ArrayDeque<>();
        Node current = root;
        int remaining = k;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.push(current);
                current = current.left;
            }
            current = stack.pop();
            if (--remaining == 0) return current.key;
            current = current.right;
        }
        throw new IllegalArgumentException("k exceeds the node count");
    }

    static int[] countSmallerAfterSelf(int[] input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        int[] sorted = input.clone();
        Arrays.sort(sorted);
        int unique = 0;
        for (int value : sorted) {
            if (unique == 0 || value != sorted[unique - 1]) sorted[unique++] = value;
        }

        Fenwick frequencies = new Fenwick(unique);
        int[] answer = new int[input.length];
        for (int i = input.length - 1; i >= 0; i--) {
            int rank = lowerBound(sorted, unique, input[i]) + 1;
            answer[i] = frequencies.prefixSum(rank - 1);
            frequencies.add(rank, 1);
        }
        return answer;
    }

    private static int lowerBound(int[] sorted, int length, int target) {
        int low = 0;
        int high = length;
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (sorted[middle] < target) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static int groups;
    private static long checks;

    private static void deterministicGroups() {
        group(isValidBst(null), "empty tree is valid");
        group(isValidBst(node(2, node(1), node(3))), "simple valid BST");
        group(!isValidBst(node(10, node(5), node(15, node(6), node(20)))),
                "ancestor-bound violation");
        group(!isValidBst(node(2, node(2), null)), "duplicate violates strict ordering");
        group(isValidBst(node(0, node(Integer.MIN_VALUE), node(Integer.MAX_VALUE))),
                "integer extremes fit long bounds");

        Node seven = node(5,
                node(3, node(2), node(4)),
                node(7, node(6), node(8)));
        group(kthSmallest(seven, 1) == 2, "first inorder value");
        group(kthSmallest(seven, 4) == 5, "source fourth-smallest trace");
        group(kthSmallest(seven, 7) == 8, "last inorder value");
        group(kthSmallest(node(-7), 1) == -7, "singleton rank");
        group(fails(() -> kthSmallest(seven, 0)), "zero k rejected");
        group(fails(() -> kthSmallest(seven, 8)), "oversized k rejected");
        group(fails(() -> kthSmallest(null, 1)), "null rank tree rejected");

        group(Arrays.equals(countSmallerAfterSelf(new int[] {5, 2, 6, 1}),
                new int[] {2, 1, 1, 0}), "official four-value example");
        group(Arrays.equals(countSmallerAfterSelf(new int[] {5, 2, 6, 1, 3, 2, 7, 0}),
                new int[] {5, 2, 4, 1, 2, 1, 1, 0}), "source long trace");
        group(Arrays.equals(countSmallerAfterSelf(new int[] {-1, -1}),
                new int[] {0, 0}), "equal values are not smaller");
        group(Arrays.equals(countSmallerAfterSelf(new int[] {3, 2, 1}),
                new int[] {2, 1, 0}), "descending input");
        group(Arrays.equals(countSmallerAfterSelf(new int[] {}), new int[] {}), "empty input");
        group(Arrays.equals(countSmallerAfterSelf(new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE}),
                new int[] {1, 0}), "full integer domain");
        group(fails(() -> countSmallerAfterSelf(null)), "null array rejected");
        int[] owned = {4, 1, 4, 0};
        int[] snapshot = owned.clone();
        countSmallerAfterSelf(owned);
        group(Arrays.equals(owned, snapshot), "input ownership retained");
    }

    private static void randomizedChecks() {
        Random random = new Random(0x7AEE12L);
        for (int trial = 0; trial < 20_000; trial++) {
            Node arbitrary = randomTree(random, random.nextInt(32));
            trialCheck(isValidBst(arbitrary) == inorderStrict(arbitrary), "BST oracle mismatch");

            TreeSet<Integer> expectedSet = new TreeSet<>();
            Node bst = null;
            int attempts = 1 + random.nextInt(35);
            for (int i = 0; i < attempts; i++) {
                int value = random.nextInt(101) - 50;
                expectedSet.add(value);
                bst = insert(bst, value);
            }
            int k = 1 + random.nextInt(expectedSet.size());
            int expectedKth = expectedSet.stream().skip(k - 1L).findFirst().orElseThrow();
            trialCheck(kthSmallest(bst, k) == expectedKth, "kth-smallest oracle mismatch");

            int[] values = new int[random.nextInt(41)];
            for (int i = 0; i < values.length; i++) values[i] = random.nextInt(41) - 20;
            int[] before = values.clone();
            trialCheck(Arrays.equals(countSmallerAfterSelf(values), quadraticCounts(values)),
                    "suffix-count oracle mismatch");
            trialCheck(Arrays.equals(values, before), "suffix counter mutated input");
        }
    }

    private static Node randomTree(Random random, int size) {
        if (size == 0) return null;
        Node[] nodes = new Node[size];
        for (int i = 0; i < size; i++) nodes[i] = new Node(random.nextInt(101) - 50);
        for (int i = 1; i < size; i++) {
            int parent = (i - 1) / 2;
            if ((i & 1) == 1) nodes[parent].left = nodes[i];
            else nodes[parent].right = nodes[i];
        }
        return nodes[0];
    }

    private static boolean inorderStrict(Node root) {
        ArrayDeque<Node> stack = new ArrayDeque<>();
        Node current = root;
        long previous = Long.MIN_VALUE;
        boolean seen = false;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.push(current);
                current = current.left;
            }
            current = stack.pop();
            if (seen && current.key <= previous) return false;
            previous = current.key;
            seen = true;
            current = current.right;
        }
        return true;
    }

    private static Node insert(Node root, int value) {
        if (root == null) return new Node(value);
        Node current = root;
        while (true) {
            if (value == current.key) return root;
            if (value < current.key) {
                if (current.left == null) {
                    current.left = new Node(value);
                    return root;
                }
                current = current.left;
            } else {
                if (current.right == null) {
                    current.right = new Node(value);
                    return root;
                }
                current = current.right;
            }
        }
    }

    private static int[] quadraticCounts(int[] values) {
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                if (values[j] < values[i]) result[i]++;
            }
        }
        return result;
    }

    private static Node node(int key) {
        return new Node(key);
    }

    private static Node node(int key, Node left, Node right) {
        Node result = new Node(key);
        result.left = left;
        result.right = right;
        return result;
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void group(boolean condition, String message) {
        check(condition, message);
        groups++;
    }

    private static void trialCheck(boolean condition, String message) {
        check(condition, message);
        checks++;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedChecks();
        System.out.println("PASS groups=" + groups + " trials=20000 checks=" + checks);
    }
}
