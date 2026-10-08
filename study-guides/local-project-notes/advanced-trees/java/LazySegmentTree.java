import java.util.Objects;

/**
 * Inclusive range-add and range-sum queries over a non-empty array.
 * All values, deltas, products, and sums must fit in a signed long.
 */
public final class LazySegmentTree {
    private final int size;
    private final long[] tree;
    private final long[] lazy;

    public LazySegmentTree(long[] values) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        size = values.length;
        tree = new long[Math.multiplyExact(4, size)];
        lazy = new long[tree.length];
        build(1, 0, size - 1, values);
    }

    public int size() {
        return size;
    }

    public void rangeAdd(int left, int right, long delta) {
        checkRange(left, right);
        rangeAdd(1, 0, size - 1, left, right, delta);
    }

    public long rangeSum(int left, int right) {
        checkRange(left, right);
        return rangeSum(1, 0, size - 1, left, right);
    }

    private void build(int node, int low, int high, long[] values) {
        if (low == high) {
            tree[node] = values[low];
            return;
        }
        int middle = low + (high - low) / 2;
        build(node * 2, low, middle, values);
        build(node * 2 + 1, middle + 1, high, values);
        tree[node] = tree[node * 2] + tree[node * 2 + 1];
    }

    private void apply(int node, int low, int high, long delta) {
        tree[node] += delta * (high - low + 1L);
        lazy[node] += delta;
    }

    private void push(int node, int low, int high) {
        if (lazy[node] == 0 || low == high) {
            return;
        }
        int middle = low + (high - low) / 2;
        apply(node * 2, low, middle, lazy[node]);
        apply(node * 2 + 1, middle + 1, high, lazy[node]);
        lazy[node] = 0;
    }

    private void rangeAdd(int node, int low, int high,
                          int queryLeft, int queryRight, long delta) {
        if (queryRight < low || high < queryLeft) {
            return;
        }
        if (queryLeft <= low && high <= queryRight) {
            apply(node, low, high, delta);
            return;
        }
        push(node, low, high);
        int middle = low + (high - low) / 2;
        rangeAdd(node * 2, low, middle, queryLeft, queryRight, delta);
        rangeAdd(node * 2 + 1, middle + 1, high, queryLeft, queryRight, delta);
        tree[node] = tree[node * 2] + tree[node * 2 + 1];
    }

    private long rangeSum(int node, int low, int high,
                          int queryLeft, int queryRight) {
        if (queryRight < low || high < queryLeft) {
            return 0;
        }
        if (queryLeft <= low && high <= queryRight) {
            return tree[node];
        }
        push(node, low, high);
        int middle = low + (high - low) / 2;
        return rangeSum(node * 2, low, middle, queryLeft, queryRight)
                + rangeSum(node * 2 + 1, middle + 1, high,
                           queryLeft, queryRight);
    }

    private void checkRange(int left, int right) {
        if (left < 0 || right >= size) {
            throw new IndexOutOfBoundsException(
                    "range [" + left + "," + right + "] for size " + size);
        }
        if (left > right) {
            throw new IllegalArgumentException("left must be <= right");
        }
    }
}
