package io.matrix.research;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;

/**
 * Locates the real BitNet 1.58 2B checkpoint, and is the ONE place that decides whether
 * these tests are allowed to execute.
 *
 * <p><b>RECON-W32.34, operator decision D1 (option A)</b> added the guard that turned 28
 * honest failures into 32 skips, on the grounds that the checkpoint was not present and
 * {@code /tmp} does not survive a reboot. That diagnosis was right and the fix was
 * incomplete: the guard pointed at the one location guaranteed to disappear.
 *
 * <p><b>RECON-W33.1, operator decision BitNet-Download</b> closes that loop. The measured
 * failure is that {@code /tmp/hf_cache} no longer existed at all, so the assumption had
 * silently become permanent and "28 failures became 32 skips" had described a skip that
 * could never become a pass. The checkpoint is now provisioned to a persistent,
 * repo-relative, git-ignored location by {@code scripts/provision-bitnet.sh}, and resolution
 * goes through an override chain instead of a literal.
 *
 * <h2>Why resolution is a chain and not a constant</h2>
 * <p>A hardcoded path has exactly two failure modes and both have already occurred here: a
 * path that is wrong on another machine, and a path that used to be right. The chain tries
 * an explicit override first, then the conventional git-ignored location relative to the
 * repo root, and only then reports absence. CI has no 1.2 GB model, so absence must stay a
 * skip and never become a failure -- a red failure whose cause is the environment is the
 * exact confusion this class exists to prevent.
 *
 * <h2>Truncation counts as absence</h2>
 * <p>A partially downloaded checkpoint is worse than a missing one: it passes an
 * {@code exists} check and then fails deep inside a safetensors reader, where the error
 * blames the reader instead of the download. Presence alone is therefore insufficient. A
 * file below {@link #MIN_CHECKPOINT_BYTES} is reported as not provisioned, with a message
 * naming the provisioning script.
 */
public final class BitNetRealModelFixture {

    /**
     * Repo-relative location of the checkpoint, under the git-ignored {@code data/} tree.
     *
     * <p>Unit: a relative filesystem path. Deliberately relative so a clone at any path
     * resolves it.</p>
     */
    public static final String RELATIVE_MODEL_PATH =
            "data/models/bitnet-checkpoint/model.safetensors";

    /**
     * System property that overrides discovery entirely, for a machine that stores the
     * model elsewhere.
     *
     * <p>Unit: a filesystem path. Example:
     * {@code -Dmatrix.bitnet.model=/mnt/models/bitnet/model.safetensors}.</p>
     */
    public static final String MODEL_PATH_PROPERTY = "matrix.bitnet.model";

    /**
     * Environment-variable equivalent of {@link #MODEL_PATH_PROPERTY}, for runners that
     * cannot pass JVM flags.
     *
     * <p>Unit: a filesystem path. Example:
     * {@code BITNET_MODEL_PATH=/mnt/models/bitnet/model.safetensors}.</p>
     */
    public static final String MODEL_PATH_ENV = "BITNET_MODEL_PATH";

    /**
     * Smallest size accepted as a usable checkpoint: 1.0 GB.
     *
     * <p>Unit: bytes. The published {@code model.safetensors} for this model measures
     * 1,178,623,988 bytes, so this floor sits at roughly 85% of the real payload and still
     * far above any truncation a completed HTTP transfer could leave behind. It is a
     * truncation tripwire, not an integrity proof, and deliberately cheaper than hashing
     * 1.2 GB in each of the eleven test classes. Exact integrity is checked once at
     * provision time by {@code scripts/provision-bitnet.sh}, which writes a sha256
     * sidecar.</p>
     */
    public static final long MIN_CHECKPOINT_BYTES = 1_000_000_000L;

    /**
     * The legacy location these tests used before RECON-W33.1.
     *
     * <p>Unit: a filesystem path. Retained so a status report can show the old location is
     * genuinely gone rather than quietly repointed. Never used for resolution.</p>
     */
    public static final String LEGACY_MODEL_PATH =
            "/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/snapshots/"
            + "04c3b9ad9361b824064a1f25ea60a8be9599b127/model.safetensors";

    private BitNetRealModelFixture() {
    }

