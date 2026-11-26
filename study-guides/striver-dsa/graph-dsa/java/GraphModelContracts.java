import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Executable contract checks for G-1 graph modeling conventions. */
public final class GraphModelContracts {
    enum Direction { DIRECTED, UNDIRECTED }

    record Edge(int from, int to, long weight) {}
    record Arc(int to, long weight, int edgeId) {}
    record GraphSpec(
            int vertices,
            Direction direction,
            boolean allowSelfLoops,
            boolean allowParallelEdges) {
        GraphSpec {
            Objects.requireNonNull(direction, "direction");
        }
    }
    record BuiltGraph(
            GraphSpec spec,
            List<List<Arc>> adjacency,
            int[] indegree,
            int[] outdegree,
            int[] degree,
            int edgeCount) {}

    private static int groups;
    private static long randomizedChecks;

    static BuiltGraph build(GraphSpec spec, List<Edge> edges) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(edges, "edges");
        if (spec.vertices() < 0) throw new IllegalArgumentException("negative V");

        List<List<Arc>> adjacency = new ArrayList<>(spec.vertices());
        for (int v = 0; v < spec.vertices(); v++) adjacency.add(new ArrayList<>());
        int[] indegree = new int[spec.vertices()];
        int[] outdegree = new int[spec.vertices()];
        int[] degree = new int[spec.vertices()];
        Set<Long> seen = new HashSet<>();

