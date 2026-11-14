package io.matrix.imports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * W53 / REP-02 — the repository's own C function, called the way it was meant to be called.
 *
 * <p><b>Why this test exists.</b> The production helper {@link HammingNative} loads its
 * library with {@code System.load} and then asks
 * {@code Linker.nativeLinker().defaultLookup()} for the symbol — and a library loaded that
 * way is <b>not</b> in the default lookup. The symbol is therefore never found, the
 * initialiser swallows the exception, and the production path silently answers with
 * {@code Long.bitCount}. The measurement of REP-02 compares three arms, and one of them
 * is that silent fallback; this test says the two things that must be said about it:</p>
 * <ul>
 *   <li><b>the C function is correct</b> — bound the way the FFM API intends
 *       ({@code SymbolLookup.libraryLookup}), it returns exactly the Java distance;</li>
 *   <li><b>the production helper returns the Java distance too</b> — so the two paths
 *       cannot be told apart by their answer, which is precisely why the silence is
 *       invisible to a test of behaviour.</li>
 * </ul>
 *
 * <p><b>What this test does not do.</b> It does not fix the lookup: the wave's directive
 * keeps the native path out of the runtime, and the decision whether it should exist at
 * all belongs to REP-03/REP-04. And it does not assert that the native path is
 * <b>un</b>available — a test that froze that fact would break the day someone fixes the
 * lookup, and the fix would be a change of the project's decision, not a defect.
 */
class NativeHammingAgreementTest {

    /** The seed of the random pairs. */
    private static final long SEED = 42L;

    /** How many pairs are compared. */
    private static final int PAIRS = 512;

    /**
     * The paths the library is looked for in.
     *
     * @return the candidates
     */
    private static java.util.List<Path> candidates() {
        java.util.List<Path> paths = new java.util.ArrayList<>();
        String work = System.getProperty("user.dir", ".");
        paths.add(Path.of(work, "matrix-core/src/main/c/libtruthy/libtruthy_hamming.so"));
        paths.add(Path.of(work, "src/main/c/libtruthy/libtruthy_hamming.so"));
        Path parent = Path.of(work).toAbsolutePath().getParent();
        if (parent != null) {
            paths.add(parent.resolve("matrix-core/src/main/c/libtruthy/libtruthy_hamming.so"));
        }
        return paths;
    }

    /**
     * The native function, bound the way the FFM API intends.
     *
     * <p>The arena must outlive the call: a handle whose arena is closed answers
     * "Already closed" at the first invocation. The caller therefore closes it, and the
     * test that binds it owns the arena's life.</p>
     *
     * @return the handle and its arena, or null when the library is not on this machine
     * @throws Throwable when the library is there but cannot be bound
     */
    private static Native nativeHamming() throws Throwable {
        for (Path candidate : candidates()) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            Linker linker = Linker.nativeLinker();
            Arena arena = Arena.ofShared();
            SymbolLookup library = SymbolLookup.libraryLookup(candidate, arena);
            MethodHandle handle = linker.downcallHandle(
                    library.find("hamming_distance_u64").orElseThrow(
                            () -> new IllegalStateException("the symbol is missing")),
                    FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
            return new Native(handle, arena);
        }
        return null;
    }

    /** The bound function and the arena it lives in. */
    private record Native(MethodHandle handle, Arena arena) {}

    @Test
    @DisplayName("the C function and the Java bit count return the same distance")
    void theNativeFunctionAgreesWithJava() throws Throwable {
        Native native_ = nativeHamming();
        assumeTrue(native_ != null,
                "the native library is not on this machine: the agreement cannot be checked here");
        try {
            Random rng = new Random(SEED);
            for (int index = 0; index < PAIRS; index++) {
                long left = rng.nextLong();
                long right = rng.nextLong();
                int distance = (int) native_.handle().invokeExact(left, right);
                int java_ = Long.bitCount(left ^ right);
                assertThat(distance)
                        .withFailMessage("the pair " + index + " must have one distance")
                        .isEqualTo(java_);
            }
        } finally {
            native_.arena().close();
        }
    }

    @Test
    @DisplayName("the production helper returns the Java distance as well")
    void theProductionHelperAgreesWithJava() {
        Random rng = new Random(SEED);
        for (int index = 0; index < PAIRS; index++) {
            long left = rng.nextLong();
            long right = rng.nextLong();
            assertThat(HammingNative.hamming(left, right))
                    .withFailMessage("the pair " + index + " must have one distance")
                    .isEqualTo(Long.bitCount(left ^ right));
        }
    }
}
