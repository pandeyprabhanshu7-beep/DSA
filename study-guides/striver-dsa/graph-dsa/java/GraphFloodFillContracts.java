import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;

/** Executable contracts and independent oracles for G-9 four-direction Flood Fill. */
public final class GraphFloodFillContracts {
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    /**
     * Recolors the source's four-direction connected component in place and returns image.
     * The image must be non-null, non-empty, rectangular, and contain the start cell.
     */
    static int[][] floodFillInPlace(int[][] image, int sr, int sc, int newColor) {
        int cols = validate(image, sr, sc);
        int oldColor = image[sr][sc];
        if (oldColor == newColor) return image;

        Deque<Integer> queue = new ArrayDeque<>();
        image[sr][sc] = newColor;             // claim before scheduling
        queue.addLast(sr * cols + sc);

        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int row = cell / cols;
            int col = cell % cols;
            for (int direction = 0; direction < 4; direction++) {
                int nextRow = row + DR[direction];
                int nextCol = col + DC[direction];
                if (nextRow >= 0 && nextRow < image.length
                        && nextCol >= 0 && nextCol < cols
                        && image[nextRow][nextCol] == oldColor) {
                    image[nextRow][nextCol] = newColor;
                    queue.addLast(nextRow * cols + nextCol);
                }
            }
        }
        return image;
    }

    /** Preservation wrapper for callers that do not authorize mutation. */
    static int[][] floodFillCopy(int[][] image, int sr, int sc, int newColor) {
        validate(image, sr, sc);
        int[][] copy = deepCopy(image);
        return floodFillInPlace(copy, sr, sc, newColor);
    }

    private static int validate(int[][] image, int sr, int sc) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("image must contain at least one row");
        }
        if (image[0] == null || image[0].length == 0) {
            throw new IllegalArgumentException("image rows must be non-null and non-empty");
        }
        int cols = image[0].length;
        for (int row = 0; row < image.length; row++) {
            if (image[row] == null || image[row].length != cols) {
                throw new IllegalArgumentException("image must be rectangular");
            }
        }
        if (sr < 0 || sr >= image.length || sc < 0 || sc >= cols) {
            throw new IllegalArgumentException("start cell is outside the image");
        }
        return cols;
    }

    private static int[][] recursiveOracle(int[][] source, int sr, int sc, int newColor) {
        int[][] result = deepCopy(source);
        int oldColor = result[sr][sc];
        if (oldColor != newColor) recursivePaint(result, sr, sc, oldColor, newColor);
        return result;
    }

    private static void recursivePaint(int[][] image, int row, int col, int oldColor, int newColor) {
        if (row < 0 || row == image.length || col < 0 || col == image[0].length
                || image[row][col] != oldColor) return;
        image[row][col] = newColor;
        recursivePaint(image, row - 1, col, oldColor, newColor);
        recursivePaint(image, row + 1, col, oldColor, newColor);
        recursivePaint(image, row, col - 1, oldColor, newColor);
        recursivePaint(image, row, col + 1, oldColor, newColor);
    }

    private static int[][] disjointSetOracle(int[][] source, int sr, int sc, int newColor) {
        int rows = source.length;
        int cols = source[0].length;
        int oldColor = source[sr][sc];
        Dsu dsu = new Dsu(rows * cols);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (source[row][col] != oldColor) continue;
                if (row + 1 < rows && source[row + 1][col] == oldColor) {
                    dsu.union(row * cols + col, (row + 1) * cols + col);
                }
                if (col + 1 < cols && source[row][col + 1] == oldColor) {
                    dsu.union(row * cols + col, row * cols + col + 1);
                }
            }
        }
        int sourceRoot = dsu.find(sr * cols + sc);
        int[][] result = deepCopy(source);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int id = row * cols + col;
                if (source[row][col] == oldColor && dsu.find(id) == sourceRoot) {
                    result[row][col] = newColor;
                }
            }
        }
        return result;
    }

    private static int[][] deepCopy(int[][] image) {
        int[][] copy = new int[image.length][];
        for (int row = 0; row < image.length; row++) {
            copy[row] = Arrays.copyOf(image[row], image[row].length);
        }
        return copy;
    }

    private static void assertGridEquals(int[][] expected, int[][] actual, String label) {
        if (!Arrays.deepEquals(expected, actual)) {
            throw new AssertionError(label + " expected=" + Arrays.deepToString(expected)
                    + " actual=" + Arrays.deepToString(actual));
        }
    }

    private static void expectFailure(int[][] image, int sr, int sc, String label) {
        try {
            floodFillInPlace(image, sr, sc, 9);
            throw new AssertionError(label + " did not fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static int deterministicGroups() {
        int groups = 0;
        int[][] standard = {{1, 1, 1}, {1, 1, 0}, {1, 0, 1}};
        assertGridEquals(new int[][]{{2, 2, 2}, {2, 2, 0}, {2, 0, 1}},
                floodFillCopy(standard, 1, 1, 2), "standard"); groups++;
        assertGridEquals(new int[][]{{7, 0}, {0, 1}},
                floodFillCopy(new int[][]{{1, 0}, {0, 1}}, 0, 0, 7), "diagonal excluded"); groups++;

        int[][] unchanged = {{4, 4}, {4, 3}};
        int[][] sameReference = floodFillInPlace(unchanged, 0, 0, 4);
        if (sameReference != unchanged) throw new AssertionError("same-color reference"); groups++;
        assertGridEquals(new int[][]{{-5}}, floodFillCopy(new int[][]{{2}}, 0, 0, -5), "singleton"); groups++;
        assertGridEquals(new int[][]{{8, 8, 3, 1}},
                floodFillCopy(new int[][]{{1, 1, 3, 1}}, 0, 0, 8), "one row"); groups++;
        assertGridEquals(new int[][]{{8}, {8}, {3}, {1}},
                floodFillCopy(new int[][]{{1}, {1}, {3}, {1}}, 0, 0, 8), "one column"); groups++;
        assertGridEquals(new int[][]{{9, 9}, {-1, 9}},
                floodFillCopy(new int[][]{{-2, -2}, {-1, -2}}, 0, 0, 9), "negative colors"); groups++;
        assertGridEquals(new int[][]{{6, 6}, {6, 6}},
                floodFillCopy(new int[][]{{0, 0}, {0, 0}}, 1, 1, 6), "all connected"); groups++;
        assertGridEquals(new int[][]{{5, 2, 5}, {2, 2, 2}, {5, 2, 5}},
                floodFillCopy(new int[][]{{5, 2, 5}, {2, 2, 2}, {5, 2, 5}}, 1, 1, 2), "same-color no-op"); groups++;

        int[][] preserved = {{1, 1}, {1, 0}};
        int[][] preservedSnapshot = deepCopy(preserved);
        int[][] copiedResult = floodFillCopy(preserved, 0, 0, 3);
        assertGridEquals(preservedSnapshot, preserved, "copy wrapper preserves input");
        if (copiedResult == preserved || copiedResult[0] == preserved[0]) {
            throw new AssertionError("copy wrapper aliases input");
        }
        groups++;

        expectFailure(null, 0, 0, "null"); groups++;
        expectFailure(new int[0][], 0, 0, "empty"); groups++;
        expectFailure(new int[][]{{}}, 0, 0, "empty row"); groups++;
        expectFailure(new int[][]{{1}, {1, 2}}, 0, 0, "ragged"); groups++;
        expectFailure(new int[][]{{1}, null}, 0, 0, "null row"); groups++;
        expectFailure(new int[][]{{1}}, -1, 0, "negative row"); groups++;
        expectFailure(new int[][]{{1}}, 0, -1, "negative column"); groups++;
        expectFailure(new int[][]{{1}}, 1, 0, "row overflow"); groups++;
        expectFailure(new int[][]{{1}}, 0, 1, "column overflow"); groups++;

        int[][] inPlace = {{3, 3}, {0, 3}};
        if (floodFillInPlace(inPlace, 0, 0, 7) != inPlace) {
            throw new AssertionError("in-place method must return image");
        }
        assertGridEquals(new int[][]{{7, 7}, {0, 7}}, inPlace, "in-place ownership"); groups++;
        return groups;
    }

    private static long randomizedChecks(int trials) {
        Random random = new Random(0xF100DF11L);
        long checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int rows = 1 + random.nextInt(7);
            int cols = 1 + random.nextInt(7);
            int[][] source = new int[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    source[row][col] = random.nextInt(6) - 2;
                }
            }
            int sr = random.nextInt(rows);
            int sc = random.nextInt(cols);
            int newColor = random.nextInt(8) - 3;
            int[][] snapshot = deepCopy(source);

            int[][] actual = floodFillCopy(source, sr, sc, newColor);
            int[][] recursive = recursiveOracle(source, sr, sc, newColor);
            int[][] dsu = disjointSetOracle(source, sr, sc, newColor);
            assertGridEquals(recursive, actual, "recursive parity trial " + trial); checks++;
            assertGridEquals(dsu, actual, "DSU parity trial " + trial); checks++;
            assertGridEquals(snapshot, source, "copy nonmutation trial " + trial); checks++;

            int[][] idempotent = deepCopy(actual);
            floodFillInPlace(idempotent, sr, sc, newColor);
            assertGridEquals(actual, idempotent, "idempotence trial " + trial); checks++;
        }
        return checks;
    }

    private static final class Dsu {
        private final int[] parent;
        private final byte[] rank;

        Dsu(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int i = 0; i < size; i++) parent[i] = i;
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
        System.out.printf("PASS GraphFloodFillContracts groups=%d trials=%d checks=%d%n",
                groups, trials, checks);
    }
}
