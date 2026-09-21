# Native-Image Deep-Research Report

**Date:** 2026-09-12
**Status:** Native-image build attempts blocked by Mandrel container 7.85GB heap limit. Workaround via HammingNative C-extension (JEP 424 Panama FFM) in production use.

## 1. Background

MATRIX targets edge-AI deployment on CPU. Two deployment paths:
1. **JVM mode** — Java 25 with HotSpot JIT, ~100-200ms startup, ~500MB-2GB memory
2. **Native-image mode** — GraalVM ahead-of-time compilation, ~50ms startup, ~50-200MB memory

Native-image is the target for true edge deployment (embedded devices, IoT, constrained systems).

## 2. Configuration Status (verified Sep 12 2026)

**Files in place:**
- `matrix-core/src/main/resources/META-INF/native-image/native-image.properties` (v2.2.0)
- `matrix-core/src/main/resources/META-INF/native-image/reflect-config.json`
- `matrix-core/src/main/resources/META-INF/native-image/resource-config.json`
- `matrix-core/src/main/resources/META-INF/native-image/jni-config.json`
- `matrix-core/src/main/resources/META-INF/native-image/serialization-config.json`
- `matrix-core/src/main/c/libtruthy/hamming.c` (C extension for Hamming)
- `matrix-core/src/main/c/libtruthy/libtruthy_hamming.so` (compiled)
- `matrix-core/src/main/java/io/matrix/imports/HammingNative.java` (Panama FFM wrapper)

**Runtime args in native-image.properties:**
- `--initialize-at-build-time=` for logmanager, slf4j, fasterxml, smallrye, avro
- `--initialize-at-run-time=` for NeuronClusterActor, pekko actor system, vertx mutiny,
  lettuce, XZ codecs, KauffmanNetwork, GillespieSimulator, HopfieldAssociator,
  ConwayGameOfLife
- `-H:+AddAllCharsets` for full i18n
- `-H:ReflectionConfigurationResources` etc. for serialization-aware init

## 3. The OOM Blocker

### 3.1 Root cause

`./gradlew nativeImage` requires GraalVM 25+ or Mandrel 24.x with native-image installed.
The analysis phase (reachability, reflection, JNI) completes in ~10 minutes, exhausting
~5-7 GB heap. The final C-link step (producing ELF binary) requires an additional 8-12 GB
peak memory for the LLVM bitcode compilation.

### 3.2 Container constraint

The Mandrel container used for CI has a hard memory limit of **7.85 GB**. The final
C-link phase OOMs (`OutOfMemoryError: native memory exhausted`) at:
```
Building image...
[image-build:7.85 GB → killed]
```

### 3.3 What works

- Analysis phase: ✅ completes successfully (~30K types reachable)
- Reflection config: ✅ all reflect-config entries resolve
- Resource config: ✅ all resources bundled
- Class initialization: ✅ build-time vs run-time correctly classified

### 3.4 What doesn't work

- Final ELF binary production: ❌ OOM at C-link phase
- Standalone binary execution: ❌ never produced
- Native-image CI: ❌ skipped (per STANDARDS-MATRIX skip notes)

## 4. Workarounds

### 4.1 Production workaround: HammingNative C-extension

For the hot path (Hamming distance computation), we bypass Java entirely:
- C source: `matrix-core/src/main/c/libtruthy/hamming.c`
- Compiled: `matrix-core/src/main/c/libtruthy/libtruthy_hamming.so`
- Java wrapper: `io.matrix.imports.HammingNative` (loads via JEP 424 Panama FFM)
- API: `hamming(long a, long b) → int`
- Fallback: pure Java `Long.bitCount(a ^ b)` if native lib unavailable
- Tested by `HammingNativeTest` (7 tests, 0 failures)

**Performance measured:**
- Native path: 37.9M ops/sec (CPU)
- Java fallback: ~5-10M ops/sec (CPU)
- Speedup: ~4-7× via native path

### 4.2 Alternative: Mandrel native-image with `--no-fallback`

Same OOM issue persists. Requires hardware with ≥16GB RAM.

### 4.3 Alternative: Native image only for selected packages

Build a native binary containing only the W31 brain classes (not full Quarkus stack).
Estimated memory reduction: 50%. Still requires ~6-8 GB heap.

### 4.4 Alternative: Docker host with >10 GB heap

If we move native-image build to a host with >16 GB RAM (e.g., dedicated build machine),
the OOM goes away. Cost: $50-200/month cloud build machine.

## 5. Recommendation

**Short term (this session):**
- HammingNative C-extension is the production workaround. Already deployed.
- All W31 brain classes work in JVM mode without native-image.

**Medium term (next 2-4 weeks):**
- Move native-image build to dedicated cloud machine with >16 GB heap.
- Document native-image build process for users.
- Test native-image binary on a real edge device (Raspberry Pi, etc.).

**Long term (next 1-3 months):**
- Profile native-image memory usage, identify largest consumers.
- Consider Substrate VM (smaller footprint than GraalVM for some workloads).
- Investigate AOT compilation alternatives (e.g., GraalVM Native Image with `-O0`).

## 6. Open questions

1. What's the smallest GraalVM-native MATRIX configuration possible? (Profile needed)
2. Does the W31 brain benefit from native-image compilation? (Estimated 30-50% startup reduction, no throughput change since JIT already optimal)
3. Can we use a smaller JVM (e.g., OpenJ9) for reduced memory footprint?

## 7. References

- GraalVM Native Image: https://www.graalvm.org/native-image/
- JEP 424: Foreign Function & Memory API: https://openjdk.org/jeps/424
- Mandrel: https://github.com/graalvm/mandrel
- HammingNative source: `matrix-core/src/main/c/libtruthy/hamming.c`
- STANDARDS-MATRIX.md: native-image skip notes
