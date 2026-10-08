import java.util.ArrayList;
import java.util.List;

/** A mutable int set implemented as a left-leaning red-black tree. */
public final class RedBlackIntSet {
    private static final boolean RED = true;
    private static final boolean BLACK = false;

    private static final class Node {
        int key;
        Node left;
        Node right;
        boolean color;

        Node(int key, boolean color) {
            this.key = key;
            this.color = color;
        }
    }

    private Node root;
    private int size;

    public int size() { return size; }
    public boolean isEmpty() { return root == null; }

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
        root.color = BLACK;
        return size != before;
    }

    private Node insert(Node node, int key) {
        if (node == null) {
            size++;
            return new Node(key, RED);
        }
        if (key < node.key) node.left = insert(node.left, key);
        else if (key > node.key) node.right = insert(node.right, key);
        else return node;

        if (isRed(node.right) && !isRed(node.left)) node = rotateLeft(node);
        if (isRed(node.left) && isRed(node.left.left)) node = rotateRight(node);
        if (isRed(node.left) && isRed(node.right)) flipColors(node);
        return node;
    }

    public boolean remove(int key) {
        if (!contains(key)) return false;
        if (!isRed(root.left) && !isRed(root.right)) root.color = RED;
        root = delete(root, key);
        size--;
        if (root != null) root.color = BLACK;
        return true;
    }

    private Node delete(Node node, int key) {
        if (key < node.key) {
            if (!isRed(node.left) && !isRed(node.left.left)) {
                node = moveRedLeft(node);
            }
            node.left = delete(node.left, key);
        } else {
            if (isRed(node.left)) node = rotateRight(node);
            if (key == node.key && node.right == null) return null;
            if (!isRed(node.right) && !isRed(node.right.left)) {
                node = moveRedRight(node);
            }
            if (key == node.key) {
                Node successor = minimum(node.right);
                node.key = successor.key;
                node.right = deleteMinimum(node.right);
            } else {
                node.right = delete(node.right, key);
            }
        }
        return balance(node);
    }

    private Node deleteMinimum(Node node) {
        if (node.left == null) return null;
        if (!isRed(node.left) && !isRed(node.left.left)) {
            node = moveRedLeft(node);
        }
        node.left = deleteMinimum(node.left);
        return balance(node);
    }

    private static Node minimum(Node node) {
        while (node.left != null) node = node.left;
        return node;
    }

    private static Node moveRedLeft(Node node) {
        flipColors(node);
        if (isRed(node.right.left)) {
            node.right = rotateRight(node.right);
            node = rotateLeft(node);
            flipColors(node);
        }
        return node;
    }

    private static Node moveRedRight(Node node) {
        flipColors(node);
        if (isRed(node.left.left)) {
            node = rotateRight(node);
            flipColors(node);
        }
        return node;
    }

    private static Node balance(Node node) {
        if (isRed(node.right)) node = rotateLeft(node);
        if (isRed(node.left) && isRed(node.left.left)) node = rotateRight(node);
        if (isRed(node.left) && isRed(node.right)) flipColors(node);
        return node;
    }

    private static Node rotateLeft(Node top) {
        Node promoted = top.right;
        top.right = promoted.left;
        promoted.left = top;
        promoted.color = top.color;
        top.color = RED;
        return promoted;
    }

    private static Node rotateRight(Node top) {
        Node promoted = top.left;
        top.left = promoted.right;
        promoted.right = top;
        promoted.color = top.color;
        top.color = RED;
        return promoted;
    }

    private static void flipColors(Node node) {
        node.color = !node.color;
        node.left.color = !node.left.color;
        node.right.color = !node.right.color;
    }

    private static boolean isRed(Node node) {
        return node != null && node.color == RED;
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

    public int heightLevels() { return heightLevels(root); }

    private static int heightLevels(Node node) {
        return node == null ? 0 : 1 + Math.max(heightLevels(node.left), heightLevels(node.right));
    }

    /** Throws when any BST or left-leaning red-black invariant is broken. */
    public void validate() {
        if (root != null && isRed(root)) fail("root is red");
        int blackHeight = validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
        if (blackHeight < 0) fail("invalid black height");
        if (count(root) != size) fail("recorded size differs from node count");
    }

    private static int validate(Node node, long low, long high) {
        if (node == null) return 1; // conceptual NIL leaf is black
        if (node.key <= low || node.key >= high) fail("BST ordering violated at " + node.key);
        if (isRed(node.right)) fail("right-leaning red link at " + node.key);
        if (isRed(node) && (isRed(node.left) || isRed(node.right))) {
            fail("consecutive red links at " + node.key);
        }
        int left = validate(node.left, low, node.key);
        int right = validate(node.right, node.key, high);
        if (left != right) fail("black-height mismatch at " + node.key);
        return left + (isRed(node) ? 0 : 1);
    }

    private static int count(Node node) {
        return node == null ? 0 : 1 + count(node.left) + count(node.right);
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}
