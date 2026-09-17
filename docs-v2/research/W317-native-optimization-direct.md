# Native Image Optimization (Direct Synthesis)

## Specific Optimizations Not Yet Applied

### 1. Image Heap Compression
GraalVM supports image heap compression.
Add: `-H:+UseCompressedOops` (already on by default in 25.x)
Add: `-H:ImageHeapStride=8` for 64-bit

### 2. Layer-Specific Compilation
Compile only required layers for startup.
Add: `--layer-create=app-layer,lib-layer`

### 3. Build Cache
Use Gradle build cache for incremental native builds.
Estimated: 3-5x faster rebuilds.

### 4. Build with PGO
Profile-Guided Optimization:
- Run with `-H:+EnableProfileQuality` for first build
- Subsequent builds use profile data
- 10-30% smaller, 5-15% faster

### 5. Cross-Compile to ARM64
With GraalVM native-image for ARM64:
```bash
docker run --rm -v $PWD:/build oraclelabs/graalvm-ce:25.0.2 \
  /bin/bash -c "cd /build && ./gradlew :matrix-core:nativeCompile \
  -Ptarget=linux-aarch64"
```

## Expected Improvements

| Optimization | Size | Startup |
|--------------|------|---------|
| Layer-specific | -10MB | unchanged |
| Build cache | N/A | -50% rebuild |
| PGO | -10MB | -20% startup |
| Cross-compile | unchanged | unchanged |

## Practical Recommendations

For immediate gains (within 1 day):
1. Implement PGO via `--pgo` flag
2. Set up Gradle build cache

For long-term gains:
1. Layer-specific compilation (requires module split)
2. ARM64 build for edge deployment

## CONSTITUTION Compliance

All optimizations are engineering improvements, not consciousness claims.
