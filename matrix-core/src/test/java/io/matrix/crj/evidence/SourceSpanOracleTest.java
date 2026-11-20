package io.matrix.crj.evidence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The source-span oracle's own tests, and the instrument's own control.
 *
 * <p>The control is the point of this class: an oracle that cannot <b>disagree</b> with the
 * solver is not an instrument. The fixture is a deliberately corrupted gold record — one case's
 * gold proposition altered by a single character — and the expectation is that the oracle
 * reports {@code GOLD_NOT_IN_CORPUS} for that case and <b>fails loudly</b>, rather than
 * scoring it as an ungrounded answer. A control fails if the corrupted case still scores as
 * grounded.</p>
 */
class SourceSpanOracleTest {

    // the proposition must occur VERBATIM, so it is written on ONE line: a line break inside
    // the gold would make it a different string, and the oracle would say so
    private static final String DOCUMENT = """
            The lamp world is the simplest thing that still has something to learn.
            Photosynthesis converts light energy into chemical energy stored in organic compounds such as sugars and starches.
            The two inputs and the one hidden rule are all there is to it.
            """;

    private static final String GOLD = "Photosynthesis converts light energy into chemical "
            + "energy stored in organic compounds such as sugars and starches.";

    @Test
    @DisplayName("a positive case whose answer cites the gold is grounded")
    void theGroundedCase(@TempDir Path corpus) throws Exception {
        SourceSpanOracle oracle = corpusWith(corpus);
        SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase("case-1", "lamp.md",
                oracle.sha256Of("lamp.md"), List.of(GOLD), List.of("clm-abc123"), true,
                SourceSpanOracle.Field.PREMISE, "", "");
        SourceSpanOracle.ServedAnswer served = new SourceSpanOracle.ServedAnswer("WRITTEN",
                "ANSWERED", "SUPPORTED",
                List.of(new SourceSpanOracle.CitedClaim("clm-abc123", GOLD, "SUPPORTED",
                        "src-1")), List.of());

        SourceSpanOracle.Verdict verdict = oracle.score(gold, served);

        assertThat(verdict.outcome()).isEqualTo(SourceSpanOracle.Outcome.GROUNDED);
        assertThat(verdict.grounded()).isTrue();
    }

    @Test
    @DisplayName("a positive case whose answer cites the proposition under ANOTHER claim is not grounded")
    void theCitationUnderAnotherClaimIsNotGrounded(@TempDir Path corpus) throws Exception {
        SourceSpanOracle oracle = corpusWith(corpus);
        SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase("case-1", "lamp.md",
                oracle.sha256Of("lamp.md"), List.of(GOLD), List.of("clm-abc123"), true,
                SourceSpanOracle.Field.PREMISE, "", "");
        SourceSpanOracle.ServedAnswer served = new SourceSpanOracle.ServedAnswer("WRITTEN",
                "ANSWERED", "SUPPORTED",
                List.of(new SourceSpanOracle.CitedClaim("clm-OTHER", GOLD, "SUPPORTED", "src-1")),
                List.of());

        SourceSpanOracle.Verdict verdict = oracle.score(gold, served);

        assertThat(verdict.outcome()).isEqualTo(SourceSpanOracle.Outcome.UNGROUNDED);
        assertThat(verdict.grounded()).isFalse();
    }

    @Test
    @DisplayName("a positive case whose answer cites a DIFFERENT proposition is not grounded")
    void theDifferentPropositionIsNotGrounded(@TempDir Path corpus) throws Exception {
        SourceSpanOracle oracle = corpusWith(corpus);
        SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase("case-1", "lamp.md",
                oracle.sha256Of("lamp.md"), List.of(GOLD), List.of("clm-abc123"), true,
                SourceSpanOracle.Field.PREMISE, "", "");
        SourceSpanOracle.ServedAnswer served = new SourceSpanOracle.ServedAnswer("WRITTEN",
                "ANSWERED", "SUPPORTED",
                List.of(new SourceSpanOracle.CitedClaim("clm-abc123",
                        "Photosynthesis converts light energy into chemical energy.", "SUPPORTED",
                        "src-1")), List.of());

        SourceSpanOracle.Verdict verdict = oracle.score(gold, served);

        assertThat(verdict.outcome()).isEqualTo(SourceSpanOracle.Outcome.UNGROUNDED);
    }

