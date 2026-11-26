import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Executable contracts and independent oracles for G-11 undirected cycle BFS. */
public final class GraphUndirectedCycleBfsContracts {
    private record Arc(int to, int edgeId) {}
    private record State(int vertex, int parentEdgeId) {}

    /** Supports disconnected undirected multigraphs, including loops and parallel edges. */
    static boolean hasCycle(int vertices, int[][] edges) {
        validate(vertices, edges);
        List<List<Arc>> graph = buildGraph(vertices, edges);
        boolean[] visited = new boolean[vertices];
        Deque<State> queue = new ArrayDeque<>();

        for (int start = 0; start < vertices; start++) {
            if (visited[start]) continue;
            visited[start] = true;                    // claim before enqueue
            queue.addLast(new State(start, -1));

            while (!queue.isEmpty()) {
                State state = queue.removeFirst();
                for (Arc arc : graph.get(state.vertex())) {
                    if (!visited[arc.to()]) {
                        visited[arc.to()] = true;
                        queue.addLast(new State(arc.to(), arc.edgeId()));
                    } else if (arc.edgeId() != state.parentEdgeId()) {
                        return true;                   // a non-tree edge closes a cycle
                    }
                }
            }
        }
        return false;
    }

    private static void validate(int vertices, int[][] edges) {
        if (vertices < 0) throw new IllegalArgumentException("vertices must be nonnegative");
        if (edges == null) throw new IllegalArgumentException("edges must be non-null");
        for (int edgeId = 0; edgeId < edges.length; edgeId++) {
            int[] edge = edges[edgeId];
            if (edge == null || edge.length != 2) {
                throw new IllegalArgumentException("each edge must contain exactly two endpoints");
            }
            if (edge[0] < 0 || edge[0] >= vertices || edge[1] < 0 || edge[1] >= vertices) {
                throw new IllegalArgumentException("edge endpoint outside 0..vertices-1");
            }
        }
    }

    private static List<List<Arc>> buildGraph(int vertices, int[][] edges) {
        List<List<Arc>> graph = new ArrayList<>(vertices);
        for (int vertex = 0; vertex < vertices; vertex++) graph.add(new ArrayList<>());
        for (int edgeId = 0; edgeId < edges.length; edgeId++) {
            int left = edges[edgeId][0];
            int right = edges[edgeId][1];
            graph.get(left).add(new Arc(right, edgeId));
            graph.get(right).add(new Arc(left, edgeId));
        }
        return graph;
    }

    /** Independent recursive traversal with parent-edge identity. */
    private static boolean recursiveOracle(int vertices, int[][] edges) {
        List<List<Arc>> graph = buildGraph(vertices, edges);
        boolean[] visited = new boolean[vertices];
        for (int start = 0; start < vertices; start++) {
            if (!visited[start] && recursiveCycle(start, -1, graph, visited)) return true;
        }
        return false;
    }

    private static boolean recursiveCycle(int vertex, int parentEdgeId,
            List<List<Arc>> graph, boolean[] visited) {
        visited[vertex] = true;
        for (Arc arc : graph.get(vertex)) {
            if (arc.edgeId() == parentEdgeId) continue;
            if (visited[arc.to()] || recursiveCycle(arc.to(), arc.edgeId(), graph, visited)) {
                return true;
            }
        }
        return false;
    }

    /** Independent disjoint-set oracle: an edge inside one existing set closes a cycle. */
    private static boolean disjointSetOracle(int vertices, int[][] edges) {
        Dsu dsu = new Dsu(vertices);
        for (int[] edge : edges) {
            if (dsu.find(edge[0]) == dsu.find(edge[1])) return true;
            dsu.union(edge[0], edge[1]);
        }
        return false;
    }

    private static int[][] shuffledEdges(int[][] edges, Random random) {
        List<int[]> shuffled = new ArrayList<>(edges.length);
        for (int[] edge : edges) shuffled.add(Arrays.copyOf(edge, 2));
        Collections.shuffle(shuffled, random);
        return shuffled.toArray(int[][]::new);
    }

    private static int[][] relabelEdges(int vertices, int[][] edges, Random random) {
        int[] label = new int[vertices];
        for (int vertex = 0; vertex < vertices; vertex++) label[vertex] = vertex;
        for (int i = vertices - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temporary = label[i];
            label[i] = label[j];
            label[j] = temporary;
        }
        int[][] relabeled = new int[edges.length][2];
        for (int edgeId = 0; edgeId < edges.length; edgeId++) {
            relabeled[edgeId][0] = label[edges[edgeId][0]];
            relabeled[edgeId][1] = label[edges[edgeId][1]];
        }
        return relabeled;
    }

    private static int[][] deepCopy(int[][] edges) {
        int[][] copy = new int[edges.length][];
        for (int index = 0; index < edges.length; index++) {
            copy[index] = edges[index] == null ? null : Arrays.copyOf(edges[index], edges[index].length);
        }
        return copy;
    }

