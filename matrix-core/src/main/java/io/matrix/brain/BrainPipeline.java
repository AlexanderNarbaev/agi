package io.matrix.brain;

import java.util.List;

/**
 * Three-block brain pipeline (input → conscious → output).
 *
 * <p>Per L0_manifesto axiom 6 (Hierarchical autonomy) and L5_DNA genetic
 * algorithm, every interaction flows through three sequential stages:
 *
 * <h2>Block 1: Input Processor</h2>
 * Converts raw stimuli (text tokens, audio frames, image pixels) into
 * a 64-bit signal vector that the conscious layer can interpret.
 * Modular adapters per modality (text/image/audio) under
 * {@link io.matrix.multimodal}.
 *
 * <h2>Block 2: Conscious Layer (3-layer MPDT neural hierarchy)</h2>
 * {@link io.matrix.neuron.HierarchicalBrain} + encoder/compression/output
 * layers. Generates the response state. Purely discrete (Axiom 1 in L0).
 * Local K≤20 input fan-in (Axiom 2 in L0).
 *
 * <h2>Block 3: Output Processor</h2>
 * Converts neural state into the requested output medium (text/audio/image
 * /tool-call). Enforces Axiom 3 (full interpretability) by writing every
 * decision as a discrete command log.
 *
 * <p>Long-term memory (HierarchicalMemory L0..L4) is queried before the
 * conscious layer (world context) and written after (write-back). See
 * {@link io.matrix.memory.HierarchicalMemory}.
 */
public interface BrainPipeline {

    /**
     * Run the full 3-block pipeline on a single input.
     *
     * @param input raw input from any modality
     * @return the generated response and metadata about the pipeline run
     */
    BrainOutput run(BrainInput input);

    /**
     * Input bundle: text + optional media attachments.
     */
    record BrainInput(
            String text,
            List<byte[]> images,
            List<byte[]> audio) {
        public static BrainInput ofText(String text) {
            return new BrainInput(text, List.of(), List.of());
        }
    }

    /**
     * Pipeline result: assistant response + execution metadata.
     */
    record BrainOutput(
            String content,
            BlockExecutions executions,
            long latencyMicros) {
    }

    /**
     * Per-block execution metadata (which adapters fired, durations, etc.).
     */
    record BlockExecutions(
            String inputProcessor,
            String consciousLayer,
            String outputProcessor,
            int memoryReads,
            int memoryWrites) {
    }
}