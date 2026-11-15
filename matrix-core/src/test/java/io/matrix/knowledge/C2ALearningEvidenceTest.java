package io.matrix.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.tsetlin.TsetlinTrainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C2A — the LEARNING EVIDENCE measurement over REAL observations (Workstream C).
 *
 * <p><b>What is measured.</b> The learning subsystem is measured again on evidence that was
 * really executed: the episodic log of this machine, after the current build served a
 * designed workload through {@code io.matrix.brain.runtime.TrueMindCycle} and wrote every
 * admitted interaction into {@code data/mind/episodic.ndjson} through the production
 * {@code EpisodicLog.append} → {@code PromotionGate} path. The collector is
 * {@code matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/C2ARealEpisodeCollectionTest.java};
 * its observations, with their provenance, are in the "collection" section of
 * {@code docs-v2/engineering/C2A-LEARNING-EVIDENCE.json}.</p>
 *
 * <p><b>Provenance classes, kept apart.</b></p>
 * <ul>
 *   <li><b>HISTORICAL</b> — the 108 records that were already in the log (written by earlier
 *       builds, the ones the C1 diagnosis measured);</li>
 *   <li><b>NEWLY_EXECUTED_REAL_OBSERVATION</b> — the records this build wrote during the
 *       collection run;</li>
 *   <li><b>CONTROLLED_SYNTHETIC</b> — the {@code e1/synthetic-episodes.ndjson} fixture, a
 *       mechanism fixture and never real-world evidence;</li>
 *   <li><b>AUTOMATICALLY DERIVED</b> — every label, which is the production rule
 *       {@code confidence >= 0.7 AND accepted} applied to what the runtime produced. No
 *       label in this harness is curated by hand, and no synthetic case is ever reported as
 *       a real observation.</li>
 * </ul>
 *
 * <p><b>The representations compared</b> (all at the production trainer width of 16 bits,
 * which is NOT changed here):</p>
 * <ol>
 *   <li>{@code raw256} — the original 256-bit HDC feature. Statistics only (the trainer caps
 *       at 20 bits, so the learner cannot consume 256 bits directly);</li>
 *   <li>{@code productionFold} — the production path: the 256-bit feature is handed to the
 *       production trainer, which XOR-parity-folds it into 16 bits
 *       ({@code TsetlinTrainer.pack}, {@code :361-376});</li>
 *   <li>{@code informativeProjection} — a TRAINING-derived projection onto the 16 bit
 *       positions that take both values in the training split (the rule E1 used), so the
 *       comparison with the E1 artefact stays exact;</li>
 *   <li>{@code discriminativeProjection} — a TRAINING-derived projection onto the 16
 *       positions whose class-conditional balance separates the training labels the most.
 *       This is the strongest 16-bit training-derived projection available, so it bounds
 *       what any such projection could achieve;</li>
 *   <li>{@code randomProjection} — the CONTROL: 16 random bit positions, label-blind;</li>
 *   <li>{@code shuffledLabels} — the CONTROL for the learner: the production fold with the
 *       training labels shuffled, which shows what the same pipeline scores when the
 *       labels carry no information.</li>
 * </ol>
 *
 * <p><b>Leakage.</b> Three partitions are evaluated: the chronological 60/20/20 (E1's rule),
 * the chronological 60/20/20 over the NEW observations only, and a GROUPED chronological
 * split that keeps every input inside one partition. The grouped split is the
 * leakage-controlled one, and the harness ASSERTS that no input and no episode id appears
 * in two partitions of it. The input-level overlap of the chronological splits is measured,
 * not assumed.</p>
 *
 * <p><b>What is NOT changed.</b> The label rule, {@code K_MAX} (20), the production fold, the
 * trainer width and the window selector are exactly as the production code has them. No
 * production source file is touched by this harness.</p>
 */
class C2ALearningEvidenceTest {

    private static final int FEATURE_BITS = 256;
    private static final int TRAINER_BITS = 16;
    private static final int[] SEEDS = {42, 43, 44};
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One episode of the machine's episodic log. */
    private record Episode(String id, String input, String reply, double confidence,
                           boolean accepted, boolean label, long ts, String provenance,
                           String declaredPartition) {
    }

    /** One representation's measured performance on one partition. */
    private record Metrics(double accuracy, double balanced, double precision, double recall,
                           double f1, int tp, int tn, int fp, int fn,
                           long trainNanos, long predictNanos, int predictEpisodes) {
    }

    // ── the dataset ─────────────────────────────────────────────────────────

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

    /** Read the machine's real episodic log and label it by the production rule. */
    private static List<Episode> realEpisodes(Path root) throws Exception {
        Path log = root.resolve("data/mind/episodic.ndjson");
        List<Episode> episodes = new ArrayList<>();
        if (!Files.isRegularFile(log)) return episodes;
        int index = 0;
        for (String line : Files.readAllLines(log)) {
            if (line.isBlank()) continue;
            JsonNode node = JSON.readTree(line);
            if (node == null || !node.has("input")) continue;
            double confidence = node.get("confidence").asDouble(0.0);
            boolean accepted = node.get("accepted").asBoolean(false);
            episodes.add(new Episode(
                node.has("id") ? node.get("id").asText() : "ep-" + index,
                node.get("input").asText(),
                node.get("reply").asText(),
                confidence,
                accepted,
                // AUTOMATICALLY DERIVED by the production rule (RealSleepScheduler.java:249).
                confidence >= 0.7 && accepted,
                node.has("ts") ? node.get("ts").asLong(0L) : 0L,
                "REAL_LOG", ""));
            index++;
        }
        return episodes;
    }

    /** The controlled synthetic fixture — a mechanism fixture, never real evidence. */
    private static List<Episode> syntheticEpisodes(Path root) throws Exception {
        Path fixture = root.resolve(
            "matrix-brain-runtime/src/test/resources/e1/synthetic-episodes.ndjson");
        List<Episode> episodes = new ArrayList<>();
        if (!Files.isRegularFile(fixture)) return episodes;
        for (String line : Files.readAllLines(fixture)) {
            if (line.isBlank()) continue;
            JsonNode node = JSON.readTree(line);
            episodes.add(new Episode(
                node.get("id").asText(),
                node.get("input").asText(),
                node.get("reply").asText(),
                node.get("label").asBoolean() ? 1.0 : 0.0,
                true,
                node.get("label").asBoolean(),
                0L,
                "CONTROLLED_SYNTHETIC",
                node.has("partition") ? node.get("partition").asText() : ""));
        }
        return episodes;
    }

    // ── the encoder (mirror of the production path) ─────────────────────────

    /**
     * The 256-bit feature, mirroring {@code PersistentHdcStore.hashToVector}
     * ({@code :394-407}) and {@code EpisodeFeatureExtractor.pack} ({@code :55-66}).
     *
     * <p>This module cannot link the runtime module (the dependency runs the other way), so
     * the encoder is mirrored here. The mirror is not trusted on its own: the harness
     * cross-checks EVERY feature against the one the production collector computed
     * ({@code EpisodeFeatureExtractor} in the runtime module) and asserts that they are
     * identical. The cross-check is the evidence; the mirror is only the transport.</p>
     */
    private static long[] encode256(String text) {
        BitSet bits = new BitSet(FEATURE_BITS);
        for (String token : text.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (token.isBlank()) continue;
            int h = 0x811c9dc5;
            for (int i = 0; i < token.length(); i++) {
                h ^= token.charAt(i);
                h *= 0x01000193;
            }
            bits.set((h & 0x7fffffff) % FEATURE_BITS);
        }
        long[] words = new long[(FEATURE_BITS + 63) / 64];
        for (int bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1)) {
            words[bit >>> 6] |= 1L << bit;
        }
        return words;
    }

    /** Parse the "[0, 1, 2]" feature string the collector wrote. */
    private static long[] parseFeature(String text) {
        String body = text.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
        String[] parts = body.split(",");
        long[] words = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            words[i] = Long.parseLong(parts[i].trim());
        }
        return words;
    }

    private static long[][] features(List<Episode> episodes) {
        long[][] out = new long[episodes.size()][];
        for (int i = 0; i < episodes.size(); i++) {
            Episode e = episodes.get(i);
            out[i] = encode256(e.input() + " " + e.reply());
        }
        return out;
    }

    // ── the partitions ───────────────────────────────────────────────────────

    /** The chronological 60 / 20 / 20 split, no shuffle (E1's rule). */
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

    /**
     * The GROUPED chronological split: whole inputs stay whole.
     *
     * <p>A runtime log repeats inputs. When an input appears in both the training and the
     * test partition, the test episode is not an unseen case, whatever the representation.
     * This split removes that leak by construction: the inputs are taken in chronological
     * order and every episode of an input goes to the partition the input was assigned to.
     * The harness asserts the invariant rather than assuming it.</p>
     */
    private static Map<String, List<Episode>> groupedSplit(List<Episode> episodes) {
        Map<String, List<Episode>> split = new LinkedHashMap<>();
        split.put("train", new ArrayList<>());
        split.put("validation", new ArrayList<>());
        split.put("test", new ArrayList<>());
        Map<String, String> inputToPartition = new LinkedHashMap<>();
        int size = episodes.size();
        int trainLimit = (int) Math.round(size * 0.6);
        int validationLimit = trainLimit + (int) Math.round(size * 0.2);
        for (int position = 0; position < episodes.size(); position++) {
            Episode episode = episodes.get(position);
            String partition = inputToPartition.get(episode.input());
            if (partition == null) {
                // the partition of an input is fixed by where the input FIRST appears
                partition = position < trainLimit ? "train"
                    : position < validationLimit ? "validation" : "test";
                inputToPartition.put(episode.input(), partition);
            }
            split.get(partition).add(episode);
        }
        return split;
    }

    // ── the representations ──────────────────────────────────────────────────

    /** The bit positions that take both values across the training split. */
    private static int[] informativeBitPositions(long[][] trainFeatures, int width) {
        boolean[] sawZero = new boolean[width];
        boolean[] sawOne = new boolean[width];
        for (long[] words : trainFeatures) {
            for (int bit = 0; bit < width; bit++) {
                boolean set = bit(words, bit);
                if (set) sawOne[bit] = true; else sawZero[bit] = true;
            }
        }
        List<Integer> positions = new ArrayList<>();
        for (int bit = 0; bit < width; bit++) {
            if (sawZero[bit] && sawOne[bit]) positions.add(bit);
        }
        return positions.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * The bit positions that separate the TRAINING labels most.
     *
     * <p>For every bit position, the difference between the rate it is set among the
     * positive training episodes and the rate it is set among the negative ones. The 16
     * positions with the largest difference are the strongest 16-bit training-derived
     * projection the data supports. It is computed on the training partition ONLY.</p>
     */
    private static int[] discriminativeBitPositions(long[][] trainFeatures, boolean[] trainLabels) {
        int positives = 0;
        int negatives = 0;
        for (boolean label : trainLabels) {
            if (label) positives++; else negatives++;
        }
        if (positives == 0 || negatives == 0) return new int[0];
        double[] difference = new double[FEATURE_BITS];
        for (int bit = 0; bit < FEATURE_BITS; bit++) {
            int setPositive = 0;
            int setNegative = 0;
            for (int i = 0; i < trainFeatures.length; i++) {
                if (!bit(trainFeatures[i], bit)) continue;
                if (trainLabels[i]) setPositive++; else setNegative++;
            }
            difference[bit] = Math.abs(setPositive / (double) positives
                - setNegative / (double) negatives);
        }
        Integer[] order = new Integer[FEATURE_BITS];
        for (int i = 0; i < FEATURE_BITS; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(difference[b], difference[a]));
        int count = Math.min(TRAINER_BITS, FEATURE_BITS);
        int[] positions = new int[count];
        for (int i = 0; i < count; i++) positions[i] = order[i];
        return positions;
    }

    private static boolean bit(long[] words, int position) {
        int word = position >>> 6;
        return word < words.length && ((words[word] >>> (position & 63)) & 1L) != 0L;
    }

    /** Project a feature vector onto the given bit positions, one bit per position. */
    private static long[][] project(long[][] source, int[] positions) {
        long[][] projected = new long[source.length][];
        for (int i = 0; i < source.length; i++) {
            long value = 0L;
            for (int bit = 0; bit < positions.length && bit < Long.SIZE; bit++) {
                if (bit(source[i], positions[bit])) value |= 1L << bit;
            }
            projected[i] = new long[] {value};
        }
        return projected;
    }

    /**
     * The production fold as a VIEW: the XOR-parity 16-bit value the production trainer
     * consumes, computed with the production fold itself
     * ({@code TsetlinTrainer.packForTest}, {@code :361-376}).
     */
    private static long[][] foldView(long[][] raw) {
        TsetlinTrainer packer = new TsetlinTrainer(TRAINER_BITS, 2, 2, new Random(1));
        long[][] view = new long[raw.length][];
        for (int i = 0; i < raw.length; i++) view[i] = new long[] {packer.packForTest(raw[i])};
        return view;
    }

    private static boolean[] labels(List<Episode> episodes) {
        boolean[] out = new boolean[episodes.size()];
        for (int i = 0; i < episodes.size(); i++) out[i] = episodes.get(i).label();
        return out;
    }

    // ── the measurement ──────────────────────────────────────────────────────

    private static Metrics measure(long[][] trainFeatures, boolean[] trainLabels,
                                   long[][] evalFeatures, boolean[] evalLabels, long seed) {
        TsetlinTrainer trainer = new TsetlinTrainer(TRAINER_BITS, 64, 200, new Random(seed));
        long startTrain = System.nanoTime();
        trainer.trainBatch(trainFeatures, trainLabels, 10);
        long trainNanos = System.nanoTime() - startTrain;

        int tp = 0, tn = 0, fp = 0, fn = 0;
        long startPredict = System.nanoTime();
        for (int i = 0; i < evalFeatures.length; i++) {
            boolean predicted = trainer.predict(trainer.packForTest(evalFeatures[i]));
            boolean actual = evalLabels[i];
            if (predicted && actual) tp++;
            else if (!predicted && !actual) tn++;
            else if (predicted) fp++;
            else fn++;
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
            tp, tn, fp, fn, trainNanos, predictNanos, evalFeatures.length);
    }

    /** The representation-level statistics: distinct features and collisions. */
    private static Map<String, Object> collisionAnalysis(List<Episode> episodes, long[][] raw) {
        TsetlinTrainer packer = new TsetlinTrainer(TRAINER_BITS, 2, 2, new Random(1));
        Map<Long, Set<String>> foldedToFeatures = new LinkedHashMap<>();
        Map<Long, Set<Boolean>> foldedToLabels = new LinkedHashMap<>();
        Map<String, Set<Boolean>> fullToLabels = new LinkedHashMap<>();
        Set<Long> foldedValues = new LinkedHashSet<>();
        Set<String> fullValues = new LinkedHashSet<>();
        for (int i = 0; i < episodes.size(); i++) {
            long folded = packer.packForTest(raw[i]);
            String fullKey = Arrays.toString(raw[i]);
            foldedValues.add(folded);
            fullValues.add(fullKey);
            foldedToFeatures.computeIfAbsent(folded, k -> new LinkedHashSet<>()).add(fullKey);
            foldedToLabels.computeIfAbsent(folded, k -> new LinkedHashSet<>())
                .add(episodes.get(i).label());
            fullToLabels.computeIfAbsent(fullKey, k -> new LinkedHashSet<>())
                .add(episodes.get(i).label());
        }
        long fullCross = fullToLabels.values().stream().filter(s -> s.size() > 1).count();
        // at 256 bits the map is the identity, so two DIFFERENT features can never collide
        long fullExact = 0L;
        List<Map<String, Object>> crossLabelDetails = new ArrayList<>();
        for (Map.Entry<String, Set<Boolean>> entry : fullToLabels.entrySet()) {
            if (entry.getValue().size() <= 1) continue;
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < episodes.size(); i++) {
                if (Arrays.toString(raw[i]).equals(entry.getKey())) {
                    texts.add("[" + i + "] " + episodes.get(i).input() + " -> "
                        + episodes.get(i).reply() + " conf=" + episodes.get(i).confidence()
                        + " accepted=" + episodes.get(i).accepted()
                        + " label=" + episodes.get(i).label());
                }
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("feature256", entry.getKey());
            detail.put("episodes", texts);
            crossLabelDetails.add(detail);
        }
        List<Map<String, Object>> foldedCrossDetails = new ArrayList<>();
        for (Map.Entry<Long, Set<Boolean>> entry : foldedToLabels.entrySet()) {
            if (entry.getValue().size() <= 1) continue;
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < episodes.size(); i++) {
                if (packer.packForTest(raw[i]) == entry.getKey()) {
                    texts.add("[" + i + "] " + episodes.get(i).input() + " -> "
                        + episodes.get(i).reply() + " label=" + episodes.get(i).label());
                }
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("fold16", String.format("0x%04x", entry.getKey()));
            detail.put("episodes", texts);
            foldedCrossDetails.add(detail);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("episodes", episodes.size());
        result.put("distinctFullFeatures256", fullValues.size());
        result.put("crossLabelCollisions256Details", crossLabelDetails);
        result.put("crossLabelCollisions16Details", foldedCrossDetails);
        result.put("distinctFoldedValues16", foldedValues.size());
        result.put("exactCollisions256", fullExact);
        result.put("exactCollisions256Note", "0 by construction: at 256 bits the feature IS "
            + "the representation, so the mapping is injective");
        result.put("crossLabelCollisions256", fullCross);
        result.put("exactCollisions16", foldedToFeatures.values().stream()
            .filter(s -> s.size() > 1).count());
        result.put("crossLabelCollisions16", foldedToLabels.values().stream()
            .filter(s -> s.size() > 1).count());
        result.put("informativeBits256", TsetlinTrainer.countInformativeBits(raw, FEATURE_BITS));
        result.put("note", "a cross-label collision is one feature value that carries both "
            + "labels; at 256 bits it is created by the encoder, at 16 bits by the fold");
        return result;
    }

    /**
     * The exact-feature memorisation control: how often does the test episode's EXACT
     * 256-bit feature appear in the training partition with the same label?
     *
     * <p>This is not a learner and not a fold option. It is the information ceiling of the
     * representation: if a feature never carries both labels, a lookup on the raw feature
     * would separate the classes perfectly on any partition that reuses known features.</p>
     */
    private static Map<String, Object> memorisationCeiling(List<Episode> train,
                                                           List<Episode> test,
                                                           long[][] trainFeatures,
                                                           long[][] testFeatures) {
        Map<String, Boolean> known = new LinkedHashMap<>();
        for (int i = 0; i < train.size(); i++) {
            known.put(Arrays.toString(trainFeatures[i]), train.get(i).label());
        }
        int hits = 0;
        int seen = 0;
        for (int i = 0; i < test.size(); i++) {
            Boolean label = known.get(Arrays.toString(testFeatures[i]));
            if (label == null) continue;
            seen++;
            if (label == test.get(i).label()) hits++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testEpisodes", test.size());
        result.put("testFeaturesSeenInTrain", seen);
        result.put("sameLabel", hits);
        result.put("accuracyOverSeen", seen == 0 ? null : hits / (double) seen);
        result.put("note", "the information ceiling of the 256-bit representation: an exact "
            + "lookup, no learning, no fold");
        return result;
    }

    /** Distinct values and cross-label collisions of one representation over a dataset. */
    private static Map<String, Object> viewCollisions(List<Episode> episodes, long[][] view) {
        Map<String, Set<Boolean>> valueToLabels = new LinkedHashMap<>();
        for (int i = 0; i < episodes.size(); i++) {
            valueToLabels.computeIfAbsent(Arrays.toString(view[i]), k -> new LinkedHashSet<>())
                .add(episodes.get(i).label());
        }
        long cross = valueToLabels.values().stream().filter(s -> s.size() > 1).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distinctValues", valueToLabels.size());
        result.put("crossLabelCollisions", cross);
        result.put("crossLabelRate", valueToLabels.isEmpty() ? 0.0
            : cross / (double) valueToLabels.size());
        return result;
    }

    /**
     * The 1-NEAREST-NEIGHBOUR control on the RAW 256-bit feature.
     *
     * <p>This is the strongest statement the data can make about the representation: it
     * asks whether ANY function of the 256-bit feature can separate the classes, by
     * letting the nearest training episode in Hamming space vote. It is a control, not a
     * fold option and not a learner proposal. If it also sits at chance, then no
     * representation of these episodes can carry the label, and the fold's collisions are
     * not what is stopping the learner.</p>
     */
    private static Map<String, Object> nearestNeighbour256(long[][] trainFeatures,
                                                           boolean[] trainLabels,
                                                           long[][] testFeatures,
                                                           boolean[] testLabels) {
        int tp = 0, tn = 0, fp = 0, fn = 0;
        for (int t = 0; t < testFeatures.length; t++) {
            int best = Integer.MAX_VALUE;
            boolean bestLabel = false;
            for (int i = 0; i < trainFeatures.length; i++) {
                int distance = 0;
                for (int w = 0; w < trainFeatures[t].length; w++) {
                    distance += Long.bitCount(trainFeatures[t][w] ^ trainFeatures[i][w]);
                }
                if (distance < best) {
                    best = distance;
                    bestLabel = trainLabels[i];
                }
            }
            if (bestLabel == testLabels[t]) {
                if (bestLabel) tp++; else tn++;
            } else if (bestLabel) fp++; else fn++;
        }
        int total = tp + tn + fp + fn;
        double accuracy = total == 0 ? 0.0 : (tp + tn) / (double) total;
        double sensitivity = (tp + fn) == 0 ? 0.0 : tp / (double) (tp + fn);
        double specificity = (tn + fp) == 0 ? 0.0 : tn / (double) (tn + fp);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accuracy", accuracy);
        result.put("balanced", (sensitivity + specificity) / 2.0);
        result.put("tp", tp);
        result.put("tn", tn);
        result.put("fp", fp);
        result.put("fn", fn);
        result.put("note", "1-NN on the raw 256-bit feature: the ceiling of ANY function of "
            + "the 256-bit representation on this partition");
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
        result.put("singleClass", positives == 0 || positives == episodes.size());
        return result;
    }

    /** True when every episode id is in exactly one partition. */
    private static boolean disjoint(Map<String, List<Episode>> split) {
        Set<String> seen = new LinkedHashSet<>();
        for (List<Episode> partition : split.values()) {
            for (Episode episode : partition) {
                if (!seen.add(episode.id())) return false;
            }
        }
        return true;
    }

    /**
     * True when every input lives in exactly ONE partition.
     *
     * <p>An input MAY occur several times inside its own partition — that is what a
     * runtime log looks like. What must not happen is the same input appearing in two
     * DIFFERENT partitions, because then the test partition is not an unseen case.</p>
     */
    private static boolean inputsDisjoint(Map<String, List<Episode>> split) {
        return firstConflictingInput(split) == null;
    }

    /** The first input that appears in TWO DIFFERENT partitions, or null when there is none. */
    private static String firstConflictingInput(Map<String, List<Episode>> split) {
        Map<String, String> owner = new LinkedHashMap<>();
        for (Map.Entry<String, List<Episode>> entry : split.entrySet()) {
            for (Episode episode : entry.getValue()) {
                String previous = owner.putIfAbsent(episode.input(), entry.getKey());
                if (previous != null && !previous.equals(entry.getKey())) {
                    return episode.input() + " appears in " + previous + " and "
                        + entry.getKey();
                }
            }
        }
        return null;
    }

    // ── the run ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("C2A — the learning subsystem measured again on REAL observations")
    void theLearningEvidenceOnRealObservations() throws Exception {
        Path root = repoRoot();
        Path artifact = root.resolve("docs-v2/engineering/C2A-LEARNING-EVIDENCE.json");
        Path historical = root.resolve("docs-v2/engineering/E1-LEARNING-EVIDENCE.json");

        List<Episode> real = realEpisodes(root);
        assertThat(real).as("the machine's episodic log").isNotEmpty();

        // ---- the collection block, when the collector has run ----------------
        JsonNode collection = null;
        if (Files.isRegularFile(artifact)) {
            JsonNode existing = JSON.readTree(Files.readString(artifact));
            if (existing != null && existing.has("collection")) collection = existing.get("collection");
        }
        int historicalCount = -1;
        int newCount = -1;
        if (collection != null) {
            historicalCount = collection.get("before").get("records").asInt();
            newCount = collection.get("after").get("records").asInt();
            // the provenance of every episode: HISTORICAL (written by earlier builds) or
            // NEWLY_EXECUTED_REAL_OBSERVATION (written by this build during the collection)
            List<Episode> rebuilt = new ArrayList<>();
            for (int index = 0; index < real.size(); index++) {
                Episode episode = real.get(index);
                rebuilt.add(new Episode(episode.id(), episode.input(), episode.reply(),
                    episode.confidence(), episode.accepted(), episode.label(), episode.ts(),
                    index < historicalCount ? "HISTORICAL"
                        : "NEWLY_EXECUTED_REAL_OBSERVATION", ""));
            }
            real = rebuilt;
        }

        // ---- the CROSS-CHECK: the mirror against the production encoder -----
        Map<String, Object> crossCheck = new LinkedHashMap<>();
        int compared = 0;
        int mismatches = 0;
        if (collection != null) {
            for (JsonNode observation : collection.get("observations")) {
                if (!observation.path("promoted").asBoolean()) continue;
                JsonNode feature = observation.get("feature256");
                if (feature == null || !feature.isTextual()) continue;
                long[] production = parseFeature(feature.asText());
                long[] mirror = encode256(observation.get("input").asText()
                    + " " + observation.get("reply").asText());
                compared++;
                if (!Arrays.equals(mirror, production)) mismatches++;
            }
        }
        crossCheck.put("featuresCompared", compared);
        crossCheck.put("mismatches", mismatches);
        crossCheck.put("method", "the mirror of PersistentHdcStore.hashToVector + "
            + "EpisodeFeatureExtractor.pack, compared against the feature the production "
            + "collector computed with the production classes");
        assertThat(mismatches).as("the encoder mirror must agree with the production encoder")
            .isEqualTo(0);

        // ---- the partitions --------------------------------------------------
        Map<String, List<Episode>> chronological = chronologicalSplit(real);
        assertThat(disjoint(chronological)).isTrue();
        Map<String, List<Episode>> grouped = groupedSplit(real);
        assertThat(disjoint(grouped)).isTrue();
        assertThat(firstConflictingInput(grouped))
            .as("the grouped split must keep every input inside one partition").isNull();

        List<Episode> newOnly = newCount > 0 && newCount <= real.size()
            ? real.subList(historicalCount, real.size()) : List.of();
        Map<String, List<Episode>> chronologicalNew = chronologicalSplit(newOnly);

        // ---- the production window -------------------------------------------
        int windowSize = Math.min(real.size(), 20);
        List<Episode> window = real.subList(real.size() - windowSize, real.size());
        Map<String, Object> windowReport = new LinkedHashMap<>();
        windowReport.put("selector", "RealSleepScheduler.java:237-240 — the last 20 entries, "
            + "no class awareness");
        windowReport.put("size", windowSize);
        windowReport.put("distribution", distribution(window));

        // ---- the representation comparison -----------------------------------
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("record", "C2A-LEARNING-EVIDENCE");
        report.put("date", "2026-11-15");
        report.put("measuredBy", "matrix-core/src/test/java/io/matrix/knowledge/"
            + "C2ALearningEvidenceTest.java");
        report.put("featureBits", FEATURE_BITS);
        report.put("trainerBits", TRAINER_BITS);
        report.put("seeds", SEEDS);
        report.put("labelRule", "confidence >= 0.7 AND accepted (RealSleepScheduler.java:249) "
            + "— AUTOMATICALLY DERIVED, never curated");
        report.put("encoderCrossCheck", crossCheck);
        report.put("productionWindow", windowReport);
        report.put("logBeforeCollection", historicalCount);
        report.put("logAfterCollection", newCount);

        Map<String, Object> datasets = new LinkedHashMap<>();
        datasets.put("realWholeLog", evaluate("REAL_LOG (historical + new)", chronological,
            grouped, real));
        if (!newOnly.isEmpty()) {
            datasets.put("realNewObservationsOnly",
                evaluate("REAL_LOG (new observations only)", chronologicalNew, null, newOnly));
        }
        List<Episode> synthetic = syntheticEpisodes(root);
        if (!synthetic.isEmpty()) {
            datasets.put("controlledSynthetic", evaluate("CONTROLLED_SYNTHETIC", null, null,
                synthetic));
        }
        report.put("datasets", datasets);

        // ---- the leakage assertions ------------------------------------------
        Map<String, Object> leakage = new LinkedHashMap<>();
        leakage.put("chronologicalInputsDisjoint", inputsDisjoint(chronological));
        leakage.put("groupedInputsDisjoint", inputsDisjoint(grouped));
        leakage.put("groupedPartitionsDisjoint", disjoint(grouped));
        Set<String> trainInputs = new LinkedHashSet<>();
        chronological.get("train").forEach(e -> trainInputs.add(e.input()));
        long reused = chronological.get("test").stream()
            .filter(e -> trainInputs.contains(e.input())).count();
        leakage.put("chronologicalTestInputsSeenInTrain", reused + " of "
            + chronological.get("test").size());
        Set<String> syntheticTexts = new LinkedHashSet<>();
        synthetic.forEach(e -> syntheticTexts.add(e.input() + " " + e.reply()));
        Set<String> realTexts = new LinkedHashSet<>();
        real.forEach(e -> realTexts.add(e.input() + " " + e.reply()));
        syntheticTexts.retainAll(realTexts);
        leakage.put("syntheticRealExactTextOverlap", syntheticTexts.size());
        assertThat(syntheticTexts).as("the synthetic fixture shares no episode with the log")
            .isEmpty();
        assertThat(leakage.get("groupedInputsDisjoint")).isEqualTo(true);
        report.put("leakage", leakage);

        // ---- the HISTORICAL measurement, reported alongside ------------------
        if (Files.isRegularFile(historical)) {
            JsonNode old = JSON.readTree(Files.readString(historical));
            if (old != null && old.has("real")) {
                Map<String, Object> historicalBlock = new LinkedHashMap<>();
                historicalBlock.put("source", "docs-v2/engineering/E1-LEARNING-EVIDENCE.json "
                    + "(retained, measured on the 108-record October log, NOT overwritten)");
                historicalBlock.put("real", old.get("real"));
                historicalBlock.put("productionWindow", old.get("productionWindow"));
                report.put("historicalMeasurement", historicalBlock);
            }
        }

        // ---- write the artefact ----------------------------------------------
        ObjectNode root2 = JSON.createObjectNode();
        if (Files.isRegularFile(artifact)) {
            JsonNode existing = JSON.readTree(Files.readString(artifact));
            if (existing != null && existing.isObject()) root2.setAll((ObjectNode) existing);
        }
        root2.set("measurements", JSON.valueToTree(report));
        Files.createDirectories(artifact.toAbsolutePath().getParent());
        Files.writeString(artifact,
            JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root2));

        System.out.println("[C2A-M] real log: " + real.size() + " episodes, "
            + chronological.get("train").size() + "/" + chronological.get("validation").size()
            + "/" + chronological.get("test").size());
        System.out.println("[C2A-M] window: " + windowReport.get("distribution"));
        System.out.println("[C2A-M] cross-check: " + crossCheck);
        System.out.println("[C2A-M] artefact: " + artifact.toAbsolutePath());
    }

    /**
     * Measure one dataset: the partition's class distribution, the collisions of each
     * representation, and the learner metrics of each representation over the seeds.
     */
    private static Map<String, Object> evaluate(String dataset,
                                                Map<String, List<Episode>> split,
                                                Map<String, List<Episode>> grouped,
                                                List<Episode> all) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", dataset);
        result.put("provenance", dataset.startsWith("CONTROLLED")
            ? "CONTROLLED_SYNTHETIC — a mechanism fixture, never real-world evidence; its "
              + "labels are MANUALLY CURATED by the fixture's documented content rule"
            : "HISTORICAL + NEWLY_EXECUTED_REAL_OBSERVATION; the labels are AUTOMATICALLY "
              + "DERIVED by the production rule from what the runtime produced");
        long[][] raw = features(all);
        result.put("distribution", distribution(all));
        result.put("collisions", collisionAnalysis(all, raw));

        if (split == null) {
            // the synthetic fixture carries its own declared partition
            Map<String, List<Episode>> declared = new LinkedHashMap<>();
            for (String partition : List.of("train", "validation", "test")) {
                List<Episode> part = new ArrayList<>();
                for (Episode episode : all) {
                    if (partition.equals(episode.declaredPartition())) part.add(episode);
                }
                declared.put(partition, part);
            }
            split = declared;
        }

        List<Episode> train = split.get("train");
        List<Episode> validation = split.get("validation");
        List<Episode> test = split.get("test");
        long[][] trainFeatures = features(train);
        long[][] validationFeatures = features(validation);
        long[][] testFeatures = features(test);
        boolean[] trainLabels = labels(train);
        boolean[] validationLabels = labels(validation);
        boolean[] testLabels = labels(test);

        result.put("partitions", Map.of("train", train.size(), "validation", validation.size(),
            "test", test.size()));
        result.put("partitionClassDistribution", Map.of(
            "train", distribution(train), "validation", distribution(validation),
            "test", distribution(test)));
        result.put("partitionsDisjoint", disjoint(split));
        result.put("memorisationCeiling256", memorisationCeiling(train, test,
            trainFeatures, testFeatures));
        result.put("nearestNeighbour256", nearestNeighbour256(trainFeatures, trainLabels,
            testFeatures, testLabels));

        int[] informative = informativeBitPositions(trainFeatures, FEATURE_BITS);
        int[] projected = informative.length <= TRAINER_BITS
            ? informative : Arrays.copyOf(informative, TRAINER_BITS);
        int[] discriminative = discriminativeBitPositions(trainFeatures, trainLabels);
        result.put("informativeBitsOnTrain", TsetlinTrainer.countInformativeBits(trainFeatures,
            FEATURE_BITS));
        result.put("informativeBitPositionsUsed", projected.length);
        result.put("discriminativeBitPositionsUsed", discriminative.length);

        Random randomSeed = new Random(42);
        Set<Integer> chosen = new LinkedHashSet<>();
        while (chosen.size() < TRAINER_BITS) chosen.add(randomSeed.nextInt(FEATURE_BITS));
        int[] randomPositions = chosen.stream().mapToInt(Integer::intValue).toArray();

        Map<String, long[][]> trainViews = new LinkedHashMap<>();
        Map<String, long[][]> validationViews = new LinkedHashMap<>();
        Map<String, long[][]> testViews = new LinkedHashMap<>();
        trainViews.put("productionFold", trainFeatures);
        validationViews.put("productionFold", validationFeatures);
        testViews.put("productionFold", testFeatures);
        trainViews.put("informativeProjection", project(trainFeatures, projected));
        validationViews.put("informativeProjection", project(validationFeatures, projected));
        testViews.put("informativeProjection", project(testFeatures, projected));
        trainViews.put("discriminativeProjection", project(trainFeatures, discriminative));
        validationViews.put("discriminativeProjection", project(validationFeatures, discriminative));
        testViews.put("discriminativeProjection", project(testFeatures, discriminative));
        trainViews.put("randomProjection", project(trainFeatures, randomPositions));
        validationViews.put("randomProjection", project(validationFeatures, randomPositions));
        testViews.put("randomProjection", project(testFeatures, randomPositions));

        // the shuffled-label control: the SAME pipeline with training labels that carry
        // no information. The shuffle is prepared here and used AFTER the real
        // representations have been measured — shuffling first would have measured the
        // real representations on shuffled labels.
        boolean[] shuffled = trainLabels.clone();
        Random shuffleSeed = new Random(2026);
        // A hand-written Fisher-Yates: Collections.shuffle(Arrays.asList(boolean[]))
        // would box the array into a temporary Boolean[] and shuffle THAT, leaving the
        // primitive array untouched — a control that silently controls nothing.
        for (int i = shuffled.length - 1; i > 0; i--) {
            int j = shuffleSeed.nextInt(i + 1);
            boolean swap = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = swap;
        }
        int moved = 0;
        for (int i = 0; i < shuffled.length; i++) {
            if (shuffled[i] != trainLabels[i]) moved++;
        }
        assertThat(moved).as("the shuffled-label control must actually change the labels")
            .isGreaterThan(0);

        Map<String, Object> representations = new LinkedHashMap<>();
        for (String representation : List.of("productionFold", "informativeProjection",
                "discriminativeProjection", "randomProjection")) {
            List<Map<String, Object>> perSeed = new ArrayList<>();
            for (int seed : SEEDS) {
                Metrics validationMetrics = measure(trainViews.get(representation),
                    trainLabels, validationViews.get(representation), validationLabels, seed);
                Metrics testMetrics = measure(trainViews.get(representation),
                    trainLabels, testViews.get(representation), testLabels, seed);
                Map<String, Object> seedResult = new LinkedHashMap<>();
                seedResult.put("seed", seed);
                seedResult.put("validation", validationMetrics);
                seedResult.put("test", testMetrics);
                if (seed == SEEDS[0]) {
                    Metrics repeat = measure(trainViews.get(representation),
                        trainLabels, validationViews.get(representation), validationLabels, seed);
                    seedResult.put("deterministicRepeatEqual",
                        repeat.accuracy() == validationMetrics.accuracy());
                }
                perSeed.add(seedResult);
            }
            representations.put(representation, perSeed);
        }

        // the shuffled-label control, on the production fold only
        List<Map<String, Object>> shuffledSeeds = new ArrayList<>();
        for (int seed : SEEDS) {
            Metrics testMetrics = measure(trainViews.get("productionFold"), shuffled,
                testViews.get("productionFold"), testLabels, seed);
            Map<String, Object> seedResult = new LinkedHashMap<>();
            seedResult.put("seed", seed);
            seedResult.put("test", testMetrics);
            shuffledSeeds.add(seedResult);
        }
        representations.put("shuffledLabels_control", shuffledSeeds);
        result.put("shuffledLabelsMoved", moved);

        result.put("representations", representations);

        // what each representation does to the class information, measured on the data
        Map<String, Object> representationCollisions = new LinkedHashMap<>();
        representationCollisions.put("raw256", viewCollisions(all, raw));
        representationCollisions.put("productionFold", viewCollisions(all, foldView(raw)));
        representationCollisions.put("informativeProjection", viewCollisions(all,
            project(raw, projected)));
        representationCollisions.put("discriminativeProjection", viewCollisions(all,
            project(raw, discriminative)));
        representationCollisions.put("randomProjection", viewCollisions(all,
            project(raw, randomPositions)));
        result.put("representationCollisions", representationCollisions);
        result.put("representationWidths", Map.of(
            "raw256", FEATURE_BITS, "productionFold", TRAINER_BITS,
            "informativeProjection", projected.length,
            "discriminativeProjection", discriminative.length,
            "randomProjection", TRAINER_BITS));
        result.put("cost", costModel(raw, trainFeatures));

        // stability across seeds
        Map<String, Object> stability = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : representations.entrySet()) {
            List<?> perSeed = (List<?>) entry.getValue();
            List<Double> accuracies = new ArrayList<>();
            List<Double> balanced = new ArrayList<>();
            for (Object seedResult : perSeed) {
                Metrics metrics = (Metrics) ((Map<?, ?>) seedResult).get("test");
                accuracies.add(metrics.accuracy());
                balanced.add(metrics.balanced());
            }
            stability.put(entry.getKey(), Map.of(
                "testAccuracyMin", Collections.min(accuracies),
                "testAccuracyMax", Collections.max(accuracies),
                "testAccuracyMean", accuracies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0),
                "testBalancedMin", Collections.min(balanced),
                "testBalancedMax", Collections.max(balanced)));
        }
        result.put("stabilityAcrossSeeds", stability);

        if (grouped != null) {
            Map<String, Object> groupedResult = new LinkedHashMap<>();
            groupedResult.put("partitions", Map.of("train", grouped.get("train").size(),
                "validation", grouped.get("validation").size(),
                "test", grouped.get("test").size()));
            groupedResult.put("partitionsDisjoint", disjoint(grouped));
            groupedResult.put("inputsDisjoint", inputsDisjoint(grouped));
            groupedResult.put("distribution", distribution(grouped.get("test")));
            groupedResult.put("partitionClassDistribution", Map.of(
                "train", distribution(grouped.get("train")),
                "validation", distribution(grouped.get("validation")),
                "test", distribution(grouped.get("test"))));
            long[][] groupedTrain = features(grouped.get("train"));
            boolean[] groupedTrainLabels = labels(grouped.get("train"));
            long[][] groupedTest = features(grouped.get("test"));
            boolean[] groupedTestLabels = labels(grouped.get("test"));
            int[] groupedInformative = informativeBitPositions(groupedTrain, FEATURE_BITS);
            int[] groupedProjected = groupedInformative.length <= TRAINER_BITS
                ? groupedInformative : Arrays.copyOf(groupedInformative, TRAINER_BITS);
            int[] groupedDiscriminative = discriminativeBitPositions(groupedTrain,
                groupedTrainLabels);
            List<Map<String, Object>> groupedPerSeed = new ArrayList<>();
            for (int seed : SEEDS) {
                Metrics metrics = measure(groupedTrain, groupedTrainLabels,
                    groupedTest, groupedTestLabels, seed);
                Map<String, Object> seedResult = new LinkedHashMap<>();
                seedResult.put("seed", seed);
                seedResult.put("productionFold", metrics);
                groupedPerSeed.add(seedResult);
            }
            groupedResult.put("productionFold", groupedPerSeed);
            groupedResult.put("informativeProjectionWidth", groupedProjected.length);
            groupedResult.put("discriminativeProjectionWidth", groupedDiscriminative.length);
            groupedResult.put("note", "the leakage-controlled partition: whole inputs stay "
                + "whole, so a test episode whose input was never trained on");
            result.put("groupedSplit", groupedResult);
        }
        return result;
    }

    /** The measured cost of each representation: bits, bytes and the fold's CPU time. */
    private static Map<String, Object> costModel(long[][] raw, long[][] trainFeatures) {
        TsetlinTrainer packer = new TsetlinTrainer(TRAINER_BITS, 2, 2, new Random(1));
        long startFold = System.nanoTime();
        for (long[] feature : raw) packer.packForTest(feature);
        long foldNanos = System.nanoTime() - startFold;

        long startEncode = System.nanoTime();
        for (long[] feature : raw) {
            BitSet bits = new BitSet(FEATURE_BITS);
            for (int i = 0; i < feature.length; i++) {
                for (int b = 0; b < Long.SIZE; b++) {
                    if (((feature[i] >>> b) & 1L) != 0L) bits.set(i * 64 + b);
                }
            }
        }
        long encodeNanos = System.nanoTime() - startEncode;

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("raw256BytesPerEpisode", FEATURE_BITS / 8);
        cost.put("folded16BytesPerEpisode", TRAINER_BITS / 8);
        cost.put("foldNanosTotal", foldNanos);
        cost.put("foldNanosPerEpisode", raw.length == 0 ? 0.0 : foldNanos / (double) raw.length);
        cost.put("encode256NanosPerEpisode", raw.length == 0 ? 0.0
            : encodeNanos / (double) raw.length);
        cost.put("trainerStateCells", 64 * 2 * TRAINER_BITS * 200);
        cost.put("trainerStateNote", "nClauses(64) x 2 polarities x inputBits(16) x nStates(200)"
            + " — identical for every representation, because every representation is "
            + "consumed at the trainer's 16-bit width");
        cost.put("note", "the fold's CPU cost is measured with the production "
            + "TsetlinTrainer.packForTest; the encode cost is the mirror's, and the mirror is "
            + "verified equal to the production encoder by the cross-check");
        return cost;
    }
}
