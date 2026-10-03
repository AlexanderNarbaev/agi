package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
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

    // ---- the resulting fact must be worth storing ---------------------------

    @Test
    void theRenderedFactIsCurrentlyNOTRetrievableByAGenericQuestionAndThatIsRecorded() {
        // MEASURED LIMITATION, asserted so it cannot be quietly forgotten.
        //
        // ContentSimilarity.score is coverage x precision. A one-content-token question
        // scores 1/1 for coverage, so a fact retrievable by it must be about 5 content
        // tokens or fewer to clear the 0.20 floor. A truthful trend sentence —
        // "temperature_c rose from 20.0 to 22.8 across 3 readings" — is about 9, because
        // "20.0" tokenises to two tokens and so does "22.8".
        //
        // So a sensor trend is NOT answerable by "What is the temperature?" today. The
        // honest options are to change the scoring function or to emit a much shorter
        // fact, and both belong to a later sub-wave. What matters here is that the
        // limitation is measured and named rather than papered over by rewording the
        // fact until it happens to match.
        SensorStreamDecoder.Stream s = SensorStreamDecoder.decode(ROOM);
        String fact = s.fact("room-sensor.jsonl");
        assertNotNull(fact);
        double score = ContentSimilarity.score("What is the temperature?", fact);
        int factTokens = ContentSimilarity.contentTokens(fact).size();
        assertTrue(factTokens > 5,
            "precondition: the fact is longer than the ~5-token retrievable budget");
        assertTrue(score < ContentSimilarity.RETRIEVAL_FLOOR,
            "the limitation is recorded as a measurement, not a hope: fact has "
                + factTokens + " content tokens and scores " + score);
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
}
