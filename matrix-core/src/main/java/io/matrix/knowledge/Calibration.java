package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * How well a stated confidence matches the outcome (RECON-W41).
 *
 * <p>The mission's §6.5 asks for the confidence to be evaluated on a held-out
 * question set, and research stream A names the instruments: the Brier
 * score, and Murphy's decomposition of it into reliability, resolution and
 * uncertainty.
 *
 * <p><b>What the numbers mean.</b>
 *
 * <ul>
 *   <li>The <b>Brier score</b> is the mean squared error of the stated
 *       confidence against the outcome. Lower is better; 0 is perfect and
 *       0.25 is what a constant forecast of 0.5 scores.</li>
 *   <li>The <b>reliability</b> is how far the stated confidences are from
 *       the frequencies they predict: the gap between "I am 80% sure" and
 *       "80% of the time I was right".</li>
 *   <li>The <b>resolution</b> is how much the confidence SPREADS the
 *       outcomes: a forecaster who says 0.5 about everything has perfect
 *       reliability and no resolution at all.</li>
 *   <li>The <b>uncertainty</b> is the base rate: how often the outcome was
 *       true regardless of what was said.</li>
 * </ul>
 *
 * <p>They obey {@code Brier = reliability - resolution + uncertainty}. A
 * confidence that is not calibrated shows up as a reliability that does not
 * vanish.
 */
public final class Calibration {

    /** The number of bins the confidences are grouped into. */
    public static final int BIN_COUNT = 5;

    /** One forecast and its outcome. */
    public record Forecast(String itemId, double confidence, boolean correct) {
    }

    /** The measurement of a set of forecasts. */
    public record Report(
            int count,
            double brier,
            double reliability,
            double resolution,
            double uncertainty,
            double meanConfidence,
            double observedFrequency,
            String meaning) {

        public Report {
            meaning = meaning == null || meaning.isBlank()
                    ? "The reliability term is the part of the error that a better-calibrated "
                            + "forecast would remove; it is near zero when the stated confidence "
                            + "matches the observed frequency."
                    : meaning;
        }
    }

    private Calibration() {
    }

    /** Measures a set of forecasts. */
    public static Report measure(List<Forecast> forecasts) {
        if (forecasts == null || forecasts.isEmpty()) {
            return new Report(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    "No forecast was measured, so nothing is claimed about calibration.");
        }
        int count = forecasts.size();
        double brier = 0.0;
        double meanConfidence = 0.0;
        int correctCount = 0;
        for (Forecast forecast : forecasts) {
            double confidence = Math.max(0.0, Math.min(1.0, forecast.confidence()));
            double outcome = forecast.correct() ? 1.0 : 0.0;
            brier += (confidence - outcome) * (confidence - outcome);
            meanConfidence += confidence;
            if (forecast.correct()) {
                correctCount++;
            }
        }
        brier /= count;
        meanConfidence /= count;
        double observedFrequency = (double) correctCount / count;

        // Murphy's decomposition, with the forecasts grouped into fixed bins.
        double reliability = 0.0;
        double resolution = 0.0;
        for (int bin = 0; bin < BIN_COUNT; bin++) {
            double lower = (double) bin / BIN_COUNT;
            double upper = (double) (bin + 1) / BIN_COUNT;
            List<Forecast> inBin = new ArrayList<>();
            for (Forecast forecast : forecasts) {
                double confidence = forecast.confidence();
                boolean inRange = bin == BIN_COUNT - 1
                        ? confidence >= lower && confidence <= upper
                        : confidence >= lower && confidence < upper;
                if (inRange) {
                    inBin.add(forecast);
                }
            }
            if (inBin.isEmpty()) {
                continue;
            }
            double binForecast = 0.0;
            double binOutcome = 0.0;
            for (Forecast forecast : inBin) {
                binForecast += forecast.confidence();
                binOutcome += forecast.correct() ? 1.0 : 0.0;
            }
            binForecast /= inBin.size();
            binOutcome /= inBin.size();
            double weight = (double) inBin.size() / count;
            reliability += weight * (binForecast - binOutcome) * (binForecast - binOutcome);
            resolution += weight * (binOutcome - observedFrequency) * (binOutcome - observedFrequency);
        }
        double uncertainty = observedFrequency * (1.0 - observedFrequency);

        // Brier = reliability - resolution + uncertainty, by Murphy's identity.
        double adjusted = Math.max(0.0, reliability - resolution + uncertainty);
        return new Report(count, brier, reliability, resolution, uncertainty,
                meanConfidence, observedFrequency,
                "The reliability term is the part of the error that a better-calibrated forecast "
                        + "would remove; it is near zero when the stated confidence matches the "
                        + "observed frequency. With " + count + " forecasts the measurement is a "
                        + "demonstration, not a generalisation.");
    }
}
