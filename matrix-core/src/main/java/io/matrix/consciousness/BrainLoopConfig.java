package io.matrix.consciousness;

/**
 * RUN 210 — BrainLoopConfig (typed configuration).
 *
 * <p>Configurable parameters for BrainLoopService in JSON-like
 * format. Per CONSTITUTION VI, defaults are conservative.
 *
 * <p>Currently includes: arousal baseline, maxRise, decayRate,
 * gate thresholds. Can be saved/loaded as JSON (no LLM deps).
 */
public final class BrainLoopConfig {

    public record Config(double arousalBaseline,
                          double maxRise,
                          double decayRate,
                          int bitLength,
                          int maxTurns) {

        public static Config defaults() {
            return new Config(
                    ArousalDynamics.BASELINE,
                    0.4, 0.05,
                    256, 32);
        }

        public String toJson() {
            return "{" +
                    "\"arousalBaseline\":" + arousalBaseline +
                    ",\"maxRise\":" + maxRise +
                    ",\"decayRate\":" + decayRate +
                    ",\"bitLength\":" + bitLength +
                    ",\"maxTurns\":" + maxTurns +
                    "}";
        }

        public static Config fromJson(String json) {
            double baseline = Double.parseDouble(
                    jsonRead(json, "arousalBaseline"));
            double maxRise = Double.parseDouble(
                    jsonRead(json, "maxRise"));
            double decay = Double.parseDouble(
                    jsonRead(json, "decayRate"));
            int bits = Integer.parseInt(
                    jsonRead(json, "bitLength"));
            int turns = Integer.parseInt(
                    jsonRead(json, "maxTurns"));
            return new Config(baseline, maxRise, decay, bits, turns);
        }
    }

    public static Config fromDefaults() {
        return Config.defaults();
    }

    private static String jsonRead(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return "";
        int colon = json.indexOf(":", idx);
        if (colon < 0) return "";
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') {
            int end = start + 1;
            while (end < json.length() && json.charAt(end) != '"') end++;
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() &&
                    "0123456789-.".indexOf(json.charAt(end)) >= 0) end++;
            return json.substring(start, end);
        }
    }
}
