import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Executable parity checks for G-2 adjacency-matrix and adjacency-list contracts. */
public final class GraphRepresentationContracts {
    record Edge(int from, int to) {}

    record MatrixGraph(boolean[][] adjacency) {
        int vertices() { return adjacency.length; }
        boolean hasEdge(int from, int to) { return adjacency[from][to]; }
        int degree(int from) {
            int degree = 0;
            for (boolean present : adjacency[from]) if (present) degree++;
            return degree;
        }
        Set<Integer> neighbors(int from) {
            Set<Integer> result = new LinkedHashSet<>();
            for (int to = 0; to < vertices(); to++) if (adjacency[from][to]) result.add(to);
            return result;
        }
    }

    record ListGraph(List<List<Integer>> adjacency) {
        int vertices() { return adjacency.size(); }
        boolean hasEdge(int from, int to) { return adjacency.get(from).contains(to); }
        int degree(int from) { return adjacency.get(from).size(); }
        Set<Integer> neighbors(int from) { return Set.copyOf(adjacency.get(from)); }
    }

    record Pair(MatrixGraph matrix, ListGraph list) {}

    private static int groups;
    private static long randomizedChecks;

    static Pair buildBoth(int vertices, boolean directed, List<Edge> edges) {
        validateVertexCount(vertices);
        Objects.requireNonNull(edges, "edges");
        boolean[][] matrix = new boolean[vertices][vertices];
        List<Set<Integer>> sets = new ArrayList<>(vertices);
        for (int u = 0; u < vertices; u++) sets.add(new LinkedHashSet<>());

        for (Edge edge : edges) {
            Objects.requireNonNull(edge, "null edge");
            validateVertex(edge.from(), vertices);
            validateVertex(edge.to(), vertices);
            matrix[edge.from()][edge.to()] = true;
            sets.get(edge.from()).add(edge.to());
            if (!directed) {
                matrix[edge.to()][edge.from()] = true;
                sets.get(edge.to()).add(edge.from());
            }
        }

        List<List<Integer>> adjacency = sets.stream().map(List::copyOf).toList();
        return new Pair(new MatrixGraph(matrix), new ListGraph(List.copyOf(adjacency)));
    }

    private static void validateVertexCount(int vertices) {
        if (vertices < 0) throw new IllegalArgumentException("negative V");
        long cells = (long) vertices * vertices;
        if (cells > 100_000_000L) throw new IllegalArgumentException("matrix budget exceeded");
    }

    private static void validateVertex(int vertex, int vertices) {
        if (vertex < 0 || vertex >= vertices) {
            throw new IllegalArgumentException("vertex outside 0..V-1: " + vertex);
        }
    }

    private static void group(boolean condition, String message) {
        groups++;
        require(condition, message);
    }

    private static void check(boolean condition, String message) {
        randomizedChecks++;
        require(condition, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable operation, String message) {
        try {
            operation.run();
            throw new AssertionError("expected failure: " + message);
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed validation.
        }
    }

    private static void deterministicChecks() {
        Pair undirected = buildBoth(4, false,
                List.of(new Edge(0, 1), new Edge(0, 2), new Edge(1, 2)));
        group(undirected.matrix().hasEdge(0, 1), "undirected matrix edge");
        group(undirected.matrix().hasEdge(1, 0), "undirected reverse edge");
        group(undirected.list().adjacency().get(0).equals(List.of(1, 2)), "list insertion order");
        group(undirected.list().adjacency().get(3).isEmpty(), "isolated bucket");
        group(undirected.matrix().degree(3) == 0, "isolated row");

        Pair directed = buildBoth(3, true, List.of(new Edge(0, 1), new Edge(1, 2)));
        group(directed.matrix().hasEdge(0, 1), "directed forward edge");
        group(!directed.matrix().hasEdge(1, 0), "directed reverse absent");
        group(directed.list().adjacency().equals(List.of(List.of(1), List.of(2), List.of())),
                "directed adjacency");

        Pair duplicate = buildBoth(2, false,
                List.of(new Edge(0, 1), new Edge(1, 0), new Edge(0, 1)));
        group(duplicate.matrix().degree(0) == 1, "matrix duplicate idempotence");
        group(duplicate.list().degree(0) == 1, "list duplicate idempotence");

        Pair selfLoop = buildBoth(2, true, List.of(new Edge(1, 1)));
        group(selfLoop.matrix().hasEdge(1, 1), "matrix self-loop");
        group(selfLoop.list().neighbors(1).equals(Set.of(1)), "list self-loop");

        Pair empty = buildBoth(0, true, List.of());
        group(empty.matrix().vertices() == 0, "empty matrix");
        group(empty.list().vertices() == 0, "empty list");

        Pair singleton = buildBoth(1, false, List.of());
        group(singleton.matrix().degree(0) == 0, "singleton matrix");
        group(singleton.list().degree(0) == 0, "singleton list");

        expectFailure(() -> buildBoth(-1, false, List.of()), "negative V");
        group(true, "negative V rejected");
        expectFailure(() -> buildBoth(2, false, List.of(new Edge(2, 0))), "high vertex");
        group(true, "high vertex rejected");
        expectFailure(() -> buildBoth(2, false, List.of(new Edge(-1, 0))), "negative vertex");
        group(true, "negative vertex rejected");
        expectFailure(() -> buildBoth(10_001, false, List.of()), "matrix budget");
        group(true, "matrix budget rejected");
    }

    private static void randomizedChecks() {
        Random random = new Random(0x4752415048524550L);
        for (int trial = 0; trial < 20_000; trial++) {
            int vertices = random.nextInt(24);
            boolean directed = random.nextBoolean();
            int edgeCount = vertices == 0 ? 0 : random.nextInt(80);
            List<Edge> edges = new ArrayList<>(edgeCount);
            for (int i = 0; i < edgeCount; i++) {
                edges.add(new Edge(random.nextInt(vertices), random.nextInt(vertices)));
            }
            Pair pair = buildBoth(vertices, directed, edges);

            boolean pairParity = true;
            boolean neighborParity = true;
            boolean degreeParity = true;
            boolean symmetry = true;
            for (int from = 0; from < vertices; from++) {
                neighborParity &= pair.matrix().neighbors(from).equals(pair.list().neighbors(from));
                degreeParity &= pair.matrix().degree(from) == pair.list().degree(from);
                for (int to = 0; to < vertices; to++) {
                    pairParity &= pair.matrix().hasEdge(from, to) == pair.list().hasEdge(from, to);
                    if (!directed) {
                        symmetry &= pair.matrix().hasEdge(from, to) == pair.matrix().hasEdge(to, from);
                    }
                }
            }
            check(pairParity, "pair-query parity");
            check(neighborParity, "neighbor-set parity");
            check(degreeParity, "degree parity");
            check(symmetry, "undirected symmetry");
        }
    }

    public static void main(String[] args) {
        deterministicChecks();
        randomizedChecks();
        require(groups == 20, "deterministic group count");
        require(randomizedChecks == 80_000, "randomized check count");
        System.out.printf("PASS GraphRepresentationContracts groups=%d trials=20000 checks=%d%n",
                groups, randomizedChecks);
    }
}