    private static void assertCycle(boolean expected, int vertices, int[][] edges, String label) {
        boolean actual = hasCycle(vertices, edges);
        if (actual != expected) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectFailure(int vertices, int[][] edges, String label) {
        try {
            hasCycle(vertices, edges);
            throw new AssertionError(label + " did not fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static int deterministicGroups() {
        int groups = 0;
        assertCycle(false, 0, new int[0][], "empty graph"); groups++;
        assertCycle(false, 1, new int[0][], "isolated singleton"); groups++;
        assertCycle(true, 1, new int[][]{{0, 0}}, "self-loop"); groups++;
        assertCycle(false, 2, new int[][]{{0, 1}}, "single edge"); groups++;
        assertCycle(true, 3, new int[][]{{0, 1}, {1, 2}, {2, 0}}, "triangle"); groups++;
        assertCycle(false, 4, new int[][]{{0, 1}, {1, 2}, {2, 3}}, "chain"); groups++;
        assertCycle(true, 6, new int[][]{{0, 1}, {2, 3}, {3, 4}, {4, 2}}, "disconnected cycle"); groups++;
        assertCycle(true, 2, new int[][]{{0, 1}, {0, 1}}, "parallel edges"); groups++;
        assertCycle(true, 5, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}, {3, 4}}, "square and tail"); groups++;

        int[][] forest = {{0, 1}, {0, 2}, {3, 4}};
        int[][] snapshot = deepCopy(forest);
        assertCycle(false, 6, forest, "disconnected forest");
        if (!Arrays.deepEquals(snapshot, forest)) throw new AssertionError("input mutation");
        groups++;

        int vertices = 50_000;
        int[][] chain = new int[vertices - 1][2];
        for (int vertex = 0; vertex + 1 < vertices; vertex++) {
            chain[vertex][0] = vertex;
            chain[vertex][1] = vertex + 1;
        }
        assertCycle(false, vertices, chain, "large iterative chain"); groups++;

        expectFailure(-1, new int[0][], "negative vertices"); groups++;
        expectFailure(1, null, "null edges"); groups++;
        expectFailure(1, new int[][]{null}, "null edge row"); groups++;
        expectFailure(1, new int[][]{{}}, "empty edge row"); groups++;
        expectFailure(2, new int[][]{{0}}, "one endpoint"); groups++;
        expectFailure(2, new int[][]{{0, 1, 0}}, "three endpoints"); groups++;
        expectFailure(2, new int[][]{{-1, 0}}, "negative endpoint"); groups++;
        expectFailure(2, new int[][]{{0, 2}}, "endpoint overflow"); groups++;
        expectFailure(3, new int[][]{{0, 1}, {2, 4}}, "later invalid endpoint"); groups++;
        return groups;
    }

    private static long randomizedChecks(int trials) {
        Random random = new Random(0xC1C1E5L);
        long checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int vertices = random.nextInt(9);
            int edgeCount = vertices == 0 ? 0 : random.nextInt(17);
            int[][] edges = new int[edgeCount][2];
            for (int edgeId = 0; edgeId < edgeCount; edgeId++) {
                edges[edgeId][0] = random.nextInt(vertices);
                edges[edgeId][1] = random.nextInt(vertices);
            }
            boolean actual = hasCycle(vertices, edges);
            if (actual != recursiveOracle(vertices, edges)) {
                throw new AssertionError("recursive parity trial " + trial);
            }
            checks++;
            if (actual != disjointSetOracle(vertices, edges)) {
                throw new AssertionError("DSU parity trial " + trial);
            }
            checks++;
            if (actual != hasCycle(vertices, shuffledEdges(edges, random))) {
                throw new AssertionError("edge-order invariance trial " + trial);
            }
            checks++;
            if (actual != hasCycle(vertices, relabelEdges(vertices, edges, random))) {
                throw new AssertionError("relabeling invariance trial " + trial);
            }
            checks++;
        }
        return checks;
    }

    private static final class Dsu {
        private final int[] parent;
        private final byte[] rank;

        Dsu(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int value = 0; value < size; value++) parent[value] = value;
        }

        int find(int value) {
            while (value != parent[value]) {
                parent[value] = parent[parent[value]];
                value = parent[value];
            }
            return value;
        }

        void union(int left, int right) {
            int a = find(left);
            int b = find(right);
            if (a == b) return;
            if (rank[a] < rank[b]) parent[a] = b;
            else if (rank[a] > rank[b]) parent[b] = a;
            else { parent[b] = a; rank[a]++; }
        }
    }

    public static void main(String[] args) {
        int groups = deterministicGroups();
        int trials = 20_000;
        long checks = randomizedChecks(trials);
        System.out.printf("PASS GraphUndirectedCycleBfsContracts groups=%d trials=%d checks=%d%n",
                groups, trials, checks);
    }
}
