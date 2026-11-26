import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;

/** Executable contracts and independent oracles for G-10 Rotten Oranges. */
public final class GraphRottingOrangesContracts {
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    private record Cell(int row, int col) {}

    /**
     * Returns the minimum minutes needed to rot every fresh orange, or -1 when
     * some fresh orange is unreachable. The caller's grid is never mutated.
     */
    static int minutesToRot(int[][] grid) {
        validate(grid);
        int[][] work = deepCopy(grid);
        Deque<Cell> queue = new ArrayDeque<>();
        int fresh = 0;

        for (int row = 0; row < work.length; row++) {
            for (int col = 0; col < work[0].length; col++) {
                if (work[row][col] == 2) queue.addLast(new Cell(row, col));
                else if (work[row][col] == 1) fresh++;
            }
        }
        if (fresh == 0) return 0;

        int minutes = 0;
        while (!queue.isEmpty() && fresh > 0) {
            int layerSize = queue.size();
            for (int item = 0; item < layerSize; item++) {
                Cell cell = queue.removeFirst();
                for (int direction = 0; direction < 4; direction++) {
                    int nextRow = cell.row() + DR[direction];
                    int nextCol = cell.col() + DC[direction];
                    if (nextRow >= 0 && nextRow < work.length
                            && nextCol >= 0 && nextCol < work[0].length
                            && work[nextRow][nextCol] == 1) {
                        work[nextRow][nextCol] = 2; // claim once, at discovery
                        fresh--;
                        queue.addLast(new Cell(nextRow, nextCol));
                    }
                }
            }
            minutes++; // exactly one fully processed BFS layer elapsed
        }
        return fresh == 0 ? minutes : -1;
    }

    private static void validate(int[][] grid) {
        if (grid == null || grid.length == 0) {
            throw new IllegalArgumentException("grid must contain at least one row");
        }
        if (grid[0] == null || grid[0].length == 0) {
            throw new IllegalArgumentException("grid rows must be non-null and non-empty");
        }
        int cols = grid[0].length;
        for (int row = 0; row < grid.length; row++) {
            if (grid[row] == null || grid[row].length != cols) {
                throw new IllegalArgumentException("grid must be rectangular");
            }
            for (int value : grid[row]) {
                if (value < 0 || value > 2) {
                    throw new IllegalArgumentException("cell values must be 0, 1, or 2");
                }
            }
        }
    }

    /** Independent minute-by-minute synchronous simulation. */
    private static int synchronousOracle(int[][] source) {
        int[][] state = deepCopy(source);
        int minutes = 0;
        while (true) {
            int[][] next = deepCopy(state);
            boolean changed = false;
            boolean freshExists = false;
            for (int row = 0; row < state.length; row++) {
                for (int col = 0; col < state[0].length; col++) {
                    if (state[row][col] != 1) continue;
                    freshExists = true;
                    for (int direction = 0; direction < 4; direction++) {
                        int neighborRow = row + DR[direction];
                        int neighborCol = col + DC[direction];
                        if (neighborRow >= 0 && neighborRow < state.length
                                && neighborCol >= 0 && neighborCol < state[0].length
                                && state[neighborRow][neighborCol] == 2) {
                            next[row][col] = 2;
                            changed = true;
                            break;
                        }
                    }
                }
            }
            if (!freshExists) return minutes;
            if (!changed) return -1;
            state = next;
            minutes++;
        }
    }

    /** Independent repeated-relaxation shortest-path oracle (no queue). */
    private static int relaxationOracle(int[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        int infinity = rows * cols + 1;
        int[][] distance = new int[rows][cols];
        for (int[] row : distance) Arrays.fill(row, infinity);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (grid[row][col] == 2) distance[row][col] = 0;
            }
        }

