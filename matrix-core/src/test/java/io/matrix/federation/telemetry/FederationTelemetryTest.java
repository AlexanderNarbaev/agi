package io.matrix.federation.telemetry;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.TelemetryEvent;
import io.matrix.federation.runtime.FederationRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FederationTelemetryTest {
    
    @Test
    void testConstruction() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        assertEquals(0, tel.getTotalEventCount());
    }
    
    @Test
    void testNullRuntime() {
        assertThrows(IllegalArgumentException.class, () -> new FederationTelemetry(null, 42L));
    }
    
    @Test
    void testRecordProposal() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        tel.recordProposal("APPROVED");
        tel.recordProposal("APPROVED");
        tel.recordProposal("REJECTED");
        
        assertEquals(3, tel.getTotalEventCount());
        var snap = tel.snapshot();
        assertEquals(2, snap.proposalCountsByStatus().get("APPROVED"));
        assertEquals(1, snap.proposalCountsByStatus().get("REJECTED"));
    }
    
    @Test
    void testRecordVote() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        tel.recordVote("YES");
        tel.recordVote("NO");
        tel.recordVote("YES");
        
        var snap = tel.snapshot();
        assertEquals(2, snap.voteCountsByDecision().get("YES"));
        assertEquals(1, snap.voteCountsByDecision().get("NO"));
    }
    
    @Test
    void testRecordMutation() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        tel.recordMutation();
        tel.recordMutation();
        
        var snap = tel.snapshot();
        assertEquals(2, snap.registryMutations());
        assertEquals(2, snap.eventCountsByType().get("mutation"));
    }
    
    @Test
    void testNullOrEmptyInput() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        tel.recordProposal(null);
        tel.recordProposal("");
        tel.recordVote(null);
        
        // No events should be recorded
        assertEquals(0, tel.getTotalEventCount());
    }
    
    @Test
    void testBuildEvent() {
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        TelemetryEvent event = tel.buildEvent("proposal_created", "node-42", "test value");
        
        assertEquals("proposal_created", event.getEventType());
        assertEquals("node-42", event.getNodeId());
        assertEquals("test value", event.getStringValue());
        assertTrue(event.getTimestampNs() > 0);
    }
    
    @Test
    void testSnapshotTimestamp() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        long before = System.nanoTime();
        var snap = tel.snapshot();
        long after = System.nanoTime();
        
        assertTrue(snap.snapshotTimestampNs() >= before);
        assertTrue(snap.snapshotTimestampNs() <= after);
    }
    
    @Test
    void testReset() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        tel.recordProposal("APPROVED");
        tel.recordVote("YES");
        tel.recordMutation();
        assertEquals(3, tel.getTotalEventCount());
        
        tel.reset();
        assertEquals(0, tel.getTotalEventCount());
        assertTrue(tel.snapshot().proposalCountsByStatus().isEmpty());
    }
    
    @Test
    void testRngDifferentSeedsDiffer() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel1 = new FederationTelemetry(runtime, 42L);
        FederationTelemetry tel2 = new FederationTelemetry(runtime, 99L);  // different seed
        
        // Same seed → same value
        assertNotEquals(tel1.getRng().nextLong(), tel2.getRng().nextLong());
    }
    
    @Test
    void testEventTypeCounts() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        FederationTelemetry tel = new FederationTelemetry(runtime, 42L);
        
        tel.recordProposal("APPROVED");
        tel.recordVote("YES");
        tel.recordMutation();
        
        var snap = tel.snapshot();
        assertEquals(1, snap.eventCountsByType().get("proposal"));
        assertEquals(1, snap.eventCountsByType().get("vote"));
        assertEquals(1, snap.eventCountsByType().get("mutation"));
    }
}
