package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The teach-back experiment, rebuilt (RECON-W41).
 *
 * <p><b>What the RECON-W40 measurement was.</b> A learner that ignored the
 * question and returned the lesson, scored by the share of the item's key
 * terms that the lesson contained. It measured lesson containment. Research
 * stream A checked it against the published validity requirements for
 * teach-back and found it fails seven of seven: it is the verbatim-parrot
 * anti-pattern the AHRQ names, it is scored against the lesson rather than
 * against an answer key, the pre and post tasks are the same items, the
 * measurement is immediate, and it measures recall rather than transfer.
 *
 * <p><b>What this experiment is.</b> Four phases over DISJOINT item sets:
 *
 * <ol>
 *   <li><b>PRE</b> — the same learner before the lesson;</li>
 *   <li><b>POST</b> — after the lesson, on items the pre-test never
 *       contained;</li>
 *   <li><b>DELAYED</b> — after a LOGICAL delay, on a third set of items.
 *       The delay is a counter the harness supplies, never the wall clock:
 *       the repository's constitution forbids wall-clock in decision paths,
 *       and a logical delay validates the measurement machinery, not human
 *       retention;</li>
 *   <li><b>TRANSFER</b> — on novel items that require the principle rather
 *       than the wording.</li>
 * </ol>
 *
 * <p>The learner is a {@link StructuredLearner}: it holds the taught claims
 * and the evidence, never the prose of the lesson, and it must CHOOSE an
 * answer. The legacy path is measured too, and the two are compared, because
 * a claim about a better measurement is only meaningful against the old one.
 *
 * <p><b>What is not claimed.</b> The learner is synthetic. A gain measured
 * here is a machine-to-machine result: it shows that the lesson carried the
 * content to a learner that could only use what it was taught. It is not
 * evidence about human learning, human retention, or the effectiveness of
 * teaching people.
 */
public final class TeachBackExperiment {

    /** The share of an utterance that must be a verbatim span of the lesson
     *  before the probe counts as a restatement. */
    public static final double RESTATEMENT_THRESHOLD = 0.5;

    /** One phase of the experiment. */
    public record Phase(String name, int itemsMeasured, int correct, double accuracy, String note) {
    }

    /** The whole experiment. */
    public record Result(
            String conceptId,
            String datasetId,
            int datasetVersion,
            List<Phase> phases,
            List<Calibration.Forecast> forecasts,
            Calibration.Report calibration,
            int probes,
            int restatements,
            int legacyRestatements,
            double structuredAccuracy,
            double legacyAccuracy,
            double lexicalBaselineAccuracy,
            double expectedMinimumGain,
            boolean metExpectation,
            String conclusion) {

        public Result {
            phases = List.copyOf(phases == null ? List.of() : phases);
            forecasts = List.copyOf(forecasts == null ? List.of() : forecasts);
        }
    }

    private TeachBackExperiment() {
    }

