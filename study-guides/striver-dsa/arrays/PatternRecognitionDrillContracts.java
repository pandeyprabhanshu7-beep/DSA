import java.util.*;

/** Executable evidence for selecting an array pattern from explicit problem signals. */
public class PatternRecognitionDrillContracts {
    enum Signal {
        SORTED, TARGET_PAIR, CONSTANT_SPACE,
        EXACT_SUM_COUNT, NEGATIVES_POSSIBLE,
        SMALLEST_POSITIVE_MISSING, CONSTANT_EXTRA,
        FIXED_WINDOW_MAX,
        MINIMIZE_RATE, MONOTONE_FEASIBILITY,
        THREE_CATEGORIES, IN_PLACE,
        CIRCULAR_CONTIGUOUS_MAX,
        N_PLUS_ONE_VALUES_ONE_TO_N, NO_MUTATION,
        OVERLAPPING_INTERVALS,
        MANY_OFFLINE_RANGE_ADDS,
        NEXT_GREATER,
        COUNT_INVERSIONS,
        KTH_STATISTIC, AVERAGE_LINEAR_REQUESTED,
        LONGEST_CONTIGUOUS_RANGE, AT_MOST_K_VIOLATIONS,
        TWO_SORTED_ARRAYS, MEDIAN, LOG_TIME
    }

    enum Pattern {
        TWO_POINTERS,
        PREFIX_SUM_FREQUENCY,
        INDEX_PLACEMENT,
        MONOTONIC_DEQUE,
        BINARY_SEARCH_ON_ANSWER,
        DUTCH_NATIONAL_FLAG,
        CIRCULAR_KADANE,
        FLOYD_CYCLE_DETECTION,
        SORT_AND_MERGE,
        DIFFERENCE_ARRAY,
        MONOTONIC_STACK,
        MERGE_SORT_COUNTING,
        QUICKSELECT,
        SLIDING_WINDOW_BUDGET,
        BINARY_SEARCH_PARTITION
    }

    private static final Map<Pattern, EnumSet<Signal>> RULES = rules();
    private static final Random RANDOM = new Random(0x5041545445524E17L);
    private static int groups;
    private static long trials;

    public static void main(String[] args) {
        deterministic();
        randomized(20_000);
        System.out.printf(
                "Pattern recognition contracts: %d groups, %,d seeded trials, %,d rule/preservation checks%n",
                groups, trials, trials * (Pattern.values().length + 1L));
    }

    /** Returns every compatible pattern; ambiguity is evidence that more requirements are needed. */
    static EnumSet<Pattern> recommend(Set<Signal> signals) {
        Objects.requireNonNull(signals);
        EnumSet<Pattern> answer = EnumSet.noneOf(Pattern.class);
        if (has(signals, Signal.SORTED, Signal.TARGET_PAIR, Signal.CONSTANT_SPACE))
            answer.add(Pattern.TWO_POINTERS);
        if (has(signals, Signal.EXACT_SUM_COUNT, Signal.NEGATIVES_POSSIBLE))
            answer.add(Pattern.PREFIX_SUM_FREQUENCY);
        if (has(signals, Signal.SMALLEST_POSITIVE_MISSING, Signal.CONSTANT_EXTRA))
            answer.add(Pattern.INDEX_PLACEMENT);
        if (has(signals, Signal.FIXED_WINDOW_MAX))
            answer.add(Pattern.MONOTONIC_DEQUE);
        if (has(signals, Signal.MINIMIZE_RATE, Signal.MONOTONE_FEASIBILITY))
            answer.add(Pattern.BINARY_SEARCH_ON_ANSWER);
        if (has(signals, Signal.THREE_CATEGORIES, Signal.IN_PLACE))
            answer.add(Pattern.DUTCH_NATIONAL_FLAG);
        if (has(signals, Signal.CIRCULAR_CONTIGUOUS_MAX))
            answer.add(Pattern.CIRCULAR_KADANE);
        if (has(signals, Signal.N_PLUS_ONE_VALUES_ONE_TO_N, Signal.NO_MUTATION))
            answer.add(Pattern.FLOYD_CYCLE_DETECTION);
        if (has(signals, Signal.OVERLAPPING_INTERVALS))
            answer.add(Pattern.SORT_AND_MERGE);
        if (has(signals, Signal.MANY_OFFLINE_RANGE_ADDS))
            answer.add(Pattern.DIFFERENCE_ARRAY);
        if (has(signals, Signal.NEXT_GREATER))
            answer.add(Pattern.MONOTONIC_STACK);
        if (has(signals, Signal.COUNT_INVERSIONS))
            answer.add(Pattern.MERGE_SORT_COUNTING);
        if (has(signals, Signal.KTH_STATISTIC, Signal.AVERAGE_LINEAR_REQUESTED))
            answer.add(Pattern.QUICKSELECT);
        if (has(signals, Signal.LONGEST_CONTIGUOUS_RANGE, Signal.AT_MOST_K_VIOLATIONS))
            answer.add(Pattern.SLIDING_WINDOW_BUDGET);
        if (has(signals, Signal.TWO_SORTED_ARRAYS, Signal.MEDIAN, Signal.LOG_TIME))
            answer.add(Pattern.BINARY_SEARCH_PARTITION);
        return answer;
    }

