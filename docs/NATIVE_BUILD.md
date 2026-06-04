# GraalVM Native Image Build — MATRIX

## Requirements

| Component | Version | Status |
|-----------|---------|--------|
| Quarkus | 3.35.4 | ✅ Current |
| Java | 25 LTS | ✅ Current (GraalVM 25.0.3) |
| GraalVM native-image | 23.0.x | ⚠️ Required for native build (25.x broken) |
| Docker | 29.x | ✅ For container-based build |

## Known Issue

**GraalVM 25.0.3 native-image is incompatible with Quarkus 3.35.4.**  
The native-image CLI in GraalVM 25 changed the syntax: it no longer accepts output filename as a positional argument. Quarkus 3.35.4 generates a command that GraalVM 25 cannot parse.

**Fix:** Use GraalVM 23 or mandrel 24.x for native compilation.

## Build Methods

### Method 1: Container-based (recommended, no local GraalVM needed)

```bash
./gradlew :matrix-core:quarkusBuild \
  -Dquarkus.native.enabled=true \
  -Dquarkus.package.type=native \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder:24.1.0-java21
```

### Method 2: Local GraalVM 23

```bash
# Install GraalVM 23
sdk install java 23.0.2-graalce

# Switch to GraalVM 23
sdk use java 23.0.2-graalce

# Build
./gradlew :matrix-core:quarkusBuild \
  -Dquarkus.native.enabled=true \
  -Dquarkus.package.type=native
```

### Method 3: Direct native-image (bypass Quarkus plugin)

```bash
# Build runner jar first
./gradlew :matrix-core:build

# Build native image directly
native-image \
  -jar matrix-core/build/matrix-core-1.0.0-runner.jar \
  -o matrix-core/build/matrix-native \
  -H:+ReportExceptionStackTraces \
  --enable-url-protocols=http,https \
  -H:ReflectionConfigurationFiles=matrix-core/reflect-config.json \
  -H:+AddAllCharsets \
  -J-Xmx8g \
  --no-fallback
```

## Verification

```bash
# Check native binary works
./matrix-core/build/matrix-native --help

# Run quick simulation in native mode
./matrix-core/build/matrix-native simulate -g 5 -p 10
```

## Test

```bash
# Run native integration tests
./gradlew :matrix-core:testNative -Dquarkus.native.enabled=true
```

## CI

The CI pipeline includes a `native-build` job that uses the container-based approach.
When GraalVM 25 compatibility is fixed upstream (Quarkus 3.36+), switch to local native-image.

## Native Image Configuration Files

| File | Purpose |
|------|---------|
| `reflect-config.json` | Classes/methods available via reflection |
| `resource-config.json` | Resources included in native image |
| `jni-config.json` | JNI configuration (empty — no JNI used) |
| `META-INF/native-image/native-image.properties` | Build args for native-image |
