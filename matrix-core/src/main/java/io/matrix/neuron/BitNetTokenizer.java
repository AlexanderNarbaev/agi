package io.matrix.neuron;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RUN 462 — Llama-compatible BPE tokenizer (DESIGN-54 §12, BitNet inference).
 *
 * <p>Pure-Java implementation of the GPT-2/Llama byte-level BPE tokenizer
 * used by microsoft/bitnet-b1.58-2B-4T. Loads vocab.json (128k base tokens),
 * merges.json (280k BPE rules), and special_tokens.json from resources.
 *
 * <h2>Algorithm</h2>
 * <pre>
 *   encode(text):
 *     1. Pre-tokenize via Llama regex (apostrophes, word boundaries, numbers)
 *     2. Byte-level encode: each byte → 2-char Unicode (space → 'Ġ')
 *     3. Apply BPE merges in priority order (longest match first)
 *     4. Look up each token in vocab
 *     5. Prepend BOS token (128000) if configured
 *
 *   decode(ids):
 *     1. Look up each id in vocab (base + special)
 *     2. Concatenate token strings
 *     3. Byte-level decode (reverse the byte mapping)
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function (encode/decode are deterministic given vocab+merges).
 * No RNG, no wall-clock. Loads from classpath at construction.
 */
public final class BitNetTokenizer {

