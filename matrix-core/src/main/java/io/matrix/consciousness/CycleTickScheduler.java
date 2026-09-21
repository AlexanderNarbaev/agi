package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 232 — CycleTickScheduler (rate-limited cycles).
 *
 * <p>Allows running brain cycles at most N per "tick" interval.
 * Used for rate-limiting to prevent runaway cycles.
 *
 * <p>Tick-based, not wall-clock; runs deterministically.
 */
public final class CycleTickScheduler {

    public record Tick(int tickNumber, List<String> inputs) {}

    private final List<Tick> ticks = new ArrayList<>();
    private int currentTick = 0;
    private int maxPerTick = 10;

    public CycleTickScheduler() {
        this.maxPerTick = 10;
    }

    public CycleTickScheduler(int maxPerTick) {
        if (maxPerTick <= 0) maxPerTick = 1;
        this.maxPerTick = maxPerTick;
    }

    public Tick beginTick() {
        currentTick++;
        Tick t = new Tick(currentTick, new ArrayList<>());
        ticks.add(t);
        return t;
    }

    public synchronized BrainLoopService.CycleResult dispatch(
            BrainLoopService svc, Tick t, String input) {
        if (t.inputs().size() >= maxPerTick) {
            // Skip; tick is full
            return null;
        }
        var r = svc.cycle(input);
        t.inputs().add(input);
        return r;
    }

    public synchronized int tickCount() { return ticks.size(); }

    public synchronized int maxPerTick() { return maxPerTick; }
}
