package io.matrix.research;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlgorithmEncyclopediaTest {

    @Test
    void testCreateEncyclopedia() {
        AlgorithmEncyclopedia enc = new AlgorithmEncyclopedia();
        assertNotNull(enc);
        assertFalse(enc.listAlgorithms().isEmpty());
    }

    @Test
    void testGetAlgorithm() {
        AlgorithmEncyclopedia enc = new AlgorithmEncyclopedia();
        AlgorithmEncyclopedia.AlgorithmEntry bir = enc.get("BIR");
        assertNotNull(bir);
        assertTrue(bir.math().contains("∧"));
    }

    @Test
    void testAllAlgorithmsHaveFields() {
        AlgorithmEncyclopedia enc = new AlgorithmEncyclopedia();
        for (var entry : enc.getAll()) {
            assertNotNull(entry.math());
            assertNotNull(entry.history());
            assertNotNull(entry.pros());
            assertNotNull(entry.cons());
            assertNotNull(entry.implementationLink());
        }
    }

    @Test
    void testHDCEntry() {
        AlgorithmEncyclopedia enc = new AlgorithmEncyclopedia();
        var hdc = enc.get("HDC");
        assertNotNull(hdc);
        assertTrue(hdc.history().contains("Kanerva"));
    }

    @Test
    void testAlgorithmCount() {
        AlgorithmEncyclopedia enc = new AlgorithmEncyclopedia();
        assertTrue(enc.listAlgorithms().size() >= 7, "Should have at least 7 algorithms documented");
    }

    @Test
    void testImplementationLinks() {
        AlgorithmEncyclopedia enc = new AlgorithmEncyclopedia();
        for (var entry : enc.getAll()) {
            assertTrue(entry.implementationLink().endsWith(".java"),
                "Implementation link should point to Java file: " + entry.implementationLink());
        }
    }
}
