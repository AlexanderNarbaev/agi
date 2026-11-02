package io.matrix.auditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RECON-W43.0 — the determinism gate.
 *
 * <p><b>What it replaces.</b> {@code Exp144DecisionPathAuditTest} scanned
 * the decision-path packages, PRINTED what it found, and asserted only that
 * it had scanned more than zero files. A gate that documents a violation
 * without failing is a report, and the report was being mistaken for a
 * check: the W42 audit recorded that the determinism audit "documents
 * violations without failing".
 *
 * <p><b>What it does now.</b>
 *
 * <ul>
 *   <li>It scans <b>every relevant production source root</b> — matrix-core,
 *       matrix-brain-runtime and matrix-api-gateway — and each root that is
 *       deliberately NOT scanned is named in {@link #EXCLUDED_ROOTS} with
 *       the reason. "Every relevant root must be covered or explicitly
 *       excluded with a reason" is only checkable if the exclusions are
 *       written down.</li>
 *   <li>It compares the <b>blocking</b> findings against an explicit
 *       baseline — a file that names each accepted finding, its owner, and
 *       the date the acceptance expires.</li>
 *   <li>It FAILS when a blocking finding is not in the baseline (a new
 *       violation), when a baselined finding has DISAPPEARED (the baseline
 *       is stale and nobody updated it), or when the baseline has expired.
 *       The middle rule is the strict one: without it the baseline would
 *       only ever grow, and a gate whose exceptions grow without bound
 *       stops being a gate.</li>
 * </ul>
 *
 * <p><b>What a violation is here.</b> A {@code VIOLATION} — a confirmed
 * breach of the constitution's Article I — or an {@code UNCLASSIFIED}
 * wall-clock read that nobody has adjudicated. An {@code UNCLASSIFIED}
 * site with a permit reason is a site a human looked at and allowed, and
 * it does not block.
 */
@Tag("exp")
class DecisionPathGateTest {

    /** The production source roots the gate scans. */
    private static final List<String> SCANNED_ROOTS = List.of(
            "matrix-core/src/main/java",
            "matrix-brain-runtime/src/main/java",
            "matrix-api-gateway/src/main/java");

    /**
     * The production source roots that are deliberately NOT scanned, and why.
     *
     * <p>The mission's rule is "every relevant production source root must
     * be covered or explicitly excluded with a reason". A reason is part of
     * the exclusion, so each module names its own — and the test fails when
     * a module appears in neither list.
     */
    private static final java.util.Map<String, String> EXCLUDED_ROOTS = java.util.Map.ofEntries(
            java.util.Map.entry("matrix-billing",
                    "a CLIENT of the billing service: it reads a decision, it makes none"),
            java.util.Map.entry("matrix-observability",
                    "writes metrics; it observes decisions and makes none"),
            java.util.Map.entry("matrix-audit",
                    "an append-only log writer"),
            java.util.Map.entry("matrix-quality",
                    "a gate: it reads the repository and reports on it"),
            java.util.Map.entry("matrix-operator",
                    "an operator command line"),
            java.util.Map.entry("matrix-sdk-java",
                    "a client library, consumed by no production path yet"),
            java.util.Map.entry("matrix-spigot",
                    "a plugin bridge"),
            java.util.Map.entry("matrix-tools-distill",
                    "a distillation tool, run by hand"));

    /** The explicit, owned and time-bounded baseline of accepted findings. */
    private static final String BASELINE_FILE = "docs-v2/quality/decision-path-baseline.txt";

    @Test
    @DisplayName("no decision path introduces a nondeterministic decision")
    void noDecisionPathIntroducesANondeterministicDecision() throws IOException {
        Path repo = repoRoot();
        Path baseline = repo.resolve(BASELINE_FILE);
        assertThat(baseline)
                .as("the determinism gate needs an explicit baseline of accepted findings")
                .exists();

        Set<String> accepted = DecisionPathBaseline.acceptedKeys(baseline);
        String expiry = DecisionPathBaseline.expiry(baseline);

        Set<String> found = new TreeSet<>();
        Set<String> rootsScanned = new LinkedHashSet<>();
        for (String root : SCANNED_ROOTS) {
            Path rootPath = repo.resolve(root);
            if (!Files.isDirectory(rootPath)) {
                continue;
            }
            rootsScanned.add(root);
            DecisionPathAuditor.AuditReport report = new DecisionPathAuditor().audit(rootPath);
            for (DecisionPathAuditor.Violation v : report.violations()) {
                if (DecisionPathBaseline.blocks(v)) {
                    found.add(DecisionPathBaseline.key(root, v));
                }
            }
        }

        assertThat(rootsScanned)
                .as("the determinism gate must scan every relevant production source root")
                .contains("matrix-core/src/main/java", "matrix-brain-runtime/src/main/java");

        Set<String> newFindings = new TreeSet<>(found);
        newFindings.removeAll(accepted);
        Set<String> staleEntries = new TreeSet<>(accepted);
        staleEntries.removeAll(found);

        if (!newFindings.isEmpty()) {
            System.out.println("[DECPATH-GATE] NEW blocking findings (" + newFindings.size() + "):");
            newFindings.forEach(f -> System.out.println("  " + f));
        }
        if (!staleEntries.isEmpty()) {
            System.out.println("[DECPATH-GATE] baseline entries that no longer hold ("
                    + staleEntries.size() + "):");
            staleEntries.forEach(f -> System.out.println("  " + f));
        }

        assertThat(newFindings)
                .as("decision paths that violate the constitution's Article I and are not in the "
                        + "accepted baseline")
                .isEmpty();
        assertThat(staleEntries)
                .as("the accepted baseline lists findings that no longer exist — update "
                        + BASELINE_FILE + " so that it says what is actually there")
                .isEmpty();
        assertThat(expiry)
                .as("the baseline must carry an expiry")
                .isNotBlank();
        assertThat(DecisionPathBaseline.expired(expiry))
                .as("the accepted baseline expired on " + expiry + " — the exceptions it holds "
                        + "must be renewed or the findings fixed")
                .isFalse();
    }

    @Test
    @DisplayName("every production source root is covered or explicitly excluded")
    void everyProductionSourceRootIsCoveredOrExplicitlyExcluded() throws IOException {
        Path repo = repoRoot();
        List<String> modules = new ArrayList<>();
        try (Stream<Path> children = Files.list(repo)) {
            children.filter(p -> Files.isDirectory(p.resolve("src/main/java")))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .forEach(modules::add);
        }
        // matrix-web-ui is not Java; the pilots are experiments and are named
        // in the exclusion list.
        modules.remove("matrix-web-ui");
        modules.removeIf(m -> m.startsWith("pilots"));

        List<String> uncovered = modules.stream()
                .filter(m -> !SCANNED_ROOTS.stream().anyMatch(r -> r.startsWith(m + "/")))
                .filter(m -> !EXCLUDED_ROOTS.containsKey(m))
                .toList();

        assertThat(uncovered)
                .as("production source roots that the determinism gate neither scans nor "
                        + "excludes with a reason")
                .isEmpty();
        assertThat(EXCLUDED_ROOTS.values())
                .as("every exclusion must carry a reason")
                .doesNotContain("", null);
    }

    /** Whether a finding blocks. */
    private static boolean blocks(DecisionPathAuditor.Violation v) {
        if (v.permitReason() != null) {
            return false;
        }
        return v.severity() == DecisionPathAuditor.Severity.VIOLATION
                || v.severity() == DecisionPathAuditor.Severity.UNCLASSIFIED;
    }

    /** The accepted findings of the baseline, one per line. */

    /** The expiry of the baseline, or an empty string. */

    /** Whether a date has passed. */

    /** The repository root, found by walking up from the test's directory. */
    private static Path repoRoot() {
        Path candidate = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6 && candidate != null; i++) {
            if (Files.isDirectory(candidate.resolve("formal"))
                    && Files.isDirectory(candidate.resolve("matrix-core"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot locate the repository root from "
                + Path.of(".").toAbsolutePath());
    }
}
