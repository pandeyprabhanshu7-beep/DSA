import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Executable contracts for G-6: source-scoped depth-first search. */
public final class GraphDfsContracts {
    private static final int RANDOM_TRIALS = 20_000;

    public record Result(List<Integer> order, List<Integer> parent) {
        public Result {
            order = List.copyOf(order);
            parent = List.copyOf(parent);
        }
    }

    private static final class Frame {
        final int vertex;
        int nextNeighbor;

        Frame(int vertex) {
            this.vertex = vertex;
        }
    }

    public static Result dfs(int source, List<List<Integer>> graph) {
        validate(graph);
        int vertices = graph.size();
        if (source < 0 || source >= vertices) {
            throw new IllegalArgumentException("source outside 0..V-1");
        }

        boolean[] seen = new boolean[vertices];
        int[] parent = new int[vertices];
        Arrays.fill(parent, -1);
        List<Integer> order = new ArrayList<>();
        Deque<Frame> stack = new ArrayDeque<>();

        seen[source] = true;                 // Claim before any cycle edge can return.
        order.add(source);                   // Preorder: process on first discovery.
        stack.push(new Frame(source));

        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            List<Integer> neighbors = graph.get(frame.vertex);
            if (frame.nextNeighbor == neighbors.size()) {
                stack.pop();                 // This vertex is completely explored.
                continue;
            }

            int neighbor = neighbors.get(frame.nextNeighbor++);
            if (seen[neighbor]) continue;

            seen[neighbor] = true;
            parent[neighbor] = frame.vertex;
            order.add(neighbor);
            stack.push(new Frame(neighbor)); // Resume parent after this child finishes.
        }

