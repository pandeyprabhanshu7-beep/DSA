import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Executable contracts for G-5 breadth-first search. */
public final class GraphBfsContracts {
    record BfsResult(List<Integer> order, List<Integer> distance, List<Integer> parent) {}
    private static int groups;
    private static long randomizedChecks;

    static BfsResult bfs(List<List<Integer>> graph, int source) {
        validate(graph, source);
        int n = graph.size();
        int[] distance = new int[n];
        int[] parent = new int[n];
        Arrays.fill(distance, -1);
        Arrays.fill(parent, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        List<Integer> order = new ArrayList<>();
        distance[source] = 0;
        queue.addLast(source);
        while (!queue.isEmpty()) {
            int vertex = queue.removeFirst();
            order.add(vertex);
            for (int next : graph.get(vertex)) {
                if (distance[next] == -1) {
                    distance[next] = distance[vertex] + 1;
                    parent[next] = vertex;
                    queue.addLast(next);
                }
            }
        }
        return new BfsResult(List.copyOf(order), boxed(distance), boxed(parent));
    }

    private static List<Integer> boxed(int[] values) {
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) result.add(value);
        return List.copyOf(result);
    }

    private static void validate(List<List<Integer>> graph, int source) {
        Objects.requireNonNull(graph, "graph");
        if (source < 0 || source >= graph.size()) throw new IllegalArgumentException("bad source");
        for (List<Integer> bucket : graph) {
            Objects.requireNonNull(bucket, "null bucket");
            for (Integer next : bucket) {
                if (next == null || next < 0 || next >= graph.size()) {
                    throw new IllegalArgumentException("bad neighbor");
                }
            }
        }
    }

    private static void group(boolean value) { groups++; require(value); }
    private static void check(boolean value) { randomizedChecks++; require(value); }
    private static void require(boolean value) { if (!value) throw new AssertionError(); }
    private static void failure(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); throw new AssertionError(); }
        catch (Throwable actual) { if (!type.isInstance(actual)) throw new AssertionError(actual); }
    }

    private static void deterministic() {
        List<List<Integer>> graph = List.of(List.of(1, 2), List.of(3), List.of(4), List.of(), List.of());
        BfsResult from0 = bfs(graph, 0);
        group(from0.order().equals(List.of(0, 1, 2, 3, 4)));
        group(from0.distance().equals(List.of(0, 1, 1, 2, 2)));
        group(from0.parent().equals(List.of(-1, 0, 0, 1, 2)));
        BfsResult from3 = bfs(graph, 3);
        group(from3.order().equals(List.of(3)));
        group(from3.distance().equals(List.of(-1, -1, -1, 0, -1)));
        BfsResult reordered = bfs(List.of(List.of(2, 1), List.of(3), List.of(4), List.of(), List.of()), 0);
        group(reordered.order().equals(List.of(0, 2, 1, 4, 3)));
        group(reordered.distance().equals(from0.distance()));
        BfsResult cycle = bfs(List.of(List.of(1, 1), List.of(2), List.of(0)), 0);
        group(cycle.order().equals(List.of(0, 1, 2)));
        group(cycle.distance().equals(List.of(0, 1, 2)));
        group(bfs(List.of(List.of()), 0).order().equals(List.of(0)));
        failure(UnsupportedOperationException.class, () -> from0.order().add(9)); group(true);
        failure(UnsupportedOperationException.class, () -> from0.distance().add(9)); group(true);
        failure(NullPointerException.class, () -> bfs(null, 0)); group(true);
        failure(IllegalArgumentException.class, () -> bfs(List.of(), 0)); group(true);
        failure(IllegalArgumentException.class, () -> bfs(graph, -1)); group(true);
        failure(NullPointerException.class, () -> bfs(Arrays.asList((List<Integer>) null), 0)); group(true);
        failure(IllegalArgumentException.class, () -> bfs(List.of(List.of(1)), 0)); group(true);
        failure(IllegalArgumentException.class, () -> bfs(List.of(Arrays.asList((Integer) null)), 0)); group(true);
        group(from0.parent().get(0) == -1);
        group(from3.parent().stream().allMatch(x -> x == -1));
    }

    private static void randomized() {
        Random random = new Random(0x4252454144544846L);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = 1 + random.nextInt(20);
            List<List<Integer>> mutable = new ArrayList<>(n);
            boolean[][] edge = new boolean[n][n];
            for (int v = 0; v < n; v++) mutable.add(new ArrayList<>());
            int arcs = random.nextInt(70);
            for (int i = 0; i < arcs; i++) {
                int u = random.nextInt(n), v = random.nextInt(n);
                mutable.get(u).add(v); edge[u][v] = true;
            }
            int source = random.nextInt(n);
            BfsResult result = bfs(mutable, source);
            int[][] distance = new int[n][n];
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++)
                distance[i][j] = i == j ? 0 : edge[i][j] ? 1 : 1_000_000;
            for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) for (int j = 0; j < n; j++)
                distance[i][j] = Math.min(distance[i][j], distance[i][k] + distance[k][j]);
            boolean distanceParity = true, parentProof = true, unique = true, layers = true;
            boolean[] seen = new boolean[n]; int previous = -1;
            for (int v = 0; v < n; v++) {
                int expected = distance[source][v] >= 1_000_000 ? -1 : distance[source][v];
                distanceParity &= result.distance().get(v) == expected;
                if (v != source && expected >= 0) {
                    int p = result.parent().get(v);
                    parentProof &= p >= 0 && edge[p][v] && result.distance().get(p) + 1 == expected;
                }
            }
            for (int v : result.order()) {
                unique &= !seen[v]; seen[v] = true;
                layers &= result.distance().get(v) >= previous; previous = result.distance().get(v);
            }
            check(distanceParity); check(parentProof); check(unique); check(layers);
        }
    }

    public static void main(String[] args) {
        deterministic(); randomized();
        require(groups == 20); require(randomizedChecks == 80_000);
        System.out.printf("PASS GraphBfsContracts groups=%d trials=20000 checks=%d%n", groups, randomizedChecks);
    }
}
