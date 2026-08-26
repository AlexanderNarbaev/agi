# STANDARDS-MATRIX

Версии зависимостей и причины пропусков. Проверено онлайн 2026-08-25 (maven-metadata).

| Компонент | Текущее | Источник | Решение |
|---|---|---|---|
| Java | 25.0.4 LTS | `gradle.properties` | ✅ новейшая LTS |
| Quarkus BOM | 3.38.3 | `matrix-core/build.gradle` | ✅ стабильный максимум (3.39 — только CR1) |
| GraalVM plugin | 1.1.10 | `matrix-core/build.gradle` | ✅ совместим |
| Avro | 1.12.2 | dep | ✅ патч-апгрейд (было 1.12.0) |
| ONNX Runtime | 1.29.0 | dep | ✅ minor-апгрейд (было 1.17.0), API совместим |
| kafka-clients | 4.3.1 | dep | ✅ мажор 4.x, миграция чистая (было 3.9.0) |
| Testcontainers | 1.21.3 | BOM | ⏸️ мажор 2.x требует пересборки явных артефактов |
| Postquantum ML-DSA | JEP 497 (JDK25) | native | ✅ без внешнего dep |
| ONNX Runtime GPU (Java) | — | — | ❌ требует системного CUDA 12 + cuDNN9 (нет в окружении) |

## Пропуски осознанные

- **Testcontainers 2.x**: мажор координат; наш build ссылается на `org.testcontainers:kafka:1.21.3`/`org.testcontainers:postgresql:1.21.3` явно — апгрейд требует пересмотра всех тестовых деклараций; запланировано на отдельный RFC.
- **Quarkus 3.39.0.CR1**: пре-релиз; остаёмся на стабильной 3.38.3.
- **GraalVM toolchain**: `gu install graalpy-graalvm` / `gu install native-image` не запускали в этом окружении — Mandrel-контейнер используется на CI; локально native-сборка не проверялась.

## Методика обновления

Мажорные апгрейды только с:
1. Проверкой maven-metadata (curl `<dep>/maven-metadata.xml`),
2. compileJava + целевые прогоны тестов,
3. при падении — откат + пометка BLOCKED.

Patch/minor — то же, но без RFC.
