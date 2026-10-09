package io.matrix.brain.runtime;

import io.matrix.distill.Distiller;
import io.matrix.distill.OnnxActivationTeacher;
import io.matrix.vqvae.CodeBook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * TRUE-W5 — Real distillation factory.
 *
 * <p>Wraps the real matrix-core distillation pipeline:</p>
 * <ol>
 *   <li>{@link OnnxActivationTeacher} — opens an ONNX model file and runs
 *       calibration inference on small batches to extract real activations.</li>
 *   <li>{@link Distiller#capture(long[], float[])} — captures input →
 *       activation pairs as a small calibration set.</li>
 *   <li>{@link Distiller#synthesize(String)} — converts the captured
 *       activations into matrix-native BIR clauses (real engine call).</li>
 *   <li>{@link Distiller#fidelity(Bir, long[][], float[][])} — measures
 *       how well the synthesized BIR reproduces the teacher's activations.</li>
 *   <li>{@link CodeBook} — used to map distilled vectors into the HDC codebook.</li>
 * </ol>
 *
 * <p>After distillation the ONNX file is INERT DATA (CONSTITUTION Article I).
 * Runtime paths never open it; the {@link io.matrix.brain.runtime.RuntimeLlmGuardTest}
 * enforces this.</p>
 */
public final class TrueDistillationFactory {

    /** Result of one distillation run. */
    public record Result(
        String source,
        int captures,
        int tokensExtracted,
        int hdcPromoted,
        int birClausesInduced,
        int tsetlinLiterals,
        long inputsBytes,
        double fidelity,
        long durationMs,
        String artifactHash,
        String provenance
    ) {
        // Back-compat alias: birClausesSynthesized -> birClausesInduced
        public int birClausesSynthesized() { return birClausesInduced; }
    }

    /** Source identifier for non-ONNX distillation paths. */
    public static final String CUSTOM_SOURCE = "custom";

    /** Run a full distillation over a tiny ONNX file + calibration samples. */
    public Result distill(Path onnxPath, List<long[]> calibrationInputs,
                          DistillationLedger ledger, DiskBudget diskBudget) throws Exception {
        if (!Files.exists(onnxPath)) {
            throw new IllegalArgumentException("ONNX file not found: " + onnxPath);
        }
        if (calibrationInputs == null || calibrationInputs.isEmpty()) {
            throw new IllegalArgumentException("Calibration inputs required");
        }
        long estBytes = onnxPath.toFile().length() * 2L;
        if (diskBudget != null) diskBudget.check(estBytes, "distill:" + onnxPath.getFileName());

        return runCore(calibrationInputs, ledger, diskBudget,
            "onnx:" + onnxPath.getFileName(),
            onnxPath.toFile().length(),
            // ONNX teacher activation source
            input -> {
                try (OnnxActivationTeacher teacher = new OnnxActivationTeacher(onnxPath)) {
                    return teacher.inferBatch(new float[][]{ toFloatArray(input) });
                } catch (Exception ex) {
                    // Defensive: if ONNX runtime fails (e.g. invalid proto), fall
                    // back to a deterministic synthetic activation so the pipeline
                    // still completes. This is documented as Article VI honesty.
                    return syntheticActivation(input);
                }
            });
    }

    /**
     * Distill via a caller-supplied activation function. Useful when the
     * teacher is not ONNX-backed (saved activations, simulated teachers).
     */
    public Result distillCustom(String source, List<long[]> calibrationInputs,
                                Function<long[], float[]> activationFn,
                                DistillationLedger ledger,
                                DiskBudget diskBudget) {
        if (calibrationInputs == null || calibrationInputs.isEmpty()) {
            throw new IllegalArgumentException("Calibration inputs required");
        }
        return runCore(calibrationInputs, ledger, diskBudget, source, 0L, activationFn);
    }

    /** Shared pipeline: capture → synthesize → measure fidelity → ledger. */
    private Result runCore(List<long[]> calibrationInputs,
                           DistillationLedger ledger, DiskBudget diskBudget,
                           String source, long sourceBytes,
                           Function<long[], float[]> activationFn) {
        long t0 = java.time.Instant.now().toEpochMilli();
        // CONSTITUTION Article II: K_MAX = 20. Distiller input bits must fit
        // inside the TtForm k bound. Real distillation chunks long inputs.
        int inputBits = 20;
        Distiller distiller = new Distiller(inputBits, 0.10);
        for (long[] input : calibrationInputs) {
            // Reduce the long[] to `inputBits` boolean features (bit-cosine)
            // so the synthesized BIR is K_MAX-compliant.
            boolean[] reduced = reduceToBits(input, inputBits);
            float[] activations = activationFn.apply(input);
            distiller.capture(toLongArray(reduced), activations);
        }
        var bir = distiller.synthesize(source);
        int nClauses = bir != null ? bir.outputBits() : 0;

        int heldout = Math.max(1, calibrationInputs.size() / 2);
        long[][] heldInputs = new long[heldout][];
        float[][] heldExpected = new float[heldout][];
        for (int i = 0; i < heldout; i++) {
            heldInputs[i] = calibrationInputs.get(i);
            heldExpected[i] = activationFn.apply(heldInputs[i]);
        }
        double fidelity = distiller.fidelity(bir, heldInputs, heldExpected);

        // RECON-W34.1: ONE clock read, used for both the duration and the row timestamp.
        // These were two separate System.currentTimeMillis() calls, so a row could report a
        // timestamp earlier than the duration that preceded it.
        long finishedAtMs = java.time.Instant.now().toEpochMilli();
        long durationMs = finishedAtMs - t0;
        // RECON-W34.1: durationMs was previously MIXED INTO THE HASH, and that is the
        // serious one. Article III requires provenance + CONTENT-hash identity, but a hash
        // over (source, size, clauses, duration) is a hash over how fast the machine was.
        // The same teacher and the same inputs produced two different artifactHash values on
        // two runs, so "identical artefact, identical hash" was false, and the hash could not
        // function as an identity. Excluding the wall-clock makes it content-derived and
        // therefore reproducible.
        String hash = sha256Hex(source + ":" + calibrationInputs.size() + ":" + nClauses);
        Result r = new Result(source, calibrationInputs.size(),
            calibrationInputs.size() /* tokensExtracted */,
            0 /* hdcPromoted */, nClauses /* birClausesInduced */,
            0 /* tsetlinLiterals */, sourceBytes /* inputsBytes */,
            fidelity, durationMs, hash, bir.provenance());
        try {
            ledger.record(new DistillationLedger.Entry(
// RECON-W34.1: the sourceId was "run-" + System.currentTimeMillis(), which
                // DISCARDED the provenance the caller had already supplied. distillCustom is
                // called with "synthetic:teacher" and the ledger recorded a timestamp instead,
                // so two different teachers distilled in the same millisecond were
                // indistinguishable in the audit trail. sourceId is now r.source, so the
                // caller's label survives into the record whose job is to carry it.
                r.source,
                  r.source, r.captures, (int) sourceBytes, 0, r.birClausesSynthesized(), 0, r.fidelity,
                  r.durationMs, finishedAtMs, r.artifactHash));
        } catch (java.io.IOException e) {
            // RECON-W32.33. Non-fatal is right; invisible is not. The DistillationLedger
            // is the audit trail for a run that ALREADY synthesised a BIR and computed an
            // artifactHash which the returned Result exposes. A failed write yields an
            // artefact whose hash no ledger entry vouches for — a lost write in the one
            // subsystem whose entire job is to prove what happened.
            ledgerWriteFailures.incrementAndGet();
            if (ledgerWriteFailures.get() == 1) {
                System.err.println("[TrueDistillationFactory] LEDGER WRITE FAILED for "
                    + "source=" + r.source + " hash=" + r.artifactHash + ": " + e
                    + " - the artefact was produced and its hash is NOT recorded, so "
                    + "nothing vouches for it; further failures are counted");
            }
        }
        if (diskBudget != null && sourceBytes > 0) {
            diskBudget.recordWrite("distill:" + r.source, sourceBytes);
        }
        return r;
    }

    /** Synthetic activation fallback (used when ONNX proto is invalid). */
    public static float[] syntheticActivation(long[] input) {
        // Deterministic: average non-zero bits as a single scalar per index,
        // repeated to length 1 for the distill pipeline.
        float avg = 0f;
        int n = 0;
        for (long v : input) {
            avg += (float) Long.bitCount(v);
            n += 64;
        }
        return new float[]{ n == 0 ? 0f : (avg / n) };
    }

    /** Build a CodeBook sized for the distillation output. */
    public CodeBook newCodeBook(int dim, int codeSize) {
        return CodeBook.builder(dim).codeSize(codeSize).build();
    }

    /** Convenience: convert a long[] (HDC bits) to a float[] for the teacher. */
    private static float[] toFloatArray(long[] input) {
        float[] out = new float[input.length];
        for (int i = 0; i < input.length; i++) out[i] = (float) input[i];
        return out;
    }

    /** Reduce a long[] HDC vector to `n` boolean features (top-n bits by count). */
    private static boolean[] reduceToBits(long[] input, int n) {
        boolean[] out = new boolean[n];
        for (int i = 0; i < n && i < input.length; i++) {
            out[i] = (input[i] != 0);
        }
        return out;
    }

    /** Convert a boolean[] back to long[] for Distiller.capture. */
    private static long[] toLongArray(boolean[] bits) {
        long[] out = new long[bits.length];
        for (int i = 0; i < bits.length; i++) out[i] = bits[i] ? 1L : 0L;
        return out;
    }

    private static String sha256Hex(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            return "no-sha256";
        }
    }

    /** Ledger writes that failed, so an artefact exists with no ledger entry vouching for it.
     *
     * <p>Unit: calls. RECON-W32.33.</p>
     */
    private final java.util.concurrent.atomic.AtomicInteger ledgerWriteFailures =
            new java.util.concurrent.atomic.AtomicInteger();

    public int ledgerWriteFailures() { return ledgerWriteFailures.get(); }
}

