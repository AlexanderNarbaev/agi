package io.matrix.neuron;

/**
 * DESIGN-39 — Gray-Scott reaction-diffusion model.
 * Two chemicals U, V diffuse and react. Pure function (CONSTITUTION I).
 */
public final class GrayScottSimulator {

    public static final double DEFAULT_DU = 1.0;
    public static final double DEFAULT_DV = 0.5;
    public static final double DEFAULT_FEED = 0.055;
    public static final double DEFAULT_KILL = 0.062;

    private GrayScottSimulator() {}

    /**
     * Step Gray-Scott reaction-diffusion by dt. Pure function:
     * returns new (u, v) arrays (inputs unchanged).
     * @param u initial U chemical
     * @param v initial V chemical
     * @param dt time step
     * @return new double[2][w][h] = {u', v'}
     */
    public static double[][][] step(double[][] u, double[][] v, double dt) {
        if (u == null || v == null) {
            throw new IllegalArgumentException("null");
        }
        if (u.length != v.length) {
            throw new IllegalArgumentException("u/v dim mismatch");
        }
        if (u.length == 0) return new double[][][]{u, v};
        int w = u.length;
        int h = u[0].length;
        double[][] uNew = new double[w][h];
        double[][] vNew = new double[w][h];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                // Diffusion (5-point Laplacian)
                double lapU = laplacian(u, i, j, w, h);
                double lapV = laplacian(v, i, j, w, h);
                double uvv = u[i][j] * v[i][j] * v[i][j];
                // Gray-Scott reaction
                double duDt = DEFAULT_DU * lapU - uvv + DEFAULT_FEED * (1 - u[i][j]);
                double dvDt = DEFAULT_DV * lapV + uvv
                        - (DEFAULT_FEED + DEFAULT_KILL) * v[i][j];
                uNew[i][j] = u[i][j] + dt * duDt;
                vNew[i][j] = v[i][j] + dt * dvDt;
            }
        }
        return new double[][][]{uNew, vNew};
    }

    private static double laplacian(double[][] f, int i, int j, int w, int h) {
        double center = f[i][j];
        double left = (i > 0) ? f[i - 1][j] : center;
        double right = (i < w - 1) ? f[i + 1][j] : center;
        double up = (j > 0) ? f[i][j - 1] : center;
        double down = (j < h - 1) ? f[i][j + 1] : center;
        return (left + right + up + down - 4 * center);
    }
}
