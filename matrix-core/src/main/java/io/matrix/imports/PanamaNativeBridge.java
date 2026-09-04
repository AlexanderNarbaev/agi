package io.matrix.imports;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Project Panama wrapper for libtruthy.so.
 *
 * <p>Loads the C library at startup and exposes
 * {@link #evaluate(byte[], int, int, long[], byte[])} that calls
 * {@code truthy_layer_evaluate} directly via FFM. When the native
 * lib cannot be loaded, {@link #isLoaded()} returns false and
 * callers fall back to the pure-Java path in {@link TruthTableLayer}.
 */
@ApplicationScoped
public class PanamaNativeBridge {

    private static final Logger log = LoggerFactory.getLogger(PanamaNativeBridge.class);

    private static final String SYM = "truthy_layer_evaluate";

    private MethodHandle evaluate;
    private boolean loaded = false;
    private String loadError = null;

    @PostConstruct
    void init() {
        try {
            java.nio.file.Path libPath = java.nio.file.Path.of(
                    "/home/alexandr-narbaev/Projects/agi/matrix-core/src/main/c/libtruthy",
                    "libtruthy.so");
            // JDK 25 Panama: prefer SymbolLookup.libraryLookup(Path, Arena) for stable lifetime
            try (Arena shared = Arena.ofShared()) {
                SymbolLookup lib = SymbolLookup.libraryLookup(libPath, shared);
                MemorySegment sym = lib.find(SYM).orElseThrow(
                        () -> new IllegalStateException("symbol " + SYM + " not found"));
                FunctionDescriptor desc = FunctionDescriptor.of(JAVA_INT,
                        ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
                evaluate = Linker.nativeLinker().downcallHandle(sym, desc);
            }
            loaded = true;
            log.info("PanamaNativeBridge loaded libtruthy.so (symbol: " + SYM + ")");
        } catch (Throwable t) {
            loadError = t.getMessage();
            log.warn("PanamaNativeBridge not loaded ({}); using pure-Java path", loadError);
        }
    }

    /** Returns true if the native library was loaded and the function handle is ready. */
    public boolean isLoaded() { return loaded; }
    public String getLoadError() { return loadError; }

    /**
     * Evaluate a layer via the native bridge.
     * @param inputBits  byte array; byte i = 0/1 for bit i
     * @param neurons    number of neurons in the layer
     * @param k          bits per neuron
     * @param table      packed truth tables (long[]; bit (cell%64) at cell/64)
     * @param outBits    byte array of size {@code neurons}; written with results
     * @return 0 on success, negative on native error
     */
    public int evaluate(byte[] inputBits, int neurons, int k, long[] table, byte[] outBits) {
        if (!loaded) throw new IllegalStateException("native bridge not loaded: " + loadError);
        if (inputBits.length < neurons * k)
            throw new IllegalArgumentException("inputBits too short: " + inputBits.length);
        if (outBits.length < neurons)
            throw new IllegalArgumentException("outBits too short: " + outBits.length);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocate(inputBits.length);
            for (int i = 0; i < inputBits.length; i++) {
                inSeg.set(JAVA_BYTE, i, inputBits[i]);
            }
            MemorySegment tableSeg = arena.allocate(table.length * 8L);
            for (int i = 0; i < table.length; i++) {
                tableSeg.set(JAVA_LONG, (long) i * 8L, table[i]);
            }
            MemorySegment outSeg = arena.allocate(outBits.length);
            int rc = (int) evaluate.invokeExact(
                    (java.lang.foreign.MemorySegment) inSeg,
                    neurons, k,
                    (java.lang.foreign.MemorySegment) tableSeg,
                    (java.lang.foreign.MemorySegment) outSeg);
            for (int i = 0; i < neurons; i++) {
                outBits[i] = outSeg.get(JAVA_BYTE, i);
            }
            return rc;
        } catch (Throwable t) {
            throw new RuntimeException("native call failed", t);
        }
    }
}