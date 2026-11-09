package io.matrix.contract;

import java.util.List;
import java.util.Map;

/**
 * W51 — what arrived, before anybody interpreted it.
 *
 * <p><b>The modality boundary.</b> An observation says what was received and where it came
 * from. It does not say what it means. A PNG header and a JSON document are both
 * observations; what they mean is a separate act, and a system that fuses the two cannot say
 * afterwards whether it perceived something or assumed something.</p>
 *
 * <p><b>Text is one modality, not the modality.</b> The {@link Modality} says how the payload
 * arrived, and the payload is a typed map, not a string. A caller that has structured data
 * hands it over as structured data; a caller that has text hands it over as text. The
 * canonical representation does not require the second to be degraded into the first.</p>
 *
 * <p><b>The raw payload is kept.</b> {@link #rawPayload()} is what the sensor produced, before
 * any extraction, so that a later reader can check whether an interpretation was faithful.
 * A record that keeps only the interpretation cannot be audited against its source.</p>
 *
 * <p><b>Nothing here is true yet.</b> An observation is a fact about the world at an instant —
 * "this is what arrived". What the observation implies is a separate claim with its own
 * evidence, and the epistemic layer is where that separation lives. This is the distinction
 * the W50 audit needed and did not have: an imported sentence was a candidate claim, while the
 * document it came from was the source.</p>
 *
 * @param id          the identity of the observation
 * @param modality    how the payload arrived
 * @param payload     the typed content of the observation
 * @param rawPayload  the payload as it was received, before interpretation
 * @param observedAt  when the observation was made
 * @param provenance  where it came from and who is responsible for it
 * @param correlation the causality metadata of the request that produced it
 * @param labels      the tags the perception layer assigned, kept separate from the payload
 */
public record Observation(
        String id,
        Modality modality,
        Map<String, Object> payload,
        Map<String, Object> rawPayload,
        String observedAt,
        Provenance provenance,
        Correlation correlation,
        List<String> labels) {

    /** How an observation arrived. */
    public enum Modality {
        /** Free text in a human language. */
        TEXT,
        /** A structured record. */
        STRUCTURED,
        /** A binary frame. */
        BINARY,
        /** A tabular stream of records. */
        TABULAR,
        /** A numeric time series. */
        TIME_SERIES,
        /** A measurement of a physical quantity. */
        SENSOR,
        /** A record produced by a simulated environment. */
        SIMULATION,
        /** A record produced by a tool. */
        TOOL,
        /** Anything that does not match a known modality. */
        UNKNOWN
    }

    public Observation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("observation id must not be blank");
        }
        if (modality == null) {
            throw new IllegalArgumentException("observation modality must not be null");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        rawPayload = rawPayload == null ? Map.of() : Map.copyOf(rawPayload);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /**
     * An observation of text.
     *
     * @param id         the identity of the observation
     * @param text       the text
     * @param observedAt when the observation was made
     * @param provenance where it came from
     * @return the observation
     */
    public static Observation ofText(String id, String text, String observedAt,
                                     Provenance provenance) {
        return new Observation(id, Modality.TEXT, Map.of("text", text == null ? "" : text),
                Map.of("text", text == null ? "" : text), observedAt, provenance,
                null, List.of());
    }

    /**
     * An observation of a structured record.
     *
     * @param id         the identity of the observation
     * @param payload    the structured content
     * @param observedAt when the observation was made
     * @param provenance where it came from
     * @return the observation
     */
    public static Observation ofStructured(String id, Map<String, Object> payload, String observedAt,
                                           Provenance provenance) {
        return new Observation(id, Modality.STRUCTURED, payload, payload, observedAt, provenance,
                null, List.of());
    }

    /** True when the observation names where it came from. */
    public boolean hasProvenance() {
        return provenance != null && provenance.hasSource();
    }

    @Override
    public String toString() {
        return "Observation[" + id + " " + modality + " labels=" + labels + "]";
    }
}
