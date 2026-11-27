package io.matrix.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / H2 — the RESTRICTED READING keeps the workspace's relevance statistics.
 *
 * <p><b>What this pins.</b> A reading that is restricted to a set of sources is the same
 * question asked in the same workspace with fewer claims eligible to answer it. The
 * restriction decides <i>what may be cited</i>; the workspace's own statistics decide <i>how
 * relevant</i> a claim is. When the restricted reading recomputed its statistics over the
 * smaller set, the scoring changed as well as the candidates — and a validated procedure's
 * replay could then never be compared with the reference it was validated against.</p>
 *
 * <p><b>Why the recomputation changes the scoring.</b> A term's weight is
 * {@code log(1 + total / documentFrequency)} over the corpus the statistics are computed on.
 * A smaller corpus gives every term a smaller weight, and it gives the <i>common</i> terms a
 * proportionally larger fall: a term carried by 12 of 59 claims loses 22 % of its weight, a
 * term carried by 1 of 59 loses 12 %. The question's total weight falls with them, but a
 * claim whose matched terms are the common ones loses coverage faster than the total does,
 * and it falls below the coverage floor — so the restricted reading drops claims that the
 * unrestricted reading cited.</p>
 *
 * <p><b>What it asserts.</b> On a question whose evidence the support carries, the restricted
 * reading cites the <i>same</i> claims as the unrestricted one, and the reading that computes
 * its statistics over the restricted set cites <i>fewer</i> — so the assertion discriminates:
 * revert the correction and the two readings agree again, and this test fails.</p>
 *
 * <p><b>Why it is its own test and not a line in a milestone's suite.</b> The correction is
 * reusable and it is in the product's retrieval path, so it is pinned by a test that names
 * the mechanism rather than by a milestone that measures an outcome.</p>
 */
class RestrictedReadingStatisticsTest {

    /** The corpus: the two documents the support holds, and the two it does not. */
    private static final List<String> FILES = List.of("h2-magnetic-anomalies.md",
            "h2-spreading-rate.md", "h2-deep-trenches.md", "h2-hydrothermal-vents.md");

    /** The documents the support holds — the other two are outside it. */
    private static final List<String> SUPPORTED = List.of("h2-magnetic-anomalies.md",
            "h2-spreading-rate.md");

    /** The classpath directory the corpus lives in. */
    private static final String RESOURCE = "/crj01-h2/";

    /**
     * The question the defect was measured on: the unrestricted reading cited four claims and
     * the restricted reading cited two.
     */
    private static final String QUESTION =
            "On the two sides of the ridge, what does the polarity sequence of the anomalies do?";

    @Test
    @DisplayName("a restricted reading cites what the unrestricted reading cites, and the reading "
            + "that recomputes its statistics cites less")
    void theRestrictedReadingKeepsTheWorkspacesStatistics(@TempDir Path temporary) throws Exception {
        Path corpus = Files.createDirectories(temporary.resolve("corpus"));
        for (String file : FILES) {
            try (var stream = RestrictedReadingStatisticsTest.class
                    .getResourceAsStream(RESOURCE + file)) {
                assertThat(stream).as("the corpus file %s is on the classpath", file).isNotNull();
                Files.write(corpus.resolve(file), stream.readAllBytes());
            }
        }
        Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            Set<String> support = new LinkedHashSet<>();
            for (String file : FILES) {
                String sourceId = bench.importDocument("restricted", corpus.resolve(file), file, "en")
                        .source().sourceId();
                if (SUPPORTED.contains(file)) {
                    support.add(sourceId);
                }
            }
            var view = bench.workspace("restricted");

            // the unrestricted reading
            Answer reference = bench.askInView(view, QUESTION, AbstractionLevel.INTRODUCTORY,
                    Set.of());
            // the restricted reading, with the WORKSPACE's relevance statistics
            Answer restricted = bench.askInView(view, view, QUESTION, AbstractionLevel.INTRODUCTORY,
                    support, false);
            // the same restricted reading, with the RESTRICTED SET's own relevance statistics —
            // the behaviour the correction replaced
            Answer recomputed = bench.askInView(view, QUESTION, AbstractionLevel.INTRODUCTORY,
                    support, false);

            assertThat(reference.answered()).as("the workspace answers the question").isTrue();
            assertThat(restricted.answered())
                    .as("the restricted reading answers it too: the support carries the evidence")
                    .isTrue();
            assertThat(ids(restricted))
                    .as("the restricted reading cites what the unrestricted reading cites: the "
                            + "restriction changes WHAT MAY BE CITED, not HOW RELEVANT a claim is")
                    .isEqualTo(ids(reference));
            assertThat(ids(restricted))
                    .as("and every citation is the claim of a source the support holds")
                    .allSatisfy(id -> assertThat(support)
                            .contains(claimOf(view, id).introducedBySourceId()));

            // and the discrimination: a reading that recomputes its statistics over the
            // restricted set drops claims the unrestricted reading cited, because the common
            // terms lose more weight than the question's total does
            assertThat(ids(recomputed).size())
                    .as("the reading that recomputes its statistics over the restricted set "
                            + "cites FEWER claims than the unrestricted reading")
                    .isLessThan(ids(reference).size());
            assertThat(ids(recomputed))
                    .as("and it is a SUBSET of the unrestricted reading's citations: the "
                            + "restriction may narrow the answer, never widen it")
                    .isSubsetOf(ids(reference));
        } finally {
            bench.close();
        }
    }

    /** The ids of an answer's claims, sorted. */
    private static List<String> ids(Answer answer) {
        return answer.claims().stream().map(Claim::id).sorted().toList();
    }

    /** The claim of an id. */
    private static Claim claimOf(KnowledgeSnapshot view, String id) {
        for (Claim claim : view.claims()) {
            if (claim.id().equals(id)) {
                return claim;
            }
        }
        throw new IllegalStateException("the view holds no claim " + id);
    }
}
