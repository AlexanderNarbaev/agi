# Технологический стек (Java-first)

**Статус: normative · v1.0 (2026-07-31)** · Принцип: продакшн-путь — только JVM (компетенция владельца, операционная целостность, один рантайм наблюдаемости). Python — исключительно `research/` как одноразовая лаборатория, без выноса в сборку (Конституция VII.1).

## FROZEN-стек (действующий, меняется через ADR)

Java 25 · Quarkus 3.x · Apache Pekko · Kafka (KRaft) · PostgreSQL (R2DBC) · Redis · Avro · K8s/Minikube · Prometheus/Grafana/Jaeger · Gradle.

## Принимается в стек (новое)

| Технология | Роль | Обоснование |
|---|---|---|
| **DJL (Deep Java Library) + ONNX Runtime** | Запуск teacher LLM и внешних моделей из JVM (дистилляция SPEC-001, NLI, эмбеддинги) | Единственный зрелый Java-путь к современным моделям без Python в проде; модель как dependency: load → predict; контракт = ONNX-файл + документированный препроцессинг + golden-векторы в CI [^172^][^173^] |
| **jqwik** | Property-based тестирование чистой логики (автоматы, таблицы, CRDT, поток Риччи) | Свойства > примеров: границы состояний, идемпотентность merge, эквивалентность экспорта клауз |
| **JMH** | Все latency/throughput-заявления (NFR, «нс/решение») | Заявление без JMH-отчёта запрещено (Статья VI) |
| **ArchUnit** | Архитектурные инварианты как тесты: `@RequiresMaturity` (INV-2 SPEC-000), запрет LLM-вызовов из рантайм-пакетов, направление зависимостей | Инварианты, не проверяемые в CI, — декорация |
| **Testcontainers** | Интеграционные тесты (Kafka/Postgres/Neo4j) — уже частично есть | Сценарии SPEC-000 §2 как тесты |
| **Java Vector API + structured concurrency/virtual threads** | SIMD-оценка таблиц/клауз (SimdTruthTableEval развитие); массовые агенты/эпизоды песочницы | Java 25 даёт это нативно; batch-оптимизации на CPU — осознанный выбор проекта (анти-монокультура GPU) |
| **TLA+ / TLC** (внешний инструмент, не JVM) | Model checking инвариантов конституции, MA-гейтов, Noosphere-протоколов; мутационные тесты инвариантов | Индустриальный стандарт (AWS, Microsoft) [^169^][^170^]; спеки в `spec/tla/` |
| **CRDT (реализация — собственный Java-модуль)** | G-set/OR-set для знаний Noosphere, PN-counter для кредитов | SEC без координации [^171^][^178^]; готовых зрелых JVM-библиотек мало — модуль мал, пишется с jqwik-свойствами merge (коммутативность, ассоциативность, идемпотентность) |
| **Gillespie SSA (собственная Java-реализация)** | Стохастическая симуляция популяций навыков/знаний (FOUNDATIONS §4.1) | Тривиальный алгоритм, полный контроль |

## Отвергается / откладывается (с причинами)

| Кандидат | Решение | Причина |
|---|---|---|
| Python-продакшн (FastAPI-ядро в MATRIX) | отвергнуто | компетенция владельца — Java; второй рантайм = двойная эксплуатация; исключение: rag-system остаётся Python-проектом как отдельный продукт, интеграция по API/MCP |
| Deeplearning4j/ND4J для обучения | отложено | обучение происходит вне рантайма; для дистилляции хватает DJL-inference + собственных циклов; DL4J — если появится Java-обучение нейросетей (маловероятно) |
| TensorNEAT/JAX | отклонено для прода | Python-зависимость; заимствуются идеи (тензоризация популяций), не код |
| GPU-зависимость рантайма | отвергнуто | философия проекта: CPU-first, SIMD; GPU/FPGA — через Substrate Interface, не в критическом пути |
| Готовые agent-фреймворки (LangChain4j и т.п.) как ядро | отвергнуто | ядро — собственное детерминированное; LLM-фреймворки допустимы только на границе (teacher, guardrail-клиенты) |
| Neo4j внутри MATRIX | отклонено | граф живёт в rag-system; MATRIX ходит в него read-only через API (SPEC-003 INV-1) |

## Правила интеграции моделей (ONNX-контракт)

Любая внешняя модель входит в систему только как пакет: `model.onnx` + `MODEL_CARD.md` (входы/выходы, препроцессинг, токенизатор и его версия, нормировки) + golden-векторы (вход → ожидаемый выход). CI прогоняет golden-векторы на каждом обновлении модели/рантайма — расхождение = блок сборки (защита от дрейфа препроцессинга, [^172^] шаг E).

## Источники

[^169^]: TLA-Prover (arXiv) — https://arxiv.org/html/2606.06133v1
[^170^]: TLA+ for System Design — https://wal.sh/research/tla-plus-system-design/
[^171^]: What are CRDTs — https://loro.dev/docs/concepts/crdt
[^172^]: DJL practical deep dive — https://arbisoft.com/blogs/deep-java-library-djl-a-practical-deep-dive-for-java-python-and-hybrid-teams
[^173^]: Getting Started with DJL — https://blog.nashtechglobal.com/getting-started-with-deep-java-library-djl-architecture-basics/
[^178^]: Conflict-free replicated data types — https://dl.acm.org/doi/10.5555/2050613.2050642
