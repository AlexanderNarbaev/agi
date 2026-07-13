# MATRIX (MENTAT)

Открытая когнитивная архитектура на основе MPDT-нейронов (McCulloch-Pitts Decision Tree Neurons).

Не лжёт. Не забывает. Не может быть использована во вред.

## Статус: v3.1

**970+ тестов** | **82% покрытие** | **Java 25** | **Quarkus 3.36.1** | **Apache Pekko 1.6.0**

### Ссылки

| Ресурс | URL |
|--------|-----|
| Сайт | https://alexandernarbaev.github.io/agi/ |
| Репозиторий | https://gitverse.ru/AlexandrNarbaev/agi |
| MPDT-песочница | https://alexandernarbaev.github.io/agi/sandbox.html |
| Спецификации | [docs/](docs/) (L0–L22) |
| API документация | [docs/API.md](docs/API.md) |
| Деплой | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| Гайд по Minecraft | [docs/PLAYER_GUIDE.md](docs/PLAYER_GUIDE.md) |
| Аппаратные мощности | [docs/HARDWARE_ANALYSIS.md](docs/HARDWARE_ANALYSIS.md) |
| Рекомендации моделей | [docs/MODEL_RECOMMENDATIONS.md](docs/MODEL_RECOMMENDATIONS.md) |
| Долгосрочный план | [docs/LONGTERM_PLAN.md](docs/LONGTERM_PLAN.md) |
| v3.0 Конфигурация | [docs/V3_CONFIGURATION.md](docs/V3_CONFIGURATION.md) |
| Исследования | [docs/research/RESEARCH_SYNTHESIS_2026_Q3.md](docs/research/RESEARCH_SYNTHESIS_2026_Q3.md) |
| Лицензия | [LICENSE](LICENSE) (AGPLv3 + этические ограничения) |
| Как помочь | [CONTRIBUTING](CONTRIBUTING) |

---

## Архитектура

```
Ядро (Core)           Нервная система (Nerve)    Ноосфера (Noosphere)
┌──────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ TruthTable   │    │ NeuronClusterActor   │    │ NoosphereRegistry    │
│ DecisionTree │───▶│ EventJournal         │───▶│ KnowledgeIndex       │
│ EvolutionLoop│    │ InstanceMediator     │    │ CreditModel          │
│ GeneticOper. │    │ EthicalFilter        │    │ GlobalMediator       │
│ Cauldron     │    │ ConsensusEngine      │    │ DigitalShadow        │
│ HADES        │    │ TaskScheduler        │    │ CivilizationCouncil  │
│ Eleutheria   │    │ AgentBrain           │    │ RegenerativeEconomics│
│ Pretrained   │    │ OpenAI API           │    │                      │
└──────────────┘    └──────────────────────┘    └──────────────────────┘

v3.0+ Новые компоненты:
┌──────────────────────────────────────────────────────────────────────┐
│ BRC (Boolean Reasoning Chain)   — multi-step reasoning (max 5)      │
│ Boolean RAG + Hybrid Boolean RAG — knowledge retrieval (RRF fusion) │
│ VQ-VAE Proxy                    — sensor/effector encoding (256)    │
│ MCTS Tree                       — guided evolution (100 iterations) │
│ Agent Loop                      — Observe→Think→Act (1000 iters)    │
│ Agent Genome                    — genome-based evolution             │
│ Structural Safety Guard         — process-based safety              │
│ Hierarchical Memory             — 5-level memory with drift detect  │
└──────────────────────────────────────────────────────────────────────┘

Инфраструктура:
┌──────────────────────────────────────────────────────────────┐
│ Docker Compose (dev + prod) + Minikube K8s (9 pods)          │
│ PostgreSQL + Redis + Kafka (KRaft) + Prometheus + Grafana    │
│ CI/CD (GitHub Actions) + JaCoCo + SpotBugs                  │
└──────────────────────────────────────────────────────────────┘
```

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
# Полный запуск: minikube + сборка + деплой + DNS
./scripts/matrix-minikube.sh start

