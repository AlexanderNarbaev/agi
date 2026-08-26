**Статус: normative · draft** · пересмотр 2026-08-26 (brain wave v5 operations drafts).

# DRAFT — Operator-Runbook

## Что

Конкретные рецепты запуска k8s-оператора `matrix-operator` на minikube/Kind-площадке + прод-чеклист.

## Цели

- Один файл ≤ 100 строк; конкретные команды без воды.
- Совместимо с CURRENT specs (`operations/RUNBOOK.md`).

## Содержание

- Предварительные требования: `kubectl`, доступ к кластеру.
- `kubectl apply -f matrix-operator/artifacts/` (CRD first, operator second).
- Smoke: создать `SignalModule`, проверить readiness probe.
- Rollback: откат CRD оператора + reconcile-off annotation.

## Метрики / Гейты

- Cluster сразу после deploy: оператор Ready ≤ 30 секунд.
- Все CRD создаются без валидационных ошибок.
- Один из label-ов на CRD = `matrix.io/egress-policy: minimize` — обязателен.

## Отложено

- helm-chart; kaniko-build.
