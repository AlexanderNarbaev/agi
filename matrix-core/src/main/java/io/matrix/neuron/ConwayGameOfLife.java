package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;

/**
 * DESIGN-50 — Conway's Game of Life (Gardner 1970).
 * 2D cellular automaton on Moore neighborhood with B3/S23 rule.
 * Pure function (CONSTITUTION I) — each generation is a pure
 * function of the preceding.
 */
public final class ConwayGameOfLife {

    public static final int DEFAULT_BIRTH = 3;
    public static final int DEFAULT_SURVIVE = 23;  // 2 OR 3

    private ConwayGameOfLife() {}

    /**
     * One generation step. Pure function: returns new grid, input unchanged.
     */
    public static boolean[][] step(boolean[][] grid) {
        if (grid == null || grid.length == 0) {
            throw new IllegalArgumentException("null/empty");
        }
        int w = grid.length;
        int h = grid[0].length;
        boolean[][] next = new boolean[w][h];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int neighbors = countNeighbors(grid, i, j, w, h);
                boolean alive = grid[i][j];
                if (alive) {
                    // Survive: 2 or 3 neighbors
                    next[i][j] = (neighbors == 2 || neighbors == 3);
                } else {
                    // Birth: exactly 3 neighbors
                    next[i][j] = (neighbors == 3);
                }
            }
        }
        return next;
    }

    /** Step N times. */
    public static boolean[][] stepN(boolean[][] grid, int n) {
        boolean[][] current = grid;
        for (int i = 0; i < n; i++) {
            current = step(current);
        }
        return current;
    }

    private static int countNeighbors(boolean[][] grid, int i, int j,
                                      int w, int h) {
        int count = 0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if (di == 0 && dj == 0) continue;
                int ni = (i + di + w) % w;  // toroidal
                int nj = (j + dj + h) % h;
                if (grid[ni][nj]) count++;
            }
        }
        return count;
    }

    /** Count live cells. */
    public static int liveCount(boolean[][] grid) {
        int count = 0;
        for (boolean[] row : grid) {
            for (boolean b : row) if (b) count++;
        }
        return count;
    }
}
