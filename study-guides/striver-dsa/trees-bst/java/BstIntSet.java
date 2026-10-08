import java.util.ArrayList;
import java.util.List;

/**
 * A deliberately unbalanced integer BST with set semantics.
 *
 * <p>This class accompanies BST-6 and BST-7 in the Trees & BST guide. It is
 * intended for learning pointer changes and invariants; use TreeSet when a
 * production program needs guaranteed logarithmic height.</p>
 */
public final class BstIntSet {
    private static final class Node {
        int key;
        Node left;
        Node right;

        Node(int key) {
            this.key = key;
        }
    }

    private Node root;
    private int size;

    public int size() {
        return size;
    }

    public boolean contains(int key) {
        Node current = root;
        while (current != null) {
            if (key == current.key) {
                return true;
            }
            current = key < current.key ? current.left : current.right;
        }
        return false;
    }

    /** Adds key if absent. Returns true exactly when the set changed. */
    public boolean add(int key) {
        if (root == null) {
            root = new Node(key);
            size = 1;
            return true;
        }

        Node parent = null;
        Node current = root;
        while (current != null) {
            parent = current;
            if (key == current.key) {
                return false;
            }
            current = key < current.key ? current.left : current.right;
        }

        if (key < parent.key) {
            parent.left = new Node(key);
        } else {
            parent.right = new Node(key);
        }
        size++;
        return true;
    }

    /** Removes key if present. Returns true exactly when the set changed. */
    public boolean remove(int key) {
        if (!contains(key)) {
            return false;
        }
        root = delete(root, key);
        size--;
        return true;
    }

    private static Node delete(Node node, int key) {
        if (node == null) {
            return null;
        }
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
        return node;
    }

    private static Node minimum(Node node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    public List<Integer> toSortedList() {
        List<Integer> result = new ArrayList<>(size);
        inorder(root, result);
        return result;
    }

    private static void inorder(Node node, List<Integer> result) {
        if (node == null) {
            return;
        }
        inorder(node.left, result);
        result.add(node.key);
        inorder(node.right, result);
    }

    /** Throws if ordering, reachability, or the stored size is inconsistent. */
    public void validate() {
        int counted = validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
        if (counted != size) {
            throw new IllegalStateException("size=" + size + ", reachable=" + counted);
        }
    }

    private static int validate(Node node, long lowExclusive, long highExclusive) {
        if (node == null) {
            return 0;
        }
        if (node.key <= lowExclusive || node.key >= highExclusive) {
            throw new IllegalStateException("BST order violated at " + node.key);
        }
        return 1
                + validate(node.left, lowExclusive, node.key)
                + validate(node.right, node.key, highExclusive);
    }
}
