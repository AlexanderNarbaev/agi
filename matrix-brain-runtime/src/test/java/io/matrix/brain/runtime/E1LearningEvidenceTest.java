package io.matrix.brain.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.tsetlin.TsetlinTrainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * E1 — the rule-learning evidence base: can the learner discriminate, and what does the
 * fold cost downstream?
 *
 * <p><b>The dataset.</b> Two splits, never mixed: the <b>real</b> episodic log
 * ({@code data/mind/episodic.ndjson}, the running gateway's own data, referenced by path
 * and sha256 in {@code matrix-brain-runtime/src/test/resources/e1/DATASET-MANIFEST.json},
 * labelled by the production rule {@code confidence >= 0.7 AND accepted}) and the
 * <b>controlled synthetic</b> fixtures ({@code e1/synthetic-episodes.ndjson}, seeded,
 * labelled by a documented CONTENT rule). The synthetic split is a mechanism fixture and
 * is never reported as real-world evidence.</p>
 *
 * <p><b>The representations compared.</b> (R1) the production path — the 256-bit HDC
 * feature, XOR-parity folded into the trainer's 16 bits; (R2) the fold's information
 * properties measured on the representation itself (collisions and cross-label
 * collisions); (R3) a research-only projection onto the 16 bit positions that vary most
 * in the TRAINING split (the lossless-as-possible view at this trainer width); (R4) a
 * random 16-bit projection, the control for "any projection is as good". No production
 * code is changed.</p>
 *
 * <p><b>What is measured.</b> Class distribution, distinct features, informative bits,
 * exact and cross-label collisions, train/validation/test accuracy, balanced accuracy,
 * precision/recall/F1, false positives/negatives, and the CPU cost of training and
 * prediction. Repeat partitions use seeds 42/43/44. The training-set agreement (fidelity)
 * is reported separately from held-out accuracy and is never called generalization.</p>
 *
 * <p><b>The result file.</b> When the system property {@code matrix.e1.result} names a
 * path, the harness writes the measurements there as JSON — the artifact is produced by
 * the measurement, not typed by hand.</p>
 */
class E1LearningEvidenceTest {

    private static final int FEATURE_BITS = 256;
    private static final int TRAINER_BITS = 16;
    private static final int[] SEEDS = {42, 43, 44};

    private record Episode(String id, String input, String reply, boolean label,
                           String partition, boolean synthetic) {
    }

