# MATRIX Observability + Quarkus Migration — Design Spec

**Date:** 2026-05-31
**Status:** Approved
**Goal:** Migrate MATRIX to Quarkus 3.x, add full observability stack (metrics/traces/logs), improve simulation fitness, and prepare Spigot plugin for real Minecraft launch.

---

## 1. Architecture Decisions

### 1.1. Quarkus Migration

| Decision | Rationale |
|----------|-----------|
| Quarkus 3.24 LTS | Built-in Micrometer, OTEL, JSON logging, health checks, native compilation — zero-config for production |
| CDI (`@ApplicationScoped`, `@Inject`) | Replaces manual `new` — cleaner DI, testable |
| Picocli for CLI | `@QuarkusMain` + picocli = typed CLI commands |
| Pekko stays | Quarkus + Pekko compatible via CDI producer for `ActorSystem` |
| Native via Quarkus | `quarkus.native.*` properties, no manual `native-image` scripts |

### 1.2. Observability Stack

| Layer | Technology | Exports To |
|-------|-----------|------------|
| Metrics | Micrometer (Prometheus registry) | Prometheus → Grafana |
| Traces | OpenTelemetry SDK (OTLP exporter) | Jaeger / OTEL Collector |
| Logs | Quarkus JSON logging (stdout + file rotation) | Loki / ELK / `jq` |
| Health | SmallRye Health (`/q/health`) | Kubernetes / Docker healthcheck |

### 1.3. Infrastructure

```
docker-compose.yml:
  prometheus   — :9090  (scrapes matrix:9091/metrics)
  jaeger       — :16686 (UI), :4317 (OTLP gRPC)
  grafana      — :3000  (pre-configured dashboards)
  otel-collector — :4317 (receives traces, exports to Jaeger)
```

---

## 2. File Plan

### 2.1. Create

| File | Purpose |
|------|---------|
| `matrix-core/src/main/resources/application.properties` | Quarkus observability config |
| `matrix-core/src/main/java/io/matrix/observability/MatrixMetrics.java` | Custom Micrometer metrics registry |
| `matrix-core/src/main/java/io/matrix/observability/ObservabilityConfig.java` | CDI producers for MeterRegistry, Tracer |
| `matrix-core/src/main/java/io/matrix/cli/SimulateCommand.java` | `matrix simulate --generations 200 --population 50` |
| `matrix-core/src/main/java/io/matrix/cli/EvolutionCommand.java` | `matrix evolve --world-size 50 --steps 500` |
| `matrix-core/src/main/java/io/matrix/cli/DemoCommand.java` | `matrix demo` — SystemDemo через Quarkus |
| `matrix-core/src/main/java/io/matrix/MatrixApplication.java` | `@QuarkusMain` entrypoint |
| `infra/docker-compose.yml` | Prometheus + Jaeger + Grafana + OTEL Collector |
| `infra/prometheus/prometheus.yml` | Prometheus scrape config |
| `infra/grafana/dashboards/matrix-overview.json` | Main dashboard JSON |
| `infra/grafana/datasources/prometheus.yml` | Grafana datasource config |

### 2.2. Modify

| File | Change |
|------|--------|
| `matrix-core/build.gradle` | Add Quarkus plugin (3.24.0), micrometer, otel, health, picocli, logging-json deps |
| `matrix-spigot/build.gradle` | Point to new matrix-core, add Quarkus runtime for native |
| `matrix-core/src/main/java/io/matrix/evolution/EvolutionLoop.java` | Inject `Logger`, `MatrixMetrics`, add `@WithSpan` |
| `matrix-core/src/main/java/io/matrix/cluster/NeuronClusterActor.java` | Inject metrics, structured logging, spans |
| `matrix-core/src/main/java/io/matrix/hades/HadesProtocol.java` | Metrics: alerts counter, isolation gauge |
| `matrix-core/src/main/java/io/matrix/mediator/InstanceMediator.java` | Metrics: driver gauges, tick counter |
| `matrix-core/src/main/java/io/matrix/minecraft/SurvivalRunner.java` | Inject metrics, spans per agent run |
| `matrix-core/src/main/java/io/matrix/MinecraftExperiment.java` | Refactor to `@ApplicationScoped`, CDI, improved fitness |
| `matrix-spigot/src/main/java/io/matrix/spigot/MatrixPlugin.java` | Structured logging, metrics per tick |

### 2.3. Remove

| File | Reason |
|------|--------|
| `LongEvolutionExperiment.java` | Replaced by `EvolutionCommand` (CLI) |
| `NativeCompileTest.java` | Quarkus handles native compilation natively |

---

## 3. Improved Fitness & Simulation

### 3.1. Current Problem

Agent fitness = `stepsSurvived * 0.5` dominates. Agent with tool=NONE can't mine ore. No crafting progression possible without tool upgrades.

### 3.2. Fix: Reward Shaping + Curriculum

| Change | Old | New |
|--------|-----|-----|
| Fitness formula | `blocksMined*1 + crafted*3 + survived*0.5 + tool*5` | `blocksMined*2 + crafted*10 + survived*0.02 + tool*20 + milestones` |
| Tool gating | Mine requires tool >= block.minTool | Agent can mine ANY block (with tool bonus for speed) |
| Hunger penalty | `hunger==0 → health--` on move only | Gradual hunger drain, eat gives +10 hunger (was +5) |
| Elite preservation | Top 2 parents always kept | Top 5 elites + tournament crossover |

### 3.3. Curriculum Milestones

| Milestone | Bonus | Triggers |
|-----------|-------|----------|
| First block mined | +50 | `blocksMined > 0` |
| First tool crafted | +100 | `toolTier > NONE` |
| Iron pickaxe | +200 | `toolTier >= IRON` |
| Diamond pickaxe | +500 | `toolTier >= DIAMOND` |

---

## 4. Dashboard (Grafana)

### 4.1. Panels

| Row | Panels |
|-----|--------|
| Overview | Neurons total, active, frozen; Health status |
| Evolution | Best fitness (line), Avg fitness (line), Generations/sec (gauge) |
| Actors | Messages/sec, errors/min, processing p50/p99 |
| HADES | Alerts/min, isolations total, recovery time |
| JVM | Heap used, GC pauses, threads |

---

## 5. Native Compilation Target

```bash
./gradlew build -Dquarkus.package.jar.type=native
# Produces: matrix-core/build/matrix-core-1.0.0-runner  (standalone binary)
```

Quarkus handles all reflection config (`-H:ReflectionConfigurationFiles`), resource bundling, and `native-image` flags automatically.

---

## 6. Self-Review

- [x] No placeholders — all file paths, class names, properties specified
- [x] Internal consistency — Quarkus config keys match classes and annotations
- [x] Scope — observability + simulation + Spigot covered; no scope creep
- [x] Ambiguity — fitness formula and curriculum milestones have explicit numeric values
