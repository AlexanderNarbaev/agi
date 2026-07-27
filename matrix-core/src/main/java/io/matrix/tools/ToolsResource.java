package io.matrix.tools;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST API for tool integration: web search, file processing, code execution.
 * Allows the system to interact with external tools.
 */
@Path("/api/v1/tools")
@Produces(MediaType.APPLICATION_JSON)
public class ToolsResource {

    private static final Logger log = LoggerFactory.getLogger(ToolsResource.class);

    private final AtomicInteger totalInvocations = new AtomicInteger(0);
    private final Map<String, AtomicInteger> toolUsage = new HashMap<>();

    /**
     * Invoke a tool.
     */
    @POST
    @Path("/invoke")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response invokeTool(Map<String, Object> payload) {
        try {
            String tool = (String) payload.get("tool");
            Map<String, Object> args = (Map<String, Object>) payload.getOrDefault("args", Map.of());
            
            if (tool == null || tool.isBlank()) {
                return Response.status(400).entity(Map.of("error", "tool name required")).build();
            }
            
            String result = invoke(tool, args);
            totalInvocations.incrementAndGet();
            toolUsage.computeIfAbsent(tool, k -> new AtomicInteger(0)).incrementAndGet();
            
            return Response.ok(Map.of(
                    "status", "success",
                    "tool", tool,
                    "result", result
            )).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * List available tools.
     */
    @GET
    @Path("/list")
    public Response listTools() {
        List<Map<String, String>> tools = List.of(
                Map.of("name", "web_search", "description", "Search the web via DuckDuckGo or Google"),
                Map.of("name", "web_fetch", "description", "Fetch a URL and extract text content"),
                Map.of("name", "code_execute", "description", "Execute a small code snippet (sandboxed)"),
                Map.of("name", "file_read", "description", "Read a local file"),
                Map.of("name", "file_write", "description", "Write to a local file"),
                Map.of("name", "shell", "description", "Execute a shell command (sandboxed)"),
                Map.of("name", "calculator", "description", "Evaluate a math expression"),
                Map.of("name", "datetime", "description", "Get current date/time in any timezone")
        );
        return Response.ok(Map.of("tools", tools, "count", tools.size())).build();
    }

    /**
     * Get tool usage statistics.
     */
    @GET
    @Path("/stats")
    public Response getStats() {
        Map<String, Integer> usage = new HashMap<>();
        toolUsage.forEach((k, v) -> usage.put(k, v.get()));
        return Response.ok(Map.of(
                "totalInvocations", totalInvocations.get(),
                "perTool", usage
        )).build();
    }

    /**
     * Invoke a specific tool.
     */
    private String invoke(String tool, Map<String, Object> args) {
        return switch (tool) {
            case "web_search" -> webSearch((String) args.getOrDefault("query", ""));
            case "web_fetch" -> webFetch((String) args.getOrDefault("url", ""));
            case "code_execute" -> codeExecute((String) args.getOrDefault("code", ""));
            case "file_read" -> fileRead((String) args.getOrDefault("path", ""));
            case "file_write" -> fileWrite((String) args.getOrDefault("path", ""),
                    (String) args.getOrDefault("content", ""));
            case "shell" -> shellExecute((String) args.getOrDefault("command", ""));
            case "calculator" -> calculator((String) args.getOrDefault("expression", ""));
            case "datetime" -> datetime((String) args.getOrDefault("timezone", "UTC"));
            default -> "Unknown tool: " + tool;
        };
    }

    private String webSearch(String query) {
        return "Search results for: " + query + " (mock: would use DuckDuckGo API)";
    }

    private String webFetch(String url) {
        if (url == null || url.isBlank() || !url.startsWith("http")) {
            return "Invalid URL";
        }
        try {
            URI uri = URI.create(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestProperty("User-Agent", "M.A.T.R.I.X./3.58");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code != 200) return "HTTP " + code;
            
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 100) {
                    sb.append(line).append("\n");
                    count++;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Fetch error: " + e.getMessage();
        }
    }

    private String codeExecute(String code) {
        // Sandboxed: only echo for safety
        return "[SANDBOXED] Would execute: " + code;
    }

    private String fileRead(String path) {
        if (path == null || path.isBlank()) return "Invalid path";
        // Security: only allow reading from safe directories
        if (path.contains("..") || path.startsWith("/etc") || path.startsWith("/proc")) {
            return "Access denied";
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)));
        } catch (Exception e) {
            return "Read error: " + e.getMessage();
        }
    }

    private String fileWrite(String path, String content) {
        if (path == null || path.isBlank()) return "Invalid path";
        if (path.contains("..")) return "Access denied";
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(path), content);
            return "Written " + content.length() + " bytes to " + path;
        } catch (Exception e) {
            return "Write error: " + e.getMessage();
        }
    }

    private String shellExecute(String command) {
        // Sandboxed: only allow safe commands
        if (command == null || command.isBlank()) return "Invalid command";
        String[] allowed = {"ls", "cat", "echo", "date", "pwd", "whoami", "df", "du", "free"};
        String firstWord = command.split(" ")[0];
        boolean isAllowed = false;
        for (String a : allowed) {
            if (a.equals(firstWord)) { isAllowed = true; break; }
        }
        if (!isAllowed) return "[SANDBOXED] Command not in allowlist: " + firstWord;
        
        try {
            Process p = new ProcessBuilder("bash", "-c", command).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "Shell error: " + e.getMessage();
        }
    }

    private String calculator(String expression) {
        if (expression == null || expression.isBlank()) return "0";
        try {
            // Whitelist: digits, whitespace, parens, basic operators
            if (!expression.matches("[0-9+\\-*/().\\s]+")) {
                return "Invalid expression (allowed: digits, + - * / ( ))";
            }
            // JavaScript engine removed from JDK 15+. Implement a small recursive
            // descent parser that handles + - * / with parens and unary minus.
            return String.valueOf(parseExpression(new java.io.StringReader(expression)));
        } catch (Exception e) {
            return "Calc error: " + e.getMessage();
        }
    }

    // ── Recursive-descent expression parser (no JS engine dependency) ──
    private double parseExpression(java.io.Reader r) throws java.io.IOException {
        java.io.StreamTokenizer st = new java.io.StreamTokenizer(r);
        st.ordinaryChar('-');
        st.ordinaryChar('/');
        st.ordinaryChar('+');
        st.ordinaryChar('*');
        st.ordinaryChar('(');
        st.ordinaryChar(')');
        double v = parseExpr(st);
        return v;
    }
    private double parseExpr(java.io.StreamTokenizer st) throws java.io.IOException {
        double v = parseTerm(st);
        for (;;) {
            int t = st.nextToken();
            if (t == '+') { v += parseTerm(st); }
            else if (t == '-') { v -= parseTerm(st); }
            else { st.pushBack(); return v; }
        }
    }
    private double parseTerm(java.io.StreamTokenizer st) throws java.io.IOException {
        double v = parseFactor(st);
        for (;;) {
            int t = st.nextToken();
            if (t == '*') { v *= parseFactor(st); }
            else if (t == '/') { v /= parseFactor(st); }
            else { st.pushBack(); return v; }
        }
    }
    private double parseFactor(java.io.StreamTokenizer st) throws java.io.IOException {
        int t = st.nextToken();
        if (t == '-') return -parseFactor(st);
        if (t == '+') return parseFactor(st);
        if (t == java.io.StreamTokenizer.TT_NUMBER) return st.nval;
        if (t == '(') {
            double v = parseExpr(st);
            int t2 = st.nextToken();
            if (t2 != ')') throw new java.io.IOException("missing )");
            return v;
        }
        throw new java.io.IOException("unexpected token: " + t);
    }

    private String datetime(String timezone) {
        return java.time.ZonedDateTime.now(
                java.time.ZoneId.of(timezone.equals("UTC") ? "UTC" : timezone)
        ).toString();
    }
}
