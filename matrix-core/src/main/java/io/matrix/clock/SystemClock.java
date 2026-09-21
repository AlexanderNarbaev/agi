package io.matrix.clock;

/**
 * RUN 222 — SystemClock (deterministic injectable clock).
 *
 * <p>Fake clock for tests. Allows time-dependent code to be tested
 * deterministically. Real wall-clock access should be confined to
 * a single boundary class so tests can replace it.
 */
public final class SystemClock {

    public interface Clock {
        long currentTimeMillis();
        long nanoTime();
    }

    public static final Clock REAL = new Clock() {
        @Override public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
        @Override public long nanoTime() {
            return System.nanoTime();
        }
    };

    public static final class FixedClock implements Clock {
        private long millis;
        public FixedClock(long startMillis) {
            this.millis = startMillis;
        }
        @Override public long currentTimeMillis() { return millis; }
        @Override public long nanoTime() { return millis * 1_000_000L; }
        public void advance(long deltaMs) { millis += deltaMs; }
    }

    private static Clock current = REAL;

    public static void setCurrent(Clock c) { current = c; }
    public static Clock getCurrent() { return current; }

    public static long now() { return current.currentTimeMillis(); }
    public static long nano() { return current.nanoTime(); }
}
