package matrix.bir;

/**
 * TT-форма BIR (DESIGN-01 §2.1): плотная таблица истинности k<=20 входов.
 * Хранение: long[] little-endian, 2^k бит. Оценка: gather + shift.
 *
 * JMM/GraalVM: артефакт иммутабелен (final-поля) — безопасная публикация
 * через final-семантику JMM без синхронизации; отсутствие рефлексии и Unsafe
 * делает класс native-image-совместимым по умолчанию.
 */
public final class TtUnit {
    private final int k;
    private final long[] table; // 2^k бит, упаковано little-endian

    public TtUnit(int k, long[] table) {
        if (k < 1 || k > 20) throw new IllegalArgumentException("k in 1..20");
        this.k = k;
        this.table = table.clone(); // защитная копия: иммутабельность
    }

    public int k() { return k; }

    /** Оценка одного входа (индекс = битовый вектор входа). */
    public int eval(int input) {
        return (int) ((table[input >>> 6] >>> (input & 63)) & 1L);
    }

    /** Пакетная оценка: выходной массив бит (0/1). Горячий путь JVM: мономорфный цикл, C2 инлайнит. */
    public void evalBatch(int[] inputs, byte[] out) {
        long[] t = table;
        for (int i = 0; i < inputs.length; i++) {
            int x = inputs[i];
            out[i] = (byte) ((t[x >>> 6] >>> (x & 63)) & 1L);
        }
    }

    public long memoryBytes() { return 8L * table.length; }
}