        for (int edgeId = 0; edgeId < edges.size(); edgeId++) {
            Edge edge = Objects.requireNonNull(edges.get(edgeId), "null edge");
            validateVertex(edge.from(), spec.vertices());
            validateVertex(edge.to(), spec.vertices());
            if (edge.from() == edge.to() && !spec.allowSelfLoops()) {
                throw new IllegalArgumentException("self-loop rejected");
            }

            long key = edgeKey(edge.from(), edge.to(), spec.direction());
            if (!spec.allowParallelEdges() && !seen.add(key)) {
                throw new IllegalArgumentException("parallel edge rejected");
            }

            adjacency.get(edge.from()).add(new Arc(edge.to(), edge.weight(), edgeId));
            if (spec.direction() == Direction.DIRECTED) {
                outdegree[edge.from()]++;
                indegree[edge.to()]++;
            } else {
                adjacency.get(edge.to()).add(new Arc(edge.from(), edge.weight(), edgeId));
                degree[edge.from()]++;
                degree[edge.to()]++;
            }
        }
        return freeze(spec, adjacency, indegree, outdegree, degree, edges.size());
    }

    private static BuiltGraph freeze(
            GraphSpec spec,
            List<List<Arc>> adjacency,
            int[] indegree,
            int[] outdegree,
            int[] degree,
            int edgeCount) {
        List<List<Arc>> immutable = adjacency.stream().map(List::copyOf).toList();
        return new BuiltGraph(spec, List.copyOf(immutable), indegree, outdegree, degree, edgeCount);
    }

    private static void validateVertex(int vertex, int vertices) {
        if (vertex < 0 || vertex >= vertices) {
            throw new IllegalArgumentException("vertex outside 0..V-1: " + vertex);
        }
    }

    private static long edgeKey(int from, int to, Direction direction) {
        int a = from;
        int b = to;
        if (direction == Direction.UNDIRECTED && a > b) {
            int swap = a;
            a = b;
            b = swap;
        }
        return ((long) a << 32) ^ (b & 0xffff_ffffL);
    }

    private static void group(boolean condition, String message) {
        groups++;
        require(condition, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable operation, String label) {
        try {
            operation.run();
            throw new AssertionError("expected failure: " + label);
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed contract behavior.
        }
    }

    private static void deterministicChecks() {
        BuiltGraph undirectedPath = build(
                new GraphSpec(3, Direction.UNDIRECTED, false, false),
                List.of(new Edge(0, 1, 1), new Edge(1, 2, 1)));
        group(Arrays.equals(undirectedPath.degree(), new int[] {1, 2, 1}), "path degree");
        group(undirectedPath.adjacency().stream().mapToInt(List::size).sum() == 4, "path arcs");

        BuiltGraph directedPath = build(
                new GraphSpec(3, Direction.DIRECTED, false, false),
                List.of(new Edge(0, 1, 1), new Edge(1, 2, 1)));
        group(Arrays.equals(directedPath.indegree(), new int[] {0, 1, 1}), "directed indegree");
        group(Arrays.equals(directedPath.outdegree(), new int[] {1, 1, 0}), "directed outdegree");

        BuiltGraph weighted = build(
                new GraphSpec(4, Direction.DIRECTED, false, false),
                List.of(new Edge(0, 1, 0), new Edge(1, 2, 7)));
        group(weighted.adjacency().get(0).get(0).weight() == 0, "zero weight preserved");
        group(weighted.adjacency().get(3).isEmpty(), "isolated vertex preserved");

        BuiltGraph multi = build(
                new GraphSpec(2, Direction.UNDIRECTED, true, true),
                List.of(new Edge(0, 1, 2), new Edge(0, 1, 3), new Edge(1, 1, 5)));
        group(Arrays.equals(multi.degree(), new int[] {2, 4}), "multigraph degree");
        group(multi.adjacency().get(1).stream().filter(a -> a.edgeId() == 2).count() == 2,
                "self-loop stored twice");
        group(multi.edgeCount() == 3, "edge identity retained");
        group(multi.adjacency().stream().mapToInt(List::size).sum() == 6, "multigraph arcs");

        group(build(new GraphSpec(0, Direction.DIRECTED, false, false), List.of())
                .adjacency().isEmpty(), "empty graph");
        group(build(new GraphSpec(1, Direction.UNDIRECTED, false, false), List.of())
                .degree()[0] == 0, "single isolated vertex");
        group(build(new GraphSpec(2, Direction.DIRECTED, true, false),
                List.of(new Edge(1, 1, -9))).indegree()[1] == 1, "directed self-loop indegree");
        group(build(new GraphSpec(2, Direction.DIRECTED, true, false),
                List.of(new Edge(1, 1, -9))).outdegree()[1] == 1, "directed self-loop outdegree");

        expectFailure(() -> build(new GraphSpec(-1, Direction.DIRECTED, false, false), List.of()), "negative V");
        group(true, "negative V rejected");
        expectFailure(() -> build(new GraphSpec(2, Direction.DIRECTED, false, false),
                List.of(new Edge(2, 0, 1))), "out-of-range label");
        group(true, "label rejected");
        expectFailure(() -> build(new GraphSpec(2, Direction.UNDIRECTED, false, true),
                List.of(new Edge(1, 1, 1))), "self-loop policy");
        group(true, "self-loop rejected");
        expectFailure(() -> build(new GraphSpec(2, Direction.DIRECTED, true, false),
                List.of(new Edge(0, 1, 1), new Edge(0, 1, 2))), "directed duplicate");
        group(true, "directed duplicate rejected");
        expectFailure(() -> build(new GraphSpec(2, Direction.UNDIRECTED, true, false),
                List.of(new Edge(0, 1, 1), new Edge(1, 0, 2))), "undirected reverse duplicate");
        group(true, "undirected reverse duplicate rejected");
        group(weighted.adjacency().get(0).get(0).edgeId() == 0, "stable edge ID");
    }

    private static void randomizedChecks() {
        Random random = new Random(0x47524150484cL);
        for (int trial = 0; trial < 20_000; trial++) {
            int vertices = 1 + random.nextInt(20);
            Direction direction = random.nextBoolean() ? Direction.DIRECTED : Direction.UNDIRECTED;
            int edgeCount = random.nextInt(61);
            List<Edge> edges = new ArrayList<>(edgeCount);
            long rawWeightSum = 0;
            for (int edgeId = 0; edgeId < edgeCount; edgeId++) {
                Edge edge = new Edge(
                        random.nextInt(vertices),
                        random.nextInt(vertices),
                        random.nextLong(-1_000, 1_001));
                edges.add(edge);
                rawWeightSum += edge.weight();
            }

            BuiltGraph graph = build(new GraphSpec(vertices, direction, true, true), edges);
            check(graph.edgeCount() == edgeCount, "edge count");

            int arcs = graph.adjacency().stream().mapToInt(List::size).sum();
            check(arcs == edgeCount * (direction == Direction.DIRECTED ? 1 : 2), "arc count");

            long degreeSummary = direction == Direction.DIRECTED
                    ? Arrays.stream(graph.indegree()).asLongStream().sum()
                            + Arrays.stream(graph.outdegree()).asLongStream().sum()
                    : Arrays.stream(graph.degree()).asLongStream().sum();
            check(degreeSummary == 2L * edgeCount, "degree conservation");

            long arcWeightSum = graph.adjacency().stream()
                    .flatMap(List::stream)
                    .mapToLong(Arc::weight)
                    .sum();
            long expectedWeightSum = direction == Direction.DIRECTED ? rawWeightSum : 2L * rawWeightSum;
            check(arcWeightSum == expectedWeightSum, "weight preservation");
        }
    }

    private static void check(boolean condition, String message) {
        randomizedChecks++;
        require(condition, message);
    }

    public static void main(String[] args) {
        deterministicChecks();
        randomizedChecks();
        require(groups == 20, "deterministic group count");
        require(randomizedChecks == 80_000, "randomized check count");
        System.out.printf("PASS GraphModelContracts groups=%d trials=20000 checks=%d%n",
                groups, randomizedChecks);
    }
}
