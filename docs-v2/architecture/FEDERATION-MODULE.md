# Federation Module Architecture

## Overview

The federation module (`io.matrix.federation.*`) implements liquid federation
for the MATRIX cognitive architecture. Per DESIGN-67 and SPEC-013.

## Package Structure

```
io.matrix.federation
├── proto/                  # Generated ProtoBuf classes (77)
│   ├── ModulatorRegistry.java
│   ├── ModulatorDefinition.java
│   ├── LocalConsensusEngine.java (consumer)
│   └── ... (77 generated classes)
│
├── registry/                # W358 - In-memory CRUD
│   └── ModulatorRegistryStore.java
│
├── consensus/              # W359 - Voting engine
│   └── LocalConsensusEngine.java
│
├── runtime/                 # W360 - End-to-end coordinator
│   └── FederationRuntime.java
│
├── integration/             # W361, W369 - Bridges
│   ├── BiochemicalMediator.java
│   └── CognitiveModulationBridge.java
│
├── gpu/                     # W362 - GPU executor stub
│   └── GpuTaskExecutor.java
│
├── telemetry/               # W363 - Metrics
│   └── FederationTelemetry.java
│
├── property/                # W372, W373 - Property tests
├── stress/                  # W375 - Concurrency tests
├── e2e/                     # W374 - End-to-end tests
├── formal/                  # W371 - Model checker
├── chaos/                   # W366 - Chaos tests
└── ...
```

## Build Integration

### sourceSets

```gradle
sourceSets {
    main {
        java {
            srcDir 'src/generated/java'  // ProtoBuf generated
        }
    }
    jmh {
        java {
            srcDir 'src/jmh/java'  // JMH benchmarks
        }
    }
}
```

### Dependencies

```gradle
dependencies {
    implementation 'com.google.protobuf:protobuf-java:3.25.5'
    testImplementation 'net.jqwik:jqwik:1.9.2'
}
```

## Test Exclusion

The gradle config excludes `**/integration/**` from default test runs:

```gradle
if (!project.hasProperty('includeIntegration')) {
    exclude '**/integration/**'
}
```

Workaround: federation integration tests live under `e2e/` package.

## Native Build

The federation classes are pure Java (no reflection, no classpath scanning)
and compile cleanly into native image. Verified by W370 native rebuild:
- Binary: 126MB
- `--version` works
- All CLI commands work

## Thread Safety

| Class | Thread Safety |
|-------|---------------|
| ModulatorRegistryStore | Not thread-safe (single-threaded expected) |
| LocalConsensusEngine | Not thread-safe (collects votes sequentially) |
| FederationRuntime | Not thread-safe |
| FederationTelemetry | **Thread-safe** (AtomicInteger + ConcurrentHashMap) |
| BiochemicalMediator | Synchronized methods |

## CONSTITUTION Compliance

- **Article I v3 (Stratified Stochasticity):** All Random uses constructor seed
- **Article II (Consensus):** Voting weights and thresholds formalized
- **Article IV (Safety):** FROZEN + L7 VETO + capability checks
- **Article VI (no consciousness claim):** Pure data structures

## Performance Baseline

| Operation | Throughput |
|-----------|------------|
| Telemetry recordProposal | 14M ops/sec |
| Registry add | ~1M ops/sec |
| Consensus evaluate (1000 votes) | ~500K ops/sec |

## Future Work

- Distributed consensus (multi-node via Pekko)
- Redis cache layer
- PostgreSQL persistence
- GPU dispatcher (CUDA/OpenCL)
- Network protocol (gRPC over TLS)
