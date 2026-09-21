package io.matrix.imports;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST API for importing pretrained models into M.A.T.R.I.X.
 */
@Path("/api/v1/import")
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    private static final Logger log = LoggerFactory.getLogger(ImportResource.class);

    @ConfigProperty(name = "matrix.models.dir", defaultValue = "models")
    String modelsDir;

    /**
     * Import all available pretrained models.
     */
    @POST
    @Path("/all")
    public Response importAll() {
        try {
            WeightImporter importer = new WeightImporter(java.nio.file.Path.of(modelsDir));
            WeightImporter.IngestReport report = importer.ingestAll();
            
            log.info("Import complete: {} models, {} tensors, {} neurons",
                    report.byModel().size(), report.totalTensors(), report.totalNeurons());
            
            return Response.ok(Map.of(
                    "status", "success",
                    "models", report.byModel().size(),
                    "tensors", report.totalTensors(),
                    "neurons", report.totalNeurons(),
                    "bytes", report.totalBytes()
            )).build();
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage());
            return Response.serverError().entity(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            )).build();
        }
    }

    /**
     * Import a specific model by ID.
     */
    @POST
    @Path("/model/{modelId}")
    public Response importModel(@PathParam("modelId") String modelId) {
        try {
            return Response.ok(Map.of(
                    "status", "info",
                    "message", "Single model import not yet implemented. Use /all to import all models.",
                    "modelId", modelId
            )).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            )).build();
        }
    }

    /**
     * List available pretrained models.
     */
    @GET
    @Path("/models")
    public Response listModels() {
        try {
            java.nio.file.Path modelsPath = java.nio.file.Path.of(modelsDir);
            var models = new java.util.ArrayList<String>();
            
            if (java.nio.file.Files.isDirectory(modelsPath)) {
                try (var stream = java.nio.file.Files.list(modelsPath)) {
                    stream.filter(java.nio.file.Files::isDirectory)
                          .map(p -> p.getFileName().toString())
                          .filter(name -> !name.equals("merged") && !name.equals("trained"))
                          .forEach(models::add);
                }
            }
            
            return Response.ok(Map.of(
                    "models", models,
                    "count", models.size()
            )).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            )).build();
        }
    }
}
