package matrix.bir;

import java.util.ArrayList;
import java.util.List;

/**
 * CLAUSESET-форма BIR (DESIGN-01 §3): набор конъюнктивных клауз (DNF) с pos/neg
 * масками в long[]. Оценка — машинные AND/CMP, ветвление по срабатыванию.
 * Иммутабельность та же, что у TtUnit (final + защитные копии).
 */
public final class ClauseSet {
    public static final class Clause {
        public final long[] pos; // обязательные единицы
        public final long[] neg; // обязательные нули
        public Clause(long[] pos, long[] neg) {
            this.pos = pos.clone(); this.neg = neg.clone();
        }
        /** Срабатывание клауза на входе x (little-endian слова). */
        public boolean fires(long[] x) {
            for (int w = 0; w < pos.length; w++) {
                if ((x[w] & pos[w]) != pos[w]) return false; // не все pos = 1
                if ((x[w] & neg[w]) != 0L) return false;     // есть neg = 1
            }
            return true;
        }
        /** Witness: биты, механически повлиявшие на выход (pos|neg). */
        public long[] witnessMask() {
            long[] m = new long[pos.length];
            for (int w = 0; w < pos.length; w++) m[w] = pos[w] | neg[w];
            return m;
        }
    }

    private final int kWords;
    private final List<Clause> clauses;

    public ClauseSet(int kBits, List<Clause> clauses) {
        this.kWords = (kBits + 63) / 64;
        this.clauses = List.copyOf(clauses);
    }

    /** OR всех клауз (один класс); witness первого сработавшего клауза. */
    public boolean eval(long[] x) {
        for (Clause c : clauses) if (c.fires(x)) return true;
        return false;
    }

    public List<Clause> clauses() { return clauses; }

    /** Читаемое правило (DESIGN-01 §3.3): «ЕСЛИ x1 И НЕ x4 И … ТО 1». */
    public String toHumanReadable(Clause c) {
        StringBuilder sb = new StringBuilder("ЕСЛИ ");
        List<String> lits = new ArrayList<>();
        for (int w = 0; w < kWords; w++) {
            for (int b = 0; b < 64; b++) {
                int i = w * 64 + b;
                if (((c.pos[w] >>> b) & 1L) == 1L) lits.add("x" + i);
                if (((c.neg[w] >>> b) & 1L) == 1L) lits.add("НЕ x" + i);
            }
        }
        sb.append(String.join(" И ", lits)).append(" ТО 1");
        return sb.toString();
    }
}
