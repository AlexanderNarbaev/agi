package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MIND-W1 — Stage 5: Analogy by HDC similarity transfer.
 *
 * <p>Recognizes the canonical analogy shape "A is to B as C is to ?" and
 * answers by analogy from a tiny in-memory analogy table. The transfer
 * is performed by exact-pair lookup + relational derivation (gender swap).
 * MIND-W2 will replace the seed map with a vector-cosine transfer over
 * the HDC store; MIND-W3 will grow the map from consolidated sleep cycles.</p>
 *
 * <p>Seed format: each entry encodes the pair (A1, B1) such that
 * "(A1) is to (B1)" via the implicit relation R; and the seed gives
 * {A2, B2} that share the same relation R. So A1:B1 :: A2:B2.</p>
 */
public final class AnalogyStage {

    private static final Pattern ANALOGY = Pattern.compile(
        "(?i)\\s*([\\w\\-]+)\\s+is\\s+to\\s+([\\w\\-]+)\\s+as\\s+([\\w\\-]+)\\s+is\\s+to\\s+\\?\\s*"
    );

    /** Seed entries: (A, B, C, D) means A is to B as C is to D. */
    private static final List<String[]> SEED = List.of(
        // Royalty gender
        s("king", "queen", "man", "woman"),
        s("queen", "king", "woman", "man"),
        // Family
        s("father", "mother", "dad", "mom"),
        s("mother", "father", "mom", "dad"),
        s("son", "daughter", "boy", "girl"),
        s("daughter", "son", "girl", "boy"),
        // Celestial / time
        s("sun", "moon", "day", "night"),
        s("moon", "sun", "night", "day"),
        // Animal
        s("cat", "dog", "kitten", "puppy"),
        // Temperature
        s("hot", "cold", "warm", "cool"),
        // Spatial
        s("tall", "short", "high", "low"),
        s("up", "down", "north", "south")
    );

    private static String[] s(String a, String b, String c, String d) {
        return new String[]{a, b, c, d};
    }

    public record AnalogyResult(boolean matched, String reply, double confidence) {
        public static AnalogyResult miss() {
            return new AnalogyResult(false, "", 0.0);
        }
        public static AnalogyResult hit(String reply, double confidence) {
            return new AnalogyResult(true, reply, confidence);
        }
    }

    public AnalogyResult tryEvaluate(String input, List<BrcStep> trace) {
        String normalized = input.trim();
        if (!normalized.contains(" is to ") && !normalized.contains("::")) {
            trace.add(BrcStep.of("ANALOGY", false, 0.50,
                List.of("reason=not-analogy-shape")));
            return AnalogyResult.miss();
        }
        String A, B, C;
        if (normalized.contains("::")) {
            // "king:queen :: man:?"
            String[] parts = normalized.split("::");
            if (parts.length != 2) {
                trace.add(BrcStep.of("ANALOGY", false, 0.50,
                    List.of("reason=invalid-colon-shape")));
                return AnalogyResult.miss();
            }
            String[] lr = parts[0].trim().split(":");
            String[] rr = parts[1].trim().split(":");
            if (lr.length != 2 || rr.length != 2 || !"?".equals(rr[1])) {
                trace.add(BrcStep.of("ANALOGY", false, 0.50,
                    List.of("reason=invalid-colon-shape")));
                return AnalogyResult.miss();
            }
            A = lr[0].trim().toLowerCase();
            B = lr[1].trim().toLowerCase();
            C = rr[0].trim().toLowerCase();
        } else {
            Matcher m = ANALOGY.matcher(normalized);
            if (!m.matches()) {
                trace.add(BrcStep.of("ANALOGY", false, 0.50,
                    List.of("reason=not-is-to-shape")));
                return AnalogyResult.miss();
            }
            A = m.group(1).toLowerCase();
            B = m.group(2).toLowerCase();
            C = m.group(3).toLowerCase();
        }

        for (String[] entry : SEED) {
            // entry: {A_seed, B_seed, C_seed, D_seed}
            if (entry[0].equals(A) && entry[1].equals(B) && entry[2].equals(C)) {
                String D = entry[3];
                String reply = A + " is to " + B + " as " + C + " is to " + D;
                trace.add(BrcStep.of("ANALOGY", true, 0.85,
                    List.of("seed_hit=A=" + A + ",B=" + B + ",C=" + C, "D=" + D)));
                return AnalogyResult.hit(reply, 0.85);
            }
            // Inverse: (B,A) :: (D,C) is the same relation
            if (entry[1].equals(A) && entry[0].equals(B) && entry[3].equals(C)) {
                String D = entry[2];
                String reply = A + " is to " + B + " as " + C + " is to " + D;
                trace.add(BrcStep.of("ANALOGY", true, 0.85,
                    List.of("seed_hit_inverse=B=" + A, "D=" + D)));
                return AnalogyResult.hit(reply, 0.85);
            }
        }
        trace.add(BrcStep.of("ANALOGY", false, 0.50,
            List.of("reason=no-analogy-found", "A=" + A, "B=" + B, "C=" + C)));
        return AnalogyResult.miss();
    }
}
