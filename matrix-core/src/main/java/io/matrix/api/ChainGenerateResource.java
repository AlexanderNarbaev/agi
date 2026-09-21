package io.matrix.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real-time text generation using the boolean chain.
 *
 * <p>POST /v1/generate {prompt, max_tokens, temperature, seed}
 *       — runs the chain through the {@link ChainTextGenerator}
 *       autoregressive loop. Not a transformer, but a real
 *       generative model: chain weights determine the next-token
 *       distribution, training on the chain shifts the outputs.
 *
 * <p>GET /v1/generate/status — whether the generator is online
 *       (chain loaded AND BPE tokenizer available).
 */
@Path("/v1/generate")
@Produces(MediaType.APPLICATION_JSON)
public class ChainGenerateResource {

    private static final Logger log = LoggerFactory.getLogger(ChainGenerateResource.class);

    @Inject
    ChainTextGenerator generator;

    public record GenerateRequest(String prompt, Integer max_tokens,
                                  Double temperature, Long seed) {}

    @POST
    public Map<String, Object> generate(GenerateRequest req) {
        String prompt = req != null && req.prompt != null ? req.prompt : "";
        int max = req != null && req.max_tokens != null ? req.max_tokens : 32;
        double temp = req != null && req.temperature != null ? req.temperature : 0.0;
        long seed = req != null && req.seed != null ? req.seed : 42L;
        if (max < 0) max = 0;
        if (max > 512) max = 512;

        long t0 = System.currentTimeMillis();
        String output;
        boolean chainUsed = generator.isAvailable();
        if (chainUsed) {
            output = generator.generate(prompt, max, temp, seed);
        } else {
            // Fall back to plain continuation (no chain) when tokenizer
            // or chain missing. Still deterministic by hash.
            output = prompt + " [no-chain-fallback]";
        }
        long ms = System.currentTimeMillis() - t0;

        log.info("generate: promptLen={} maxTokens={} temp={} chain={} ms={}",
                prompt.length(), max, temp, chainUsed, ms);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("prompt", prompt);
        resp.put("output", output);
        resp.put("generated_tokens", countApproxTokens(output) - countApproxTokens(prompt));
        resp.put("max_tokens", max);
        resp.put("temperature", temp);
        resp.put("seed", seed);
        resp.put("chain_used", chainUsed);
        resp.put("elapsed_ms", ms);
        return resp;
    }

    @GET
    @Path("/status")
    public Map<String, Object> status() {
        boolean avail = generator.isAvailable();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("available", avail);
        resp.put("chain_loaded", generator.isAvailable());
        resp.put("vocab_size", generator.vocabSize());
        resp.put("hint", avail
                ? "POST /v1/generate with {prompt,max_tokens,temperature,seed}"
                : "Tokenizer or chain not loaded — generation falls back to no-op");
        return resp;
    }

    private static int countApproxTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\\s+").length;
    }
}
