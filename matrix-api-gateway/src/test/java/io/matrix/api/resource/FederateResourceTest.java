package io.matrix.api.resource;

import io.matrix.api.federation.FederationRegistry;
import io.matrix.api.security.JwtAuthFilter;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FederateResourceTest {

    private final FederateResource resource = new FederateResource(new FederationRegistry(), new JwtAuthFilter());

    private String devToken() {
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        return "Bearer d|d@x|" + futureExp + "|PRO|DEVELOPER";
    }

    private String viewerToken() {
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        return "Bearer vw-1|vw@x|" + futureExp + "|FREE|VIEWER";
    }

    @Test
    void testJoinSucceeds() {
        FederateResource.JoinRequest req = new FederateResource.JoinRequest();
        req.region = "us-east";
        req.shardCapacity = 500;
        Response resp = resource.join(devToken(), req);
        assertEquals(201, resp.getStatus());
    }

    @Test
    void testJoinRequiresAuth() {
        FederateResource.JoinRequest req = new FederateResource.JoinRequest();
        req.region = "us-east";
        Response resp = resource.join(null, req);
        assertEquals(401, resp.getStatus());
    }

    @Test
    void testViewerCannotJoin() {
        FederateResource.JoinRequest req = new FederateResource.JoinRequest();
        req.region = "us-east";
        Response resp = resource.join(viewerToken(), req);
        assertEquals(403, resp.getStatus());
    }

    @Test
    void testMissingRegion() {
        FederateResource.JoinRequest req = new FederateResource.JoinRequest();
        Response resp = resource.join(devToken(), req);
        assertEquals(400, resp.getStatus());
    }

    @Test
    void testShardCapacityOutOfRange() {
        FederateResource.JoinRequest req = new FederateResource.JoinRequest();
        req.region = "us-east";
        req.shardCapacity = 999_999_999;
        Response resp = resource.join(devToken(), req);
        assertEquals(400, resp.getStatus());
    }

    @Test
    void testListRequiresViewer() {
        Response resp = resource.list(viewerToken());
        assertEquals(200, resp.getStatus());
    }
}
