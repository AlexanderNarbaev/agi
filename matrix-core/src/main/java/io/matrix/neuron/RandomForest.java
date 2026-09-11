package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DESIGN-49 — Random Forest (Breiman 2001).
 * Ensemble of decision trees on bootstrap samples with random
 * feature subsets. Pure function (CONSTITUTION I) with seeded RNG.
 */
public final class RandomForest {

    public record Sample(double[] features, int label) {}

    public record ForestPrediction(int predicted, double confidence) {}

    private RandomForest() {}

    /** Single decision tree node. */
    public static final class TreeNode {
        public final int featureIndex;
        public final double threshold;
        public final TreeNode left;
        public final TreeNode right;
        public final int leafLabel;
        public final boolean isLeaf;

        public TreeNode(int featureIndex, double threshold,
                        TreeNode left, TreeNode right) {
            this.featureIndex = featureIndex;
            this.threshold = threshold;
            this.left = left;
            this.right = right;
            this.leafLabel = -1;
            this.isLeaf = false;
        }

        public TreeNode(int leafLabel) {
            this.featureIndex = -1;
            this.threshold = 0;
            this.left = null;
            this.right = null;
            this.leafLabel = leafLabel;
            this.isLeaf = true;
        }
    }

    /**
     * Train a forest of nTrees trees on the same dataset.
     * Each tree trained on bootstrap sample with random feature subsets.
     */
    public static List<TreeNode> train(List<Sample> data, int nTrees,
                                        int maxDepth, long seed) {
        if (data == null || data.isEmpty()) return new ArrayList<>();
        Random rng = new Random(seed);
        int nFeatures = data.get(0).features().length;
        int featuresPerSplit = Math.max(1, (int) Math.sqrt(nFeatures));
        List<TreeNode> forest = new ArrayList<>();
        for (int t = 0; t < nTrees; t++) {
            List<Sample> bootstrap = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                bootstrap.add(data.get(rng.nextInt(data.size())));
            }
            forest.add(buildTree(bootstrap, maxDepth, featuresPerSplit,
                    rng, nFeatures));
        }
        return forest;
    }

    private static TreeNode buildTree(List<Sample> data, int depth,
                                       int featuresPerSplit, Random rng,
                                       int nFeatures) {
        if (depth == 0 || data.isEmpty() || isPure(data)) {
            return new TreeNode(majorityLabel(data));
        }
        // Random feature subset
        int[] featureSubset = randomSubset(nFeatures, featuresPerSplit, rng);
        // Find best split
        double bestGain = -1;
        int bestFeature = -1;
        double bestThreshold = 0;
        for (int f : featureSubset) {
            // Try a few thresholds
            for (int t = 0; t < 5; t++) {
                Sample s = data.get(rng.nextInt(data.size()));
                double threshold = s.features()[f];
                double gain = giniGain(data, f, threshold);
                if (gain > bestGain) {
                    bestGain = gain;
                    bestFeature = f;
                    bestThreshold = threshold;
                }
            }
        }
        if (bestFeature < 0) return new TreeNode(majorityLabel(data));
        // Split
        List<Sample> left = new ArrayList<>();
        List<Sample> right = new ArrayList<>();
        for (Sample s : data) {
            if (s.features()[bestFeature] <= bestThreshold) left.add(s);
            else right.add(s);
        }
        return new TreeNode(bestFeature, bestThreshold,
                buildTree(left, depth - 1, featuresPerSplit, rng, nFeatures),
                buildTree(right, depth - 1, featuresPerSplit, rng, nFeatures));
    }

    private static int[] randomSubset(int total, int k, Random rng) {
        java.util.Set<Integer> chosen = new java.util.HashSet<>();
        while (chosen.size() < Math.min(k, total)) {
            chosen.add(rng.nextInt(total));
        }
        int[] result = new int[chosen.size()];
        int i = 0;
        for (int c : chosen) result[i++] = c;
        return result;
    }

    private static double giniGain(List<Sample> data, int feature, double threshold) {
        // Simplified Gini impurity-based gain
        int nLeft = 0, nRight = 0;
        for (Sample s : data) {
            if (s.features()[feature] <= threshold) nLeft++;
            else nRight++;
        }
        if (nLeft == 0 || nRight == 0) return 0;
        // Calculate class distributions
        java.util.Map<Integer, Integer> leftCount = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> rightCount = new java.util.HashMap<>();
        for (Sample s : data) {
            if (s.features()[feature] <= threshold) {
                leftCount.merge(s.label(), 1, Integer::sum);
            } else {
                rightCount.merge(s.label(), 1, Integer::sum);
            }
        }
        double giniL = gini(leftCount, nLeft);
        double giniR = gini(rightCount, nRight);
        double giniAll = gini(combined(leftCount, rightCount), data.size());
        return giniAll - ((double) nLeft / data.size()) * giniL
                - ((double) nRight / data.size()) * giniR;
    }

    private static double gini(java.util.Map<Integer, Integer> counts, int total) {
        if (total == 0) return 0;
        double sum = 0;
        for (int c : counts.values()) {
            double p = (double) c / total;
            sum += p * p;
        }
        return 1 - sum;
    }

    private static java.util.Map<Integer, Integer> combined(
            java.util.Map<Integer, Integer> a,
            java.util.Map<Integer, Integer> b) {
        java.util.Map<Integer, Integer> out = new java.util.HashMap<>(a);
        for (var e : b.entrySet()) out.merge(e.getKey(), e.getValue(), Integer::sum);
        return out;
    }

    private static boolean isPure(List<Sample> data) {
        if (data.isEmpty()) return true;
        int first = data.get(0).label();
        for (Sample s : data) if (s.label() != first) return false;
        return true;
    }

    private static int majorityLabel(List<Sample> data) {
        if (data.isEmpty()) return 0;
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (Sample s : data) counts.merge(s.label(), 1, Integer::sum);
        return counts.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse(0);
    }

    /** Predict using majority vote across all trees. */
    public static ForestPrediction predict(List<TreeNode> forest, double[] features) {
        if (forest == null || forest.isEmpty()) {
            return new ForestPrediction(0, 0);
        }
        java.util.Map<Integer, Integer> votes = new java.util.HashMap<>();
        for (TreeNode tree : forest) {
            int label = predictOne(tree, features);
            votes.merge(label, 1, Integer::sum);
        }
        int total = forest.size();
        int best = votes.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse(0);
        return new ForestPrediction(best, (double) votes.get(best) / total);
    }

    private static int predictOne(TreeNode node, double[] features) {
        if (node.isLeaf) return node.leafLabel;
        return features[node.featureIndex] <= node.threshold
                ? predictOne(node.left, features)
                : predictOne(node.right, features);
    }
}
