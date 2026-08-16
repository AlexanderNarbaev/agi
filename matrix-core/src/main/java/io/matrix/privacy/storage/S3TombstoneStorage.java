package io.matrix.privacy.storage;

import io.matrix.privacy.Tombstone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * S3-compatible tombstone storage using the S3 REST API directly.
 *
 * <p>Compatible with AWS S3, MinIO, and any S3-compatible local cluster.
 * No AWS SDK dependency — uses {@link java.net.http.HttpClient} with the
 * standard S3 SigV4 request-signing flow.
 *
 * <p>Each tombstone is stored as a separate object under
 * {@code tombstones/{resourceType}/{resourceId}/{id}.json}. The id-based
 * filename makes tombstones immutable (no overwrite) and version-aware
 * when bucket versioning is enabled.
 *
 * <p>Configuration via constructor args:
 * <pre>
 *   new S3TombstoneStorage(
 *       "http://minio:9000",   // endpoint
 *       "us-east-1",            // region
 *       "tombstones",           // bucket
 *       "minioadmin",           // access key
 *       "minioadmin"            // secret key
 *   );
 * </pre>
 */
public final class S3TombstoneStorage implements TombstoneStorage {

    private static final Logger log = LoggerFactory.getLogger(S3TombstoneStorage.class);

    private final String endpoint;
    private final String region;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final HttpClient http;
    private final ExecutorService executor;
    private final String prefix;

    public S3TombstoneStorage(String endpoint, String region, String bucket,
                                String accessKey, String secretKey) {
        this(endpoint, region, bucket, accessKey, secretKey, "tombstones/");
    }

