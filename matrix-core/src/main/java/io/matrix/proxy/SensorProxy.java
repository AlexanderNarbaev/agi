package io.matrix.proxy;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Converts external signals (text, images, sensor data) into binary vectors
 * for the MPDT core. Implements L7 §2 Sensor Proxy specification.
 *
 * <p>All output is packed into a {@code long} using at most
 * {@link #OUTPUT_BITS} bits (K_MAX = 20).
 */
@ApplicationScoped
public class SensorProxy {

    /** Maximum number of output bits (matches {@code TruthTable.K_MAX}). */
    public static final int OUTPUT_BITS = 20;

    /** Bit mask for the low {@link #OUTPUT_BITS} bits. */
    private static final long OUTPUT_MASK = (1L << OUTPUT_BITS) - 1L;

    /**
     * Convert text to binary vector using word hashing.
     *
     * <p>Each word is hashed and sets a bit at position {@code hash % OUTPUT_BITS}.
     * This is deterministic: the same text always produces the same vector.
     *
     * @param text input text, must not be null
     * @return packed binary vector (low {@link #OUTPUT_BITS} bits)
     */
    public long textToBits(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            return 0L;
        }
        long bits = 0L;
        String[] words = text.trim().toLowerCase().split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            int pos = Math.floorMod(word.hashCode(), OUTPUT_BITS);
            bits |= (1L << pos);
        }
        return bits & OUTPUT_MASK;
    }

    /**
     * Convert numeric sensor reading to binary vector using threshold-based
     * binarization.
     *
     * <p>The range [{@code min}, {@code max}] is divided into {@code thresholds}
     * levels. All bits at positions 0..level are set (thermometer encoding),
     * making the representation monotonic.
     *
     * @param value      the sensor value
     * @param min        minimum expected value
     * @param max        maximum expected value
     * @param thresholds number of threshold levels (clamped to [{@link #OUTPUT_BITS}])
     * @return packed binary vector
     */
    public long numericToBits(double value, double min, double max, int thresholds) {
        if (thresholds < 1) {
            throw new IllegalArgumentException("thresholds must be ≥ 1, got " + thresholds);
        }
        int levels = Math.min(thresholds, OUTPUT_BITS);
        double range = max - min;
        if (range <= 0) {
            return 0L;
        }
        double clamped = Math.max(min, Math.min(max, value));
        double fraction = (clamped - min) / range;
        int level = (int) Math.round(fraction * levels);
        if (level > levels) level = levels;
        long bits = 0L;
        for (int i = 0; i < level; i++) {
            bits |= (1L << i);
        }
        return bits & OUTPUT_MASK;
    }

    /**
     * Convert numeric sensor reading using the default {@link #OUTPUT_BITS} thresholds.
     *
     * @param value the sensor value
     * @param min   minimum expected value
     * @param max   maximum expected value
     * @return packed binary vector
     */
    public long numericToBits(double value, double min, double max) {
        return numericToBits(value, min, max, OUTPUT_BITS);
    }

    /**
     * Convert 2D grid (e.g., Minecraft blocks) to binary vector.
     *
     * <p>Scans a square area of side {@code 2*radius+1} centered at
     * ({@code centerX}, {@code centerZ}). Each non-empty cell in the scan
     * area sets a bit via hashed position.
     *
     * @param grid    2D array of block types (0 = empty, non-zero = occupied)
     * @param centerX player X position in grid coordinates
     * @param centerZ player Z position in grid coordinates
     * @param radius  scan radius (scan area = {@code 2*radius+1} square)
     * @return packed binary vector
     */
    public long gridToBits(int[][] grid, int centerX, int centerZ, int radius) {
        Objects.requireNonNull(grid, "grid");
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be ≥ 0, got " + radius);
        }
        long bits = 0L;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (z < 0 || z >= grid.length || x < 0 || x >= grid[z].length) {
                    continue;
                }
                if (grid[z][x] != 0) {
                    int hash = hash2D(dx, dz, radius);
                    int pos = Math.floorMod(hash, OUTPUT_BITS);
                    bits |= (1L << pos);
                }
            }
        }
        return bits & OUTPUT_MASK;
    }

    /**
     * Convert inventory state to binary vector.
     *
     * <p>Each occupied slot (count > 0) sets a bit. When {@code slotCount > 20},
     * bits are assigned via hashing to fit within {@link #OUTPUT_BITS}.
     *
     * @param slots     array of item counts per slot
     * @param slotCount number of slots to encode (clamped to slots.length)
     * @return packed binary vector
     */
    public long inventoryToBits(int[] slots, int slotCount) {
        Objects.requireNonNull(slots, "slots");
        int n = Math.min(slotCount, slots.length);
        long bits = 0L;
        for (int i = 0; i < n; i++) {
            if (slots[i] > 0) {
                if (slotCount <= OUTPUT_BITS) {
                    bits |= (1L << i);
                } else {
                    int pos = Math.floorMod(i, OUTPUT_BITS);
                    bits |= (1L << pos);
                }
            }
        }
        return bits & OUTPUT_MASK;
    }

    /**
     * Hash function for 2D grid offsets, producing well-distributed values.
     */
    private static int hash2D(int dx, int dz, int radius) {
        int span = 2 * radius + 1;
        int linearIndex = (dx + radius) * span + (dz + radius);
        return linearIndex * 73856093 ^ (linearIndex >>> 13);
    }
}
