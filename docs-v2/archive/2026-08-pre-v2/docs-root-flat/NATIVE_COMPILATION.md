# GraalVM Native Image Compilation — MATRIX

## Overview

MATRIX supports GraalVM native image compilation for dramatically reduced startup time
(~50ms vs ~3s JVM) and lower memory footprint (~60MB vs ~256MB JVM). Native compilation
converts the JVM bytecode into a standalone native binary.

## Prerequisites

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 25 LTS | GraalVM 25 or Temurin 25 |
| GraalVM native-image | 25.0.3+ | Local native compilation |
| **OR** Mandrel | 24.1+ | Container-based native compilation (recommended) |
| Docker | 29.x+ | Container-based build |
| RAM | 8GB+ | native-image needs `-J-Xmx8g` |
| Disk | 5GB+ | Native binary is ~150MB |

## Build Methods

### Method 1: Container-based (Recommended)

No local GraalVM installation required. Uses Mandrel 24 in a Docker container.

```bash
# Via Gradle task
./gradlew :matrix-core:buildNative

# Or directly via Quarkus
./gradlew :matrix-core:quarkusBuild \
  -Dquarkus.native.enabled=true \
  -Dquarkus.package.type=native \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder:24.1.0-java21
```

### Method 2: Local GraalVM

```bash
# Install GraalVM 25 via SDKMAN
sdk install java 25.0.3-graalce
sdk use java 25.0.3-graalce

# Install native-image component
gu install native-image

# Build
./gradlew :matrix-core:buildNativeLocal
```

### Method 3: Docker Compose

```bash
# Build and run native image service
docker compose up matrix-core-native
```

## Build Tasks

| Task | Description |
|------|-------------|
| `./gradlew :matrix-core:buildNative` | Container-based native build (Mandrel 24) |
| `./gradlew :matrix-core:buildNativeLocal` | Local native build (requires GraalVM) |
| `./gradlew :matrix-core:runNative` | Build + run native binary |
| `./gradlew :matrix-core:nativeCompilationTest` | Run native config validation tests |
| `./gradlew :matrix-core:nativeSmokeTest` | Run native binary smoke tests |
| `./gradlew :matrix-core:testNative` | Run tests in native mode |

## Configuration Files

All native image configuration lives in:
```
matrix-core/src/main/resources/META-INF/native-image/
├── native-image.properties      # Build args for native-image
├── reflect-config.json          # Classes available via reflection
├── resource-config.json         # Resources included in native image
├── serialization-config.json    # Pekko serialization classes
└── jni-config.json              # JNI configuration (SIMD vectors)
```

### reflect-config.json
Contains all classes that need reflection access at runtime:
- **io.matrix.\*** — All domain classes (neuron, cluster, mediator, etc.)
- **io.matrix.cluster.NeuronClusterActor\$*** — All Pekko actor inner records
- **org.apache.pekko.\*** — Pekko actor system classes
- **org.apache.avro.\*** — Avro serialization classes
- **org.apache.kafka.\*** — Kafka serializers/deserializers
- **io.lettuce.\*** — Redis client classes
- **com.fasterxml.jackson.\*** — Jackson JSON classes

### resource-config.json
Includes resources bundled in the native image:
- `*.properties`, `*.json`, `*.yaml`, `*.yml` — Config files
- `*.avsc`, `*.avro` — Avro schemas
- `db/migration/*.sql` — Database migrations
- `models/*`, `pretrained/*` — ML model files

### serialization-config.json
Pekko serialization classes for actor message passing:
- Actor system internals (Behaviors, ActorRef, etc.)
- All NeuronClusterActor inner records (Command, Response, etc.)
- ClusterMediator inner records
- Domain events (ClusterEvent, ClusterSnapshot, etc.)

### jni-config.json
JNI access for Java Vector API (SIMD acceleration):
- `jdk.incubator.vector.*` — Vector species, shuffle, mask
- `java.lang.foreign.*` — Memory segments (FFM API)

## Native Image Properties

Key build arguments (in `native-image.properties`):

