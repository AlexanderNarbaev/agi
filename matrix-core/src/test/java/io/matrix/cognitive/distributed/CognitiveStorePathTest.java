package io.matrix.cognitive.distributed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * W53 — chain A's cognitive store lives in a file that can be named.
 *
 * <p><b>What these tests hold.</b> The store used to be opened at a hard-coded relative
 * path, so every process that ran the application wrote into the same file in the working
 * directory: a benchmark, a test or a second instance could not be given a store of its
 * own, and a measurement of the distributed path was taken against a database that every
 * earlier run had grown. The path is a configuration value now, and the rule is a single
 * one: the named file wins, the environment names the file when nothing else does, and the
 * working directory's file is the last resort.</p>
 */
class CognitiveStorePathTest {

    /**
     * The named file is the one that is opened.
     */
    @Test
    @DisplayName("the named file is the one chain A opens")
    void theNamedFileIsUsed(@TempDir Path directory) {
        Path named = directory.resolve("chain-a-cognitive.sqlite");
        String previous = System.getProperty(DistributedTaskResource.COGNITIVE_PATH_PROPERTY);
        System.setProperty(DistributedTaskResource.COGNITIVE_PATH_PROPERTY, named.toString());
        try {
            assertEquals(named, DistributedTaskResource.cognitiveStorePath());
        } finally {
            restore(previous);
        }
    }

    /**
     * The rule is one rule: the property names the file, the environment names it when
     * the property does not, and the default is the working directory's file.
     */
    @Test
    @DisplayName("the property, then the environment, then the default")
    void theRuleIsOneRule(@TempDir Path directory) {
        String previous = System.getProperty(DistributedTaskResource.COGNITIVE_PATH_PROPERTY);
        System.clearProperty(DistributedTaskResource.COGNITIVE_PATH_PROPERTY);
        try {
            Path resolved = DistributedTaskResource.cognitiveStorePath();
            String environment = System.getenv(DistributedTaskResource.COGNITIVE_PATH_ENV);
            if (environment == null || environment.isBlank()) {
                assertEquals(Path.of(DistributedTaskResource.DEFAULT_COGNITIVE_PATH), resolved,
                        "without a named file, the working directory's file is the store");
            } else {
                assertEquals(Path.of(environment), resolved,
                        "the environment names the file when the property does not");
            }
        } finally {
            restore(previous);
        }
    }

    /**
     * The property wins over the environment: a caller that names the file explicitly
     * must not be overruled by an ambient variable.
     */
    @Test
    @DisplayName("the named file wins over the environment")
    void thePropertyWinsOverTheEnvironment(@TempDir Path directory) {
        String environment = System.getenv(DistributedTaskResource.COGNITIVE_PATH_ENV);
        if (environment == null || environment.isBlank()) {
            return; // nothing to win over; the rule above already holds the default
        }
        Path named = directory.resolve("explicit.sqlite");
        String previous = System.getProperty(DistributedTaskResource.COGNITIVE_PATH_PROPERTY);
        System.setProperty(DistributedTaskResource.COGNITIVE_PATH_PROPERTY, named.toString());
        try {
            assertEquals(named, DistributedTaskResource.cognitiveStorePath());
        } finally {
            restore(previous);
        }
    }

    /**
     * Puts the property back the way the test found it.
     *
     * @param previous the value the property held, or null when it held none
     */
    private static void restore(String previous) {
        if (previous == null) {
            System.clearProperty(DistributedTaskResource.COGNITIVE_PATH_PROPERTY);
        } else {
            System.setProperty(DistributedTaskResource.COGNITIVE_PATH_PROPERTY, previous);
        }
    }
}
