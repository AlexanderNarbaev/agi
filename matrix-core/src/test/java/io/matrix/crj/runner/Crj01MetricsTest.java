package io.matrix.crj.runner;

import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the METRICS, exercised.
 *
 * <p><b>What this proves.</b> That the protocol's verdicts are computable from the evidence
 * rows, that every metric carries its denominator, that the paired difference is computed
 * on <b>paired</b> cases rather than on two independent proportions, that the bootstrap
 * interval is <b>reproducible</b> under its recorded seed, and that the McNemar test
 * reports the power limitation beside its p-value.</p>
 *
 * <p><b>What this does not establish.</b> Nothing about an arm's advantage: the rows it
 * reads are the dev run's, and the dev run is a debugging measurement.</p>
 */
class Crj01MetricsTest {

    /** The evidence artefact the dev run produced. */
    private static final String EVIDENCE = "docs-v2/engineering/CRJ-01-EVIDENCE.json";

    /** The dev split the evidence names. */
    private static final String DATASET = "docs-v2/engineering/CRJ-01-DEV-SPLIT.json";

    @Test
    @DisplayName("the protocol's verdicts are computed from the rows, with their denominators")
    void theVerdictsAreComputedFromTheRows() throws Exception {
        Path evidence = RepositoryPaths.root().resolve(EVIDENCE);
        Path dataset = RepositoryPaths.root().resolve(DATASET);
        assertThat(evidence).as("the evidence artefact %s is missing", EVIDENCE).exists();
        assertThat(dataset).as("the dev split %s is missing", DATASET).exists();

        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        Map<?, ?> split = json.readValue(Files.readString(dataset), Map.class);
        Map<String, String> expected = new LinkedHashMap<>();
        Map<String, String> classes = new LinkedHashMap<>();
        for (Object entry : (List<?>) split.get("cases")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) entry;
            expected.put(String.valueOf(row.get("id")), String.valueOf(row.get("expectedDisposition")));
            classes.put(String.valueOf(row.get("id")), String.valueOf(row.get("class")));
        }

        Crj01Metrics.Report report = Crj01Metrics.compute(evidence, 20261121L, expected, classes);

        // ── the metrics artefact: the rows and the verdicts, written so that a
        //    reviewer can read both without running anything ─────────────────────
        Map<String, Object> artefact = new LinkedHashMap<>();
        artefact.put("metrics", "crj-01-metrics");
        artefact.put("version", "1.0");
        artefact.put("date", "2026-11-21");
        artefact.put("dataset", "crj-01-dev-split");
        artefact.put("evidence", EVIDENCE);
        artefact.put("bootstrapSeed", 20261121L);
        artefact.put("bootstrapResamples", Crj01Metrics.BOOTSTRAP_RESAMPLES);
        artefact.put("note", "a DEV run on the solver's own split: the verdicts are computed, "
                + "and they are a DEBUGGING artefact, never evidence of an arm's advantage");
        List<Map<String, Object>> metricRows = new java.util.ArrayList<>();
        for (Crj01Metrics.Metric metric : report.metrics()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", metric.id());
            entry.put("name", metric.name());
            entry.put("numerator", metric.numerator());
            entry.put("denominator", metric.denominator());
            entry.put("rate", metric.rate());
            entry.put("population", metric.population());
            entry.put("note", metric.note());
            metricRows.add(entry);
        }
        artefact.put("domainAMetrics", metricRows);
        List<Map<String, Object>> differenceRows = new java.util.ArrayList<>();
        for (Crj01Metrics.PairedDifference difference : report.differences()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", difference.name());
            entry.put("n", difference.n());
            entry.put("wins", difference.wins());
            entry.put("losses", difference.losses());
            entry.put("ties", difference.ties());
            entry.put("pointEstimate", difference.pointEstimate());
            entry.put("bootstrapLower", difference.lower());
            entry.put("bootstrapUpper", difference.upper());
            entry.put("intervalExcludesZero", difference.intervalExcludesZero());
            entry.put("mcnemarP", difference.mcnemarP());
            entry.put("powerLimitation", difference.powerLimitation());
            differenceRows.add(entry);
        }
        artefact.put("pairedDifferences", differenceRows);
        List<Map<String, Object>> pairedRows = new java.util.ArrayList<>();
        for (Crj01Metrics.PairedRow row : report.pairedRows()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("caseId", row.caseId());
            entry.put("arm", row.arm());
            entry.put("variant", row.variant());
            entry.put("domain", row.domain());
            entry.put("outcome", row.outcome());
            entry.put("oracleVerdict", row.oracleVerdict());
            entry.put("goldClaimIdsCited", row.goldClaimIdsCited());
            entry.put("goldPropositionsMatched", row.goldPropositionsMatched());
            entry.put("disposition", row.disposition());
            entry.put("epistemicDisposition", row.epistemicDisposition());
            entry.put("premiseStatus", row.premiseStatus());
            entry.put("citations", row.citations());
            entry.put("durationMs", row.durationMs());
            entry.put("methodId", row.methodId());
            entry.put("artifactSha256", row.artifactSha256());
            entry.put("traceRef", row.traceRef());
            entry.put("effectVerified", row.effectVerified());
            entry.put("conflictCount", row.conflictCount());
            entry.put("inapplicableReason", row.inapplicableReason());
            entry.put("error", row.error());
            pairedRows.add(entry);
        }
        artefact.put("pairedRows", pairedRows);
        String artefactJson = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .writerWithDefaultPrettyPrinter().writeValueAsString(artefact);
        Path out = RepositoryPaths.root().resolve("docs-v2/engineering")
                .resolve("CRJ-01-METRICS.json");
        Files.writeString(out, artefactJson);
        assertThat(Files.readString(out)).isEqualTo(artefactJson);
        System.out.println("[CRJ-01] the metrics " + out);
        for (Crj01Metrics.Metric metric : report.metrics()) {
            System.out.println("[CRJ-01] " + metric.id() + " " + metric.name() + " = "
                    + metric.numerator() + "/" + metric.denominator());
        }
        for (Crj01Metrics.PairedDifference difference : report.differences()) {
            System.out.println("[CRJ-01] " + difference.name() + " n=" + difference.n()
                    + " wins=" + difference.wins() + " losses=" + difference.losses()
                    + " delta=" + difference.pointEstimate() + " ["
                    + difference.lower() + ", " + difference.upper() + "]"
                    + " mcnemar=" + difference.mcnemarP());
        }

