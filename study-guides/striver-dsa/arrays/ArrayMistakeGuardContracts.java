import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Executable contracts for the "Common Array Interview Mistakes" guardrails. */
public final class ArrayMistakeGuardContracts {
    private ArrayMistakeGuardContracts() {}

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

    static final class PrefixRange {
        private final long[] prefix;

        PrefixRange(int[] nums) {
            prefix = new long[nums.length + 1];
            for (int i = 0; i < nums.length; i++) {
                prefix[i + 1] = prefix[i] + nums[i];
            }
        }

        long sum(int left, int right) {
            if (left < 0 || left > right || right >= prefix.length - 1) {
                throw new IllegalArgumentException("require 0 <= left <= right < length");
            }
            return prefix[right + 1] - prefix[left];
        }
    }

    static int lowerBound(int[] sorted, int target) {
        int left = 0;
        int right = sorted.length;
        while (left < right) {
            int middle = left + (right - left) / 2;
            if (sorted[middle] < target) {
                left = middle + 1;
            } else {
                right = middle;
            }
        }
        return left;
    }

    static void sortColors(int[] nums) {
        for (int value : nums) {
            if (value < 0 || value > 2) {
                throw new IllegalArgumentException("colors must be 0, 1, or 2");
            }
        }
        int low = 0;
        int mid = 0;
        int high = nums.length - 1;
        while (mid <= high) {
            if (nums[mid] == 0) {
                swap(nums, low++, mid++);
            } else if (nums[mid] == 1) {
                mid++;
            } else {
                swap(nums, mid, high--);
                // mid does not move: the value received from high is unclassified.
            }
        }
    }

    static int[] maxSlidingWindow(int[] nums, int width) {
        if (width < 1 || width > nums.length) {
            throw new IllegalArgumentException("require 1 <= width <= length");
        }
        int[] answer = new int[nums.length - width + 1];
        ArrayDeque<Integer> deque = new ArrayDeque<>();
        for (int right = 0; right < nums.length; right++) {
            while (!deque.isEmpty() && deque.peekFirst() <= right - width) {
                deque.removeFirst();
            }
            while (!deque.isEmpty() && nums[deque.peekLast()] <= nums[right]) {
                deque.removeLast();
            }
            deque.addLast(right);
            if (right >= width - 1) {
                answer[right - width + 1] = nums[deque.peekFirst()];
            }
        }
        return answer;
    }

    static List<List<Integer>> threeSum(int[] nums) {
        int[] sorted = Arrays.copyOf(nums, nums.length);
        Arrays.sort(sorted);
        List<List<Integer>> answer = new ArrayList<>();
        for (int anchor = 0; anchor + 2 < sorted.length; anchor++) {
            if (anchor > 0 && sorted[anchor] == sorted[anchor - 1]) {
                continue;
            }
            int left = anchor + 1;
            int right = sorted.length - 1;
            while (left < right) {
                long sum = (long) sorted[anchor] + sorted[left] + sorted[right];
                if (sum < 0) {
                    left++;
                } else if (sum > 0) {
                    right--;
                } else {
                    answer.add(List.of(sorted[anchor], sorted[left], sorted[right]));
                    int leftValue = sorted[left];
                    int rightValue = sorted[right];
                    while (left < right && sorted[left] == leftValue) left++;
                    while (left < right && sorted[right] == rightValue) right--;
                }
            }
        }
        return answer;
    }

    static long maxContainerArea(int[] height) {
        for (int value : height) {
            if (value < 0) throw new IllegalArgumentException("height must be nonnegative");
        }
        int left = 0;
        int right = height.length - 1;
        long best = 0L;
        while (left < right) {
            long area = (long) (right - left) * Math.min(height[left], height[right]);
            best = Math.max(best, area);
            if (height[left] <= height[right]) left++;
            else right--;
        }
        return best;
    }

    private static void swap(int[] values, int a, int b) {
        int temporary = values[a];
        values[a] = values[b];
        values[b] = temporary;
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

    private static long rangeOracle(int[] nums, int left, int right) {
        long sum = 0L;
        for (int i = left; i <= right; i++) sum += nums[i];
        return sum;
    }

    private static int lowerBoundOracle(int[] sorted, int target) {
        int index = 0;
        while (index < sorted.length && sorted[index] < target) index++;
        return index;
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

    private static Set<String> threeSumOracle(int[] nums) {
        Set<String> answer = new HashSet<>();
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                for (int k = j + 1; k < nums.length; k++) {
                    if ((long) nums[i] + nums[j] + nums[k] == 0L) {
                        int[] triple = {nums[i], nums[j], nums[k]};
                        Arrays.sort(triple);
                        answer.add(Arrays.toString(triple));
                    }
                }
            }
        }
        return answer;
    }

    private static Set<String> encode(List<List<Integer>> triples) {
        Set<String> encoded = new HashSet<>();
        for (List<Integer> triple : triples) {
            encoded.add(triple.toString());
        }
        if (encoded.size() != triples.size()) throw new AssertionError("duplicate triplet");
        return encoded;
    }

