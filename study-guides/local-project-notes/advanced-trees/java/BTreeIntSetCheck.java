import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public final class BTreeIntSetCheck {
    private static long assertions;

    public static void main(String[] args) {
        sourceSequence();
        deletionCases();
        boundaryContracts();
        permutationChecks();
        randomizedOracleChecks();
        System.out.println("BTreeIntSet checks passed: " + assertions + " assertions");
    }

    private static void sourceSequence() {
        BTreeIntSet tree = new BTreeIntSet(3);
        int[] sequence = {10, 20, 5, 6, 12, 30, 7, 17, 3, 4, 2, 40, 50};
        for (int key : sequence) truth(tree.add(key), "source insert " + key);
        equal(List.of(2, 3, 4, 5, 6, 7, 10, 12, 17, 20, 30, 40, 50),
                tree.toList(), "source traversal");
        equal(2, tree.heightLevels(), "source height");
        truth(tree.contains(20), "internal key found");
        truth(!tree.contains(21), "missing key absent");
        truth(!tree.add(20), "duplicate rejected");
        tree.validate();
        assertions++;
    }

    private static void deletionCases() {
        BTreeIntSet tree = new BTreeIntSet(2);
        for (int i = 1; i <= 40; i++) tree.add(i);
        int[] removal = {6, 13, 7, 4, 2, 16, 15, 14, 1, 3, 5, 8, 9, 10,
                11, 12, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40};
        TreeSet<Integer> oracle = new TreeSet<>();
        for (int i = 1; i <= 40; i++) oracle.add(i);
        for (int key : removal) {
            equal(oracle.remove(key), tree.remove(key), "delete result " + key);
            equal(new ArrayList<>(oracle), tree.toList(), "delete traversal " + key);
            tree.validate();
            assertions++;
        }
        truth(tree.isEmpty(), "root contracts to empty leaf");
        equal(1, tree.heightLevels(), "empty height");
        truth(!tree.remove(99), "remove missing");
    }

    private static void boundaryContracts() {
        throwsType(IllegalArgumentException.class, () -> new BTreeIntSet(1));
        throwsType(IllegalArgumentException.class, () -> new BTreeIntSet(Integer.MAX_VALUE));
        BTreeIntSet tree = new BTreeIntSet(2);
        truth(tree.add(Integer.MIN_VALUE), "add minimum int");
        truth(tree.add(0), "add zero");
        truth(tree.add(Integer.MAX_VALUE), "add maximum int");
        equal(List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE), tree.toList(), "extremes order");
        truth(tree.remove(0), "remove zero");
        tree.validate();
        assertions++;
    }

    private static void permutationChecks() {
        Random random = new Random(0xB7EE_1628L);
        List<Integer> keys = new ArrayList<>();
        for (int i = -25; i <= 25; i++) keys.add(i);
        for (int trial = 0; trial < 1_000; trial++) {
            Collections.shuffle(keys, random);
            int degree = 2 + random.nextInt(6);
            BTreeIntSet tree = new BTreeIntSet(degree);
            for (int key : keys) tree.add(key);
            tree.validate();
            assertions++;
            Collections.shuffle(keys, random);
            for (int key : keys) {
                truth(tree.remove(key), "permutation remove");
                tree.validate();
                assertions++;
            }
            truth(tree.isEmpty(), "permutation empty");
        }
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x1972_B7EEL);
        for (int degree = 2; degree <= 9; degree++) {
            BTreeIntSet tree = new BTreeIntSet(degree);
            TreeSet<Integer> oracle = new TreeSet<>();
            for (int step = 0; step < 25_000; step++) {
                int key = random.nextInt(20_001) - 10_000;
                int operation = random.nextInt(3);
                if (operation == 0) equal(oracle.add(key), tree.add(key), "random add");
                else if (operation == 1) equal(oracle.remove(key), tree.remove(key), "random remove");
                else equal(oracle.contains(key), tree.contains(key), "random contains");

                if (step % 100 == 0) {
                    tree.validate();
                    assertions++;
                    equal(new ArrayList<>(oracle), tree.toList(), "random ordered traversal");
                    equal(oracle.size(), tree.size(), "random size");
                }
            }
            tree.validate();
            assertions++;
            equal(new ArrayList<>(oracle), tree.toList(), "final traversal");
        }
    }

    private static void truth(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private static void equal(Object expected, Object actual, String label) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void throwsType(Class<? extends Throwable> expected, Runnable action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("expected " + expected.getSimpleName());
        } catch (Throwable error) {
            if (!expected.isInstance(error)) throw error;
        }
    }
}
