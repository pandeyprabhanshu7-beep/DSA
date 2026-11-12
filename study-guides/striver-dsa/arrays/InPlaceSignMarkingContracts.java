import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class InPlaceSignMarkingContracts {
    private static final long SEED = 0x51A6_4D41_22L;

    private InPlaceSignMarkingContracts() {}

    /**
     * Returns duplicate values in second-occurrence order and restores the
     * caller's positive [1,n] input before returning.
     */
    static List<Integer> findDuplicatesRestoring(int[] values) {
        validate(values);
        try {
            return findDuplicatesInPlaceCore(values);
        } finally {
            for (int i = 0; i < values.length; i++) values[i] = Math.abs(values[i]);
        }
    }

    static List<Integer> findDuplicatesInPlaceCore(int[] values) {
        List<Integer> duplicates = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            int value = Math.abs(values[i]);
            int markerIndex = value - 1;
            if (values[markerIndex] < 0) {
                duplicates.add(value);
            } else {
                values[markerIndex] = -values[markerIndex];
            }
        }
        return duplicates;
    }

    private static void validate(int[] values) {
        if (values == null) throw new IllegalArgumentException("values must be non-null");
        int[] frequency = new int[values.length + 1];
        for (int value : values) {
            if (value < 1 || value > values.length) {
                throw new IllegalArgumentException("every value must be in [1,n]");
            }
            if (++frequency[value] > 2) {
                throw new IllegalArgumentException("every value may appear at most twice");
            }
        }
    }

    private static List<Integer> frequencyOracle(int[] values) {
        int[] frequency = new int[values.length + 1];
        List<Integer> answer = new ArrayList<>();
        for (int value : values) {
            if (++frequency[value] == 2) answer.add(value);
        }
        return answer;
    }

    private static void expect(List<Integer> expected, List<Integer> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectInvalid(int[] values, String label) {
        int[] before = values == null ? null : values.clone();
        try {
            findDuplicatesRestoring(values);
            throw new AssertionError(label + " should have failed");
        } catch (IllegalArgumentException expected) {
            if (values != null && !Arrays.equals(before, values)) {
                throw new AssertionError(label + " mutated before rejection");
            }
        }
    }

    private static int deterministicChecks() {
        int groups = 0;
        expect(List.of(2, 3), findDuplicatesRestoring(new int[] {4,3,2,7,8,2,3,1}), "official one"); groups++;
        expect(List.of(1), findDuplicatesRestoring(new int[] {1,1,2}), "official two"); groups++;
        expect(List.of(), findDuplicatesRestoring(new int[] {1}), "official three"); groups++;
        expect(List.of(), findDuplicatesRestoring(new int[0]), "empty reusable input"); groups++;
        expect(List.of(2, 3), findDuplicatesRestoring(new int[] {2,2,1,3,3}), "two duplicates"); groups++;
        expect(List.of(), findDuplicatesRestoring(new int[] {1,2,3,4}), "all unique"); groups++;
        expect(List.of(4), findDuplicatesRestoring(new int[] {4,1,4,2}), "upper boundary"); groups++;
        expect(List.of(1), findDuplicatesRestoring(new int[] {2,1,1}), "lower boundary"); groups++;
        int[] preserved = {4,3,2,7,8,2,3,1};
        int[] before = preserved.clone();
        findDuplicatesRestoring(preserved);
        if (!Arrays.equals(before, preserved)) throw new AssertionError("wrapper did not restore"); groups++;
        int[] core = {1,1,2};
        expect(List.of(1), findDuplicatesInPlaceCore(core), "core answer");
        if (core[0] >= 0) throw new AssertionError("core did not mark"); groups++;
        expectInvalid(null, "null"); groups++;
        expectInvalid(new int[] {0}, "zero"); groups++;
        expectInvalid(new int[] {-1}, "negative"); groups++;
        expectInvalid(new int[] {2}, "above n"); groups++;
        expectInvalid(new int[] {1,1,1}, "three occurrences"); groups++;
        expectInvalid(new int[] {Integer.MIN_VALUE}, "min value"); groups++;
        expectInvalid(new int[] {Integer.MAX_VALUE}, "max value"); groups++;
        int[] secondOrder = {3,1,3,2,1};
        expect(List.of(3,1), findDuplicatesRestoring(secondOrder), "second occurrence order"); groups++;
        int[] reverseOrder = {1,3,1,2,3};
        expect(List.of(1,3), findDuplicatesRestoring(reverseOrder), "order may differ"); groups++;
        int[] legal = {5,4,3,2,1};
        expect(frequencyOracle(legal), findDuplicatesRestoring(legal), "oracle parity"); groups++;
        return groups;
    }

    public static void main(String[] args) {
        int groups = deterministicChecks();
        if (groups != 20) throw new AssertionError("expected 20 deterministic groups");
        Random random = new Random(SEED);
        int trials = 20_000;
        int checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int n = 1 + random.nextInt(80);
            int[] values = new int[n];
            for (int i = 0; i < n; i++) values[i] = i + 1;
            for (int i = n - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int t = values[i]; values[i] = values[j]; values[j] = t;
            }
            int pairs = random.nextInt(n / 2 + 1);
            for (int i = 0; i < pairs; i++) values[n - 1 - i] = values[i];
            for (int i = n - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int t = values[i]; values[i] = values[j]; values[j] = t;
            }

            int[] before = values.clone();
            List<Integer> expected = frequencyOracle(values);
            List<Integer> actual = findDuplicatesRestoring(values);
            expect(expected, actual, "oracle trial " + trial); checks++;
            if (!Arrays.equals(before, values)) throw new AssertionError("restore trial " + trial);
            checks++;
            boolean[] emitted = new boolean[n + 1];
            for (int value : actual) {
                if (emitted[value]) throw new AssertionError("duplicate output trial " + trial);
                emitted[value] = true;
                if (java.util.Collections.frequency(expected, value) != 1) {
                    throw new AssertionError("unexpected output trial " + trial);
                }
            }
            checks++;
            int[] reversed = before.clone();
            for (int l = 0, r = n - 1; l < r; l++, r--) {
                int t = reversed[l]; reversed[l] = reversed[r]; reversed[r] = t;
            }
            int[] reversedBefore = reversed.clone();
            List<Integer> reverseAnswer = findDuplicatesRestoring(reversed);
            int[] sortedA = actual.stream().mapToInt(Integer::intValue).sorted().toArray();
            int[] sortedB = reverseAnswer.stream().mapToInt(Integer::intValue).sorted().toArray();
            if (!Arrays.equals(sortedA, sortedB)) throw new AssertionError("reversal trial " + trial);
            checks++;
            if (!Arrays.equals(before, values) || !Arrays.equals(reversedBefore, reversed)) {
                throw new AssertionError("preservation trial " + trial);
            }
            checks++;
        }
        System.out.println("InPlaceSignMarkingContracts: " + groups + " scenario groups + "
                + trials + " seeded frequency-oracle trials passed; checks=" + checks);
    }
}
