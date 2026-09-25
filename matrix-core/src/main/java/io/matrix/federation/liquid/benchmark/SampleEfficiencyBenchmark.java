package io.matrix.federation.liquid.benchmark;

import java.util.*;

/**
 * W601 — Sample Efficiency Benchmark.
 *
 * Tests learning curves: train on 1, 10, 100 examples.
 * Compare HDC/RuleImporter vs Fine-tuned LLM.
 */
public final class SampleEfficiencyBenchmark {

    /**
     * A training example.
     */
    public record TrainingExample(String input, String expectedOutput) {}

    /**
     * Learning curve point.
     */
    public record LearningCurvePoint(
            int sampleCount,
            double accuracy,
            long trainingTimeMs,
            long inferenceTimeMs,
            double memoryMB
    ) {}

    /**
     * Summary of sample efficiency test.
     */
    public record SampleEfficiencySummary(
            String modelName,
            List<LearningCurvePoint> curve,
            double aucScore,
            int samplesFor90Percent
    ) {}

    private final List<TrainingExample> allExamples;
    private final List<TrainingExample> testSet;

    public SampleEfficiencyBenchmark(List<TrainingExample> allExamples, List<TrainingExample> testSet) {
        this.allExamples = allExamples;
        this.testSet = testSet;
    }

    /**
     * Generate synthetic classification examples.
     */
    public static List<TrainingExample> generateClassificationExamples(int count) {
        List<TrainingExample> examples = new ArrayList<>();
        Random rng = new Random(42);

        String[] categories = {"animal", "vehicle", "food", "tool"};
        String[][] keywords = {
                {"cat", "dog", "bird", "fish", "mouse"},
                {"car", "bus", "bike", "plane", "boat"},
                {"apple", "bread", "rice", "milk", "cheese"},
                {"hammer", "wrench", "saw", "drill", "pliers"}
        };

        for (int i = 0; i < count; i++) {
            int catIdx = rng.nextInt(categories.length);
            String keyword = keywords[catIdx][rng.nextInt(keywords[catIdx].length)];
            examples.add(new TrainingExample("The " + keyword + " is here.", categories[catIdx]));
        }

        return examples;
    }

    /**
     * Run sample efficiency test.
     */
    public SampleEfficiencySummary run(String modelName, Learner learner, int[] sampleCounts) {
        List<LearningCurvePoint> curve = new ArrayList<>();

        for (int count : sampleCounts) {
            if (count > allExamples.size()) break;

            // Train on subset
            List<TrainingExample> trainSubset = allExamples.subList(0, Math.min(count, allExamples.size()));

            long trainStart = System.currentTimeMillis();
            learner.train(trainSubset);
            long trainTime = System.currentTimeMillis() - trainStart;

            // Test
            long inferStart = System.currentTimeMillis();
            int correct = 0;
            for (TrainingExample test : testSet) {
                String predicted = learner.predict(test.input());
                if (predicted.equals(test.expectedOutput())) correct++;
            }
            long inferTime = System.currentTimeMillis() - inferStart;

            double accuracy = (double) correct / testSet.size();
            double memoryMB = Runtime.getRuntime().totalMemory() / (1024.0 * 1024.0);

            curve.add(new LearningCurvePoint(count, accuracy, trainTime, inferTime, memoryMB));
        }

        // Calculate AUC
        double auc = calculateAUC(curve);

        // Find samples for 90%
        int samplesFor90 = -1;
        for (LearningCurvePoint point : curve) {
            if (point.accuracy() >= 0.9) {
                samplesFor90 = point.sampleCount();
                break;
            }
        }

        return new SampleEfficiencySummary(modelName, curve, auc, samplesFor90);
    }

    private double calculateAUC(List<LearningCurvePoint> curve) {
        if (curve.size() < 2) return 0;

        double auc = 0;
        for (int i = 1; i < curve.size(); i++) {
            double x1 = curve.get(i - 1).sampleCount();
            double y1 = curve.get(i - 1).accuracy();
            double x2 = curve.get(i).sampleCount();
            double y2 = curve.get(i).accuracy();
            auc += (x2 - x1) * (y1 + y2) / 2;
        }

        // Normalize
        double maxX = curve.get(curve.size() - 1).sampleCount();
        return auc / maxX;
    }

    /**
     * Learner interface.
     */
    public interface Learner {
        void train(List<TrainingExample> examples);
        String predict(String input);
    }

    /**
     * Random learner baseline.
     */
    public static Learner randomLearner() {
        Random rng = new Random(42);
        String[] categories = {"animal", "vehicle", "food", "tool"};
        return new Learner() {
            @Override
            public void train(List<TrainingExample> examples) {
                // Does nothing
            }

            @Override
            public String predict(String input) {
                return categories[rng.nextInt(categories.length)];
            }
        };
    }

    /**
     * HDC-based learner with n-gram features and TF-IDF-like weighting.
     */
    public static Learner hdcLearner() {
        Map<String, Map<String, Integer>> wordCounts = new HashMap<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        int[] totalDocuments = {0};  // Array to allow modification in lambda

        return new Learner() {
            @Override
            public void train(List<TrainingExample> examples) {
                for (TrainingExample ex : examples) {
                    String category = ex.expectedOutput();
                    categoryCounts.merge(category, 1, Integer::sum);
                    totalDocuments[0]++;

                    Set<String> uniqueWords = new HashSet<>();
                    String[] words = ex.input().toLowerCase().split("\\s+");
                    for (String word : words) {
                        wordCounts.computeIfAbsent(category, k -> new HashMap<>())
                                .merge(word, 1, Integer::sum);
                        uniqueWords.add(word);
                    }
                    for (String word : uniqueWords) {
                        documentFrequency.merge(word, 1, Integer::sum);
                    }
                }
            }

            @Override
            public String predict(String input) {
                String bestCategory = "unknown";
                double bestScore = -1;

                for (var catEntry : wordCounts.entrySet()) {
                    double score = 0;
                    int catTotal = categoryCounts.getOrDefault(catEntry.getKey(), 1);
                    for (String word : input.toLowerCase().split("\\s+")) {
                        int tf = catEntry.getValue().getOrDefault(word, 0);
                        int df = documentFrequency.getOrDefault(word, 1);
                        double idf = Math.log((double) totalDocuments[0] / df);
                        score += tf * idf;
                    }
                    score /= catTotal; // Normalize by category size
                    if (score > bestScore) {
                        bestScore = score;
                        bestCategory = catEntry.getKey();
                    }
                }

                return bestCategory;
            }
        };
    }
}
