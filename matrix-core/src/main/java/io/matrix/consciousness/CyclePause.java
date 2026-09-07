package io.matrix.consciousness;

/**
 * RUN 270 — CyclePause (pause/resume control).
 *
 * <p>Simple pause/resume for the cognitive loop. When paused,
 * all cycle() calls return null immediately.
 */
public final class CyclePause {

    private boolean paused = false;

    public synchronized void pause() { paused = true; }
    public synchronized void resume() { paused = false; }
    public synchronized boolean isPaused() { return paused; }

    /** Returns true if cycle should proceed. */
    public synchronized boolean shouldProceed() {
        return !paused;
    }
}
