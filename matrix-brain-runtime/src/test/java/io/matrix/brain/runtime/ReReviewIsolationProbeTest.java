package io.matrix.brain.runtime;

import io.matrix.bir.BirRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the INDEPENDENT RE-REVIEW's probe file.
 *
 * <p><b>Why this file exists.</b> The isolation work was implemented and first-reviewed by
 * the same agent. A second pair of eyes has to re-check the claims from the outside, on
 * the machine as it stands, without trusting the implementer's own list. This file
 * re-derives every claim from the artefacts themselves.</p>
 *
 * <p><b>The rule this file obeys.</b> The real machine state — {@code data/mind/episodic.ndjson}
 * and its quarantine ledger — is READ, never written. Every mutation in this file happens
 * on a COPY in a temporary directory. The real log's digest is asserted against the value
 * the record publishes, so a rewrite cannot pass unnoticed.</p>
 *
 * <p><b>What each probe checks.</b> The eight items of the re-review list, plus the three
 * properties the directive names on its own: nothing is deleted or rewritten, the quarantine
 * is reversible, and the rule is stable under the growth of the log.</p>
 */
class ReReviewIsolationProbeTest {

    /** The digest of the real log that the record publishes. */
    private static final String RECORDED_LOG_SHA256 =
            "5732004d24956132712f6a875ef6082faa3b8df251421f07390b500e894ca11b";

    /**
     * How many ids the decision file names.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 — RAISED from 209 to 212 on 2026-11-15, under the
     * owner's answer to question D1-QUARANTINE-RESIDUAL-010 ("A + an
     * owner-authorized exception for the frozen corpus"): the frozen probe corpus was
     * extended to the letter-less arithmetic probes, which marked three more records
     * of the real log ('100-7?' and '7*6' twice). The three marks were appended to
     * the ledger with the operator tool; the log itself was not touched, and the
     * digest and the record count below did not move. The canary
     * (docs-v2/engineering/EPISTEMIC-LEARNING-QUARANTINE-CANARY.json) records the
     * same number.</p>
     */
    private static final int RECORDED_QUARANTINE_COUNT = 212;

    /** How many records the real log holds. */
    private static final int RECORDED_RECORD_COUNT = 226;

    /**
     * How many records the gate admits on the real log.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 — LOWERED from 17 to 14 on 2026-11-15, under the
     * owner's answer to question D1-QUARANTINE-RESIDUAL-010: the extended corpus
     * caught the three letter-less arithmetic records that were still admitted. The
     * 14 that remain are the records the owner decided to leave as they are.</p>
     */
    private static final int RECORDED_ADMITTED_COUNT = 14;

    // ── item 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review item 1 — the remediation is still there and still means something")
    void theRemediationIsStillThereAndStillMeansSomething() throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");

        // the log itself: untouched, and the digest the record publishes
        assertThat(EpisodeQuarantineTool.sha256(real)).isEqualTo(RECORDED_LOG_SHA256);
        List<EpisodicLog.Entry> all = new EpisodicLog(real).readAll();
        assertThat(all).hasSize(RECORDED_RECORD_COUNT);

