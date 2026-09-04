package io.matrix.api;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 9.8 — Async training endpoint.
 *
 * <p>Runs POST /v1/train in a background thread so the HTTP request
 * returns immediately with a job ID. The actual training takes
 * ~1s/pair (200 flips/pair cap × 8-bit BPE encoding), so a 100-pair
 * 5-epoch job takes ~8 minutes and would otherwise lock the server.
 *
 * <p>POST /v1/train/async — start a job
 * <br>GET /v1/train/async/{jobId} — get status
 * <br>GET /v1/train/async — list all jobs
 *
 * <p>Uses a single-thread executor to serialize training (chain state
 * is mutated; parallel training would race). New jobs queue if one
 * is already running.
 */
@jakarta.ws.rs.Path("/v1/train/async")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AsyncChainTrainerResource {

    private static final Logger log = LoggerFactory.getLogger(AsyncChainTrainerResource.class);

    @Inject
    ChainTrainerEndpoint trainer;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicLong jobCounter = new AtomicLong();
    private ExecutorService executor;

    void onStart(@Observes StartupEvent ev) {
        // Single thread — chain state is mutated, parallel jobs would race.
        // New jobs queue behind the currently running one.
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "async-trainer");
            t.setDaemon(true);
            return t;
        });
        log.info("AsyncChainTrainer: started single-thread executor");
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @POST
    public Map<String, Object> startJob(Map<String, Object> body) {
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        long seq = jobCounter.incrementAndGet();
        Job job = new Job(jobId, seq, body);
        jobs.put(jobId, job);

        Future<?> future = executor.submit(() -> runJob(job));
        job.future = future;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jobId", jobId);
        resp.put("seq", seq);
        resp.put("status", "queued");
        resp.put("queuedJobs", jobs.size());
        resp.put("note", "GET /v1/train/async/" + jobId + " to check status");
        log.info("AsyncChainTrainer: queued job {} (seq {})", jobId, seq);
        return resp;
    }

    @GET
    @Path("/{jobId}")
    public Map<String, Object> getJob(@PathParam("jobId") String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            return Map.of("error", "no such job: " + jobId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.jobId);
        body.put("seq", job.seq);
        body.put("status", job.status);
        body.put("submittedAt", job.submittedAt);
        body.put("startedAt", job.startedAt);
        body.put("finishedAt", job.finishedAt);
        body.put("elapsedMs", job.elapsedMs);
        if (job.result != null) body.put("result", job.result);
        if (job.error != null) body.put("error", job.error);
        return body;
    }

    @GET
    public Map<String, Object> listJobs() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalJobs", jobs.size());
        body.put("totalSubmitted", jobCounter.get());
        body.put("jobs", jobs.values().stream()
                .map(j -> Map.of(
                        "jobId", j.jobId,
                        "seq", j.seq,
                        "status", j.status))
                .toList());
        return body;
    }

    private void runJob(Job job) {
        job.status = "running";
        job.startedAt = System.currentTimeMillis();
        long t0 = System.nanoTime();
        try {
            Map<String, Object> result = trainer.trainImpl(job.requestBody);
            job.result = result;
            job.status = "completed";
            log.info("AsyncChainTrainer: job {} (seq {}) completed", job.jobId, job.seq);
        } catch (Throwable t) {
            job.error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            job.status = "failed";
            log.error("AsyncChainTrainer: job {} (seq {}) failed: {}",
                    job.jobId, job.seq, job.error);
        } finally {
            job.finishedAt = System.currentTimeMillis();
            job.elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        }
    }

    /** Internal job record. */
    static class Job {
        final String jobId;
        final long seq;
        final long submittedAt;
        final Map<String, Object> requestBody;
        volatile String status = "queued";
        volatile long startedAt;
        volatile long finishedAt;
        volatile long elapsedMs;
        volatile Map<String, Object> result;
        volatile String error;
        volatile Future<?> future;

        Job(String jobId, long seq, Map<String, Object> body) {
            this.jobId = jobId;
            this.seq = seq;
            this.submittedAt = System.currentTimeMillis();
            this.requestBody = body;
        }
    }
}
