import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Random;

public class TreeDecisionContracts {
    enum DominantOperation {
        ORDERED_NEIGHBOUR,
        REPEATED_EXTREME,
        PREFIX_LOOKUP,
        PAGE_RANGE_SCAN,
        PREFIX_SUM,
        GENERAL_RANGE_AGGREGATE
    }

    enum UpdateProfile { READ_HEAVY, FREQUENT_MUTATION, POINT_UPDATE, RANGE_UPDATE }

    enum Structure {
        AVL_TREE,
        RED_BLACK_TREE,
        BINARY_HEAP,
        TRIE,
        B_PLUS_TREE,
        FENWICK_TREE,
        SEGMENT_TREE
    }

    record Requirement(
            DominantOperation operation,
            UpdateProfile updates,
            boolean pageOriented,
            boolean customAggregate) {
        Requirement {
            if (operation == null || updates == null) {
                throw new IllegalArgumentException("operation and updates are required");
            }
            if (pageOriented != (operation == DominantOperation.PAGE_RANGE_SCAN)) {
                throw new IllegalArgumentException("page storage must match a page-range requirement");
            }
            if (customAggregate && operation != DominantOperation.GENERAL_RANGE_AGGREGATE) {
                throw new IllegalArgumentException("custom aggregate requires the general range contract");
            }
        }
    }

    private static int groups;
    private static long checks;

    static Structure choose(Requirement requirement) {
        return switch (requirement.operation()) {
            case ORDERED_NEIGHBOUR -> requirement.updates() == UpdateProfile.FREQUENT_MUTATION
                    ? Structure.RED_BLACK_TREE : Structure.AVL_TREE;
            case REPEATED_EXTREME -> Structure.BINARY_HEAP;
            case PREFIX_LOOKUP -> Structure.TRIE;
            case PAGE_RANGE_SCAN -> Structure.B_PLUS_TREE;
            case PREFIX_SUM -> requirement.updates() == UpdateProfile.POINT_UPDATE
                    ? Structure.FENWICK_TREE : Structure.SEGMENT_TREE;
            case GENERAL_RANGE_AGGREGATE -> Structure.SEGMENT_TREE;
        };
    }

    static int[] topKLargest(int[] input, int k) {
        if (input == null) throw new IllegalArgumentException("input is required");
        if (k < 0 || k > input.length) throw new IllegalArgumentException("k out of range");
        PriorityQueue<Integer> retained = new PriorityQueue<>();
        for (int value : input) {
            if (retained.size() < k) {
                retained.offer(value);
            } else if (k > 0 && value > retained.peek()) {
                retained.poll();
                retained.offer(value);
            }
        }
        int[] descending = new int[k];
        for (int i = k - 1; i >= 0; i--) descending[i] = retained.remove();
        return descending;
    }

    private static Requirement requirementFor(int code) {
        return switch (code) {
            case 0 -> new Requirement(DominantOperation.ORDERED_NEIGHBOUR, UpdateProfile.READ_HEAVY, false, false);
            case 1 -> new Requirement(DominantOperation.ORDERED_NEIGHBOUR, UpdateProfile.FREQUENT_MUTATION, false, false);
            case 2 -> new Requirement(DominantOperation.REPEATED_EXTREME, UpdateProfile.FREQUENT_MUTATION, false, false);
            case 3 -> new Requirement(DominantOperation.PREFIX_LOOKUP, UpdateProfile.READ_HEAVY, false, false);
            case 4 -> new Requirement(DominantOperation.PAGE_RANGE_SCAN, UpdateProfile.FREQUENT_MUTATION, true, false);
            case 5 -> new Requirement(DominantOperation.PREFIX_SUM, UpdateProfile.POINT_UPDATE, false, false);
            case 6 -> new Requirement(DominantOperation.PREFIX_SUM, UpdateProfile.RANGE_UPDATE, false, false);
            default -> new Requirement(DominantOperation.GENERAL_RANGE_AGGREGATE, UpdateProfile.RANGE_UPDATE, false, true);
        };
    }

