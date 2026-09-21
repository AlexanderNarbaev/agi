package io.matrix.neuron;

/**
 * RUN 423 — Naive Bayes text/instance classifier (multinomial-style).
 * <p>Computes {@code argmax_c log P(c) + Σ_i log P(x_i | c)} with
 * Laplace smoothing {@code α = 1}. Pure function. CONSTITUTION I-safe.
 */
public final class NaiveBayes {

    private NaiveBayes() {}

    /** Number of instances per (class, token). */
    public record Training(
            int classes,        // C
            int vocabSize,      // V
            int[] classCount,   // per-class instance count
            int[][] tokenCount  // per-(class, token) count
    ) {
        public Training(int classes, int vocabSize) {
            this(classes, vocabSize, new int[classes], new int[classes][vocabSize]);
        }

        public static Training empty(int classes, int vocabSize) {
            return new Training(classes, vocabSize);
        }

        public Training increment(int cls, int[] tokens) {
            classCount[cls]++;
            for (int t : tokens) tokenCount[cls][t]++;
            return this;
        }
    }

    /**
     * @return the class index with the highest posterior log-probability.
     */
    public static int classify(Training t, double[] logPrior, double alpha, int[] tokens) {
        double best = Double.NEGATIVE_INFINITY;
        int bestCls = 0;
        int totalDocs = 0;
        for (int c : t.classCount()) totalDocs += c;

        for (int c = 0; c < t.classes(); c++) {
            double lp = logPrior != null
                    ? logPrior[c]
                    : Math.log((t.classCount()[c] + alpha) /
                            (totalDocs + alpha * t.classes()));
            double tokenDenom = (double) sum(t.tokenCount()[c]) + alpha * t.vocabSize();
            for (int tok : tokens) {
                lp += Math.log((t.tokenCount()[c][tok] + alpha) / tokenDenom);
            }
            if (lp > best) { best = lp; bestCls = c; }
        }
        return bestCls;
    }

    private static long sum(int[] xs) {
        long s = 0;
        for (int x : xs) s += x;
        return s;
    }
}
