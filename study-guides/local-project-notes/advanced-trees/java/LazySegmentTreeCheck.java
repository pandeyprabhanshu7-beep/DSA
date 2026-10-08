import java.util.Random;

public final class LazySegmentTreeCheck {
    public static void main(String[] args) {
        deterministicChecks();
        boundaryChecks();
        randomizedOracleChecks();
        System.out.println(
                "PASS: deterministic/boundary checks; 100,000 random updates; "
                        + "200,000 oracle queries.");
    }

    private static void deterministicChecks() {
        LazySegmentTree tree = new LazySegmentTree(
                new long[] {2, 1, 3, 4, 5, 7, 6, 8});
        expect(36, tree.rangeSum(0, 7), "initial total");
        tree.rangeAdd(2, 6, 10);
        expect(86, tree.rangeSum(0, 7), "updated total");
        expect(60, tree.rangeSum(1, 5), "source dry run");
        expect(13, tree.rangeSum(2, 2), "lazy leaf");

        tree.rangeAdd(0, 7, -2);
        expect(70, tree.rangeSum(0, 7), "negative full-range add");
        expect(6, tree.rangeSum(7, 7), "right boundary");
    }

    private static void boundaryChecks() {
        LazySegmentTree one = new LazySegmentTree(new long[] {7});
        one.rangeAdd(0, 0, -12);
        expect(-5, one.rangeSum(0, 0), "singleton");

        expectThrows(NullPointerException.class,
                () -> new LazySegmentTree(null));
        expectThrows(IllegalArgumentException.class,
                () -> new LazySegmentTree(new long[0]));
        expectThrows(IndexOutOfBoundsException.class,
                () -> one.rangeSum(-1, 0));
        expectThrows(IndexOutOfBoundsException.class,
                () -> one.rangeAdd(0, 1, 3));

        LazySegmentTree two = new LazySegmentTree(new long[] {1, 2});
        expectThrows(IllegalArgumentException.class,
                () -> two.rangeSum(1, 0));

        LazySegmentTree wide = new LazySegmentTree(
                new long[] {2_000_000_000L, 2_000_000_000L});
        expect(4_000_000_000L, wide.rangeSum(0, 1), "long sum");
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(9_031);
        int updates = 0;
        int queries = 0;
        for (int trial = 0; trial < 2_000; trial++) {
            int n = 1 + random.nextInt(64);
            long[] oracle = new long[n];
            for (int i = 0; i < n; i++) {
                oracle[i] = random.nextInt(2_001) - 1_000;
            }
            LazySegmentTree tree = new LazySegmentTree(oracle.clone());

            for (int operation = 0; operation < 50; operation++) {
                int left = random.nextInt(n);
                int right = random.nextInt(n);
                if (left > right) {
                    int temporary = left;
                    left = right;
                    right = temporary;
                }
                long delta = random.nextInt(2_001) - 1_000;
                tree.rangeAdd(left, right, delta);
                for (int i = left; i <= right; i++) {
                    oracle[i] += delta;
                }
                updates++;

                for (int check = 0; check < 2; check++) {
                    int queryLeft = random.nextInt(n);
                    int queryRight = random.nextInt(n);
                    if (queryLeft > queryRight) {
                        int temporary = queryLeft;
                        queryLeft = queryRight;
                        queryRight = temporary;
                    }
                    long expected = 0;
                    for (int i = queryLeft; i <= queryRight; i++) {
                        expected += oracle[i];
                    }
                    expect(expected, tree.rangeSum(queryLeft, queryRight),
                            "random range");
                    queries++;
                }
            }
        }
        expect(100_000, updates, "update count");
        expect(200_000, queries, "query count");
    }

    private static void expect(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected
                    + ", got " + actual);
        }
    }

    private static void expectThrows(
            Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (Throwable error) {
            if (!type.isInstance(error)) {
                throw new AssertionError("Expected " + type.getSimpleName()
                        + ", got " + error, error);
            }
        }
    }
}
