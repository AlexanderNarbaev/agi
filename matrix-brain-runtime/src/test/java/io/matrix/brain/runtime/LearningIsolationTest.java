package io.matrix.brain.runtime;

import io.matrix.bir.BirRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC-INTEGRITY P0.4 — evaluation, probe and synthetic records must never
 * enter durable organic learning, and the already-contaminated records must stay
 * auditable.
 *
 * <p><b>The defect this test exists for.</b> The machine's real episodic log grew
 * from 108 records to 226: a collection harness wrote 118 deliberately constructed
 * executions into the production log through the production write path, and two
 * frozen evaluation probes ({@code "stolitsa frantsii"},
 * {@code "Moskva stolitsa Rossii?"}) were already there. The learner then trained on
 * its own examination paper. The record carried no provenance, so nothing in the
 * system could tell an evaluation probe from a user's question.</p>
 *
 * <p><b>What is asserted here.</b></p>
 * <ol>
 *   <li>an evaluation-tagged record IS written durably and does NOT enter the
 *       learning window;</li>
 *   <li>an organic record still enters the window — and it does so with NO
 *       configuration, because an absent declaration must not silently disable
 *       learning;</li>
 *   <li>a legacy record written before the tag existed is still admitted, and reads
 *       back exactly as before (backward compatibility);</li>
 *   <li>the LEAKAGE test: not one record from the evaluation path reaches the
 *       induction input — and the evidence is the actual list of episode ids the
 *       induction was given, not a summary;</li>
 *   <li>the root cause of the two leaked probes is closed: the frozen corpus now
 *       covers the TRANSLITERATED form, which is the form the log actually holds;</li>
 *   <li>the refusal leaves a durable trace: the audit chain records why the record
 *       was kept out of the learning feed.</li>
 * </ol>
 *
 * <p>Article III: no randomness, no wall clock in a decision path, same input ⇒ same
 * verdict.</p>
 */
class LearningIsolationTest {

    /** A grounded, promotable organic exchange. */
    private static final String ORGANIC_INPUT = "Which ocean borders Lisbon?";
    private static final String ORGANIC_REPLY = "Atlantic";

    @Test
    @DisplayName("an evaluation-tagged record is written durably but never enters the learning window")
    void evaluationRecordIsDurableButNotLearnable(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);

        PromotionGate.Decision d = log.append("stolitsa frantsii", "Paris", 0.95, true,
            List.of("mat:hdc"), EpisodeRole.EVALUATION);

        // 1. durable: the bytes are on disk and the record is readable.
        assertThat(d.promoted()).as("evaluation traffic must never be learning material").isFalse();
        assertThat(d.written()).as("the refusal must still leave a durable trace").isTrue();
        assertThat(d.reason()).isEqualTo(PromotionGate.Reason.NON_ORGANIC_PROVENANCE);
        assertThat(Files.readString(logPath)).contains("stolitsa frantsii");
        assertThat(log.readAll()).hasSize(1);
        assertThat(log.readAll().get(0).role()).isEqualTo(EpisodeRole.EVALUATION);

