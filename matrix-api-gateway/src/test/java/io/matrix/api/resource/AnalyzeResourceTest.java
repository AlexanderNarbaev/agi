package io.matrix.api.resource;

import io.matrix.api.brain.StubBrainCycle;
import io.matrix.api.dto.AnalyzeRequest;
import io.matrix.api.security.JwtAuthFilter;
import io.matrix.api.security.RateLimiter;
import io.matrix.api.security.RbacChecker;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzeResourceTest {

    private AnalyzeResource resource;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(60_000);
        resource = new AnalyzeResource(new StubBrainCycle(), new JwtAuthFilter(), rateLimiter);
    }

    private String devToken() {
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        return "Bearer dev-1|dev@x|" + futureExp + "|PRO|DEVELOPER";
    }

    private String viewerToken() {
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        return "Bearer vw-1|vw@x|" + futureExp + "|FREE|VIEWER";
    }

    @Test
    void testSuccessfulAnalyze() {
        AnalyzeRequest req = new AnalyzeRequest("hello");
        Response resp = resource.analyze(devToken(), req);
        assertEquals(200, resp.getStatus());
        Object entity = resp.getEntity();
        assertNotNull(entity);
    }

    @Test
    void testViewerForbidden() {
        AnalyzeRequest req = new AnalyzeRequest("hello");
        Response resp = resource.analyze(viewerToken(), req);
        assertEquals(403, resp.getStatus());
    }

    @Test
    void testMissingAuth() {
        AnalyzeRequest req = new AnalyzeRequest("hello");
        Response resp = resource.analyze(null, req);
        assertEquals(401, resp.getStatus());
    }

    @Test
    void testRateLimited() {
        // Set up FREE plan with small limit
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        String freeDevToken = "Bearer d|d@x|" + futureExp + "|FREE|DEVELOPER";
        AnalyzeRequest req = new AnalyzeRequest("hello");

        for (int i = 0; i < 100; i++) {
            Response r = resource.analyze(freeDevToken, req);
            assertEquals(200, r.getStatus(), "request " + i + " should succeed");
        }
        Response limited = resource.analyze(freeDevToken, req);
        assertEquals(429, limited.getStatus());
    }

    @Test
    void testInputTooLargeRejected() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.input = "x".repeat(2_000_000);  // > 1MiB
        Response resp = resource.analyze(devToken(), req);
        assertEquals(400, resp.getStatus());
    }

    @Test
    void testNullRequestBody() {
        Response resp = resource.analyze(devToken(), null);
        assertEquals(400, resp.getStatus());
    }
}
