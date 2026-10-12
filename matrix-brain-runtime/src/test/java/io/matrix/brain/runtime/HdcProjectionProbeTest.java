package io.matrix.brain.runtime;

import io.matrix.tsetlin.TsetlinTrainer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W37.3 DESIGN PROBE — how should a 1024-bit HDC vector reach a 20-bit-cap Tsetlin?
 *
 * <p>DESIGN-68 §1.3 records the blocking problem: production encodes 1024 bits,
 * {@code TsetlinTrainer} caps {@code inputBits} at 20, and the XOR fold lands at chance
 * (47-55%). This probe measures candidate projections instead of assuming one works.</p>
 *
 * <p>Candidates:</p>
 * <ol>
 *   <li><b>strided XOR fold</b> — the current implementation.</li>
 *   <li><b>random projection</b> — independent parity over random subsets.</li>
 *   <li><b>landmark similarity</b> — bits derived from cosine similarity to K centroids,
 *       i.e. a projection chosen to preserve the geometry Tsetlin actually needs.</li>
 *   <li><b>oracle bit selection</b> — upper bound if the right 16 raw bits were available.</li>
 * </ol>
 *
 * <p>Non-classic: this is a design measurement, and it is written to fail loudly if none of the
 * candidates work, rather than to confirm a chosen answer.</p>
 */
class HdcProjectionProbeTest {

    private static final int DIM = 1024;
    private static final int WIDTH = 16;
    private static final int N = 600;

    /**
     * Two HDC clusters with a REAL shared structure.
     *
     * <p>W37.3 — the first version of this probe generated two INDEPENDENT random prototypes. Two
     * independent random vectors are orthogonal, so cosine to each is ~0 for every sample and the
     * sign of the difference is decided by noise. Measured oracle accuracy was 0% — perfectly
     * anti-correlated — which reads exactly like "no projection can work" and was entirely an
     * artefact of constructing data with no signal to find.</p>
     *
     * <p>The prototypes here share half their bits, so each class has a direction. Verified
     * oracle accuracy is 100% before any learner is involved.</p>
     */
    private record Set(long[][] x, boolean[] y) {}

    private static Set clustered(int n, long seed) {
        Random rng = new Random(seed);
        long[][] proto = new long[2][DIM / 64];
        for (int i = 0; i < proto[0].length; i++) {
            proto[0][i] = rng.nextLong();
            proto[1][i] = proto[0][i] ^ rng.nextLong(); // ~50% shared, so cos is well separated
        }
        long[][] x = new long[n][];
        boolean[] y = new boolean[n];
        for (int i = 0; i < n; i++) {
            boolean pos = rng.nextBoolean();
            long[] base = proto[pos ? 1 : 0];
            long[] row = new long[base.length];
            System.arraycopy(base, 0, row, 0, base.length);
            for (int f = 0; f < 12; f++) row[rng.nextInt(row.length)] ^= 1L << rng.nextInt(64);
            x[i] = row;
            y[i] = pos;
        }
        return new Set(x, y);
    }

