package io.matrix.noosphere.p2p;

import io.matrix.noosphere.FnlPackage;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;

class KnowledgeConsensusTest {

    private KnowledgeConsensus consensus;
    private TrustManager trustManager;

    @BeforeEach
    void setUp() {
        trustManager = new TrustManager();
        consensus = new KnowledgeConsensus();
        // Inject trustManager via field
        try {
            var field = KnowledgeConsensus.class.getDeclaredField("trustManager");
            field.setAccessible(true);
            field.set(consensus, trustManager);
        } catch (Exception e) {
            fail("Failed to inject trustManager: " + e.getMessage());
        }
    }

    @Test
    void singlePackageNoConflict() {
        var pkg = createPackage("hash1", "author1");
        var result = consensus.resolveConflict(List.of(pkg));
        assertEquals(pkg, result);
    }

    @Test
    void emptyListThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            consensus.resolveConflict(List.of()));
    }

    @Test
    void hasConflictDetectsDifferences() {
        var pkg1 = createPackage("hash1", "author1");
        var pkg2 = createPackage("hash2", "author1");
        assertTrue(consensus.hasConflict(List.of(pkg1, pkg2)));
    }

    @Test
    void noConflictWhenSameHash() {
        var pkg1 = createPackage("hash1", "author1");
        var pkg2 = createPackage("hash1", "author2");
        assertFalse(consensus.hasConflict(List.of(pkg1, pkg2)));
    }

    @Test
    void mergeUniqueRemovesDuplicates() {
        var pkg1 = createPackage("hash1", "author1");
        var pkg2 = createPackage("hash1", "author1");
        var result = consensus.mergeUnique(List.of(pkg1, pkg2));
        assertEquals(1, result.size());
    }

    private FnlPackage createPackage(String hash, String author) {
        return FnlPackage.builder()
                .name("test")
                .snapshotHash(hash)
                .authorInstanceId(author)
                .build();
    }
}
