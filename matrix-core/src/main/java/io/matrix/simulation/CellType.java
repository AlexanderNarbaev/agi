package io.matrix.simulation;

/**
 * Types of cells on the grid.
 *
 * <p>Encoded as 2-bit values: EMPTY=00, WALL=01, RESOURCE=10.
 */
public enum CellType {
    EMPTY,
    WALL,
    RESOURCE;

    /**
     * Returns the 2-bit encoding of this cell type.
     */
    public int bits() {
        return ordinal();
    }

    /**
     * Decodes a 2-bit value into a CellType. Values ≥ 3 default to WALL.
     */
    public static CellType fromBits(int bits) {
        return switch (bits) {
            case 0 -> EMPTY;
            case 1 -> WALL;
            case 2 -> RESOURCE;
            default -> WALL;
        };
    }
}
