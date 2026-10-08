public final class Fenwick {
    private final long[] bit;
    public Fenwick(int size) {
        if (size < 0 || size == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid size");
        bit = new long[size + 1];
    }
    public int size() { return bit.length - 1; }
    public void add(int index, long delta) {
        checkIndex(index);
        for (long i = index + 1L; i < bit.length; i += i & -i)
            bit[(int) i] += delta;
    }
    public long prefixSum(int index) {
        if (index < -1 || index >= size())
            throw new IndexOutOfBoundsException("Prefix index: " + index);
        long result = 0;
        for (int i = index + 1; i > 0; i -= i & -i)
            result += bit[i];
        return result;
    }
    public long rangeSum(int left, int right) {
        checkIndex(left);
        checkIndex(right);
        if (left > right) throw new IllegalArgumentException("left > right");
        return prefixSum(right) - prefixSum(left - 1);
    }
    private void checkIndex(int index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException("Element index: " + index);
    }
}
