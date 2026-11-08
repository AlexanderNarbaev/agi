package io.matrix.nativekernel;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * W51 — the loader of the native kernel library, through Project Panama FFM.
 *
 * <p><b>Why FFM and not JNI.</b> JNI needs a generated header, a C stub
 * library and a build step inside the Java build. FFM needs none of that: the
 * {@link java.lang.foreign} API is final in JDK 22 and later, so the
 * {@code Linker} finds the symbol in a shared object that already exists on
 * disk and calls it directly. The FFM API was verified to compile and run on
 * this project's JDK 25 without {@code --enable-preview}.
 *
 * <p><b>Lazy by construction.</b> The library is looked up on the first call
 * to {@link #instance()}, not when this class is loaded. A deployment without
 * the shared object, or on a platform this loader does not recognise, pays
 * nothing and loses nothing: {@link #isAvailable()} is false and the caller
 * uses the Java path.
 *
 * <p><b>The library is looked for in named places, in this order:</b>
 * <ol>
 *   <li>the system property {@value #LIBRARY_PATH_PROPERTY}, when it is set —
 *       an explicit path always wins;</li>
 *   <li>the shared object next to the module build directory, in the
 *       {@code matrix-core/build/native} directory and its neighbours, so the
 *       library is found wherever the module was built;</li>
 *   <li>the platform's own library search path.</li>
 * </ol>
 * The names are relative to the working directory, never to one developer's
 * home directory: a path that is only true on one machine is a path that
 * fails everywhere else, and the failure must name the paths it tried.
 */
public final class NativeKernelLoader {

    /** The system property that names the shared object explicitly. */
    public static final String LIBRARY_PATH_PROPERTY = "matrix.nativekernel.lib";

    /** The file name of the shared object on Linux. */
    public static final String LIBRARY_FILE_NAME = "libmatrixkernel.so";

    /** The symbol that evaluates a truth table over a batch. */
    public static final String EVAL_BATCH_SYMBOL = "matrix_tt_eval_batch";

    /** The symbol that evaluates nothing; it measures the call cost. */
    public static final String EVAL_BATCH_NOOP_SYMBOL = "matrix_tt_eval_batch_noop";

    /**
     * The relative directories the shared object is looked for in, under the
     * working directory.
     *
     * <p>They are the ones the module's own build produces, plus the two
     * neighbours that occur when the tests run from the repository root.
     */
    private static final String[] CANDIDATE_DIRECTORIES = {
        "matrix-core/build/native",
        "build/native",
        "../matrix-core/build/native",
        "src/main/c/matrixkernel"
    };

    private final MethodHandle evalBatch;
    private final MethodHandle evalBatchNoop;
    private final String libraryPath;
    private final String unavailableReason;

    /**
     * The lazily created default loader.
     *
     * <p>The holder class is initialised on the first access, so the shared
     * object is looked for only when the kernel is actually used.
     */
    private static final class Holder {
        static final NativeKernelLoader INSTANCE = load();
    }

    /**
     * The default loader.
     *
     * @return the process-wide loader; the library is looked for on the first
     *         call to this method.
     */
    public static NativeKernelLoader instance() {
        return Holder.INSTANCE;
    }

    /**
     * Loads a specific shared object.
     *
     * <p>Used by the tests to exercise the "the library is not there" case
     * without touching the process-wide loader.
     *
     * @param path the shared object to load.
     * @return a loader; {@link #isAvailable()} is false when the load failed.
     */
    public static NativeKernelLoader loadFrom(Path path) {
        if (path == null) {
            return unavailable("no library path was given");
        }
        if (!Files.isRegularFile(path)) {
            return unavailable("the library " + path + " does not exist");
        }
        try {
            return load(path);
        } catch (Throwable t) {
            return unavailable("the library " + path + " could not be loaded: " + t);
        }
    }

    /**
     * Loads the default shared object, looking for it in the named places.
     *
     * @return a loader; {@link #isAvailable()} is false when no candidate was
     *         found or the load failed.
     */
    public static NativeKernelLoader load() {
        String explicit = System.getProperty(LIBRARY_PATH_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return loadFrom(Path.of(explicit.trim()));
        }
        for (String directory : CANDIDATE_DIRECTORIES) {
            Path candidate = Path.of(directory, LIBRARY_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return loadFrom(candidate);
            }
        }
        return unavailable("the shared object " + LIBRARY_FILE_NAME + " was not found; the paths tried were: "
                + String.join(", ", candidatePaths()));
    }

    /**
     * The paths the loader would try, in order.
     *
     * @return the candidate paths as strings.
     */
    public static List<String> candidatePaths() {
        List<String> paths = new ArrayList<>();
        String explicit = System.getProperty(LIBRARY_PATH_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            paths.add(explicit.trim());
            return paths;
        }
        for (String directory : CANDIDATE_DIRECTORIES) {
            paths.add(Path.of(directory, LIBRARY_FILE_NAME).toString());
        }
        return paths;
    }

    private static NativeKernelLoader load(Path path) {
        Arena libraryArena = Arena.global();
        SymbolLookup lookup = SymbolLookup.libraryLookup(path, libraryArena);
        Linker linker = Linker.nativeLinker();
        FunctionDescriptor descriptor = FunctionDescriptor.of(
                JAVA_INT,
                ADDRESS,   // const uint64_t* table
                JAVA_LONG, // int64_t table_words
                JAVA_INT,  // int32_t k
                ADDRESS,   // const uint64_t* inputs
                ADDRESS,   // uint64_t* outputs
                JAVA_LONG); // int64_t n
        MethodHandle batch = linker.downcallHandle(
                lookup.find(EVAL_BATCH_SYMBOL)
                        .orElseThrow(() -> new IllegalStateException(
                                "the symbol " + EVAL_BATCH_SYMBOL + " is not in " + path)),
                descriptor);
        MethodHandle noop = linker.downcallHandle(
                lookup.find(EVAL_BATCH_NOOP_SYMBOL)
                        .orElseThrow(() -> new IllegalStateException(
                                "the symbol " + EVAL_BATCH_NOOP_SYMBOL + " is not in " + path)),
                descriptor);
        return new NativeKernelLoader(batch, noop, path.toAbsolutePath().toString(), null);
    }

    private static NativeKernelLoader unavailable(String reason) {
        return new NativeKernelLoader(null, null, null, reason);
    }

    private NativeKernelLoader(MethodHandle evalBatch, MethodHandle evalBatchNoop,
                               String libraryPath, String unavailableReason) {
        this.evalBatch = evalBatch;
        this.evalBatchNoop = evalBatchNoop;
        this.libraryPath = libraryPath;
        this.unavailableReason = unavailableReason;
    }

    /**
     * Whether the shared object was loaded.
     *
     * @return true when the native path is usable.
     */
    public boolean isAvailable() {
        return evalBatch != null;
    }

    /**
     * Why the native path is not usable, when it is not.
     *
     * @return the reason, or null when the library was loaded.
     */
    public String unavailableReason() {
        return unavailableReason;
    }

    /**
     * The path of the shared object that was loaded.
     *
     * @return the absolute path, or null when no library was loaded.
     */
    public String libraryPath() {
        return libraryPath;
    }

    /**
     * Evaluates a truth table over a batch, natively.
     *
     * @param table       the packed truth table in native memory.
     * @param tableWords  the number of 64-bit words in the table.
     * @param arity       the arity of the function.
     * @param inputs      the packed input words in native memory.
     * @param outputs     the output words in native memory; written by the call.
     * @param count       the batch size.
     * @return the native status code.
     */
    public int evalBatch(MemorySegment table, int tableWords, int arity,
                         MemorySegment inputs, MemorySegment outputs, int count) {
        if (!isAvailable()) {
            throw new IllegalStateException("the native kernel is not available: " + unavailableReason);
        }
        try {
            return (int) evalBatch.invokeExact(table, (long) tableWords, (int) arity,
                    inputs, outputs, (long) count);
        } catch (Throwable t) {
            throw new IllegalStateException("the native call failed: " + t, t);
        }
    }

    /**
     * Calls the native entry point that does no work, with the same ABI.
     *
     * <p>It is the measurement of the call and of the marshalling, without the
     * arithmetic. It is never used to produce a result.
     *
     * @param table       the packed truth table in native memory.
     * @param tableWords  the number of 64-bit words in the table.
     * @param arity       the arity of the function.
     * @param inputs      the packed input words in native memory.
     * @param outputs     the output words in native memory; not written.
     * @param count       the batch size.
     * @return the native status code.
     */
    public int evalBatchNoop(MemorySegment table, int tableWords, int arity,
                             MemorySegment inputs, MemorySegment outputs, int count) {
        if (!isAvailable()) {
            throw new IllegalStateException("the native kernel is not available: " + unavailableReason);
        }
        try {
            return (int) evalBatchNoop.invokeExact(table, (long) tableWords, (int) arity,
                    inputs, outputs, (long) count);
        } catch (Throwable t) {
            throw new IllegalStateException("the native call failed: " + t, t);
        }
    }
}