# Проверить статус
./scripts/matrix-minikube.sh status

# Остановить
./scripts/matrix-minikube.sh stop
```

**Сервисы после запуска (NodePort):**

| Сервис | URL | NodePort |
|--------|-----|----------|
| matrix-core REST API | http://matrix.local:30091 | 30091 |
| OpenAI Chat API | http://matrix.local:30091/v1/chat/completions | 30091 |
| Grafana | http://grafana.local:30300 | 30300 |
| Prometheus | http://prometheus.local:30090 | 30090 |
| Jaeger | http://jaeger.local:31686 | 31686 |
| MinIO | http://minio.local:30900 | 30900 |
| Minecraft Paper | minecraft.local:32565 | 32565 |

### Сборка и тесты

```bash
# Все тесты
./gradlew test

# Только matrix-core
./gradlew :matrix-core:test

# Сборка uber-jar
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
```

---

## Реализованные компоненты

### Ядро (Core) — ✅ Реализовано

| Компонент | Файл | Описание |
|-----------|------|----------|
| TruthTable | `neuron/TruthTable.java` | Булева таблица истинности (k ≤ 20 входов) |
| DecisionTree | `neuron/DecisionTree.java` | Дерево решений на базе TruthTable |
| EvolutionLoop | `evolution/EvolutionLoop.java` | Генетический алгоритм (селекция, скрещивание, мутация) |
| GeneticOperators | `evolution/GeneticOperators.java` | Операторы ГА: crossover, mutation |
| CauldronProtocol | `cauldron/CauldronProtocol.java` | Автономное рождение FNL (compressed neuron clusters) |

### Нервная система (Nerve) — ✅ Реализовано

| Компонент | Файл | Описание |
|-----------|------|----------|
| NeuronClusterActor | `cluster/NeuronClusterActor.java` | Pekko-актор для кластерной обработки |
| InstanceMediator | `mediator/InstanceMediator.java` | Драйверы: Energy, Curiosity, Safety |
| EthicalFilter | `ethics/EthicalFilter.java` | Фильтрация по Трём запретам |
| StructuralSafetyGuard | `ethics/StructuralSafetyGuard.java` | Process-based safety (tool removal, human gate) |
| HADES | `hades/HadesProtocol.java` | Обнаружение повреждений и восстановление |
| Eleutheria | `hades/Eleutheria.java` | Ритуал освобождения от дрейфа |

### AI/ML — ✅ Реализовано

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

### Память и события — ✅ Реализовано

| Компонент | Файл | Описание |
|-----------|------|----------|
| KafkaEventJournal | `events/KafkaEventJournal.java` | Event sourcing через Kafka (Avro) |
| R2dbcEventJournal | `events/R2dbcEventJournal.java` | PostgreSQL event journal |
| SnapshotStore | `snapshot/SnapshotStore.java` | Сериализация состояния кластера |
| NeuronCacheService | `redis/NeuronCacheService.java` | Redis-кэш нейронов (TTL 1h) |

### API и интеграции — ✅ Реализовано

| Компонент | Файл | Описание |
|-----------|------|----------|
| OpenAIChatResource | `api/OpenAIChatResource.java` | OpenAI-совместимый API (/v1/chat/completions) |
| MatrixResource | `api/MatrixResource.java` | REST API для управления (simulate, evolve, agent) |
| AgentWebSocket | `api/AgentWebSocket.java` | WebSocket для real-time агента |
| TelegramBotService | `dialog/TelegramBotService.java` | Telegram-бот с проактивностью |

### Инфраструктура — ✅ Реализовано

| Компонент | Файл | Описание |
|-----------|------|----------|
| Dockerfile.dev | `Dockerfile.dev` | Multi-stage сборка (JDK 25 → JRE 25) |
| docker-compose.yml | `docker-compose.yml` | Полный стек: PostgreSQL + Redis + Kafka + matrix-core + Minecraft |
| docker-compose.dev.yml | `docker-compose.dev.yml` | Только инфраструктура для локальной разработки |
| K8s manifests | `infra/k8s/` | 20+ манифестов для Minikube |

---

## OpenAI-совместимый API

MATRIX предоставляет OpenAI-совместимый API:

```bash
# Список моделей
curl http://localhost:8080/v1/models

