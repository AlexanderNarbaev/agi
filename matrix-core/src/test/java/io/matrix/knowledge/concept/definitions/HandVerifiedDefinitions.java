package io.matrix.knowledge.concept.definitions;

import java.util.List;

/**
 * EPISTEMIC-INTEGRITY P1 - the FROZEN HAND-VERIFIED SET of the definitions (A2-ii).
 *
 * <p><b>What this is.</b> The acceptance measure A2 has two parts: (i) the
 * fraction of concepts with a usable definition, and (ii) the fraction of
 * those a human marks as <i>a definition</i> - as opposed to a sentence that
 * merely mentions the concept. This set closes part of that gap.</p>
 *
 * <p><b>THE METRIC, AS CORRECTED BY THE HARD REVIEW (2026-11-16).</b> The first
 * version of this set measured "about the concept", which is a weaker question
 * and flatters the rule: a sentence can be about a concept and still say
 * nothing about what the concept IS. The metric is now:</p>
 *
 * <blockquote><b>A definition</b> is a sentence whose subject is the concept and
 * whose predicate states the concept's nature, composition or identity - a
 * sentence that could stand as the concept's glossary entry. A sentence that
 * states an action the concept performs, a comparison, a discourse about the
 * concept, or a denial of it, is <b>not</b> a definition.</blockquote>
 *
 * <p><b>PROVENANCE, AND WHAT CHANGED.</b> The definitions of the seed corpus
 * under the CORRECTED rule (the subject test, the negation test and the
 * non-term rejection) are <b>27</b>, and this set is a CENSUS of all of them -
 * with 27 definitions a sample would only hide the effect of the choice. The
 * sentences were labelled by hand on 2026-11-16, against the definition's own
 * text, under the metric above.</p>
 *
 * <p><b>THE CORRECTION, STATED PLAINLY.</b> The earlier set measured "about the
 * concept" over 25 definitions of the earlier (weaker) rule and reported
 * 24/25 = 96 %. That number was measured against a weaker definition of
 * "definition" and it is <b>superseded</b>: the current set measures "is a
 * definition" over a census of 27, and the number it gives is lower. The
 * earlier claim is kept in the document as a correction, never replaced
 * silently.</p>
 *
 * <p><b>WHY THE DEFINITIONS ARE KEYED BY THEIR DIGEST.</b> A definition is a
 * sentence of the seed corpus, and the P0.3 held-out set's gold propositions are
 * sentences of the same corpus: a file that carried the definition verbatim
 * would carry a gold string of the held-out set, and the P0.3 guard
 * ({@code EpistemicBaselineTest}) would - rightly - refuse it. The set is
 * therefore keyed by the SHA-256 of the definition, and the human-readable
 * record of the sentences and their judgements is in
 * {@code docs-v2/engineering/EPISTEMIC-CONCEPT-DEFINITIONS.md}, which is a
 * document and not the development code.</p>
 *
 * <p><b>WHAT THE SET DOES NOT ESTABLISH.</b> The judgements were made after the
 * rule was corrected, not blind; the corpus is one domain, one register, one
 * language; and the measure is a human judgement, not a proof.</p>
 */
final class HandVerifiedDefinitions {

    /**
     * One verified definition.
     *
     * @param position the position in the census
     * @param definitionSha256 the SHA-256 of the definition, in lower-case hex
     * @param isADefinition whether a human marked it as a definition of its concept
     */
    record VerifiedDefinition(int position, String definitionSha256, boolean isADefinition) {
    }

    /** How many definitions the census covers - every one the corrected rule finds. */
    static final int SAMPLE_SIZE = 27;

