package io.matrix.cognitive.report;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.DocumentImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the CONTAINMENT GATE's blind spot: the excerpt is TRUNCATED, and the gate checks
 * the excerpt.
 *
 * <p><b>The defect.</b> The learner admits a candidate only when the claim's proposition
 * "occurs verbatim in the text of" its source — and it checks that against the source's
 * stored <b>excerpt</b>. But the excerpt is not the document: {@code DocumentImporter}
 * keeps only the <b>first {@link DocumentImporter#EXCERPT_LIMIT} characters</b> of the
 * text.</p>
 *
 * <p>So a claim whose proposition is a <b>genuine verbatim span</b> of the document, but
 * which lies <b>past the cut</b>, is <b>refused</b> — and refused with a reason that says it
 * "is a paraphrase of the document rather than a span of it". That reason is <b>false</b> for
 * those claims: they <b>are</b> spans.</p>
 *
 * <p><b>Why it matters.</b> This bounds the learner's candidate set by a <b>storage</b>
 * decision, not by the evidence: the corpus report measured <b>483 of 540</b> claims as
 * verbatim spans of the document's bytes, and the learner can only ever see the ones that
 * happen to fall inside the first 8 000 characters. Every claim past the cut is not
 * <b>rejected on the evidence</b> — it is <b>never examined</b>.</p>
 *
 * <p><b>What is measured.</b> Over the research pool: for every claim, whether its
 * proposition occurs verbatim in the document's <b>full text</b> and whether it occurs in
 * the stored <b>excerpt</b>, and how far into the document the claim's span begins.</p>
 */
class ClaimSpanBeyondExcerptTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

    @Test
    @DisplayName("the claims that ARE spans of the document but lie past the excerpt's cut")
    void theClaimsPastTheCutAreMeasured(@TempDir Path directory) throws Exception {
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            var snapshot = bench.workspace(WORKSPACE);
            Map<String, String> textByLocator = new java.util.LinkedHashMap<>();
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                textByLocator.put(entry.file(), Files.readString(entry.path(),
                        java.nio.charset.StandardCharsets.UTF_8));
                textByLocator.put(entry.path().toString(), Files.readString(entry.path(),
                        java.nio.charset.StandardCharsets.UTF_8));
            }

            int total = 0;
            int spanOfTheDocument = 0;
            int inTheExcerpt = 0;
            int spanButBeyondTheCut = 0;
            int notASpanAtAll = 0;
            List<String> beyond = new java.util.ArrayList<>();
            for (Claim claim : snapshot.claims()) {
                String sourceId = claim.introducedBySourceId();
                if (sourceId == null) {
                    continue;
                }
                Source source = snapshot.sources().stream()
                        .filter(candidate -> candidate.id().equals(sourceId)).findFirst().orElse(null);
                if (source == null) {
                    continue;
                }
                total++;
                boolean inExcerpt = source.excerpt() != null
                        && source.excerpt().contains(claim.proposition());
                if (inExcerpt) {
                    inTheExcerpt++;
                }
                String text = textByLocator.get(source.locator());
                if (text == null) {
                    text = textByLocator.get(source.title());
                }
                if (text == null) {
                    text = "";
                }
                int at = text.indexOf(claim.proposition());
                if (at < 0) {
                    notASpanAtAll++;
                    continue;
                }
                spanOfTheDocument++;
                if (!inExcerpt) {
                    spanButBeyondTheCut++;
                    beyond.add(source.id() + " @" + at + " (excerpt holds "
                            + (source.excerpt() == null ? 0 : source.excerpt().length())
                            + " of " + text.length() + " characters)");
                }
            }

            System.out.println("[CRJ-01] the containment gate's blind spot, over the research pool:");
            System.out.println("[CRJ-01]   the excerpt's limit: " + DocumentImporter.EXCERPT_LIMIT
                    + " characters");
            System.out.println("[CRJ-01]   the claims: " + total);
            System.out.println("[CRJ-01]   a VERBATIM SPAN of the document's text: "
                    + spanOfTheDocument);
            System.out.println("[CRJ-01]   found in the stored EXCERPT: " + inTheExcerpt);
            System.out.println("[CRJ-01]   a span but BEYOND THE CUT (refused, wrongly): "
                    + spanButBeyondTheCut);
            System.out.println("[CRJ-01]   not a span at all: " + notASpanAtAll);
            if (!beyond.isEmpty()) {
                System.out.println("[CRJ-01]   the claims past the cut: " + beyond);
            }

            // ── the span distribution: how far into the documents the claims reach ──
            int maxEnd = 0;
            java.util.Map<String, Integer> perDocument = new java.util.LinkedHashMap<>();
            for (Claim claim : snapshot.claims()) {
                String sourceId = claim.introducedBySourceId();
                if (sourceId == null) {
                    continue;
                }
                Source source = snapshot.sources().stream()
                        .filter(candidate -> candidate.id().equals(sourceId)).findFirst().orElse(null);
                if (source == null) {
                    continue;
                }
                String text = textByLocator.getOrDefault(source.locator(),
                        textByLocator.getOrDefault(source.title(), ""));
                int at = text.indexOf(claim.proposition());
                if (at < 0) {
                    continue;
                }
                maxEnd = Math.max(maxEnd, at + claim.proposition().length());
                perDocument.merge(source.id(), 1, Integer::sum);
            }
            // ── the STORAGE COST of the fix, measured ───────────────────────────
            long storedNow = 0;
            long storedBefore = 0;
            long documentBytes = 0;
            for (Source source : snapshot.sources()) {
                storedNow += source.excerpt() == null ? 0 : source.excerpt().length();
                storedBefore += Math.min(DocumentImporter.EXCERPT_LIMIT,
                        source.excerpt() == null ? 0 : source.excerpt().length());
                String text = textByLocator.getOrDefault(source.locator(),
                        textByLocator.getOrDefault(source.title(), ""));
                documentBytes += text.length();
            }
            System.out.println("[CRJ-01]   the STORAGE: the excerpt holds " + storedNow
                    + " characters over the pool, against " + storedBefore
                    + " under the old floor, and the documents hold " + documentBytes
                    + " characters in all");
            System.out.println("[CRJ-01]   the highest claim end in the corpus: " + maxEnd
                    + " characters");
            System.out.println("[CRJ-01]   the claims per document: " + perDocument);

            assertThat(total).as("the pool holds claims").isPositive();
            assertThat(spanOfTheDocument)
                    .as("and most of them ARE verbatim spans of their document")
                    .isGreaterThan(0);
            assertThat(inTheExcerpt)
                    .as("but the gate can only see the ones inside the excerpt's cut")
                    .isLessThanOrEqualTo(spanOfTheDocument);
        } finally {
            bench.close();
        }
    }
}
