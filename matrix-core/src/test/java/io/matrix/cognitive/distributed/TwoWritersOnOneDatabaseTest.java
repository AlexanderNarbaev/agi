package io.matrix.cognitive.distributed;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — one file, one writer.
 *
 * <p><b>What these tests hold.</b> The dispatches live in their own file and the service's
 * cognitive tables live in the knowledge file. A configuration that pointed both at one
 * file would give this process two writers on one SQLite database — the exact thing the
 * wave's boundary forbids — and the writer lock would not catch it, because both holders
 * are in this process and merely join the lock. The failure would show up later as a
 * lock contention error on an unrelated request, so the configuration is refused at the
 * moment the store is opened.</p>
 *
 * <p><b>Why the comparison is about the file and not about the spelling.</b> Three
 * spellings name one file: the path itself, the same path written with {@code ..} in
 * it, and a symbolic link to it. The third is the one a written rule is most likely to
 * miss, because {@code normalize()} removes {@code ..} and a trailing separator but does
 * not follow a link — a dispatch store opened on a link to the knowledge file writes into
 * the knowledge file, and the rule that compares spellings would have let it through.</p>
 *
 * <p><b>What they also hold.</b> A guard that refuses everything is not a guard. Two
 * different files must be let through, or the service could not be configured at all.</p>
 */
class TwoWritersOnOneDatabaseTest {

    @TempDir
    Path directory;

    /**
     * The same file under the same name is one file.
     */
    @Test
    @DisplayName("the dispatch store may not live in the knowledge file")
    void theSameFileIsRefused() {
        Path knowledge = directory.resolve("knowledge.sqlite");

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> DistributedTaskResource.refuseTheKnowledgeFile(knowledge, knowledge),
                "two stores on one file are two writers on one database");

        assertTrue(refusal.getMessage().contains("two writers on one database"),
                "the refusal must name the harm it prevents: " + refusal.getMessage());
    }

    /**
     * The same file written with {@code ..} in it is the same file.
     */
    @Test
    @DisplayName("a normalized path names the same file and is refused")
    void aNormalizedPathIsRefused() {
        Path knowledge = directory.resolve("knowledge.sqlite");
        Path spelled = directory.resolve("sub").resolve("..").resolve("knowledge.sqlite");

        assertThrows(IllegalStateException.class,
                () -> DistributedTaskResource.refuseTheKnowledgeFile(knowledge, spelled),
                "a path that walks through .. names the file it arrives at");
    }

    /**
     * A symbolic link to the knowledge file names the knowledge file.
     */
    @Test
    @DisplayName("a symbolic link to the knowledge file is refused")
    void aSymbolicLinkIsRefused() throws IOException {
        Path knowledge = directory.resolve("knowledge.sqlite");
        Files.writeString(knowledge, "the knowledge store's own file");
        Path link = directory.resolve("dispatch-alias.sqlite");
        assumeSymlinksAreSupported(link, knowledge);

        assertThrows(IllegalStateException.class,
                () -> DistributedTaskResource.refuseTheKnowledgeFile(knowledge, link),
                "a link to the knowledge file opens a second writer on it");
    }

    /**
     * A link in the directory part names the same file too.
     */
    @Test
    @DisplayName("a symbolic link to the directory of the knowledge file is refused")
    void aSymbolicLinkToTheDirectoryIsRefused() throws IOException {
        Path real = Files.createDirectories(directory.resolve("real"));
        Path knowledge = real.resolve("knowledge.sqlite");
        Files.writeString(knowledge, "the knowledge store's own file");
        Path linkDirectory = directory.resolve("linked");
        assumeSymlinksAreSupported(linkDirectory, real);

        assertThrows(IllegalStateException.class,
                () -> DistributedTaskResource.refuseTheKnowledgeFile(knowledge,
                        linkDirectory.resolve("knowledge.sqlite")),
                "a link in the directory part names the same file");
    }

    /**
     * Two different files are two stores, and the rule must let them through.
     */
    @Test
    @DisplayName("two different files are not refused")
    void twoDifferentFilesAreNotRefused() {
        Path knowledge = directory.resolve("knowledge.sqlite");
        Path dispatch = directory.resolve("dispatch.sqlite");

        assertDoesNotThrow(() -> DistributedTaskResource.refuseTheKnowledgeFile(knowledge, dispatch),
                "a dispatch store of its own is the configuration the wave asks for");
    }

    /**
     * Creates the link, unless this file system has none.
     *
     * @param link   the link to create
     * @param target the file the link names
     */
    private static void assumeSymlinksAreSupported(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false,
                    "this file system has no symbolic links: " + e.getClass().getSimpleName());
        }
    }
}
