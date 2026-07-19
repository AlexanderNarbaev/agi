package io.matrix.imports;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight HuggingFace Hub weight source.
 *
 * <p>Implements {@link WeightSource} using only {@link java.net.http.HttpClient}
 * — no external dependencies, suitable for native-image compilation.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Resolves {@code owner/name} into a metadata JSON via the
 *       {@code https://huggingface.co/api/models/{owner}/{name}} endpoint.</li>
 *   <li>Filters the {@code siblings} list down to weight files
 *       ({@code .safetensors} / {@code .bin} / {@code .gguf}).</li>
 *   <li>Streams each weight file to {@code target/<owner>--<name>/}.</li>
 *   <li>Aggregates sha256 hashes into a single fingerprint.</li>
 * </ul>
 *
 * <p>Implementation is deliberately network-failure-tolerant: a 404 model page
 * yields {@link WeightSource.ProbeResult} with {@code availableOnSource=false}
 * instead of throwing, so callers can decide whether to skip.
 */
public final class HuggingFaceHubSource implements WeightSource {

    private static final String HF_BASE = "https://huggingface.co";
    private static final String HF_API = HF_BASE + "/api/models/";
    private static final Pattern PATTERN_WEIGHT_FILE =
            Pattern.compile(".+\\.(safetensors|gguf|bin)$");

    private final HttpClient http;

    public HuggingFaceHubSource() {
        this(Duration.ofSeconds(30));
    }

    public HuggingFaceHubSource(Duration timeout) {
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public String sourceId() { return "huggingface"; }

    @Override
    public ProbeResult probe(String modelId) {
        if (modelId == null || !modelId.contains("/")) {
            return new ProbeResult(modelId, false, 0L);
        }
        try {
            String url = HF_API + modelId;
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                return new ProbeResult(modelId, false, 0L);
            }
            if (resp.statusCode() >= 400) {
                throw new WeightSourceException("Probe failed: HTTP " + resp.statusCode());
            }
            long bytes = estimateWeightBytes(resp.body());
            return new ProbeResult(modelId, true, bytes);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new WeightSourceException("Probe failed for " + modelId, e);
        }
    }

    @Override
    public DownloadResult download(String modelId, Path target) {
        if (modelId == null || !modelId.contains("/")) {
            throw new WeightSourceException("Bad model id: " + modelId);
        }
        String owner = modelId.substring(0, modelId.indexOf('/'));
        String name = modelId.substring(modelId.indexOf('/') + 1);
        Path cacheDir = target.resolve(safeDirName(modelId));
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new WeightSourceException("Cannot create cache dir: " + cacheDir, e);
        }

        String apiUrl = HF_API + modelId;
        String respBody;
        try {
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create(apiUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 400) {
                throw new WeightSourceException("Model page " + modelId + " → HTTP " + r.statusCode());
            }
            respBody = r.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new WeightSourceException("Download metadata failed", e);
        }

        List<Map<String, Object>> siblings = extractSiblings(respBody);
        List<Path> downloaded = new ArrayList<>();
        long total = 0;
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) {
            throw new WeightSourceException("SHA-256 unavailable", e);
        }
        try {
            for (Map<String, Object> s : siblings) {
                String rfilename = (String) s.get("rfilename");
                if (rfilename == null || !PATTERN_WEIGHT_FILE.matcher(rfilename).matches()) continue;
                String dl = HF_BASE + "/" + modelId + "/resolve/main/" + rfilename;
                Path local = cacheDir.resolve(safeFileName(rfilename));
                downloadOne(dl, local);
                downloaded.add(local);
                total += Files.size(local);
                byte[] fileDigest = sha256(local);
                digest.update(fileDigest);
                // also include the per-file sha in the aggregate to detect partial state
                digest.update((rfilename + "=" + HexFormat.of().formatHex(fileDigest) + ";")
                        .getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new WeightSourceException("Stream failure during download of " + modelId, e);
        }

        if (downloaded.isEmpty()) {
            throw new WeightSourceException("No weight files (.safetensors/.gguf/.bin) at " + modelId);
        }

        return new DownloadResult(modelId, cacheDir, downloaded, total,
                HexFormat.of().formatHex(digest.digest()));
    }

    private void downloadOne(String url, Path destination) throws IOException {
        try {
            HttpResponse<Path> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(destination));
            if (resp.statusCode() >= 400) {
                throw new IOException("HTTP " + resp.statusCode() + " for " + url);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, ie);
        }
    }

    // ── Lightweight JSON scanning (no library deps) ──

    /** Extracts the {@code siblings:[...]} array from the metadata. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSiblings(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        int idx = json.indexOf("\"siblings\"");
        if (idx < 0) return result;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return result;
        int lb = json.indexOf('[', colon);
        if (lb < 0) return result;
        int depth = 0;
        boolean inStr = false;
        int rb = -1;
        for (int i = lb; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (c == '"' && json.charAt(i - 1) != '\\') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) { rb = i; break; }
            }
        }
        if (rb < 0) return result;
        String arr = json.substring(lb + 1, rb);
        // Each sibling is an object { "rfilename": "...", ... }
        int i = 0;
        while (i < arr.length()) {
            int obStart = arr.indexOf('{', i);
            if (obStart < 0) break;
            int obEnd = skipBalanced(arr, obStart);
            if (obEnd < 0) break;
            String block = arr.substring(obStart, obEnd + 1);
            Map<String, Object> entry = new LinkedHashMap<>();
            Matcher m = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(block);
            while (m.find()) {
                entry.put(m.group(1), m.group(2));
            }
            result.add(entry);
            i = obEnd + 1;
        }
        return result;
    }

    private int skipBalanced(String s, int from) {
        int depth = 0;
        boolean inStr = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '"' && s.charAt(i - 1) != '\\') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private long estimateWeightBytes(String json) {
        long sum = 0;
        Matcher m = Pattern.compile("\"size\"\\s*:\\s*(\\d+)").matcher(json);
        while (m.find()) {
            try { sum += Long.parseLong(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return sum;
    }

    private byte[] sha256(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Files.readAllBytes(file));
            return md.digest();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new WeightSourceException("sha256 failed for " + file, e);
        }
    }

    private static String safeDirName(String modelId) {
        return modelId.replace('/', '_').replace('\\', '_').replace(':', '_');
    }

    private static String safeFileName(String filename) {
        return Optional.ofNullable(filename)
                .map(s -> s.replaceAll("[^A-Za-z0-9._\\-]", "_"))
                .orElse("unknown");
    }
}
