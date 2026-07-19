package io.matrix.chat;

import io.matrix.agent.AgentBrainService;
import io.matrix.chat.feedback.FeedbackAggregator;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background daemon that feeds real user conversations into the neural brain.
 *
 * <h2>Pipeline</h2>
 * <pre>
 *   ConversationRecorder ──► ChatTrainingPairGenerator ──► AgentBrainService
 *          ▲                                                  ▲    │
 *          │                                                  │    ▼
 *   ChatFeedbackStore ───── FeedbackAggregator ──────────────┘  onlineTrain()
 * </pre>
 *
 * <h2>Cadence</h2>
 * <ul>
 *   <li>Every {@code matrix.chat.gen-interval-seconds} (default 60):
 *       <ol>
 *         <li>{@link ChatTrainingPairGenerator#generateAndAppend()} converts any new
 *             recorded conversations into (input → response) pairs on disk</li>
 *         <li>For each new pair, encode the input into a 64-bit MPDT sensor vector
 *             and call {@link AgentBrainService#recordFeedback(long, boolean)}</li>
 *         <li>If feedback is positive, call {@link AgentBrainService#onlineTrain(int)}
 *             to nudge the action layer towards the user's preference</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <p>This is the "real input data → real tasks" loop the user requested:
 * the brain improves with every human interaction, gated by the ethical filter
 * and the user's thumbs-up/down rating.
 *
 * <p>The scheduler is a daemon thread, so the JVM can shut down cleanly.
 */
@ApplicationScoped
public class ChatDrivenTrainer {

    private static final Logger log = LoggerFactory.getLogger(ChatDrivenTrainer.class);

    @ConfigProperty(name = "matrix.chat.trainer-enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "matrix.chat.trainer-interval-seconds", defaultValue = "60")
    long trainerIntervalSeconds;

    @ConfigProperty(name = "matrix.chat.online-train-iterations", defaultValue = "3")
    int onlineTrainIterations;

    @ConfigProperty(name = "matrix.chat.output-file",
            defaultValue = "models/training_data/auto_generated.jsonl")
    String trainingPairsFile;

    @ConfigProperty(name = "matrix.chat.last-run-marker",
            defaultValue = "data/conversations/.last_training_run")
    String lastRunMarker;

    @Inject
    ChatTrainingPairGenerator generator;

    @Inject
    AgentBrainService brain;

    @Inject
    FeedbackAggregator feedback;

    @Inject
    ConversationRecorder recorder;

    private ScheduledExecutorService executor;
    private final AtomicLong totalCycles = new AtomicLong();
    private final AtomicLong totalPairs = new AtomicLong();
    private final AtomicLong totalFeedbacks = new AtomicLong();
    private final AtomicLong totalTrains = new AtomicLong();

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            log.info("ChatDrivenTrainer disabled (matrix.chat.trainer-enabled=false)");
            return;
        }
        try {
            Files.createDirectories(Path.of(lastRunMarker).getParent());
        } catch (Exception e) {
            log.warn("Could not create trainer marker dir: {}", e.getMessage());
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-chat-trainer");
            t.setDaemon(true);
            return t;
        });
        // First run after a short delay so the rest of the app can wire up.
        executor.scheduleAtFixedRate(this::runCycleSafely,
                30, trainerIntervalSeconds, TimeUnit.SECONDS);
        log.info("ChatDrivenTrainer started → interval={}s, output={}",
                trainerIntervalSeconds, trainingPairsFile);
    }

    void onStop(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("ChatDrivenTrainer stopped. cycles={}, pairs={}, feedbacks={}, trains={}",
                totalCycles.get(), totalPairs.get(), totalFeedbacks.get(), totalTrains.get());
    }

    private void runCycleSafely() {
        try {
            runCycle();
        } catch (Throwable t) {
            log.error("ChatDrivenTrainer cycle crashed: {}", t.getMessage());
        }
    }

    /**
     * Single training cycle:
     * <ol>
     *   <li>Generate new training pairs from recent conversations</li>
     *   <li>For each pair, encode input → 64-bit sensor bits, record feedback
     *       (positive iff feedback rating ≥ 0.6)</li>
     *   <li>If at least one positive feedback was recorded this cycle, trigger
     *       a single {@code onlineTrain()} call to update the action layer</li>
     * </ol>
     */
    public void runCycle() {
        totalCycles.incrementAndGet();
        int written = generator.generateAndAppend();
        totalPairs.addAndGet(written);

        if (written == 0) {
            if (log.isDebugEnabled()) {
                log.debug("runCycle(): no new pairs (existing={})", generator.knownPairCount());
            }
            touchMarker();
            return;
        }

        // Read back the just-written pairs, convert each into a feedback signal
        List<ChatTrainingPairGenerator.TrainingPair> pairs = generator.generateFromRecords(
                readNewlyWrittenPairs(written));
        int positive = 0;
        int negative = 0;
        for (ChatTrainingPairGenerator.TrainingPair p : pairs) {
            long sensorBits = encode(p.input());
            double rating = feedback.ratingFor(p.conversationId());
            // Treat the rating as the "success" signal for the brain
            boolean success = rating >= 0.6;
            if (brain != null) {
                brain.recordFeedback(sensorBits, success);
                totalFeedbacks.incrementAndGet();
                if (success) positive++; else negative++;
            }
        }

        // Train only on positive signals — we don't want to reinforce negative feedback
        if (brain != null && positive > 0) {
            int iters = Math.max(1, onlineTrainIterations);
            brain.onlineTrain(iters);
            totalTrains.incrementAndGet();
            log.info("ChatDrivenTrainer cycle: newPairs={}, positive={}, negative={}, trained={} iter",
                    written, positive, negative, iters);
        } else if (written > 0) {
            log.info("ChatDrivenTrainer cycle: newPairs={}, but no positive feedback yet",
                    written);
        }
        touchMarker();
    }

    private List<ConversationRecord> readNewlyWrittenPairs(int expected) {
        // Reload everything and let the generator filter; in practice this reads
        // a few hundred records per cycle which is fine.
        if (recorder == null) return List.of();
        return recorder.readAll(expected * 2 + 16);
    }

    /** Encode a string into the same 64-bit hash MPDT neurons use for sensor input. */
    static long encode(String input) {
        if (input == null) return 0L;
        long h = 1125899906842597L; // large prime
        for (int i = 0; i < input.length(); i++) {
            h = 31 * h + input.charAt(i);
        }
        // Spread the bits
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }

    private void touchMarker() {
        try {
            Files.writeString(Path.of(lastRunMarker),
                    Long.toString(System.currentTimeMillis()),
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // marker is best-effort
        }
    }

    public long totalCycles() { return totalCycles.get(); }
    public long totalPairs() { return totalPairs.get(); }
    public long totalFeedbacks() { return totalFeedbacks.get(); }
    public long totalTrains() { return totalTrains.get(); }
}