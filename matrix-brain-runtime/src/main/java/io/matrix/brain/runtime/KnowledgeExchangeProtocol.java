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
        public static Fact fromJsonLine(String line) {
            String id = extract(line, "id");
            String input = extract(line, "input");
            String answer = extract(line, "answer");
            double conf = 0.0;
            try {
                int i = line.indexOf("\"confidence\":");
                if (i >= 0) {
                    int s = i + 14;
                    int e = s;
                    while (e < line.length() && line.charAt(e) != ',' && line.charAt(e) != '}') e++;
                    conf = Double.parseDouble(line.substring(s, e).trim());
                }
            } catch (NumberFormatException ignore) {}
            long ts = 0L;
            try {
                int i = line.indexOf("\"ts\":");
                if (i >= 0) ts = Long.parseLong(line.substring(i + 6).replaceAll("[^0-9].*", ""));
            } catch (NumberFormatException ignore) {}
            return new Fact(id, input, answer, conf, ts);
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

    public static int mergeInto(PersistentHdcStore localStore, Batch batch) {
        if (batch == null || batch.facts == null) return 0;
        int added = 0;
        for (Fact f : batch.facts) {
            String id = "fed-" + batch.sourceNode + "-" + f.id;
            if (!localStore.snapshot().containsKey(id)) {
                localStore.teach(id, f.input + " => " + f.answer);
                added++;
            }
        }
        return added;
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
