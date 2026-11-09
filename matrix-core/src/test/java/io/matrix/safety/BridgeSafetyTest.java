package io.matrix.safety;

import io.matrix.contract.CognitiveEvent;
import io.matrix.contract.Correlation;
import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;
import io.matrix.contract.RetryPolicy;
import io.matrix.contract.bridge.BridgeBudget;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeIdempotency;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.BridgeReceiver;
import io.matrix.contract.bridge.BridgeRefusalReason;
import io.matrix.contract.bridge.BridgeSink;
import io.matrix.contract.bridge.HmacBridgeSigner;
import io.matrix.contract.bridge.InMemoryBridgeIdempotency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W51 §11 — the bridge under failure: an unreachable peer, a redelivered message, an exhausted
 * budget and an incompatible version.
 *
 * <p><b>What this file is for.</b> §11 asks for the safety properties of the bridge, and every
 * one of them is a property of a REFUSAL: the system must be able to say "no" and mean it.
 * A bridge that answers ACCEPTED when it did not accept anything is worse than one that is
 * down, because a down bridge is visible.</p>
 *
 * <p><b>Why every instant is a parameter.</b> The protocol reads no clock. Every decision in
 * this file is taken at a fixed instant, so the verdicts below are consequences of the
 * messages and of the budget, never of when the machine ran them.</p>
 *
 * <p><b>Why nothing here touches a database.</b> The bridge protocol is deliberately free of
 * one: the effect goes through {@link BridgeSink}, and this file supplies a sink that counts.
 * A refusal that happens before the sink is called is a refusal that provably changed
 * nothing, and that is exactly the property §11 asks for.</p>
 */
class BridgeSafetyTest {

    /** The secret both sides of the bridge share. */
    private static final byte[] SECRET = "the-shared-bridge-secret".getBytes(StandardCharsets.UTF_8);

    /** The sender's identity. */
    private static final String SENDER = "chain-b-runtime";

    /** The instant every message and every decision in this file carries. */
    private static final String SENT_AT = "2026-11-08T00:00:00Z";

    /** The instant the decisions are taken at, in epoch milliseconds. */
    private static final long NOW_MILLIS = 1_700_000_000_000L;

    /** The width of the rate window the budget uses. */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** The largest message the budget admits in these tests, in bytes. */
    private static final int MAX_MESSAGE_BYTES = 64 * 1024;

    /** The largest batch the budget admits in these tests. */
    private static final int MAX_BATCH = 10;

    /** The number of messages the budget admits in a day in these tests. */
    private static final int PER_DAY_CEILING = 1_000;

    /** The number of attempts the retry policy in these tests allows. */
    private static final int MAX_ATTEMPTS = 3;

    /** The first backoff the retry policy in these tests waits. */
    private static final long INITIAL_BACKOFF_MILLIS = 50L;

    /** The multiplier the retry policy in these tests applies. */
    private static final double BACKOFF_MULTIPLIER = 2.0;

    /** The longest backoff the retry policy in these tests allows. */
    private static final long MAX_BACKOFF_MILLIS = 2_000L;

    /** The time a connection attempt to an unreachable peer may take, in milliseconds. */
    private static final int CONNECT_TIMEOUT_MILLIS = 250;

    /** The longest a connection attempt to an unreachable peer may take before the test fails. */
    private static final long CONNECT_FAILURE_BUDGET_MILLIS = 1_000L;

    /** A size limit small enough that a padded message exceeds it and a plain one does not. */
    private static final int SMALL_MESSAGE_BYTES = 2_048;

    /** The padding that makes one message larger than the small size limit. */
    private static final int LARGE_PAYLOAD_PADDING = 8_192;

    /** The rights the receiver grants in these tests. */
    private static final Set<BridgeCapability> GRANTED = Set.of(BridgeCapability.OBSERVATION);

    // ── §11.1 — the process became unreachable ─────────────────────────────────

