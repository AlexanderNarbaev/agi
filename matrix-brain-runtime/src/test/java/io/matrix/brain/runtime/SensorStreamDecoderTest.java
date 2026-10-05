package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W32.3 — sensor streams become nameable facts.
 *
 * <p><b>Why.</b> A JSON-lines sensor stream used to be ingested as an opaque blob: the
 * store received the literal file text, so {@code "What is the temperature?"} had nothing
 * to match and refused. Before W32.1 it was worse — it fell through to a SHA-256
 * fingerprint, so the temperature existed only as a hash of a file containing it.</p>
 *
 * <p>What is produced here is a TREND, not a transcription: a file containing three
 * readings becomes a statement about direction and extent. That is the difference between
 * a mind that has read a file and one that has observed a change.</p>
 *
 * <p><b>Honesty rules this class enforces.</b> A stream with fewer than two readings has
 * no trend and must not be given one. A field that is not consistently numeric is not
 * described as rising or falling. And nothing here claims causality: a co-occurring
 * boolean is reported as co-occurring, never as a cause, because the data cannot support
 * it. That distinction is the whole point of the {@link #coOccurrences} accessor being
 * separate from {@link #trends()}.</p>
 *
 * <p>Article III: pure functions of the input text, no clock, no randomness, no network.</p>
 */
class SensorStreamDecoderTest {

    /** Minimal JSON-lines stream: three temperature readings, fan off then on. */
    private static final String ROOM = """
        {"t":0,"temperature_c":20.0,"humidity_pct":45,"fan_on":false}
        {"t":1,"temperature_c":21.4,"humidity_pct":46,"fan_on":true}
        {"t":2,"temperature_c":22.8,"humidity_pct":47,"fan_on":true}
        """;

    // ---- parsing -----------------------------------------------------------

    @Test
    void parsesEveryReadingInAStream() {
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        assertNotNull(s, "valid JSON-lines must decode");
        assertEquals(3, s.readings().size());
        assertEquals(20.0, s.numeric("temperature_c").get(0), 1e-9);
        assertEquals(22.8, s.numeric("temperature_c").get(2), 1e-9);
    }

    @Test
    void refusesMalformedJsonLines() {
        assertNull(SensorStreamDecoder.decode("{\"t\":0,\"temperature_c\":20.0}\n{oops}"),
            "a malformed line must be refused, not half-parsed");
        assertNull(SensorStreamDecoder.decode("this is not json at all"),
            "non-JSON must be refused");
        assertNull(SensorStreamDecoder.decode(""),
            "an empty stream must be refused");
    }

    @Test
    void refusesAStreamWithNoNumericField() {
        // Everything textual is not a measurement, and describing it as one would be the
        // W32 defect wearing a new hat.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(
            "{\"label\":\"kitchen\",\"note\":\"door open\"}\n{\"label\":\"kitchen\"}");
        assertNotNull(s, "well-formed JSON is still parseable");
        assertTrue(s.trends().isEmpty(),
            "no numeric field means no trend, got " + s.trends());
    }

    // ---- trends ------------------------------------------------------------

    @Test
    void namesTheTrendOfAFieldThatRose() {
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        // 20.0 is rendered as "20": a whole number is not printed with a bare decimal.
        // Asserting the source spelling would test the formatter, not the trend.
        assertTrue(s.trends().stream().anyMatch(t -> t.contains("temperature_c")
                && t.contains("rose") && t.contains("20 ") && t.contains("22.8")),
            "expected a rising temperature trend, got " + s.trends());
    }

    @Test
    void namesTheTrendOfAFieldThatFell() {
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(
            "{\"t\":0,\"pressure_hpa\":1013}\n{\"t\":1,\"pressure_hpa\":1010}"
                + "\n{\"t\":2,\"pressure_hpa\":995}\n");
        assertTrue(s.trends().stream().anyMatch(t -> t.contains("pressure_hpa")
                && t.contains("fell") && t.contains("1013") && t.contains("995")),
            "expected a falling pressure trend, got " + s.trends());
    }

    @Test
    void aFieldMissingFromAnyReadingHasNoTrendRatherThanAPartialOne() {
        // Corrected while writing this suite: my first fixture omitted pressure_hpa from
        // the first reading and expected a trend anyway. The rule is right — a field that
        // vanishes from one reading has an UNKNOWN direction, not a direction. The
        // fixture was wrong, so the fixture was fixed and the rule kept.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(
            "{\"p\":0}\n{\"pressure_hpa\":1013}\n{\"pressure_hpa\":995}\n");
        assertTrue(s.trends().isEmpty(),
            "a partially observed field must not be described as moving, got " + s.trends());
    }

    @Test
    void aReadingIndexIsNotReportedAsAMeasurement() {
        // "t rose from 0 to 2" is true and about nothing; it would be the headline of
        // every sensor fact.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        assertTrue(s.trends().stream().noneMatch(t -> t.startsWith("t ")),
            "the index field must not appear as a trend, got " + s.trends());
    }

    @Test
    void aSingleReadingHasNoTrend() {
        // One reading cannot establish a direction. Reporting "rose" from a single point
        // is exactly the kind of confident claim from nothing that this campaign is about.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(
            "{\"t\":0,\"temperature_c\":20.0}");
        assertNotNull(s);
        assertTrue(s.trends().isEmpty(), "a single reading has no trend, got " + s.trends());
    }

    @Test
    void aConstantFieldIsReportedAsStableNotAsRising() {
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(
            "{\"t\":0,\"humidity_pct\":45}\n{\"t\":1,\"humidity_pct\":45}");
        assertTrue(s.trends().isEmpty(),
            "a constant field has no direction, got " + s.trends());
    }

    // ---- co-occurrence is never causality -----------------------------------

    @Test
    void reportsCoOccurrenceWithoutClaimingCause() {
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        assertTrue(s.coOccurrences().stream().anyMatch(c -> c.contains("fan_on")),
            "the boolean field must be reported, got " + s.coOccurrences());
        for (String c : s.coOccurrences()) {
            assertFalse(c.toLowerCase().contains("caused")
                    || c.toLowerCase().contains("because")
                    || c.toLowerCase().contains("due to"),
                "co-occurrence must not be phrased as cause: " + c);
        }
    }

    @Test
    void aBooleanThatChangesIsReportedAsAChangeNotAsTwoContradictoryFacts() {
        // "observed fan_on = false" beside "observed fan_on = true" looks to an operator
        // like a self-contradictory store. The stream is not contradictory: the fan
        // switched, and that is one fact.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        List<String> obs = s.distinctObservations();
        assertEquals(1, obs.size(), "one statement per boolean field, got " + obs);
        assertTrue(obs.get(0).contains("fan_on") && obs.get(0).contains("changed"),
            "a switched field must be reported as changing, got " + obs);
    }

    @Test
    void aConstantBooleanIsReportedOnceWithNoChange() {
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(
            "{\"t\":0,\"pump_on\":true}\n{\"t\":1,\"pump_on\":true}");
        List<String> obs = s.distinctObservations();
        assertEquals(1, obs.size(), obs.toString());
        assertTrue(obs.get(0).contains("pump_on") && obs.get(0).contains("was true")
                && !obs.get(0).contains("changed"), obs.toString());
    }

    // ---- the resulting fact must be worth storing ---------------------------

    @Test
    void everyFieldInTheStreamGetsItsOwnRetrievableFact() {
        // The limitation fixed in this sub-wave: one multi-field fact cannot fit the
        // ~5-token budget, so only the FIRST trend was answerable. One fact per field
        // makes every field answerable.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        List<String> facts = s.facts();
        assertTrue(facts.stream().anyMatch(f -> f.contains("temperature_c")), facts.toString());
        assertTrue(facts.stream().anyMatch(f -> f.contains("humidity_pct")), facts.toString());
        assertTrue(facts.stream().anyMatch(f -> f.contains("fan_on")), facts.toString());
        for (String f : facts) {
            assertTrue(ContentSimilarity.contentTokens(f).size() <= 6,
                "each claim must fit the retrievable budget, got " + f);
        }
    }

    @Test
    void everyFieldClaimIsRetrievableByAQuestionNamingIt() {
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        assertTrue(ContentSimilarity.score("What is the temperature?", s.fact("room.jsonl"))
                   >= ContentSimilarity.RETRIEVAL_FLOOR, "temperature must be answerable");
        String humidity = s.facts().stream()
            .filter(f -> f.contains("humidity_pct")).findFirst().orElse(null);
        assertNotNull(humidity);
        assertTrue(ContentSimilarity.score("What is the humidity?", humidity)
                   >= ContentSimilarity.RETRIEVAL_FLOOR,
            "humidity must be answerable too, scored "
                + ContentSimilarity.score("What is the humidity?", humidity));
    }

    @Test
    void theRenderedFactIsRetrievableByAGenericQuestionBecauseItIsKeptShort() {
        // MEASURED LIMITATION, asserted so it cannot be quietly forgotten.
        //
        // ContentSimilarity.score is coverage x precision. A one-content-token question
        // scores 1.0 for coverage, so a fact it can retrieve must be about five content
        // tokens or fewer to clear the 0.20 floor. Measured live:
        //
        //   "temperature rose 20 to 22.8"                          4 tokens -> 0.250 PASS
        //   "temperature_c rose from 20 to 22.8 across 3 readings"  6 tokens -> 0.167 fail
        //
        // So the persisted claim is the headline trend ONLY, and the reading count, the
        // source and the remaining trends live in detail(), which the log keeps. Nothing
        // is dropped; the retrievable surface is simply kept inside the budget the
        // current scorer imposes.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        String fact = s.fact("room-sensor.jsonl");
        assertNotNull(fact);
        double score = ContentSimilarity.score("What is the temperature?", fact);
        int factTokens = ContentSimilarity.contentTokens(fact).size();
        assertTrue(factTokens <= 5,
            "the claim must fit the ~5-token retrievable budget, has " + factTokens);
        assertTrue(score >= ContentSimilarity.RETRIEVAL_FLOOR,
            "and must therefore be retrievable, scored " + score);
    }

    @Test
    void theLongFormIsPreservedInTheDetailEvenThoughTheFactIsShort() {
        // Nothing is lost to the length budget: the full reading including every trend,
        // every observed boolean and the source is still available.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        String fact = s.fact("room-sensor.jsonl");
        String detail = s.detail("room-sensor.jsonl");
        assertNotNull(detail);
        assertTrue(detail.contains("room-sensor.jsonl"), detail);
        assertTrue(detail.contains("across 3 readings"), detail);
        assertTrue(detail.contains("humidity_pct"), detail);
        assertTrue(detail.contains("fan_on"), detail);
        assertTrue(fact.length() < detail.length(),
            "the claim must be the shorter of the two");
    }

    @Test
    void aRefusedStreamRendersNoFact() {
        assertNull(SensorStreamDecoder.decode("not json"),
            "non-JSON must be refused outright");
        SensorStreamDecoder.Stream empty =
            new SensorStreamDecoder.Stream(java.util.List.of());
        assertNull(empty.fact("empty.jsonl"),
            "a stream with nothing to say must not produce a fact");
    }

    // ---- integration: the watcher must route .jsonl here ------------------

    @Test
    void theWatcherPerceivesASensorStreamAsATrend(@TempDir Path dir) throws Exception {
        Path inbox = dir.resolve("inbox");
        Files.createDirectories(inbox);
        Files.write(inbox.resolve("room.jsonl"), ROOM.getBytes(StandardCharsets.UTF_8));

        PersistentHdcStore store = new PersistentHdcStore(dir.resolve("kb.ndjson"), 256);
        RealInboxWatcher w = new RealInboxWatcher(inbox, store);
        assertEquals(1, w.scan());
        String content = store.snapshot().values().iterator().next();
        assertTrue(content.contains("temperature_c"),
            "the stream must become a semantic fact, got: " + content);
        assertFalse(content.startsWith("inbox:room.jsonl {"),
            "the raw blob must not be what gets stored");
    }

    // ---- RECON-W32.19: a trend needs an ordering, and file order is not one ----

    @Test
    void theSameReadingsGiveTheSameTrendWhateverTheirFileOrder() {
        // The measured defect: 20.0, 22.8, 21.0 delivered in two different orders
        // produced "rose 20 to 21" and "rose 20 to 22.8" — two claims from one physical
        // event, no error anywhere. Ordering by the time field is what makes the
        // statement a property of the DATA rather than of the file.
        String a = "{\"t\":0,\"temperature_c\":20.0}\n"
                 + "{\"t\":1,\"temperature_c\":22.8}\n"
                 + "{\"t\":2,\"temperature_c\":21.0}";
        String b = "{\"t\":0,\"temperature_c\":20.0}\n"
                 + "{\"t\":2,\"temperature_c\":21.0}\n"
                 + "{\"t\":1,\"temperature_c\":22.8}";
        org.junit.jupiter.api.Assertions.assertEquals(
            SensorStreamDecoder.decode(a).facts(),
            SensorStreamDecoder.decode(b).facts(),
            "the trend must depend on the readings, not on the order they were written in");
    }

    @Test
    void aStreamWithoutATimeFieldIsLeftInFileOrderAndSaysSo() {
        // Sorting a stream with no time field would be inventing an ordering. The
        // fallback is file order, and timeOrdered() reports which basis was used.
        var s = SensorStreamDecoder.decode(
            "{\"temperature_c\":20.0}\n{\"temperature_c\":22.0}");
        assertFalse(s.timeOrdered(),
            "no time field means the ordering is line order, and that must be visible");
        assertNotNull(s.ordered());
    }

    @Test
    void aStreamWithATimeFieldIsReportedAsTimeOrdered() {
        var s = SensorStreamDecoder.decode(
            "{\"t\":0,\"temperature_c\":20.0}\n{\"t\":1,\"temperature_c\":22.0}");
        assertTrue(s.timeOrdered(), "a numeric t in every reading is a real ordering");
    }

    @Test
    void aFirstToLastTrendCannotExpressAPeakAndThatIsStatedNotHidden() {
        // 20.0 -> 22.8 -> 21.0 peaks in the middle. A first-to-last comparison can only
        // say "rose 20 to 21", which is TRUE and INCOMPLETE.
        //
        // RECON-W32.28: this test's second assertion FLIPPED, and that is the point. It
        // used to assert the 22.8 peak was NOT represented, recording the incompleteness
        // so it could not be mistaken for a full description. W32.28 fixed the
        // incompleteness, so the honest claim is no longer "this is endpoint-only" but
        // "the endpoint part is still endpoint-only, and the peak is now stated
        // separately rather than being left out".
        //
        // It was UPDATED rather than deleted, and the flip is the evidence that the
        // original limitation was real: a test that documented an incompleteness should
        // break when the incompleteness is closed.
        var s = SensorStreamDecoder.decode(
            "{\"t\":0,\"temperature_c\":20.0}\n"
            + "{\"t\":1,\"temperature_c\":22.8}\n"
            + "{\"t\":2,\"temperature_c\":21.0}");
        String claim = String.join("; ", s.facts());
        assertTrue(claim.contains("rose 20 to 21"),
            "the endpoint trend is still endpoint-based and must still be stated: " + claim);
        assertTrue(claim.contains("peaked 22.8"),
            "and since W32.28 the peak must be represented too: " + claim);
        assertTrue(s.trends().get(0).contains("rose 20 to 21"),
            "precondition: the TREND alone is still endpoint-only — extrema are a separate "
                + "claim, and must not be folded into the trend string: " + s.trends());
    }

    // ---- RECON-W32.28 / SENSOR-1: extrema, not just first-to-last ----------
    //
    // A trend is first-to-last, so on 20.0, 22.8, 20.0 there is NO trend: the field
    // starts and ends where it began. Before this the peak was not under-described, it
    // was absent from the retrievable surface entirely, and no threshold could recover
    // it from a pair of endpoints.

    private static String ts(double... v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            sb.append("{\"ts\":").append(i).append(",\"temperature_c\":").append(v[i]).append("}\n");
        }
        return sb.toString();
    }

    @Test
    void aPeakWithNoNetTrendIsStillStored() {
        var st = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 22.8, 20.0));
        assertTrue(st.trends().isEmpty(),
            "precondition: 20 -> 22.8 -> 20 establishes NO first-to-last trend");
        List<String> x = st.extrema();
        assertEquals(1, x.size(), "but the peak is a real observation: " + x);
        assertTrue(x.get(0).contains("peaked 22.8"), "got: " + x.get(0));
        assertTrue(x.get(0).contains("reading 2 of 3"),
            "and it must be checkable against the data, not merely asserted: " + x.get(0));
    }

    @Test
    void anExtremaClaimIsRetrievable() {
        // The claim has to survive the scorer's ~5-content-token budget and the W32.25
        // evidence rule, or it exists and cannot be found - which is the same as not
        // existing as far as an operator is concerned.
        var st = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 22.8, 20.0));
        String claim = st.extrema().get(0);
        assertTrue(ContentSimilarity.isConfident("temperature peaked", claim),
            "the claim must be findable by a question naming its subject: " + claim);
    }

    @Test
    void anExtremeAtAnEndpointIsNotReported() {
        // It would add nothing to the trend and facts() is a one-fact-per-field budget.
        // "peaked 23" alongside "rose 20 to 23" is the trend wearing a hat.
        var st = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 22.8, 23.0));
        assertEquals(1, st.trends().size(), "precondition: there is a real trend");
        assertTrue(st.extrema().isEmpty(),
            "a maximum at the last reading must not be restated as a peak: " + st.extrema());
    }

    @Test
    void aDeepInteriorDipIsReportedEvenWhenTheTrendIsUp() {
        // The asymmetry that a first-to-last pair cannot express: the field ended higher
        // than it started, and still spent most of the window far below.
        var st = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 17.0, 21.0));
        assertTrue(st.trends().get(0).contains("rose"), "precondition: net trend is up");
        assertTrue(st.extrema().get(0).contains("bottomed 17"),
            "and the dip must still be stated: " + st.extrema());
    }

    @Test
    void aConstantFieldHasNoExtrema() {
        var st = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 20.0, 20.0));
        assertTrue(st.extrema().isEmpty(), "a flat field has nothing to report: " + st.extrema());
        assertTrue(st.trends().isEmpty(), "and no trend either");
    }

    @Test
    void twoReadingsCannotHaveAnInteriorExtreme() {
        var st = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 22.8));
        assertTrue(st.extrema().isEmpty(),
            "with two readings neither extreme can be interior, so claiming one would be "
                + "an invention: " + st.extrema());
    }

    @Test
    void theDetailLogNoLongerSaysNoTrendWhenAPeakExists() {
        // The log is the one place with no length budget, so it is the one place where an
        // omission is actually visible to an operator reading the log.
        var st = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 22.8, 20.0));
        String d = st.detail("rose.jsonl");
        assertNotNull(d, "there is something to say");
        assertFalse(d.contains("no numeric trend established"),
            "that string is true but incomplete, and the peak was the whole story: " + d);
        assertTrue(d.contains("22.8"), "the extremum must appear in the detail: " + d);
    }

    @Test
    void extremaIsDeterministicForTheSameInput() {
        // Article II: no Random in a decision path, and a claim that varies run to run
        // cannot be checked against anything.
        var a = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 22.8, 21.0, 19.0, 20.5));
        var b = io.matrix.brain.runtime.SensorStreamDecoder.decode(ts(20.0, 22.8, 21.0, 19.0, 20.5));
        assertEquals(a.extrema(), b.extrema(), "the same input must give the same claim");
    }
}