    /**
     * Runs the experiment.
     *
     * @param conceptId the concept that was taught
     * @param snapshot the workspace the lesson was built from
     * @param dataset the dataset, whose items declare their phase in
     *     {@code variant}
     * @param taughtClaims the claims the learner is taught
     * @param lessonText the prose of the lesson, used only for the leakage
     *     audit
     * @param expectedMinimumGain the gain the experiment expects
     */
    public static Result run(String conceptId, KnowledgeSnapshot snapshot, AssessmentDataset dataset,
                             List<Claim> taughtClaims, String lessonText,
                             double expectedMinimumGain) {
        List<AssessmentItem> items = dataset.items().stream()
                .filter(item -> item.conceptId().equals(conceptId))
                .filter(AssessmentItem::hasAlternatives)
                .toList();

        StructuredLearner before = new StructuredLearner(List.of(), snapshot.evidence());
        StructuredLearner after = new StructuredLearner(taughtClaims, snapshot.evidence());

        List<Phase> phases = new ArrayList<>();
        List<Calibration.Forecast> forecasts = new ArrayList<>();
        int probes = 0;
        int restatements = 0;

        for (String phase : List.of("pre", "post", "delayed", "transfer")) {
            StructuredLearner learner = phase.equals("pre") ? before : after;
            List<AssessmentItem> phaseItems = items.stream()
                    .filter(item -> phase.equals(item.variant()))
                    .toList();
            if (phaseItems.isEmpty()) {
                phases.add(new Phase(phase, 0, 0, 0.0,
                        "no item declares this phase, so nothing was measured here"));
                continue;
            }
            int correct = 0;
            for (AssessmentItem item : phaseItems) {
                StructuredLearner.Attempt attempt = learner.attempt(item);
                if (attempt == null) {
                    continue;
                }
                probes++;
                boolean isCorrect = item.correctAlternativeId().equals(attempt.alternativeId());
                if (isCorrect) {
                    correct++;
                }
                forecasts.add(new Calibration.Forecast(item.id(), attempt.confidence(), isCorrect));

                // The leakage audit: what the learner said, against the
                // lesson it was given.
                AssessmentItem.Alternative chosen = item.alternative(attempt.alternativeId());
                if (chosen != null) {
                    String utterance = chosen.text();
                    if (restates(utterance, lessonText)) {
                        restatements++;
                    }
                }
            }
            phases.add(new Phase(phase, phaseItems.size(), correct,
                    (double) correct / phaseItems.size(),
                    phase.equals("pre")
                            ? "the same learner before the lesson"
                            : phase.equals("post")
                                    ? "after the lesson, on items the pre-test never contained"
                                    : phase.equals("delayed")
                                            ? "after a logical delay, on a third set of items"
                                            : "on novel items that require the principle"));
        }

        // The legacy path, measured on the same items: the learner that
        // echoes the lesson, scored by the share of the item's key terms the
        // lesson contains.
        int legacyCorrect = 0;
        int legacyProbes = 0;
        int legacyRestatements = 0;
        for (AssessmentItem item : items) {
            if (!item.variant().equals("post")) {
                continue;
            }
            legacyProbes++;
            double coverage = coverageOfLesson(item, lessonText);
            if (coverage >= 0.6) {
                legacyCorrect++;
            }
            if (restates(lessonText, lessonText)) {
                legacyRestatements++;
            }
        }
        double legacyAccuracy = legacyProbes == 0 ? 0.0 : (double) legacyCorrect / legacyProbes;

        // The lexical baseline: a learner that picks the alternative with the
        // highest overlap with the lesson's words. The adversarial items are
        // chosen so that this baseline is fooled.
        int baselineCorrect = 0;
        int baselineProbes = 0;
        for (AssessmentItem item : items) {
            if (!item.variant().equals("post")) {
                continue;
            }
            baselineProbes++;
            AssessmentItem.Alternative best = null;
            double bestScore = -1.0;
            for (AssessmentItem.Alternative alternative : item.alternatives()) {
                double score = lexicalOverlap(alternative.text(), lessonText);
                if (score > bestScore) {
                    bestScore = score;
                    best = alternative;
                }
            }
            if (best != null && item.correctAlternativeId().equals(best.id())) {
                baselineCorrect++;
            }
        }
        double baselineAccuracy = baselineProbes == 0 ? 0.0 : (double) baselineCorrect / baselineProbes;

        Phase post = phases.stream().filter(phase -> phase.name().equals("post")).findFirst().orElse(null);
        Phase pre = phases.stream().filter(phase -> phase.name().equals("pre")).findFirst().orElse(null);
        double structuredAccuracy = post == null ? 0.0 : post.accuracy();
        double preAccuracy = pre == null ? 0.0 : pre.accuracy();
        double gain = structuredAccuracy - preAccuracy;
        boolean met = gain >= expectedMinimumGain;

        Calibration.Report calibration = Calibration.measure(forecasts);

        String conclusion = "The learner chose correctly on " + postAccuracyText(structuredAccuracy)
                + " of the post-lesson items, against " + postAccuracyText(preAccuracy)
                + " before the lesson. The legacy containment measurement scored "
                + postAccuracyText(legacyAccuracy) + " on the same items, and the lexical baseline "
                + "scored " + postAccuracyText(baselineAccuracy) + ". "
                + (restatements == 0
                        ? "No probe restated the lesson."
                        : restatements + " of " + probes + " probes restated the lesson verbatim.")
                + " This is a machine-to-machine measurement: the learner is synthetic, and it shows "
                + "that the lesson carried the content to a learner that could only use what it was "
                + "taught. It is not evidence about human learning or human retention.";

        return new Result(conceptId, dataset.id(), dataset.version(), phases, forecasts, calibration,
                probes, restatements, legacyRestatements, structuredAccuracy, legacyAccuracy,
                baselineAccuracy, expectedMinimumGain, met, conclusion);
    }

    private static String postAccuracyText(double accuracy) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", accuracy * 100.0);
    }

    /**
     * Whether an utterance is a verbatim span of the lesson.
     *
     * <p>The longest run of consecutive words the two share, as a share of
     * the utterance's length. The legacy learner, which returns the lesson
     * itself, scores 1.0; a learner that chooses among the item's
     * alternatives does not.
     */
    public static boolean restates(String utterance, String lessonText) {
        List<String> words = TextFeatures.rawWords(utterance);
        if (words.isEmpty() || lessonText == null || lessonText.isBlank()) {
            return false;
        }
        List<String> lesson = TextFeatures.rawWords(lessonText);
        int longest = 0;
        for (int i = 0; i < words.size(); i++) {
            for (int j = 0; j < lesson.size(); j++) {
                int run = 0;
                while (i + run < words.size() && j + run < lesson.size()
                        && words.get(i + run).equals(lesson.get(j + run))) {
                    run++;
                }
                longest = Math.max(longest, run);
            }
        }
        return (double) longest / words.size() >= RESTATEMENT_THRESHOLD;
    }

    /** The share of an item's key terms the lesson contains. */
    private static double coverageOfLesson(AssessmentItem item, String lessonText) {
        Set<String> lessonTerms = new java.util.LinkedHashSet<>(TextFeatures.tokens(lessonText));
        if (item.keyTerms().isEmpty()) {
            return 0.0;
        }
        int present = 0;
        for (String term : item.keyTerms()) {
            if (lessonTerms.contains(term)) {
                present++;
            }
        }
        return (double) present / item.keyTerms().size();
    }

    /** The share of the first text's content terms that appear in the second. */
    private static double lexicalOverlap(String text, String other) {
        Set<String> terms = new java.util.LinkedHashSet<>(TextFeatures.tokens(text));
        if (terms.isEmpty()) {
            return 0.0;
        }
        Set<String> otherTerms = new java.util.LinkedHashSet<>(TextFeatures.tokens(other));
        int shared = 0;
        for (String term : terms) {
            if (otherTerms.contains(term)) {
                shared++;
            }
        }
        return (double) shared / terms.size();
    }
}
