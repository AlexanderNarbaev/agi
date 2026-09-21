package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 446 — Neural Cellular Automata brain (DESIGN-59, Mordvintsev 2020).
 *
 * <p>Minimal implementation of Growing NCA: a grid of cells, each cell has
 * a small state vector, and update rules are derived from a lookup table
 * (here, fixed-seed random rules for self-organization demo).
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>Grid: {@code width × height} cells</li>
 *   <li>State per cell: 4 channels (alpha RGB + visibility)</li>
 *   <li>Neighborhood: 3×3 (Moore) for each cell</li>
 *   <li>Update rule: deterministic lookup based on neighborhood hash
 *       (in real NCA this is a learned neural network; here we use
 *       a random-seeded table for reproducible demonstration)</li>
 * </ul>
 *
 * <h2>Use cases</h2>
 * <ul>
 *   <li>Self-organization: random initial state → stable pattern after N steps</li>
 *   <li>Regeneration: damage the grid → regrows to original pattern</li>
 *   <li>Local learning: rules can be modified by {@link HebbianUpdater}-like
 *       update on local state transitions</li>
 * </ul>
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All operations deterministic given inputs.
 */
public final class NcaBrainSimulator {

    /** Cell state: 4 channels. */
    public static final int CHANNELS = 4;

    /** Rule table size: 2^(3x3 cells * CHANNELS bits) per cell update. */
    private static final int NEIGHBORHOOD_SIZE = 9;

    private final int width;
    private final int height;
    private final float[][] state; // state[cellIndex][channel]
    private final int[] updateRules; // updateRules[neighborhoodHash] -> newState
    private final Random rng;

    /**
     * Create a new NCA simulator.
     *
     * @param width  grid width (≥ 3)
     * @param height grid height (≥ 3)
     * @param rng    RNG source for initial state and rules
     */
    public NcaBrainSimulator(int width, int height, Random rng) {
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("width and height must be ≥ 3");
        }
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.width = width;
        this.height = height;
        this.rng = rng;
        this.state = new float[width * height][CHANNELS];
        // Random initial state and rules
        for (int i = 0; i < state.length; i++) {
            for (int c = 0; c < CHANNELS; c++) {
                state[i][c] = rng.nextFloat();
            }
        }
        // Build rule table: hash 9*4=36 bits → 2^36 entries, but we use 2^16
        // (sample 16-bit hash of neighborhood). Each entry is a small state delta.
        this.updateRules = new int[1 << 16];
        for (int i = 0; i < updateRules.length; i++) {
            updateRules[i] = rng.nextInt();
        }
    }

    /**
     * Seed the center cell with a specific state (used to grow a target
     * pattern from a single seed).
     */
    public void seedCenter(float[] channels) {
        if (channels == null || channels.length != CHANNELS) {
            throw new IllegalArgumentException("channels wrong length");
        }
        int idx = (height / 2) * width + (width / 2);
        System.arraycopy(channels, 0, state[idx], 0, CHANNELS);
    }

    /**
     * Run N update steps. Each cell looks at its 3×3 neighborhood, computes
     * a 16-bit hash of the quantized neighborhood, and looks up the new
     * state in the rule table.
     */
    public void stepN(int n) {
        if (n < 0) throw new IllegalArgumentException("n must be ≥ 0");
        float[][] next = new float[width * height][CHANNELS];
        for (int s = 0; s < n; s++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = y * width + x;
                    int hash = neighborhoodHash(x, y);
                    int rule = updateRules[hash];
                    // Apply rule as delta to current state
                    for (int c = 0; c < CHANNELS; c++) {
                        int shift = (3 - c) * 8;
                        int delta = (rule >>> shift) & 0xFF;
                        next[idx][c] = clamp01(state[idx][c] + (delta - 128) / 256.0f);
                    }
                }
            }
            // Swap
            for (int i = 0; i < state.length; i++) {
                System.arraycopy(next[i], 0, state[i], 0, CHANNELS);
            }
        }
    }

    /**
     * Compute the average L1 distance between current state and a target.
     * Used for self-organization assessment.
     */
    public double distanceTo(float[][] targetState) {
        if (targetState == null || targetState.length != state.length) {
            throw new IllegalArgumentException("targetState wrong size");
        }
        double sum = 0.0;
        for (int i = 0; i < state.length; i++) {
            if (targetState[i] == null || targetState[i].length != CHANNELS) {
                throw new IllegalArgumentException("target cell " + i + " wrong size");
            }
            for (int c = 0; c < CHANNELS; c++) {
                sum += Math.abs(state[i][c] - targetState[i][c]);
            }
        }
        return sum / (state.length * CHANNELS);
    }

    /**
     * Snapshot the current state (for use as target or restoration).
     */
    public float[][] snapshot() {
        float[][] copy = new float[state.length][CHANNELS];
        for (int i = 0; i < state.length; i++) {
            System.arraycopy(state[i], 0, copy[i], 0, CHANNELS);
        }
        return copy;
    }

    /**
     * Restore state from a snapshot.
     */
    public void restore(float[][] saved) {
        if (saved == null || saved.length != state.length) {
            throw new IllegalArgumentException("saved wrong size");
        }
        for (int i = 0; i < state.length; i++) {
            if (saved[i] == null || saved[i].length != CHANNELS) {
                throw new IllegalArgumentException("saved cell " + i + " wrong size");
            }
            System.arraycopy(saved[i], 0, state[i], 0, CHANNELS);
        }
    }

    /**
     * Get a single cell's state.
     */
    public float[] cell(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IllegalArgumentException("cell coords out of bounds");
        }
        return state[y * width + x].clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // ============ private helpers ============

    /**
     * Compute a 16-bit hash of the 3x3 neighborhood (with wraparound) at (x, y).
     * Each cell contributes 4 quantized bits (one per channel).
     */
    private int neighborhoodHash(int x, int y) {
        int hash = 0;
        int bit = 0;
        for (int dy = -1; dy <= 1; dy++) {
            int ny = (y + dy + height) % height;
            for (int dx = -1; dx <= 1; dx++) {
                int nx = (x + dx + width) % width;
                float[] cell = state[ny * width + nx];
                for (int c = 0; c < CHANNELS; c++) {
                    int q = cell[c] < 0.5f ? 0 : 1;
                    hash |= (q << bit);
                    bit++;
                    if (bit >= 16) return hash; // truncate to 16 bits
                }
            }
        }
        return hash;
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }
}
