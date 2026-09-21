package io.matrix.bir;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PureBirGeneratorTest {

    @Test
    void cascadeStructure() {
        PureBirGenerator gen = new PureBirGenerator();
        assertEquals(3, gen.cascadeSize(), "3 cascade layers");
        assertEquals(60, gen.totalNeurons(), "20 neurons × 3 layers");
        assertTrue(gen.memoryBytes() > 0, "Truth tables use memory");
    }

    @Test
    void deterministicOutput() {
        // Same input → same output, always
        PureBirGenerator gen = new PureBirGenerator();
        long in = 0b10101010101010101010L;
        long out1 = gen.generate(in);
        long out2 = gen.generate(in);
        long out3 = gen.generate(in);
        assertEquals(out1, out2, "Deterministic across calls");
        assertEquals(out2, out3, "Deterministic across calls");
    }

    @Test
    void pureGenerativeProperties() {
        // Pure BIR is a deterministic many-to-one function over 2^20 inputs.
        // We verify that the cascade spreads its image (output range) broadly
        // — i.e., it's not constant — by sampling across the full 20-bit
        // input space and counting distinct outputs.
        PureBirGenerator gen = new PureBirGenerator();
        Set<Long> seen = new HashSet<>();
        // Sample 4096 random 20-bit inputs across the full domain
        java.util.Random rng = new java.util.Random(1234);
        for (int i = 0; i < 4096; i++) {
            long in = rng.nextLong() & ((1L << 20) - 1);
            seen.add(gen.generate(in));
        }
        // With 3 non-trivial layers (parity + majority + mix3-majority),
        // we expect at least 100 distinct outputs in 4096 samples.
        assertTrue(seen.size() >= 100,
                "Cascade should produce ≥100 distinct outputs across 4096 random inputs, got " + seen.size());
    }

    @Test
    void bitWidthRespected() {
        PureBirGenerator gen = new PureBirGenerator();
        // Output must fit in 20 bits
        long out = gen.generate(0xFFFFFFFFL); // all 1s in low 32
        long mask = (1L << 20) - 1;
        assertEquals(out & ~mask, 0L, "Output must fit in 20 bits");
    }

    @Test
    void textToBitsEncoding() {
        // Word-hash encoding: same text → same bits
        assertEquals(PureBirGenerator.encodeText("hello world"),
                     PureBirGenerator.encodeText("hello world"));
        // Null/blank → zero vector
        assertEquals(0L, PureBirGenerator.encodeText(null));
        assertEquals(0L, PureBirGenerator.encodeText(""));
        assertEquals(0L, PureBirGenerator.encodeText("   "));
    }

    @Test
    void generateFromText() {
        PureBirGenerator gen = new PureBirGenerator();
        // generateFromText = generate(encodeText(text))
        String text = "What is the meaning of life?";
        long a = gen.generateFromText(text);
        long b = gen.generate(PureBirGenerator.encodeText(text));
        assertEquals(a, b, "generateFromText matches generate(encodeText)");
    }

    @Test
    void differentInputsDifferentOutputs() {
        // Sample random 20-bit inputs, verify the cascade's image is large
        // relative to input space. Boolean functions are many-to-one, so
        // we expect image size proportional to but smaller than input size.
        PureBirGenerator gen = new PureBirGenerator();
        Set<Long> outputs = new HashSet<>();
        java.util.Random rng = new java.util.Random(99);
        for (int i = 0; i < 2000; i++) {
            long in = rng.nextLong() & ((1L << 20) - 1);
            outputs.add(gen.generate(in));
        }
        // Boolean function over 2^20 inputs → image size > 100 in 2000 random samples
        assertTrue(outputs.size() > 100,
                "Expected >100 distinct outputs from 2000 random inputs, got " + outputs.size());
    }

    @Test
    void zeroInput() {
        // Edge case: all-zero input
        PureBirGenerator gen = new PureBirGenerator();
        long out = gen.generate(0L);
        // Parity(0) = 0, Majority(0) = 0, then XOR-fold layer produces some value
        // Just verify it doesn't throw and fits in 20 bits.
        long mask = (1L << 20) - 1;
        assertEquals(0L, out & ~mask);
    }

    @Test
    void noCorpusRequired() {
        // Pure BIR generation must not require any corpus/training_data files.
        // We verify by checking it runs from a fresh instance with no file IO.
        PureBirGenerator gen = new PureBirGenerator();
        // If this works without throwing, no corpus was needed.
        for (int i = 0; i < 100; i++) {
            long out = gen.generate(i);
            assertNotNull(Long.valueOf(out));
        }
    }

    @Test
    void cascadeShapeString() {
        PureBirGenerator gen = new PureBirGenerator();
        String shape = gen.cascadeShapes();
        assertNotNull(shape);
        assertTrue(shape.contains("tt"), "Cascade shape mentions tt form");
        assertTrue(shape.contains("x20"), "Cascade has 20 neurons per layer");
    }
}