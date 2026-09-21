package io.matrix.ingest;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Base64;
import java.util.HexFormat;

/**
 * REST API for multi-modal ingestion: text, audio, video, photo, PDF, books, etc.
 * Feeds the RAG system for knowledge accumulation.
 */
@Path("/api/v1/ingest")
@Produces(MediaType.APPLICATION_JSON)
public class IngestResource {

    private static final Logger log = LoggerFactory.getLogger(IngestResource.class);

    @Inject
    MultimodalIngestor ingestor;

    /**
     * Ingest text content (book, article, dialogue).
     */
    @POST
    @Path("/text")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestText(Map<String, Object> payload) {
        try {
            String text = (String) payload.get("text");
            String source = (String) payload.getOrDefault("source", "user");
            String title = (String) payload.getOrDefault("title", "Untitled");
            
            if (text == null || text.isBlank() || text.length() > 1_000_000) {
                return Response.status(400).entity(Map.of("error", "text required, max 1MB")).build();
            }
            
            String hash = sha256(text);
            int chunks = ingestor.ingestText(text, source, title, hash);
            
            log.info("Ingested text '{}' from {}: {} chunks, hash={}", title, source, chunks, hash.substring(0, 12));
            return Response.ok(Map.of(
                    "status", "success",
                    "type", "text",
                    "title", title,
                    "chunks", chunks,
                    "hash", hash,
                    "bytes", text.length()
            )).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Ingest binary data: audio, video, photo, PDF (base64 encoded).
     */
    @POST
    @Path("/binary/{type}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestBinary(@PathParam("type") String type, Map<String, Object> payload) {
        try {
            String data64 = (String) payload.get("data");
            String source = (String) payload.getOrDefault("source", "upload");
            String title = (String) payload.getOrDefault("title", type);
            
            if (data64 == null || data64.isBlank()) {
                return Response.status(400).entity(Map.of("error", "data required (base64)")).build();
            }
            
            byte[] data = Base64.getDecoder().decode(data64);
            if (data.length > 100_000_000) {
                return Response.status(400).entity(Map.of("error", "max 100MB")).build();
            }
            
            String hash = sha256(data);
            int chunks = ingestor.ingestBinary(type, data, source, title, hash);
            
            log.info("Ingested {} '{}' from {}: {} bytes, {} chunks", type, title, source, data.length, chunks);
            return Response.ok(Map.of(
                    "status", "success",
                    "type", type,
                    "title", title,
                    "chunks", chunks,
                    "bytes", data.length,
                    "hash", hash
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("error", "invalid base64 data")).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Ingest from URL (web scraping).
     */
    @POST
    @Path("/url")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestUrl(Map<String, Object> payload) {
        try {
            String url = (String) payload.get("url");
            if (url == null || url.isBlank() || !url.startsWith("http")) {
                return Response.status(400).entity(Map.of("error", "valid http(s) URL required")).build();
            }
            
            int chunks = ingestor.ingestUrl(url);
            return Response.ok(Map.of("status", "success", "url", url, "chunks", chunks)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Get ingestion statistics.
     */
    @POST
    @Path("/stats")
    public Response getStats() {
        return Response.ok(Map.of(
                "status", "success",
                "totalIngested", ingestor.getTotalIngested(),
                "totalChunks", ingestor.getTotalChunks(),
                "totalBytes", ingestor.getTotalBytes(),
                "knowledgeBase", "matrix-rag"
        )).build();
    }

    private String sha256(String s) {
        return sha256(s.getBytes());
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
