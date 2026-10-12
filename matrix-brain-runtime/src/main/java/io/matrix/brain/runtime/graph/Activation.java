package io.matrix.brain.runtime.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The signal travelling through a {@link Connectome}.
 *
 * <p>An activation is immutable: every hop produces a new instance rather than mutating the
 * previous one, so a node cannot observe or corrupt its upstream neighbour's signal. That is what
 * makes {@link CognitiveNode#process} safe to call concurrently for independent nodes.</p>
 *
 * <p>{@code seed} travels with the activation so a stochastic node can derive a reproducible RNG
 * from it (see {@link SeededRandom}) without consulting global state. CONSTITUTION Article I
 * requires stochastic decisions in the cognitive layer to be seeded and auditable; carrying the
 * seed in the activation keeps that property local to the data flow rather than depending on
 * whichever thread happened to run.</p>
 *
 * @param signal the activation magnitudes; defensively copied, never null
 * @param tags   scalar annotations carried alongside the signal (e.g. {@code "modulator.attention"})
 * @param seed   the seed for this step, used to derive reproducible randomness
 */
public record Activation(double[] signal, Map<String, Double> tags, long seed) {

    public Activation {
        Objects.requireNonNull(signal, "signal");
        tags = tags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        signal = signal.clone();
    }

    /** An activation with no tags. */
    /**
     * An activation carrying {@code signal} and no seed.
     *
     * <p><b>W37.3 trap, closed.</b> This was previously the only {@code of} overload alongside a
     * varargs one, so {@code Activation.of(1.0, seed)} resolved to the varargs form and built a
     * signal of {@code [1.0, seed]} — the seed silently became a signal value and inflated
     * {@link #magnitude()}. Found when a replay produced {@code in.magnitude() == 43.0} for a
     * seed of 42.</p>
     *
     * <p>The varargs form now takes exactly one argument and the seeded form takes an explicit
     * array, so the two cannot be confused. Java overload resolution picks the most specific
     * applicable method, and with these signatures {@code of(1.0, 42L)} no longer compiles
     * against the varargs overload.</p>
     */
    public static Activation of(double single) {
        return new Activation(new double[] {single}, Map.of(), 0L);
    }

    /** An activation carrying a multi-element signal and no seed. */
    public static Activation ofSignal(double... signal) {
        return new Activation(signal, Map.of(), 0L);
    }

    /** An activation carrying an explicit signal array and seed. */
    public static Activation of(double[] signal, long seed) {
        return new Activation(signal, Map.of(), seed);
    }

    /** Returns a copy with a different signal, preserving tags and seed. */
    public Activation withSignal(double[] newSignal) {
        return new Activation(newSignal, tags, seed);
    }

    /** Returns a copy with one tag added or replaced. */
    public Activation withTag(String key, double value) {
        Map<String, Double> copy = new LinkedHashMap<>(tags);
        copy.put(key, value);
        return new Activation(signal, copy, seed);
    }

    /** Total magnitude, used by {@link Connectome#propagate} to score outgoing edges. */
    public double magnitude() {
        double sum = 0.0;
        for (double v : signal) sum += Math.abs(v);
        return sum;
    }

    @Override
    public double[] signal() {
        return signal.clone();
    }
}