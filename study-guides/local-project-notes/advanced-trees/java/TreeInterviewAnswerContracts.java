import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/** Executable contracts for concise, evidence-backed advanced-tree interview answers. */
public final class TreeInterviewAnswerContracts {
    enum Structure {
        PLAIN_BST,
        AVL,
        RED_BLACK,
        B_PLUS_TREE,
        MIN_HEAP,
        FENWICK,
        SEGMENT_TREE,
        LAZY_SEGMENT_TREE
    }

    enum Claim {
        GUARANTEED_LOG_ORDERED_SEARCH,
        MINIMUM_AT_HEAD,
        SORTED_ITERATION,
        POINT_ADD_AND_PREFIX_SUM,
        RANGE_ADD_AND_RANGE_SUM,
        LINKED_LEAF_RANGE_SCAN
    }

    enum RangeWorkload {
        POINT_ADD_PREFIX_SUM,
        POINT_SET_RANGE_MINIMUM,
        RANGE_ADD_RANGE_SUM
    }

    record AnswerPacket(
            Structure structure,
            String invariant,
            String problem,
            String mechanics,
            String complexity,
            String tradeoff,
            String useCase) {
        AnswerPacket {
            if (structure == null) throw new IllegalArgumentException("structure");
            requireText(invariant, "invariant");
            requireText(problem, "problem");
            requireText(mechanics, "mechanics");
            requireText(complexity, "complexity");
            requireText(tradeoff, "tradeoff");
            requireText(useCase, "useCase");
        }

        List<String> speakingOrder() {
            return List.of(invariant, problem, mechanics, complexity, tradeoff, useCase);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field);
    }

    static boolean supports(Structure structure, Claim claim) {
        return switch (claim) {
            case GUARANTEED_LOG_ORDERED_SEARCH ->
                    structure == Structure.AVL || structure == Structure.RED_BLACK;
            case MINIMUM_AT_HEAD -> structure == Structure.MIN_HEAP;
            case SORTED_ITERATION -> false;
            case POINT_ADD_AND_PREFIX_SUM -> structure == Structure.FENWICK;
            case RANGE_ADD_AND_RANGE_SUM -> structure == Structure.LAZY_SEGMENT_TREE;
            case LINKED_LEAF_RANGE_SCAN -> structure == Structure.B_PLUS_TREE;
        };
    }

    static Structure selectRangeStructure(RangeWorkload workload) {
        if (workload == null) throw new IllegalArgumentException("workload");
        return switch (workload) {
            case POINT_ADD_PREFIX_SUM -> Structure.FENWICK;
            case POINT_SET_RANGE_MINIMUM -> Structure.SEGMENT_TREE;
            case RANGE_ADD_RANGE_SUM -> Structure.LAZY_SEGMENT_TREE;
        };
    }

    /** Returns the k largest values in descending order without mutating the input. */
    static int[] topKLargest(int[] values, int k) {
        if (values == null) throw new IllegalArgumentException("values");
        if (k < 0 || k > values.length) throw new IllegalArgumentException("k");
        PriorityQueue<Integer> candidates = new PriorityQueue<>();
        for (int value : values) {
            if (candidates.size() < k) candidates.add(value);
            else if (k > 0 && value > candidates.element()) {
                candidates.remove();
                candidates.add(value);
            }
        }
        int[] result = new int[k];
        for (int i = k - 1; i >= 0; i--) result[i] = candidates.remove();
        return result;
    }

    private static int groups;
    private static long checks;

