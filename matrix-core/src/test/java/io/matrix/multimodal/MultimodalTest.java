package io.matrix.multimodal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Map;

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

class UnifiedRepresentationTest {

    @Test
    void toBooleanVectorThresholdsCorrectly() {
        var aligner = new CrossModalAligner(Map.of("text", new TextFeatureExtractor()));
        var repr = new UnifiedRepresentation();
        try {
            var field = UnifiedRepresentation.class.getDeclaredField("aligner");
            field.setAccessible(true);
            field.set(repr, aligner);
        } catch (Exception e) {
            fail("Failed to inject aligner: " + e.getMessage());
        }
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

    @Test
    void crossModalAlignerExtractsFeatures() {
        var aligner = new CrossModalAligner(Map.of("text", new TextFeatureExtractor()));
        var features = aligner.extract("text", "hello");
        assertEquals(256, features.length);
    }

    @Test
    void crossModalAlignerUnknownModalityThrows() {
        var aligner = new CrossModalAligner(Map.of());
        assertThrows(IllegalArgumentException.class, () -> 
            aligner.extract("unknown", "data"));
    }
}
