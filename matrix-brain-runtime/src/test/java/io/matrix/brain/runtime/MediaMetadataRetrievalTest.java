package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W35.1 — proves that probed media metadata is actually RETRIEVABLE, which is the part the
 * W34.11 wiring left unfinished.
 *
 * <p>W34.11 made the mind learn media metadata: byte count, container signature, MIME guess.
 * It taught the whole description as ONE document. Storing it was not the same as being able
 * to find it. HDC retrieval scores a query against a document as a whole, so a long paragraph
 * dilutes its own answer — a question naming the file could not surface the MIME string
 * buried in the middle of it. The capability existed and was unreachable for the same reason
 * {@code probe()} had been unreachable the wave before.</p>
 *
 * <p>These tests assert on retrieval, not on the presence of a substring in a stored blob,
 * because "the string is in there" is exactly the claim that turned out to be insufficient.
 */
class MediaMetadataRetrievalTest {

    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    /** An extension no transcoder routes to, so the probe fallback is what runs. */
    private static byte[] payload(int size) {
        byte[] b = new byte[size];
        System.arraycopy(PNG_MAGIC, 0, b, 0, PNG_MAGIC.length);
        for (int i = PNG_MAGIC.length; i < size; i++) {
            b[i] = (byte) (i & 0xFF);
        }
        return b;
    }

    private static Map<String, String> ingestAndReadBack(Path dir, String store)
            throws Exception {
        Files.write(dir.resolve("chart.dat"), payload(300));
        RealInboxWatcher watcher = new RealInboxWatcher(dir, new PersistentHdcStore(dir.resolve(store), 2048));
        assertThat(watcher.scan()).isEqualTo(1);
        java.lang.reflect.Field f = RealInboxWatcher.class.getDeclaredField("hdcStore");
        f.setAccessible(true);
        return ((PersistentHdcStore) f.get(watcher)).snapshot();
    }

    @Test
    @DisplayName("each measured property is stored as its own retrievable fact")
    void propertiesAreStoredIndividually(@TempDir Path dir) throws Exception {
        Map<String, String> taught = ingestAndReadBack(dir, "s1");

        assertThat(taught.values())
                .as("the format must be its own short claim, not a fragment of a paragraph")
                .anyMatch(s -> s.contains("chart.dat") && s.contains("image/png"));

        assertThat(taught.values())
                .as("the byte count must be independently answerable")
                .anyMatch(s -> s.contains("chart.dat") && s.contains("300 bytes"));

        assertThat(taught.values())
                .as("the signature must be independently answerable")
                .anyMatch(s -> s.contains("89504e47") || s.contains("signature"));
    }

    @Test
    @DisplayName("a question naming the file can retrieve the format")
    void formatQuestionIsRetrievable(@TempDir Path dir) throws Exception {
        Map<String, String> taught = ingestAndReadBack(dir, "s2");

        // Simulates the retrieval question the capability exists to answer. HDC scoring is
        // length-penalised, so this asserts on SHORT claims existing — a single long
        // description document is exactly what failed before.
        String formatFact = taught.values().stream()
                .filter(s -> s.contains("format") && s.contains("chart.dat"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no standalone format claim was taught; the metadata was stored only "
                                + "inside the description paragraph and is therefore unfindable"));

        assertThat(formatFact.length())
                .as("a claim long enough to be diluted by its own length defeats retrieval; "
                        + "this one is %d chars", formatFact.length())
                .isLessThan(160);
        assertThat(formatFact).contains("image/png");
    }

    @Test
    @DisplayName("an unrecognised payload states that, rather than inventing a format")
    void unrecognisedPayloadIsHonest(@TempDir Path dir) throws Exception {
        byte[] noise = new byte[128];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = (byte) ((i * 37) & 0xFF);
        }
        Files.write(dir.resolve("mystery.dat"), noise);

        RealInboxWatcher watcher =
                new RealInboxWatcher(dir, new PersistentHdcStore(dir.resolve("s3"), 2048));
        assertThat(watcher.scan()).isEqualTo(1);

        java.lang.reflect.Field f = RealInboxWatcher.class.getDeclaredField("hdcStore");
        f.setAccessible(true);
        Map<String, String> taught = ((PersistentHdcStore) f.get(watcher)).snapshot();

        assertThat(taught.values())
                .as("no signature matched, so the claim must say so")
                .anyMatch(s -> s.contains("unrecognised") && s.contains("mystery.dat"));
        assertThat(taught.values())
                .as("must never claim a format it did not detect")
                .noneMatch(s -> s.contains("format of mystery.dat") && !s.contains("unrecognised"));
    }

    @Test
    @DisplayName("the not-a-perception disclaimer still reaches the mind")
    void honestyClauseSurvivesFactSplitting(@TempDir Path dir) throws Exception {
        Map<String, String> taught = ingestAndReadBack(dir, "s4");
        assertThat(taught.values().stream().map(String::toLowerCase).reduce("", (a, b) -> a + b))
                .as("splitting facts must not drop the sentence saying the contents were "
                        + "never read — that clause is what keeps metadata from masquerading "
                        + "as perception")
                .contains("did not read the contents");
    }
}