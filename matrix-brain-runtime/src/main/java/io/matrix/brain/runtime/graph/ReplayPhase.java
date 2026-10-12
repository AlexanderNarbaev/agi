package io.matrix.brain.runtime.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * W37.3 — the replay phase of sleep: re-activate high-value episodes and apply Hebbian updates.
 *
 * <p>The brief describes it as: "Select high-priority episodes (Uncertainty/Novelty). Reactivate
 * paths with reduced Attention. Apply Hebbian updates."</p>
 *
 * <h2>What "priority" means here, and why</h2>
 *
 * <p>{@link io.matrix.brain.runtime.EpisodicLog.Entry} carries {@code confidence} and
 * {@code accepted}. Priority is defined as <b>distance from confident-and-accepted</b>:</p>
 *
 * <ul>
 *   <li>an <b>accepted but low-confidence</b> episode scores highest — the system answered and was
 *       unsure, so the path carries real uncertainty worth revisiting;</li>
 *   <li>a <b>rejected</b> episode scores next — something vetoed it and the reason is worth
 *       reinforcing;</li>
 *   <li>a <b>confident and accepted</b> episode scores lowest — nothing is unresolved about it.</li>
 * </ul>
 *
 * <p>Replaying only successes would reinforce what already works and consolidate nothing. This
 * ordering is the whole reason the phase exists.</p>
 *
 * <h2>Reduced attention</h2>
 *
 * <p>Replayed activations carry {@code attention = replayAttentionScale} (0.5 by default), half the
 * production value. A replay is a low-intensity re-activation: it should perturb the weights
 * without being indistinguishable from live processing. The scale is a parameter, not a constant,
 * because the right value is an empirical question that has not been answered.</p>
 *
 * <h2>Reproducibility</h2>
 *
 * <p>Ties in priority break on {@code id}, so the same episode log always replays in the same
 * order. No RNG is involved in selection at all.</p>
 */
public final class ReplayPhase {

    /** Default attention applied to replayed activations; half of production. */
    public static final double DEFAULT_ATTENTION_SCALE = 0.5;

    /** One replayable episode, decoupled from the on-disk log type. */
    public record Episode(String id, double confidence, boolean accepted) {
        public Episode {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("episode id required");
            }
            if (Double.isNaN(confidence)) confidence = 0.0;
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
    }

    /** What one replay pass did. */
    public record Result(int selected, int skipped, int replayed, double totalPlasticity,
                        NeuroModulatorHub modulator) {

        /** True when nothing was replayed because nothing qualified. */
        public boolean empty() {
            return replayed == 0;
        }
    }

    private final Connectome graph;
    private final double attentionScale;
    private final long seed;

    public ReplayPhase(Connectome graph, long seed) {
        this(graph, seed, DEFAULT_ATTENTION_SCALE);
    }

    public ReplayPhase(Connectome graph, long seed, double attentionScale) {
        if (graph == null) throw new IllegalArgumentException("graph required");
        if (attentionScale <= 0.0 || attentionScale > 1.0) {
            throw new IllegalArgumentException("attentionScale in (0,1], got " + attentionScale);
        }
        this.graph = graph;
        this.seed = seed;
        this.attentionScale = attentionScale;
    }

    /**
     * Priority score in [0,1]; higher means "revisit this first".
     *
     * <p>Accepted-but-uncertain (1.0 at confidence 0) beats rejected (0.75 floor) beats
     * confident-and-accepted (falls to 0 at confidence 1).</p>
     */
    public static double priority(Episode e) {
        if (!e.accepted()) {
            return 0.75 + 0.25 * (1.0 - e.confidence());
        }
        return 1.0 - e.confidence();
    }

    /**
     * Replays the {@code limit} highest-priority episodes, applying plasticity to each.
     *
     * @param episodes the candidate log
     * @param limit    how many to replay; must be >= 0
     * @return what was selected and how much the weights moved
     */
    public Result replay(List<Episode> episodes, int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit >= 0, got " + limit);

        List<Episode> ordered = new ArrayList<>(episodes);
        // priority desc, then id ascending: a total order, so the selection is reproducible
        ordered.sort(Comparator
                .comparingDouble((Episode e) -> -priority(e))
                .thenComparing(Episode::id));

        int selected = Math.min(limit, ordered.size());
        int replayed = 0;
        double totalPlasticity = 0.0;
        NeuroModulatorHub lastAppliedModulator =
                new NeuroModulatorHub(attentionScale, 0.5, 0.0, 0.0,
                        NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);

        for (int i = 0; i < selected; i++) {
            Episode e = ordered.get(i);
            // Reduced attention: a replay perturbs, it does not behave like live processing.
            NeuroModulatorHub modulator = new NeuroModulatorHub(
                    attentionScale,
                    0.5,
                    e.accepted() ? 1.0 : -1.0,
                    /* fatigue */ 0.0,
                    NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);

            double[] beforeWeights = graph.synapses().stream().mapToDouble(Synapse::weight).toArray();
            lastAppliedModulator = modulator;
            Activation before = modulator.applyTo(Activation.of(new double[] {1.0}, seed + i));
            Activation after = graph.propagate(before, Integer.MAX_VALUE);
            if (after.magnitude() > 0.0) {
                graph.applyPlasticity(before, after);
                replayed++;
                // Measure the WEIGHT DELTA, not the activation delta. A node that forwards its
                // input returns the same magnitude it was given, so |after - before| is zero by
                // construction and would report "nothing happened" for every correct replay.
                // Weights are what replay actually changes.
                for (int k = 0; k < graph.synapses().size(); k++) {
                    totalPlasticity += Math.abs(graph.synapses().get(k).weight() - beforeWeights[k]);
                }
            }
        }

        // Report the modulator ACTUALLY applied during replay, not a freshly built equivalent.
        // Returning a baseline object means a test asserting on it proves nothing about what the
        // phase did — and a mutation setting attention to 1.0 survives, because the reported value
        // was never the used one.
        return new Result(selected, ordered.size() - selected, replayed, totalPlasticity,
                lastAppliedModulator);
    }

    @Override
    public String toString() {
        return "ReplayPhase[seed=" + seed + " attentionScale=" + attentionScale + "]";
    }
}