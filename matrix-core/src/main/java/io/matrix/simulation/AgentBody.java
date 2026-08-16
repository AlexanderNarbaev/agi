package io.matrix.simulation;

import java.util.BitSet;

/**
 * Physical body of an agent in the simulation world.
 */
public class AgentBody {

    private Position position;
    private int energy;

    public AgentBody(Position position, int energy) {
        this.position = position;
        this.energy = energy;
    }

    public Position position() { return position; }

    public int energy() { return energy; }

    public boolean isAlive() { return energy > 0; }

    public void move(Direction dir) {
        position = new Position(position.x() + dir.dx(), position.y() + dir.dy());
    }

    public void consumeEnergy(int amount) { energy -= amount; }

    public void addEnergy(int amount) { energy += amount; }

    /**
     * Computes sensor bits from Moore neighborhood (3×3, 8 neighbors) + energy level.
     *
     * <p>Bit layout:
     * <pre>
     * 0-1:   N  cell type
     * 2-3:   NE cell type
     * 4-5:   E  cell type
     * 6-7:   SE cell type
     * 8-9:   S  cell type
     * 10-11: SW cell type
     * 12-13: W  cell type
     * 14-15: NW cell type
     * 16-17: energy level (00=critical,01=low,10=med,11=high)
     * </pre>
     */
    public long sensors(Grid grid) {
        long bits = 0;

        int[][] dirs = {
                {0, -1}, {1, -1}, {1, 0}, {1, 1},
                {0, 1}, {-1, 1}, {-1, 0}, {-1, -1},
        };

        for (int i = 0; i < 8; i++) {
            Position neighbor = new Position(
                    position.x() + dirs[i][0],
                    position.y() + dirs[i][1]);
            CellType cell = grid.cellAt(neighbor);
            bits |= ((long) cell.bits()) << (i * 2);
        }

        int energyBits;
        if (energy <= 0) energyBits = 0;
        else if (energy <= 25) energyBits = 1;
        else if (energy <= 75) energyBits = 2;
        else energyBits = 3;

        bits |= ((long) energyBits) << 16;

        return bits;
    }

    @Override
    public String toString() {
        return "Agent{pos=" + position + ", energy=" + energy + "}";
    }
}
