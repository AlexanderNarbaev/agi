package io.matrix.signals;

/**
 * SignalModule contract (DESIGN-06 §2): modular input/output signal processing.
 *
 * <p>Each module handles one modality (text, image, audio, sensor, actuator).
 * Modules are versioned, registered in a central registry, and produce
 * 64-bit signal vectors for the brain pipeline.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #modality()} — modality identifier ("text", "image", "audio")</li>
 *   <li>{@link #version()} — semantic version for compatibility</li>
 *   <li>{@link #encode(Object)} — input → 64-bit signal vector</li>
 *   <li>{@link #decode(long[])} — 64-bit signal vector → output</li>
 *   <li>{@link #validate()} — structural check (bit count, ranges)</li>
 * </ul>
 */
public interface SignalModule {

    /** Modality identifier: "text", "image", "audio", "sensor", "actuator". */
    String modality();

    /** Semantic version: "1.0.0". */
    String version();

    /** Encode raw input into 64-bit signal vector. */
    long[] encode(Object input);

    /** Decode 64-bit signal vector into output. */
    Object decode(long[] signal);

    /** Validate module structure (bit count, ranges). */
    default boolean validate() {
        return true;
    }

    /** Module metadata. */
    default ModuleInfo info() {
        return new ModuleInfo(modality(), version(), getClass().getSimpleName());
    }

    record ModuleInfo(String modality, String version, String className) {}
}
