package io.matrix.audit;

import io.matrix.ethics.frozen.FrozenAxiomNeuron;
import io.matrix.ethics.frozen.FrozenEthicalFNL;
import io.matrix.ethics.frozen.TextFeatureExtractor;
import io.matrix.neuron.TruthTable;

import java.util.Locale;
import java.util.Objects;

/**
 * Bridges the {@link FrozenEthicalFNL} network to a {@link HashChain}, so
 * every observation can be provably tied back to a specific neuron-set
 * (FROZEN-AXIOM-tied). The chain is suitable for off-line audits and
 * "any-tampering-detected" proofs.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Attestation of a FROZEN network's content at a point in time:
 *       {@link #attestNetwork()} appends a single link whose payload is
 *       the canonical fingerprint of every neuron in the network.</li>
 *   <li>Per-decision audit: {@link #recordDecision(String, java.util.BitSet)}
 *       records every verdict the network produced.</li>
 * </ul>
 *
 * <p>Ref: L7 §5, L12 §4.
 */
public final class FrozenFNLHashChain {

    private final HashChain chain;
    private final FrozenEthicalFNL fnl;

    public FrozenFNLHashChain(FrozenEthicalFNL fnl) {
        this.fnl = Objects.requireNonNull(fnl, "fnl");
        this.chain = new HashChain();
    }

    public FrozenFNLHashChain(FrozenEthicalFNL fnl, HashChain chain) {
        this.fnl = Objects.requireNonNull(fnl, "fnl");
        this.chain = Objects.requireNonNull(chain, "chain");
    }

    /**
     * Append a single link to the chain that attests the FROZEN network.
     * The link payload is a sorted, deterministic JSON fingerprint of every
     * neuron in the network (axiom + tag + TruthTable k + size).
     */
    public HashLink attestNetwork() {
        StringBuilder payload = new StringBuilder("{");
        payload.append("\"frozen\":true,");
        payload.append("\"size\":").append(fnl.size()).append(',');
        payload.append("\"k\":").append(fnl.featureExtractor().k()).append(',');
        payload.append("\"neurons\":[");
        boolean first = true;
        for (FrozenAxiomNeuron n : fnl.neurons()) {
            if (!first) payload.append(',');
            first = false;
            TruthTable tt = n.table();
            payload.append('{')
                    .append("\"axiom\":\"").append(n.axiom().name()).append("\",")
                    .append("\"tag\":\"").append(n.tag()).append("\",")
                    .append("\"k\":").append(tt.k()).append(',')
                    .append("\"tableHash\":\"").append(tableHash(tt)).append('"')
                    .append('}');
        }
        payload.append("]}");
        return chain.append(payload.toString(), "FrozenEthicalFNL.attest");
    }

    /**
     * Append a per-decision audit link. The payload includes the input
     * feature bits, the resulting verdict, and the FROZEN axiom fired.
     */
    public HashLink recordDecision(String verdict, java.util.BitSet features) {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(features, "features");
        StringBuilder payload = new StringBuilder("{");
        payload.append("\"verdict\":\"").append(verdict).append("\",");
        payload.append("\"bits\":\"");
        for (int i = 0; i < TextFeatureExtractor.DEFAULT_K; i++) {
            payload.append(features.get(i) ? '1' : '0');
        }
        payload.append("\",");
        payload.append("\"bitsHash\":\"").append(bitsHash(features)).append('"');
        payload.append('}');
        return chain.append(payload.toString(), "FrozenEthicalFNL.decision");
    }

    public HashChain chain() { return chain; }
    public FrozenEthicalFNL fnl() { return fnl; }

    // ── Hash helpers ──

    private static String tableHash(TruthTable tt) {
        // FROZEN invariant: the table is encoded as long[] internally.
        // We hash the BitSet's serialisation for stability across JVMs.
        byte[] bytes = tt.table().toByteArray();
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bitsHash(java.util.BitSet features) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(features.toByteArray()));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "FrozenFNLHashChain[%s]", chain.summary());
    }
}