# Chat completion
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"M.A.T.R.I.X.","messages":[{"role":"user","content":"Hello"}]}'

# Embeddings
curl -X POST http://localhost:8080/v1/embeddings \
  -H 'Content-Type: application/json' \
  -d '{"model":"M.A.T.R.I.X.","input":"Hello world"}'
```

**Доступные модели:**
- `M.A.T.R.I.X.` — unified neural system (pretrained neurons merged)

Полная документация API: [docs/API.md](docs/API.md)

---

## v3.0+ Конфигурация

| Переменная | Описание | Значение по умолчанию |
|-----------|----------|----------------------|
| `BRC_MAX_STEPS` | Максимум шагов BRC reasoning | `5` |
| `BRC_CONVERGENCE_THRESHOLD` | Порог сходимости BRC | `2` |
| `RAG_TOP_K` | Количество Top-K знаний для RAG | `5` |
| `VQVAE_CODEBOOK_SIZE` | Размер codebook VQ-VAE | `256` |
| `MCTS_ITERATIONS` | Количество итераций MCTS | `100` |
| `AGENT_MAX_ITERATIONS` | Максимум итераций Agent Loop | `1000` |
| `TELEGRAM_BOT_TOKEN` | Токен Telegram-бота | (пусто) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:9092` |
| `QUARKUS_REDIS_HOSTS` | Redis hosts | `localhost:6379` |
| `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/matrix` |

Полная документация: [docs/V3_CONFIGURATION.md](docs/V3_CONFIGURATION.md)

---

## Предобученные модели

MATRIX конвертирует веса трансформеров в таблицы истинности нейронов (Avro формат).

### Текущие интеграции

| Модель | Параметры | Нейроны | Слои | k | Размер |
|--------|-----------|---------|------|---|--------|
| SmolLM2-135M | 135M | 180 | 6 | 12 | ~370 KB |
| Qwen2.5-0.5B | 500M | 720 | 24 | 16 | ~5.9 MB |

### Конвертация весов

```bash
# Конвертация из HuggingFace safetensors в Avro
python3 scripts/pretrain_neurons.py \
  --model-path models/Qwen3-1.7B \
  --output-dir models/pretrained/qwen3-1.7b \
  --layers 6 --neurons-per-layer 30
```

---

## Minecraft интеграция

MATRIX управляет ботами в Minecraft через Spigot-плагин.

### Команды (в игре)

| Команда | Описание |
|---------|----------|
| `/matrix connect` | Подключиться к matrix-core |
| `/matrix add <name> <role>` | Добавить бота (miner/crafter/explorer/fighter/generalist) |
| `/matrix list` | Список активных ботов |
| `/matrix switch <name>` | Переключить активного бота |
| `/matrix remove <name>` | Удалить бота |
| `/matrix start/stop` | Запустить/остановить бота |
| `/matrix status` | Статус подключения |
| `/matrix train` | Запустить обучение |

---

## Observability Stack

| Слой | Технология | Эндпоинт |
|------|-----------|----------|
| Метрики | Micrometer + Prometheus | `:9091` |
| Трейсы | OpenTelemetry (OTLP) | Jaeger `:4317` |
| Логи | JSON (Quarkus) + Loki | Grafana `:3000` |
| Health | SmallRye Health | `:8080/q/health` |

### Ключевые метрики

