package io.matrix.bir;

/**
 * FPGA synthesis configuration for compiling BIR artifacts to hardware.
 *
 * <p>Defines target device parameters used by {@link FpgaBackend#compile(Bir, FpgaConfig)}
 * to generate a LUT-level bitstream from a truth table or BIR form.
 *
 * <p>Typical targets:
 * <ul>
 *   <li>{@code ice40} — Lattice iCE40 HX1K/HX8K (open-source Yosys+nextpnr flow)</li>
 *   <li>{@code ecp5} — Lattice ECP5 (ULX3S, OrangeCrab)</li>
 *   <li>{@code artix7} — Xilinx Artix-7 (Basys 3, Vivado flow)</li>
 *   <li>{@code cyclonev} — Intel Cyclone V (DE10-Lite, Quartus flow)</li>
 * </ul>
 *
 * <p>Ref: docs/improvements/FPGA_SYNTHESIS.md, SPEC-002 FR-D1 (substrate substitution)
 */
public record FpgaConfig(
        /** Target FPGA family: "ice40", "ecp5", "artix7", "cyclonev". */
        String target,

        /** Lookup-table input width (4 for LUT4, 6 for LUT6). Default: 4. */
        int lutWidth,

        /** Maximum fanout per LUT output. Default: 8. */
        int maxFanout,

        /** Pipeline register stages for timing closure. 0 = combinational. */
        int pipelineDepth) {

    /** iCE40-compatible defaults (LUT4, no pipeline). */
    public static FpgaConfig ice40() {
        return new FpgaConfig("ice40", 4, 8, 0);
    }

    /** Artix-7 defaults (LUT6, single pipeline stage). */
    public static FpgaConfig artix7() {
        return new FpgaConfig("artix7", 6, 12, 1);
    }

    /** Validates configuration sanity. */
    public FpgaConfig {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
        if (lutWidth < 2 || lutWidth > 8) {
            throw new IllegalArgumentException("lutWidth must be 2..8, got: " + lutWidth);
        }
        if (maxFanout < 1) {
            throw new IllegalArgumentException("maxFanout must be >= 1, got: " + maxFanout);
        }
        if (pipelineDepth < 0) {
            throw new IllegalArgumentException("pipelineDepth must be >= 0, got: " + pipelineDepth);
        }
    }
}
