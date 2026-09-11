package io.matrix.neuron;

import io.matrix.neuron.TruthTable;

import java.util.BitSet;

/**
 * DESIGN-20 — EnrichedNeuron: extends TruthTable with magnitude +
 * chemical vector + neurotransmitter tag. All derived deterministically
 * from the underlying table (CONSTITUTION I compliant).
 *
 * <p>Wraps, does NOT replace TruthTable — legacy code keeps working.
 *
 * <p>Magnitude ∈ [0, 1]: sigmoid-transformed density of the table.
 * Density = cardinality / 2^k; magnitude measures how "polarized"
 * the neuron's response is.
 *
 * <p>Chemical vector (4D, derived from input pattern):
 *  - excitation  = |input bits| / k
 *  - inhibition  = 1 − excitation
 *  - novelty     = |input − table.mode()| (normalized Hamming)
 *  - certainty   = 1 − 2·|density − 0.5|
 */
public record EnrichedNeuron(
        TruthTable table,
        double magnitude,
        double[] chemicalVector,
        Neurotransmitter tag
) {
    /** Canonical 4D chemical vector dimension. */
    public static final int CHEMICAL_DIM = 4;
    /** Indices into chemicalVector. */
    public static final int EXCITATION = 0;
    public static final int INHIBITION = 1;
    public static final int NOVELTY = 2;
    public static final int CERTAINTY = 3;

    public EnrichedNeuron {
        if (table == null) throw new IllegalArgumentException("table must not be null");
        if (magnitude < 0.0 || magnitude > 1.0) {
            throw new IllegalArgumentException("magnitude must be in [0, 1]: " + magnitude);
        }
        if (chemicalVector == null || chemicalVector.length != CHEMICAL_DIM) {
            throw new IllegalArgumentException(
                    "chemicalVector must be length " + CHEMICAL_DIM
                            + ", got " + (chemicalVector == null ? "null" : chemicalVector.length));
        }
        for (int i = 0; i < CHEMICAL_DIM; i++) {
            double v = chemicalVector[i];
            if (v < 0.0 || v > 1.0 || Double.isNaN(v)) {
                throw new IllegalArgumentException(
                        "chemicalVector[" + i + "] must be in [0, 1], got " + v);
            }
        }
        if (tag == null) throw new IllegalArgumentException("tag must not be null");
    }

    /**
     * Compute magnitude from a table's density.
     * density = cardinality / 2^k. magnitude = sigmoid((density − 0.5) * 2 * sigma).
     */
    public static double magnitudeFromTable(TruthTable table, double sigma) {
        int k = table.k();
        int card = table.table().cardinality();
        double density = (double) card / (1L << k);
        // Sigmoid: 1 / (1 + exp(-x))
        double x = (density - 0.5) * 2.0 * sigma;
        return 1.0 / (1.0 + Math.exp(-x));
    }

    public static double magnitudeFromTable(TruthTable table) {
        return magnitudeFromTable(table, 4.0);
    }

    /**
     * Compute the 4D chemical vector from the neuron's response on a
     * given input bit pattern. Pure function (CONSTITUTION I).
     */
    public static double[] chemicalFromInput(TruthTable table, boolean[] input) {
        int k = table.k();
        if (input.length != k) {
            throw new IllegalArgumentException(
                    "input length " + input.length + " != k=" + k);
        }
        double excitation = 0.0;
        for (boolean b : input) if (b) excitation++;
        excitation /= k;
        double inhibition = 1.0 - excitation;

        // Novelty: Hamming distance from the majority output
        int cardinality = table.table().cardinality();
        int totalCells = 1 << k;
        // mode = 1 if density > 0.5, else 0 (majority output)
        int majorityOutput = cardinality > totalCells / 2 ? 1 : 0;
        // For novelty, count how many cells in the table disagree with majority
        int disagree = cardinality < totalCells / 2 ? cardinality : totalCells - cardinality;
        double novelty = Math.min(1.0, (double) disagree / Math.max(1, totalCells / 2));

        // Certainty: 1 when density is very polarized (close to 0 or 1)
        double density = (double) cardinality / totalCells;
        double certainty = 1.0 - 2.0 * Math.abs(density - 0.5);

        return new double[]{
                clamp01(excitation),
                clamp01(inhibition),
                clamp01(novelty),
                clamp01(certainty)
        };
    }

    /** Classify a chemical vector into a Neurotransmitter tag. */
    public static Neurotransmitter classify(double[] chemical) {
        double excitation = chemical[EXCITATION];
        double inhibition = chemical[INHIBITION];
        double novelty = chemical[NOVELTY];
        double certainty = chemical[CERTAINTY];
        // Priority order: norepinephrine (most urgent) → dopamine → others
        if (excitation > 0.5 && novelty > 0.8) return Neurotransmitter.NOREPINEPHRINE;
        if (certainty < 0.4 && novelty > 0.5) return Neurotransmitter.ACETYLCHOLINE;
        if (novelty > 0.7 && certainty > 0.5) return Neurotransmitter.DOPAMINE;
        if (certainty > 0.7 && excitation < 0.3) return Neurotransmitter.SEROTONIN;
        if (inhibition > 0.7) return Neurotransmitter.GABA;
        if (excitation > 0.7 && novelty > 0.3) return Neurotransmitter.GLUTAMATE;
        return Neurotransmitter.SEROTONIN; // default: calm
    }

    /** Factory: derive enriched metadata from a table + sample input. */
    public static EnrichedNeuron derive(TruthTable table, boolean[] sampleInput) {
        double magnitude = magnitudeFromTable(table);
        double[] chemical = chemicalFromInput(table, sampleInput);
        Neurotransmitter tag = classify(chemical);
        return new EnrichedNeuron(table, magnitude, chemical, tag);
    }

    /** Factory without sample input — chemical uses empty pattern. */
    public static EnrichedNeuron derive(TruthTable table) {
        return derive(table, new boolean[table.k()]);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /** Hamming distance between two tables, normalized to [0, 1]. */
    public static double hammingDistance(EnrichedNeuron a, EnrichedNeuron b) {
        if (a.table.k() != b.table.k()) {
            throw new IllegalArgumentException("k mismatch: " + a.table.k() + " vs " + b.table.k());
        }
        int k = a.table.k();
        BitSet xa = a.table.table();
        BitSet xb = b.table.table();
        long xor = xa.stream().sum() - xb.stream().sum();
        // Simple: count differing bits
        BitSet diff = (BitSet) xa.clone();
        diff.xor(xb);
        return (double) diff.cardinality() / (1L << k);
    }

    /** Euclidean distance between chemical vectors. */
    public static double chemicalDistance(EnrichedNeuron a, EnrichedNeuron b) {
        double sum = 0;
        for (int i = 0; i < CHEMICAL_DIM; i++) {
            double d = a.chemicalVector[i] - b.chemicalVector[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
