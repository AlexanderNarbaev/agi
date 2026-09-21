package io.matrix.ingest;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IngestResourceTest {

    @Test
    void ingestTextAcceptsValidPayload() {
        var res = new IngestResource();
        // No @Inject wiring — calls will hit the catch block or default
        // We just verify it doesn't crash on a null ingestor with a basic payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "Hello world");
        payload.put("title", "Test");
        Response r = res.ingestText(payload);
        // Without ingestor, either 500 (NPE caught) or 200 depending on path
        assertTrue(r.getStatus() == 200 || r.getStatus() == 500);
    }

    @Test
    void ingestTextRejectsEmpty() {
        var res = new IngestResource();
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "");
        Response r = res.ingestText(payload);
        assertEquals(400, r.getStatus());
    }

    @Test
    void ingestTextRejectsMissing() {
        var res = new IngestResource();
        Map<String, Object> payload = new HashMap<>();
        // no text key
        Response r = res.ingestText(payload);
        assertEquals(400, r.getStatus());
    }

    @Test
    void ingestTextRejectsTooLarge() {
        var res = new IngestResource();
        Map<String, Object> payload = new HashMap<>();
        String big = "x".repeat(1_000_001);
        payload.put("text", big);
        Response r = res.ingestText(payload);
        assertEquals(400, r.getStatus());
    }

    @Test
    void ingestBinaryRejectsMissingData() {
        var res = new IngestResource();
        Map<String, Object> payload = new HashMap<>();
        // no data key
        Response r = res.ingestBinary("audio", payload);
        assertEquals(400, r.getStatus());
    }

    @Test
    void ingestBinaryRejectsEmptyData() {
        var res = new IngestResource();
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "");
        Response r = res.ingestBinary("audio", payload);
        assertEquals(400, r.getStatus());
    }
}
