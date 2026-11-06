---
layout: default
title: Архитектура
nav_order: 6
parent: Экосистема
permalink: /ru/ecosystem/architecture/
---

**Статус:** ACTIVE · **Обновлено:** 2026-11-06

# Архитектура

MATRIX — **монорепозиторий**, граф модулей которого измерен по файлам сборки Gradle и
скомпилированным классам, а не по проектному документу
([`docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md`](../../../engineering/W46-REPOSITORY-TOPOLOGY.md);
со стороны рантайма —
[`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../../../architecture/RUNTIME-TOPOLOGY.md)).

В `settings.gradle` включено **четырнадцать модулей**, ещё три закомментированы, веб-интерфейс
и Python- и JavaScript-SDK находятся вне Gradle, а в дереве остался один потерянный
вспомогательный файл.

## Модули и то, чем каждый из них является на самом деле

| модуль | что это | состояние |
|---|---|---|
| `matrix-core` | 1 248 файлов основного кода, 1 180 тестовых классов; эпистемическое хранилище, когнитивное хранилище, рабочий стол знаний, решатель, CLI | **единственное приложение Quarkus** — цепочка A |
| `matrix-brain-runtime` | 97 файлов; `TrueMindCycle` и его стадии, постоянное HDC-хранилище, трекер целей | **CLI** — REPL над `TrueMindCycle`. Ни одного `@Path` |
| `matrix-api-gateway` | 33 файла; `MinimalHttpServer` | **точка входа обслуживания** — цепочка B. Простой JDK `HttpServer`, а не приложение Quarkus |
| `matrix-quality` | 19 файлов; 14 гейтов-хранителей целей | CLI (`QualityCli`) |
| `matrix-sdk-java` | 12 файлов; `io.matrix.sdk.MatrixClient` и его DTO | публикует `io.matrix:matrix-sdk-java`, версия `0.1.0-T08-SNAPSHOT` — снапшот, а не выпущенный артефакт |
| `matrix-tools-distill` | 1 файл; офлайновый CLI дистилляции | **намеренно изолирован** — нет сборочной зависимости от `matrix-core`, свой тулчейн |
| `matrix-audit` | 6 файлов; `HashChainedLog` | **только в памяти** — в классе нет пути к файлу |
| `matrix-spigot` | 3 файла; Bukkit-плагин | **независимый тулчейн: Java 21**, против 25 везде. Это `JavaPlugin`, а не `main` |
| `matrix-operator` | 8 файлов; типы Kubernetes CRD | только типы — `TaskCellResource` это `CustomResource`, а **не** JAX-RS-ресурс |
| `matrix-billing` | 10 файлов | **фактически спит** — зависимости Stripe и Postgres отложены; 0 main, 0 `@Path` |
| `matrix-observability` | 5 файлов | **сирота** — входящего ребра `project(':matrix-observability')` нет нигде, а десять пользователей этого пакета импортируют `MatrixMetrics` из `matrix-core` |
| `pilots/smart-home-agent`, `pilots/edu-assessor`, `pilots/compliance-bot` | по 1 файлу | тонкие пилоты, зависящие только от `matrix-sdk-java` |
| `matrix-fpga`, `matrix-micro`, `matrix-ros2` | Amaranth, C и Python | **закомментированы в Gradle и спят** |
| `matrix-web-ui` | 397 файлов, проект на Next.js | **намеренно вне Gradle** — свой тулчейн и своя CI-задача |
| `matrix-sdk-python`, `matrix-sdk-js` | 9 и 6 файлов, с `src/` и своими тестами | вне Gradle; реестр называет их плановыми, чему дерево противоречит |
| `matrix-core/matrix-core/build.gradle` | 13 строк | **потерянный вспомогательный файл** — продублированный блок `jmhJar` с синтаксической ошибкой. Мёртвый артефакт |

## Две обслуживающие цепочки

```
                    цепочка A — эпистемическое приложение
   io.matrix.MatrixApplication (@QuarkusMain, matrix-core)
      слушает  9091 HTTP  +  9094 TCP  +  9093 multicast
      │
      ├── /v1/workbench    57 маршрутов  → KnowledgeWorkbench → KnowledgeStore
      ├── /v1/cognitive    11 маршрутов  → CognitiveService    → CognitiveStore
      ├── /v1  (chat)       6 маршрутов  → io.matrix.reasoning.BrainLoopService
      └── ещё 36 ресурсов               136 маршрутов
                                  ────────────────────
                                  цепочка A всего: 210 маршрутов


                    цепочка B — мозговой рантайм
   io.matrix.api.MinimalHttpServer (matrix-api-gateway)
      слушает 8765
      │
      ├── MATRIX_MODE=production ──► ProductionBrainClient ──► TrueMindCycle ──► BirBrainCycle
      │         (настоящий мозг)                                  ├── PersistentMind → data/mind/mind.sqlite
      │                                                          ├── NeuroModulatorHub
      │                                                          └── GoalTracker, HierarchicalMemory
      │
      └── ПО УМОЛЧАНИЮ ──► StubBrainCycle (87 строк)   ← что на самом деле обслуживает по умолчанию
                                  ────────────────────
                                  цепочка B всего: 28 маршрутов
```

**Цепочки не встречаются.** B не достигает ни одного класса эпистемического и когнитивного
хранилищ. Общее у них — субстрат, библиотека, а не путь к данным. Изоляцию доказывает
`scripts/w45-chain-isolation-check.sh` (16 проверок), а её способность краснеть показывает
`scripts/w45-chain-isolation-selftest.sh` (6 проверок).

**Два порта не заявлены.** 9094 (TCP) и 9093 (multicast) открывает `P2PNetwork`, и они не
появляются ни в одном свойстве конфигурации — открытый вопрос владельца F-02.

## Направление зависимостей

```
matrix-api-gateway  →  matrix-core          (собственные классы шлюза; изоляцию данных
                                              держит гейт, а не соглашение)
matrix-sdk-java     →  (ничего в ядре)
pilots/*            →  matrix-sdk-java
matrix-tools-distill→  (намеренно изолирован — нет сборочной зависимости от matrix-core)
matrix-web-ui       →  HTTP-поверхности цепочки A

matrix-core         ←  ни одного входящего ребра от слоя экосистемы
```

Правило слоя экосистемы: это транспорт и упаковка. Он не меняет ядро, а ядро от него не
зависит.

## Где лежат данные

| хранилище | файл | владелец |
|---|---|---|
| эпистемическое хранилище — схема **v19** | `data/knowledge/workbench.sqlite` | `io.matrix.knowledge.KnowledgeStore` (`matrix-core`) |
| когнитивное хранилище — схема v4, таблицы `cognitive_*` | **тот же файл**, со своим соединением | `io.matrix.cognitive.CognitiveStore` (`matrix-core`) |
| постоянное состояние мозгового рантайма | `data/mind/mind.sqlite` | `io.matrix.brain.runtime.PersistentMind` (`matrix-brain-runtime`) |
| база знаний HDC | `data/mind/hdc_kb.ndjson` — NDJSON, запись через атомарные temp-переименование | `io.matrix.brain.runtime.PersistentHdcStore` |
| журнал аудита | **только в памяти** | `io.matrix.audit.HashChainedLog` |

Карта хранения канонична в
[`docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md`](../../../architecture/DATA-AND-MEMORY-ARCHITECTURE.md).

## Чего здесь намеренно нет

- **Никакого кластера.** Прежняя версия топологии описывала PostgreSQL, Redis, Kafka и Loki
  с акторной ячейкой на сервис. Ничего из этого в работающей системе нет: измерение нашло
  один процесс Quarkus и один JDK-сервер, один файл SQLite в цепочке A и один в цепочке B,
  и ни Kafka, ни Redis, ни PostgreSQL, ни Loki ни в одном обслуживающем пути. Старый текст
  сохранён приложением в документе о топологии и помечен как проект, а не как топология.
- **Никакого native-исполняемого файла здесь.** Сборка native-image — `BLOCKED` на этом
  хосте: инструментарий GraalVM отсутствует, а native-scope отказывается запускаться, а не
  сообщает о проходе.
- **Никакой версии релиза.** Существующие теги старше нескольких волн работы.

## Документы, которые являются источником истины

- [`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../../../architecture/RUNTIME-TOPOLOGY.md) — что работает, что слушает порты и куда не достигает.
- [`docs-v2/architecture/CAPABILITY-REGISTRY.yml`](../../../architecture/CAPABILITY-REGISTRY.yml) — каждая возможность на четырёх осях.
- [`docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md`](../../../architecture/DATA-AND-MEMORY-ARCHITECTURE.md) — карта хранения.
- [`docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md`](../../../engineering/W46-REPOSITORY-TOPOLOGY.md) — граф модулей и точки входа.
- [`docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md`](../../../engineering/W46-ARCHITECTURE-TRUTH-MAP.md) — перепись константного пула, из которой взяты счётчики маршрутов.

## Дальше

- [Быстрый старт](../guide/getting-started.html) — запустить.
- [Топология рантайма](../../../architecture/RUNTIME-TOPOLOGY.html) — измеренный граф обслуживания.
- [Конституция](/ru/ecosystem/constitution/) — этические ограничения и замороженные зоны.
- [XAI подробно](/ru/ecosystem/xai/) — откуда берётся трасса объяснения.
