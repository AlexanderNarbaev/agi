package io.matrix.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * W428 — Conversation Extract CLI.
 * 
 * Extracts specific turn types from a session:
 * - --user:    Only user messages
 * - --assistant: Only assistant messages
 * - --turns:   All turns (no META)
 * 
 * Usage:
 *   java io.matrix.cli.ConversationExtract <session-id> <--user|--assistant|--turns> [output-file]
 */
public final class ConversationExtract {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: ConversationExtract <session-id> <--user|--assistant|--turns> [output-file]");
            System.exit(1);
        }
        
        String sessionId = args[0];
        String mode = args[1];
        
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        BufferedWriter writer = null;
        if (args.length > 2) {
            Path outFile = Paths.get(args[2]);
            writer = Files.newBufferedWriter(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            writer = new BufferedWriter(new java.io.OutputStreamWriter(System.out));
        }
        
        int count = 0;
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank() || line.startsWith("#")) continue;
                if (!line.startsWith("{")) continue;
                
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role == null || content == null) continue;
                
                boolean include = false;
                if ("--user".equals(mode) && "user".equals(role)) include = true;
                else if ("--assistant".equals(mode) && "assistant".equals(role)) include = true;
                else if ("--turns".equals(mode)) include = true;
                
                if (include) {
                    writer.write(role.toUpperCase() + ": " + content);
                    writer.newLine();
                    count++;
                }
            }
        } finally {
            if (args.length > 2 && writer != null) {
                writer.close();
            }
        }
        
        if (args.length > 2) {
            System.out.println("Extracted " + count + " turns to " + args[2]);
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
