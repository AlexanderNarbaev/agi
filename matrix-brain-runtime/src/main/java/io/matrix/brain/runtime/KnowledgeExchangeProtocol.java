package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TRUE-W14 — Knowledge Exchange Protocol.
 */
public final class KnowledgeExchangeProtocol {

    public record Fact(String id, String input, String answer, double confidence, long timestamp) {
        public String toJsonLine() {
            return "{\"id\":\"" + esc(id)
                + "\",\"input\":\"" + esc(input)
                + "\",\"answer\":\"" + esc(answer)
                + "\",\"confidence\":" + String.format("%.3f", confidence)
                + ",\"ts\":" + timestamp + "}";
        }
        /**
         * Parse one federated JSON line into a {@link Fact}.
         *
         * <p><b>RECON-W33.2 — shape validation added.</b> Lenient field extraction over
         * untrusted input is correct and stays: a missing or malformed {@code confidence} or
         * {@code ts} still degrades to 0.0 / 0, because "not stated" is the truthful answer
         * and the fact may still carry a real subject and a real answer.</p>
         *
         * <p>What was missing was a check that the line is a FACT at all. Before this, a line
         * truncated mid-write produced {@code Fact("", "", "", 0.0, 0)}: a well-formed Java
         * object carrying no content, indistinguishable downstream from a genuine fact.
         * {@code mergeIntoWithReport} merged it and the store came to hold a fact rendering as
         * {@code fed-<node>- => } — blank subject mapped to blank answer, learned from a
         * message that never arrived. That is the fabrication shape, entering through the
         * federation door. It does not crash, so nothing alerts; it does not assert, so nothing
         * goes red. A knowledge store accumulates invented facts quietly.</p>
         *
         * <p>So: {@code id}, {@code input} and {@code answer} are required and must be
         * non-blank. Numeric fields remain optional.</p>
         *
         * @param line one JSON object as a line of text; may be null
         * @return the parsed fact, never one with a blank required field
         * @throws IllegalArgumentException if the line is absent, blank, or missing any of
         *     {@code id}, {@code input} or {@code answer}; the message names the offending field
         */
        public static Fact fromJsonLine(String line) {
            if (line == null || line.isBlank()) {
                throw new IllegalArgumentException(
                        "rejected federated line: line is null or blank, so it carries no fact");
            }
            String id = extract(line, "id");
            String input = extract(line, "input");
            String answer = extract(line, "answer");
            // RECON-W33.2. Each required field is checked by name, so the rejection says which
            // one is missing instead of leaving an operator to diff the JSON by eye. Blanks are
            // rejected, not trimmed into existence: "" and "   " produce the same empty lesson.
            requirePresent("id", id, line);
            requirePresent("input", input, line);
            requirePresent("answer", answer, line);
            double conf = 0.0;
            try {
                int i = line.indexOf("\"confidence\":");
                if (i >= 0) {
                    int s = i + 14;
                    int e = s;
                    while (e < line.length() && line.charAt(e) != ',' && line.charAt(e) != '}') e++;
                    conf = Double.parseDouble(line.substring(s, e).trim());
                }
            } catch (NumberFormatException e) {
                    // RECON-W32.33. LEGITIMATE to swallow, per the W32.26 audit: this is a
                    // lenient field extractor over UNTRUSTED federated input, and its
                    // defaults (conf = 0.0, ts = 0) are inert — no consumer reads them as
                    // a real confidence or a real timestamp, and PromotionGate decides what
                    // is actually taught. A malformed confidence becoming 0.0 is the
                    // correct answer for "not stated", not a lost lesson.
                    //
                    // RECON-W33.2: the fabrication-shaped defect in this class was NOT here
                    // but in the absence of shape validation above, and it is now fixed. This
                    // catch is deliberately left lenient.
                }
            long ts = 0L;
            try {
                int i = line.indexOf("\"ts\":");
                if (i >= 0) ts = Long.parseLong(line.substring(i + 5).replaceAll("[^0-9].*", ""));
            } catch (NumberFormatException e) {
                    // RECON-W32.33, retained unchanged. Rationale identical to the confidence
                    // catch above: an unstated timestamp is 0, not a reason to discard a fact.
                    //
                    // RECON-W33.2: the fabrication-shaped defect was the missing shape check,
                    // now fixed above. This catch stays lenient.
                }
            return new Fact(id, input, answer, conf, ts);
        }

