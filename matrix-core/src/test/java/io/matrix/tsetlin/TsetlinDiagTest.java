package io.matrix.tsetlin;

import org.junit.jupiter.api.Test;
import java.util.Random;

class TsetlinDiagTest {
    @Test
    void traceOr() {
        var tr = new TsetlinTrainer(2, 8, 10, new Random(1L), TsetlinTrainer.InitStrategy.RANDOM);
        long[][] X = {{0},{1},{2},{3}};
        boolean[] y = {false,true,true,true};
        System.out.println("INIT:");
        for (int i = 0; i < tr.clauseCount(); i++) System.out.println(tr.dbgClause(i));
        for (int e = 1; e <= 3; e++) {
            tr.trainBatch(X, y, 400);
            System.out.println("after " + (e*400) + " epochs:");
            for (int i = 0; i < tr.clauseCount(); i++) System.out.println(tr.dbgClause(i));
        }
    }
}
