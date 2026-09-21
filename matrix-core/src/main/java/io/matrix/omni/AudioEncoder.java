package io.matrix.omni;

import java.util.*;

/**
 * W1002 — Audio Encoder (Whisper-Tiny ONNX integration).
 *
 * Converts audio spectrograms to HDC vectors for the core brain.
 * Uses distilled Whisper-Tiny for offline STT.
 *
 * CONSTITUTION I: No LLM in runtime — uses distilled ONNX model.
 */
public final class AudioEncoder implements OmniModalGateway.DistilledEncoder {

    private final Random rng;
    private final int dimension;
    private long lastLatencyMs = 0;

    public AudioEncoder(int dimension, long seed) {
        this.dimension = dimension;
        this.rng = new Random(seed);
    }

    @Override
    public OmniModalGateway.Modality supportedModality() {
        return OmniModalGateway.Modality.AUDIO;
    }

    /**
     * Encode audio bytes to HDC vector.
     * In production: Whisper-Tiny → log-mel spectrogram → HDC binding.
     * Here: simplified hash-based encoding for testing.
     */
    @Override
    public boolean[] encode(OmniModalGateway.OmniMessage message) {
        long start = System.currentTimeMillis();

        boolean[] hdc = new boolean[dimension];
        byte[] data = message.data();

        if (data == null || data.length == 0) {
            // Empty audio → zero vector
            lastLatencyMs = System.currentTimeMillis() - start;
            return hdc;
        }

        // Hash-based encoding: each byte contributes to the HDC vector
        for (int i = 0; i < dimension; i++) {
            int hash = 0;
            for (int j = 0; j < data.length && j < 100; j++) {
                hash = hash * 31 + (data[j] & 0xFF) + i;
            }
            hdc[i] = (hash & 1) == 1;
        }

        lastLatencyMs = System.currentTimeMillis() - start;
        return hdc;
    }

    @Override
    public long getLatencyMs() {
        return lastLatencyMs;
    }
}

/**
 * Text-to-Speech decoder (VALL-E-X distilled).
 */
final class TextToSpeechDecoder implements OmniModalGateway.GenerativeDecoder {

    private final Random rng;
    private long lastLatencyMs = 0;

    public TextToSpeechDecoder(long seed) {
        this.rng = new Random(seed);
    }

    @Override
    public OmniModalGateway.Modality supportedModality() {
        return OmniModalGateway.Modality.AUDIO;
    }

    @Override
    public OmniModalGateway.OmniMessage decode(boolean[] hdcVector, String contentType) {
        long start = System.currentTimeMillis();

        // In production: VALL-E-X → waveform
        // Here: generate placeholder audio bytes
        byte[] audio = new byte[16000 * 2]; // 1 sec at 16kHz mono
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (byte) (rng.nextInt(256) - 128);
        }

        lastLatencyMs = System.currentTimeMillis() - start;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("sample_rate", "16000");
        metadata.put("channels", "1");
        metadata.put("format", "wav");

        return new OmniModalGateway.OmniMessage(
            OmniModalGateway.Modality.AUDIO,
            audio,
            "audio/wav",
            metadata,
            System.currentTimeMillis()
        );
    }

    @Override
    public long getLatencyMs() {
        return lastLatencyMs;
    }
}
