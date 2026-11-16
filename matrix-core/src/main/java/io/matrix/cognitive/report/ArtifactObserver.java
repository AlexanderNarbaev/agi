package io.matrix.cognitive.report;

import io.matrix.actions.ArtifactWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * P2 — the observation of the effect, made by a reader that is not the writer.
 *
 * <p><b>Why this class exists.</b> The action's own report of success is not the
 * observation. A writer that says "written" and a file that is not there are two different
 * worlds, and a loop that trusts the first one has closed over nothing. The observation
 * here is read from the file system by a reader that shares nothing with the write path
 * except the root: the artifact exists, its size and its SHA-256 are measured on its
 * bytes, and the append-only index is read to see how many records it holds for the
 * identity.</p>
 *
 * <p><b>What a disagreement means.</b> A missing artifact, a digest that does not match
 * the one the response reported, or an index that holds no committed record for the
 * identity is a <b>failed effect</b> — recorded as such, never smoothed over. The caller
 * is told the effect did not happen, whatever the action said.</p>
 */
public final class ArtifactObserver {

    private ArtifactObserver() {
    }

    /**
     * What the file system says about one artifact.
     *
     * @param exists          whether the artifact is there
     * @param bytes           its measured size
     * @param sha256          its measured digest
     * @param stagedRecords   the index's STAGED records for the identity
     * @param committedRecords the index's COMMITTED records for the identity
     * @param matches         whether the measured digest is the one that was reported
     * @param detail          what the reader saw, in words
     */
    public record Observation(
            boolean exists,
            long bytes,
            String sha256,
            int stagedRecords,
            int committedRecords,
            boolean matches,
            String detail) {

        public Observation {
            detail = detail == null ? "" : detail;
        }

        /**
         * Whether the effect is verified.
         *
         * <p>The effect happened when the artifact is there, its digest is the one that
         * was reported, and the index holds exactly one committed record for the
         * identity: one artifact, one commit, one file.</p>
         */
        public boolean verified() {
            return exists && matches && committedRecords == 1;
        }
    }

    /**
     * Observes one artifact, independently of the write that produced it.
     *
     * @param writer       the writer whose root the artifact lives in
     * @param actionId     the identity to look for in the index
     * @param fileName     the artifact's plain file name
     * @param expectedSha256 the digest the response reported, or null to accept any
     * @return the observation
     * @throws IOException when the root cannot be read
     */
    public static Observation observe(ArtifactWriter writer, String actionId, String fileName,
            String expectedSha256) throws IOException {
        Path root = writer.root();
        Path artifact = root.resolve(fileName);
        boolean exists = Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS);
        long bytes = 0L;
        String digest = null;
        if (exists) {
            byte[] content = Files.readAllBytes(artifact);
            bytes = content.length;
            digest = sha256(content);
        }
        int staged = 0;
        int committed = 0;
        List<ArtifactWriter.Record> records = writer.readIndex();
        for (ArtifactWriter.Record record : records) {
            if (!record.actionId().equals(actionId)) {
                continue;
            }
            if ("STAGED".equals(record.status())) {
                staged++;
            } else if ("COMMITTED".equals(record.status())) {
                committed++;
            }
        }
        boolean matches = digest != null && (expectedSha256 == null || expectedSha256.equals(digest));
        String detail = exists
                ? "the artifact is there with " + bytes + " byte(s) and the digest "
                        + (matches ? "matches the one that was reported" : "does NOT match the one "
                                + "that was reported")
                : "the artifact is not there";
        return new Observation(exists, bytes, digest, staged, committed, matches, detail);
    }

    /**
     * The digest of some bytes.
     *
     * @param content the bytes
     * @return the SHA-256, in hex
     */
    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content));
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is unavailable", absent);
        }
    }

    /**
     * The digest of some text.
     *
     * @param content the text
     * @return the SHA-256, in hex
     */
    public static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }
}
