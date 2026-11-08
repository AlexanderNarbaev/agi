package io.matrix.brain.runtime.bridge;

import io.matrix.contract.bridge.HmacBridgeSigner;

import java.util.Optional;

/**
 * W51 — the chain B side's configuration of the bridge.
 *
 * <p><b>Why this class exists on this side of the boundary.</b> The chain A configuration of
 * the bridge lives in {@code io.matrix.bridge}, which reaches the epistemic store. Chain B
 * must not reach that package — the isolation gate's rule G8 says so, and the gate checks
 * it. So the chain B side reads its own configuration here, from the same property names,
 * and it depends on nothing but the protocol package.</p>
 *
 * <p><b>Fail closed, in both directions.</b> Without an endpoint there is nothing to publish
 * to and the brain runtime behaves exactly as it did before this wave. Without a secret there
 * is nothing to sign with, and a publisher that cannot sign is not created: an unsigned
 * message would be refused by chain A anyway, and sending it would fill the receiver's
 * refusal log with noise that looks like an attack.</p>
 *
 * @param endpoint  the base URL of chain A
 * @param secret    the shared secret
 * @param senderId  the identity this sender presents
 */
public record BridgeClientConfiguration(String endpoint, String secret, String senderId) {

    /** The system property that names the endpoint of chain A. */
    public static final String ENDPOINT_PROPERTY = "matrix.bridge.endpoint";

    /** The system property that carries the shared secret. */
    public static final String SECRET_PROPERTY = "matrix.bridge.secret";

    /** The system property that names this sender. */
    public static final String SENDER_PROPERTY = "matrix.bridge.sender";

    /** The identity this sender presents when none is configured. */
    public static final String DEFAULT_SENDER = "chain-b";

    /**
     * The configuration, when one is present and complete.
     *
     * @return the configuration, or an empty optional when the bridge is not configured
     */
    public static Optional<BridgeClientConfiguration> fromSystemProperties() {
        String endpoint = System.getProperty(ENDPOINT_PROPERTY);
        String secret = System.getProperty(SECRET_PROPERTY);
        if (endpoint == null || endpoint.isBlank() || secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        String sender = System.getProperty(SENDER_PROPERTY);
        return Optional.of(new BridgeClientConfiguration(endpoint, secret,
                sender == null || sender.isBlank() ? DEFAULT_SENDER : sender));
    }

    /**
     * The publisher this configuration describes.
     *
     * @return the publisher
     */
    public BridgePublisher publisher() {
        return new BridgePublisher(endpoint, new HmacBridgeSigner(secret), senderId);
    }
}
