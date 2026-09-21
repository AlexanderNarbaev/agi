package io.matrix.api.resource;

import io.matrix.api.brain.StubBrainCycle;
import io.matrix.api.security.JwtAuthFilter;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplainResourceTest {

    private final ExplainResource resource = new ExplainResource(new StubBrainCycle(), new JwtAuthFilter());

    private String viewerToken() {
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        return "Bearer vw-1|vw@x|" + futureExp + "|FREE|VIEWER";
    }

    @Test
    void testExplainRequiresAuth() {
        Response resp = resource.explain(null, "expl_validid123");
        assertEquals(401, resp.getStatus());
    }

    @Test
    void testUnknownIdReturns404() {
        Response resp = resource.explain(viewerToken(), "expl_nonexistent123");
        assertEquals(404, resp.getStatus());
    }

    @Test
    void testInvalidIdFormat() {
        Response resp = resource.explain(viewerToken(), "short");
        assertEquals(400, resp.getStatus());
    }
}