    static Pattern requireUnique(Set<Signal> signals) {
        EnumSet<Pattern> candidates = recommend(signals);
        if (candidates.size() != 1)
            throw new IllegalArgumentException("Need more requirements; candidates=" + candidates);
        return candidates.iterator().next();
    }

    private static boolean has(Set<Signal> actual, Signal... required) {
        for (Signal signal : required) if (!actual.contains(signal)) return false;
        return true;
    }

    private static void deterministic() {
        for (Pattern pattern : Pattern.values()) {
            EnumSet<Signal> prompt = RULES.get(pattern).clone();
            check(requireUnique(prompt) == pattern);
            groups++;
        }

        check(recommend(EnumSet.of(Signal.EXACT_SUM_COUNT)).isEmpty()); groups++;
        check(recommend(EnumSet.of(Signal.MINIMIZE_RATE)).isEmpty()); groups++;
        check(recommend(EnumSet.of(Signal.TWO_SORTED_ARRAYS, Signal.MEDIAN)).isEmpty()); groups++;

        EnumSet<Signal> ambiguous = RULES.get(Pattern.MONOTONIC_DEQUE).clone();
        ambiguous.addAll(RULES.get(Pattern.MONOTONIC_STACK));
        check(recommend(ambiguous).equals(EnumSet.of(Pattern.MONOTONIC_DEQUE, Pattern.MONOTONIC_STACK)));
        groups++;

        EnumSet<Signal> original = RULES.get(Pattern.BINARY_SEARCH_PARTITION).clone();
        EnumSet<Signal> snapshot = original.clone();
        requireUnique(original);
        check(original.equals(snapshot)); groups++;
    }

    private static void randomized(int rounds) {
        Signal[] signals = Signal.values();
        for (int round = 0; round < rounds; round++) {
            EnumSet<Signal> prompt = EnumSet.noneOf(Signal.class);
            for (Signal signal : signals) if (RANDOM.nextBoolean()) prompt.add(signal);
            EnumSet<Signal> before = prompt.clone();
            EnumSet<Pattern> actual = recommend(prompt);
            EnumSet<Pattern> expected = tableOracle(prompt);
            check(actual.equals(expected));
            check(prompt.equals(before));
            trials++;
        }
    }

    /** Independent declarative oracle: a rule matches iff every required signal is present. */
    private static EnumSet<Pattern> tableOracle(Set<Signal> prompt) {
        EnumSet<Pattern> answer = EnumSet.noneOf(Pattern.class);
        for (Map.Entry<Pattern, EnumSet<Signal>> rule : RULES.entrySet())
            if (prompt.containsAll(rule.getValue())) answer.add(rule.getKey());
        return answer;
    }

    private static Map<Pattern, EnumSet<Signal>> rules() {
        EnumMap<Pattern, EnumSet<Signal>> map = new EnumMap<>(Pattern.class);
        map.put(Pattern.TWO_POINTERS, set(Signal.SORTED, Signal.TARGET_PAIR, Signal.CONSTANT_SPACE));
        map.put(Pattern.PREFIX_SUM_FREQUENCY, set(Signal.EXACT_SUM_COUNT, Signal.NEGATIVES_POSSIBLE));
        map.put(Pattern.INDEX_PLACEMENT, set(Signal.SMALLEST_POSITIVE_MISSING, Signal.CONSTANT_EXTRA));
        map.put(Pattern.MONOTONIC_DEQUE, set(Signal.FIXED_WINDOW_MAX));
        map.put(Pattern.BINARY_SEARCH_ON_ANSWER, set(Signal.MINIMIZE_RATE, Signal.MONOTONE_FEASIBILITY));
        map.put(Pattern.DUTCH_NATIONAL_FLAG, set(Signal.THREE_CATEGORIES, Signal.IN_PLACE));
        map.put(Pattern.CIRCULAR_KADANE, set(Signal.CIRCULAR_CONTIGUOUS_MAX));
        map.put(Pattern.FLOYD_CYCLE_DETECTION, set(Signal.N_PLUS_ONE_VALUES_ONE_TO_N, Signal.NO_MUTATION));
        map.put(Pattern.SORT_AND_MERGE, set(Signal.OVERLAPPING_INTERVALS));
        map.put(Pattern.DIFFERENCE_ARRAY, set(Signal.MANY_OFFLINE_RANGE_ADDS));
        map.put(Pattern.MONOTONIC_STACK, set(Signal.NEXT_GREATER));
        map.put(Pattern.MERGE_SORT_COUNTING, set(Signal.COUNT_INVERSIONS));
        map.put(Pattern.QUICKSELECT, set(Signal.KTH_STATISTIC, Signal.AVERAGE_LINEAR_REQUESTED));
        map.put(Pattern.SLIDING_WINDOW_BUDGET, set(Signal.LONGEST_CONTIGUOUS_RANGE, Signal.AT_MOST_K_VIOLATIONS));
        map.put(Pattern.BINARY_SEARCH_PARTITION, set(Signal.TWO_SORTED_ARRAYS, Signal.MEDIAN, Signal.LOG_TIME));
        return Collections.unmodifiableMap(map);
    }

    private static EnumSet<Signal> set(Signal first, Signal... rest) {
        return EnumSet.of(first, rest);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("pattern contract failed");
    }
}
