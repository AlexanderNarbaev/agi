package io.matrix.security;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * The operating mode of a MATRIX process, and the bind address it may use
 * (RECON-W43.0).
 *
 * <p><b>Why this class exists.</b> The W42 audit found that MATRIX had no
 * effective access control anywhere, and that the reason was structural
 * rather than accidental: the repository contains ELEVEN raw
 * {@code HttpServer}/{@code ServerSocket} listeners next to the Quarkus
 * application, and a filter placed on one of them would have protected one
 * route while leaving the other ten open. A security boundary that is
 * implemented per-server is a boundary that will be missed.
 *
 * <p>So the boundary is decided ONCE, here, and every listener asks this
 * class where it may bind and whether it must authenticate. A server that
 * does not consult it is the gap this class is designed to close, and the
 * audit that follows has exactly one question to ask: <em>does every
 * listener bind through {@link #bindAddress()}?</em>
 *
 * <p><b>The two modes.</b>
 *
 * <ul>
 *   <li>{@link Mode#LOCAL_ONLY} — the default. The process binds the
 *       loopback interface, so no other host can reach it, and the local
 *       user is trusted. This is the posture the mission's §10.3 asks for
 *       ("local autonomy"), and it needs no credentials to operate.</li>
 *   <li>{@link Mode#NETWORK_EXPOSED} — the process is reachable from other
 *       hosts. This mode is <b>deny-by-default</b>: it refuses to start
 *       unless an authentication token is configured, and until it is, every
 *       state-changing route is refused.</li>
 * </ul>
 *
 * <p><b>Fail closed.</b> {@link #guard(String)} throws when a component asks
 * to be exposed to the network without a configured token. A misconfigured
 * deployment stops; it does not silently serve.
 *
 * <p><b>Configuration.</b> The mode and the token are read, in order, from
 * the system properties {@code matrix.security.mode} and
 * {@code matrix.security.token}, then from the environment variables
 * {@code MATRIX_SECURITY_MODE} and {@code MATRIX_SECURITY_TOKEN}. An
 * unrecognised mode is an ERROR, not a default: a typo in a deployment
 * variable must not silently mean "local" or, worse, "exposed".
 *
 * <p><b>What this class does not claim.</b> It is not an identity provider,
 * it is not a role model, and it is not a session system. It decides two
 * things — where a listener may bind, and whether a request carries the
 * configured credential — and it does those two things in one place so that
 * they cannot disagree.
 */
public final class NetworkExposure {

    /** The system property that names the mode. */
    public static final String MODE_PROPERTY = "matrix.security.mode";

    /** The system property that carries the authentication token. */
    public static final String TOKEN_PROPERTY = "matrix.security.token";

    /** The environment variable that names the mode. */
    public static final String MODE_ENVIRONMENT = "MATRIX_SECURITY_MODE";

    /** The environment variable that carries the authentication token. */
    public static final String TOKEN_ENVIRONMENT = "MATRIX_SECURITY_TOKEN";

    /** The loopback address: the only address a local-only process binds. */
    public static final String LOOPBACK = "127.0.0.1";

    /** The wildcard address: every interface of the host. */
    public static final String ALL_INTERFACES = "0.0.0.0";

    /** The mode a process runs in. */
    public enum Mode {
        /** Reachable only from the local host. No credential is required. */
        LOCAL_ONLY,
        /** Reachable from other hosts. Requires a configured credential. */
        NETWORK_EXPOSED;

        /** The mode named by a configuration value. */
        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return LOCAL_ONLY;
            }
            String normalized = raw.strip().toUpperCase(Locale.ROOT).replace('-', '_');
            for (Mode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException(
                    "unknown security mode '" + raw + "'; expected LOCAL_ONLY or NETWORK_EXPOSED");
        }
    }

    private static volatile NetworkExposure configured;

    private final Mode mode;
    private final String token;

    private NetworkExposure(Mode mode, String token) {
        this.mode = mode;
        this.token = token == null || token.isBlank() ? null : token;
    }

    /**
     * The exposure configuration of this process.
     *
     * <p>The value is read once and then held, so that every listener in the
     * process agrees about the boundary — a boundary that could differ
     * between two components is not a boundary.
     *
     * @return the configuration
     */
    public static NetworkExposure current() {
        NetworkExposure local = configured;
        if (local == null) {
            synchronized (NetworkExposure.class) {
                local = configured;
                if (local == null) {
                    local = of(Mode.parse(readSetting(MODE_PROPERTY, MODE_ENVIRONMENT)),
                            readSetting(TOKEN_PROPERTY, TOKEN_ENVIRONMENT));
                    configured = local;
                }
            }
        }
        return local;
    }

    /**
     * Sets the configuration explicitly.
     *
     * <p>This is what a bootstrap uses when it has already read the
     * configuration through the framework's own mechanism, and what the
     * tests use to exercise the boundary.
     *
     * @param mode the mode
     * @param token the authentication token, or null
     */
    public static void configure(Mode mode, String token) {
        configured = new NetworkExposure(mode == null ? Mode.LOCAL_ONLY : mode, token);
    }

    /** Restores the configuration to the one the environment describes. */
    public static void reset() {
        configured = null;
    }

    /**
     * A configuration built from a mode and a token.
     *
     * @param mode the mode
     * @param token the token, or null
     * @return the configuration
     */
    public static NetworkExposure of(Mode mode, String token) {
        return new NetworkExposure(mode, token);
    }

    /** The mode of this process. */
    public Mode mode() {
        return mode;
    }

    /** Whether the process is reachable from other hosts. */
    public boolean isNetworkExposed() {
        return mode == Mode.NETWORK_EXPOSED;
    }

    /** Whether an authentication token is configured. */
    public boolean hasToken() {
        return token != null;
    }

    /**
     * The address a listener of this process may bind.
     *
     * @return the loopback address in local-only mode, the wildcard address
     *         in network-exposed mode
     */
    public String bindAddress() {
        return isNetworkExposed() ? ALL_INTERFACES : LOOPBACK;
    }

    /**
     * Refuses to let a component be exposed without a credential.
     *
     * <p>This is the startup guard. It is called by every listener before it
     * binds, so a deployment that sets {@code MATRIX_SECURITY_MODE=
     * NETWORK_EXPOSED} and forgets the token stops at startup with a
     * message that names the component — rather than serving the network
     * with no way in and no way to tell who is asking.
     *
     * @param component the component that wants to bind
     * @throws IllegalStateException when the component would be exposed
     *         without a configured token
     */
    public void guard(String component) {
        if (isNetworkExposed() && !hasToken()) {
            throw new IllegalStateException(
                    "refusing to start " + component + " exposed to the network without a credential: "
                            + "set " + TOKEN_PROPERTY + " (or " + TOKEN_ENVIRONMENT + ")");
        }
    }

    /**
     * Whether a request may change persistent state.
     *
     * <p>In local-only mode the process is not reachable from another host
     * and the local user is trusted, so the request is allowed. In
     * network-exposed mode the request must carry the configured token.
     *
     * @param authorizationHeader the {@code Authorization} header, or null
     * @return whether the request is authorized
     */
    public boolean isAuthorized(String authorizationHeader) {
        if (!isNetworkExposed()) {
            return true;
        }
        if (token == null) {
            return false;
        }
        String presented = bearerToken(authorizationHeader);
        return presented != null && constantTimeEquals(token, presented);
    }

    /**
     * The tenant identity of a request.
     *
     * <p>In local-only mode the identity is the server-controlled local
     * identity: the client cannot choose it, because a header a client
     * controls is not an identity. In network-exposed mode the identity is
     * the tenant the configured credential belongs to.
     *
     * @return the tenant identity
     */
    public String tenantIdentity() {
        return isNetworkExposed() ? "authenticated" : LOCAL_TENANT;
    }

    /** The tenant of a local-only process. */
    public static final String LOCAL_TENANT = "local";

    /**
     * The bearer token of an {@code Authorization} header.
     *
     * @param header the header value, or null
     * @return the token, or null
     */
    public static String bearerToken(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.strip();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = trimmed.substring(7).strip();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * Compares two strings without leaking their contents through the time
     * the comparison takes.
     *
     * @param left the expected value
     * @param right the presented value
     * @return whether they are equal
     */
    static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = right.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /** Reads a setting from the system properties, then the environment. */
    private static String readSetting(String property, String environment) {
        String value = System.getProperty(property);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(environment);
        return value == null || value.isBlank() ? null : value;
    }
}
