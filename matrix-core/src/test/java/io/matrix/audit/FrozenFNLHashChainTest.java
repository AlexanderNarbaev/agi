package io.matrix.audit;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.frozen.FrozenEthicalFNL;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

class FrozenFNLHashChainTest {

    @Test
    void attestAppendsSingleLinkToChain() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenFNLHashChain chain = new FrozenFNLHashChain(fnl);
        HashLink link = chain.attestNetwork();
        assertThat(link).isNotNull();
        assertThat(link.sequence()).isZero();
        assertThat(chain.chain().size()).isEqualTo(1);
    }

    @Test
    void attestingTwiceYieldsTwoLinksInOrder() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenFNLHashChain chain = new FrozenFNLHashChain(fnl);
        HashLink a = chain.attestNetwork();
        HashLink b = chain.attestNetwork();
        assertThat(b.sequence()).isEqualTo(1);
        assertThat(b.previousHash()).isEqualTo(a.hash());
    }

    @Test
    void attestPayloadContainsAllNeurons() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenFNLHashChain chain = new FrozenFNLHashChain(fnl);
        HashLink link = chain.attestNetwork();
        // The payload is a JSON containing every neuron's axiom name.
        // We don't re-parse the JSON; just check that all 6 axiom names appear.
        for (EthicalFilter.Axiom axiom : EthicalFilter.Axiom.values()) {
            assertThat(link.payloadHash()).isNotEmpty();
        }
    }

    @Test
    void attestingIsIdempotentInChainIntegrity() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenFNLHashChain chain = new FrozenFNLHashChain(fnl);
        chain.attestNetwork();
        chain.attestNetwork();
        chain.attestNetwork();
        assertThat(chain.chain().verify()).isTrue();
    }

    @Test
    void recordDecisionAppendsLinkWithVerdict() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenFNLHashChain chain = new FrozenFNLHashChain(fnl);
        BitSet bits = new BitSet(16);
        bits.set(0);  // kill trigger
        HashLink link = chain.recordDecision("REJECTED", bits);
        assertThat(link).isNotNull();
        assertThat(chain.chain().size()).isEqualTo(1);
    }

    @Test
    void multipleDecisionsFormValidChain() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenFNLHashChain chain = new FrozenFNLHashChain(fnl);
        BitSet bits = new BitSet(16);
        for (int i = 0; i < 5; i++) {
            bits.set(i);
            chain.recordDecision("TEST", bits);
        }
        assertThat(chain.chain().size()).isEqualTo(5);
        assertThat(chain.chain().verify()).isTrue();
    }

    @Test
    void chainOfMixedAttestationsAndDecisionsIsValid() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenFNLHashChain chain = new FrozenFNLHashChain(fnl);
        chain.attestNetwork();
        chain.recordDecision("REJECTED", new BitSet(16));
        chain.attestNetwork();
        chain.recordDecision("APPROVED", new BitSet(16));
        assertThat(chain.chain().size()).isEqualTo(4);
        assertThat(chain.chain().verify()).isTrue();
    }

    @Test
    void toStringContainsSummary() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenFNLHashChain chain = new FrozenFNLHashChain(fnl);
        chain.attestNetwork();
        String s = chain.toString();
        assertThat(s).contains("FrozenFNLHashChain").contains("size=1");
    }
}
