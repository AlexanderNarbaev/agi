package io.matrix.security;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed registry of neuron identities and their public keys.
 *
 * <p>Maps {@code NeuronId UUID → Ed25519 public key}. Used to verify that
 * a snapshot or signal originated from a registered neuron.
 *
 * <p>Ref: L6_Memory.md §7
 */
@ApplicationScoped
public class NeuronIdentityLedger {

    private final Map<UUID, PublicKey> ledger = new ConcurrentHashMap<>();

    /**
     * Registers a neuron's public key in the ledger.
     *
     * @param neuronId the neuron's UUID
     * @param pubKey   the neuron's Ed25519 public key
     */
    public void register(UUID neuronId, PublicKey pubKey) {
        ledger.put(neuronId, pubKey);
    }

    /**
     * Verifies that the given data was signed by the neuron registered
     * under {@code neuronId}.
     *
     * @param neuronId  the neuron's UUID
     * @param data      the original data
     * @param signature the signature to verify
     * @return true if the neuron is registered and the signature is valid
     * @throws Exception if verification fails
     */
    public boolean verify(UUID neuronId, byte[] data, byte[] signature) throws Exception {
        PublicKey pubKey = ledger.get(neuronId);
        if (pubKey == null) {
            return false;
        }
        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(pubKey);
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * Returns the public key for a registered neuron.
     *
     * @param neuronId the neuron's UUID
     * @return the public key, or {@code null} if not registered
     */
    public PublicKey getPublicKey(UUID neuronId) {
        return ledger.get(neuronId);
    }

    /**
     * Checks whether a neuron is registered in the ledger.
     *
     * @param neuronId the neuron's UUID
     * @return true if registered
     */
    public boolean isRegistered(UUID neuronId) {
        return ledger.containsKey(neuronId);
    }

    /**
     * Removes a neuron from the ledger.
     *
     * @param neuronId the neuron's UUID
     */
    public void unregister(UUID neuronId) {
        ledger.remove(neuronId);
    }

    /**
     * Returns the number of registered neurons.
     */
    public int size() {
        return ledger.size();
    }
}