    private static Structure independentChoice(Requirement requirement) {
        if (requirement.pageOriented()) return Structure.B_PLUS_TREE;
        if (requirement.customAggregate()) return Structure.SEGMENT_TREE;
        if (requirement.operation() == DominantOperation.ORDERED_NEIGHBOUR) {
            return requirement.updates() == UpdateProfile.FREQUENT_MUTATION
                    ? Structure.RED_BLACK_TREE : Structure.AVL_TREE;
        }
        if (requirement.operation() == DominantOperation.REPEATED_EXTREME) return Structure.BINARY_HEAP;
        if (requirement.operation() == DominantOperation.PREFIX_LOOKUP) return Structure.TRIE;
        if (requirement.operation() == DominantOperation.PREFIX_SUM) {
            return requirement.updates() == UpdateProfile.POINT_UPDATE
                    ? Structure.FENWICK_TREE : Structure.SEGMENT_TREE;
        }
        return Structure.SEGMENT_TREE;
    }

    private static int[] sortedOracle(int[] input, int k) {
        int[] copy = input.clone();
        Arrays.sort(copy);
        int[] result = new int[k];
        for (int i = 0; i < k; i++) result[i] = copy[copy.length - 1 - i];
        return result;
    }

    private static void deterministicGroups() {
        group(choose(requirementFor(0)) == Structure.AVL_TREE, "read-heavy ordered neighbours");
        group(choose(requirementFor(1)) == Structure.RED_BLACK_TREE, "mutation-heavy ordered neighbours");
        group(choose(requirementFor(2)) == Structure.BINARY_HEAP, "repeated extreme");
        group(choose(requirementFor(3)) == Structure.TRIE, "prefix lookup");
        group(choose(requirementFor(4)) == Structure.B_PLUS_TREE, "page range scan");
        group(choose(requirementFor(5)) == Structure.FENWICK_TREE, "point prefix sum");
        group(choose(requirementFor(6)) == Structure.SEGMENT_TREE, "range-updated sum");
        group(choose(requirementFor(7)) == Structure.SEGMENT_TREE, "custom aggregate");

        group(Arrays.equals(topKLargest(new int[] {7, 2, 11, 4, 9, 1}, 3), new int[] {11, 9, 7}), "top three");
        group(Arrays.equals(topKLargest(new int[] {-5, -1, -9}, 2), new int[] {-1, -5}), "negative top K");
        group(Arrays.equals(topKLargest(new int[] {4, 4, 2}, 2), new int[] {4, 4}), "duplicate top K");
        group(Arrays.equals(topKLargest(new int[] {}, 0), new int[] {}), "empty top K");
        group(Arrays.equals(topKLargest(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}, 1), new int[] {Integer.MAX_VALUE}), "extremes");
        group(fails(() -> topKLargest(null, 0)), "null input rejected");
        group(fails(() -> topKLargest(new int[] {1}, -1)), "negative K rejected");
        group(fails(() -> topKLargest(new int[] {1}, 2)), "oversized K rejected");
        group(fails(() -> new Requirement(DominantOperation.PAGE_RANGE_SCAN, UpdateProfile.READ_HEAVY, false, false)), "page flag required");
        group(fails(() -> new Requirement(DominantOperation.ORDERED_NEIGHBOUR, UpdateProfile.READ_HEAVY, true, false)), "page flag scoped");
        group(fails(() -> new Requirement(DominantOperation.PREFIX_SUM, UpdateProfile.POINT_UPDATE, false, true)), "custom aggregate scoped");
        group(fails(() -> new Requirement(null, UpdateProfile.READ_HEAVY, false, false)), "missing operation rejected");
    }

    private static void randomizedChecks() {
        Random random = new Random(0x71EE5EEDL);
        for (int trial = 0; trial < 20_000; trial++) {
            Requirement requirement = requirementFor(random.nextInt(8));
            trialCheck(choose(requirement) == independentChoice(requirement), "selector parity");

            int length = random.nextInt(25);
            int[] values = new int[length];
            for (int i = 0; i < length; i++) values[i] = random.nextInt(101) - 50;
            int[] snapshot = values.clone();
            int k = random.nextInt(length + 1);
            int[] actual = topKLargest(values, k);
            trialCheck(Arrays.equals(actual, sortedOracle(values, k)), "top-K oracle");
            trialCheck(Arrays.equals(values, snapshot), "input ownership");
            trialCheck(actual.length == k, "output cardinality");
        }
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
        check(condition, message);
        groups++;
    }

    private static void trialCheck(boolean condition, String message) {
        check(condition, message);
        checks++;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        deterministicGroups();
        randomizedChecks();
        System.out.println("PASS groups=" + groups + " trials=20000 checks=" + checks);
    }
}
