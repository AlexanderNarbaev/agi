package io.matrix.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3 — the bounded artifact writer's boundary, idempotency and crash recovery.
 *
 * <p>Every test writes through the real implementation to a real directory. The crash
 * boundaries are exercised by constructing exactly the on-disk state a crash leaves
 * (a staged temporary file with its STAGED record, a final file whose COMMITTED append never
 * happened, a tampered artifact) and asking the writer's own recovery to resolve it. The
 * independent review found three classes of defect that these tests now pin: a planted
 * symlink at the deterministic staged path or at the index (the writer must refuse, never
 * follow), the crash window between the atomic move and the COMMITTED append (the retry must
 * report the commit, not overwrite it), and a stale digest replay (the retry must re-hash the
 * artifact, not trust the record).</p>
 */
class EvidenceToActionTest {

    @TempDir
    Path temporary;

    private Path root;
    private ArtifactWriter writer;

    private static final byte[] MARKDOWN = "# Report\n\nThe plate boundary evidence.\n"
            .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() throws Exception {
        root = temporary.resolve("authorized");
        Files.createDirectories(root);
        writer = new ArtifactWriter(root);
    }

    // ── the normal path ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a normal write stages, commits, and the artifact verifies")
    void normalCompletion() throws Exception {
        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
        assertThat(result.sha256()).hasSize(64);
        Path artifact = root.resolve("report.md");
        assertThat(artifact).exists();
        assertThat(result.sha256()).isEqualTo(sha256(artifact));
        assertThat(writer.readIndex()).extracting(ArtifactWriter.Record::status)
                .containsExactly("STAGED", "COMMITTED");
        assertThat(Files.list(root).noneMatch(p -> p.getFileName().toString().contains(".tmp-")))
                .isTrue();
    }

    @Test
    @DisplayName("a repeated request with the same identity and content writes nothing")
    void idempotentRepeat() throws Exception {
        ArtifactWriter.Result first = writer.write("act-1", "report.md", MARKDOWN);
        var before = Files.getLastModifiedTime(root.resolve("report.md"));
        int recordsBefore = writer.readIndex().size();

        ArtifactWriter.Result second = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(second.disposition()).isEqualTo(ArtifactWriter.Disposition.REPLAYED);
        assertThat(second.sha256()).isEqualTo(first.sha256());
        assertThat(writer.readIndex()).hasSize(recordsBefore);
        assertThat(Files.getLastModifiedTime(root.resolve("report.md"))).isEqualTo(before);
    }

