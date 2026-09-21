package io.matrix.recording;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkCameraTest {

    @Test
    void testCreateCamera() {
        BenchmarkCamera camera = new BenchmarkCamera();
        assertNotNull(camera);
        assertTrue(camera.getAllRecordings().isEmpty());
    }

    @Test
    void testRecordVideo() {
        BenchmarkCamera camera = new BenchmarkCamera();
        camera.recordVideo("test1", "/tmp/test1.mp4", 5000);
        assertEquals(1, camera.getAllRecordings().size());
    }

    @Test
    void testRecordHAR() {
        BenchmarkCamera camera = new BenchmarkCamera();
        camera.recordHAR("test2", "/tmp/test2.har", 3000);
        assertEquals(1, camera.getAllRecordings().size());
    }

    @Test
    void testRecordTimeline() {
        BenchmarkCamera camera = new BenchmarkCamera();
        camera.recordTimeline("test3", "/tmp/test3.csv", 2000);
        assertEquals(1, camera.getAllRecordings().size());
    }

    @Test
    void testBundleEvidence() {
        BenchmarkCamera camera = new BenchmarkCamera();
        camera.recordVideo("test4", "/tmp/test4.mp4", 1000);
        camera.recordHAR("test4", "/tmp/test4.har", 500);
        BenchmarkCamera.EvidenceBundle bundle = camera.bundle("test4", "Test summary");
        assertEquals(2, bundle.recordings().size());
        assertEquals(1500, bundle.totalDurationMs());
    }

    @Test
    void testArtifactTypes() {
        assertEquals(5, BenchmarkCamera.ArtifactType.values().length);
        assertNotNull(BenchmarkCamera.ArtifactType.valueOf("MP4_VIDEO"));
        assertNotNull(BenchmarkCamera.ArtifactType.valueOf("HAR_LOG"));
    }
}
