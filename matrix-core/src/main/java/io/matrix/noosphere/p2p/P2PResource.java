package io.matrix.noosphere.p2p;

import io.matrix.noosphere.FnlPackage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * REST API for P2P Noosphere network.
 */
@Path("/api/v1/noosphere/p2p")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class P2PResource {

    private static final Logger log = LoggerFactory.getLogger(P2PResource.class);

    @Inject
    P2PNetwork network;

    @Inject
    TrustManager trustManager;

    @Inject
    KnowledgeConsensus consensus;

    @GET
    @Path("/peers")
    public List<Peer> listPeers() {
        return network.getPeers();
    }

    @GET
    @Path("/peers/count")
    public Map<String, Integer> peerCount() {
        return Map.of("count", network.getPeerCount());
    }

    @POST
    @Path("/publish")
    public Response publishKnowledge(FnlPackage pkg) {
        network.broadcastKnowledge(pkg);
        log.info("Published knowledge: {}", pkg.name());
        return Response.ok(Map.of("status", "published")).build();
    }

    @GET
    @Path("/query")
    public List<FnlPackage> queryKnowledge(@QueryParam("topic") String topic) {
        if (topic == null || topic.isBlank()) {
            throw new WebApplicationException("Topic required", Response.Status.BAD_REQUEST);
        }
        return network.requestKnowledge(topic);
    }

    @GET
    @Path("/trust")
    public Map<String, Double> getTrustScores() {
        return trustManager.getAllScores();
    }

    @GET
    @Path("/trust/{peerId}")
    public Map<String, Object> getPeerTrust(@PathParam("peerId") String peerId) {
        double score = trustManager.getTrustScore(peerId);
        return Map.of("peerId", peerId, "trustScore", score);
    }

    @POST
    @Path("/trust/{peerId}/success")
    public Response recordSuccess(@PathParam("peerId") String peerId,
                                   @QueryParam("quality") @DefaultValue("1.0") double quality) {
        trustManager.recordSuccess(peerId, quality);
        return Response.ok(Map.of("status", "recorded")).build();
    }

    @POST
    @Path("/trust/{peerId}/failure")
    public Response recordFailure(@PathParam("peerId") String peerId) {
        trustManager.recordFailure(peerId);
        return Response.ok(Map.of("status", "recorded")).build();
    }
}
