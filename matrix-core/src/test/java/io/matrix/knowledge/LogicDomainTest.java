package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W41 — the second domain.
 *
 * <p>The mission's §10: "Extend the current W40 photosynthesis demonstration.
 * Do not remove it. Add at least one independent subject domain to test
 * whether the knowledge and reasoning engine works outside the original
 * curated package."
 *
 * <p>Boolean logic is that domain, and it was chosen because it exercises a
 * different kind of knowledge: formal facts, rules, and a computable
 * consequence relation, rather than biological mechanisms and conditions.
 *
 * <p>The point of the domain is NOT that the answers are right - the
 * biology package is still there and still works. The point is that the
 * engine does not know the answers in advance: the pack holds claims and
 * rules, and the questions below were not written into it.
 */
class LogicDomainTest {

    private KnowledgeSnapshot logic;
    private ReasoningEngine engine;

    @BeforeEach
    void setUp() {
        logic = new KnowledgeImporter().importResource("knowledge/logic.pack.json");
        engine = new ReasoningEngine();
    }

    @Test
    @DisplayName("the package imports with its sources, claims and rules")
    void thePackageImports() {
        assertThat(logic.concepts()).hasSize(11);
        assertThat(logic.claims()).hasSize(28);
        assertThat(logic.sources()).hasSize(2);
        assertThat(logic.derivations()).hasSize(3);
    }

    @Test
    @DisplayName("every source has a real locator and a content hash")
    void everySourceHasARealLocatorAndAContentHash() {
        assertThat(logic.sources()).allSatisfy(source -> {
            assertThat(source.locator()).startsWith("https://");
            assertThat(source.contentHash()).isNotBlank();
            assertThat(source.publishedAt()).isNotBlank();
        });
    }

    @Test
    @DisplayName("every established claim carries a warrant and a validation method")
    void everyEstablishedClaimCarriesAWarrantAndAValidationMethod() {
        assertThat(logic.claims()).allSatisfy(claim -> {
            assertThat(claim.validationMethod()).isNotBlank();
            assertThat(claim.extractionMethod()).isNotBlank();
            if (claim.status() == ClaimStatus.VERIFIED || claim.status() == ClaimStatus.SUPPORTED) {
                assertThat(claim.warrant()).isNotEqualTo(ClaimWarrant.NONE);
            }
        });
    }

    @Test
    @DisplayName("a question the workspace does not cover is abstained from")
    void aQuestionTheWorkspaceDoesNotCoverIsAbstainedFrom() {
        ReasoningEngine.Result result =
                engine.reason(logic, "What is the boiling point of mercury?",
                        AbstractionLevel.INTRODUCTORY);

        assertThat(result.answered()).isFalse();
        assertThat(result.abstentionReason()).isNotBlank();
        assertThat(result.answer()).isEmpty();
    }

    @Test
    @DisplayName("a question outside the domain is abstained from, not guessed")
    void aQuestionOutsideTheDomainIsAbstainedFrom() {
        ReasoningEngine.Result result = engine.reason(logic,
                "How does a plant convert light energy into sugars?", AbstractionLevel.INTRODUCTORY);

        assertThat(result.answered()).isFalse();
    }

    @Test
    @DisplayName("a fact question is answered from the stored claims, with its sources")
    void aFactQuestionIsAnsweredFromTheStoredClaims() {
        ReasoningEngine.Result result = engine.reason(logic, "What is modus ponens?",
                AbstractionLevel.INTRODUCTORY);

        assertThat(result.answered()).isTrue();
        assertThat(result.task()).isEqualTo(ReasoningEngine.TaskKind.FACT);
        assertThat(result.claims()).isNotEmpty();
        assertThat(result.sources()).isNotEmpty();
        assertThat(result.answer()).containsIgnoringCase("modus ponens");
    }

