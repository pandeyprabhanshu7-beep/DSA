import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Executable contracts for G-4 undirected connected components. */
public final class GraphComponentsContracts {
    record Edge(int u, int v) {}
    record Components(int count, List<Integer> componentOf, List<List<Integer>> members) {
        boolean connected(int u, int v) { return componentOf.get(u).equals(componentOf.get(v)); }
    }

    private static int groups;
    private static long randomizedChecks;

    static Components findComponents(int vertices, List<Edge> edges) {
        List<List<Integer>> graph = buildUndirected(vertices, edges);
        int[] id = new int[vertices];
        Arrays.fill(id, -1);
        List<List<Integer>> members = new ArrayList<>();

        for (int root = 0; root < vertices; root++) {
            if (id[root] != -1) continue;
            int component = members.size();
            List<Integer> current = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            id[root] = component;
            queue.addLast(root);

            while (!queue.isEmpty()) {
                int vertex = queue.removeFirst();
                current.add(vertex);
                for (int next : graph.get(vertex)) {
                    if (id[next] == -1) {
                        id[next] = component;
                        queue.addLast(next);
                    }
                }
            }
            members.add(List.copyOf(current));
        }

        List<Integer> frozenIds = new ArrayList<>(vertices);
        for (int value : id) frozenIds.add(value);
        return new Components(members.size(), List.copyOf(frozenIds), List.copyOf(members));
    }

    private static List<List<Integer>> buildUndirected(int vertices, List<Edge> edges) {
        if (vertices < 0) throw new IllegalArgumentException("negative V");
        Objects.requireNonNull(edges, "edges");
        List<List<Integer>> graph = new ArrayList<>(vertices);
        for (int v = 0; v < vertices; v++) graph.add(new ArrayList<>());
        for (Edge edge : edges) {
            Objects.requireNonNull(edge, "null edge");
            validateVertex(edge.u(), vertices);
            validateVertex(edge.v(), vertices);
            graph.get(edge.u()).add(edge.v());
            graph.get(edge.v()).add(edge.u());
        }
        return graph;
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

    private static void expectFailure(Class<? extends Throwable> type, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected " + type.getSimpleName());
        } catch (Throwable actual) {
            if (!type.isInstance(actual)) throw new AssertionError("wrong failure", actual);
        }
    }

    private static void deterministicChecks() {
        Components sample = findComponents(6,
                List.of(new Edge(0, 1), new Edge(1, 2), new Edge(3, 4)));
        group(sample.count() == 3, "sample count");
        group(sample.componentOf().equals(List.of(0, 0, 0, 1, 1, 2)), "sample ids");
        group(sample.members().equals(List.of(List.of(0, 1, 2), List.of(3, 4), List.of(5))),
                "sample members");
        group(sample.connected(0, 2) && !sample.connected(2, 3), "sample connectivity");
        group(sample.members().get(2).equals(List.of(5)), "isolated member");

        Components triangle = findComponents(3,
                List.of(new Edge(0, 1), new Edge(1, 2), new Edge(2, 0)));
        group(triangle.count() == 1, "cycle is one component");
        group(triangle.members().get(0).size() == 3, "cycle covers vertices");

        Components empty = findComponents(0, List.of());
        group(empty.count() == 0, "empty count");
        group(empty.componentOf().isEmpty() && empty.members().isEmpty(), "empty outputs");

        Components isolated = findComponents(3, List.of());
        group(isolated.count() == 3, "isolated count");
        group(isolated.componentOf().equals(List.of(0, 1, 2)), "isolated ids");

        Components repeated = findComponents(2,
                List.of(new Edge(0, 0), new Edge(0, 1), new Edge(0, 1)));
        group(repeated.count() == 1 && repeated.members().get(0).size() == 2,
                "loops and parallel edges");

        expectFailure(UnsupportedOperationException.class, () -> sample.componentOf().add(9));
        group(true, "ids immutable");
        expectFailure(UnsupportedOperationException.class, () -> sample.members().add(List.of()));
        group(true, "outer members immutable");
        expectFailure(UnsupportedOperationException.class, () -> sample.members().get(0).add(9));
        group(true, "inner members immutable");

        expectFailure(IllegalArgumentException.class, () -> findComponents(-1, List.of()));
        group(true, "negative V rejected");
        expectFailure(NullPointerException.class, () -> findComponents(1, null));
        group(true, "null edges rejected");
        expectFailure(NullPointerException.class,
                () -> findComponents(1, java.util.Arrays.asList((Edge) null)));
        group(true, "null edge rejected");
        expectFailure(IllegalArgumentException.class,
                () -> findComponents(2, List.of(new Edge(-1, 0))));
        group(true, "negative endpoint rejected");
        expectFailure(IllegalArgumentException.class,
                () -> findComponents(2, List.of(new Edge(0, 2))));
        group(true, "high endpoint rejected");
    }

    private static int find(int[] parent, int vertex) {
        while (parent[vertex] != vertex) {
            parent[vertex] = parent[parent[vertex]];
            vertex = parent[vertex];
        }
        return vertex;
    }

    private static void union(int[] parent, int[] size, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA == rootB) return;
        if (size[rootA] < size[rootB]) {
            int swap = rootA; rootA = rootB; rootB = swap;
        }
        parent[rootB] = rootA;
        size[rootA] += size[rootB];
    }

    private static void randomizedChecks() {
        Random random = new Random(0x434f4d504f4e454eL);
        for (int trial = 0; trial < 20_000; trial++) {
            int vertices = random.nextInt(24);
            int edgeCount = vertices == 0 ? 0 : random.nextInt(80);
            List<Edge> edges = new ArrayList<>(edgeCount);
            int[] parent = new int[vertices];
            int[] size = new int[vertices];
            for (int v = 0; v < vertices; v++) { parent[v] = v; size[v] = 1; }
            for (int i = 0; i < edgeCount; i++) {
                Edge edge = new Edge(random.nextInt(vertices), random.nextInt(vertices));
                edges.add(edge);
                union(parent, size, edge.u(), edge.v());
            }

            Components actual = findComponents(vertices, edges);
            int rootCount = 0;
            for (int v = 0; v < vertices; v++) if (find(parent, v) == v) rootCount++;
            check(actual.count() == rootCount, "union-find count parity");

            boolean pairParity = true;
            for (int a = 0; a < vertices; a++) {
                for (int b = 0; b < vertices; b++) {
                    pairParity &= actual.connected(a, b) == (find(parent, a) == find(parent, b));
                }
            }
            check(pairParity, "union-find pair parity");

            boolean[] seen = new boolean[vertices];
            boolean partition = true;
            for (int component = 0; component < actual.members().size(); component++) {
                for (int vertex : actual.members().get(component)) {
                    partition &= !seen[vertex] && actual.componentOf().get(vertex) == component;
                    seen[vertex] = true;
                }
            }
            for (boolean value : seen) partition &= value;
            check(partition, "partition coverage");

            boolean minimumRoots = true;
            for (List<Integer> member : actual.members()) {
                int minimum = member.stream().mapToInt(Integer::intValue).min().orElseThrow();
                minimumRoots &= member.get(0) == minimum;
            }
            check(minimumRoots, "ascending-root component ids");
        }
    }

    public static void main(String[] args) {
        deterministicChecks();
        randomizedChecks();
        require(groups == 20, "deterministic group count");
        require(randomizedChecks == 80_000, "randomized check count");
        System.out.printf("PASS GraphComponentsContracts groups=%d trials=20000 checks=%d%n",
                groups, randomizedChecks);
    }
}
