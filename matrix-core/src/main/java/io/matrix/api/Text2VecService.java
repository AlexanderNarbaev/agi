package io.matrix.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Text-to-binary-vector converter for MPDT neuron input/output.
 *
 * <p>Converts natural language text into 20-bit binary vectors suitable
 * for McCulloch-Pitts Decision Tree neurons, and maps 5-bit action codes
 * back to human-readable response templates.
 *
 * <p>Uses word hashing: each unique word maps to a bit position via
 * {@code Math.abs(hashCode()) % VECTOR_BITS}. Multiple words = multiple
 * bits set, providing a sparse binary representation.
 */
@ApplicationScoped
@RegisterForReflection
public class Text2VecService {

    /** Fixed vector width matching MPDT K_MAX = 20. */
    public static final int VECTOR_BITS = 20;

    /** Number of distinct response templates (2^5 = 32 action codes). */
    public static final int TEMPLATE_COUNT = 32;

    /**
     * Converts text to a 20-bit binary vector.
     *
     * <p>Each word's hash maps to a bit position. Words producing the same
     * position are OR'd together, so the vector captures word presence rather
     * than frequency. Common stop words contribute to the representation.
     *
     * @param text input text (may be null — returns zero vector)
     * @return 20-bit vector with bits set for each unique word position
     */
    public long textToBits(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }

        long bits = 0;
        String[] words = text.toLowerCase().split("\\W+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            int hash = Math.abs(word.hashCode()) % VECTOR_BITS;
            bits |= (1L << hash);
        }
        return bits;
    }

    /**
     * Converts a binary vector to a response template string.
     *
     * <p>The lower 5 bits of the vector select one of 32 response templates.
     * This provides deterministic but varied responses based on neuron output.
     *
     * @param bits binary vector from neuron decision (5 least-significant bits used)
     * @return one of 32 response templates
     */
    public String bitsToResponse(long bits) {
        int idx = (int) (bits & 0x1F);
        return TEMPLATES[idx];
    }

    /**
     * Returns the action code (0-31) extracted from a binary vector.
     * Useful when the caller needs to know which template was selected.
     */
    public int actionCode(long bits) {
        return (int) (bits & 0x1F);
    }

    // ─── 32 Response Templates ───

    private static final String[] TEMPLATES = {
        "I understand. Let me think about that.",
        "That's an interesting perspective. Thank you for sharing.",
        "Based on my analysis, I would suggest exploring this further.",
        "I appreciate you sharing that with me.",
        "Let me explain my reasoning: MPDT neurons process this as a binary decision path.",
        "Interesting. My neural clusters are evaluating this input now.",
        "I see. From my hierarchical brain perspective, this makes sense.",
        "Thank you for the input. My sensors have registered this.",
        "That aligns with what I understand. Let me expand on that.",
        "Fascinating. The MATRIX framework would approach this through truth tables.",
        "I follow your logic. My decision trees confirm this interpretation.",
        "Good point. My action layer is analyzing the feature vector now.",
        "Let me break this down: the binary representation suggests multiple interpretations.",
        "I'm processing this through my sensor layer. One moment.",
        "Noted. I'll incorporate this into my evolving knowledge base.",
        "That triggers several of my MPDT neurons. Let me elaborate.",
        "From an evolutionary standpoint, this is a promising direction.",
        "My ethical filter confirms this is safe to discuss. Here's my take:",
        "The hierarchical brain approach gives me a structured way to think about this.",
        "Interesting pattern. My feature extraction layer is highlighting key aspects.",
        "I can work with that. Let me route it through the appropriate neural pathway.",
        "That's worth investigating. My curiosity driver is engaged.",
        "I've recorded this input. Future generations of neurons may benefit from it.",
        "Clear. My truth tables are deterministic — here's what I compute:",
        "That resonates with my training data. Let me share my perspective.",
        "Good question. The answer involves several layers of neural processing.",
        "I detect a pattern here. Let me correlate with previous inputs.",
        "My action layer suggests this is a productive line of inquiry.",
        "That's consistent with what I've learned. Let me add some context.",
        "Processing complete. Here's what my neurons determined:",
        "I'm functioning well. My neural clusters are active and learning.",
        "Let me consult my truth tables. Yes, I have a response for this."
    };
}
