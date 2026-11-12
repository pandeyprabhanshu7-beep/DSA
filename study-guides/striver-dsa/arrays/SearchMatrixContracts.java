import java.util.Arrays;
import java.util.Random;

/** Dependency-free executable evidence for Arrays problem 47. */
public final class SearchMatrixContracts {
    private static int groups;
    private static long randomizedChecks;

    private SearchMatrixContracts() {}

    /** Validated reusable boundary; the official problem already guarantees this shape. */
    static boolean searchMatrix(int[][] matrix, int target) {
        validateGlobalRowMajorOrder(matrix);
        return searchMatrixCore(matrix, target);
    }

    /** Trusted-input logarithmic core over virtual row-major indices. */
    static boolean searchMatrixCore(int[][] matrix, int target) {
        return searchMatrixInspected(matrix, target).found();
    }

    static SearchResult searchMatrixInspected(int[][] matrix, int target) {
        int columns = matrix[0].length;
        long left = 0;
        long right = (long) matrix.length * columns - 1;
        int inspections = 0;
        while (left <= right) {
            long middle = left + (right - left) / 2;
            int value = matrix[(int) (middle / columns)][(int) (middle % columns)];
            inspections++;
            if (value == target) return new SearchResult(true, inspections);
            if (value < target) left = middle + 1;
            else right = middle - 1;
        }
        return new SearchResult(false, inspections);
    }

    /** Linear validation is intentionally separate from the O(log(m*n)) core. */
    static void validateGlobalRowMajorOrder(int[][] matrix) {
        if (matrix == null) throw new IllegalArgumentException("matrix must be non-null");
        if (matrix.length == 0) throw new IllegalArgumentException("matrix must have a row");
        if (matrix[0] == null || matrix[0].length == 0) {
            throw new IllegalArgumentException("matrix rows must be non-null and non-empty");
        }
        int columns = matrix[0].length;
        for (int row = 0; row < matrix.length; row++) {
            if (matrix[row] == null || matrix[row].length != columns) {
                throw new IllegalArgumentException("matrix must be rectangular");
            }
            for (int column = 1; column < columns; column++) {
                if (matrix[row][column - 1] > matrix[row][column]) {
                    throw new IllegalArgumentException("each row must be non-decreasing");
                }
            }
            if (row > 0 && matrix[row][0] <= matrix[row - 1][columns - 1]) {
                throw new IllegalArgumentException("rows must be globally ordered");
            }
        }
    }

    static boolean linearOracle(int[][] matrix, int target) {
        for (int[] row : matrix) {
            for (int value : row) if (value == target) return true;
        }
        return false;
    }

    record SearchResult(boolean found, int inspections) {}

    public static void main(String[] args) {
        deterministicScenarios();
        randomizedOracleChecks();
        System.out.println("SearchMatrixContracts: " + groups
                + " scenario groups + 20000 seeded linear-oracle trials passed; checks="
                + randomizedChecks);
    }

