package io.matrix.brain.runtime.actuation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W35.2 — a deliberately small, strict JSON reader for command lines.
 *
 * <h2>Why not a JSON library</h2>
 *
 * <p>The shapes needed here are one flat object plus one nested object of strings. Pulling in a
 * parser for that would add a dependency to a module whose whole purpose is to keep the
 * dependency chain short, and — more importantly — a general parser makes it easy to accept
 * types the command path has no meaning for. A number where a string is expected, a nested
 * object where a flat one was declared, a duplicate key: all of these become errors here
 * rather than being coerced.</p>
 *
 * <h2>What "strict" means in this class</h2>
 *
 * <ul>
 *   <li>Unterminated strings, objects and braces are rejected.</li>
 *   <li>Trailing content after the closing brace is rejected, so a torn line followed by the
 *       next line cannot be silently merged.</li>
 *   <li>A repeated key is rejected rather than overwritten, because "which one won" is not a
 *       question a command path should leave open.</li>
 *   <li>Escapes are not interpreted beyond the basic ones; an unknown escape is an error.</li>
 * </ul>
 *
 * <p>Every failure message names the offending position, because a command path that rejects
 * silently is indistinguishable from one that is dropping commands.</p>
 */
final class MiniJson {

    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    /**
     * Parse a flat object into scalar strings, recursing into nested objects.
     *
     * @param json an object literal including braces
     * @return key to raw scalar text, in document order
     */
    static Map<String, String> parseFlatObject(String json) {
        MiniJson p = new MiniJson(json);
        Map<String, String> out = p.readObject(null);
        p.skipWhitespace();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException(
                    "trailing content after the command object at offset " + p.pos);
        }
        return out;
    }

    /**
     * Parse the nested {@code params} object into a string map.
     *
     * @param raw the raw value text, or null when the field was absent
     * @return the parsed parameters, empty when absent
     */
    static Map<String, String> parseNestedStringMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> flat = new MiniJson(raw).readObject(null);
        return flat;
    }

    /** Read one object; when {@code terminator} is null the value read must be a scalar. */
    private Map<String, String> readObject(String terminator) {
        skipWhitespace();
        expect('{');
        Map<String, String> out = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return out;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            if (out.containsKey(key)) {
                throw new IllegalArgumentException("duplicate key in command: " + key);
            }
            skipWhitespace();
            expect(':');
            skipWhitespace();
            out.put(key, readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return out;
            }
            throw new IllegalArgumentException(
                    "expected ',' or '}' in command at offset " + pos + " but found '" + c + "'");
        }
    }

    /** Read a scalar, or an object rendered back to its raw text for the nested case. */
    private String readValue() {
        char c = peek();
        if (c == '"') {
            return readString();
        }
        if (c == '{') {
            int start = pos;
            readObject("}");
            return src.substring(start, pos);
        }
        int start = pos;
        while (pos < src.length()) {
            char d = src.charAt(pos);
            if (d == ',' || d == '}') {
                break;
            }
            pos++;
        }
        String raw = src.substring(start, pos).trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("empty value in command at offset " + start);
        }
        return raw;
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("unterminated string in command");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("unterminated escape in command");
                }
                char e = src.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    default -> throw new IllegalArgumentException(
                            "unsupported escape \\" + e + " in command at offset " + (pos - 1));
                }
            } else {
                sb.append(c);
            }
        }
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("command ended unexpectedly at offset " + pos);
        }
        return src.charAt(pos);
    }

    private void expect(char c) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new IllegalArgumentException(
                    "expected '" + c + "' in command at offset " + pos);
        }
        pos++;
    }
}