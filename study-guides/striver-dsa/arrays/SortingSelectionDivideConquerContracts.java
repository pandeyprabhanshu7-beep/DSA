import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class SortingSelectionDivideConquerContracts {
    private static int groups;

    static void mergeSortedIntoFirst(int[] first, int m, int[] second, int n) {
        if (first == null || second == null || m < 0 || n < 0
                || m + n != first.length || n != second.length) {
            throw new IllegalArgumentException("invalid merge shape");
        }
        requireSorted(first, m);
        requireSorted(second, n);
        int left = m - 1, right = n - 1, write = m + n - 1;
        while (right >= 0) {
            if (left >= 0 && first[left] > second[right]) first[write--] = first[left--];
            else first[write--] = second[right--];
        }
    }

    static String largestNumber(int[] values) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("values required");
        String[] parts = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] < 0) throw new IllegalArgumentException("non-negative values required");
            parts[i] = Integer.toString(values[i]);
        }
        Arrays.sort(parts, (a, b) -> (b + a).compareTo(a + b));
        if (parts[0].equals("0")) return "0";
        return String.join("", parts);
    }

    static long maximumGap(int[] values) {
        if (values == null) throw new IllegalArgumentException("values required");
        if (values.length < 2) return 0;
        int minimum = values[0], maximum = values[0];
        for (int value : values) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        long range = (long) maximum - minimum;
        if (range == 0) return 0;
        long width = Math.max(1L, (range + values.length - 2L) / (values.length - 1L));
        int bucketCount = (int) (range / width) + 1;
        long[] bucketMinimum = new long[bucketCount];
        long[] bucketMaximum = new long[bucketCount];
        boolean[] used = new boolean[bucketCount];
        Arrays.fill(bucketMinimum, Long.MAX_VALUE);
        Arrays.fill(bucketMaximum, Long.MIN_VALUE);
        for (int value : values) {
            int bucket = (int) (((long) value - minimum) / width);
            used[bucket] = true;
            bucketMinimum[bucket] = Math.min(bucketMinimum[bucket], value);
            bucketMaximum[bucket] = Math.max(bucketMaximum[bucket], value);
        }
        long answer = 0, previousMaximum = 0;
        boolean havePrevious = false;
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            if (!used[bucket]) continue;
            if (havePrevious) answer = Math.max(answer, bucketMinimum[bucket] - previousMaximum);
            previousMaximum = bucketMaximum[bucket];
            havePrevious = true;
        }
        return answer;
    }

    static int[] topKFrequent(int[] values, int k) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("values required");
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int value : values) frequency.merge(value, 1, Integer::sum);
        if (k < 1 || k > frequency.size()) throw new IllegalArgumentException("invalid k");
        @SuppressWarnings("unchecked")
        List<Integer>[] buckets = new List[values.length + 1];
        for (var entry : frequency.entrySet()) {
            int count = entry.getValue();
            if (buckets[count] == null) buckets[count] = new ArrayList<>();
            buckets[count].add(entry.getKey());
        }
        int[] answer = new int[k];
        int write = 0;
        for (int count = buckets.length - 1; count >= 1 && write < k; count--) {
            if (buckets[count] == null) continue;
            buckets[count].sort(Integer::compare);
            for (int value : buckets[count]) {
                answer[write++] = value;
                if (write == k) break;
            }
        }
        return answer;
    }

    static long countReversePairs(int[] values) {
        if (values == null) throw new IllegalArgumentException("values required");
        int[] working = values.clone();
        return countReversePairs(working, new int[working.length], 0, working.length);
    }

    private static long countReversePairs(int[] values, int[] buffer, int from, int to) {
        if (to - from < 2) return 0;
        int middle = (from + to) >>> 1;
        long count = countReversePairs(values, buffer, from, middle)
                + countReversePairs(values, buffer, middle, to);
        int right = middle;
        for (int left = from; left < middle; left++) {
            while (right < to && (long) values[left] > 2L * values[right]) right++;
            count += right - middle;
        }
        int left = from;
        right = middle;
        int write = from;
        while (left < middle || right < to) {
            if (right == to || (left < middle && values[left] <= values[right])) {
                buffer[write++] = values[left++];
            } else {
                buffer[write++] = values[right++];
            }
        }
        System.arraycopy(buffer, from, values, from, to - from);
        return count;
    }

    static int[] countSmallerAfterSelf(int[] values) {
        if (values == null) throw new IllegalArgumentException("values required");
        Indexed[] indexed = new Indexed[values.length];
        for (int i = 0; i < values.length; i++) indexed[i] = new Indexed(values[i], i);
        int[] answer = new int[values.length];
        mergeAndCount(indexed, new Indexed[indexed.length], answer, 0, indexed.length);
        return answer;
    }

    private static void mergeAndCount(Indexed[] values, Indexed[] buffer, int[] answer,
                                      int from, int to) {
        if (to - from < 2) return;
        int middle = (from + to) >>> 1;
        mergeAndCount(values, buffer, answer, from, middle);
        mergeAndCount(values, buffer, answer, middle, to);
        int left = from, right = middle, write = from, movedRight = 0;
        while (left < middle || right < to) {
            if (right == to || (left < middle && values[left].value <= values[right].value)) {
                answer[values[left].index] += movedRight;
                buffer[write++] = values[left++];
            } else {
                buffer[write++] = values[right++];
                movedRight++;
            }
        }
        System.arraycopy(buffer, from, values, from, to - from);
    }

    static void wiggleSort(int[] values) {
        if (values == null) throw new IllegalArgumentException("values required");
        if (values.length < 2) return;
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int median = sorted[sorted.length / 2];
        int smaller = 0, scan = 0, larger = values.length - 1;
        while (scan <= larger) {
            int mappedScan = virtualIndex(scan, values.length);
            if (values[mappedScan] > median) {
                swap(values, virtualIndex(smaller++, values.length), mappedScan);
                scan++;
            } else if (values[mappedScan] < median) {
                swap(values, mappedScan, virtualIndex(larger--, values.length));
            } else {
                scan++;
            }
        }
    }

    private static int virtualIndex(int index, int length) {
        return (1 + 2 * index) % (length | 1);
    }

    private static void requireSorted(int[] values, int length) {
        for (int i = 1; i < length; i++) {
            if (values[i - 1] > values[i]) throw new IllegalArgumentException("sorted prefix required");
        }
    }

    private static String bruteLargestNumber(int[] values) {
        boolean[] used = new boolean[values.length];
        String[] best = {""};
        permuteLargest(values, used, new StringBuilder(), 0, best);
        return normalizeNumber(best[0]);
    }

    private static void permuteLargest(int[] values, boolean[] used, StringBuilder candidate,
                                       int depth, String[] best) {
        if (depth == values.length) {
            String value = candidate.toString();
            if (value.compareTo(best[0]) > 0) best[0] = value;
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < values.length; i++) {
            if (used[i] || !seen.add(values[i])) continue;
            used[i] = true;
            int before = candidate.length();
            candidate.append(values[i]);
            permuteLargest(values, used, candidate, depth + 1, best);
            candidate.setLength(before);
            used[i] = false;
        }
    }

    private static String normalizeNumber(String value) {
        int first = 0;
        while (first < value.length() && value.charAt(first) == '0') first++;
        return first == value.length() ? "0" : value.substring(first);
    }

    private static long bruteMaximumGap(int[] values) {
        if (values.length < 2) return 0;
        int[] copy = values.clone();
        Arrays.sort(copy);
        long answer = 0;
        for (int i = 1; i < copy.length; i++) answer = Math.max(answer, (long) copy[i] - copy[i - 1]);
        return answer;
    }

    private static long bruteReversePairs(int[] values) {
        long answer = 0;
        for (int i = 0; i < values.length; i++)
            for (int j = i + 1; j < values.length; j++)
                if ((long) values[i] > 2L * values[j]) answer++;
        return answer;
    }

    private static int[] bruteCountSmaller(int[] values) {
        int[] answer = new int[values.length];
        for (int i = 0; i < values.length; i++)
            for (int j = i + 1; j < values.length; j++)
                if (values[j] < values[i]) answer[i]++;
        return answer;
    }

    private static boolean findWiggle(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        return findWiggle(sorted, new boolean[sorted.length], new int[sorted.length], 0);
    }

    private static boolean findWiggle(int[] sorted, boolean[] used, int[] arrangement, int at) {
        if (at == sorted.length) return true;
        for (int i = 0; i < sorted.length; i++) {
            if (used[i] || (i > 0 && sorted[i] == sorted[i - 1] && !used[i - 1])) continue;
            if (at > 0) {
                boolean valid = (at % 2 == 1) ? arrangement[at - 1] < sorted[i]
                        : arrangement[at - 1] > sorted[i];
                if (!valid) continue;
            }
            used[i] = true;
            arrangement[at] = sorted[i];
            if (findWiggle(sorted, used, arrangement, at + 1)) return true;
            used[i] = false;
        }
        return false;
    }

    private static boolean isStrictWiggle(int[] values) {
        for (int i = 1; i < values.length; i++) {
            if (i % 2 == 1 && values[i - 1] >= values[i]) return false;
            if (i % 2 == 0 && values[i - 1] <= values[i]) return false;
        }
        return true;
    }

    private static void swap(int[] values, int first, int second) {
        int temporary = values[first];
        values[first] = values[second];
        values[second] = temporary;
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("contract failed");
        groups++;
    }

    private record Indexed(int value, int index) {}

    public static void main(String[] args) {
        int[] merged = {1,2,3,0,0,0};
        mergeSortedIntoFirst(merged, 3, new int[]{2,5,6}, 3);
        check(Arrays.equals(merged, new int[]{1,2,2,3,5,6}));
        int[] emptyFirst = {0};
        mergeSortedIntoFirst(emptyFirst, 0, new int[]{1}, 1);
        check(Arrays.equals(emptyFirst, new int[]{1}));
        int[] emptySecond = {1};
        mergeSortedIntoFirst(emptySecond, 1, new int[]{}, 0);
        check(Arrays.equals(emptySecond, new int[]{1}));
        check(largestNumber(new int[]{3,30,34,5,9}).equals("9534330"));
        check(largestNumber(new int[]{0,0}).equals("0"));
        check(largestNumber(new int[]{10,2}).equals("210"));
        check(maximumGap(new int[]{3,6,9,1}) == 3);
        check(maximumGap(new int[]{10}) == 0);
        check(maximumGap(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) == 4_294_967_295L);
        check(Set.of(1,2).equals(toSet(topKFrequent(new int[]{1,1,1,2,2,3}, 2))));
        check(Arrays.equals(topKFrequent(new int[]{7}, 1), new int[]{7}));
        check(countReversePairs(new int[]{1,3,2,3,1}) == 2);
        check(countReversePairs(new int[]{2,4,3,5,1}) == 3);
        check(countReversePairs(new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE}) == 1);
        check(Arrays.equals(countSmallerAfterSelf(new int[]{5,2,6,1}), new int[]{2,1,1,0}));
        check(Arrays.equals(countSmallerAfterSelf(new int[]{-1,-1}), new int[]{0,0}));
        int[] wiggleOne = {1,5,1,1,6,4};
        wiggleSort(wiggleOne);
        check(isStrictWiggle(wiggleOne));
        int[] wiggleTwo = {1,3,2,2,3,1};
        wiggleSort(wiggleTwo);
        check(isStrictWiggle(wiggleTwo));
        int[] singleton = {9};
        wiggleSort(singleton);
        check(Arrays.equals(singleton, new int[]{9}));
        int[] preserved = {5,2,6,1};
        int[] snapshot = preserved.clone();
        largestNumber(preserved); maximumGap(preserved); countReversePairs(preserved); countSmallerAfterSelf(preserved);
        check(Arrays.equals(preserved, snapshot));

        Random random = new Random(0x50A7D1AEL);
        for (int trial = 0; trial < 20_000; trial++) {
            int leftLength = random.nextInt(7), rightLength = random.nextInt(7);
            int[] left = new int[leftLength], right = new int[rightLength];
            for (int i = 0; i < leftLength; i++) left[i] = random.nextInt(21) - 10;
            for (int i = 0; i < rightLength; i++) right[i] = random.nextInt(21) - 10;
            Arrays.sort(left); Arrays.sort(right);
            int[] destination = Arrays.copyOf(left, leftLength + rightLength);
            int[] mergedOracle = new int[leftLength + rightLength];
            System.arraycopy(left, 0, mergedOracle, 0, leftLength);
            System.arraycopy(right, 0, mergedOracle, leftLength, rightLength);
            Arrays.sort(mergedOracle);
            mergeSortedIntoFirst(destination, leftLength, right, rightLength);
            assert Arrays.equals(destination, mergedOracle);

            int n = 1 + random.nextInt(6);
            int[] values = new int[n];
            for (int i = 0; i < n; i++) values[i] = random.nextInt(100);
            assert largestNumber(values).equals(bruteLargestNumber(values));
            assert maximumGap(values) == bruteMaximumGap(values);

            int[] signed = new int[n];
            for (int i = 0; i < n; i++) signed[i] = random.nextInt(31) - 15;
            assert countReversePairs(signed) == bruteReversePairs(signed);
            assert Arrays.equals(countSmallerAfterSelf(signed), bruteCountSmaller(signed));

            int[] candidate;
            do {
                candidate = new int[1 + random.nextInt(7)];
                for (int i = 0; i < candidate.length; i++) candidate[i] = random.nextInt(5);
            } while (!findWiggle(candidate));
            int[] multiset = candidate.clone();
            Arrays.sort(multiset);
            wiggleSort(candidate);
            int[] after = candidate.clone();
            Arrays.sort(after);
            assert Arrays.equals(multiset, after) && isStrictWiggle(candidate);
        }
        System.out.println("SortingSelectionDivideConquerContracts: 20 deterministic groups + 20000 seeded trials; 120000 oracle/preservation checks passed.");
    }

    private static Set<Integer> toSet(int[] values) {
        Set<Integer> result = new HashSet<>();
        for (int value : values) result.add(value);
        return result;
    }
}
