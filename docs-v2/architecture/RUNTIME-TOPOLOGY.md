# RUNTIME-TOPOLOGY

Конфигурация процессов и потоков рантайма. Соответствует `architecture/OVERVIEW.md`.

## Сервисы

| Сервис | Артефакт | Порт | Конфиг |
|---|---|---|---|
| Quarkus | matrix-core | 9091 (HTTP), 9090 (OTel), 8443 (TLS) | application.properties |
| PostgreSQL | R2DBC | 5433 (dev) | `quarkus.datasource.reactive.url` |
| Redis | Jedis | 6379 | `redis.uri` |
| Kafka | kafka-clients 4.3 | 9092 (dev) | `KAFKA_BOOTSTRAP_SERVERS` env |
| Loki (logging) | JSON-логирование | 4317 (OTel) | `quarkus.log.console.json=true` |

## Конвейер кластера (cell)

```
[ ingress /api/v1/* ] ← OpenAI compat + MCP
 |
 v
[ ai/AgentLoop ] ──► [ brain/Viewpoint ] ──► [ neuron/NeuronLayer.eval → bir/BooleanRuntime ]
 | |
 | v
 ├────────► [ events/KafkaEventJournal (append) ] [ bir/TtForm (cache: actor/federation/FORM_CACHE) ]
 | |
 v v
[ dialog/TelegramBotService ] [ audit/HashChain (x-matrix-trace) ]
```

## Кластерный cell-akka (Pekko 1.6)

`cluster/NeuronClusterActor` — actor-узел на одну ячейку сигмоид/Tsetlin-нейронов. Маршрутизация по `TopologyCache`, бюджет памяти через `ConjugateBudgeter`, snapshot через `snapshot/SnapshotStore` каждые N операций.

## Operational SLO (минимальные)

| Метрика | Цель | Реальное (synthetic) |
|---|---|---|
| BIR eval латентность (per-call, CPU) | ≤ 1 µs | ~62 нс (EXP-009C, FFN16 distilled) |
| GPU latency per-call (RTX 5070 Ti, fp32 FFN16) | reference | 17.25 µs (reference) |
| GPU batch throughput N=2000 | reference | 0.020 ms |
| ORT-CPU batch N=2000 | reference | 18.68 ms |

## Безопасность

- `api/TenantFilter` — изоляция тенантов (X-Matrix-Tenant header).
- `security/` — Spring-Security-подобный адаптер; FROZEN-этика доминирует.
- `audit/HashChain` — append-only цепочка; `events/KafkaEventJournal` реплицируется в `ClusterSnapshot` для воспроизводимости.

## Integration tests (Testcontainers 1.21.3)

- KafkaIntegrationTest — Testcontainers Kafka (1.21.3); чувствителен к таймаутам метаданных брокера (зафиксирован флейк на медленном хосте).
- PostgreSQLIntegrationTest, EndToEndIntegrationTest, AgentLoopIntegrationTest, BrcIntegrationTest, BooleanRagIntegrationTest, CompressionIntegrationTest и др. — см. `matrix-core/src/test/java/io/matrix/integration/`.