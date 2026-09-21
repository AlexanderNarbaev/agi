package io.matrix.bir;

/**
 * BIR limits configuration (SPEC-002 INV-2).
 *
 * <p>Reads {@code matrix.bir.max-literals} from system properties, falling
 * back to the {@code MATRIX_BIR_MAX_LITERALS} environment variable, then to
 * {@link #DEFAULT_MAX_LITERALS}. Pure static access with no CDI so that BIR
 * value forms stay Quarkus-free and deterministic. The default is also
 * declared in {@code application.properties} for documentation.
 */
public final class BirLimits {

    /** System property name for the literal limit. */
    public static final String MAX_LITERALS_PROPERTY = "matrix.bir.max-literals";

    /** Environment variable fallback for the literal limit. */
    public static final String MAX_LITERALS_ENV = "MATRIX_BIR_MAX_LITERALS";

    /** Default maximum number of literals (input/output bits) per BIR form. */
    public static final int DEFAULT_MAX_LITERALS = 4096;

    private BirLimits() {}

    /**
     * Returns the configured maximum number of literals per BIR form.
     *
     * @return max literals (≥ 1)
     * @throws IllegalArgumentException if the configured value is not a positive integer
     */
    public static int maxLiterals() {
        String v = System.getProperty(MAX_LITERALS_PROPERTY);
        if (v == null || v.isBlank()) {
            v = System.getenv(MAX_LITERALS_ENV);
        }
        if (v == null || v.isBlank()) {
            return DEFAULT_MAX_LITERALS;
        }
        try {
            int n = Integer.parseInt(v.trim());
            if (n < 1) {
                throw new IllegalArgumentException(
                        MAX_LITERALS_PROPERTY + " must be ≥ 1, got: " + n);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + MAX_LITERALS_PROPERTY + " value: '" + v + "'", e);
        }
    }
}
