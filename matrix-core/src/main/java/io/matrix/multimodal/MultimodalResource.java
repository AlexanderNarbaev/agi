package io.matrix.multimodal;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST API for multi-modal capabilities.
 */
@Path("/api/v1/multimodal")
@Produces(MediaType.APPLICATION_JSON)
public class MultimodalResource {

    private static final Logger log = LoggerFactory.getLogger(MultimodalResource.class);

    @Inject
    MultimodalFeatureExtractor featureExtractor;

    /**
     * Get supported modalities.
     */
    @GET
    @Path("/modalities")
    public Response getModalities() {
        return Response.ok(Map.of(
                "modalities", featureExtractor.getSupportedModalities(),
                "count", featureExtractor.getSupportedModalities().size()
        )).build();
    }

    /**
     * Extract features from text.
     */
    @POST
    @Path("/text/extract")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response extractTextFeatures(String text) {
        try {
            var features = featureExtractor.extractFeatures(Map.of("text", text));
            var textFeatures = features.get("text");
            
            return Response.ok(Map.of(
                    "status", "success",
                    "modality", "text",
                    "featureCount", textFeatures != null ? textFeatures.length : 0,
                    "sample", textFeatures != null ? java.util.Arrays.copyOf(textFeatures, 10) : new float[0]
            )).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            )).build();
        }
    }

    /**
     * Convert multi-modal input to unified boolean vector.
     */
    @POST
    @Path("/unify")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response unifyFeatures(Map<String, Object> inputs) {
        try {
            var vector = featureExtractor.toUnifiedVector(inputs);
            
            return Response.ok(Map.of(
                    "status", "success",
                    "vectorLength", vector.length,
                    "trueCount", countTrue(vector),
                    "falseCount", countFalse(vector)
            )).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            )).build();
        }
    }

    private int countTrue(boolean[] vector) {
        int count = 0;
        for (boolean b : vector) {
            if (b) count++;
        }
        return count;
    }

    private int countFalse(boolean[] vector) {
        return vector.length - countTrue(vector);
    }
}
