package io.matrix.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotSignerTest {

    @Test
    void shouldSignAndVerifyData() throws Exception {
        var signer = new SnapshotSigner();
        byte[] data = "test snapshot data".getBytes(StandardCharsets.UTF_8);

        byte[] signature = signer.sign(data);

        assertThat(signature).isNotEmpty();
        assertThat(signer.verify(data, signature)).isTrue();
    }

    @Test
    void shouldRejectTamperedData() throws Exception {
        var signer = new SnapshotSigner();
        byte[] data = "original data".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.sign(data);

        byte[] tampered = "tampered data".getBytes(StandardCharsets.UTF_8);

        assertThat(signer.verify(tampered, signature)).isFalse();
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        var signer = new SnapshotSigner();
        byte[] data = "some data".getBytes(StandardCharsets.UTF_8);
        byte[] badSignature = new byte[64];

        assertThat(signer.verify(data, badSignature)).isFalse();
    }

    @Test
    void shouldSignLargeData() throws Exception {
        var signer = new SnapshotSigner();
        byte[] data = new byte[10_000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }

        byte[] signature = signer.sign(data);

        assertThat(signer.verify(data, signature)).isTrue();
    }

    @Test
    void shouldProduceDifferentSignaturesForDifferentData() throws Exception {
        var signer = new SnapshotSigner();
        byte[] data1 = "data1".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "data2".getBytes(StandardCharsets.UTF_8);

        byte[] sig1 = signer.sign(data1);
        byte[] sig2 = signer.sign(data2);

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void shouldExposePublicKey() throws Exception {
        var signer = new SnapshotSigner();

        assertThat(signer.publicKey()).isNotNull();
        assertThat(signer.publicKey().getAlgorithm()).isEqualTo("EdDSA");
    }

    @Test
    void shouldVerifyWithExternalPublicKey() throws Exception {
        var signer = new SnapshotSigner();
        byte[] data = "cross-verify".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.sign(data);

        java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
        verifier.initVerify(signer.publicKey());
        verifier.update(data);

        assertThat(verifier.verify(signature)).isTrue();
    }
}
