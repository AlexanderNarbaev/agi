package io.matrix.contract.bridge;

import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * W51 — the fixtures the bridge tests share.
 *
 * <p>The values here are fixed, not random and not read from a clock: the bridge protocol
 * refuses messages that carry an instant the reader did not supply, and a test that used the
 * wall clock would make the rate window and the daily ceiling untestable. The instant is
 * therefore a constant of the test, and the tests that need the budget to move say so
 * explicitly by choosing another instant.</p>
 */
final class BridgeTestFixtures {

    /** The shared secret of the tests. */
    static final byte[] SECRET = "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    /** A secret that is not the right one. */
    static final byte[] OTHER_SECRET = "a-different-secret".getBytes(StandardCharsets.UTF_8);

    /** The instant every fixture claims to have been written at. */
    static final String SENT_AT = "2026-11-08T10:00:00Z";

    /** The instant every fixture is delivered at, in epoch milliseconds. */
    static final long NOW_MILLIS = 1_763_049_600_000L;

    /** An instant one minute later, still inside the same rate window. */
    static final long NOW_MILLIS_LATER = NOW_MILLIS + 30_000L;

    /** The identity of the sender. */
    static final String SENDER_ID = "chain-b-brain-runtime";

    /** The request id of the first fixture message. */
    static final String REQUEST_ID = "req-bridge-1";

    private BridgeTestFixtures() {
    }

    /** The provenance of a delivery. */
    static Provenance provenance() {
        return Provenance.of("chain-b", SENT_AT);
    }

    /** An observation. */
    static Observation observation() {
        return Observation.ofText("obs-bridge-1", "the lamp is lit", SENT_AT, provenance());
    }

    /** An observation whose payload carries more than one key. */
    static Observation multiKeyObservation() {
        return Observation.ofStructured("obs-bridge-2",
                Map.of("zeta", "last", "alpha", "first", "mu", "middle"), SENT_AT, provenance());
    }

    /** A payload carrying an observation. */
    static BridgePayload payload() {
        return BridgePayload.ofObservation(observation(), provenance());
    }

    /** A payload carrying an observation with several keys. */
    static BridgePayload multiKeyPayload() {
        return BridgePayload.ofObservation(multiKeyObservation(), provenance());
    }

    /** A payload whose observation is far larger than a small message limit allows. */
    static BridgePayload hugePayload() {
        return BridgePayload.ofObservation(
                Observation.ofText("obs-bridge-huge", "x".repeat(4096), SENT_AT, provenance()),
                provenance());
    }

    /** An unsigned envelope carrying the given payload. */
    static BridgeEnvelope envelope(BridgePayload payload, String requestId) {
        return new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION, requestId, SENT_AT,
                SENDER_ID, payload.kind(), new BridgePayloadCodec().encode(payload), null);
    }

    /** An unsigned envelope carrying the default payload. */
    static BridgeEnvelope envelope() {
        return envelope(payload(), REQUEST_ID);
    }

    /** An envelope signed with the shared secret. */
    static BridgeEnvelope signedEnvelope() {
        return signedEnvelope(envelope());
    }

    /** An envelope signed with the shared secret. */
    static BridgeEnvelope signedEnvelope(BridgeEnvelope unsigned) {
        return unsigned.withSignature(unsigned.sign(SECRET));
    }

    /**
     * A signed withdrawal of a dispatched task.
     *
     * @return the envelope
     */
    static BridgeEnvelope signedCancellation() {
        CognitiveTaskCancel cancel = new CognitiveTaskCancel(BridgeProtocol.VERSION,
                "tsk-cancel-1", "req-cancel-1",
                new io.matrix.contract.Correlation("corr-cancel-1", "ep-parent",
                        "ep-cancel-1", "req-cancel-1"),
                "the task was withdrawn by the requester", SENT_AT, provenance());
        BridgePayload payload = BridgePayload.ofTaskCancel(cancel, provenance());
        return signedEnvelope(envelope(payload, "cancel-req-cancel-1"));
    }

    /** A sink that remembers what it was given. */
    static final class RecordingSink implements BridgeSink {

        private final List<BridgeEnvelope> accepted = new ArrayList<>();

        @Override
        public io.matrix.contract.Outcome accept(BridgeEnvelope envelope) {
            accepted.add(envelope);
            return null;
        }

        /** How many messages the sink was given. */
        int count() {
            return accepted.size();
        }

        /** The messages the sink was given. */
        List<BridgeEnvelope> accepted() {
            return List.copyOf(accepted);
        }
    }
}
