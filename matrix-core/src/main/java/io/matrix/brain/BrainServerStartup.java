package io.matrix.brain;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * W507 — Brain Server Startup Task.
 * 
 * Starts the BrainHttpServer automatically when Quarkus boots.
 * This makes the brain accessible via HTTP alongside the Quarkus REST endpoints.
 * 
 * Configuration via environment variables or application.properties:
 * - MATRIX_BRAIN_PORT (default: 9200)
 * - MATRIX_BRAIN_MODEL (default: models/onnx/qwen05b)
 * - MATRIX_BRAIN_ENABLED (default: true)
 */
@ApplicationScoped
public class BrainServerStartup {
    
    private static final Logger log = LoggerFactory.getLogger(BrainServerStartup.class);
    private BrainHttpServer server;
    
    void onStart(@Observes StartupEvent ev) {
        String enabled = System.getenv("MATRIX_BRAIN_ENABLED");
        if (enabled != null && enabled.equals("false")) {
            log.info("BrainServerStartup: disabled via MATRIX_BRAIN_ENABLED=false");
            return;
        }
        
        String port = System.getenv("MATRIX_BRAIN_PORT");
        String model = System.getenv("MATRIX_BRAIN_MODEL");
        int portNum = port != null ? Integer.parseInt(port) : 9200;
        String modelPath = model != null ? model : "models/onnx/qwen05b";
        
        log.info("BrainServerStartup: starting brain server on port " + portNum);
        log.info("BrainServerStartup: model path: " + modelPath);
        
        try {
            server = new BrainHttpServer(portNum, modelPath);
            server.start();
            log.info("BrainServerStartup: brain server started on port " + portNum);
        } catch (Exception e) {
            log.error("BrainServerStartup: failed to start brain server", e);
        }
    }
    
    // Shutdown hook
    void onStop(@Observes io.quarkus.runtime.ShutdownEvent ev) {
        log.info("BrainServerStartup: shutting down brain server");
    }
}
