package io.matrix.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NeuronIdentityLedgerTest {

    @Test
    void shouldRegisterAndVerifyNeuron() throws Exception {
        var ledger = new NeuronIdentityLedger();
        UUID neuronId = UUID.randomUUID();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();

        ledger.register(neuronId, keyPair.getPublic());

        byte[] data = "neuron data".getBytes(StandardCharsets.UTF_8);
        java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(data);
        byte[] signature = sig.sign();

        assertThat(ledger.verify(neuronId, data, signature)).isTrue();
    }

    @Test
    void shouldRejectUnregisteredNeuron() throws Exception {
        var ledger = new NeuronIdentityLedger();
        UUID unknown = UUID.randomUUID();

        assertThat(ledger.isRegistered(unknown)).isFalse();
        assertThat(ledger.verify(unknown, new byte[0], new byte[64])).isFalse();
    }

    @Test
    void shouldDetectTamperedData() throws Exception {
        var ledger = new NeuronIdentityLedger();
        UUID neuronId = UUID.randomUUID();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();
        ledger.register(neuronId, keyPair.getPublic());

        byte[] data = "original".getBytes(StandardCharsets.UTF_8);
        java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(data);
        byte[] signature = sig.sign();

        byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);

        assertThat(ledger.verify(neuronId, tampered, signature)).isFalse();
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        var ledger = new NeuronIdentityLedger();
        UUID neuronId = UUID.randomUUID();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();
        ledger.register(neuronId, keyPair.getPublic());

        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        byte[] badSignature = new byte[64];

        assertThat(ledger.verify(neuronId, data, badSignature)).isFalse();
    }

    @Test
    void shouldRejectSignatureFromDifferentKey() throws Exception {
        var ledger = new NeuronIdentityLedger();
        UUID neuronId = UUID.randomUUID();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair registered = kpg.generateKeyPair();
        KeyPair other = kpg.generateKeyPair();

        ledger.register(neuronId, registered.getPublic());

        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(other.getPrivate());
        sig.update(data);
        byte[] signature = sig.sign();

        assertThat(ledger.verify(neuronId, data, signature)).isFalse();
    }

    @Test
    void shouldGetPublicKeyForRegisteredNeuron() throws Exception {
        var ledger = new NeuronIdentityLedger();
        UUID neuronId = UUID.randomUUID();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();
        ledger.register(neuronId, keyPair.getPublic());

        assertThat(ledger.getPublicKey(neuronId)).isEqualTo(keyPair.getPublic());
    }

    @Test
    void shouldReturnNullForUnregisteredNeuron() {
        var ledger = new NeuronIdentityLedger();

        assertThat(ledger.getPublicKey(UUID.randomUUID())).isNull();
    }

    @Test
    void shouldUnregisterNeuron() throws Exception {
        var ledger = new NeuronIdentityLedger();
        UUID neuronId = UUID.randomUUID();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();
        ledger.register(neuronId, keyPair.getPublic());

        assertThat(ledger.isRegistered(neuronId)).isTrue();
        assertThat(ledger.size()).isEqualTo(1);

        ledger.unregister(neuronId);

        assertThat(ledger.isRegistered(neuronId)).isFalse();
        assertThat(ledger.size()).isEqualTo(0);
    }

    @Test
    void shouldTrackMultipleNeurons() throws Exception {
        var ledger = new NeuronIdentityLedger();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");

        for (int i = 0; i < 5; i++) {
            KeyPair keyPair = kpg.generateKeyPair();
            ledger.register(UUID.randomUUID(), keyPair.getPublic());
        }

        assertThat(ledger.size()).isEqualTo(5);
    }
}
