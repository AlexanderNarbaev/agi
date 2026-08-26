**Статус: normative · draft** · пересмотр 2026-08-26 (brain wave v5 operations drafts).

# DRAFT — Release Process

## Что

Шаги выпуска новой версии MATRIX: changelog сборка, smoke-tests, GH release tag, образ (Quarkus native → jib → ghcr).

## Шаги

1. `git tag -a vX.Y.Z -m "WAL: release vX.Y.Z"`.
2. CI собирает matrix-core native-образ; push в ghcr.
3. Draft GH release: changelog autoиз `git log vX.Y.Z-1..vX.Y.Z`.
4. Smoke integration: Kafka/Postgres/Redis — Testcontainers (если Docker ресурсы доступны).
5. Публикация.

## Метрики / Гейты

- Native-image размер ≤ 200 MiB.
- Smoke сценарий проходит за ≤60 секунд.

## Отложено

- Подписывание образов (cosign); SBOM attestation.
