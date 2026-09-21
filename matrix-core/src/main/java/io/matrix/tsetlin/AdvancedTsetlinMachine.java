package io.matrix.tsetlin;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;

import java.util.Random;

/**
 * Phase S (RUN 414) — Tsetlin Machine with advanced features.
 *
 * <p>Extends basic TsetlinTrainer with:
 *  - Fractional s (state sum)
 *  - Γ(t) tempering schedule
 *  - Multi-clause Tsetlin (per-class ensemble)
 *  - State export/import for reproducibility
 *
 * <p>Pure functions where possible. update() may use seeded RNG.
 */
public final class AdvancedTsetlinMachine {

    public record Clause(int[] include, int[] exclude, int polarity) {}

    public record Model(int nFeatures, int nClauses, int nClasses,
                        Clause[][] clauses, int[] classSum) {}

    public record PredictResult(int predicted, double confidence) {}

    private AdvancedTsetlinMachine() {}

    /** Initialize a new model. */
    public static Model init(int nFeatures, int nClauses, int nClasses, long seed) {
        Random rng = new Random(seed);
        Clause[][] clauses = new Clause[nClasses][nClauses];
        for (int c = 0; c < nClasses; c++) {
            for (int k = 0; k < nClauses; k++) {
                int[] include = new int[nFeatures];
                int[] exclude = new int[nFeatures];
                for (int f = 0; f < nFeatures; f++) {
                    include[f] = rng.nextInt(2);
                    exclude[f] = rng.nextInt(2);
                }
                clauses[c][k] = new Clause(include, exclude, rng.nextInt(2));
            }
        }
        return new Model(nFeatures, nClauses, nClasses, clauses, new int[nClasses]);
    }

    /** Tempering schedule: Γ(t) = Γ_max / (1 + decay·t). */
    public static double temperingSchedule(double gammaMax, double decay, int t) {
        return gammaMax / (1.0 + decay * t);
    }

    /** Fractional s: s ∈ [0, S_max] with continuous value. */
    public static double fractionalS(int baseS, double fraction) {
        return Math.max(0, Math.min(2 * baseS, baseS + fraction * baseS));
    }

    /** Predict class for a sample via clause voting. */
    public static PredictResult predict(Model model, int[] features) {
        int[] votes = new int[model.nClasses()];
        for (int c = 0; c < model.nClasses(); c++) {
            for (int k = 0; k < model.nClauses(); k++) {
                Clause clause = model.clauses()[c][k];
                boolean includeMatch = true;
                for (int f = 0; f < model.nFeatures(); f++) {
                    if (clause.include()[f] == 1 && features[f] != 1) {
                        includeMatch = false;
                        break;
                    }
                }
                if (includeMatch && clause.polarity() > 0) votes[c]++;
            }
        }
        int predicted = 0;
        for (int c = 1; c < model.nClasses(); c++) {
            if (votes[c] > votes[predicted]) predicted = c;
        }
        double total = votes[predicted];
        double sum = 0;
        for (int v : votes) sum += v;
        double confidence = sum == 0 ? 0 : total / sum;
        return new PredictResult(predicted, confidence);
    }

    /** Update clause state (Taum-I feedback) — uses seeded RNG. */
    public static Model updateClause(Model model, int classIdx, int clauseIdx,
                                    int[] features, int actualLabel,
                                    double gamma, long seed) {
        Random rng = new Random(seed);
        Clause[] newClauses = new Clause[model.nClauses()];
        System.arraycopy(model.clauses()[classIdx], 0, newClauses, 0,
                model.nClauses());
        Clause old = model.clauses()[classIdx][clauseIdx];
        boolean predictPos = predict(model, features).predicted() == actualLabel;
        int[] newInclude = old.include().clone();
        int[] newExclude = old.exclude().clone();
        for (int f = 0; f < model.nFeatures(); f++) {
            if (predictPos) {
                // Reinforce
                if (features[f] == 1 && rng.nextDouble() < gamma / 2) {
                    if (rng.nextBoolean()) newInclude[f] = 1;
                }
            } else {
                // Anti-reinforce
                if (features[f] == 1 && rng.nextDouble() < gamma) {
                    newInclude[f] = 0;
                }
            }
        }
        newClauses[clauseIdx] = new Clause(newInclude, newExclude, old.polarity());
        Clause[][] allClauses = new Clause[model.nClasses()][];
        for (int c = 0; c < model.nClasses(); c++) {
            allClauses[c] = model.clauses()[c].clone();
        }
        allClauses[classIdx] = newClauses;
        return new Model(model.nFeatures(), model.nClauses(), model.nClasses(),
                allClauses, model.classSum().clone());
    }

    /** Export model state as serializable byte array. */
    public static byte[] exportState(Model model) {
        // Simplified: use a known-stable serialization
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream dos = new java.io.DataOutputStream(baos)) {
            dos.writeInt(model.nFeatures());
            dos.writeInt(model.nClauses());
            dos.writeInt(model.nClasses());
            for (int c = 0; c < model.nClasses(); c++) {
                for (int k = 0; k < model.nClauses(); k++) {
                    Clause cl = model.clauses()[c][k];
                    for (int f = 0; f < model.nFeatures(); f++) {
                        dos.writeInt(cl.include()[f]);
                        dos.writeInt(cl.exclude()[f]);
                    }
                    dos.writeInt(cl.polarity());
                }
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
