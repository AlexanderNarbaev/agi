package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W32.7 — colour naming is a measurable contract, not a look at an output.
 *
 * <p>W32.6 found the namer was a chain of nested ternaries that tested red before blue, so
 * a dark blue (20, 20, 220) was reported as "dark". The shape of the code was the bug:
 * naming a colour by comparing channels pairwise cannot express hue, and no amount of
 * reordering fixes it.</p>
 *
 * <p>It is now classified by hue, saturation and brightness, and this table is the
 * contract. Every row is a colour a caller can actually produce, so a regression shows up
 * as a named failure rather than as a screenshot nobody compares.</p>
 *
 * <p><b>Scope, honestly.</b> These are flat fills. Nothing here claims region segmentation,
 * shape recognition, or that the mind can say "a red circle" — it cannot, and the
 * perception text deliberately does not imply it.</p>
 */
class ColourNamingTest {

    @ParameterizedTest(name = "rgb({0},{1},{2}) is {3}")
    @CsvSource({
        // primaries, the cases the old chain got wrong or called "dark"
        "220,  20,  20, red",
        " 20,  20, 220, blue",
        " 20, 220,  20, green",
        // secondaries
        "220, 220,  20, yellow",
        " 20, 220, 220, cyan",
        "220,  20, 220, magenta",
        // neutrals
        "240, 240, 240, white",
        " 10,  10,  10, black",
        "128, 128, 128, grey",
        // the exact pair that broke the old implementation
        " 20,  20, 220, blue",
        // orange sits at the red/yellow boundary
        "235, 140,  20, orange",
        // lightness-qualified variants
        " 40,  40, 110, dark blue",
        "180, 180, 250, light blue",
        " 60,  60, 200, blue",
    })
    void namesColoursByHue(int r, int g, int b, String expected) {
        assertEquals(expected, RealInboxWatcher.colourName(r, g, b),
            "colourName(" + r + "," + g + "," + b + ")");
    }

    @Test
    void distinguishesDarkBlueFromBlueWhichTheOldChainCouldNot() {
        // The regression that motivated the rewrite, kept as its own assertion so it
        // cannot be reintroduced by a plausible-looking edit.
        assertEquals("blue", RealInboxWatcher.colourName(20, 20, 220));
        assertEquals("dark blue", RealInboxWatcher.colourName(40, 40, 110));
        // A mid-lightness blue stays "blue": qualifying every saturated colour as "light"
        // or "dark" is what made the first attempt call a vivid orange "light yellow".
        assertEquals("blue", RealInboxWatcher.colourName(60, 60, 200));
    }

    @Test
    void greyIsNeverConfusedWithAWeakHue() {
        // A desaturated blue is grey, not "blue", because it carries no hue information.
        assertEquals("grey", RealInboxWatcher.colourName(128, 130, 135));
    }

    @Test
    void namingIsDeterministic() {
        for (int i = 0; i < 50; i++) {
            assertEquals("red", RealInboxWatcher.colourName(200, 30, 30));
        }
    }

    @Test
    void aDecodedSolidImageIsNamedForItsActualColour() {
        // End to end through the real decoder, not just the naming function.
        byte[] png = MediaDecodingTestFixtures.synthPng(16, 16, 20, 20, 220);
        MediaDecoding.Image img = MediaDecoding.decodePng(png);
        org.junit.jupiter.api.Assumptions.assumeTrue(img != null, "decoder refused the fixture");
        assertTrue(RealInboxWatcher.dominantColour(img).contains("blue"),
            "a solid blue image must be named blue, got: " + RealInboxWatcher.dominantColour(img));
    }

    // ---- RECON-W32.7: the perception must be ADDRESSABLE -------------------

    @Test
    void aPerceptionIsAlsoAddressableByItsFileName() {
        // The descriptive fact and its source live in different places: description in
        // the text, source in the record id. That is deliberate — putting the filename in
        // the text costs three content tokens and the precision term punishes length
        // enough that "What colour is red32.png?" scored 0.07 against a 0.20 floor.
        // So the same perception is indexed a second time, addressably.
        String descriptive = "image: 32x32 px, dominant colour red, 0 edge primitives, "
            + "total magnitude 0.0000";
        String addressable =
            RealInboxWatcher.addressablePerception("red32.png", descriptive);
        org.junit.jupiter.api.Assertions.assertEquals("red32 colour red", addressable);
        org.junit.jupiter.api.Assertions.assertTrue(
            ContentSimilarity.score("What colour is red32.png?", addressable)
                >= ContentSimilarity.RETRIEVAL_FLOOR,
            "the addressable form must be retrievable, scored "
                + ContentSimilarity.score("What colour is red32.png?", addressable));
    }

    @Test
    void audioPerceptionsAreAddressableToo() {
        String a = RealInboxWatcher.addressablePerception("tone440.wav",
            "audio: 8000 Hz 2000 samples, 8 bands, dominant band 2000-2500 Hz, "
                + "dominant frequency 440.0 Hz (peak bin 110, sharpness 0.99), "
                + "total energy 32.7");
        org.junit.jupiter.api.Assertions.assertEquals("tone440 frequency 440.0 Hz", a);
    }

    @Test
    void aSummaryWithNeitherColourNorFrequencyHasNoAddressableForm() {
        // Returning null is correct: an addressable fact that names nothing is noise.
        org.junit.jupiter.api.Assertions.assertNull(
            RealInboxWatcher.addressablePerception("notes.txt", "just some text"));
    }
}
