package io.matrix.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FederationTest {

    @Test
    void artifactSignerSignsAndVerifies() throws Exception {
        var signer = new ArtifactSigner("node-1");
        byte[] content = "test artifact".getBytes();
        var signed = signer.sign(content, "text/plain");

        assertNotNull(signed.signature());
        assertNotNull(signed.contentHash());
        assertEquals("node-1", signed.nodeId());

        assertTrue(signer.verify(signed, content));
        assertFalse(signer.verify(signed, "tampered".getBytes()));
    }

    @Test
    void artifactSignerTamperDetection() throws Exception {
        var signer = new ArtifactSigner("node-1");
        byte[] content = "original".getBytes();
        var signed = signer.sign(content, "text/plain");

        // Tamper with content
        assertFalse(signer.verify(signed, "modified".getBytes()));
    }

    @Test
    void anonymizerThreshold() {
        var anon = new Anonymizer(3);
        anon.recordContribution("hash1", "node1");
        anon.recordContribution("hash1", "node2");
        assertFalse(anon.isAnonymous("hash1")); // 2 < 3

        anon.recordContribution("hash1", "node3");
        assertTrue(anon.isAnonymous("hash1")); // 3 >= 3
    }

    @Test
    void anonymizerMultipleHashes() {
        var anon = new Anonymizer(2);
        anon.recordContribution("a", "n1");
        anon.recordContribution("a", "n2");
        anon.recordContribution("b", "n1");

        assertTrue(anon.isAnonymous("a"));
        assertFalse(anon.isAnonymous("b"));
        assertEquals(2, anon.anonymityLevel("a"));
        assertEquals(1, anon.anonymityLevel("b"));
    }
}
