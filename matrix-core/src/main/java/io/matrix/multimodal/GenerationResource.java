package io.matrix.multimodal;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;

@Path("/api/v1/generation")
@Produces(MediaType.APPLICATION_JSON)
public class GenerationResource {

    private static final Logger log = LoggerFactory.getLogger(GenerationResource.class);

    @Inject
    MultimodalFeatureExtractor featureExtractor;

    @POST
    @Path("/image")
    public Response generateImage(@QueryParam("prompt") String prompt,
                                   @QueryParam("width") @jakarta.ws.rs.DefaultValue("256") int width,
                                   @QueryParam("height") @jakarta.ws.rs.DefaultValue("256") int height) {
        if (prompt == null || prompt.isBlank()) {
            return Response.status(400).entity(Map.of("error", "prompt required")).build();
        }
        try {
            var features = featureExtractor.extractFeatures(Map.of("text", prompt));
            var textFeatures = features.get("text");
            byte[] imageData = generateImageFromFeatures(textFeatures, width, height);
            String shortPrompt = prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt;
            log.info("Generated {}x{} image from: {}", width, height, shortPrompt);
            return Response.ok(Map.of("status", "success", "prompt", prompt,
                    "width", width, "height", height, "bytes", imageData.length)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/video")
    public Response generateVideo(@QueryParam("prompt") String prompt,
                                   @QueryParam("frames") @jakarta.ws.rs.DefaultValue("30") int frames,
                                   @QueryParam("width") @jakarta.ws.rs.DefaultValue("128") int width,
                                   @QueryParam("height") @jakarta.ws.rs.DefaultValue("128") int height) {
        if (prompt == null || prompt.isBlank()) {
            return Response.status(400).entity(Map.of("error", "prompt required")).build();
        }
        try {
            var features = featureExtractor.extractFeatures(Map.of("text", prompt));
            var textFeatures = features.get("text");
            int totalBytes = 0;
            for (int f = 0; f < frames; f++) {
                totalBytes += generateImageFromFeatures(textFeatures, width, height).length;
            }
            String shortPrompt = prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt;
            log.info("Generated {} frames video from: {}", frames, shortPrompt);
            return Response.ok(Map.of("status", "success", "prompt", prompt,
                    "frames", frames, "totalBytes", totalBytes)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/audio")
    public Response generateAudio(@QueryParam("prompt") String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Response.status(400).entity(Map.of("error", "prompt required")).build();
        }
        try {
            var features = featureExtractor.extractFeatures(Map.of("text", prompt));
            var audioFeatures = features.get("text");
            return Response.ok(Map.of("status", "success", "prompt", prompt,
                    "samples", audioFeatures != null ? audioFeatures.length : 0)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    private byte[] generateImageFromFeatures(float[] features, int width, int height) {
        if (features == null || features.length == 0) return new byte[width * height * 3];
        byte[] pixels = new byte[width * height * 3];
        long seed = hashLong(features);
        Random rng = new Random(seed);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (rng.nextInt(256));
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = (y * width + x) * 3;
                int fi = ((x * 37 + y * 53) % features.length + features.length) % features.length;
                if (features[fi] > 0.5f) {
                    pixels[idx] = (byte) (Math.abs((x * 7 + y * 13) % 256));
                    pixels[idx + 1] = (byte) (Math.abs((x * 31 + y * 17) % 256));
                    pixels[idx + 2] = (byte) (Math.abs((x * 61 + y * 37) % 256));
                }
            }
        }
        return pixels;
    }

    private long hashLong(float[] features) {
        long h = 0xABCDEF;
        for (float f : features) h = h * 31 + Float.floatToIntBits(f);
        return h;
    }
}
