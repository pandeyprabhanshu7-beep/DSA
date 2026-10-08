import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public final class BstIntSetCheck {
    private static long assertions;

    public static void main(String[] args) {
        sourceExamples();
        boundaryCases();
        permutationChecks();
        randomizedOracleChecks();
        System.out.println("PASS BstIntSetCheck assertions=" + assertions);
    }

    private static void sourceExamples() {
        BstIntSet set = new BstIntSet();
        for (int key : new int[] {50, 30, 70, 20, 40, 60, 80, 35, 75}) {
            check(set.add(key), "source insert " + key);
        }
        check(set.remove(20), "delete leaf");
        check(set.remove(80), "delete one-child node");
        check(set.remove(30), "delete two-child node");
        checkEquals(List.of(35, 40, 50, 60, 70, 75), set.toSortedList(), "source result");
        set.validate();
    }

    private static void boundaryCases() {
        BstIntSet set = new BstIntSet();
        check(!set.remove(1), "remove from empty");
        check(set.add(0), "insert root");
        check(!set.add(0), "duplicate no-op");
        check(set.add(Integer.MIN_VALUE), "minimum int");
        check(set.add(Integer.MAX_VALUE), "maximum int");
        checkEquals(List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE), set.toSortedList(), "extremes");
        check(set.remove(0), "delete root with two children");
        check(set.remove(Integer.MIN_VALUE), "delete root with one child");
        check(set.remove(Integer.MAX_VALUE), "delete final node");
        checkEquals(List.of(), set.toSortedList(), "empty again");
        set.validate();
    }

    private static void permutationChecks() {
        Random random = new Random(0xB57C0DEL);
        List<Integer> values = new ArrayList<>();
        for (int i = -63; i <= 63; i++) {
            values.add(i);
        }

        for (int trial = 0; trial < 1_000; trial++) {
            Collections.shuffle(values, random);
            BstIntSet set = new BstIntSet();
            for (int value : values) {
                check(set.add(value), "permutation insert");
            }
            set.validate();
            checkEquals(values.size(), set.size(), "permutation size");

            Collections.shuffle(values, random);
            for (int i = 0; i < values.size(); i++) {
                check(set.remove(values.get(i)), "permutation remove");
                if ((i & 15) == 0) {
                    set.validate();
                }
            }
            checkEquals(0, set.size(), "permutation empty");
        }
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x5EEDB57L);
        BstIntSet actual = new BstIntSet();
        TreeSet<Integer> expected = new TreeSet<>();

        for (int operation = 1; operation <= 200_000; operation++) {
            int key = random.nextInt(20_001) - 10_000;
            int kind = random.nextInt(3);
            if (kind == 0) {
                checkEquals(expected.add(key), actual.add(key), "oracle add");
            } else if (kind == 1) {
                checkEquals(expected.remove(key), actual.remove(key), "oracle remove");
            } else {
                checkEquals(expected.contains(key), actual.contains(key), "oracle contains");
            }

            if (operation % 1_000 == 0) {
                actual.validate();
                checkEquals(new ArrayList<>(expected), actual.toSortedList(), "oracle contents");
                checkEquals(expected.size(), actual.size(), "oracle size");
            }
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkEquals(Object expected, Object actual, String message) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
