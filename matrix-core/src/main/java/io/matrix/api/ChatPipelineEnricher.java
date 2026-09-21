package io.matrix.api;

import io.matrix.model.ModelRegistry;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility helper: enriches OpenAIChatResource responses with model
 * registry metadata (sentiment prediction, topic routing). Wired into
 * the chat pipeline via constructor injection in {@link OpenAIChatResource}.
 */
public final class ChatPipelineEnricher {

    private static final Logger log = LoggerFactory.getLogger(ChatPipelineEnricher.class);

    private final ModelRegistry registry;

    public ChatPipelineEnricher(ModelRegistry registry) {
        this.registry = registry;
    }

    /**
     * Run the sentiment classifier on the input bits and return a
     * small metadata map that the chat can attach to its response.
     */
    public Map<String, Object> enrich(String userText, long[] inputBits, String response) {
        if (registry == null) {
            return Map.of();
        }
        try {
            int sentiment = registry.predictSentiment(inputBits);
            // collapse to 4 bits for the topic router
            long[] routerInput = new long[1];
            routerInput[0] = inputBits.length > 0 ? (inputBits[0] & 0xF) : 0L;
            int topic = registry.routeTopic(routerInput);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sentiment", sentiment == 1 ? "positive" : "negative");
            meta.put("topicCode", topic);
            meta.put("topic", switch (topic) {
                case 0 -> "chat";
                case 1 -> "qa";
                case 2 -> "command";
                default -> "none";
            });
            meta.put("registryEvals", registry.totalEvaluations());
            log.debug("enriched chat metadata: {}", meta);
            return meta;
        } catch (Exception e) {
            log.warn("ChatPipelineEnricher failed: {}", e.getMessage());
            return Map.of("enricherError", e.getMessage());
        }
    }
}