        // 2. not learnable: the window is EMPTY even though the log holds the record.
        assertThat(log.learningCandidates()).isEmpty();
        assertThat(log.learningWindow()).isEmpty();
        assertThat(log.appendedCount()).isEqualTo(1);
        assertThat(log.recordedNotAdmittedCount()).isEqualTo(1);
        assertThat(log.rejectedCount()).as("it was written, not rejected").isZero();
    }

    @Test
    @DisplayName("an organic record still enters the learning window, with no configuration")
    void organicRecordStillEntersTheWindow(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        // No quarantine file, no role declaration, no configuration of any kind.
        EpisodicLog log = new EpisodicLog(logPath);

        PromotionGate.Decision d = log.append(ORGANIC_INPUT, ORGANIC_REPLY, 0.82, true,
            List.of("mat:hdc:geo-atlantic"));

        assertThat(d.promoted()).as("a grounded retrieval must still be promotable").isTrue();
        assertThat(d.written()).isTrue();
        assertThat(log.learningCandidates()).hasSize(1);
        assertThat(log.learningWindow()).hasSize(1);
        assertThat(log.learningWindow().get(0).input()).isEqualTo(ORGANIC_INPUT);
    }

    @Test
    @DisplayName("the window reaches past an excluded record instead of stopping at it")
    void theWindowReachesPastTheContamination(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);

        log.append("organic one", "answer one", 0.9, true, List.of("mat:hdc"));
        log.append("organic two", "answer two", 0.9, true, List.of("mat:hdc"));
        for (int i = 0; i < 5; i++) {
            log.append("probe " + i, "Paris", 0.9, true, List.of("mat:hdc"),
                EpisodeRole.EVALUATION);
        }
        log.append("organic three", "answer three", 0.9, true, List.of("mat:hdc"));

        List<EpisodicLog.Entry> window = log.learningWindow();
        assertThat(window).hasSize(3);
        assertThat(window).extracting(EpisodicLog.Entry::input)
            .containsExactly("organic one", "organic two", "organic three");
        assertThat(window).allSatisfy(e ->
            assertThat(e.role().learningEligible()).isTrue());
    }

    @Test
    @DisplayName("LEAKAGE: no evaluation record can reach the induction input")
    void noEvaluationRecordReachesTheInductionInput(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);

        // 30 organic records and 30 evaluation records, interleaved: the evaluation
        // records are the LAST ones in the log, so the naive "last 20" window is
        // entirely evaluation traffic.
        //
        // The organic records are all ACCEPTED, so all 30 reach the log (the
        // production gate refuses a vetoed episode, which is a different mechanism and
        // is exercised elsewhere). Their confidence alternates, so the window is
        // class-balanced in the production sense.
        for (int i = 0; i < 30; i++) {
            log.append("organic question " + i, "organic answer " + i,
                i % 2 == 0 ? 0.9 : 0.2, true, List.of("mat:hdc"));
        }
        List<String> evaluationIds = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            log.append("evaluation probe " + i, "Paris",
                i % 2 == 0 ? 0.9 : 0.2, i % 2 == 0, List.of("mat:hdc"),
                EpisodeRole.EVALUATION);
            evaluationIds.add(log.readAll().get(log.readAll().size() - 1).id());
        }

        // The naive window (what the learner used to see) is all evaluation traffic.
        List<EpisodicLog.Entry> rawWindow =
            LearningAdmission.window(log.readAll(), KMaxEnforcer.K_MAX);
        assertThat(rawWindow).hasSize(20);
        assertThat(rawWindow).allSatisfy(e ->
            assertThat(e.role()).isEqualTo(EpisodeRole.EVALUATION));

        // The PRODUCTION induction now runs through the admission gate.
        BirRegistry registry = new BirRegistry();
        RealSleepScheduler scheduler = new RealSleepScheduler(
            new io.matrix.memory.HierarchicalMemory(),
            new io.matrix.lifecycle.ConsolidationCycle(),
            new io.matrix.federation.Anonymizer(2), 5,
            log, registry, new RuleInductionEngine(registry, null),
            new EpisodeFeatureExtractor());
        RealSleepScheduler.DreamReport dream = scheduler.triggerNow();
        scheduler.close();

        // The evidence is the ACTUAL list of ids the induction was given.
        assertThat(dream.inducedEpisodeIds)
            .as("no evaluation record may be offered to the induction")
            .doesNotContainAnyElementsOf(evaluationIds);
        assertThat(dream.inducedEpisodeIds).hasSize(20);
        assertThat(dream.inducedEpisodeIds).allSatisfy(id ->
            assertThat(evaluationIds).doesNotContain(id));
        assertThat(dream.episodesAdmitted).isEqualTo(30);
        assertThat(dream.episodesExcluded).isEqualTo(30);
        assertThat(dream.notes).anyMatch(n -> n.contains("admission=LearningAdmission"));
    }

    @Test
    @DisplayName("the induction engine refuses non-organic records even if the gate is bypassed")
    void theEngineIsASecondLineOfDefence(@TempDir Path dir) {
        BirRegistry registry = new BirRegistry();
        RuleInductionEngine engine = new RuleInductionEngine(registry, null);
        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();

        List<String> ids = List.of("ep-eval-1", "ep-organic-1");
        long[][] features = new long[][] {
            extractor.encode("evaluation probe"), extractor.encode("organic question") };
        boolean[] labels = new boolean[] { true, false };
        List<EpisodeRole> roles = List.of(EpisodeRole.EVALUATION, EpisodeRole.ORGANIC);

        RuleInductionEngine.RuleInductionResult result =
            engine.induce(ids, features, labels, roles);

        assertThat(result.usedCount()).as("only the organic record may be trained on").isEqualTo(1);
        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.provenance()).doesNotContain("ep-eval-1");
        assertThat(result.provenance()).contains("ep-organic-1");
    }

    @Test
    @DisplayName("the frozen corpus now covers the transliterated probe the log actually holds")
    void theTransliteratedProbeIsFirewalledAtTheWritePath(@TempDir Path dir) {
        // The root cause of the leak: the battery holds "столица франции", the log
        // holds "stolitsa frantsii", and the firewall compared only the Cyrillic form.
        assertThat(PromotionGate.isFrozenProbe("столица франции")).isTrue();
        assertThat(PromotionGate.isFrozenProbe("Moskva stolitsa Rossii?")).isTrue();
        assertThat(PromotionGate.isFrozenProbe("stolitsa anglii")).isTrue();
        assertThat(PromotionGate.transliteratedCorpusSize())
            .as("the transliterated corpus must not be empty").isGreaterThan(0);

        // The write path refuses the transliterated probe as untagged organic traffic.
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        PromotionGate.Decision d = log.append("Moskva stolitsa Rossii?", "Moscow", 0.99, true,
            List.of("mat:hdc"));
        assertThat(d.promoted()).isFalse();
        assertThat(d.reason()).isEqualTo(PromotionGate.Reason.EVAL_PROBE);
        assertThat(d.written()).isFalse();
        assertThat(Files.exists(logPath))
            .as("an untagged probe must not reach the log at all").isFalse();
    }

    @Test
    @DisplayName("a legacy record written before the tag existed reads back exactly as before")
    void legacyRecordsAreReadExactlyAsBefore(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        String historical = "{\"id\":\"ep-old\",\"input\":\"legacy\",\"reply\":\"legacy\","
            + "\"confidence\":0.5,\"accepted\":true,\"modulators\":[],\"ts\":1}";
        Files.writeString(logPath, historical + "\n", StandardCharsets.UTF_8);

        List<EpisodicLog.Entry> read = new EpisodicLog(logPath).readAll();
        assertThat(read).hasSize(1);
        assertThat(read.get(0).input()).isEqualTo("legacy");
        assertThat(read.get(0).reply()).isEqualTo("legacy");
        assertThat(read.get(0).confidence()).isEqualTo(0.5);
        assertThat(read.get(0).accepted()).isTrue();
        assertThat(read.get(0).modulatorsFired()).isEmpty();
        assertThat(read.get(0).tsMillis()).isEqualTo(1L);
        // The provenance is unknown, and it is labelled honestly.
        assertThat(read.get(0).role()).isEqualTo(EpisodeRole.LEGACY_UNTAGGED);
        // A legacy record is still learnable: an absent tag must not silently
        // disable learning on a log written before the tag existed.
        assertThat(read.get(0).role().learningEligible()).isTrue();
        assertThat(new EpisodicLog(logPath).learningCandidates()).hasSize(1);
    }

    @Test
    @DisplayName("the refusal leaves a durable trace in the audit chain")
    void theRefusalIsAuditable(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        AuditChain chain = new AuditChain();
        EpisodicLog log = new EpisodicLog(logPath, chain);

        log.append("organic question", "organic answer", 0.9, true, List.of("mat:hdc"));
        log.append("evaluation probe", "Paris", 0.9, true, List.of("mat:hdc"),
            EpisodeRole.EVALUATION);

        List<AuditChain.Entry> entries = chain.all();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).action()).isEqualTo("episode.promoted");
        assertThat(entries.get(1).action()).isEqualTo("episode.recorded-not-admitted");
        assertThat(entries.get(1).details()).contains("NON_ORGANIC_PROVENANCE");
        assertThat(chain.verify()).isTrue();
    }

    @Test
    @DisplayName("the role travels with the mind cycle's result")
    void theRoleTravelsThroughTheCycle(@TempDir Path dir) {
        TrueMindCycle cycle = new TrueMindCycle();
        // EPISTEMIC-INTEGRITY P0.4 (review fix F3): an UNDECLARED cycle declares
        // nothing, so its result is LEGACY_UNTAGGED — not organic. Only an explicit
        // declaration writes "organic".
        MindResult undeclared = cycle.think("Which ocean borders Lisbon?");
        MindResult organic = cycle.think("Which ocean borders Lisbon?",
            EpisodeRole.ORGANIC);
        MindResult experiment = cycle.think("Which ocean borders Lisbon?",
            EpisodeRole.EXPERIMENT);

        assertThat(undeclared.role()).isEqualTo(EpisodeRole.LEGACY_UNTAGGED);
        assertThat(organic.role()).isEqualTo(EpisodeRole.ORGANIC);
        assertThat(experiment.role()).isEqualTo(EpisodeRole.EXPERIMENT);
        // The behaviour is unchanged: the role labels the result, it does not alter
        // the reasoning.
        assertThat(experiment.reply()).isEqualTo(organic.reply());
        assertThat(experiment.trace().size()).isEqualTo(organic.trace().size());
    }

    @Test
    @DisplayName("an undeclared record is not stamped organic — it is labelled UNDECLARED")
    void anUndeclaredRecordIsNotStampedOrganic(@TempDir Path dir) throws Exception {
        // EPISTEMIC-INTEGRITY P0.4 (review fix F3). The defect this pins: an absent
        // declaration used to mean ORGANIC, and the false claim was written into the
        // PERMANENT audit trail — the record on disk carried "role":"organic" for
        // traffic nobody had declared.
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);

        // The five-argument append declares nothing.
        log.append("an evaluation probe that forgot to declare itself", "Paris", 0.95,
            true, List.of("mat:hdc"));

        String bytes = Files.readString(logPath);
        assertThat(bytes)
            .as("an undeclared record must not carry a provenance claim")
            .doesNotContain("\"role\":\"organic\"")
            .contains("\"role\":\"" + EpisodeRole.LEGACY_UNTAGGED.wire() + "\"");
        assertThat(log.readAll().get(0).role()).isEqualTo(EpisodeRole.LEGACY_UNTAGGED);
        // It is still LEARNABLE: an absent declaration must not disable learning.
        assertThat(log.learningCandidates()).hasSize(1);
    }

    @Test
    @DisplayName("only an explicit ORGANIC declaration writes organic")
    void onlyAnExplicitOrganicDeclarationWritesOrganic(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);

        log.append("a real user question", "Atlantic", 0.9, true, List.of("mat:hdc"),
            EpisodeRole.ORGANIC);

        assertThat(Files.readString(logPath)).contains("\"role\":\"organic\"");
        assertThat(log.readAll().get(0).role()).isEqualTo(EpisodeRole.ORGANIC);
        assertThat(log.learningCandidates()).hasSize(1);
    }

    @Test
    @DisplayName("an experiment record written through the production path stays out of the window")
    void theExperimentPathCannotContaminateLearning(@TempDir Path dir) {
        // This is the exact path the C2A collection harness uses.
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        TrueMindCycle cycle = new TrueMindCycle();

        for (int i = 0; i < 25; i++) {
            MindResult result = cycle.think("Which ocean borders Lisbon?",
                EpisodeRole.EXPERIMENT);
            log.append("Which ocean borders Lisbon?", result.reply(), result.confidence(),
                result.accepted(), result.modulatorsFired(), result.role());
        }

        assertThat(log.readAll()).hasSize(25);
        assertThat(log.learningWindow()).isEmpty();
        assertThat(log.learningCandidates()).isEmpty();
        assertThat(log.recordedNotAdmittedCount()).isEqualTo(25);
    }
}
