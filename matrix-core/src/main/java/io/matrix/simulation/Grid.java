package io.matrix.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Toroidal grid of cells. Coordinates wrap around.
 */
public class Grid {

    private final int width;
    private final int height;
    private final CellType[][] cells;

    public Grid(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException(
                    "Grid dimensions must be positive, got " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.cells = new CellType[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cells[y][x] = CellType.EMPTY;
            }
        }
    }

    public int width() { return width; }

    public int height() { return height; }

    private int wrapX(int x) { return ((x % width) + width) % width; }

    private int wrapY(int y) { return ((y % height) + height) % height; }

    public CellType cellAt(Position pos) {
        return cells[wrapY(pos.y())][wrapX(pos.x())];
    }

    public void setCell(Position pos, CellType type) {
        cells[wrapY(pos.y())][wrapX(pos.x())] = type;
    }

    public boolean isWalkable(Position pos) {
        return cellAt(pos) != CellType.WALL;
    }

    public int count(CellType type) {
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y][x] == type) count++;
            }
        }
        return count;
    }

    public List<Position> emptyPositions() {
        List<Position> result = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y][x] == CellType.EMPTY) {
                    result.add(new Position(x, y));
                }
            }
        }
        return result;
    }
}
