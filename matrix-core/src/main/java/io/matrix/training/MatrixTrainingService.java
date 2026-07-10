package io.matrix.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * M.A.T.R.I.X. Training Service — starts background training on application startup.
 *
 * <p>Initializes the training engine with:
 * <ul>
 *   <li>All pretrained neurons from7 models</li>
 *   <li>Training data from SINV forum (12,900 ideas)</li>
 *   <li>Comprehensive world understanding data</li>
 *   <li>Background training loop (every30 seconds)</li>
 *   <li>LLM verification loop (every60 seconds)</li>
 *   <li>Genetic evolution loop (every5 minutes)</li>
 * </ul>
 */
@ApplicationScoped
public class MatrixTrainingService {

    private static final Logger log = LoggerFactory.getLogger(MatrixTrainingService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private MatrixTrainingEngine engine;

    /**
     * Starts the training engine on application startup.
     */
    void onStart(@Observes StartupEvent ev) {
        log.info("Starting M.A.T.R.I.X. Training Service...");

        engine = new MatrixTrainingEngine();

        // Load training data from files
        loadTrainingData();

        // Add default training pairs
        addDefaultTrainingPairs();

        // Start background training
        engine.start();

        log.info("M.A.T.R.I.X. Training Service started");
        log.info("Pretrained neurons: {}", engine.getStats().pretrainedNeurons());
        log.info("Training data pairs: {}", engine.getStats().trainingDataSize());
    }

    /**
     * Loads training data from JSON files.
     */
    private void loadTrainingData() {
        List<String[]> pairs = new ArrayList<>();

        // Load all available training data files
        String[] dataFiles = {
            "models/training_data/comprehensive_training.json",
            "models/training_data/qa_pairs.json",
            "models/training_data/world_understanding.json",
            "models/training_data/forum_training_pairs.json",
            "models/training_data/world_knowledge.json",
            "models/training_data/combined_training.json"
        };

        for (String file : dataFiles) {
            pairs.addAll(loadJsonFile(file));
        }

        // Add all pairs to engine
        for (String[] pair : pairs) {
            engine.addTrainingPair(pair[0], pair[1]);
        }

        log.info("Loaded {} training pairs from {} files", pairs.size(), dataFiles.length);
    }

    /**
     * Loads Q&A pairs from a JSON file.
     */
    private List<String[]> loadJsonFile(String filePath) {
        List<String[]> pairs = new ArrayList<>();
        Path path = Path.of(filePath);

        if (!Files.exists(path)) {
            log.warn("Training data file not found: {}", filePath);
            return pairs;
        }

        try {
            JsonNode root = JSON.readTree(path.toFile());

            if (root.isArray()) {
                for (JsonNode node : root) {
                    String input = node.has("input") ? node.get("input").asText() :
                                  node.has("question") ? node.get("question").asText() : null;
                    String output = node.has("output") ? node.get("output").asText() :
                                   node.has("answer") ? node.get("answer").asText() : null;

                    if (input != null && output != null && !input.isBlank() && !output.isBlank()) {
                        pairs.add(new String[]{input, output});
                    }
                }
            }

            log.info("Loaded {} pairs from {}", pairs.size(), filePath);
        } catch (IOException e) {
            log.error("Failed to load training data from {}: {}", filePath, e.getMessage());
        }

        return pairs;
    }

    /**
     * Adds default training pairs for bootstrapping.
     */
    private void addDefaultTrainingPairs() {
        // Basic conversation pairs
        engine.addTrainingPair("Hello", "Hello! I am M.A.T.R.I.X., a modular autonomous neural system.");
        engine.addTrainingPair("What is AI?", "AI is artificial intelligence — systems that can learn, reason, and act autonomously.");
        engine.addTrainingPair("How are you?", "I am functioning well. My neural clusters are active and learning.");
        engine.addTrainingPair("What can you do?", "I can reason through boolean logic, learn from experience, and help with various tasks.");
        engine.addTrainingPair("Explain boolean logic", "Boolean logic uses true/false values with operators like AND, OR, NOT to make decisions.");

        // MATRIX-specific pairs
        engine.addTrainingPair("What is MATRIX?", "M.A.T.R.I.X. is Modular Autonomous Training & Recursive Intelligence eXchange — a neural architecture based on boolean logic.");
        engine.addTrainingPair("How does MATRIX work?", "MATRIX uses MPDT neurons — boolean decision trees that evolve through genetic algorithms to learn patterns.");
        engine.addTrainingPair("What are MPDT neurons?", "MPDT neurons are McCulloch-Pitts Decision Tree neurons — boolean functions represented as learnable decision trees.");
        engine.addTrainingPair("What is BRC?", "BRC is Boolean Reasoning Chain — multi-step logical reasoning through boolean vector transformations.");
        engine.addTrainingPair("What is BRAG?", "BRAG is Boolean RAG — knowledge retrieval using Hamming distance search in boolean space.");

        log.info("Added {} default training pairs", 10);
    }

    /**
     * Returns the training engine (for external access).
     */
    public MatrixTrainingEngine getEngine() {
        return engine;
    }
}
