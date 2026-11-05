import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable contracts for Set Matrix Zeroes. */
public final class SetMatrixZeroesContracts {
    private static int groups;
    private static long checks;

    static void setZeroes(int[][] matrix) {
        validateRectangular(matrix);
        int rows = matrix.length;
        int columns = matrix[0].length;

        boolean firstRowZero = false;
        for (int column = 0; column < columns; column++) {
            if (matrix[0][column] == 0) {
                firstRowZero = true;
                break;
            }
        }

        boolean firstColumnZero = false;
        for (int row = 0; row < rows; row++) {
            if (matrix[row][0] == 0) {
                firstColumnZero = true;
                break;
            }
        }

        for (int row = 1; row < rows; row++) {
            for (int column = 1; column < columns; column++) {
                if (matrix[row][column] == 0) {
                    matrix[row][0] = 0;
                    matrix[0][column] = 0;
                }
            }
        }

        for (int row = 1; row < rows; row++) {
            for (int column = 1; column < columns; column++) {
                if (matrix[row][0] == 0 || matrix[0][column] == 0) {
                    matrix[row][column] = 0;
                }
            }
        }

        if (firstRowZero) {
            Arrays.fill(matrix[0], 0);
        }
        if (firstColumnZero) {
            for (int row = 0; row < rows; row++) {
                matrix[row][0] = 0;
            }
        }
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

    /** Independent baseline with explicit row and column marker arrays. */
    private static int[][] markerArrayOracle(int[][] source) {
        int rows = source.length;
        int columns = source[0].length;
        boolean[] zeroRow = new boolean[rows];
        boolean[] zeroColumn = new boolean[columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (source[row][column] == 0) {
                    zeroRow[row] = true;
                    zeroColumn[column] = true;
                }
            }
        }
        int[][] result = deepCopy(source);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (zeroRow[row] || zeroColumn[column]) {
                    result[row][column] = 0;
                }
            }
        }
        return result;
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

    private static boolean exactSemantics(int[][] original, int[][] output) {
        int rows = original.length;
        int columns = original[0].length;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                boolean mustZero = false;
                for (int scanColumn = 0; scanColumn < columns; scanColumn++) {
                    mustZero |= original[row][scanColumn] == 0;
                }
                for (int scanRow = 0; scanRow < rows; scanRow++) {
                    mustZero |= original[scanRow][column] == 0;
                }
                int expected = mustZero ? 0 : original[row][column];
                if (output[row][column] != expected) {
                    return false;
                }
            }
        }
        return true;
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

    private static boolean sameShape(int[][] left, int[][] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int row = 0; row < left.length; row++) {
            if (left[row].length != right[row].length) {
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

    private static boolean transformsTo(int[][] input, int[][] expected) {
        setZeroes(input);
        return Arrays.deepEquals(input, expected);
    }

    private static void deterministicEvidence() {
        group("official center", transformsTo(
                new int[][] {{1, 1, 1}, {1, 0, 1}, {1, 1, 1}},
                new int[][] {{1, 0, 1}, {0, 0, 0}, {1, 0, 1}}));
        group("official border zeros", transformsTo(
                new int[][] {{0, 1, 2, 0}, {3, 4, 5, 2}, {1, 3, 1, 5}},
                new int[][] {{0, 0, 0, 0}, {0, 4, 5, 0}, {0, 3, 1, 0}}));
        group("no zeros", transformsTo(
                new int[][] {{1, 2}, {3, 4}}, new int[][] {{1, 2}, {3, 4}}));
        group("all zeros", transformsTo(
                new int[][] {{0, 0}, {0, 0}}, new int[][] {{0, 0}, {0, 0}}));
        group("singleton zero", transformsTo(new int[][] {{0}}, new int[][] {{0}}));
        group("singleton nonzero", transformsTo(new int[][] {{7}}, new int[][] {{7}}));
        group("single row", transformsTo(
                new int[][] {{5, 0, 7}}, new int[][] {{0, 0, 0}}));
        group("single column", transformsTo(
                new int[][] {{5}, {0}, {7}}, new int[][] {{0}, {0}, {0}}));
        group("first row marker", transformsTo(
                new int[][] {{1, 0, 3}, {4, 5, 6}, {7, 8, 9}},
                new int[][] {{0, 0, 0}, {4, 0, 6}, {7, 0, 9}}));
        group("first column marker", transformsTo(
                new int[][] {{1, 2, 3}, {0, 5, 6}, {7, 8, 9}},
                new int[][] {{0, 2, 3}, {0, 0, 0}, {0, 8, 9}}));
        group("top-left marker", transformsTo(
                new int[][] {{0, 2}, {3, 4}}, new int[][] {{0, 0}, {0, 4}}));
        group("multiple interior zeros", transformsTo(
                new int[][] {{1, 2, 3, 4}, {5, 0, 7, 8}, {9, 10, 0, 12}},
                new int[][] {{1, 0, 0, 4}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        group("full-width values", transformsTo(
                new int[][] {{Integer.MIN_VALUE, 1, 0}, {2, Integer.MAX_VALUE, 3}},
                new int[][] {{0, 0, 0}, {2, Integer.MAX_VALUE, 0}}));
        group("new zeros do not cascade", transformsTo(
                new int[][] {{1, 0, 3}, {4, 5, 6}, {7, 8, 9}},
                new int[][] {{0, 0, 0}, {4, 0, 6}, {7, 0, 9}}));

        int[][] identity = {{1, 2, 0}, {4, 5, 6}};
        int[][] outer = identity;
        int[][] rows = identity.clone();
        setZeroes(identity);
        group("outer identity preserved", identity == outer);
        group("row identities preserved", sameRowIdentities(identity, rows));

        group("null rejected", rejects(() -> setZeroes(null)));
        group("empty shapes rejected", rejects(() -> setZeroes(new int[0][]))
                && rejects(() -> setZeroes(new int[][] {{}})));
        group("null row rejected", rejects(() -> setZeroes(new int[][] {{1, 2}, null})));
        int[][] ragged = {{1, 0}, {3}};
        int[][] raggedSnapshot = deepCopy(ragged);
        group("ragged rejected before mutation", rejects(() -> setZeroes(ragged))
                && Arrays.deepEquals(ragged, raggedSnapshot));
    }

    private static int[][] randomMatrix(Random random) {
        int rows = 1 + random.nextInt(7);
        int columns = 1 + random.nextInt(7);
        int[][] matrix = new int[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int draw = random.nextInt(20);
                if (draw < 4) {
                    matrix[row][column] = 0;
                } else if (draw == 4) {
                    matrix[row][column] = Integer.MIN_VALUE;
                } else if (draw == 5) {
                    matrix[row][column] = Integer.MAX_VALUE;
                } else {
                    int value;
                    do {
                        value = random.nextInt(17) - 8;
                    } while (value == 0);
                    matrix[row][column] = value;
                }
            }
        }
        return matrix;
    }

    private static void randomizedEvidence() {
        Random random = new Random(0x5E7_2E20L);
        for (int trial = 0; trial < 20_000; trial++) {
            int[][] matrix = randomMatrix(random);
            int[][] original = deepCopy(matrix);
            int[][] expected = markerArrayOracle(original);
            int[][] outer = matrix;
            int[][] rowReferences = matrix.clone();

            setZeroes(matrix);
            checks++;
            if (!Arrays.deepEquals(matrix, expected)) {
                throw new AssertionError("oracle mismatch at trial " + trial);
            }
            checks++;
            if (!exactSemantics(original, matrix)) {
                throw new AssertionError("semantic mismatch at trial " + trial);
            }
            checks++;
            if (matrix != outer) {
                throw new AssertionError("outer identity changed at trial " + trial);
            }
            checks++;
            if (!sameRowIdentities(matrix, rowReferences)) {
                throw new AssertionError("row identity changed at trial " + trial);
            }
            checks++;
            if (!sameShape(matrix, original)) {
                throw new AssertionError("shape changed at trial " + trial);
            }
        }
    }

    public static void main(String[] args) {
        deterministicEvidence();
        randomizedEvidence();
        if (groups != 20) {
            throw new AssertionError("expected 20 scenario groups, got " + groups);
        }
        System.out.println("PARITY|CENTER=1:0:1/0:0:0/1:0:1|BORDER=0:0:0:0/0:4:5:0/0:3:1:0|SINGLE=true|IDENTITY=true");
        System.out.println("SetMatrixZeroesContracts: 20 scenario groups + 20000 seeded marker-array comparisons passed; checks=" + checks);
    }
}
