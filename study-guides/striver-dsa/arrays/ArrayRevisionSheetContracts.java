import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Four proof-shaped mechanisms for the ten-minute Arrays revision sheet. */
public final class ArrayRevisionSheetContracts {
    private ArrayRevisionSheetContracts() {}

    static long countExactSum(int[] nums, long target) {
        Map<Long, Long> frequency = new HashMap<>();
        frequency.put(0L, 1L);
        long prefix = 0L;
        long answer = 0L;
        for (int value : nums) {
            prefix += value;
            answer += frequency.getOrDefault(prefix - target, 0L);
            frequency.merge(prefix, 1L, Long::sum);
        }
        return answer;
    }

    static long minimizeLargestSplit(int[] nums, int groups) {
        if (nums.length == 0 || groups < 1 || groups > nums.length) {
            throw new IllegalArgumentException("require nonempty input and 1 <= groups <= length");
        }
        long low = 0L;
        long high = 0L;
        for (int value : nums) {
            if (value < 0) throw new IllegalArgumentException("values must be nonnegative");
            low = Math.max(low, value);
            high += value;
        }
        while (low < high) {
            long capacity = low + (high - low) / 2;
            if (groupsNeeded(nums, capacity) <= groups) high = capacity;
            else low = capacity + 1;
        }
        return low;
    }

    private static int groupsNeeded(int[] nums, long capacity) {
        int used = 1;
        long current = 0L;
        for (int value : nums) {
            if (current + value > capacity) {
                used++;
                current = value;
            } else {
                current += value;
            }
        }
        return used;
    }

    static int[] maxSlidingWindow(int[] nums, int width) {
        if (width < 1 || width > nums.length) {
            throw new IllegalArgumentException("require 1 <= width <= length");
        }
        int[] answer = new int[nums.length - width + 1];
        ArrayDeque<Integer> deque = new ArrayDeque<>();
        for (int right = 0; right < nums.length; right++) {
            while (!deque.isEmpty() && deque.peekFirst() <= right - width) deque.removeFirst();
            while (!deque.isEmpty() && nums[deque.peekLast()] <= nums[right]) deque.removeLast();
            deque.addLast(right);
            if (right >= width - 1) answer[right - width + 1] = nums[deque.peekFirst()];
        }
        return answer;
    }

    static long largestRectangle(int[] heights) {
        for (int height : heights) {
            if (height < 0) throw new IllegalArgumentException("heights must be nonnegative");
        }
        int[] startStack = new int[heights.length];
        int[] heightStack = new int[heights.length];
        int size = 0;
        long best = 0L;
        for (int index = 0; index <= heights.length; index++) {
            int current = index == heights.length ? 0 : heights[index];
            int start = index;
            while (size > 0 && heightStack[size - 1] > current) {
                size--;
                start = startStack[size];
                best = Math.max(best, (long) heightStack[size] * (index - start));
            }
            if (current > 0 && (size == 0 || heightStack[size - 1] < current)) {
                startStack[size] = start;
                heightStack[size] = current;
                size++;
            }
        }
        return best;
    }

    private static long exactSumOracle(int[] nums, long target) {
        long answer = 0L;
        for (int left = 0; left < nums.length; left++) {
            long sum = 0L;
            for (int right = left; right < nums.length; right++) {
                sum += nums[right];
                if (sum == target) answer++;
            }
        }
        return answer;
    }

    private static long splitOracle(int[] nums, int groups) {
        int n = nums.length;
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + nums[i];
        long[][] dp = new long[groups + 1][n + 1];
        for (long[] row : dp) Arrays.fill(row, Long.MAX_VALUE);
        dp[0][0] = 0L;
        for (int used = 1; used <= groups; used++) {
            for (int end = used; end <= n; end++) {
                for (int cut = used - 1; cut < end; cut++) {
                    if (dp[used - 1][cut] == Long.MAX_VALUE) continue;
                    long candidate = Math.max(dp[used - 1][cut], prefix[end] - prefix[cut]);
                    dp[used][end] = Math.min(dp[used][end], candidate);
                }
            }
        }
        return dp[groups][n];
    }

    private static int[] windowOracle(int[] nums, int width) {
        int[] answer = new int[nums.length - width + 1];
        for (int left = 0; left + width <= nums.length; left++) {
            int maximum = Integer.MIN_VALUE;
            for (int i = left; i < left + width; i++) maximum = Math.max(maximum, nums[i]);
            answer[left] = maximum;
        }
        return answer;
    }

