package io.matrix.neuron;

/**
 * RUN 435 — BigInteger-friendly arithmetic primitives for exact-precision
 * use cases (no double rounding). Pure function.
 *
 * <p>Where Java already has {@link java.math.BigInteger}/{@code BigDecimal},
 * these static facades centralise the common patterns (modular exponentiation
 * via square-and-multiply, modular inverse via extended Euclidean, etc.).
 * CONSTITUTION I-safe.
 */
public final class BigArithmetic {

    private BigArithmetic() {}

    /** Modular exponentiation: (base^exp) mod mod. Square-and-multiply. */
    public static long modPow(long base, long exp, long mod) {
        if (mod == 1) return 0;
        long result = 1;
        base = base % mod;
        if (base < 0) base += mod;
        long e = exp;
        while (e > 0) {
            if ((e & 1) == 1) result = (result * base) % mod;
            e >>>= 1;
            base = (base * base) % mod;
        }
        return result;
    }

    /** GCD via Euclidean algorithm. */
    public static long gcd(long a, long b) {
        long x = Math.abs(a), y = Math.abs(b);
        while (y != 0) {
            long t = y;
            y = x % y;
            x = t;
        }
        return x;
    }

    /** LCM via gcd. */
    public static long lcm(long a, long b) {
        if (a == 0 || b == 0) return 0;
        return Math.abs(a) / gcd(a, b) * Math.abs(b);
    }

    /** Modular inverse of {@code a} modulo {@code mod} (assumes gcd=1). */
    public static long modInverse(long a, long mod) {
        if (mod == 1) return 0;
        long m0 = mod;
        long origMod = mod;
        if (mod < 0) mod = -mod;
        long A = a % mod;
        if (A < 0) A += mod;
        // Standard extended Euclidean (Stein's forms)
        long oldR = A, r = mod;
        long oldS = 1, s = 0;
        while (r != 0) {
            long q = oldR / r;
            long newR = oldR - q * r;
            oldR = r; r = newR;
            long newS = oldS - q * s;
            oldS = s; s = newS;
        }
        if (oldR != 1) throw new IllegalStateException("no inverse: gcd=" + oldR);
        long inv = oldS;
        if (inv < 0) inv += origMod;
        return inv;
    }

    /** Average of two long values, avoiding overflow. */
    public static long average(long a, long b) {
        return (a & b) + ((a ^ b) >>> 1);
    }

    /** Binomial coefficient C(n,k) modulo {@code mod}. Lucas theorem not used. */
    public static long binomSmall(int n, int k, long mod) {
        if (k < 0 || k > n) return 0;
        if (k == 0 || k == n) return 1;
        if (k * 2 > n) k = n - k;
        java.math.BigInteger num = java.math.BigInteger.ONE;
        java.math.BigInteger den = java.math.BigInteger.ONE;
        for (int i = 0; i < k; i++) {
            num = num.multiply(java.math.BigInteger.valueOf(n - i));
            den = den.multiply(java.math.BigInteger.valueOf(k - i));
        }
        return num.divide(den).mod(java.math.BigInteger.valueOf(mod)).longValue();
    }
}
