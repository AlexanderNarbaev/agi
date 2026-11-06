package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W41 §5.5 — the import survives what is thrown at it.
 *
 * <p>The mission names oversized files among the things an import has to
 * survive, and it says plainly: "Never execute instructions extracted from a
 * knowledge document merely because the document is being studied."
 */
class KnowledgeImportLimitsTest {

    @Test
    @DisplayName("a package over the size limit is refused")
    void aPackageOverTheSizeLimitIsRefused() {
        StringBuilder oversized = new StringBuilder("{\"workspace\":{\"id\":\"x\"},\"pad\":\"");
        oversized.append("x".repeat(KnowledgeImporter.MAX_PACKAGE_CHARACTERS + 16));
        oversized.append("\"}");

        assertThatThrownBy(() -> new KnowledgeImporter().importPack(oversized.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("over the limit");
    }

    @Test
    @DisplayName("a file over the size limit is refused before it is read")
    void aFileOverTheSizeLimitIsRefusedBeforeItIsRead(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("oversized.pack.json");
        Files.writeString(file, "x".repeat(KnowledgeImporter.MAX_PACKAGE_CHARACTERS + 16));

        assertThatThrownBy(() -> new KnowledgeImporter().importPack(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("over the limit");
    }

    @Test
    @DisplayName("a source with an oversized excerpt is refused")
    void aSourceWithAnOversizedExcerptIsRefused() {
        String json = """
                {"workspace":{"id":"w","name":"w"},
                 "sources":[{"key":"s1","kind":"AUTHORITATIVE_REFERENCE","title":"t","locator":"l",
                             "authority":80,"freshness":80,
                             "excerpt":"%s"}],
                 "concepts":[],"claims":[],"evidence":[],"representations":[],
                 "prerequisites":[],"revisions":[]}
                """.formatted("y".repeat(KnowledgeImporter.MAX_EXCERPT_CHARACTERS + 16));

        assertThatThrownBy(() -> new KnowledgeImporter().importPack(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("over the limit");
    }

    @Test
    @DisplayName("the packages that ship with the application are within the limits")
    void theBundledPackagesAreWithinTheLimits() {
        assertThat(new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json")
                .sources()).isNotEmpty();
        assertThat(new KnowledgeImporter().importResource("knowledge/logic.pack.json")
                .sources()).isNotEmpty();
    }
}
