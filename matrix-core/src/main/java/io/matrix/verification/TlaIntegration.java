package io.matrix.verification;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Integration with TLA+ model checker for formal verification.
 * 
 * Provides utilities to run TLC model checker on TLA+ specs
 * and parse results into VerificationResult format.
 */
@ApplicationScoped
public class TlaIntegration {

    private static final Logger log = LoggerFactory.getLogger(TlaIntegration.class);

    /**
     * Check if TLC is available on the system.
     */
    public boolean isTlcAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("tlc2", "-version");
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("TLC not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get list of TLA+ spec files in the project.
     */
    public List<String> getSpecFiles() {
        try {
            Path formalDir = Path.of("formal");
            if (Files.isDirectory(formalDir)) {
                return Files.list(formalDir)
                        .filter(p -> p.toString().endsWith(".tla"))
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Failed to list TLA+ specs: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Get spec file contents for verification.
     */
    public Map<String, String> getSpecContents(String specName) {
        try {
            Path specPath = Path.of("formal", specName);
            if (Files.exists(specPath)) {
                return Map.of("name", specName, "content", Files.readString(specPath));
            }
        } catch (Exception e) {
            log.warn("Failed to read spec {}: {}", specName, e.getMessage());
        }
        return Map.of();
    }

    /**
     * Get available TLA+ specifications summary.
     */
    public Map<String, Object> getSpecsSummary() {
        List<String> specs = getSpecFiles();
        return Map.of(
                "specs", specs,
                "count", specs.size(),
                "tlcAvailable", isTlcAvailable()
        );
    }
}
