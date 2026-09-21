package io.matrix.nas;

import io.matrix.nas.ArchitectureSpec.Activation;
import io.matrix.nas.ArchitectureSpec.LayerSpec;
import io.matrix.nas.ArchitectureSpec.LayerType;
import io.matrix.nas.ArchitectureSpec.MutationResult;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Genetic algorithm mutation operators for neural architectures.
 *
 * <p>Supports both random mutation (fallback) and LLM-guided mutation
 * (uses suggestions from {@link LlmArchitectureOptimizer}).
 *
 * <p>Mutation types:
 * <ul>
 *   <li>{@link MutationType#ADD_LAYER} — insert a new layer</li>
 *   <li>{@link MutationType#REMOVE_LAYER} — remove an existing layer</li>
 *   <li>{@link MutationType#CHANGE_SIZE} — change neuron count</li>
 *   <li>{@link MutationType#CHANGE_ACTIVATION} — change activation function</li>
 *   <li>{@link MutationType#CHANGE_LAYER_TYPE} — change layer type</li>
 * </ul>
 *
 * <p>Thread-safe: all methods are stateless; state lives in the immutable
 * {@link ArchitectureSpec}.
 */
public final class MutationOperator {

    private final Random rng;
    private final int maxNeurons;
    private final int minLayers;
    private final int maxLayers;

    /**
     * Creates a mutation operator with default bounds.
     *
     * @param rng random generator for reproducibility
     */
    public MutationOperator(Random rng) {
        this(rng, 64, 1, 10);
    }

    /**
     * Creates a mutation operator with explicit bounds.
     *
     * @param rng        random generator
     * @param maxNeurons maximum neurons per layer
     * @param minLayers  minimum layer count
     * @param maxLayers  maximum layer count
     */
    public MutationOperator(Random rng, int maxNeurons, int minLayers, int maxLayers) {
        this.rng = Objects.requireNonNull(rng);
        this.maxNeurons = maxNeurons;
        this.minLayers = minLayers;
        this.maxLayers = maxLayers;
    }

    /**
     * Mutation types supported by the operator.
     */
    public enum MutationType {
        ADD_LAYER,
        REMOVE_LAYER,
        CHANGE_SIZE,
        CHANGE_ACTIVATION,
        CHANGE_LAYER_TYPE
    }

    /**
     * Applies a random mutation to the architecture.
     *
     * <p>Selects a mutation type weighted by probability and applies it.
     * Respects min/max layer bounds. Returns {@link MutationResult.NoOp}
     * if no valid mutation is possible.
     *
     * @param spec the architecture to mutate
     * @return the mutation result
     */
    public MutationResult randomMutate(ArchitectureSpec spec) {
        Objects.requireNonNull(spec);
        if (spec.layers().isEmpty()) {
            return new MutationResult.AddLayer(randomLayer(0), 0);
        }

        double roll = rng.nextDouble();
        if (roll < 0.20 && spec.layerCount() < maxLayers) {
            return addLayer(spec);
        } else if (roll < 0.35 && spec.layerCount() > minLayers) {
            return removeLayer(spec);
        } else if (roll < 0.60) {
            return changeSize(spec);
        } else if (roll < 0.80) {
            return changeActivation(spec);
        } else {
            return changeLayerType(spec);
        }
    }

    /**
     * Applies an LLM-guided mutation.
     *
     * <p>If the LLM suggestion is valid, applies it directly. Otherwise
     * falls back to random mutation.
     *
     * @param spec       the architecture to mutate
     * @param suggestion LLM suggestion (null = fallback to random)
     * @return the mutation result
     */
    public MutationResult llmGuidedMutate(ArchitectureSpec spec, MutationResult suggestion) {
        Objects.requireNonNull(spec);
        if (suggestion == null || suggestion instanceof MutationResult.NoOp) {
            return randomMutate(spec);
        }
        if (isValidMutation(spec, suggestion)) {
            return suggestion;
        }
        return randomMutate(spec);
    }

    /**
     * Validates that a mutation can be applied to the given spec.
     *
     * @param spec     the architecture
     * @param mutation the proposed mutation
     * @return true if the mutation is valid
     */
    public boolean isValidMutation(ArchitectureSpec spec, MutationResult mutation) {
        if (mutation instanceof MutationResult.AddLayer) {
            return spec.layerCount() < maxLayers;
        } else if (mutation instanceof MutationResult.RemoveLayer rem) {
            return spec.layerCount() > minLayers
                    && rem.index() >= 0
                    && rem.index() < spec.layerCount();
        } else if (mutation instanceof MutationResult.ChangeSize cs) {
            return cs.index() >= 0
                    && cs.index() < spec.layerCount()
                    && cs.newSize() >= 1
                    && cs.newSize() <= maxNeurons;
        } else if (mutation instanceof MutationResult.ChangeActivation ca) {
            return ca.index() >= 0 && ca.index() < spec.layerCount();
        } else if (mutation instanceof MutationResult.ChangeLayerType clt) {
            return clt.index() >= 0 && clt.index() < spec.layerCount();
        }
        return mutation instanceof MutationResult.NoOp;
    }

    // ─── Individual mutation operators ───

    /**
     * Adds a random layer at a random position.
     */
    public MutationResult addLayer(ArchitectureSpec spec) {
        int index = rng.nextInt(spec.layerCount() + 1);
        return new MutationResult.AddLayer(randomLayer(index), index);
    }

    /**
     * Removes a layer at a random position.
     */
    public MutationResult removeLayer(ArchitectureSpec spec) {
        int index = rng.nextInt(spec.layerCount());
        return new MutationResult.RemoveLayer(index);
    }

    /**
     * Changes the size of a random layer.
     */
    public MutationResult changeSize(ArchitectureSpec spec) {
        int index = rng.nextInt(spec.layerCount());
        int currentSize = spec.layers().get(index).size();
        int delta = 1 + rng.nextInt(Math.max(1, maxNeurons / 4));
        int newSize = Math.max(1, Math.min(maxNeurons, currentSize + (rng.nextBoolean() ? delta : -delta)));
        return new MutationResult.ChangeSize(index, newSize);
    }

    /**
     * Changes the activation of a random layer.
     */
    public MutationResult changeActivation(ArchitectureSpec spec) {
        int index = rng.nextInt(spec.layerCount());
        Activation current = spec.layers().get(index).activation();
        Activation[] values = Activation.values();
        Activation next = current;
        while (next == current) {
            next = values[rng.nextInt(values.length)];
        }
        return new MutationResult.ChangeActivation(index, next);
    }

    /**
     * Changes the type of a random layer.
     */
    public MutationResult changeLayerType(ArchitectureSpec spec) {
        int index = rng.nextInt(spec.layerCount());
        LayerType current = spec.layers().get(index).type();
        LayerType[] values = LayerType.values();
        LayerType next = current;
        while (next == current) {
            next = values[rng.nextInt(values.length)];
        }
        return new MutationResult.ChangeLayerType(index, next);
    }

    /**
     * Returns all available mutation types.
     */
    public List<MutationType> availableTypes() {
        return List.of(MutationType.values());
    }

    // ─── Internal ───

    private LayerSpec randomLayer(int connectionIndex) {
        LayerType type = LayerType.values()[rng.nextInt(LayerType.values().length)];
        int size = 4 + rng.nextInt(Math.max(1, maxNeurons - 4));
        Activation activation = Activation.values()[rng.nextInt(Activation.values().length)];
        return new LayerSpec(type, size, activation, connectionIndex);
    }
}