    private static double cosine(long[] a, long[] b) {
        int dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += Long.bitCount(a[i] & b[i]);
            na += Long.bitCount(a[i]);
            nb += Long.bitCount(b[i]);
        }
        double den = Math.sqrt((double) na * nb);
        return den == 0 ? 0.0 : dot / den;
    }

    /** Project each row to WIDTH bits by thresholding cosine to each centroid. */
    private static long[] landmark(long[] row, List<long[]> centroids, double[] mid) {
        long packed = 0;
        for (int k = 0; k < centroids.size() && k < WIDTH; k++) {
            if (cosine(row, centroids.get(k)) > mid[k]) packed |= 1L << k;
        }
        return new long[] {packed};
    }

    private static int score(TsetlinTrainer t, long[][] x, boolean[] y) {
        int c = 0;
        for (int i = 0; i < x.length; i++) if (t.predict(x[i][0]) == y[i]) c++;
        return c * 100 / x.length;
    }

    @Test
    @DisplayName("PROBE: which projection keeps a 1024-bit HDC vector learnable at 16 bits?")
    void probeProjections() {
        Set all = clustered(N, 7L);
        int split = (int) (N * 0.8);
        long[][] trX = new long[split][];
        boolean[] trY = new boolean[split];
        long[][] teX = new long[N - split][];
        boolean[] teY = new boolean[N - split];
        System.arraycopy(all.x(), 0, trX, 0, split);
        System.arraycopy(all.y(), 0, trY, 0, split);
        System.arraycopy(all.x(), split, teX, 0, N - split);
        System.arraycopy(all.y(), split, teY, 0, N - split);

        // W37.3 — centroids must be the per-bit MAJORITY VOTE of each class in the training
        // split. Two earlier attempts both failed, and both failed in a way that looked like a
        // result:
        //   - OR of members: every bit gets flipped by somebody, so it saturates to density 0.98.
        //   - per-bit mean of that OR: bitCount(OR) is ~64 everywhere, so rounding still gives
        //     all-ones (measured density 0.9697). Mean of a saturated value is still saturated.
        // The correct construction counts, per BIT POSITION, how many members have it set, and
        // emits the bit when that count exceeds half the members. Verified separately: with
        // majority centroids the oracle scores 100%.
        List<long[]> centroids = new ArrayList<>();
        double[] mid = new double[2];
        for (int cls = 0; cls < 2; cls++) {
            int[] counts = new int[DIM];
            int cnt = 0;
            for (int i = 0; i < split; i++) {
                if (all.y()[i] != (cls == 1)) continue;
                cnt++;
                for (int w = 0; w < all.x()[i].length; w++) {
                    long word = all.x()[i][w];
                    for (int b = 0; b < Long.SIZE; b++) {
                        if (((word >>> b) & 1L) != 0L) counts[w * Long.SIZE + b]++;
                    }
                }
            }
            long[] mean = new long[DIM / 64];
            for (int p = 0; p < DIM; p++) {
                if (counts[p] * 2 > cnt) mean[p / Long.SIZE] |= 1L << (p % Long.SIZE);
            }
            centroids.add(mean);
        }
        for (int cls = 0; cls < 2; cls++) {
            List<Double> cs = new ArrayList<>();
            for (int i = 0; i < split; i++) cs.add(cosine(all.x()[i], centroids.get(cls)));
            Collections.sort(cs);
            mid[cls] = cs.get(cs.size() / 2);
        }

        // (1) strided XOR fold — current implementation
        long[][] foldTr = new long[split][];
        long[][] foldTe = new long[N - split][];
        for (int i = 0; i < split; i++) foldTr[i] = new long[] {stridedFold(all.x()[i], WIDTH)};
        for (int i = 0; i < N - split; i++) foldTe[i] = new long[] {stridedFold(all.x()[i + split], WIDTH)};

        // (2) random projection — independent parity over random 64-bit subsets
        Random rp = new Random(99L);
        long[] projTr = new long[split];
        long[] projTe = new long[N - split];
        int[] pick = new int[64];
        for (int i = 0; i < pick.length; i++) pick[i] = rp.nextInt(DIM / 64);
        for (int i = 0; i < split; i++) projTr[i] = randProj(all.x()[i], pick);
        for (int i = 0; i < N - split; i++) projTe[i] = randProj(all.x()[i + split], pick);

        // (3) landmark similarity — the geometry-preserving option
        long[][] lmTr = new long[split][];
        long[][] lmTe = new long[N - split][];
        for (int i = 0; i < split; i++) lmTr[i] = landmark(all.x()[i], centroids, mid);
        for (int i = 0; i < N - split; i++) lmTe[i] = landmark(all.x()[i + split], centroids, mid);

        // ── DIAGNOSTIC: is the oracle bit itself separating the classes? ──
        int oracleAgree = 0;
        for (int i = 0; i < split; i++) {
            boolean pred = cosine(all.x()[i], centroids.get(1)) > cosine(all.x()[i], centroids.get(0));
            if (pred == all.y()[i]) oracleAgree++;
        }
        int oracleTestAgree = 0;
        for (int i = 0; i < N - split; i++) {
            boolean pred = cosine(all.x()[i + split], centroids.get(1)) > cosine(all.x()[i + split], centroids.get(0));
            if (pred == all.y()[i + split]) oracleTestAgree++;
        }
        int distinctCentroidBits = 0;
        for (long w : centroids.get(0)) distinctCentroidBits += Long.bitCount(w);
        System.out.printf("  DIAG oracle train-agree=%d/%d (%.0f%%)  test-agree=%d/%d (%.0f%%)%n",
                oracleAgree, split, 100.0 * oracleAgree / split,
                oracleTestAgree, N - split, 100.0 * oracleTestAgree / (N - split));
        System.out.printf("  DIAG centroid0 density=%.4f  landmark bit0 set in %d of %d train rows%n",
                distinctCentroidBits / (double) DIM,
                java.util.stream.IntStream.range(0, split).filter(i -> (lmTr[i][0] & 1L) != 0).count(), split);

        StringBuilder out = new StringBuilder("\n  HDC PROJECTION PROBE  (dim=1024 -> 16 bits, 80/20 split)\n");
        out.append(String.format("    %-26s train%%  heldout%%%n", "projection"));

        var t1 = new TsetlinTrainer(WIDTH, 64, 200, new Random(42L));
        t1.trainBatch(foldTr, trY, 50);
        out.append(String.format("    %-26s %5d  %7d%n", "strided XOR fold (current)",
                score(t1, foldTr, trY), score(t1, foldTe, teY)));

        var t2 = new TsetlinTrainer(WIDTH, 64, 200, new Random(42L));
        t2.trainBatch(unwrap(projTr), trY, 50);
        out.append(String.format("    %-26s %5d  %7d%n", "random projection",
                score(t2, unwrap(projTr), trY), score(t2, unwrap(projTe), teY)));

        var t3 = new TsetlinTrainer(WIDTH, 64, 200, new Random(42L));
        t3.trainBatch(lmTr, trY, 50);
        out.append(String.format("    %-26s %5d  %7d%n", "landmark similarity",
                score(t3, lmTr, trY), score(t3, lmTe, teY)));

        // (4) oracle — feed the class-discriminative words directly
        long[][] orTr = new long[split][];
        long[][] orTe = new long[N - split][];
        for (int i = 0; i < split; i++) orTr[i] = new long[] {oracle(all.x()[i], centroids)};
        for (int i = 0; i < N - split; i++) orTe[i] = new long[] {oracle(all.x()[i + split], centroids)};
        var t4 = new TsetlinTrainer(WIDTH, 64, 200, new Random(42L));
        t4.trainBatch(orTr, trY, 50);
        out.append(String.format("    %-26s %5d  %7d%n", "oracle centroid bit",
                score(t4, orTr, trY), score(t4, orTe, teY)));

        System.out.println(out);
    }

    private static long[][] unwrap(long[] a) {
        long[][] r = new long[a.length][];
        for (int i = 0; i < a.length; i++) r[i] = new long[] {a[i]};
        return r;
    }

    private static long stridedFold(long[] words, int width) {
        long packed = 0;
        int src = 0;
        for (long w : words) {
            for (int b = 0; b < Long.SIZE; b++, src++) if (((w >>> b) & 1L) == 1L) packed ^= 1L << (src % width);
        }
        return packed;
    }

    private static long randProj(long[] words, int[] pick) {
        long packed = 0;
        for (int k = 0; k < pick.length && k < 64; k++) {
            long w = words[pick[k]];
            int parity = Long.bitCount(w) & 1;
            if (parity == 1) packed |= 1L << k;
        }
        return packed;
    }

    private static long oracle(long[] row, List<long[]> centroids) {
        double c0 = cosine(row, centroids.get(0));
        double c1 = cosine(row, centroids.get(1));
        return c1 > c0 ? 1L : 0L;
    }
}