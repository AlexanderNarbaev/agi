package io.matrix.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.imports.BooleanChainRunner;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;

/**
 * Q&A corpus learning endpoint.
 *
 * <p>Adds a new (question, answer) pair to the corpus on disk
 * and re-indexes for retrieval. Equivalent to "fine-tuning" the
 * MATRIX on a single example: the next chat call that matches
 * the new question will return this answer verbatim.
 *
 * <p>Optionally scores the new Q&A through the boolean chain
 * to capture the chain's feature vector on this example, so the
 * chain's future ranking will favor it.
 */
@Path("/v1/qa")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QaLearnResource {

    private static final Logger log = LoggerFactory.getLogger(QaLearnResource.class);

    @Inject
    QaCorpusIndex index;

    @Inject
    io.matrix.imports.BooleanChainRunner chainRunner;

    private final ObjectMapper mapper = new ObjectMapper();

    public record LearnRequest(String question, String answer,
                               String category, String source) {}

    public record LearnResponse(int id, String question, String answer,
                                String category, String source,
                                int corpusSize, String message) {}

    public record BulkLearnRequest(List<LearnRequest> pairs) {}

    public record BulkLearnResponse(int added, int totalCorpus, String message) {}

    @POST
    @Path("/learn")
    public Response learn(LearnRequest req) {
        if (req == null || req.question == null || req.question.isBlank()
                || req.answer == null || req.answer.isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", Map.of(
                            "message", "question and answer are required and must not be blank",
                            "type", "invalid_request_error",
                            "code", "missing_fields")))
                    .build();
        }

        QaCorpusIndex.Entry e = index.add(req.question.trim(), req.answer.trim(),
                req.category, req.source);

        // Optionally score through the chain — captures the feature
        // signature of the new Q&A so future retrieval can favor it.
        long chainScore = 0L;
        if (chainRunner != null && chainRunner.layerCount() > 0) {
            try {
                String sigInput = (req.question + " " + req.answer).substring(0,
                        Math.min(64, req.question.length() + req.answer.length() + 1));
                boolean[] chainInput = new boolean[64];
                long hash = 0xCBF29CE484222325L;
                for (int i = 0; i < sigInput.length() && i < 64; i++) {
                    hash ^= sigInput.charAt(i);
                    hash *= 0x100000001b3L;
                }
                long mask = 1L;
                for (int i = 0; i < 64; i++) {
                    chainInput[i] = (hash & mask) != 0;
                    mask <<= 1;
                }
                BooleanChainRunner.ChainResult res = chainRunner.evaluateWithScore(chainInput);
                chainScore = (long) Math.min(Long.MAX_VALUE, Math.max(0, res.weightedScore()));
            } catch (Exception ex) {
                log.warn("chain score failed: {}", ex.getMessage());
            }
        }

        log.info("Learned new QA: id={} question='{}...' answerLen={} chainScore={}",
                e.id(),
                e.question().substring(0, Math.min(40, e.question().length())),
                e.answer().length(), chainScore);

        LearnResponse body = new LearnResponse(e.id(), e.question(), e.answer(),
                e.category(), e.source(), index.size(),
                "QA learned. Retrieval index updated on disk.");
        return Response.ok(body).build();
    }

    @POST
    @Path("/bulk-learn")
    public Response bulkLearn(BulkLearnRequest req) {
        if (req == null || req.pairs == null || req.pairs.isEmpty()) {
            return Response.status(400)
                    .entity(Map.of("error", Map.of(
                            "message", "pairs array is required and must not be empty",
                            "type", "invalid_request_error",
                            "code", "missing_pairs")))
                    .build();
        }
        int added = 0;
        for (LearnRequest pair : req.pairs) {
            if (pair == null || pair.question == null || pair.question.isBlank()
                    || pair.answer == null || pair.answer.isBlank()) {
                continue;
            }
            index.add(pair.question.trim(), pair.answer.trim(),
                    pair.category, pair.source);
            added++;
        }
        return Response.ok(Map.of(
                "added", added,
                "total_corpus", index.size(),
                "message", "QA pairs learned and persisted on disk"
        )).build();
    }

    @GET
    @Path("/search")
    public Response search(@QueryParam("q") String query,
                           @QueryParam("topk") @DefaultValue("5") int topk) {
        if (query == null || query.isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", Map.of(
                            "message", "query param 'q' is required",
                            "type", "invalid_request_error",
                            "code", "missing_query")))
                    .build();
        }
        List<QaCorpusIndex.Entry> hits = index.search(query, topk);
        return Response.ok(Map.of(
                "query", query,
                "corpus_size", index.size(),
                "top_k", topk,
                "hits", hits.stream().map(e -> Map.of(
                        "id", e.id(),
                        "question", e.question(),
                        "answer", e.answer(),
                        "category", e.category()
                )).toList()
        )).build();
    }

    @GET
    @Path("/stats")
    public Response stats() {
        QaCorpusIndex.State s = index.state();
        return Response.ok(Map.of(
                "corpus_size", s.size(),
                "unique_tokens", s.inverted.size(),
                "loaded_at", s.loadedAt
        )).build();
    }

    @POST
    @Path("/reload")
    public Response reload() {
        index.reload();
        return Response.ok(Map.of(
                "corpus_size", index.size(),
                "unique_tokens", index.state().inverted.size(),
                "message", "QA corpus reloaded from disk"
        )).build();
    }
}