```
-H:+ReportExceptionStackTraces     # Better error reporting
--enable-url-protocols=http,https   # HTTP/HTTPS support
-H:+AddAllCharsets                 # Full charset support
--no-fallback                       # No JVM fallback
-H:+RemoveSaturatedTypeFlows       # Optimize type analysis
-J-Xmx8g                           # 8GB heap for native-image
```

### Initialization Strategy

| Phase | Classes |
|-------|---------|
| **Build-time** | `org.jboss.logmanager`, `org.slf4j`, `io.netty`, `com.fasterxml`, `io.smallrye`, `org.apache.avro`, `io.lettuce`, `org.apache.kafka` |
| **Run-time** | `io.matrix.cluster.NeuronClusterActor`, `org.apache.pekko`, `io.matrix.events.R2dbcEventJournal`, `io.vertx.mutiny` |

## Performance Comparison

| Metric | JVM | Native | Improvement |
|--------|-----|--------|-------------|
| Startup time | ~3s | ~50ms | **60x** |
| Memory (idle) | ~256MB | ~60MB | **4.3x** |
| Binary size | ~50MB JAR | ~150MB | Larger (self-contained) |
| Peak throughput | Higher | Slightly lower | JVM JIT wins long-running |

## Testing

### JVM Mode (config validation)
```bash
# Run native compilation tests in JVM mode
./gradlew :matrix-core:test --tests "io.matrix.nativeimage.*"
```

### Native Mode
```bash
# Run all tests in native mode
./gradlew :matrix-core:testNative -Dquarkus.native.enabled=true

# Run smoke tests
./gradlew :matrix-core:nativeSmokeTest
```

## CI/CD

The CI pipeline includes a `native-build` job that:
1. Builds the native image using Mandrel 24 container
2. Verifies the binary exists and is executable
3. Tests basic startup (`--help`)
4. Uploads the binary as an artifact
5. Runs native compilation validation tests

See `.github/workflows/ci.yml` for details.

## Docker

### JVM Docker Image
```bash
docker build -t matrix-core:jvm .
docker run --rm -p 8080:8080 matrix-core:jvm
```

### Native Docker Image
```bash
docker build -f Dockerfile.native -t matrix-core:native .
docker run --rm -p 8080:8080 matrix-core:native
```

### Docker Compose (side-by-side)
```bash
# Run both JVM and native services
docker compose up matrix-core matrix-core-native
```

## Troubleshooting

### Build fails with "native-image not found"
Install GraalVM or use container-based build:
```bash
./gradlew :matrix-core:buildNative  # Uses Mandrel container
```

### Build fails with "OutOfMemoryError"
Increase native-image heap:
```bash
# In native-image.properties or build.gradle:
-J-Xmx12g
```

### Missing reflection class at runtime
Add the class to `reflect-config.json`:
```json
{
  "name": "io.matrix.your.MissingClass",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
```

### Pekko actor fails to initialize
Ensure the actor class is in `--initialize-at-run-time`:
```
--initialize-at-run-time=io.matrix.your.ActorClass
```

### Avro schema not found
Add the pattern to `resource-config.json`:
```json
{
  "pattern": ".*\\.avsc$"
}
```

### GraalVM 25 incompatibility with Quarkus
Known issue: GraalVM 25 changed native-image CLI syntax. Use:
- **Container build** with Mandrel 24 (recommended)
- **Local build** with GraalVM 23 (`sdk install java 23.0.2-graalce`)

## Known Limitations

1. **SIMD (Vector API)** — Limited support in native mode. The `SimdEvaluator` may fall back to scalar.
2. **Dynamic class loading** — FNL dynamic loading requires explicit reflection config.
3. **Debug info** — Native binaries have limited debug info. Use `-H:+GenerateDebugInfo` for more.
4. **Build time** — Native compilation takes 5-15 minutes (vs seconds for JVM JAR).

## References

- [Quarkus Native Reference](https://quarkus.io/guides/native-reference)
- [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Mandrel](https://github.com/graalvm/mandrel)
- [Pekko + GraalVM](https://pekko.apache.org/docs/pekko/current/additional/native.html)
