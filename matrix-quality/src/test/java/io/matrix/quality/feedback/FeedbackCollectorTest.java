package io.matrix.quality.feedback;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackCollectorTest {

    @Test
    void testSubmitBugReport() {
        FeedbackCollector fc = new FeedbackCollector();
        FeedbackCollector.Feedback f = fc.submit(
            FeedbackCollector.Type.BUG, "user-1",
            "Cannot login", "OAuth callback fails");
        assertEquals(FeedbackCollector.Type.BUG, f.type());
        assertEquals(FeedbackCollector.Feedback.Status.OPEN, f.status());
        assertEquals(1, fc.openCount());
    }

    @Test
    void testSubmitFeatureRequest() {
        FeedbackCollector fc = new FeedbackCollector();
        fc.submit(FeedbackCollector.Type.FEATURE_REQUEST, "u", "Add dark mode", "...");
        assertEquals(1, fc.all().size());
    }

    @Test
    void testAllReturnsAllEntries() {
        FeedbackCollector fc = new FeedbackCollector();
        fc.submit(FeedbackCollector.Type.BUG, "u1", "A", "a");
        fc.submit(FeedbackCollector.Type.FEATURE_REQUEST, "u2", "B", "b");
        fc.submit(FeedbackCollector.Type.BUG, "u3", "C", "c");
        assertEquals(3, fc.all().size());
    }

    @Test
    void testIdIsUnique() {
        FeedbackCollector fc = new FeedbackCollector();
        FeedbackCollector.Feedback a = fc.submit(FeedbackCollector.Type.BUG, "u", "A", "a");
        FeedbackCollector.Feedback b = fc.submit(FeedbackCollector.Type.BUG, "u", "B", "b");
        assertNotEquals(a.id(), b.id());
    }
}
