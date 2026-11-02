package io.matrix.knowledge.benchmark;

import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * The synthetic typo benchmark (RECON-W42.7).
 *
 * <p>The mission's §14 names a row this system had never measured:
 * "≥ 95 % intent preservation on a synthetic typo benchmark". The W42
 * benchmark's MISSPELLED category holds four items — a demonstration, not
 * a measurement — and no benchmark said what the user MEANT.
 *
 * <p><b>What a case is.</b> A question about a concept of the workspace,
 * and the same question with a typo in it. The intended question is the
 * LABEL: the case carries both, and the intended one is what the system's
 * answer to the typo must agree with.
 *
 * <p><b>The typos are synthetic and deterministic.</b> Three kinds — the
 * substitution, the transposition and the deletion — over the workspace's
 * own concept names, chosen by a fixed seed. The same workspace and the
 * same seed always produce the same cases, so the benchmark is
 * reproducible and the version of the dataset is the version of the seed.
 *
 * <p><b>What the measurement is.</b> The share of the cases where the
 * answer to the misspelled question covers the same key terms as the
 * answer to the intended one. The intent is preserved when the system
 * ends up at the same knowledge.
 */
public final class TypoBenchmarkGenerator {

    /** The seed of the benchmark. The dataset's version is this seed. */
    public static final long SEED = 42L;

    /** The kinds of typo the benchmark uses. */
    public enum Typo {
        /** A letter is replaced by another. */
        SUBSTITUTION,
        /** Two neighbouring letters are swapped. */
        TRANSPOSITION,
        /** A letter is dropped. */
        DELETION
    }

    /** One case: the misspelled question and the question that was meant. */
    public record Case(
            String id,
            String workspace,
            String question,
            String intendedQuestion,
            String concept,
            Typo typo,
            List<String> keyTerms) {

        public Case {
            keyTerms = List.copyOf(keyTerms == null ? List.of() : keyTerms);
        }
    }

    /** The benchmark: the cases and the seed that produced them. */
    public record Benchmark(
            String id,
            String version,
            long seed,
            int cases,
            List<Case> casesList) {

        public Benchmark {
            casesList = List.copyOf(casesList == null ? List.of() : casesList);
        }
    }

    private TypoBenchmarkGenerator() {
    }

    /**
     * Generates the benchmark over a workspace's concepts.
     *
     * @param snapshot the workspace
     * @return the benchmark
     */
    public static Benchmark generate(KnowledgeSnapshot snapshot) {
        Random random = new Random(SEED);
        List<Case> cases = new ArrayList<>();
        int index = 0;
        for (Concept concept : snapshot.concepts()) {
            String name = concept.name();
            if (name == null || name.isBlank() || name.length() < 6) {
                continue;
            }
            String question = "What is " + name + "?";
            List<String> keyTerms = keyTerms(concept);
            Typo[] typos = Typo.values();
            for (Typo typo : typos) {
                String misspelled = apply(typo, name, random);
                if (misspelled == null || misspelled.equals(name)) {
                    continue;
                }
                cases.add(new Case("typo-" + index, snapshot.workspaceId(),
                        "What is " + misspelled + "?", question, name, typo, keyTerms));
                index++;
            }
        }
        return new Benchmark("w42-typo-benchmark-v1", String.valueOf(SEED), SEED, cases.size(),
                cases);
    }

    /**
     * The key terms of a concept: its own name first, then the terms of
     * its claims.
     *
     * @param concept the concept
     * @return the terms
     */
    private static List<String> keyTerms(Concept concept) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : io.matrix.knowledge.TextFeatures.originalTokens(concept.name())) {
            terms.add(token);
        }
        for (String token : io.matrix.knowledge.TextFeatures.originalTokens(
                concept.definition() == null ? "" : concept.definition())) {
            if (terms.size() >= 6) {
                break;
            }
            terms.add(token);
        }
        return List.copyOf(terms);
    }

    /**
     * Applies a typo to a name.
     *
     * @param typo the kind of typo
     * @param name the name
     * @param random the generator of the position
     * @return the misspelled name, or null when the name is too short
     */
    static String apply(Typo typo, String name, Random random) {
        if (name == null || name.length() < 6) {
            return null;
        }
        int position = 1 + random.nextInt(name.length() - 3);
        return switch (typo) {
            case SUBSTITUTION -> {
                char replacement = name.charAt(position) == 'a' ? 'b' : 'a';
                yield name.substring(0, position) + replacement + name.substring(position + 1);
            }
            case TRANSPOSITION -> {
                char first = name.charAt(position);
                char second = name.charAt(position + 1);
                yield name.substring(0, position) + second + first + name.substring(position + 2);
            }
            case DELETION -> name.substring(0, position) + name.substring(position + 1);
        };
    }

    /**
     * The share of the cases where the answer to the misspelled question
     * covers the same key terms as the answer to the intended one.
     *
     * @param benchmark the benchmark
     * @param intended the answer to each intended question, by case id
     * @param actual the answer to each misspelled question, by case id
     * @return the share, and the cases that did not preserve the intent
     */
    public static Result measure(Benchmark benchmark, java.util.Map<String, String> intended,
                                 java.util.Map<String, String> actual) {
        int preserved = 0;
        List<String> failures = new ArrayList<>();
        for (Case item : benchmark.casesList()) {
            String wanted = intended.get(item.id());
            String got = actual.get(item.id());
            // THE BAR IS THE INTENDED ANSWER, NOT THE CONCEPT.
            //
            // The first version measured the answer against the concept's
            // own key terms, and it punished a POOR answer rather than a
            // wrong one: the reference answer about photorespiration
            // mentions one of the concept's six terms, so the three
            // photorespiration cases failed even though the misspelled
            // question got exactly the reference's answer. The intent is
            // preserved when the answer covers everything the INTENDED
            // answer covered.
            boolean same = wanted != null && got != null
                    && coversAll(got, coveredTerms(wanted, item.keyTerms()));
            if (same) {
                preserved++;
            } else {
                failures.add(item.id() + " (" + item.typo() + ", " + item.question() + ")");
            }
        }
        double share = benchmark.casesList().isEmpty()
                ? 0.0 : (double) preserved / benchmark.casesList().size();
        return new Result(preserved, benchmark.casesList().size(), share, failures);
    }

    /**
     * The key terms an answer covers.
     *
     * @param answer the answer
     * @param keyTerms the terms
     * @return the covered ones
     */
    private static List<String> coveredTerms(String answer, List<String> keyTerms) {
        String lower = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        List<String> covered = new ArrayList<>();
        for (String term : keyTerms) {
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                covered.add(term);
            }
        }
        return covered;
    }

    /**
     * Whether an answer covers every one of the given terms.
     *
     * @param answer the answer
     * @param terms the terms
     * @return whether it covers them
     */
    private static boolean coversAll(String answer, List<String> terms) {
        String lower = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (!lower.contains(term.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /** The result of the measurement. */
    public record Result(int preserved, int cases, double share, List<String> failures) {

        public Result {
            failures = List.copyOf(failures == null ? List.of() : failures);
        }
    }
}
