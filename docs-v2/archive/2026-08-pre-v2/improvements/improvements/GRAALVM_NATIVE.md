# GraalVM Native Compilation — Implementation Plan

**Status:** 🔴 BLOCKED (Quarkus 3.37 compatibility)
**Priority:** HIGH
**Estimated effort:** 2-3 weeks
**Target:** v3.58

---

## Problem Statement

GraalVM native compilation is blocked on Quarkus 3.37 due to:
1. Java 25 bytecode incompatibility with GraalVM 24
2. Pekko 1.6.0 reflection-heavy code requires native-image hints
3. Kafka client needs runtime initialization config
4. JNI calls in SIMD vector operations need explicit registration

---

## Implementation Steps

### Step 1: GraalVM 25 Toolchain (Week 1)
```bash
# Install GraalVM 25
sdk install java 25.0.1-graal
sdk use java 25.0.1-graal

# Verify
native-image --version
```

### Step 2: Quarkus Native Config (Week 1)
```properties
# matrix-core/src/main/resources/application.properties
quarkus.native.enabled=true
quarkus.native.container-build=true
quarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-25
quarkus.native.additional-build-args=\
  --initialize-at-run-time=io.matrix.cluster.NeuronClusterActor,\
  --initialize-at-run-time=io.matrix.events.KafkaEventJournal,\
  -H:+ReportExceptionStackTraces
```

### Step 3: Reflection Config (Week 1)
```json
// matrix-core/src/main/resources/META-INF/native-image/reflect-config.json
[
  {
    "name": "io.matrix.neuron.TruthTable",
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "io.matrix.neuron.DecisionTree",
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "io.matrix.evolution.Chromosome",
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  }
]
```

### Step 4: Resource Config (Week 1)
```json
// matrix-core/src/main/resources/META-INF/native-image/resource-config.json
{
  "resources": {
    "includes": [
      {"pattern": ".*\\.avro$"},
      {"pattern": ".*\\.jsonl$"},
      {"pattern": ".*\\.properties$"},
      {"pattern": "models/.*"}
    ]
  }
}
```

### Step 5: JNI Config (Week 2)
```json
// matrix-core/src/main/resources/META-INF/native-image/jni-config.json
[
  {
    "name": "jdk.incubator.vector.VectorSpecies",
    "allDeclaredMethods": true
  }
]
```

### Step 6: Dockerfile.native Update (Week 2)
```dockerfile
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-25 AS build
COPY --chown=quarkus:quarkus . /work
WORKDIR /work/matrix-core
RUN ./gradlew build -Dquarkus.package.type=native -Dquarkus.native.enabled=true

FROM quay.io/quarkus/ubi-quarkus-micro-image:2.0
COPY --from=build /work/matrix-core/build/*-runner /application
EXPOSE 9091
CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

### Step 7: CI Pipeline Update (Week 2)
```yaml
# .github/workflows/native.yml
name: Native Build
on: [push]
jobs:
  native:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: graalvm/setup-graalvm@v1
        with:
          java-version: '25'
          distribution: 'graalvm'
      - run: ./gradlew :matrix-core:build -Dquarkus.package.type=native
      - run: ./matrix-core/build/*-runner --help
```

### Step 8: Performance Benchmarks (Week 3)
- Startup time: target < 500ms (vs ~3s JVM)
- Memory: target < 100MB RSS (vs ~300MB JVM)
- Throughput: JMH comparison native vs JVM

---

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Quarkus 3.37 incompatibility | HIGH | Wait for 3.38 or use 3.36.1 |
| Pekko reflection failures | HIGH | Add @RegisterForReflection |
| Kafka JNI issues | MEDIUM | Use Kafka native client |
| SIMD vector API | MEDIUM | Fallback to scalar |

---

## Verification

```bash
# Build native
./gradlew :matrix-core:build -Dquarkus.package.type=native

# Run native
./matrix-core/build/*-runner

# Test
curl http://localhost:9091/q/health
curl http://localhost:9091/v1/models
```
