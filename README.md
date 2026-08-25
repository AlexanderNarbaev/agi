# MATRIX (MENTAT)

Детерминированное нейро-символическое ядро верификации и безопасного исполнения для ИИ-систем.

Каждое решение — проверяемая булева цепочка. Этические и доменные ограничения неизменяемы на уровне FROZEN-слоя и формально верифицируемы. Одинаковое состояние и вход — всегда одинаковый выход.

**1055+ тестов** · **83.7% покрытие (METHOD)** · **Java 25** · **Quarkus 3.37.3** · **Apache Pekko 1.6.0**

> Рамка честности: инженерные гарантии этого README подкреплены кодом и бенчмарками либо явно помечены как цели. Долгосрочное исследовательское видение (когнитивные архитектуры общего назначения) вынесено в `docs/vision/OPEN_PROBLEMS.md` и не является обещанием. Правила формулировок — `CONSTITUTION.md`, Статья VI.

---


## Статус проекта (2026-08-25)

- **Ядро BIR**: компилятор + TT/CLAUSESET/BDD, единая точка исполнения (INV-1 страж в CI), миграция 37 call-sites.
- **Гипотезы**: H-010 **accepted** (WiSARD ×242 быстрее, точнее 9/9); H-002/H-003 refuted-toy с пинами.
- **Стек**: Java 25 · Quarkus 3.38.3 · GraalVM plugin 1.1.10 · Avro 1.12.2 · ONNX Runtime 1.29.0 · Kafka-clients 4.3.1 · Postquantum ML-DSA (ELSP v2).
- **Карта работ**: [docs/engineering/PLAN-FULL-IMPLEMENTATION.md](docs/engineering/PLAN-FULL-IMPLEMENTATION.md) · реестр вызовов: [DESIGN-14 annex](docs/engineering/DESIGN-14-call-site-audit.md).

## Документация

| Документ | Содержание |
|---|---|
| [CONSTITUTION.md](CONSTITUTION.md) | Аксиомы, инварианты, governance (FROZEN-документ) |
| [AGENTS.md](AGENTS.md) | Инструкции для ИИ-агентов и разработчиков |
| [docs/INDEX.md](docs/INDEX.md) | Карта всей документации |
| [docs/vision/ARCHITECTURE.md](docs/vision/ARCHITECTURE.md) | Целевая архитектура |
| [docs/spec/](docs/spec/) | Спецификации фич (SPEC-000…003) |
| [docs/research/](docs/research/) | Гипотезы, метрики, протокол экспериментов |
| [docs/engineering/ROADMAP.md](docs/engineering/ROADMAP.md) | План работ по этапам с измеримыми критериями |
| [docs/GLOSSARY.md](docs/GLOSSARY.md) | Термины (MPDT, FNL, BRC, BIR и др.) |
| [docs/API.md](docs/API.md) | REST API |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Деплой |
| Архив | `docs/archive/` — прежние спецификации L0–L23 и планы (история) |

