import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.Random;

/** Executable contracts for G-7 Number of Provinces on a binary symmetric matrix. */
public final class GraphProvincesContracts {
    private static int groups;
    private static long randomizedChecks;

    static int countProvinces(int[][] isConnected) {
        validate(isConnected);
        int n = isConnected.length;
        boolean[] seen = new boolean[n];
        Deque<Integer> stack = new ArrayDeque<>();
        int provinces = 0;

        for (int root = 0; root < n; root++) {
            if (seen[root]) continue;
            provinces++;
            seen[root] = true;
            stack.push(root);

            while (!stack.isEmpty()) {
                int city = stack.pop();
                for (int neighbor = 0; neighbor < n; neighbor++) {
                    if (isConnected[city][neighbor] == 1 && !seen[neighbor]) {
                        seen[neighbor] = true;
                        stack.push(neighbor);
                    }
                }
            }
        }
        return provinces;
    }

    private static void validate(int[][] matrix) {
        Objects.requireNonNull(matrix, "isConnected");
        int n = matrix.length;
        for (int row = 0; row < n; row++) {
            if (matrix[row] == null || matrix[row].length != n) {
                throw new IllegalArgumentException("matrix must be square");
            }
            for (int column = 0; column < n; column++) {
                int value = matrix[row][column];
                if (value != 0 && value != 1) {
                    throw new IllegalArgumentException("matrix must be binary");
                }
                if (column < row && value != matrix[column][row]) {
                    throw new IllegalArgumentException("matrix must be symmetric");
                }
            }
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

    private static int[][] copy(int[][] matrix) {
        int[][] result = new int[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            result[row] = matrix[row] == null ? null : matrix[row].clone();
        }
        return result;
    }

    private static void deterministicChecks() {
        group(countProvinces(new int[][]{{1, 1, 0}, {1, 1, 0}, {0, 0, 1}}) == 2,
                "canonical two provinces");
        group(countProvinces(new int[][]{{1, 1, 0, 0}, {1, 1, 1, 0},
                {0, 1, 1, 1}, {0, 0, 1, 1}}) == 1, "transitive chain");
        group(countProvinces(new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}) == 3,
                "identity means isolated cities");
        group(countProvinces(new int[][]{{0, 1, 0}, {1, 0, 0}, {0, 0, 0}}) == 2,
                "diagonal is irrelevant");
        group(countProvinces(new int[0][0]) == 0, "empty extension");
        group(countProvinces(new int[][]{{0}}) == 1, "single city zero diagonal");
        group(countProvinces(new int[][]{{1, 1}, {1, 1}}) == 1, "dense pair");
        group(countProvinces(new int[][]{{1, 1, 0, 0, 0}, {1, 1, 0, 0, 0},
                {0, 0, 1, 1, 0}, {0, 0, 1, 1, 0}, {0, 0, 0, 0, 1}}) == 3,
                "three components");

        int[][] preserved = {{1, 1, 0}, {1, 1, 0}, {0, 0, 1}};
        int[][] snapshot = copy(preserved);
        countProvinces(preserved);
        group(Arrays.deepEquals(preserved, snapshot), "input is not mutated");

        expectFailure(NullPointerException.class, () -> countProvinces(null));
        group(true, "null matrix rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countProvinces(new int[][]{null}));
        group(true, "null row rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countProvinces(new int[][]{{1, 0}, {0}}));
        group(true, "ragged matrix rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countProvinces(new int[][]{{1, 2}, {2, 1}}));
        group(true, "high nonbinary value rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countProvinces(new int[][]{{1, 1}, {0, 1}}));
        group(true, "asymmetry rejected");
        group(countProvinces(new int[][]{{0, 0}, {0, 0}}) == 2,
                "all-zero diagonal accepted");
        expectFailure(IllegalArgumentException.class,
                () -> countProvinces(new int[][]{{1, -1}, {-1, 1}}));
        group(true, "negative nonbinary value rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countProvinces(new int[][]{{1, 0, 1}, {0, 1, 0}}));
        group(true, "rectangular matrix rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countProvinces(new int[][]{{1, 0}, {0, 1}, {0, 0}}));
        group(true, "too many rows rejected");
        group(countProvinces(new int[][]{{0, 1, 1}, {1, 0, 1}, {1, 1, 0}}) == 1,
                "dense zero-diagonal graph");
        group(countProvinces(new int[][]{{1, 0, 0, 1}, {0, 1, 1, 0},
                {0, 1, 1, 0}, {1, 0, 0, 1}}) == 2, "interleaved labels");
    }

    private static int find(int[] parent, int vertex) {
        while (parent[vertex] != vertex) {
            parent[vertex] = parent[parent[vertex]];
            vertex = parent[vertex];
        }
        return vertex;
    }

    private static int unionFindOracle(int[][] matrix) {
        int n = matrix.length;
        int[] parent = new int[n];
        int[] size = new int[n];
        for (int city = 0; city < n; city++) { parent[city] = city; size[city] = 1; }
        int components = n;
        for (int row = 0; row < n; row++) {
            for (int column = row + 1; column < n; column++) {
                if (matrix[row][column] == 0) continue;
                int a = find(parent, row);
                int b = find(parent, column);
                if (a == b) continue;
                if (size[a] < size[b]) { int swap = a; a = b; b = swap; }
                parent[b] = a;
                size[a] += size[b];
                components--;
            }
        }
        return components;
    }

    private static int bfsOracle(int[][] matrix) {
        boolean[] seen = new boolean[matrix.length];
        Deque<Integer> queue = new ArrayDeque<>();
        int components = 0;
        for (int root = matrix.length - 1; root >= 0; root--) {
            if (seen[root]) continue;
            components++;
            seen[root] = true;
            queue.addLast(root);
            while (!queue.isEmpty()) {
                int city = queue.removeFirst();
                for (int next = matrix.length - 1; next >= 0; next--) {
                    if (matrix[city][next] == 1 && !seen[next]) {
                        seen[next] = true;
                        queue.addLast(next);
                    }
                }
            }
        }
        return components;
    }

    private static int[][] permute(int[][] matrix, int[] permutation) {
        int n = matrix.length;
        int[][] result = new int[n][n];
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                result[row][column] = matrix[permutation[row]][permutation[column]];
            }
        }
        return result;
    }

    private static void randomizedChecks() {
        Random random = new Random(0x50524f56494e4345L);
        for (int trial = 0; trial < 20_000; trial++) {
            int n = random.nextInt(20);
            int[][] matrix = new int[n][n];
            for (int row = 0; row < n; row++) {
                matrix[row][row] = random.nextBoolean() ? 1 : 0;
                for (int column = row + 1; column < n; column++) {
                    int edge = random.nextInt(4) == 0 ? 1 : 0;
                    matrix[row][column] = edge;
                    matrix[column][row] = edge;
                }
            }

            int actual = countProvinces(matrix);
            check(actual == unionFindOracle(matrix), "union-find count parity");
            check(actual == bfsOracle(matrix), "reverse-order BFS parity");

            int[] permutation = new int[n];
            for (int city = 0; city < n; city++) permutation[city] = city;
            for (int city = n - 1; city > 0; city--) {
                int other = random.nextInt(city + 1);
                int swap = permutation[city]; permutation[city] = permutation[other];
                permutation[other] = swap;
            }
            check(countProvinces(permute(matrix, permutation)) == actual,
                    "label permutation invariance");

            int[][] toggledDiagonal = copy(matrix);
            for (int city = 0; city < n; city++) toggledDiagonal[city][city] ^= 1;
            check(countProvinces(toggledDiagonal) == actual, "diagonal invariance");
        }
    }

    public static void main(String[] args) {
        deterministicChecks();
        randomizedChecks();
        require(groups == 20, "deterministic group count");
        require(randomizedChecks == 80_000, "randomized check count");
        System.out.printf("PASS GraphProvincesContracts groups=%d trials=20000 checks=%d%n",
                groups, randomizedChecks);
    }
}
