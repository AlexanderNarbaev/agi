package io.matrix.nas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immutable specification of a neural architecture for NAS (Neural Architecture Search).
 *
 * <p>Represents a feed-forward network as an ordered list of {@link LayerSpec} entries.
 * Each layer defines type, size, activation, and connectivity. The spec supports
 * mutation via {@link #withMutation(MutationResult)} and serialization for LLM
 * prompts via {@link #toPromptString()} and {@link #toJson()}.
 *
 * <p>Ref: arXiv:2406.05433 — LLM-Assisted Adversarial Robustness NAS
 */
public final class ArchitectureSpec {

    private final UUID id;
    private final List<LayerSpec> layers;
    private final int generation;

    private ArchitectureSpec(UUID id, List<LayerSpec> layers, int generation) {
        this.id = Objects.requireNonNull(id);
        this.layers = List.copyOf(layers);
        this.generation = generation;
    }

    /**
     * Creates a new architecture spec with the given layers.
     */
    public static ArchitectureSpec of(List<LayerSpec> layers) {
        return new ArchitectureSpec(UUID.randomUUID(), List.copyOf(layers), 0);
    }

    /**
     * Creates a new architecture spec with explicit id and generation.
     */
    public static ArchitectureSpec of(UUID id, List<LayerSpec> layers, int generation) {
        return new ArchitectureSpec(id, List.copyOf(layers), generation);
    }

    /**
     * Creates a random architecture with the given number of layers.
     *
     * @param layerCount number of layers
     * @param maxNeurons maximum neurons per layer
     * @param rng        random generator for reproducibility
     * @return random architecture spec
     */
    public static ArchitectureSpec random(int layerCount, int maxNeurons, java.util.Random rng) {
        if (layerCount < 1) {
            throw new IllegalArgumentException("layerCount must be >= 1");
        }
        var layers = new ArrayList<LayerSpec>();
        for (int i = 0; i < layerCount; i++) {
            LayerType type = LayerType.values()[rng.nextInt(LayerType.values().length)];
            int size = 4 + rng.nextInt(Math.max(1, maxNeurons - 4));
            Activation activation = Activation.values()[rng.nextInt(Activation.values().length)];
            layers.add(new LayerSpec(type, size, activation, i));
        }
        return ArchitectureSpec.of(layers);
    }

    // ─── Accessors ───

    public UUID id() { return id; }

    public List<LayerSpec> layers() { return layers; }

    public int generation() { return generation; }

    public int layerCount() { return layers.size(); }

    /**
     * Returns the total neuron count across all layers.
     */
    public int totalNeurons() {
        return layers.stream().mapToInt(LayerSpec::size).sum();
    }

    /**
     * Returns the maximum layer size in this architecture.
     */
    public int maxLayerSize() {
        return layers.stream().mapToInt(LayerSpec::size).max().orElse(0);
    }

    /**
     * Returns the complexity score (sum of layer sizes × depth).
     */
    public int complexity() {
        return totalNeurons() * layers.size();
    }

    // ─── Mutation ───

    /**
     * Applies a mutation result, returning a new spec at the next generation.
     *
     * @param mutation the mutation to apply
     * @return new architecture spec with mutation applied
     */
    public ArchitectureSpec withMutation(MutationResult mutation) {
        Objects.requireNonNull(mutation);
        if (mutation instanceof MutationResult.AddLayer add) {
            var newLayers = new ArrayList<>(layers);
            int insertAt = Math.min(add.index(), newLayers.size());
            newLayers.add(insertAt, add.layer());
            return new ArchitectureSpec(UUID.randomUUID(), newLayers, generation + 1);
        } else if (mutation instanceof MutationResult.RemoveLayer rem) {
            if (layers.isEmpty()) return this;
            var newLayers = new ArrayList<>(layers);
            int removeAt = Math.min(rem.index(), newLayers.size() - 1);
            newLayers.remove(removeAt);
            return new ArchitectureSpec(UUID.randomUUID(), newLayers, generation + 1);
        } else if (mutation instanceof MutationResult.ChangeSize cs) {
            var newLayers = new ArrayList<>(layers);
            int target = Math.min(cs.index(), newLayers.size() - 1);
            LayerSpec old = newLayers.get(target);
            newLayers.set(target, new LayerSpec(old.type(), cs.newSize(), old.activation(), old.connectionIndex()));
            return new ArchitectureSpec(UUID.randomUUID(), newLayers, generation + 1);
        } else if (mutation instanceof MutationResult.ChangeActivation ca) {
            var newLayers = new ArrayList<>(layers);
            int target = Math.min(ca.index(), newLayers.size() - 1);
            LayerSpec old = newLayers.get(target);
            newLayers.set(target, new LayerSpec(old.type(), old.size(), ca.newActivation(), old.connectionIndex()));
            return new ArchitectureSpec(UUID.randomUUID(), newLayers, generation + 1);
        } else if (mutation instanceof MutationResult.ChangeLayerType clt) {
            var newLayers = new ArrayList<>(layers);
            int target = Math.min(clt.index(), newLayers.size() - 1);
            LayerSpec old = newLayers.get(target);
            newLayers.set(target, new LayerSpec(clt.newType(), old.size(), old.activation(), old.connectionIndex()));
            return new ArchitectureSpec(UUID.randomUUID(), newLayers, generation + 1);
        } else {
            return this;
        }
    }

    // ─── Serialization ───

    /**
     * Serializes to a human-readable prompt string for LLM consumption.
     *
     * <p>Format:
     * <pre>
     * Architecture (gen=N, layers=L):
     *   [0] DENSE size=16 activation=RELU connection=0
     *   [1] DENSE size=8 activation=SIGMOID connection=0
     * </pre>
     */
    public String toPromptString() {
        var sb = new StringBuilder();
        sb.append("Architecture (gen=").append(generation)
          .append(", layers=").append(layers.size()).append("):\n");
        for (int i = 0; i < layers.size(); i++) {
            LayerSpec l = layers.get(i);
            sb.append("  [").append(i).append("] ")
              .append(l.type()).append(" size=").append(l.size())
              .append(" activation=").append(l.activation())
              .append(" connection=").append(l.connectionIndex())
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * Serializes to JSON string for structured LLM prompts.
     */
    public String toJson() {
        var sb = new StringBuilder();
        sb.append("{\"id\":\"").append(id)
          .append("\",\"generation\":").append(generation)
          .append(",\"layers\":[");
        for (int i = 0; i < layers.size(); i++) {
            LayerSpec l = layers.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"type\":\"").append(l.type())
              .append("\",\"size\":").append(l.size())
              .append(",\"activation\":\"").append(l.activation())
              .append("\",\"connection\":").append(l.connectionIndex())
              .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Parses an architecture spec from a prompt string.
     *
     * @param text the prompt string (format from {@link #toPromptString()})
     * @return parsed architecture spec
     * @throws IllegalArgumentException if parsing fails
     */
    public static ArchitectureSpec fromPromptString(String text) {
        Objects.requireNonNull(text);
        String[] lines = text.split("\n");
        var layers = new ArrayList<LayerSpec>();

        for (String line : lines) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("[")) continue;

            // Parse: [0] DENSE size=16 activation=RELU connection=0
            try {
                int bracketEnd = trimmed.indexOf(']');
                String rest = trimmed.substring(bracketEnd + 1).strip();

                String[] parts = rest.split("\\s+");
                LayerType type = LayerType.valueOf(parts[0]);

                int size = 8; // default
                Activation activation = Activation.RELU; // default
                int connection = 0;

                for (String part : parts) {
                    if (part.startsWith("size=")) {
                        size = Integer.parseInt(part.substring(5));
                    } else if (part.startsWith("activation=")) {
                        activation = Activation.valueOf(part.substring(11));
                    } else if (part.startsWith("connection=")) {
                        connection = Integer.parseInt(part.substring(11));
                    }
                }

                layers.add(new LayerSpec(type, size, activation, connection));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse line: " + trimmed, e);
            }
        }

        if (layers.isEmpty()) {
            throw new IllegalArgumentException("No layers found in prompt string");
        }
        return ArchitectureSpec.of(layers);
    }

    // ─── Records ───

    /**
     * A single layer specification.
     *
     * @param type           layer type
     * @param size           number of neurons
     * @param activation     activation function
     * @param connectionIndex index of the source layer (0 = input)
     */
    public record LayerSpec(LayerType type, int size, Activation activation, int connectionIndex) {
        public LayerSpec {
            if (size < 1) {
                throw new IllegalArgumentException("Layer size must be >= 1, got: " + size);
            }
            if (connectionIndex < 0) {
                throw new IllegalArgumentException("connectionIndex must be >= 0");
            }
        }
    }

    /**
     * Layer types supported by the NAS system.
     */
    public enum LayerType {
        DENSE,
        CONV1D,
        RECURRENT,
        ATTENTION,
        DROPOUT,
        BATCH_NORM
    }

    /**
     * Activation functions.
     */
    public enum Activation {
        RELU,
        SIGMOID,
        TANH,
        GELU,
        SOFTMAX,
        NONE
    }

    /**
     * Sealed interface for mutation results.
     */
    public sealed interface MutationResult
            permits MutationResult.AddLayer,
                    MutationResult.RemoveLayer,
                    MutationResult.ChangeSize,
                    MutationResult.ChangeActivation,
                    MutationResult.ChangeLayerType,
                    MutationResult.NoOp {

        record AddLayer(LayerSpec layer, int index) implements MutationResult {}
        record RemoveLayer(int index) implements MutationResult {}
        record ChangeSize(int index, int newSize) implements MutationResult {}
        record ChangeActivation(int index, Activation newActivation) implements MutationResult {}
        record ChangeLayerType(int index, LayerType newType) implements MutationResult {}
        record NoOp() implements MutationResult {}
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArchitectureSpec that)) return false;
        return generation == that.generation && id.equals(that.id) && layers.equals(that.layers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, layers, generation);
    }

    @Override
    public String toString() {
        return "ArchitectureSpec{id=" + id
                + ", gen=" + generation
                + ", layers=" + layers.size()
                + ", neurons=" + totalNeurons() + '}';
    }
}
