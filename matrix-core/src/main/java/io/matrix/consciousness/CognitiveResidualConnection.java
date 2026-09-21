package io.matrix.consciousness;

/**
 * W242 — Cognitive Residual Connection.
 *
 * <p>Inspired by ResNet (He et al. 2015) and Transformer (Vaswani 2017).
 * y = x + sublayer(x). Enables training of very deep networks.
 *
 * <p>CONSTITUTION VI compliance: residual cognitive streams, not
 * phenomenal consciousness claim.
 */
public final class CognitiveResidualConnection {

    private CognitiveResidualConnection() {}

    /**
     * Standard residual: y = x + sublayer(x)
     */
    public static double[] residual(double[] x, double[] sublayer) {
        if (x == null) return sublayer;
        if (sublayer == null) return x;
        if (x.length != sublayer.length) return x;
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = x[i] + sublayer[i];
        }
        return result;
    }

    /**
     * Scaled residual: y = x + alpha * sublayer(x)
     */
    public static double[] scaledResidual(double[] x, double[] sublayer, double alpha) {
        if (x == null) return sublayer;
        if (sublayer == null) return x;
        if (x.length != sublayer.length) return x;
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = x[i] + alpha * sublayer[i];
        }
        return result;
    }

    /**
     * Gated residual: y = x + gate * sublayer(x)
     */
    public static double[] gatedResidual(double[] x, double[] sublayer, double gate) {
        return scaledResidual(x, sublayer, gate);
    }

    /**
     * Highway-style: y = gate * x + (1 - gate) * sublayer(x)
     */
    public static double[] highway(double[] x, double[] sublayer, double gate) {
        if (x == null) return sublayer;
        if (sublayer == null) return x;
        if (x.length != sublayer.length) return x;
        double g = Math.max(0.0, Math.min(1.0, gate));
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = g * x[i] + (1 - g) * sublayer[i];
        }
        return result;
    }

    /**
     * Apply residual across a chain of sublayer outputs.
     */
    public static double[] chain(double[] x, double[][] sublayers) {
        if (x == null) return null;
        if (sublayers == null || sublayers.length == 0) return x;
        double[] result = x;
        for (double[] sublayer : sublayers) {
            result = residual(result, sublayer);
        }
        return result;
    }
}
