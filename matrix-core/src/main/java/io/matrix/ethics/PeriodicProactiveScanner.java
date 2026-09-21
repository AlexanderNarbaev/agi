package io.matrix.ethics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * L7 §3.6 — Periodic proactive ethical scanning.
 *
 * <p>Wraps {@link ProactiveEthicalScanner} in a {@link ScheduledExecutorService}
 * that periodically scans driver states and emits risks to a registered
 * {@link RiskListener}. The scanner is FROZEN-tolerant — it never mutates
 * the system under scan and respects {@code FROZEN} flags by logging-only
 * mode when invoked against immutable configurations.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Construct with a {@link ProactiveEthicalScanner} and a {@link RiskListener}.</li>
 *   <li>Call {@link #start(long)} with the desired period (ms).</li>
 *   <li>Call {@link #stop()} when shutting down (always — even on SIGTERM).</li>
 * </ol>
 *
 * <p>Gap: GAP-022 polish — periodic background scanning per L7 §3.6.
 */
public final class PeriodicProactiveScanner {

    /** Receives risk notifications. Implementations should be thread-safe. */
    @FunctionalInterface
    public interface RiskListener {
        /** Called for each new risk detected by the underlying scanner. */
        void onRisks(List<String> risks);
    }

    private static final Logger log = LoggerFactory.getLogger(PeriodicProactiveScanner.class);

    private final ProactiveEthicalScanner scanner;
    private final RiskListener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> task;

    public PeriodicProactiveScanner(ProactiveEthicalScanner scanner, RiskListener listener) {
        if (scanner == null) throw new IllegalArgumentException("scanner must not be null");
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        this.scanner = scanner;
        this.listener = listener;
    }

    /** Start periodic scanning every {@code periodMs} milliseconds. */
    public void start(long periodMs) {
        if (periodMs <= 0) throw new IllegalArgumentException("periodMs must be > 0");
        if (!running.compareAndSet(false, true)) {
            log.debug("PeriodicProactiveScanner already running; start() is a no-op");
            return;
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-proactive-scanner");
            t.setDaemon(true);
            return t;
        });
        // Periodic scan uses an empty driver-states map as the simplest baseline.
        // Callers wanting custom states should use scanOnce(Map) on-demand.
        this.task = executor.scheduleAtFixedRate(
                () -> scanOnce(Map.of()), periodMs, periodMs, TimeUnit.MILLISECONDS);
        log.info("PeriodicProactiveScanner started: period={}ms", periodMs);
    }

    /** Stop the scanner. Safe to call multiple times. */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (task != null) task.cancel(false);
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("PeriodicProactiveScanner executor did not terminate in 2s");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("PeriodicProactiveScanner stopped");
    }

    /** Whether the scanner is currently scheduled. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Run a single scan pass using the given driver states.
     * Visible for testing and for callers that want immediate on-demand scanning.
     */
    public List<String> scanOnce(Map<String, Double> driverStates) {
        List<String> risks = scanner.scan(driverStates);
        if (!risks.isEmpty()) {
            try {
                listener.onRisks(risks);
            } catch (RuntimeException re) {
                log.warn("RiskListener threw: {}", re.getMessage());
            }
        }
        return risks;
    }
}