    @Test
    @DisplayName("the same identity with different content is refused, not overwritten")
    void identityNamesOneArtifact() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);
        byte[] other = "# Another report\n".getBytes(StandardCharsets.UTF_8);
        ArtifactWriter.Result second = writer.write("act-1", "report.md", other);

        assertThat(second.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_IDENTITY);
        assertThat(Files.readString(root.resolve("report.md")))
                .isEqualTo(new String(MARKDOWN, StandardCharsets.UTF_8));
    }

    // ── the crash boundaries ────────────────────────────────────────────────

    @Test
    @DisplayName("a crash before commit leaves a staged file that recovery removes")
    void crashBeforeCommit() throws Exception {
        // exactly the state a crash between the stage and the commit leaves
        Path staged = root.resolve(ArtifactWriter.stagedName("report.md", "act-9"));
        Files.write(staged, MARKDOWN, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        writer.index().toFile().createNewFile();
        Files.writeString(writer.index(), "{\"actionId\":\"act-9\",\"fileName\":\"report.md\","
                + "\"sha256\":\"" + sha256(staged) + "\",\"status\":\"STAGED\",\"bytes\":10,"
                + "\"timestamp\":\"2026-11-14T00:00:00Z\"}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        Map<String, Object> state = writer.recover("act-9");

        assertThat(state.get("disposition")).isEqualTo("NO_COMMIT");
        assertThat(state.get("stagedRemoved")).isEqualTo(true);
        assertThat(Files.exists(root.resolve("report.md"))).isFalse();
        assertThat(Files.exists(staged)).isFalse();
        assertThat(writer.write("act-9", "report.md", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.WRITTEN);
    }

    @Test
    @DisplayName("a crash after the commit but before the response: recovery finds the commit")
    void crashAfterCommit() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);

        Map<String, Object> state = writer.recover("act-1");

        assertThat(state.get("disposition")).isEqualTo("COMMITTED");
        assertThat(state.get("finalPresent")).isEqualTo(true);
        assertThat(state.get("finalMatchesDigest")).isEqualTo(true);
        assertThat(state.get("recoversOnRetry")).isEqualTo(false);
    }

    @Test
    @DisplayName("a crash between the atomic move and the COMMITTED append is still a commit")
    void crashBetweenMoveAndCommit() throws Exception {
        // construct that exact window: the final file exists, only the STAGED record was
        // persisted
        Files.write(root.resolve("report.md"), MARKDOWN);
        String digest = sha256(root.resolve("report.md"));
        Files.writeString(writer.index(), "{\"actionId\":\"act-7\",\"fileName\":\"report.md\","
                + "\"sha256\":\"" + digest + "\",\"status\":\"STAGED\",\"bytes\":"
                + MARKDOWN.length + ",\"timestamp\":\"2026-11-14T00:00:00Z\"}\n");

        // the retry with the same content reports the commit and does not write again
        ArtifactWriter.Result replay = writer.write("act-7", "report.md", MARKDOWN);
        assertThat(replay.disposition()).isEqualTo(ArtifactWriter.Disposition.REPLAYED);

        // the retry with different content is refused: the artifact is committed
        ArtifactWriter.Result other = writer.write("act-7", "report.md",
                "# Different\n".getBytes(StandardCharsets.UTF_8));
        assertThat(other.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_IDENTITY);

        Map<String, Object> state = writer.recover("act-7");
        assertThat(state.get("disposition")).isEqualTo("COMMITTED");
    }

    @Test
    @DisplayName("another identity cannot take over the crash window's committed artifact")
    void crossIdentityCrashWindow() throws Exception {
        // the exact state the writer produces when the COMMITTED append fails: the final
        // file plus only the first identity's STAGED record
        Files.write(root.resolve("report.md"), MARKDOWN);
        Files.writeString(writer.index(), "{\"actionId\":\"act-a\",\"fileName\":\"report.md\","
                + "\"sha256\":\"" + sha256(root.resolve("report.md")) + "\",\"status\":\"STAGED\","
                + "\"bytes\":" + MARKDOWN.length + ",\"timestamp\":\"2026-11-14T00:00:00Z\"}\n");

        ArtifactWriter.Result takeover = writer.write("act-b", "report.md",
                "# Takeover\n".getBytes(StandardCharsets.UTF_8));

        assertThat(takeover.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_IDENTITY);
        assertThat(Files.readString(root.resolve("report.md")))
                .isEqualTo(new String(MARKDOWN, StandardCharsets.UTF_8));
        // the owner's retry still replays its own artifact
        assertThat(writer.write("act-a", "report.md", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REPLAYED);
    }

    @Test
    @DisplayName("a control character or NUL in the name is a disposition, not an exception")
    void controlCharacterNames() {
        assertThat(writer.write("act-1", "re\u0000port.md", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_TRAVERSAL);
        assertThat(writer.write("act-2", "re\u0007port.md", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_TRAVERSAL);
    }

    @Test
    @DisplayName("a hard-linked index is refused, never appended through")
    void hardLinkedIndex() throws Exception {
        Path outside = temporary.resolve("outside-index.ndjson");
        Files.writeString(outside, "");
        Files.createLink(root.resolve(ArtifactWriter.INDEX_FILE), outside);

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_SYMLINK_ESCAPE);
        assertThat(Files.size(outside)).isZero();
    }

    @Test
    @DisplayName("a commit record whose file is missing is reconciled by the retry")
    void committedButFileMissing() throws Exception {
        ArtifactWriter.Result first = writer.write("act-1", "report.md", MARKDOWN);
        Files.delete(root.resolve("report.md"));

        Map<String, Object> state = writer.recover("act-1");
        assertThat(state.get("disposition")).isEqualTo("COMMITTED_BUT_CONTENT_DIFFERS");

        ArtifactWriter.Result retry = writer.write("act-1", "report.md", MARKDOWN);
        assertThat(retry.disposition()).isEqualTo(ArtifactWriter.Disposition.RECONCILED_WRITTEN);
        assertThat(retry.sha256()).isEqualTo(first.sha256());
        assertThat(root.resolve("report.md")).exists();
    }

    @Test
    @DisplayName("a tampered artifact is re-hashed, never replayed from the record")
    void tamperedArtifactIsNotReplayed() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);
        Files.writeString(root.resolve("report.md"), "# Tampered\n", StandardOpenOption.TRUNCATE_EXISTING);

        ArtifactWriter.Result retry = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(retry.disposition()).isEqualTo(ArtifactWriter.Disposition.RECONCILED_WRITTEN);
        assertThat(Files.readString(root.resolve("report.md")))
                .isEqualTo(new String(MARKDOWN, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("an artifact name committed by one identity cannot be taken by another")
    void otherIdentityCannotTakeTheName() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);

        ArtifactWriter.Result second = writer.write("act-2", "report.md",
                "# Other\n".getBytes(StandardCharsets.UTF_8));

        assertThat(second.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_IDENTITY);
        assertThat(Files.readString(root.resolve("report.md")))
                .isEqualTo(new String(MARKDOWN, StandardCharsets.UTF_8));
        // and the first identity's retry still replays its own digest, measured
        ArtifactWriter.Result retry = writer.write("act-1", "report.md", MARKDOWN);
        assertThat(retry.disposition()).isEqualTo(ArtifactWriter.Disposition.REPLAYED);
        assertThat(retry.sha256()).isEqualTo(sha256(root.resolve("report.md")));
    }

    @Test
    @DisplayName("a torn index line is tolerated; the next write still works")
    void tornIndexLine() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);
        Files.writeString(writer.index(), "{\"actionId\":\"act-2\",\"fileName\":\"broken",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        ArtifactWriter.Result second = writer.write("act-2", "other.md",
                "# Other report\n".getBytes(StandardCharsets.UTF_8));

        assertThat(second.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
        // the torn line must not swallow the next write's own records
        List<ArtifactWriter.Record> records = writer.readIndex().stream()
                .filter(r -> r.actionId().equals("act-2")).toList();
        assertThat(records).extracting(ArtifactWriter.Record::status)
                .containsExactly("STAGED", "COMMITTED");
    }

    // ── the boundary ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a destination outside the root is refused")
    void unauthorizedDestination() {
        ArtifactWriter.Result result = writer.write("act-1", "../outside.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_TRAVERSAL);
        assertThat(result.written()).isFalse();
        assertThat(temporary.resolve("outside.md")).doesNotExist();
    }

    @Test
    @DisplayName("a symbolic link that escapes the root is refused")
    void symlinkEscape() throws Exception {
        Path outside = temporary.resolve("symlink-target.md");
        Files.writeString(outside, "# outside\n");
        Files.createSymbolicLink(root.resolve("linked.md"), outside);

        ArtifactWriter.Result result = writer.write("act-1", "linked.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_SYMLINK_ESCAPE);
        assertThat(Files.readString(outside)).isEqualTo("# outside\n");
    }

    @Test
    @DisplayName("a symlink planted at the staged temporary path is refused, never followed")
    void symlinkedStagedPath() throws Exception {
        Path victim = temporary.resolve("victim.md");
        Files.writeString(victim, "# victim\n");
        Files.createSymbolicLink(root.resolve(ArtifactWriter.stagedName("report.md", "act-1")),
                victim);

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_SYMLINK_ESCAPE);
        assertThat(Files.readString(victim)).isEqualTo("# victim\n");
        assertThat(Files.exists(root.resolve("report.md"))).isFalse();
    }

    @Test
    @DisplayName("a symlink planted at the index path is refused, never followed")
    void symlinkedIndex() throws Exception {
        Path victim = temporary.resolve("victim-index.ndjson");
        Files.writeString(victim, "");
        Files.createSymbolicLink(root.resolve(ArtifactWriter.INDEX_FILE), victim);

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_SYMLINK_ESCAPE);
        assertThat(Files.size(victim)).isZero();
    }

    @Test
    @DisplayName("the reserved index name and a directory part are refused")
    void reservedNames() {
        assertThat(writer.write("act-1", ArtifactWriter.INDEX_FILE, MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_TRAVERSAL);
        assertThat(writer.write("act-1", "sub/report.md", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_TRAVERSAL);
        assertThat(writer.write("act-1", ".hidden.md", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_TRAVERSAL);
        assertThat(writer.write("act-1", "", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_EMPTY_NAME);
        assertThat(writer.write("", "report.md", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_IDENTITY);
    }

    @Test
    @DisplayName("an unsupported type, an oversized artifact and invalid content are refused")
    void contentBoundary() {
        assertThat(writer.write("act-1", "report.txt", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_UNSUPPORTED_TYPE);

        byte[] oversized = new byte[ArtifactWriter.MAX_ARTIFACT_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) 'x');
        assertThat(writer.write("act-2", "report.md", oversized).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_TOO_LARGE);

        assertThat(writer.write("act-3", "report.json", "not json".getBytes(StandardCharsets.UTF_8))
                .disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_INVALID_CONTENT);
        assertThat(writer.write("act-4", "report.md", "no heading".getBytes(StandardCharsets.UTF_8))
                .disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_INVALID_CONTENT);
        assertThat(writer.write("act-5", "report.md", new byte[0]).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_INVALID_CONTENT);
    }

    @Test
    @DisplayName("an I/O failure is a write failure, not an invalid content")
    void writeFailure() throws Exception {
        Set<PosixFilePermission> writable = Files.getPosixFilePermissions(root);
        Files.setPosixFilePermissions(root, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE));
        try {
            ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);
            assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_WRITE_FAILED);
        } finally {
            Files.setPosixFilePermissions(root, writable);
        }
    }

    @Test
    @DisplayName("recovery never deletes a committed artifact whose name resembles a staged file")
    void recoveryNeverDeletesArtifacts() throws Exception {
        ArtifactWriter.Result written = writer.write("act-1", "weird.tmp-act-1.md", MARKDOWN);
        assertThat(written.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);

        writer.recover("act-1");

        assertThat(root.resolve("weird.tmp-act-1.md")).exists();
    }

    @Test
    @DisplayName("a forged index record cannot suppress a write or certify an outside path")
    void forgedRecordIsNotTrusted() throws Exception {
        Files.writeString(root.resolve("victim.md"), "# the real victim\n");
        byte[] content = "# New artifact\n".getBytes(StandardCharsets.UTF_8);
        String digest = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(content));
        Files.writeString(writer.index(), "{\"actionId\":\"poison\",\"fileName\":\"victim.md\","
                + "\"sha256\":\"" + digest + "\",\"status\":\"COMMITTED\",\"bytes\":10,"
                + "\"timestamp\":\"2026-11-14T00:00:00Z\"}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Files.writeString(writer.index(), "{\"actionId\":\"poison2\",\"fileName\":\"../../etc/x.md\","
                + "\"sha256\":\"" + digest + "\",\"status\":\"COMMITTED\",\"bytes\":10,"
                + "\"timestamp\":\"2026-11-14T00:00:00Z\"}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        // the forged record binds the identity to victim.md; a request for new.md must not
        // be redirected to victim.md, and must not be silently treated as a replay either
        ArtifactWriter.Result forged = writer.write("poison", "new.md", content);
        assertThat(forged.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_IDENTITY);
        assertThat(root.resolve("new.md")).doesNotExist();
        assertThat(Files.readString(root.resolve("victim.md"))).isEqualTo("# the real victim\n");
        // the traversal record is invalid and skipped entirely: a fresh identity writes normally
        ArtifactWriter.Result normal = writer.write("act-1", "report.md", MARKDOWN);
        assertThat(normal.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
    }

    @Test
    @DisplayName("a failed move cleans the staged file and leaves the artifact unwritten")
    void moveFailureLeavesNoResidue() throws Exception {
        Files.createDirectory(root.resolve("report.md"));

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.written()).isFalse();
        assertThat(Files.list(root).noneMatch(p -> p.getFileName().toString().contains(".tmp-")))
                .isTrue();
        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_WRITE_FAILED);
    }

    @Test
    @DisplayName("the artifact budget bounds the root")
    void artifactBudget() throws Exception {
        for (int index = 0; index < ArtifactWriter.MAX_ARTIFACTS; index++) {
            Files.writeString(writer.index(), "{\"actionId\":\"a" + index + "\",\"fileName\":\"f"
                    + index + ".md\",\"sha256\":\"" + "0".repeat(64)
                    + "\",\"status\":\"COMMITTED\",\"bytes\":1,"
                    + "\"timestamp\":\"2026-11-14T00:00:00Z\"}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_BUDGET);
    }

    @Test
    @DisplayName("invalid JSON that trails a valid value is refused")
    void strictJson() {
        assertThat(writer.write("act-1", "report.json",
                "{\"a\":1} THIS IS NOT JSON".getBytes(StandardCharsets.UTF_8)).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REFUSED_INVALID_CONTENT);
        assertThat(writer.write("act-2", "report.json", "null".getBytes(StandardCharsets.UTF_8))
                .disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_INVALID_CONTENT);
        assertThat(writer.write("act-3", "report.json", "42".getBytes(StandardCharsets.UTF_8))
                .disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_INVALID_CONTENT);
    }

    @Test
    @DisplayName("a replay is not refused by the artifact budget")
    void replayIsNotBlockedByTheBudget() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);
        for (int index = 0; index < ArtifactWriter.MAX_ARTIFACTS - 1; index++) {
            Files.writeString(writer.index(), "{\"actionId\":\"seed" + index + "\",\"fileName\":\"s"
                    + index + ".md\",\"sha256\":\"" + "0".repeat(64)
                    + "\",\"status\":\"COMMITTED\",\"bytes\":1,"
                    + "\"timestamp\":\"2026-11-14T00:00:00Z\"}\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        ArtifactWriter.Result replay = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(replay.disposition()).isEqualTo(ArtifactWriter.Disposition.REPLAYED);
    }

    @Test
    @DisplayName("a STAGED-append failure leaves no staged file behind")
    void stagedAppendFailureLeavesNoResidue() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);   // creates the index
        Set<PosixFilePermission> indexPermissions = Files.getPosixFilePermissions(writer.index());
        Files.setPosixFilePermissions(writer.index(), Set.of(PosixFilePermission.OWNER_READ));
        try {
            ArtifactWriter.Result result = writer.write("act-2", "other.md",
                    "# Other\n".getBytes(StandardCharsets.UTF_8));
            assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_WRITE_FAILED);
            assertThat(Files.list(root).noneMatch(p ->
                    p.getFileName().toString().contains(".tmp-"))).isTrue();
        } finally {
            Files.setPosixFilePermissions(writer.index(), indexPermissions);
        }
    }

    @Test
    @DisplayName("recovery reports a refused index rather than a crash state")
    void recoveryRefusesALinkedIndex() throws Exception {
        Path outside = temporary.resolve("linked-index.ndjson");
        Files.writeString(outside, "");
        Files.createSymbolicLink(writer.index(), outside);

        Map<String, Object> state = writer.recover("act-1");

        assertThat(state.get("disposition")).isEqualTo("INDEX_REFUSED");
    }

    @Test
    @DisplayName("the action contract wraps the writer and reports its refusals as failures")
    void theActionContract() {
        ActionRegistry.Action action = ArtifactAction.of(writer);

        ActionRegistry.ActionResult refused = action.execute(Map.of(
                "actionId", "act-1", "fileName", "../outside.md", "content", MARKDOWN));
        assertThat(refused.success()).isFalse();
        assertThat(refused.error()).contains("REFUSED_TRAVERSAL");

        ActionRegistry.ActionResult missing = action.execute(Map.of("actionId", "act-1"));
        assertThat(missing.success()).isFalse();

        ActionRegistry.ActionResult written = action.execute(Map.of(
                "actionId", "act-2", "fileName", "report.md", "content", MARKDOWN));
        assertThat(written.success()).isTrue();
        assertThat(written.output()).isInstanceOf(ArtifactWriter.Result.class);
    }

    private static String sha256(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
