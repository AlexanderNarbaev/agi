package io.matrix.omni;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OmniModalGatewayTest {

    @Test
    void testCreateGateway() {
        OmniModalGateway gateway = new OmniModalGateway();
        assertNotNull(gateway);
        assertTrue(gateway.getSupportedInputModalities().isEmpty());
    }

    @Test
    void testRegisterEncoder() {
        OmniModalGateway gateway = new OmniModalGateway();
        gateway.registerEncoder(new AudioEncoder(1024, 42L));
        assertTrue(gateway.isInputSupported(OmniModalGateway.Modality.AUDIO));
    }

    @Test
    void testRegisterDecoder() {
        OmniModalGateway gateway = new OmniModalGateway();
        gateway.registerDecoder(new TextToSpeechDecoder(42L));
        assertTrue(gateway.isOutputSupported(OmniModalGateway.Modality.AUDIO));
    }

    @Test
    void testProcessAudioInput() {
        OmniModalGateway gateway = new OmniModalGateway();
        gateway.registerEncoder(new AudioEncoder(1024, 42L));

        byte[] audio = new byte[1000];
        new Random(42).nextBytes(audio);
        OmniModalGateway.OmniMessage msg = new OmniModalGateway.OmniMessage(
            OmniModalGateway.Modality.AUDIO, audio, "audio/wav", Map.of(), System.currentTimeMillis()
        );

        boolean[] hdc = gateway.processInput(msg);
        assertNotNull(hdc);
        assertEquals(1024, hdc.length);
    }

    @Test
    void testAudioRoundtrip() {
        OmniModalGateway gateway = new OmniModalGateway();
        gateway.registerEncoder(new AudioEncoder(1024, 42L));
        gateway.registerDecoder(new TextToSpeechDecoder(42L));

        byte[] audio = new byte[500];
        new Random(42).nextBytes(audio);

        // Input → HDC
        OmniModalGateway.OmniMessage input = new OmniModalGateway.OmniMessage(
            OmniModalGateway.Modality.AUDIO, audio, "audio/wav", Map.of(), System.currentTimeMillis()
        );
        boolean[] hdc = gateway.processInput(input);

        // HDC → Output
        OmniModalGateway.OmniMessage output = gateway.generateOutput(
            OmniModalGateway.Modality.AUDIO, hdc, "audio/wav"
        );

        assertNotNull(output);
        assertEquals(OmniModalGateway.Modality.AUDIO, output.modality());
        assertNotNull(output.data());
    }

    @Test
    void testUnsupportedModality() {
        OmniModalGateway gateway = new OmniModalGateway();
        assertFalse(gateway.isInputSupported(OmniModalGateway.Modality.IMAGE));
        assertFalse(gateway.isOutputSupported(OmniModalGateway.Modality.VIDEO));
    }

    @Test
    void testEmptyAudio() {
        OmniModalGateway gateway = new OmniModalGateway();
        gateway.registerEncoder(new AudioEncoder(1024, 42L));

        OmniModalGateway.OmniMessage msg = new OmniModalGateway.OmniMessage(
            OmniModalGateway.Modality.AUDIO, new byte[0], "audio/wav", Map.of(), System.currentTimeMillis()
        );

        boolean[] hdc = gateway.processInput(msg);
        assertNotNull(hdc);
        assertEquals(1024, hdc.length);
    }

    @Test
    void testHistoryTracking() {
        OmniModalGateway gateway = new OmniModalGateway();
        gateway.registerEncoder(new AudioEncoder(1024, 42L));

        for (int i = 0; i < 5; i++) {
            byte[] audio = new byte[100];
            OmniModalGateway.OmniMessage msg = new OmniModalGateway.OmniMessage(
                OmniModalGateway.Modality.AUDIO, audio, "audio/wav", Map.of(), System.currentTimeMillis()
            );
            gateway.processInput(msg);
        }
        assertEquals(5, gateway.getHistory().size());
    }

    @Test
    void testLatencyTracking() {
        AudioEncoder encoder = new AudioEncoder(1024, 42L);
        byte[] audio = new byte[500];
        OmniModalGateway.OmniMessage msg = new OmniModalGateway.OmniMessage(
            OmniModalGateway.Modality.AUDIO, audio, "audio/wav", Map.of(), System.currentTimeMillis()
        );
        encoder.encode(msg);
        assertTrue(encoder.getLatencyMs() >= 0);
    }
}
