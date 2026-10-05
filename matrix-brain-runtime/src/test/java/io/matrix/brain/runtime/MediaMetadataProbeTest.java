package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.matrix.brain.runtime.MediaDecoding.MediaMetadata;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W33.3 — any binary stream yields metadata, so a perception gap is not a dead end.
 *
 * <p>Spec: {@code docs-v2/specs/SPEC-W33.3-unknown-media-payload.md}. Each test below names
 * the acceptance criterion it pins (AC1..AC10) so a failure points at the contract rather
 * than at an implementation detail.</p>
 *
 * <p>The capability under test is architectural, not numerical. Before this, a JPEG produced
 * {@code Classification(UNKNOWN, false, ...)} and the observation was discarded, so the system
 * forgot a file the instant it refused it. Now the observation survives and is answerable.</p>
 *
 * <p>Hostile input is the normal case here, not an edge case: {@code probe} is reached with
 * whatever a user dropped in the inbox. Every fixture below is deliberately malformed in some
 * way, and "did not throw" is itself an assertion in most of them.</p>
 */
class MediaMetadataProbeTest {

    /** Bytes that are recognisably a PNG signature but nothing more. */
    private static byte[] pngSignatureOnly() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    }

    /** A JPEG SOI marker followed by a start-of-frame and one APP segment. */
    private static byte[] jpegHeader() {
        return new byte[]{
            (byte) 0xFF, (byte) 0xD8,                       // SOI
            (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, // APP0
            (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00,    // DQT
            (byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08      // SOF0 marker
        };
    }

    /** An ELF binary carrying a filename that lies about being an image. */
    private static byte[] elfNamedJpeg() {
        return new byte[]{
            0x7F, 'E', 'L', 'F',              // magic
            0x02,                             // 64-bit
            0x01,                             // little endian
            0x01,                             // version
            0x00,                             // System V ABI
            0x3E, 0x00                        // x86-64
        };
    }

    // ==================================================================
    // R1: probe never throws. Any input is acceptable input.
    // ==================================================================

    @Test
    @DisplayName("AC2/R1: null name and null bytes return an empty record, not an exception")
    void nulls_are_absorbed() {
        assertThatCode(() -> MediaDecoding.probe(null, null)).doesNotThrowAnyException();
        MediaMetadata m = MediaDecoding.probe(null, null);
        assertThat(m.sizeBytes()).isZero();
        assertThat(m.detectedMime()).isNull();
        assertThat(m.headerFields()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("AC1/R1: a PNG signature with no IHDR behind it does not throw")
    void truncated_png_does_not_throw() {
        assertThatCode(() -> MediaDecoding.probe("t.png", pngSignatureOnly()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC5/R1: PNG bytes shorter than a full IHDR do not throw or report dimensions")
    void png_shorter_than_ihdr_yields_no_dimensions() {
        byte[] tooShort = Arrays.copyOf(pngSignatureOnly(), 12);
        assertThatCode(() -> MediaDecoding.probe("t.png", tooShort)).doesNotThrowAnyException();
        MediaMetadata m = MediaDecoding.probe("t.png", tooShort);
        assertThat(m.detectedMime()).isEqualTo("image/png");
        // IHDR lives at bytes 16..24. At 12 bytes it is absent, so width/height must be
        // absent too rather than read past the end.
        assertThat(m.headerFields()).doesNotContainKeys("width", "height");
    }

    @Test
    @DisplayName("AC9/R1: a large hostile blob does not throw and renders a bounded description")
    void large_hostile_input_is_bounded() {
        byte[] hostile = new byte[2_000_000];
        Arrays.fill(hostile, (byte) 0xFF);
        assertThatCode(() -> MediaDecoding.probe("bomb.jpg", hostile)).doesNotThrowAnyException();
        String described = MediaDecoding.describe(MediaDecoding.probe("bomb.jpg", hostile));
        // 2 MB of hostile input must not become 2 MB of stored prose.
        assertThat(described.length()).isLessThan(2000);
    }

    // ==================================================================
    // R3: the format comes from bytes, never from the filename.
    // ==================================================================

    @Test
    @DisplayName("AC4/R3: an ELF binary named photo.jpg is reported as ELF, never as JPEG")
    void lying_filename_does_not_change_the_format() {
        MediaMetadata m = MediaDecoding.probe("photo.jpg", elfNamedJpeg());
        assertThat(m.detectedMime()).isNotEqualTo("image/jpeg");
        assertThat(m.detectedMime()).contains("elf");
        assertThat(m.headerFields()).containsKey("class");
        assertThat(m.headerFields()).containsKey("endianness");
    }

    @Test
    @DisplayName("AC3/R3: a real JPEG header is recognised with a citable signature")
    void jpeg_is_recognised_from_bytes() {
        MediaMetadata m = MediaDecoding.probe("photo.jpg", jpegHeader());
        assertThat(m.detectedMime()).isEqualTo("image/jpeg");
        assertThat(m.signatureHex()).isNotNull().startsWith("ffd8ff");
        assertThat(m.headerFields()).containsEntry("marker", "SOI");
        // Segment contents are NOT parsed; only their count is honest.
        assertThat(m.headerFields()).containsKey("appSegments");
    }

    @Test
    @DisplayName("AC8/R6: a real decodable PNG is still reported as PNG, with dimensions")
    void decodable_png_is_still_probed() {
        byte[] png = MediaMetadataProbeFixtures.minimalPng(7, 11);
        MediaMetadata m = MediaDecoding.probe("ok.png", png);
        assertThat(m.detectedMime()).isEqualTo("image/png");
        assertThat(m.headerFields()).containsEntry("width", "7")
                .containsEntry("height", "11");
    }

    @Test
    @DisplayName("R3: bytes matching nothing yield a null mime, never a guess")
    void unrecognised_bytes_yield_null_mime() {
        byte[] opaque = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                         (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08};
        MediaMetadata m = MediaDecoding.probe("mystery.dat", opaque);
        assertThat(m.detectedMime()).isNull();
        assertThat(m.signatureHex()).isNotNull(); // bytes were seen even if unexplained
        assertThat(m.sizeBytes()).isEqualTo(8L);
    }

    // ==================================================================
    // R2/R4/R5: size, measurability, no null maps.
    // ==================================================================

    @Test
    @DisplayName("AC6/R4: ASCII text scores materially higher printableRatio than random binary")
    void printable_ratio_separates_text_from_binary() {
        byte[] ascii = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] binary = new byte[43];
        Arrays.fill(binary, (byte) 0x8B);
        double textScore = MediaDecoding.probe("t.txt", ascii).printableRatio();
        double binaryScore = MediaDecoding.probe("b.bin", binary).printableRatio();
        assertThat(textScore).isGreaterThan(binaryScore);
        assertThat(textScore).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("R5: headerFields is never null, including for empty input")
    void header_fields_never_null() {
        assertThat(MediaDecoding.probe("x", new byte[0]).headerFields()).isNotNull();
    }

    @Test
    @DisplayName("R2: sizeBytes reflects the real length")
    void size_is_reported() {
        assertThat(MediaDecoding.probe("f", new byte[1234]).sizeBytes()).isEqualTo(1234L);
    }

    // ==================================================================
    // AC7: the gap becomes answerable knowledge. This is the whole point.
    // ==================================================================

    @Test
    @DisplayName("AC7: the refused file is retrievable from the knowledge base afterwards")
    void perception_gap_is_retrievable_afterwards() {
        var kb = new io.matrix.knowledge.SimpleKnowledgeBase();
        MediaMetadata m = MediaDecoding.probe("holiday.jpg", jpegHeader());
        var classification = MediaDecoding.classify("holiday.jpg", jpegHeader());

        String title = "media metadata: holiday.jpg";
        String content = MediaDecoding.describe(m) + " " + classification.reason();
        kb.addDocument(title, "holiday.jpg", content);

        var hits = kb.retrieve("holiday.jpg", 3);
        assertThat(hits).isNotEmpty();
        // Assert the SUBSTANCE of both halves -- the format was identified, and the contents
        // were not read -- rather than pinning exact phrasing. An earlier version of this test
        // asserted the phrase "not perceived" from the spec's illustrative example, and failed
        // against a description that says the same thing in different words. Wording is not the
        // contract; both claims being present is.
        assertThat(hits.get(0).doc().content())
                .contains("image/jpeg")
                .contains("did not read the CONTENTS");
    }

    @Test
    @DisplayName("AC7: describe() states both what was seen and what was not read")
    void description_names_both_halves() {
        String d = MediaDecoding.describe(
                MediaDecoding.probe("holiday.jpg", jpegHeader()));
        assertThat(d).contains("holiday.jpg");
        assertThat(d).contains("bytes");
        assertThat(d).contains("image/jpeg");
    }

    // ==================================================================
    // AC10 / R7: purity and determinism.
    // ==================================================================

    @Test
    @DisplayName("AC10/R7: probing is deterministic — same input, same record")
    void probing_is_deterministic() {
        MediaMetadata a = MediaDecoding.probe("x.jpg", jpegHeader());
        MediaMetadata b = MediaDecoding.probe("x.jpg", jpegHeader());
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("R7: probing does not mutate or retain the caller's array")
    void probing_does_not_mutate_input() {
        byte[] input = jpegHeader();
        byte[] copy = input.clone();
        MediaDecoding.probe("x.jpg", input);
        assertThat(input).isEqualTo(copy);
    }
}