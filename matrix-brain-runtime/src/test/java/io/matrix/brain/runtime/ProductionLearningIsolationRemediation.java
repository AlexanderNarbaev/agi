package io.matrix.brain.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the PRODUCTION remediation of the already-contaminated
 * records of this machine's real episodic log.
 *
 * <p><b>Why this is a test and not a script.</b> The remediation is an operator
 * action on machine state: it appends quarantine marks to the ledger that sits next
 * to the real log. Making it an opt-in test means the action is reproducible, that
 * its evidence is a JUnit XML, and that the log's integrity is ASSERTED rather than
 * assumed: the log's sha256 is taken before and after, and the test fails if a single
 * byte of it changed.</p>
 *
 * <p><b>What it writes.</b> Two files, both machine state under {@code data/mind/}
 * (the same directory as the log itself, which is gitignored for the same reason):</p>
 * <ul>
 *   <li>{@code episodic.quarantine.ndjson} — the append-only ledger of the marks.
 *       It is reversible with {@code release}, and the marks stay in its history.</li>
 *   <li>{@code episodic.quarantine.ids} — the DECISION, in a reviewable form: one
 *       record id per line, with the rule that named it. It is what makes the
 *       remediation reproducible:
 *       {@code EpisodeQuarantineTool quarantine-ids <log> <ids-file>}.</li>
 * </ul>
 *
 * <p><b>The rule (review fix F5) — by CONTENT, therefore by ID.</b> A record is
 * contaminated when it is a frozen evaluation probe (in the Cyrillic OR the
 * transliterated form the serving path writes) or when its input is one of the
 * collection harness's own designed workload inputs. A POSITION is never used: a
 * position is not an identity, and the same positional rule applied to a grown log
 * would mark a fresh organic interaction and free a contaminated one.</p>
 *
 * <p><b>How to run it.</b> The measured run is opt-in, exactly like the collection
 * harness it is remediating:</p>
 * <pre>
 *   ./gradlew :matrix-brain-runtime:test --tests \
 *       "io.matrix.brain.runtime.ProductionLearningIsolationRemediation" \
 *       -Dmatrix.p0.4.remediate=true --offline
 * </pre>
 */
class ProductionLearningIsolationRemediation {

    /** The property that turns the measured remediation on. */
    private static final String REMEDIATE_PROPERTY = "matrix.p0.4.remediate";

    /**
     * The environment variable that turns the measured remediation on.
     *
     * <p>The environment is the route that works without touching the build file:
     * Gradle propagates a named system property to the test JVM only when the build
     * says so, and the environment is inherited by the test JVM as it is. The C2A
     * collection harness — the thing this remediation cleans up after — uses the same
     * two switches.</p>
     */
    private static final String REMEDIATE_ENV = "MATRIX_P0_4_REMEDIATE";

    /**
     * Whether the operator asked for the remediation.
     *
     * @return true when the remediation is requested
     */
    private static boolean requested() {
        return Boolean.getBoolean(REMEDIATE_PROPERTY)
            || "true".equalsIgnoreCase(System.getenv(REMEDIATE_ENV));
    }

    /** The file the decision is written to, next to the ledger. */
    private static final String DECISION_FILE_NAME = "episodic.quarantine.ids";

