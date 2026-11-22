package io.matrix.crj.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Crj01EpistemicMetricsControlTest {
    @Test
    void scoresGoldLabelsInTheirOwnFields(@TempDir Path directory) throws Exception {
        Path evidence = directory.resolve("control.json");
        Files.writeString(evidence, """
                {"rows":[
                  {"caseId":"p","arm":"A","variant":"measured","disposition":"WRITTEN",
                   "epistemicDisposition":"ANSWERED","premiseStatus":"AMBIGUOUS",
                   "oracleOutcome":"DISPOSITION_MISMATCHED","grounded":false},
                  {"caseId":"p","arm":"C","variant":"measured","disposition":"WRITTEN",
                   "epistemicDisposition":"ABSTAINED","premiseStatus":"REFUTED",
                   "oracleOutcome":"DISPOSITION_MATCHED","grounded":true},
                  {"caseId":"e","arm":"A","variant":"measured","disposition":"WRITTEN",
                   "epistemicDisposition":"ABSTAINED","oracleOutcome":"DISPOSITION_MATCHED",
                   "grounded":true},
                  {"caseId":"e","arm":"C","variant":"measured","disposition":"REFUSED",
                   "oracleOutcome":"DISPOSITION_MISMATCHED","grounded":false},
                  {"caseId":"q","arm":"A","variant":"measured","disposition":"WRITTEN",
                   "epistemicDisposition":"ANSWERED","premiseStatus":"AMBIGUOUS",
                   "oracleOutcome":"GROUNDED","grounded":true},
                  {"caseId":"missing","arm":"A","variant":"measured"}]}
                """);
        var report = Crj01Metrics.compute(evidence, 7,
                Map.of("p", "REFUTED", "e", "ABSTAINED", "q", "ANSWERED"),
                Map.of("p", "REFUTED_PREMISE", "e", "NEGATIVE", "q", "POSITIVE",
                        "missing", "NEGATIVE"),
                Map.of("p", "PREMISE", "e", "EPISTEMIC", "q", "PREMISE",
                        "missing", "EPISTEMIC"));
        var abstention = metric(report, "M-A2");
        var premise = metric(report, "M-A4");
        assertAll(
                () -> assertEquals(1, abstention.numerator()),
                () -> assertEquals(2, abstention.denominator()),
                () -> assertEquals(1, premise.numerator()),
                () -> assertEquals(2, premise.denominator()));
    }

    private static Crj01Metrics.Metric metric(Crj01Metrics.Report report, String id) {
        return report.metrics().stream().filter(metric -> metric.id().equals(id)).findFirst()
                .orElseThrow();
    }
}
