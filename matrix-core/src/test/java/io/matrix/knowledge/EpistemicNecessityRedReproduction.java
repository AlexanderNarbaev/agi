package io.matrix.knowledge;

import io.matrix.crj.necessity.Crj01NecessityCases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — the RED reproduction.
 *
 * <p><b>What this is.</b> A reproduction that must <b>fail</b>, and whose failure is the
 * evidence. The milestone claims that the missing prerequisite becomes visible because the
 * task's necessary evidence is DERIVED from the workspace's own reading. This reproduction
 * asserts that claim with the derivation switched <b>off</b>, so the assertion fails, and the
 * failure says exactly what is missing: without the derivation the rule has nothing to judge
 * on, falls back to the incumbent's verdict, and the missing prerequisite is invisible
 * again.</p>
 *
 * <p><b>Why the reproduction is meaningful, and how it is made so.</b> A test that fails for
 * any reason is not evidence. Two things make this one specific:</p>
 * <ul>
 * <li>the <b>same</b> scope is assessed twice — once with the derivation <b>on</b>, where the
 * rule refuses the scope and names the requirement, and once with it <b>off</b>, where the
 * rule cannot decide and the incumbent's verdict stands. The first assessment is asserted
 * <b>before</b> the second, so the test proves on its own run that the scope IS one the rule
 * refuses when it can see; only the derivation is the difference;</li>
 * <li>the scope is found by the <b>production derivation's own verdict</b> and not by the
 * oracle: the reproduction searches the subsets of the workspace's sources for one the rule
 * refuses, and it reads no oracle label, no case id and no gold to do it.</li>
 * </ul>
 *
 * <p><b>Why it is a separate class and not a failing test in the green suite.</b> A red test
 * in the suite would make every build red. This class is enabled <b>only</b> when the run
 * says so (the environment variable {@code CRJ01_NECESSITY_RED=true}, which the test JVM
 * inherits), so the ordinary suite stays green and the reproduction is produced by a dedicated
 * run whose XML is retained.</p>
 *
 * <p><b>What it does NOT do.</b> It does not inject a gold, an oracle label or a case id into
 * the production logic: the only thing it changes is WHICH WORKBENCH asks the question — the
 * one that derives or the negative control that does not — and that difference is an
 * immutable input of the workbench, not a switch any caller can move.</p>
 */
@EnabledIfEnvironmentVariable(named = "CRJ01_NECESSITY_RED", matches = "true")
class EpistemicNecessityRedReproduction {

    /**
     * The negative control — a workbench that does NOT derive.
     *
     * <p><b>Why this is a workbench and not a switch.</b> The first version turned the
     * derivation off through a <b>mutable static</b> in production code, which any caller
     * could move and which the served path read. The coordinator's acceptance found it. The
     * derivation is now an <b>immutable input of the workbench</b>: the reproduction builds
     * a workbench that cannot derive, the served path builds one that can, and no request can
     * change either. The served behaviour is unchanged.</p>
     */
    private KnowledgeWorkbench bench;

    @BeforeEach
    void setUp() {
        bench = null;
    }

    @AfterEach
    void tearDown() {
        if (bench != null) {
            bench.close();
        }
    }

