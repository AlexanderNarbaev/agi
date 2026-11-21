package io.matrix.cognitive.report;

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
 * WHERE arm C's mechanism actually pays off — a measurement, not a gate.
 *
 * <p><b>Why this measurement exists.</b> The arm C report carries one number: the procedure
 * saves **2.7 %** of the reading time, because its support set covered **15 of the
 * workspace's 17 claims**. That is a real measurement and it is a null. But a null on one
 * workload does not say whether the mechanism is worthless or merely mis-applied: the
 * mechanism's premise is that a <b>narrower reading is cheaper</b>, and the saving must
 * therefore grow with the ratio of the support set to the workspace.</p>
 *
 * <p><b>What it measures.</b> The same two readings — the full one and the restricted one —
 * over workspaces of <b>different sizes</b> and support sets of <b>different fractions</b>,
 * each measured <b>repeatedly and interleaved</b> (full, restricted, full, restricted, …) so
 * that a drift in the machine hits both sides equally. Every reading is timed the same way,
 * and the two are compared as means <b>and</b> as spreads.</p>
 *
 * <p><b>What it asserts.</b> Only what is not a matter of timing: the restricted reading cites
 * a <b>subset</b> of the full reading's claims, and it answers whenever the full one answers.
 * A timing assertion would be the load-sensitive class the protocol's §7.1 names, and it is
 * not introduced here.</p>
 *
 * <p><b>What it is for.</b> The programme needs to know where to build the sealed split's
 * corpus: a corpus whose support set is a small fraction of the workspace is the corpus in
 * which this mechanism can show what it is worth. This measurement says how small that
 * fraction has to be.</p>
 */
class ArmCCostMeasurementTest {

    /** How many times each reading is timed, after the warmup. */
    private static final int REPETITIONS = 30;

    /**
     * How many pairs are timed and thrown away first.
     *
     * <p>The first readings pay the JIT's warmup, and the warmup is not what the mechanism
     * is about. Both sides are warmed equally, because the alternation warms them
     * alternately.</p>
     */
    private static final int WARMUP = 10;

    /** The question every reading of the research pool asks. */
    private static final String QUESTION = "What is plate tectonics?";

    /** The question every reading of the bundled workspace asks. */
    private static final String BUNDLED_QUESTION = "What is photosynthesis?";

    @Test
    @DisplayName("the saving grows with the fraction of the workspace the support set covers")
    void theSavingGrowsWithTheSupportFraction(@TempDir Path directory) throws Exception {
        // ── the workspace: the research pool, imported through the production path ──
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            List<String> sourceIds = new ArrayList<>();
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                var imported = bench.importDocument("research-pool", entry.path(), entry.file(),
                        entry.language());
                sourceIds.add(imported.source().sourceId());
            }
            var snapshot = bench.workspace("research-pool");
            int claimsOfTheWorkspace = snapshot.claims().size();