    /**
     * The repo root, derived the way sibling tests in this module derive it.
     *
     * <p>Gradle runs a module's tests with the module directory as the working directory,
     * so the repo root is one level up. Falls back to the working directory when it has no
     * parent, so this yields a valid path instead of throwing during class
     * initialisation.</p>
     *
     * @return the repo root, or the working directory if that has no parent
     */
    private static Path repoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path parent = cwd.getParent();
        return parent == null ? cwd : parent;
    }

    /**
     * The override supplied by system property or environment, if any.
     *
     * <p>The property wins over the environment. A blank value counts as absent, so an
     * empty property cannot accidentally resolve to the current directory.</p>
     *
     * @return the override path, or empty when neither source supplies one
     */
    private static Optional<Path> overridePath() {
        String fromProperty = System.getProperty(MODEL_PATH_PROPERTY, "");
        if (!fromProperty.isBlank()) {
            return Optional.of(Path.of(fromProperty));
        }
        String fromEnv = System.getenv(MODEL_PATH_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(Path.of(fromEnv));
        }
        return Optional.empty();
    }

    /**
     * Every location the checkpoint might legitimately occupy, in priority order.
     *
     * @return candidate paths; never empty, because the repo-relative location always is one
     */
    public static List<Path> candidatePaths() {
        Path relative = repoRoot().resolve(RELATIVE_MODEL_PATH);
        return overridePath().map(o -> List.of(o, relative)).orElseGet(() -> List.of(relative));
    }

    /**
     * Whether a path looks like a complete checkpoint: a regular file of plausible size.
     *
     * @param candidate path to inspect
     * @return true when the file exists and is at least {@link #MIN_CHECKPOINT_BYTES}
     */
    private static boolean isUsable(Path candidate) {
        if (!Files.isRegularFile(candidate)) {
            return false;
        }
        try {
            return Files.size(candidate) >= MIN_CHECKPOINT_BYTES;
        } catch (IOException unreadable) {
            return false;
        }
    }

    /**
     * The first candidate that is present and large enough to be a real checkpoint.
     *
     * <p>Size is checked alongside existence so a truncated download is reported as absent
     * rather than failing somewhere unrelated.</p>
     *
     * @return the usable checkpoint, or empty when none is provisioned
     */
    public static Optional<Path> locate() {
        return candidatePaths().stream().filter(BitNetRealModelFixture::isUsable).findFirst();
    }

    /**
     * The checkpoint as a {@link Path}, whether or not it is present.
     *
     * <p>When the checkpoint is missing this returns the preferred candidate anyway, so an
     * error message names the path a user should create instead of reporting null.</p>
     *
     * @return the preferred checkpoint path, existing or not
     */
    public static Path modelPath() {
        return locate().orElseGet(() -> candidatePaths().get(candidatePaths().size() - 1));
    }

    /**
     * The resolved checkpoint path as a string, for the eleven classes that alias this as a
     * constant.
     *
     * <p>Unit: a filesystem path. Resolved once at class initialisation via
     * {@link #modelPath()}; the working directory does not change during a test run, so one
     * resolution is equivalent to resolving per call.</p>
     */
    public static final String MODEL_PATH = modelPath().toString();

    /**
     * True when a usable real checkpoint is present.
     *
     * <p>Unit: a boolean. Exposed so a status page or report can state the fact instead of
     * inferring it from a test count.</p>
     *
     * @return true only when a candidate is present and meets {@link #MIN_CHECKPOINT_BYTES}
     */
    public static boolean available() {
        return locate().isPresent();
    }

    /**
     * Skip the calling test unless a usable real checkpoint is present.
     *
     * <p>JUnit reports an unmet assumption as <b>skipped</b>, which is the correct word: the
     * test did not run. It is deliberately not an assertion, because asserting
     * {@code exists(...)} keeps producing a red failure whose cause is the environment
     * rather than the code. The message names the provisioning command, so a reader can
     * convert the skip into a pass instead of filing it as a defect.</p>
     */
    public static void assumeAvailable() {
        Assumptions.assumeTrue(
                available(),
                "ENVIRONMENT-BLOCKED, NOT PASSING: no usable BitNet 1.58 2B safetensors "
                    + "checkpoint found. Looked at " + candidatePaths()
                    + " (override with -D" + MODEL_PATH_PROPERTY + " or " + MODEL_PATH_ENV
                    + "). A file under " + MIN_CHECKPOINT_BYTES
                    + " bytes counts as absent, because a truncated download would otherwise "
                    + "fail inside the safetensors reader instead of here. Provision it with: "
                    + "bash scripts/provision-bitnet.sh");
    }
}