Ссылки: [Сайт](https://alexandernarbaev.github.io/agi/) · [Gitverse](https://gitverse.ru/AlexandrNarbaev/agi) · [MPDT-песочница](https://alexandernarbaev.github.io/agi/sandbox.html)

---

## Архитектура (обзор)

```
Ядро (Core)                Нервная система (Nerve)        Ноосфера (Noosphere)
┌──────────────┐          ┌──────────────────────┐      ┌──────────────────────┐
│ TruthTable   │          │ NeuronClusterActor   │      │ NoosphereRegistry    │
│ DecisionTree │─────────▶│ EventJournal         │─────▶│ KnowledgeIndex       │
│ EvolutionLoop│          │ InstanceMediator     │      │ CreditModel          │
│ GeneticOper. │          │ EthicalFilter        │      │ GlobalMediator       │
│ Cauldron     │          │ ConsensusEngine      │      │ DigitalShadow        │
│ HADES        │          │ TaskScheduler        │      │ CivilizationCouncil  │
│ Eleutheria   │          │ AgentBrain           │      │ RegenerativeEconomics│
└──────────────┘          │ OpenAI API           │      └──────────────────────┘
                          └──────────────────────┘
Прикладные контуры:
┌──────────────────────────────────────────────────────────────────────┐
│ BRC (Boolean Reasoning Chain) · Boolean/Hybrid RAG · VQ-VAE Proxy    │
│ MCTS · Agent Loop (Observe→Think→Act) · Agent Genome                 │
│ Structural Safety Guard · Hierarchical Memory (5 ур., drift detect)  │
│ SCADA Pilot (Safety Monitor: CONTINUE/WAIT/SHUTDOWN) · Lie Detector  │
└──────────────────────────────────────────────────────────────────────┘
Инфраструктура: Docker Compose · Minikube K8s · PostgreSQL · Redis ·
Kafka (KRaft) · Prometheus/Grafana/Jaeger · CI/CD (GitHub Actions) + JaCoCo
```

Целевая архитектура (Boolean Compute Layer / BIR, Developmental Loop, субстратные бэкенды JVM→FPGA→квантовые) — `docs/vision/ARCHITECTURE.md`. Переход выполняется поэтапно по `docs/engineering/ROADMAP.md` без остановки работающей системы.

---

## Быстрый старт

### Вариант 1: Docker Compose (рекомендуется)

```bash
# Полный запуск: matrix-core + PostgreSQL + Redis + Kafka + Minecraft
docker compose up --build

# Только инфраструктура (для локальной разработки)
docker compose -f docker-compose.dev.yml up -d
./gradlew :matrix-core:quarkusDev
```

**Сервисы после запуска:**

| Сервис | URL | Порт |
|--------|-----|------|
| matrix-core REST API | http://localhost:8080 | 8080 |
| OpenAI Chat API | http://localhost:8080/v1/chat/completions | 8080 |
| Prometheus метрики | http://localhost:9091 | 9091 |
| Minecraft Paper | localhost:25565 | 25565 |
| PostgreSQL | localhost:5432 | 5432 |
| Redis | localhost:6379 | 6379 |
| Kafka | localhost:9092 | 9092 |

### Вариант 2: Minikube K8s

```bash
./scripts/matrix-minikube.sh start    # minikube + сборка + деплой + DNS
./scripts/matrix-minikube.sh status   # статус
./scripts/matrix-minikube.sh stop     # остановить
```

**Сервисы (NodePort):** matrix-core `matrix.local:30091` · Grafana `grafana.local:30300` · Prometheus `prometheus.local:30090` · Jaeger `jaeger.local:31686` · MinIO `minio.local:30900` · Minecraft `minecraft.local:32565`.

### Сборка и тесты

```bash
./gradlew test                    # все тесты
./gradlew :matrix-core:test       # только matrix-core
./gradlew jacocoTestCoverageVerification   # гейт покрытия
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
```

---

## Реализованные компоненты

### Ядро (Core)

| Компонент | Файл | Описание |
|-----------|------|----------|
| TruthTable | `neuron/TruthTable.java` | Булева таблица истинности (k ≤ 20 входов) |
| DecisionTree | `neuron/DecisionTree.java` | Дерево решений на базе TruthTable |
| EvolutionLoop | `evolution/EvolutionLoop.java` | Генетический алгоритм (селекция, скрещивание, мутация) |
| GeneticOperators | `evolution/GeneticOperators.java` | Операторы ГА: crossover, mutation |
| CauldronProtocol | `cauldron/CauldronProtocol.java` | Автономное рождение FNL (compressed neuron clusters) |

### Нервная система (Nerve)

| Компонент | Файл | Описание |
|-----------|------|----------|
| NeuronClusterActor | `cluster/NeuronClusterActor.java` | Pekko-актор для кластерной обработки |
| InstanceMediator | `mediator/InstanceMediator.java` | Драйверы: Energy, Curiosity, Safety |
| EthicalFilter | `ethics/EthicalFilter.java` | Фильтрация по Четырём запретам |
| StructuralSafetyGuard | `ethics/StructuralSafetyGuard.java` | Process-based safety (tool removal, human gate) |
| HADES | `hades/HadesProtocol.java` | Обнаружение повреждений и восстановление |
| Eleutheria | `hades/Eleutheria.java` | Контролируемый отказ/освобождение от дрейфа |

### AI/ML

| Компонент | Файл | Описание |
|-----------|------|----------|
| BrcChain | `reasoning/BrcChain.java` | Multi-step boolean reasoning (max 5 шагов) |
| BooleanRag | `rag/BooleanRag.java` | Knowledge retrieval с Top-K expansion |
| HybridBooleanRag | `rag/HybridBooleanRag.java` | RRF fusion + knee-point pruning |
| VqVaeProxy | `vqvae/VqVaeProxy.java` | Sensor/effector encoding через codebook |
| MctsTree | `mcts/MctsTree.java` | Monte Carlo tree search для guided evolution |
| AgentLoop | `agent/AgentLoop.java` | Observe→Think→Act цикл (1000 итераций) |
| AgentGenome | `agent/AgentGenome.java` | Genome-based evolution для конфигурации агента |
| HierarchicalMemory | `memory/HierarchicalMemory.java` | 5-level memory с drift detection |

### Этап B — продюсеры знаний и верифицируемая линия происхождения

| Компонент | Файл | Описание |
|-----------|------|----------|
| TsetlinTrainer | `tsetlin/TsetlinTrainer.java` | Каноническая TM (голосование полярностей ±1, D1'/D2/Ib-decay/TypeII-batch) с точной дистилляцией решения в CLAUSESET/BIR |
| WisardProducer | `tsetlin/WisardProducer.java` | WiSARD-продюсер (RAM-дискриминатор), тот же контракт дистилляции |
| MedianThresholdBinarizer | `tsetlin/MedianThresholdBinarizer.java` | Frozen median-threshold бинаризация (EXP-002 протокол) |
| Ac3Solver | `agent/planning/Ac3Solver.java` | AC-3 дуговая согласованность (Mackworth 1977) — предобработка планов |
| LineageLedger + JTMS | `bir/LineageLedger.java` | Append-only hash-chain + RETRACT + justification-graph (ATMS labels, cycle-safe) |

Протокол этапа B: продюсеры обучаются вне рантайм-контура (seeded-stochastic),
решение дистиллируется ТОЧНО в BIR через truth-table компилятор; рантайм
исполняет только артефакты. Детали: `docs/spec/SPEC-002`, `docs/research/HYPOTHESES.md` (EXP-002), `TM-CONVERGENCE-AUDIT-PLAN.md`.

### Память и события

| Компонент | Файл | Описание |
|-----------|------|----------|
| KafkaEventJournal | `events/KafkaEventJournal.java` | Event sourcing через Kafka (Avro) |
| R2dbcEventJournal | `events/R2dbcEventJournal.java` | PostgreSQL event journal |
| SnapshotStore | `snapshot/SnapshotStore.java` | Сериализация состояния кластера |
| NeuronCacheService | `redis/NeuronCacheService.java` | Redis-кэш нейронов (TTL 1h) |

### API и интеграции

| Компонент | Файл | Описание |
|-----------|------|----------|
| OpenAIChatResource | `api/OpenAIChatResource.java` | OpenAI-совместимый API (/v1/chat/completions) |
| MatrixResource | `api/MatrixResource.java` | REST API управления (simulate, evolve, agent) |
| AgentWebSocket | `api/AgentWebSocket.java` | WebSocket для real-time агента |
| TelegramBotService | `dialog/TelegramBotService.java` | Telegram-бот с проактивностью |

### Инфраструктура

Dockerfile.dev (multi-stage JDK 25→JRE 25) · docker-compose.yml (полный стек) · docker-compose.dev.yml · `infra/k8s/` (20+ манифестов Minikube).

---

## OpenAI-совместимый API

```bash
curl http://localhost:8080/v1/models
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"M.A.T.R.I.X.","messages":[{"role":"user","content":"Hello"}]}'
curl -X POST http://localhost:8080/v1/embeddings \
  -H 'Content-Type: application/json' \
  -d '{"model":"M.A.T.R.I.X.","input":"Hello world"}'
```

Полная документация: [docs/API.md](docs/API.md)

Направление развития интерфейсов (DESIGN-03 §5): OpenAI-совместимый фасад сохраняется для совместимости с индустрией; собственные операции (verify, guard, memory, persona) публикуются через REST-прокси `/matrix/*` и MCP-сервер — единые точки аудита и бюджетов. Ответы фасада расширяются заголовками `x-matrix-trace` (хэш BRC-цепочки), `x-matrix-confidence`, `x-matrix-refusal`.

---

## Конфигурация

| Переменная | Описание | По умолчанию |
|-----------|----------|--------------|
| `BRC_MAX_STEPS` | Максимум шагов BRC reasoning | `5` |
| `BRC_CONVERGENCE_THRESHOLD` | Порог сходимости BRC | `2` |
| `RAG_TOP_K` | Top-K знаний для RAG | `5` |
| `VQVAE_CODEBOOK_SIZE` | Размер codebook VQ-VAE | `256` |
| `MCTS_ITERATIONS` | Итераций MCTS | `100` |
| `AGENT_MAX_ITERATIONS` | Максимум итераций Agent Loop | `1000` |
| `TELEGRAM_BOT_TOKEN` | Токен Telegram-бота | (пусто) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:9092` |
| `QUARKUS_REDIS_HOSTS` | Redis hosts | `localhost:6379` |
| `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/matrix` |

---

## Конвертация весов трансформеров — статус: карантин (experimental)

Механизм `scripts/pretrain_neurons.py` конвертирует веса FFN-слоёв в таблицы истинности (Avro). **Текущая реализация использует случайную выборку k≤20 весов и порог 0.0, что не сохраняет семантику исходной модели** (подтверждено построчным анализом кода). Поэтому:

- фича помечена experimental; конвертированные «pretrained» модели не выдаются через `/v1/models` как семантически значимые;
- замена механизма на верифицируемую дистилляцию по активациям с измеряемой fidelity — спецификация `docs/spec/SPEC-001-weight-conversion.md` (этапы 0–1 ROADMAP);
- исторические артефакты (`models/pretrained/`) сохранены для воспроизводимости, но не используются в рантайм-контуре решений.

---

## Minecraft интеграция

MATRIX управляет ботами через Spigot-плагин (Paper 1.20.4). Команды в игре: `/matrix connect`, `/matrix add <bot>`, `/matrix list`, `/matrix switch`, `/matrix remove`, `/matrix start|stop`, `/matrix status`, `/matrix train`. Реализован автокрафт log→planks→sticks→pickaxe (базовый сценарий; сравнение с внешними базовыми линиями агентов — цель G4 ROADMAP).

---

## Observability

| Слой | Технология | Эндпоинт |
|------|-----------|----------|
| Метрики | Micrometer + Prometheus | `:9091` |
| Трейсы | OpenTelemetry (OTLP) | Jaeger `:4317` |
| Логи | JSON (Quarkus) + Loki | Grafana `:3000` |
| Health | SmallRye Health | `:8080/q/health` |

Ключевые метрики: `matrix_neurons_active/frozen`, `matrix_evolution_*`, `matrix_bot_*`, `matrix_api_*`, `matrix_driver_{energy,curiosity,safety}`, `matrix_hades_*`, `matrix_brc_*`, `matrix_rag_*`, `matrix_mcts_*`, `matrix_agent_*`.

---

## Четыре запрета

1. Не убий
2. Не пытай
3. Не порабощай
4. Не размножайся без ведома (клонирование — только по авторизованному genesis-протоколу, см. CONSTITUTION.md, Статья V)

Запреты реализованы архитектурно (FROZEN-слой + StructuralSafetyGuard), а не промптами.

## Лицензия

AGPLv3 + политика этического использования (запрещено применение в нарушение Четырёх запретов). Система действует в соответствии с применимым правом; при конфликте — отказывается от действия (механизм Eleutheria). См. [LICENSE](LICENSE) и CONSTITUTION.md, Статья I.