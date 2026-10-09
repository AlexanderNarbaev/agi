package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W34.11 — end-to-end proof that an undecodable media file reaches the mind as measured
 * metadata rather than as a bare hash or nothing at all.
 *
 * <p>W33.3 added {@link MediaDecoding#probe} with 15 passing tests. Nothing in production
 * called it, so the capability was an island: the only thing a mind could learn about a file
 * it could not decode was a SHA-256 fingerprint, and the metadata was unreachable by
 * construction. Unit tests on an unwired method prove the method works, not that the mind
 * benefits.</p>
 *
 * <p>These tests drive the real inbox and assert on what the mind can actually retrieve, which
 * is the only question that matters. The honesty constraint is asserted alongside: the
 * ingested text must state that the contents were not read, because a machine-readable fact
 * about a file is still not a perception of it.</p>
 *
 * <p>The files here carry PNG magic bytes under an extension that routes to no transcoder at
 * all, which is the only case where metadata is the correct answer. A file named {@code .png}
 * that its decoder cannot read is a DIFFERENT case and is still refused: we know it is meant to
 * be an image, we failed to perceive the image, and replacing that failure with a byte count
 * would be the W32 fabrication. That distinction is asserted below rather than assumed.</p>
 */
class MediaMetadataInboxIntegrationTest {

    /** PNG magic bytes. The test files carry these under an extension nothing routes to. */
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static byte[] opaquePng(int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(PNG_MAGIC, 0, bytes, 0, PNG_MAGIC.length);
        for (int i = PNG_MAGIC.length; i < size; i++) {
            bytes[i] = (byte) (i & 0xFF);       // opaque, mostly non-printable
        }
        return bytes;
    }

    private static RealInboxWatcher watcherFor(Path dir, Path store) {
        return new RealInboxWatcher(dir, new PersistentHdcStore(store, 2048));
    }

    private static String allTaughtText(RealInboxWatcher watcher) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : taughtEntries(watcher)) {
            sb.append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private static Iterable<Map.Entry<String, String>> taughtEntries(RealInboxWatcher watcher) {
        return storeRef(watcher).snapshot().entrySet();
    }

    private static PersistentHdcStore storeRef(RealInboxWatcher watcher) throws AssertionError {
        try {
            java.lang.reflect.Field f = RealInboxWatcher.class.getDeclaredField("hdcStore");
            f.setAccessible(true);
            return (PersistentHdcStore) f.get(watcher);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot reach the store the watcher taught", e);
        }
    }

    @Test
    @DisplayName("an undecodable file is ingested as measured metadata, not refused")
    void undecodableMediaReachesTheMindAsMetadata(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("opaque-image.dat"), opaquePng(512));

        RealInboxWatcher watcher = watcherFor(dir, dir.resolve("store1"));
        int ingested = watcher.scan();

        assertThat(ingested)
                .as("an undecodable file must still reach the mind as metadata; refusing "
                        + "silently is what made this capability unreachable")
                .isEqualTo(1);
        assertThat(watcher.rejections())
                .as("the file must not be rejected once metadata can stand in for a decoder")
                .isEmpty();
    }

    @Test
    @DisplayName("the ingested text is honest: it says the contents were NOT read")
    void ingestedMetadataDoesNotClaimToBeAPerception(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("chart.dat"), opaquePng(256));

        RealInboxWatcher watcher = watcherFor(dir, dir.resolve("store2"));
        assertThat(watcher.scan()).isEqualTo(1);

        String taught = allTaughtText(watcher);

        assertThat(taught)
                .as("the description must carry the measured signature and MIME guess read "
                        + "off the magic bytes, not just a hash")
                .contains("image/png");
        assertThat(taught)
                .as("the byte count is a measurement too, and was previously absent")
                .contains("256 bytes");

        assertThat(taught.toLowerCase())
                .as("metadata must never be mistaken for a decoded perception — the W32 "
                        + "fabrication rule still binds")
                .contains("did not read the contents");
    }

    @Test
    @DisplayName("the file name is addressable, so the mind can be asked about it later")
    void fileNameRemainsAddressable(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("receipt-2024.dat"), opaquePng(128));

        RealInboxWatcher watcher = watcherFor(dir, dir.resolve("store3"));
        assertThat(watcher.scan()).isEqualTo(1);

        assertThat(allTaughtText(watcher))
                .as("a payload the mind cannot name is a payload the mind cannot find")
                .contains("receipt-2024");
    }

    @Test
    @DisplayName("plain text still decodes normally — the fallback did not swallow the good path")
    void plainTextPathIsUnaffected(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("note.txt"),
                "the mitochondrion is the powerhouse of the cell", StandardCharsets.UTF_8);

        RealInboxWatcher watcher = watcherFor(dir, dir.resolve("store4"));
        assertThat(watcher.scan()).isEqualTo(1);

        String taught = allTaughtText(watcher);
        assertThat(taught).contains("mitochondrion");
        assertThat(taught.toLowerCase())
                .as("decodable text must NOT be wrapped in the not-a-perception disclaimer")
                .doesNotContain("did not read the contents");
    }

    /**
     * The boundary the previous test alone would not catch.
     *
     * <p>A file named {@code .png} carries PNG magic bytes, routes to the image transcoder,
     * and that transcoder cannot read it. The metadata fallback must NOT rescue this case: we
     * know the file is meant to be an image, we failed to perceive the image, and substituting
     * "it is 256 bytes and starts with the PNG signature" for the perception would convert a
     * visible failure into an invisible one. It stays a recorded rejection.</p>
     */
    @Test
    @DisplayName("a recognised format whose decoder fails is still refused, not laundered")
    void recognisedButUndecodableImageIsStillRefused(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("broken.png"), opaquePng(256));

        RealInboxWatcher watcher = watcherFor(dir, dir.resolve("store5"));
        int ingested = watcher.scan();

        assertThat(ingested)
                .as("a failed decode must not be laundered into a successful metadata ingest")
                .isEqualTo(0);
        assertThat(watcher.rejections())
                .as("the refusal must be visible, so an operator can tell a policy decision "
                        + "from a broken pipeline")
                .isNotEmpty();
        assertThat(allTaughtText(watcher))
                .as("nothing about an undecoded image may reach the mind as knowledge")
                .doesNotContain("image/png");
    }
}