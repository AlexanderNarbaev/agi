package io.matrix.simulation;

/**
 * Cardinal directions and STAY.
 */
public enum Direction {
    N(0, -1),
    S(0, 1),
    W(-1, 0),
    E(1, 0),
    STAY(0, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() { return dx; }
    public int dy() { return dy; }
}
