package io.matrix.omni;

import java.util.*;

/**
 * W1001 — Omni-Modal Gateway.
 *
 * Pluggable architecture where each modality has a DistilledEncoder
 * (to HDC) and GenerativeDecoder (from BIR/HDC).
 *
 * Supports ANY input (Text, Audio, Video, Code, Sensor Data)
 * and produces ANY output (Text, Image, Video, Audio, Robot Action, Code).
 *
 * CONSTITUTION I: No LLM in runtime — all heavy lifting uses
 * distilled ONNX models or local APIs.
 */
public final class OmniModalGateway {

    /**
     * Supported modalities.
     */
    public enum Modality {
        TEXT, AUDIO, IMAGE, VIDEO, CODE, SENSOR, ROBOT_ACTION
    }

    /**
     * A multi-modal message.
     */
    public record OmniMessage(
            Modality modality,
            byte[] data,
            String contentType,
            Map<String, String> metadata,
            long timestamp
    ) {}

    /**
     * Encoder: converts modality → HDC vector representation.
     */
    public interface DistilledEncoder {
        Modality supportedModality();
        boolean[] encode(OmniMessage message);
        long getLatencyMs();
    }

    /**
     * Decoder: converts BIR/HDC output → modality output.
     */
    public interface GenerativeDecoder {
        Modality supportedModality();
        OmniMessage decode(boolean[] hdcVector, String contentType);
        long getLatencyMs();
    }

    private final Map<Modality, DistilledEncoder> encoders = new HashMap<>();
    private final Map<Modality, GenerativeDecoder> decoders = new HashMap<>();
    private final List<OmniMessage> history = new ArrayList<>();

    /**
     * Register an encoder for a modality.
     */
    public void registerEncoder(DistilledEncoder encoder) {
        encoders.put(encoder.supportedModality(), encoder);
    }

    /**
     * Register a decoder for a modality.
     */
    public void registerDecoder(GenerativeDecoder decoder) {
        decoders.put(decoder.supportedModality(), decoder);
    }

    /**
     * Process an input message: encode to HDC vector.
     * Returns null if no encoder registered for the modality.
     */
    public boolean[] processInput(OmniMessage message) {
        DistilledEncoder encoder = encoders.get(message.modality());
        if (encoder == null) return null;

        boolean[] hdc = encoder.encode(message);
        history.add(message);
        return hdc;
    }

    /**
     * Generate an output from an HDC vector.
     * Returns null if no decoder registered for the modality.
     */
    public OmniMessage generateOutput(Modality modality, boolean[] hdcVector, String contentType) {
        GenerativeDecoder decoder = decoders.get(modality);
        if (decoder == null) return null;
        return decoder.decode(hdcVector, contentType);
    }

    /**
     * Get message history.
     */
    public List<OmniMessage> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Check if a modality is supported for input.
     */
    public boolean isInputSupported(Modality modality) {
        return encoders.containsKey(modality);
    }

    /**
     * Check if a modality is supported for output.
     */
    public boolean isOutputSupported(Modality modality) {
        return decoders.containsKey(modality);
    }

    public Set<Modality> getSupportedInputModalities() {
        return encoders.keySet();
    }

    public Set<Modality> getSupportedOutputModalities() {
        return decoders.keySet();
    }
}
