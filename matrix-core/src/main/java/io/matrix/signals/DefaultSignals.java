package io.matrix.signals;

/**
 * RUN 211 — DefaultSignals (built-in signal modules).
 *
 * <p>Provides canonical signal modules (text, audio-stub,
 * video-stub) so the registry is non-empty by default.
 */
public final class DefaultSignals {

    public static final String TEXT_NAME = "text";
    public static final String AUDIO_NAME = "audio";
    public static final String VIDEO_NAME = "video";

    private DefaultSignals() {}

    public static SignalRegistry.SignalModule textModule() {
        io.matrix.perception.TextEncoder encoder = new io.matrix.perception.TextEncoder();
        return new SignalRegistry.SignalModule() {
            @Override public String name() { return TEXT_NAME; }
            @Override public boolean[] emit(String raw) {
                return encoder.encode(raw);
            }
        };
    }

    public static SignalRegistry.SignalModule audioModule() {
        return new SignalRegistry.SignalModule() {
            @Override public String name() { return AUDIO_NAME; }
            @Override public boolean[] emit(String raw) {
                byte[] payload = raw == null ? new byte[0]
                        : raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try {
                    byte[] hash = java.security.MessageDigest
                            .getInstance("SHA-256").digest(payload);
                    boolean[] out = new boolean[256];
                    for (int i = 0; i < out.length; i++) {
                        out[i] = ((hash[i / 8] >> (7 - (i % 8))) & 1) == 1;
                    }
                    return out;
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    public static SignalRegistry.SignalModule videoModule() {
        return new SignalRegistry.SignalModule() {
            @Override public String name() { return VIDEO_NAME; }
            @Override public boolean[] emit(String raw) {
                byte[] payload = raw == null ? new byte[0]
                        : raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try {
                    byte[] hash = java.security.MessageDigest
                            .getInstance("SHA-256").digest(payload);
                    boolean[] out = new boolean[256];
                    for (int i = 0; i < out.length; i++) {
                        int shifted = i % 256;
                        int byteIdx = ((shifted + 7) / 8) % hash.length;
                        int bitIdx = 7 - (shifted % 8);
                        out[i] = ((hash[byteIdx] >> bitIdx) & 1) == 1;
                    }
                    return out;
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    /** Build a registry pre-populated with all defaults. */
    public static SignalRegistry defaultRegistry() {
        SignalRegistry r = new SignalRegistry();
        r.register(textModule());
        r.register(audioModule());
        r.register(videoModule());
        return r;
    }
}
