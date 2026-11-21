package io.matrix.crj.evidence;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The source-span oracle against the SERVED answer of the BUNDLED workspace.
 *
 * <p><b>Why this measurement exists.</b> The oracle was proved against synthetic fixtures, and
 * a fixture cannot tell the owner whether the workspace the system actually serves could ever
 * be scored by it. So this measurement runs the <b>real served path</b> — the report journey
 * over the bundled photosynthesis package — and scores the answer it produces with the
 * oracle, over a corpus built from the workspace's own source documents.</p>
 *
 * <p><b>The corpus.</b> One file per source, holding the source's <b>excerpt</b> — the
 * document text the package carries. That is the shape the protocol's corpus has (§2.1.3.1:
 * a directory, one file per document, with the digest of the imported bytes), and it is the
 * only shape in which the oracle's question is meaningful: a corpus file that held the
 * package's own JSON would contain every proposition by construction, and the check would
 * say nothing.</p>
 *
 * <p><b>What it measures.</b> The oracle asks one question: does the served response cite the
 * evidence the source document <b>actually contains</b>? For the bundled package the answer
 * is <b>no</b>, and for two independent reasons that the measurement separates:</p>
 * <ol>
 *   <li><b>the proposition is a paraphrase.</b> The package carries each document's text as
 *       the source's excerpt, and the claim's own evidence excerpt <b>is</b> a span of it —
 *       but the claim's <b>proposition</b>, which is what the answer cites, is a paraphrase of
 *       that text and never byte-identical to a span of it;</li>
 *   <li><b>the identifier is not the package's key.</b> The answer carries the production
 *       claim id the import derives, not the package's own key, so the gold a case names and
 *       the id the answer cites are two different worlds unless the case names the production
 *       one.</li>
 * </ol>
 *
 * <p><b>What this means.</b> The bundled package <b>cannot</b> be the sealed split's corpus.
 * The protocol's requirement of a <b>new corpus directory</b>, imported through the
 * <b>production acquisition path</b> (§2.1.3.1–2), is not a formality: it is the condition
 * under which the oracle's question is answerable at all. This measurement is what makes that
 * requirement <b>measured</b> rather than assumed.</p>
 *
 * <p><b>What this test asserts.</b> That the oracle <b>disagrees</b> with the served answer —
 * and the exact reason. An oracle that agreed with everything would not be an instrument, so
 * a change that made this test fail would be a change worth reading: either the package
 * changed to carry spans (and then the finding must be rewritten), or the oracle broke (and
 * then the sealed split's measurement is void).</p>
 */
class BundledCorpusSpanMeasurementTest {

    @Test
    @DisplayName("the bundled package's cited propositions are not spans of the documents")
    void theBundledPackageIsAParaphraseCorpus(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        Path corpus = Files.createDirectories(directory.resolve("corpus"));

        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            ArtifactReportResource route = new ArtifactReportResource();
            route.workbench = bench;
            route.actions = new ArtifactActionService(true, root.toString());
            route.planner = new ReportJourneyPlanner();
            route.cognitive = cognitive;

            // the corpus: one file per source, holding the document text the package carries
            for (Source source : bench.workspace(KnowledgeWorkbench.DEFAULT_WORKSPACE).sources()) {
                Files.writeString(corpus.resolve(fileOf(source.id())),
                        source.excerpt() == null ? "" : source.excerpt(),
                        StandardCharsets.UTF_8);
            }

            var response = route.report(KnowledgeWorkbench.DEFAULT_WORKSPACE,
                    new ArtifactReportResource.ReportRequest("What is photosynthesis?", null,
                            true, "key-measurement", null));
            assertThat(response.getStatus()).as("the journey answered").isEqualTo(201);

            var body = (ArtifactReportResource.ReportResponse) response.getEntity();
            assertThat(body.evidence()).as("the answer cited evidence").isNotEmpty();

            SourceSpanOracle oracle = new SourceSpanOracle(corpus);
            List<String> reasons = new ArrayList<>();
            int grounded = 0;
            for (ArtifactReportResource.EvidenceReference cited : body.evidence()) {
                String file = fileOf(cited.sourceId());
                SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase(
                        cited.claimId(), file, oracle.sha256Of(file),
                        List.of(cited.proposition()), List.of(cited.claimId()), true,
                        SourceSpanOracle.Field.PREMISE, "", "");
                SourceSpanOracle.Verdict verdict = oracle.score(gold,
                        new SourceSpanOracle.ServedAnswer(body.disposition(),
                                body.epistemicDisposition(),
                                body.premise() == null ? "" : body.premise().status(),
                                List.of(new SourceSpanOracle.CitedClaim(cited.claimId(),
                                        cited.proposition(), cited.status(), cited.sourceId())),
                                List.of()));
                if (verdict.grounded()) {
                    grounded++;
                } else {
                    reasons.add(verdict.reason());
                }
            }

            assertThat(grounded)
                    .as("the bundled package is a PARAPHRASE corpus: not one cited proposition "
                            + "is a span of its document, so the oracle reports every one of "
                            + "them as ungrounded")
                    .isZero();
            assertThat(reasons).isNotEmpty();
            assertThat(reasons.get(0))
                    .as("and the reason names the paraphrase, so the finding is readable")
                    .contains("does not occur verbatim");
        } finally {
            bench.close();
        }
    }

    @Test
    @DisplayName("the bundled package's claim ids are the production ones, not the package's keys")
    void theBundledPackageCarriesProductionIds(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");

        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            ArtifactReportResource route = new ArtifactReportResource();
            route.workbench = bench;
            route.actions = new ArtifactActionService(true, root.toString());
            route.planner = new ReportJourneyPlanner();
            route.cognitive = cognitive;

            var response = route.report(KnowledgeWorkbench.DEFAULT_WORKSPACE,
                    new ArtifactReportResource.ReportRequest("What is photosynthesis?", null,
                            true, "key-measurement-2", null));
            var body = (ArtifactReportResource.ReportResponse) response.getEntity();

            assertThat(body.evidence()).isNotEmpty();
            for (ArtifactReportResource.EvidenceReference cited : body.evidence()) {
                assertThat(cited.claimId())
                        .as("the answer carries the PRODUCTION claim id, not the package's key")
                        .startsWith("cl-");
            }
        } finally {
            bench.close();
        }
    }

    @Test
    @DisplayName("the oracle DOES ground a proposition that is a span of the document")
    void theOracleGroundsARealSpan(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        Path corpus = Files.createDirectories(directory.resolve("corpus"));

        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            ArtifactReportResource route = new ArtifactReportResource();
            route.workbench = bench;
            route.actions = new ArtifactActionService(true, root.toString());
            route.planner = new ReportJourneyPlanner();
            route.cognitive = cognitive;

            var snapshot = bench.workspace(KnowledgeWorkbench.DEFAULT_WORKSPACE);
            for (Source source : snapshot.sources()) {
                Files.writeString(corpus.resolve(fileOf(source.id())),
                        source.excerpt() == null ? "" : source.excerpt(),
                        StandardCharsets.UTF_8);
            }

            // the POSITIVE CONTROL: the claim's own evidence excerpt IS a span of its
            // document, and the oracle must ground it. Without this, "nothing is grounded"
            // could mean "the oracle is broken" rather than "the propositions are
            // paraphrases".
            Evidence span = snapshot.evidence().stream()
                    .filter(e -> e.excerpt() != null && !e.excerpt().isBlank())
                    .filter(e -> corpus.resolve(fileOf(e.sourceId())).toFile().exists())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("the package carries no "
                            + "evidence excerpt to control the measurement with"));
            String file = fileOf(span.sourceId());
            SourceSpanOracle oracle = new SourceSpanOracle(corpus);
            SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase(span.claimId(), file,
                    oracle.sha256Of(file), List.of(span.excerpt()), List.of(span.claimId()), true,
                    SourceSpanOracle.Field.PREMISE, "", "");
            SourceSpanOracle.Verdict verdict = oracle.score(gold,
                    new SourceSpanOracle.ServedAnswer("WRITTEN", "ANSWERED", "SUPPORTED",
                            List.of(new SourceSpanOracle.CitedClaim(span.claimId(),
                                    span.excerpt(), "SUPPORTED", span.sourceId())),
                            List.of()));

            assertThat(verdict.outcome())
                    .as("the oracle grounds what IS a span - so the measurement above is a "
                            + "property of the package, not of the instrument")
                    .isEqualTo(SourceSpanOracle.Outcome.GROUNDED);
            assertThat(verdict.grounded()).isTrue();
        } finally {
            bench.close();
        }
    }

    /** The corpus file name a source's document is written under. */
    private static String fileOf(String sourceId) {
        return sourceId + ".md";
    }
}
