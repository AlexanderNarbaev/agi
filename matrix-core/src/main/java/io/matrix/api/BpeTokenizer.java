package io.matrix.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Minimal Qwen-style BPE tokenizer (Wave I, replaces the position-aware
 * hash encoder with a real BPE). Loads {@code vocab.json} and
 * {@code merges.txt} from a HuggingFace model directory and applies
 * the BPE merge rules.
 *
 * <p>This is intentionally minimal — it handles byte-level BPE with
 * GPT-2-style pre-tokenization (regex split on word boundaries +
 * punctuation). For Qwen, the tokenizer is identical to GPT-2's
 * BPE, so the same algorithm works.
 *
 * <p>Usage:
 * <pre>{@code
 * BpeTokenizer tok = BpeTokenizer.fromModelDir(Path.of("models/external/qwen2.5-0.5b"));
 * int[] ids = tok.encode("hello world");
 * // or, for boolean chain input:
 * boolean[] bits = tok.textToBits("hello world", 896);
 * }</pre>
 */
public final class BpeTokenizer {

    /** Map token-string → id (from vocab.json). */
    private final Map<String, Integer> vocab;
    /** Ordered list of merge rules (from merges.txt). Each is "a b". */
    private final List<String[]> merges;
    /** Reverse vocab for decoding. */
    private final Map<Integer, String> reverseVocab;

