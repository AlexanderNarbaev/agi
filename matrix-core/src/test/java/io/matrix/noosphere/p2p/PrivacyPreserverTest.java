package io.matrix.noosphere.p2p;

import io.matrix.noosphere.FnlPackage;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PrivacyPreserverTest {

    @Test
    void anonymizeRemovesAuthorIdentity() {
        var preserver = new PrivacyPreserver();
        var pkg = FnlPackage.builder()
                .name("test")
                .authorInstanceId("real-author-123")
                .snapshotHash("abc123def456")
                .build();
        var anonymized = preserver.anonymize(pkg);
        assertNotNull(anonymized.authorInstanceId());
        assertTrue(anonymized.authorInstanceId().startsWith("anon-"));
        assertNotEquals("real-author-123", anonymized.authorInstanceId());
    }

    @Test
    void anonymizePreservesName() {
        var preserver = new PrivacyPreserver();
        var pkg = FnlPackage.builder().name("test").build();
        var anonymized = preserver.anonymize(pkg);
        assertEquals("test", anonymized.name());
    }

    @Test
    void verifyIntegrityWithValidHash() {
        var preserver = new PrivacyPreserver();
        var pkg = FnlPackage.builder().snapshotHash("abc123def456ghij").build();
        assertTrue(preserver.verifyIntegrity(pkg));
    }

    @Test
    void verifyIntegrityWithNullHash() {
        var preserver = new PrivacyPreserver();
        var pkg = FnlPackage.builder().build();
        assertFalse(preserver.verifyIntegrity(pkg));
    }

    @Test
    void verifyIntegrityWithShortHash() {
        var preserver = new PrivacyPreserver();
        var pkg = FnlPackage.builder().snapshotHash("abc").build();
        assertFalse(preserver.verifyIntegrity(pkg));
    }

    @Test
    void hashPeerConsistent() {
        var preserver = new PrivacyPreserver();
        var pkg1 = FnlPackage.builder().authorInstanceId("peer-1").build();
        var pkg2 = FnlPackage.builder().authorInstanceId("peer-1").build();
        var anon1 = preserver.anonymize(pkg1);
        var anon2 = preserver.anonymize(pkg2);
        assertEquals(anon1.authorInstanceId(), anon2.authorInstanceId());
    }
}
