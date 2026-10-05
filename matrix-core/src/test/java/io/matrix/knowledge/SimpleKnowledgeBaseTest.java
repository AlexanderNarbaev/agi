package io.matrix.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W32.26 — an empty knowledge base and a broken one must not look alike.
 *
 * <p>Before this, both empty catches in {@link SimpleKnowledgeBase} were literally
 * {@code // ignore}. A listing failure left the base empty, {@code buildContext()}
 * returned {@code ""}, and the brain answered with zero grounding while {@code size()}
 * reported a perfectly plausible 0 — the same number a legitimately empty directory
 * produces. An operator had no way to tell a healthy empty mind from a broken one.</p>
 */
class SimpleKnowledgeBaseTest {

    @Test
    void aMissingDirectoryIsAnEmptyBaseAndNotABrokenOne(@TempDir Path tmp) {
        var kb = new SimpleKnowledgeBase();
        kb.loadFromDir(tmp.resolve("does-not-exist"));
        assertEquals(0, kb.size(), "no documents is the honest size");
        assertFalse(kb.listingFailed(),
            "a directory that simply is not there is not a listing FAILURE; conflating "
                + "the two would cry wolf on every fresh install");
    }

    @Test
    void anUnlistablePathIsReportedAsABrokenBase(@TempDir Path tmp) throws Exception {
        // Listing a FILE throws NotDirectoryException, which is the real shape of this
        // failure: the path exists, so the operator is not warned by its absence.
        Path notADir = Files.createFile(tmp.resolve("kb-file"));
        var kb = new SimpleKnowledgeBase();
        kb.loadFromDir(notADir);
        assertTrue(kb.listingFailed(),
            "a path that cannot be listed must be reported, or an empty base and a broken "
                + "base are indistinguishable");
    }

    @Test
    void aReadFailureCountsTheDocumentRatherThanIgnoringIt(@TempDir Path tmp) throws Exception {
        Path dir = Files.createDirectory(tmp.resolve("kb"));
        Files.writeString(dir.resolve("good.md"), "a readable document");
        // A DIRECTORY named .md: readString then throws for real, so the document is
        // dropped exactly as an unreadable one would be.
        Files.createDirectories(dir.resolve("bad.md"));
        var kb = new SimpleKnowledgeBase();
        kb.loadFromDir(dir);
        assertEquals(1, kb.skippedDocs(), "an unreadable document must be counted");
        assertEquals(1, kb.size(), "and the readable one must still load");
    }
}
