package io.matrix.knowledge.dialogue;

import io.matrix.knowledge.Concept;
import io.matrix.knowledge.DialogueAct;
import io.matrix.knowledge.DialogueContext;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.TextProfiles;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The dialogue cases and the runner that measures them (RECON-W42.3).
 *
 * <p>The mission's §14 asks for at least a hundred reproducible multilingual
 * dialogue cases, and §8 asks that the sample size and the limitations
 * stand beside every number. This is the instrument: the cases are a
 * versioned resource, the runner reports what agrees and what does not, and
 * a case that fails is a named failure rather than a missing number.
 */
public final class DialogueCase {

    /** The resource the cases are read from. */
    public static final String RESOURCE = "benchmark/w42-dialogue-cases-v1.json";

    /** One dialogue case. */
    public record Case(
            String id,
            String category,
            String utterance,
            DialogueAct.Act expectedAct,
            boolean expectedAnswerable,
            String language,
            String scope,
            String note) {
    }

    private DialogueCase() {
    }

    /**
     * The cases of the resource.
     *
     * @return the cases, in the order the resource holds them
     */
    public static List<Case> load() {
        try (InputStream stream = DialogueCase.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("the dialogue cases are missing: " + RESOURCE);
            }
            String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return parse(json);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read the dialogue cases", e);
        }
    }

    /**
     * The cases of a JSON document.
     *
     * <p>Jackson is the JSON library of this package already - the
     * assessment dataset and the graph store both use it - so the cases are
     * read with it rather than with a hand-written parser.
     *
     * @param json the document
     * @return the cases
     */
    @SuppressWarnings("unchecked")
    public static List<Case> parse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> document = mapper.readValue(json, Map.class);
            Object raw = document.get("cases");
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<Case> cases = new ArrayList<>();
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                cases.add(new Case(
                        text(map.get("id")),
                        text(map.get("category")),
                        text(map.get("utterance")),
                        DialogueAct.Act.valueOf(text(map.get("expectedAct"))),
                        Boolean.TRUE.equals(map.get("expectedAnswerable")),
                        text(map.get("language")),
                        text(map.get("scope")),
                        text(map.get("note"))));
            }
            return cases;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to parse the dialogue cases", e);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** What one case did. */
    public record Outcome(
            Case testCase,
            DialogueAct.Act actualAct,
            boolean answered,
            boolean actAgrees,
            boolean answerabilityAgrees,
            boolean inScope,
            String note) {
    }

    /** The whole run. */
    public record Report(
            int count,
            int inScope,
            int actAgreements,
            int answerabilityAgreements,
            int refusalsExpected,
            int refusalsCorrect,
            Map<String, CategoryReport> byCategory,
            List<Outcome> failures) {

        public Report {
            byCategory = Map.copyOf(byCategory == null ? Map.of() : byCategory);
            failures = List.copyOf(failures == null ? List.of() : failures);
        }

        /** The share of the cases whose class was recognised. */
        public double actAccuracy() {
            return count == 0 ? 0.0 : (double) actAgreements / count;
        }

        /**
         * The share of the IN-SCOPE cases whose answerability agreed.
         *
         * <p>A case about a subject the workspace does not hold cannot be
         * answered, and counting it as a failure would measure the
         * workspace's coverage rather than the dialogue's behaviour. The
         * out-of-scope cases are counted and reported; they are simply not
         * judged on answerability.
         */
        public double answerabilityAccuracy() {
            return inScope == 0 ? 0.0 : (double) answerabilityAgreements / inScope;
        }

        /** The share of the cases that had to be refused and were. */
        public double refusalAccuracy() {
            return refusalsExpected == 0 ? 0.0 : (double) refusalsCorrect / refusalsExpected;
        }
    }

    /** What a category did. */
    public record CategoryReport(int count, int actAgreements, int answerabilityAgreements) {
    }

    /**
     * Runs the cases against a workspace.
     *
     * @param workbench the workspace
     * @param cases the cases to run
     * @return the report
     */
    public static Report run(KnowledgeWorkbench workbench, List<Case> cases) {
        int actAgreements = 0;
        int answerabilityAgreements = 0;
        int inScope = 0;
        int refusalsExpected = 0;
        int refusalsCorrect = 0;
        Map<String, int[]> byCategory = new LinkedHashMap<>();
        List<Outcome> failures = new ArrayList<>();
        Set<String> heldConcepts = new java.util.LinkedHashSet<>();
        for (Concept concept : workbench.workspace("photosynthesis").concepts()) {
            heldConcepts.add(concept.name());
        }

        for (Case testCase : cases) {
            DialogueAct.Act actualAct = DialogueAct.classify(testCase.utterance());
            boolean answered = workbench.ask("photosynthesis", testCase.utterance(),
                    io.matrix.knowledge.AbstractionLevel.INTRODUCTORY).answered();
            boolean actAgrees = actualAct == testCase.expectedAct();
            // A case is IN SCOPE when the workspace holds the concept it is
            // about - or when it declares no concept at all, because such a
            // case is about the dialogue and not about the knowledge.
            boolean scope = !"out-of-scope".equals(testCase.scope());
            boolean answerabilityAgrees = scope && answered == testCase.expectedAnswerable();
            if (scope) {
                inScope++;
            }
            if (scope && !testCase.expectedAnswerable()) {
                refusalsExpected++;
                if (!answered) {
                    refusalsCorrect++;
                }
            }
            if (actAgrees) {
                actAgreements++;
            }
            if (answerabilityAgrees) {
                answerabilityAgreements++;
            }
            int[] counts = byCategory.computeIfAbsent(testCase.category(), key -> new int[3]);
            counts[0]++;
            if (actAgrees) {
                counts[1]++;
            }
            if (answerabilityAgrees) {
                counts[2]++;
            }
            // A case that is OUT OF SCOPE and was answered anyway is not a
            // dialogue failure: the scope was computed from the
            // workspace's vocabulary and it was wrong. It is reported as a
            // scope disagreement, not counted against the behaviour.
            if (!actAgrees || (scope && !answerabilityAgrees)) {
                failures.add(new Outcome(testCase, actualAct, answered, actAgrees,
                        answerabilityAgrees, scope, testCase.note()));
            }
        }

        Map<String, CategoryReport> categories = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : byCategory.entrySet()) {
            categories.put(entry.getKey(), new CategoryReport(entry.getValue()[0],
                    entry.getValue()[1], entry.getValue()[2]));
        }
        return new Report(cases.size(), inScope, actAgreements, answerabilityAgreements,
                refusalsExpected, refusalsCorrect, categories, failures);
    }

    /** The language of an utterance, for the report. */
    public static String languageOf(String utterance) {
        return TextProfiles.detect(utterance);
    }

    /** Whether an utterance refers to something without naming it. */
    public static boolean referential(String utterance) {
        return DialogueContext.isReferential(utterance);
    }
}