    private static void deterministicScenarios() {
        int[][] official = {{1, 3, 5, 7}, {10, 11, 16, 20}, {23, 30, 34, 60}};
        group("official present example", () -> checkTrue(searchMatrix(official, 3)));
        group("official absent example", () -> checkFalse(searchMatrix(official, 13)));
        group("singleton hit and miss", () -> {
            checkTrue(searchMatrix(new int[][] {{7}}, 7));
            checkFalse(searchMatrix(new int[][] {{7}}, -7));
        });
        group("one row with duplicates", () -> {
            checkTrue(searchMatrix(new int[][] {{1, 2, 2, 4, 9}}, 2));
            checkFalse(searchMatrix(new int[][] {{1, 2, 2, 4, 9}}, 3));
        });
        group("one column", () -> checkTrue(searchMatrix(
                new int[][] {{-8}, {-1}, {5}, {12}}, 5)));
        group("negative values", () -> checkFalse(searchMatrix(
                new int[][] {{-20, -10}, {-3, -1}, {4, 8}}, -2)));
        group("full int values", () -> checkTrue(searchMatrix(new int[][] {
                {Integer.MIN_VALUE, -1}, {0, Integer.MAX_VALUE}}, Integer.MAX_VALUE)));
        group("first and last values", () -> {
            checkTrue(searchMatrix(official, 1));
            checkTrue(searchMatrix(official, 60));
        });
        group("input preservation", () -> {
            int[][] input = {{1, 2, 2}, {4, 8, 9}};
            int[][] before = deepCopy(input);
            searchMatrix(input, 8);
            if (!Arrays.deepEquals(before, input)) throw new AssertionError("input mutated");
        });
        group("validated-core parity", () -> {
            int[][] input = {{1, 4, 7}, {9, 12, 15}};
            eq(searchMatrixCore(input, 12), searchMatrix(input, 12));
        });
        group("independent deterministic oracle", () -> {
            int[][] input = {{-5, -5, -2}, {0, 3, 6}};
            eq(linearOracle(input, -5), searchMatrix(input, -5));
        });
        group("logarithmic inspection bound", () -> {
            SearchResult result = searchMatrixInspected(official, 13);
            checkFalse(result.found());
            if (result.inspections() > maxBinaryInspections(12)) {
                throw new AssertionError("too many inspections");
            }
        });
        group("null rejection", () -> expectIllegal(() -> searchMatrix(null, 0)));
        group("zero rows rejection", () -> expectIllegal(
                () -> searchMatrix(new int[0][], 0)));
        group("zero columns rejection", () -> expectIllegal(
                () -> searchMatrix(new int[][] {{}}, 0)));
        group("ragged rejection", () -> expectIllegal(
                () -> searchMatrix(new int[][] {{1, 2}, {3}}, 3)));
        group("null row rejection", () -> expectIllegal(
                () -> searchMatrix(new int[][] {{1, 2}, null}, 2)));
        group("row-order rejection", () -> expectIllegal(
                () -> searchMatrix(new int[][] {{1, 3, 2}}, 2)));
        group("cross-row overlap rejection", () -> expectIllegal(
                () -> searchMatrix(new int[][] {{1, 5}, {4, 8}}, 4)));
        group("cross-row equality rejection", () -> expectIllegal(
                () -> searchMatrix(new int[][] {{1, 5}, {5, 8}}, 5)));
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x742026L);
        for (int trial = 0; trial < 20_000; trial++) {
            int rows = 1 + random.nextInt(10);
            int columns = 1 + random.nextInt(10);
            int[][] matrix = new int[rows][columns];
            int value = -1_000 + random.nextInt(201);
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    if (row != 0 || column != 0) {
                        value += column == 0 ? 1 + random.nextInt(4) : random.nextInt(4);
                    }
                    matrix[row][column] = value;
                }
            }
            int target;
            if (random.nextBoolean()) {
                target = matrix[random.nextInt(rows)][random.nextInt(columns)];
            } else {
                target = -1_100 + random.nextInt(2_500);
            }
            int[][] before = deepCopy(matrix);
            SearchResult inspected = searchMatrixInspected(matrix, target);
            boolean actual = searchMatrix(matrix, target);
            check(actual == linearOracle(matrix, target), "oracle mismatch");
            check(Arrays.deepEquals(before, matrix), "input mutated");
            check(actual == searchMatrixCore(matrix, target), "wrapper/core mismatch");
            check(actual == inspected.found(), "instrumented search mismatch");
            check(inspected.inspections() <= maxBinaryInspections((long) rows * columns),
                    "inspection bound exceeded");
        }
    }

    private static int maxBinaryInspections(long count) {
        return count == 0 ? 0 : 64 - Long.numberOfLeadingZeros(count);
    }

    private static int[][] deepCopy(int[][] matrix) {
        int[][] result = new int[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            result[row] = matrix[row] == null ? null : matrix[row].clone();
        }
        return result;
    }

    private static void group(String name, Runnable scenario) {
        scenario.run();
        groups++;
    }

    private static void eq(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void checkTrue(boolean value) {
        if (!value) throw new AssertionError("expected true");
    }

    private static void checkFalse(boolean value) {
        if (value) throw new AssertionError("expected false");
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        randomizedChecks++;
        if (!condition) throw new AssertionError(message);
    }
}
