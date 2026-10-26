import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Dependency-free executable evidence for the Fruit Into Baskets section. */
public final class FruitIntoBasketsContracts {
    private static int scenarios;

    private FruitIntoBasketsContracts() {}

    static int totalFruit(int[] fruits) {
        if (fruits == null) {
            throw new IllegalArgumentException("fruits must not be null");
        }

        Map<Integer, Integer> frequencies = new HashMap<>();
        int left = 0;
        int best = 0;

        for (int right = 0; right < fruits.length; right++) {
            frequencies.merge(fruits[right], 1, Integer::sum);
            while (frequencies.size() > 2) {
                int departing = fruits[left++];
                int remaining = frequencies.get(departing) - 1;
                if (remaining == 0) {
                    frequencies.remove(departing);
                } else {
                    frequencies.put(departing, remaining);
                }
            }
            best = Math.max(best, right - left + 1);
        }
        return best;
    }

    static int oracle(int[] fruits) {
        int best = 0;
        for (int start = 0; start < fruits.length; start++) {
            Map<Integer, Integer> frequencies = new HashMap<>();
            for (int end = start; end < fruits.length; end++) {
                frequencies.merge(fruits[end], 1, Integer::sum);
                if (frequencies.size() > 2) {
                    break;
                }
                best = Math.max(best, end - start + 1);
            }
        }
        return best;
    }

    private static void expect(int expected, int... fruits) {
        int[] before = fruits.clone();
        int actual = totalFruit(fruits);
        if (actual != expected) {
            throw new AssertionError("expected " + expected + " but got " + actual
                    + " for " + Arrays.toString(fruits));
        }
        if (!Arrays.equals(before, fruits)) {
            throw new AssertionError("input mutated");
        }
        scenarios++;
    }

    private static void rejectsNull() {
        try {
            totalFruit(null);
            throw new AssertionError("expected null rejection");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    public static void main(String[] args) {
        expect(3, 1, 2, 1);
        expect(3, 0, 1, 2, 2);
        expect(4, 1, 2, 3, 2, 2);
        expect(4, 1, 2, 1, 2, 3);
        expect(0);
        expect(1, 7);
        expect(5, 7, 7, 7, 7, 7);
        expect(6, 4, 9, 4, 9, 4, 9);
        expect(2, 1, 2, 3, 4);
        expect(5, 3, 3, 3, 1, 2, 1, 1, 2, 3, 3, 4);
        expect(4, -1, -2, -1, -1);
        expect(3, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE);
        expect(5, 1, 2, 3, 2, 2, 4, 4, 4);
        expect(5, 1, 1, 2, 3, 3, 2, 2);
        expect(2, 0, 0);
        rejectsNull();
        scenarios += 2; // randomized oracle agreement and nonmutation properties

        Random random = new Random(90_400_012L);
        int trials = 20_000;
        for (int trial = 0; trial < trials; trial++) {
            int[] fruits = new int[random.nextInt(31)];
            for (int i = 0; i < fruits.length; i++) {
                fruits[i] = random.nextInt(9) - 4;
            }
            int[] before = fruits.clone();
            int expected = oracle(fruits);
            int actual = totalFruit(fruits);
            if (actual != expected || !Arrays.equals(before, fruits)) {
                throw new AssertionError("trial " + trial + " failed for "
                        + Arrays.toString(fruits) + ": " + actual + " != " + expected);
            }
        }

        System.out.println("Fruit Into Baskets contracts passed: " + scenarios
                + " scenario groups + " + trials + " seeded oracle comparisons");
    }
}
