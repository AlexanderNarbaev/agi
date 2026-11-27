package io.matrix.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
import io.matrix.cognitive.report.ReportProcedure;
import io.matrix.cognitive.report.ReportProcedureLearner;
import io.matrix.cognitive.report.ReportProcedureMethod;
import io.matrix.crj.evidence.SourceSpanOracle;
import io.matrix.security.NetworkExposure;
import io.matrix.testsupport.RepositoryPaths;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / ADAPTIVE-COMPETENCE — the SERVED gate on the question-specific applicability of a
 * learned procedure, and the milestone's independent deterministic supervisor gate.
 *
 * <p><b>What this milestone is about.</b> The H2 milestone served a learned, versioned
 * procedure for a question its support set could not answer, and measured the cost of that:
 * on the two out-of-support controls the served answer was the restricted reading — one claim
 * where the reference cited three, and an abstention where the reference answered — while the
 * procedure's applicability was the <b>presence</b> of its support set, a statement about the
 * workspace and not about the request. This milestone asks the question H2's finding points
 * at: <b>can a procedure decide, from the EVIDENCE and for THIS question, whether it may
 * answer?</b></p>
 *
 * <p><b>What is measured here.</b> The <b>real served route</b> — {@link ArtifactReportResource}
 * → the planner → the kernel's selector → the method → the bounded writer → an independent
 * reader → the trace. No method is forced in the served arm: the kernel chooses on its own
 * evidence, and the trace says what it chose and why. The corpus is <b>new and synthetic</b>
 * (two domains on two different technical subjects), the cases were authored by an
 * <b>independent case author</b> from the production extraction's own report, and the oracle
 * re-reads the corpus bytes and never calls the retriever.</p>
 *
 * <p><b>What is asserted.</b> The <b>parity</b> of the served path with the stateless
 * reference on every measured case — the served answer cites the reference's evidence, or the
 * procedure was not applicable and the reference answered — and the <b>fallback</b>: the
 * refusal happens before any durable side effect, the reason is recorded with the evidence it
 * was made from, and a procedure that is forced outside its domain is refused rather than
 * served. The timings are <b>measured and never asserted</b>: they are machine state, and a
 * fast answer is not evidence of competence.</p>
 *
 * <p><b>What is NOT claimed.</b> This is a <b>DEV</b> measurement on the solver's own split.
 * The <b>sealed evaluation is NOT EXECUTED</b>: the isolation a sealed split needs — an
 * implementer that cannot read the gold and an evaluator that cannot read the solver — was
 * inspected with synthetic canaries and <b>could not be established from this executor</b>
 * (one non-privileged account, no second identity, no usable namespace, no confinement of a
 * same-uid process, and a harness sandbox that is a write perimeter only). Nothing here
 * supports a claim about a sealed δ_MI.</p>
 */
class AdaptiveCompetenceServedTest {

    /** The classpath directory the adaptive corpora live in. */
    private static final String CORPUS_RESOURCE = "/crj01-adaptive/";

    /** The split the cases are read from. */
    private static final String SPLIT = "docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json";

    /** The corpus report the split's gold was written against. */
    private static final String CORPUS_REPORT =
            "docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json";

    /**
     * The retained evidence directory — the CANONICAL home of this milestone's row
     * artefacts.
     *
     * <p><b>Why the rows are written there and not in the engineering root.</b> A second
     * copy of the same rows in the engineering root is a copy that can be stale: the gate
     * reads one of them, a reader opens the other, and the two disagree without anyone
     * having done anything wrong. The retained directory holds the rows, the manifest
     * covers them, and the gate reads them where they are.</p>
     */
    private static final String EVIDENCE_DIRECTORY =
            "docs-v2/engineering/evidence/crj01-adaptive-competence";

    /** The instant the promotions are recorded with — supplied, never read from a clock. */
    private static final String PROMOTED_AT = "2026-11-27T00:00:00Z";

    /** The workspace a domain's corpus is imported into. */
    private static final String WORKSPACE_PREFIX = "crj01-adaptive-";

    /** The number of bootstrap draws of the paired interval. */
    private static final int BOOTSTRAP_DRAWS = 2000;

    /** The seed of the bootstrap. The pre-registration demands it be named in the evidence. */
    private static final long BOOTSTRAP_SEED = 20261127L;

    /** The instant a competence revision is recorded with — supplied, never read from a clock. */
    private static final String REVISION_PROMOTED_AT = "2026-11-27T00:00:01Z";

    @TempDir
    Path directory;

    /** The workbench this test opened. */
    private KnowledgeWorkbench bench;

    /** The kernel's service — closed and reopened by the restart. */
    private CognitiveService cognitive;

    /** The route, wired by hand, the way the container wires it. */
    private ArtifactReportResource route;

    /** The corpus, as the bytes on disk. */
    private Path corpusDirectory;

    /** The output root the action writes into. */
    private Path outputRoot;

    /** The cases of the split. */
    private List<Case> cases;

    /** The domains of the split, in the order the split names them. */
    private List<String> domains;

    /** The episodes of each domain's learning episode. */
    private final Map<String, List<String>> episodes = new LinkedHashMap<>();

    /** The promotion outcome of each domain. */
    private final Map<String, ReportProcedureLearner.Outcome> promotions = new LinkedHashMap<>();

    /** The corpus report, read once. */
    private Map<String, Map<String, Object>> corpusReportCache;

    /** The REFUSED attempt at each domain's revision — the one that held the proposed case. */
    private final Map<String, ReportProcedureLearner.Outcome> refusedOutcomes =
            new LinkedHashMap<>();

    /** The revision experiment of each domain — its own workspace, before and after. */
    private final Map<String, RevisionExperiment> revisionExperiments = new LinkedHashMap<>();

    /** The cost of the PROMOTION and the VALIDATION, per domain — NOT part of the
     * per-request timings of the served path. */
    private final Map<String, Map<String, Long>> promotionValidationCosts = new LinkedHashMap<>();

    // ── the fixture ────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
        cases = readCases();
        domains = domainsOf();
        // the corpus is laid out FLAT, and the oracle reads the same layout: a file's name
        // is what the split names, and the digest is the digest of those bytes
        corpusDirectory = Files.createDirectories(directory.resolve("corpus"));
        for (String domain : domains) {
            for (String file : filesOf(domain)) {
                Files.write(corpusDirectory.resolve(file), corpusBytes(domain, file));
            }
        }
        bench = KnowledgeWorkbench.at(Files.createDirectories(directory.resolve("db"))
                .resolve("workbench.sqlite").toString());
        for (String domain : domains) {
            for (String file : filesOf(domain)) {
                bench.importDocument(workspaceOf(domain), corpusDirectory.resolve(file),
                        file, "en");
            }
        }
        outputRoot = Files.createDirectories(directory.resolve("output"));
        Files.setPosixFilePermissions(outputRoot, java.nio.file.attribute.PosixFilePermissions
                .fromString("rwx------"));
        cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route = route(bench, cognitive, outputRoot, new ReportJourneyPlanner());

        // ── THE LEARNING EPISODE — the product answers, and the answer is kept ──
        for (String domain : domains) {
            List<String> observed = new ArrayList<>();
            for (Case testCase : subset(domain, "OBSERVED")) {
                ArtifactReportResource.JourneyResult result = route.journey(workspaceOf(domain),
                        request(testCase, "observed"), ReportJourneyMethod.REFERENCE_ID);
                assertThat(result.response().getStatus())
                        .as("the learning episode's journey was served: %s", testCase.id())
                        .isEqualTo(201);
                observed.add(ReportJourneyTrace.episodeId(result.outcome().taskId()));
            }
            episodes.put(domain, observed);

            // ── THE PROMOTION — on cases the procedure was NOT proposed from ────
            List<ReportProcedureLearner.Observation> observations =
                    ReportProcedureLearner.observationsFrom(cognitive.store(), observed);
            assertThat(observations)
                    .as("the retained journeys carry the observations the procedure is learned from")
                    .hasSize(observed.size());
            ReportProcedureLearner.Outcome outcome = new ReportProcedureLearner(bench,
                    cognitive.store()).learn(workspaceOf(domain), observations,
                    validationCases(domain), Set.of(), PROMOTED_AT);
            promotions.put(domain, outcome);
        }

