package io.matrix.cli;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.stream.Stream;

/**
 * W459 — Conversation Share CLI.
 * 
 * Creates a shareable URL with the conversation content encoded.
 * The URL can be opened in a browser to view the conversation.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationShare <session-id>
 *   
 * The URL contains base64-encoded NDJSON content as a fragment.
 * The included share.html viewer can decode and display it.
 */
public final class ConversationShare {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationShare <session-id>");
            System.exit(1);
        }
        
        String sessionId = args[0];
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        // Read session content
        StringBuilder content = new StringBuilder();
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (!line.isBlank()) {
                    content.append(line).append("\n");
                }
            }
        }
        
        // Base64-encode
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
            content.toString().getBytes(StandardCharsets.UTF_8));
        
        // Get file:// URL
        Path viewer = Paths.get("matrix-core/src/main/resources/web/share.html");
        String baseUrl;
        if (Files.exists(viewer)) {
            baseUrl = viewer.toUri().toString();
        } else {
            baseUrl = "file:///path/to/share.html";
        }
        
        String shareUrl = baseUrl + "#data=" + encoded;
        
        System.out.println("Shareable URL for session: " + sessionId);
        System.out.println("Size: " + shareUrl.length() + " chars");
        System.out.println("Content size: " + content.length() + " bytes");
        System.out.println();
        System.out.println(shareUrl);
        System.out.println();
        
        // Also save the URL to a file
        Path urlFile = Paths.get("data/exports", sessionId + ".share.url");
        Files.createDirectories(urlFile.getParent());
        Files.writeString(urlFile, shareUrl + "\n");
        System.out.println("URL saved to: " + urlFile);
    }
}
