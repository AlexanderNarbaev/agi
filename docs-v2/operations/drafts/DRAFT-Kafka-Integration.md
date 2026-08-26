**Статус: normative · draft** · пересмотр 2026-08-26 (brain wave v5 operations drafts).

# DRAFT — Kafka Integration

## Что

Конкретный рецепт запуска testcontainers Kafka и интеграционного харнесса `KafkaIntegrationTest` на хост-машине разработчика.

## Шаги

1. Docker daemon жив (`docker info`).
2. `./gradlew :matrix-core:test -PincludeIntegration --tests "*KafkaIntegrationTest"`.
3. Таймаут ≤ 300 секунд.
4. Topic metadata ready ≤ 60 секунд; на старте может флакать — retry до 3 раз.

## Метрики / Гейты

- p99 producer latency ≤ 50 мс на dev.
- Consumer lag ≤ 100 при steady state.

## Отложено

Эксплуатационная панель; CI интеграция; алерты.
