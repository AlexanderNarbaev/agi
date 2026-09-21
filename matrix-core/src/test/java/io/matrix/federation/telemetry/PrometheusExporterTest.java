package io.matrix.federation.telemetry;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.runtime.FederationRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrometheusExporterTest {
    
    @Test
    void testEmptySnapshot() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        String result = PrometheusExporter.render(telemetry);
        
        assertNotNull(result);
        assertTrue(result.contains("federation_total_events 0"));
        assertTrue(result.contains("federation_registry_mutations 0"));
        assertTrue(result.contains("# HELP"));
        assertTrue(result.contains("# TYPE"));
    }
    
    @Test
    void testWithEvents() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        for (int i = 0; i < 5; i++) {
            telemetry.recordProposal("APPROVED");
        }
        for (int i = 0; i < 3; i++) {
            telemetry.recordVote("YES");
        }
        
        String result = PrometheusExporter.render(telemetry);
        
        assertTrue(result.contains("federation_total_events 8"));
        assertTrue(result.contains("federation_proposals_by_status{status=\"APPROVED\"} 5"));
        assertTrue(result.contains("federation_votes_by_decision{decision=\"YES\"} 3"));
    }
    
    @Test
    void testWithMutations() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        for (int i = 0; i < 10; i++) {
            telemetry.recordMutation();
        }
        
        String result = PrometheusExporter.render(telemetry);
        
        assertTrue(result.contains("federation_registry_mutations 10"));
        assertTrue(result.contains("federation_events_by_type{type=\"mutation\"} 10"));
    }
    
    @Test
    void testContentTypeConstant() {
        assertTrue(PrometheusExporter.CONTENT_TYPE.startsWith("text/plain"));
        assertTrue(PrometheusExporter.CONTENT_TYPE.contains("version="));
    }
    
    @Test
    void testMultipleEventTypes() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        telemetry.recordProposal("APPROVED");
        telemetry.recordProposal("REJECTED");
        telemetry.recordVote("YES");
        telemetry.recordVote("NO");
        telemetry.recordVote("YES");
        telemetry.recordMutation();
        
        String result = PrometheusExporter.render(telemetry);
        
        // Each event type should appear
        assertTrue(result.contains("federation_events_by_type{type=\"proposal\"} 2"));
        assertTrue(result.contains("federation_events_by_type{type=\"vote\"} 3"));
        assertTrue(result.contains("federation_events_by_type{type=\"mutation\"} 1"));
        // Proposal and vote status counts
        assertTrue(result.contains("federation_proposals_by_status{status=\"APPROVED\"} 1"));
        assertTrue(result.contains("federation_proposals_by_status{status=\"REJECTED\"} 1"));
        assertTrue(result.contains("federation_votes_by_decision{decision=\"YES\"} 2"));
        assertTrue(result.contains("federation_votes_by_decision{decision=\"NO\"} 1"));
    }
}
