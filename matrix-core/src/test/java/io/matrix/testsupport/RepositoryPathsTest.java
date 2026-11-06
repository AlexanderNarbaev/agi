package io.matrix.testsupport;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W48-02 — the contract of the repository-root resolution.
 *
 * <p>The source-integrity tests must fail when the file they verify is missing. A test
 * that cannot find its subject proves nothing, and the defect this class exists to close
 * was exactly that: a test that passed on one machine and failed on every other.
 */
class RepositoryPathsTest {

    @Test
    void theRootResolvesToTheRepositoryThatHoldsTheBuild() {
        Path root = RepositoryPaths.root();
        assertThat(root).isDirectory();
        assertThat(Files.isRegularFile(root.resolve("settings.gradle")))
                .as("the resolved root must be the repository root")
                .isTrue();
    }

    @Test
    void theRootIsTheOneTheBuildDeclares() {
        String declared = System.getProperty(RepositoryPaths.ROOT_PROPERTY);
        assertThat(declared)
                .as("the build must declare the repository root for every test task")
                .isNotBlank();
        assertThat(RepositoryPaths.root()).isEqualTo(Path.of(declared).toAbsolutePath().normalize());
    }

    @Test
    void aMissingSourceFileFailsLoudlyInsteadOfPassingQuietly() {
        assertThatThrownBy(() -> RepositoryPaths.source("matrix-core/src/main/java/NoSuchFile.java"))
                .as("a source-integrity test must not be able to pass without its subject")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void theTextOfAnExistingSourceFileIsReturned() {
        String text = RepositoryPaths.sourceText("matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java");
        assertThat(text).contains("class KnowledgeStore");
    }

    @Test
    void aRuntimeArtefactMayBeAbsent() {
        // The runtime artefacts are not tracked in git: their absence is normal and
        // must not be an exception.
        Path path = RepositoryPaths.maybe("data/mind/mind.sqlite");
        assertThat(path).isNotNull();
        assertThat(path.getParent()).isNotNull();
    }
}
