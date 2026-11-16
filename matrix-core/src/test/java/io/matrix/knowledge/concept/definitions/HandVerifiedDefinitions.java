package io.matrix.knowledge.concept.definitions;

import java.util.List;

/**
 * EPISTEMIC-INTEGRITY P1 — the FROZEN HUMAN-VERIFIED SAMPLE of the definitions (A2-ii).
 *
 * <p><b>What this is.</b> The acceptance measure A2 has two parts: (i) the fraction
 * of concepts with a usable definition, and (ii) the fraction of those a human
 * marks as <i>about that concept</i>. The research measured (i) and listed (ii)
 * as a gap ("needs a labelled subset of ~100 definitions"). This sample closes
 * part of that gap.</p>
 *
 * <p><b>PROVENANCE.</b> The definitions of the seed corpus, sorted by the concept
 * id and shuffled with {@link java.util.Random} seeded <b>20261115</b> (the
 * research's seed); the first <b>25</b> are taken. They were labelled by hand on
 * 2026-11-16, against the definition's own text, with the rule: <b>the
 * definition is about the concept when the sentence states something about the
 * thing the concept's name refers to</b>. A sentence that merely mentions the
 * name is not about the concept.</p>
 *
 * <p><b>WHY THE DEFINITIONS ARE KEYED BY THEIR DIGEST.</b> A definition is a
 * sentence of the seed corpus, and the P0.3 held-out set's gold propositions are
 * sentences of the same corpus: a file that carried the definition verbatim would
 * carry a gold string of the held-out set, and the P0.3 guard
 * ({@code EpistemicBaselineTest}) would — rightly — refuse it. The sample is
 * therefore keyed by the SHA-256 of the definition, and the human-readable record
 * of the 25 sentences and their judgements is in
 * {@code docs-v2/engineering/EPISTEMIC-CONCEPT-DEFINITIONS.md}, which is a
 * document and not the development code.</p>
 *
 * <p><b>WHAT THE SAMPLE DOES NOT ESTABLISH.</b> n = 25 is a small sample: the
 * share it gives carries a wide interval, and the corpus is one domain, one
 * register, one language. The judgement is also generous for a concept whose
 * name is a fragment or a pronoun — the sentence still says something about what
 * the fragment refers to — and one label says so explicitly.</p>
 */
final class HandVerifiedDefinitions {

    /**
     * One verified definition.
     *
     * @param position the position in the draw (1-based)
     * @param definitionSha256 the SHA-256 of the definition, in lower-case hex
     * @param aboutTheConcept whether a human marked it as about its concept
     */
    record VerifiedDefinition(int position, String definitionSha256, boolean aboutTheConcept) {
    }

    /** The seed of the draw, the research's seed. */
    static final long DRAW_SEED = 20261115L;

    /** How many definitions the draw takes. */
    static final int SAMPLE_SIZE = 25;

    /** The frozen sample, in the draw's order. */
    static final List<VerifiedDefinition> SAMPLE = List.of(
            new VerifiedDefinition(1, "f031ceff9481f97c8f974a429aacc7c5022c60fc89b92e40b668b44cd90468e1", true),
            new VerifiedDefinition(2, "7a895e7714973e8db2f5a64df77edce6e7db48008130a7adc9f01cb799ca2f6f", true),
            new VerifiedDefinition(3, "de7e39773910704363f74b6ec8eed53aff44cda4b22d7ca8f89fe92a59abb135", true),
            new VerifiedDefinition(4, "41ddce2d2630cb71d5df759f1322a8a89f26e79570c0d79053ffe6dfa0e689e9", true),
            new VerifiedDefinition(5, "95576a415fbe56f60071086621922bc4b605c4b535aa8a95760376c0109fb2a1", true),
            new VerifiedDefinition(6, "0f43326fb97ee7b41c49c156605a841305418354b4d267595eb3c4af6bc39ba4", true),
            new VerifiedDefinition(7, "23417aa019ec9fe78554d671c8a27a3ee06e2e577fca9d3c77675c5b6d4e1d84", true),
            new VerifiedDefinition(8, "73d6611b5280b5213fd4cdc00ccd1afde88becd98f496e3da2ffbb83aef2193f", true),
            new VerifiedDefinition(9, "2af42769eb58c4c14fc8b51aba09c053d6e8f4330ede41ed0ade6c6e018aeb88", true),
            new VerifiedDefinition(10, "f870573ad4ab57ef7195cfb024d50c37e5cf5b9bd2e76da6ed195c95cfeb5e3d", true),
            new VerifiedDefinition(11, "01d1b6766fc39b86a493a0fa6a6ed1355b1517e0833615ff4c566f1c45efe39c", true),
            new VerifiedDefinition(12, "123747cd3c37f1836967562e638423d121e291eb5473f8f64c3dd1942219ebbb", true),
            new VerifiedDefinition(13, "e8a7abd2ff1d2e5a6ea2b0b952818435c7abd77ac25f039f7493912a98de1972", true),
            new VerifiedDefinition(14, "1a8e80f9ad797b22972901d5ebd4f8095071d478c9d14411de44c4cc9a38c4e6", true),
            new VerifiedDefinition(15, "86aa917c236faca0d10cb1ac9addb84d2ceee892831b2d380065457bdcbd42d1", true),
            new VerifiedDefinition(16, "19d9f57a30e1fc09466b130b411c4b43b5eba83d8e223b39b3baefb5931d147c", true),
            new VerifiedDefinition(17, "61b19ccdf943ec0fdd55bb0f5c1274b58c262c14f84d49c05cfb2c0012e08a57", true),
            new VerifiedDefinition(18, "27659be37ef2270c6c70251d3c3e5c39fbeaf831fafbb905ec47a36e2adda42f", true),
            new VerifiedDefinition(19, "f8a3b20cc14334693da6dc4076ff6cc7495ed56c716e0f4f6d09ca2fe78db6b2", false),
            new VerifiedDefinition(20, "ded87d68cd5def418a3efbfbf412dd8dd07d2e102c594d2ba55237963d71b077", true),
            new VerifiedDefinition(21, "b05230c2df445b67a16c6d84127fc694cc32665be8f0934872f7e93956954b7f", true),
            new VerifiedDefinition(22, "e243e08b5026b481537754a02530c20606c1ac5112237c616a2e6471f920e634", true),
            new VerifiedDefinition(23, "3a8af0043b86c36a9bb813de559a3aa87111f1786a03e5073c069c5e3e9300ec", true),
            new VerifiedDefinition(24, "d4cecdcb1ce8102bdf49da576f2cbd617079126ccca769c4b111f331fc89ac75", true),
            new VerifiedDefinition(25, "f61751514d24f21dcb688249de5d685d7d11663d15d1cc07091826b8b59b4a2d", true));

    private HandVerifiedDefinitions() {
    }
}
