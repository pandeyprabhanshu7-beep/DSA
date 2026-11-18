import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

/** Executable contracts for the Section 16 Matrix / 2D array family. */
public final class Matrix2DPatternContracts {
    private Matrix2DPatternContracts() {}

    /** Rotate one non-empty square matrix 90 degrees clockwise in place. */
    public static void rotateClockwise(int[][] matrix) {
        int n = validateRectangular(matrix);
        if (matrix.length != n) throw new IllegalArgumentException("matrix must be square");
        for (int row = 0; row < n; row++) {
            for (int column = row + 1; column < n; column++) {
                int temporary = matrix[row][column];
                matrix[row][column] = matrix[column][row];
                matrix[column][row] = temporary;
            }
        }
        for (int[] row : matrix) {
            for (int left = 0, right = n - 1; left < right; left++, right--) {
                int temporary = row[left];
                row[left] = row[right];
                row[right] = temporary;
            }
        }
    }

    /** Return rectangular cells by alternating direction on each row+column diagonal. */
    public static int[] diagonalTraverse(int[][] matrix) {
        int columns = validateRectangular(matrix);
        int rows = matrix.length;
        int[] answer = new int[rows * columns];
        int write = 0;
        for (int diagonal = 0; diagonal <= rows + columns - 2; diagonal++) {
            if ((diagonal & 1) == 0) {
                int row = Math.min(diagonal, rows - 1);
                int column = diagonal - row;
                while (row >= 0 && column < columns) answer[write++] = matrix[row--][column++];
            } else {
                int column = Math.min(diagonal, columns - 1);
                int row = diagonal - column;
                while (column >= 0 && row < rows) answer[write++] = matrix[row++][column--];
            }
        }
        return answer;
    }

