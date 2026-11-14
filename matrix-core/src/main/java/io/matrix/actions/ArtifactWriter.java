package io.matrix.actions;

import com.fasterxml.jackson.core.JsonParser;
import io.matrix.auditor.ArticleIPermitted;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * B3 — the bounded artifact writer: the one new side effect of Evidence-to-Action.
 *
 * <p><b>What it may do.</b> Write exactly one file, whose name is a plain file name (no
 * directory part) inside one explicitly authorized root directory, whose bytes the caller
 * supplies. Nothing else: it does not read the root's contents beyond its own index, does
 * not run a command, does not open a connection, does not expand its own permissions.</p>
 *
 * <p><b>How the boundary is enforced.</b> The target is resolved against the root's REAL
 * path ({@link Path#toRealPath}) and refused when the resolved target is not under it; a
 * name containing a separator, a {@code ..}, or a leading dot is refused before any I/O;
 * a symlink at the target is resolved and its real path is checked the same way. The
 * content is bounded ({@link #MAX_ARTIFACT_BYTES}) and validated by type before anything
 * is staged. The staged temporary file and the index are opened with
 * {@link LinkOption#NOFOLLOW_LINKS} and created with {@code CREATE_NEW}: a symbolic link
 * planted at either deterministic path is refused, never followed, so the write cannot be
 * redirected outside the root.</p>
 *
 * <p><b>How a write is made recoverable.</b> The bytes go to a staged temporary file in the
 * same directory, are forced to disk, and are moved onto the final name with
 * {@link StandardCopyOption#ATOMIC_MOVE}. The action appends its own records to an
 * append-only NDJSON index in the root ({@value #INDEX_FILE}): {@code STAGED} before the
 * move, {@code COMMITTED} after it. A crash therefore leaves one of: the old file, the
 * staged file without a commit, or the final file with a commit record — and
 * {@link #recover(String)} resolves each of those dispositions explicitly. One window is
 * documented rather than eliminated: a temporary file created before its {@code STAGED}
 * record was written is not derivable from the index, so recovery cannot remove it; the
 * intended retry overwrites it (it is the identity's own reserved path). A crash
 * <b>between the move and the COMMITTED append</b> is resolved as a commit too: a
 * {@code STAGED} record whose final file exists with the recorded digest <i>is</i> a
 * committed artifact, and the retry reports it instead of writing again.</p>
 *
 * <p><b>What the guarantee is.</b> For one action identity, at most one committed file
 * exists, and a retry can only reproduce it: a retry with the same identity and the same
 * content re-hashes the final file and returns the recorded digest without a second write
 * when the file still matches; when the file is missing or its bytes changed, the retry
 * rewrites it and records the reconciliation. The index is append-only and a torn trailing
 * line (the expected state after a crash mid-append) is skipped on read, never repaired
 * automatically. Two concurrent processes writing the same root are not supported; the
 * guarantee is per identity within one process.</p>
 */
public final class ArtifactWriter {

    /** The largest artifact the writer will stage, in bytes. */
    public static final int MAX_ARTIFACT_BYTES = 256 * 1024;

    /** The most committed artifacts one root may hold. */
    public static final int MAX_ARTIFACTS = 1000;

    /** The append-only index of every staged and committed write, in the root. */
    public static final String INDEX_FILE = ".artifact-index.ndjson";

    /** The dispositions of one write attempt. */
    public enum Disposition {
        WRITTEN, REPLAYED, RECONCILED_WRITTEN,
        REFUSED_OUTSIDE_ROOT, REFUSED_TRAVERSAL, REFUSED_SYMLINK_ESCAPE,
        REFUSED_UNSUPPORTED_TYPE, REFUSED_TOO_LARGE, REFUSED_INVALID_CONTENT,
        REFUSED_EMPTY_NAME, REFUSED_IDENTITY, REFUSED_WRITE_FAILED, REFUSED_BUDGET,
        WRITTEN_TRACE_INCOMPLETE
    }

    /** One write's outcome. */
    public record Result(Disposition disposition, String actionId, String fileName,
                         Path path, String sha256, String reason, String tracePath) {
        public boolean written() {
            return disposition == Disposition.WRITTEN || disposition == Disposition.REPLAYED
                    || disposition == Disposition.RECONCILED_WRITTEN
                    || disposition == Disposition.WRITTEN_TRACE_INCOMPLETE;
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path root;

    /**
     * A writer over one authorized root.
     *
     * @param root the authorized output directory; it must already exist, because the
     *     permission to write there is the directory the caller created and named
     */
    public ArtifactWriter(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("the authorized root is required");
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("the authorized root must be an existing directory: "
                    + root);
        }
        try {
            this.root = root.toRealPath();
        } catch (IOException failure) {
            throw new UncheckedIOException("the authorized root cannot be resolved", failure);
        }
    }

    /** The authorized root, as the real path. */
    public Path root() {
        return root;
    }

    /** The index's path. */
    public Path index() {
        return root.resolve(INDEX_FILE);
    }

    /**
     * Writes one artifact, or explains why it refuses.
     *
     * @param actionId the stable identity of the action (see {@link VersionedContract})
     * @param fileName a plain file name with a supported extension
     * @param content the artifact's bytes
     * @return the outcome; {@code written()} is false for every refusal
     */
    public Result write(String actionId, String fileName, byte[] content) {
        if (actionId == null || actionId.isBlank()) {
            return refuse(Disposition.REFUSED_IDENTITY, actionId, fileName, "the action identity is required");
        }
        if (content == null) {
            return refuse(Disposition.REFUSED_INVALID_CONTENT, actionId, fileName, "the content is absent");
        }
        if (content.length > MAX_ARTIFACT_BYTES) {
            return refuse(Disposition.REFUSED_TOO_LARGE, actionId, fileName,
                    "the artifact is " + content.length + " bytes, above the bound of "
                            + MAX_ARTIFACT_BYTES);
        }
        if (fileName == null || fileName.isBlank()) {
            return refuse(Disposition.REFUSED_EMPTY_NAME, actionId, fileName, "the file name is required");
        }
        if (fileName.chars().anyMatch(c -> c < 0x20 || c == 0x7f) || fileName.contains("\u0000")
                || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")
                || fileName.startsWith(".") || fileName.equals(INDEX_FILE)) {
            return refuse(Disposition.REFUSED_TRAVERSAL, actionId, fileName,
                    "the name must be a plain file name without a directory part, a parent "
                            + "reference or a reserved prefix");
        }
        boolean markdown = fileName.endsWith(".md");
        boolean json = fileName.endsWith(".json");
        if (!markdown && !json) {
            return refuse(Disposition.REFUSED_UNSUPPORTED_TYPE, actionId, fileName,
                    "the supported artifact types are .md and .json");
        }
        String invalid = validateContent(fileName, content);
        if (invalid != null) {
            return refuse(Disposition.REFUSED_INVALID_CONTENT, actionId, fileName, invalid);
        }
        if (Files.isSymbolicLink(index())) {
            return refuse(Disposition.REFUSED_SYMLINK_ESCAPE, actionId, fileName,
                    "the index is a symbolic link; the writer never follows one");
        }
        if (hardLinked(index())) {
            return refuse(Disposition.REFUSED_SYMLINK_ESCAPE, actionId, fileName,
                    "the index is a hard link to another file; the writer never appends through one");
        }

        Path target = root.resolve(fileName);
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent == null || !Files.isDirectory(parent)
                    || !parent.toRealPath().equals(root)) {
                return refuse(Disposition.REFUSED_OUTSIDE_ROOT, actionId, fileName,
                        "the target is not inside the authorized root");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) {
                Path resolved = target.toRealPath();
                if (!resolved.startsWith(root)) {
                    return refuse(Disposition.REFUSED_SYMLINK_ESCAPE, actionId, fileName,
                            "a symbolic link at the target resolves outside the root: " + resolved);
                }
            }
        } catch (IOException failure) {
            return refuse(Disposition.REFUSED_OUTSIDE_ROOT, actionId, fileName,
                    "the target's location cannot be resolved: " + failure.getMessage());
        }

        String digest = digest(content);
        try {
            // the name belongs to the identity that committed it — a COMMITTED record OR the
            // crash window's STAGED record whose final file already carries its digest
            Optional<Record> boundToAnother = boundToAnotherIdentity(fileName, actionId);
            if (boundToAnother.isPresent()) {
                return refuse(Disposition.REFUSED_IDENTITY, actionId, fileName,
                        "the artifact name is already committed by the action identity "
                                + boundToAnother.get().actionId() + "; one artifact has one identity");
            }
            Optional<Record> commit = effectiveCommit(actionId);
            if (commit.isPresent()) {
                if (!commit.get().fileName().equals(fileName)) {
                    // an identity names ONE artifact: a record naming another file must not
                    // redirect this request, and a forged one must not suppress it silently
                    return refuse(Disposition.REFUSED_IDENTITY, actionId, fileName,
                            "the action identity is committed to the artifact " + commit.get()
                                    .fileName() + ", not " + fileName);
                }
                if (!commit.get().sha256().equals(digest)) {
                    return refuse(Disposition.REFUSED_IDENTITY, actionId, fileName,
                            "the action identity was already committed with different content; an "
                                    + "identity names one artifact, and a second content under it is "
                                    + "a new action");
                }
                Path finalPath = root.resolve(commit.get().fileName());
                if (Files.isRegularFile(finalPath)
                        && digest(finalPath).equals(commit.get().sha256())) {
                    return new Result(Disposition.REPLAYED, actionId, fileName, finalPath, digest,
                            "the same action identity and content were already committed, and the "
                                    + "artifact still matches its recorded digest", index().toString());
                }
                // the commit record exists and the artifact is missing or changed: reconcile
                return stagedWrite(actionId, fileName, content, digest, Disposition.RECONCILED_WRITTEN);
            }
            long committedFiles = readIndex().stream()
                    .filter(r -> r.status().equals("COMMITTED"))
                    .map(Record::fileName).distinct().count();
            if (committedFiles >= MAX_ARTIFACTS) {
                return refuse(Disposition.REFUSED_BUDGET, actionId, fileName,
                        "the root holds " + committedFiles + " committed artifacts, at "
                                + "the budget of " + MAX_ARTIFACTS);
            }
            return stagedWrite(actionId, fileName, content, digest, Disposition.WRITTEN);
        } catch (IOException failure) {
            return new Result(Disposition.REFUSED_WRITE_FAILED, actionId, fileName, target,
                    digest, "the write failed: " + failure.getMessage(), index().toString());
        }
    }

    /** The staged-then-committed write. */
    @ArticleIPermitted("the record timestamp is journal metadata written verbatim into the "
            + "STAGED and COMMITTED lines; it decides nothing — no branch, ordering, retry, "
            + "timeout or verdict reads it (the adapter writes exactly what the caller supplied)")
    private Result stagedWrite(String actionId, String fileName, byte[] content, String digest,
                               Disposition success) throws IOException {
        Path staged = root.resolve(stagedName(fileName, actionId));
        if (Files.isSymbolicLink(staged)) {
            // never follow a planted link: a symbolic link at the deterministic staged
            // path would redirect the bytes outside the root
            return refuse(Disposition.REFUSED_SYMLINK_ESCAPE, actionId, fileName,
                    "a symbolic link is planted at the staged path " + staged.getFileName());
        }
        Files.deleteIfExists(staged);
        try {
            Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            try (FileChannel channel = FileChannel.open(staged, options)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
        } catch (IOException failure) {
            // nothing was moved: remove the partial staged file so a refusal leaves no residue
            Files.deleteIfExists(staged);
            throw failure;
        }
        try {
            appendRecord(new Record(actionId, fileName, digest, "STAGED", content.length,
                    Instant.now().toString()));
        } catch (IOException failure) {
            Files.deleteIfExists(staged);
            throw failure;
        }
        Path target = root.resolve(fileName);
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            // the move failed: the staged file must not be left behind
            Files.deleteIfExists(staged);
            throw failure;
        }
        try {
            appendRecord(new Record(actionId, fileName, digest, "COMMITTED", content.length,
                    Instant.now().toString()));
        } catch (IOException incomplete) {
            // the artifact IS committed; only its trace line is missing. The retry reads
            // the STAGED record and the file's digest and reports it (effectiveCommit), so
            // the state is honest and recoverable — the caller must not be told "refused".
            return new Result(Disposition.WRITTEN_TRACE_INCOMPLETE, actionId, fileName, target,
                    digest, "the artifact was committed but its trace line could not be "
                    + "appended: " + incomplete.getMessage(), index().toString());
        }
        return new Result(success, actionId, fileName, target, digest, null, index().toString());
    }

    /**
     * The commit of an identity, including the crash window between the move and the
     * COMMITTED append: a {@code STAGED} record whose final file exists with the recorded
     * digest is treated as committed.
     */
    public Optional<Record> effectiveCommit(String actionId) throws IOException {
        List<Record> records = readIndex();
        Optional<Record> committed = records.stream()
                .filter(r -> r.actionId().equals(actionId) && r.status().equals("COMMITTED"))
                .reduce((first, second) -> second);
        if (committed.isPresent()) {
            return committed;
        }
        Optional<Record> staged = records.stream()
                .filter(r -> r.actionId().equals(actionId) && r.status().equals("STAGED"))
                .reduce((first, second) -> second);
        if (staged.isPresent()) {
            Path finalPath = root.resolve(staged.get().fileName());
            if (Files.isRegularFile(finalPath) && digest(finalPath).equals(staged.get().sha256())) {
                return staged;
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the state a crash may have left for one action identity.
     *
     * @param actionId the identity whose state is asked for
     * @return a description of the state and the disposition the recovery resolved
     */
    public Map<String, Object> recover(String actionId) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (Files.isSymbolicLink(index()) || hardLinked(index())) {
            state.put("actionId", actionId);
            state.put("disposition", "INDEX_REFUSED");
            state.put("note", "the index is a link; the writer never follows one, so the "
                    + "crash state cannot be read through it");
            return state;
        }
        try {
            List<Record> records = readIndex();
            boolean staged = records.stream().anyMatch(r -> r.actionId().equals(actionId)
                    && r.status().equals("STAGED"));
            Optional<Record> committed = records.stream()
                    .filter(r -> r.actionId().equals(actionId) && r.status().equals("COMMITTED"))
                    .reduce((first, second) -> second);
            // the exact staged names this identity can have left behind, derived from its
            // records — never a substring match, so a committed artifact is never deleted
            List<String> stagedNames = new ArrayList<>();
            for (Record record : records) {
                if (record.actionId().equals(actionId) && record.status().equals("STAGED")) {
                    stagedNames.add(stagedName(record.fileName(), actionId));
                }
            }
            boolean stagedRemoved = false;
            for (String name : stagedNames) {
                Path candidate = root.resolve(name);
                if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(candidate)) {
                    Files.deleteIfExists(candidate);
                    stagedRemoved = true;
                }
            }
            state.put("actionId", actionId);
            state.put("hadStaged", staged);
            state.put("hadCommitted", committed.isPresent());
            state.put("stagedRemoved", stagedRemoved);
            Optional<Record> effective = effectiveCommit(actionId);
            if (effective.isPresent()) {
                Path finalPath = root.resolve(effective.get().fileName());
                boolean present = Files.isRegularFile(finalPath);
                boolean matches = present && digest(finalPath).equals(effective.get().sha256());
                state.put("finalPresent", present);
                state.put("finalMatchesDigest", matches);
                state.put("disposition", matches ? "COMMITTED" : "COMMITTED_BUT_CONTENT_DIFFERS");
                state.put("recoversOnRetry", !matches);
            } else {
                state.put("disposition", "NO_COMMIT");
                state.put("recoversOnRetry", true);
            }
        } catch (IOException failure) {
            state.put("disposition", "UNREADABLE");
            state.put("error", failure.getMessage());
        }
        return state;
    }

    /** The committed record for an identity, if any. */
    public Optional<Record> committedRecord(String actionId) throws IOException {
        return readIndex().stream()
                .filter(r -> r.actionId().equals(actionId) && r.status().equals("COMMITTED"))
                .reduce((first, second) -> second);
    }

    /**
     * Every readable record of the index, oldest first. A torn line — the state a crash
     * mid-append leaves — is skipped, not repaired: the index is evidence, not state to
     * rewrite.
     */
    public List<Record> readIndex() throws IOException {
        List<Record> records = new ArrayList<>();
        if (!Files.isRegularFile(index(), LinkOption.NOFOLLOW_LINKS)) {
            return records;
        }
        for (String line : Files.readAllLines(index(), StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            Record record;
            try {
                record = JSON.readValue(line, Record.class);
            } catch (IOException torn) {
                // a partial append: skip it; the write that appends next is unaffected
                continue;
            }
            if (validRecord(record)) {
                records.add(record);
            }
        }
        return records;
    }

    private void appendRecord(Record record) throws IOException {
        String line = JSON.writeValueAsString(record) + "\n";
        ensureTrailingNewline();
        Files.write(index(), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    /** A torn trailing line (no newline) must not swallow the next record. */
    private void ensureTrailingNewline() throws IOException {
        if (!Files.isRegularFile(index(), LinkOption.NOFOLLOW_LINKS) || Files.size(index()) == 0) {
            return;
        }
        try (var channel = Files.newByteChannel(index(), StandardOpenOption.READ)) {
            channel.position(Files.size(index()) - 1);
            ByteBuffer last = ByteBuffer.allocate(1);
            channel.read(last);
            if (last.get(0) != '\n') {
                Files.write(index(), new byte[] {'\n'}, StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            }
        }
    }

    private static boolean hardLinked(Path path) {
        try {
            Object links = Files.getAttribute(path, "unix:nlink");
            return links instanceof Integer count && count > 1;
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException notUnix) {
            return false;
        }
    }

    /** The identity's own temp name: the sanitized key plus a hash of the raw key. */
    static String stagedName(String fileName, String actionId) {
        return "." + fileName + ".tmp-" + sanitize(actionId) + "-" + shortHash(actionId);
    }

    private static String shortHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 10);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is unavailable", absent);
        }
    }

    /** The other identity that already holds this artifact name, if any. */
    private Optional<Record> boundToAnotherIdentity(String fileName, String actionId) throws IOException {
        for (Record record : readIndex()) {
            if (!record.fileName().equals(fileName) || record.actionId().equals(actionId)) {
                continue;
            }
            if (record.status().equals("COMMITTED")) {
                return Optional.of(record);
            }
            // the crash window: a STAGED record whose final file already carries its digest
            Path finalPath = root.resolve(record.fileName());
            if (Files.isRegularFile(finalPath) && digest(finalPath).equals(record.sha256())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    /** A record is evidence only if it names a plain artifact and a real digest. */
    private static boolean validRecord(Record record) {
        return record != null
                && record.actionId() != null && !record.actionId().isBlank()
                && record.fileName() != null && !record.fileName().isBlank()
                && !record.fileName().contains("/") && !record.fileName().contains("\\")
                && !record.fileName().contains("..") && !record.fileName().startsWith(".")
                && !record.fileName().equals(INDEX_FILE)
                && (record.fileName().endsWith(".md") || record.fileName().endsWith(".json"))
                && record.sha256() != null && record.sha256().matches("[0-9a-f]{64}")
                && ("STAGED".equals(record.status()) || "COMMITTED".equals(record.status()))
                && record.bytes() > 0;
    }

    private String validateContent(String fileName, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return "the content is empty";
        }
        if (fileName.endsWith(".json")) {
            try (JsonParser parser = JSON.getFactory().createParser(content)) {
                var node = parser.readValueAsTree();
                if (node == null || !(node.isObject() || node.isArray())) {
                    return "the .json artifact must be one JSON object or array";
                }
                if (parser.nextToken() != null) {
                    return "the .json artifact carries content after its first JSON value";
                }
            } catch (IOException unreadable) {
                return "the .json artifact does not parse: " + unreadable.getMessage();
            }
        }
        if (fileName.endsWith(".md") && !text.startsWith("#")) {
            return "the .md artifact must start with a Markdown heading";
        }
        return null;
    }

    private Result refuse(Disposition disposition, String actionId, String fileName, String reason) {
        return new Result(disposition, actionId, fileName, null, null, reason, index().toString());
    }

    private static String digest(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is unavailable", absent);
        }
    }

    private static String digest(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is unavailable", absent);
        }
    }

    private static String sanitize(String actionId) {
        return actionId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** One line of the append-only index. */
    public record Record(String actionId, String fileName, String sha256, String status,
                         int bytes, String timestamp) {
    }
}
