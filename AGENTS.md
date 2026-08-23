# AGENTS.md — Инструкции для ИИ-агентов и разработчиков

**Статус: normative.** Прочитай `CONSTITUTION.md` первым. При конфликте инструкций — конституция побеждает.

## Обзор проекта

**MATRIX (MENTAT)** — детерминированное нейро-символическое ядро верификации и безопасного исполнения для ИИ-систем. Каждое решение — проверяемая булева цепочка (BRC); этические и доменные ограничения зашиты в FROZEN-слой и формально верифицируемы (TLA+ спеки в `formal/`). Одинаковое состояние и вход — всегда одинаковый выход.

Стек: **Java 25** · **Quarkus 3.37.3** · **Apache Pekko 1.6.0** (акторы, Scala-артефакты `_2.13`) · PostgreSQL (R2DBC) · Redis (Lettuce) · Kafka (KRaft) · Avro · ONNX Runtime · JaCoCo · SpotBugs · JMH · GraalVM native image. 1055+ тестов, покрытие ≥82% (METHOD) — гейт в CI.

Архитектурные слои (пакеты `io.matrix.*` в `matrix-core/src/main/java`):

- **Core (ядро)** — `neuron/` (TruthTable k ≤ 20, DecisionTree), `evolution/` (генетический алгоритм), `cauldron/` (автономное рождение FNL).
- **Nerve (нервная система)** — `cluster/` (Pekko NeuronClusterActor), `mediator/` (драйверы Energy/Curiosity/Safety), `ethics/` (EthicalFilter, StructuralSafetyGuard, LieDetector, `frozen/`), `hades/` (обнаружение повреждений, Eleutheria).
- **AI/ML** — `reasoning/` (BrcChain, max 5 шагов), `rag/` (Boolean/Hybrid RAG), `vqvae/`, `mcts/`, `agent/` (AgentLoop Observe→Think→Act, AgentGenome), `memory/` (5-уровневая память с drift detection), `bir/` (Boolean Intermediate Representation — keystone, SPEC-002).
- **Память и события** — `events/` (Kafka/PostgreSQL event journal), `snapshot/`, `redis/`.
- **API и интеграции** — `api/` (OpenAI-совместимый `/v1/chat/completions`, `/v1/embeddings`, REST `/matrix/*`, WebSocket), `dialog/` (Telegram-бот), `minecraft/`, `mcp/`.

Целевая архитектура (BIR, Developmental Loop, субстраты JVM→FPGA→квантовые): `docs/vision/ARCHITECTURE.md`. Глоссарий (MPDT, FNL, BRC, BIR): `docs/GLOSSARY.md`. Карта документации: `docs/INDEX.md`.

## Структура репозитория

| Путь | Что это |
|---|---|
| `matrix-core/` | Основной модуль: Quarkus-приложение, весь рантайм и тесты (`src/main`, `src/test`, `src/jmh`) |
| `matrix-spigot/` | Spigot/Paper 1.20.4 плагин для Minecraft-ботов (Java 21 toolchain, fatJar) |
| `matrix-operator/` | Kubernetes-оператор (fabric8 kubernetes-client 7.2.0) |
| `matrix-fpga/` | Компилятор таблиц истинности `.ldn` → Verilog (Python-утилиты, Yosys/nextpnr, iCE40) |
| `matrix-micro/` | C-библиотека MPDT-нейронов для MCU (ESP32/Arduino/STM32) |
| `matrix-ros2/` | ROS2-нода и мост (Python, `package.xml`, `setup.py`) |
| `formal/` | TLA+ спецификации (Consensus, HashChain, MPDTNeuron, BotEthicsPipeline, FrozenEthicalFNL) + `.cfg` |
| `infra/` | docker-compose для observability: Prometheus, Grafana, Loki, Promtail, k8s-манифесты |
| `docs/` | Вся документация: `spec/` (SPEC-000…003), `design/` (DESIGN-01…13), `research/` (гипотезы, EXP-отчёты), `engineering/` (ROADMAP, ADR), `vision/`, `science/`, `agents/`, `archive/` (история L0–L23) |
| `scripts/` | Research-only Python-скрипты (см. `scripts/README.md`) |
| `models/` | Локальные модели и исторические pretrained-артефакты (карантин, не в рантайме) |
| `minecraft-server/` | Локальный Paper-сервер для ручных экспериментов |

Gradle multi-module build (`settings.gradle`): `matrix-core`, `matrix-spigot`, `matrix-operator`. Подпроекты `matrix-fpga`, `matrix-micro`, `matrix-ros2` — самостоятельные, в Gradle не входят.

## Быстрые команды

