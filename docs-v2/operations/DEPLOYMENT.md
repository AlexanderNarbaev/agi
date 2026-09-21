# DEPLOYMENT

Производственное развёртывание MATRIX.

## Артефакты

- Docker-образ `matrix-core` (`matrix-core/Dockerfile` / `Dockerfile` репозитория).
- Helm-chart (если есть; см. `matrix-operator/` K8s-оператор).
- `docker-compose.prod.yml` для прод-стенда (Kafka, PG, Redis).

## Kubernetes (matrix-operator)

CRD MATRIX-проекта (см. `architecture/MODULES.md` и `operator/`):

| CRD | Группа / Версия | Назначение |
|---|---|---|
| `MatrixCluster` | matrix.io/v1alpha1 | один кластер = один демон MATRIX |
| `SignalModule` | matrix.io/v1alpha1 | один signal-модуль (think⇄media конвертер) |
| `TaskCell` | matrix.io/v1alpha1 | эфемерный ячейка-задача с бюджетом |

Пример:

```yaml
apiVersion: matrix.io/v1alpha1
kind: SignalModule
metadata:
 name: text-lexicon-v3
 namespace: matrix
spec:
 moduleName: text-lexicon
 version: v3
 mediaType: text
 frozen: true # FROZEN — изменения только через RFC
```

## Native-image

```bash
./gradlew :matrix-core:build -Dquarkus.native.enabled=true
# Mandrel / GraalVM CE: требует gu install native-image на сборщике
```

## Горизонтальное масштабирование

`matrix-cluster/NeuronClusterActor` — Pekko-кластер: каждая ячейка-clone запускается как actor внутри cell-router. Снепшоты — `snapshot/SnapshotStore` каждые N операций (по умолчанию — задаётся в `signal-modules`).

## Observability

- OpenTelemetry traces → `:4317` OTLP → Loki.
- Prometheus: `:9091/q/metrics`.
- `audit/HashChain` — append-only по `x-matrix-trace` header.

## Безопасность

- `api/TenantFilter` изолирует тенантов.
- `security/` — адаптер с ролями; FROZEN-этика (см. `CONSTITUTION.md` IV) доминирует над любым поведенческим тюнингом.
- Все действия через `events/KafkaEventJournal` — репликация в `ClusterSnapshot`.

## Секреты

- `secrets.env` (НЕ коммитится).
- `DOCS_REGISTRY_TOKEN` — для GH-pages деплоя через `.github/workflows/pages.yml`.
- `OPA_TOKEN` — для опционального policy-gate на проде.