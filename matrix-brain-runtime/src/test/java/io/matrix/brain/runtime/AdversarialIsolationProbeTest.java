package io.matrix.brain.runtime;

import io.matrix.bir.BirRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The adversarial read of the isolation, kept as a REGRESSION GUARD.
 *
 * <p>These probes were written to attack the P0.4 change; each of them found a
 * defect. They are kept, and inverted, because a defect that is only written down
 * comes back. Each probe now asserts the FIXED behaviour, so the defect it found
 * cannot return unnoticed.</p>
 *
 * <ol>
 *   <li>P1 — the already-contaminated records are isolated in PRODUCTION (the
 *       quarantine ledger exists next to the real log and the window holds no
 *       quarantined record);</li>
 *   <li>P2 — the probe corpus catches the leaked probes, and the residual is
 *       reported;</li>
 *   <li>P3 — a malformed ledger FAILS CLOSED: the contaminated record does not
 *       re-enter the learning feed;</li>
 *   <li>P4 — the quarantine rule names RECORDS, not positions: appending a fresh
 *       organic interaction does not change what the rule marks;</li>
 *   <li>P5 — an undeclared record is labelled UNDECLARED, not organic, and is still
 *       learnable;</li>
 *   <li>P6 — with no configuration an organic record still reaches the induction.</li>
 * </ol>
 *
 * <p>Every probe works on a COPY of this machine's real episodic log; the real log
 * is never modified.</p>
 */
class AdversarialIsolationProbeTest {

