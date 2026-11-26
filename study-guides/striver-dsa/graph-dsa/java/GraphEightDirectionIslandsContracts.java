import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.Random;

/** Executable contracts for G-8 eight-direction Number of Islands. */
public final class GraphEightDirectionIslandsContracts {
    record Cell(int row, int column) {}

    private static int groups;
    private static long randomizedChecks;

    static int countIslands(char[][] grid) {
        int columns = validate(grid);
        int rows = grid.length;
        boolean[][] seen = new boolean[rows][columns];
        Deque<Cell> queue = new ArrayDeque<>();
        int islands = 0;

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (grid[row][column] != '1' || seen[row][column]) continue;
                islands++;
                seen[row][column] = true;
                queue.addLast(new Cell(row, column));

                while (!queue.isEmpty()) {
                    Cell cell = queue.removeFirst();
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) continue;
                            int nextRow = cell.row() + dr;
                            int nextColumn = cell.column() + dc;
                            if (nextRow < 0 || nextRow >= rows
                                    || nextColumn < 0 || nextColumn >= columns
                                    || grid[nextRow][nextColumn] != '1'
                                    || seen[nextRow][nextColumn]) continue;
                            seen[nextRow][nextColumn] = true;
                            queue.addLast(new Cell(nextRow, nextColumn));
                        }
                    }
                }
            }
        }
        return islands;
    }

    private static int validate(char[][] grid) {
        Objects.requireNonNull(grid, "grid");
        int columns = grid.length == 0 ? 0 : rowLength(grid[0]);
        for (char[] row : grid) {
            if (row == null || row.length != columns) {
                throw new IllegalArgumentException("grid must be rectangular");
            }
            for (char value : row) {
                if (value != '0' && value != '1') {
                    throw new IllegalArgumentException("grid must contain only '0' and '1'");
                }
            }
        }
        return columns;
    }

    private static int rowLength(char[] row) {
        if (row == null) throw new IllegalArgumentException("null row");
        return row.length;
    }

    private static char[][] grid(String... rows) {
        char[][] result = new char[rows.length][];
        for (int row = 0; row < rows.length; row++) result[row] = rows[row].toCharArray();
        return result;
    }

    private static char[][] copy(char[][] source) {
        char[][] result = new char[source.length][];
        for (int row = 0; row < source.length; row++) {
            result[row] = source[row] == null ? null : source[row].clone();
        }
        return result;
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
        group(countIslands(grid("10", "01")) == 1, "diagonal cells connect");
        group(countIslands(grid("101", "000", "101")) == 4, "separated corners");
        group(countIslands(grid("000", "000")) == 0, "all water");
        group(countIslands(grid("111", "111")) == 1, "all land");
        group(countIslands(grid("1")) == 1, "single land");
        group(countIslands(grid("0")) == 0, "single water");
        group(countIslands(new char[0][]) == 0, "empty outer grid extension");
        group(countIslands(new char[][]{new char[0], new char[0]}) == 0,
                "zero-column grid extension");
        group(countIslands(grid("11101", "10000", "11101", "00011")) == 2,
                "Take U Forward sample");

        char[][] preserved = grid("101", "010", "001");
        char[][] snapshot = copy(preserved);
        countIslands(preserved);
        group(Arrays.deepEquals(preserved, snapshot), "input is not mutated");

        expectFailure(NullPointerException.class, () -> countIslands(null));
        group(true, "null grid rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countIslands(new char[][]{null}));
        group(true, "null first row rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countIslands(new char[][]{{'1'}, null}));
        group(true, "null later row rejected");
        expectFailure(IllegalArgumentException.class,
                () -> countIslands(new char[][]{{'1', '0'}, {'1'}}));
        group(true, "ragged grid rejected");
        expectFailure(IllegalArgumentException.class, () -> countIslands(grid("10", "0x")));
        group(true, "invalid symbol rejected");

        group(countIslands(grid("10101")) == 3, "one row alternation");
        group(countIslands(grid("1", "0", "1", "0", "1")) == 3,
                "one column alternation");
        group(countIslands(grid("1000", "0100", "0010", "0001")) == 1,
                "long diagonal chain");
        group(countIslands(grid("11000", "00010", "00101")) == 2,
                "rectangular diagonal merge");
        group(countIslands(grid("101", "010", "101")) == 1,
                "checkerboard joins through diagonals");
    }

    private static int find(int[] parent, int cell) {
        while (parent[cell] != cell) {
            parent[cell] = parent[parent[cell]];
            cell = parent[cell];
        }
        return cell;
    }

    private static int dsuOracle(char[][] grid) {
        int rows = grid.length;
        int columns = rows == 0 ? 0 : grid[0].length;
        int cells = rows * columns;
        int[] parent = new int[cells];
        int[] size = new int[cells];
        int components = 0;
        for (int cell = 0; cell < cells; cell++) { parent[cell] = cell; size[cell] = 1; }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (grid[row][column] == '1') components++;
            }
        }
        int[][] previous = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}};
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (grid[row][column] != '1') continue;
                int cell = row * columns + column;
                for (int[] direction : previous) {
                    int nextRow = row + direction[0];
                    int nextColumn = column + direction[1];
                    if (nextRow < 0 || nextColumn < 0 || nextColumn >= columns
                            || grid[nextRow][nextColumn] != '1') continue;
                    int a = find(parent, cell);
                    int b = find(parent, nextRow * columns + nextColumn);
                    if (a == b) continue;
                    if (size[a] < size[b]) { int swap = a; a = b; b = swap; }
                    parent[b] = a;
                    size[a] += size[b];
                    components--;
                }
            }
        }
        return components;
    }

    private static int dfsOracle(char[][] grid) {
        int rows = grid.length;
        int columns = rows == 0 ? 0 : grid[0].length;
        boolean[][] seen = new boolean[rows][columns];
        int islands = 0;
        for (int row = rows - 1; row >= 0; row--) {
            for (int column = columns - 1; column >= 0; column--) {
                if (grid[row][column] == '1' && !seen[row][column]) {
                    islands++;
                    visit(grid, seen, row, column);
                }
            }
        }
        return islands;
    }

    private static void visit(char[][] grid, boolean[][] seen, int row, int column) {
        if (row < 0 || row >= grid.length || column < 0 || column >= grid[0].length
                || grid[row][column] != '1' || seen[row][column]) return;
        seen[row][column] = true;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr != 0 || dc != 0) visit(grid, seen, row + dr, column + dc);
            }
        }
    }

    private static char[][] rotate180(char[][] grid) {
        int rows = grid.length;
        int columns = rows == 0 ? 0 : grid[0].length;
        char[][] result = new char[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                result[rows - 1 - row][columns - 1 - column] = grid[row][column];
            }
        }
        return result;
    }

    private static void randomizedChecks() {
        Random random = new Random(0x3844495249534c4eL);
        for (int trial = 0; trial < 20_000; trial++) {
            int rows = random.nextInt(11);
            int columns = rows == 0 ? 0 : random.nextInt(11);
            char[][] candidate = new char[rows][columns];
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    candidate[row][column] = random.nextInt(3) == 0 ? '1' : '0';
                }
            }
            char[][] snapshot = copy(candidate);
            int actual = countIslands(candidate);
            check(actual == dsuOracle(candidate), "DSU parity");
            check(actual == dfsOracle(candidate), "reverse-scan recursive DFS parity");
            check(countIslands(rotate180(candidate)) == actual, "rotation invariance");
            check(Arrays.deepEquals(candidate, snapshot), "nonmutation");
        }
    }

    public static void main(String[] args) {
        deterministicChecks();
        randomizedChecks();
        require(groups == 20, "deterministic group count");
        require(randomizedChecks == 80_000, "randomized check count");
        System.out.printf("PASS GraphEightDirectionIslandsContracts groups=%d trials=20000 checks=%d%n",
                groups, randomizedChecks);
    }
}