        // ── the paired rows: one per (case, arm), and NONE deleted ───────────────
        assertThat(report.pairedRows()).as("the paired rows").isNotEmpty();
        assertThat(report.pairedRows()).allSatisfy(row -> {
            assertThat(row.caseId()).isNotBlank();
            assertThat(row.arm()).isIn("A", "B", "C");
            assertThat(row.outcome()).isIn("CORRECT", "INCORRECT", "REFUSED", "INAPPLICABLE",
                    "ERROR");
            assertThat(row.oracleVerdict()).isIn("GROUNDED", "REFUSED_CORRECTLY",
                    "DISPOSITION_MISMATCH", "UNGROUNDED", "N/A");
        });
        // the dev run produced 36 rows: A once per case, B twice per case, C once per case
        assertThat(report.pairedRows()).as("every row is kept, including the inapplicable ones")
                .hasSize(36);

        // ── every metric carries its DENOMINATOR and names its population ─────────
        assertThat(report.metrics()).as("the metrics").isNotEmpty();
        assertThat(report.metrics()).allSatisfy(metric -> {
            assertThat(metric.id()).startsWith("M-A");
            assertThat(metric.denominator()).as("the denominator of %s", metric.id())
                    .isNotNegative();
            assertThat(metric.population()).as("the population of %s", metric.id()).isNotBlank();
            assertThat(metric.note()).isNotBlank();
        });
        // M-A1's denominator is the cases that carried a question, on the measured variant:
        // arm A's 9 plus arm C's 9
        Crj01Metrics.Metric mA1 = report.metrics().stream()
                .filter(metric -> metric.id().equals("M-A1")).findFirst().orElseThrow();
        assertThat(mA1.denominator()).as("M-A1's denominator").isEqualTo(18);
        // M-A2's denominator is the NEGATIVE classes only: 2 unsupported + 1 refuted, on two
        // measured arms
        Crj01Metrics.Metric mA2 = report.metrics().stream()
                .filter(metric -> metric.id().equals("M-A2")).findFirst().orElseThrow();
        assertThat(mA2.denominator()).as("M-A2's denominator").isEqualTo(6);
        assertThat(mA2.numerator()).as("M-A2's numerator").isLessThanOrEqualTo(mA2.denominator());

        // ── the paired differences, with their intervals and their power limitation ─
        assertThat(report.differences()).as("the paired differences").hasSize(3);
        Crj01Metrics.PairedDifference cMinusA = report.differences().get(0);
        assertThat(cMinusA.name()).contains("C - A");
        assertThat(cMinusA.n()).as("the paired difference is over PAIRED cases").isEqualTo(9);
        assertThat(cMinusA.wins() + cMinusA.losses() + cMinusA.ties()).isEqualTo(cMinusA.n());
        assertThat(cMinusA.lower()).isLessThanOrEqualTo(cMinusA.pointEstimate());
        assertThat(cMinusA.pointEstimate()).isLessThanOrEqualTo(cMinusA.upper());
        assertThat(cMinusA.powerLimitation()).as("the power limitation is printed beside the "
                + "p-value").contains("discordant");
        // the dev run's arms are equal on the dev split, so there are no discordant pairs
        // and the McNemar test has no power at all — which the row must say
        assertThat(cMinusA.mcnemarP()).as("no discordant pairs means no p-value").isNull();
        assertThat(cMinusA.powerLimitation()).contains("no power");

