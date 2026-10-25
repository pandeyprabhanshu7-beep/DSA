import java.util.Arrays;
import java.util.Objects;
import java.util.SplittableRandom;

final class ContainerWaterContracts {
    static long maxArea(int[] height) {
        Objects.requireNonNull(height, "height");
        if (height.length < 2) {
            throw new IllegalArgumentException("need two heights");
        }
        for (int value : height) {
            if (value < 0) {
                throw new IllegalArgumentException("negative height");
            }
        }

        int left = 0;
        int right = height.length - 1;
        long best = 0;
        while (left < right) {
            long area = (long) (right - left) * Math.min(height[left], height[right]);
            best = Math.max(best, area);
            if (height[left] <= height[right]) {
                left++;
            } else {
                right--;
            }
        }
        return best;
    }

    private static long bruteForce(int[] height) {
        long best = 0;
        for (int left = 0; left < height.length; left++) {
            for (int right = left + 1; right < height.length; right++) {
                long area = (long) (right - left) * Math.min(height[left], height[right]);
                best = Math.max(best, area);
            }
        }
        return best;
    }

    private static void expect(long expected, int... height) {
        int[] before = height.clone();
        long actual = maxArea(height);
        if (actual != expected) {
            throw new AssertionError("expected=" + expected + " actual=" + actual
                    + " input=" + Arrays.toString(height));
        }
        if (!Arrays.equals(before, height)) {
            throw new AssertionError("input mutated");
        }
    }

    private static void expectFailure(Class<? extends Throwable> type, int[] height) {
        try {
            maxArea(height);
            throw new AssertionError("expected " + type.getSimpleName());
        } catch (Throwable actual) {
            if (!type.isInstance(actual)) {
                throw new AssertionError("expected " + type.getSimpleName()
                        + " but got " + actual, actual);
            }
        }
    }

    public static void main(String[] args) {
        expect(49, 1, 8, 6, 2, 5, 4, 8, 3, 7);
        expect(1, 1, 1);
        expect(16, 4, 3, 2, 1, 4);
        expect(4, 1, 2, 4, 3);
        expect(15, 5, 0, 0, 5);
        expect(0, 0, 0);
        expect(6, 1, 2, 3, 4, 5);
        expect(6, 5, 4, 3, 2, 1);
        expect(15, 5, 5, 5, 5);
        expect(4_294_967_294L, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
        int[] preserved = {7, 1, 7};
        int[] snapshot = preserved.clone();
        maxArea(preserved);
        if (!Arrays.equals(snapshot, preserved)) throw new AssertionError("input mutated");
        expectFailure(NullPointerException.class, null);
        expectFailure(IllegalArgumentException.class, new int[] {7});
        expectFailure(IllegalArgumentException.class, new int[] {1, -1, 2});

        SplittableRandom random = new SplittableRandom(0xC0A7A1E5L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] height = new int[random.nextInt(2, 31)];
            for (int i = 0; i < height.length; i++) {
                height[i] = random.nextInt(0, 1_000_000_001);
            }
            int[] before = height.clone();
            long expected = bruteForce(height);
            long actual = maxArea(height);
            if (actual != expected || !Arrays.equals(before, height)) {
                throw new AssertionError("trial=" + trial + " expected=" + expected
                        + " actual=" + actual + " input=" + Arrays.toString(height));
            }
        }
        System.out.println("ContainerWaterContracts: 14 scenario groups and 20000 trials passed");
    }
}
