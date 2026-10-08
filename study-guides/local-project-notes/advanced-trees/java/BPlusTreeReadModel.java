import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, in-memory teaching model of B+ tree routing and linked-leaf scans.
 * It deliberately omits writes, page I/O, latching, logging, and recovery.
 */
public final class BPlusTreeReadModel {
    public record Entry(int key, String value) {
        public Entry {
            Objects.requireNonNull(value, "value");
        }
    }

    private abstract static class Node {
        abstract int firstKey();
    }

    private static final class Leaf extends Node {
        final int[] keys;
        final String[] values;
        Leaf next;

        Leaf(int[] keys, String[] values) {
            this.keys = keys;
            this.values = values;
        }

        @Override int firstKey() {
            if (keys.length == 0) throw new IllegalStateException("empty root has no separator");
            return keys[0];
        }
    }

    private static final class Internal extends Node {
        final int[] separators;
        final Node[] children;

        Internal(List<Node> group) {
            children = group.toArray(Node[]::new);
            separators = new int[children.length - 1];
            for (int i = 1; i < children.length; i++) {
                separators[i - 1] = children[i].firstKey();
            }
        }

        @Override int firstKey() { return children[0].firstKey(); }
    }

    private final Node root;
    private final int size;
    private final int height;
    private final int leafCount;

    private BPlusTreeReadModel(Node root, int size, int height, int leafCount) {
        this.root = root;
        this.size = size;
        this.height = height;
        this.leafCount = leafCount;
    }

    public static BPlusTreeReadModel bulkLoad(
            int[] sortedDistinctKeys, String[] values, int leafCapacity, int fanout) {
        Objects.requireNonNull(sortedDistinctKeys, "sortedDistinctKeys");
        Objects.requireNonNull(values, "values");
        if (sortedDistinctKeys.length != values.length) {
            throw new IllegalArgumentException("keys and values must have equal length");
        }
        if (leafCapacity < 2) throw new IllegalArgumentException("leafCapacity must be at least 2");
        if (fanout < 2) throw new IllegalArgumentException("fanout must be at least 2");
        for (int i = 0; i < sortedDistinctKeys.length; i++) {
            Objects.requireNonNull(values[i], "values[" + i + "]");
            if (i > 0 && sortedDistinctKeys[i - 1] >= sortedDistinctKeys[i]) {
                throw new IllegalArgumentException("keys must be strictly increasing");
            }
        }

        if (sortedDistinctKeys.length == 0) {
            return new BPlusTreeReadModel(new Leaf(new int[0], new String[0]), 0, 1, 1);
        }

        int leavesNeeded = ceilDiv(sortedDistinctKeys.length, leafCapacity);
        List<Node> level = new ArrayList<>(leavesNeeded);
        Leaf previous = null;
        int offset = 0;
        for (int groupSize : balancedGroupSizes(sortedDistinctKeys.length, leavesNeeded)) {
            Leaf leaf = new Leaf(
                    Arrays.copyOfRange(sortedDistinctKeys, offset, offset + groupSize),
                    Arrays.copyOfRange(values, offset, offset + groupSize));
            if (previous != null) previous.next = leaf;
            previous = leaf;
            level.add(leaf);
            offset += groupSize;
        }

        int height = 1;
        while (level.size() > 1) {
            int parentsNeeded = ceilDiv(level.size(), fanout);
            int[] groupSizes = balancedGroupSizes(level.size(), parentsNeeded);
            List<Node> parents = new ArrayList<>(parentsNeeded);
            int child = 0;
            for (int groupSize : groupSizes) {
                parents.add(new Internal(level.subList(child, child + groupSize)));
                child += groupSize;
            }
            level = parents;
            height++;
        }
        return new BPlusTreeReadModel(level.get(0), sortedDistinctKeys.length, height, leavesNeeded);
    }

    public int size() { return size; }
    public int height() { return height; }
    public int leafCount() { return leafCount; }

    public Optional<String> get(int key) {
        Leaf leaf = descend(key);
        int index = Arrays.binarySearch(leaf.keys, key);
        return index >= 0 ? Optional.of(leaf.values[index]) : Optional.empty();
    }

    /** Returns entries with fromInclusive <= key <= toInclusive, capped by limit. */
    public List<Entry> range(int fromInclusive, int toInclusive, int limit) {
        if (fromInclusive > toInclusive) {
            throw new IllegalArgumentException("fromInclusive must not exceed toInclusive");
        }
        if (limit < 0) throw new IllegalArgumentException("limit must not be negative");
        List<Entry> result = new ArrayList<>(Math.min(limit, size));
        if (limit == 0 || size == 0) return result;

        Leaf leaf = descend(fromInclusive);
        int index = lowerBound(leaf.keys, fromInclusive);
        while (leaf != null && result.size() < limit) {
            while (index < leaf.keys.length && result.size() < limit) {
                int key = leaf.keys[index];
                if (key > toInclusive) return result;
                result.add(new Entry(key, leaf.values[index]));
                index++;
            }
            leaf = leaf.next;
            index = 0;
        }
        return result;
    }

    private Leaf descend(int key) {
        Node node = root;
        while (node instanceof Internal internal) {
            int child = upperBound(internal.separators, key);
            node = internal.children[child];
        }
        return (Leaf) node;
    }

    private static int upperBound(int[] sorted, int key) {
        int low = 0, high = sorted.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (key >= sorted[middle]) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static int lowerBound(int[] sorted, int key) {
        int low = 0, high = sorted.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sorted[middle] < key) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static int ceilDiv(int value, int divisor) {
        return 1 + (value - 1) / divisor;
    }

    private static int[] balancedGroupSizes(int itemCount, int groupCount) {
        int[] result = new int[groupCount];
        int base = itemCount / groupCount;
        int extra = itemCount % groupCount;
        for (int i = 0; i < groupCount; i++) result[i] = base + (i < extra ? 1 : 0);
        return result;
    }
}
