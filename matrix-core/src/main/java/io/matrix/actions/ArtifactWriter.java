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
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
 * automatically.</p>
 *
 * <p><b>Who may write a root at one time.</b> A root has exactly one writer at a time. The
 * writer takes a <i>lease</i> on the root before it looks at the index and holds it until the
 * write is finished: a JVM-wide lock keyed by the root's canonical path serializes the threads
 * of this process, and an operating-system lock on {@value #LOCK_FILE} inside the root excludes
 * every other process on the host. A second process is <b>refused</b>
 * ({@link Disposition#REFUSED_WRITER_CONFLICT}) rather than queued, because a process that
 * cannot prove it owns the root must not write into it. The lease is what makes the identity
 * check and the index append one indivisible step: no other writer can observe the index
 * between the check and the append. This is process-level exclusion on one host — it is not a
 * distributed lock, and it makes no claim about two hosts.</p>
 *
 * <p><b>What a record proves.</b> A record binds an artifact name to an identity only when the
 * artifact <i>on disk</i> carries the digest the record states. A record whose artifact is
 * missing or altered is stale, and a record that was forged into the index is unverifiable;
 * neither may deny a write. The old behaviour — treating every {@code COMMITTED} line as a
 * binding — let an attacker who could write the index reserve every name in the root without
 * writing a single artifact.</p>
 *
 * <p><b>What is durable.</b> The bytes are forced to disk before the move, the index is forced
 * after every append, and the root directory is forced after the move, so the rename and the
 * trace that describes it survive a power loss in the state the index records.</p>
 */
public final class ArtifactWriter {

    /** The largest artifact the writer will stage, in bytes. */
    public static final int MAX_ARTIFACT_BYTES = 256 * 1024;

    /** The most committed artifacts one root may hold. */
    public static final int MAX_ARTIFACTS = 1000;

    /**
     * The most index records one root may hold.
     *
     * <p>The trace is append-only evidence, so it is never truncated: at the limit the writer
     * refuses new writes instead. The limit is four records per artifact — one {@code STAGED}
     * and one {@code COMMITTED} record per write, with room for the reconciliation writes a
     * retry may append.</p>
     */
    public static final int MAX_INDEX_RECORDS = 4 * MAX_ARTIFACTS;

    /** The largest index file one root may hold, in bytes. */
    public static final long MAX_INDEX_BYTES = 4L * 1024 * 1024;

    /** The append-only index of every staged and committed write, in the root. */
    public static final String INDEX_FILE = ".artifact-index.ndjson";

    /**
     * The file that carries the root's writer lease.
     *
     * <p>It is a dot file, so the name rules refuse it as an artifact name, and the operating
     * system lock on it is released by the kernel when the process dies — a crashed writer
     * cannot hold a root hostage.</p>
     */
    public static final String LOCK_FILE = ".artifact-lock";

    /** The dispositions of one write attempt. */
    public enum Disposition {
        WRITTEN, REPLAYED, RECONCILED_WRITTEN,
        REFUSED_OUTSIDE_ROOT, REFUSED_TRAVERSAL, REFUSED_SYMLINK_ESCAPE,
        REFUSED_UNSUPPORTED_TYPE, REFUSED_TOO_LARGE, REFUSED_INVALID_CONTENT,
        REFUSED_EMPTY_NAME, REFUSED_IDENTITY, REFUSED_WRITE_FAILED, REFUSED_BUDGET,
        REFUSED_WRITER_CONFLICT, REFUSED_ROOT_REPLACED, REFUSED_INDEX_CORRUPT,
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

    /**
     * The in-process lease of every root, keyed by the root's canonical path.
     *
     * <p>The key is the real path, so two writers that name the same directory by different
     * routes share one lease, and two different directories never share one. The map grows
     * with the number of roots a process opens, which is bounded by the process's own
     * configuration; an entry is never removed while its lock may still be held.</p>
     */
    private static final ConcurrentHashMap<String, Lock> ROOT_LEASES = new ConcurrentHashMap<>();

    private final Path root;

    /**
     * The identity of the root directory as the file system saw it when the writer was made.
     *
     * <p>A root that is replaced — deleted and recreated, or swapped for a symbolic link —
     * is a different directory wearing the same name. Comparing the identity before every
     * write turns that into a refusal instead of a write into somewhere else.</p>
     */
    private final Object rootIdentity;

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
            this.rootIdentity = identityOf(this.root);
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
        Path target = root.resolve(fileName);
        // the root must still be the directory the writer was made for. The check comes
        // BEFORE the lease, so that a root that was deleted or replaced is reported as
        // what it is, and not as a lease that could not be taken.
        String replaced = rootReplaced();
        if (replaced != null) {
            return refuse(Disposition.REFUSED_ROOT_REPLACED, actionId, fileName, replaced);
        }
        // the lease: one writer per root, in this process and on this host. It is taken
        // BEFORE the index is read, so the identity check and the index append are one
        // indivisible step with respect to every other writer.
        RootLease lease;
        try {
            lease = acquireRootLease();
        } catch (WriterConflict conflict) {
            return refuse(conflict.disposition, actionId, fileName, conflict.getMessage());
        }
        try (lease) {
            // the index's own identity is checked INSIDE the lease: the check and the
            // append must be one step with respect to every other writer.
            if (Files.isSymbolicLink(index())) {
                return refuse(Disposition.REFUSED_SYMLINK_ESCAPE, actionId, fileName,
                        "the index is a symbolic link; the writer never follows one");
            }
            if (hardLinked(index())) {
                return refuse(Disposition.REFUSED_SYMLINK_ESCAPE, actionId, fileName,
                        "the index is a hard link to another file; the writer never appends through one");
            }
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
                IndexView index = readIndexView();
                if (index.corrupt()) {
                    // the trace is evidence, not state to rewrite: a broken log is reported,
                    // never repaired, and the writer refuses to act on an unsound view of it
                    return refuse(Disposition.REFUSED_INDEX_CORRUPT, actionId, fileName,
                            "the index is not a readable append-only log; the writer refuses to "
                                    + "act on an unsound trace");
                }
                if (index.records().size() >= MAX_INDEX_RECORDS
                        || index.bytes() >= MAX_INDEX_BYTES) {
                    return refuse(Disposition.REFUSED_BUDGET, actionId, fileName,
                            "the index holds " + index.records().size() + " records and "
                                    + index.bytes() + " bytes, at the bound of "
                                    + MAX_INDEX_RECORDS + " records and " + MAX_INDEX_BYTES
                                    + " bytes; the trace is never truncated, so this root is "
                                    + "full — use another root");
                }
                // the name belongs to the identity that committed it — a COMMITTED record OR the
                // crash window's STAGED record whose final file already carries its digest
                Optional<Record> boundToAnother = boundToAnotherIdentity(index.records(), fileName,
                        actionId);
                if (boundToAnother.isPresent()) {
                    return refuse(Disposition.REFUSED_IDENTITY, actionId, fileName,
                            "the artifact name is already committed by the action identity "
                                    + boundToAnother.get().actionId() + "; one artifact has one identity");
                }
                Optional<Record> commit = effectiveCommit(index.records(), actionId);
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
                long committedFiles = committedArtifactCount(index.records());
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
            // the rename itself must be on the medium before the trace that describes it
            forceRootDirectory();
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
        return effectiveCommit(readIndex(), actionId);
    }

    /**
     * The commit of an identity inside an already-read index.
     *
     * @param records the index records
     * @param actionId the identity
     * @return the record that commits the identity, if any
     */
    private Optional<Record> effectiveCommit(List<Record> records, String actionId) {
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
            if (carriesDigest(finalPath, staged.get().sha256())) {
                return staged;
            }
        }
        return Optional.empty();
    }

    /** Whether the path is a regular file that carries the expected digest. */
    private boolean carriesDigest(Path path, String expected) {
        try {
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && digest(path).equals(expected);
        } catch (IOException unreadable) {
            // an artifact that cannot be read cannot be verified, so it is not a commit
            return false;
        }
    }

    /**
     * Resolves the state a crash may have left for one action identity.
     *
     * @param actionId the identity whose state is asked for
     * @return a description of the state and the disposition the recovery resolved
     */
    public Map<String, Object> recover(String actionId) {
        Map<String, Object> state = new LinkedHashMap<>();
        // the recovery removes the staged files of an identity, so it runs under the same
        // lease as the write: without it, a recovery could delete the staged file of a
        // write that is in flight for the same identity
        RootLease lease;
        try {
            lease = acquireRootLease();
        } catch (WriterConflict conflict) {
            state.put("actionId", actionId);
            state.put("disposition", "WRITER_CONFLICT");
            state.put("note", conflict.getMessage());
            return state;
        }
        try (lease) {
            return recoverUnderLease(actionId, state);
        }
    }

    /** The recovery, under the root's lease. */
    private Map<String, Object> recoverUnderLease(String actionId, Map<String, Object> state) {
        if (Files.isSymbolicLink(index()) || hardLinked(index())) {
            state.put("actionId", actionId);
            state.put("disposition", "INDEX_REFUSED");
            state.put("note", "the index is a link; the writer never follows one, so the "
                    + "crash state cannot be read through it");
            return state;
        }
        try {
            IndexView view = readIndexView();
            if (view.corrupt()) {
                state.put("actionId", actionId);
                state.put("disposition", "CORRUPT_INDEX");
                state.put("note", "the index is not a readable append-only log, so the crash state "
                        + "cannot be derived from it; the writer refuses to act on it");
                return state;
            }
            List<Record> records = view.records();
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
        return readIndexView().records();
    }

    /**
     * The index as the writer reads it, with the facts the write path needs.
     *
     * @param records the readable records
     * @param bytes the size of the index file
     * @param corrupt whether the log's structure is broken
     */
    private record IndexView(List<Record> records, long bytes, boolean corrupt) {
    }

    /**
     * Reads the index once, and reports whether its structure is sound.
     *
     * <p>A line that does not parse is <b>tolerated</b> when it is the last line of a file
     * that does not end in a newline — that is exactly the residue a crash mid-append
     * leaves. Any other unreadable line means the log's structure is broken: a record
     * cannot be placed in the order it was written, so the identity and budget checks
     * computed from it would be unsound. The writer then fails closed rather than acting on
     * a trace it cannot read. The index is never rewritten to repair it.</p>
     */
    private IndexView readIndexView() throws IOException {
        List<Record> records = new ArrayList<>();
        Path index = index();
        if (!Files.exists(index, LinkOption.NOFOLLOW_LINKS)) {
            return new IndexView(records, 0L, false);
        }
        if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS)) {
            // something that is not a log stands in the index's place
            return new IndexView(records, 0L, true);
        }
        byte[] raw = Files.readAllBytes(index);
        String text = new String(raw, StandardCharsets.UTF_8);
        boolean endsWithNewline = text.endsWith("\n");
        String[] lines = text.split("\n", -1);
        int count = endsWithNewline ? lines.length - 1 : lines.length;
        boolean corrupt = false;
        for (int position = 0; position < count; position++) {
            String line = lines[position];
            if (line.isBlank()) {
                continue;
            }
            Record record;
            try {
                record = JSON.readValue(line, Record.class);
            } catch (IOException torn) {
                if (position == count - 1 && !endsWithNewline) {
                    // the residue of a crash mid-append: skipped, never repaired
                    continue;
                }
                corrupt = true;
                continue;
            }
            if (validRecord(record)) {
                records.add(record);
            }
        }
        return new IndexView(records, raw.length, corrupt);
    }

    /**
     * Appends one record and forces it to disk.
     *
     * <p>The separator and the record go out in one write, so a crash in the middle leaves a
     * partial line <i>without</i> a trailing newline — which is precisely the shape the
     * reader recognises as a torn append. The channel is forced afterwards, so the trace
     * that describes the artifact is on the medium before the caller is told the write
     * succeeded.</p>
     */
    private void appendRecord(Record record) throws IOException {
        byte[] payload = JSON.writeValueAsString(record).getBytes(StandardCharsets.UTF_8);
        Path index = index();
        prepareIndexForAppend(index);
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + 1);
        buffer.put(payload);
        buffer.put((byte) '\n');
        buffer.flip();
        try (FileChannel channel = FileChannel.open(index, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS)) {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    /**
     * Prepares the index for an append.
     *
     * <p>The index is append-only evidence, and a <b>complete</b> record is never removed.
     * The residue of a crash mid-append is not a record: it is a fragment the writer never
     * finished. Two cases are told apart, because they are different things:</p>
     * <ul>
     *   <li>a tail that <b>parses</b> is a complete record whose newline was lost. It is
     *       terminated and kept — it is evidence, and it stays;</li>
     *   <li>a tail that does <b>not</b> parse is a fragment. It is removed, and nothing else
     *       in the file is touched. Leaving it there would put a broken line in the middle
     *       of the log, and every later read would see a structure it cannot trust — the
     *       root would refuse every further write over a fragment that was never evidence.</li>
     * </ul>
     */
    private void prepareIndexForAppend(Path index) throws IOException {
        if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS) || Files.size(index) == 0) {
            return;
        }
        byte[] raw = Files.readAllBytes(index);
        if (raw[raw.length - 1] == '\n') {
            return;
        }
        int lastNewline = -1;
        for (int position = raw.length - 1; position >= 0; position--) {
            if (raw[position] == '\n') {
                lastNewline = position;
                break;
            }
        }
        int tailStart = lastNewline + 1;
        String tail = new String(raw, tailStart, raw.length - tailStart, StandardCharsets.UTF_8);
        if (tail.isBlank()) {
            return;
        }
        Record complete = null;
        try {
            complete = JSON.readValue(tail, Record.class);
        } catch (IOException fragment) {
            // the tail is a fragment, not a record
        }
        if (complete != null && validRecord(complete)) {
            // a complete record that lost its newline: it is terminated, not removed
            Files.write(index, new byte[] {'\n'}, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
            return;
        }
        try (FileChannel channel = FileChannel.open(index, StandardOpenOption.WRITE)) {
            channel.truncate(tailStart);
            channel.force(true);
        }
    }

    /** Forces the root directory, so the rename into it survives a power loss. */
    private void forceRootDirectory() {
        try (FileChannel channel = FileChannel.open(root, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException notSupported) {
            // a platform that cannot fsync a directory still gets the artifact's own fsync
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

    // ── the writer lease ───────────────────────────────────────────────────

    /**
     * The exclusive right to write one root.
     *
     * <p>Held for the whole write: the identity check, the staging, the move and the index
     * append are one step with respect to every other writer.</p>
     */
    private interface RootLease extends AutoCloseable {
        @Override
        void close();
    }

    /** The root is held by another writer, or its lease cannot be taken. */
    private static final class WriterConflict extends Exception {
        private final Disposition disposition;

        WriterConflict(Disposition disposition, String message) {
            super(message);
            this.disposition = disposition;
        }
    }

    /**
     * Takes the writer lease of this root.
     *
     * <p>Two mechanisms, because they exclude different writers. The JVM-wide lock
     * serializes the threads of this process; the operating-system lock on the lease file
     * excludes every other process on the host. A process that cannot take the lease is
     * <b>refused</b>, not queued: a writer that cannot prove it owns the root must not
     * write into it. The operating-system lock is released by the kernel when the process
     * dies, so a crashed writer cannot hold a root hostage.</p>
     *
     * @return the lease; closing it releases the root
     * @throws WriterConflict when another writer holds the root
     */
    private RootLease acquireRootLease() throws WriterConflict {
        Lock local = ROOT_LEASES.computeIfAbsent(root.toString(), key -> new ReentrantLock());
        local.lock();
        FileChannel channel = null;
        try {
            Path lease = root.resolve(LOCK_FILE);
            if (Files.isSymbolicLink(lease)) {
                throw new WriterConflict(Disposition.REFUSED_WRITER_CONFLICT,
                        "the writer lease file is a symbolic link; the writer never follows one");
            }
            channel = FileChannel.open(lease, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            FileLock held;
            try {
                held = channel.tryLock();
            } catch (OverlappingFileLockException overlapping) {
                throw new WriterConflict(Disposition.REFUSED_WRITER_CONFLICT,
                        "another writer in this process holds this root");
            }
            if (held == null) {
                throw new WriterConflict(Disposition.REFUSED_WRITER_CONFLICT,
                        "another process holds the writer lease of this root");
            }
            final FileLock fileLock = held;
            final FileChannel openChannel = channel;
            channel = null;
            return new RootLease() {
                @Override
                public void close() {
                    try {
                        fileLock.release();
                    } catch (IOException ignored) {
                        // closing the channel releases the lock anyway
                    }
                    try {
                        openChannel.close();
                    } catch (IOException ignored) {
                        // the kernel drops the lock with the process
                    }
                    local.unlock();
                }
            };
        } catch (WriterConflict conflict) {
            closeQuietly(channel);
            local.unlock();
            throw conflict;
        } catch (IOException failure) {
            closeQuietly(channel);
            local.unlock();
            // an unwritable root is an I/O failure, not a conflict with another writer
            throw new WriterConflict(Disposition.REFUSED_WRITE_FAILED,
                    "the writer lease could not be taken: " + failure.getMessage());
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // the lease was never taken; there is nothing to release
        }
    }

    /**
     * Why the root is no longer the directory this writer was made for, or null.
     *
     * <p>A root that is deleted, replaced or swapped for a symbolic link is a different
     * directory wearing the same name. The writer refuses rather than write into a place
     * the caller did not authorize.</p>
     */
    private String rootReplaced() {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return "the authorized root no longer exists: " + root;
        }
        if (Files.isSymbolicLink(root)) {
            return "the authorized root was replaced by a symbolic link: " + root;
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return "the authorized root is no longer a directory: " + root;
        }
        try {
            Object identity = identityOf(root);
            if (rootIdentity != null && identity != null && !rootIdentity.equals(identity)) {
                return "the authorized root was replaced by another directory: " + root;
            }
        } catch (IOException unreadable) {
            return "the authorized root cannot be identified: " + unreadable.getMessage();
        }
        return null;
    }

    /**
     * The file system's identity of a path, or null when it has none.
     *
     * <p>The identity is the file key together with the creation time. Both are needed: on an
     * overlay file system the inode number of a deleted directory is handed straight to its
     * replacement, so the file key alone would call a replaced root the original one. The
     * creation time of a directory does not move when files are added to it or removed from
     * it, so the pair is stable for the directory the caller authorized and different for a
     * different directory that took its place.</p>
     */
    private static Object identityOf(Path path) throws IOException {
        java.nio.file.attribute.BasicFileAttributes attributes =
                Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
        return attributes.fileKey() + "@" + attributes.creationTime();
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

    /**
     * The other identity that holds this artifact name, if any.
     *
     * <p>A record holds a name only when the artifact <b>on disk</b> carries the digest the
     * record states. A record whose artifact is missing was written for an artifact that is
     * gone; a record whose artifact no longer matches was written for different bytes. In
     * either case the record cannot be checked against the file system, so it does not bind
     * the name and does not deny this write. This is the difference between an honest trace
     * and a forged one: the writer verifies the claim it acts on.</p>
     *
     * @param records the index records
     * @param fileName the requested artifact name
     * @param actionId the requesting identity
     * @return the record of the identity that holds the name, if any
     */
    private Optional<Record> boundToAnotherIdentity(List<Record> records, String fileName,
                                                     String actionId) {
        for (Record record : records) {
            if (!record.fileName().equals(fileName) || record.actionId().equals(actionId)) {
                continue;
            }
            if (!record.status().equals("COMMITTED") && !record.status().equals("STAGED")) {
                continue;
            }
            Path finalPath = root.resolve(record.fileName());
            if (carriesDigest(finalPath, record.sha256())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    /**
     * The artifacts the root really holds.
     *
     * <p>Only a {@code COMMITTED} record whose artifact is present on disk counts. A forged
     * record names an artifact that does not exist, and counting it would let anyone who can
     * write the index exhaust the budget of a root that holds nothing.</p>
     *
     * @param records the index records
     * @return the number of distinct artifacts present on disk
     */
    private long committedArtifactCount(List<Record> records) {
        return records.stream()
                .filter(r -> r.status().equals("COMMITTED"))
                .map(Record::fileName)
                .distinct()
                .filter(name -> Files.isRegularFile(root.resolve(name), LinkOption.NOFOLLOW_LINKS))
                .count();
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
