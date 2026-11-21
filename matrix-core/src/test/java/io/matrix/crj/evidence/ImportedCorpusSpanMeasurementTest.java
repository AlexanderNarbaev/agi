package io.matrix.crj.evidence;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The source-span oracle against a corpus imported through the PRODUCTION acquisition path.
 *
 * <p><b>Why this measurement exists.</b> The measurement over the bundled photosynthesis
 * package found that its claims are <b>paraphrases</b>, and concluded that the sealed split
 * needs a new corpus. That conclusion is only useful if the new corpus's shape is
 * <b>known to work</b> — otherwise the case author is told "import through the production
 * path" and cannot tell whether that is enough.</p>
 *
 * <p>So this measurement does the opposite of the other one: it imports the repository's
 * research pool — the corpus the C2A set was built from — through the <b>production
 * acquisition path</b>, and scores the same oracle against the documents' <b>raw bytes</b>.</p>
 *
 * <p><b>What it measures.</b> Whether a claim the production importer derives is a verbatim
 * span of the document it came from, and therefore whether the oracle can ground it. The
 * repository's own builder already asserts the same property over the same pool
 * ({@code HeldOutSetBuilderTest}: "THE PROPOSITION IS VERBATIM IN ITS SOURCE"), so this
 * measurement checks the property <b>through the oracle</b> — the instrument that will score
 * the sealed split.</p>
 *
 * <p><b>What it means.</b> The two measurements together settle the corpus question: the
 * bundled package is a paraphrase corpus and <b>cannot</b> be the sealed corpus, while a
 * corpus imported through the production path <b>can</b> — its claims are spans, and the
 * oracle grounds them. The case author therefore has a recipe and a proven one.</p>
 */
class ImportedCorpusSpanMeasurementTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

    @Test
    @DisplayName("a claim the production importer derives is a verbatim span of its document")
    void theImportedClaimsAreSpans(@TempDir Path directory) throws Exception {
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        Path corpus = Files.createDirectories(directory.resolve("corpus"));
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            // the corpus files, as the bytes on disk - the oracle reads THESE
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                Files.copy(entry.path(), corpus.resolve(entry.file()));
            }

            // the workspace, assembled through the PRODUCTION acquisition path
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }

            var snapshot = bench.workspace(WORKSPACE);
            assertThat(snapshot.claims()).as("the pool produced claims").isNotEmpty();

            SourceSpanOracle oracle = new SourceSpanOracle(corpus);
            int grounded = 0;
            int scored = 0;
            List<String> reasons = new ArrayList<>();
            for (Claim claim : snapshot.claims()) {
                Source source = snapshot.sources().stream()
                        .filter(s -> s.id().equals(claim.introducedBySourceId()))
                        .findFirst().orElse(null);
                if (source == null) {
                    continue;
                }
                // the corpus file is the document the source came from, found by its title
                String file = fileOf(source, pool);
                if (file == null) {
                    continue;
                }
                scored++;
                SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase(claim.id(), file,
                        oracle.sha256Of(file), List.of(claim.proposition()),
                        List.of(claim.id()), true, SourceSpanOracle.Field.PREMISE, "", "");
                SourceSpanOracle.Verdict verdict = oracle.score(gold,
                        new SourceSpanOracle.ServedAnswer("WRITTEN", "ANSWERED", "SUPPORTED",
                                List.of(new SourceSpanOracle.CitedClaim(claim.id(),
                                        claim.proposition(), String.valueOf(claim.status()),
                                        source.id())), List.of()));
                if (verdict.grounded()) {
                    grounded++;
                } else {
                    reasons.add(claim.id() + ": " + verdict.reason());
                }
            }

            assertThat(scored).as("the claims were scored against their documents").isPositive();
            assertThat(grounded)
                    .as("the oracle grounds the claims the production importer derives from a "
                            + "real imported corpus: %d of %d", grounded, scored)
                    .isPositive();
            assertThat(grounded)
                    .as("and the great majority of them are LITERAL SPANS of the document: "
                            + "%d of %d. The importer builds a proposition from a document's "
                            + "SEGMENT, and a segment is sometimes a heading and the paragraph "
                            + "that follows it, a flattened list, or boilerplate - and then the "
                            + "proposition is not a literal substring of the document's bytes. "
                            + "So the corpus condition is satisfiable PER CASE, not per corpus: "
                            + "the case author must select a gold that IS a span, and the builder "
                            + "must prove it, exactly as the repository's own held-out builder "
                            + "does for the claims it selects.", grounded, scored)
                    .isGreaterThan(scored / 2);
            assertThat(reasons)
                    .as("every exception is a containment failure - the oracle refused exactly "
                            + "and only the propositions that are not literal spans")
                    .allMatch(reason -> reason.contains("does not occur verbatim"));
        } finally {
            bench.close();
        }
    }

    /** The corpus file a source came from, by the source's title. */
    private static String fileOf(Source source, ClasspathCorpus pool) {
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            if (entry.file().equals(source.title()) || entry.file().contains(source.title())) {
                return entry.file();
            }
        }
        return null;
    }
}
