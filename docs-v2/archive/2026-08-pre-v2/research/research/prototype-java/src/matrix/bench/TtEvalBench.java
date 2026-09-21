package matrix.bench;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import matrix.bir.TtUnit;

/**
 * Замер TT-eval на JVM (без JMH — прототипная скобка, JAVA_NATIVE §4):
 *  - on-heap long[] (TtUnit);
 *  - off-heap direct ByteBuffer (модель будущего FFM-варианта: обход лимита
 *    кучи JMM для крупных таблиц и zero-copy к FPGA/DMA-субстратам).
 * Прогрев C2 обязателен; числа — порядок величины, продакшн-замер — JMH.
 */
public final class TtEvalBench {
    public static void main(String[] args) {
        int k = 16;
        int words = 1 << (k - 6);
        long[] table = new long[words];
        long x = 0x9E3779B97F4A7C15L; // детерминированный xorshift вместо Random (детерминизм прогонов)
        for (int i = 0; i < words; i++) { x ^= x << 13; x ^= x >>> 7; x ^= x << 17; table[i] = x; }

        int n = 20_000_000;
        int[] inputs = new int[n];
        long s = 42;
        for (int i = 0; i < n; i++) { s = s * 6364136223846793005L + 1442695040888963407L; inputs[i] = (int) ((s >>> 33) & ((1 << k) - 1)); }

        // --- on-heap ---
        TtUnit unit = new TtUnit(k, table);
        byte[] out = new byte[n];
        for (int w = 0; w < 3; w++) unit.evalBatch(inputs, out); // прогрев
        long t0 = System.nanoTime();
        unit.evalBatch(inputs, out);
        long t1 = System.nanoTime();
        long sum = 0; for (byte b : out) sum += b;
        System.out.printf("on-heap  long[]      : %.2f нс/оценка (%,d оценок, checksum=%d)%n",
                (t1 - t0) / (double) n, n, sum);

        // --- off-heap direct buffer ---
        ByteBuffer buf = ByteBuffer.allocateDirect(words * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (long v : table) buf.putLong(v);
        for (int w = 0; w < 3; w++) sum = benchDirect(buf, inputs);
        t0 = System.nanoTime();
        sum = benchDirect(buf, inputs);
        t1 = System.nanoTime();
        System.out.printf("off-heap direct BB   : %.2f нс/оценка (checksum=%d)%n", (t1 - t0) / (double) n, sum);

        // память
        System.out.printf("память таблицы k=16  : %d байт (on-heap) / %d байт (direct)%n",
                unit.memoryBytes(), buf.capacity());
        System.out.println("примечание: продакшн-замеры — JMH на целевом железе (METRICS.md); " +
                "native-image: класс компилируется без рефлексии/Unsafe (JAVA_NATIVE §2)");
    }

    private static long benchDirect(ByteBuffer buf, int[] inputs) {
        long acc = 0;
        for (int x : inputs) {
            long word = buf.getLong((x >>> 6) * 8);
            acc += (word >>> (x & 63)) & 1L;
        }
        return acc;
    }
}
