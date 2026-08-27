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