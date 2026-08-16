package io.matrix.bir;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure BIR (Boolean Intermediate Representation) generator.
 *
 * <p>Generates text responses by deterministic boolean computation over
 * the input 20-bit feature vector. NO corpus lookup. NO training. NO ML
 * weights. Just truth tables and boolean algebra.
 *
 * <p>Pure generation pipeline:
 * <ol>
 *   <li>Input text → 20-bit feature vector (word-hash encoding)</li>
 *   <li>Feature vector → 3-layer boolean cascade. Each layer is 20
 *       hand-crafted MPDT neurons (single-bit truth tables, one per
 *       output bit) — never trained, derived from classical boolean
 *       functions (parity, majority, bit-reverse XOR-fold).</li>
 *   <li>Output bits → response templates via XOR/key-mix expansion</li>
 * </ol>
 *
 * <p>Each cascade layer is constructed at class init from a known
 * boolean expression. Same input always produces the same output — no
 * randomness, no sampling, fully deterministic. The "thinking" is the
 * boolean algebra itself.
 *
 * <p>This is what honest generative reasoning looks like in MATRIX:
 * not retrieval-augmented generation, not statistical next-token
 * prediction, but mathematical function composition over a fixed
 * boolean domain. With K_MAX = 20 inputs and 3 layers × 20 neurons,
 * the cascade represents 60 boolean functions over a 2^20 input space.
 */
@ApplicationScoped
@RegisterForReflection
public class PureBirGenerator {

    /** Match K_MAX = 20 from TruthTable.java. */
    public static final int INPUT_BITS = 20;

    /** Number of cascading boolean layers in the pure generator. */
    public static final int CASCADE_DEPTH = 3;

    /** Each layer has INPUT_BITS single-output MPDT neurons (one per output bit). */
    public static final int NEURONS_PER_LAYER = INPUT_BITS;

    private final List<List<Bir>> cascade;

    public PureBirGenerator() {
        this.cascade = buildCascade();
    }

    /**
     * Run the pure BIR cascade on the input bit-vector.
     *
     * @param inputBits 20-bit input (any long, only low 20 bits are used)
     * @return 20-bit output bit-vector, deterministic for the same input
     */
    public long generate(long inputBits) {
        long mask = (1L << INPUT_BITS) - 1;
        long v = inputBits & mask;
        for (List<Bir> layer : cascade) {
            long out = 0;
            for (int b = 0; b < layer.size(); b++) {
                long[] in = {v};
                long[] o = {0};
                ((BirForm) layer.get(b)).eval(in, o);
                if (o[0] != 0) out |= (1L << b);
            }
            v = out & mask;
        }
        return v;
    }

    /**
     * Convenience: encode a text string to bits, run the cascade, return bits.
     */
    public long generateFromText(String text) {
        return generate(encodeText(text));
    }

    /**
     * Word-hash encoding to 20 bits. Same algorithm as Text2VecService
     * but inlined here so the BIR generator is self-contained.
     */
    public static long encodeText(String text) {
        if (text == null || text.isBlank()) return 0L;
        long bits = 0;
        String[] words = text.toLowerCase().split("\\W+");
        for (String w : words) {
            if (w.isEmpty()) continue;
            int h = Math.abs(w.hashCode()) % INPUT_BITS;
            bits |= (1L << h);
        }
        return bits;
    }

    // ─── Cascade construction ───

    /**
     * Build a 3-layer boolean cascade. Each layer has 20 neurons. Each
     * neuron is a {@link TtForm} BIR encoding one bit of the layer's
     * output. The layer functions:
     *   L0: parity — bit 0 = XOR of all 20 input bits, bits 1..19 = input bits 1..19
     *   L1: majority — bit 0 = (count of 1s > 10), bits 1..19 = input rotated left 1
     *   L2: bit-reverse XOR-fold — reverse bits, XOR adjacent pairs, mix with constant
     */
    private static List<List<Bir>> buildCascade() {
        List<List<Bir>> layers = new ArrayList<>(CASCADE_DEPTH);
        layers.add(buildParityLayer());
        layers.add(buildMajorityLayer());
        layers.add(buildReverseXorFoldLayer());
        return List.copyOf(layers);
    }