    public S3TombstoneStorage(String endpoint, String region, String bucket,
                                String accessKey, String secretKey, String prefix) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.region = region;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.prefix = prefix.endsWith("/") ? prefix : prefix + "/";
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "s3-tombstone");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public CompletableFuture<Void> append(Tombstone tombstone) {
        return CompletableFuture.runAsync(() -> {
            try {
                String key = objectKey(tombstone);
                String body = JsonSerde.toJson(tombstone);
                HttpRequest req = signedRequest("PUT", key, body, "application/json",
                        "x-amz-meta-subject:" + tombstone.subjectId(),
                        "x-amz-meta-reason:" + tombstone.reason());
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) {
                    throw new StorageUnavailableException("S3 PUT failed: " + resp.statusCode()
                            + " body=" + resp.body());
                }
            } catch (Exception e) {
                throw new StorageUnavailableException("S3 putObject failed", e);
            }
        }, executor);
    }

    @Override
    public Optional<Tombstone> load(String resourceType, String resourceId) {
        try {
            String listPrefix = this.prefix + resourceType + "/" + resourceId + "/";
            HttpRequest req = signedRequest("GET", "?list-type=2&prefix=" + URLEncoder.encode(listPrefix, StandardCharsets.UTF_8),
                    null, null);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) return Optional.empty();
            if (resp.statusCode() / 100 != 2) {
                throw new StorageUnavailableException("S3 LIST failed: " + resp.statusCode());
            }
            // Minimal XML parser — find the largest <Key>...</Key> element.
            String xml = resp.body();
            String bestKey = null;
            String bestDate = null;
            int pos = 0;
            while (true) {
                int s = xml.indexOf("<Key>", pos);
                if (s < 0) break;
                int e = xml.indexOf("</Key>", s);
                if (e < 0) break;
                String key = xml.substring(s + 5, e);
                // Try to find the LastModified after this key.
                int lm = xml.indexOf("<LastModified>", e);
                String date = null;
                if (lm > 0) {
                    int lme = xml.indexOf("</LastModified>", lm);
                    if (lme > 0) date = xml.substring(lm + 14, lme);
                }
                if (bestDate == null || (date != null && date.compareTo(bestDate) > 0)) {
                    bestKey = key;
                    bestDate = date;
                }
                pos = e + 6;
            }
            if (bestKey == null) return Optional.empty();
            return Optional.of(getObject(bestKey));
        } catch (Exception e) {
            throw new StorageUnavailableException("S3 load failed", e);
        }
    }

    @Override
    public List<Tombstone> all() {
        try {
            List<Tombstone> all = new ArrayList<>();
            String token = null;
            do {
                StringBuilder path = new StringBuilder("?list-type=2");
                if (prefix != null && !prefix.isEmpty()) {
                    path.append("&prefix=").append(URLEncoder.encode(prefix, StandardCharsets.UTF_8));
                }
                if (token != null) {
                    path.append("&continuation-token=").append(URLEncoder.encode(token, StandardCharsets.UTF_8));
                }
                HttpRequest req = signedRequest("GET", path.toString(), null, null);
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) {
                    throw new StorageUnavailableException("S3 LIST all failed: " + resp.statusCode());
                }
                String xml = resp.body();
                int pos = 0;
                while (true) {
                    int ks = xml.indexOf("<Key>", pos);
                    if (ks < 0) break;
                    int ke = xml.indexOf("</Key>", ks);
                    if (ke < 0) break;
                    String key = xml.substring(ks + 5, ke);
                    Tombstone t = getObject(key);
                    if (t != null) all.add(t);
                    pos = ke + 6;
                }
                int ct = xml.indexOf("<NextContinuationToken>");
                if (ct < 0) break;
                int cte = xml.indexOf("</NextContinuationToken>", ct);
                if (cte < 0) break;
                token = xml.substring(ct + 22, cte);
            } while (token != null && !token.isEmpty());
            all.sort((a, b) -> b.deletedAt().compareTo(a.deletedAt()));
            return all;
        } catch (Exception e) {
            throw new StorageUnavailableException("S3 all() failed", e);
        }
    }

    @Override
    public List<Tombstone> filterByReason(String reasonPrefix) {
        if (reasonPrefix == null || reasonPrefix.isEmpty()) return all();
        return all().stream()
                .filter(t -> t.reason() != null && t.reason().startsWith(reasonPrefix))
                .toList();
    }

    @Override
    public List<Tombstone> filterBySubject(String subjectId) {
        if (subjectId == null) return List.of();
        return all().stream().filter(t -> subjectId.equals(t.subjectId())).toList();
    }

    @Override
    public boolean isHealthy() {
        try {
            HttpRequest req = signedRequest("HEAD", null, null, null);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 || resp.statusCode() == 404;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String backendId() { return "s3"; }

    public String bucket() { return bucket; }

    public void close() {
        executor.shutdownNow();
    }

    // ── S3 REST API helpers ──

    private Tombstone getObject(String key) {
        try {
            HttpRequest req = signedRequest("GET", key, null, null);
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) return null;
            if (resp.statusCode() / 100 != 2) {
                log.warn("S3 GET {} failed: {}", key, resp.statusCode());
                return null;
            }
            return JsonSerde.fromJson(resp.body());
        } catch (Exception e) {
            log.warn("S3 GET {} exception: {}", key, e.getMessage());
            return null;
        }
    }

    private String objectKey(Tombstone t) {
        return prefix + t.resourceType() + "/" + t.resourceId() + "/" + t.id() + ".json";
    }

    /**
     * SigV4-signed request. Header list contains metadata pairs
     * {@code name:value} (one per element).
     */
    private HttpRequest signedRequest(String method, String resourcePath, String body, String contentType, String... extraHeaders) {
        try {
            String host = URI.create(endpoint).getHost();
            String path = "/" + bucket + (resourcePath == null ? "" : (resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath));
            String now = java.time.Instant.now().toString();
            String dateStamp = now.substring(0, 8).replace("-", "");

            // 1) Canonical request
            StringBuilder headers = new StringBuilder();
            headers.append("host:").append(host).append('\n');
            headers.append("x-amz-content-sha256:UNSIGNED-PAYLOAD\n");
            headers.append("x-amz-date:").append(now).append('\n');
            if (extraHeaders != null) {
                for (String h : extraHeaders) headers.append(h).append('\n');
            }
            StringBuilder signedHeaderNames = new StringBuilder("host;x-amz-content-sha256;x-amz-date");
            for (String h : extraHeaders) {
                int colon = h.indexOf(':');
                if (colon > 0) signedHeaderNames.append(';').append(h, 0, colon);
            }
            StringBuilder canonicalRequest = new StringBuilder();
            canonicalRequest.append(method).append('\n');
            canonicalRequest.append(path).append('\n');
            canonicalRequest.append('\n');
            canonicalRequest.append(headers);
            canonicalRequest.append('\n');
            canonicalRequest.append(signedHeaderNames).append('\n');
            canonicalRequest.append("UNSIGNED-PAYLOAD");
            String hashedCanonicalRequest = sha256Hex(canonicalRequest.toString());

            // 2) String to sign
            String scope = dateStamp + "/" + region + "/s3/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" + now + "\n" + scope + "\n" + hashedCanonicalRequest;

            // 3) Signing key
            byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
            byte[] kRegion = hmacSha256(kDate, region);
            byte[] kService = hmacSha256(kRegion, "s3");
            byte[] kSigning = hmacSha256(kService, "aws4_request");

            byte[] signature = hmacSha256(kSigning, stringToSign);
            String signatureHex = HexFormat.of().formatHex(signature);

            String authHeader = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                    + ", SignedHeaders=" + signedHeaderNames
                    + ", Signature=" + signatureHex;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + path))
                    .header("Host", host)
                    .header("x-amz-date", now)
                    .header("x-amz-content-sha256", "UNSIGNED-PAYLOAD")
                    .header("Authorization", authHeader);
            for (String h : extraHeaders) {
                int colon = h.indexOf(':');
                if (colon > 0) {
                    builder.header(h.substring(0, colon), h.substring(colon + 1));
                }
            }
            if (body != null) builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            else if ("PUT".equals(method)) builder.PUT(HttpRequest.BodyPublishers.noBody());
            else if ("DELETE".equals(method)) builder.DELETE();
            else if ("HEAD".equals(method)) builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            return builder.timeout(Duration.ofSeconds(30)).build();
        } catch (Exception e) {
            throw new StorageUnavailableException("Failed to sign S3 request", e);
        }
    }

    private static String sha256Hex(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Minimal hand-written JSON ser/de for {@link Tombstone}. */
    static final class JsonSerde {
        static String toJson(Tombstone t) {
            return "{"
                    + "\"id\":\"" + t.id() + "\","
                    + "\"subjectId\":\"" + esc(t.subjectId()) + "\","
                    + "\"resourceType\":\"" + esc(t.resourceType()) + "\","
                    + "\"resourceId\":\"" + esc(t.resourceId()) + "\","
                    + "\"reason\":\"" + esc(t.reason()) + "\","
                    + "\"signature\":\"" + esc(t.signature()) + "\","
                    + "\"deletedAt\":\"" + t.deletedAt() + "\","
                    + "\"requesterId\":\"" + esc(t.requesterId()) + "\""
                    + "}";
        }

        static Tombstone fromJson(String json) {
            String body = json.substring(1, json.length() - 1);
            String[] fields = body.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            UUID id = null; String subject = null, rtype = null, rid = null, reason = null,
                    sig = null, deletedAt = null, req = null;
            for (String f : fields) {
                int colon = f.indexOf(':');
                if (colon < 0) continue;
                String k = f.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                String v = f.substring(colon + 1).trim().replaceAll("^\"|\"$", "");
                switch (k) {
                    case "id" -> id = UUID.fromString(v);
                    case "subjectId" -> subject = v;
                    case "resourceType" -> rtype = v;
                    case "resourceId" -> rid = v;
                    case "reason" -> reason = v;
                    case "signature" -> sig = v;
                    case "deletedAt" -> deletedAt = v;
                    case "requesterId" -> req = v;
                }
            }
            return new Tombstone(id, subject, rtype, rid, reason, sig == null ? "" : sig,
                    Instant.parse(deletedAt), req);
        }

        private static String esc(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
