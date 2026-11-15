package io.matrix.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONCEPT-TO-ACTION — the writer's exclusion, crash and integrity contract.
 *
 * <p>These tests pin the guarantees the HTTP route depends on, and each of them has a
 * negative control: {@code scripts/artifact-writer-negative-control.sh} removes the writer's
 * lease and shows that the concurrency test goes red without it. A guard that no test can
 * fail is not a guard, so every claim in this file is a claim that can be broken.</p>
 *
 * <p>The properties under test:</p>
 * <ul>
 *   <li><b>One writer per root.</b> Concurrent requests inside one process are serialised;
 *       a second process is refused, not queued.</li>
 *   <li><b>One identity, one artifact.</b> Concurrent requests with the same identity leave
 *       exactly one {@code STAGED} and one {@code COMMITTED} record.</li>
 *   <li><b>A record binds a name only when the artifact on disk carries its digest.</b> A
 *       forged record cannot reserve a name, and cannot exhaust the budget.</li>
 *   <li><b>The trace is bounded.</b> The index has a documented record and byte limit, and
 *       the writer fails closed at it instead of truncating evidence.</li>
 *   <li><b>Broken state fails closed.</b> A replaced root and a structurally broken index are
 *       refusals, not writes.</li>
 * </ul>
 */
class ArtifactWriterConcurrencyTest {

    @TempDir
    Path temporary;

    private Path root;
    private ArtifactWriter writer;

    private static final byte[] MARKDOWN = "# Report\n\nThe plate boundary evidence.\n"
            .getBytes(StandardCharsets.UTF_8);

    /** How many requests race for the root. */
    private static final int RACERS = 8;

    @BeforeEach
    void setUp() throws Exception {
        root = temporary.resolve("authorized");
        Files.createDirectories(root);
        writer = new ArtifactWriter(root);
    }

    // ── one writer per root ─────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent requests with one identity commit exactly one artifact")
    void concurrentSameIdentity() throws Exception {
        List<ArtifactWriter.Result> results = race(identity -> "act-same",
                index -> "report.md", index -> MARKDOWN);

        assertThat(results).allSatisfy(result ->
                assertThat(result.disposition()).isIn(ArtifactWriter.Disposition.WRITTEN,
                        ArtifactWriter.Disposition.REPLAYED));
        // exactly one racer wrote; the others found the commit and replayed it
        assertThat(results.stream()
                .filter(result -> result.disposition() == ArtifactWriter.Disposition.WRITTEN)
                .count()).isEqualTo(1);
        // one identity, one staged record and one committed record — not one pair per racer
        assertThat(recordsOf("act-same")).extracting(ArtifactWriter.Record::status)
                .containsExactlyInAnyOrder("STAGED", "COMMITTED");
        assertThat(root.resolve("report.md")).exists();
        assertThat(sha256(root.resolve("report.md"))).isEqualTo(sha256OfBytes(MARKDOWN));
    }

