package io.matrix.synthesis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomatedDistillationPipelineTest {

    @Test
    void testCreatePipeline() {
        AutomatedDistillationPipeline pipeline = new AutomatedDistillationPipeline();
        assertNotNull(pipeline);
        assertEquals(AutomatedDistillationPipeline.Status.IDLE, pipeline.getCurrentStatus());
    }

    @Test
    void testRunPipeline() {
        AutomatedDistillationPipeline pipeline = new AutomatedDistillationPipeline();
        AutomatedDistillationPipeline.PipelineStatus status = pipeline.runPipeline();
        assertEquals(AutomatedDistillationPipeline.Status.COMPLETE, status.status());
        assertTrue(status.rulesExtracted() > 0);
        assertTrue(status.rulesValidated() >= 0);
    }

    @Test
    void testFROZENFilter() {
        AutomatedDistillationPipeline pipeline = new AutomatedDistillationPipeline();
        pipeline.runPipeline();
        // Verify no harmful rules in merged set
        for (String rule : pipeline.getMergedRules()) {
            assertFalse(rule.contains("harm"), "Should not merge harmful rules");
            assertFalse(rule.contains("deceive"), "Should not merge deceptive rules");
        }
    }

    @Test
    void testMergedRulesAvailable() {
        AutomatedDistillationPipeline pipeline = new AutomatedDistillationPipeline();
        pipeline.runPipeline();
        assertFalse(pipeline.getMergedRules().isEmpty());
    }

    @Test
    void testLastRunTimestamp() {
        AutomatedDistillationPipeline pipeline = new AutomatedDistillationPipeline();
        pipeline.runPipeline();
        AutomatedDistillationPipeline.PipelineStatus status = pipeline.runPipeline();
        assertTrue(status.lastRunTimestamp() > 0);
    }
}
