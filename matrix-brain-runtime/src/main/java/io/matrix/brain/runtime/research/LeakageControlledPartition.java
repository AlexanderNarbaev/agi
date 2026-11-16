package io.matrix.brain.runtime.research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EPISTEMIC-INTEGRITY P3 — the LEAKAGE-CONTROLLED partition of the observations.
 *
 * <p><b>Why a partition needs its own class.</b> The label this workstream studies is
 * computed from the text the mind produced. A feature that contains that text can
 * therefore "predict" the label for reasons that have nothing to do with learning. Two
 * things have to be kept apart, and this class keeps them apart:</p>
 * <ul>
 *   <li><b>The split must not leak.</b> No observation may appear in both halves, and no
 *       observation's <em>input</em> may appear in both halves either: the same question
 *       asked twice is one observation for the purpose of a split, and a near-duplicate
 *       on both sides turns the measurement into a lookup.</li>
 *   <li><b>The label must not be a function of the feature.</b> That is not the split's
 *       business — it is measured and published by the experiment, because it is a
 *       property of the LABEL, not of the split.</li>
 * </ul>
 *
 * <p><b>The partitions.</b> {@link #byFamily} holds out whole task families (the
 * cross-task split); {@link #byInput} holds out whole inputs; {@link #withLeak} is the
 * CONTAMINATION CONTROL — the same observation on both sides — and exists so the
 * experiment can show that the harness detects a leak instead of quietly scoring one.</p>
 */
public final class LeakageControlledPartition {

    /** The train half. */
    private final List<Integer> train;

    /** The test half. */
    private final List<Integer> test;

    /** The name of the partition, for the report. */
    private final String name;

    private LeakageControlledPartition(String name, List<Integer> train, List<Integer> test) {
        this.name = name;
        this.train = List.copyOf(train);
        this.test = List.copyOf(test);
    }

    /** The name of the partition. */
    public String name() {
        return name;
    }

    /** The indices of the train half. */
    public List<Integer> train() {
        return train;
    }

    /** The indices of the test half. */
    public List<Integer> test() {
        return test;
    }

    /** The size of the train half. */
    public int trainSize() {
        return train.size();
    }

    /** The size of the test half. */
    public int testSize() {
        return test.size();
    }

    /**
     * Splits by TASK FAMILY: no family appears on both sides.
     *
     * @param observations the observations
     * @param testShare   the share of the observations that goes to the test half
     * @return the partition
     */
    public static LeakageControlledPartition byFamily(List<OutcomeSignal> observations,
                                                       double testShare) {
        Map<String, List<Integer>> byFamily = new LinkedHashMap<>();
        for (int i = 0; i < observations.size(); i++) {
            byFamily.computeIfAbsent(observations.get(i).family(), key -> new ArrayList<>())
                    .add(i);
        }
        List<String> families = new ArrayList<>(byFamily.keySet());
        // Deterministic order, so the split is reproducible without a seed.
        families.sort(String::compareTo);
        int target = (int) Math.round(observations.size() * testShare);
        List<Integer> train = new ArrayList<>();
        List<Integer> test = new ArrayList<>();
        int placed = 0;
        for (String family : families) {
            List<Integer> indices = byFamily.get(family);
            if (placed + indices.size() <= target) {
                test.addAll(indices);
                placed += indices.size();
            } else {
                train.addAll(indices);
            }
        }
        if (test.isEmpty() && !train.isEmpty()) {
            // A single family cannot be split: the whole set goes to one side and the
            // other side is empty. The experiment reports that instead of hiding it.
            test.clear();
        }
        return new LeakageControlledPartition("byFamily", train, test);
    }

    /**
     * Splits by INPUT: no input appears on both sides.
     *
     * @param observations the observations
     * @param testShare   the share of the observations that goes to the test half
     * @return the partition
     */
    public static LeakageControlledPartition byInput(List<OutcomeSignal> observations,
                                                     double testShare) {
        Map<String, List<Integer>> byInput = new LinkedHashMap<>();
        for (int i = 0; i < observations.size(); i++) {
            byInput.computeIfAbsent(observations.get(i).input(), key -> new ArrayList<>())
                    .add(i);
        }
        List<String> inputs = new ArrayList<>(byInput.keySet());
        inputs.sort(String::compareTo);
        int target = (int) Math.round(observations.size() * testShare);
        List<Integer> train = new ArrayList<>();
        List<Integer> test = new ArrayList<>();
        int placed = 0;
        for (String input : inputs) {
            List<Integer> indices = byInput.get(input);
            if (placed + indices.size() <= target) {
                test.addAll(indices);
                placed += indices.size();
            } else {
                train.addAll(indices);
            }
        }
        if (test.isEmpty() && !train.isEmpty()) test.clear();
        return new LeakageControlledPartition("byInput", train, test);
    }

    /**
     * Splits by CLASS and by INPUT: both classes are present in both halves, and no
     * input appears on both sides.
     *
     * <p><b>Why this partition exists.</b> An input-ordered split can hand one half a
     * degenerate class balance — and a degenerate half makes every metric on it
     * meaningless (the balanced accuracy of a half with no positives is 0.5 whatever the
     * learner does). The class balance of every half is therefore REPORTED with every
     * row, and this partition exists so a measurement can be made on a half that has
     * both classes at all.</p>
     *
     * @param observations the observations
     * @param testShare   the share of each class that goes to the test half
     * @return the partition
     */
    public static LeakageControlledPartition stratifiedByLabel(List<OutcomeSignal> observations,
                                                                  double testShare) {
        Map<String, List<Integer>> byInput = new LinkedHashMap<>();
        for (int i = 0; i < observations.size(); i++) {
            byInput.computeIfAbsent(observations.get(i).input(), key -> new ArrayList<>())
                    .add(i);
        }
        // the input groups of each class, in a deterministic order
        List<String> positiveInputs = new ArrayList<>();
        List<String> negativeInputs = new ArrayList<>();
        List<String> inputs = new ArrayList<>(byInput.keySet());
        inputs.sort(String::compareTo);
        for (String input : inputs) {
            if (observations.get(byInput.get(input).get(0)).positive()) {
                positiveInputs.add(input);
            } else {
                negativeInputs.add(input);
            }
        }
        int positiveTest = (int) Math.round(positiveInputs.size() * testShare);
        int negativeTest = (int) Math.round(negativeInputs.size() * testShare);
        List<Integer> train = new ArrayList<>();
        List<Integer> test = new ArrayList<>();
        for (int i = 0; i < positiveInputs.size(); i++) {
            List<Integer> indices = byInput.get(positiveInputs.get(i));
            if (i < positiveTest) test.addAll(indices); else train.addAll(indices);
        }
        for (int i = 0; i < negativeInputs.size(); i++) {
            List<Integer> indices = byInput.get(negativeInputs.get(i));
            if (i < negativeTest) test.addAll(indices); else train.addAll(indices);
        }
        return new LeakageControlledPartition("stratifiedByLabel", train, test);
    }

    /**
     * The CROSS-TASK protocol: one partition per task family, each holding that family
     * out.
     *
     * <p><b>Why leave-one-out and not a single split.</b> A cross-task generalisation
     * claim is a claim about a TASK, not about one lucky half. Leaving each family out
     * in turn uses every observation as a test observation, and the spread across the
     * folds is the honest uncertainty of the claim.</p>
     *
     * @param observations the observations
     * @return one partition per family
     */
    public static List<LeakageControlledPartition> leaveOneFamilyOut(
            List<OutcomeSignal> observations) {
        Set<String> families = new LinkedHashSet<>();
        for (OutcomeSignal observation : observations) families.add(observation.family());
        List<String> ordered = new ArrayList<>(families);
        ordered.sort(String::compareTo);
        List<LeakageControlledPartition> partitions = new ArrayList<>();
        for (String family : ordered) {
            List<Integer> train = new ArrayList<>();
            List<Integer> test = new ArrayList<>();
            for (int i = 0; i < observations.size(); i++) {
                if (observations.get(i).family().equals(family)) test.add(i);
                else train.add(i);
            }
            partitions.add(new LeakageControlledPartition("leaveOneFamilyOut:" + family,
                    train, test));
        }
        return partitions;
    }

    /**
     * The CONTAMINATION CONTROL: every observation is on both sides.
     *
     * <p>This partition is deliberately wrong. It exists so the experiment can show that
     * a leaky split produces a much better score — which is the evidence that the
     * leakage-free score is a real number and not an artefact of the harness.</p>
     *
     * @param observations the observations
     * @return the partition
     */
    public static LeakageControlledPartition withLeak(List<OutcomeSignal> observations) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < observations.size(); i++) all.add(i);
        return new LeakageControlledPartition("withLeak(all-on-both-sides)", all, all);
    }

    /**
     * Whether the partition leaks: an observation, or an input, on both sides.
     *
     * @param observations the observations the partition was built from
     * @return the offending inputs, empty when the partition is clean
     */
    public Set<String> leakedInputs(List<OutcomeSignal> observations) {
        Set<String> trainInputs = new LinkedHashSet<>();
        Set<String> testInputs = new LinkedHashSet<>();
        for (Integer index : train) trainInputs.add(observations.get(index).input());
        for (Integer index : test) testInputs.add(observations.get(index).input());
        Set<String> shared = new LinkedHashSet<>(trainInputs);
        shared.retainAll(testInputs);
        return shared;
    }
}
