package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RECON-W32.3 — turn a sensor stream into statements the mind can be asked about.
 *
 * <p><b>Why.</b> A JSON-lines sensor stream was ingested as an opaque blob, so
 * {@code "What is the temperature?"} had nothing to match and refused. Before RECON-W32.1
 * it was worse: the format fell through to a SHA-256 fingerprint, so the temperature
 * existed only as a hash of a file that contained it.</p>
 *
 * <p>What this produces is a TREND, not a transcription. Three readings become a
 * statement about direction and extent, which is the difference between a mind that has
 * read a file and one that has observed a change.</p>
 *
 * <p><b>The honesty rules, which matter more than the parsing.</b></p>
 * <ul>
 *   <li>One reading has no trend. A direction cannot be established from a single point,
 *       and reporting "rose" from it is exactly the confident-from-nothing claim this
 *       campaign exists to remove.</li>
 *   <li>A constant field is stable, not rising and not falling.</li>
 *   <li>A field is a trend only if EVERY reading carries it as a number. A field present
 *       in some readings and absent in others has an unknown direction, not a direction.</li>
 *   <li>Co-occurrence is never causation. A boolean that is true while something rises is
 *       reported as co-occurring and never as causing it, because the data cannot
 *       support that claim. {@link Stream#coOccurrences()} and {@link Stream#trends()} are
 *       separate precisely so the distinction survives into the stored text.</li>
 * </ul>
 *
 * <p><b>Scope, honestly.</b> This is not temporal reasoning. Readings are taken in file
 * order, no timestamps are parsed or ordered, and "rose" means the values increased
 * between the first and last reading in the file. A stream whose lines are out of
 * chronological order will produce a wrong trend, and the class does not attempt to detect
 * that.</p>
 *
 * <p><b>Article III.</b> Pure functions of the input string, no clock, no randomness, no
 * I/O, no network. The same stream always yields the same facts.</p>
 */
public final class SensorStreamDecoder {

    private SensorStreamDecoder() {}

    /** Readings accepted from one file. Unit: lines. Bounds memory on a hostile input. */
    public static final int MAX_READINGS = 100_000;

    /**
     * Field names treated as index or clock, not measurement.
     *
     * <p>A reading index increases by construction, so reporting "t rose from 0 to 2
     * across 3 readings" is a true statement about nothing. The first version of this
     * class produced exactly that as a headline trend, which would have put the least
     * informative sentence first in every sensor fact.</p>
     *
     * <p>Unit: lowercase field names. Matching is exact against the lowercased name, so a
     * measurement genuinely called "time_of_flight" is unaffected.</p>
     */
    private static final Set<String> INDEX_FIELDS = Set.of(
        "t", "ts", "time", "timestamp", "i", "idx", "index", "n", "seq", "sequence",
        "step", "tick", "epoch", "ms", "millis", "id", "seqno");

    /**
     * Minimum change between first and last reading to be called a trend.
     * Unit: absolute units of the field. Below this a trend is noise, and naming noise
     * "rose" is a claim the data does not support.
     */
    public static final double MIN_TREND_DELTA = 1e-9;

    /**
     * A decoded stream.
     *
     * <p>Numeric field values are kept in file order. Boolean and string fields are kept
     * as observed values so co-occurrence can be reported without inventing a mechanism.</p>
     */
    public record Stream(List<Map<String, Object>> readings) {

        /**
         * Values of one field across all readings, in file order.
         *
         * @param field field name
         * @return the numeric values, or an empty list when the field is absent or
         *         non-numeric in any reading
         */
        /**
         * Readings in TIME ORDER when the data supports it, in file order otherwise.
         *
         * <p>RECON-W32.19. A trend is a claim about CHANGE, so it needs an ordering, and
         * file order is not one. Measured on the readings 20.0, 22.8, 21.0: delivering
         * them in a different order produced "temperature_c rose 20 to 21" and
         * "rose 20 to 22.8" — two different claims from one physical event, no error
         * anywhere. The first is not merely wrong in magnitude: the temperature peaked
         * in the middle, so a first-to-last comparison is not a direction at all.</p>
         *
         * @return readings in time order if every reading carries a recognised numeric
         *         time field, otherwise exactly as they appeared
         */
        public List<Map<String, Object>> ordered() {
            return TimeOrdered.sorted(readings);
        }

        /** True when the ordering rests on a time field rather than on line order. */
        public boolean timeOrdered() {
            for (String f : TimeOrdered.TIME_FIELDS) {
                if (readings.isEmpty()) return false;
                boolean all = true;
                for (Map<String, Object> r : readings) {
                    if (!(r.get(f) instanceof Number)) { all = false; break; }
                }
                if (all) return true;
            }
            return false;
        }

        public List<Double> numeric(String field) {
            List<Double> out = new ArrayList<>();
            for (Map<String, Object> r : ordered()) {
                Object v = r.get(field);
                if (!(v instanceof Number n)) return List.of();
                out.add(n.doubleValue());
            }
            return out;
        }

        /** True when every reading carries {@code field} as a boolean. */
        public boolean hasBoolean(String field) {
            for (Map<String, Object> r : readings) {
                if (!(r.get(field) instanceof Boolean)) return false;
            }
            return !readings.isEmpty();
        }

        /**
         * Trend statements, one per numeric field whose direction is established.
         *
         * <p>Empty for a single reading or a constant field, by design.</p>
         */
        public List<String> trends() {
            List<String> out = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Map<String, Object> r : ordered()) {
                for (String field : r.keySet()) {
                    if (!seen.add(field)) continue;
                    if (INDEX_FIELDS.contains(field.toLowerCase(Locale.ROOT))) continue;
                    List<Double> v = numeric(field);
                    if (v.size() < MIN_READINGS_FOR_TREND) continue;
                    double first = v.get(0);
                    double last = v.get(v.size() - 1);
                    double delta = last - first;
                    if (Math.abs(delta) < MIN_TREND_DELTA) continue;   // stable
                    String direction = delta > 0 ? "rose" : "fell";
                    out.add(field + " " + direction + " " + fmt(first) + " to " + fmt(last));
                }
            }
            return out;
        }

        /**
         * Interior extrema: the peak or trough that the first-to-last trend does not show.
         *
         * <p>RECON-W32.28, SENSOR-1. A trend is first-to-last, so on readings
         * 20.0, 22.8, 21.0 it says only "rose 20 to 21" and the fact that the temperature
         * reached 22.8 <em>in the middle</em> and then came back down is not stored
         * anywhere. That is the more useful half of the signal: it is what distinguishes a
         * transient spike from a sustained change, and no threshold or gate can recover
         * it from a first-to-last pair.</p>
         *
         * <p><b>Only an INTERIOR extremum is reported.</b> When the maximum is the last
         * reading, "peaked at 22.8" adds nothing to "rose 20 to 22.8" — it would be a
         * second fact saying the same thing, and {@code facts()} is a one-fact-per-field
         * budget by design. An extremum is worth a fact only when it contradicts the
         * impression the trend gives.</p>
         *
         * <p>Phrasing stays within the measured ~5-content-token retrievable budget, and
         * the timestamp is the reading's own time field when the stream has one, so the
         * claim is checkable against the data rather than asserted.</p>
         *
         * @return one claim per field with an interior extreme, in first-seen order
         */
        public List<String> extrema() {
            List<String> out = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Map<String, Object> r : ordered()) {
                for (String field : r.keySet()) {
                    if (!seen.add(field)) continue;
                    if (INDEX_FIELDS.contains(field.toLowerCase(Locale.ROOT))) continue;
                    List<Double> v = numeric(field);
                    if (v.size() < MIN_READINGS_FOR_EXTREMUM) continue;
                    String e = interiorExtreme(field, v);
                    if (e != null) out.add(e);
                }
            }
            return out;
        }

        /**
         * The interior max or min of {@code v}, or null when there is none worth stating.
         *
         * <p>Both directions are considered and the more pronounced is reported, because a
         * stream can dip in the middle and still trend upward. A tie between the two is
         * resolved to the maximum, deterministically, so the same input always yields the
         * same claim (Article II).</p>
         */
        private String interiorExtreme(String field, List<Double> v) {
            int n = v.size();
            int hi = 0, lo = 0;
            for (int i = 1; i < n; i++) {
                if (v.get(i) > v.get(hi)) hi = i;
                if (v.get(i) < v.get(lo)) lo = i;
            }
            boolean hiInterior = hi > 0 && hi < n - 1;
            boolean loInterior = lo > 0 && lo < n - 1;
            if (!hiInterior && !loInterior) return null;   // extremes at the endpoints

            // Report whichever excursion is larger RELATIVE to the endpoint the trend
            // already describes, because that is the one the trend hides. A stream that
            // dips far and ends high hides the dip; one that spikes and returns hides the
            // spike. Ties go to the peak, deterministically.
            double last = v.get(n - 1), first = v.get(0);
            double peakSpan  = hiInterior ? v.get(hi) - last : -1;
            double troughSpan = loInterior ? first - v.get(lo) : -1;
            int idx; String word; double span;
            if (troughSpan > peakSpan) { idx = lo; word = "bottomed"; span = troughSpan; }
            else                      { idx = hi; word = "peaked";   span = peakSpan;   }
            if (span < MIN_EXTREMUM_SPAN) return null;
            return field + " " + word + " " + fmt(v.get(idx))
                    + " at reading " + (idx + 1) + " of " + n;
        }

        /**
         * Boolean fields and the values they took, reported as observed.
         *
         * <p>Deliberately phrased as presence, not cause. A reader must not be able to
         * read causality out of this string, because the data does not contain it.</p>
         */
        public List<String> coOccurrences() {
            List<String> out = new ArrayList<>();
            for (Map<String, Object> r : readings) {
                for (Map.Entry<String, Object> e : r.entrySet()) {
                    if (!(e.getValue() instanceof Boolean)) continue;
                    if (INDEX_FIELDS.contains(e.getKey().toLowerCase(Locale.ROOT))) continue;
                    out.add("observed " + e.getKey() + " = " + e.getValue());
                }
            }
            return out;
        }

        /**
         * The SHORT claim to persist, or null when there is nothing honest to say.
         *
         * <p><b>Deliberately short, and the reason is measured.</b>
         * {@code ContentSimilarity.score} is coverage x precision. A one-content-token
         * question scores 1.0 for coverage, so a fact it can retrieve must be about five
         * content tokens or fewer to clear the 0.20 floor. Verified live:</p>
         *
         * <pre>
         *   "temperature rose 20 to 22.8"                                4 tokens -> 0.250 PASS
         *   "temperature_c rose from 20 to 22.8 across 3 readings"        6 tokens -> 0.167 fail
         * </pre>
         *
         * <p>So the persisted claim is the headline trend and nothing else. The full
         * detail — every trend, every observed boolean, the source — is preserved in
         * {@link #detail}, which the watcher logs. Nothing is lost; the retrievable
         * surface is kept small enough that the current scorer can find it. The other
         * honest option is to change the scorer, and that is a separate decision.</p>
         *
         * @param source file name, recorded in the detail and in the record id
         * @return a short natural-language claim, or null when there is nothing to state
         */
        public String fact(String source) {
            List<String> all = facts();
            return all.isEmpty() ? null : all.get(0);
        }

        /**
         * ONE SHORT CLAIM PER FIELD, so every field in the stream is answerable.
         *
         * <p>This is the fix for the limitation measured in the previous sub-wave: only
         * the first trend was retrievable, because a single multi-field fact cannot fit
         * the ~5-content-token budget that {@code ContentSimilarity} imposes. Storing one
         * fact per field keeps every claim individually retrievable.</p>
         *
         * <p>Order is deterministic: trends in first-seen field order, then observed
         * booleans in first-seen order. Each element is a complete, self-contained
         * sentence and each is short enough to be found by a question naming its
         * subject.</p>
         *
         * @return one claim per field; empty when the stream has nothing to state
         */
        public List<String> facts() {
            List<String> out = new ArrayList<>();
            if (readings == null || readings.isEmpty()) return out;
            for (String t : trends()) out.add(t);
            // RECON-W32.28: extrema are facts, not decoration. On 20.0, 22.8, 20.0 there
            // is NO trend at all — the field starts and ends where it began — so before
            // this the peak was not merely under-described, it was absent from the
            // retrievable surface entirely.
            for (String e : extrema()) out.add(e);
            for (String c : distinctObservations()) out.add(c);
            return out;
        }

        /**
         * Boolean fields as STATE, not as a bag of observations.
         *
         * <p>Storing "observed fan_on = false" and "observed fan_on = true" as two facts
         * presents the operator with what looks like a contradiction. The stream is not
         * self-contradictory: the fan SWITCHED. A field that never changed is reported
         * once as "fan_on was false"; a field that changed is reported once as
         * "fan_on changed from false to true".</p>
         *
         * <p>Still co-occurrence only: "changed" describes the values, not a mechanism,
         * and never a cause.</p>
         *
         * @return one statement per boolean field, in first-seen order
         */
        public List<String> distinctObservations() {
            List<String> out = new ArrayList<>();
            for (String field : booleanFieldsInOrder()) {
                Boolean first = null, last = null;
                for (Map<String, Object> r : ordered()) {
                    Boolean b = (Boolean) r.get(field);
                    if (b == null) continue;
                    if (first == null) first = b;
                    last = b;
                }
                if (first == null) continue;
                if (first.equals(last)) {
                    out.add("observed " + field + " was " + first);
                } else {
                    out.add("observed " + field + " changed from " + first + " to " + last);
                }
            }
            return out;
        }

        /** Boolean field names, first-seen order, index fields excluded. */
        private List<String> booleanFieldsInOrder() {
            List<String> out = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Map<String, Object> r : readings) {
                for (Map.Entry<String, Object> e : r.entrySet()) {
                    if (!(e.getValue() instanceof Boolean)) continue;
                    if (INDEX_FIELDS.contains(e.getKey().toLowerCase(Locale.ROOT))) continue;
                    if (seen.add(e.getKey())) out.add(e.getKey());
                }
            }
            return out;
        }

        /**
         * The complete reading of this stream: source, every trend, every observation.
         *
         * <p>Written to the log rather than the store. The store holds the short claim
         * so retrieval can find it; the log holds the whole story so nothing is lost to
         * a length limit in a scorer.</p>
         *
         * @param source file name
         * @return the full description, or null when there is nothing to describe
         */
        public String detail(String source) {
            if (readings == null || readings.isEmpty()) return null;
            List<String> t = trends();
            List<String> c = coOccurrences();
            // RECON-W32.28: the detail log is where there is NO length budget, so it must
            // carry the extrema too. Before this, a stream reading 20.0, 22.8, 20.0 logged
            // "no numeric trend established" — which is true and also the whole story,
            // with the fact that it hit 22.8 in the middle omitted from the only place
            // where the omission was visible.
            List<String> x = extrema();
            if (t.isEmpty() && c.isEmpty() && x.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("sensor ").append(source).append(": ").append(readings.size())
              .append(" readings; ");
            if (t.isEmpty()) {
                sb.append(x.isEmpty()
                    ? "no numeric trend established"
                    : "no net numeric trend, but interior extrema were observed");
            } else {
                // Re-attach the reading count here, where there is no length budget.
                for (int i = 0; i < t.size(); i++) {
                    sb.append(t.get(i)).append(" across ").append(readings.size())
                      .append(" readings");
                    if (i < t.size() - 1) sb.append("; ");
                }
            }
            if (!x.isEmpty()) {
                sb.append("; extrema: ").append(String.join("; ", x));
            }
            if (!c.isEmpty()) {
                sb.append("; ").append(String.join("; ", distinctObservations()));
            }
            return sb.toString();
        }

        private static String fmt(double d) {
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.format(Locale.ROOT, "%.4f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
    }

    /** Readings needed before a direction means anything. Unit: readings. */
    public static final int MIN_READINGS_FOR_TREND = 2;

    /**
     * Readings needed before an interior extremum can exist at all.
     *
     * <p>RECON-W32.28. Three: with two readings neither extreme can be interior. Unit:
     * readings.</p>
     */
    public static final int MIN_READINGS_FOR_EXTREMUM = 3;

    /**
     * How far an interior extremum must depart from the endpoint it will be compared to.
     *
     * <p>RECON-W32.28. A peak 1e-9 above the final reading is the trend wearing a hat.
     * Unit: the field's own units.</p>
     */
    public static final double MIN_EXTREMUM_SPAN = 1e-9;

    /**
     * Decode a JSON-lines stream.
     *
     * @param text file contents, one JSON object per line
     * @return the decoded stream, or null when the text is not a well-formed stream
     */
    public static Stream decode(String text) {
        if (text == null || text.isBlank()) return null;
        List<Map<String, Object>> readings = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (readings.size() >= MAX_READINGS) break;
            Map<String, Object> obj = parseObject(t);
            if (obj == null) return null;          // one malformed line voids the stream
            readings.add(obj);
        }
        if (readings.isEmpty()) return null;
        return new Stream(readings);
    }

    /**
     * A deliberately small JSON-object parser.
     *
     * <p>Scoped to the flat, numeric, boolean and string objects that sensor streams
     * actually contain. A nested object or array yields null rather than a partial parse,
     * because a half-read record is indistinguishable from a real one once it is a fact.</p>
     *
     * @return the parsed object, or null when the line is not a flat JSON object
     */
    static Map<String, Object> parseObject(String s) {
        if (s.length() < 2 || s.charAt(0) != '{' || s.charAt(s.length() - 1) != '}') {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        String body = s.substring(1, s.length() - 1);
        int i = 0;
        while (i < body.length()) {
            while (i < body.length() && (Character.isWhitespace(body.charAt(i))
                    || body.charAt(i) == ',')) i++;
            if (i >= body.length()) break;
            if (body.charAt(i) != '"') return null;
            int keyEnd = body.indexOf('"', i + 1);
            if (keyEnd < 0) return null;
            String key = unescape(body.substring(i + 1, keyEnd));
            i = keyEnd + 1;
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= body.length() || body.charAt(i) != ':') return null;
            i++;
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= body.length()) return null;
            char c = body.charAt(i);
            if (c == '"') {
                int strEnd = findStringEnd(body, i);
                if (strEnd < 0) return null;
                out.put(key, unescape(body.substring(i + 1, strEnd)));
                i = strEnd + 1;
            } else if (c == '{' || c == '[') {
                return null;                        // nested: out of scope, refuse
            } else {
                int valEnd = i;
                while (valEnd < body.length() && body.charAt(valEnd) != ','
                        && !Character.isWhitespace(body.charAt(valEnd))) valEnd++;
                String raw = body.substring(i, valEnd);
                i = valEnd;
                if ("true".equals(raw)) out.put(key, Boolean.TRUE);
                else if ("false".equals(raw)) out.put(key, Boolean.FALSE);
                else if ("null".equals(raw)) out.put(key, null);
                else {
                    try {
                        out.put(key, Double.valueOf(raw));
                    } catch (NumberFormatException e) {
                        return null;                // unknown token: refuse
                    }
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static int findStringEnd(String s, int openQuote) {
        for (int j = openQuote + 1; j < s.length(); j++) {
            if (s.charAt(j) == '\\') { j++; continue; }
            if (s.charAt(j) == '"') return j;
        }
        return -1;
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        return s.replace("\\n", "\n").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Sorts readings into time order when the data supports it, and leaves them alone
     * when it does not.
     *
     * <p>RECON-W32.19. Sorting a stream with NO time field would be inventing an
     * ordering, so the fallback is file order, and {@link Stream#timeOrdered()} lets a
     * caller tell which basis a trend rests on. The trend TEXT is unchanged by this: a
     * claim about which field moved and by how much is the same either way, and the
     * ordering only decides which endpoint is the start.
     *
     * <p>Ties keep original position, so repeated timestamps give a deterministic order
     * rather than depending on a sort's stability.</p>
     */
    static final class TimeOrdered {

        /** Field names recognised as a reading time. Unit: lowercase field names. */
        static final List<String> TIME_FIELDS = List.of(
            "t", "ts", "time", "timestamp", "epoch", "seq", "index", "step");

        private TimeOrdered() {}

        static List<Map<String, Object>> sorted(List<Map<String, Object>> readings) {
            if (readings == null || readings.size() < MIN_READINGS_FOR_TREND) return readings;
            for (String field : TIME_FIELDS) {
                if (numericInEveryReading(readings, field)) return byField(readings, field);
            }
            return readings;
        }

        private static boolean numericInEveryReading(List<Map<String, Object>> rs, String f) {
            for (Map<String, Object> r : rs) {
                if (!(r.get(f) instanceof Number)) return false;
            }
            return true;
        }

        private static List<Map<String, Object>> byField(List<Map<String, Object>> rs, String f) {
            List<Map<String, Object>> out = new ArrayList<>(rs);
            out.sort(Comparator.comparingDouble(r -> ((Number) r.get(f)).doubleValue()));
            return List.copyOf(out);
        }
    }
}