    @Test
    @DisplayName("§11.1 — a delivery whose effect cannot be applied fails explicitly and leaves no success behind")
    void aDeliveryThatCannotBeAppliedFailsExplicitlyAndLeavesNoSuccessBehind() {
        AtomicInteger effects = new AtomicInteger();
        InMemoryBridgeIdempotency idempotency = new InMemoryBridgeIdempotency();
        BridgeReceiver receiver = receiver(idempotency, envelope -> {
            if (effects.incrementAndGet() == 1) {
                throw new IllegalStateException("the epistemic store is not reachable");
            }
            return null;
        });

        BridgeEnvelope envelope = signedEnvelope("req-unreachable", observationPayload());

        // The failure is EXPLICIT: it is an exception, not a decision that says ACCEPTED.
        assertThatThrownBy(() -> receiver.receive(envelope, NOW_MILLIS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not reachable");

        // Nothing claims the message was accepted: the audit holds no record of a decision,
        // and the idempotency registry holds no outcome — so the failed attempt was not
        // remembered as a success.
        assertThat(receiver.auditLog()).isEmpty();
        assertThat(idempotency.find("req-unreachable")).isEmpty();

        // And the retry is a FRESH attempt, not a replay of a success that never happened.
        BridgeDecision retried = receiver.receive(envelope, NOW_MILLIS);
        assertThat(retried.accepted()).isTrue();
        assertThat(effects.get()).isEqualTo(2);
        assertThat(idempotency.find("req-unreachable")).isPresent();
    }

    @Test
    @DisplayName("§11.1 — an unreachable loopback peer fails fast and explicitly instead of hanging")
    void anUnreachableLoopbackPeerFailsFastAndExplicitly() throws Exception {
        // The fixture the mandate names: a free loopback port with nothing listening on it.
        int freePort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            freePort = probe.getLocalPort();
        }

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> {
            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), freePort),
                        CONNECT_TIMEOUT_MILLIS);
            }
        })
                .as("a peer that is not there must fail explicitly, never silently succeed")
                .isInstanceOf(ConnectException.class);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        // The failure is immediate: the caller learns that the peer is unreachable instead of
        // waiting for a timeout that says nothing more precise.
        assertThat(elapsedMillis).isLessThan(CONNECT_FAILURE_BUDGET_MILLIS);
    }

    @Test
    @DisplayName("§11.1 — a message the bridge cannot authenticate is refused and applies no effect")
    void aMessageTheBridgeCannotAuthenticateIsRefusedAndAppliesNoEffect() {
        AtomicInteger effects = new AtomicInteger();
        BridgeReceiver receiver = receiver(new InMemoryBridgeIdempotency(), envelope -> {
                effects.incrementAndGet();
                return null;
            });

        // The signature was made with a different secret: the message cannot be trusted.
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                "req-forged", SENT_AT, SENDER, BridgeCapability.OBSERVATION, observationPayload(), null);
        String forged = envelope.withSignature(envelope.sign(new HmacBridgeSigner("a-different-secret")))
                .signature();

        BridgeDecision decision = receiver.receive(envelope.withSignature(forged), NOW_MILLIS);

        assertThat(decision.refused()).isTrue();
        assertThat(decision.reason()).contains(BridgeRefusalReason.SIGNATURE_INVALID.description());
        assertThat(effects.get()).isZero();
    }

    // ── §11.4 — the event was delivered more than once ────────────────────────

    @Test
    @DisplayName("§11.4 — the same message delivered twice applies its effect once and answers with the first outcome")
    void theSameMessageDeliveredTwiceAppliesItsEffectOnce() {
        AtomicInteger effects = new AtomicInteger();
        BridgeReceiver receiver = receiver(new InMemoryBridgeIdempotency(), envelope -> {
                effects.incrementAndGet();
                return null;
            });
        BridgeEnvelope envelope = signedEnvelope("req-redelivered", observationPayload());

        BridgeDecision first = receiver.receive(envelope, NOW_MILLIS);
        BridgeDecision second = receiver.receive(envelope, NOW_MILLIS);

        assertThat(first.accepted()).isTrue();
        assertThat(second)
                .as("the retry must be answered with the outcome of the first attempt")
                .isEqualTo(first);
        assertThat(effects.get())
                .as("the effect must be applied exactly once")
                .isEqualTo(1);
        assertThat(receiver.auditLog())
                .as("a replay is not a second delivery, so it is not a second audit record")
                .hasSize(1);
    }

    @Test
    @DisplayName("§11.4 — the same request id with a different payload is a conflict, not a second effect")
    void theSameRequestIdWithADifferentPayloadIsAConflict() {
        AtomicInteger effects = new AtomicInteger();
        BridgeReceiver receiver = receiver(new InMemoryBridgeIdempotency(), envelope -> {
                effects.incrementAndGet();
                return null;
            });

        BridgeDecision first = receiver.receive(signedEnvelope("req-conflict", observationPayload()),
                NOW_MILLIS);
        BridgeDecision second = receiver.receive(signedEnvelope("req-conflict", candidatePayload()),
                NOW_MILLIS);

        assertThat(first.accepted()).isTrue();
        assertThat(second.refused()).isTrue();
        assertThat(second.reason()).contains(BridgeRefusalReason.REQUEST_ID_CONFLICT.description());
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("§11.4 — a consumer can tell a redelivery from a first delivery")
    void aConsumerCanTellARedeliveryFromAFirstDelivery() {
        CognitiveEvent first = event("evt-1", CognitiveEvent.FIRST_DELIVERY);
        CognitiveEvent again = event("evt-1", CognitiveEvent.FIRST_DELIVERY + 1);

        assertThat(first.redelivery()).isFalse();
        assertThat(again.redelivery()).isTrue();
        assertThat(again.eventId())
                .as("a redelivery carries the identity of the event it repeats")
                .isEqualTo(first.eventId());
    }

    // ── §11.5 — the timeout: the retry budget is spent ───────────────────────

    @Test
    @DisplayName("§11.5 — a retry policy stops at its attempt limit and its backoff stays bounded")
    void aRetryPolicyStopsAtItsAttemptLimitAndItsBackoffStaysBounded() {
        RetryPolicy policy = new RetryPolicy(MAX_ATTEMPTS, INITIAL_BACKOFF_MILLIS,
                BACKOFF_MULTIPLIER, MAX_BACKOFF_MILLIS);

        int attempts = 0;
        while (policy.mayRetry(attempts)) {
            attempts++;
        }

        assertThat(attempts).isEqualTo(MAX_ATTEMPTS);
        assertThat(policy.mayRetry(MAX_ATTEMPTS)).isFalse();
        assertThat(policy.backoffMillis(MAX_ATTEMPTS)).isLessThanOrEqualTo(MAX_BACKOFF_MILLIS);
        assertThat(policy.backoffMillis(1)).isZero();
    }

    @Test
    @DisplayName("§11.5 — when the quota is spent the bridge refuses the message and writes nothing")
    void whenTheQuotaIsSpentTheBridgeRefusesTheMessageAndWritesNothing() {
        AtomicInteger effects = new AtomicInteger();
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET), GRANTED,
                new BridgeBudget(1, WINDOW, MAX_MESSAGE_BYTES, MAX_BATCH, PER_DAY_CEILING),
                new InMemoryBridgeIdempotency(), envelope -> {
                effects.incrementAndGet();
                return null;
            });

        BridgeDecision first = receiver.receive(signedEnvelope("req-first", observationPayload()),
                NOW_MILLIS);
        BridgeDecision second = receiver.receive(signedEnvelope("req-second", observationPayload()),
                NOW_MILLIS);

        assertThat(first.accepted()).isTrue();
        assertThat(second.refused()).isTrue();
        assertThat(second.reason()).contains(BridgeRefusalReason.RATE_LIMIT_EXCEEDED.description());
        assertThat(effects.get())
                .as("the refused attempt must not have written anything")
                .isEqualTo(1);
    }

    // ── §11.6 — the bounded queue overflows ──────────────────────────────────

    @Test
    @DisplayName("§11.6 — a message that exceeds the size limit is refused and the budget is not charged for it")
    void aMessageThatExceedsTheSizeLimitIsRefusedAndTheBudgetIsNotCharged() {
        BridgeBudget budget = new BridgeBudget(10, WINDOW, SMALL_MESSAGE_BYTES, MAX_BATCH,
                PER_DAY_CEILING);
        AtomicInteger effects = new AtomicInteger();
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET), GRANTED, budget,
                new InMemoryBridgeIdempotency(), envelope -> {
                effects.incrementAndGet();
                return null;
            });

        BridgeEnvelope oversized = signedEnvelope("req-oversized", largeObservationPayload());
        BridgeDecision decision = receiver.receive(oversized, NOW_MILLIS);

        assertThat(decision.refused()).isTrue();
        assertThat(decision.reason()).contains(BridgeRefusalReason.MESSAGE_TOO_LARGE.description());
        assertThat(effects.get()).isZero();

        // The refusal cost the budget nothing: the refused message did not consume a slot of
        // the rate window, so an ordinary message that follows is still admitted.
        BridgeDecision ordinary = receiver.receive(signedEnvelope("req-ordinary", observationPayload()),
                NOW_MILLIS);
        assertThat(ordinary.accepted()).isTrue();
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("§11.6 — the budget names the limit that was exceeded")
    void theBudgetNamesTheLimitThatWasExceeded() {
        BridgeBudget budget = new BridgeBudget(1, WINDOW, MAX_MESSAGE_BYTES, MAX_BATCH,
                PER_DAY_CEILING);
        BridgeEnvelope envelope = signedEnvelope("req-budget", observationPayload());

        assertThat(budget.check(envelope, 1, NOW_MILLIS).allowed()).isTrue();
        BridgeBudget.Verdict second = budget.check(envelope, 1, NOW_MILLIS);
        assertThat(second.allowed()).isFalse();
        assertThat(second.reason()).isEqualTo(BridgeRefusalReason.RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("§11.6 — a batch that exceeds the limit is refused and nothing in it is applied")
    void aBatchThatExceedsTheLimitIsRefusedAndNothingInItIsApplied() {
        AtomicInteger effects = new AtomicInteger();
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET), GRANTED,
                new BridgeBudget(10, WINDOW, MAX_MESSAGE_BYTES, 1, PER_DAY_CEILING),
                new InMemoryBridgeIdempotency(), envelope -> {
                effects.incrementAndGet();
                return null;
            });

        List<BridgeEnvelope> batch = List.of(
                signedEnvelope("req-batch-one", observationPayload()),
                signedEnvelope("req-batch-two", observationPayload()));

        List<BridgeDecision> decisions = receiver.receiveBatch(batch, NOW_MILLIS);

        assertThat(decisions).hasSize(2);
        assertThat(decisions).allMatch(BridgeDecision::refused);
        assertThat(decisions.get(0).reason())
                .contains(BridgeRefusalReason.BATCH_TOO_LARGE.description());
        assertThat(effects.get()).isZero();
    }

    // ── §11.8 — an incompatible contract version ──────────────────────────────

    @Test
    @DisplayName("§11.8 — a message that names a version this reader does not speak is refused and the version is named")
    void aMessageThatNamesAnUnsupportedVersionIsRefusedAndTheVersionIsNamed() {
        AtomicInteger effects = new AtomicInteger();
        BridgeReceiver receiver = receiver(new InMemoryBridgeIdempotency(), envelope -> {
                effects.incrementAndGet();
                return null;
            });

        int unsupportedVersion = BridgeProtocol.VERSION + 1;
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, unsupportedVersion,
                "req-version", SENT_AT, SENDER, BridgeCapability.OBSERVATION, observationPayload(), null);
        String signature = envelope.sign(new HmacBridgeSigner(SECRET));

        BridgeDecision decision = receiver.receive(envelope.withSignature(signature), NOW_MILLIS);

        assertThat(decision.refused()).isTrue();
        assertThat(decision.reason()).contains(BridgeRefusalReason.UNSUPPORTED_VERSION.description());
        assertThat(decision.reason())
                .as("the refusal must name the version it refused")
                .contains(String.valueOf(unsupportedVersion));
        assertThat(effects.get()).isZero();
    }

    @Test
    @DisplayName("§11.8 — a message that names another protocol is refused before anything else is looked at")
    void aMessageThatNamesAnotherProtocolIsRefused() {
        AtomicInteger effects = new AtomicInteger();
        BridgeReceiver receiver = receiver(new InMemoryBridgeIdempotency(), envelope -> {
                effects.incrementAndGet();
                return null;
            });

        BridgeEnvelope envelope = new BridgeEnvelope("some.other.protocol", BridgeProtocol.VERSION,
                "req-protocol", SENT_AT, SENDER, BridgeCapability.OBSERVATION, observationPayload(),
                null);
        String signature = envelope.sign(new HmacBridgeSigner(SECRET));

        BridgeDecision decision = receiver.receive(envelope.withSignature(signature), NOW_MILLIS);

        assertThat(decision.refused()).isTrue();
        assertThat(decision.reason()).contains(BridgeRefusalReason.UNKNOWN_PROTOCOL.description());
        assertThat(effects.get()).isZero();
    }

    @Test
    @DisplayName("§11.8 — an event whose contract version is not supported says so")
    void anEventWhoseContractVersionIsNotSupportedSaysSo() {
        CognitiveEvent supported = new CognitiveEvent("matrix.cognitive", 1, "evt-1",
                Correlation.ofRequest("corr-1"), "{}", SENT_AT, CognitiveEvent.FIRST_DELIVERY, SENDER);
        CognitiveEvent unsupported = new CognitiveEvent("matrix.cognitive", 2, "evt-2",
                Correlation.ofRequest("corr-2"), "{}", SENT_AT, CognitiveEvent.FIRST_DELIVERY, SENDER);

        assertThat(supported.contractSupported(1)).isTrue();
        assertThat(unsupported.contractSupported(1)).isFalse();
    }

    // ── the fixtures ──────────────────────────────────────────────────────────

    private static BridgeReceiver receiver(BridgeIdempotency idempotency, BridgeSink sink) {
        return new BridgeReceiver(new HmacBridgeSigner(SECRET), GRANTED,
                new BridgeBudget(60, WINDOW, MAX_MESSAGE_BYTES, MAX_BATCH, PER_DAY_CEILING),
                idempotency, sink);
    }

    private static BridgeEnvelope signedEnvelope(String requestId, String payload) {
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                requestId, SENT_AT, SENDER, BridgeCapability.OBSERVATION, payload, null);
        return envelope.withSignature(envelope.sign(new HmacBridgeSigner(SECRET)));
    }

    private static String observationPayload() {
        Provenance provenance = Provenance.of("the sandbox", SENT_AT);
        Observation observation = Observation.ofText("obs-1", "the lamp was on", SENT_AT, provenance);
        return new BridgePayloadCodec().encode(BridgePayload.ofObservation(observation, provenance));
    }

    private static String candidatePayload() {
        Provenance provenance = Provenance.of("the sandbox", SENT_AT);
        return new BridgePayloadCodec().encode(BridgePayload.ofObservation(
                Observation.ofText("obs-2", "a different observation", SENT_AT, provenance),
                provenance));
    }

    /** A payload that is comfortably larger than the small size limit of one test. */
    private static String largeObservationPayload() {
        Provenance provenance = Provenance.of("the sandbox", SENT_AT);
        String text = "x".repeat(LARGE_PAYLOAD_PADDING);
        Observation observation = Observation.ofText("obs-big", text, SENT_AT, provenance);
        return new BridgePayloadCodec().encode(BridgePayload.ofObservation(observation, provenance));
    }

    private static CognitiveEvent event(String eventId, int deliveryAttempt) {
        return new CognitiveEvent("matrix.cognitive", 1, eventId,
                Correlation.ofRequest("corr-1"), "{}", SENT_AT, deliveryAttempt, SENDER);
    }
}