    /** The repo root's real log, if this machine has one. */
    private static Path realLog() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path d = start; d != null; d = d.getParent()) {
            Path candidate = d.resolve("data/mind/episodic.ndjson");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    @Test
    @DisplayName("the contaminated records of the real log are quarantined by id")
    void theContaminatedRecordsAreQuarantinedById() throws Exception {
        Path real = realLog();
        assumeTrue(real != null, "this machine has no runtime episodic log");
        assumeTrue(requested(),
            "the remediation writes machine state: it is opt-in "
                + "(-D" + REMEDIATE_PROPERTY + "=true or " + REMEDIATE_ENV + "=true)");

        // ---- the log's digest BEFORE ----------------------------------------
        String before = EpisodeQuarantineTool.sha256(real);
        long bytesBefore = Files.size(real);
        List<EpisodicLog.Entry> all = new EpisodicLog(real).readAll();

        // ---- the rule, by content -------------------------------------------
        Set<String> designedInputs = new LinkedHashSet<>();
        for (C2ARealEpisodeCollectionTest.Work w : C2ARealEpisodeCollectionTest.workload()) {
            designedInputs.add(w.input());
        }
        List<String> decision = new ArrayList<>();
        int probes = 0;
        int designed = 0;
        for (EpisodicLog.Entry e : all) {
            boolean probe = PromotionGate.isFrozenProbe(e.input())
                || PromotionGate.isFrozenProbe(e.reply());
            boolean isDesigned = designedInputs.contains(e.input());
            if (!probe && !isDesigned) continue;
            decision.add(e.id() + "\t" + (probe ? "RULE A frozen evaluation probe"
                : "RULE B designed workload input"));
            if (probe) probes++; else designed++;
        }
        assertThat(decision)
            .as("the machine's log holds contaminated records; if this fails, the "
                + "contamination was already remediated")
            .isNotEmpty();

        // ---- write the decision, then the marks ------------------------------
        Path ledger = EpisodeQuarantine.defaultLedgerPath(real);
        Path decisionFile = ledger.toAbsolutePath().getParent().resolve(DECISION_FILE_NAME);
        Files.writeString(decisionFile,
            String.join("\n", decision) + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledger);
        int marked = 0;
        long stamp = 1L;
        for (String line : decision) {
            String id = line.substring(0, line.indexOf('\t'));
            String reason = line.substring(line.indexOf('\t') + 1);
            if (quarantine.isQuarantined(id)) continue;
            quarantine.quarantine(id, reason, stamp++);
            marked++;
        }

        // ---- the log's digest AFTER: it must be byte-identical ---------------
        String after = EpisodeQuarantineTool.sha256(real);
        long bytesAfter = Files.size(real);
        assertThat(after)
            .as("the remediation must not change a single byte of the log")
            .isEqualTo(before);
        assertThat(bytesAfter).isEqualTo(bytesBefore);
        assertThat(new EpisodicLog(real).readAll()).hasSize(all.size());

        // ---- the numbers ------------------------------------------------------
        List<EpisodicLog.Entry> admitted = LearningAdmission.admitted(all, quarantine);
        List<EpisodicLog.Entry> window =
            LearningAdmission.window(admitted, KMaxEnforcer.K_MAX);
        int positives = 0;
        for (EpisodicLog.Entry e : window) {
            if (e.confidence() >= 0.7 && e.accepted()) positives++;
        }
        System.out.printf("[REMEDIATION] log=%s sha256=%s (unchanged: %s)%n",
            real.toAbsolutePath(), after, before.equals(after));
        System.out.printf("[REMEDIATION] records=%d ruleA(probe)=%d ruleB(designed)=%d "
                + "marked=%d ledger=%s decision=%s%n",
            all.size(), probes, designed, marked, ledger, decisionFile);
        System.out.printf("[REMEDIATION] ledger lines=%d quarantined ids=%d admitted=%d "
                + "excluded=%d window=%d (+%d/-%d)%n",
            Files.readAllLines(ledger).size(), quarantine.quarantinedIds().size(),
            admitted.size(), all.size() - admitted.size(), window.size(), positives,
            window.size() - positives);
        System.out.println("[REMEDIATION] residual inputs: " + admitted.stream()
            .map(EpisodicLog.Entry::input).distinct().toList());

        // IDEMPOTENT: the ledger is append-only and a second run adds nothing. The
        // invariant is that every record the rule names is IN FORCE, not that this
        // run was the one that marked it.
        assertThat(quarantine.quarantinedIds())
            .as("every record the rule names must be quarantined")
            .containsAll(decision.stream().map(l -> l.substring(0, l.indexOf('\t'))).toList());
        assertThat(quarantine.quarantinedIds().size())
            .as("the ledger holds at least the marks of this decision")
            .isGreaterThanOrEqualTo(marked);
        assertThat(admitted.size()).isLessThan(all.size());
        assertThat(window)
            .as("no quarantined record may be in the window")
            .allSatisfy(e -> assertThat(quarantine.isQuarantined(e.id())).isFalse());
    }
}