    private static long areaOracle(int[] height) {
        long best = 0L;
        for (int left = 0; left < height.length; left++) {
            for (int right = left + 1; right < height.length; right++) {
                best = Math.max(best, (long) (right - left) * Math.min(height[left], height[right]));
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
        group(countExactSum(new int[] {1, -1, 1}, 1) == 3);
        group(countExactSum(new int[] {0, 0, 0}, 0) == 6);
        group(countExactSum(new int[0], 0) == 0);
        group(new PrefixRange(new int[] {2, -5, 4, 7}).sum(1, 3) == 6);
        group(new PrefixRange(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}).sum(0, 1)
            == 4_294_967_294L);
        expectFailure(() -> new PrefixRange(new int[] {1}).sum(1, 1)); group(true);
        group(lowerBound(new int[] {1, 3, 3, 8}, 3) == 1);
        group(lowerBound(new int[] {1, 3, 3, 8}, 9) == 4);
        group(lowerBound(new int[0], 7) == 0);
        int[] colors = {2, 0, 2, 1, 1, 0}; sortColors(colors);
        group(Arrays.equals(colors, new int[] {0, 0, 1, 1, 2, 2}));
        int[] swappedHigh = {2, 0, 1}; sortColors(swappedHigh);
        group(Arrays.equals(swappedHigh, new int[] {0, 1, 2}));
        expectFailure(() -> sortColors(new int[] {0, 3, 1})); group(true);
        group(Arrays.equals(maxSlidingWindow(new int[] {1,3,-1,-3,5,3,6,7}, 3),
            new int[] {3,3,5,5,6,7}));
        group(Arrays.equals(maxSlidingWindow(new int[] {4,4,4}, 2), new int[] {4,4}));
        expectFailure(() -> maxSlidingWindow(new int[] {1}, 0)); group(true);
        group(encode(threeSum(new int[] {-1,0,1,2,-1,-4})).equals(
            Set.of("[-1, -1, 2]", "[-1, 0, 1]")));
        group(encode(threeSum(new int[] {0,0,0,0})).equals(Set.of("[0, 0, 0]")));
        int[] preserved = {3,-1,-2,0}; int[] snapshot = preserved.clone(); threeSum(preserved);
        group(Arrays.equals(preserved, snapshot));
        group(maxContainerArea(new int[] {Integer.MAX_VALUE, 0, Integer.MAX_VALUE})
            == 4_294_967_294L);
        group(maxContainerArea(new int[0]) == 0L);

        if (deterministicGroups != 20) throw new AssertionError("group count");

        Random random = new Random(0x18C0FFEE);
        int trials = 20_000;
        long checks = 0L;
        for (int trial = 0; trial < trials; trial++) {
            int[] signed = randomArray(random, random.nextInt(9), -5, 6);
            int[] signedBefore = signed.clone();
            long target = random.nextInt(17) - 8;
            if (countExactSum(signed, target) != exactSumOracle(signed, target)
                || !Arrays.equals(signed, signedBefore)) throw new AssertionError("exact sum");
            checks++;

            int[] ranged = randomArray(random, 1 + random.nextInt(8), -20, 21);
            int left = random.nextInt(ranged.length);
            int right = left + random.nextInt(ranged.length - left);
            if (new PrefixRange(ranged).sum(left, right) != rangeOracle(ranged, left, right))
                throw new AssertionError("prefix range");
            checks++;

            int[] ordered = randomArray(random, random.nextInt(10), -8, 9);
            Arrays.sort(ordered);
            int search = random.nextInt(21) - 10;
            if (lowerBound(ordered, search) != lowerBoundOracle(ordered, search))
                throw new AssertionError("lower bound");
            checks++;

            int[] paint = randomArray(random, random.nextInt(11), 0, 3);
            int[] sortedPaint = paint.clone(); Arrays.sort(sortedPaint); sortColors(paint);
            if (!Arrays.equals(paint, sortedPaint)) throw new AssertionError("colors");
            checks++;

            int[] window = randomArray(random, 1 + random.nextInt(8), -20, 21);
            int width = 1 + random.nextInt(window.length);
            int[] windowBefore = window.clone();
            if (!Arrays.equals(maxSlidingWindow(window, width), windowOracle(window, width))
                || !Arrays.equals(window, windowBefore)) throw new AssertionError("window");
            checks++;

            int[] triples = randomArray(random, random.nextInt(9), -7, 8);
            int[] triplesBefore = triples.clone();
            if (!encode(threeSum(triples)).equals(threeSumOracle(triples))
                || !Arrays.equals(triples, triplesBefore)) throw new AssertionError("three sum");
            checks++;

            int[] height = randomArray(random, random.nextInt(9), 0, 21);
            int[] heightBefore = height.clone();
            if (maxContainerArea(height) != areaOracle(height)
                || !Arrays.equals(height, heightBefore)) throw new AssertionError("area");
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