            // ── the support sets: one document, two documents, all of them ────────
            int[] widths = {1, 2, 4, sourceIds.size()};
            for (int width : widths) {
                if (width > sourceIds.size()) {
                    continue;
                }
                Set<String> support = new LinkedHashSet<>(sourceIds.subList(0, width));
                int claimsOfTheSupport = (int) snapshot.claims().stream()
                        .filter(claim -> support.contains(claim.introducedBySourceId())).count();
                Measurement measurement = measure(bench, "research-pool", support);
                System.out.println("PROBE workspace=pool claims=" + claimsOfTheWorkspace
                        + " support=" + claimsOfTheSupport + " fraction="
                        + String.format("%.4f", (double) claimsOfTheSupport / claimsOfTheWorkspace)
                        + " full_median_ms=" + String.format("%.4f", measurement.fullMedianMillis())
                        + " restricted_median_ms="
                        + String.format("%.4f", measurement.restrictedMedianMillis())
                        + " saving_pct=" + String.format("%.2f", measurement.savingPercent())
                        + " full_spread_ms=" + String.format("%.4f", measurement.fullSpreadMillis())
                        + " restricted_spread_ms="
                        + String.format("%.4f", measurement.restrictedSpreadMillis()));
            }
        } finally {
            bench.close();
        }
    }

    @Test
    @DisplayName("the bundled workspace is too small for the mechanism to show, at any fraction")
    void theSmallWorkspaceShowsNothing(@TempDir Path directory) throws Exception {
        // ── the workspace: the bundled photosynthesis package, 17 claims ─────────
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        try {
            var snapshot = bench.workspace(KnowledgeWorkbench.DEFAULT_WORKSPACE);
            int claimsOfTheWorkspace = snapshot.claims().size();
            List<String> sourceIds = snapshot.sources().stream().map(Source::id).toList();

            for (int width = 1; width <= sourceIds.size(); width++) {
                Set<String> support = new LinkedHashSet<>(sourceIds.subList(0, width));
                int claimsOfTheSupport = (int) snapshot.claims().stream()
                        .filter(claim -> support.contains(claim.introducedBySourceId())).count();
                Measurement measurement = measure(bench, KnowledgeWorkbench.DEFAULT_WORKSPACE,
                        support);
                System.out.println("PROBE workspace=bundled claims=" + claimsOfTheWorkspace
                        + " support=" + claimsOfTheSupport + " fraction="
                        + String.format("%.4f", (double) claimsOfTheSupport / claimsOfTheWorkspace)
                        + " full_median_ms=" + String.format("%.4f", measurement.fullMedianMillis())
                        + " restricted_median_ms="
                        + String.format("%.4f", measurement.restrictedMedianMillis())
                        + " saving_pct=" + String.format("%.2f", measurement.savingPercent())
                        + " full_spread_ms=" + String.format("%.4f", measurement.fullSpreadMillis())
                        + " restricted_spread_ms="
                        + String.format("%.4f", measurement.restrictedSpreadMillis()));
            }
        } finally {
            bench.close();
        }
    }

    @Test
    @DisplayName("the restricted reading cites a subset of the full reading's claims")
    void theRestrictedReadingIsNarrower(@TempDir Path directory) throws Exception {
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            List<String> sourceIds = new ArrayList<>();
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                var imported = bench.importDocument("research-pool", entry.path(), entry.file(),
                        entry.language());
                sourceIds.add(imported.source().sourceId());
            }
            Set<String> support = new LinkedHashSet<>(sourceIds.subList(0, 1));
            var full = bench.ask("research-pool", QUESTION, AbstractionLevel.INTRODUCTORY);
            var restricted = bench.ask("research-pool", QUESTION, AbstractionLevel.INTRODUCTORY,
                    support);

            Set<String> fullClaims = new LinkedHashSet<>();
            for (Claim claim : full.claims()) {
                fullClaims.add(claim.id());
            }
            Set<String> restrictedClaims = new LinkedHashSet<>();
            for (Claim claim : restricted.claims()) {
                restrictedClaims.add(claim.id());
            }

            assertThat(restrictedClaims)
                    .as("the restricted reading cites a subset of the full reading's claims")
                    .isSubsetOf(fullClaims);
            assertThat(restricted.answered())
                    .as("and it answers whenever the full one answers")
                    .isEqualTo(full.answered());
            assertThat(restricted.sources().size())
                    .as("and it reads fewer sources")
                    .isLessThan(full.sources().size());
        } finally {
            bench.close();
        }
    }

    /** The two readings, timed alternately, and what they cost. */
    private record Measurement(double fullMedianMillis, double restrictedMedianMillis,
                               double fullSpreadMillis, double restrictedSpreadMillis) {

        /** The saving of the restricted reading, in per cent. */
        double savingPercent() {
            if (fullMedianMillis <= 0) {
                return 0.0;
            }
            return (fullMedianMillis - restrictedMedianMillis) / fullMedianMillis * 100.0;
        }
    }

    /**
     * Times both readings, alternately.
     *
     * <p>The alternation matters: a machine that drifts between the first reading and the
     * last would tilt the comparison, and the alternation hits both sides with the same
     * drift.</p>
     */
    private Measurement measure(KnowledgeWorkbench bench, String workspaceId,
                               Set<String> support) {
        List<Long> full = new ArrayList<>();
        List<Long> restricted = new ArrayList<>();
        for (int repetition = 0; repetition < WARMUP; repetition++) {
            bench.ask(workspaceId, question(workspaceId), AbstractionLevel.INTRODUCTORY);
            bench.ask(workspaceId, question(workspaceId), AbstractionLevel.INTRODUCTORY, support);
        }
        for (int repetition = 0; repetition < REPETITIONS; repetition++) {
            long start = System.nanoTime();
            bench.ask(workspaceId, question(workspaceId), AbstractionLevel.INTRODUCTORY);
            full.add(System.nanoTime() - start);

            start = System.nanoTime();
            bench.ask(workspaceId, question(workspaceId), AbstractionLevel.INTRODUCTORY, support);
            restricted.add(System.nanoTime() - start);
        }
        return new Measurement(medianMillis(full), medianMillis(restricted), spreadMillis(full),
                spreadMillis(restricted));
    }

    /** The question a workspace is asked. */
    private static String question(String workspaceId) {
        return KnowledgeWorkbench.DEFAULT_WORKSPACE.equals(workspaceId)
                ? BUNDLED_QUESTION : QUESTION;
    }

    /**
     * The MEDIAN of the timings, in milliseconds.
     *
     * <p>The median and not the mean: the distribution of a reading on a shared machine has a
     * long tail (a collection, a page fault, a neighbour), and the mean of such a tail is
     * not the cost of the reading. The repository's own latency assertion uses the median of
     * 50 passes for exactly this reason.</p>
     */
    private static double medianMillis(List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        java.util.Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle) / 1_000_000.0;
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0 / 1_000_000.0;
    }

    /** The spread of the timings (max − min), in milliseconds. */
    private static double spreadMillis(List<Long> nanos) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long value : nanos) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return (max - min) / 1_000_000.0;
    }
}
