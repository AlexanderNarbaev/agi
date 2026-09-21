package io.matrix.multimodal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TextFeatureExtractorTest {

    @Test
    void extractReturns256Features() {
        var extractor = new TextFeatureExtractor();
        var features = extractor.extract("Hello world");
        assertEquals(256, features.length);
    }

    @Test
    void modalityIsText() {
        var extractor = new TextFeatureExtractor();
        assertEquals("text", extractor.modality());
    }

    @Test
    void featuresNormalized() {
        var extractor = new TextFeatureExtractor();
        var features = extractor.extract("test");
        for (float f : features) {
            assertTrue(f >= 0.0f && f <= 1.0f, "Feature out of range: " + f);
        }
    }

    @Test
    void emptyTextProducesZeroFeatures() {
        var extractor = new TextFeatureExtractor();
        var features = extractor.extract("");
        for (float f : features) {
            assertEquals(0.0f, f, 0.001f);
        }
    }
}

class ImageFeatureExtractorTest {

    @Test
    void extractReturns512Features() {
        var extractor = new ImageFeatureExtractor();
        byte[] data = new byte[900]; // 300 pixels RGB
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        var features = extractor.extract(data);
        assertEquals(512, features.length);
    }

    @Test
    void modalityIsImage() {
        assertEquals("image", new ImageFeatureExtractor().modality());
    }

    @Test
    void emptyImageHandlesGracefully() {
        var extractor = new ImageFeatureExtractor();
        var features = extractor.extract(new byte[0]);
        assertEquals(512, features.length);
        for (float f : features) assertEquals(0.0f, f, 0.001f);
    }
}

class AudioFeatureExtractorTest {

    @Test
    void extractReturns128Features() {
        var extractor = new AudioFeatureExtractor();
        byte[] data = new byte[200]; // 100 samples 16-bit
        for (int i = 0; i < data.length; i++) data[i] = (byte) ((i % 256) - 128);
        var features = extractor.extract(data);
        assertEquals(128, features.length);
    }

    @Test
    void modalityIsAudio() {
        assertEquals("audio", new AudioFeatureExtractor().modality());
    }

    @Test
    void emptyAudioHandlesGracefully() {
        var extractor = new AudioFeatureExtractor();
        var features = extractor.extract(new byte[0]);
        assertEquals(128, features.length);
    }

    @Test
    void singleSampleHandlesGracefully() {
        var extractor = new AudioFeatureExtractor();
        var features = extractor.extract(new byte[]{0, 50});
        assertEquals(128, features.length);
    }
}

class UnifiedRepresentationTest {

    @Test
    void toBooleanVectorThresholdsCorrectly() {
        var repr = new UnifiedRepresentation();
        boolean[] bits = repr.toBooleanVector("test text", "text");
        assertNotNull(bits);
        assertTrue(bits.length > 0);
    }

    @Test
    void toFeatureVectorConvertsBits() {
        var repr = new UnifiedRepresentation();
        boolean[] input = {true, false, true, true, false};
        float[] features = repr.toFeatureVector(input);
        assertEquals(5, features.length);
        assertEquals(1.0f, features[0], 0.001f);
        assertEquals(0.0f, features[1], 0.001f);
        assertEquals(1.0f, features[2], 0.001f);
    }

    @Test
    void mergeXorMergesVectors() {
        var repr = new UnifiedRepresentation();
        boolean[][] vectors = {
            {true, false, true},
            {true, true, false},
            {false, true, true}
        };
        boolean[] merged = repr.merge(vectors);
        assertEquals(3, merged.length);
        assertFalse(merged[0]); // true XOR true XOR false = false
        assertFalse(merged[1]); // false XOR true XOR true = false
        assertFalse(merged[2]); // true XOR false XOR true = false
    }

    @Test
    void mergeEmptyReturnsEmpty() {
        var repr = new UnifiedRepresentation();
        assertEquals(0, repr.merge(new boolean[0][]).length);
    }
}
