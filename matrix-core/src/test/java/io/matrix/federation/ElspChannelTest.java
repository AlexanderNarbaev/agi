package io.matrix.federation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ElspChannel}: sign/verify roundtrip, tamper detection,
 * and anti-replay sequence monotonicity (DESIGN-08 §ELSP v1).
 */
class ElspChannelTest {

    @Test
    void roundtripAuthenticEnvelopeAccepted() throws Exception {
        ElspChannel channel = new ElspChannel();
        var env = channel.sign(1, "artifact-bytes".getBytes());
        assertThat(channel.verifyAndAccept(env)).isTrue();
    }

    @Test
    void tamperedPayloadRejected() throws Exception {
        ElspChannel channel = new ElspChannel();
        var env = channel.sign(1, "original".getBytes());
        var tampered = new ElspChannel.Envelope(env.seq(), "tampered!".getBytes(), env.signature());
        assertThat(channel.verifyAndAccept(tampered)).isFalse();
    }

    @Test
    void replayedAndStaleSequencesRejected() throws Exception {
        ElspChannel channel = new ElspChannel();
        var env1 = channel.sign(5, "a".getBytes());
        assertThat(channel.verifyAndAccept(env1)).isTrue();

        // Exact replay.
        assertThat(channel.verifyAndAccept(env1)).isFalse();
        // Stale (below high-water mark) even with a fresh valid signature attempt is blocked at seq check.
        assertThatThrownBy(() -> channel.sign(4, "b".getBytes()))
                .hasMessageContaining("seq must exceed");
    }

    @Test
    void strictlyIncreasingSequenceAccepted() throws Exception {
        ElspChannel channel = new ElspChannel();
        for (long seq : new long[]{1, 2, 10, 11}) {
            assertThat(channel.verifyAndAccept(channel.sign(seq, ("p" + seq).getBytes()))).isTrue();
        }
    }
}
