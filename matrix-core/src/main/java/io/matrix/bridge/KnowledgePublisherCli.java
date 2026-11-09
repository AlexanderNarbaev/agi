package io.matrix.bridge;

import io.matrix.contract.Belief;
import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Confidence;
import io.matrix.contract.Provenance;
import io.matrix.contract.ValidityInterval;
import io.matrix.contract.bridge.BridgeAnswer;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.HmacBridgeSigner;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * W52 — the command-line face of the SECOND direction of the bridge.
 *
 * <p><b>Why there is a CLI.</b> The cross-process demonstration has to show two processes
 * talking to each other over a network, and a demonstration that runs the publisher inside
 * the process under test proves nothing about the crossing. This CLI is what runs in the
 * OTHER process.</p>
 *
 * <p><b>What it prints.</b> The decision and the outcome, one per line, so that a shell
 * script can assert on them without parsing prose. The exit status is 0 when the message
 * was accepted and 1 when it was refused — a refusal is a failure of the demonstration, not
 * of the tool, and the tool says which it was.</p>
 *
 * <p>Usage:
 * <pre>
 *   KnowledgePublisherCli &lt;baseUrl&gt; &lt;secret&gt; &lt;proposition&gt; &lt;requestId&gt; [status]
 * </pre>
 */
public final class KnowledgePublisherCli {

    private KnowledgePublisherCli() {
        throw new AssertionError("no instances");
    }

    /**
     * Publishes an established belief to chain B's brain.
     *
     * @param args the base URL, the secret, the proposition, the request id and,
     *             optionally, the status of the belief
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("usage: KnowledgePublisherCli <baseUrl> <secret> <proposition> "
                    + "<requestId> [status]");
            System.exit(2);
        }
        String baseUrl = args[0];
        String secret = args[1];
        String proposition = args[2];
        String requestId = args[3];
        BeliefStatus status = args.length > 4
                ? BeliefStatus.parse(args[4]).orElse(BeliefStatus.SUPPORTED)
                : BeliefStatus.SUPPORTED;
        String now = Instant.now().toString();

        Belief belief = new Belief("belief-" + requestId, proposition, status,
                new Confidence(0.9, Confidence.Basis.MEASURED, 1, 0,
                        "the cognitive kernel established it", List.of()),
                List.of("evidence-" + requestId), List.of(),
                Provenance.of("chain-a-epistemic-core", now),
                ValidityInterval.openFrom(now), 1, null, null,
                "published by the W52 cross-process demonstration");

        KnowledgePublisher publisher = new KnowledgePublisher(baseUrl,
                new HmacBridgeSigner(secret.getBytes(StandardCharsets.UTF_8)),
                "chain-a-epistemic-core");
        try {
            BridgeAnswer answer = publisher.publishKnowledge(requestId, belief,
                    Provenance.of("chain-a-epistemic-core", now), now);
            BridgeDecision decision = answer.decision();
            System.out.println("status=" + decision.status().name());
            System.out.println("auditId=" + decision.auditId());
            System.out.println("reason=" + decision.reason());
            if (answer.outcome() == null) {
                System.out.println("outcome=none");
            } else {
                System.out.println("outcome.status=" + answer.outcome().status().name());
                System.out.println("outcome.observed=" + answer.outcome().observed());
            }
            System.exit(decision.status() == BridgeDecision.Status.ACCEPTED ? 0 : 1);
        } catch (KnowledgePublicationFailure failure) {
            System.out.println("status=FAILED");
            System.out.println("reason=" + failure.getMessage());
            System.exit(1);
        }
    }
}