    @Test
    @DisplayName("the instrument's own control: a gold corrupted by one character fails LOUDLY")
    void theCorruptedGoldFailsLoudly(@TempDir Path corpus) throws Exception {
        SourceSpanOracle oracle = corpusWith(corpus);
        // the control's fixture: the gold proposition is altered by a SINGLE character
        String corrupted = GOLD.replace("chemical", "chemicaL");
        SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase("case-1", "lamp.md",
                oracle.sha256Of("lamp.md"), List.of(corrupted), List.of("clm-abc123"), true,
                SourceSpanOracle.Field.PREMISE, "", "");
        // and the answer cites the corrupted proposition, so a lenient oracle would call it
        // grounded
        SourceSpanOracle.ServedAnswer served = new SourceSpanOracle.ServedAnswer("WRITTEN",
                "ANSWERED", "SUPPORTED",
                List.of(new SourceSpanOracle.CitedClaim("clm-abc123", corrupted, "SUPPORTED",
                        "src-1")), List.of());

        SourceSpanOracle.Verdict verdict = oracle.score(gold, served);

        assertThat(verdict.outcome())
                .as("the corrupted case must fail loudly, not score as grounded")
                .isEqualTo(SourceSpanOracle.Outcome.GOLD_NOT_IN_CORPUS);
        assertThat(verdict.grounded()).isFalse();
        assertThat(verdict.reason()).contains("does not occur verbatim");
    }

    @Test
    @DisplayName("a corpus whose bytes are not the frozen ones is reported, not scored")
    void theChangedCorpusIsReported(@TempDir Path corpus) throws Exception {
        SourceSpanOracle oracle = corpusWith(corpus);
        SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase("case-1", "lamp.md",
                "0000000000000000000000000000000000000000000000000000000000000000",
                List.of(GOLD), List.of("clm-abc123"), true,
                SourceSpanOracle.Field.PREMISE, "", "");
        SourceSpanOracle.ServedAnswer served = new SourceSpanOracle.ServedAnswer("WRITTEN",
                "ANSWERED", "SUPPORTED",
                List.of(new SourceSpanOracle.CitedClaim("clm-abc123", GOLD, "SUPPORTED",
                        "src-1")), List.of());

        SourceSpanOracle.Verdict verdict = oracle.score(gold, served);

        assertThat(verdict.outcome()).isEqualTo(SourceSpanOracle.Outcome.CORPUS_CHANGED);
        assertThat(verdict.grounded()).isFalse();
    }

    @Test
    @DisplayName("a negative case is scored on its disposition and its provenance")
    void theNegativeCase(@TempDir Path corpus) throws Exception {
        SourceSpanOracle oracle = corpusWith(corpus);
        SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase("case-2", "lamp.md",
                oracle.sha256Of("lamp.md"), List.of(GOLD), List.of(), false,
                SourceSpanOracle.Field.PREMISE, "UNSUPPORTED", "src-refuter");
        SourceSpanOracle.ServedAnswer served = new SourceSpanOracle.ServedAnswer("WRITTEN",
                "ANSWERED", "UNSUPPORTED", List.of(), List.of("src-refuter"));

        SourceSpanOracle.Verdict verdict = oracle.score(gold, served);

        assertThat(verdict.outcome())
                .isEqualTo(SourceSpanOracle.Outcome.DISPOSITION_MATCHED);
        assertThat(verdict.grounded()).isTrue();
    }

    @Test
    @DisplayName("a negative case whose provenance does not match is a mismatch")
    void theNegativeCaseWithTheWrongProvenance(@TempDir Path corpus) throws Exception {
        SourceSpanOracle oracle = corpusWith(corpus);
        SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase("case-2", "lamp.md",
                oracle.sha256Of("lamp.md"), List.of(GOLD), List.of(), false,
                SourceSpanOracle.Field.PREMISE, "UNSUPPORTED", "src-refuter");
        SourceSpanOracle.ServedAnswer served = new SourceSpanOracle.ServedAnswer("WRITTEN",
                "ANSWERED", "UNSUPPORTED", List.of(), List.of("src-other"));

        SourceSpanOracle.Verdict verdict = oracle.score(gold, served);

        assertThat(verdict.outcome())
                .isEqualTo(SourceSpanOracle.Outcome.DISPOSITION_MISMATCHED);
        assertThat(verdict.grounded()).isFalse();
    }

    /** A corpus with one document in it, and the oracle over it. */
    private static SourceSpanOracle corpusWith(Path corpus) throws Exception {
        Files.writeString(corpus.resolve("lamp.md"), DOCUMENT, StandardCharsets.UTF_8);
        return new SourceSpanOracle(corpus);
    }
}
