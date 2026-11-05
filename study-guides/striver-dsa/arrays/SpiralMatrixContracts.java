import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Dependency-free executable contracts for Spiral Matrix. */
public final class SpiralMatrixContracts {
    private static int groups;
    private static long checks;

    static List<Integer> spiralOrder(int[][] matrix) {
        validateRectangular(matrix);
        int rows = matrix.length;
        int columns = matrix[0].length;
        long cellCount = (long) rows * columns;
        if (cellCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("matrix has too many cells for a Java List");
        }

        List<Integer> order = new ArrayList<>((int) cellCount);
        int top = 0;
        int bottom = rows - 1;
        int left = 0;
        int right = columns - 1;

        while (top <= bottom && left <= right) {
            for (int column = left; column <= right; column++) {
                order.add(matrix[top][column]);
            }
            top++;

            for (int row = top; row <= bottom; row++) {
                order.add(matrix[row][right]);
            }
            right--;

            if (top <= bottom) {
                for (int column = right; column >= left; column--) {
                    order.add(matrix[bottom][column]);
                }
                bottom--;
            }

            if (left <= right) {
                for (int row = bottom; row >= top; row--) {
                    order.add(matrix[row][left]);
                }
                left++;
            }
        }
        return order;
    }

    private static void validateRectangular(int[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0] == null
                || matrix[0].length == 0) {
            throw new IllegalArgumentException("matrix must be non-empty and rectangular");
        }
        int columns = matrix[0].length;
        for (int row = 1; row < matrix.length; row++) {
            if (matrix[row] == null || matrix[row].length != columns) {
                throw new IllegalArgumentException("matrix must be non-empty and rectangular");
            }
        }
    }

    /** Independent baseline: walk cells with direction changes and a visited grid. */
    private static List<Integer> visitedSimulationOracle(int[][] matrix) {
        int rows = matrix.length;
        int columns = matrix[0].length;
        boolean[][] visited = new boolean[rows][columns];
        int[] rowStep = {0, 1, 0, -1};
        int[] columnStep = {1, 0, -1, 0};
        int direction = 0;
        int row = 0;
        int column = 0;
        List<Integer> order = new ArrayList<>(rows * columns);

        for (int count = 0; count < rows * columns; count++) {
            order.add(matrix[row][column]);
            visited[row][column] = true;
            int nextRow = row + rowStep[direction];
            int nextColumn = column + columnStep[direction];
            if (nextRow < 0 || nextRow >= rows || nextColumn < 0 || nextColumn >= columns
                    || visited[nextRow][nextColumn]) {
                direction = (direction + 1) % 4;
                nextRow = row + rowStep[direction];
                nextColumn = column + columnStep[direction];
            }
            row = nextRow;
            column = nextColumn;
        }
        return order;
    }

    private static int[][] deepCopy(int[][] matrix) {
        if (matrix == null) {
            return null;
        }
        int[][] copy = new int[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            copy[row] = matrix[row] == null ? null : matrix[row].clone();
        }
        return copy;
    }

    private static boolean sameRowIdentities(int[][] matrix, int[][] rowReferences) {
        if (matrix.length != rowReferences.length) {
            return false;
        }
        for (int row = 0; row < matrix.length; row++) {
            if (matrix[row] != rowReferences[row]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsEveryUniqueCell(List<Integer> order, int cells) {
        if (order.size() != cells) {
            return false;
        }
        Set<Integer> seen = new HashSet<>(order);
        if (seen.size() != cells) {
            return false;
        }
        for (int value = 0; value < cells; value++) {
            if (!seen.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean rejects(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void group(String name, boolean condition) {
        groups++;
        checks++;
        if (!condition) {
            throw new AssertionError("group failed: " + name);
        }
    }

    private static void deterministicEvidence() {
        group("official square", spiralOrder(new int[][] {
                {1, 2, 3}, {4, 5, 6}, {7, 8, 9}
        }).equals(List.of(1, 2, 3, 6, 9, 8, 7, 4, 5)));
        group("official rectangle", spiralOrder(new int[][] {
                {1, 2, 3, 4}, {5, 6, 7, 8}, {9, 10, 11, 12}
        }).equals(List.of(1, 2, 3, 4, 8, 12, 11, 10, 9, 5, 6, 7)));
        group("singleton", spiralOrder(new int[][] {{42}}).equals(List.of(42)));
        group("single row", spiralOrder(new int[][] {{1, 2, 3, 4}})
                .equals(List.of(1, 2, 3, 4)));
        group("single column", spiralOrder(new int[][] {{1}, {2}, {3}, {4}})
                .equals(List.of(1, 2, 3, 4)));
        group("two by two", spiralOrder(new int[][] {{1, 2}, {3, 4}})
                .equals(List.of(1, 2, 4, 3)));
        group("wide two by three", spiralOrder(new int[][] {{1, 2, 3}, {4, 5, 6}})
                .equals(List.of(1, 2, 3, 6, 5, 4)));
        group("tall three by two", spiralOrder(new int[][] {{1, 2}, {3, 4}, {5, 6}})
                .equals(List.of(1, 2, 4, 6, 5, 3)));
        group("four by four layers", spiralOrder(new int[][] {
                {1, 2, 3, 4}, {5, 6, 7, 8}, {9, 10, 11, 12}, {13, 14, 15, 16}
        }).equals(List.of(1, 2, 3, 4, 8, 12, 16, 15, 14, 13, 9, 5, 6, 7, 11, 10)));
        group("zero and negatives", spiralOrder(new int[][] {{0, -1}, {-2, -3}})
                .equals(List.of(0, -1, -3, -2)));
        group("duplicate values retained", spiralOrder(new int[][] {{7, 7, 7}, {7, 7, 7}})
                .equals(List.of(7, 7, 7, 7, 7, 7)));
        group("full-width values", spiralOrder(new int[][] {
                {Integer.MIN_VALUE, Integer.MAX_VALUE}
        }).equals(List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)));

        int[][] preserved = {{1, 2, 3}, {4, 5, 6}};
        int[][] snapshot = deepCopy(preserved);
        int[][] outer = preserved;
        int[][] rows = preserved.clone();
        List<Integer> result = spiralOrder(preserved);
        group("components preserved", Arrays.deepEquals(preserved, snapshot));
        group("outer identity preserved", preserved == outer);
        group("row identities preserved", sameRowIdentities(preserved, rows));
        group("output cardinality", result.size() == preserved.length * preserved[0].length);

        int[][] unique = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};
        group("every coordinate exactly once", containsEveryUniqueCell(
                spiralOrder(unique), unique.length * unique[0].length));
        group("null rejected", rejects(() -> spiralOrder(null)));
        group("empty shapes rejected", rejects(() -> spiralOrder(new int[0][]))
                && rejects(() -> spiralOrder(new int[][] {{}})));
        group("null or ragged rows rejected", rejects(
                () -> spiralOrder(new int[][] {{1, 2}, null}))
                && rejects(() -> spiralOrder(new int[][] {{1, 2}, {3}})));
    }

    private static int[][] randomMatrix(Random random) {
        int rows = 1 + random.nextInt(8);
        int columns = 1 + random.nextInt(8);
        int[][] matrix = new int[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int draw = random.nextInt(24);
                if (draw == 0) {
                    matrix[row][column] = Integer.MIN_VALUE;
                } else if (draw == 1) {
                    matrix[row][column] = Integer.MAX_VALUE;
                } else {
                    matrix[row][column] = random.nextInt(21) - 10;
                }
            }
        }
        return matrix;
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x5A1_2A1L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[][] matrix = randomMatrix(random);
            int[][] snapshot = deepCopy(matrix);
            int[][] outer = matrix;
            int[][] rowReferences = matrix.clone();
            List<Integer> expected = visitedSimulationOracle(matrix);
            List<Integer> actual = spiralOrder(matrix);

            checks++;
            if (!actual.equals(expected)) {
                throw new AssertionError("oracle mismatch at trial " + trial);
            }
            checks++;
            if (actual.size() != matrix.length * matrix[0].length) {
                throw new AssertionError("cardinality mismatch at trial " + trial);
            }
            checks++;
            if (!Arrays.deepEquals(matrix, snapshot)) {
                throw new AssertionError("matrix mutated at trial " + trial);
            }
            checks++;
            if (matrix != outer || !sameRowIdentities(matrix, rowReferences)) {
                throw new AssertionError("identity changed at trial " + trial);
            }
            checks++;
            if (!actual.isEmpty()) {
                int original = matrix[0][0];
                actual.set(0, original ^ 1);
                if (matrix[0][0] != original) {
                    throw new AssertionError("output aliases matrix at trial " + trial);
                }
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|SQUARE=1,2,3,6,9,8,7,4,5|RECT=1,2,3,4,8,12,11,10,9,5,6,7|THIN=true|PRESERVED=true");
        System.out.println("SpiralMatrixContracts: 20 scenario groups + 20000 seeded visited-simulation comparisons passed; checks=" + checks);
    }
}
