package io.matrix.brain.runtime;

import io.matrix.tsetlin.TsetlinTrainer;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W37.3 PROBE (temporary) — is the 46% a hyperparameter problem or a data-encoding problem?
 *
 * <p>The pinned production test builds each sample as {@code long[16]} with {@code x[j] =
 * rng.nextInt(2)} and {@code labels[i] = x[0] == 1}. But {@code TsetlinTrainer.pack(long[])}
 * returns {@code words[0]}, so 15 of the 16 "features" are discarded before the trainer ever
 * sees them, and the label is a function of the one feature that survives.</p>
 *
 * <p>So the pinned test may be measuring a degenerate 1-bit task while presenting it as 16-bit.
 * If so, tuning epochs to "fix 46% -> 80%" would be tuning against a broken fixture, not
 * against a real capability gap. This probe trains on the SAME function with two encodings.</p>
 */
class TsetlinEncodingProbeTest {

    private static TsetlinTrainer trainer() { return new TsetlinTrainer(16, 64, 200, new Random(42L)); }

    /**
     * REGRESSION GUARD for the W37.3 fix — the trainer must see the WHOLE feature vector.
     *
     * <p>Before the fix, {@code pack()} returned {@code words[0]}. A 16-word row therefore
     * collapsed to as few as 2 distinct inputs, so the learner was fitting a two-valued
     * function while holding 400 rows. This asserts the fold actually separates distinct rows,
     * which is the property that was silently absent.</p>
     *
     * <p>These assertions call the REAL {@code pack} through {@code packForTest}. An earlier
     * draft of this guard reimplemented the fold locally in the test and passed even with the
     * bug restored — verified by mutation. A guard that reimplements the logic it guards
     * measures nothing.</p>
     */
    @Test
    @DisplayName("pack() uses every word: distinct rows stay distinct after folding")
    void packUsesEveryWord() {
        Random rng = new Random(11);
        java.util.Set<Long> distinct = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            long[] row = new long[16];
            for (int j = 0; j < row.length; j++) row[j] = rng.nextLong();
            distinct.add(trainer().packForTest(row));
        }
        assertThat(distinct.size())
                .as("200 random 16-word rows must remain distinguishable after folding to 16 bits. "
                        + "A tiny count means pack() is discarding words again.")
                .isGreaterThan(190);
    }

    /**
     * REGRESSION GUARD — the low 64 bits alone must not be the only thing that survives.
     */
    @Test
    @DisplayName("pack() is sensitive to high words, not just words[0]")
    void packIsSensitiveToHighWords() {
        long[] low = new long[16];
        long[] high = low.clone();
        high[15] = 1L << 40; // a bit that lives entirely in the LAST word

        assertThat(trainer().packForTest(high))
                .as("a bit in word[15] must change the folded value; if it does not, pack() is "
                        + "still reading only words[0]")
                .isNotEqualTo(trainer().packForTest(low));
    }


    /** Sample set: features plus labels. */
    private record Data(long[][] x, boolean[] y) {}

    /**
     * The pinned production encoding: 16 separate longs per row, of which the trainer reads one.
     */
    private static Data wordEncoded(int n) {
        Random rng = new Random(7);
        long[][] x = new long[n][];
        boolean[] y = new boolean[n];
        for (int i = 0; i < n; i++) {
            long[] row = new long[16];
            for (int j = 0; j < 16; j++) row[j] = rng.nextInt(2);
            y[i] = row[0] == 1;
            x[i] = row;
        }
        return new Data(x, y);
    }

    /**
     * Honest encoding: one long whose 16 bits actually vary, so all 16 features reach the
     * trainer instead of only bit 0.
     */
    private static Data bitPacked(int n) {
        Data base = wordEncoded(n);
        long[][] x = new long[n][];
        for (int i = 0; i < n; i++) {
            long packed = 0;
            for (int j = 0; j < 16; j++) if (base.x()[i][j] == 1) packed |= (1L << j);
            x[i] = new long[] {packed};
        }
        return new Data(x, base.y());
    }

    private static int score(TsetlinTrainer t, long[][] x, boolean[] y) {
        int c = 0;
        for (int i = 0; i < x.length; i++) if (t.predict(x[i][0]) == y[i]) c++;
        return c * 100 / x.length;
    }

    @Test
    @DisplayName("PROBE: does the XOR fold deliver the whole feature vector?")
    void probeEncodings() {
        int[] epochs = {10, 50, 200};
        StringBuilder out = new StringBuilder("\n  ENCODING PROBE (label = bit 0)\n");

        // distinct inputs the trainer can now distinguish
        Data wide = bitPacked(400);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (long[] r : wide.x()) seen.add(r[0]);
        out.append("    bit-packed 400 rows -> ").append(seen.size())
           .append(" distinct inputs after the fold\n");

        // the decisive case: 1024-bit HDC vectors, production width, 16 words per row
        Random rng = new Random(7);
        long[][] x1024 = new long[400][];
        boolean[] y1024 = new boolean[400];
        for (int i = 0; i < 400; i++) {
            long[] row = new long[16];
            for (int j = 0; j < 16; j++) row[j] = rng.nextLong();
            row[0] = rng.nextInt(2);
            y1024[i] = (row[0] & 1L) == 1L;
            x1024[i] = row;
        }
        java.util.Set<Long> seenWide = new java.util.HashSet<>();
        for (int e : epochs) {
            var t = new TsetlinTrainer(16, 64, 200, new Random(42L));
            t.trainBatch(x1024, y1024, e);
            if (e == epochs[0]) {
                // report distinctness by retraining once and probing the fold via predict
                for (long[] r : x1024) seenWide.add(fold(r));
                out.append("    16-word rows collapse to ").append(seenWide.size())
                   .append(" distinct 16-bit inputs (was 2 distinct values before the fix)\n");
            }
            out.append("    1024-bit production-shaped, epochs=").append(e)
               .append(" -> ").append(scoreWide(t, x1024, y1024)).append("%\n");
        }
        System.out.println(out);
    }

    /** Mirror of TsetlinTrainer.pack for measurement only. */
    private static long fold(long[] words) {
        long packed = 0L;
        int srcBit = 0;
        for (int w = 0; w < words.length; w++) {
            long word = words[w];
            for (int b = 0; b < Long.SIZE; b++, srcBit++) {
                if (((word >>> b) & 1L) == 1L) packed ^= 1L << (srcBit % 16);
            }
        }
        return packed;
    }

    private static int scoreWide(TsetlinTrainer t, long[][] x, boolean[] y) {
        int c = 0;
        for (int i = 0; i < x.length; i++) if (t.predict(fold(x[i])) == y[i]) c++;
        return c * 100 / x.length;
    }
}