```bash
./gradlew test                              # все JVM-тесты (integration/** исключены — нужен Docker)
./gradlew :matrix-core:test                 # только matrix-core
./gradlew :matrix-core:test -PincludeIntegration   # + интеграционные (Testcontainers: Kafka, PostgreSQL)
./gradlew jacocoTestCoverageVerification    # гейт покрытия ≥82% METHOD — не понижать
./gradlew :matrix-core:spotbugsMain         # статический анализ
./gradlew :matrix-core:jmh                  # JMH-бенчмарки (-PjmhBenchmark=NeuronBenchmark)
./gradlew :matrix-core:quarkusDev           # dev-режим (нужна инфраструктура, см. ниже)
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
./gradlew :matrix-core:buildNativeContainer # GraalVM native image через Mandrel-контейнер
docker compose up -d                        # полный стек (core + PostgreSQL + Redis + Kafka + Minecraft)
docker compose -f docker-compose.dev.yml up -d   # только инфраструктура для локальной разработки
./scripts/matrix-minikube.sh start          # Minikube K8s (деплой, DNS, NodePort-сервисы)
```

Компиляция и тесты требуют флаг `--add-modules=jdk.incubator.vector` (уже настроен в `matrix-core/build.gradle`).

Конфигурация рантайма: `matrix-core/src/main/resources/application.properties` (HTTP :9091, Prometheus `/metrics`, OTEL → Jaeger :4317, health `/q/health`, PostgreSQL :5433, Redis :6379). Ключевые env-переменные (BRC_MAX_STEPS, RAG_TOP_K, MCTS_ITERATIONS, KAFKA_BOOTSTRAP_SERVERS и др.) — таблица в `README.md` (секция «Конфигурация»).

## Тестирование

- **JUnit 5** — юнит и интеграция; **jqwik** — property-based для чистой логики; **Testcontainers** (Kafka, PostgreSQL) — для `**/integration/**`; **AssertJ** — ассерты; Pekko TestKit — для акторов.
- Тесты лежат зеркально исходникам: `matrix-core/src/test/java/io/matrix/<пакет>/`. Интеграционные — в пакете `integration/` (по умолчанию исключены).
- Новый код — с тестами. Coverage gate 82% METHOD не понижать; список исключений JaCoCo (experimental/pilot-пакеты) — в `matrix-core/build.gradle`, расширять осознанно.
- Известная проблема (WAL): полный прогон (~298 тест-классов) может упираться в OOM/timeout ~4–5 мин на слабых машинах — гоняй пакеты батчами.

## Жёсткие ограничения (нарушение = отклонение PR)

1. **K_MAX = 20 для TT-формы** — не увеличивать размер таблиц истинности; рантайм исполняет только BIR-артефакты (TT/CLAUSESET/BDD); «сырые» структуры обучения — вне рантайма.
2. **FROZEN-слой и FROZEN-документы** — не изменять: `matrix-core/src/main/java/io/matrix/ethics/frozen/`, `CONSTITUTION.md`, схемы `matrix-core/src/main/resources/avro/` (только обратимо-совместимые изменения), `.github/workflows/**`.
3. **Четыре запрета** (не убий / не пытай / не порабощай / не размножайся без ведома) — не ослаблять, не обходить, не «переинтерпретировать». Реализованы архитектурно (FROZEN-слой + StructuralSafetyGuard), не промптами.
4. **Детерминизм рантайма** — никаких LLM-вызовов, случайности без seed и wall-clock в рантайм-контуре решений. Обучение может быть стохастичным — вне рантайма.
5. **Coverage gate** не понижать; новый код — с тестами.
6. **Продакшн-код — Java.** Python только в `docs/research/` и `scripts/` (CONSTITUTION VII.1). Любой вызов Python из Java — за guard `Boolean.getBoolean("matrix.research.enabled")` с fail-fast; все `scripts/*.py` несут header `# MATRIX RESEARCH-ONLY`.
7. **Запрещённые claims** (Статья VI): «AGI», «не лжёт», «не забывает», «не может быть использован во вред», числа без бенчмарка. Встретил такие строки в редактируемых файлах — исправляй на проверяемые формулировки.

## Безопасность

- Никогда не коммитить секреты и токены (TELEGRAM_BOT_TOKEN, пароли БД); `infra/.env` — локальный, в репо не попадает.
- SpotBugs-исключения — `matrix-core/config/spotbugs/exclude.xml`, OWASP-саппрессии — `matrix-core/config/owasp-suppressions.xml`; не расширять без причины.
- Аудит решений — через хэш-цепочки BRC (заголовок `x-matrix-trace`) и hash-chain спеки в `formal/`; не отключать аудит и этические фильтры «для отладки» в коммитимом коде.
- Конвертация весов трансформеров (`scripts/pretrain_neurons.py`, `models/pretrained/`) — в карантине: случайная выборка k≤20 весов не сохраняет семантику. Не выдавать через `/v1/models`, не использовать в рантайме; замена — SPEC-001.

## CI/CD и деплой

GitHub Actions (`.github/workflows/`): `ci.yml` (JVM-тесты + JaCoCo-гейт 82% + Codecov + SpotBugs), `native.yml` (native-сборка), `tla.yml` (проверка TLA+ спек), `pages.yml` (GitHub Pages из `docs/`).

