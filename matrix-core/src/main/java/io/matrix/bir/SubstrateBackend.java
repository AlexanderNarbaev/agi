package io.matrix.bir;

/**
 * Substrate Backend interface (SPEC-002 FR-D1).
 *
 * <p>Each backend implements evaluate/compile/capabilities for a specific
 * substrate (JVM, FPGA, Quantum). The runtime contract is:
 * {@code evaluate(BIR, long[][]) → long[][]}.
 *
 * <p>Substrate substitution (JVM→FPGA→quantum) = new backend implementation;
 * BIR and verification unchanged.
 */
public interface SubstrateBackend {

    /** Backend identifier: "jvm-simd", "fpga-ice40", "quantum-mps". */
    String id();

    /** Evaluate a batch of inputs. */
    long[][] evaluate(Bir bir, long[][] inputs);

    /** Compile BIR to substrate-specific artifact. */
    default Object compile(Bir bir) {
        throw new UnsupportedOperationException("compile not supported");
    }

    /** Backend capabilities. */
    Capabilities capabilities();

    record Capabilities(
            boolean supportsBatch,
            boolean supportsCompile,
            int maxInputBits,
            String description) {}
}
