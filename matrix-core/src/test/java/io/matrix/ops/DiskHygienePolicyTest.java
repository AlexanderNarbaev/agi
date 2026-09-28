package io.matrix.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RECON-W20 — DiskHygienePolicy contract tests. */
class DiskHygienePolicyTest {

    private static void writeLines(Path p, int n) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append("{\"i\":").append(i).append(",\"pad\":\"").append("x".repeat(60)).append("\"}\n");
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
    }

    // --- rotation: keeps last N active + archives the rest -------------------

    @Test
    void rotationMovesOldLinesToArchiveAndKeepsTail(@TempDir Path dir) throws IOException {
        Path active = dir.resolve("episodic.ndjson");
        writeLines(active, 100);
        int before = Files.readAllLines(active).size();

        DiskHygienePolicy.RotationResult r =
                DiskHygienePolicy.rotate(active, dir.resolve("archive"), 4000, 1);

        assertThat(r.changed()).isTrue();
        assertThat(r.linesRotated()).isPositive();
        assertThat(before).isEqualTo(
                r.linesRotated() + r.activeLinesRemaining());
        // Tail retained, head archived — nothing lost.
        assertThat(r.activeLinesRemaining()).isPositive();
        assertThat(r.archivePath()).exists();
        // Archives are gzip — must be read through a decompressor, not as text.
        try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(r.archivePath()))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isNotEmpty();
        }
    }

    @Test
    void rotationPreservesAllRecordsAcrossActivePlusArchive(@TempDir Path dir) throws IOException {
        Path active = dir.resolve("e.ndjson");
        writeLines(active, 60);
        List<String> original = Files.readAllLines(active);

        DiskHygienePolicy.RotationResult r =
                DiskHygienePolicy.rotate(active, dir.resolve("arch"), 2000, 7);

        List<String> archived = new ArrayList<>();
        try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(r.archivePath()))) {
            archived = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        List<String> kept = Files.readAllLines(active);

        List<String> rejoined = new ArrayList<>(archived);
        rejoined.addAll(kept);
        assertThat(rejoined).containsExactlyElementsOf(original);
    }

    // --- idempotence ---------------------------------------------------------

    @Test
    void runningRotationTwiceIsSafeAndSecondRunIsNoOp(@TempDir Path dir) throws IOException {
        Path active = dir.resolve("e.ndjson");
        writeLines(active, 50);
        Path arch = dir.resolve("arch");

        DiskHygienePolicy.RotationResult first = DiskHygienePolicy.rotate(active, arch, 1500, 1);
        assertThat(first.changed()).isTrue();

        DiskHygienePolicy.RotationResult second = DiskHygienePolicy.rotate(active, arch, 1500, 2);
        assertThat(second.changed()).isFalse();
        assertThat(second.linesRotated()).isZero();
        // Exactly one archive exists — the second pass created nothing.
        try (var s = Files.list(arch)) {
            assertThat(s.count()).isEqualTo(1);
        }
    }

    @Test
    void smallFileIsNeverRotated(@TempDir Path dir) throws IOException {
        Path active = dir.resolve("e.ndjson");
        writeLines(active, 3);
        DiskHygienePolicy.RotationResult r =
                DiskHygienePolicy.rotate(active, dir.resolve("arch"), 1_000_000, 1);
        assertThat(r.changed()).isFalse();
        assertThat(r.activeLinesRemaining()).isEqualTo(3);
    }

    @Test
    void missingFileIsReportedNotSilentlyOk(@TempDir Path dir) throws IOException {
        DiskHygienePolicy.RotationResult r =
                DiskHygienePolicy.rotate(dir.resolve("nope.ndjson"), dir.resolve("a"), 10, 1);
        assertThat(r.changed()).isFalse();
        assertThat(r.activeLinesRemaining()).isZero();
    }

    // --- ledger --------------------------------------------------------------

    @Test
    void ledgerGrowsMonotonicallyAndNeverShrinks(@TempDir Path dir) throws IOException {
        Path ledger = dir.resolve("data/DISK-LEDGER.ndjson");
        int prev = 0;
        for (int i = 0; i < 5; i++) {
            DiskHygienePolicy.appendLedger(ledger, "{\"op\":\"w20\",\"n\":" + i + "}");
            int now = DiskHygienePolicy.ledgerLineCount(ledger);
            assertThat(now).isGreaterThan(prev);
            prev = now;
        }
        assertThat(prev).isEqualTo(5);
    }

    @Test
    void ledgerLineCountOnMissingFileIsZero(@TempDir Path dir) throws IOException {
        assertThat(DiskHygienePolicy.ledgerLineCount(dir.resolve("none.ndjson"))).isZero();
    }

    // --- classification -------------------------------------------------------

    @Test
    void cognitiveDataIsNeverClassifiedForDeletion(@TempDir Path dir) {
        for (String p : new String[]{
                "data/mind/episodic.ndjson", "data/mind/bir.ndjson",
                "data/mind/benchmarks/w13-live.csv", "data/DISK-LEDGER.ndjson",
                "data/models/teacher/teacher.onnx", "data/knowledge/x.jsonl"}) {
            assertThat(DiskHygienePolicy.classify(p, true, true)).isEqualTo("KEEP");
            assertThat(DiskHygienePolicy.classify(p, false, true)).isEqualTo("KEEP");
        }
    }

    @Test
    void buildOutputsAndUntrackedPathsAreCache() {
        assertThat(DiskHygienePolicy.classify("matrix-core/build", false, false))
                .isEqualTo("DELETE-CACHE");
        assertThat(DiskHygienePolicy.classify("data/smoke-old", false, false))
                .isEqualTo("DELETE-CACHE");
        assertThat(DiskHygienePolicy.classify("data/streaming/ndjson", true, false))
                .isEqualTo("ROTATE");
    }

    // --- tiers ---------------------------------------------------------------

    @Test
    void tiersFollowTheOperatorDiskBudgetContract() {
        assertThat(DiskHygienePolicy.Tier.of(30L * 1024 * 1024 * 1024)).isEqualTo(DiskHygienePolicy.Tier.HEALTHY);
        assertThat(DiskHygienePolicy.Tier.of(23L * 1024 * 1024 * 1024)).isEqualTo(DiskHygienePolicy.Tier.WARN);
        assertThat(DiskHygienePolicy.Tier.of(9L * 1024 * 1024 * 1024)).isEqualTo(DiskHygienePolicy.Tier.REFUSE);
    }

    // --- treeSize ------------------------------------------------------------

    @Test
    void treeSizeMeasuresRecursiveBytes(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a"), "12345");
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub/b"), "123");
        assertThat(DiskHygienePolicy.treeSize(dir)).isEqualTo(8L);
        assertThat(DiskHygienePolicy.treeSize(dir.resolve("absent"))).isZero();
    }

    // --- refusal discipline --------------------------------------------------

    @Test
    void ledgerRefusesToSilentlySwallowIoErrors(@TempDir Path dir) throws IOException {
        // A regular file standing where a directory must be: guaranteed
        // NotDirectoryException. Must surface, never be swallowed.
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "i am a file");
        Path impossible = blocker.resolve("DISK-LEDGER.ndjson");
        assertThatThrownBy(() -> DiskHygienePolicy.appendLedger(impossible, "{}"))
                .isInstanceOf(java.io.IOException.class);
    }
}
