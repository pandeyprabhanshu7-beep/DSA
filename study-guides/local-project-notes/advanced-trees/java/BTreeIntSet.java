import java.util.ArrayList;
import java.util.List;

/** A mutable textbook B-tree set for distinct int keys. Not a storage engine. */
public final class BTreeIntSet {
    private static final class Node {
        int keyCount;
        final int[] keys;
        final Node[] children;
        final boolean leaf;

        Node(int minimumDegree, boolean leaf) {
            this.leaf = leaf;
            this.keys = new int[2 * minimumDegree - 1];
            this.children = new Node[2 * minimumDegree];
        }
    }

    private final int minimumDegree;
    private Node root;
    private int size;

    public BTreeIntSet(int minimumDegree) {
        if (minimumDegree < 2) {
            throw new IllegalArgumentException("minimumDegree must be at least 2");
        }
        if (minimumDegree > (Integer.MAX_VALUE - 1) / 2) {
            throw new IllegalArgumentException("minimumDegree is too large for array sizing");
        }
        this.minimumDegree = minimumDegree;
        this.root = new Node(minimumDegree, true);
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    public boolean contains(int key) {
        Node node = root;
        while (true) {
            int index = lowerBound(node, key);
            if (index < node.keyCount && node.keys[index] == key) return true;
            if (node.leaf) return false;
            node = node.children[index];
        }
    }

    public boolean add(int key) {
        if (contains(key)) return false;
        if (root.keyCount == maxKeys()) {
            Node newRoot = new Node(minimumDegree, false);
            newRoot.children[0] = root;
            splitChild(newRoot, 0);
            root = newRoot;
        }
        insertNonFull(root, key);
        size++;
        return true;
    }

    public boolean remove(int key) {
        if (!contains(key)) return false;
        delete(root, key);
        if (!root.leaf && root.keyCount == 0) root = root.children[0];
        size--;
        return true;
    }

    public List<Integer> toList() {
        List<Integer> result = new ArrayList<>(size);
        traverse(root, result);
        return result;
    }

    public int heightLevels() {
        int levels = 1;
        Node node = root;
        while (!node.leaf) {
            levels++;
            node = node.children[0];
        }
        return levels;
    }

    /** Throws if ordering, occupancy, child-range, depth, or size invariants fail. */
    public void validate() {
        int[] leafDepth = {-1};
        int counted = validateNode(root, true, Long.MIN_VALUE, Long.MAX_VALUE, 0, leafDepth);
        if (counted != size) {
            throw new IllegalStateException("stored size " + size + " but counted " + counted);
        }
    }

    private void insertNonFull(Node node, int key) {
        int index = node.keyCount - 1;
        if (node.leaf) {
            while (index >= 0 && key < node.keys[index]) {
                node.keys[index + 1] = node.keys[index];
                index--;
            }
            node.keys[index + 1] = key;
            node.keyCount++;
            return;
        }

        while (index >= 0 && key < node.keys[index]) index--;
        index++;
        if (node.children[index].keyCount == maxKeys()) {
            splitChild(node, index);
            if (key > node.keys[index]) index++;
        }
        insertNonFull(node.children[index], key);
    }

    /** Split a full child and promote its median into a non-full parent. */
    private void splitChild(Node parent, int childIndex) {
        Node full = parent.children[childIndex];
        Node right = new Node(minimumDegree, full.leaf);
        right.keyCount = minimumDegree - 1;

        for (int j = 0; j < minimumDegree - 1; j++) {
            right.keys[j] = full.keys[j + minimumDegree];
        }
        if (!full.leaf) {
            for (int j = 0; j < minimumDegree; j++) {
                right.children[j] = full.children[j + minimumDegree];
                full.children[j + minimumDegree] = null;
            }
        }
        int median = full.keys[minimumDegree - 1];
        full.keyCount = minimumDegree - 1;

        for (int j = parent.keyCount; j >= childIndex + 1; j--) {
            parent.children[j + 1] = parent.children[j];
        }
        parent.children[childIndex + 1] = right;
        for (int j = parent.keyCount - 1; j >= childIndex; j--) {
            parent.keys[j + 1] = parent.keys[j];
        }
        parent.keys[childIndex] = median;
        parent.keyCount++;
    }

    private void delete(Node node, int key) {
        int index = lowerBound(node, key);
        if (index < node.keyCount && node.keys[index] == key) {
            if (node.leaf) removeFromLeaf(node, index);
            else removeFromInternal(node, index);
            return;
        }
        if (node.leaf) throw new IllegalStateException("prechecked key disappeared");

        boolean wasLastChild = index == node.keyCount;
        if (node.children[index].keyCount == minimumDegree - 1) fill(node, index);

        // A merge with the previous sibling reduces node.keyCount and moves the
        // former last child one slot left.
        if (wasLastChild && index > node.keyCount) delete(node.children[index - 1], key);
        else delete(node.children[index], key);
    }

    private void removeFromLeaf(Node node, int index) {
        for (int j = index + 1; j < node.keyCount; j++) node.keys[j - 1] = node.keys[j];
        node.keyCount--;
    }

    private void removeFromInternal(Node node, int index) {
        int key = node.keys[index];
        Node left = node.children[index];
        Node right = node.children[index + 1];
        if (left.keyCount >= minimumDegree) {
            int predecessor = maximum(left);
            node.keys[index] = predecessor;
            delete(left, predecessor);
        } else if (right.keyCount >= minimumDegree) {
            int successor = minimum(right);
            node.keys[index] = successor;
            delete(right, successor);
        } else {
            merge(node, index);
            delete(left, key);
        }
    }

    /** Ensure children[index] has at least minimumDegree keys before descent. */
    private void fill(Node parent, int index) {
        if (index > 0 && parent.children[index - 1].keyCount >= minimumDegree) {
            borrowFromPrevious(parent, index);
        } else if (index < parent.keyCount
                && parent.children[index + 1].keyCount >= minimumDegree) {
            borrowFromNext(parent, index);
        } else if (index < parent.keyCount) {
            merge(parent, index);
        } else {
            merge(parent, index - 1);
        }
    }

    private void borrowFromPrevious(Node parent, int index) {
        Node child = parent.children[index];
        Node sibling = parent.children[index - 1];
        for (int j = child.keyCount - 1; j >= 0; j--) child.keys[j + 1] = child.keys[j];
        if (!child.leaf) {
            for (int j = child.keyCount; j >= 0; j--) child.children[j + 1] = child.children[j];
        }
        child.keys[0] = parent.keys[index - 1];
        if (!child.leaf) {
            child.children[0] = sibling.children[sibling.keyCount];
            sibling.children[sibling.keyCount] = null;
        }
        parent.keys[index - 1] = sibling.keys[sibling.keyCount - 1];
        child.keyCount++;
        sibling.keyCount--;
    }

    private void borrowFromNext(Node parent, int index) {
        Node child = parent.children[index];
        Node sibling = parent.children[index + 1];
        child.keys[child.keyCount] = parent.keys[index];
        if (!child.leaf) child.children[child.keyCount + 1] = sibling.children[0];
        parent.keys[index] = sibling.keys[0];
        for (int j = 1; j < sibling.keyCount; j++) sibling.keys[j - 1] = sibling.keys[j];
        if (!sibling.leaf) {
            for (int j = 1; j <= sibling.keyCount; j++) {
                sibling.children[j - 1] = sibling.children[j];
            }
            sibling.children[sibling.keyCount] = null;
        }
        child.keyCount++;
        sibling.keyCount--;
    }

    /** Merge children[index], parent separator, and children[index + 1]. */
    private void merge(Node parent, int index) {
        Node left = parent.children[index];
        Node right = parent.children[index + 1];
        left.keys[minimumDegree - 1] = parent.keys[index];
        for (int j = 0; j < right.keyCount; j++) {
            left.keys[j + minimumDegree] = right.keys[j];
        }
        if (!left.leaf) {
            for (int j = 0; j <= right.keyCount; j++) {
                left.children[j + minimumDegree] = right.children[j];
            }
        }
        left.keyCount += right.keyCount + 1;

        for (int j = index + 1; j < parent.keyCount; j++) parent.keys[j - 1] = parent.keys[j];
        for (int j = index + 2; j <= parent.keyCount; j++) {
            parent.children[j - 1] = parent.children[j];
        }
        parent.children[parent.keyCount] = null;
        parent.keyCount--;
    }

    private int minimum(Node node) {
        while (!node.leaf) node = node.children[0];
        return node.keys[0];
    }

    private int maximum(Node node) {
        while (!node.leaf) node = node.children[node.keyCount];
        return node.keys[node.keyCount - 1];
    }

    private int lowerBound(Node node, int key) {
        int low = 0, high = node.keyCount;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (node.keys[middle] < key) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private void traverse(Node node, List<Integer> output) {
        for (int i = 0; i < node.keyCount; i++) {
            if (!node.leaf) traverse(node.children[i], output);
            output.add(node.keys[i]);
        }
        if (!node.leaf) traverse(node.children[node.keyCount], output);
    }

    private int validateNode(Node node, boolean isRoot, long lower, long upper,
                             int depth, int[] leafDepth) {
        int minimumKeys = isRoot ? (node.leaf ? 0 : 1) : minimumDegree - 1;
        if (node.keyCount < minimumKeys || node.keyCount > maxKeys()) {
            throw new IllegalStateException("invalid occupancy at depth " + depth);
        }
        long previous = lower;
        for (int i = 0; i < node.keyCount; i++) {
            long key = node.keys[i];
            if (key <= previous || key >= upper) {
                throw new IllegalStateException("invalid key order/range at depth " + depth);
            }
            previous = key;
        }

        if (node.leaf) {
            if (leafDepth[0] == -1) leafDepth[0] = depth;
            else if (leafDepth[0] != depth) throw new IllegalStateException("leaves differ in depth");
            return node.keyCount;
        }

        int count = node.keyCount;
        for (int i = 0; i <= node.keyCount; i++) {
            if (node.children[i] == null) throw new IllegalStateException("missing child");
            long childLower = i == 0 ? lower : node.keys[i - 1];
            long childUpper = i == node.keyCount ? upper : node.keys[i];
            count += validateNode(node.children[i], false, childLower, childUpper,
                    depth + 1, leafDepth);
        }
        return count;
    }

    private int maxKeys() { return 2 * minimumDegree - 1; }
}
