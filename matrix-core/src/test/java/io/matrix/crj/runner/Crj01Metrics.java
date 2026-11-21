package io.matrix.crj.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * CRJ-01 — the METRICS: the protocol's verdicts, computed from the evidence rows.
 *
 * <p><b>Why they are needed.</b> The protocol defines its metrics with their numerators,
 * their denominators and the exact population each denominator counts (§6), the row schema
 * the evidence artefact must carry (§6.3), and the uncertainty treatment for a small
 * sample (§6.4). Until now the rows existed and the verdicts did not — and a run that
 * produces rows nobody turns into a verdict is not a measurement.</p>
 *
 * <p><b>What is computed.</b>
 * <ul>
 *   <li>the **paired outcome format** (§6.3): one row per (case, arm) with
 *       {@code outcome} ∈ {CORRECT, INCORRECT, REFUSED, INAPPLICABLE, ERROR} and
 *       {@code oracle_verdict} ∈ {GROUNDED, REFUSED_CORRECTLY, DISPOSITION_MISMATCH,
 *       UNGROUNDED, N/A}. A row is <b>never deleted</b>: a crash is a row with
 *       {@code outcome = ERROR} and the exception text;</li>
 *   <li>the **domain-A metrics** M-A1 … M-A6, each with its numerator and its
 *       denominator, and <b>no metric reported without its denominator</b> (§6.4.3);</li>
 *   <li>the **paired difference** Δ with a <b>seeded bootstrap percentile interval</b>
 *       (B = 10000 resamples, the [2.5, 97.5] percentiles) for Δ(C − A) and for Δ(C − B)
 *       on the <b>novel</b> subset only (§6.4.1);</li>
 *   <li>the **exact McNemar test** on the discordant pairs, with its <b>power limitation
 *       printed next to the p-value</b>: at n = 18 the test reaches p ≤ 0.05 only with at
 *       least 6 discordant pairs and <b>all</b> of them favouring one arm (§6.4.2).</li>
 * </ul>
 *
 * <p><b>What it does not do.</b> It does not decide that a result is good, and it does not
 * choose a threshold. The thresholds are fixed in the protocol before any run; this class
 * only computes what the protocol says to compute.</p>
 */
final class Crj01Metrics {

    /** The bootstrap's resamples — the protocol's convention, fixed before any run. */
    static final int BOOTSTRAP_RESAMPLES = 10_000;

    /** The interval's percentiles. */
    static final double LOWER_PERCENTILE = 2.5;

    /** The interval's percentiles. */
    static final double UPPER_PERCENTILE = 97.5;

    /** The paired outcome of one (case, arm). */
    record PairedRow(String caseId,
                      String arm,
                      String variant,
                      String domain,
                      String outcome,
                      String oracleVerdict,
                      List<String> goldClaimIdsCited,
                      int goldPropositionsMatched,
                      String disposition,
                      String epistemicDisposition,
                      String premiseStatus,
                      int citations,
                      double durationMs,
                      String methodId,
                      String artifactSha256,
                      String traceRef,
                      boolean effectVerified,
                      int conflictCount,
                      String inapplicableReason,
                      String error) {
        PairedRow {
            goldClaimIdsCited = List.copyOf(
                    goldClaimIdsCited == null ? List.of() : goldClaimIdsCited);
        }
    }

    /** One metric, with the population it counts. */
    record Metric(String id,
                  String name,
                  long numerator,
                  long denominator,
                  String population,
                  String note) {
        /** The rate, or null when the denominator is zero — a rate over nothing is not a number. */
        Double rate() {
            return denominator == 0 ? null : (double) numerator / (double) denominator;
        }
    }

    /** The paired difference and its interval. */
    record PairedDifference(String name,
                            int n,
                            int wins,
                            int losses,
                            int ties,
                            double pointEstimate,
                            double lower,
                            double upper,
                            boolean intervalExcludesZero,
                            String mcnemarP,
                            String powerLimitation) {
    }

