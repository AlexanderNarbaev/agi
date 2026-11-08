package io.matrix.bridge;

import io.matrix.contract.bridge.BridgeBudget;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeSigner;
import io.matrix.contract.bridge.HmacBridgeSigner;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * W51 — the configuration of the bridge, and the fail-closed rule that governs it.
 *
 * <p><b>There is no default secret.</b> BRIDGE-3 requires every call to be authenticated and
 * BRIDGE-6 requires every failure to be a refusal rather than a degraded service. A bridge
 * that starts with a built-in secret would satisfy neither: the secret would be in the
 * repository, and a deployment that forgot to configure one would serve unauthenticated
 * calls. The configuration therefore returns an empty optional when no secret is present, and
 * the receiver refuses every message with {@code NO_SECRET_CONFIGURED}.</p>
 *
 * <p><b>Where the secret comes from.</b> A system property, or an environment variable. The
 * system property wins, because it is the one an operator sets for a single process and it
 * does not leak into the environment of everything the process starts.</p>
 *
 * <p><b>The grant is explicit too.</b> The rights the bridge grants are named, and the
 * default is the narrowest useful set: the three things BRIDGE-5 allows. An operator can
 * narrow it further by naming the rights.</p>
 */
public final class BridgeConfiguration {

    /** The system property that carries the shared secret. */
    public static final String SECRET_PROPERTY = "matrix.bridge.secret";

    /** The environment variable that carries the shared secret. */
    public static final String SECRET_ENV = "MATRIX_BRIDGE_SECRET";

    /** The system property that names the rights the bridge grants. */
    public static final String GRANT_PROPERTY = "matrix.bridge.grant";

    /** The system property that names the workspace the bridged records belong to. */
    public static final String WORKSPACE_PROPERTY = "matrix.bridge.workspace";

    /** The workspace the bridged records belong to when none is named. */
    public static final String DEFAULT_WORKSPACE = "bridged";

    private BridgeConfiguration() {
        throw new AssertionError("no instances");
    }

    /**
     * The shared secret, when one is configured.
     *
     * @return the secret, or an empty optional when none is configured
     */
    public static Optional<byte[]> secret() {
        String configured = System.getProperty(SECRET_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(SECRET_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(configured.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The signer that verifies incoming messages, when a secret is configured.
     *
     * @return the signer, or {@code null} when no secret is configured — and {@code null} is
     *         what makes the receiver refuse everything
     */
    public static BridgeSigner verifier() {
        return secret().map(HmacBridgeSigner::new).orElse(null);
    }

    /**
     * The rights the bridge grants to its senders.
     *
     * @return the granted rights
     */
    public static Set<BridgeCapability> granted() {
        String configured = System.getProperty(GRANT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return EnumSet.allOf(BridgeCapability.class);
        }
        EnumSet<BridgeCapability> granted = EnumSet.noneOf(BridgeCapability.class);
        for (String name : configured.split(",")) {
            BridgeCapability.parse(name.trim()).ifPresent(granted::add);
        }
        return granted;
    }

    /** The budget the bridge enforces. */
    public static BridgeBudget budget() {
        return BridgeBudget.standard();
    }

    /** The workspace the bridged records belong to. */
    public static String workspaceId() {
        String configured = System.getProperty(WORKSPACE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_WORKSPACE;
        }
        return configured;
    }
}
