# AGENTS.md — MATRIX (MENTAT)

Инструкции для ИИ-агентов разработки. Читать в начале каждой сессии; обновлять `wal/SESSION_WAL.md` в конце.

## Быстрые команды

```bash
./gradlew test                          # все тесты (1055+, обязательны зелёными перед PR)
./gradlew :matrix-core:test             # только ядро
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
./gradlew jacocoTestCoverageVerification # gate: покрытие ≥ 82%
docker compose up --build               # полный стек (core + PostgreSQL + Redis + Kafka + Minecraft)
docker compose -f docker-compose.dev.yml up -d   # только инфраструктура для локальной разработки
```

## Стек (FROZEN — не менять без RFC)

Java 25 · Quarkus 3.37.3 · Apache Pekko 1.6.0 · Kafka 3.7 (KRaft) · PostgreSQL 17 (R2DBC) · Redis 7 · Avro 1.12 · Gradle 9.x

## Жёсткие ограничения (constitution.md)

1. **K_MAX = 20** входов на MPDT-нейрон — никогда не менять (`TruthTable.java`).
2. **FROZEN-нейроны неизменяемы** (`FrozenEthicalFNL`, `FROZENFNLGuardian`) — запрещено любое изменение, мутация или обход; изменение только через RFC в ThePath + консенсус.
3. **Три запрета**: Не убивай. Не пытай. Не порабощай. Код, ослабляющий EthicalFilter/StructuralSafetyGuard, отклоняется без обсуждений.
4. Булева детерминированность ядра в рантайме: никаких недетерминированных вызовов внутри инференса нейронов.
5. Покрытие тестами не опускается ниже 82% (CI-gate).

## Do not touch

- `matrix-core/src/main/java/io/matrix/ethics/` — только через RFC + отдельный ревьюер
- `matrix-core/src/main/resources/avro/` — схемы Avro меняются только с миграцией
- `.github/workflows/`, `infra/k8s/` — только с обоснованием в PR
- `docs/archive/` — исторические документы, не редактировать

## Конвенции

- Статусы документов: `normative` / `experimental` / `archived` / `vision` — шапка обязательна.
- Новая функциональность = spec/RFC → plan → tasks → код + тесты (spec-driven, см. `docs/REFORM`).
- Экспериментальный код — в `research/experiments/EXP-XXX/`, не в основные пакеты.
- Каждая цифра в README/документации ссылается на воспроизводимый запуск в `benchmarks/`.
- Никаких заявлений «AGI», «не лжёт», «не забывает» в технических документах — только измеримые утверждения.

## Протокол сессии

1. Прочитать `wal/SESSION_WAL.md` (статус, активные задачи, защищённые зоны).
2. Перед изменением обучения/эволюции — свериться с `research/HYPOTHESES.md` (не дублировать отвергнутые гипотезы).
3. После работы: обновить `wal/SESSION_WAL.md` (Status / Active / Protected) и `research/METRICS.md`, если измерялось.