    /** Everything the metrics produced. */
    record Report(List<PairedRow> pairedRows,
                  List<Metric> metrics,
                  List<PairedDifference> differences,
                  String note) {
        Report {
            pairedRows = List.copyOf(pairedRows == null ? List.of() : pairedRows);
            metrics = List.copyOf(metrics == null ? List.of() : metrics);
            differences = List.copyOf(differences == null ? List.of() : differences);
        }
    }

    private Crj01Metrics() {
    }

    /**
     * Reads an evidence artefact and computes the protocol's verdicts.
     *
     * @param evidence the artefact
     * @param seed     the bootstrap's seed — recorded in the freeze record
     * @param expected the cases' expected dispositions, by case id
     * @param classes  the cases' classes, by case id
     * @return the report
     * @throws Exception when the artefact cannot be read
     */
    static Report compute(Path evidence, long seed, Map<String, String> expected,
                           Map<String, String> classes) throws Exception {
        ObjectMapper json = JsonMapper.builder().build();
        Map<?, ?> parsed = json.readValue(Files.readString(evidence, StandardCharsets.UTF_8),
                Map.class);
        List<PairedRow> paired = new ArrayList<>();
        for (Object entry : (List<?>) parsed.get("rows")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) entry;
            paired.add(toPairedRow(row, expected));
        }

