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
    public static Activation of(double... signal) {
        return new Activation(signal, Map.of(), 0L);
    }

    /** An activation carrying an explicit seed. */
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