    @Test
    @DisplayName("the rules are checked exactly and the derivation is reported")
    void theRulesAreCheckedExactlyAndTheDerivationIsReported() {
        ReasoningEngine.Result result = engine.reason(logic,
                "Is q true if p is true and p implies q?", AbstractionLevel.INTRODUCTORY);

        // The rule "modus ponens" is checked against the claims and the
        // premises the question supplies.
        assertThat(result.derivations()).anySatisfy(derivation -> {
            assertThat(derivation.ruleName()).isEqualTo("modus ponens");
            assertThat(derivation.status()).isEqualTo(DerivationStatus.DERIVED_VALID);
            assertThat(derivation.premiseClaimIds()).hasSize(2);
        });
        assertThat(result.steps()).anyMatch(step -> step.stage().equals("inference")
                && step.detail().contains("DERIVED_VALID"));
    }

    @Test
    @DisplayName("a rule whose premise is missing is reported as invalid, not as valid")
    void aRuleWhosePremiseIsMissingIsReportedAsInvalid() {
        // The question supplies "p implies q" but not "p is true", so the
        // modus ponens rule cannot fire, and the chain over two steps
        // certainly cannot.
        ReasoningEngine.Result result = engine.reason(logic,
                "Is r true if p implies q and q implies r?", AbstractionLevel.INTRODUCTORY);

        assertThat(result.derivations()).anySatisfy(derivation ->
                assertThat(derivation.status()).isEqualTo(DerivationStatus.DERIVED_INVALID));
    }

    @Test
    @DisplayName("the conflict between opposite claims is recorded and neither side is preferred")
    void theConflictBetweenOppositeClaimsIsRecorded() {
        List<Contradiction> conflicts = new ContradictionDetector().detect(logic);

        // Exactly two conflicts: "p is true" against "p is not true" in the
        // first assignment, and the same pair in the other assignment.
        //
        // The claims about the OTHER variables differ in polarity but say
        // different things, so they are not conflicts; and the claim about
        // p in the first assignment is not a conflict with the claim about
        // p in the other assignment, because the scopes differ. A detector
        // that cried "contradiction" on those would be worthless.
        assertThat(conflicts).hasSize(2);
        assertThat(conflicts).allSatisfy(conflict -> {
            assertThat(conflict.kind()).isEqualTo(Contradiction.Kind.OPPOSITE_POLARITY);
            assertThat(conflict.claimAId()).isNotEqualTo(conflict.claimBId());
        });
        // Neither conflict is between claims about different variables.
        assertThat(conflicts).allSatisfy(conflict -> {
            Claim left = claimById(conflict.claimAId());
            Claim right = claimById(conflict.claimBId());
            assertThat(left.conceptId()).isEqualTo(right.conceptId());
            assertThat(left.scope()).isEqualTo(right.scope());
        });
        // And the claims themselves keep their status.
        assertThat(logic.claims()).filteredOn(claim ->
                "The propositional variable p is true in the assignment under consideration."
                        .equals(claim.proposition()))
                .allSatisfy(claim -> assertThat(claim.status()).isEqualTo(ClaimStatus.SUPPORTED));
    }

    private Claim claimById(String id) {
        return logic.claims().stream()
                .filter(claim -> claim.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("the package round-trips through the store with its rules")
    void thePackageRoundTripsThroughTheStoreWithItsRules() throws Exception {
        java.nio.file.Path db = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                "logic-round-trip-" + System.nanoTime() + ".sqlite");
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(logic);
            KnowledgeSnapshot read = store.exportWorkspace("logic");

            assertThat(read.claims()).hasSize(28);
            assertThat(read.derivations()).hasSize(3);
            assertThat(read.derivations()).anySatisfy(derivation -> {
                assertThat(derivation.ruleName()).isEqualTo("modus ponens");
                assertThat(derivation.premiseClaimIds()).hasSize(2);
            });
        } finally {
            java.nio.file.Files.deleteIfExists(db);
        }
    }
}
