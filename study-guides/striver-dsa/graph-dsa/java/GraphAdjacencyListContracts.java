import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Executable contracts for G-3 typed Java adjacency-list construction. */
public final class GraphAdjacencyListContracts {
    record InputEdge(int from, int to, long weight) {}
    record Arc(int to, long weight, int edgeId) {}
    record Graph(int vertices, boolean directed, int inputEdges, List<List<Arc>> adjacency) {}

    private static int groups;
    private static long randomizedChecks;

    static Graph build(int vertices, boolean directed, List<InputEdge> edges) {
        if (vertices < 0) throw new IllegalArgumentException("negative V");
        Objects.requireNonNull(edges, "edges");

        List<List<Arc>> mutable = new ArrayList<>(vertices);
        for (int v = 0; v < vertices; v++) mutable.add(new ArrayList<>());

        for (int edgeId = 0; edgeId < edges.size(); edgeId++) {
            InputEdge edge = Objects.requireNonNull(edges.get(edgeId), "null edge");
            validateVertex(edge.from(), vertices);
            validateVertex(edge.to(), vertices);
            mutable.get(edge.from()).add(new Arc(edge.to(), edge.weight(), edgeId));
            if (!directed) {
                mutable.get(edge.to()).add(new Arc(edge.from(), edge.weight(), edgeId));
            }
        }

        List<List<Arc>> frozen = mutable.stream().map(List::copyOf).toList();
        return new Graph(vertices, directed, edges.size(), frozen);
    }

    private static void validateVertex(int vertex, int vertices) {
        if (vertex < 0 || vertex >= vertices) {
            throw new IllegalArgumentException("vertex outside 0..V-1: " + vertex);
        }
    }

    private static List<List<Arc>> flatScanOracle(
            int vertices, boolean directed, List<InputEdge> edges) {
        List<List<Arc>> expected = new ArrayList<>(vertices);
        for (int from = 0; from < vertices; from++) {
            List<Arc> bucket = new ArrayList<>();
            for (int edgeId = 0; edgeId < edges.size(); edgeId++) {
                InputEdge edge = edges.get(edgeId);
                if (edge.from() == from) bucket.add(new Arc(edge.to(), edge.weight(), edgeId));
                if (!directed && edge.to() == from) {
                    bucket.add(new Arc(edge.from(), edge.weight(), edgeId));
                }
            }
            expected.add(List.copyOf(bucket));
        }
        return List.copyOf(expected);
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

    private static void expectFailure(Class<? extends Throwable> type, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected " + type.getSimpleName());
        } catch (Throwable actual) {
            if (!type.isInstance(actual)) throw actual;
        }
    }

    private static void deterministicChecks() {
        Graph undirected = build(4, false,
                List.of(new InputEdge(0, 1, 4), new InputEdge(0, 2, 7)));
        group(undirected.adjacency().get(0).equals(
                List.of(new Arc(1, 4, 0), new Arc(2, 7, 1))), "forward arcs");
        group(undirected.adjacency().get(1).equals(List.of(new Arc(0, 4, 0))),
                "first reverse arc");
        group(undirected.adjacency().get(2).equals(List.of(new Arc(0, 7, 1))),
                "second reverse arc");
        group(undirected.adjacency().get(3).isEmpty(), "isolated vertex");

        Graph directed = build(3, true,
                List.of(new InputEdge(0, 1, 5), new InputEdge(1, 2, -2)));
        group(directed.adjacency().get(0).equals(List.of(new Arc(1, 5, 0))),
                "directed forward");
        group(directed.adjacency().get(1).equals(List.of(new Arc(2, -2, 1))),
                "negative weight preserved");
        group(directed.adjacency().get(2).isEmpty(), "no directed reverse");

        Graph parallel = build(2, false,
                List.of(new InputEdge(0, 1, 9), new InputEdge(0, 1, 3)));
        group(parallel.adjacency().get(0).size() == 2, "parallel multiplicity");
        group(parallel.adjacency().get(0).get(0).edgeId() == 0
                && parallel.adjacency().get(0).get(1).edgeId() == 1, "parallel identity");

        Graph loop = build(2, false, List.of(new InputEdge(1, 1, 6)));
        group(loop.adjacency().get(1).equals(
                List.of(new Arc(1, 6, 0), new Arc(1, 6, 0))), "loop stored twice");

        Graph empty = build(0, true, List.of());
        group(empty.adjacency().isEmpty(), "empty graph");
        Graph singleton = build(1, false, List.of());
        group(singleton.adjacency().equals(List.of(List.of())), "singleton bucket");

        expectFailure(UnsupportedOperationException.class,
                () -> singleton.adjacency().add(List.of()));
        group(true, "outer list immutable");
        expectFailure(UnsupportedOperationException.class,
                () -> undirected.adjacency().get(0).add(new Arc(3, 0, 2)));
        group(true, "inner list immutable");

        expectFailure(IllegalArgumentException.class, () -> build(-1, false, List.of()));
        group(true, "negative V rejected");
        expectFailure(NullPointerException.class, () -> build(1, false, null));
        group(true, "null edge list rejected");
        expectFailure(NullPointerException.class, () -> build(1, false, java.util.Arrays.asList((InputEdge) null)));
        group(true, "null edge rejected");
        expectFailure(IllegalArgumentException.class,
                () -> build(2, false, List.of(new InputEdge(-1, 0, 0))));
        group(true, "negative endpoint rejected");
        expectFailure(IllegalArgumentException.class,
                () -> build(2, false, List.of(new InputEdge(0, 2, 0))));
        group(true, "high endpoint rejected");
        group(undirected.inputEdges() == 2 && undirected.vertices() == 4,
                "metadata preserved");
    }

    private static void randomizedChecks() {
        Random random = new Random(0x4a41564147524150L);
        for (int trial = 0; trial < 20_000; trial++) {
            int vertices = random.nextInt(24);
            boolean directed = random.nextBoolean();
            int edgeCount = vertices == 0 ? 0 : random.nextInt(80);
            List<InputEdge> edges = new ArrayList<>(edgeCount);
            for (int i = 0; i < edgeCount; i++) {
                edges.add(new InputEdge(random.nextInt(vertices), random.nextInt(vertices),
                        random.nextLong(-1_000, 1_001)));
            }

            Graph graph = build(vertices, directed, edges);
            check(graph.adjacency().equals(flatScanOracle(vertices, directed, edges)),
                    "flat-scan parity");
            long arcCount = graph.adjacency().stream().mapToLong(List::size).sum();
            check(arcCount == (directed ? edgeCount : 2L * edgeCount), "arc count");
            check(graph.adjacency().size() == vertices, "all buckets retained");

            boolean provenance = true;
            for (int from = 0; from < vertices; from++) {
                for (Arc arc : graph.adjacency().get(from)) {
                    InputEdge source = edges.get(arc.edgeId());
                    boolean forward = source.from() == from && source.to() == arc.to();
                    boolean reverse = !directed && source.to() == from && source.from() == arc.to();
                    provenance &= (forward || reverse) && source.weight() == arc.weight();
                }
            }
            check(provenance, "arc provenance");
        }
    }

    public static void main(String[] args) {
        deterministicChecks();
        randomizedChecks();
        require(groups == 20, "deterministic group count");
        require(randomizedChecks == 80_000, "randomized check count");
        System.out.printf("PASS GraphAdjacencyListContracts groups=%d trials=20000 checks=%d%n",
                groups, randomizedChecks);
    }
}