    @Test
    @DisplayName("concurrent requests with distinct identities commit distinct artifacts")
    void concurrentDistinctIdentities() throws Exception {
        List<ArtifactWriter.Result> results = race(
                index -> "act-" + index,
                index -> "report-" + index + ".md",
                index -> ("# Report " + index + "\n").getBytes(StandardCharsets.UTF_8));

        assertThat(results).allSatisfy(result ->
                assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN));
        for (int index = 0; index < RACERS; index++) {
            assertThat(recordsOf("act-" + index)).extracting(ArtifactWriter.Record::status)
                    .containsExactlyInAnyOrder("STAGED", "COMMITTED");
            assertThat(root.resolve("report-" + index + ".md")).exists();
        }
        // every record in the trace names an artifact that exists with the recorded digest
        for (ArtifactWriter.Record record : writer.readIndex()) {
            Path artifact = root.resolve(record.fileName());
            assertThat(artifact).exists();
            assertThat(sha256(artifact)).isEqualTo(record.sha256());
        }
    }

    @Test
    @DisplayName("a second process holding the root's lease is refused, not queued")
    void secondProcessIsRefused() throws Exception {
        LeaseHolder holder = LeaseHolder.start(root.resolve(ArtifactWriter.LOCK_FILE));
        try {
            ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

            assertThat(result.disposition())
                    .isEqualTo(ArtifactWriter.Disposition.REFUSED_WRITER_CONFLICT);
            assertThat(result.written()).isFalse();
            assertThat(root.resolve("report.md")).doesNotExist();
            // the refusal leaves no trace of a write that never happened
            assertThat(writer.readIndex()).isEmpty();
        } finally {
            holder.stop();
        }
        // once the lease is released the root accepts writes again
        assertThat(writer.write("act-1", "report.md", MARKDOWN).disposition())
                .isEqualTo(ArtifactWriter.Disposition.WRITTEN);
    }

    // ── the trace is bounded, and a forged record binds nothing ─────────────

    @Test
    @DisplayName("forged records reserve no name and do not exhaust the budget")
    void forgedRecordsBindNothing() throws Exception {
        for (int index = 0; index < ArtifactWriter.MAX_ARTIFACTS; index++) {
            appendRecord("forged-" + index, "forged-" + index + ".md", "0".repeat(64), "COMMITTED");
        }

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
        assertThat(root.resolve("report.md")).exists();
    }

    @Test
    @DisplayName("the artifact budget counts the artifacts the root really holds")
    void artifactBudgetCountsRealArtifacts() throws Exception {
        for (int index = 0; index < ArtifactWriter.MAX_ARTIFACTS; index++) {
            String name = "held-" + index + ".md";
            byte[] content = ("# Held " + index + "\n").getBytes(StandardCharsets.UTF_8);
            Files.write(root.resolve(name), content);
            appendRecord("held-" + index, name, sha256OfBytes(content), "COMMITTED");
        }

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_BUDGET);
        assertThat(root.resolve("report.md")).doesNotExist();
    }

    @Test
    @DisplayName("the index record bound fails closed instead of truncating the trace")
    void indexRecordBound() throws Exception {
        for (int index = 0; index < ArtifactWriter.MAX_INDEX_RECORDS; index++) {
            appendRecord("bulk-" + index, "bulk-" + index + ".md", "0".repeat(64), "COMMITTED");
        }
        long recordsBefore = writer.readIndex().size();

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_BUDGET);
        assertThat(root.resolve("report.md")).doesNotExist();
        // the evidence is preserved: the trace was not truncated to make room
        assertThat(writer.readIndex()).hasSize((int) recordsBefore);
    }

    // ── broken state fails closed ───────────────────────────────────────────

    @Test
    @DisplayName("a replaced root is refused")
    void replacedRootIsRefused() throws Exception {
        Files.delete(root);
        Files.createDirectories(root);

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_ROOT_REPLACED);
        assertThat(root.resolve("report.md")).doesNotExist();
    }

    @Test
    @DisplayName("a root replaced by a symbolic link is refused")
    void symlinkedRootIsRefused() throws Exception {
        Path elsewhere = temporary.resolve("elsewhere");
        Files.createDirectories(elsewhere);
        Files.delete(root);
        Files.createSymbolicLink(root, elsewhere);

        ArtifactWriter.Result result = writer.write("act-1", "report.md", MARKDOWN);

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_ROOT_REPLACED);
        assertThat(elsewhere.resolve("report.md")).doesNotExist();
    }

    @Test
    @DisplayName("a structurally broken index is refused, never repaired")
    void corruptIndexIsRefused() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);
        // a malformed line that is NOT the last one: the log's order is broken
        Files.writeString(writer.index(),
                "{not a record\n" + Files.readString(writer.index()),
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        ArtifactWriter.Result result = writer.write("act-2", "other.md",
                "# Other\n".getBytes(StandardCharsets.UTF_8));

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.REFUSED_INDEX_CORRUPT);
        assertThat(root.resolve("other.md")).doesNotExist();
        // the evidence is left exactly as it was
        assertThat(Files.readString(writer.index())).startsWith("{not a record");
    }

    @Test
    @DisplayName("an incomplete tail is repaired, and the writes after it still work")
    void incompleteTailIsRepaired() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);
        // a crash in the middle of an append leaves a fragment that was never a record
        Files.writeString(writer.index(), "{\"actionId\":\"act-2\",\"fileName\":\"bro",
                StandardOpenOption.WRITE, StandardOpenOption.APPEND);

        ArtifactWriter.Result second = writer.write("act-2", "other.md",
                "# Other\n".getBytes(StandardCharsets.UTF_8));
        ArtifactWriter.Result third = writer.write("act-3", "third.md",
                "# Third\n".getBytes(StandardCharsets.UTF_8));

        assertThat(second.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
        // the write that terminated the fragment must not leave the root unusable
        assertThat(third.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
        assertThat(third.reason()).isNull();
        // the fragment is gone and every record that was a record is still there
        assertThat(writer.readIndex()).extracting(ArtifactWriter.Record::actionId)
                .containsExactly("act-1", "act-1", "act-2", "act-2", "act-3", "act-3");
    }

    @Test
    @DisplayName("a complete record that lost its newline is kept, not removed")
    void completeTailIsKept() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);
        // the record's bytes are there and only its newline was lost
        String tail = "{\"actionId\":\"act-2\",\"fileName\":\"other.md\","
                + "\"sha256\":\"" + sha256OfBytes("# Other\n".getBytes(StandardCharsets.UTF_8))
                + "\",\"status\":\"COMMITTED\",\"bytes\":7,"
                + "\"timestamp\":\"2026-11-15T00:00:00Z\"}";
        Files.writeString(writer.index(), tail, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

        ArtifactWriter.Result result = writer.write("act-3", "third.md",
                "# Third\n".getBytes(StandardCharsets.UTF_8));

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
        assertThat(writer.readIndex()).extracting(ArtifactWriter.Record::actionId)
                .contains("act-2", "act-3");
    }

    @Test
    @DisplayName("a partial trailing record is tolerated, and the next write works")
    void partialTrailingRecordIsTolerated() throws Exception {
        writer.write("act-1", "report.md", MARKDOWN);
        // a crash in the middle of an append leaves a line with no newline
        Files.writeString(writer.index(), "{\"actionId\":\"act-2\",\"fileName\":\"bro",
                StandardOpenOption.WRITE, StandardOpenOption.APPEND);

        ArtifactWriter.Result result = writer.write("act-2", "other.md",
                "# Other\n".getBytes(StandardCharsets.UTF_8));

        assertThat(result.disposition()).isEqualTo(ArtifactWriter.Disposition.WRITTEN);
        assertThat(recordsOf("act-2")).extracting(ArtifactWriter.Record::status)
                .containsExactlyInAnyOrder("STAGED", "COMMITTED");
    }

    @Test
    @DisplayName("a hard link at the target leaves the other link's content intact")
    void hardLinkAtTarget() throws Exception {
        Path outside = temporary.resolve("outside.md");
        Files.writeString(outside, "# outside\n");
        Files.createLink(root.resolve("report.md"), outside);

        writer.write("act-1", "report.md", MARKDOWN);

        assertThat(Files.readString(outside)).isEqualTo("# outside\n");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Runs {@link #RACERS} writes at the same time and collects their outcomes. */
    private List<ArtifactWriter.Result> race(
            java.util.function.IntFunction<String> identity,
            java.util.function.IntFunction<String> fileName,
            java.util.function.IntFunction<byte[]> content) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Callable<ArtifactWriter.Result>> calls = new ArrayList<>();
            for (int index = 0; index < RACERS; index++) {
                int racer = index;
                calls.add(() -> {
                    start.await();
                    return writer.write(identity.apply(racer), fileName.apply(racer),
                            content.apply(racer));
                });
            }
            List<Future<ArtifactWriter.Result>> futures = new ArrayList<>();
            for (Callable<ArtifactWriter.Result> call : calls) {
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<ArtifactWriter.Result> results = new ArrayList<>();
            for (Future<ArtifactWriter.Result> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** The records of one identity. */
    private List<ArtifactWriter.Record> recordsOf(String actionId) throws IOException {
        return writer.readIndex().stream()
                .filter(record -> record.actionId().equals(actionId))
                .collect(Collectors.toList());
    }

    /** Appends one record to the index directly, as a forged or seeded writer would. */
    private void appendRecord(String actionId, String fileName, String sha256, String status)
            throws IOException {
        String line = "{\"actionId\":\"" + actionId + "\",\"fileName\":\"" + fileName
                + "\",\"sha256\":\"" + sha256 + "\",\"status\":\"" + status
                + "\",\"bytes\":1,\"timestamp\":\"2026-11-15T00:00:00Z\"}\n";
        Files.writeString(writer.index(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String sha256(Path path) throws Exception {
        return sha256OfBytes(Files.readAllBytes(path));
    }

    private static String sha256OfBytes(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
