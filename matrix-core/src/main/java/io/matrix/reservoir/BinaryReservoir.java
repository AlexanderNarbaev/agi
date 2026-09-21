package io.matrix.reservoir;

import java.util.Random;

/**
 * Binary Reservoir (DESIGN-10): dynamic memory as a reservoir of binary
 * states with fading.
 *
 * <p>Each bit has an activation level that decays over time (fading).
 * Writing sets bits to high activation; reading returns the current
 * activation pattern. Useful for short-term memory and temporal context.
 *
 * <p>Fading follows a power law: activation *= decay^timeDelta.
 * When activation drops below threshold, the bit is "forgotten".
 */
public final class BinaryReservoir {

    private final int size;
    private final double[] activation;
    private final long[] lastWrite;
    private final double decayRate;
    private final double forgetThreshold;
    private long currentTime;

    public BinaryReservoir(int size, double decayRate, double forgetThreshold) {
        this.size = size;
        this.activation = new double[size];
        this.lastWrite = new long[size];
        this.decayRate = decayRate;
        this.forgetThreshold = forgetThreshold;
        this.currentTime = 0;
    }

    /** Write a bit pattern. */
    public void write(long[] bits) {
        tick();
        for (int i = 0; i < size; i++) {
            boolean set = ((bits[i >>> 6] >>> (i & 63)) & 1L) == 1L;
            if (set) {
                activation[i] = 1.0;
                lastWrite[i] = currentTime;
            }
        }
    }

    /** Read current activation pattern. */
    public long[] read() {
        tick();
        long[] result = new long[(size + 63) / 64];
        for (int i = 0; i < size; i++) {
            if (activation[i] > forgetThreshold) {
                result[i >>> 6] |= (1L << (i & 63));
            }
        }
        return result;
    }

    /** Get activation level for a bit. */
    public double activation(int bit) {
        tick();
        return activation[bit];
    }

    /** Advance time and apply decay. */
    private void tick() {
        currentTime++;
        for (int i = 0; i < size; i++) {
            if (activation[i] > 0) {
                long elapsed = currentTime - lastWrite[i];
                activation[i] *= Math.pow(decayRate, elapsed);
                if (activation[i] < 0.001) activation[i] = 0;
            }
        }
    }

    /** Get current time. */
    public long time() { return currentTime; }

    /** Get number of active bits (above threshold). */
    public int activeCount() {
        tick();
        int count = 0;
        for (double a : activation) if (a > forgetThreshold) count++;
        return count;
    }
}