    /** The frozen set, in the census's order. */
    static final List<VerifiedDefinition> SAMPLE = List.of(
            new VerifiedDefinition(1, "2e18b6ee079dc3fae207515e6e2dbac92651d2e8d77b6bdd67ac0f90379829a2", true),
            new VerifiedDefinition(2, "e2c60c0f2304a710a51b6eae145c46e09a4efe299143325ca6f4d197b45a34e0", true),
            new VerifiedDefinition(3, "b44a9b86add3a00dcd628abedce45a474d65f68a1cbb317fa4f600e325bc7365", true),
            new VerifiedDefinition(4, "2fa443914b04d547b47170a1db1e04c5021b58c7fb7b33e7ab2ef980576d39f1", false),
            new VerifiedDefinition(5, "b949f4df837c173a47a63d0a64833e9614569e0cc225f409b376ad686b612705", true),
            new VerifiedDefinition(6, "32bdf053ebc27432e617976aa48e69218028f243867a566effc2ba19c4e47fe2", true),
            new VerifiedDefinition(7, "9d051867ae1df48db60b8db3978b748f1872de6963dfabe72ef7406972941776", true),
            new VerifiedDefinition(8, "d9498aac153e040d636b0bf774123621bbfda39c2c4651aa08c734b00b46223c", false),
            new VerifiedDefinition(9, "c6f07b5e3d38ca8dc5692dd3c38d405944c38377de685681ce4d571615dc640a", false),
            new VerifiedDefinition(10, "cb239d9477a5f20c5c2a181e756b6f885fb2a72c16bf38b2991f33b84781a260", false),
            new VerifiedDefinition(11, "67a10e1e41ca806dab253fe41d4032f779d16b2bd65ce2d8ee7bbb45f5262c61", false),
            new VerifiedDefinition(12, "e64cb1568c158748c510583b7e4a37e53fe4f7330d09ed5c66c739a7bf01e648", false),
            new VerifiedDefinition(13, "1356ab87a10daf9c5331465e5320f4352bb81fd191e42790baa21557a3058368", true),
            new VerifiedDefinition(14, "38245458889181141c639ccc3bf6991bc7759858de896661bb0590553451b60f", true),
            new VerifiedDefinition(15, "4a057f90faf363344a822fc4763c6ee94a4c157384b6068fdfe9658e4c8856d1", true),
            new VerifiedDefinition(16, "4d3496b264628603aa009d068f62410888ad096b93b0c39cd7e60181a64ab82d", true),
            new VerifiedDefinition(17, "e8a7abd2ff1d2e5a6ea2b0b952818435c7abd77ac25f039f7493912a98de1972", true),
            new VerifiedDefinition(18, "b8199725c638cbe3b7e1fe83bb66f2d42563e091463323cd019e1540813d9b8d", true),
            new VerifiedDefinition(19, "0369095682cdd80080176f08a02e82db21717761b9f8e127a9025c0c8b950b4f", true),
            new VerifiedDefinition(20, "b2f4cdf1cc880830ada3250a860f80acdd2bd073492ffc3f1ab88bae42ff29a7", false),
            new VerifiedDefinition(21, "df1addb707f5d703a2d527f35ac46d7cbfe0b051fb940e1f81e2fb4fb7bc159e", true),
            new VerifiedDefinition(22, "87e775fadca466e8d8a26d4f19097c143716709455fa4b85e9b6a9123e192f6e", false),
            new VerifiedDefinition(23, "01d1b6766fc39b86a493a0fa6a6ed1355b1517e0833615ff4c566f1c45efe39c", false),
            new VerifiedDefinition(24, "012a06fc62094039892035b6097a6d36d32f65ef873ec3a0f92b387e3b40e988", true),
            new VerifiedDefinition(25, "f69cc98e9be68aff74c79748b97a0410afaa18c0d810778df07869ef22bab167", true),
            new VerifiedDefinition(26, "d2aed1837dd502139209321dcf5590ab5179276ca62ade95abbddee8abde669d", true),
            new VerifiedDefinition(27, "0f43326fb97ee7b41c49c156605a841305418354b4d267595eb3c4af6bc39ba4", true)
            );
}
