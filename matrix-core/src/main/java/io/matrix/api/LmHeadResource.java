package io.matrix.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RUN 10 — LM head training + status endpoints.
 *
 * <p>The LM head is a sparse Hebbian-trained projection from chain
 * output to vocabulary distribution. Training uses Q&A pairs from the
 * corpus to learn which neuron activations predict which tokens.
 *
 * <p>POST /v1/lm-head/train?limit=N&epochs=M — train
 * <br>GET  /v1/lm-head/status                  — show training state
 * <br>POST /v1/lm-head/reset                  — clear all weights
 */
@jakarta.ws.rs.Path("/v1/lm-head")
@Produces(MediaType.APPLICATION_JSON)
public class LmHeadResource {

    @Inject
    LmHeadTrainer trainer;

    @POST
    @Path("/train")
    public Map<String, Object> train(
            @QueryParam("limit") Integer limit,
            @QueryParam("epochs") Integer epochs) {
        int l = limit != null ? Math.min(limit, 8606) : 500;
        int e = epochs != null ? Math.min(epochs, 10) : 3;
        LmHeadTrainer.TrainResult result = trainer.train(l, e);
        Map<String, Object> body = result.toMap();
        body.put("totalNeurons", trainer.lmHead().totalNeurons());
        body.put("trained", trainer.isTrained());
        body.put("cumulative_pairs", trainer.trainedPairs());
        body.put("cumulative_epochs", trainer.trainedEpochs());
        body.put("last_trained_at", trainer.lastTrainedAt());
        return body;
    }

    @GET
    @Path("/status")
    public Map<String, Object> status() {
        LmHead head = trainer.lmHead();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trained", trainer.isTrained());
        body.put("totalNeurons", head.totalNeurons());
        body.put("vocabCoverage", head.vocabularyCoverage());
        body.put("updateCount", head.updateCount());
        body.put("queryCount", head.queryCount());
        body.put("cumulative_pairs", trainer.trainedPairs());
        body.put("cumulative_epochs", trainer.trainedEpochs());
        body.put("last_trained_at", trainer.lastTrainedAt());
        return body;
    }
}