        List<Integer> parentList = new ArrayList<>(vertices);
        for (int value : parent) parentList.add(value);
        return new Result(order, parentList);
    }

    private static void validate(List<List<Integer>> graph) {
        Objects.requireNonNull(graph, "graph");
        int vertices = graph.size();
        for (int from = 0; from < vertices; from++) {
            List<Integer> bucket = Objects.requireNonNull(graph.get(from), "null bucket");
            for (Integer boxed : bucket) {
                int to = Objects.requireNonNull(boxed, "null neighbor");
                if (to < 0 || to >= vertices) {
                    throw new IllegalArgumentException("neighbor outside 0..V-1");
                }
            }
        }
    }

    public static void main(String[] args) {
        int groups = deterministicGroups();
        int checks = randomizedChecks();
        System.out.printf("PASS GraphDfsContracts groups=%d trials=%d checks=%d%n",
                groups, RANDOM_TRIALS, checks);
    }

    private static int deterministicGroups() {
        int groups = 0;

        expectOrder(List.of(List.of(1, 2), List.of(0, 3), List.of(0, 4), List.of(1), List.of(2)),
                0, List.of(0, 1, 3, 2, 4)); groups++;
        expectOrder(List.of(List.of(), List.of(), List.of()), 2, List.of(2)); groups++;
        expectOrder(List.of(List.of(2, 1), List.of(0, 3), List.of(0, 4), List.of(1), List.of(2)),
                0, List.of(0, 2, 4, 1, 3)); groups++;
        expectOrder(List.of(List.of(0, 1, 1), List.of(0, 2), List.of(1)),
                0, List.of(0, 1, 2)); groups++;
        expectOrder(List.of(List.of(1, 2), List.of(3), List.of(3), List.of()),
                0, List.of(0, 1, 3, 2)); groups++;
        expectOrder(List.of(List.of(0)), 0, List.of(0)); groups++;

        int length = 50_000;
        List<List<Integer>> chain = new ArrayList<>(length);
        for (int vertex = 0; vertex < length; vertex++) {
            chain.add(vertex + 1 < length ? List.of(vertex + 1) : List.of());
        }
        check(dfs(0, chain).order().size() == length, "explicit stack must survive long chain"); groups++;

        expectThrows(NullPointerException.class, () -> dfs(0, null)); groups++;
        expectThrows(IllegalArgumentException.class, () -> dfs(-1, List.of(List.of()))); groups++;
        expectThrows(IllegalArgumentException.class, () -> dfs(1, List.of(List.of()))); groups++;
        expectThrows(NullPointerException.class, () -> dfs(0, Arrays.asList((List<Integer>) null))); groups++;
        expectThrows(NullPointerException.class, () -> dfs(0, List.of(Arrays.asList((Integer) null)))); groups++;
        expectThrows(IllegalArgumentException.class, () -> dfs(0, List.of(List.of(-1)))); groups++;
        expectThrows(IllegalArgumentException.class, () -> dfs(0, List.of(List.of(1)))); groups++;
        expectThrows(IllegalArgumentException.class, () -> dfs(0, List.of())); groups++;

        Result immutable = dfs(0, List.of(List.of()));
        expectThrows(UnsupportedOperationException.class, () -> immutable.order().add(1)); groups++;
        expectThrows(UnsupportedOperationException.class, () -> immutable.parent().set(0, 7)); groups++;

        List<List<Integer>> disconnected = List.of(List.of(1), List.of(), List.of(3), List.of());
        check(dfs(2, disconnected).order().equals(List.of(2, 3)), "source scope"); groups++;
        check(dfs(0, List.of(List.of(1), List.of(2), List.of())).parent().equals(List.of(-1, 0, 1)),
                "parent certificate"); groups++;
        check(dfs(0, List.of(List.of(1, 2), List.of(2), List.of(0))).order().equals(List.of(0, 1, 2)),
                "dense directed cycle"); groups++;

        check(groups == 20, "deterministic group count");
        return groups;
    }

    private static int randomizedChecks() {
        Random random = new Random(0xD15EA5EL);
        int checks = 0;

        for (int trial = 0; trial < RANDOM_TRIALS; trial++) {
            int vertices = 1 + random.nextInt(9);
            List<List<Integer>> graph = randomGraph(random, vertices);
            int source = random.nextInt(vertices);
            Result actual = dfs(source, graph);
            Result expected = recursiveOracle(source, graph);

            check(actual.order().equals(expected.order()), "recursive preorder parity"); checks++;
            check(actual.parent().equals(expected.parent()), "recursive parent parity"); checks++;
            check(reached(actual.order(), vertices).equals(transitiveReachability(source, graph)),
                    "independent reachability parity"); checks++;
            check(validCertificate(source, graph, actual), "DFS certificate"); checks++;
        }

        check(checks == 80_000, "random check count");
        return checks;
    }

    private static List<List<Integer>> randomGraph(Random random, int vertices) {
        List<List<Integer>> graph = new ArrayList<>(vertices);
        for (int from = 0; from < vertices; from++) {
            List<Integer> neighbors = new ArrayList<>();
            int entries = random.nextInt(vertices * 2 + 1);
            for (int i = 0; i < entries; i++) neighbors.add(random.nextInt(vertices));
            graph.add(List.copyOf(neighbors));
        }
        return List.copyOf(graph);
    }

    private static Result recursiveOracle(int source, List<List<Integer>> graph) {
        boolean[] seen = new boolean[graph.size()];
        int[] parent = new int[graph.size()];
        Arrays.fill(parent, -1);
        List<Integer> order = new ArrayList<>();
        recursive(source, graph, seen, parent, order);
        List<Integer> parents = new ArrayList<>(parent.length);
        for (int value : parent) parents.add(value);
        return new Result(order, parents);
    }

    private static void recursive(int vertex, List<List<Integer>> graph, boolean[] seen,
                                  int[] parent, List<Integer> order) {
        seen[vertex] = true;
        order.add(vertex);
        for (int neighbor : graph.get(vertex)) {
            if (!seen[neighbor]) {
                parent[neighbor] = vertex;
                recursive(neighbor, graph, seen, parent, order);
            }
        }
    }

    private static List<Boolean> transitiveReachability(int source, List<List<Integer>> graph) {
        int vertices = graph.size();
        boolean[][] reach = new boolean[vertices][vertices];
        for (int vertex = 0; vertex < vertices; vertex++) reach[vertex][vertex] = true;
        for (int from = 0; from < vertices; from++) {
            for (int to : graph.get(from)) reach[from][to] = true;
        }
        for (int through = 0; through < vertices; through++) {
            for (int from = 0; from < vertices; from++) {
                for (int to = 0; to < vertices; to++) {
                    reach[from][to] |= reach[from][through] && reach[through][to];
                }
            }
        }
        List<Boolean> result = new ArrayList<>(vertices);
        for (int vertex = 0; vertex < vertices; vertex++) result.add(reach[source][vertex]);
        return result;
    }

    private static List<Boolean> reached(List<Integer> order, int vertices) {
        boolean[] reached = new boolean[vertices];
        for (int vertex : order) reached[vertex] = true;
        List<Boolean> result = new ArrayList<>(vertices);
        for (boolean value : reached) result.add(value);
        return result;
    }

    private static boolean validCertificate(int source, List<List<Integer>> graph, Result result) {
        int vertices = graph.size();
        if (result.parent().size() != vertices || result.order().isEmpty() || result.order().get(0) != source) {
            return false;
        }
        int[] position = new int[vertices];
        Arrays.fill(position, -1);
        for (int i = 0; i < result.order().size(); i++) {
            int vertex = result.order().get(i);
            if (vertex < 0 || vertex >= vertices || position[vertex] != -1) return false;
            position[vertex] = i;
        }
        for (int vertex = 0; vertex < vertices; vertex++) {
            int parent = result.parent().get(vertex);
            if (vertex == source || position[vertex] == -1) {
                if (parent != -1) return false;
            } else {
                if (parent < 0 || position[parent] < 0 || position[parent] >= position[vertex]) return false;
                if (!graph.get(parent).contains(vertex)) return false;
            }
        }
        return true;
    }

    private static void expectOrder(List<List<Integer>> graph, int source, List<Integer> expected) {
        check(dfs(source, graph).order().equals(expected), "unexpected order");
    }

    private static void expectThrows(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
