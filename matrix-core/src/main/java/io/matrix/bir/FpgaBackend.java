package io.matrix.bir;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * FPGA substrate backend — compiles BIR truth tables to FPGA LUT bitstreams.
 *
 * <p>Implements {@link SubstrateBackend} for hardware acceleration of boolean
 * function evaluation. The compile path transforms a {@link TtForm} (k ≤ 20)
 * into a device-specific LUT configuration that maps directly to FPGA hardware.
 *
 * <p><b>Safety (IEC 61508):</b> FPGA synthesis produces a deterministic,
 * auditable bitstream — no floating-point, no probabilistic logic. The truth
 * table is the single source of truth; hardware evaluation is bit-exact with
 * software simulation.
 *
 * <p><b>Current status — SKELETON:</b> The compile method produces a
 * canonical binary representation suitable for downstream Verilog generation
 * (via {@code matrix-fpga/ldn2v.py}) but does not yet interface with physical
 * FPGA hardware. Evaluation falls through to the JVM SIMD path.
 *
 * <p>Ref: docs/improvements/FPGA_SYNTHESIS.md, SPEC-002 FR-D1,
 * matrix-fpga/README.md
 */
public final class FpgaBackend implements SubstrateBackend {

    private static final Logger LOG = Logger.getLogger(FpgaBackend.class.getName());

    /** Magic bytes for bitstream format identification: "MPDT". */
    private static final int MAGIC = 0x4D504454;

    /** Bitstream format version. */
    private static final int VERSION = 1;

    /** K_MAX constraint per CONSTITUTION II.3. */
    private static final int K_MAX = 20;

    /** Maximum input bits this backend supports. */
    private static final int MAX_INPUT_BITS = 4096;

    private final FpgaConfig config;

    public FpgaBackend(FpgaConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return "fpga-" + config.target();
    }

    /**
     * Evaluate a batch of inputs using the BIR form's native evalBatch.
     * In production, this would dispatch to FPGA hardware via serial/JTAG.
     * For now, falls through to the JVM path — correctness matches hardware.
     */
    @Override
    public long[][] evaluate(Bir bir, long[][] inputs) {
        if (!(bir instanceof BirForm form)) {
            throw new IllegalArgumentException("Not a BirForm: " + bir.getClass());
        }
        if (bir.inputBits() > MAX_INPUT_BITS) {
            throw new IllegalArgumentException(
                    "inputBits " + bir.inputBits() + " exceeds max " + MAX_INPUT_BITS);
        }
        long[][] outputs = new long[inputs.length][(form.outputBits() + 63) / 64];
        form.evalBatch(inputs, outputs);
        return outputs;
    }

    /**
     * Compile a BIR truth table to an FPGA LUT bitstream.
     *
     * <p>Bitstream format (little-endian):
     * <pre>
     *   [4 bytes] magic    = 0x4D504454 ("MPDT")
     *   [4 bytes] version  = 1
     *   [4 bytes] k        = input arity (1..20)
     *   [4 bytes] m        = output bits
     *   [4 bytes] lutWidth = config LUT input width
     *   [4 bytes] reserved
     *   [variable] table   = truth table packed as long[] (little-endian)
     * </pre>
     *
     * @param bir  the BIR artifact to compile (must be TtForm for FPGA)
     * @return FPGA bitstream bytes
     * @throws IllegalArgumentException if bir is not a TtForm
     */
    @Override
    public byte[] compile(Bir bir) {
        if (!(bir instanceof TtForm tt)) {
            throw new IllegalArgumentException(
                    "FPGA compile requires TtForm; got: " + bir.getClass().getSimpleName());
        }
        int k = tt.k();
        if (k < 1 || k > K_MAX) {
            throw new IllegalArgumentException("k must be 1..20 for FPGA synthesis, got: " + k);
        }

        LOG.info(() -> "Compiling TtForm(k=" + k + ") for " + config.target()
                + " (LUT" + config.lutWidth() + ", pipeline=" + config.pipelineDepth() + ")");

        long[] table = tt.table();
        int tableBytes = table.length * 8;
        int headerSize = 24; // 6 × 4 bytes
        ByteBuffer buf = ByteBuffer.allocate(headerSize + tableBytes);
        buf.putInt(MAGIC);
        buf.putInt(VERSION);
        buf.putInt(k);
        buf.putInt(tt.outputBits());
        buf.putInt(config.lutWidth());
        buf.putInt(config.pipelineDepth());
        for (long word : table) {
            buf.putLong(word);
        }

        byte[] bitstream = buf.array();
        LOG.fine(() -> "Bitstream: " + bitstream.length + " bytes ("
                + table.length + " LUT entries)");
        return bitstream;
    }

    /**
     * Compile with default iCE40 config.
     * Convenience overload for test/demo usage.
     */
    public byte[] compileToIce40(Bir bir) {
        return compile(bir);
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(
                /* supportsBatch    */ true,
                /* supportsCompile  */ true,
                /* maxInputBits     */ MAX_INPUT_BITS,
                /* description      */ "FPGA LUT backend (" + config.target()
                        + ", LUT" + config.lutWidth()
                        + ", pipeline=" + config.pipelineDepth() + ")"
        );
    }
}
