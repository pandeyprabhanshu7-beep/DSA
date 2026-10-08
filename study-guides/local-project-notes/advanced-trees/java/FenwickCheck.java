import java.util.Random;
public final class FenwickCheck {
    public static void main(String[] args) {
        Random random = new Random(29);
        int queries = 0;
        for (int trial = 0; trial < 2000; trial++) {
            int n = 1 + random.nextInt(64);
            long[] array = new long[n];
            Fenwick tree = new Fenwick(n);
            for (int op = 0; op < 50; op++) {
                int index = random.nextInt(n);
                long delta = random.nextInt(2001) - 1000;
                array[index] += delta;
                tree.add(index, delta);
                int left = random.nextInt(n), right = random.nextInt(n);
                if (left > right) { int tmp = left; left = right; right = tmp; }
                long expected = 0;
                for (int i = left; i <= right; i++) expected += array[i];
                if (tree.rangeSum(left, right) != expected) throw new AssertionError("range");
                expected = 0;
                for (int i = 0; i <= index; i++) expected += array[i];
                if (tree.prefixSum(index) != expected) throw new AssertionError("prefix");
                queries += 2;
            }
        }
        Fenwick empty = new Fenwick(0);
        if (empty.prefixSum(-1) != 0) throw new AssertionError("empty");
        try { empty.add(0, 1); throw new AssertionError("bounds"); }
        catch (IndexOutOfBoundsException expected) { }
        Fenwick big = new Fenwick(2);
        big.add(0, 2_000_000_000L); big.add(1, 2_000_000_000L);
        if (big.rangeSum(0, 1) != 4_000_000_000L) throw new AssertionError("long");
        try { big.rangeSum(1, 0); throw new AssertionError("range order"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS: 100,000 random updates; " + queries + " oracle queries; empty, bounds and large sums.");
    }
}