    private static long rectangleOracle(int[] heights) {
        long best = 0L;
        for (int left = 0; left < heights.length; left++) {
            int minimum = Integer.MAX_VALUE;
            for (int right = left; right < heights.length; right++) {
                minimum = Math.min(minimum, heights[right]);
                best = Math.max(best, (long) minimum * (right - left + 1));
            }
        }
        return best;
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static int deterministicGroups;

    private static void group(boolean condition) {
        deterministicGroups++;
        if (!condition) throw new AssertionError("deterministic group " + deterministicGroups);
    }

    public static void main(String[] args) {
        group(countExactSum(new int[] {1, 1, 1}, 2) == 2);
        group(countExactSum(new int[] {1, -1, 1}, 1) == 3);
        group(countExactSum(new int[] {0, 0, 0}, 0) == 6);
        group(countExactSum(new int[0], 0) == 0);

        group(minimizeLargestSplit(new int[] {7,2,5,10,8}, 2) == 18);
        group(minimizeLargestSplit(new int[] {1,2,3,4,5}, 2) == 9);
        group(minimizeLargestSplit(new int[] {5,1,2}, 1) == 8);
        group(minimizeLargestSplit(new int[] {5,1,2}, 3) == 5);
        group(minimizeLargestSplit(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 1)
            == 4_294_967_294L);
        expectFailure(() -> minimizeLargestSplit(new int[] {1,-1}, 1)); group(true);

        group(Arrays.equals(maxSlidingWindow(new int[] {1,3,-1,-3,5,3,6,7}, 3),
            new int[] {3,3,5,5,6,7}));
        group(Arrays.equals(maxSlidingWindow(new int[] {4,4,3}, 2), new int[] {4,4}));
        group(Arrays.equals(maxSlidingWindow(new int[] {-4}, 1), new int[] {-4}));
        int[] windowInput = {2,1,2}; int[] windowCopy = windowInput.clone();
        maxSlidingWindow(windowInput, 2); group(Arrays.equals(windowInput, windowCopy));
        expectFailure(() -> maxSlidingWindow(new int[] {1}, 0)); group(true);

        group(largestRectangle(new int[] {2,1,5,6,2,3}) == 10);
        group(largestRectangle(new int[] {2,4}) == 4);
        group(largestRectangle(new int[] {2,2,2}) == 6);
        group(largestRectangle(new int[0]) == 0);
        group(largestRectangle(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE})
            == 4_294_967_294L);

        if (deterministicGroups != 20) throw new AssertionError("group count");

        Random random = new Random(0x19A11CE);
        int trials = 20_000;
        long checks = 0L;
        for (int trial = 0; trial < trials; trial++) {
            int[] signed = randomArray(random, random.nextInt(9), -5, 6);
            int[] signedBefore = signed.clone();
            long target = random.nextInt(17) - 8;
            if (countExactSum(signed, target) != exactSumOracle(signed, target)
                || !Arrays.equals(signed, signedBefore)) throw new AssertionError("exact sum");
            checks++;

            int[] split = randomArray(random, 1 + random.nextInt(8), 0, 21);
            int groups = 1 + random.nextInt(split.length);
            int[] splitBefore = split.clone();
            if (minimizeLargestSplit(split, groups) != splitOracle(split, groups)
                || !Arrays.equals(split, splitBefore)) throw new AssertionError("split");
            checks++;

            int[] window = randomArray(random, 1 + random.nextInt(8), -20, 21);
            int width = 1 + random.nextInt(window.length);
            int[] windowBefore = window.clone();
            if (!Arrays.equals(maxSlidingWindow(window, width), windowOracle(window, width))
                || !Arrays.equals(window, windowBefore)) throw new AssertionError("window");
            checks++;

            int[] heights = randomArray(random, random.nextInt(9), 0, 21);
            int[] heightsBefore = heights.clone();
            if (largestRectangle(heights) != rectangleOracle(heights)
                || !Arrays.equals(heights, heightsBefore)) throw new AssertionError("rectangle");
            checks++;
        }
        System.out.printf("PASS groups=%d trials=%d oracle/preservation checks=%d%n",
            deterministicGroups, trials, checks);
    }

    private static int[] randomArray(Random random, int length, int origin, int bound) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = origin + random.nextInt(bound - origin);
        return values;
    }
}
