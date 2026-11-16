package io.matrix.knowledge.concept.definitions;

import io.matrix.knowledge.Concept;
import io.matrix.knowledge.ConceptDefinition;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HARD REVIEW — P1, the precision of the definition rule.
 *
 * <p>The wave's own frozen hand-labelling of the concept spans is the yardstick: a span it
 * labels "not a concept" is not a concept, and a definition attached to it is not a usable
 * definition. This probe crosses the 120 definitions the rule produced against that
 * labelling and quotes the sentences it can demonstrate.</p>
 */
class HardReviewDefinitionPrecisionTest {

    private static final String WORKSPACE = "precision";

    @TempDir
    Path directory;

    @Test
    @DisplayName("the definitions the rule produced, against the wave's own hand-labelling")
    void theDefinitionsAgainstTheHandLabelling() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        for (ClasspathCorpus.Entry entry : seed.entries()) {
            bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
        }
        KnowledgeStore store = new KnowledgeStore(database);
        List<Concept> concepts = store.concepts(WORKSPACE);
        List<ConceptDefinition> definitions = store.conceptDefinitions(WORKSPACE);
        Map<String, String> names = new HashMap<>();
        for (Concept concept : concepts) {
            names.put(concept.id(), concept.name());
        }
        Map<String, Boolean> labels = new HashMap<>();
        for (HandLabelledSpans.HandLabel label : HandLabelledSpans.LABELS) {
            labels.put(label.span(), label.concept());
        }
        System.out.println("### P1-precision - the seed corpus");
        System.out.println("  concepts=" + concepts.size() + " definitions=" + definitions.size());
        int labelled = 0;
        int notAConcept = 0;
        List<String> offenders = new ArrayList<>();
        for (ConceptDefinition definition : definitions) {
            String name = names.getOrDefault(definition.conceptId(), definition.conceptId());
            Boolean isConcept = labels.get(name);
            if (isConcept == null) {
                continue;
            }
            labelled++;
            if (!isConcept) {
                notAConcept++;
                offenders.add("  NOT A CONCEPT per the frozen hand-labelling :: " + name
                        + "\n      definition: " + definition.definition());
            }
        }
        System.out.println("  definitions whose concept name the frozen hand-labelling also labels: "
                + labelled);
        System.out.println("  of those, attached to a span the labelling calls NOT a concept: "
                + notAConcept + " (of " + definitions.size() + " definitions)");
        for (String offender : offenders) {
            System.out.println(offender);
        }
    }

    @Test
    @DisplayName("a first qualifying sentence that is not about the concept")
    void aFirstQualifyingSentenceThatIsNotAboutTheConcept() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        for (ClasspathCorpus.Entry entry : seed.entries()) {
            bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
        }
        KnowledgeStore store = new KnowledgeStore(database);
        Map<String, String> names = new HashMap<>();
        for (Concept concept : store.concepts(WORKSPACE)) {
            names.put(concept.id(), concept.name());
        }
        System.out.println("### P1-first - the sentences the rule took first");
        for (ConceptDefinition definition : store.conceptDefinitions(WORKSPACE)) {
            String name = names.getOrDefault(definition.conceptId(), definition.conceptId());
            String text = definition.definition();
            // the name is the subject of the sentence?  (a discourse marker may precede it)
            List<String> sentence = words(text);
            List<String> subject = words(name);
            int at = indexOfSubject(sentence, subject);
            if (at < 0 || at > 12) {
                System.out.println("  the name is NOT the subject :: " + name + "\n      "
                        + text);
            }
        }
    }

    private static int indexOfSubject(List<String> sentence, List<String> subject) {
        if (subject.isEmpty()) {
            return -1;
        }
        // a discourse marker or a subordinate clause may precede the subject
        java.util.List<String> markers = java.util.List.of("furthermore", "moreover", "however",
                "as", "in", "because", "since", "when", "while", "whenever", "if", "thus",
                "therefore", "so", "and", "but", "yet", "also", "this", "that", "these", "those",
                "it", "they", "he", "she", "we", "you", "i");
        int limit = 0;
        while (limit < sentence.size() && markers.contains(sentence.get(limit))) {
            limit++;
        }
        return matchesAt(sentence, limit, subject) ? limit : -1;
    }

    private static boolean matchesAt(List<String> sentence, int start, List<String> subject) {
        if (start < 0 || start + subject.size() > sentence.size()) {
            return false;
        }
        for (int offset = 0; offset < subject.size(); offset++) {
            if (!sentence.get(start + offset).equals(subject.get(offset))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[A-Za-z0-9]+").matcher(text == null ? "" : text);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(java.util.Locale.ROOT));
        }
        return words;
    }
}