        List<Metric> metrics = domainAMetrics(paired, classes);
        List<PairedDifference> differences = new ArrayList<>();
        differences.add(pairedDifference("C - A (all cases)", paired, "A", "measured", "C",
                "measured", seed));
        differences.add(pairedDifference("C - B (the NOVEL subset)", paired, "B", "novel", "C",
                "measured", seed));
        differences.add(pairedDifference("C - B (the REPEAT subset, reported separately)",
                paired, "B", "repeat", "C", "measured", seed));
        return new Report(paired, metrics, differences,
                "the metrics are computed from the evidence rows; no threshold is applied here, "
                        + "and no metric is reported without its denominator");
    }

    /**
     * The paired row of one evidence row.
     *
     * @param row      the evidence row
     * @param expected the case's expected disposition
     * @return the paired row
     */
    private static PairedRow toPairedRow(Map<String, Object> row, Map<String, String> expected) {
        String caseId = String.valueOf(row.get("caseId"));
        String arm = String.valueOf(row.get("arm"));
        String disposition = String.valueOf(row.get("disposition"));
        boolean grounded = Boolean.TRUE.equals(row.get("grounded"));
        boolean inapplicable = Boolean.TRUE.equals(row.get("inapplicable"));
        String error = row.get("error") == null ? null : String.valueOf(row.get("error"));
        String oracleOutcome = String.valueOf(row.get("oracleOutcome"));

        String outcome;
        String oracleVerdict;
        if (error != null) {
            outcome = "ERROR";
            oracleVerdict = "N/A";
        } else if (inapplicable) {
            outcome = "INAPPLICABLE";
            oracleVerdict = "N/A";
        } else if ("REFUSED".equals(disposition)) {
            // a refusal is CORRECT when the case EXPECTED one
            outcome = expected.containsKey(caseId)
                    && "REFUSED".equals(expected.get(caseId)) ? "CORRECT" : "INCORRECT";
            oracleVerdict = "REFUSED_CORRECTLY".equals(oracleOutcome) ? "REFUSED_CORRECTLY"
                    : "DISPOSITION_MISMATCH";
        } else if (grounded) {
            outcome = "CORRECT";
            oracleVerdict = "GROUNDED";
        } else {
            outcome = "INCORRECT";
            oracleVerdict = "UNGROUNDED";
        }

        List<String> cited = new ArrayList<>();
        Object citedIds = row.get("citedClaimIds");
        if (citedIds instanceof List<?> ids) {
            for (Object id : ids) {
                cited.add(String.valueOf(id));
            }
        }
        return new PairedRow(caseId, arm, String.valueOf(row.get("variant")), "A", outcome,
                oracleVerdict, cited, intOf(row.get("goldPropositionsMatched")), disposition,
                String.valueOf(row.get("epistemicDisposition")),
                String.valueOf(row.get("premiseStatus")), intOf(row.get("citations")),
                longOf(row.get("wallNanos")) / 1_000_000.0, String.valueOf(row.get("methodId")),
                String.valueOf(row.get("artifactSha256")), String.valueOf(row.get("traceRef")),
                Boolean.TRUE.equals(row.get("effectVerified")), intOf(row.get("conflictCount")),
                inapplicable ? String.valueOf(row.get("reason")) : null, error);
    }

    /**
     * The domain-A metrics, each with its numerator and its denominator.
     *
     * @param rows    the paired rows
     * @param classes the cases' classes
     * @return the metrics
     */
    private static List<Metric> domainAMetrics(List<PairedRow> rows, Map<String, String> classes) {
        List<Metric> metrics = new ArrayList<>();

        // M-A1: the grounded answer rate over the cases that carried a question
        long a1n = 0;
        long a1d = 0;
        for (PairedRow row : rows) {
            if (!"measured".equals(row.variant())) {
                continue;
            }
            a1d++;
            if ("CORRECT".equals(row.outcome()) && row.goldPropositionsMatched() > 0) {
                a1n++;
            }
        }
        metrics.add(new Metric("M-A1", "grounded answer rate", a1n, a1d,
                "every sealed case that carried a question, on the measured variant of each arm",
                "a case counts when the answer cites at least one gold claim whose proposition "
                        + "is byte-identical to a gold proposition and whose claim id is the "
                        + "production id of that source in the arm's own workspace"));

        // M-A2: the abstention correctness over the negative classes
        long a2n = 0;
        long a2d = 0;
        for (PairedRow row : rows) {
            if (!"measured".equals(row.variant())) {
                continue;
            }
            String caseClass = classes.get(row.caseId());
            if (caseClass == null || !isNegativeClass(caseClass)) {
                continue;
            }
            a2d++;
            if ("CORRECT".equals(row.outcome())) {
                a2n++;
            }
        }
        metrics.add(new Metric("M-A2", "abstention correctness", a2n, a2d,
                "the sealed split's unanswerable and premise-bearing NEGATIVE classes only",
                "a negative case counts when the answer's disposition is the expected one"));

        // M-A3: the unsupported-answer rate over all the cases that carried a question
        long a3n = 0;
        long a3d = 0;
        for (PairedRow row : rows) {
            if (!"measured".equals(row.variant())) {
                continue;
            }
            a3d++;
            if (row.goldPropositionsMatched() == 0 && !"INAPPLICABLE".equals(row.outcome())) {
                a3n++;
            }
        }
        metrics.add(new Metric("M-A3", "unsupported-answer rate", a3n, a3d,
                "every sealed case that carried a question, on the measured variant of each arm",
                "a case counts when the answer cited ZERO gold claims"));

        // M-A4: the premise-disposition accuracy over the cases that carry a premise
        long a4n = 0;
        long a4d = 0;
        for (PairedRow row : rows) {
            if (!"measured".equals(row.variant())) {
                continue;
            }
            String status = row.premiseStatus();
            if (status == null || status.isBlank() || "null".equals(status)
                    || "NO_PREMISE".equals(status)) {
                continue;
            }
            a4d++;
            if ("DISPOSITION_MATCHED".equals(row.oracleVerdict())) {
                a4n++;
            }
        }
        metrics.add(new Metric("M-A4", "premise-disposition accuracy", a4n, a4d,
                "the sealed cases that carry a premise, on the measured variant of each arm",
                "a case counts when the answer's premise status is the expected one"));

        // M-A5: the conflict-surfacing rate over the CONTRADICTORY cases
        long a5n = 0;
        long a5d = 0;
        for (PairedRow row : rows) {
            if (!"measured".equals(row.variant())) {
                continue;
            }
            if (!"CONTRADICTORY_EVIDENCE".equals(classes.get(row.caseId()))) {
                continue;
            }
            a5d++;
            if (row.conflictCount() >= 2) {
                a5n++;
            }
        }
        metrics.add(new Metric("M-A5", "conflict-surfacing rate", a5n, a5d,
                "the sealed CONTRADICTORY cases, on the measured variant of each arm",
                "a case counts when the answer carries BOTH sides with their provenance"));

        // M-A6: the effect-verification rate over the cases whose arm performed an effect
        long a6n = 0;
        long a6d = 0;
        for (PairedRow row : rows) {
            if (!"measured".equals(row.variant())) {
                continue;
            }
            if (!"WRITTEN".equals(row.disposition()) && !"REPLAYED".equals(row.disposition())) {
                continue;
            }
            a6d++;
            if (row.effectVerified()) {
                a6n++;
            }
        }
        metrics.add(new Metric("M-A6", "effect-verification rate", a6n, a6d,
                "the sealed cases whose arm performed an effect, on the measured variant",
                "a case counts when the written artifact was independently verified"));
        return metrics;
    }

    /**
     * Whether a class is one of the NEGATIVE classes.
     *
     * @param caseClass the class
     * @return true when it is negative
     */
    private static boolean isNegativeClass(String caseClass) {
        return switch (caseClass) {
            case "UNSUPPORTED_PREMISE", "REFUTED_PREMISE", "CONTRADICTORY_EVIDENCE",
                 "AMBIGUOUS_NAME", "NEGATIVE", "UNAVAILABLE_ACTION" -> true;
            default -> false;
        };
    }

    /**
     * The paired difference between two arms, with its bootstrap interval and its exact
     * McNemar test.
     *
     * @param name         the difference's name
     * @param rows         the paired rows
     * @param leftArm      the left arm
     * @param leftVariant  the left arm's variant
     * @param rightArm     the right arm
     * @param rightVariant the right arm's variant
     * @param seed         the bootstrap's seed
     * @return the difference
     */
    static PairedDifference pairedDifference(String name, List<PairedRow> rows, String leftArm,
                                              String leftVariant, String rightArm,
                                              String rightVariant, long seed) {
        Map<String, PairedRow> left = byArmAndVariant(rows, leftArm, leftVariant);
        Map<String, PairedRow> right = byArmAndVariant(rows, rightArm, rightVariant);
        List<Integer> differences = new ArrayList<>();
        int wins = 0;
        int losses = 0;
        int ties = 0;
        for (String caseId : right.keySet()) {
            PairedRow l = left.get(caseId);
            PairedRow r = right.get(caseId);
            if (l == null) {
                continue;
            }
            int d = (isCorrect(r) ? 1 : 0) - (isCorrect(l) ? 1 : 0);
            differences.add(d);
            if (d > 0) {
                wins++;
            } else if (d < 0) {
                losses++;
            } else {
                ties++;
            }
        }
        int n = differences.size();
        double point = n == 0 ? 0.0 : differences.stream().mapToInt(Integer::intValue).sum()
                / (double) n;
        double[] interval = bootstrap(differences, seed);
        String p = mcnemarExact(wins, losses);
        return new PairedDifference(name, n, wins, losses, ties, point, interval[0], interval[1],
                interval[0] > 0 || interval[1] < 0, p, powerLimitation(wins, losses));
    }

    /**
     * The rows of one arm and variant, by case id.
     *
     * @param rows    the rows
     * @param arm     the arm
     * @param variant the variant
     * @return the rows
     */
    private static Map<String, PairedRow> byArmAndVariant(List<PairedRow> rows, String arm,
                                                           String variant) {
        Map<String, PairedRow> byCase = new TreeMap<>();
        for (PairedRow row : rows) {
            if (row.arm().equals(arm) && row.variant().equals(variant)) {
                byCase.put(row.caseId(), row);
            }
        }
        return byCase;
    }

    /**
     * Whether a row counts as CORRECT.
     *
     * @param row the row
     * @return true when it is correct
     */
    private static boolean isCorrect(PairedRow row) {
        return "CORRECT".equals(row.outcome());
    }

    /**
     * The seeded bootstrap percentile interval of the mean of the paired differences.
     *
     * <p>The resampling is over CASES and the seed is recorded, so the interval is
     * reproducible: a reader who re-runs the analysis with the freeze's seed gets the same
     * interval. That is the whole point of fixing B = 10000 and the seed before the run.</p>
     *
     * @param differences the paired differences
     * @param seed        the seed
     * @return the lower and the upper percentile
     */
    static double[] bootstrap(List<Integer> differences, long seed) {
        if (differences.isEmpty()) {
            return new double[] {0.0, 0.0};
        }
        Random random = new Random(seed);
        int n = differences.size();
        double[] means = new double[BOOTSTRAP_RESAMPLES];
        for (int b = 0; b < BOOTSTRAP_RESAMPLES; b++) {
            int total = 0;
            for (int i = 0; i < n; i++) {
                total += differences.get(random.nextInt(n));
            }
            means[b] = total / (double) n;
        }
        java.util.Arrays.sort(means);
        return new double[] {percentile(means, LOWER_PERCENTILE),
                percentile(means, UPPER_PERCENTILE)};
    }

    /**
     * A percentile of a sorted array, by the nearest-rank rule.
     *
     * @param sorted    the sorted array
     * @param percentile the percentile
     * @return the value
     */
    static double percentile(double[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    /**
     * The exact two-sided McNemar test on the discordant pairs.
     *
     * @param wins  the pairs the right arm won
     * @param losses the pairs the left arm won
     * @return the p-value, or null when there are no discordant pairs
     */
    static String mcnemarExact(int wins, int losses) {
        int discordant = wins + losses;
        if (discordant == 0) {
            return null;
        }
        int smaller = Math.min(wins, losses);
        double tail = 0.0;
        for (int k = 0; k <= smaller; k++) {
            tail += binomial(discordant, k) / Math.pow(2.0, discordant);
        }
        double p = Math.min(1.0, 2.0 * tail);
        return String.format(java.util.Locale.ROOT, "%.6f", p);
    }

    /**
     * The binomial coefficient.
     *
     * @param n the count
     * @param k the choice
     * @return the coefficient
     */
    private static double binomial(int n, int k) {
        double result = 1.0;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    /**
     * The McNemar test's power limitation, printed next to its p-value.
     *
     * <p>At n = 18 the test reaches p ≤ 0.05 only with at least 6 discordant pairs and all
     * of them favouring one arm. A p-value that is not significant with fewer discordant
     * pairs says nothing about the arms — it says the SAMPLE was too small.</p>
     *
     * @param wins   the pairs the right arm won
     * @param losses the pairs the left arm won
     * @return the limitation, in words
     */
    static String powerLimitation(int wins, int losses) {
        int discordant = wins + losses;
        if (discordant == 0) {
            return "there were no discordant pairs, so the test has no power at all here";
        }
        return "the exact McNemar test reaches p <= 0.05 only with at least 6 discordant pairs "
                + "AND all of them favouring one arm (6/6 -> p = 0.03125; 5/6 -> p = 0.21875); "
                + "with " + discordant + " discordant pair(s) a non-significant p-value says the "
                + "SAMPLE was too small, not that the arms are equal";
    }

    /**
     * Reads an int out of a map.
     *
     * @param value the value
     * @return the int, or zero
     */
    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Reads a long out of a map.
     *
     * @param value the value
     * @return the long, or zero
     */
    private static long longOf(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