        for (int pass = 0; pass < rows * cols; pass++) {
            boolean changed = false;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (grid[row][col] == 0) continue;
                    for (int direction = 0; direction < 4; direction++) {
                        int neighborRow = row + DR[direction];
                        int neighborCol = col + DC[direction];
                        if (neighborRow >= 0 && neighborRow < rows
                                && neighborCol >= 0 && neighborCol < cols
                                && grid[neighborRow][neighborCol] != 0
                                && distance[neighborRow][neighborCol] + 1 < distance[row][col]) {
                            distance[row][col] = distance[neighborRow][neighborCol] + 1;
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) break;
        }

        int answer = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (grid[row][col] != 1) continue;
                if (distance[row][col] == infinity) return -1;
                answer = Math.max(answer, distance[row][col]);
            }
        }
        return answer;
    }

    private static int[][] rotate180(int[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        int[][] rotated = new int[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                rotated[rows - 1 - row][cols - 1 - col] = grid[row][col];
            }
        }
        return rotated;
    }

    private static int[][] deepCopy(int[][] grid) {
        int[][] copy = new int[grid.length][];
        for (int row = 0; row < grid.length; row++) {
            copy[row] = Arrays.copyOf(grid[row], grid[row].length);
        }
        return copy;
    }

    private static void assertMinutes(int expected, int[][] grid, String label) {
        int actual = minutesToRot(grid);
        if (expected != actual) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectFailure(int[][] grid, String label) {
        try {
            minutesToRot(grid);
            throw new AssertionError(label + " did not fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static int deterministicGroups() {
        int groups = 0;
        assertMinutes(4, new int[][]{{2, 1, 1}, {1, 1, 0}, {0, 1, 1}}, "standard"); groups++;
        assertMinutes(-1, new int[][]{{2, 1, 1}, {0, 1, 1}, {1, 0, 1}}, "unreachable"); groups++;
        assertMinutes(0, new int[][]{{0, 2}}, "no fresh"); groups++;
        assertMinutes(-1, new int[][]{{1}}, "fresh without source"); groups++;
        assertMinutes(0, new int[][]{{2, 2}, {0, 2}}, "all rotten or empty"); groups++;
        assertMinutes(4, new int[][]{{2, 1, 1, 1, 1}}, "one row"); groups++;
        assertMinutes(4, new int[][]{{2}, {1}, {1}, {1}, {1}}, "one column"); groups++;
        assertMinutes(2, new int[][]{{2, 1, 1}, {1, 1, 1}, {1, 1, 2}}, "multiple sources"); groups++;
        assertMinutes(-1, new int[][]{{2, 0, 1}, {1, 0, 1}, {1, 0, 1}}, "empty barrier"); groups++;

        int[][] preserved = {{2, 1, 1}, {0, 1, 1}};
        int[][] snapshot = deepCopy(preserved);
        assertMinutes(3, preserved, "preserving method");
        if (!Arrays.deepEquals(snapshot, preserved)) throw new AssertionError("input mutation");
        groups++;

        expectFailure(null, "null"); groups++;
        expectFailure(new int[0][], "empty"); groups++;
        expectFailure(new int[][]{null}, "null first row"); groups++;
        expectFailure(new int[][]{{}}, "empty row"); groups++;
        expectFailure(new int[][]{{1}, {1, 2}}, "ragged"); groups++;
        expectFailure(new int[][]{{2}, null}, "later null row"); groups++;
        expectFailure(new int[][]{{-1}}, "negative value"); groups++;
        expectFailure(new int[][]{{3}}, "value three"); groups++;
        expectFailure(new int[][]{{2, 1}, {0, 9}}, "later invalid value"); groups++;
        assertMinutes(0, new int[][]{{0}}, "singleton empty"); groups++;
        return groups;
    }

    private static long randomizedChecks(int trials) {
        Random random = new Random(0xA0A6E5L);
        long checks = 0;
        for (int trial = 0; trial < trials; trial++) {
            int rows = 1 + random.nextInt(7);
            int cols = 1 + random.nextInt(7);
            int[][] grid = new int[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) grid[row][col] = random.nextInt(3);
            }
            int[][] snapshot = deepCopy(grid);
            int actual = minutesToRot(grid);

            if (actual != synchronousOracle(grid)) {
                throw new AssertionError("synchronous parity trial " + trial);
            }
            checks++;
            if (actual != relaxationOracle(grid)) {
                throw new AssertionError("relaxation parity trial " + trial);
            }
            checks++;
            if (!Arrays.deepEquals(snapshot, grid)) {
                throw new AssertionError("nonmutation trial " + trial);
            }
            checks++;
            if (actual != minutesToRot(rotate180(grid))) {
                throw new AssertionError("rotation invariance trial " + trial);
            }
            checks++;
        }
        return checks;
    }

    public static void main(String[] args) {
        int groups = deterministicGroups();
        int trials = 20_000;
        long checks = randomizedChecks(trials);
        System.out.printf("PASS GraphRottingOrangesContracts groups=%d trials=%d checks=%d%n",
                groups, trials, checks);
    }
}
