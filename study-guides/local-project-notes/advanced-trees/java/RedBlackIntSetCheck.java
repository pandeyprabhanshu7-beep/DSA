import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public final class RedBlackIntSetCheck {
    private static long assertions;

    public static void main(String[] args) {
        deterministicCases();
        permutationCases();
        randomizedOracle();
        System.out.println("RedBlackIntSetCheck passed " + assertions + " assertions");
    }

    private static void deterministicCases() {
        RedBlackIntSet set = new RedBlackIntSet();
        eq(false, set.remove(1));
        int[] keys = {10, 20, 30, 15, 25, 5, 1};
        for (int key : keys) {
            eq(true, set.add(key));
            set.validate();
        }
        eq(List.of(1, 5, 10, 15, 20, 25, 30), set.toList());
        eq(false, set.add(15));
        eq(false, set.remove(99));
        eq(true, set.add(Integer.MIN_VALUE));
        eq(true, set.add(Integer.MAX_VALUE));
        set.validate();
        int[] order = {10, 1, 30, 20, 15, 25, 5, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int key : order) {
            eq(true, set.remove(key));
            set.validate();
        }
        eq(true, set.isEmpty());
        eq(0, set.heightLevels());
    }

    private static void permutationCases() {
        Random random = new Random(0x5EEDBEEFL);
        List<Integer> keys = new ArrayList<>();
        for (int i = -63; i <= 63; i++) keys.add(i);
        for (int round = 0; round < 1_000; round++) {
            Collections.shuffle(keys, random);
            RedBlackIntSet set = new RedBlackIntSet();
            for (int key : keys) eq(true, set.add(key));
            set.validate();
            eq(new TreeSet<>(keys).stream().toList(), set.toList());
            Collections.shuffle(keys, random);
            for (int key : keys) {
                eq(true, set.remove(key));
                set.validate();
            }
            eq(true, set.isEmpty());
        }
    }

    private static void randomizedOracle() {
        Random random = new Random(0xC0FFEE42L);
        RedBlackIntSet actual = new RedBlackIntSet();
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
