package io.matrix.random;

/**
 * RUN 223 — RandomSource (deterministic injectable RNG).
 *
 * <p>Like SystemClock, this provides an injection point for
 * randomness. Default uses Math.random; tests can replace
 * with a fixed-seed version for determinism.
 */
public final class RandomSource {

    public interface Rng {
        double nextDouble();
        int nextInt(int bound);
    }

    public static final Rng REAL = new Rng() {
        @Override public double nextDouble() { return Math.random(); }
        @Override public int nextInt(int bound) {
            return (int) (Math.random() * bound);
        }
    };

    public static final class SeededRng implements Rng {
        private final java.util.Random rng;
        public SeededRng(long seed) { this.rng = new java.util.Random(seed); }
        @Override public double nextDouble() { return rng.nextDouble(); }
        @Override public int nextInt(int bound) {
            return rng.nextInt(bound);
        }
    }

    private static volatile Rng current = REAL;

    public static void setCurrent(Rng r) { current = r; }
    public static Rng getCurrent() { return current; }

    public static double nextDouble() { return current.nextDouble(); }
    public static int nextInt(int bound) { return current.nextInt(bound); }
}
