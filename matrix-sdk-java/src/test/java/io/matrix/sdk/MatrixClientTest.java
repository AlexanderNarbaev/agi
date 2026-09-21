package io.matrix.sdk;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatrixClientTest {

    @Test
    void testDefaultBaseUrl() {
        assertEquals("https://api.matrix.ai", MatrixClient.baseUrl());
    }

    @Test
    void testSdkVersion() {
        assertEquals("0.1.0-T01", MatrixClient.SDK_VERSION);
    }
}