    private static void deterministicGroups() {
        AnswerPacket avl = new AnswerPacket(
                Structure.AVL,
                "BST order plus balance factor in [-1,1]",
                "prevents a skewed ordered tree",
                "update heights and rotate on the first unbalanced ancestor",
                "search and update are O(log n)",
                "more rigid balancing than a red-black tree",
                "read-heavy in-memory ordered set");
        group(avl.speakingOrder().size() == 6, "six-slot speaking packet");
        group(avl.speakingOrder().get(0).contains("balance"), "invariant comes first");
        group(fails(() -> new AnswerPacket(Structure.AVL, " ", "p", "m", "c", "t", "u")),
                "blank invariant rejected");

        group(!supports(Structure.PLAIN_BST, Claim.GUARANTEED_LOG_ORDERED_SEARCH),
                "plain BST has no unconditional logarithmic bound");
        group(supports(Structure.AVL, Claim.GUARANTEED_LOG_ORDERED_SEARCH), "AVL height bound");
        group(supports(Structure.RED_BLACK, Claim.GUARANTEED_LOG_ORDERED_SEARCH),
                "red-black height bound");
        group(supports(Structure.MIN_HEAP, Claim.MINIMUM_AT_HEAD), "heap head guarantee");
        group(!supports(Structure.MIN_HEAP, Claim.SORTED_ITERATION), "heap iteration is not sorted");
        group(supports(Structure.B_PLUS_TREE, Claim.LINKED_LEAF_RANGE_SCAN),
                "B+ linked-leaf scan");

        group(selectRangeStructure(RangeWorkload.POINT_ADD_PREFIX_SUM) == Structure.FENWICK,
                "Fenwick workload");
        group(selectRangeStructure(RangeWorkload.POINT_SET_RANGE_MINIMUM) == Structure.SEGMENT_TREE,
                "segment-tree workload");
        group(selectRangeStructure(RangeWorkload.RANGE_ADD_RANGE_SUM) == Structure.LAZY_SEGMENT_TREE,
                "lazy segment-tree workload");
        group(supports(Structure.FENWICK, Claim.POINT_ADD_AND_PREFIX_SUM), "Fenwick capability");
        group(!supports(Structure.FENWICK, Claim.RANGE_ADD_AND_RANGE_SUM),
                "single Fenwick is not the declared range-add/range-sum contract");
        group(supports(Structure.LAZY_SEGMENT_TREE, Claim.RANGE_ADD_AND_RANGE_SUM),
                "lazy range contract");

        int[] sample = {5, 1, 9, 3, 9, 7};
        int[] before = sample.clone();
        group(Arrays.equals(topKLargest(sample, 3), new int[] {9, 9, 7}), "top-k trace");
        group(Arrays.equals(sample, before), "top-k retains input ownership");
        group(Arrays.equals(topKLargest(sample, 0), new int[0]), "zero-size top-k");
        group(fails(() -> topKLargest(sample, 7)), "oversized k rejected");
        group(fails(() -> selectRangeStructure(null)), "null workload rejected");
    }

    private static void randomizedChecks() {
        Random random = new Random(0x14A7E2L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[] values = new int[random.nextInt(65)];
            for (int i = 0; i < values.length; i++) values[i] = random.nextInt(401) - 200;
            int[] before = values.clone();
            int k = random.nextInt(values.length + 1);

            trialCheck(Arrays.equals(topKLargest(values, k), topKReference(values, k)),
                    "top-k oracle mismatch");
            trialCheck(Arrays.equals(values, before), "top-k mutated input");

            RangeWorkload workload = RangeWorkload.values()[random.nextInt(RangeWorkload.values().length)];
            Structure expected = switch (workload) {
                case POINT_ADD_PREFIX_SUM -> Structure.FENWICK;
                case POINT_SET_RANGE_MINIMUM -> Structure.SEGMENT_TREE;
                case RANGE_ADD_RANGE_SUM -> Structure.LAZY_SEGMENT_TREE;
            };
            trialCheck(selectRangeStructure(workload) == expected, "range selector mismatch");

            AnswerPacket packet = new AnswerPacket(Structure.RED_BLACK,
                    "red-black invariant " + trial,
                    "ordered map " + trial,
                    "recolor and rotate " + trial,
                    "O(log n) " + trial,
                    "looser than AVL " + trial,
                    "library map " + trial);
            trialCheck(packet.speakingOrder().stream().allMatch(text -> !text.isBlank()),
                    "packet lost a speaking slot");
        }
    }

    private static int[] topKReference(int[] values, int k) {
        Integer[] boxed = Arrays.stream(values).boxed().toArray(Integer[]::new);
        Arrays.sort(boxed, Collections.reverseOrder());
        int[] result = new int[k];
        for (int i = 0; i < k; i++) result[i] = boxed[i];
        return result;
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void group(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        groups++;
    }

    private static void trialCheck(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedChecks();
        if (groups != 20 || checks != 80_000) {
            throw new AssertionError("unexpected totals groups=" + groups + " checks=" + checks);
        }
        System.out.println("PASS groups=" + groups + " trials=20000 checks=" + checks);
    }
}