        // the ledger and the decision: both hold the ids they claim
        Path ledger = EpisodeQuarantine.defaultLedgerPath(real);
        Path decision = ledger.toAbsolutePath().getParent().resolve("episodic.quarantine.ids");
        assertThat(ledger).exists();
        assertThat(decision).exists();
        assertThat(Files.readAllLines(ledger)).hasSize(RECORDED_QUARANTINE_COUNT);
        assertThat(Files.readAllLines(decision)).hasSize(RECORDED_QUARANTINE_COUNT);

        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledger);
        assertThat(quarantine.quarantinedIds()).hasSize(RECORDED_QUARANTINE_COUNT);
        assertThat(quarantine.readable()).as("the ledger must be readable in full").isTrue();

        // THE LEDGER STILL MEANS SOMETHING: re-applying the rule to the real log must
        // reproduce exactly the ids the ledger holds. A ledger that names records the
        // rule no longer names would be a mark with no meaning.
        Set<String> byRule = ruleNames(all);
        assertThat(byRule)
                .as("the ledger must name exactly the records the rule names")
                .containsExactlyInAnyOrderElementsOf(quarantine.quarantinedIds());

        // the gate on the real log: the measured numbers of the record
        LearningAdmission.Assessment assessment = LearningAdmission.assess(
                new EpisodicLog(real), quarantine);
        assertThat(assessment.candidates()).hasSize(RECORDED_ADMITTED_COUNT);
        assertThat(assessment.excluded()).isEqualTo(RECORDED_QUARANTINE_COUNT);
        List<EpisodicLog.Entry> window = assessment.window(KMaxEnforcer.K_MAX);
        assertThat(window).hasSize(RECORDED_ADMITTED_COUNT);
        int positives = 0;
        for (EpisodicLog.Entry e : window) {
            if (e.confidence() >= 0.7 && e.accepted()) positives++;
        }
        assertThat(positives).as("the window must keep both classes").isPositive();
        assertThat(window.size() - positives).as("the window must keep both classes").isPositive();

        // the ledger is GITIGNORED machine state: a cleanup would lose it silently
        Path root = repoRoot();
        assertThat(Files.readString(root.resolve(".gitignore")))
                .as("the ledger lives under a gitignored path")
                .contains("data/mind/*");
    }

    // ── item 2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review item 2 — the rule is stable under the growth of the log")
    void theRuleIsStableUnderGrowth(@TempDir Path dir) throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");

        // a COPY of the machine state: the real log and the real ledger are not touched
        Path copy = dir.resolve("episodic.ndjson");
        Files.copy(real, copy);
        Path copyLedger = EpisodeQuarantine.defaultLedgerPath(copy);
        Files.copy(EpisodeQuarantine.defaultLedgerPath(real), copyLedger);

        EpisodicLog log = new EpisodicLog(copy);
        List<EpisodicLog.Entry> before = log.readAll();
        Set<String> quarantinedBefore = new EpisodeQuarantine(copyLedger).quarantinedIds();

        // append ONE organic interaction to the copy
        log.append("a fresh organic question about the tides", "the tides rise and fall",
                0.9, true, List.of("mat:hdc"), EpisodeRole.ORGANIC);
        List<EpisodicLog.Entry> grown = log.readAll();
        assertThat(grown).hasSize(before.size() + 1);
        String freshId = grown.get(grown.size() - 1).id();

        // re-apply the rule to the GROWN log: it must name exactly the same records
        Set<String> byRule = ruleNames(grown);
        assertThat(byRule)
                .as("the rule must name the same records after the log grew")
                .containsExactlyInAnyOrderElementsOf(quarantinedBefore);
        assertThat(byRule).doesNotContain(freshId);

        // and the OPERATOR TOOL must not mark it either, under either rule
        assertThat(toolOutput("quarantine-probes", copy, copyLedger, "--dry-run"))
                .contains("would quarantine 0 frozen-probe record(s)");
        Path inputs = dir.resolve("designed-inputs.txt");
        Files.writeString(inputs, String.join("\n", designedInputs()), StandardCharsets.UTF_8);
        assertThat(toolOutput("quarantine-inputs", copy, copyLedger, "--dry-run", inputs.toString()))
                .contains("would quarantine 0 record(s) by designed input");

        // and the fresh record is still learnable
        LearningAdmission.Assessment assessment =
                LearningAdmission.assess(log, new EpisodeQuarantine(copyLedger));
        assertThat(assessment.candidates()).extracting(EpisodicLog.Entry::id).contains(freshId);
    }

    // ── item 3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review item 3 — the gate fails closed on a corrupted ledger")
    void theGateFailsClosedOnACorruptedLedger(@TempDir Path dir) throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");

        Path copy = dir.resolve("episodic.ndjson");
        Files.copy(real, copy);
        // the ledger sits NEXT TO THE LOG under its documented name: that is how the
        // scheduler finds it without configuration.
        Path copyLedger = EpisodeQuarantine.defaultLedgerPath(copy);
        List<String> lines = Files.readAllLines(EpisodeQuarantine.defaultLedgerPath(real));
        // tear one line in the middle: the record it named would be lost if the ledger
        // failed OPEN
        int torn = lines.size() / 2;
        lines.set(torn, lines.get(torn).substring(0, lines.get(torn).length() / 2));
        Files.writeString(copyLedger, String.join("\n", lines) + "\n",
                StandardCharsets.UTF_8);

        EpisodeQuarantine quarantine = new EpisodeQuarantine(copyLedger);
        // readable() reports the state of the LAST READ, so the ledger must be read
        // before it can be judged. The production gate reads first and then asks.
        quarantine.quarantinedIds();
        assertThat(quarantine.readable()).as("a torn ledger is not readable").isFalse();
        assertThat(quarantine.malformed()).isTrue();
        assertThat(quarantine.lastSkippedLines()).isPositive();

        // the gate admits NOTHING while the ledger cannot be read
        LearningAdmission.Assessment assessment =
                LearningAdmission.assess(new EpisodicLog(copy), quarantine);
        assertThat(assessment.candidates())
                .as("a malformed ledger must admit nothing")
                .isEmpty();
        assertThat(assessment.ledgerReadable()).isFalse();
        assertThat(assessment.ledgerMalformedLines()).isPositive();

        // the RUNTIME reports it
        try (RealSleepScheduler scheduler = schedulerFor(new EpisodicLog(copy))) {
            RealSleepScheduler.DreamReport dream = scheduler.triggerNow();
            assertThat(dream.quarantineLedgerReadable)
                    .as("the runtime must report the unreadable ledger").isFalse();
            assertThat(dream.quarantineMalformedLines).isPositive();
            assertThat(dream.episodesAdmitted)
                    .as("nothing may be learned from an unreadable ledger").isZero();
        }
    }

    // ── item 4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review item 4 — no declaration means untagged, never organic")
    void noDeclarationMeansUntagged(@TempDir Path dir) throws Exception {
        // every constructor that carries no role
        assertThat(new EpisodicLog.Entry("ep-x", "in", "out", 0.9, true, List.of(), 1L).role())
                .isEqualTo(EpisodeRole.LEGACY_UNTAGGED);
        assertThat(PromotionGate.Candidate.forInteraction("in", "out", 0.9, List.of(), true).role())
                .isEqualTo(EpisodeRole.LEGACY_UNTAGGED);
        assertThat(PromotionGate.Candidate.forRetrieval("in", "out", 0.9, "mat:hdc").role())
                .isEqualTo(EpisodeRole.LEGACY_UNTAGGED);
        assertThat(new MindResult("out", 0.9, 1L, true, List.of(), List.of()).role())
                .isEqualTo(EpisodeRole.LEGACY_UNTAGGED);

        // and the record that is actually WRITTEN carries the untagged label
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        log.append("a plain question about the sky", "the sky is blue", 0.9, true,
                List.of("mat:hdc"));
        List<EpisodicLog.Entry> written = log.readAll();
        assertThat(written).isNotEmpty();
        assertThat(written).allSatisfy(e ->
                assertThat(e.role()).isEqualTo(EpisodeRole.LEGACY_UNTAGGED));

        // SOURCE LEVEL: no role-less path in the brain runtime declares ORGANIC
        Path root = repoRoot();
        List<Path> sources = new ArrayList<>();
        try (var walk = Files.walk(root.resolve("matrix-brain-runtime/src/main/java"))) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }
        assertThat(sources).isNotEmpty();
        List<String> offenders = new ArrayList<>();
        for (Path source : sources) {
            String text = Files.readString(source);
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    continue;
                }
                if (trimmed.contains("EpisodeRole.ORGANIC")) {
                    offenders.add(root.relativize(source) + ": " + trimmed);
                }
            }
        }
        assertThat(offenders)
                .as("the brain runtime must not declare ORGANIC anywhere")
                .isEmpty();
    }

    // ── item 5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review item 5 — training without provenance is refused, and every caller states roles")
    void trainingWithoutProvenanceIsRefused() throws Exception {
        BirRegistry registry = new BirRegistry();
        RuleInductionEngine engine = new RuleInductionEngine(registry, null);
        long[][] features = { { 1L, 2L }, { 3L, 4L } };
        boolean[] labels = { true, false };

        assertThatThrownBy(() -> engine.induce(List.of("ep-1", "ep-2"), features, labels))
                .as("a training call that cannot say where its episodes came from is refused")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provenance");

        // SOURCE LEVEL: every production call site passes the roles
        Path root = repoRoot();
        List<String> offenders = new ArrayList<>();
        for (String module : List.of("matrix-brain-runtime/src/main/java",
                "matrix-api-gateway/src/main/java", "matrix-core/src/main/java")) {
            Path start = root.resolve(module);
            if (!Files.exists(start)) continue;
            try (var walk = Files.walk(start)) {
                for (Path source : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(source);
                    for (String line : text.split("\n")) {
                        String trimmed = line.trim();
                        int at = trimmed.indexOf(".induce(");
                        if (at < 0) continue;
                        if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
                        if (trimmed.substring(0, at).indexOf('"') >= 0) continue;
                        if (trimmed.contains("public ") && trimmed.contains("induce(")) continue;
                        if (!trimmed.contains("roles") && !trimmed.contains(", role")) {
                            offenders.add(root.relativize(source) + ": " + trimmed);
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a production caller trains without roles: %s", offenders)
                .isEmpty();
    }

    // ── item 6 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review item 6 — the three window definitions agree, and the induced ids ARE the window")
    void theThreeWindowDefinitionsAgree(@TempDir Path dir) throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");

        Path copy = dir.resolve("episodic.ndjson");
        Files.copy(real, copy);
        Path copyLedger = EpisodeQuarantine.defaultLedgerPath(copy);
        Files.copy(EpisodeQuarantine.defaultLedgerPath(real), copyLedger);

        EpisodicLog log = new EpisodicLog(copy);
        List<EpisodicLog.Entry> viaLog = log.learningWindow();
        List<EpisodicLog.Entry> viaGate = LearningAdmission.learningWindow(log,
                new EpisodeQuarantine(copyLedger), KMaxEnforcer.K_MAX);
        assertThat(viaLog).isEqualTo(viaGate);

        List<String> induced;
        try (RealSleepScheduler scheduler = schedulerFor(log)) {
            RealSleepScheduler.DreamReport dream = scheduler.triggerNow();
            induced = dream.inducedEpisodeIds;
            assertThat(dream.episodesAdmitted).isEqualTo(RECORDED_ADMITTED_COUNT);
            assertThat(dream.episodesExcluded).isEqualTo(RECORDED_QUARANTINE_COUNT);
        }
        assertThat(induced)
                .as("the ids the induction was given ARE the window's ids")
                .containsExactlyElementsOf(viaLog.stream().map(EpisodicLog.Entry::id).toList());

        // and the OPERATOR TOOL reports the same window
        String scan = toolOutput("scan", copy, copyLedger);
        assertThat(scan)
                .as("the tool must report the same admitted count")
                .contains("admitted=" + RECORDED_ADMITTED_COUNT
                        + " excluded=" + RECORDED_QUARANTINE_COUNT);
        assertThat(scan).contains("window(admitted) : size=" + viaLog.size());
    }

    // ── item 7 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review item 7 — the frozen battery was NOT edited, and its corpus still holds")
    void theFrozenBatteryWasNotEdited() throws Exception {
        Path root = repoRoot();
        String source = Files.readString(root.resolve(
                "matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/EvalBattery.java"));

        // the reverted F9 edit: the unused random handle is still there, on purpose
        assertThat(source)
                .as("the frozen zone must be untouched: the unused Random stays")
                .contains("Random r = new Random(42L);");
        assertThat(source).contains("import java.util.Random;");

        // and the corpus the firewall derives from it still holds
        assertThat(PromotionGate.frozenCorpusSize()).isGreaterThanOrEqualTo(47);
        assertThat(PromotionGate.transliteratedCorpusSize()).isGreaterThanOrEqualTo(5);
        assertThat(EvalBattery.standardBattery())
                .as("the battery must be a pure function of its source")
                .isEqualTo(EvalBattery.standardBattery());
        assertThat(PromotionGate.isFrozenProbe("stolitsa frantsii"))
                .as("the transliterated probe must still be caught").isTrue();
        assertThat(PromotionGate.isFrozenProbe("Moskva stolitsa Rossii?"))
                .as("the transliterated probe must still be caught").isTrue();
    }

    // ── item 8 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review item 8 — the residual is measured and reported, not decided")
    void theResidualIsMeasuredAndReported() throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");

        EpisodeQuarantine quarantine =
                new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(real));
        List<EpisodicLog.Entry> admitted = LearningAdmission.admitted(
                new EpisodicLog(real).readAll(), quarantine);
        assertThat(admitted).hasSize(RECORDED_ADMITTED_COUNT);

        // the residual holds no designed workload input and no frozen probe
        Set<String> designed = designedInputs();
        for (EpisodicLog.Entry e : admitted) {
            assertThat(designed)
                    .as("a designed workload input is still admitted: %s", e.input())
                    .doesNotContain(e.input());
            assertThat(PromotionGate.isFrozenProbe(e.input()))
                    .as("a frozen probe is still admitted: %s", e.input()).isFalse();
            assertThat(PromotionGate.isFrozenProbe(e.reply()))
                    .as("a frozen probe is still admitted: %s", e.reply()).isFalse();
        }

        // the look-alikes of the earlier waves are REPORTED, not decided
        List<String> inputs = admitted.stream().map(EpisodicLog.Entry::input).distinct().toList();
        System.out.println("[RE-REVIEW] residual inputs (" + inputs.size() + "): " + inputs);
        // EPISTEMIC-INTEGRITY P0.4 — CHANGED on 2026-11-15 under the owner's answer to
        // question D1-QUARANTINE-RESIDUAL-010: the frozen corpus was extended to the
        // letter-less arithmetic probes, so "100-7?" and "7*6" are no longer in the
        // residual — they are probes now. The look-alike the owner left as it is
        // ("What is the capital of Chile?") is still reported.
        assertThat(inputs).contains("What is the capital of Chile?");
        assertThat(inputs).doesNotContain("100-7?", "7*6");
        assertThat(quarantine.quarantinedIds())
                .as("the re-review must not change the machine's quarantine")
                .hasSize(RECORDED_QUARANTINE_COUNT);
    }

    // ── the directive's own requirements ─────────────────────────────────────

    @Test
    @DisplayName("P0.4 re-review — the quarantine is reversible, and nothing in the log is deleted")
    void theQuarantineIsReversible(@TempDir Path dir) throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");

        Path copy = dir.resolve("episodic.ndjson");
        Files.copy(real, copy);
        Path copyLedger = dir.resolve("reverse.quarantine.ndjson");
        Files.copy(EpisodeQuarantine.defaultLedgerPath(real), copyLedger);

        EpisodicLog log = new EpisodicLog(copy);
        EpisodeQuarantine quarantine = new EpisodeQuarantine(copyLedger);
        List<EpisodicLog.Entry> all = log.readAll();
        int recordsBefore = all.size();
        String digestBefore = EpisodeQuarantineTool.sha256(copy);

        // quarantine a record that is currently admitted
        String id = all.get(all.size() - 1).id();
        quarantine.quarantine(id, "re-review probe", 9000L);
        assertThat(LearningAdmission.admitted(log.readAll(), quarantine))
                .extracting(EpisodicLog.Entry::id).doesNotContain(id);

        // release it: the record is admitted again and the mark STAYS in the history
        quarantine.release(id, "re-review probe release", 9001L);
        assertThat(LearningAdmission.admitted(log.readAll(), quarantine))
                .extracting(EpisodicLog.Entry::id).contains(id);
        assertThat(Files.readAllLines(copyLedger))
                .as("the quarantine itself stays in the ledger's history")
                .anyMatch(line -> line.contains("\"op\":\"QUARANTINE\"") && line.contains(id));
        assertThat(Files.readAllLines(copyLedger))
                .anyMatch(line -> line.contains("\"op\":\"RELEASE\"") && line.contains(id));

        // nothing was deleted and nothing was rewritten
        assertThat(log.readAll()).hasSize(recordsBefore);
        assertThat(EpisodeQuarantineTool.sha256(copy)).isEqualTo(digestBefore);
    }

    @Test
    @DisplayName("P0.4 re-review — the gate decides without a clock and without randomness")
    void theGateDecidesWithoutAClockOrRandomness() throws Exception {
        Path root = repoRoot();
        for (String file : List.of(
                "matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/LearningAdmission.java",
                "matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/EpisodeRole.java")) {
            String text = Files.readString(root.resolve(file));
            for (String forbidden : List.of("currentTimeMillis", "nanoTime", "new Random",
                    "Math.random", "Instant.now")) {
                assertThat(text)
                        .as("%s must not read a clock or a random source", file)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("P0.4 re-review — an ABSENT ledger admits everything: the real ledger is load-bearing")
    void anAbsentLedgerAdmitsEverything(@TempDir Path dir) throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");

        // a copy with NO ledger next to it: the documented backward-compatible behaviour
        Path copy = dir.resolve("episodic.ndjson");
        Files.copy(real, copy);
        assertThat(EpisodeQuarantine.defaultLedgerPath(copy)).doesNotExist();

        LearningAdmission.Assessment assessment =
                LearningAdmission.assess(new EpisodicLog(copy), new EpisodeQuarantine(
                        EpisodeQuarantine.defaultLedgerPath(copy)));
        assertThat(assessment.candidates())
                .as("an absent ledger means nothing is quarantined")
                .hasSize(RECORDED_RECORD_COUNT);

        // THE CONSEQUENCE, stated as a fact rather than as a warning: the real ledger is
        // load-bearing machine state, and nothing in the repository monitors it. The
        // re-review's own file is the first thing that pins its digest.
        Path realLedger = EpisodeQuarantine.defaultLedgerPath(real);
        assertThat(realLedger).exists();
        assertThat(Files.readAllLines(realLedger)).hasSize(RECORDED_QUARANTINE_COUNT);
    }

    @Test
    @DisplayName("P0.4 re-review — the quarantine is enforced at the GATE, not at the trainer")
    void theQuarantineIsEnforcedAtTheGateNotAtTheTrainer(@TempDir Path dir) throws Exception {
        // The trainer's own filter is by ROLE. A record that is QUARANTINED but whose
        // role is learnable would be trained on if it reached the trainer without
        // passing the gate. The gate is the single entry point; this probe pins that
        // boundary so a future caller that bypasses the gate is visible.
        BirRegistry registry = new BirRegistry();
        RuleInductionEngine engine = new RuleInductionEngine(registry, null);
        long[][] features = { { 1L, 2L }, { 3L, 4L } };
        boolean[] labels = { true, false };
        List<EpisodeRole> roles = List.of(EpisodeRole.ORGANIC, EpisodeRole.ORGANIC);

        assertThat(engine.induce(List.of("ep-1", "ep-2"), features, labels, roles))
                .as("the trainer filters by ROLE, not by quarantine")
                .isNotNull();

        // and the gate is where the quarantine bites: the same record, offered to the
        // gate, is excluded.
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        log.append("a question about the tides", "the tides rise and fall", 0.9, true,
                List.of("mat:hdc"), EpisodeRole.ORGANIC);
        String id = log.readAll().get(0).id();
        Path ledger = EpisodeQuarantine.defaultLedgerPath(logPath);
        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledger);
        quarantine.quarantine(id, "re-review probe", 8000L);
        assertThat(LearningAdmission.admitted(log.readAll(), quarantine)).isEmpty();
        assertThat(LearningAdmission.admitted(log.readAll(), null))
                .as("without the ledger the same record is admitted")
                .hasSize(1);
    }

    @Test
    @DisplayName("P0.4 re-review — the content rule also quarantines a GENUINE record that repeats a designed input")
    void theContentRuleAlsoQuarantinesGenuineRepeats(@TempDir Path dir) throws Exception {
        // RULE B is by CONTENT: it names every record whose INPUT is one of the
        // collection harness's designed workload inputs. A real user who asks the same
        // question as the harness would be quarantined as well. That is the price of a
        // rule that survives the log growing, and the operator has to know it.
        Set<String> designed = designedInputs();
        assertThat(designed).as("the harness must still have designed inputs").isNotEmpty();
        String designedInput = designed.iterator().next();

        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        log.append(designedInput, "a genuine answer", 0.9, true, List.of("mat:hdc"),
                EpisodeRole.ORGANIC);
        String id = log.readAll().get(0).id();

        // the operator tool, on this log, names the record
        Path ledger = EpisodeQuarantine.defaultLedgerPath(logPath);
        Path inputs = dir.resolve("designed-inputs.txt");
        Files.writeString(inputs, String.join("\n", designed), StandardCharsets.UTF_8);
        String output = toolOutput("quarantine-inputs", logPath, ledger, "--dry-run",
                inputs.toString());
        assertThat(output).contains("would quarantine 1 record(s) by designed input");

        // and the gate excludes it: a genuine record, excluded by content
        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledger);
        quarantine.quarantine(id, "RULE B designed workload input", 7000L);
        assertThat(LearningAdmission.admitted(log.readAll(), quarantine))
                .as("a genuine record that repeats a designed input is excluded")
                .isEmpty();
    }

    @Test
    @DisplayName("P0.4 re-review — a ledger line whose id names NO record is accepted silently")
    void aLedgerLineThatNamesNoRecordIsAcceptedSilently(@TempDir Path dir) throws Exception {
        // The ledger's parser is a lenient field extractor: a line is accepted when it
        // parses, and an id that names no record is still a "readable" ledger. The
        // consequence is that a mark whose id does not match a record exactly (a
        // hand-edited ledger, a padded id) is SILENTLY INEFFECTIVE: the ledger says
        // "readable", and the record it was meant to exclude is admitted.
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        log.append("a question about the tides", "the tides rise and fall", 0.9, true,
                List.of("mat:hdc"), EpisodeRole.ORGANIC);
        String id = log.readAll().get(0).id();

        Path ledger = EpisodeQuarantine.defaultLedgerPath(logPath);
        Files.writeString(ledger,
                "{\"op\":\"QUARANTINE\",\"id\":\"" + id + " \",\"reason\":\"padded id\",\"ts\":1}\n",
                StandardCharsets.UTF_8);

        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledger);
        assertThat(quarantine.readable())
                .as("the ledger parses, so it reports itself readable").isTrue();
        assertThat(quarantine.quarantinedIds())
                .as("the padded id does not match the record")
                .doesNotContain(id);
        assertThat(LearningAdmission.admitted(log.readAll(), quarantine))
                .as("the record the mark was meant to exclude is ADMITTED")
                .hasSize(1);
    }

    // ── the plumbing ─────────────────────────────────────────────────────────

    /** The repo root, found by walking up from the working directory. */
    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path d = start; d != null; d = d.getParent()) {
            if (Files.isRegularFile(d.resolve(".gitignore")) && Files.isDirectory(d.resolve("data"))) {
                return d;
            }
        }
        return start;
    }

    /** This machine's real episodic log, if it has one. */
    private static Path realLog() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path d = start; d != null; d = d.getParent()) {
            Path candidate = d.resolve("data/mind/episodic.ndjson");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    /** The designed workload inputs of the collection harness: RULE B. */
    private static Set<String> designedInputs() {
        Set<String> inputs = new LinkedHashSet<>();
        for (C2ARealEpisodeCollectionTest.Work work : C2ARealEpisodeCollectionTest.workload()) {
            inputs.add(work.input());
        }
        return inputs;
    }

    /** The ids the remediation rule names: RULE A (frozen probe) OR RULE B (designed input). */
    private static Set<String> ruleNames(List<EpisodicLog.Entry> all) {
        Set<String> designed = designedInputs();
        Set<String> ids = new LinkedHashSet<>();
        for (EpisodicLog.Entry e : all) {
            boolean probe = PromotionGate.isFrozenProbe(e.input())
                    || PromotionGate.isFrozenProbe(e.reply());
            if (probe || designed.contains(e.input())) ids.add(e.id());
        }
        return ids;
    }

    /** A scheduler over the given log, with the production induction engine. */
    private static RealSleepScheduler schedulerFor(EpisodicLog log) {
        BirRegistry registry = new BirRegistry();
        return new RealSleepScheduler(
                new io.matrix.memory.HierarchicalMemory(),
                new io.matrix.lifecycle.ConsolidationCycle(),
                new io.matrix.federation.Anonymizer(2), 5,
                log, registry, new RuleInductionEngine(registry, null),
                new EpisodeFeatureExtractor());
    }

    /** Runs the operator tool and returns what it printed. */
    private static String toolOutput(String command, Path log, Path ledger, String... extra) {
        List<String> args = new ArrayList<>();
        args.add(command);
        args.add(log.toString());
        args.add("--ledger");
        args.add(ledger.toString());
        args.addAll(List.of(extra));
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            EpisodeQuarantineTool.run(args.toArray(new String[0]));
        } finally {
            System.setOut(original);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