        // ── the bootstrap is REPRODUCIBLE under its recorded seed ────────────────
        List<Integer> differences = List.of(0, 1, 0, 1, 0, 0, 0, -1, 0);
        double[] first = Crj01Metrics.bootstrap(differences, 20261121L);
        double[] second = Crj01Metrics.bootstrap(differences, 20261121L);
        assertThat(first).as("the same seed gives the same interval").isEqualTo(second);
        double[] other = Crj01Metrics.bootstrap(differences, 1L);
        // A different seed MAY give the same two percentiles: with nine cases the
        // bootstrap has few distinct outcomes, and the [2.5, 97.5] percentiles are often
        // the same whatever the resample. What the freeze needs is REPRODUCIBILITY under
        // the recorded seed, and that is what the first assertion pins. The point estimate
        // does not depend on the seed at all.
        assertThat(first[0]).isLessThanOrEqualTo(first[1]);
        assertThat(other[0]).isLessThanOrEqualTo(other[1]);
        assertThat(Crj01Metrics.bootstrap(differences, 20261121L))
                .as("the same seed gives the same interval, which is what the freeze record "
                        + "relies on")
                .isEqualTo(first);
    }

    @Test
    @DisplayName("the domain-B metrics are computed, and the transfer's empty denominator is reported")
    void theDomainBMetricsAreComputed() throws Exception {
        Path evidence = RepositoryPaths.root()
                .resolve("docs-v2/engineering/CRJ-01-EVIDENCE-DOMAIN-B.json");
        assertThat(evidence).as("the domain-B evidence").exists();
        java.util.List<Crj01Metrics.Metric> metrics = Crj01Metrics.domainBMetrics(evidence);
        assertThat(metrics).as("the domain-B metrics").isNotEmpty();
        assertThat(metrics).allSatisfy(metric -> {
            assertThat(metric.id()).startsWith("M-B");
            assertThat(metric.population()).isNotBlank();
            assertThat(metric.note()).isNotBlank();
        });

        // M-B2: every dev episode settled — the bound is the domain's size
        Crj01Metrics.Metric settled = metrics.stream()
                .filter(metric -> metric.id().equals("M-B2") && metric.name().contains("(A)"))
                .findFirst().orElseThrow();
        assertThat(settled.denominator()).as("the episodes").isEqualTo(9);
        assertThat(settled.numerator()).as("the episodes that settled").isEqualTo(9);

        // M-B5: the primary metric — arm A pays for the probes, arm C does not
        Crj01Metrics.Metric costA = metrics.stream()
                .filter(metric -> metric.id().equals("M-B5") && metric.name().contains("(A)"))
                .findFirst().orElseThrow();
        Crj01Metrics.Metric costC = metrics.stream()
                .filter(metric -> metric.id().equals("M-B5") && metric.name().contains("(C)"))
                .findFirst().orElseThrow();
        assertThat(costA.numerator()).as("arm A's probes").isEqualTo(36);
        assertThat(costA.denominator()).as("arm A's correct answers").isEqualTo(36);
        assertThat(costC.numerator()).as("arm C's probes").isZero();
        assertThat(costC.denominator()).as("arm C's correct answers").isEqualTo(36);

        // M-B4: the TRANSFER metric's denominator is ZERO, and it is REPORTED, not dropped
        Crj01Metrics.Metric transfer = metrics.stream()
                .filter(metric -> metric.id().equals("M-B4") && metric.name().contains("(A)"))
                .findFirst().orElseThrow();
        assertThat(transfer.denominator())
                .as("the transfer subset is EMPTY in the lamp world as it exists")
                .isZero();
        assertThat(transfer.rate()).as("a rate over nothing is not a number").isNull();
        assertThat(transfer.note()).contains("ZERO");
    }

    @Test
    @DisplayName("the exact McNemar test agrees with the protocol's own worked examples")
    void theMcnemarTestAgreesWithTheWorkedExamples() {
        // the protocol's own numbers: 6/6 discordant pairs all favouring one arm reach
        // p = 0.03125, and 5/6 reach p = 0.21875
        assertThat(Crj01Metrics.mcnemarExact(6, 0)).isEqualTo("0.031250");
        assertThat(Crj01Metrics.mcnemarExact(5, 1)).isEqualTo("0.218750");
        assertThat(Crj01Metrics.mcnemarExact(0, 0)).as("no discordant pairs means no p-value")
                .isNull();
        assertThat(Crj01Metrics.mcnemarExact(3, 3)).as("a tie is not significant")
                .isEqualTo("1.000000");
    }
}