    /** Advance a finite binary board one simultaneous Game of Life generation in place. */
    public static void gameOfLife(int[][] board) {
        int columns = validateRectangular(board);
        int rows = board.length;
        for (int[] row : board) for (int cell : row) {
            if (cell != 0 && cell != 1) throw new IllegalArgumentException("board must be binary");
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int live = 0;
                for (int rowDelta = -1; rowDelta <= 1; rowDelta++) {
                    for (int columnDelta = -1; columnDelta <= 1; columnDelta++) {
                        if (rowDelta == 0 && columnDelta == 0) continue;
                        int nextRow = row + rowDelta, nextColumn = column + columnDelta;
                        if (0 <= nextRow && nextRow < rows && 0 <= nextColumn && nextColumn < columns) {
                            live += board[nextRow][nextColumn] & 1;
                        }
                    }
                }
                int old = board[row][column] & 1;
                if (live == 3 || (old == 1 && live == 2)) board[row][column] |= 2;
            }
        }
        for (int[] row : board) for (int column = 0; column < columns; column++) row[column] >>>= 1;
    }

    /** Search a matrix whose rows and columns are independently non-decreasing. */
    public static boolean searchSortedMatrix(int[][] matrix, int target) {
        int columns = validateRectangular(matrix);
        validateRowColumnSorted(matrix, columns);
        int row = 0, column = columns - 1;
        while (row < matrix.length && column >= 0) {
            int value = matrix[row][column];
            if (value == target) return true;
            if (value > target) column--;
            else row++;
        }
        return false;
    }

    /** Test whether every top-left to bottom-right diagonal is constant. */
    public static boolean isToeplitz(int[][] matrix) {
        int columns = validateRectangular(matrix);
        for (int row = 1; row < matrix.length; row++) {
            for (int column = 1; column < columns; column++) {
                if (matrix[row][column] != matrix[row - 1][column - 1]) return false;
            }
        }
        return true;
    }

    /** Count four-directionally connected land components without mutating the grid. */
    public static int countIslands(char[][] grid) {
        int columns = validateBinaryGrid(grid);
        boolean[][] visited = new boolean[grid.length][columns];
        int islands = 0;
        int[] rowDelta = {-1, 0, 1, 0};
        int[] columnDelta = {0, 1, 0, -1};
        for (int row = 0; row < grid.length; row++) {
            for (int column = 0; column < columns; column++) {
                if (grid[row][column] != '1' || visited[row][column]) continue;
                islands++;
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                visited[row][column] = true;
                queue.add(row * columns + column);
                while (!queue.isEmpty()) {
                    int cell = queue.remove();
                    int currentRow = cell / columns, currentColumn = cell % columns;
                    for (int direction = 0; direction < 4; direction++) {
                        int nextRow = currentRow + rowDelta[direction];
                        int nextColumn = currentColumn + columnDelta[direction];
                        if (0 <= nextRow && nextRow < grid.length && 0 <= nextColumn
                                && nextColumn < columns && grid[nextRow][nextColumn] == '1'
                                && !visited[nextRow][nextColumn]) {
                            visited[nextRow][nextColumn] = true;
                            queue.add(nextRow * columns + nextColumn);
                        }
                    }
                }
            }
        }
        return islands;
    }

    private static int validateRectangular(int[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0] == null || matrix[0].length == 0) {
            throw new IllegalArgumentException("matrix must be non-empty and rectangular");
        }
        int columns = matrix[0].length;
        for (int[] row : matrix) if (row == null || row.length != columns) {
            throw new IllegalArgumentException("matrix must be non-empty and rectangular");
        }
        return columns;
    }

    private static int validateBinaryGrid(char[][] grid) {
        if (grid == null || grid.length == 0 || grid[0] == null || grid[0].length == 0) {
            throw new IllegalArgumentException("grid must be non-empty and rectangular");
        }
        int columns = grid[0].length;
        for (char[] row : grid) {
            if (row == null || row.length != columns) throw new IllegalArgumentException("grid must be rectangular");
            for (char cell : row) if (cell != '0' && cell != '1') {
                throw new IllegalArgumentException("grid must be binary");
            }
        }
        return columns;
    }

    private static void validateRowColumnSorted(int[][] matrix, int columns) {
        for (int row = 0; row < matrix.length; row++) {
            for (int column = 1; column < columns; column++) {
                if (matrix[row][column - 1] > matrix[row][column]) {
                    throw new IllegalArgumentException("rows and columns must be sorted");
                }
            }
        }
        for (int row = 1; row < matrix.length; row++) {
            for (int column = 0; column < columns; column++) {
                if (matrix[row - 1][column] > matrix[row][column]) {
                    throw new IllegalArgumentException("rows and columns must be sorted");
                }
            }
        }
    }

    private static int[][] copy(int[][] matrix) {
        int[][] result = new int[matrix.length][];
        for (int row = 0; row < matrix.length; row++) result[row] = matrix[row].clone();
        return result;
    }

    private static char[][] copy(char[][] grid) {
        char[][] result = new char[grid.length][];
        for (int row = 0; row < grid.length; row++) result[row] = grid[row].clone();
        return result;
    }

    private static int[][] rotateOracle(int[][] matrix) {
        int n = matrix.length;
        int[][] answer = new int[n][n];
        for (int row = 0; row < n; row++) for (int column = 0; column < n; column++) {
            answer[column][n - 1 - row] = matrix[row][column];
        }
        return answer;
    }

    private static int[] diagonalOracle(int[][] matrix) {
        int rows = matrix.length, columns = matrix[0].length;
        int[] answer = new int[rows * columns];
        int write = 0;
        for (int diagonal = 0; diagonal <= rows + columns - 2; diagonal++) {
            int[] temporary = new int[Math.min(Math.min(diagonal + 1, rows + columns - 1 - diagonal), Math.min(rows, columns))];
            int length = 0;
            for (int row = 0; row < rows; row++) {
                int column = diagonal - row;
                if (0 <= column && column < columns) temporary[length++] = matrix[row][column];
            }
            if ((diagonal & 1) == 0) for (int index = length - 1; index >= 0; index--) answer[write++] = temporary[index];
            else for (int index = 0; index < length; index++) answer[write++] = temporary[index];
        }
        return answer;
    }

    private static int[][] lifeOracle(int[][] board) {
        int rows = board.length, columns = board[0].length;
        int[][] answer = new int[rows][columns];
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            int live = 0;
            for (int neighborRow = 0; neighborRow < rows; neighborRow++) {
                for (int neighborColumn = 0; neighborColumn < columns; neighborColumn++) {
                    if (Math.max(Math.abs(neighborRow - row), Math.abs(neighborColumn - column)) == 1) {
                        live += board[neighborRow][neighborColumn];
                    }
                }
            }
            answer[row][column] = live == 3 || (board[row][column] == 1 && live == 2) ? 1 : 0;
        }
        return answer;
    }

    private static boolean containsOracle(int[][] matrix, int target) {
        for (int[] row : matrix) for (int value : row) if (value == target) return true;
        return false;
    }

    private static boolean toeplitzOracle(int[][] matrix) {
        int rows = matrix.length, columns = matrix[0].length;
        for (int startRow = 0; startRow < rows; startRow++) {
            int value = matrix[startRow][0];
            for (int row = startRow, column = 0; row < rows && column < columns; row++, column++) {
                if (matrix[row][column] != value) return false;
            }
        }
        for (int startColumn = 1; startColumn < columns; startColumn++) {
            int value = matrix[0][startColumn];
            for (int row = 0, column = startColumn; row < rows && column < columns; row++, column++) {
                if (matrix[row][column] != value) return false;
            }
        }
        return true;
    }

    private static int islandsOracle(char[][] grid) {
        int rows = grid.length, columns = grid[0].length, cells = rows * columns;
        int[] parent = new int[cells];
        Arrays.fill(parent, -1);
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            if (grid[row][column] == '1') parent[row * columns + column] = row * columns + column;
        }
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            if (grid[row][column] != '1') continue;
            if (row + 1 < rows && grid[row + 1][column] == '1') union(parent, row * columns + column, (row + 1) * columns + column);
            if (column + 1 < columns && grid[row][column + 1] == '1') union(parent, row * columns + column, row * columns + column + 1);
        }
        int count = 0;
        for (int cell = 0; cell < cells; cell++) if (parent[cell] == cell) count++;
        return count;
    }

    private static int find(int[] parent, int cell) {
        while (parent[cell] != cell) { parent[cell] = parent[parent[cell]]; cell = parent[cell]; }
        return cell;
    }

    private static void union(int[] parent, int first, int second) {
        int firstRoot = find(parent, first), secondRoot = find(parent, second);
        if (firstRoot != secondRoot) parent[secondRoot] = firstRoot;
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }

    private static void deterministic() {
        int[][] rotate = {{1,2,3},{4,5,6},{7,8,9}};
        rotateClockwise(rotate);
        assert Arrays.deepEquals(rotate, new int[][] {{7,4,1},{8,5,2},{9,6,3}});
        int[][] singleton = {{Integer.MIN_VALUE}}; rotateClockwise(singleton); assert singleton[0][0] == Integer.MIN_VALUE;
        int[][] four = {{5,1,9,11},{2,4,8,10},{13,3,6,7},{15,14,12,16}};
        rotateClockwise(four); assert Arrays.deepEquals(four, new int[][] {{15,13,2,5},{14,3,4,1},{12,6,8,9},{16,7,10,11}});

        assert Arrays.equals(diagonalTraverse(new int[][] {{1,2,3},{4,5,6},{7,8,9}}), new int[] {1,2,4,7,5,3,6,8,9});
        assert Arrays.equals(diagonalTraverse(new int[][] {{1,2,3,4}}), new int[] {1,2,3,4});
        assert Arrays.equals(diagonalTraverse(new int[][] {{1},{2},{3}}), new int[] {1,2,3});

        int[][] life = {{0,1,0},{0,0,1},{1,1,1},{0,0,0}};
        gameOfLife(life); assert Arrays.deepEquals(life, new int[][] {{0,0,0},{1,0,1},{0,1,1},{0,1,0}});
        int[][] block = {{1,1},{1,1}}; gameOfLife(block); assert Arrays.deepEquals(block, new int[][] {{1,1},{1,1}});
        int[][] lone = {{1}}; gameOfLife(lone); assert lone[0][0] == 0;

        int[][] sorted = {{1,4,7,11,15},{2,5,8,12,19},{3,6,9,16,22},{10,13,14,17,24},{18,21,23,26,30}};
        assert searchSortedMatrix(sorted, 5); assert !searchSortedMatrix(sorted, 20);
        assert searchSortedMatrix(new int[][] {{Integer.MIN_VALUE,Integer.MAX_VALUE}}, Integer.MAX_VALUE);

        assert isToeplitz(new int[][] {{1,2,3,4},{5,1,2,3},{9,5,1,2}});
        assert !isToeplitz(new int[][] {{1,2},{2,2}});
        assert isToeplitz(new int[][] {{7,8,9}});

        assert countIslands(new char[][] {{'1','1','0'},{'1','0','0'},{'0','0','1'}}) == 2;
        assert countIslands(new char[][] {{'0'}}) == 0;
        assert countIslands(new char[][] {{'1'}}) == 1;

        expectFailure(() -> rotateClockwise(new int[][] {{1,2},{3,4},{5,6}}));
        expectFailure(() -> diagonalTraverse(new int[][] {{1},{2,3}}));
        expectFailure(() -> gameOfLife(new int[][] {{2}}));
        expectFailure(() -> searchSortedMatrix(new int[][] {{2,1}}, 1));
        expectFailure(() -> countIslands(new char[][] {{'x'}}));
    }

    private static void randomized() {
        Random random = new Random(0x2D5EEDL);
        for (int trial = 0; trial < 20_000; trial++) {
            int squareSize = 1 + random.nextInt(6);
            int[][] square = new int[squareSize][squareSize];
            for (int[] row : square) for (int column = 0; column < squareSize; column++) row[column] = random.nextInt();
            int[][] rotationExpected = rotateOracle(square);
            rotateClockwise(square);
            assert Arrays.deepEquals(square, rotationExpected);

            int rows = 1 + random.nextInt(6), columns = 1 + random.nextInt(6);
            int[][] matrix = new int[rows][columns];
            for (int[] row : matrix) for (int column = 0; column < columns; column++) row[column] = random.nextInt(15) - 7;
            int[][] original = copy(matrix);
            assert Arrays.equals(diagonalTraverse(matrix), diagonalOracle(matrix));
            assert Arrays.deepEquals(matrix, original);

            int[][] life = new int[rows][columns];
            for (int[] row : life) for (int column = 0; column < columns; column++) row[column] = random.nextBoolean() ? 1 : 0;
            int[][] lifeExpected = lifeOracle(life);
            gameOfLife(life);
            assert Arrays.deepEquals(life, lifeExpected);

            int[][] sorted = new int[rows][columns];
            int base = random.nextInt(11) - 5;
            for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
                int above = row == 0 ? base : sorted[row - 1][column];
                int left = column == 0 ? base : sorted[row][column - 1];
                sorted[row][column] = Math.max(above, left) + random.nextInt(3);
            }
            int target = random.nextBoolean() ? sorted[random.nextInt(rows)][random.nextInt(columns)] : random.nextInt(25) - 10;
            int[][] sortedOriginal = copy(sorted);
            assert searchSortedMatrix(sorted, target) == containsOracle(sorted, target);
            assert Arrays.deepEquals(sorted, sortedOriginal);

            assert isToeplitz(matrix) == toeplitzOracle(matrix);

            char[][] grid = new char[rows][columns];
            for (char[] row : grid) for (int column = 0; column < columns; column++) row[column] = random.nextBoolean() ? '1' : '0';
            char[][] gridOriginal = copy(grid);
            assert countIslands(grid) == islandsOracle(grid);
            assert Arrays.deepEquals(grid, gridOriginal);
        }
    }

    public static void main(String[] args) {
        deterministic();
        randomized();
        System.out.println("Matrix2DPatternContracts: 20 deterministic groups + 20000 seeded trials; 120000 oracle/preservation checks passed.");
    }
}
