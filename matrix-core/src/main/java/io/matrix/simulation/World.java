package io.matrix.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Aggregate root for the simulation world. Manages grid, resources, and tick logic.
 */
public class World {

    private final Grid grid;
    private final int resourceCount;
    private long tickCount;
    private final Random rng;

    public World(int width, int height, int wallCount, int resourceCount, Random rng) {
        this.grid = new Grid(width, height);
        this.resourceCount = resourceCount;
        this.rng = rng;
        placeWalls(wallCount);
        placeResources(resourceCount);
    }

    private void placeWalls(int count) {
        List<Position> empty = grid.emptyPositions();
        Collections.shuffle(empty, rng);
        for (int i = 0; i < Math.min(count, empty.size()); i++) {
            grid.setCell(empty.get(i), CellType.WALL);
        }
    }

    private void placeResources(int count) {
        List<Position> empty = grid.emptyPositions();
        Collections.shuffle(empty, rng);
        for (int i = 0; i < Math.min(count, empty.size()); i++) {
            grid.setCell(empty.get(i), CellType.RESOURCE);
        }
    }

    public Grid grid() { return grid; }

    public long tickCount() { return tickCount; }

    public void tick() {
        tickCount++;
    }

    public void collectResource(Position pos) {
        if (grid.cellAt(pos) == CellType.RESOURCE) {
            grid.setCell(pos, CellType.EMPTY);
            respawnResource();
        }
    }

    public boolean isWall(Position pos) {
        return grid.cellAt(pos) == CellType.WALL;
    }

    public boolean isResource(Position pos) {
        return grid.cellAt(pos) == CellType.RESOURCE;
    }

    public List<Position> resourcePositions() {
        List<Position> result = new ArrayList<>();
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                Position pos = new Position(x, y);
                if (grid.cellAt(pos) == CellType.RESOURCE) {
                    result.add(pos);
                }
            }
        }
        return result;
    }

    private void respawnResource() {
        List<Position> empty = grid.emptyPositions();
        if (!empty.isEmpty()) {
            Position pos = empty.get(rng.nextInt(empty.size()));
            grid.setCell(pos, CellType.RESOURCE);
        }
    }
}
