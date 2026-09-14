package io.matrix.consciousness;

import io.matrix.neuron.HdcEncoding;

import java.util.Random;

/**
 * Integration metrics for measuring consciousness-like integration in MATRIX.
 *
 * <h2>Metrics implemented</h2>
 * <ul>
 *   <li>Phi_binary (Tononi 2004 BMC): exact Phi for N <= 8 binary systems</li>
 *   <li>PhiF (EMD-based): 1 - W1(forward, backward) on Hamming cube</li>
 *   <li>PhiR (Mediano 2022): redundancy-suppressing Phi</li>
 *   <li>C_N (neural complexity): cheap integration proxy</li>
 * </ul>
 */
public final class IntegrationMetrics {

    private IntegrationMetrics() {}

    public enum Type {
        PERIODIC, SPARSE, RECURRENT, HIERARCHICAL, GAUSSIAN
    }

    private static int popcount(int x) {
        return Integer.bitCount(x);
    }

    private static int nextPowerOf2(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return Math.max(p, 2);
    }

    private static double shannonBinary(long c0, long c1) {
        long total = c0 + c1;
        if (total == 0 || c0 == 0 || c1 == 0) return 0.0;
        double p0 = (double) c0 / total;
        double p1 = (double) c1 / total;
        return -p0 * Math.log(p0) / Math.log(2) - p1 * Math.log(p1) / Math.log(2);
    }

    private static double shannon(long[] counts, long total) {
        return entropy(counts, total);
    }

    private static double entropy(long[] counts, long total) {
        double h = 0.0;
        for (long c : counts) {
            if (c == 0) continue;
            double p = (double) c / total;
            h -= p * Math.log(p) / Math.log(2);
        }
        return h;
    }

    // ============ Phi_binary (Tononi 2004 BMC) ============

