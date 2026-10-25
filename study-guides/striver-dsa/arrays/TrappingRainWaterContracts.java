import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for the Trapping Rain Water section. */
public final class TrappingRainWaterContracts {
    private static int scenarios;

    private TrappingRainWaterContracts() {}

    static long trap(int[] height) {
        if (height == null) {
            throw new IllegalArgumentException("height must not be null");
        }
        for (int value : height) {
            if (value < 0) {
                throw new IllegalArgumentException("heights must be non-negative");
            }
        }
        int left = 0;
        int right = height.length - 1;
        int leftMax = 0;
        int rightMax = 0;
        long water = 0L;

        while (left <= right) {
            if (leftMax <= rightMax) {
                leftMax = Math.max(leftMax, height[left]);
                water = Math.addExact(water, (long) leftMax - height[left]);
                left++;
            } else {
                rightMax = Math.max(rightMax, height[right]);
                water = Math.addExact(water, (long) rightMax - height[right]);
                right--;
            }
        }
        return water;
    }

    static long oracle(int[] height) {
        long water = 0L;
        for (int i = 0; i < height.length; i++) {
            int leftMax = 0;
            int rightMax = 0;
            for (int left = 0; left <= i; left++) {
                leftMax = Math.max(leftMax, height[left]);
            }
            for (int right = i; right < height.length; right++) {
                rightMax = Math.max(rightMax, height[right]);
            }
            water = Math.addExact(water,
                    (long) Math.min(leftMax, rightMax) - height[i]);
        }
        return water;
    }

    private static void expect(long expected, int... height) {
        int[] before = height.clone();
        long actual = trap(height);
        if (actual != expected) {
            throw new AssertionError("expected " + expected + " but got " + actual
                    + " for " + Arrays.toString(height));
        }
        if (!Arrays.equals(before, height)) {
            throw new AssertionError("input mutated");
        }
        scenarios++;
    }

    private static void rejects(int[] height) {
        try {
            trap(height);
            throw new AssertionError("expected invalid input rejection");
        } catch (IllegalArgumentException expected) {
            scenarios++;
        }
    }

    public static void main(String[] args) {
        expect(6L, 0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1);
        expect(9L, 4, 2, 0, 3, 2, 5);
        expect(0L);
        expect(0L, 7);
        expect(0L, 7, 7);
        expect(0L, 1, 2, 3, 4, 5);
        expect(0L, 5, 4, 3, 2, 1);
        expect(0L, 3, 3, 3, 3);
        expect(1L, 1, 0, 1);
        expect(2L, 2, 0, 2);
        expect(4L, 2, 1, 0, 1, 3);
        expect(14L, 5, 2, 1, 2, 1, 5);
        expect(4_294_967_294L, Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE);
        expect(2_147_483_647L, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
        rejects(null);
        rejects(new int[] {3, -1, 3});
        scenarios += 2; // oracle agreement and nonmutation randomized properties

        Random random = new Random(42_424_242L);
        int trials = 20_000;
        for (int trial = 0; trial < trials; trial++) {
            int[] height = new int[random.nextInt(31)];
            for (int i = 0; i < height.length; i++) {
                height[i] = random.nextInt(41);
            }
            int[] before = height.clone();
            long expected = oracle(height);
            long actual = trap(height);
            if (actual != expected || !Arrays.equals(before, height)) {
                throw new AssertionError("trial " + trial + " failed for "
                        + Arrays.toString(height) + ": " + actual + " != " + expected);
            }
        }

        System.out.println("Trapping Rain Water contracts passed: " + scenarios
                + " scenario groups + " + trials + " seeded oracle comparisons");
    }
}
