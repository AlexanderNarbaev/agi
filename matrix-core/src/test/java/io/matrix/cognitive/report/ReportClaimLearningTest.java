package io.matrix.cognitive.report;

import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.knowledge.AbstractionLevel;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learned claims of CRJ-01's arm C: the producer and its gates.
 *
 * <p><b>Why the workspace is built from an IMPORTED corpus.</b> The learned claim may assert
 * only what its source <b>literally contains</b>, and that is a property of the corpus rather
 * than of the code: the bundled photosynthesis package holds <b>paraphrases</b> (measured —
 * {@code BundledCorpusSpanMeasurementTest}), so the containment gate would refuse every
 * candidate there and nothing would be proved. The research pool is imported through the
 * <b>production acquisition path</b>, and its claims <b>are</b> spans (measured —
 * {@code ImportedCorpusSpanMeasurementTest}: 483 of 540).</p>
 *
 * <p><b>What this suite pins.</b> That each gate can refuse, and that a candidate which
 * passes them all is promoted with a version and a provenance. A gate that cannot refuse is
 * not a gate.</p>
 */
class ReportClaimLearningTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

    /** The instant the promotion carries. */
    private static final String PROMOTED_AT = "2026-11-21T00:00:00Z";

    @Test
    @DisplayName("a claim the reference carries on every held-out case is promoted, with a version")
    void theCandidateIsPromoted(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        try {
            List<ReportClaimLearner.Observation> observations = fixture.observations();
            List<ReportClaimLearner.Case> cases = fixture.cases(5);
            String claimId = fixture.citedClaimId();

            ReportClaimLearner.Outcome outcome = fixture.learner().learn(WORKSPACE, observations,
                    cases, Set.of(), PROMOTED_AT);

            assertThat(outcome.promoted())
                    .as("the candidate is carried by the reference on every case and its "
                            + "proposition is a span of its document: " + outcome.reason())
                    .isTrue();
            assertThat(outcome.claim()).isNotNull();
            assertThat(outcome.claim().claimId()).isEqualTo(claimId);
            assertThat(outcome.claim().version()).isEqualTo(LearnedClaim.FIRST_VERSION);
            assertThat(outcome.claim().validationCases()).isEqualTo(5);
            assertThat(outcome.claim().agreement()).isEqualTo(1.0);
            assertThat(outcome.claim().learnedInEpisode()).isNotBlank();
            assertThat(outcome.claim().validationNote()).contains("verbatim");
            assertThat(fixture.store().learnedClaims(WORKSPACE))
                    .as("the claim is durable, with its provenance")
                    .hasSize(1);
        } finally {
            fixture.close();
        }
    }

    @Test
    @DisplayName("the quarantine gate fails closed when the list is absent")
    void theAbsentQuarantineRefuses(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        try {
            ReportClaimLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                    fixture.observations(), fixture.cases(5), null, PROMOTED_AT);

            assertThat(outcome.promoted()).isFalse();
            assertThat(outcome.reason()).contains("quarantine list is absent");
            assertThat(fixture.store().learnedClaims(WORKSPACE)).isEmpty();
        } finally {
            fixture.close();
        }
    }

    @Test
    @DisplayName("a quarantined episode is not learning-eligible")
    void theQuarantinedEpisodeIsRefused(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        try {
            List<ReportClaimLearner.Observation> observations = fixture.observations();
            Set<String> quarantined = new LinkedHashSet<>();
            quarantined.add(observations.get(0).episodeId());

            ReportClaimLearner.Outcome outcome = fixture.learner().learn(WORKSPACE, observations,
                    fixture.cases(5), quarantined, PROMOTED_AT);

            assertThat(outcome.promoted()).isFalse();
            assertThat(outcome.reason()).contains("is quarantined");
        } finally {
            fixture.close();
        }
    }

    @Test
    @DisplayName("a validation case that is one of the observed episodes is not held out")
    void theObservedCaseIsRefused(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        try {
            List<ReportClaimLearner.Observation> observations = fixture.observations();
            List<ReportClaimLearner.Case> cases = new ArrayList<>(fixture.cases(5));
            cases.set(0, new ReportClaimLearner.Case(observations.get(0).episodeId(),
                    observations.get(0).question(), AbstractionLevel.INTRODUCTORY));

            ReportClaimLearner.Outcome outcome = fixture.learner().learn(WORKSPACE, observations,
                    cases, Set.of(), PROMOTED_AT);

            assertThat(outcome.promoted()).isFalse();
            assertThat(outcome.reason()).contains("not held out");
        } finally {
            fixture.close();
        }
    }

    @Test
    @DisplayName("a validation case that asks the proposed question is not held out")
    void theProposedQuestionIsRefused(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        try {
            List<ReportClaimLearner.Observation> observations = fixture.observations();
            List<ReportClaimLearner.Case> cases = new ArrayList<>(fixture.cases(5));
            cases.set(0, new ReportClaimLearner.Case("case-same-question",
                    observations.get(0).question(), AbstractionLevel.INTRODUCTORY));

            ReportClaimLearner.Outcome outcome = fixture.learner().learn(WORKSPACE, observations,
                    cases, Set.of(), PROMOTED_AT);

            assertThat(outcome.promoted()).isFalse();
            assertThat(outcome.reason()).contains("not held out");
        } finally {
            fixture.close();
        }
    }

    @Test
    @DisplayName("too few held-out cases refuse the promotion")
    void tooFewCasesAreRefused(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        try {
            ReportClaimLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                    fixture.observations(), fixture.cases(2), Set.of(), PROMOTED_AT);

            assertThat(outcome.promoted()).isFalse();
            assertThat(outcome.reason()).contains("at least " + ReportClaimLearner.MINIMUM_CASES);
        } finally {
            fixture.close();
        }
    }

    @Test
    @DisplayName("a claim the reference does not carry is refused, naming the case")
    void theUncarriedClaimIsRefused(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        try {
            // a case the reference does not answer with the claim
            List<ReportClaimLearner.Case> cases = new ArrayList<>(fixture.cases(5));
            cases.set(4, new ReportClaimLearner.Case("case-elsewhere",
                    "What is the boiling point of water at sea level?",
                    AbstractionLevel.INTRODUCTORY));

            ReportClaimLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                    fixture.observations(), cases, Set.of(), PROMOTED_AT);

            assertThat(outcome.promoted()).isFalse();
            assertThat(outcome.reason()).contains("not among the claims the reference cited");
        } finally {
            fixture.close();
        }
    }

    @Test
    @DisplayName("a proposition that is a paraphrase of its document is refused")
    void theParaphraseIsRefused(@TempDir Path directory) throws Exception {
        Fixture fixture = new Fixture(directory);
        try {
            // a claim whose proposition is NOT a span of its source's text
            String paraphrase = fixture.paraphraseClaimId();
            assertThat(paraphrase).as("the workspace holds a claim that is not a span").isNotBlank();

            List<ReportClaimLearner.Observation> observations =
                    fixture.observations(paraphrase);
            ReportClaimLearner.Outcome outcome = fixture.learner().learn(WORKSPACE, observations,
                    fixture.cases(5), Set.of(), PROMOTED_AT);

            assertThat(outcome.promoted())
                    .as("a learned claim may assert only what the document literally contains")
                    .isFalse();
            assertThat(outcome.reason()).contains("does not occur verbatim");
        } finally {
            fixture.close();
        }
    }

    /** The workspace, the store and the observations the learner reads. */
    private static final class Fixture {

        private final KnowledgeWorkbench bench;
        private final CognitiveService cognitive;
        private final List<Source> sources;

        Fixture(Path directory) throws Exception {
            Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
            bench = KnowledgeWorkbench.at(database.toString());
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            cognitive = new CognitiveService();
            cognitive.workbench(bench);
            sources = List.copyOf(bench.workspace(WORKSPACE).sources());
        }

        CognitiveStore store() {
            return cognitive.store();
        }

        ReportClaimLearner learner() {
            return new ReportClaimLearner(bench, cognitive.store());
        }

        /**
         * The claim the observations cite: one the REFERENCE cites for a held-out question
         * and whose proposition IS a span of its source.
         *
         * <p>Both halves matter: a claim the reference never cites could not pass the
         * agreement gate, and a claim that is not a span could not pass the containment
         * gate.</p>
         */
        String citedClaimId() {
            var answer = bench.ask(WORKSPACE, "What is plate tectonics, held-out 0?",
                    AbstractionLevel.INTRODUCTORY);
            for (Claim claim : answer.claims()) {
                Source source = sourceOf(claim.introducedBySourceId());
                if (source != null && source.excerpt() != null
                        && source.excerpt().contains(claim.proposition())) {
                    return claim.id();
                }
            }
            throw new IllegalStateException("the pool holds no cited claim that is a span");
        }

        /** A claim whose proposition is NOT a span of its source's text. */
        String paraphraseClaimId() {
            for (Claim claim : bench.workspace(WORKSPACE).claims()) {
                Source source = sourceOf(claim.introducedBySourceId());
                if (source != null && source.excerpt() != null
                        && !source.excerpt().contains(claim.proposition())) {
                    return claim.id();
                }
            }
            throw new IllegalStateException("the pool holds no claim that is not a span");
        }

        /** The observations: journeys that all cited the same claim. */
        List<ReportClaimLearner.Observation> observations() {
            return observations(citedClaimId());
        }

        /** The observations, citing a named claim. */
        List<ReportClaimLearner.Observation> observations(String claimId) {
            List<ReportClaimLearner.Observation> observations = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                observations.add(new ReportClaimLearner.Observation("episode-" + index,
                        "What is plate tectonics, episode " + index + "?",
                        AbstractionLevel.INTRODUCTORY, List.of(claimId),
                        sources.stream().map(Source::id).toList()));
            }
            return observations;
        }

        /** The held-out cases: questions whose answers carry the claim. */
        List<ReportClaimLearner.Case> cases(int count) {
            List<ReportClaimLearner.Case> cases = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                cases.add(new ReportClaimLearner.Case("case-" + index,
                        "What is plate tectonics, held-out " + index + "?",
                        AbstractionLevel.INTRODUCTORY));
            }
            return cases;
        }

        private Source sourceOf(String sourceId) {
            for (Source source : sources) {
                if (source.id().equals(sourceId)) {
                    return source;
                }
            }
            return null;
        }

        void close() {
            bench.close();
        }
    }
}