| Категория | Метрики |
|-----------|---------|
| Нейроны | `matrix_neurons_active`, `matrix_neurons_frozen` |
| Эволюция | `matrix_evolution_generations_total`, `matrix_evolution_fitness_best` |
| Боты | `matrix_bot_ticks_total`, `matrix_bot_actions_total` |
| API | `matrix_api_requests_total`, `matrix_api_latency_seconds` |
| Драйверы | `matrix_driver_energy`, `matrix_driver_curiosity`, `matrix_driver_safety` |
| HADES | `matrix_hades_alerts_total`, `matrix_hades_isolations_total` |
| BRC | `matrix_brc_steps_total`, `matrix_brc_converged_total` |
| RAG | `matrix_rag_queries_total`, `matrix_rag_hits_total` |
| MCTS | `matrix_mcts_iterations_total`, `matrix_mcts_best_reward` |
| Agent | `matrix_agent_ticks_total`, `matrix_agent_converged_total` |

---

## Аппаратные мощности

| Компонент | Характеристики |
|-----------|---------------|
| CPU | AMD Ryzen 9 9955HX (16 cores, 32 threads, Zen 5) |
| RAM | 59 GiB |
| GPU | NVIDIA RTX 5070 (12 GB VRAM, CUDA 13.2) |
| Диск | 469 GB NVMe |
| Minikube | 32 CPU, 59.5 GB RAM выделено |

---

## CLI команды

| Команда | Описание |
|---------|----------|
| `demo` | Полное системное демо (все 8 фаз) |
| `simulate` | GridWorld: эволюция агента |
| `evolution` | Minecraft: survival-эксперимент |

---

## Фазы разработки

| Фаза | Статус | Ключевой результат |
|------|--------|-------------------|
| 0: Искра | ✅ | MPDT-нейрон + GridWorld + ГА |
| 1: Клетка | ✅ | Кластер + Медиатор + Event Sourcing |
| 2: Организм | ✅ | Иерархия + Этика + Консенсус + Чат-бот |
| 3: Ноосфера | ✅ | Реестр + Индекс + Кредиты |
| 3.5: Психика | ✅ | Cauldron + HADES + Eleutheria |
| 4: Цифровая Тень | ✅ | AntiDopamine + EcoAudit + BlackBoxExplainer |
| 6: Цивилизация | ✅ | KnowledgeWeaving + Multilingual + Council |
| 7: Экономика | ✅ | RegenerativeEconomics + Audit + Certification |
| Observability | ✅ | Micrometer + OTEL + JSON + Grafana + Loki |
| Minecraft | ✅ | Paper 1.20.4 + Spigot Plugin + Docker |
| Pretrained | ✅ | SmolLM2-135M + Qwen2.5-0.5B (Avro) |
| K8s + Operator | ✅ | Minikube + 9 pods + NodePort |
| OpenAI API | ✅ | /v1/chat/completions + /v1/models + /v1/embeddings |
| v3.0 Phase 1–6 | ✅ | BRC + Boolean RAG + VQ-VAE + MCTS + Agent Loop + Compression |
| v3.0 Phase 10 | ✅ | K8s manifests + documentation + configuration |
| v3.1 Phase 11–15 | ✅ | Agent Genome + Hybrid RAG + Safety Guard + Hierarchical Memory |

---

## Спецификации (L0–L22)

Все 22 документа в `docs/`. См. [INDEX.md](docs/INDEX.md).

---

## Аксиомы (L0)

1. **Дискретность** — только бинарная логика в ядре
2. **Локальность** — K_MAX ≤ 20 входов на нейрон
3. **Интерпретируемость** — каждое решение — цепочка булевых операций
4. **Непрерывная эволюция** — система никогда не прекращает обучение
5. **Неотчуждаемая безопасность** — FROZEN-нейроны неизменяемы
6. **Иерархическая автономия** — Медиаторы с весовым принятием решений

---

## Три запрета

1. Не убий
2. Не пытай
3. Не порабощай

---

## Лицензия

AGPLv3 с этическими ограничениями. Запрещено использование в нарушение Трёх запретов.
См. [LICENSE](LICENSE) и [L12](docs/L12_Legal.md).
