package io.matrix.knowledge;

import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The learning surface of the Workbench: the seed corpus, the bounded
 * acquisition cycle and the revision.
 *
 * <p>These routes are the visible part of the learning loop. Each one returns
 * a report that says what was acquired, from where, under which licence, and
 * what was skipped. The routes never hide the boundary of the acquisition: the
 * pool is pre-cached and local, and the report says so.
 */
@Path("/v1/learning")
@Produces(MediaType.APPLICATION_JSON)
public class LearningCycleResource {

    @Inject
    KnowledgeWorkbench workbench;

    /**
     * The declared source catalogue: what the cycle may acquire, and under
     * which licence.
     *
     * @return the catalogue
     */
    @GET
    @Path("/catalogue")
    public Response catalogue() {
        try {
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            List<Map<String, Object>> documents = pool.entries().stream()
                    .map(entry -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("file", entry.file());
                        item.put("licence", entry.licence());
                        item.put("licenceUrl", entry.licenceUrl());
                        item.put("sourceUrl", entry.sourceUrl());
                        item.put("attribution", entry.attribution());
                        item.put("accessedAt", entry.accessedAt());
                        item.put("revision", entry.revision());
                        item.put("sha256", entry.sha256());
                        item.put("purpose", entry.purpose());
                        return item;
                    }).toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("seedCorpus", AcquisitionCycle.SEED_CORPUS);
            body.put("researchPool", AcquisitionCycle.RESEARCH_POOL);
            body.put("boundary", AcquisitionCycle.BOUNDARY);
            body.put("maximumDocumentsPerCycle", AcquisitionCycle.MAXIMUM_DOCUMENTS);
            body.put("documents", documents);
            return Response.ok(body).build();
        } catch (IOException failure) {
            return Response.status(500).entity(Map.of("error", failure.getMessage())).build();
        }
    }

    /**
     * Loads the licensed seed corpus into a workspace.
     *
     * @param workspaceId the workspace to load
     * @return the report of the import
     */
    @POST
    @Path("/workspaces/{id}/seed")
    public Response seed(@PathParam("id") String workspaceId) {
        try {
            return Response.ok(AcquisitionCycle.loadSeed(workbench, workspaceId)).build();
        } catch (IOException failure) {
            return Response.status(500).entity(Map.of("error", failure.getMessage())).build();
        }
    }

    /**
     * Runs one bounded acquisition cycle.
     *
     * @param workspaceId the workspace to learn in
     * @param question the question whose gap drives the cycle
     * @param documents how many documents the cycle may acquire
     * @return the report of the cycle
     */
    @POST
    @Path("/workspaces/{id}/learn")
    public Response learn(@PathParam("id") String workspaceId,
                          @QueryParam("question") String question,
                          @QueryParam("documents") @DefaultValue("3") int documents) {
        if (question == null || question.isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", "the cycle needs a question, because the question names the gap"))
                    .build();
        }
        try {
            AcquisitionCycle.Report report = AcquisitionCycle.run(workbench, workspaceId, question,
                    Math.min(Math.max(documents, 0), AcquisitionCycle.MAXIMUM_DOCUMENTS));
            return Response.ok(report).build();
        } catch (IOException failure) {
            return Response.status(500).entity(Map.of("error", failure.getMessage())).build();
        }
    }

    /**
     * Acquires one document of the research pool and compares it with the
     * model, superseding what the new evidence replaces.
     *
     * @param workspaceId the workspace to learn in
     * @param file the name of the document of the research pool
     * @return the report of the attempt
     */
    @POST
    @Path("/workspaces/{id}/revise")
    public Response revise(@PathParam("id") String workspaceId, @QueryParam("file") String file) {
        if (file == null || file.isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", "the revision needs the name of a document of the research pool"))
                    .build();
        }
        try {
            AcquisitionCycle.RevisionReport report =
                    AcquisitionCycle.acquireAndRevise(workbench, workspaceId, file);
            return Response.ok(report).build();
        } catch (IOException failure) {
            return Response.status(400).entity(Map.of("error", failure.getMessage())).build();
        }
    }
}