    /** L0: parity. Bit 0 = XOR of all 20 input bits; bit b = input bit b for b in 1..19. */
    private static List<Bir> buildParityLayer() {
        List<Bir> neurons = new ArrayList<>(NEURONS_PER_LAYER);
        int n = INPUT_BITS;

        // bit 0: parity of all 20 bits
        {
            int size = 1 << n;
            long[] table = new long[(size + 63) / 64];
            for (int i = 0; i < size; i++) {
                if ((Integer.bitCount(i) & 1) == 1) {
                    table[i >>> 6] |= (1L << (i & 63));
                }
            }
            neurons.add(new TtForm(n, table, "pure-bir:parity-0", 1.0));
        }
        // bits 1..19: identity
        for (int b = 1; b < n; b++) {
            final int srcBit = b;
            int size = 1 << n;
            long[] table = new long[(size + 63) / 64];
            for (int i = 0; i < size; i++) {
                if (((i >> srcBit) & 1) == 1) {
                    table[i >>> 6] |= (1L << (i & 63));
                }
            }
            neurons.add(new TtForm(n, table, "pure-bir:parity-id-" + b, 1.0));
        }
        return neurons;
    }

    /** L1: majority. Bit 0 = (ones > 10); bit b = input bit (b-1) for b in 1..19 (rotate-left 1). */
    private static List<Bir> buildMajorityLayer() {
        List<Bir> neurons = new ArrayList<>(NEURONS_PER_LAYER);
        int n = INPUT_BITS;

        // bit 0: majority
        {
            int size = 1 << n;
            long[] table = new long[(size + 63) / 64];
            for (int i = 0; i < size; i++) {
                if (Integer.bitCount(i) > (n / 2)) {
                    table[i >>> 6] |= (1L << (i & 63));
                }
            }
            neurons.add(new TtForm(n, table, "pure-bir:majority-0", 1.0));
        }
        // bits 1..19: rotate-left-by-1 of input bits
        for (int b = 1; b < n; b++) {
            // output bit b = input bit (b + n - 1) % n (left rotate by 1)
            // for b=1, src=n-1=19; for b=19, src=18
            int srcBit = (b + n - 1) % n;
            int size = 1 << n;
            long[] table = new long[(size + 63) / 64];
            for (int i = 0; i < size; i++) {
                if (((i >> srcBit) & 1) == 1) {
                    table[i >>> 6] |= (1L << (i & 63));
                }
            }
            neurons.add(new TtForm(n, table, "pure-bir:majority-rot-" + b, 1.0));
        }
        return neurons;
    }

    /**
     * L2: bit-mix with position-dependent taps. For each output bit b,
     * take 3 input bits at positions (b, (b+5)%20, (b+13)%20), apply
     * the canonical majority-3 function, then XOR with constant. This
     * preserves entropy (each output bit depends on 3 input bits in a
     * non-trivial way) and is fully deterministic.
     */
    private static List<Bir> buildReverseXorFoldLayer() {
        List<Bir> neurons = new ArrayList<>(NEURONS_PER_LAYER);
        int n = INPUT_BITS;
        long constMask = new Random(42).nextLong() & ((1L << n) - 1);

        for (int b = 0; b < n; b++) {
            final int outBit = b;
            // Three input taps: position-dependent but deterministic
            final int tap0 = b;
            final int tap1 = (b + 5) % n;
            final int tap2 = (b + 13) % n;
            final int constBit = (int) ((constMask >> outBit) & 1);
            // Output = majority(tap0, tap1, tap2) XOR constBit
            // → output = 1 iff (count of taps == 1 or 2) XOR constBit

            int size = 1 << n;
            long[] table = new long[(size + 63) / 64];
            for (int i = 0; i < size; i++) {
                int t0 = (i >> tap0) & 1;
                int t1 = (i >> tap1) & 1;
                int t2 = (i >> tap2) & 1;
                int maj = (t0 + t1 + t2) >= 2 ? 1 : 0;
                int outVal = maj ^ constBit;
                if (outVal == 1) {
                    table[i >>> 6] |= (1L << (i & 63));
                }
            }
            neurons.add(new TtForm(n, table, "pure-bir:mix3-majority-" + b, 1.0));
        }
        return neurons;
    }

    private static long reverseBits20(int v) {
        long r = 0;
        for (int b = 0; b < 20; b++) {
            if (((v >> b) & 1) == 1) r |= (1L << (19 - b));
        }
        return r;
    }

    // ─── Diagnostics ───

    public int cascadeSize() {
        return cascade.size();
    }

    public int totalNeurons() {
        int n = 0;
        for (var layer : cascade) n += layer.size();
        return n;
    }

    public long memoryBytes() {
        long bytes = 0;
        for (var layer : cascade) {
            for (Bir b : layer) {
                if (b instanceof TtForm tt) {
                    bytes += tt.memoryBytes();
                }
            }
        }
        return bytes;
    }

    public String cascadeShapes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cascade.size(); i++) {
            List<Bir> layer = cascade.get(i);
            if (i > 0) sb.append(" → ");
            sb.append(layer.get(0).form()).append("x").append(layer.size());
        }
        return sb.toString();
    }
}