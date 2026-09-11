# RUNBOOK

## Поднять dev-окружение

```bash
# 1. Инфраструктура
docker compose -f docker-compose.dev.yml up -d
# kafka:9092, postgres:5432, redis:6379, loki:3100

# 2. Сборка
./gradlew :matrix-core:compileJava

# 3. Quarkus dev
./gradlew :matrix-core:quarkusDev
# HTTP: http://localhost:9091
# Health: http://localhost:9091/q/health
# Metrics: http://localhost:9091/q/metrics (Prometheus)
# OpenAI compat: POST http://localhost:9091/v1/chat/completions
# MCP: см. mcp/MatrixMcpServer (stdio)
```

## Конфиг через env

| Env | Назначение |
|---|---|
| `BRC_MAX_STEPS` | лимит шагов BRC-цепочки |
| `RAG_TOP_K` | top-K retrieval |
| `MCTS_ITERATIONS` | итерации MCTS/LATS |
| `KAFKA_BOOTSTRAP_SERVERS` | bootstrap для `events/` |
| `MATRIX_BIR_MAX_LITERALS` | лимит ClauseSetForm |

Реализация: `io.matrix.runtime.RuntimeLimits`.

## Типовые сценарии

| Сценарий | Команда |
|---|---|
| Запуск всех юнит-тестов matrix-core | `./gradlew :matrix-core:test` |
| Целевой прогон пакета | `./gradlew :matrix-core:test --tests "io.matrix.bir.*"` |
| JMH Benchmark | `./gradlew :matrix-core:jmh -PjmhBenchmark=BatchEvaluatorBenchmark` |
| Кросс-профиль (R2DBC) | `:matrix-core:test -PcrossProfile` |
| GPU-нога (Python) | `python3 scripts/bench_gpu_vs_bir.py models/teacher/teacher_ffn16.onnx` |
| CPU-нога EXP-009B | `./gradlew :matrix-core:test --tests "io.matrix.distill.Exp009bCpuVsOnnxTest"` |

## Troubleshooting

| Симптом | Решение |
|---|---|
| `teacher.onnx не найден` | `python3 scripts/gen_teacher_onnx.py` (research-only) |
| `cudaEP=unavailable` | поднять системный CUDA 12 + cuDNN9 + `pip install onnxruntime-gpu` |
| Testcontainers fails | проверить, что Docker daemon жив (`docker info`) |
| Ошибка покрытия | JaCoCo ≥82% METHOD gate; см. [engineering/INVARIANTS.md](../engineering/INVARIANTS.md) |
| Git Push отклонил | проверить токен GitHub; секреты в [operations/DEPLOYMENT.md](DEPLOYMENT.md) |
| R2DBC-миграция | `./gradlew :matrix-core:test -PincludeIntegration --tests "*Integration*"` |

## Checklists для коммита

1. `./gradlew :matrix-core:test --tests "io.matrix.<новый_пакет>.*"` зелёный.
2. INV-1 (для bool-логики): source-scan `bir.Inv1SourceGuardTest` тоже зелёный.
3. `git status --short`: только заявленные изменения.
4. Сообщение: `WAL: <краткое описание>`.
5. После коммита: `git push origin HEAD`.

См. также [`operations/DEPLOYMENT.md`](DEPLOYMENT.md).

## Native Build Status — Wave H.3 (RUN 321)

**Status: BLOCKED, RFC required to resolve.** JVM-mode is the production
target. The blocker is documented here with concrete fix paths so any future
session can resume with full context.

### Attempted (RUN 18, RUN 53, RUN 55, RUN 64)

| Step | Date | Result |
|---|---|---|
| `nativeCompile` with bundled GraalVM CE 25.0.2 | 2026-09-05 (RUN 18) | `UnsupportedFeatureException` cascading; `NoClassDefFoundError` for `io.netty.resolver.dns.DnsAddressResolverGroup` and `org.tukaani.xz.XZ` |
| Class-init list extension 5 → 17 entries | RUN 18 | Still fails — the missing classes come from Quarkus runtime reflection paths, not application code |
| Mandrel container pull | RUN 55 | 401 Unauthorized — Mandrel requires a Red Hat subscription token |
| CDI compat fixes (`OnnxRuntimeAdapter`, `QwenModelAdapter`) | RUN 64 | `@ApplicationScoped` + `@ConfigProperty` + `onStart`; native-build still fails downstream |

### Concrete Fix Paths (any of these unblocks the build)

1. **Mandrel subscription token** (preferred — full Quarkus native parity)
   - Obtain a Red Hat developer subscription (free for individuals)
   - Set `GRADLE_MANDREL_TOKEN` env var
   - Switch Quarkus `graalvm-build-time` builder to `docker` + Mandrel image `quay.io/quarkus/mandrel-for-jdk-21-rhel8`
   - Re-run `./gradlew :matrix-core:build -Dquarkus.native.enabled=true`
   - Expected: full native compile; runtime memory ~50 MB vs ~250 MB JVM

2. **Replace `org.apache.pekko` with raw `akka-actor` 2.6.x** (heavy refactor)
   - Pekko 1.x pulls in `io.netty.resolver.dns` reflective paths not handled by Graal CE
   - Roll back to Akka 2.6.x or replace the actor system with `java.util.concurrent` primitives
   - Re-run native compile — should drop the cascading failures
   - Cost: 2–4 weeks of refactor work in `matrix-operator`

3. **`--report-unsupported-elements-at-runtime` fallback** (cheapest — gets a binary)
   - Add to `application.properties`:
     ```
     quarkus.native.report-unsupported-elements-at-runtime=true
     quarkus.native.fallback=true
     ```
   - Build produces a binary that throws `UnsupportedFeatureException` at runtime for unsupported features; user sees a clear error
   - Acceptable for development/CI; NOT acceptable for production (CONSTITUTION VIII)

4. **Drop native entirely — JVM mode is production target** (current choice)
   - `./gradlew :matrix-core:quarkusDev` or `:matrix-core:quarkusRun` — JVM with Quarkus REST
   - Memory: ~250 MB RSS, startup ~1.5s
   - All current features (GPU ONNX inference, 24-block chain, BPE, LM head, sandbox UI) work
   - This is the mode the system has been operated in since RUN 1

### Decision

We chose option 4 (JVM mode) for production. Native build remains a
deferred optimisation — when the throughput-per-watt or cold-start budget
demands it, execute option 1 (Mandrel token) first; option 2 (Pekko
replacement) is the long-term engineering path.

### Smoke fallback evidence (RUN 321)

```bash
./gradlew :matrix-core:compileJava    # PASS
./gradlew :matrix-core:test --tests "io.matrix.api.*"  # 617 tests, 0 failures
curl http://localhost:9091/q/health   # 200 OK (Quarkus dev mode)
```

All operations green in JVM mode. The native-build blocker is documented,
not blocking any current functionality.

См. также [`research/reports/EXP-MATRIX.36-native-attempt.md`](../research/reports/EXP-MATRIX.36-native-attempt.md) для полного attempt log.
