package io.matrix.federated;

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
 * REST API for Federated Learning.
 */
@Path("/api/v1/federated")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FederatedResource {

    private static final Logger log = LoggerFactory.getLogger(FederatedResource.class);

    @Inject
    FederatedProtocol protocol;

    @Inject
    SecureAggregator aggregator;

    @Inject
    PrivacyMechanism privacy;

    private boolean[] globalModel;
    private int currentRound = 0;

    @POST
    @Path("/round")
    public Response runRound(List<LocalUpdate> updates) {
        // Apply differential privacy
        List<LocalUpdate> privateUpdates = updates.stream()
                .map(privacy::addNoise)
                .toList();

        // Run federated round
        FederatedProtocol.FederatedRound round = protocol.runRound(privateUpdates);
        globalModel = round.aggregatedModel();
        currentRound++;

        log.info("Federated round {} completed: {} participants, avgLoss={}",
                currentRound, round.participantCount(), round.averageLoss());

        return Response.ok(Map.of(
                "round", currentRound,
                "participants", round.participantCount(),
                "averageLoss", round.averageLoss()
        )).build();
    }

    @GET
    @Path("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "currentRound", currentRound,
                "minParticipants", protocol.getMinParticipants(),
                "totalRounds", protocol.getRounds(),
                "epsilon", privacy.getEpsilon()
        );
    }

    @GET
    @Path("/model")
    public Map<String, Object> getGlobalModel() {
        return Map.of(
                "round", currentRound,
                "hasModel", globalModel != null,
                "modelSize", globalModel != null ? globalModel.length : 0
        );
    }

    @POST
    @Path("/config")
    public Response updateConfig(Map<String, Object> config) {
        if (config.containsKey("epsilon")) {
            privacy.setEpsilon(((Number) config.get("epsilon")).doubleValue());
        }
        return Response.ok(Map.of("status", "updated")).build();
    }
}
