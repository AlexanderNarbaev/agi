package io.matrix.federation.gpu;
import org.junit.jupiter.api.Test;
class VectorSpeedupBenchmarkTest {
  @Test void benchmark() {
    long[] a = new long[156]; long[] b = new long[156];
    java.util.Random r = new java.util.Random(42L);
    for (int i = 0; i < a.length; i++) { a[i] = r.nextLong(); b[i] = r.nextLong(); }
    int iters = 100000; long total = 0; MatrixNativeMath mnm = new MatrixNativeMath();
    long s1 = System.nanoTime();
    for (int i = 0; i < iters; i++) { long x = 0; for (int k = 0; k < a.length; k++) x += Long.bitCount(a[k]^b[k]); total += x; }
    long e1 = System.nanoTime();
    System.out.println("[SCALAR]" + (e1-s1)/1000000 + "ms total=" + total);
    if (mnm.backend() == MatrixNativeMath.Backend.CPU_VECTOR) {
      long s2 = System.nanoTime(); for (int i = 0; i < iters; i++) mnm.vectorXorPopCount(a, b); long e2 = System.nanoTime();
      System.out.println("[VECTOR]" + (e2-s2)/1000000 + "ms SPEEDUP=" + String.format("0,00", (double)(e1-s1)/(e2-s2)));
    } else System.out.println("[NOVEC]backend=" + mnm.backend());
  }
}