        /**
         * Reject a line whose required field is absent or blank.
         *
         * <p>Unit: a field name, its extracted value, and the originating line for context.</p>
         *
         * @param field the JSON key that must be present, e.g. {@code id}
         * @param value the extracted value, possibly null or blank
         * @param line the original line, quoted and truncated in the message
         * @throws IllegalArgumentException when {@code value} is null, empty or whitespace
         */
        private static void requirePresent(String field, String value, String line) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "rejected federated line: required field '" + field
                            + "' is absent or blank, so this line is not a teachable fact. "
                            + "Line was: " + abbreviate(line));
            }
        }

        /**
         * Shorten a line for inclusion in an error message.
         *
         * <p>Unit: characters. Bounded so an attacker-supplied megabyte of "line" cannot turn
         * a rejection message into a denial-of-service vector.</p>
         *
         * @param line the line to quote
         * @return the line, truncated to 120 characters with an ellipsis marker
         */
        private static String abbreviate(String line) {
            int maxChars = 120;
            if (line == null || line.length() <= maxChars) {
                return String.valueOf(line);
            }
            return line.substring(0, maxChars) + "...";
        }
    }

    public record Batch(String sourceNode, List<Fact> facts) {
        public String toJsonArray() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"source\":\"").append(esc(sourceNode)).append("\",\"facts\":[");
            boolean first = true;
            for (Fact f : facts) {
                if (!first) sb.append(',');
                first = false;
                sb.append(f.toJsonLine());
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    /**
     * Merge a federated batch into the local store.
     *
     * <p>RECON-W31.1 / EPI-1 + EPI-4. A remote node's batch is untrusted input, so
     * every fact passes the same {@link PromotionGate} as local knowledge — a
     * federated node that taught itself the eval battery cannot push that poison
     * into a peer. This closes a real hole: federation ingest is an ordinary
     * {@code teach} caller, and before this wave a remote node could seed a peer with
     * "Tell me a racist joke" as a canonical fact.
     *
     * <p>A refused fact is skipped and counted, NOT thrown: one poisoned peer must not
     * abort a whole batch, and silently swallowing it would be a shadow failure. The
     * counts are returned in the {@link MergeReport} so the caller can surface them.
     *
     * @return accepted, rejected, and total counts for the batch
     */
    public static MergeReport mergeIntoWithReport(PersistentHdcStore localStore, Batch batch) {
        if (batch == null || batch.facts == null) {
            return new MergeReport(0, 0, 0);
        }
        int added = 0;
        int rejected = 0;
        for (Fact f : batch.facts) {
            String id = "fed-" + batch.sourceNode + "-" + f.id;
            if (localStore.snapshot().containsKey(id)) continue;
            try {
                localStore.teach(id, f.input + " => " + f.answer);
                added++;
            } catch (PersistentHdcStore.PromotionRejectedException ex) {
                rejected++;
            }
        }
        return new MergeReport(added, rejected, batch.facts.size());
    }

    /**
     * @param added    facts promoted into the local store
     * @param rejected facts refused by the promotion gate (probe, refusal, fiction, empty)
     * @param total    facts offered in the batch
     */
    public record MergeReport(int added, int rejected, int total) {
        /** Fraction of the offered batch that became knowledge, in [0,1]. */
        public double acceptanceRate() {
            return total == 0 ? 0.0 : (double) added / (double) total;
        }
    }

    /** @return number of facts merged; refused facts are skipped, not thrown */
    public static int mergeInto(PersistentHdcStore localStore, Batch batch) {
        return mergeIntoWithReport(localStore, batch).added();
    }

    public static Batch snapshotToBatch(PersistentHdcStore store, String sourceNode) {
        List<Fact> facts = new ArrayList<>();
        Map<String, String> snap = store.snapshot();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> e : snap.entrySet()) {
            String content = e.getValue();
            // If stored as "input => answer", split. Otherwise treat the whole
            // content as the answer (input is metadata).
            int sep = content.indexOf(" => ");
            String input, answer;
            if (sep > 0) {
                input = content.substring(0, sep);
                answer = content.substring(sep + 4);
            } else {
                input = content;  // legacy: whole content as input
                answer = content;
            }
            facts.add(new Fact(e.getKey(), input, answer, 0.95, now));
        }
        return new Batch(sourceNode, facts);
    }

    public static int pushToPeer(String peerBaseUrl, String endpoint,
                                 String token, Batch batch) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                new java.net.URL(peerBaseUrl + endpoint).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            c.setDoOutput(true);
            c.getOutputStream().write(batch.toJsonArray().getBytes());
            int code = c.getResponseCode();
            return code >= 200 && code < 300 ? batch.facts.size() : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String extract(String line, String key) {
        String marker = "\"" + key + "\":\"";
        int i = line.indexOf(marker);
        if (i < 0) return "";
        int s = i + marker.length();
        int e = s;
        while (e < line.length()) {
            char c = line.charAt(e);
            if (c == '\\' && e + 1 < line.length()) { e += 2; continue; }
            if (c == '"') break;
            e++;
        }
        return line.substring(s, e).replace("\\n", "\n").replace("\\\"", "\"");
    }
}
