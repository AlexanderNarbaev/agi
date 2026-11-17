package io.matrix.knowledge;

import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.SourceSpanDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HARD REVIEW — the CORRECTED definition rule (P1).
 *
 * <p>The rule now requires the concept's name to BE the subject of the qualifying
 * sentence and rejects the names that do not denote a referent. This probe measures the
 * definitions it produced, attacks it, and counts what it lost.</p>
 */
class HardReviewCorrectedRuleTest {

    private static final String WORKSPACE = "corrected";

    @TempDir
    Path directory;

    /** The definitions the corrected rule produced — quoted for the review. */
    @Test
    @DisplayName("the definitions the corrected rule produced on the seed corpus")
    void theDefinitionsOfTheCorrectedRule() throws Exception {
        KnowledgeWorkbench bench = bench();
        Map<String, String> names = names(bench);
        List<ConceptDefinition> definitions = bench.store().conceptDefinitions(WORKSPACE);
        System.out.println("### the corrected rule - the seed corpus");
        System.out.println("  concepts=" + bench.store().concepts(WORKSPACE).size()
                + " definitions=" + definitions.size());
        int index = 0;
        for (ConceptDefinition definition : definitions) {
            index++;
            System.out.println("  [" + index + "] " + names.getOrDefault(definition.conceptId(),
                    definition.conceptId()));
            System.out.println("       -> " + definition.definition());
        }
    }

    /** The negation, in its plain and in its subtler forms. */
    @Test
    @DisplayName("the negation: plain, contracted, and qualified")
    void theNegationForms() {
        System.out.println("### the negation forms");
        String[][] probes = {
                {"A trench is a long narrow depression in the ocean floor.", "A trench"},
                {"A trench is not a long narrow depression in the ocean floor.", "A trench"},
                {"A trench isn't a long narrow depression in the ocean floor.", "A trench"},
                {"A trench is largely not a long narrow depression in the ocean floor.", "A trench"},
                {"A trench is no longer a long narrow depression in the ocean floor.", "A trench"},
                {"A trench is not the same as a deep-ocean trench.", "A trench"},
                {"A trench was not carved by a subducting plate.", "A trench"},
                {"A trench is sometimes not a deep-ocean trench.", "A trench"},
                {"A trench is defined as not a canyon.", "A trench"},
        };
        for (String[] probe : probes) {
            System.out.println("  " + (SourceSpanDefinitions.statesDefinition(probe[0], probe[1])
                    ? "ACCEPTED" : "rejected") + " :: " + probe[0]);
        }
    }

    /** What the corrected rule lost: the concepts whose own document holds a subject sentence. */
    @Test
    @DisplayName("the lost true referents: the concepts the rule dropped")
    void theLostTrueReferents() throws Exception {
        KnowledgeWorkbench bench = bench();
        Map<String, String> names = names(bench);
        Map<String, String> definitions = new HashMap<>();
        for (ConceptDefinition definition : bench.store().conceptDefinitions(WORKSPACE)) {
            definitions.put(definition.conceptId(), definition.definition());
        }
        List<Claim> claims = bench.store().claims(WORKSPACE);
        List<SourceSpanDefinitions.Sentence> sentences =
                SourceSpanDefinitions.sentencesOf(claims, null);
        Map<String, List<SourceSpanDefinitions.Sentence>> bySource = new LinkedHashMap<>();
        Map<String, List<String>> sourcesOfConcept = new LinkedHashMap<>();
        for (SourceSpanDefinitions.Sentence sentence : sentences) {
            bySource.computeIfAbsent(sentence.claim().introducedBySourceId(),
                    key -> new ArrayList<>()).add(sentence);
            sourcesOfConcept.computeIfAbsent(sentence.claim().conceptId(),
                    key -> new ArrayList<>()).add(sentence.claim().introducedBySourceId());
        }
        int lost = 0;
        int lostReferents = 0;
        List<String> examples = new ArrayList<>();
        for (Concept concept : bench.store().concepts(WORKSPACE)) {
            if (definitions.containsKey(concept.id())) {
                continue;
            }
            lost++;
            boolean referent = SourceSpanDefinitions.isReferent(concept.name());
            String subjectSentence = null;
            for (String sourceId : sourcesOfConcept.getOrDefault(concept.id(), List.of())) {
                for (SourceSpanDefinitions.Sentence sentence : bySource.getOrDefault(sourceId, List.of())) {
                    if (SourceSpanDefinitions.statesDefinition(sentence.text(), concept.name())) {
                        subjectSentence = sentence.text();
                    }
                }
            }
            if (subjectSentence != null) {
                lostReferents++;
                if (examples.size() < 8) {
                    examples.add("  LOST a qualifying sentence :: " + concept.name()
                            + "\n      " + subjectSentence);
                }
            } else if (referent && examples.size() < 12) {
                examples.add("  a referent with no definition :: " + concept.name());
            }
        }
        System.out.println("### the corrected rule - what it lost");
        System.out.println("  concepts without a definition: " + lost);
        System.out.println("  of those, a sentence that DOES qualify by the rule: " + lostReferents);
        for (String example : examples) {
            System.out.println(example);
        }
    }

    /** The same concept name as the subject in two documents: which sentence wins. */
    @Test
    @DisplayName("the same name in two documents: which sentence wins")
    void theSameNameInTwoDocuments() throws Exception {
        KnowledgeWorkbench bench = bench();
        Map<String, String> names = names(bench);
        List<SourceSpanDefinitions.Sentence> sentences =
                SourceSpanDefinitions.sentencesOf(bench.store().claims(WORKSPACE), null);
        List<Source> sources = bench.store().sources(WORKSPACE);
        Map<String, List<SourceSpanDefinitions.Sentence>> bySource = new LinkedHashMap<>();
        for (SourceSpanDefinitions.Sentence sentence : sentences) {
            bySource.computeIfAbsent(sentence.claim().introducedBySourceId(),
                    key -> new ArrayList<>()).add(sentence);
        }
        Map<String, String> won = new HashMap<>();
        for (ConceptDefinition definition : bench.store().conceptDefinitions(WORKSPACE)) {
            won.put(definition.conceptId(), definition.definition());
        }
        System.out.println("### the same name in two documents");
        int shown = 0;
        for (Concept concept : bench.store().concepts(WORKSPACE)) {
            if (!won.containsKey(concept.id())) {
                continue;
            }
            List<String> candidates = new ArrayList<>();
            for (Source source : sources) {
                for (SourceSpanDefinitions.Sentence sentence
                        : bySource.getOrDefault(source.id(), List.of())) {
                    if (SourceSpanDefinitions.statesDefinition(sentence.text(), concept.name())
                            && !candidates.contains(sentence.text())) {
                        candidates.add(sentence.text());
                    }
                }
            }
            if (candidates.size() > 1 && shown < 6) {
                shown++;
                System.out.println("  " + concept.name() + " has " + candidates.size()
                        + " qualifying sentences:");
                for (String candidate : candidates) {
                    System.out.println("     " + (candidate.equals(won.get(concept.id()))
                            ? "WON  " : "lost ") + candidate);
                }
            }
        }
    }

    // ── the fixtures ────────────────────────────────────────────────────────

    private KnowledgeWorkbench bench() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        for (ClasspathCorpus.Entry entry : seed.entries()) {
            bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
        }
        return bench;
    }

    private static Map<String, String> names(KnowledgeWorkbench bench) {
        Map<String, String> names = new HashMap<>();
        for (Concept concept : bench.store().concepts(WORKSPACE)) {
            names.put(concept.id(), concept.name());
        }
        return names;
    }
}
