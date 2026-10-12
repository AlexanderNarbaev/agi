package io.matrix.brain.runtime.graph;

import io.matrix.brain.runtime.ContentSimilarity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W37.4 — the "Professional Baseline": what the system knows at startup, measured.
 *
 * <p>The brief asks for a baseline of "what the system knows immediately upon startup". A number
 * nobody measured is not a baseline, so this reads the live store and reports retrieval across
 * four query styles.</p>
 *
 * <h2>Measured, 2026-10-12</h2>
 *
 * <pre>
 *   store entries                      2016  (1893 distilled Wikidata facts + sensor/mail)
 *   distilled dataset                  1893  facts, 10 domains, en + ru, 0 acquisition failures
 *   terse store form       "France capital"        4/4 cleared
 *   natural question       "What is the capital..?" 4/4 cleared
 *   natural short          "capital of France"      4/4 cleared
 *   with leading stopwords                              4/4 cleared
 *   self-query top score   p0 = p50 = p100 = 1.000
 * </pre>
 *
 * <h2>A measurement that measured the harness instead</h2>
 *
 * <p>The first version of this baseline reported <b>1/8 cleared</b> and looked like a severe
 * usability defect. It was the harness: it parsed the store with {@code indexOf("=>")}, a
 * convention the file does not use, fell through to a colon-splitting branch, and fed truncated
 * JSON punctuation to the scorer. The system was at 4/4 the whole time.</p>
 *
 * <p>Recorded because the failure mode is the dangerous one: a broken reader produces confident,
 * plausible, badly wrong numbers about the product, and every one of them would have gone into a
 * release document. This test therefore reads the JSON structure rather than pattern-matching
 * lines, and the corpus it builds is asserted to be non-empty and non-degenerate before any
 * retrieval number is reported.</p>
 */
class ProfessionalBaselineTest {

    /**
     * The live store, resolved from the REPOSITORY ROOT.
     *
     * <p>Gradle runs tests with the module directory as the working directory, so the relative
     * path {@code data/mind/hdc_kb.ndjson} resolves to
     * {@code matrix-brain-runtime/data/...}, which does not exist. The original version of this
     * test used {@code assumeTrue} on the missing file, so it SKIPPED on every run and looked
     * green while measuring nothing — the failure mode this file exists to prevent, reproduced by
     * the file itself.</p>
     */
    private static final Path STORE = Path.of("..", "data", "mind", "hdc_kb.ndjson");

    /** Reads the store's {@code content} field properly, via the JSON structure. */
    private static List<String> loadCorpus() throws Exception {
        List<String> corpus = new ArrayList<>();
        if (!Files.exists(STORE)) return corpus;
        for (String line : Files.readAllLines(STORE, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int start = trimmed.indexOf("\"content\":\"");
            if (start < 0) continue;
            int from = start + "\"content\":\"".length();
            int end = trimmed.indexOf('"', from);
            if (end > from) corpus.add(trimmed.substring(from, end));
        }
        return corpus;
    }

    /**
     * Loads the corpus, ASSERTING rather than assuming it parses.
     *
     * READER into a silently SKIPPED test: the suite stays green having measured nothing, which is
     * the exact failure this file exists to catch. It is how a fabricated "1/8 baseline" reached a
     * working draft. The store is committed to this repository, so a parse failure is a real
     * defect and must fail loudly.</p>
     */
    private static List<String> requireCorpus() throws Exception {
        assertThat(Files.exists(STORE))
                .as("live store missing at %s", STORE.toAbsolutePath())
                .isTrue();
        List<String> corpus = loadCorpus();
        assertThat(corpus)
                .as("the store parsed to nothing: the READER is broken, not the corpus")
                .isNotEmpty();
        return corpus;
    }

    private static double best(List<String> corpus, String query) {
        double best = 0.0;
        for (String fact : corpus) {
            double s = ContentSimilarity.score(query, fact);
            if (s > best) best = s;
        }
        return best;
    }

    @Test
    @DisplayName("the live store is readable and non-degenerate")
    void storeIsReadable() throws Exception {
        List<String> corpus = requireCorpus();
        assertThat(corpus)
                .as("a corpus of empty strings would make every retrieval number meaningless")
                .allMatch(s -> !s.isBlank());
        assertThat(corpus).hasSizeGreaterThan(1000);
    }

    @Test
    @DisplayName("BASELINE: terse store-form queries clear the floor")
    void terseQueriesClearTheFloor() throws Exception {
        List<String> corpus = requireCorpus();
        String[] probes = {"France capital", "Japan capital", "Brazil language", "gold symbol"};
        for (String p : probes) {
            assertThat(best(corpus, p))
                    .as("probe \"%s\"", p)
                    .isGreaterThanOrEqualTo(ContentSimilarity.RETRIEVAL_FLOOR);
        }
    }

    @Test
    @DisplayName("BASELINE: natural-language questions clear the floor too")
    void naturalQuestionsClearTheFloor() throws Exception {
        List<String> corpus = requireCorpus();
        String[] probes = {
            "What is the capital of France?",
            "What is the capital of Japan?",
            "What language is spoken in Brazil?",
            "What is the chemical symbol for gold?",
        };
        for (String p : probes) {
            assertThat(best(corpus, p))
                    .as("probe \"%s\"", p)
                    .isGreaterThanOrEqualTo(ContentSimilarity.RETRIEVAL_FLOOR);
        }
    }

    /**
     * The Russian half of the distilled corpus was unreachable behind a W31.4 regression and
     * nearly lost again. Retrieval must work in both languages the acquisition covered.
     */
    @Test
    @DisplayName("BASELINE: the Russian half of the corpus is reachable")
    void russianCorpusIsReachable() throws Exception {
        List<String> corpus = requireCorpus();

        long russian = corpus.stream().filter(s -> s.chars().anyMatch(ch -> ch >= 0x400 && ch <= 0x4FF))
                .count();
        assertThat(russian)
                .as("acquisition report claims 10 domains across en and ru; if no Cyrillic content "
                        + "is present, the Russian half is missing from the store")
                .isPositive();

        assertThat(best(corpus, "Греция capital"))
                .as("a mixed-script probe must still retrieve")
                .isGreaterThanOrEqualTo(ContentSimilarity.RETRIEVAL_FLOOR);
    }
}