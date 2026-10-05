package io.matrix.research;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;

/**
 * The real BitNet 1.58 2B checkpoint these tests need, and the ONE place that decides
 * whether it is available.
 *
 * <p><b>RECON-W32.34, operator decision D1 (option A).</b> Eleven test classes read a
 * 1.1 GB safetensors checkpoint from a hardcoded {@code /tmp} path. That path did not
 * exist, {@code /tmp} does not survive a reboot, and nothing in the build, any script or
 * any CI step provisions it. Every one of those tests therefore failed at the file check
 * before reaching a single assertion — which is why this suite reported 28 failures for
 * weeks with no cause attributed to them.</p>
 *
 * <h2>What this guard does and, more importantly, what it does not do</h2>
 * <p>It does <b>not</b> make those tests pass. It converts
 * {@code FAILURE -> never executed}, which is the honest description of what has actually
 * been happening all along.</p>
 *
 * <p><b>BitNet has never been executed.</b> No forward pass, no prefill, no KV-cache, no
 * sampling. It is <em>untested</em>, not <em>failing for a known reason</em>, and any
 * release note, status report or dashboard that describes these classes as passing is
 * wrong. Twenty sibling test classes already carried an equivalent guard; these eleven did
 * not, and that asymmetry is the whole defect.</p>
 *
 * <h2>The debt this defers, and option B of D1</h2>
 * <p>Option B is to provision the weights and run the tests for real. That is the only step
 * that validates anything, and it needs roughly 1.1 GB downloaded and ~17 GB on disk for a
 * 2B model in fp32. When it happens, the expectations underneath may reveal genuine
 * failures that this guard is currently hiding along with the environmental ones — which
 * is the honest cost of doing it late rather than early.</p>
 */
public final class BitNetRealModelFixture {

    /**
     * Where the checkpoint lives.
     *
     * <p>Unit: a filesystem path. {@code /tmp} is a poor home for a multi-gigabyte
     * artefact — it is wiped on reboot on most systems — and the pinned snapshot
     * subdirectory is what makes the path reproducible, so both are kept verbatim rather
     * than "improved" into a path that resolves to nothing.</p>
     */
    public static final String MODEL_PATH =
            "/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/snapshots/"
            + "04c3b9ad9361b824064a1f25ea60a8be9599b127/model.safetensors";

    private BitNetRealModelFixture() {
    }

    /**
     * The checkpoint as a {@link Path}. Exposed so a test can assert about the file
     * itself rather than re-deriving the string.
     *
     * @return the checkpoint path
     */
    public static Path modelPath() {
        return Path.of(MODEL_PATH);
    }

    /**
     * True when the real checkpoint is present.
     *
     * <p>Unit: a boolean. Exposed so a status page or a report can state the fact rather
     * than infer it from a test count.</p>
     *
     * @return true only if the file exists on disk right now
     */
    public static boolean available() {
        return Files.exists(modelPath());
    }

    /**
     * Skip the calling test unless the real checkpoint is present.
     *
     * <p>JUnit reports an unmet assumption as <b>skipped</b>, which is the correct word: the
     * test did not run. It is deliberately not an assertion, because asserting
     * {@code Files.exists(...)} would keep producing a red failure whose cause is the
     * environment rather than the code — which is the confusion this class exists to end.</p>
     */
    public static void assumeAvailable() {
        Assumptions.assumeTrue(available(),
                "ENVIRONMENT-BLOCKED, NOT PASSING: the real BitNet 1.58 2B safetensors "
                    + "checkpoint is not present at " + MODEL_PATH + ". These tests have "
                    + "never executed, so BitNet is UNTESTED. Provision the weights "
                    + "(operator decision D1 option B) to validate it.");
    }
}
