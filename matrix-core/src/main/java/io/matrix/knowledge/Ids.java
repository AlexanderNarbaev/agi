package io.matrix.knowledge;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Deterministic identifiers for knowledge records.
 *
 * <p>Determinism is a product requirement, not a convenience: importing the
 * same pack twice must produce the SAME identifiers, so a second import
 * updates the existing records instead of creating a parallel copy of the
 * workspace. It also makes a knowledge package portable — the identifiers
 * travel with the content, so a workspace exported from one machine keeps its
 * identity when it is imported into another.
 *
 * <p>The identifier is a readable slug plus a short digest of the natural
 * key, so two different concepts with similar names never collide and a
 * human can still recognise what an identifier refers to.
 */
public final class Ids {

    private Ids() {
    }

    public static String concept(String workspaceId, String name) {
        return "c-" + slug(name) + "-" + digest(workspaceId + "|" + name);
    }

    public static String claim(String workspaceId, String conceptId, String proposition) {
        return "cl-" + digest(workspaceId + "|" + conceptId + "|" + proposition);
    }

    public static String source(String workspaceId, SourceKind kind, String locator) {
        return "s-" + slug(kind.name().toLowerCase(Locale.ROOT)) + "-" + digest(workspaceId + "|" + kind + "|" + locator);
    }

    public static String evidence(String workspaceId, String claimId, String sourceId) {
        return "e-" + digest(workspaceId + "|" + claimId + "|" + sourceId);
    }

    public static String representation(String workspaceId, String conceptId,
                                        AbstractionLevel level, String statement) {
        return "r-" + digest(workspaceId + "|" + conceptId + "|" + level + "|" + statement);
    }

    public static String revision(String workspaceId, String targetId,
                                  RevisionKind kind, String summary) {
        return "rev-" + digest(workspaceId + "|" + targetId + "|" + kind + "|" + summary);
    }

    private static String slug(String raw) {
        if (raw == null || raw.isBlank()) {
            return "x";
        }
        StringBuilder sb = new StringBuilder();
        boolean lastDash = true;
        for (char ch : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
                lastDash = false;
            } else if (!lastDash) {
                sb.append('-');
                lastDash = true;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.setLength(sb.length() - 1);
        }
        if (sb.length() > 40) {
            sb.setLength(40);
        }
        return sb.length() == 0 ? "x" : sb.toString();
    }

    /** FNV-1a over the UTF-8 bytes, rendered as eight hex characters. */
    private static String digest(String raw) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : raw.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return Long.toHexString(hash & 0xffffffffL);
    }
}
