import java.util.ArrayList;
import java.util.List;

/** A mutable set of distinct int keys backed by an AVL tree. */
public final class AvlIntSet {
    private static final class Node {
        int key;
        int height = 1;
        Node left;
        Node right;

        Node(int key) {
            this.key = key;
        }
    }

    private Node root;
    private int size;

    public int size() { return size; }
    public boolean isEmpty() { return root == null; }
    public int heightLevels() { return height(root); }

    public boolean contains(int key) {
        Node node = root;
        while (node != null) {
            if (key < node.key) node = node.left;
            else if (key > node.key) node = node.right;
            else return true;
        }
        return false;
    }

    public boolean add(int key) {
        int before = size;
        root = insert(root, key);
        return size != before;
    }

    private Node insert(Node node, int key) {
        if (node == null) {
            size++;
            return new Node(key);
        }
        if (key < node.key) node.left = insert(node.left, key);
        else if (key > node.key) node.right = insert(node.right, key);
        else return node;
        return rebalance(node);
    }

    public boolean remove(int key) {
        if (!contains(key)) return false;
        root = delete(root, key);
        size--;
        return true;
    }

    private static Node delete(Node node, int key) {
        if (key < node.key) {
            node.left = delete(node.left, key);
        } else if (key > node.key) {
            node.right = delete(node.right, key);
        } else if (node.left == null) {
            return node.right;
        } else if (node.right == null) {
            return node.left;
        } else {
            Node successor = minimum(node.right);
            node.key = successor.key;
            node.right = delete(node.right, successor.key);
        }
        return rebalance(node);
    }

    private static Node minimum(Node node) {
        while (node.left != null) node = node.left;
        return node;
    }

    private static Node rebalance(Node node) {
        updateHeight(node);
        int balance = balance(node);
        if (balance > 1) {
            if (balance(node.left) < 0) node.left = rotateLeft(node.left); // LR
            return rotateRight(node); // LL after optional straightening
        }
        if (balance < -1) {
            if (balance(node.right) > 0) node.right = rotateRight(node.right); // RL
            return rotateLeft(node); // RR after optional straightening
        }
        return node;
    }

    private static Node rotateRight(Node top) {
        Node promoted = top.left;
        Node middle = promoted.right;
        promoted.right = top;
        top.left = middle;
        updateHeight(top);      // the node moved down: update first
        updateHeight(promoted); // then the new subtree root
        return promoted;
    }

    private static Node rotateLeft(Node top) {
        Node promoted = top.right;
        Node middle = promoted.left;
        promoted.left = top;
        top.right = middle;
        updateHeight(top);
        updateHeight(promoted);
        return promoted;
    }

    private static int height(Node node) {
        return node == null ? 0 : node.height;
    }

    private static int balance(Node node) {
        return node == null ? 0 : height(node.left) - height(node.right);
    }

    private static void updateHeight(Node node) {
        node.height = 1 + Math.max(height(node.left), height(node.right));
    }

    public List<Integer> toList() {
        List<Integer> keys = new ArrayList<>(size);
        inOrder(root, keys);
        return keys;
    }

    private static void inOrder(Node node, List<Integer> keys) {
        if (node == null) return;
        inOrder(node.left, keys);
        keys.add(node.key);
        inOrder(node.right, keys);
    }

    /** Throws if ordering, stored heights, balance, or recorded size is invalid. */
    public void validate() {
        Validation result = validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
        if (result.count != size) fail("recorded size differs from node count");
    }

    private static Validation validate(Node node, long low, long high) {
        if (node == null) return new Validation(0, 0);
        if (node.key <= low || node.key >= high) fail("BST ordering violated at " + node.key);
        Validation left = validate(node.left, low, node.key);
        Validation right = validate(node.right, node.key, high);
        int expectedHeight = 1 + Math.max(left.height, right.height);
        if (node.height != expectedHeight) fail("stale height at " + node.key);
        if (Math.abs(left.height - right.height) > 1) fail("AVL balance violated at " + node.key);
        return new Validation(1 + left.count + right.count, expectedHeight);
    }

    private record Validation(int count, int height) {}

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}
