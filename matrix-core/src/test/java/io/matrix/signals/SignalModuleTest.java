package io.matrix.signals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignalModuleTest {

    @Test
    void textModuleEncodes() {
        var m = new TextSignalModule();
        long[] s1 = m.encode("hello");
        long[] s2 = m.encode("hello");
        long[] s3 = m.encode("world");
        assertArrayEquals(s1, s2); // deterministic
        assertNotEquals(s1[0], s3[0]); // different inputs → different signals
        assertEquals("text", m.modality());
    }

    @Test
    void imageModuleEncodes() {
        var m = new ImageSignalModule();
        byte[] img1 = new byte[64]; // all zeros
        byte[] img2 = new byte[64]; img2[0] = 1;
        long[] s1 = m.encode(img1);
        long[] s2 = m.encode(img2);
        assertNotEquals(s1[0], s2[0]);
        assertEquals("image", m.modality());
    }

    @Test
    void audioModuleEncodes() {
        var m = new AudioSignalModule();
        byte[] audio1 = new byte[128]; // silence
        byte[] audio2 = new byte[128]; for (int i = 0; i < 128; i++) audio2[i] = (byte) (i % 2 == 0 ? 50 : -50);
        long[] s1 = m.encode(audio1);
        long[] s2 = m.encode(audio2);
        assertNotEquals(s1[0], s2[0]);
        assertEquals("audio", m.modality());
    }

    @Test
    void registryRegistersAndFinds() {
        var reg = new SignalModuleRegistry();
        var text = new TextSignalModule();
        var image = new ImageSignalModule();
        reg.register(text);
        reg.register(image);

        assertTrue(reg.get("text").isPresent());
        assertTrue(reg.get("image").isPresent());
        assertFalse(reg.get("audio").isPresent());
        assertEquals(2, reg.modalities().size());
    }

    @Test
    void registryVersionedLookup() {
        var reg = new SignalModuleRegistry();
        var text1 = new TextSignalModule();
        reg.register(text1);
        assertTrue(reg.get("text", "1.0.0").isPresent());
        assertFalse(reg.get("text", "2.0.0").isPresent());
    }

    @Test
    void registryLatestVersionWins() {
        var reg = new SignalModuleRegistry();
        reg.register(new TextSignalModuleV1());
        reg.register(new TextSignalModuleV2());
        assertEquals("2.0.0", reg.get("text").get().version());
    }

    static final class TextSignalModuleV1 extends TextSignalModule {
        @Override public String version() { return "1.0.0"; }
    }
    static final class TextSignalModuleV2 extends TextSignalModule {
        @Override public String version() { return "2.0.0"; }
    }

    @Test
    void registryListModules() {
        var reg = new SignalModuleRegistry();
        reg.register(new TextSignalModule());
        reg.register(new ImageSignalModule());
        var modules = reg.listModules();
        assertEquals(2, modules.size());
        assertTrue(modules.stream().anyMatch(m -> m.modality().equals("text")));
        assertTrue(modules.stream().anyMatch(m -> m.modality().equals("image")));
    }

    @Test
    void moduleValidate() {
        assertTrue(new TextSignalModule().validate());
    }
}
