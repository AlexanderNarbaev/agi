package io.matrix.actions;

import io.matrix.security.NetworkExposure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * CONCEPT-TO-ACTION — the bounded artifact action, offered to the serving layer as a service.
 *
 * <p>This class decides <b>whether</b> the one side effect may run and <b>with what identity</b>.
 * It does not write anything itself: the writer does, and the writer's own rules (path
 * confinement, digest verification, one identity per artifact) stay where they are.</p>
 *
 * <p><b>The gate is closed by default.</b> The feature is off unless the operator sets
 * {@value #ENABLED_PROPERTY} to true, and even then it runs only while the process is in
 * {@link NetworkExposure.Mode#LOCAL_ONLY}. A network-exposed process refuses this action even
 * when the general mutation filter has accepted the caller's credential: the general filter
 * answers "may this caller change the state of the process", and this action changes the
 * state of the <i>file system</i>. Enabling it for a network-facing deployment is a separate
 * decision with a separate threat review, and this class does not make it.</p>
 *
 * <p><b>The root is the server's, not the caller's.</b> The authorized output directory comes
 * from the configuration and nowhere else: a request cannot name a path, and there is no
 * request field that could become one. The root is checked before every use — it must exist,
 * be a real directory, not a symbolic link, and not writable by other users — and a root that
 * fails the check is a refusal, not a fallback to some other directory.</p>
 *
 * <p><b>The identity is the server's, too.</b> The caller may offer an idempotency key; the
 * action identity is derived from the workspace, that key (or the question when there is
 * none) and the version of the action contract. A repeated request therefore names the same
 * action, and the writer's rule — one identity, one artifact — turns the repeat into a replay
 * instead of a second file.</p>
 */
@ApplicationScoped
public class ArtifactActionService {

    /** The configuration key that switches the side effect on. */
    public static final String ENABLED_PROPERTY = "matrix.actions.artifact.enabled";

    /** The configuration key that names the authorized output root. */
    public static final String ROOT_PROPERTY = "matrix.actions.artifact.root";

    /** The longest question the service accepts. */
    public static final int MAX_QUESTION_LENGTH = 2000;

    /** The longest idempotency key the service accepts. */
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    /** The gate's answer: whether the action may run, and why not. */
    public record Gate(boolean allowed, String code, String detail) {
        /** The gate is open. */
        public static Gate open() {
            return new Gate(true, null, null);
        }

        /** The gate is closed, with the reason. */
        public static Gate closed(String code, String detail) {
            return new Gate(false, code, detail);
        }
    }

    private final boolean enabled;
    private final String configuredRoot;

    /**
     * The writer over the authorized root.
     *
     * <p>It is created once and kept: the writer holds the identity of the root it was made
     * for, and that identity is what lets it notice a root that was replaced under it. A
     * writer created per request would compare the root with itself and notice nothing.</p>
     */
    private volatile ArtifactWriter writer;

    /**
     * The service, configured by the application.
     *
     * <p>The root is injected as an {@link Optional}: a configuration key that has no value
     * is not an empty string, it is an absent key, and the difference matters — an empty
     * root would name the current directory.</p>
     *
     * @param enabled whether the operator switched the side effect on
     * @param configuredRoot the authorized output root, if one is configured
     */
    @Inject
    public ArtifactActionService(
            @ConfigProperty(name = ENABLED_PROPERTY, defaultValue = "false") boolean enabled,
            @ConfigProperty(name = ROOT_PROPERTY) java.util.Optional<String> configuredRoot) {
        this(enabled, configuredRoot.orElse(""));
    }

    /**
     * The service over a chosen root, for the tests and the tools.
     *
     * @param enabled whether the side effect may run
     * @param configuredRoot the authorized output root
     */
    public ArtifactActionService(boolean enabled, String configuredRoot) {
        this.enabled = enabled;
        this.configuredRoot = configuredRoot == null ? "" : configuredRoot.strip();
    }

    /** Whether the operator has switched the feature on. */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Whether the bounded action may run in this process.
     *
     * @return the gate
     */
    public Gate gate() {
        if (!enabled) {
            return Gate.closed("feature_disabled",
                    "the bounded artifact action is off; set " + ENABLED_PROPERTY + "=true to "
                            + "switch it on");
        }
        NetworkExposure exposure = NetworkExposure.current();
        if (exposure.isNetworkExposed()) {
            return Gate.closed("local_only_action",
                    "this action is authorized in a local-only process only; the process is "
                            + "reachable from other hosts, and a side effect on the file system "
                            + "needs a separate decision and a threat review");
        }
        String host = io.matrix.security.StartupGuard.configuredHost();
        if (!isLoopback(host)) {
            // the mode is a DECLARATION; the bind address is the FACT. A process that
            // declares LOCAL_ONLY and binds every interface is reachable from other
            // hosts, and the declaration must not be what decides the side effect.
            return Gate.closed("local_only_action",
                    "this action is authorized in a local-only process only; the process binds "
                            + host + ", which is not the loopback interface");
        }
        String problem = rootProblem();
        if (problem != null) {
            return Gate.closed("output_root_unavailable", problem);
        }
        return Gate.open();
    }

    /**
     * The writer over the authorized root.
     *
     * @return the writer
     * @throws IllegalStateException when the gate is closed or the root cannot be opened
     */
    public ArtifactWriter writer() {
        Gate gate = gate();
        if (!gate.allowed()) {
            throw new IllegalStateException(gate.code() + ": " + gate.detail());
        }
        ArtifactWriter current = writer;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (writer == null) {
                Path root = Paths.get(configuredRoot);
                try {
                    writer = new ArtifactWriter(root);
                } catch (UncheckedIOException | IllegalArgumentException failure) {
                    throw new IllegalStateException("output_root_unavailable: the authorized root "
                            + "cannot be opened: " + failure.getMessage(), failure);
                }
            }
            return writer;
        }
    }

    /**
     * The action identity of one request.
     *
     * <p>The client never chooses it. The server binds the workspace, the request's subject
     * (the idempotency key when the client offered one, otherwise the question) and the
     * version of the action contract into one digest. The same subject therefore names the
     * same action, and the writer turns a repeated request into a replay rather than a second
     * file.</p>
     *
     * @param workspaceId the workspace the request addressed
     * @param idempotencyKey the client's idempotency key, or null
     * @param question the question the request asked
     * @return the identity
     */
    public String identity(String workspaceId, String idempotencyKey, String question) {
        String subject = idempotencyKey == null || idempotencyKey.isBlank() ? question : idempotencyKey;
        // the workspace's length is part of the material, so two different pairs cannot
        // produce the same string by accident
        String material = workspaceId.length() + ":" + workspaceId + subject;
        return "c2a.report.v" + ArtifactAction.VERSION + "."
                + digest(material).substring(0, 24);
    }

    /**
     * The report name a request gets when it does not ask for one.
     *
     * <p>The name is derived from the identity, so the same request always addresses the same
     * file, and a repeated request is a replay.</p>
     *
     * @param identity the action identity
     * @return a plain file name
     */
    public String defaultReportName(String identity) {
        return "report-" + identity.substring(identity.length() - 12) + ".md";
    }

    /**
     * Why the configured root is not usable, or null when it is.
     *
     * <p>The root must exist, be a real directory, not be a symbolic link, and not be writable
     * by other users: a root other people can write to is a root the writer's guarantees do
     * not describe.</p>
     */
    private String rootProblem() {
        if (configuredRoot.isBlank()) {
            return "no output root is configured; set " + ROOT_PROPERTY + " to the directory the "
                    + "reports may be written to";
        }
        Path root = Paths.get(configuredRoot);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return "the configured output root does not exist";
        }
        if (Files.isSymbolicLink(root)) {
            return "the configured output root is a symbolic link";
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return "the configured output root is not a directory";
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(root);
            if (permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                return "the configured output root is writable by other users";
            }
        } catch (IOException | UnsupportedOperationException notPosix) {
            // a file system without POSIX permissions cannot say; the other checks still hold
        }
        return null;
    }

    /**
     * Whether a bind address is the loopback interface.
     *
     * @param host the address the process binds
     * @return whether the process is reachable only from this host
     */
    private static boolean isLoopback(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String address = host.strip().toLowerCase(java.util.Locale.ROOT);
        return NetworkExposure.LOOPBACK.equals(address) || "localhost".equals(address)
                || "::1".equals(address) || "[::1]".equals(address);
    }

    /**
     * Removes the server's filesystem paths from a message that leaves the process.
     *
     * <p>A refusal names the file system it refused to write to, and that path is the
     * server's business: it tells a caller where the server's directories are. The route
     * runs every detail it returns through this method, so no response body carries an
     * absolute path.</p>
     *
     * @param message the message
     * @return the message without the server's paths
     */
    public String redact(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String redacted = message;
        if (!configuredRoot.isBlank()) {
            redacted = redacted.replace(configuredRoot, "<output-root>");
        }
        return ABSOLUTE_PATH.matcher(redacted).replaceAll("<path>");
    }

    /** An absolute path, as a token of the message it appears in. */
    private static final java.util.regex.Pattern ABSOLUTE_PATH =
            java.util.regex.Pattern.compile("(?<![\\w:/])/(?:[\\w.+@%\\-]+/)*[\\w.+@%\\-]+");

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is unavailable", absent);
        }
    }
}
