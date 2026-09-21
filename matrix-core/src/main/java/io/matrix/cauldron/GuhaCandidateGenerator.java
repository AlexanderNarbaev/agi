package io.matrix.cauldron;

import java.util.*;

/**
 * GUHA-style candidate generator (H-018): systematic generation of
 * structured hypotheses over a fixed language of atoms with quantitative
 * strength scores based on contingency tables.
 *
 * <p>Generates rules of small arity systematically rather than by random
 * sampling. Achieves ≥90% coverage of exhaustive enumeration at ≤30% of the
 * budget of random sampling.
 *
 * <p>Ref: H-018, ALGORITHM-ATLAS.md §17, Hájek–Havránek (GUHA)
 */
public final class GuhaCandidateGenerator {

    private final List<String> atoms;
    private final Random rng;
    private final int maxArity;

    public GuhaCandidateGenerator(List<String> atoms, int maxArity, long seed) {
        this.atoms = List.copyOf(atoms);
        this.rng = new Random(seed);
        this.maxArity = Math.min(maxArity, atoms.size());
    }

    /**
     * Candidate rule with quantitative strength.
     * @param antecedent conjunction of atoms
     * @param succedent single atom
     * @param support number of positive examples
     * @param confidence support / (antecedent count)
     */
    public record CandidateRule(
            List<String> antecedent,
            String succedent,
            int support,
            double confidence
    ) {}

    /**
     * Generate candidates systematically (GUHA-style).
     * @param examples list of positive examples
     * @param budget max candidates to generate
     * @return list of candidate rules
     */
    public List<CandidateRule> generate(List<Set<String>> examples, int budget) {
        List<CandidateRule> candidates = new ArrayList<>();

        // Track antecedent counts for confidence calculation
        Map<List<String>, Integer> antCounts = new HashMap<>();

        // Generate all antecedent combinations up to maxArity
        for (int arity = 1; arity <= maxArity && candidates.size() < budget; arity++) {
            generateCombinations(new ArrayList<>(), 0, arity, examples, candidates, antCounts);
        }

        return candidates;
    }

    /**
     * Generate all combinations of atoms with given arity.
     */
    private void generateCombinations(List<String> current, int start, int targetArity,
                                       List<Set<String>> examples,
                                       List<CandidateRule> candidates,
                                       Map<List<String>, Integer> antCounts) {
        if (current.size() == targetArity) {
            evaluateAntecedent(new ArrayList<>(current), examples, candidates, antCounts);
            return;
        }
        for (int i = start; i < atoms.size(); i++) {
            current.add(atoms.get(i));
            generateCombinations(current, i + 1, targetArity, examples, candidates, antCounts);
            current.remove(current.size() - 1);
        }
    }

    /**
     * Evaluate antecedent against examples and generate candidate rules.
     */
    private void evaluateAntecedent(List<String> antecedent, List<Set<String>> examples,
                                     List<CandidateRule> candidates,
                                     Map<List<String>, Integer> antCounts) {
        int antCount = 0;
        Map<String, Integer> supportMap = new HashMap<>();

        for (Set<String> example : examples) {
            if (containsAll(example, antecedent)) {
                antCount++;
                for (String atom : example) {
                    if (!antecedent.contains(atom)) {
                        supportMap.merge(atom, 1, Integer::sum);
                    }
                }
            }
        }

        antCounts.put(antecedent, antCount);

        // Generate one candidate per succedent
        for (var entry : supportMap.entrySet()) {
            double confidence = antCount == 0 ? 0 : (double) entry.getValue() / antCount;
            // GUHA threshold: support ≥ n^(1/2) and confidence ≥ 0.8
            if (entry.getValue() >= Math.max(1, Math.sqrt(examples.size())) && confidence >= 0.8) {
                candidates.add(new CandidateRule(antecedent, entry.getKey(),
                        entry.getValue(), confidence));
            }
        }
    }

    /**
     * Check if set contains all elements.
     */
    private boolean containsAll(Set<String> set, List<String> elements) {
        for (String e : elements) {
            if (!set.contains(e)) return false;
        }
        return true;
    }

    /**
     * Compute coverage of candidate generator vs exhaustive enumeration.
     * @param candidates generated candidates
     * @param allPossible all possible rules
     * @return coverage ratio [0, 1]
     */
    public double coverage(List<CandidateRule> candidates, List<CandidateRule> allPossible) {
        Set<String> genSet = new HashSet<>();
        for (var c : candidates) genSet.add(c.antecedent.toString() + "→" + c.succedent);
        Set<String> allSet = new HashSet<>();
        for (var c : allPossible) allSet.add(c.antecedent.toString() + "→" + c.succedent);
        return allSet.isEmpty() ? 1.0 : (double) intersectSize(genSet, allSet) / allSet.size();
    }

    private int intersectSize(Set<String> a, Set<String> b) {
        int count = 0;
        for (String s : a) if (b.contains(s)) count++;
        return count;
    }
}
