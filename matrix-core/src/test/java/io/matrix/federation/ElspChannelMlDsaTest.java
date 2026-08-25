package io.matrix.federation;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the ELSP v2 post-quantum profile ({@link ElspChannelMlDsa}).
 * Requires a JDK with ML-DSA support (JEP 497, present since Java 24).
 */
class ElspChannelMlDsaTest {

    private static boolean mlDsaAvailable() {
        try {
            KeyPairGenerator.getInstance("ML-DSA");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void roundtripAuthentic() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mlDsaAvailable());
        var ch = new ElspChannelMlDsa();
        assertThat(ch.verifyAndAccept(ch.sign(1, "pq".getBytes()))).isTrue();
    }

    @Test
    void tamperRejected() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mlDsaAvailable());
        var ch = new ElspChannelMlDsa();
        var env = ch.sign(1, "orig".getBytes());
        var bad = new ElspChannelMlDsa.Envelope(env.seq(), "hack".getBytes(), env.signature());
        assertThat(ch.verifyAndAccept(bad)).isFalse();
    }

    @Test
    void replayRejected() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mlDsaAvailable());
        var ch = new ElspChannelMlDsa();
        var env = ch.sign(5, "x".getBytes());
        assertThat(ch.verifyAndAccept(env)).isTrue();
        assertThat(ch.verifyAndAccept(env)).isFalse();
        assertThatThrownBySequenceBelow(5, ch);
    }

    private static void assertThatThrownBySequenceBelow(long high, ElspChannelMlDsa ch) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ch.sign(high - 1, "late".getBytes()))
                .hasMessageContaining("seq must exceed");
    }
}
