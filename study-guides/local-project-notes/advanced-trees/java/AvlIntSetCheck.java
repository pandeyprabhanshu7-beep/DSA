import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public final class AvlIntSetCheck {
    private static long assertions;

    public static void main(String[] args) {
        rotationAndBoundaryCases();
        deletionPropagationCase();
        permutationCases();
        randomizedOracle();
        System.out.println("AvlIntSetCheck passed " + assertions + " assertions");
    }

    private static void rotationAndBoundaryCases() {
        for (int[] order : new int[][] {
                {30, 20, 10}, // LL
                {10, 20, 30}, // RR
                {30, 10, 20}, // LR
                {10, 30, 20}  // RL
        }) {
            AvlIntSet set = new AvlIntSet();
            for (int key : order) eq(true, set.add(key));
            set.validate();
            eq(List.of(10, 20, 30), set.toList());
            eq(2, set.heightLevels());
            eq(false, set.add(20));
            eq(false, set.remove(99));
        }

        AvlIntSet extremes = new AvlIntSet();
        eq(false, extremes.remove(0));
        eq(true, extremes.add(Integer.MIN_VALUE));
        eq(true, extremes.add(0));
        eq(true, extremes.add(Integer.MAX_VALUE));
        extremes.validate();
        eq(List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE), extremes.toList());
    }

    private static void deletionPropagationCase() {
        AvlIntSet set = new AvlIntSet();
        int[] keys = {9, 5, 10, 0, 6, 11, -1, 1, 2};
        for (int key : keys) eq(true, set.add(key));
        set.validate();
        eq(true, set.remove(10));
        set.validate();
        eq(List.of(-1, 0, 1, 2, 5, 6, 9, 11), set.toList());
        for (int key : new int[] {-1, 0, 1, 2, 5, 6, 9, 11}) {
            eq(true, set.remove(key));
            set.validate();
        }
        eq(true, set.isEmpty());
        eq(0, set.heightLevels());
    }

    private static void permutationCases() {
        Random random = new Random(0xA71BEEFL);
        List<Integer> keys = new ArrayList<>();
        for (int i = -63; i <= 63; i++) keys.add(i);
        for (int round = 0; round < 1_000; round++) {
            Collections.shuffle(keys, random);
            AvlIntSet set = new AvlIntSet();
            for (int key : keys) eq(true, set.add(key));
            set.validate();
            eq(new ArrayList<>(new TreeSet<>(keys)), set.toList());
            Collections.shuffle(keys, random);
            for (int key : keys) {
                eq(true, set.remove(key));
                set.validate();
            }
            eq(true, set.isEmpty());
        }
    }

    private static void randomizedOracle() {
        Random random = new Random(0xC0DEC0DEL);
        AvlIntSet actual = new AvlIntSet();
        TreeSet<Integer> expected = new TreeSet<>();
        for (int step = 1; step <= 200_000; step++) {
            int key = random.nextInt(20_001) - 10_000;
            int operation = random.nextInt(3);
            if (operation == 0) eq(expected.add(key), actual.add(key));
            else if (operation == 1) eq(expected.remove(key), actual.remove(key));
            else eq(expected.contains(key), actual.contains(key));
            eq(expected.size(), actual.size());
            if ((step & 127) == 0) {
                actual.validate();
                eq(new ArrayList<>(expected), actual.toList());
            }
        }
        actual.validate();
        eq(new ArrayList<>(expected), actual.toList());
    }

    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }
}