Деплой: Docker Compose (полный стек, `Dockerfile*` в корне) или Minikube (`scripts/matrix-minikube.sh`, манифесты `infra/k8s/`). Подробности: `docs/DEPLOYMENT.md`, `docs/RUNBOOK.md`, `docs/engineering/JAVA_NATIVE.md` (GraalVM native, JMM).

## Конвенции документов

- Статусы в шапке каждого .md: `FROZEN` (только RFC), `normative` (спеки; изменения — с changelog в шапке: дата, причина, суть), `living` (research/, обновляется экспериментами), `ephemeral` (WAL.md).
- Ссылки на секции — URI-формат: `SPEC-001#metrics`, `AGENTS.md#быстрые-команды`. Запрещены ссылки «см. документ X» без секции.
- Спеки — это IPC между человеком и агентом. Изменение, не записанное в файл, не существует.

## Протокол сессии (WAL)

В начале сессии прочитай `WAL.md`. В конце — перепиши его по шаблону из самого файла: Активный фокус / Правила сессии / Что сделано / Следующее действие / Известные проблемы. WAL — checkpoint, не лог: детали реализации — в спеках и git-истории. Заверши сессию milestone-коммитом (`git commit -m "WAL: <что сделано>"`).

## Специализированные протоколы

При работе в соответствующих зонах обязательны файлы `docs/agents/`: `AGENTS-MODULES.md` (модули сигналов, DESIGN-06 — зоны `signal-modules/**` и `matrix-io/**`, пока планируются), `AGENTS-RESEARCH.md` (исследования и статьи, `docs/research/**`). Карта и правила области действия — `docs/agents/README.md`. Специализированный файл дополняет корневой, не отменяет его.

## Правила работы

1. **Одна сессия — один SPEC.** Нашёл постороннюю проблему — запиши в WAL «Известные проблемы», не чини мимоходом (кроме блокеров).
2. **Малые правки** (≤ ~30 строк): WAL-запись + тест + короткий diff. **Фичи** — полный цикл: specify → clarify → plan → tasks → analyze → implement, гейт analyze перед implement.
3. **Каждый обучающий/эволюционный цикл** — с объявленным монотонным функционалом Φ (CONSTITUTION, Статья III). Нет Φ — не запускай.
4. **Эксперименты** — только через карточки в `docs/research/HYPOTHESES.md` (статусы proposed → running → accepted/rejected/superseded); гипотеза и метрики фиксируются до запуска; обязательная базовая линия.
5. **Сессии короткие:** блоки ≤ 30–45 минут; лучше milestone-коммит и новая сессия, чем дрифт контекста.

## Куда что класть

| Артефакт | Путь |
|---|---|
| Спеки фич | `docs/spec/SPEC-NNN-*.md` |
| Проектные спецификации (алгоритмы, структуры данных) | `docs/design/DESIGN-NN-*.md` |
| Гипотезы и EXP-карточки (preregistration) | `docs/research/HYPOTHESES.md` |
| Отчёты экспериментов | `docs/research/reports/EXP-NNN-report.md` |
| Реестр метрик | `docs/research/METRICS.md` |
| Архитектурные решения | `docs/engineering/ADR-NNN-*.md` |
| Агентные протоколы зон | `docs/agents/AGENTS-<ZONE>.md` (+ строка в docs/agents/README.md) |
| Разовые исследовательские скрипты | `scripts/` или `docs/research/` (Python допустим только здесь) |
| Формальные спецификации | `formal/*.tla` (+ `.cfg`) |

## Agent System Prompt

The primary agent operates as a **Universal AI Coprocessor** (see `.opencode/skills/coprocessor/SKILL.md`).

### Core Protocols

| Protocol | Description |
|----------|-------------|
| **Dual-Process Reasoning** | System 1 (fast: edits, grep, fixes) / System 2 (slow: analysis, planning, multi-file refactors). Escalate after 2 failures or >3 files touched. |
| **Memory Hierarchy** | WAL (session journal) → Specs (persistent designs) → Artifacts (ground truth). Artifacts override stale specs. |
| **Shared State = IPC** | Files are the communication protocol. Read before action, verify after write. `.opencode/state/` for inter-agent coordination. |
| **Keyboard Correction** | Auto-detect RU↔EN layout mismatch. Silent for unambiguous, confirm for ambiguous. Log to WAL with `[KB]`. |
| **CO-STAR Output** | Context → Objective → Steps → Thinking → Answer → References. Skip for trivial outputs. |
| **Memory Anchor** | Every response starts with `[CTX: domain]`. Enables context resumption after compaction. |
| **Source Ladder** | Official docs > authoritative secondary > encyclopedias > model knowledge. Flag tier: `[L1]`–`[L4]`. |

### Hard Gates
- Never emit secrets. Redact with `***`.
- Never delete code you don't understand. `#S2` analyze first.
- Never skip WAL. Journal every consequential decision.
- Never speculate. Flag `[speculative]` when confidence < 80%.