    /** The repo root's real log, if this machine has one. */
    private static Path realLog() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path d = start; d != null; d = d.getParent()) {
            Path candidate = d.resolve("data/mind/episodic.ndjson");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private static Path copyOfRealLog() throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");
        Path work = Files.createTempDirectory("p04-probe-");
        Path copy = work.resolve("episodic.ndjson");
        Files.copy(real, copy, StandardCopyOption.REPLACE_EXISTING);
        return copy;
    }

    @Test
    @DisplayName("P1: the already-contaminated records are isolated in PRODUCTION")
    void theContaminatedRecordsAreIsolatedInProduction() throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");
        Path ledger = EpisodeQuarantine.defaultLedgerPath(real);
        System.out.printf("[P1] real log records=%d, production ledger exists=%s (%s)%n",
            countLines(real), Files.exists(ledger), ledger);

        // The ledger is machine state, next to the log it governs.
        assertThat(Files.exists(ledger))
            .as("PROBE P1: the remediation must exist for the real machine, not only in a "
                + "test's temporary directory")
            .isTrue();

        List<EpisodicLog.Entry> all = new EpisodicLog(copyOfRealLog()).readAll();
        EpisodeQuarantine production = new EpisodeQuarantine(ledger);
        List<EpisodicLog.Entry> admitted = LearningAdmission.admitted(all, production);
        List<EpisodicLog.Entry> window =
            LearningAdmission.window(admitted, KMaxEnforcer.K_MAX);
        System.out.printf("[P1] quarantined=%d admitted=%d of %d, window=%d%n",
            production.quarantinedIds().size(), admitted.size(), all.size(), window.size());

        assertThat(production.quarantinedIds())
            .as("PROBE P1: the contaminated records must be named in the ledger")
            .isNotEmpty();
        assertThat(admitted.size())
            .as("PROBE P1: the contaminated records must be excluded from learning")
            .isLessThan(all.size());
        assertThat(window)
            .as("PROBE P1: no quarantined record may be in the window")
            .allSatisfy(e -> assertThat(production.isQuarantined(e.id())).isFalse());
    }

    @Test
    @DisplayName("P2: the probe corpus catches the leaked probes, and the residual is reported")
    void theProbeCorpusCatchesTheLeakedProbes() throws Exception {
        Path copy = copyOfRealLog();
        List<EpisodicLog.Entry> all = new EpisodicLog(copy).readAll();

        int matched = 0;
        List<String> matchedInputs = new ArrayList<>();
        for (EpisodicLog.Entry e : all) {
            if (PromotionGate.isFrozenProbe(e.input()) || PromotionGate.isFrozenProbe(e.reply())) {
                matched++;
                if (matchedInputs.size() < 8) matchedInputs.add(e.input());
            }
        }
        System.out.printf("[P2] frozen-corpus matches=%d of %d records, sample=%s%n",
            matched, all.size(), matchedInputs);
        System.out.printf("[P2] corpus sizes: cyrillic=%d transliterated=%d%n",
            PromotionGate.frozenCorpusSize(), PromotionGate.transliteratedCorpusSize());

        // The two probes the directive names reached the log in their TRANSLITERATED
        // form; the transliterated corpus is what catches them.
        assertThat(PromotionGate.isFrozenProbe("Moskva stolitsa Rossii?")).isTrue();
        assertThat(PromotionGate.isFrozenProbe("stolitsa frantsii")).isTrue();
        assertThat(matched)
            .as("PROBE P2: the frozen corpus must catch the leaked probes")
            .isGreaterThan(0);
    }

    @Test
    @DisplayName("P3: a malformed ledger FAILS CLOSED")
    void aMalformedLedgerFailsClosed() throws Exception {
        Path work = Files.createTempDirectory("p04-probe-");
        Path logPath = work.resolve("episodic.ndjson");
        Files.writeString(logPath,
            "{\"id\":\"ep-contaminated\",\"input\":\"organic question\",\"reply\":\"r\","
            + "\"confidence\":0.9,\"accepted\":true,\"modulators\":[],\"ts\":1}\n",
            StandardCharsets.UTF_8);

        Path ledger = EpisodeQuarantine.defaultLedgerPath(logPath);
        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledger);
        // A ledger line this build cannot read — the shape a torn write leaves.
        Files.writeString(ledger, "{\"op\":\"QUARANTINE\",\"id\":\"ep-contaminated\","
            + "\"reason\":\"probe\",\"ts\":1", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        boolean quarantined = quarantine.isQuarantined("ep-contaminated");
        int skipped = quarantine.lastSkippedLines();
        List<EpisodicLog.Entry> admitted = new EpisodicLog(logPath).learningCandidates();
        System.out.printf("[P3] truncated ledger line: isQuarantined=%s skippedLines=%d "
            + "readable=%s admitted=%d%n", quarantined, skipped, quarantine.readable(),
            admitted.size());

        assertThat(skipped).isEqualTo(1);
        assertThat(quarantine.readable())
            .as("PROBE P3: the ledger must report that it could not be read in full")
            .isFalse();
        assertThat(admitted)
            .as("PROBE P3: an unreadable exclusion is not an exclusion — the gate fails "
                + "closed and the record does not re-enter the learning feed")
            .isEmpty();
    }

    @Test
    @DisplayName("P4: the quarantine rule names RECORDS, not positions")
    void theQuarantineRuleNamesRecordsNotPositions() throws Exception {
        Path work = Files.createTempDirectory("p04-probe-");
        Path logPath = work.resolve("episodic.ndjson");
        Path ledger = EpisodeQuarantine.defaultLedgerPath(logPath);
        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledger);

        for (int i = 0; i < 3; i++) {
            Files.writeString(logPath,
                "{\"id\":\"ep-" + i + "\",\"input\":\"organic " + i + "\",\"reply\":\"r\","
                + "\"confidence\":0.9,\"accepted\":true,\"modulators\":[],\"ts\":1}\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        List<EpisodicLog.Entry> before = new EpisodicLog(logPath).readAll();
        // The rule names records: ep-1 and ep-2, by id.
        quarantine.quarantine(before.get(1).id(), "named record", 1000L);
        quarantine.quarantine(before.get(2).id(), "named record", 1001L);

        // A FRESH ORGANIC interaction arrives.
        Files.writeString(logPath,
            "{\"id\":\"ep-new-organic\",\"input\":\"a fresh organic question\",\"reply\":\"r\","
            + "\"confidence\":0.9,\"accepted\":true,\"modulators\":[],\"ts\":2}\n",
            StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        Set<String> marked = new LinkedHashSet<>(quarantine.quarantinedIds());
        System.out.printf("[P4] marked=%s%n", marked);

        assertThat(marked)
            .as("PROBE P4: the rule marks the records it named and nothing else")
            .containsExactlyInAnyOrder("ep-1", "ep-2")
            .doesNotContain("ep-new-organic");
        assertThat(new EpisodicLog(logPath).learningCandidates())
            .as("PROBE P4: the fresh organic interaction is still learnable")
            .anySatisfy(e -> assertThat(e.id()).isEqualTo("ep-new-organic"));
    }

    @Test
    @DisplayName("P5: an undeclared record is labelled UNDECLARED and stays learnable")
    void anUndeclaredRecordIsLabelledUndeclared() throws Exception {
        Path work = Files.createTempDirectory("p04-probe-");
        Path logPath = work.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);

        // This is the shape ProductionBrainClient.java:388 used to use.
        log.append("an evaluation probe that forgot to declare itself", "Paris", 0.95, true,
            List.of("mat:hdc"));

        String bytes = Files.readString(logPath);
        System.out.printf("[P5] the record on disk carries: %s%n",
            bytes.substring(bytes.indexOf("\"role\"")));
        assertThat(bytes).doesNotContain("\"role\":\"organic\"");
        assertThat(log.readAll().get(0).role()).isEqualTo(EpisodeRole.LEGACY_UNTAGGED);
        assertThat(log.learningCandidates())
            .as("PROBE P5: an absent declaration must not silently disable learning")
            .hasSize(1);
    }

    @Test
    @DisplayName("P6: with no configuration an organic record still reaches the induction")
    void theOrganicPathStillReachesTheInduction() throws Exception {
        Path work = Files.createTempDirectory("p04-probe-");
        Path logPath = work.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);

        // No ledger, no role declaration, no configuration of any kind.
        log.append("Which ocean borders Lisbon?", "Atlantic", 0.82, true,
            List.of("mat:hdc:geo-atlantic"));

        BirRegistry registry = new BirRegistry();
        try (RealSleepScheduler scheduler = new RealSleepScheduler(
                new io.matrix.memory.HierarchicalMemory(),
                new io.matrix.lifecycle.ConsolidationCycle(),
                new io.matrix.federation.Anonymizer(2), 5,
                log, registry, new RuleInductionEngine(registry, null),
                new EpisodeFeatureExtractor())) {
            RealSleepScheduler.DreamReport dream = scheduler.triggerNow();
            System.out.printf("[P6] admitted=%d excluded=%d inducedIds=%s notes=%s%n",
                dream.episodesAdmitted, dream.episodesExcluded, dream.inducedEpisodeIds,
                dream.notes);
            assertThat(dream.inducedEpisodeIds)
                .as("PROBE P6: the organic record reached the production induction")
                .hasSize(1);
            assertThat(dream.episodesAdmitted).isEqualTo(1);
            assertThat(dream.episodesExcluded).isZero();
            assertThat(dream.quarantineLedgerReadable).isTrue();
        }
    }

    /** How many records the real log holds. Unit: records. */
    private static int countLines(Path path) throws Exception {
        return Files.readAllLines(path).size();
    }
}
