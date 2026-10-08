import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Random;

public final class IntMinHeapCheck {
    public static void main(String[] args) {
        deterministicChecks();
        boundaryChecks();
        randomizedOperationChecks();
        randomizedHeapifyChecks();
        System.out.println(
                "PASS: deterministic/boundary checks; 100,000 mixed random "
                        + "operations with PriorityQueue oracle; 10,000 "
                        + "random heapify-and-drain comparisons.");
    }

    private static void deterministicChecks() {
        IntMinHeap heap = new IntMinHeap(new int[] {2, 5, 3, 9, 7, 8, 6});
        expect(2, heap.peek(), "initial root");
        heap.add(4);
        expect(2, heap.poll(), "source poll");
        int[] expected = {3, 4, 5, 6, 7, 8, 9};
        for (int value : expected) {
            expect(value, heap.poll(), "source drain");
        }

        IntMinHeap duplicates = new IntMinHeap();
        duplicates.add(5);
        duplicates.add(-2);
        duplicates.add(5);
        duplicates.add(Integer.MIN_VALUE);
        duplicates.add(Integer.MAX_VALUE);
        int[] order = {Integer.MIN_VALUE, -2, 5, 5, Integer.MAX_VALUE};
        for (int value : order) {
            expect(value, duplicates.poll(), "extreme/duplicate order");
        }
    }

    private static void boundaryChecks() {
        IntMinHeap empty = new IntMinHeap();
        expectTrue(empty.isEmpty(), "new heap empty");
        expect(0, empty.size(), "new heap size");
        expectThrows(NoSuchElementException.class, empty::peek);
        expectThrows(NoSuchElementException.class, empty::poll);
        expectThrows(NullPointerException.class, () -> new IntMinHeap(null));

        IntMinHeap one = new IntMinHeap(new int[] {7});
        expect(7, one.peek(), "singleton peek");
        expect(7, one.poll(), "singleton poll");
        expectTrue(one.isEmpty(), "singleton becomes empty");

        IntMinHeap growth = new IntMinHeap();
        for (int value = 100; value >= 0; value--) growth.add(value);
        for (int value = 0; value <= 100; value++) {
            expect(value, growth.poll(), "capacity growth");
        }
    }

    private static void randomizedOperationChecks() {
        Random random = new Random(7_007);
        int operations = 0;
        for (int trial = 0; trial < 2_000; trial++) {
            IntMinHeap actual = new IntMinHeap();
            PriorityQueue<Integer> oracle = new PriorityQueue<>();
            for (int operation = 0; operation < 50; operation++) {
                if (oracle.isEmpty() || random.nextInt(100) < 62) {
                    int value = random.nextInt();
                    actual.add(value);
                    oracle.add(value);
                } else if (random.nextBoolean()) {
                    expect(oracle.remove(), actual.poll(), "random poll");
                } else {
                    expect(oracle.element(), actual.peek(), "random peek");
                }
                expect(oracle.size(), actual.size(), "random size");
                if (!oracle.isEmpty()) {
                    expect(oracle.element(), actual.peek(), "root after operation");
                }
                operations++;
            }
            while (!oracle.isEmpty()) {
                expect(oracle.remove(), actual.poll(), "final drain");
            }
            expectTrue(actual.isEmpty(), "drained heap empty");
        }
        expect(100_000, operations, "operation count");
    }

    private static void randomizedHeapifyChecks() {
        Random random = new Random(7_071);
        for (int trial = 0; trial < 10_000; trial++) {
            int[] values = new int[random.nextInt(65)];
            for (int i = 0; i < values.length; i++) values[i] = random.nextInt();
            int[] sorted = values.clone();
            Arrays.sort(sorted);
            IntMinHeap heap = new IntMinHeap(values);
            for (int expected : sorted) {
                expect(expected, heap.poll(), "heapify drain");
            }
            expectTrue(heap.isEmpty(), "heapified heap drained");
        }
    }

    private static void expect(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected
                    + ", got " + actual);
        }
    }

    private static void expectTrue(boolean value, String label) {
        if (!value) throw new AssertionError(label);
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