    /** Llama pre-tokenization regex (from HuggingFace LlamaTokenizer). */
    private static final Pattern PRETOKENIZE_PATTERN = Pattern.compile(
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)"
            + "|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+"
            + "|\\p{N}{1,3}"
            + "| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*"
            + "|\\s*[\\r\\n]+"
            + "|\\s+(?!\\S)"
            + "|\\s+"
    );

    /** Vocab: token string → id. Includes base 128k + special 256. */
    public final Map<String, Integer> vocab;
    /** Inverse vocab: id → token string. */
    public final List<String> idToToken;
    /** Merges list: each is a 2-element list of token strings. */
    private final List<String[]> merges;
    /** Merge rank map: "a|b" → priority rank (lower = higher priority). */
    private final Map<String, Integer> mergeRanks;
    /** Byte-to-Unicode mapping (GPT-2 byte fallback). */
    private final Map<Integer, String> byteToUnicode;
    /** Unicode-to-byte inverse mapping for decode. */
    private final Map<String, Integer> unicodeToByte;
    /** BOS token id (128000). */
    public final int bosTokenId;
    /** EOS token id (128001). */
    public final int eosTokenId;
    /** Pad token id (-1 if no pad token defined). */
    public final int padTokenId;

    /**
     * Load tokenizer from classpath resources.
     */
    public BitNetTokenizer() throws IOException {
        this.vocab = new HashMap<>();
        this.idToToken = new ArrayList<>();
        this.mergeRanks = new HashMap<>();
        this.merges = new ArrayList<>();
        this.byteToUnicode = buildByteToUnicode();
        this.unicodeToByte = invertMap(this.byteToUnicode);

        // Load vocab
        try (InputStream is = getResource("tokenizer/vocab.json");
             InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            String json = sb.toString();
            // Minimal JSON array parser (each entry is a quoted string)
            parseStringArrayIntoList(json, idToToken);
            for (int i = 0; i < idToToken.size(); i++) {
                vocab.put(idToToken.get(i), i);
            }
        }

        // Load special tokens
        int bosId = -1, eosId = -1, padId = -1;
        try (InputStream is = getResource("tokenizer/special_tokens.json");
             InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            String json = sb.toString();
            // Parse [{"id":..., "content":...}, ...]
            List<int[]> ids = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            parseSpecialTokens(json, ids, contents);
            for (int i = 0; i < ids.size(); i++) {
                int id = ids.get(i).length > 0 ? ids.get(i)[0] : -1;
                if (id >= idToToken.size()) {
                    while (idToToken.size() <= id) idToToken.add(null);
                }
                if (id >= 0 && id < idToToken.size()) {
                    idToToken.set(id, contents.get(i));
                    vocab.put(contents.get(i), id);
                }
                if (contents.get(i).equals("<|begin_of_text|>")) bosId = id;
                if (contents.get(i).equals("<|end_of_text|>")) eosId = id;
            }
        }
        this.bosTokenId = bosId;
        this.eosTokenId = eosId;
        this.padTokenId = padId;

        // Load merges
        try (InputStream is = getResource("tokenizer/merges.json");
             InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[16384];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            String json = sb.toString();
            parseMergesArray(json, merges, mergeRanks);
        }
    }

    private static InputStream getResource(String path) throws IOException {
        InputStream is = BitNetTokenizer.class.getClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new IOException("Resource not found: " + path);
        }
        return is;
    }

    /**
     * Encode text into token IDs (without BOS prefix).
     */
    public int[] encode(String text) {
        // 1. Pre-tokenize
        List<String> pretokens = new ArrayList<>();
        Matcher m = PRETOKENIZE_PATTERN.matcher(text);
        while (m.find()) {
            pretokens.add(m.group());
        }

        // 2. For each pretoken, byte-encode then apply BPE
        List<Integer> allIds = new ArrayList<>();
        for (String pretoken : pretokens) {
            // Byte-level encode
            StringBuilder encoded = new StringBuilder();
            byte[] bytes = pretoken.getBytes(StandardCharsets.UTF_8);
            for (byte b : bytes) {
                int ub = b & 0xFF;
                encoded.append(byteToUnicode.get(ub));
            }
            // Apply BPE
            List<String> word = new ArrayList<>();
            for (int i = 0; i < encoded.length(); ) {
                int cp = encoded.codePointAt(i);
                word.add(new String(Character.toChars(cp)));
                i += Character.charCount(cp);
            }
            List<String> bpeTokens = applyBpe(word);
            for (String t : bpeTokens) {
                Integer id = vocab.get(t);
                if (id != null) {
                    allIds.add(id);
                }
                // Skip unknown tokens (could log warning, but for simplicity ignore)
            }
        }

        int[] ids = new int[allIds.size()];
        for (int i = 0; i < allIds.size(); i++) ids[i] = allIds.get(i);
        return ids;
    }

    /**
     * Encode text and prepend BOS token.
     */
    public int[] encodeWithBos(String text) {
        int[] body = encode(text);
        int[] withBos = new int[body.length + 1];
        withBos[0] = bosTokenId;
        System.arraycopy(body, 0, withBos, 1, body.length);
        return withBos;
    }

    /**
     * Decode token IDs back to text.
     */
    public String decode(int[] ids) {
        // Skip BOS if present at start
        int start = 0;
        if (ids.length > 0 && ids[0] == bosTokenId) start = 1;
        StringBuilder sb = new StringBuilder();
        byte[] bytes = new byte[1024];
        int byteCount = 0;
        for (int i = start; i < ids.length; i++) {
            int id = ids[i];
            String token = (id >= 0 && id < idToToken.size()) ? idToToken.get(id) : "";
            if (token == null) continue;
            // Decode each char in token
            for (int j = 0; j < token.length(); ) {
                int cp = token.codePointAt(j);
                j += Character.charCount(cp);
                String s = new String(Character.toChars(cp));
                Integer b = unicodeToByte.get(s);
                if (b != null) {
                    if (byteCount >= bytes.length) {
                        byte[] newBytes = new byte[bytes.length * 2];
                        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                        bytes = newBytes;
                    }
                    bytes[byteCount++] = b.byteValue();
                }
            }
        }
        return new String(bytes, 0, byteCount, StandardCharsets.UTF_8);
    }

    /**
     * Apply BPE merges to a single word.
     */
    private List<String> applyBpe(List<String> word) {
        if (word.size() <= 1) return word;
        while (true) {
            // Find pair with lowest rank
            int bestRank = Integer.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < word.size() - 1; i++) {
                String pair = word.get(i) + "|" + word.get(i + 1);
                Integer rank = mergeRanks.get(pair);
                if (rank != null && rank < bestRank) {
                    bestRank = rank;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) break;
            // Merge pair at bestIdx
            String merged = word.get(bestIdx) + word.get(bestIdx + 1);
            List<String> newWord = new ArrayList<>(word.size() - 1);
            for (int i = 0; i < word.size(); i++) {
                if (i == bestIdx) {
                    newWord.add(merged);
                    i++; // skip next
                } else if (i < word.size()) {
                    newWord.add(word.get(i));
                }
            }
            word = newWord;
            if (word.size() <= 1) break;
        }
        return word;
    }

    /**
     * GPT-2 byte fallback: map each byte to a printable Unicode character.
     */
    private static Map<Integer, String> buildByteToUnicode() {
        Map<Integer, String> map = new HashMap<>();
        // Printable ASCII bytes map to themselves
        for (int b = 33; b <= 126; b++) {
            map.put(b, new String(Character.toChars(b)));
        }
        for (int b = 161; b <= 172; b++) {
            map.put(b, new String(Character.toChars(b)));
        }
        for (int b = 174; b <= 255; b++) {
            map.put(b, new String(Character.toChars(b)));
        }
        // Remaining bytes (0-32, 127-160, 173) get mapped to 256+i
        List<Integer> remaining = new ArrayList<>();
        for (int b = 0; b < 256; b++) {
            if (!map.containsKey(b)) remaining.add(b);
        }
        int n = 0;
        for (int b : remaining) {
            int codePoint = 256 + n;
            map.put(b, new String(Character.toChars(codePoint)));
            n++;
        }
        return map;
    }

    private static <K, V> Map<V, K> invertMap(Map<K, V> map) {
        Map<V, K> inv = new HashMap<>();
        for (Map.Entry<K, V> e : map.entrySet()) {
            inv.put(e.getValue(), e.getKey());
        }
        return inv;
    }

    // ===== Minimal JSON parsers (no external dependency) =====

    private static void parseStringArrayIntoList(String json, List<String> out) {
        // Simple parser for JSON array of strings
        int i = json.indexOf('[');
        while ((i = json.indexOf('"', i + 1)) != -1) {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            if (i + 5 < json.length()) {
                                int cp = Integer.parseInt(json.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 4;
                            }
                            break;
                        default: sb.append(next);
                    }
                    i += 2;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            out.add(sb.toString());
        }
    }

    private static void parseSpecialTokens(String json, List<int[]> ids,
                                           List<String> contents) {
        // Parse flat array format: [[128000, "<|begin_of_text|>"], [128001, "<|end_of_text|>"], ...]
        // Use bracket depth counter to find each inner tuple correctly.
        int depth = 0;
        int tupleStart = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                if (depth == 1) {
                    tupleStart = i + 1;
                }
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 1 && tupleStart >= 0) {
                    String tuple = json.substring(tupleStart, i);
                    int comma = tuple.indexOf(',');
                    if (comma > 0) {
                        String idStr = tuple.substring(0, comma).trim();
                        String contentStr = tuple.substring(comma + 1).trim();
                        if (contentStr.startsWith("\"") && contentStr.endsWith("\"")) {
                            contentStr = contentStr.substring(1, contentStr.length() - 1);
                        }
                        try {
                            int id = Integer.parseInt(idStr);
                            ids.add(new int[]{id});
                            contents.add(contentStr);
                        } catch (NumberFormatException e) {
                            // skip malformed
                        }
                    }
                    tupleStart = -1;
                }
            }
        }
    }

    private static void parseMergesArray(String json, List<String[]> merges,
                                         Map<String, Integer> mergeRanks) {
        // Parse ["Ġ a", "Ġ t", ...]
        int i = json.indexOf('[');
        int rank = 0;
        while ((i = json.indexOf('"', i + 1)) != -1) {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    if (next == 'u' && i + 5 < json.length()) {
                        int cp = Integer.parseInt(json.substring(i + 2, i + 6), 16);
                        sb.append((char) cp);
                        i += 4;
                    } else if (next == 'n') sb.append('\n');
                    else if (next == 't') sb.append('\t');
                    else if (next == 'r') sb.append('\r');
                    else sb.append(next);
                    i += 2;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            String merge = sb.toString();
            int sp = merge.indexOf(' ');
            if (sp > 0) {
                String a = merge.substring(0, sp);
                String b = merge.substring(sp + 1);
                String[] pair = new String[]{a, b};
                merges.add(pair);
                mergeRanks.put(a + "|" + b, rank++);
            }
        }
    }
}
