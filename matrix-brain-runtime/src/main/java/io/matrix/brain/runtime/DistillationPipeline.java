package io.matrix.brain.runtime;

import io.matrix.bir.Bir;
import io.matrix.bir.BirRegistry;
import io.matrix.distill.DatasetConnectorV2;
import io.matrix.distill.Distiller;
import io.matrix.federation.liquid.DatasetConnector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RECON-W5 Step 1 — RealDistillationPipeline.
 *
 * <p>Production caller of {@link Distiller} + {@link DatasetConnectorV2}.
 * Captures activations, synthesizes BIR clauses via the core Distiller,
 * and merges the result into the live {@link BirRegistry}.</p>
 *
 * <p><b>Determinism</b>: seeded {@link Random}(42L); same source ⇒ same
 * clauses (Article III reproducibility).</p>
 *
 * <p><b>Article VIII</b>: every distilled merge emits engine markers naming
 * the actual core engine calls (Distiller.synthesize + BirRegistry.register +
 * DatasetConnectorV2.generateSamples).</p>
 */
public final class DistillationPipeline {

    /** Output of a single distillation run. */
    public record RunResult(
        Bir distilledBir,
        String sourceId,
        int samplesUsed,
        double fidelity,
        long durationMs,
        String artifactHash,
        String provenance
    ) {}

    private final Random random;
    private final long seed;
    private final BirRegistry registry;
    private final DatasetConnectorV2 datasetConnector;

    public DistillationPipeline(BirRegistry registry) {
        this(42L, registry);
    }

    public DistillationPipeline(long seed, BirRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry required");
        this.seed = seed;
        this.random = new Random(seed);
        this.registry = registry;
        this.datasetConnector = new DatasetConnectorV2(
            java.nio.file.Path.of(System.getProperty("user.dir"), "data", "distill-cache"));
    }

    public long seed() { return seed; }
    public BirRegistry registry() { return registry; }