    /**
     * GPT-2-style pre-tokenization. One regex matching common
     * word/punctuation/number/whitespace patterns.
     */
    private static final Pattern PRE_TOKENIZE = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d|"
                    + " ?\\p{L}+|"
                    + " ?\\p{N}+|"
                    + " ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+");

    /** Visible-for-testing constructor. Use {@link #fromModelDir} in production. */
    BpeTokenizer(Map<String, Integer> vocab, List<String[]> merges) {
        this.vocab = Collections.unmodifiableMap(new HashMap<>(vocab));
        this.merges = List.copyOf(merges);
        Map<Integer, String> rev = new HashMap<>();
        for (var e : vocab.entrySet()) rev.put(e.getValue(), e.getKey());
        this.reverseVocab = Collections.unmodifiableMap(rev);
    }

    /** Load from a model directory containing vocab.json and merges.txt. */
    public static BpeTokenizer fromModelDir(Path modelDir) throws IOException {
        Path vocabFile = findFile(modelDir, "vocab.json");
        Path mergesFile = findFile(modelDir, "merges.txt");
        Path tokenizerConfigFile = findFile(modelDir, "tokenizer_config.json");
        if (vocabFile == null || mergesFile == null) {
            throw new IOException("vocab.json and merges.txt required at " + modelDir);
        }
        Map<String, Integer> vocab = readVocabJson(vocabFile);
        // Add special tokens from tokenizer_config.json if present
        if (tokenizerConfigFile != null) {
            addSpecialTokens(tokenizerConfigFile, vocab);
        }
        List<String[]> merges = readMergesTxt(mergesFile);
        return new BpeTokenizer(vocab, merges);
    }

    /**
     * Parse the added_tokens_decoder section of tokenizer_config.json and
     * add special tokens to the vocab map. Each entry is keyed by id and
     * contains a "content" string.
     */
    private static void addSpecialTokens(Path configFile, Map<String, Integer> vocab)
            throws IOException {
        String text = Files.readString(configFile, StandardCharsets.UTF_8);
        // Find "added_tokens_decoder" block
        int idx = text.indexOf("\"added_tokens_decoder\"");
        if (idx < 0) return;
        int braceStart = text.indexOf('{', idx);
        if (braceStart < 0) return;
        // Match braces to find the end
        int depth = 0;
        int end = -1;
        for (int i = braceStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (end < 0) return;
        String block = text.substring(braceStart, end + 1);
        // Walk through entries: "id": { "content": "..." ... }
        int p = 0;
        while (p < block.length()) {
            // Skip whitespace and commas
            while (p < block.length()
                    && (Character.isWhitespace(block.charAt(p))
                            || block.charAt(p) == ',')) {
                p++;
            }
            if (p >= block.length()) break;
            if (block.charAt(p) != '"') { p++; continue; }
            int q1 = p;
            int q2 = block.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String idStr = block.substring(q1 + 1, q2);
            int id;
            try {
                id = Integer.parseInt(idStr);
            } catch (NumberFormatException nfe) {
                p = q2 + 1;
                continue;
            }
            // Find the entry's opening brace
            int entryStart = block.indexOf('{', q2);
            if (entryStart < 0) break;
            int depthEntry = 0;
            int entryEnd = -1;
            for (int i = entryStart; i < block.length(); i++) {
                char c = block.charAt(i);
                if (c == '{') depthEntry++;
                else if (c == '}') {
                    depthEntry--;
                    if (depthEntry == 0) {
                        entryEnd = i;
                        break;
                    }
                }
            }
            if (entryEnd < 0) break;
            String entry = block.substring(entryStart, entryEnd + 1);
            // Look for "content" inside this entry
            int contentIdx = entry.indexOf("\"content\"");
            if (contentIdx >= 0) {
                int colon = entry.indexOf(':', contentIdx);
                int c1 = entry.indexOf('"', colon);
                int c2 = entry.indexOf('"', c1 + 1);
                if (c1 >= 0 && c2 > c1) {
                    String content = unescapeJson(entry.substring(c1 + 1, c2));
                    if (!content.isEmpty()) {
                        vocab.putIfAbsent(content, id);
                    }
                }
            }
            p = entryEnd + 1;
        }
    }

    private static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'n') sb.append('\n');
                else if (next == 't') sb.append('\t');
                else if (next == 'r') sb.append('\r');
                else if (next == '"') sb.append('"');
                else if (next == '\\') sb.append('\\');
                else sb.append(next);
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Path findFile(Path dir, String name) throws IOException {
        try (var stream = Files.walk(dir, 3)) {
            return stream.filter(p -> p.getFileName().toString().equals(name))
                    .findFirst().orElse(null);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> readVocabJson(Path file) throws IOException {
        // simple JSON parser for { "tok": id, ... } — avoids jackson dep
        String text = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (!text.startsWith("{")) throw new IOException("not a JSON object: " + file);
        Map<String, Integer> vocab = new LinkedHashMap<>();
        // strip braces, then parse tokens
        text = text.substring(1, text.length() - 1);
        int i = 0;
        while (i < text.length()) {
            // skip whitespace and commas
            while (i < text.length() && (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == ',')) i++;
            if (i >= text.length()) break;
            // read string key
            if (text.charAt(i) != '"') throw new IOException("expected '\"' at " + i);
            i++;
            StringBuilder key = new StringBuilder();
            while (i < text.length() && text.charAt(i) != '"') {
                char c = text.charAt(i);
                if (c == '\\' && i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    if (next == 'n') key.append('\n');
                    else if (next == 't') key.append('\t');
                    else if (next == 'r') key.append('\r');
                    else if (next == '"') key.append('"');
                    else if (next == '\\') key.append('\\');
                    else if (next == '/') key.append('/');
                    else key.append(next);
                    i += 2;
                } else {
                    key.append(c);
                    i++;
                }
            }
            i++;  // closing "
            // skip colon + value (an integer)
            while (i < text.length() && (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == ':')) i++;
            int valStart = i;
            while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '-')) i++;
            int id = Integer.parseInt(text.substring(valStart, i).trim());
            vocab.put(key.toString(), id);
        }
        return vocab;
    }

    private static List<String[]> readMergesTxt(Path file) throws IOException {
        List<String[]> merges = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean firstLine = true;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (firstLine && line.startsWith("#")) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;
                int sp = line.indexOf(' ');
                if (sp > 0) {
                    merges.add(new String[]{line.substring(0, sp), line.substring(sp + 1)});
                }
            }
        }
        return merges;
    }

    /** BPE-encode a string into a list of token ids. */
    public int[] encode(String text) {
        if (text == null || text.isEmpty()) return new int[0];
        List<Integer> ids = new ArrayList<>();
        // Walk through text, looking for special tokens (<|...|>) and
        // encoding each segment with BPE.
        int i = 0;
        while (i < text.length()) {
            int specialStart = text.indexOf("<|", i);
            if (specialStart < 0) {
                ids.addAll(encodeSegment(text.substring(i)));
                break;
            }
            // Encode the prefix before the special token
            if (specialStart > i) {
                ids.addAll(encodeSegment(text.substring(i, specialStart)));
            }
            // Find the matching closing |>
            int specialEnd = text.indexOf("|>", specialStart);
            if (specialEnd < 0) {
                ids.addAll(encodeSegment(text.substring(specialStart)));
                break;
            }
            String specialToken = text.substring(specialStart, specialEnd + 2);
            Integer specialId = vocab.get(specialToken);
            if (specialId != null) {
                ids.add(specialId);
            } else {
                // Fallback: BPE-encode the literal text
                ids.addAll(encodeSegment(specialToken));
            }
            i = specialEnd + 2;
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    /** BPE-encode a single segment (no special tokens assumed). */
    private List<Integer> encodeSegment(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        // 1. pre-tokenize
        List<String> preTokens = new ArrayList<>();
        var m = PRE_TOKENIZE.matcher(text);
        while (m.find()) preTokens.add(m.group());
        // 2. for each pre-token, get byte-level chars and apply BPE merges
        for (String pre : preTokens) {
            int[] byteIds = bytesToBase(pre);
            ids.addAll(applyBpe(byteIds));
        }
        return ids;
    }

    /** Look up the vocab token for a given id (raw, no decode). */
    public String reverseToken(int id) {
        return reverseVocab.get(id);
    }

    /** Decode ids back to string. */
    public String decode(int[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            String tok = reverseVocab.get(id);
            if (tok == null) continue;
            // If it's a special token (e.g. <|im_end|>), emit it literally
            if (tok.startsWith("<|") && tok.endsWith("|>")) {
                sb.append(tok);
                continue;
            }
            // GPT-2 byte-level BPE uses Ġ (U+0120) as whitespace marker.
            // Replace it with a real space at the start of each subword.
            if (tok.startsWith("Ġ")) {
                sb.append(' ').append(tok.substring(1));
            } else {
                sb.append(tok);
            }
        }
        // un-byte-decode the GPT-2 byte-level mapping (latin-1 fallback)
        String raw = sb.toString();
        return unbyteDecode(raw);
    }

    /** Apply BPE merges to a sequence of byte-level token ids. */
    private List<Integer> applyBpe(int[] byteIds) {
        // convert each byte to its GPT-2 byte-level unicode string
        List<String> symbols = new ArrayList<>();
        for (int b : byteIds) symbols.add(byteLevelChar(b));
        // apply merges in order
        for (String[] merge : merges) {
            String merged = merge[0] + merge[1];
            for (int i = 0; i + 1 < symbols.size(); i++) {
                if (symbols.get(i).equals(merge[0]) && symbols.get(i + 1).equals(merge[1])) {
                    symbols.set(i, merged);
                    symbols.remove(i + 1);
                    i--;  // back up so we catch consecutive merges
                }
            }
        }
        // lookup ids; unknown symbols fall back to unk (use 0 if available)
        List<Integer> out = new ArrayList<>();
        for (String sym : symbols) {
            Integer id = vocab.get(sym);
            if (id != null) out.add(id);
            // else: drop or use 0 — for now drop
        }
        return out;
    }

    /** Convert each UTF-8 byte to its GPT-2 byte-level string. */
    private static int[] bytesToBase(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int[] ids = new int[b.length];
        for (int i = 0; i < b.length; i++) ids[i] = b[i] & 0xFF;
        return ids;
    }

    /** GPT-2 byte-level char map (256 → printable unicode). */
    private static final String[] BYTE_CHARS = new String[256];
    static {
        // Standard GPT-2 byte-level encoding: printable bytes map to themselves;
        // non-printable bytes (including space=0x20) map to chars in U+0100+.
        // Reference: https://huggingface.co/gpt2/raw/main/vocab.json
        // bs = ord("!")..ord("~") + ord("¡")..ord("¬") + ord("®")..ord("ÿ")
        // Non-printable bytes get assigned U+0100..U+0143 in order.
        for (int i = 0; i < 256; i++) BYTE_CHARS[i] = String.valueOf((char) i);
        // The printable set used in GPT-2 vocab (bytes that map to themselves):
        java.util.Set<Integer> printable = new java.util.HashSet<>();
        for (int c = '!'; c <= '~'; c++) printable.add(c);
        for (int c = 0xA1; c <= 0xAC; c++) printable.add(c);
        for (int c = 0xAE; c <= 0xFF; c++) printable.add(c);
        // Non-printable bytes (including 0x20 space) get assigned codepoints in
        // U+0100..U+0143, skipping the ones already used.
        int codepoint = 0x100;
        for (int b = 0; b < 256; b++) {
            if (printable.contains(b)) {
                BYTE_CHARS[b] = String.valueOf((char) b);
            } else {
                BYTE_CHARS[b] = String.valueOf((char) codepoint);
                codepoint++;
            }
        }
    }

    private static String byteLevelChar(int b) {
        return BYTE_CHARS[b];
    }

    /** Inverse of byteLevelChar: convert display chars back to original bytes. */
    private static String unbyteDecode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp < 256) {
                sb.append((char) cp);
            } else {
                // Find the byte that this codepoint maps from.
                int mapped = -1;
                for (int b = 0; b < 256; b++) {
                    if (BYTE_CHARS[b].codePointAt(0) == cp) {
                        mapped = b;
                        break;
                    }
                }
                if (mapped >= 0) {
                    sb.append((char) mapped);
                } else {
                    sb.appendCodePoint(cp);
                }
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /** Convert text to a fixed-width bit array via BPE → ids → bits.
     *  Each token id becomes (id % width) as the bit index to set. */
    public boolean[] textToBits(String text, int width) {
        boolean[] bits = new boolean[width];
        int[] ids = encode(text);
        for (int id : ids) {
            bits[Math.floorMod(id, width)] = true;
        }
        return bits;
    }

    /** Convert ids to bits directly (for testing). */
    public boolean[] idsToBits(int[] ids, int width) {
        boolean[] bits = new boolean[width];
        for (int id : ids) {
            bits[Math.floorMod(id, width)] = true;
        }
        return bits;
    }

    public int vocabSize() { return vocab.size(); }

    /** Public accessor — exposes the reverse-vocab table (id → text). */
    public Map<Integer, String> reverseVocabForPublic() {
        return reverseVocab;
    }

    /** Look up a single token id. */
    public String reverseVocabFor(int id) {
        return reverseVocab.get(id);
    }
}