    @Test
    @DisplayName("without the derivation the necessary-prerequisite rule refuses nothing: the "
            + "missing prerequisite is NOT visible — this assertion is EXPECTED TO FAIL")
    void withoutTheDerivationTheMissingPrerequisiteIsNotVisible() throws Exception {
        Crj01NecessityCases.CaseSet caseSet = Crj01NecessityCases.read();
        Crj01NecessityCases.Case subject = null;
        for (Crj01NecessityCases.Case testCase : caseSet.cases()) {
            if ("MISSING_PREREQUISITE".equals(testCase.condition())) {
                subject = testCase;
                break;
            }
        }
        assertThat(subject).as("the case set carries a missing-prerequisite case").isNotNull();
        // The subject's own question is the one the reproduction asks, but the scope it
        // searches is wider than that case's: the reproduction needs a scope on which the
        // TWO RULES DISAGREE, because the derivation is the only thing that can make them
        // disagree. It finds that scope by asking both rules.

        Path directory = Files.createTempDirectory("crj01-red-");
        String store = Files.createDirectories(directory.resolve("db")).resolve("workbench.sqlite")
                .toString();
        // THE NEGATIVE CONTROL — a workbench over the same store that does NOT derive, and
        // the workbench that does. The two assessments differ by the derivation alone.
        bench = KnowledgeWorkbench.atWithoutDerivation(store, java.time.Clock.systemUTC());
        KnowledgeWorkbench deriving = KnowledgeWorkbench.at(store);
        try {
            String workspace = "crj01-red";
            Crj01NecessityCases.Domain domain = caseSet.domain(subject.domain());
            List<String> sourceIds = new ArrayList<>();
            for (String file : domain.files()) {
                Path corpus = Files.createDirectories(directory.resolve("corpus"));
                Path bytes = corpus.resolve(file);
                try (java.io.InputStream in = EpistemicNecessityRedReproduction.class
                        .getResourceAsStream("/crj01-reach/" + domain.id() + "/" + file)) {
                    assertThat(in).as("the corpus holds %s", file).isNotNull();
                    Files.write(bytes, in.readAllBytes());
                }
                sourceIds.add(bench.importDocument(workspace, bytes, file, "en").source()
                        .sourceId());
            }
            KnowledgeSnapshot view = bench.workspace(workspace);
            AbstractionLevel level = AbstractionLevel.valueOf(subject.level());

            // ── THE SCOPE — found by the PRODUCTION derivation's own verdict ───────────────
            // The scope must be one the rule REFUSES when it can see, or the reproduction
            // would show nothing. The search reads no oracle: it asks the rule.
            // THE SCOPE — one on which the two rules DISAGREE: the incumbent admits it and
            // the rule refuses it, with the derivation running. The disagreement is found by
            // asking both rules, and it is the ONLY thing the derivation can produce, so
            // the reproduction would show nothing without it.
            Set<String> narrow = null;
            ScopeAssessment green = null;
            Crj01NecessityCases.Case asked = null;
            AbstractionLevel askedLevel = level;
            outer:
            for (Crj01NecessityCases.Case candidateCase : caseSet.cases()) {
                if (!candidateCase.domain().equals(subject.domain())) {
                    continue;
                }
                AbstractionLevel candidateLevel =
                        AbstractionLevel.valueOf(candidateCase.level());
                for (Set<String> candidate : properSubsets(sourceIds)) {
                    ScopeAssessment assessment = deriving.assessScope(view, view,
                            candidateCase.question(), candidateLevel, candidate, false,
                            ScopeCriterion.NECESSARY_PREREQUISITE);
                    if (assessment.applicable()) {
                        continue;
                    }
                    ScopeAssessment incumbent = deriving.assessScope(view, view,
                            candidateCase.question(), candidateLevel, candidate, false,
                            ScopeCriterion.ANCHOR);
                    if (!incumbent.applicable()) {
                        continue;
                    }
                    narrow = candidate;
                    green = assessment;
                    asked = candidateCase;
                    askedLevel = candidateLevel;
                    break outer;
                }
            }
            assertThat(narrow)
                    .as("a question and a scope on which the two rules disagree: the incumbent "
                            + "admits and the rule refuses")
                    .isNotNull();
            assertThat(deriving.derivationEnabled())
                    .as("the workbench that derives says so")
                    .isTrue();
            assertThat(bench.derivationEnabled())
                    .as("the negative control's workbench does not derive")
                    .isFalse();
            subject = asked;
            level = askedLevel;
            assertThat(green.taskPrerequisites())
                    .as("the derivation ran and produced the task's requirements")
                    .isNotNull();
            assertThat(green.missingClaimIds())
                    .as("the refusal names the requirement the scope does not carry")
                    .isNotEmpty();

            // ── THE SAME SCOPE, THE DERIVATION OFF — this is the reproduction ──────────────
            ScopeAssessment red = bench.assessScope(view, view, subject.question(), level,
                    narrow, false, ScopeCriterion.NECESSARY_PREREQUISITE);
            assertThat(red.applicable())
                    .as("without the derivation the rule refuses the scope (the reason it gives "
                            + "is: %s)", red.reason())
                    .isFalse();
            assertThat(red.missingClaimIds())
                    .as("without the derivation the missing prerequisite is NOT named")
                    .isNotEmpty();
        } finally {
            deriving.close();
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.io.IOException ignored) {
                        // a temporary directory that cannot be cleaned is not a failure
                    }
                });
            }
        }
    }

    /**
     * The proper, non-empty subsets of a set of sources, largest first.
     *
     * @param sources the sources
     * @return the subsets
     */
    private static List<Set<String>> properSubsets(List<String> sources) {
        List<Set<String>> subsets = new ArrayList<>();
        int count = 1 << sources.size();
        for (int mask = 1; mask < count - 1; mask++) {
            Set<String> subset = new LinkedHashSet<>();
            for (int index = 0; index < sources.size(); index++) {
                if ((mask & (1 << index)) != 0) {
                    subset.add(sources.get(index));
                }
            }
            subsets.add(subset);
        }
        subsets.sort((left, right) -> Integer.compare(right.size(), left.size()));
        return subsets;
    }
}
