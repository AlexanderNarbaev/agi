package io.matrix.actions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3 — the bounded artifact writer's boundary, idempotency and crash recovery.
 *
 * <p>Every test here writes through the real implementation to a real directory. The crash
 * boundaries are exercised by constructing exactly the on-disk state a crash leaves
 * (a staged temporary file with its STAGED record, or a COMMITTED record with its final
 * file) and asking {@link ArtifactWriter#recover(String)} to resolve it.</p>
 */
class EvidenceToActionTest {

    @TempDir
    Path temporary;

    private Path root;
    private ArtifactWriter writer;

    private static final byte[] MARKDOWN = "# Report\n\nThe plate boundary evidence.\n"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] JSON_REPORT = "{\"claims\": [\"clm-1\"]}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() throws Exception {
        root = temporary.resolve("authorized");
        Files.createDirectories(root);
        writer = new ArtifactWriter(root);
    }

    @AfterEach
    void noEscapedWrite() throws Exception {
        // nothing may be written outside the authorized root by any case above
        assertThat(Files.exists(temporary.resolve("outside.md"))).isFalse();
    }

    @Test
    @DisplayName("a normal write stages, commits, and the artifact verifies")
    void normalCompletion() throws Exception {
        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
        assertThat(result.sha256()).hasSize(64);
        Path artifact = root.resolve("report.md");
        assertThat(artifact).exists();
        assertThat(Files.readString(artifact)).isEqualTo(new String(MARKDOWN, StandardCharsets.UTF_8));
        assertThat(result.sha256()).isEqualTo(sha256(artifact));
        List<ArtifactWriter.Record> records = writer.readIndex();
        assertThat(records).extracting(ArtifactWriter.Record::status)
                .containsExactly("STAGED", "COMMITTED");
        assertThat(Files.list(root).noneMatch(p -> p.getFileName().toString().contains(".tmp-"))).isTrue();
    }

    @Test
    @DisplayName("a repeated request with the same identity and content writes nothing")
    void idempotentRepeat() throws Exception {
        ArtifactWriter.Result first = writer.write("act-1", "report.md", MARKDOWN);
        java.nio.file.attribute.FileTime before = Files.getLastModifiedTime(root.resolve("report.md"));
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

    @Test
    @DisplayName("a crash before commit leaves a staged file that recovery removes")
    void crashBeforeCommit() throws Exception {
        // exactly the state a crash between the stage and the commit leaves
        Path staged = root.resolve(".report.md.tmp-act-9");
        Files.write(staged, MARKDOWN, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        writer.index().toFile().createNewFile();
        Files.writeString(writer.index(), "{\"actionId\":\"act-9\",\"fileName\":\"report.md\","
                + "\"sha256\":\"deadbeef\",\"status\":\"STAGED\",\"bytes\":10,"
                + "\"timestamp\":\"2026-11-14T00:00:00Z\"}\n", StandardOpenOption.APPEND);

        Map<String, Object> state = writer.recover("act-9");

        assertThat(state.get("disposition")).isEqualTo("NO_COMMIT");
        assertThat(state.get("stagedRemoved")).isEqualTo(true);
        assertThat(Files.exists(root.resolve("report.md"))).isFalse();
        assertThat(Files.exists(staged)).isFalse();
        // the retry after recovery writes exactly once
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
        assertThat(state.get("recoversOnRetry")).isEqualTo(false);
    }

    @Test
    @DisplayName("a committed record whose file is missing is reconciled by the retry")
    void committedButFileMissing() throws Exception {
        ArtifactWriter.Result first = writer.write("act-1", "report.md", MARKDOWN);
        Files.delete(root.resolve("report.md"));

        Map<String, Object> state = writer.recover("act-1");
        assertThat(state.get("disposition")).isEqualTo("COMMITTED_BUT_FILE_MISSING");

        ArtifactWriter.Result retry = writer.write("act-1", "report.md", MARKDOWN);
        assertThat(retry.disposition()).isEqualTo(ArtifactWriter.Disposition.RECONCILED_WRITTEN);
        assertThat(retry.sha256()).isEqualTo(first.sha256());
        assertThat(root.resolve("report.md")).exists();
    }

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
    }

    @Test
    @DisplayName("the action contract wraps the writer and reports its refusals as failures")
    void theActionContract() {
        ActionRegistry.Action action = io.matrix.actions.ArtifactAction.of(writer);

        ActionRegistry.ActionResult refused = action.execute(Map.of(
                "actionId", "act-1", "fileName", "../outside.md", "content", MARKDOWN));
        assertThat(refused.success()).isFalse();
        assertThat(refused.error()).contains("REFUSED_TRAVERSAL");

        ActionRegistry.ActionResult missing = action.execute(Map.of("actionId", "act-1"));
        assertThat(missing.success()).isFalse();

        ActionRegistry.ActionResult written = action.execute(Map.of(
                "actionId", "act-2", "fileName", "report.md", "content", MARKDOWN));
        assertThat(written.success()).isTrue();
    }

    private static String sha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