        // ── THE RESTART — a competence must outlive the process that learned it ─
        cognitive.store().close();
        cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route = route(bench, cognitive, outputRoot, new ReportJourneyPlanner());
    }

    @AfterEach
    void tearDown() {
        NetworkExposure.reset();
        if (cognitive != null) {
            cognitive.store().close();
        }
        if (bench != null) {
            bench.close();
        }
    }

    // ── the acceptance ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("every measured case is answered with the reference's own evidence, and the "
            + "served path never answers where the procedure cannot")
    void theServedPathNeverAnswersWhereTheProcedureCannot() {
        for (String domain : domains) {
            for (Case testCase : measured(domain)) {
                ArtifactReportResource.JourneyResult served = route.journey(workspaceOf(domain),
                        request(testCase, "served"), null);
                assertThat(served.response().getStatus())
                        .as("the served journey answered: %s", testCase.id())
                        .isEqualTo(201);
                ArtifactReportResource.JourneyResult reference = route.journey(workspaceOf(domain),
                        request(testCase, "reference"), ReportJourneyMethod.REFERENCE_ID);
                assertThat(reference.response().getStatus())
                        .as("the reference journey answered: %s", testCase.id())
                        .isEqualTo(201);

                String method = served.outcome().methodId();
                boolean procedureServed = method.startsWith("procedure.");
                if (procedureServed) {
                    // the procedure answered: its evidence must be GROUNDED, and the gate
                    // must have found the support set adequate for this question
                    assertThat(grounded(testCase, domain, served.response()))
                            .as("the procedure's answer is grounded: %s", testCase.id())
                            .isTrue();
                } else {
                    // the procedure was not applicable: the reference answered, and the
                    // served evidence is the reference's own — the fallback is a fallback to
                    // the SAME answer, not to a worse one
                    assertThat(method)
                            .as("the fallback is the reference method: %s", testCase.id())
                            .isEqualTo(ReportJourneyMethod.REFERENCE_ID);
                    assertThat(citedOf(served.response()))
                            .as("the served evidence is the reference's own: %s", testCase.id())
                            .isEqualTo(citedOf(reference.response()));
                    assertThat(grounded(testCase, domain, served.response()))
                            .as("the fallback's answer is grounded: %s", testCase.id())
                            .isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("a question the support set cannot answer falls back to the reference, and the "
            + "reason names the evidence")
    void anUnsupportedQuestionFallsBackWithItsReason() {
        for (String domain : domains) {
            List<Case> outOfSupport = subset(domain, "OUT_OF_SUPPORT");
            assertThat(outOfSupport)
                    .as("the split carries the out-of-support controls of %s", domain)
                    .isNotEmpty();
            for (Case testCase : outOfSupport) {
                ArtifactReportResource.JourneyResult served = route.journey(workspaceOf(domain),
                        request(testCase, "served"), null);
                assertThat(served.outcome().methodId())
                        .as("the procedure is not applicable to an out-of-support question: %s",
                                testCase.id())
                        .isEqualTo(ReportJourneyMethod.REFERENCE_ID);
                String plan = planOf(served.outcome().taskId());
                assertThat(plan)
                        .as("the plan records WHY the procedure was not selected: %s", testCase.id())
                        .contains("was not applicable to this question");
                assertThat(body(served.response()).limitations())
                        .as("the caller is told why: %s", testCase.id())
                        .anySatisfy(limitation -> assertThat(limitation)
                                .contains("was not applicable to this question"));
            }
        }
    }

    @Test
    @DisplayName("a procedure forced outside its domain is refused, and no artifact is written")
    void aForcedProcedureOutsideItsDomainIsRefused() {
        for (String domain : domains) {
            Procedure procedure = promotedProcedure(domain);
            if (procedure == null) {
                // no procedure was promoted for this domain; the refusal cannot be exercised
                // on one that does not exist, and the reason is recorded in the evidence
                continue;
            }
            for (Case testCase : subset(domain, "OUT_OF_SUPPORT")) {
                ArtifactReportResource.JourneyResult refused = route.journey(workspaceOf(domain),
                        request(testCase, "forced"),
                        new ReportProcedureMethod(procedure).capability().id());
                assertThat(refused.response().getStatus())
                        .as("a procedure outside its domain is refused: %s", testCase.id())
                        .isEqualTo(422);
                String detail = refusalDetail(refused.response());
                assertThat(detail)
                        .as("the refusal says why")
                        .isNotBlank();
                assertThat(detail)
                        .as("the refusal names the EVIDENCE, not a drift of the workspace")
                        .contains("does not carry the workspace's own evidence")
                        .contains("coverage");
                assertThat(artifactsOf(refused.outcome().taskId()))
                        .as("no artifact was written for the refused journey: %s", testCase.id())
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("the gate's best claim is the claim the reference reading cites first")
    void theGateAndTheReadingAgreeOnTheBestClaim() {
        // The gate compares numbers the retrieval produced; the reading produces its own.
        // If the two disagreed, the gate's verdict would be made of a different measurement
        // than the one the answer is built from — which is the defect class the H2 milestone
        // was about. The check is exact: the best claim IS the first claim the reading cites.
        for (String domain : domains) {
            for (Case testCase : measured(domain)) {
                KnowledgeSnapshot view = bench.workspace(workspaceOf(domain));
                SupportAdequacy adequacy = bench.assessSupport(view, view, testCase.question(),
                        AbstractionLevel.INTRODUCTORY, Set.of(), false);
                Answer answer = bench.askInView(view, testCase.question(),
                        AbstractionLevel.INTRODUCTORY, Set.of());
                if (adequacy.bestClaimId() == null) {
                    // No claim reaches the floor — and the reading may STILL cite claims,
                    // because the concept bridge reaches a concept's claims regardless of
                    // their own coverage. The gate says so: "the workspace's own reading of
                    // this question reaches no claim that may be cited, so no support set
                    // can be shown to carry its evidence" — and the VERDICT is that the
                    // procedure does not hold, not that the reading is empty.
                    assertThat(adequacy.applicable())
                            .as("a question that reaches no claim cannot be answered by any "
                                    + "support set: %s", testCase.id())
                            .isFalse();
                    assertThat(adequacy.reason())
                            .as("the verdict says why: %s", testCase.id())
                            .isNotBlank();
                    continue;
                }
                assertThat(answer.claims())
                        .as("the reading cites the claim the gate found best: %s", testCase.id())
                        .isNotEmpty();
                assertThat(answer.claims().get(0).id())
                        .as("the gate's best claim is the reading's first claim: %s", testCase.id())
                        .isEqualTo(adequacy.bestClaimId());
            }
        }
    }

    @Test
    @DisplayName("the statistics of a view are reused, and a changed view invalidates them")
    void theStatisticsAreReusedAndInvalidated() {
        for (String domain : domains) {
            String workspace = workspaceOf(domain);
            KnowledgeSnapshot view = bench.workspace(workspace);
            KnowledgeStatistics first = bench.statisticsOf(workspace, view);
            KnowledgeStatistics second = bench.statisticsOf(workspace, view);
            assertThat(second)
                    .as("the statistics of an unchanged view are the SAME object: the pass over "
                            + "the claims is made once")
                    .isSameAs(first);

            // an UNCHANGED view has the same identity: the identity is a property of the
            // view's content, not of the moment it was read
            KnowledgeSnapshot unchanged = bench.workspace(workspace);
            assertThat(bench.statisticsOf(workspace, unchanged).viewIdentity())
                    .as("the identity is a property of the view's content")
                    .isEqualTo(first.viewIdentity());

            // a restricted view's statistics are a subset of the whole, and carry the whole's
            // weights: the restriction must not change how relevant a claim is
            Set<String> subsetIds = new LinkedHashSet<>();
            for (Claim claim : view.claims()) {
                if (subsetIds.size() >= 2) {
                    break;
                }
                subsetIds.add(claim.id());
            }
            KnowledgeStatistics restricted = first.restrictedTo(subsetIds);
            assertThat(restricted.claimCount())
                    .as("the restricted statistics hold the restricted claims")
                    .isEqualTo(subsetIds.size());
            assertThat(restricted.idf())
                    .as("the weights are the workspace's own: the restriction decides what may "
                            + "be cited, not how relevant a claim is")
                    .isEqualTo(first.idf());
            for (String claimId : subsetIds) {
                assertThat(restricted.termsOf(claimId))
                        .as("the terms of a kept claim are the terms it already had")
                        .isEqualTo(first.termsOf(claimId));
            }
        }
    }

    @Test
    @DisplayName("a changed document invalidates the reused statistics, and the next request "
            + "pays the cold cost again")
    void aChangedDocumentInvalidatesTheReusedStatistics() throws Exception {
        String domain = domains.get(0);
        String workspace = workspaceOf(domain);
        KnowledgeStatistics before = bench.statisticsOf(workspace, bench.workspace(workspace));
        assertThat(bench.statisticsOf(workspace, bench.workspace(workspace)))
                .as("the statistics are reused while the view does not change")
                .isSameAs(before);

        // ── THE CHANGE — a revised document, through the production revision path ──
        String file = filesOf(domain).get(0);
        io.matrix.knowledge.Source superseded = sourceOf(domain, file);
        String documentKey = "ac-document-" + domain + "-" + file;
        bench.selectSourceRevision(new io.matrix.knowledge.SourceRevisionSelection(
                "ac-revision-1", workspace, documentKey,
                superseded.id(), superseded.contentHash(), null));
        Path revised = directory.resolve("revised-" + domain + "-" + file);
        Files.writeString(revised, Files.readString(corpusDirectory.resolve(file))
                + "\nThe revised document adds one further observation about its subject.\n",
                StandardCharsets.UTF_8);
        var imported = bench.importDocument(workspace, revised, file, "en").source();
        bench.selectSourceRevision(new io.matrix.knowledge.SourceRevisionSelection(
                "ac-revision-2", workspace, documentKey,
                imported.sourceId(), imported.contentHash(), "ac-revision-1"));

        // ── THE INVALIDATION ──
        KnowledgeSnapshot after = bench.workspace(workspace);
        KnowledgeStatistics refreshed = bench.statisticsOf(workspace, after);
        assertThat(refreshed)
                .as("the statistics of the older view are never served for the newer one")
                .isNotSameAs(before);
        assertThat(refreshed.viewIdentity())
                .as("a changed document gives the view another identity")
                .isNotEqualTo(before.viewIdentity());
        assertThat(bench.statisticsOf(workspace, bench.workspace(workspace)))
                .as("the refreshed statistics are reused in their turn")
                .isSameAs(refreshed);
    }

    @Test
    @DisplayName("a revision that changes the bytes but not the sentences still invalidates "
            + "the reused statistics")
    void aRevisionThatKeepsTheSentencesStillInvalidatesTheStatistics() throws Exception {
        String domain = domains.get(0);
        String workspace = workspaceOf(domain);
        // the SELECTED view — the projection the served path reads, not the archive
        KnowledgeSnapshot before = io.matrix.knowledge.SourceRevisionView.project(
                bench.workspace(workspace), null);
        KnowledgeStatistics beforeStatistics = bench.statisticsOf(workspace, before);
        List<String> propositionsBefore = before.claims().stream()
                .map(Claim::proposition).sorted().toList();
        List<String> idsBefore = before.claims().stream().map(Claim::id).sorted().toList();

        // ── THE REVISION — the BYTES change and the SENTENCES do not ──
        // A production claim id is derived from the document's content hash, so the ids change
        // while the propositions stay the same. The statistics are indexed BY the ids, so an
        // identity over the propositions alone would serve the older view's statistics for the
        // newer one, and every lookup of a new id would miss: the reading would score no
        // claim at all, and nothing would say so.
        String file = filesOf(domain).get(0);
        io.matrix.knowledge.Source superseded = sourceOf(domain, file);
        String documentKey = "ac-bytes-" + domain + "-" + file;
        bench.selectSourceRevision(new io.matrix.knowledge.SourceRevisionSelection(
                "ac-bytes-revision-1", workspace, documentKey,
                superseded.id(), superseded.contentHash(), null));
        Path revised = directory.resolve("bytes-" + domain + "-" + file);
        Files.writeString(revised,
                Files.readString(corpusDirectory.resolve(file)) + "\n<!-- formatting only -->\n",
                StandardCharsets.UTF_8);
        var imported = bench.importDocument(workspace, revised, file, "en").source();
        bench.selectSourceRevision(new io.matrix.knowledge.SourceRevisionSelection(
                "ac-bytes-revision-2", workspace, documentKey,
                imported.sourceId(), imported.contentHash(), "ac-bytes-revision-1"));

        KnowledgeSnapshot after = io.matrix.knowledge.SourceRevisionView.project(
                bench.workspace(workspace), null);
        List<String> propositionsAfter = after.claims().stream()
                .map(Claim::proposition).sorted().toList();
        List<String> idsAfter = after.claims().stream().map(Claim::id).sorted().toList();
        assertThat(idsAfter)
                .as("the claim ids carry the document's content hash, so they changed")
                .isNotEqualTo(idsBefore);
        List<String> fresh = idsAfter.stream().filter(id -> !idsBefore.contains(id)).toList();
        assertThat(fresh)
                .as("the revised document's claims are new claims")
                .isNotEmpty();

        KnowledgeStatistics afterStatistics = bench.statisticsOf(workspace, after);
        assertThat(afterStatistics)
                .as("the statistics of the older view are never served for the newer one")
                .isNotSameAs(beforeStatistics);
        assertThat(afterStatistics.viewIdentity())
                .as("the identity covers the claim's identifier, not only its proposition")
                .isNotEqualTo(beforeStatistics.viewIdentity());

        // ── THE IDENTITY CONTRACT, DIRECTLY ──
        // The production path above does not always produce the shape that bites — a
        // revision that replaces a document may also change the claim COUNT, and the
        // identity moves with it. The contract is therefore asserted directly: two views
        // that hold the same PROPOSITIONS under different IDENTIFIERS are different views,
        // because the statistics are indexed by the identifier.
        KnowledgeSnapshot renamed = withRenamedClaimIds(after);
        assertThat(renamed.claims().stream().map(Claim::proposition).sorted().toList())
                .as("the renamed view holds the same propositions")
                .isEqualTo(after.claims().stream().map(Claim::proposition).sorted().toList());
        assertThat(KnowledgeStatistics.identityOf(renamed))
                .as("the identity covers the claim's identifier, not only its proposition")
                .isNotEqualTo(KnowledgeStatistics.identityOf(after));
        for (Claim claim : renamed.claims()) {
            assertThat(KnowledgeStatistics.of(renamed).termsOf(claim.id()))
                    .as("the statistics of the renamed view are indexed by ITS OWN ids")
                    .isNotEmpty();
        }

        // ── THE SILENT LOSS, WITNESSED DIRECTLY ──
        // The defect an identity over the propositions alone would carry is that the older
        // view's statistics are served for the newer one: their index holds the OLDER ids,
        // every lookup of a new id misses, and the reading scores NO CLAIM — a silent loss
        // of the whole evidence, which is worse than a stale number because nothing says so.
        for (String claimId : fresh) {
            assertThat(beforeStatistics.termsOf(claimId))
                    .as("the older statistics do not cover the new claim: %s", claimId)
                    .isEmpty();
            assertThat(afterStatistics.termsOf(claimId))
                    .as("the refreshed statistics cover the new claim: %s", claimId)
                    .isNotEmpty();
        }

        // ── AND THE READING STILL FINDS ITS EVIDENCE ──
        // The defect this test witnesses is SILENT: the reading would score no claim and
        // would say nothing about it. The reading must still cite the evidence.
        Case control = subset(domain, "OBSERVED").get(0);
        ArtifactReportResource.JourneyResult served = route.journey(workspace,
                request(control, "bytes-revision"), null);
        assertThat(served.response().getStatus()).isEqualTo(201);
        assertThat(citedOf(served.response()))
                .as("the reading after the revision still cites its evidence")
                .isNotEmpty();
        assertThat(grounded(control, domain, workspace, served.response()))
                .as("the reading after the revision is still grounded")
                .isTrue();
    }

    /**
     * A view whose claims carry new identifiers over the same propositions.
     *
     * @param view the view
     * @return the renamed view
     */
    private KnowledgeSnapshot withRenamedClaimIds(KnowledgeSnapshot view) {
        List<Claim> renamed = view.claims().stream()
                .map(claim -> new Claim("clm-renamed-" + claim.id().substring(4),
                        claim.workspaceId(), claim.conceptId(), claim.proposition(),
                        claim.scope(), claim.context(), claim.status(), claim.confidence(),
                        claim.version(), claim.introducedBySourceId(), claim.extractionMethod(),
                        claim.validationMethod(), claim.warrant(), claim.derivationStatus(),
                        claim.createdAt(), claim.updatedAt()))
                .toList();
        return new KnowledgeSnapshot(view.workspaceId(), view.workspaceName(), view.concepts(),
                view.sources(), renamed, view.evidence(), view.representations(),
                view.prerequisites(), view.revisions(), view.derivations(),
                view.contradictions(), view.goals(), view.sourceRevisionSelections(),
                view.exportedAt());
    }

    /**
     * The source a document was imported as.
     *
     * @param domain the domain
     * @param file   the document's file name
     * @return the source
     */
    private io.matrix.knowledge.Source sourceOf(String domain, String file) {
        for (io.matrix.knowledge.Source source : bench.workspace(workspaceOf(domain)).sources()) {
            if (source.locator() != null && source.locator().endsWith("/" + file)) {
                return source;
            }
            if (file.equals(source.title())) {
                return source;
            }
        }
        throw new IllegalStateException("the source of " + file + " is not in the workspace");
    }

    @Test
    @DisplayName("the distinct subsequent task is DISJOINT from the observations, the failure "
            + "question and the validation")
    void theDistinctSubsequentTaskIsDisjoint() {
        // The claim of the milestone is that the changed competence is measured on a
        // DISTINCT subsequent task. "Distinct" is not a word: it is a property of the
        // split, and this test is where the property is checked — the gold of the
        // subsequent task belongs to NO other case, and it is a claim of the failure
        // episode's own DOCUMENT, so that the revision's support can carry it.
        for (String domain : domains) {
            List<Case> subsequent = subset(domain, "SUBSEQUENT");
            assertThat(subsequent)
                    .as("the domain carries exactly one distinct subsequent task")
                    .hasSize(1);
            Case task = subsequent.get(0);
            List<Case> others = new ArrayList<>();
            for (Case other : cases) {
                if (other.domain().equals(domain) && !other.id().equals(task.id())) {
                    others.add(other);
                }
            }
            for (Case other : others) {
                for (String gold : task.goldPropositions()) {
                    assertThat(other.goldPropositions())
                            .as("the distinct subsequent task's gold is not another case's "
                                    + "gold: %s against %s", task.id(), other.id())
                            .doesNotContain(gold);
                }
            }
            Case failure = subset(domain, "OUT_OF_SUPPORT").get(0);
            assertThat(task.corpusFile())
                    .as("the distinct subsequent task is a claim of the failure episode's own "
                            + "document")
                    .isEqualTo(failure.corpusFile());
            assertThat(task.id())
                    .as("the distinct subsequent task is not the failure question")
                    .isNotEqualTo(failure.id());
            assertThat(task.subset())
                    .as("the distinct subsequent task is a case of its own subset")
                    .isEqualTo("SUBSEQUENT");
        }
    }

    @Test
    @DisplayName("the rejected tie rule is WITNESSED: it accepts a support set that holds the "
            + "wrong claim, and the kept rule refuses it")
    void theRejectedTieRuleIsWitnessed() {
        // THE REJECTED ALTERNATIVE, WITNESSED. The first criterion accepted a support set
        // whose best claim reached EXACTLY the workspace's best coverage — but a DIFFERENT
        // claim. A tie in the number is not a tie in the evidence: the restricted answer
        // would be anchored on other evidence while looking equally relevant, which is the
        // H2 degradation.
        //
        // The witness is CONSTRUCTED, not remembered: a term that exactly two claims of two
        // different documents hold makes the two claims reach the same coverage, so a
        // support set that holds ONE of them ties the workspace's best coverage while
        // holding a claim the workspace's own reading would not cite first.
        for (String domain : domains) {
            assertThat(tieRuleWitnessOf(domain))
                    .as("the rejected tie rule is witnessed on a real corpus: %s", domain)
                    .isNotNull();
        }
    }

    /**
     * The witness of the rejected tie rule for a domain.
     *
     * <p>It is computed once and kept, so that the evidence artefact carries the same
     * witness the test asserts — a test's order is not a guarantee.</p>
     *
     * @param domain the domain
     * @return the witness, or null when the corpus holds no tie
     */
    private Map<String, Object> tieRuleWitnessOf(String domain) {
        Map<String, Object> cached = tieRuleWitnesses.get(domain);
        if (cached != null) {
            return cached;
        }
        KnowledgeSnapshot view = bench.workspace(workspaceOf(domain));
        Map<String, List<Claim>> holders = holdersOfTwoDocuments(view);
        Map<String, Object> witness = null;
        for (Map.Entry<String, List<Claim>> entry : holders.entrySet()) {
            String question = "What is stated about " + entry.getKey() + "?";
            SupportAdequacy workspace = bench.assessSupport(view, view, question,
                    AbstractionLevel.INTRODUCTORY, Set.of(), false);
            if (workspace.bestClaimId() == null) {
                continue;
            }
            Claim other = entry.getValue().stream()
                    .filter(claim -> !claim.id().equals(workspace.bestClaimId()))
                    .findFirst().orElse(null);
            if (other == null) {
                continue;
            }
            Set<String> support = Set.of(other.introducedBySourceId());
            SupportAdequacy restricted = bench.assessSupport(view, view, question,
                    AbstractionLevel.INTRODUCTORY, support, false);
            boolean tieWouldAccept = restricted.supportBestCoverage()
                    >= restricted.bestCoverage() - SupportAdequacy.COVERAGE_TOLERANCE;
            if (tieWouldAccept && !restricted.applicable()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("domain", domain);
                line.put("term", entry.getKey());
                line.put("question", question);
                line.put("workspaceBestClaim", workspace.bestClaimId());
                line.put("workspaceBestCoverage", workspace.bestCoverage());
                line.put("supportBestCoverage", restricted.supportBestCoverage());
                line.put("theClaimTheSupportHolds", other.id());
                line.put("tieRuleWouldAccept", tieWouldAccept);
                line.put("anchorRuleAccepts", restricted.applicable());
                line.put("why", "the support set ties the workspace's best coverage with a "
                        + "DIFFERENT claim, so the tie rule admits it and the anchor rule "
                        + "refuses it");
                witness = line;
                break;
            }
        }
        tieRuleWitnesses.put(domain, witness);
        return witness;
    }

    /** The witness of the rejected tie rule, per domain. */
    private final Map<String, Map<String, Object>> tieRuleWitnesses = new LinkedHashMap<>();

    /**
     * The terms that exactly two claims of two DIFFERENT documents hold.
     *
     * <p>Such a term makes the two claims reach the same coverage, which is what a tie in
     * the numbers needs.</p>
     *
     * @param view the view
     * @return the terms and the claims that hold them, in a deterministic order
     */
    private Map<String, List<Claim>> holdersOfTwoDocuments(KnowledgeSnapshot view) {
        Map<String, Map<String, Claim>> holders = new java.util.TreeMap<>();
        for (Claim claim : view.claims()) {
            for (String term : io.matrix.knowledge.TextFeatures.tokens(claim.proposition())) {
                if (term.length() < 6) {
                    continue;
                }
                holders.computeIfAbsent(term, key -> new LinkedHashMap<>())
                        .put(claim.introducedBySourceId(), claim);
            }
        }
        Map<String, List<Claim>> result = new java.util.TreeMap<>();
        for (Map.Entry<String, Map<String, Claim>> entry : holders.entrySet()) {
            if (entry.getValue().size() == 2) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            }
        }
        return result;
    }

    @Test
    @DisplayName("every cited claim carries a citable status, and the evidence names its source")
    void everyCitedClaimIsCitableAndNamed() {
        for (String domain : domains) {
            for (Case testCase : measured(domain)) {
                ArtifactReportResource.JourneyResult served = route.journey(workspaceOf(domain),
                        request(testCase, "citable"), null);
                for (ArtifactReportResource.EvidenceReference reference : body(served.response())
                        .evidence()) {
                    assertThat(reference.status())
                            .as("a cited claim is established: %s / %s", testCase.id(),
                                    reference.claimId())
                            .isNotEqualTo(String.valueOf(ClaimStatus.CANDIDATE));
                    assertThat(reference.sourceId())
                            .as("the provenance names the source the claim came from: %s / %s",
                                    testCase.id(), reference.claimId())
                            .isNotBlank();
                }
            }
        }
    }

    @Test
    @DisplayName("a reused identity with a changed question is refused, and the committed "
            + "artifact is untouched")
    void aReusedIdentityWithAChangedQuestionIsRefused() {
        for (String domain : domains) {
            List<Case> candidates = measured(domain);
            if (candidates.isEmpty()) {
                continue;
            }
            Case first = candidates.get(0);
            Case second = candidates.size() > 1 ? candidates.get(1) : first;
            ArtifactReportResource.ReportRequest request = new ArtifactReportResource.ReportRequest(
                    first.question(), null, Boolean.TRUE, "ac-identity-" + domain,
                    first.level());
            ArtifactReportResource.JourneyResult served = route.journey(workspaceOf(domain),
                    request, null);
            assertThat(served.response().getStatus()).isEqualTo(201);
            String digest = body(served.response()).sha256();

            ArtifactReportResource.ReportRequest changed = new ArtifactReportResource.ReportRequest(
                    second.question(), null, Boolean.TRUE, "ac-identity-" + domain,
                    second.level());
            ArtifactReportResource.JourneyResult conflict = route.journey(workspaceOf(domain),
                    changed, null);
            assertThat(conflict.response().getStatus())
                    .as("a changed payload under a reused key is refused")
                    .isEqualTo(409);
            assertThat(body(served.response()).sha256())
                    .as("the committed artifact is untouched")
                    .isEqualTo(digest);
        }
    }

    @Test
    @DisplayName("an ambiguous question is not answered, by either path")
    void anAmbiguousQuestionIsNotAnswered() {
        for (String domain : domains) {
            ArtifactReportResource.ReportRequest request =
                    new ArtifactReportResource.ReportRequest(
                            "Do you mean the first document or the second one?",
                            null, Boolean.TRUE, "ac-ambiguous-" + domain, "INTRODUCTORY");
            ArtifactReportResource.JourneyResult served = route.journey(workspaceOf(domain),
                    request, null);
            assertThat(served.response().getStatus()).isEqualTo(201);
            assertThat(body(served.response()).epistemicDisposition())
                    .as("an unsettled referent is not answered")
                    .isNotEqualTo("ANSWERED");
        }
    }

    @Test
    @DisplayName("the competence survives the restart, and the gate still holds afterwards")
    void theCompetenceSurvivesTheRestart() {
        for (String domain : domains) {
            List<Procedure> stored = cognitive.store().procedures(workspaceOf(domain));
            Procedure procedure = promotedProcedure(domain);
            if (procedure == null) {
                assertThat(stored)
                        .as("no procedure was promoted for %s, so none is stored", domain)
                        .isEmpty();
                continue;
            }
            assertThat(stored)
                    .as("the promoted procedure is in the durable store after the restart")
                    .hasSize(1);
            Procedure reloaded = stored.get(0);
            assertThat(reloaded.id()).isEqualTo(procedure.id());
            assertThat(reloaded.version()).isEqualTo(procedure.version());
            assertThat(reloaded.validationCases()).isEqualTo(procedure.validationCases());
            assertThat(ReportProcedure.supportOf(reloaded))
                    .as("the support set survives with it")
                    .isEqualTo(ReportProcedure.supportOf(procedure));
            assertThat(cognitive.store().procedureMeasuredCostNanos(workspaceOf(domain),
                    reloaded.id(), reloaded.version()))
                    .as("the cost the promotion measured survives the restart")
                    .isNotNull()
                    .isPositive();
        }
    }

    // ── the pre-registered cost grid ───────────────────────────────────────────

    @Test
    @DisplayName("the pre-registered cost grid: the reference against the procedure, warm and "
            + "cold, over two domains and two corpus sizes")
    void theCostGridIsMeasured() throws Exception {
        List<CostRow> rows = new ArrayList<>();
        Map<String, Object> cells = new LinkedHashMap<>();
        for (String domain : domains) {
            for (String size : List.of("narrow", "wide")) {
                for (int supportCount : List.of(1, 2)) {
                    Cell cell = buildCell(domain, size, supportCount);
                    if (cell == null) {
                        continue;
                    }
                    cells.put(cell.key(), measureCell(cell, rows));
                }
            }
        }
        assertThat(rows)
                .as("the grid produced rows: two domains, two corpus sizes, two support sizes")
                .isNotEmpty();
        // the promotion and its validation are measured where they happen, so the cost
        // artefact can report them SEPARATELY from the per-request rows
        for (String domain : domains) {
            revisionExperiment(domain);
        }
        writeCostEvidence(rows, cells);
    }

    /**
     * Builds one cell of the grid: its workspace, its corpus and its procedure.
     *
     * @param domain       the domain
     * @param size         the corpus size: the support documents only, or the whole corpus
     * @param supportCount the number of documents the support set covers
     * @return the cell, or null when the cell cannot be built from the split
     */
    private Cell buildCell(String domain, String size, int supportCount) throws IOException {
        List<Case> observed = subset(domain, "OBSERVED");
        if (observed.size() < supportCount) {
            return null;
        }
        // the documents the observed golds live in: the support set is LEARNED from the
        // observed episodes, and the narrow cell holds exactly the documents the support
        // covers
        List<String> supportDocuments = new ArrayList<>();
        for (Case testCase : observed) {
            String document = documentOfGold(domain, testCase);
            if (document != null && !supportDocuments.contains(document)) {
                supportDocuments.add(document);
            }
        }
        if (supportDocuments.size() < supportCount) {
            return null;
        }
        List<String> documents = "narrow".equals(size)
                ? new ArrayList<>(supportDocuments.subList(0, supportCount))
                : filesOf(domain);
        String workspace = workspaceOf(domain) + "-" + size;
        for (String document : documents) {
            bench.importDocument(workspace, corpusDirectory.resolve(document),
                    document, "en");
        }
        // ── the learning episode, in the CELL's workspace ──
        List<String> episodeIds = new ArrayList<>();
        for (Case testCase : observed) {
            ArtifactReportResource.JourneyResult result = route.journey(workspace,
                    request(testCase, "cell-" + size + "-" + supportCount + "-observed"),
                    ReportJourneyMethod.REFERENCE_ID);
            episodeIds.add(ReportJourneyTrace.episodeId(result.outcome().taskId()));
        }
        List<ReportProcedureLearner.Observation> observations =
                ReportProcedureLearner.observationsFrom(cognitive.store(), episodeIds);
        // the support fraction is realised by the OBSERVATIONS the learner is given: the
        // support set is the union of the observed episodes' sources
        List<ReportProcedureLearner.Observation> chosen = observations.subList(0,
                Math.min(supportCount, observations.size()));
        ReportProcedureLearner.Outcome promotion = new ReportProcedureLearner(bench,
                cognitive.store()).learn(workspace, chosen, validationCases(domain), Set.of(),
                PROMOTED_AT);
        return new Cell(domain + "/" + size + "/" + supportCount, domain, size, supportCount,
                workspace, documents, promotion.procedure(), promotion, subset(domain, "NOVEL"),
                subset(domain, "OUT_OF_SUPPORT"));
    }

    /**
     * Measures one cell.
     *
     * <p>The order is the one the pre-registration fixes: for every matched case, every arm
     * runs a COLD pass and a WARM pass, and the cold pass is the one that follows an emptied
     * cache — the state a restart leaves behind.</p>
     *
     * @param cell the cell
     * @param rows the rows the measurement appends to
     * @return the cell's verdict, by the pre-registered rule
     */
    private Map<String, Object> measureCell(Cell cell, List<CostRow> rows) {
        String procedureMethodId = cell.procedure() == null ? null
                : new ReportProcedureMethod(cell.procedure()).capability().id();
        for (Case testCase : cell.matched()) {
            for (String arm : List.of("a", "c", "s")) {
                if (!"a".equals(arm) && procedureMethodId == null) {
                    continue;
                }
                String forced = "c".equals(arm) ? procedureMethodId
                        : ("a".equals(arm) ? ReportJourneyMethod.REFERENCE_ID : null);
                for (String pass : List.of("cold", "warm")) {
                    if ("cold".equals(pass)) {
                        // the cold pass follows an emptied cache: the state a restart leaves
                        // behind, in which the pass over the claims is paid in full
                        bench.clearStatisticsCache();
                    }
                    rows.add(arm(cell, testCase, arm, pass, forced));
                }
            }
        }
        // the fallback arm: the served path on an out-of-support control
        for (Case testCase : cell.outOfSupport()) {
            if (!cell.documents().contains(documentOfGold(cell.domain(), testCase))) {
                // the control's gold is not in this cell's corpus, so the reference has
                // nothing to answer: the cell does not measure the fallback
                continue;
            }
            bench.clearStatisticsCache();
            rows.add(arm(cell, testCase, "f", "cold", null));
            bench.clearStatisticsCache();
            rows.add(arm(cell, testCase, "f", "warm", null));
        }
        return verdict(cell, rows);
    }

    /**
     * Runs one arm of one case and records what it cost and what it produced.
     *
     * @param cell       the cell
     * @param testCase   the case
     * @param arm        the arm
     * @param pass       the pass: cold or warm
     * @param forced     the method to force, or null to let the kernel choose
     * @return the row
     */
    private CostRow arm(Cell cell, Case testCase, String arm, String pass, String forced) {
        ArtifactReportResource.ReportRequest request = request(testCase,
                cell.key() + "-" + arm + "-" + pass);
        long cpuStart = cpuNanos();
        long wallStart = System.nanoTime();
        ArtifactReportResource.JourneyResult result = route.journey(cell.workspace(), request,
                forced);
        long wall = System.nanoTime() - wallStart;
        long cpu = cpuNanos() - cpuStart;
        String methodId = result.outcome() == null ? "" : result.outcome().methodId();
        List<String> cited = citedOf(result.response());
        String fallback = "";
        String plan = result.outcome() == null ? "" : planOf(result.outcome().taskId());
        int at = plan.indexOf("was not applicable");
        if (at > 0) {
            fallback = plan.substring(at);
        }
        return new CostRow(cell.key(), cell.domain(), cell.size(), cell.supportCount(), arm, pass,
                testCase.id(), methodId,
                grounded(testCase, cell.domain(), cell.workspace(), result.response()),
                cited.size(), wall, cpu, fallback, cited,
                result.outcome() != null && result.outcome().effectVerified());
    }

    /**
     * The cell's verdict, by the pre-registered rule.
     *
     * <p>The rule was written down before any of these numbers existed: the procedure arm's
     * warm median must be lower than the reference arm's, the paired differences' 95 %
     * bootstrap interval must exclude zero, and the procedure's answers must be grounded on
     * the same matched cases. If any of the three fails, the verdict is NOT ESTABLISHED —
     * including when the medians differ in the procedure's favour.</p>
     *
     * @param cell the cell
     * @param rows every row measured so far
     * @return the verdict
     */
    private Map<String, Object> verdict(Cell cell, List<CostRow> rows) {
        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("cell", cell.key());
        verdict.put("domain", cell.domain());
        verdict.put("corpusSize", cell.size());
        verdict.put("supportDocuments", cell.supportCount());
        verdict.put("documents", cell.documents());
        verdict.put("promoted", cell.promotion() != null && cell.promotion().promoted());
        verdict.put("promotionReason", cell.promotion() == null ? "no promotion was attempted"
                : cell.promotion().reason());
        List<Long> reference = warmMedians(rows, cell.key(), "a");
        List<Long> procedure = warmMedians(rows, cell.key(), "c");
        if (reference.isEmpty() || procedure.isEmpty()) {
            verdict.put("verdict", "NOT ESTABLISHED");
            verdict.put("reason", "the cell carries no procedure to measure: "
                    + (cell.promotion() == null ? "no promotion was attempted"
                            : cell.promotion().reason()));
            return verdict;
        }
        long referenceMedian = median(reference);
        long procedureMedian = median(procedure);
        long[] interval = bootstrapInterval(rows, cell.key());
        // THE PRE-REGISTERED RULE, THIRD PART, AS WRITTEN: "the procedure arm's answers are
        // grounded on the SAME MATCHED CASES as the reference's — a cheaper answer to a
        // different question is not a cheaper answer". So BOTH arms are checked, and they
        // are checked on the SAME set of cases: a procedure arm that answered a question
        // the reference did not, or answered one of them wrongly, fails the rule even when
        // it is faster.
        List<CostRow> referenceWarm = rows.stream()
                .filter(row -> row.cell().equals(cell.key()) && "a".equals(row.arm())
                        && "warm".equals(row.pass())).toList();
        List<CostRow> procedureWarm = rows.stream()
                .filter(row -> row.cell().equals(cell.key()) && "c".equals(row.arm())
                        && "warm".equals(row.pass())).toList();
        Set<String> referenceCases = referenceWarm.stream().map(CostRow::caseId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> procedureCases = procedureWarm.stream().map(CostRow::caseId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        boolean sameCases = referenceCases.equals(procedureCases);
        boolean referenceGrounded = referenceWarm.stream().allMatch(CostRow::grounded);
        boolean grounded = procedureWarm.stream().allMatch(CostRow::grounded);
        boolean cheaper = procedureMedian < referenceMedian;
        boolean separated = interval[0] > 0 || interval[1] < 0;
        verdict.put("referenceWarmMedianNanos", referenceMedian);
        verdict.put("procedureWarmMedianNanos", procedureMedian);
        verdict.put("pairedDifferenceIntervalNanos", List.of(interval[0], interval[1]));
        verdict.put("procedureAnswersGrounded", grounded);
        verdict.put("referenceAnswersGrounded", referenceGrounded);
        verdict.put("bothArmsGroundedOnTheSameCases", sameCases && grounded && referenceGrounded);
        verdict.put("matchedCases", new ArrayList<>(referenceCases));
        // the bootstrap's own conditions, named as the pre-registration demands
        verdict.put("bootstrapResamples", BOOTSTRAP_DRAWS);
        verdict.put("bootstrapSeed", BOOTSTRAP_SEED);
        // the COLD pass and the WARM pass are measured separately: the cold pass is the
        // first request of a workspace, the warm pass is a request that finds the
        // statistics already computed. The amendment that changes a document invalidates
        // the statistics, and the amendment's cost is therefore the cost of the next COLD
        // request — which is what the amortisation has to pay back.
        List<Long> referenceCold = passMedians(rows, cell.key(), "a", "cold");
        List<Long> procedureCold = passMedians(rows, cell.key(), "c", "cold");
        verdict.put("referenceColdMedianNanos",
                referenceCold.isEmpty() ? null : median(referenceCold));
        verdict.put("procedureColdMedianNanos",
                procedureCold.isEmpty() ? null : median(procedureCold));
        verdict.put("referenceWarmMinusColdNanos",
                referenceCold.isEmpty() ? null : referenceMedian - median(referenceCold));
        verdict.put("procedureWarmMinusColdNanos",
                procedureCold.isEmpty() ? null : procedureMedian - median(procedureCold));
        // the AMORTISATION: how many requests the procedure must serve to pay back the cold
        // start it costs once. A negative number means the cold start pays for itself.
        long saving = referenceMedian - procedureMedian;
        long coldPremium = procedureCold.isEmpty() ? 0L : median(procedureCold) - procedureMedian;
        verdict.put("coldStartPremiumNanos", coldPremium);
        verdict.put("requestsToAmortiseColdStart",
                coldPremium <= 0 ? 0 : (saving <= 0 ? -1 : (coldPremium + saving - 1) / saving));
        verdict.put("amortisationNote", coldPremium <= 0
                ? "the cold pass is not more expensive than the warm pass, so there is "
                        + "nothing to amortise"
                : (saving <= 0
                        ? "the cold start costs more than the warm requests save, so the "
                                + "procedure never pays for itself on this cell"
                        : "the cold start is paid back after this many requests, if the "
                                + "warm difference holds"));
        verdict.put("verdict", cheaper && separated && sameCases && grounded && referenceGrounded
                ? "CHEAPER" : "NOT ESTABLISHED");
        return verdict;
    }

    /** The wall times of an arm of a cell in one pass, per matched case, in the split's order. */
    private List<Long> passMedians(List<CostRow> rows, String cell, String arm, String pass) {
        List<Long> times = new ArrayList<>();
        for (CostRow row : rows) {
            if (row.cell().equals(cell) && arm.equals(row.arm()) && pass.equals(row.pass())) {
                times.add(row.wallNanos());
            }
        }
        return times;
    }

    /** The warm wall times of an arm of a cell, per matched case, in the split's order. */
    private List<Long> warmMedians(List<CostRow> rows, String cell, String arm) {
        List<Long> times = new ArrayList<>();
        for (CostRow row : rows) {
            if (row.cell().equals(cell) && arm.equals(row.arm()) && "warm".equals(row.pass())) {
                times.add(row.wallNanos());
            }
        }
        return times;
    }

    /**
     * The 95 % bootstrap interval of the paired difference (procedure minus reference).
     *
     * <p>The pairing is the unit: the two arms measured the same case in the same cell, and
     * the difference of the two warm passes of that case is what is resampled. The seed is
     * fixed, so the interval is the same number on every run.</p>
     *
     * @param rows every row measured so far
     * @param cell the cell
     * @return the interval, as {low, high}
     */
    private long[] bootstrapInterval(List<CostRow> rows, String cell) {
        List<Long> differences = new ArrayList<>();
        Map<String, Long> reference = new LinkedHashMap<>();
        Map<String, Long> procedure = new LinkedHashMap<>();
        for (CostRow row : rows) {
            if (!row.cell().equals(cell) || !"warm".equals(row.pass())) {
                continue;
            }
            if ("a".equals(row.arm())) {
                reference.put(row.caseId(), row.wallNanos());
            }
            if ("c".equals(row.arm())) {
                procedure.put(row.caseId(), row.wallNanos());
            }
        }
        for (Map.Entry<String, Long> entry : reference.entrySet()) {
            Long other = procedure.get(entry.getKey());
            if (other != null) {
                differences.add(other - entry.getValue());
            }
        }
        if (differences.isEmpty()) {
            return new long[] {0L, 0L};
        }
        java.util.Random random = new java.util.Random(BOOTSTRAP_SEED);
        List<Long> means = new ArrayList<>();
        for (int draw = 0; draw < BOOTSTRAP_DRAWS; draw++) {
            long total = 0L;
            for (int pick = 0; pick < differences.size(); pick++) {
                total += differences.get(random.nextInt(differences.size()));
            }
            means.add(total / differences.size());
        }
        means.sort(java.util.Comparator.naturalOrder());
        int low = (int) Math.floor(0.025 * (means.size() - 1));
        int high = (int) Math.ceil(0.975 * (means.size() - 1));
        return new long[] {means.get(low), means.get(high)};
    }

    /** The median of a list of measurements. */
    private static long median(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(java.util.Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    /** The thread's CPU time, or -1 when the platform does not report it. */
    private static long cpuNanos() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!bean.isCurrentThreadCpuTimeSupported()) {
            return -1L;
        }
        return bean.getCurrentThreadCpuTime();
    }

    /** Writes the cost rows and the per-cell verdicts. */
    private void writeCostEvidence(List<CostRow> rows, Map<String, Object> cells) throws IOException {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("report", "crj-01-adaptive-cost-grid");
        evidence.put("version", "1.0");
        evidence.put("date", "2026-11-27");
        evidence.put("baselineSha", "dff12b5883242b74c09cc2388be32061c2b87cf0");
        evidence.put("preregistration", "docs-v2/engineering/CRJ-01-ADAPTIVE-PREREGISTRATION.md");
        evidence.put("sealed", false);
        evidence.put("sealedNote", "the sealed evaluation is NOT EXECUTED: the isolation it "
                + "needs has not been established from this executor");
        evidence.put("timingNote", "the wall and CPU times are machine state; they are RECORDED "
                + "and are NOT asserted by any test, and the verdicts are read against the "
                + "pre-registered rule rather than against a preference");
        // ── WHAT THE TIMED PATH INCLUDES AND EXCLUDES ──
        // The per-request rows time the SERVED path: the selection, the statistics, the
        // validation of the preconditions and the fallback. They do NOT time the work that
        // MAKES a competence — the learning episode, the promotion and its validation —
        // because that work happens once per competence and not once per request. The
        // cost of that work is measured separately below, and it is NOT folded into the
        // per-request numbers: folding it in would make the per-request medians depend on
        // how many requests a cell made.
        evidence.put("timedPathCovers", "the SERVED path of one request: the selection "
                + "(the planner's scoring of the candidates), the retrieval statistics, the "
                + "validation of the preconditions (the presence check and the "
                + "question-specific adequacy check), the fallback and the writing of the "
                + "artefact. This is the list of the stages the timing covers, NOT an "
                + "exhaustive account of the work the process does");
        evidence.put("timedPathExcludes", "the work that MAKES a competence: the learning "
                + "episode, the promotion and the promotion's validation on the held-out "
                + "cases. That work is measured once per competence, is reported under "
                + "'promotionValidationCost', and is deliberately NOT folded into the "
                + "per-request medians");
        evidence.put("amortisationNote", "the cold premium of a cell is the cost of building "
                + "the statistics once; the requestsToAmortiseColdStart count is how many "
                + "requests pay it back, if the warm difference holds");
        // ── THE PROMOTION AND VALIDATION COST, MEASURED SEPARATELY ──
        Map<String, Object> promotionCost = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> entry : promotionValidationCosts.entrySet()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("firstPromotionAndValidationNanos",
                    entry.getValue().get("firstPromotionAndValidationNanos"));
            line.put("revisionPromotionAndValidationNanos",
                    entry.getValue().get("revisionPromotionAndValidationNanos"));
            line.put("heldOutCasesValidated",
                    separateValidationCases(entry.getKey()).size());
            promotionCost.put(entry.getKey(), line);
        }
        evidence.put("promotionValidationCost", promotionCost);
        evidence.put("promotionValidationNote", "the promotion and its validation are the "
                + "work that MAKES a competence: the learner reads the retained journeys, "
                + "derives the candidate's support, and checks the candidate against the "
                + "held-out cases. The times are machine state, are RECORDED and are NOT "
                + "asserted; they are reported separately because they happen once per "
                + "competence and not once per request, and folding them into the "
                + "per-request medians would make those medians depend on the number of "
                + "requests a cell made");

        List<Map<String, Object>> written = new ArrayList<>();
        for (CostRow row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("cell", row.cell());
            map.put("domain", row.domain());
            map.put("corpusSize", row.corpusSize());
            map.put("supportDocuments", row.supportDocuments());
            map.put("arm", row.arm());
            map.put("pass", row.pass());
            map.put("caseId", row.caseId());
            map.put("methodId", row.methodId());
            map.put("grounded", row.grounded());
            map.put("citations", row.citations());
            map.put("wallNanos", row.wallNanos());
            map.put("cpuNanos", row.cpuNanos());
            map.put("fallbackReason", row.fallbackReason());
            map.put("citedClaimIds", row.citedClaimIds());
            map.put("effectVerified", row.effectVerified());
            written.add(map);
        }
        evidence.put("rows", written);
        evidence.put("cells", cells);
        ObjectMapper json = JsonMapper.builder().build();
        Path artefact = RepositoryPaths.root()
                .resolve(EVIDENCE_DIRECTORY + "/CRJ-01-ADAPTIVE-COST-ROWS.json");
        Files.createDirectories(artefact.getParent());
        Files.writeString(artefact, json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence),
                StandardCharsets.UTF_8);
        Map<?, ?> reread = json.readValue(artefact.toFile(), Map.class);
        assertThat(((List<?>) reread.get("rows")).size())
                .as("the artefact carries every measured row")
                .isEqualTo(rows.size());
        // the suite rewrote a file the manifest pins, so the manifest is refreshed here
        refreshRetainedManifest();
    }

    /**
     * The corpus file a case's gold lives in, read from the corpus report.
     *
     * @param domain   the domain
     * @param testCase the case
     * @return the file name, or null when the report does not name one
     */
    private String documentOfGold(String domain, Case testCase) {
        for (Map<String, Object> domainReport : corpusReport().values()) {
            if (!domain.equals(domainReport.get("domain"))) {
                continue;
            }
            for (Object document : (List<?>) domainReport.get("documents")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) document;
                for (Object claim : (List<?>) row.get("claims")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> claimRow = (Map<String, Object>) claim;
                    if (testCase.goldPropositions().contains(claimRow.get("proposition"))) {
                        return String.valueOf(row.get("file"));
                    }
                }
            }
        }
        return null;
    }

    /** The corpus report, read once. */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> corpusReport() {
        if (corpusReportCache == null) {
            Map<String, Map<String, Object>> read = new LinkedHashMap<>();
            try {
                Path path = RepositoryPaths.root().resolve(CORPUS_REPORT);
                if (Files.exists(path)) {
                    ObjectMapper json = JsonMapper.builder().build();
                    Map<String, Object> parsed = json.readValue(
                            Files.readString(path, StandardCharsets.UTF_8), Map.class);
                    for (Object entry : (List<?>) parsed.get("domains")) {
                        Map<String, Object> domainReport = (Map<String, Object>) entry;
                        read.put(String.valueOf(domainReport.get("domain")), domainReport);
                    }
                }
            } catch (IOException unreadable) {
                throw new IllegalStateException("the corpus report could not be read", unreadable);
            }
            corpusReportCache = read;
        }
        return corpusReportCache;
    }

    // ── the competence revision ────────────────────────────────────────────────

    @Test
    @DisplayName("a failure episode yields a candidate revision, validated on a separate "
            + "subset, and the changed competence is measured on distinct subsequent tasks")
    void aFailureEpisodeYieldsAValidatedRevision() {
        for (String domain : domains) {
            // The experiment runs in its OWN workspace, so that the revision's effect on
            // what the competence can answer does not change the state the other tests
            // measure. It is computed once per domain and shared with the evidence writer.
            RevisionExperiment experiment = revisionExperiment(domain);
            Procedure original = experiment.first().procedure();
            if (original == null) {
                // no competence was promoted for this domain; there is nothing to revise.
                // The reason is in the promotion outcome and is carried into the evidence.
                assertThat(experiment.first().reason())
                        .as("a domain without a competence says why")
                        .isNotBlank();
                continue;
            }
            ReportProcedureLearner.Outcome outcome = experiment.outcome();
            if (!outcome.promoted()) {
                // A REJECTION IS A LEGITIMATE OUTCOME. The reason is the evidence, and it is
                // carried into the artefact; it is not worked around.
                assertThat(outcome.reason())
                        .as("a refused revision says why")
                        .isNotBlank();
                continue;
            }
            Procedure revised = outcome.procedure();
            assertThat(revised.id())
                    .as("a changed support set is a different competence, not a new version "
                            + "of the same one")
                    .isNotEqualTo(original.id());
            assertThat(ReportProcedure.supportOf(revised))
                    .as("the revision covers the failure episode's source as well")
                    .containsAll(ReportProcedure.supportOf(original));

            // ── BEFORE: the distinct task the competence CANNOT answer ──
            // The question is a different question — it is the failure episode's own
            // DOCUMENT, but a claim no other case uses, and the first competence's support
            // does not hold that document.
            Case subsequent = experiment.subsequent();
            assertThat(subsequent.subset())
                    .as("the distinct subsequent task is a case of its own subset")
                    .isEqualTo("SUBSEQUENT");
            assertThat(subsequent.id())
                    .as("the distinct subsequent task is not the failure question")
                    .isNotEqualTo(subset(domain, "OUT_OF_SUPPORT").get(0).id());
            ArtifactReportResource.JourneyResult before = experiment.before();
            assertThat(before.outcome().methodId())
                    .as("before the revision the competence cannot answer the distinct task, "
                            + "so the reference answers: %s", subsequent.id())
                    .isEqualTo(ReportJourneyMethod.REFERENCE_ID);
            assertThat(planOf(before.outcome().taskId()))
                    .as("the fallback names why the competence did not hold")
                    .contains("was not applicable to this question");
            assertThat(grounded(subsequent, domain, experiment.workspace(), before.response()))
                    .as("the reference answers the distinct task with its own evidence: %s",
                            subsequent.id())
                    .isTrue();

            // ── AFTER: the EXACT revised competence answers it ──
            ArtifactReportResource.JourneyResult after = experiment.after();
            assertThat(after.outcome().methodId())
                    .as("after the revision the EXACT revised competence answers the distinct "
                            + "task: %s", subsequent.id())
                    .isEqualTo(ReportProcedureMethod.ID_PREFIX + revised.id());
            assertThat(grounded(subsequent, domain, experiment.workspace(), after.response()))
                    .as("the revised competence answers the distinct task with its evidence: %s",
                            subsequent.id())
                    .isTrue();
            assertThat(after.outcome().effectVerified())
                    .as("the revised competence's effect is verified: %s", subsequent.id())
                    .isTrue();
        }
    }

    // ── the evidence ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("the served evidence is written: the method that ran, the fallback reason, the "
            + "evidence and the effect, case by case")
    void theServedEvidenceIsWritten() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        int referenceGrounded = 0;
        int servedGrounded = 0;
        int cases = 0;
        for (String domain : domains) {
            for (Case testCase : allCases(domain)) {
                ArtifactReportResource.JourneyResult served = route.journey(workspaceOf(domain),
                        request(testCase, "evidence-served"), null);
                ArtifactReportResource.JourneyResult reference = route.journey(workspaceOf(domain),
                        request(testCase, "evidence-reference"), ReportJourneyMethod.REFERENCE_ID);
                assertThat(served.response().getStatus()).isEqualTo(201);
                assertThat(reference.response().getStatus()).isEqualTo(201);

                boolean servedGroundedHere = grounded(testCase, domain, served.response());
                boolean referenceGroundedHere = grounded(testCase, domain, reference.response());
                servedGrounded += servedGroundedHere ? 1 : 0;
                referenceGrounded += referenceGroundedHere ? 1 : 0;
                cases++;

                String method = served.outcome().methodId();
                if (!method.startsWith("procedure.")) {
                    // the procedure was not applicable: the served evidence must be the
                    // reference's own — the fallback is a fallback to the SAME answer
                    assertThat(citedOf(served.response()))
                            .as("the served evidence is the reference's own: %s", testCase.id())
                            .isEqualTo(citedOf(reference.response()));
                }
                rows.add(row(domain, testCase, "served", served, servedGroundedHere));
                rows.add(row(domain, testCase, "reference", reference, referenceGroundedHere));
            }
        }
        // THE PARITY, CASE BY CASE. A count equality would pass on 20 against 20, and the
        // claim is not about the counts: it is that the served path answers EVERY case the
        // reference answers, and answers none of them wrongly.
        Map<String, Boolean> servedByCase = new LinkedHashMap<>();
        Map<String, Boolean> referenceByCase = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Boolean> target = "served".equals(row.get("arm"))
                    ? servedByCase : referenceByCase;
            target.put(String.valueOf(row.get("caseId")), Boolean.TRUE.equals(row.get("grounded")));
        }
        assertThat(servedByCase.keySet())
                .as("the served path answered exactly the cases the reference answered")
                .isEqualTo(referenceByCase.keySet());
        List<String> disagreements = new ArrayList<>();
        for (String caseId : referenceByCase.keySet()) {
            if (!Objects.equals(servedByCase.get(caseId), referenceByCase.get(caseId))) {
                disagreements.add(caseId + ": served " + servedByCase.get(caseId)
                        + " against the reference's " + referenceByCase.get(caseId));
            }
        }
        assertThat(disagreements)
                .as("the served path's verdict is the reference's, CASE BY CASE")
                .isEmpty();
        assertThat(servedGrounded)
                .as("the served path grounds as many cases as the stateless reference")
                .isEqualTo(referenceGrounded);
        writeServedEvidence(rows, servedGrounded, referenceGrounded, cases);
    }

    /** One row of the served evidence. */
    private Map<String, Object> row(String domain, Case testCase, String arm,
            ArtifactReportResource.JourneyResult result, boolean grounded) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domain", domain);
        map.put("caseId", testCase.id());
        map.put("class", testCase.caseClass());
        map.put("subset", testCase.subset());
        map.put("level", testCase.level());
        map.put("arm", arm);
        map.put("methodId", result.outcome().methodId());
        map.put("forced", result.outcome().forced());
        map.put("grounded", grounded);
        map.put("citations", citedOf(result.response()).size());
        map.put("citedClaimIds", citedOf(result.response()));
        map.put("epistemicDisposition", body(result.response()).epistemicDisposition());
        map.put("disposition", body(result.response()).disposition());
        map.put("effectVerified", result.outcome().effectVerified());
        map.put("artifactSha256", body(result.response()).sha256());
        map.put("plan", planOf(result.outcome().taskId()));
        map.put("fallbackReason", fallbackReason(planOf(result.outcome().taskId())));
        map.put("limitations", body(result.response()).limitations());
        map.put("tracePhases", result.outcome().tracePhases());
        return map;
    }

    /**
     * The fallback's reason, as a first-class field.
     *
     * <p>The reason is written into the plan, because the plan is what a reader of the
     * journey reads. It is ALSO copied into its own field, because a claim that must be
     * checked mechanically — "every fallback names its reason" — cannot be checked in a
     * prose field, and a record that only a human can read is not evidence.</p>
     *
     * @param plan the plan
     * @return the reason, or an empty string when the arm did not fall back
     */
    private static String fallbackReason(String plan) {
        for (String marker : new String[]{"was not applicable to this question: ",
                "was applicable to this question but was not selected: "}) {
            int at = plan.indexOf(marker);
            if (at >= 0) {
                return plan.substring(at + marker.length()).trim();
            }
        }
        return "";
    }

    /** Every case of a domain. */
    /**
     * Every case of a domain — the learning episode's OBSERVED cases included.
     *
     * <p><b>Why the observed cases are here and not only in the episode.</b> The evidence is
     * the record of what the served path did, case by case, and the observed cases were
     * served too: they are the learning episode's own journeys. A reader who compares the
     * evidence with the split must find every case of the split in the evidence, or the
     * missing ones must be named as missing. They are served here with a fresh purpose, so
     * the row records a served journey and not the episode's.</p>
     *
     * @param domain the domain
     * @return the cases
     */
    private List<Case> allCases(String domain) {
        List<Case> selected = new ArrayList<>();
        for (Case testCase : cases) {
            if (domain.equals(testCase.domain())) {
                selected.add(testCase);
            }
        }
        return selected;
    }

    /** Writes the served evidence. */
    private void writeServedEvidence(List<Map<String, Object>> rows, int servedGrounded,
            int referenceGrounded, int cases) throws IOException {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("report", "crj-01-adaptive-evidence");
        evidence.put("version", "1.0");
        evidence.put("date", "2026-11-27");
        evidence.put("baselineSha", "dff12b5883242b74c09cc2388be32061c2b87cf0");
        evidence.put("split", SPLIT);
        evidence.put("splitSha256", sha256(Files.readAllBytes(
                RepositoryPaths.root().resolve(SPLIT))));
        evidence.put("corpusReport", CORPUS_REPORT);
        evidence.put("corpusReportSha256", sha256(Files.readAllBytes(
                RepositoryPaths.root().resolve(CORPUS_REPORT))));
        evidence.put("preregistration",
                "docs-v2/engineering/CRJ-01-ADAPTIVE-PREREGISTRATION.md");
        evidence.put("sealed", false);
        evidence.put("sealedNote", "the sealed evaluation is NOT EXECUTED: the isolation it needs "
                + "has not been established from this executor — one non-privileged account, no "
                + "second identity, no usable namespace, and a harness sandbox that is a write "
                + "perimeter only");
        // THE ROLE RECORD MUST BE TRUE. The independent case author did NOT deliver: the
        // corpora and the split were written by the implementer's own deterministic
        // generator, and the cases are therefore NOT independent of the implementer's
        // mechanism. A role record that says otherwise would make the artefact a false
        // statement about the milestone's own independence — and the protocol's §5.5a asks
        // for the role record to be part of the record.
        evidence.put("roles", Map.of(
                "implementer", "the DeepSeek Harness session that wrote the gate, the "
                        + "statistics, the criterion, the corpora and the split",
                "caseAuthor", "NONE — the independent case author did NOT deliver. The two "
                        + "corpora and the 28 cases were written by the IMPLEMENTER's "
                        + "deterministic generator from the separation probe's own focused "
                        + "questions. The cases are therefore NOT independent of the "
                        + "implementer's mechanism, and the questions are FORMULIC rather "
                        + "than natural. The split's gold is the production extraction's own "
                        + "sentence and the corpus digests are re-derived by the gate from "
                        + "the corpus bytes, so the cases remain verifiable. The split holds 30 "
                        + "cases: 15 per domain.",
                "isolationInspector", "a SEPARATE DSH agent that probed the host's separation "
                        + "with synthetic canaries only",
                "reviewer", "a SEPARATE DSH agent that read the diff and the evidence; its "
                        + "findings are in docs-v2/engineering/evidence/crj01-adaptive-competence/"
                        + "REVIEW.md"));
        evidence.put("rows", rows);

        Map<String, Object> promotionLines = new LinkedHashMap<>();
        Map<String, Object> revisions = new LinkedHashMap<>();
        // the revision experiment is computed here as well, so that the artefact carries it
        // whatever order the tests ran in
        for (String domain : domains) {
            revisionExperiment(domain);
            tieRuleWitnessOf(domain);
        }
        Map<String, Object> refusals = new LinkedHashMap<>();
        for (Map.Entry<String, ReportProcedureLearner.Outcome> entry
                : refusedOutcomes.entrySet()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("promoted", entry.getValue().promoted());
            line.put("reason", entry.getValue().reason());
            line.put("proposedCase", entry.getValue().promoted() ? "" : "the case the candidate "
                    + "was proposed from was included in the validation subset");
            refusals.put(entry.getKey(), line);
        }
        for (String domain : domains) {
            ReportProcedureLearner.Outcome promotion = this.promotions.get(domain);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("promoted", promotion != null && promotion.promoted());
            line.put("reason", promotion == null ? "" : promotion.reason());
            line.put("procedureId", promotion == null || promotion.procedure() == null ? ""
                    : promotion.procedure().id());
            line.put("support", promotion == null || promotion.procedure() == null ? List.of()
                    : new ArrayList<>(ReportProcedure.supportOf(promotion.procedure())));
            promotionLines.put(domain, line);
            ReportProcedureLearner.Outcome revision = revisionExperiment(domain).outcome();
            Map<String, Object> revisionLine = new LinkedHashMap<>();
            revisionLine.put("promoted", revision.promoted());
            revisionLine.put("reason", revision.reason());
            revisionLine.put("procedureId", revision.procedure() == null ? ""
                    : revision.procedure().id());
            revisionLine.put("support", revision.procedure() == null ? List.of()
                    : new ArrayList<>(ReportProcedure.supportOf(revision.procedure())));
            revisions.put(domain, revisionLine);
        }
        evidence.put("promotions", promotionLines);
        evidence.put("refusedRevisionAttempts", refusals);
        // ── THE DISTINCT SUBSEQUENT TASK, BEFORE AND AFTER THE REVISION ──
        // The rows are persisted because a measurement nobody can read is not a
        // measurement: each row names the question, the method that actually ran, the
        // grounding verdict, the effect and the fallback's reason.
        Map<String, Object> subsequentRows = new LinkedHashMap<>();
        for (String domain : domains) {
            RevisionExperiment experiment = revisionExperiment(domain);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("domain", experiment.domain());
            line.put("workspace", experiment.workspace());
            line.put("caseId", experiment.subsequent().id());
            line.put("subset", experiment.subsequent().subset());
            line.put("question", experiment.subsequent().question());
            line.put("goldPropositions", experiment.subsequent().goldPropositions());
            line.put("disjointFrom", "the learning episode's observations, the failure "
                    + "episode's question and the revision's validation cases — the case is a "
                    + "claim of the failure episode's own DOCUMENT that no other case uses");
            line.put("before", subsequentRow(experiment, experiment.before(), true));
            line.put("after", subsequentRow(experiment, experiment.after(), false));
            line.put("revisedProcedureId",
                    experiment.outcome().promoted() ? experiment.outcome().procedure().id() : "");
            line.put("promoted", experiment.outcome().promoted());
            line.put("promotionReason", experiment.outcome().reason());
            // ── THE ROWS ARE ASSERTED BEFORE THEY ARE WRITTEN ──
            // The evidence writer runs in its OWN execution, with its own workspace and its
            // own store, and it must therefore verify the properties ITSELF: a row that
            // says the exact revised procedure answered must BE the exact revised
            // procedure answering, in this execution. A writer that recorded what it
            // found without checking it would write a no-op revision's row as if it were a
            // success — and the row would outlive the failure that produced it.
            assertThat(experiment.outcome().promoted())
                    .as("the distinct subsequent task's 'after' row is written only when the "
                            + "revision was promoted: %s", domain)
                    .isTrue();
            String revisedId = experiment.outcome().procedure().id();
            assertThat(String.valueOf(line.get("after")).isEmpty()).isFalse();
            Map<String, Object> after = (Map<String, Object>) line.get("after");
            Map<String, Object> before = (Map<String, Object>) line.get("before");
            assertThat(after.get("methodId"))
                    .as("the 'after' row is the EXACT revised procedure: %s", domain)
                    .isEqualTo(ReportProcedureMethod.ID_PREFIX + revisedId);
            assertThat(after.get("grounded"))
                    .as("the 'after' row is grounded: %s", domain)
                    .isEqualTo(Boolean.TRUE);
            assertThat(after.get("effectVerified"))
                    .as("the 'after' row's effect is verified: %s", domain)
                    .isEqualTo(Boolean.TRUE);
            assertThat(before.get("methodId"))
                    .as("the 'before' row is the reference, which answers what the competence "
                            + "cannot: %s", domain)
                    .isEqualTo(ReportJourneyMethod.REFERENCE_ID);
            assertThat(String.valueOf(before.get("planNoteOnTheProcedureThatDidNotRun")))
                    .as("the 'before' row names the fallback's reason: %s", domain)
                    .isNotBlank();
            assertThat(before.get("grounded"))
                    .as("the 'before' row is grounded: %s", domain)
                    .isEqualTo(Boolean.TRUE);
            subsequentRows.put(domain, line);
        }
        evidence.put("distinctSubsequentTask", subsequentRows);
        // THE WITNESS OF THE REJECTED RULE: the tie rule would have admitted a support set
        // that ties the workspace's best coverage with a DIFFERENT claim, and the kept rule
        // refuses it. The rejection is a measurement, not a recollection.
        Map<String, Object> witnesses = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : tieRuleWitnesses.entrySet()) {
            witnesses.put(entry.getKey(), entry.getValue());
        }
        evidence.put("rejectedTieRuleWitness", witnesses);
        evidence.put("revisions", revisions);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cases", cases);
        summary.put("servedGrounded", servedGrounded);
        summary.put("referenceGrounded", referenceGrounded);
        summary.put("parity", servedGrounded == referenceGrounded);
        summary.put("groundedMeaning", "the oracle's verdict: a cited gold for a positive case, "
                + "the expected disposition for a negative-premise case");
        evidence.put("summary", summary);
        evidence.put("timingNote", "the timings are measured in the cost grid and are NOT "
                + "asserted by any test: they are machine state, and a fast answer is not "
                + "evidence of competence");

        ObjectMapper json = JsonMapper.builder().build();
        Path artefact = RepositoryPaths.root()
                .resolve(EVIDENCE_DIRECTORY + "/CRJ-01-ADAPTIVE-EVIDENCE.json");
        Files.createDirectories(artefact.getParent());
        Files.writeString(artefact, json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence),
                StandardCharsets.UTF_8);
        Map<?, ?> reread = json.readValue(artefact.toFile(), Map.class);
        assertThat(((List<?>) reread.get("rows")).size())
                .as("the artefact carries every measured row")
                .isEqualTo(rows.size());
        // the suite rewrote a file the manifest pins, so the manifest is refreshed here
        refreshRetainedManifest();
    }

    // ── the helpers ────────────────────────────────────────────────────────────

    /** The route, wired by hand, the way the container wires it. */
    private static ArtifactReportResource route(KnowledgeWorkbench bench, CognitiveService cognitive,
            Path outputRoot, ReportJourneyPlanner planner) {
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = new ArtifactActionService(true, outputRoot.toString());
        route.planner = planner;
        route.cognitive = cognitive;
        return route;
    }

    /** The workspace a domain's corpus lives in. */
    private static String workspaceOf(String domain) {
        return WORKSPACE_PREFIX + domain;
    }

    /**
     * The workspace the REVISION EXPERIMENT runs in.
     *
     * <p><b>Why the revision has a workspace of its own.</b> The revision changes what the
     * competence can answer: the failure episode's source joins its support, so the
     * questions of that document stop being out of support. A revision made in the shared
     * workspace would therefore change the state every other test measures, and the
     * measurement would depend on the order the tests ran in — a suite whose assertions
     * depend on an order it does not declare is a suite that can pass or fail for a reason
     * nobody wrote. The revision runs in its own workspace, with its own learning episode
     * and its own first competence, and the BEFORE and the AFTER are both measured there,
     * around the revision itself.</p>
     *
     * @param domain the domain
     * @return the workspace
     */
    private static String revisionWorkspaceOf(String domain) {
        return WORKSPACE_PREFIX + domain + "-revision";
    }

    /**
     * The revision experiment of a domain: the distinct subsequent task BEFORE the revision
     * and AFTER it.
     *
     * @param domain the domain
     * @return the experiment
     */
    private RevisionExperiment revisionExperiment(String domain) {
        RevisionExperiment cached = revisionExperiments.get(domain);
        if (cached != null) {
            return cached;
        }
        String workspace = revisionWorkspaceOf(domain);
        for (String file : filesOf(domain)) {
            bench.importDocument(workspace, corpusDirectory.resolve(file), file, "en");
        }
        // ── THE LEARNING EPISODE, in the revision's own workspace ──
        List<String> observed = new ArrayList<>();
        for (Case testCase : subset(domain, "OBSERVED")) {
            ArtifactReportResource.JourneyResult result = route.journey(workspace,
                    request(testCase, "revision-observed"), ReportJourneyMethod.REFERENCE_ID);
            assertThat(result.response().getStatus())
                    .as("the revision workspace's learning episode was served: %s", testCase.id())
                    .isEqualTo(201);
            observed.add(ReportJourneyTrace.episodeId(result.outcome().taskId()));
        }
        List<ReportProcedureLearner.Observation> observations =
                ReportProcedureLearner.observationsFrom(cognitive.store(), observed);
        long promotionStart = System.nanoTime();
        ReportProcedureLearner.Outcome first = new ReportProcedureLearner(bench,
                cognitive.store()).learn(workspace, observations, validationCases(domain),
                Set.of(), PROMOTED_AT);
        long promotionNanos = System.nanoTime() - promotionStart;

        // ── THE DISTINCT SUBSEQUENT TASK ──
        // A question of the failure episode's OWN document that no other case uses: the
        // first competence's support does not hold that document, and the revision's does.
        List<Case> subsequentCases = subset(domain, "SUBSEQUENT");
        assertThat(subsequentCases)
                .as("the domain carries a distinct subsequent task")
                .hasSize(1);
        Case subsequent = subsequentCases.get(0);

        // ── BEFORE: the competence cannot answer it, and the reference answers ──
        ArtifactReportResource.JourneyResult before = route.journey(workspace,
                request(subsequent, "subsequent-before"), null);
        assertThat(before.response().getStatus())
                .as("the distinct subsequent task was served before the revision: %s",
                        subsequent.id())
                .isEqualTo(201);

        // ── THE FAILURE EPISODE — the control the first competence cannot answer ──
        List<Case> controls = subset(domain, "OUT_OF_SUPPORT");
        assertThat(controls)
                .as("the domain carries the control the competence cannot answer")
                .isNotEmpty();
        Case control = controls.get(0);
        ArtifactReportResource.JourneyResult failure = route.journey(workspace,
                request(control, "revision-failure"), null);

        // ── THE CANDIDATE REVISION ──
        List<String> episodeIds = new ArrayList<>(observed);
        episodeIds.add(ReportJourneyTrace.episodeId(failure.outcome().taskId()));
        List<ReportProcedureLearner.Observation> revisionObservations =
                ReportProcedureLearner.observationsFrom(cognitive.store(), episodeIds);
        List<ReportProcedureLearner.Case> separate = separateValidationCases(domain);
        long validationStart = System.nanoTime();
        ReportProcedureLearner.Outcome outcome = new ReportProcedureLearner(bench,
                cognitive.store()).learn(workspace, revisionObservations, separate,
                Set.of(), REVISION_PROMOTED_AT);
        long validationNanos = System.nanoTime() - validationStart;

        // ── THE REFUSED ATTEMPT, WITNESSED ──
        // The first attempt validated the candidate on the case the candidate was PROPOSED
        // from, and the learner refused it. The refusal is performed here and kept, so that
        // the artefact carries the refusal itself and not a narration of it.
        List<ReportProcedureLearner.Case> withTheProposedCase = new ArrayList<>(separate);
        withTheProposedCase.add(new ReportProcedureLearner.Case(control.id(), control.question(),
                AbstractionLevel.valueOf(control.level())));
        ReportProcedureLearner.Outcome refused = new ReportProcedureLearner(bench,
                cognitive.store()).learn(workspace, revisionObservations, withTheProposedCase,
                Set.of(), REVISION_PROMOTED_AT);
        refusedOutcomes.put(domain, refused);

        // ── AFTER: the revised competence answers the SAME distinct task ──
        ArtifactReportResource.JourneyResult after = route.journey(workspace,
                request(subsequent, "subsequent-after"), null);
        assertThat(after.response().getStatus())
                .as("the distinct subsequent task was served after the revision: %s",
                        subsequent.id())
                .isEqualTo(201);

        Map<String, Long> costs = new LinkedHashMap<>();
            // the learner's ONE call does the promotion AND its validation, so
            // the time carries both; the key says so
            costs.put("firstPromotionAndValidationNanos", promotionNanos);
        costs.put("revisionPromotionAndValidationNanos", validationNanos);
        promotionValidationCosts.put(domain, costs);

        RevisionExperiment experiment = new RevisionExperiment(domain, workspace, subsequent,
                before, failure, after, first, outcome, refused);
        revisionExperiments.put(domain, experiment);
        return experiment;
    }

    /** The response's body. */
    private static ArtifactReportResource.ReportResponse body(Response response) {
        Object entity = response.getEntity();
        assertThat(entity).as("the response carries a body").isInstanceOf(
                ArtifactReportResource.ReportResponse.class);
        return (ArtifactReportResource.ReportResponse) entity;
    }

    /**
     * The detail a refusal carries.
     *
     * <p>A refusal is not a report: its body is the route's error body, and the reason it
     * carries is the reason the method gave.</p>
     *
     * @param response the refused response
     * @return the detail, or an empty string when the body carries none
     */
    @SuppressWarnings("unchecked")
    private static String refusalDetail(Response response) {
        Object entity = response.getEntity();
        if (entity instanceof Map<?, ?> map) {
            Object detail = map.get("detail");
            return detail == null ? "" : String.valueOf(detail);
        }
        return "";
    }

    /** The claim ids a response cited. */
    private static List<String> citedOf(Response response) {
        List<String> cited = new ArrayList<>();
        for (ArtifactReportResource.EvidenceReference reference : body(response).evidence()) {
            cited.add(reference.claimId());
        }
        java.util.Collections.sort(cited);
        return cited;
    }

    /** The artifacts of a journey's identity. */
    private List<String> artifactsOf(String taskId) {
        List<String> found = new ArrayList<>();
        try {
            for (io.matrix.actions.ArtifactWriter.Record record
                    : route.actions.writer().readIndex()) {
                if (record.actionId().equals(taskId) && "COMMITTED".equals(record.status())) {
                    found.add(record.fileName());
                }
            }
        } catch (IOException unreadable) {
            throw new IllegalStateException("the writer's index could not be read", unreadable);
        }
        return found;
    }

    /** The plan the kernel made, read from the trace. */
    private String planOf(String taskId) {
        for (io.matrix.cognitive.LearningTrace.Step step
                : cognitive.store().events(ReportJourneyTrace.episodeId(taskId))) {
            if (ReportJourneyTrace.PHASE_PLAN.equals(step.phase())) {
                return step.detail();
            }
        }
        return "";
    }

    /** The procedure a domain's promotion produced, or null when it was refused. */
    private Procedure promotedProcedure(String domain) {
        ReportProcedureLearner.Outcome outcome = promotions.get(domain);
        return outcome == null ? null : outcome.procedure();
    }

    /**
     * The support set of a domain's promoted procedure.
     *
     * @param domain the domain
     * @return the support set, or an empty set when the domain has no promoted procedure
     */
    private Set<String> supportOf(String domain) {
        Procedure procedure = promotedProcedure(domain);
        return procedure == null ? Set.of() : ReportProcedure.supportOf(procedure);
    }

    /**
     * One row of the distinct subsequent task: the method that ran, the grounding verdict,
     * the effect, the evidence and the fallback's reason.
     *
     * @param experiment the revision experiment
     * @param journey    the journey that served the task
     * @param before     whether this is the BEFORE row
     * @return the row
     */
    private Map<String, Object> subsequentRow(RevisionExperiment experiment,
            ArtifactReportResource.JourneyResult journey, boolean before) {
        Map<String, Object> line = new LinkedHashMap<>();
        Case subsequent = experiment.subsequent();
        line.put("phase", before ? "before the revision" : "after the revision");
        line.put("methodId", journey.outcome().methodId());
        line.put("forced", journey.outcome().forced());
        line.put("grounded", grounded(subsequent, experiment.domain(), experiment.workspace(),
                journey.response()));
        line.put("citations", citedOf(journey.response()).size());
        line.put("citedClaimIds", citedOf(journey.response()));
        line.put("effectVerified", journey.outcome().effectVerified());
        line.put("artifactSha256", body(journey.response()).sha256());
        line.put("plan", planOf(journey.outcome().taskId()));
        // the plan's note about the procedure that did NOT run - in the "before" phase that
        // is the first competence's refusal, and in the "after" phase it may be the same
        // note, because the original competence is still offered and still does not hold
        line.put("planNoteOnTheProcedureThatDidNotRun",
                fallbackReason(planOf(journey.outcome().taskId())));
        line.put("limitations", body(journey.response()).limitations());
        line.put("tracePhases", journey.outcome().tracePhases());
        return line;
    }

    /**
     * The oracle over the corpus of a domain, resolving the gold's ids in a workspace.
     *
     * <p><b>Why the workspace is a parameter.</b> A production claim id is derived from the
     * workspace as well as from the content, so the SAME document imported into two
     * workspaces yields two different claim ids. The grid's cells run in their OWN
     * workspaces, and a gold resolved in another workspace would read as ungrounded for a
     * reason that has nothing to do with the arm under test.</p>
     *
     * @param domain    the domain
     * @param workspace the workspace the answer came from
     * @return the oracle
     */
    private SourceSpanOracle oracle(String domain, String workspace) {
        Map<String, String> idsByProposition = new LinkedHashMap<>();
        for (Claim claim : bench.workspace(workspace).claims()) {
            idsByProposition.put(claim.proposition(), claim.id());
        }
        return new SourceSpanOracle(corpusDirectory, idsByProposition);
    }

    /** Whether the served answer cites the case's gold. */
    private boolean grounded(Case testCase, String domain, Response response) {
        return grounded(testCase, domain, workspaceOf(domain), response);
    }

    /**
     * Whether the served answer cites the case's gold, in a given workspace.
     *
     * @param testCase  the case
     * @param domain    the domain
     * @param workspace the workspace the answer came from
     * @param response  the served response
     * @return the verdict
     */
    private boolean grounded(Case testCase, String domain, String workspace, Response response) {
        SourceSpanOracle oracle = oracle(domain, workspace);
        List<SourceSpanOracle.CitedClaim> cited = new ArrayList<>();
        String epistemic = "";
        String premise = "";
        for (ArtifactReportResource.EvidenceReference reference : body(response).evidence()) {
            cited.add(new SourceSpanOracle.CitedClaim(reference.claimId(),
                    reference.proposition(), reference.status(), reference.sourceId()));
        }
        if (body(response).premise() != null) {
            premise = body(response).premise().status();
        }
        epistemic = body(response).epistemicDisposition();
        SourceSpanOracle.Verdict verdict = oracle.score(gold(testCase, domain),
                new SourceSpanOracle.ServedAnswer(body(response).disposition(), epistemic, premise,
                        cited, List.of()));
        return verdict.outcome() == SourceSpanOracle.Outcome.GROUNDED
                || verdict.outcome() == SourceSpanOracle.Outcome.DISPOSITION_MATCHED;
    }

    /** The gold of a case, as the oracle reads it. */
    private SourceSpanOracle.GoldCase gold(Case testCase, String domain) {
        String expectedSource = null;
        if (testCase.expectedProvenance() != null && !testCase.expectedProvenance().isBlank()) {
            for (Claim claim : bench.workspace(workspaceOf(domain)).claims()) {
                if (testCase.goldPropositions().contains(claim.proposition())
                        && claim.introducedBySourceId() != null) {
                    expectedSource = claim.introducedBySourceId();
                    break;
                }
            }
            if (expectedSource == null) {
                expectedSource = testCase.expectedProvenance();
            }
        }
        return new SourceSpanOracle.GoldCase(testCase.id(), testCase.corpusFile(),
                testCase.corpusSha256(), testCase.goldPropositions(), testCase.goldClaimIds(),
                testCase.positive(), fieldOf(testCase.expectedField()),
                testCase.expectedDisposition(), expectedSource);
    }

    /** The field a negative case's expected disposition lives in. */
    private static SourceSpanOracle.Field fieldOf(String name) {
        if (name == null) {
            return SourceSpanOracle.Field.PREMISE;
        }
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "EPISTEMIC" -> SourceSpanOracle.Field.EPISTEMIC;
            case "ACTION" -> SourceSpanOracle.Field.ACTION;
            default -> SourceSpanOracle.Field.PREMISE;
        };
    }

    /**
     * The request the served path takes.
     *
     * @param testCase the case
     * @param purpose  what the request is for; it keeps the keys apart
     * @return the request
     */
    private static ArtifactReportResource.ReportRequest request(Case testCase, String purpose) {
        return new ArtifactReportResource.ReportRequest(testCase.question(), null, Boolean.TRUE,
                testCase.idempotencyKey() + "-" + purpose, testCase.level());
    }

    /** The cases of a domain's subset. */
    private List<Case> subset(String domain, String name) {
        List<Case> selected = new ArrayList<>();
        for (Case testCase : cases) {
            if (domain.equals(testCase.domain()) && name.equals(testCase.subset())) {
                selected.add(testCase);
            }
        }
        return selected;
    }

    /** The cases a domain is measured on: everything the procedure was not learned from. */
    private List<Case> measured(String domain) {
        List<Case> selected = new ArrayList<>();
        for (Case testCase : subset(domain, "NOVEL")) {
            selected.add(testCase);
        }
        for (Case testCase : subset(domain, "OUT_OF_SUPPORT")) {
            selected.add(testCase);
        }
        return selected;
    }

    /**
     * The held-out cases of a domain's REVISION — the cases the first promotion did not use.
     *
     * @param domain the domain
     * @return the cases
     */
    private List<ReportProcedureLearner.Case> separateValidationCases(String domain) {
        List<ReportProcedureLearner.Case> separate = new ArrayList<>();
        for (Case held : subset(domain, "NOVEL")) {
            separate.add(new ReportProcedureLearner.Case(held.id(), held.question(),
                    AbstractionLevel.valueOf(held.level())));
        }
        // the OUT_OF_SUPPORT cases are NOT here: the revision was PROPOSED from the failure
        // episode, and the learner refuses a validation case that was already observed —
        // "the validation case asks the question the procedure was proposed from, so it is
        // not held out". The revision is therefore validated on the NOVEL cases alone, which
        // it was not proposed from.
        return separate;
    }

    /** The held-out cases of a domain's promotion. */
    private List<ReportProcedureLearner.Case> validationCases(String domain) {
        List<ReportProcedureLearner.Case> validation = new ArrayList<>();
        for (Case held : subset(domain, "VALIDATION")) {
            validation.add(new ReportProcedureLearner.Case(held.id(), held.question(),
                    AbstractionLevel.valueOf(held.level())));
        }
        return validation;
    }

    /** The domains of the split, in the order the split names them. */
    private List<String> domainsOf() {
        List<String> found = new ArrayList<>();
        for (Case testCase : cases) {
            if (!found.contains(testCase.domain())) {
                found.add(testCase.domain());
            }
        }
        assertThat(found)
                .as("the split carries at least two genuinely different development domains")
                .hasSizeGreaterThanOrEqualTo(2);
        return found;
    }

    /** The files of a domain, as the corpus report names them. */
    private List<String> filesOf(String domain) {
        List<String> files = new ArrayList<>();
        Path into = Path.of("src/test/resources/crj01-adaptive").resolve(domain);
        if (!Files.isDirectory(into)) {
            return files;
        }
        try (var entries = Files.list(into)) {
            entries.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(path -> files.add(path.getFileName().toString()));
        } catch (IOException unreadable) {
            throw new IllegalStateException("the corpus directory could not be read", unreadable);
        }
        return files;
    }

    /** The bytes of one corpus file, as the classpath holds them. */
    private static byte[] corpusBytes(String domain, String file) throws IOException {
        try (InputStream stream = AdaptiveCompetenceServedTest.class
                .getResourceAsStream(CORPUS_RESOURCE + domain + "/" + file)) {
            assertThat(stream).as("the corpus file %s/%s is on the classpath", domain, file)
                    .isNotNull();
            return stream.readAllBytes();
        }
    }

    /** Reads the split. */
    private List<Case> readCases() throws IOException {
        Path split = RepositoryPaths.root().resolve(SPLIT);
        assertThat(split).as("the DEV split %s is present", SPLIT).exists();
        ObjectMapper json = JsonMapper.builder().build();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = json.readValue(Files.readString(split, StandardCharsets.UTF_8),
                Map.class);
        List<Case> read = new ArrayList<>();
        for (Object entry : (List<?>) parsed.get("cases")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            List<String> propositions = new ArrayList<>();
            List<String> claimIds = new ArrayList<>();
            if (map.get("goldClaims") instanceof List<?> golds) {
                for (Object gold : golds) {
                    if (gold instanceof Map<?, ?> goldMap) {
                        propositions.add(String.valueOf(goldMap.get("proposition")));
                        claimIds.add(String.valueOf(goldMap.get("claimId")));
                    }
                }
            }
            read.add(new Case(String.valueOf(map.get("id")), String.valueOf(map.get("domain")),
                    String.valueOf(map.get("class")), String.valueOf(map.get("subset")),
                    String.valueOf(map.get("level")), String.valueOf(map.get("question")),
                    !Boolean.FALSE.equals(map.get("positive")),
                    String.valueOf(map.get("expectedField")),
                    String.valueOf(map.get("expectedDisposition")),
                    map.get("expectedProvenance") == null ? null
                            : String.valueOf(map.get("expectedProvenance")),
                    String.valueOf(map.get("corpusFile")),
                    String.valueOf(map.get("corpusSha256")), propositions, claimIds,
                    String.valueOf(map.get("idempotencyKey"))));
        }
        assertThat(read).as("the split carries cases").isNotEmpty();
        return read;
    }

    /**
     * Refreshes the retained evidence directory's manifest.
     *
     * <p><b>Why the suite owns it.</b> The suite rewrites the row artefacts on every run,
     * and the manifest pins their digests — so a run that does not refresh the manifest
     * leaves the evidence directory BROKEN, and the gate that verifies it fails on a
     * repository that is in fact fine. The producer of a file owns the record of that
     * file's digest; the manifest still detects every change made AFTER the run, which is
     * what it is for.</p>
     */
    private void refreshRetainedManifest() throws IOException {
        Path directory = RepositoryPaths.root().resolve(EVIDENCE_DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<String> names = new ArrayList<>();
        try (var entries = Files.list(directory)) {
            entries.filter(path -> path.getFileName().toString().endsWith(".md")
                            || path.getFileName().toString().endsWith(".json")
                            || path.getFileName().toString().endsWith(".xml")
                            || path.getFileName().toString().endsWith(".py"))
                    .sorted()
                    .forEach(path -> names.add(path.getFileName().toString()));
        }
        StringBuilder manifest = new StringBuilder();
        for (String name : names) {
            if ("CHECKSUMS.sha256".equals(name)) {
                continue;
            }
            manifest.append(sha256(Files.readAllBytes(directory.resolve(name)))).append("  ")
                    .append(name).append("\n");
        }
        Files.writeString(directory.resolve("CHECKSUMS.sha256"), manifest.toString(),
                StandardCharsets.UTF_8);
    }

    /** The digest of some bytes. */
    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /**
     * The revision experiment of a domain: a DISTINCT subsequent task, answered before the
     * revision and after it.
     *
     * @param domain    the domain the experiment ran in
     * @param workspace the workspace the experiment ran in
     * @param subsequent the distinct subsequent task
     * @param before    the journey that served the task BEFORE the revision
     * @param failure   the failure episode's journey
     * @param after     the journey that served the task AFTER the revision
     * @param first     the outcome of the first promotion
     * @param outcome   the outcome of the revision's promotion
     * @param refused   the refused attempt at the revision
     */
    private record RevisionExperiment(String domain, String workspace, Case subsequent,
            ArtifactReportResource.JourneyResult before,
            ArtifactReportResource.JourneyResult failure,
            ArtifactReportResource.JourneyResult after,
            ReportProcedureLearner.Outcome first,
            ReportProcedureLearner.Outcome outcome,
            ReportProcedureLearner.Outcome refused) {
    }

    /**
     * One measured row of the cost grid.
     *
     * @param cell             the cell the row belongs to
     * @param domain           the domain
     * @param corpusSize       the corpus size of the cell
     * @param supportDocuments the number of documents the support covers
     * @param arm              the arm
     * @param pass             the pass: cold or warm
     * @param caseId           the case
     * @param methodId         the method that actually ran, read from the journey's trace
     * @param grounded         whether the answer cites the case's gold
     * @param citations        the number of claims the answer cited
     * @param wallNanos        the wall time of the reading
     * @param cpuNanos         the CPU time of the reading
     * @param fallbackReason   the reason a procedure was not applicable, when there was one
     * @param citedClaimIds    the claims the answer cited
     * @param effectVerified   whether the independent reader verified the effect
     */
    private record CostRow(String cell, String domain, String corpusSize, int supportDocuments,
            String arm, String pass, String caseId, String methodId, boolean grounded,
            int citations, long wallNanos, long cpuNanos, String fallbackReason,
            List<String> citedClaimIds, boolean effectVerified) {
    }

    /**
     * One cell of the grid.
     *
     * @param key              the cell's key
     * @param domain           the domain
     * @param size             the corpus size
     * @param supportCount     the number of documents the support covers
     * @param workspace        the workspace the cell runs in
     * @param documents        the corpus of the cell
     * @param procedure        the promoted procedure, or null when the promotion was refused
     * @param promotion        the promotion outcome
     * @param matched          the matched cases of the cell
     * @param outOfSupport     the out-of-support controls of the domain
     */
    private record Cell(String key, String domain, String size, int supportCount, String workspace,
            List<String> documents, Procedure procedure, ReportProcedureLearner.Outcome promotion,
            List<Case> matched, List<Case> outOfSupport) {
    }

    /**
     * One case of the split.
     *
     * @param id                 the case's id
     * @param domain             the domain the case belongs to
     * @param caseClass          the class the case belongs to
     * @param subset             the subset the case belongs to
     * @param level              the abstraction level the case is asked at
     * @param question           the question
     * @param positive           whether the case expects an answer
     * @param expectedField      the disposition field a negative case is scored against
     * @param expectedDisposition the expected disposition, for a negative case
     * @param expectedProvenance the expected refuting source, for a negative case
     * @param corpusFile         the corpus file the gold comes from
     * @param corpusSha256       the digest of that file's bytes
     * @param goldPropositions   the gold propositions, verbatim
     * @param goldClaimIds       the gold's claim identifiers
     * @param idempotencyKey     the key the request carries
     */
    private record Case(String id, String domain, String caseClass, String subset, String level,
            String question, boolean positive, String expectedField, String expectedDisposition,
            String expectedProvenance, String corpusFile, String corpusSha256,
            List<String> goldPropositions, List<String> goldClaimIds, String idempotencyKey) {
    }
}
