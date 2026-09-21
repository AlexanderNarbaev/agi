# GraalVM Native Image Build — MATRIX

> **This document is superseded by [NATIVE_COMPILATION.md](NATIVE_COMPILATION.md).**
> The new document includes: container-based builds, CI/CD pipeline, Docker native image,
> performance benchmarks, and comprehensive troubleshooting.

## Quick Start

```bash
# Container-based build (recommended)
./gradlew :matrix-core:buildNative

# Local build (requires GraalVM)
./gradlew :matrix-core:buildNativeLocal

# Run native binary
./gradlew :matrix-core:runNative
```

See [NATIVE_COMPILATION.md](NATIVE_COMPILATION.md) for full documentation.
