package io.matrix.cli;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * W457 — Conversation HTML Export CLI.
 * 
 * Exports NDJSON conversation to a styled HTML page for sharing/printing.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationHtmlExport <session-id> [output-file]
 *   Default: data/exports/<session-id>.html
 */
public final class ConversationHtmlExport {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationHtmlExport <session-id> [output-file]");
            System.exit(1);
        }
        
        String sessionId = args[0];
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        Path outFile = args.length > 1 ? Paths.get(args[1]) :
            Paths.get("data/exports", sessionId + ".html");
        Files.createDirectories(outFile.getParent());
        
        String name = null;
        int userCount = 0;
        int assistantCount = 0;
        long earliestMs = Long.MAX_VALUE;
        long latestMs = 0L;
        
        try (OutputStreamWriter w = new OutputStreamWriter(
                Files.newOutputStream(outFile), StandardCharsets.UTF_8)) {
            w.write("<!DOCTYPE html>\n");
            w.write("<html lang=\"en\">\n");
            w.write("<head>\n");
            w.write("<meta charset=\"UTF-8\">\n");
            w.write("<title>MATRIX: " + escape(sessionId) + "</title>\n");
            w.write("<style>\n");
            w.write("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',monospace;background:#0a0a0a;color:#e0e0e0;max-width:800px;margin:0 auto;padding:20px;line-height:1.5;}\n");
            w.write("h1,h2{color:#00ffff;border-bottom:1px solid #003322;padding-bottom:10px;}\n");
            w.write(".meta{color:#888;font-size:0.9em;margin:10px 0;}\n");
            w.write(".turn{margin:15px 0;padding:10px;border-left:3px solid;}\n");
            w.write(".user{background:#0a1a0a;border-left-color:#00aaff;}\n");
            w.write(".assistant{background:#001a0a;border-left-color:#00ff88;}\n");
            w.write(".role{font-weight:bold;color:#ffff00;margin-bottom:5px;}\n");
            w.write(".content{white-space:pre-wrap;}\n");
            w.write(".bookmark{background:#332200;border-left:3px solid #ffaa00;padding:5px;margin:5px 0;}\n");
            w.write("</style>\n</head>\n<body>\n");
            w.write("<h1>MATRIX Conversation</h1>\n");
            w.write("<p>Session: <code>" + escape(sessionId) + "</code></p>\n");
            
            try (Stream<String> lines = Files.lines(sessionFile)) {
                for (String line : (Iterable<String>) lines::iterator) {
                    if (line.isBlank()) continue;
                    
                    if (line.startsWith("# META name: ")) {
                        name = line.substring("# META name: ".length()).trim();
                        continue;
                    }
                    if (line.startsWith("# META bookmark turn=")) {
                        // Bookmark comment
                        String bookmark = line.substring("# META ".length());
                        w.write("<div class=\"bookmark\">📌 " + escape(bookmark) + "</div>\n");
                        continue;
                    }
                    if (line.startsWith("#")) continue;
                    
                    String role = extractField(line, "role");
                    String content = extractField(line, "content");
                    String ts = extractField(line, "timestamp");
                    
                    if (role == null || content == null) continue;
                    
                    if ("user".equals(role)) userCount++;
                    else if ("assistant".equals(role)) assistantCount++;
                    
                    if (ts != null) {
                        try {
                            long ms = Instant.parse(ts).toEpochMilli();
                            earliestMs = Math.min(earliestMs, ms);
                            latestMs = Math.max(latestMs, ms);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    
                    String cssClass = role;
                    w.write("<div class=\"turn " + cssClass + "\">\n");
                    w.write("<div class=\"role\">" + role.toUpperCase() + "</div>\n");
                    if (ts != null) {
                        String formatted = formatTimestamp(ts);
                        w.write("<div class=\"meta\">" + formatted + "</div>\n");
                    }
                    w.write("<div class=\"content\">" + escape(content) + "</div>\n");
                    w.write("</div>\n");
                }
            }
            
            // Footer with metadata
            w.write("<h2>Summary</h2>\n");
            w.write("<ul>\n");
            w.write("<li>Total turns: " + (userCount + assistantCount) + "</li>\n");
            w.write("<li>User turns: " + userCount + "</li>\n");
            w.write("<li>Assistant turns: " + assistantCount + "</li>\n");
            if (name != null) {
                w.write("<li>Name: \"" + escape(name) + "\"</li>\n");
            }
            if (earliestMs != Long.MAX_VALUE) {
                String first = Instant.ofEpochMilli(earliestMs).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_DATE_TIME);
                String last = Instant.ofEpochMilli(latestMs).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_DATE_TIME);
                w.write("<li>First: " + first + "</li>\n");
                w.write("<li>Last: " + last + "</li>\n");
            }
            w.write("</ul>\n");
            w.write("<p><em>Generated by MATRIX Conversation CLI (W457)</em></p>\n");
            w.write("</body>\n</html>\n");
        }
        
        System.out.println("Exported to: " + outFile);
        System.out.println("User turns: " + userCount + ", Assistant turns: " + assistantCount);
    }
    
    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
    
    private static String formatTimestamp(String iso) {
        try {
            Instant instant = Instant.parse(iso);
            return instant.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return iso;
        }
    }
    
    private static String extractField(String json, String fieldName) {
        String needle = "\"" + fieldName + "\":\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        boolean escaped = false;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') sb.append(' ');
                else if (c == 'r') sb.append(' ');
                else if (c == 't') sb.append(' ');
                else if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