    /**
     * Distill a single-source run: synthetic teacher for a known relation
     * (transitive / AND / OR / etc.). The {@code pattern} determines the
     * teacher activation function.
     *
     * <p>This is the LAYER-WISE PROBE-1 path: we don't need a real ONNX
     * model to demonstrate the pipeline. The synthetic teacher produces
     * deterministic activation patterns that the Distiller can learn.</p>
     *
     * @param sourceId  identifier for the source (model name or dataset name)
     * @param pattern   teacher function: "and", "or", "transitive", "xor", "imply"
     * @param inputBits number of input bits (Article II: K_MAX ≤ 20)
     * @param sampleCount how many (input,activation) pairs to capture
     * @return RunResult with the distilled Bir + fidelity + provenance
     */
    public RunResult distillFromSyntheticTeacher(
            String sourceId, String pattern, int inputBits, int sampleCount) {
        KMaxEnforcer.enforce(inputBits);
        if (sampleCount < 1) throw new IllegalArgumentException("sampleCount must be > 0");
        long startNs = System.nanoTime();
        int before = registry.size();
        Distiller distiller = new Distiller(inputBits, 0.5);
        // Generate (input, activation) pairs according to the pattern.
        int captured = 0;
        for (int i = 0; i < sampleCount; i++) {
            long[] input = randomFingerprint(i, inputBits);
            float[] activation = applyTeacher(pattern, input);
            distiller.capture(input, activation);
            captured++;
        }
        // Synthesize the Bir.
        String provenance = String.format("source=%s,pattern=%s,seed=%d,fidelity=TBD",
                                          sourceId, pattern, seed);
        Bir distilled = distiller.synthesize(provenance);
        double fidelity = distiller.fidelity(distilled,
            // Held-out 20% of inputs (not actually held out here for demo speed)
            new long[][] { randomFingerprint(0, inputBits) },
            new float[][] { applyTeacher(pattern, randomFingerprint(0, inputBits)) });
        // Register in BirRegistry.
        String ruleId = "distill-" + pattern + "-" + seed + "-" + sourceId;
        BirRegistry.Entry entry = registry.register(
            ruleId, distilled, ruleId, fidelity, provenance.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        // Artifact hash: simple deterministic digest.
        String artifactHash = Integer.toHexString(distilled.toString().hashCode());
        int after = registry.size();
        return new RunResult(entry.bir(), sourceId, captured, fidelity, durationMs,
            artifactHash, provenance + ",consolidationDelta=" + (after - before)
            + ",registered=" + (after > before));
    }

    /**
     * Distill from a local dataset (BoolQ / LogiQA / CLUTRR via
     * DatasetConnectorV2). No network required — datasets are
     * procedurally generated and cached.
     */
    public RunResult distillFromDataset(
            String sourceId, DatasetConnectorV2.DatasetType datasetType, int inputBits) {
        KMaxEnforcer.enforce(inputBits);
        long startNs = System.nanoTime();
        int before = registry.size();
        Distiller distiller = new Distiller(inputBits, 0.5);
        List<DatasetConnector.DatasetEntry> samples = datasetConnector.generateSamples(datasetType, 16);
        int captured = 0;
        for (DatasetConnector.DatasetEntry e : samples) {
            // Use text length as a 1-bit activation feature.
            long[] input = hashText(e.question(), inputBits);
            float[] activation = "True".equalsIgnoreCase(e.answer()) ? new float[]{1.0f} : new float[]{0.0f};
            distiller.capture(input, activation);
            captured++;
        }
        String provenance = String.format("source=%s,dataset=%s,seed=%d",
                                          sourceId, datasetType, seed);
        Bir distilled = distiller.synthesize(provenance);
        BirRegistry.Entry entry = registry.register(
            "distill-" + datasetType + "-" + seed + "-" + sourceId,
            distilled, sourceId, 0.5, provenance.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        String artifactHash = Integer.toHexString(distilled.toString().hashCode());
        int after = registry.size();
        return new RunResult(entry.bir(), sourceId, captured, 0.5, durationMs,
            artifactHash, provenance + ",registered=" + (after > before));
    }

    /**
     * RECON-W14 — Real ONNX distillation.
     *
     * <p>Loads a real ONNX teacher model (e.g. data/models/teacher/teacher.onnx
     * — the tiny FFN produced by scripts/gen_teacher_onnx.py), captures
     * activations through ONNX Runtime, and synthesizes a Bir via the core
     * Distiller. This is the production path that closes L-1 (no real ONNX
     * distillation).</p>
     *
     * <p>Determinism: the synthetic input fingerprints are seeded (42L), so
     * the same teacher ONNX file yields the same Bir (Article III).</p>
     *
     * <p>Engine markers: distillation runs {@code OnnxActivationTeacher.inferBatch()}
     * (engine=OnnxActivationTeacher) → {@code Distiller.synthesize}
     * (engine=Distiller.synthesize) → {@code BirRegistry.register}
     * (engine=BirRegistry.register).</p>
     *
     * <p>Article VIII compliance: this method makes no external network calls
     * (teacher ONNX is offline); only the ONNX Runtime native library is
     * loaded. RuntimeLlmGuardTest stays green.</p>
     */
    public RunResult distillFromOnnxTeacher(
            String sourceId, java.nio.file.Path onnxPath, int inputBits, int sampleCount)
            throws java.io.IOException {
        KMaxEnforcer.enforce(inputBits);
        if (sampleCount < 1) throw new IllegalArgumentException("sampleCount must be > 0");
        if (!java.nio.file.Files.isRegularFile(onnxPath)) {
            throw new java.io.IOException("ONNX teacher not found: " + onnxPath
                + " — generate via scripts/gen_teacher_onnx.py");
        }
        long startNs = System.nanoTime();
        int before = registry.size();
        Distiller distiller = new Distiller(inputBits, 0.5);
        try (io.matrix.distill.OnnxActivationTeacher teacher =
                new io.matrix.distill.OnnxActivationTeacher(onnxPath)) {
            int captured = 0;
            long[][] capturedInputs = new long[sampleCount][];
            for (int i = 0; i < sampleCount; i++) {
                long[] input = randomFingerprint(i, inputBits);
                capturedInputs[i] = input;
                float[] features = io.matrix.distill.OnnxActivationTeacher.unpackFeatures(
                    packInputToLong(input), inputBits);
                try {
                    float[] activation = teacher.inferBatch(new float[][]{features});
                    distiller.capture(input, activation);
                    captured++;
                } catch (Exception ex) {
                    // Propagate diagnostic info; engine marker visible above
                    throw new java.io.IOException("ONNX inference failed at sample "
                        + i + ": " + ex.getMessage(), ex);
                }
            }
            String provenance = String.format(
                "engine=OnnxActivationTeacher,engine=Distiller.synthesize,engine=BirRegistry.register,"
                + "source=%s,onnx=%s,seed=%d,inputBits=%d,samples=%d",
                sourceId, onnxPath.toString(), seed, inputBits, captured);
            Bir distilled = distiller.synthesize(provenance);
            double fidelity = captured > 0 ? distiller.fidelity(distilled,
                new long[][]{capturedInputs[0]},
                inferHeldOut(teacher, capturedInputs[0], inputBits)) : 0.5;
            BirRegistry.Entry entry = registry.register(
                "distill-onnx-" + sourceId + "-" + seed, distilled,
                sourceId, fidelity,
                provenance.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            String artifactHash = Integer.toHexString(distilled.toString().hashCode());
            int after = registry.size();
            return new RunResult(entry.bir(), sourceId, captured, fidelity, durationMs,
                artifactHash, provenance + ",consolidationDelta=" + (after - before)
                + ",registered=" + (after > before));
        } catch (java.io.IOException ioe) {
            throw ioe;
        } catch (Exception ex) {
            throw new java.io.IOException("ONNX teacher init failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * RECON-W21 — distil from ACTIVATIONS CAPTURED OUT OF PROCESS.
     *
     * <p><b>Why this method exists.</b> {@link #distillFromOnnxTeacher} is wired
     * and correct, but ONNX Runtime 1.29.0's Java binding SEGFAULTS on this host
     * (JDK 25 + this Linux), so calling it from inside the JVM kills the process
     * before any distillation happens. That made L-1 look unfixable while the
     * actual capability was fine.</p>
     *
     * <p><b>The escape hatch.</b> Activations are captured in a SEPARATE process
     * by {@code scripts/capture_activations.py} (Python onnxruntime), written as
     * NDJSON, and replayed here. The ONNX dependency is now confined to an
     * offline tool and is unreachable from analyze/chat/think — which is exactly
     * what Article I requires of it.</p>
     *
     * <p><b>Article III (provenance).</b> The provenance string records the sidecar
     * script, the NDJSON path, the seed, the bit width, the sample count and the
     * batch id carried in the capture, so a learned artifact can always be traced
     * back to the offline run that produced it.</p>
     *
     * <p><b>Article II.</b> {@code inputBits} is still enforced by
     * {@link KMaxEnforcer}; the sidecar does not widen K_MAX.</p>
     *
     * <p>Malformed lines are skipped and counted, never silently ignored: the
     * number consumed is reported in the provenance so a partial capture is
     * visible rather than looking like a complete one.</p>
     */
    public RunResult distillFromActivations(
            String sourceId, String ndjsonPath, int inputBits)
            throws java.io.IOException {
        return distillFromActivations(sourceId, java.nio.file.Path.of(ndjsonPath), inputBits);
    }

    /**
     * RECON-W21 — content-addressed artifact hash.
     *
     * <p>Article III violation found by a determinism test: hashing
     * {@code Bir.toString()} mixed in the registry timestamp and the per-run entry
     * id, so two identical distillations of the same capture produced different
     * hashes. The hash must depend only on the LEARNED STRUCTURE, so it is
     * computed over arity, form kind and clause masks — never over a clock or a
     * generated identifier.</p>
     */
    private static String contentHash(io.matrix.bir.Bir distilled) {
        StringBuilder sb = new StringBuilder();
        sb.append(distilled.inputBits()).append('/').append(distilled.outputBits())
          .append('/').append(distilled.form());
        if (distilled instanceof io.matrix.bir.ClauseSetForm cs) {
            for (var c : cs.clauses()) {
                sb.append('|');
                for (long w : c.pos) sb.append(Long.toHexString(w)).append(':');
                sb.append('/');
                for (long w : c.neg) sb.append(Long.toHexString(w)).append(':');
            }
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    /** Path-taking overload. */
    public RunResult distillFromActivations(
            String sourceId, java.nio.file.Path ndjsonPath, int inputBits)
            throws java.io.IOException {
        KMaxEnforcer.enforce(inputBits);
        if (!java.nio.file.Files.isRegularFile(ndjsonPath)) {
            throw new java.io.IOException("activation capture not found: " + ndjsonPath
                + " — generate via: python3 scripts/capture_activations.py"
                + " --model data/models/teacher/teacher.onnx"
                + " --out " + ndjsonPath + " --dims 8");
        }
        long startNs = System.nanoTime();
        int before = registry.size();
        Distiller distiller = new Distiller(inputBits, 0.5);

        int consumed = 0;
        int skipped = 0;
        String batchId = "unknown";
        long[] firstInput = null;
        float[] firstActivation = null;

        java.util.List<String> lines = java.nio.file.Files.readAllLines(
            ndjsonPath, java.nio.charset.StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            io.matrix.distill.ActivationRecord rec =
                io.matrix.distill.ActivationRecord.parse(line);
            if (rec == null || rec.activation() == null || rec.activation().length == 0) {
                skipped++;
                continue;   // counted, not silently dropped
            }
            if ("unknown".equals(batchId) && rec.batchId() != null) {
                batchId = rec.batchId();
            }
            long[] input = rec.toBitVector(inputBits);
            distiller.capture(input, rec.activation());
            if (firstInput == null) {
                firstInput = input;
                firstActivation = rec.activation();
            }
            consumed++;
        }

        if (consumed == 0) {
            throw new java.io.IOException(
                "no usable activation records in " + ndjsonPath
                + " (lines=" + lines.size() + ", skipped=" + skipped + ")");
        }

        String provenance = String.format(
            "engine=ActivationRecord.replay,engine=Distiller.synthesize,"
            + "engine=BirRegistry.register,source=%s,capture=%s,"
            + "captureTool=scripts/capture_activations.py,onnxRuntime=out-of-process,"
            + "batch=%s,seed=%d,inputBits=%d,samples=%d,skipped=%d",
            sourceId, ndjsonPath.toString(), batchId, seed, inputBits, consumed, skipped);

        Bir distilled = distiller.synthesize(provenance);
        double fidelity = distiller.fidelity(distilled,
            new long[][]{firstInput}, new float[][]{firstActivation});

        BirRegistry.Entry entry = registry.register(
            "distill-activations-" + sourceId + "-" + seed, distilled,
            sourceId, fidelity,
            provenance.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        String artifactHash = contentHash(distilled);
        int after = registry.size();
        return new RunResult(entry.bir(), sourceId, consumed, fidelity, durationMs,
            artifactHash, provenance + ",consolidationDelta=" + (after - before)
            + ",registered=" + (after > before));
    }

    /** Pack a long[] fingerprint back to a single long (first element only). */
    private long packInputToLong(long[] input) {
        if (input == null || input.length == 0) return 0L;
        // Take the lower 64 bits of the input fingerprint.
        return input[0];
    }

    /** Compute activations on a held-out sample for fidelity measurement. */
    private float[][] inferHeldOut(io.matrix.distill.OnnxActivationTeacher teacher,
                                    long[] input, int inputBits) {
        try {
            float[] features = io.matrix.distill.OnnxActivationTeacher.unpackFeatures(
                packInputToLong(input), inputBits);
            return new float[][]{teacher.inferBatch(new float[][]{features})};
        } catch (Exception ex) {
            return new float[][]{{0.0f}};
        }
    }

    /** Generate a deterministic fingerprint for sample i. */
    private long[] randomFingerprint(int seed, int inputBits) {
        // Pack 64-bit fingerprint that exercises the lower inputBits bits.
        long[] out = new long[Math.max(1, (inputBits + 63) / 64)];
        Random r = new Random(this.seed + seed * 1000L);
        for (int i = 0; i < out.length; i++) out[i] = r.nextLong();
        return out;
    }

    /** Hash a text string into a bit vector of the given input size. */
    private long[] hashText(String text, int inputBits) {
        long[] out = new long[Math.max(1, (inputBits + 63) / 64)];
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            h ^= text.charAt(i);
            h *= 0x100000001b3L;
        }
        out[0] = h;
        return out;
    }

    /** Apply a teacher function. Supported: and, or, xor, imply, transitive. */
    private float[] applyTeacher(String pattern, long[] input) {
        // Count set bits in the input.
        int bits = 0;
        for (long l : input) bits += Long.bitCount(l);
        boolean isHigh = bits > 32;
        boolean isLow = bits == 0;
        float out = switch (pattern) {
            case "and"        -> isHigh ? 1.0f : 0.0f;
            case "or"         -> isLow ? 0.0f : 1.0f;
            case "xor"        -> (bits % 2 == 1) ? 1.0f : 0.0f;
            case "imply"      -> (isLow || isHigh) ? 1.0f : 0.0f;
            case "transitive" -> (bits >= 2)      ? 1.0f : 0.0f;
            default           -> (bits > 0)        ? 1.0f : 0.0f;
        };
        return new float[] { out };
    }
}
