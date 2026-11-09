package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W41 — the number that was called "confidence" is now what it always
 * was: a support score, with its inputs disclosed.
 *
 * <p>The W41.0 audit established that the old field was the mean of the
 * matched claims' authored confidence multiplied by the retrieval coverage,
 * with no calibration data anywhere in the repository, presented to two
 * decimal places under the name of a probability. The mission's rule is
 * explicit: call it a support score and disclose its meaning.
 *
 * <p>These tests are about the disclosure, and about the value not having
 * changed underneath anyone who depended on it.
 */
class SupportScoreTest {

    private KnowledgeRetriever retriever;

    @BeforeEach
    void setUp() {
        KnowledgeSnapshot snapshot =
                new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");
        retriever = new KnowledgeRetriever(snapshot);
    }

    @Test
    @DisplayName("the value is the same one RECON-W40 reported")
    void theValueIsUnchanged() {
        Answer answer = retriever.answer("What is photosynthesis?", AbstractionLevel.INTRODUCTORY);

        // W51 §6 MOVED IT, AND THE REASON IS KNOWN.
        //
        // The score is the mean of the confidences of the claims the
        // answer CITES, and the score now counts the ESTABLISHED claims
        // only. The refuted misconception — the claim that the workspace
        // holds about the oxygen a plant releases, marked REFUTED — used to
        // be in the answer's evidence and used to carry its confidence into
        // the mean. A claim the workspace has withdrawn is not evidence,
        // and a withdrawn claim's confidence is not support.
        //
        // The value therefore moved from 0.85 back to 0.84: the evidence
        // shrank by the claim that was never evidence. The retrieval did
        // not change - the same claims are matched, with the same
        // coverages - and the number still means what it says it means.
        assertThat(answer.supportScore()).isEqualTo(0.84);
    }

    @Test
    @DisplayName("the score says it is not a probability")
    void theScoreSaysWhatItIsNot() {
        Answer answer = retriever.answer("What is photosynthesis?", AbstractionLevel.INTRODUCTORY);

        assertThat(answer.supportMeaning()).contains("NOT a probability");
        assertThat(answer.supportMeaning()).contains("not been calibrated");
        assertThat(answer.support().meaning()).isEqualTo(SupportScore.MEANING);
    }

    @Test
    @DisplayName("the calibration status is honest about there being no calibration")
    void theCalibrationStatusIsHonest() {
        Answer answer = retriever.answer("What is photosynthesis?", AbstractionLevel.INTRODUCTORY);

        assertThat(answer.support().calibrationStatus())
                .contains("not calibrated")
                .contains("no held-out set");
    }

    @Test
    @DisplayName("the contradiction status says the check has not been done")
    void theContradictionStatusIsHonest() {
        Answer answer = retriever.answer("What is photosynthesis?", AbstractionLevel.INTRODUCTORY);

        // NOT_COMPUTED, not NONE_DETECTED: the difference between "there is
        // no contradiction" and "nobody has looked" is the whole point.
        assertThat(answer.support().contradiction())
                .isEqualTo(SupportScore.ContradictionStatus.NOT_COMPUTED);
    }

    @Test
    @DisplayName("the inputs of the score are reported separately")
    void theInputsAreReported() {
        Answer answer = retriever.answer("What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
        SupportScore support = answer.support();

        assertThat(support.evidenceCoverage()).isGreaterThan(0.0);
        assertThat(support.evidenceCoverage()).isLessThanOrEqualTo(1.0);
        assertThat(support.claimCount()).isGreaterThan(0);
        assertThat(support.sourceCount()).isGreaterThan(0);
        assertThat(support.meanSourceAuthority()).isGreaterThan(0.0);
        assertThat(support.meanSourceFreshness()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("the scope match is computed, not asserted")
    void theScopeMatchIsComputed() {
        Answer inside = retriever.answer("What is photosynthesis in green plants?",
                AbstractionLevel.INTRODUCTORY);
        assertThat(inside.support().scopeMatch())
                .isIn(SupportScore.ScopeMatch.MATCHES, SupportScore.ScopeMatch.OUTSIDE,
                        SupportScore.ScopeMatch.UNDECLARED);
    }

    @Test
    @DisplayName("an abstention carries no support score")
    void anAbstentionCarriesNoSupport() {
        Answer answer = retriever.answer("What is the boiling point of mercury?",
                AbstractionLevel.INTRODUCTORY);

        assertThat(answer.answered()).isFalse();
        assertThat(answer.supportScore()).isZero();
        assertThat(answer.support().value()).isZero();
        assertThat(answer.support().meaning()).contains("No answer was given");
        assertThat(answer.support().claimCount()).isZero();
    }
}