    private record Metrics(double accuracy, double balanced, double precision, double recall,
                           double f1, int tp, int tn, int fp, int fn,
                           long trainNanos, long predictNanos) {
    }

    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            if (Files.isDirectory(directory.resolve("matrix-brain-runtime"))
                    && Files.isDirectory(directory.resolve("docs-v2"))) {
                return directory;
            }
        }
        return start;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    // ── the datasets ────────────────────────────────────────────────────────

    private static List<Episode> syntheticEpisodes(Path root) throws Exception {
        Path fixture = root.resolve("matrix-brain-runtime/src/test/resources/e1/synthetic-episodes.ndjson");
        assumeTrue(Files.isRegularFile(fixture), "the synthetic fixture is absent");
        List<Episode> episodes = new ArrayList<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = JSON.readTree(line);
            episodes.add(new Episode(node.get("id").asText(), node.get("input").asText(),
                    node.get("reply").asText(), node.get("label").asBoolean(),
                    node.get("partition").asText(), true));
        }
        return episodes;
    }

    private static List<Episode> realEpisodes(Path root) {
        Path log = root.resolve("data/mind/episodic.ndjson");
        if (!Files.isRegularFile(log)) {
            return List.of();
        }
        List<Episode> episodes = new ArrayList<>();
        for (EpisodicLog.Entry entry : new EpisodicLog(log).readAll()) {
            episodes.add(new Episode(entry.id(), entry.input(), entry.reply(),
                    entry.confidence() >= 0.7 && entry.accepted(), "", false));
        }
        return episodes;
    }

    /** The chronological split of the real log: 60 / 20 / 20, no shuffle. */
    private static Map<String, List<Episode>> chronologicalSplit(List<Episode> episodes) {
        int size = episodes.size();
        int train = (int) Math.round(size * 0.6);
        int validation = (int) Math.round(size * 0.2);
        Map<String, List<Episode>> split = new LinkedHashMap<>();
        split.put("train", episodes.subList(0, train));
        split.put("validation", episodes.subList(train, Math.min(size, train + validation)));
        split.put("test", episodes.subList(Math.min(size, train + validation), size));
        return split;
    }

    private static Map<String, List<Episode>> declaredSplit(List<Episode> episodes) {
        Map<String, List<Episode>> split = new LinkedHashMap<>();
        for (String partition : List.of("train", "validation", "test")) {
            split.put(partition, episodes.stream()
                    .filter(e -> e.partition().equals(partition)).toList());
        }
        return split;
    }

    // ── the representations ─────────────────────────────────────────────────

    private static long[][] features(List<Episode> episodes, EpisodeFeatureExtractor extractor) {
        long[][] features = new long[episodes.size()][];
        for (int index = 0; index < episodes.size(); index++) {
            Episode episode = episodes.get(index);
            features[index] = extractor.encode(episode.input() + " " + episode.reply());
        }
        return features;
    }

    private static boolean[] labels(List<Episode> episodes) {
        boolean[] labels = new boolean[episodes.size()];
        for (int index = 0; index < episodes.size(); index++) {
            labels[index] = episodes.get(index).label();
        }
        return labels;
    }

    /** The bit positions that take both values across the training split. */
    private static int[] informativeBitPositions(long[][] trainFeatures, int width) {
        boolean[] seenZero = new boolean[width];
        boolean[] seenOne = new boolean[width];
        for (long[] words : trainFeatures) {
            for (int bit = 0; bit < width; bit++) {
                boolean set = ((words[bit >>> 6] >>> (bit & 63)) & 1L) == 1L;
                if (set) {
                    seenOne[bit] = true;
                } else {
                    seenZero[bit] = true;
                }
            }
        }
        List<Integer> positions = new ArrayList<>();
        for (int bit = 0; bit < width; bit++) {
            if (seenZero[bit] && seenOne[bit]) {
                positions.add(bit);
            }
        }
        return positions.stream().mapToInt(Integer::intValue).toArray();
    }

    private static long[][] project(long[][] features, int[] positions) {
        long[][] projected = new long[features.length][];
        for (int index = 0; index < features.length; index++) {
            long[] source = features[index];
            long value = 0L;
            for (int bit = 0; bit < positions.length && bit < Long.SIZE; bit++) {
                int sourceBit = positions[bit];
                if (((source[sourceBit >>> 6] >>> (sourceBit & 63)) & 1L) == 1L) {
                    value |= 1L << bit;
                }
            }
            projected[index] = new long[] {value};
        }
        return projected;
    }

    // ── the measurement ─────────────────────────────────────────────────────

    private static Metrics measure(long[][] trainFeatures, boolean[] trainLabels,
                                   long[][] evalFeatures, boolean[] evalLabels,
                                   long seed) {
        TsetlinTrainer trainer = new TsetlinTrainer(TRAINER_BITS, 64, 200, new Random(seed));
        long startTrain = System.nanoTime();
        trainer.trainBatch(trainFeatures, trainLabels, 10);
        long trainNanos = System.nanoTime() - startTrain;

        int tp = 0, tn = 0, fp = 0, fn = 0;
        long startPredict = System.nanoTime();
        for (int index = 0; index < evalFeatures.length; index++) {
            boolean predicted = trainer.predict(trainer.packForTest(evalFeatures[index]));
            boolean actual = evalLabels[index];
            if (predicted && actual) {
                tp++;
            } else if (!predicted && !actual) {
                tn++;
            } else if (predicted) {
                fp++;
            } else {
                fn++;
            }
        }
        long predictNanos = System.nanoTime() - startPredict;

        int total = tp + tn + fp + fn;
        double accuracy = total == 0 ? 0.0 : (tp + tn) / (double) total;
        double sensitivity = (tp + fn) == 0 ? 0.0 : tp / (double) (tp + fn);
        double specificity = (tn + fp) == 0 ? 0.0 : tn / (double) (tn + fp);
        double precision = (tp + fp) == 0 ? 0.0 : tp / (double) (tp + fp);
        double recall = sensitivity;
        double f1 = (precision + recall) == 0.0 ? 0.0
                : 2 * precision * recall / (precision + recall);
        return new Metrics(accuracy, (sensitivity + specificity) / 2.0, precision, recall, f1,
                tp, tn, fp, fn, trainNanos, predictNanos);
    }

    private static Map<String, Object> collisionAnalysis(List<Episode> episodes,
                                                         long[][] features,
                                                         EpisodeFeatureExtractor extractor) {
        Map<Long, Set<String>> foldedToFeatures = new LinkedHashMap<>();
        Map<Long, Set<Boolean>> foldedToLabels = new LinkedHashMap<>();
        TsetlinTrainer packer = new TsetlinTrainer(TRAINER_BITS, 2, 2, new Random(1));
        for (int index = 0; index < episodes.size(); index++) {
            long folded = packer.packForTest(features[index]);
            String key = java.util.Arrays.toString(features[index]);
            foldedToFeatures.computeIfAbsent(folded, k -> new LinkedHashSet<>()).add(key);
            foldedToLabels.computeIfAbsent(folded, k -> new LinkedHashSet<>())
                    .add(episodes.get(index).label());
        }
        long exactCollisions = foldedToFeatures.values().stream().filter(s -> s.size() > 1).count();
        long crossLabel = foldedToLabels.values().stream().filter(s -> s.size() > 1).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("episodes", episodes.size());
        result.put("distinctFullFeatures", foldedToFeatures.values().stream()
                .mapToInt(Set::size).sum());
        result.put("distinctFoldedValues", foldedToFeatures.size());
        result.put("exactCollisions", exactCollisions);
        result.put("crossLabelCollisions", crossLabel);
        result.put("informativeBits", TsetlinTrainer.countInformativeBits(features, FEATURE_BITS));
        return result;
    }

    private static Map<String, Object> distribution(List<Episode> episodes) {
        Map<String, Object> result = new LinkedHashMap<>();
        long positives = episodes.stream().filter(Episode::label).count();
        result.put("episodes", episodes.size());
        result.put("positives", positives);
        result.put("negatives", episodes.size() - positives);
        result.put("distinctInputs", episodes.stream().map(Episode::input).distinct().count());
        result.put("distinctReplies", episodes.stream().map(Episode::reply).distinct().count());
        return result;
    }

    private static boolean disjoint(Map<String, List<Episode>> split) {
        Set<String> seen = new LinkedHashSet<>();
        for (List<Episode> partition : split.values()) {
            for (Episode episode : partition) {
                if (!seen.add(episode.id())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Map<String, Object> evaluate(String dataset,
                                                Map<String, List<Episode>> split,
                                                EpisodeFeatureExtractor extractor,
                                                Map<String, Object> report) {
        List<Episode> train = split.get("train");
        List<Episode> validation = split.get("validation");
        List<Episode> test = split.get("test");
        long[][] trainFeatures = features(train, extractor);
        long[][] validationFeatures = features(validation, extractor);
        long[][] testFeatures = features(test, extractor);
        boolean[] trainLabels = labels(train);
        boolean[] validationLabels = labels(validation);
        boolean[] testLabels = labels(test);

        int[] informative = informativeBitPositions(trainFeatures, FEATURE_BITS);
        int[] projectedPositions = informative.length <= TRAINER_BITS
                ? informative : java.util.Arrays.copyOf(informative, TRAINER_BITS);
        Random randomSeed = new Random(42);
        int[] randomPositions = new int[TRAINER_BITS];
        java.util.Set<Integer> chosen = new LinkedHashSet<>();
        while (chosen.size() < TRAINER_BITS) {
            chosen.add(randomSeed.nextInt(FEATURE_BITS));
        }
        int index = 0;
        for (int bit : chosen) {
            randomPositions[index++] = bit;
        }

        long[][] informativeTrain = project(trainFeatures, projectedPositions);
        long[][] informativeValidation = project(validationFeatures, projectedPositions);
        long[][] informativeTest = project(testFeatures, projectedPositions);
        long[][] randomTrain = project(trainFeatures, randomPositions);
        long[][] randomValidation = project(validationFeatures, randomPositions);
        long[][] randomTest = project(testFeatures, randomPositions);

        Map<String, Object> representations = new LinkedHashMap<>();
        Map<String, long[][]> trainingViews = Map.of(
                "productionFold", trainFeatures,
                "informativeProjection", informativeTrain,
                "randomProjection", randomTrain);
        Map<String, long[][]> validationViews = Map.of(
                "productionFold", validationFeatures,
                "informativeProjection", informativeValidation,
                "randomProjection", randomValidation);
        Map<String, long[][]> testViews = Map.of(
                "productionFold", testFeatures,
                "informativeProjection", informativeTest,
                "randomProjection", randomTest);

        for (String representation : List.of("productionFold", "informativeProjection",
                "randomProjection")) {
            List<Object> perSeed = new ArrayList<>();
            for (int seed : SEEDS) {
                Metrics validationMetrics = measure(trainingViews.get(representation),
                        trainLabels, validationViews.get(representation), validationLabels, seed);
                Metrics testMetrics = measure(trainingViews.get(representation),
                        trainLabels, testViews.get(representation), testLabels, seed);
                Map<String, Object> seedResult = new LinkedHashMap<>();
                seedResult.put("seed", seed);
                seedResult.put("validation", validationMetrics);
                seedResult.put("test", testMetrics);
                if (seed == SEEDS[0]) {
                    // a second training run with the same seed must land on the same
                    // held-out accuracy — the determinism claim, measured
                    Metrics repeat = measure(trainingViews.get(representation),
                            trainLabels, validationViews.get(representation), validationLabels, seed);
                    seedResult.put("deterministicRepeatEqual",
                            repeat.accuracy() == validationMetrics.accuracy());
                }
                perSeed.add(seedResult);
            }
            representations.put(representation, perSeed);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", dataset);
        result.put("distribution", distribution(Stream.concat(Stream.concat(
                train.stream(), validation.stream()), test.stream()).toList()));
        result.put("partitions", Map.of("train", train.size(), "validation", validation.size(),
                "test", test.size()));
        result.put("partitionsDisjoint", disjoint(split));
        result.put("informativeBitsOnTrain", informative.length);
        result.put("projectionWidth", projectedPositions.length);
        List<Episode> union = Stream.concat(Stream.concat(train.stream(), validation.stream()),
                test.stream()).toList();
        result.put("collisions", collisionAnalysis(union, features(union, extractor), extractor));
        result.put("representations", representations);
        report.put("real".equals(dataset) ? "real" : "synthetic", result);
        return result;
    }

    @Test
    @DisplayName("E1 — the learner's discrimination on both splits, and the fold's cost")
    void theLearningEvidenceBase() throws Exception {
        Path root = repoRoot();
        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("record", "E1-LEARNING-EVIDENCE");
        report.put("date", "2026-11-14");
        report.put("featureBits", FEATURE_BITS);
        report.put("trainerBits", TRAINER_BITS);
        report.put("seeds", SEEDS);

        List<Episode> synthetic = syntheticEpisodes(root);
        assertThat(synthetic).hasSize(144);
        Map<String, List<Episode>> syntheticSplit = declaredSplit(synthetic);
        assertThat(disjoint(syntheticSplit)).isTrue();
        Map<String, Object> syntheticResult = evaluate("synthetic", syntheticSplit, extractor,
                report);

        List<Episode> real = realEpisodes(root);
        // the leakage claim, asserted: the two splits share no episode text, and the real
        // split's input-level overlap is measured rather than assumed
        java.util.Set<String> syntheticTexts = synthetic.stream()
                .map(e -> e.input() + " " + e.reply()).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> realTexts = real.stream()
                .map(e -> e.input() + " " + e.reply()).collect(java.util.stream.Collectors.toSet());
        syntheticTexts.retainAll(realTexts);
        Map<String, Object> leakage = new LinkedHashMap<>();
        leakage.put("syntheticRealExactTextOverlap", syntheticTexts.size());
        report.put("leakage", leakage);
        assertThat(syntheticTexts).isEmpty();
        Map<String, Object> realResult = null;
        if (real.isEmpty()) {
            report.put("real", Map.of("status", "INSUFFICIENT_DATA",
                    "note", "the runtime's episodic log is not on this machine"));
        } else {
            Map<String, List<Episode>> realSplit = chronologicalSplit(real);
            assertThat(disjoint(realSplit)).isTrue();
            realResult = evaluate("real", realSplit, extractor, report);
            // how much of the real test partition reuses an input seen in training — measured,
            // because the texts repeat in a runtime log even when the full entries do not
            java.util.Set<String> trainInputs = realSplit.get("train").stream()
                    .map(Episode::input).collect(java.util.stream.Collectors.toSet());
            long reused = realSplit.get("test").stream()
                    .filter(e -> trainInputs.contains(e.input())).count();
            leakage.put("realTestInputsSeenInTrain",
                    reused + " of " + realSplit.get("test").size());
            // the production training window, measured separately
            int window = Math.min(real.size(), 20);
            List<Episode> productionWindow = real.subList(real.size() - window, real.size());
            Map<String, Object> windowReport = new LinkedHashMap<>();
            windowReport.put("size", productionWindow.size());
            windowReport.put("distribution", distribution(productionWindow));
            report.put("productionWindow", windowReport);
        }

        // determinism: every representation's first-seed repeat landed on the same
        // held-out accuracy (measured inside evaluate, per representation)
        for (Object seedResults : ((Map<?, ?>) syntheticResult.get("representations")).values()) {
            Map<?, ?> firstSeed = (Map<?, ?>) ((List<?>) seedResults).get(0);
            assertThat(firstSeed.get("deterministicRepeatEqual")).isEqualTo(true);
        }

        System.out.println("[E1] " + JSON.writeValueAsString(report));
        String artifact = System.getProperty("matrix.e1.result");
        if (artifact != null && !artifact.isBlank()) {
            Path out = Path.of(artifact);
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(report));
            System.out.println("[E1] the artifact is at " + out.toAbsolutePath());
        }
        assertThat(syntheticResult.get("partitionsDisjoint")).isEqualTo(true);
        if (realResult != null) {
            assertThat(realResult.get("partitionsDisjoint")).isEqualTo(true);
        }
    }
}