    public static double phiBinary(long[] trajectory, int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException("N in [1, 8] for phiBinary, got " + N);
        }
        if (trajectory == null || trajectory.length == 0) {
            throw new IllegalArgumentException("empty trajectory");
        }
        long[] counts = new long[1 << N];
        for (long s : trajectory) {
            counts[(int) s & ((1 << N) - 1)]++;
        }
        double minMi = Double.POSITIVE_INFINITY;
        for (int mask = 1; mask < (1 << N) - 1; mask++) {
            double mi = mutualInformationBipartition(counts, mask, N);
            if (mi < minMi) minMi = mi;
        }
        return minMi;
    }

    private static double mutualInformationBipartition(long[] counts, int mask, int N) {
        int aSize = popcount(mask);
        int bSize = N - aSize;
        long[] margA = new long[1 << aSize];
        long[] margB = new long[1 << bSize];
        long total = 0;
        for (int state = 0; state < counts.length; state++) {
            long c = counts[state];
            if (c == 0) continue;
            int aIdx = projectState(state, mask, N);
            int bIdx = projectState(state, ~mask & ((1 << N) - 1), N);
            margA[aIdx] += c;
            margB[bIdx] += c;
            total += c;
        }
        if (total == 0) return 0.0;
        double hA = entropy(margA, total);
        double hB = entropy(margB, total);
        double hJoint = entropy(counts, total);
        return hA + hB - hJoint;
    }

    private static int projectState(int state, int mask, int N) {
        int idx = 0;
        int bit = 0;
        for (int i = 0; i < N; i++) {
            if ((mask & (1 << i)) != 0) {
                idx |= (((state >> i) & 1) << bit);
                bit++;
            }
        }
        return idx;
    }

    // ============ PhiF (EMD-based) ============

    public static double phiF(double[] forwardDist, double[] backwardDist) {
        if (forwardDist.length != backwardDist.length) {
            throw new IllegalArgumentException("dist length mismatch");
        }
        int nStates = forwardDist.length;
        int nBits = (int) Math.round(Math.log(nStates) / Math.log(2));
        if (nStates != (1 << nBits)) {
            throw new IllegalArgumentException("nStates must be power of 2, got " + nStates);
        }
        double[][] cost = hammingCostMatrix(nBits);
        double w1 = wasserstein1LP(forwardDist, backwardDist, cost);
        return Math.max(0.0, 1.0 - w1 / Math.log(nStates));
    }

    private static double[][] hammingCostMatrix(int N) {
        int size = 1 << N;
        double[][] c = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                c[i][j] = Integer.bitCount(i ^ j);
            }
        }
        return c;
    }

    private static double wasserstein1LP(double[] p, double[] q, double[][] cost) {
        int n = p.length;
        double[] supply = p.clone();
        double[] demand = q.clone();
        double total = 0.0;
        double[] costFlat = new double[n * n];
        Integer[] order = new Integer[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                costFlat[i * n + j] = cost[i][j];
                order[i * n + j] = i * n + j;
            }
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(costFlat[a], costFlat[b]));
        for (Integer idx : order) {
            int i = idx / n, j = idx % n;
            if (supply[i] < 1e-12 || demand[j] < 1e-12) continue;
            double amount = Math.min(supply[i], demand[j]);
            total += amount * cost[i][j];
            supply[i] -= amount;
            demand[j] -= amount;
        }
        return total;
    }

    // ============ C_N (Neural Complexity) ============

    public static double neuralComplexity(long[] trajectory, int N) {
        if (N < 1 || N > 16) {
            throw new IllegalArgumentException("N in [1, 16] for cN, got " + N);
        }
        long total = trajectory.length;
        if (total == 0) return 0.0;
        double sumH = 0.0;
        double sumMI = 0.0;
        for (int i = 0; i < N; i++) {
            long[] margI = new long[2];
            long[] margMinusI = new long[1 << (N - 1)];
            long[] joint = new long[2 * (1 << (N - 1))];
            for (long s : trajectory) {
                int xi = (int) ((s >> i) & 1L);
                int xMinusI = 0;
                int bitPos = 0;
                for (int j = 0; j < N; j++) {
                    if (j != i) {
                        xMinusI |= (int) (((s >> j) & 1L) << bitPos);
                        bitPos++;
                    }
                }
                margI[xi]++;
                margMinusI[xMinusI]++;
                joint[(xi << (N - 1)) | xMinusI]++;
            }
            double hi = shannonBinary(margI[0], margI[1]);
            double hMinusI = entropy(margMinusI, total);
            double hJoint = entropy(joint, total);
            sumH += hi;
            sumMI += (hi + hMinusI - hJoint);
        }
        return Math.max(0.0, sumH - sumMI);
    }

    // ============ PhiR (Mediano 2022) ============

    public static double phiR(long[] trajectory, int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException("N in [1, 8] for phiR, got " + N);
        }
        if (trajectory == null || trajectory.length == 0) {
            throw new IllegalArgumentException("empty trajectory");
        }
        long[] counts = new long[1 << N];
        for (long s : trajectory) {
            counts[(int) s & ((1 << N) - 1)]++;
        }
        double minIR = Double.POSITIVE_INFINITY;
        for (int mask = 1; mask < (1 << N) - 1; mask++) {
            double ir = redundancyPenalizedMI(counts, mask, N);
            if (ir < minIR) minIR = ir;
        }
        return minIR;
    }

    private static double redundancyPenalizedMI(long[] counts, int mask, int N) {
        double sum = 0.0;
        for (int u = 0; u < N; u++) {
            if ((mask & (1 << u)) == 0) continue;
            double miUB = miUnitToGroup(counts, u, ~mask & ((1 << N) - 1), N);
            double miUBcondA = miUnitToGroupCond(
                    counts, u,
                    ~mask & ((1 << N) - 1),
                    mask & ~(1 << u), N);
            sum += Math.min(miUB, miUBcondA);
        }
        for (int v = 0; v < N; v++) {
            if ((~mask & (1 << v) & ((1 << N) - 1)) == 0) continue;
            double miVA = miUnitToGroup(counts, v, mask, N);
            double miVAcondB = miUnitToGroupCond(
                    counts, v, mask,
                    ~mask & ((1 << N) - 1) & ~(1 << v), N);
            sum += Math.min(miVA, miVAcondB);
        }
        return sum / 2.0;
    }

    private static double miUnitToGroup(long[] counts, int unit, int groupMask, int N) {
        long total = 0;
        for (long c : counts) total += c;
        if (total == 0) return 0.0;
        int gSize = popcount(groupMask);
        int gStates = 1 << gSize;
        long[] joint = new long[2 * gStates];
        long[] margU = new long[2];
        long[] margG = new long[gStates];
        for (int state = 0; state < counts.length; state++) {
            long c = counts[state];
            if (c == 0) continue;
            int u = (state >> unit) & 1;
            int g = 0;
            int bitPos = 0;
            for (int i = 0; i < N; i++) {
                if ((groupMask & (1 << i)) != 0) {
                    g |= (((state >> i) & 1) << bitPos);
                    bitPos++;
                }
            }
            joint[(u << gSize) | g] += c;
            margU[u] += c;
            margG[g] += c;
        }
        double hU = entropy(margU, total);
        double hG = entropy(margG, total);
        double hJoint = entropy(joint, total);
        return hU + hG - hJoint;
    }

    private static double miUnitToGroupCond(long[] counts, int unit,
                                              int groupMask, int condMask, int N) {
        long total = 0;
        for (long c : counts) total += c;
        if (total == 0) return 0.0;
        if (condMask == 0) return miUnitToGroup(counts, unit, groupMask, N);
        int gSize = popcount(groupMask);
        int cSize = popcount(condMask);
        int gStates = 1 << gSize;
        int cStates = 1 << cSize;
        long[] margUC = new long[2 * cStates];
        long[] margGC = new long[gStates * cStates];
        long[] margC = new long[cStates];
        long[] jointUGC = new long[2 * gStates * cStates];
        for (int state = 0; state < counts.length; state++) {
            long c = counts[state];
            if (c == 0) continue;
            int u = (state >> unit) & 1;
            int g = 0, cIdx = 0;
            int bitPosG = 0, bitPosC = 0;
            for (int i = 0; i < N; i++) {
                if ((groupMask & (1 << i)) != 0) {
                    g |= (((state >> i) & 1) << bitPosG);
                    bitPosG++;
                }
                if ((condMask & (1 << i)) != 0) {
                    cIdx |= (((state >> i) & 1) << bitPosC);
                    bitPosC++;
                }
            }
            margUC[cIdx * 2 + u] += c;
            margGC[g * cStates + cIdx] += c;
            margC[cIdx] += c;
            jointUGC[(g * cStates + cIdx) * 2 + u] += c;
        }
        double hUC = entropy(margUC, total);
        double hGC = entropy(margGC, total);
        double hJoint = entropy(jointUGC, total);
        return hUC + hGC - hJoint;
    }

    // ============ Helpers for MATRIX primitives ============

    public static double phiBinaryFromBitLinear(int[][] activations, int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException("N in [1, 8]");
        }
        for (int[] act : activations) {
            if (act == null || act.length < N) {
                throw new IllegalArgumentException("activations have insufficient length");
            }
        }
        long[] trajectory = new long[activations.length];
        for (int t = 0; t < activations.length; t++) {
            int state = 0;
            for (int i = 0; i < N; i++) {
                int bit = activations[t][i] > 0 ? 1 : 0;
                state |= (bit << i);
            }
            trajectory[t] = state;
        }
        return phiBinary(trajectory, N);
    }

    public static double phiRFromBitLinear(int[][] activations, int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException("N in [1, 8]");
        }
        for (int[] act : activations) {
            if (act == null || act.length < N) {
                throw new IllegalArgumentException("activations have insufficient length");
            }
        }
        long[] trajectory = new long[activations.length];
        for (int t = 0; t < activations.length; t++) {
            int state = 0;
            for (int i = 0; i < N; i++) {
                int bit = activations[t][i] > 0 ? 1 : 0;
                state |= (bit << i);
            }
            trajectory[t] = state;
        }
        return phiR(trajectory, N);
    }

    public static double cNFromHdcCodes(long[][] hdcCodes, int N) {
        if (N < 1 || N > 16) {
            throw new IllegalArgumentException("N in [1, 16]");
        }
        long[] trajectory = new long[hdcCodes.length];
        for (int t = 0; t < hdcCodes.length; t++) {
            int state = 0;
            for (int i = 0; i < N; i++) {
                int bit = ((hdcCodes[t][i >>> 6] >>> (i & 63)) & 1L) != 0 ? 1 : 0;
                state |= (bit << i);
            }
            trajectory[t] = state;
        }
        return neuralComplexity(trajectory, N);
    }

    public static double phiFFromBitLinear(int[][] activations, int N) {
        if (N < 1 || N > 16) {
            throw new IllegalArgumentException("N in [1, 16]");
        }
        for (int[] act : activations) {
            if (act == null || act.length < N) {
                throw new IllegalArgumentException("activations have insufficient length");
            }
        }
        int nBins = nextPowerOf2(N + 1);
        double[] forwardDist = new double[nBins];
        double[] backwardDist = new double[nBins];
        for (int t = 0; t < activations.length; t++) {
            int density = 0;
            for (int i = 0; i < N; i++) if (activations[t][i] > 0) density++;
            forwardDist[density] += 1.0;
            backwardDist[density] += 1.0;
        }
        double sumF = 0, sumB = 0;
        for (double v : forwardDist) sumF += v;
        for (double v : backwardDist) sumB += v;
        for (int i = 0; i < forwardDist.length; i++) {
            forwardDist[i] /= sumF;
            backwardDist[i] /= sumB;
        }
        return phiF(forwardDist, backwardDist);
    }
}
