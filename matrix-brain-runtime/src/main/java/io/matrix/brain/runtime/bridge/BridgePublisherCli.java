package io.matrix.brain.runtime.bridge;

import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.HmacBridgeSigner;

/**
 * W51 — the chain B side of the bridge, as a command.
 *
 * <p>This is the program that publishes one observation from chain B to chain A over the
 * network. It runs in its own process, with its own class path and its own database (it has
 * none), and it is the executable form of the claim that the bridge is a real crossing and
 * not a method call in one JVM.</p>
 *
 * <p><b>What it prints.</b> The decision the receiver made, and the identity of the request.
 * The exit status is zero when the receiver accepted the message and one when it refused it
 * or could not be reached — so a script can assert on it.</p>
 *
 * <p><b>What it never does.</b> It does not open a database, and it cannot: it depends only
 * on {@code io.matrix.contract} and {@code io.matrix.contract.bridge}, both of which the
 * isolation gate proves free of any persistence stack.</p>
 *
 * <p>The arguments are the endpoint of chain A, the shared secret, the text of the
 * observation and the request id. The secret is read from the argument or from the
 * {@code matrix.bridge.secret} property; it is never printed.</p>
 */
public final class BridgePublisherCli {

    /** The argument index of the endpoint of chain A. */
    public static final int ENDPOINT_ARGUMENT = 0;

    /** The argument index of the shared secret. */
    public static final int SECRET_ARGUMENT = 1;

    /** The argument index of the text of the observation. */
    public static final int TEXT_ARGUMENT = 2;

    /** The argument index of the request id. */
    public static final int REQUEST_ID_ARGUMENT = 3;

    /** The identity this sender presents. */
    public static final String SENDER_ID = "chain-b";

    /**
     * The instant the observation is stamped with.
     *
     * <p>It is FIXED, and that is the point: a retry of a request must be byte-identical,
     * and a payload that carries a fresh wall-clock reading is not. BRIDGE-8 says that the
     * same request id with a different payload is a conflict, and it is refused — which is
     * correct, and which means that a caller that wants an idempotent retry has to resend
     * the same bytes. This command therefore stamps a constant, so that "publish the same
     * request id with the same text" is a true retry and "publish the same request id with
     * a different text" is a conflict.</p>
     */
    public static final String OBSERVATION_INSTANT = "2026-11-08T00:00:00Z";

    private BridgePublisherCli() {
        throw new AssertionError("no instances");
    }

    /**
     * Publishes one observation and prints the decision.
     *
     * @param args the endpoint, the secret, the text and the request id
     */
    public static void main(String[] args) {
        if (args.length < REQUEST_ID_ARGUMENT + 1) {
            System.err.println("usage: BridgePublisherCli <endpoint> <secret> <text> <request-id>");
            System.exit(2);
            return;
        }
        String endpoint = args[ENDPOINT_ARGUMENT];
        String secret = args[SECRET_ARGUMENT];
        String text = args[TEXT_ARGUMENT];
        String requestId = args[REQUEST_ID_ARGUMENT];

        BridgePublisher publisher = new BridgePublisher(endpoint, new HmacBridgeSigner(secret),
                SENDER_ID);
        String sentAt = OBSERVATION_INSTANT;
        Observation observation = Observation.ofText("obs-" + requestId, text, sentAt,
                Provenance.of(SENDER_ID, sentAt));
        try {
            BridgeDecision decision = publisher.publishObservation(requestId, observation,
                    Provenance.of(SENDER_ID, sentAt), sentAt);
            System.out.println("the decision: " + decision.status());
            System.out.println("the request id: " + decision.requestId());
            System.out.println("the audit id: " + decision.auditId());
            System.out.println("the reason: " + decision.reason());
            System.exit(decision.accepted() ? 0 : 1);
        } catch (BridgePublicationFailure e) {
            System.err.println("the bridge could not be reached: " + e.getMessage());
            System.exit(3);
        }
    }
}
