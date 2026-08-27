package io.matrix.signals;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W-F tests for the perception pipeline (SPEC-004, DESIGN-16): the
 * {@link SensorPacket} record, the {@link FederatedEncoder} dispatch, and
 * the encode → decode round-trip on synthetic data for every modality
 * already registered in {@link SignalModuleRegistry}.
 */
class PerceptionPipelineTest {

    @Test
    void sensorPacketRejectsNullModality() {
        assertThatThrownBy(() -> new SensorPacket(Instant.now(), null, "x", false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sensorPacketRejectsBlankModality() {
        assertThatThrownBy(() -> new SensorPacket(Instant.now(), "  ", "x", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeDecodeRoundTripAcrossModalities() {
        SignalModuleRegistry registry = new SignalModuleRegistry();
        registry.register(new TextSignalModule());
        registry.register(new ImageSignalModule());
        registry.register(new AudioSignalModule());
        FederatedEncoder encoder = new FederatedEncoder.DefaultFederatedEncoder(registry);

        // text → encode → decode → hex string (lossy, but documented)
        SensorPacket text = SensorPacket.of("text", "hello world");
        SensorPacket.Encoded textEnc = encoder.encode(text);
        assertThat(textEnc.signal()).hasSize(1);
        String decodedText = (String) new TextSignalModule().decode(textEnc.signal());
        assertThat(decodedText).isNotBlank();

        // image → encode → decode round-trip on synthetic payload
        // (ImageSignalModule only decodes real PNG/JPEG; we use a small
        // byte array so the fallback hash path runs and signal length = 1)
        SensorPacket image = SensorPacket.of("image", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        SensorPacket.Encoded imageEnc = encoder.encode(image);
        assertThat(imageEnc.signal()).hasSize(1);
        Object imageOut = new ImageSignalModule().decode(imageEnc.signal());
        assertThat(imageOut).isNotNull();

        // audio → encode → decode
        SensorPacket audio = SensorPacket.of("audio", new double[]{0.1, 0.2, 0.3});
        SensorPacket.Encoded audioEnc = encoder.encode(audio);
        assertThat(audioEnc.signal()).hasSize(1);
        Object audioOut = new AudioSignalModule().decode(audioEnc.signal());
        assertThat(audioOut).isNotNull();

        // metadata is preserved across encode
        assertThat(textEnc.modality()).isEqualTo("text");
        assertThat(imageEnc.modality()).isEqualTo("image");
        assertThat(audioEnc.modality()).isEqualTo("audio");
        assertThat(textEnc.kAnonymous()).isFalse();
    }

    @Test
    void encodePropagatesKAnonymousFlag() {
        SignalModuleRegistry registry = new SignalModuleRegistry();
        registry.register(new TextSignalModule());
        FederatedEncoder encoder = new FederatedEncoder.DefaultFederatedEncoder(registry);

        SensorPacket anon = SensorPacket.anonymous("text", "private data");
        SensorPacket.Encoded enc = encoder.encode(anon);
        assertThat(enc.kAnonymous()).isTrue();
    }

    @Test
    void encodeRefusesModalityMismatch() {
        SensorPacket text = SensorPacket.of("text", "hello");
        assertThatThrownBy(() -> text.encodeWith(new ImageSignalModule()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modality mismatch");
    }

    @Test
    void federatedEncoderRejectsUnknownModality() {
        SignalModuleRegistry empty = new SignalModuleRegistry();
        FederatedEncoder encoder = new FederatedEncoder.DefaultFederatedEncoder(empty);
        SensorPacket unknown = SensorPacket.of("smell", "data");
        assertThatThrownBy(() -> encoder.encode(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no module registered");
    }

    @Test
    void encodedSignalIsDefensivelyCopied() {
        SignalModuleRegistry registry = new SignalModuleRegistry();
        registry.register(new TextSignalModule());
        FederatedEncoder encoder = new FederatedEncoder.DefaultFederatedEncoder(registry);

        SensorPacket text = SensorPacket.of("text", "alpha");
        SensorPacket.Encoded enc = encoder.encode(text);
        long[] firstRead = enc.signal();
        long[] secondRead = enc.signal();
        // defensive copy: same contents, distinct arrays
        assertThat(firstRead).isEqualTo(secondRead);
        assertThat(firstRead).isNotSameAs(secondRead);
    }
}