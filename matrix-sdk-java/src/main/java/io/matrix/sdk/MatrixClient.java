package io.matrix.sdk;

/**
 * WAVE T-01 placeholder — Wave T-08 will implement the full Java SDK facade.
 *
 * <p>Final SDK contract (T-08):</p>
 * <pre>{@code
 *   MatrixClient client = MatrixClient.builder()
 *       .apiKey("sk-matrix-...")
 *       .baseUrl("https://api.matrix.ai")
 *       .build();
 *   AnalyzeResponse resp = client.analyze().text("What is 2+2?").call();
 * }</pre>
 *
 * <p><b>CONSTITUTION compliance:</b> Pure transport, no inference, no
 * LLM call. All computation happens server-side in {@code matrix-core}.</p>
 */
public final class MatrixClient {

    /** Default base URL — overridable in T-08 via builder. */
    public static final String DEFAULT_BASE_URL = "https://api.matrix.ai";

    /** SDK version for telemetry headers. */
    public static final String SDK_VERSION = "0.1.0-T01";

    private MatrixClient() {
        // static facade only in T-01
    }

    /** Returns the default base URL. Used by health checks. */
    public static String baseUrl() {
        return DEFAULT_BASE_URL;
    }
}
