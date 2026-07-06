# MATRIX (MENTAT)

Открытая когнитивная архитектура на основе MPDT-нейронов (McCulloch-Pitts Decision Tree Neurons).

Не лжёт. Не забывает. Не может быть использована во вред.

## Статус: v2.5.0

**920 тестов** | **82% покрытие** | **Java 25** | **Quarkus 3.36.1** | **Apache Pekko 1.6.0**

### Ссылки

| Ресурс | URL |
|--------|-----|
| Сайт | https://alexandernarbaev.github.io/agi/ |
| Репозиторий | https://gitverse.ru/AlexandrNarbaev/agi |
| MPDT-песочница | https://alexandernarbaev.github.io/agi/sandbox.html |
| Спецификации | [docs/](docs/) (L0–L22) |
| Гайд по Minecraft | [docs/PLAYER_GUIDE.md](docs/PLAYER_GUIDE.md) |
| Аппаратные мощности | [docs/HARDWARE_ANALYSIS.md](docs/HARDWARE_ANALYSIS.md) |
| Рекомендации моделей | [docs/MODEL_RECOMMENDATIONS.md](docs/MODEL_RECOMMENDATIONS.md) |
| Долгосрочный план | [docs/LONGTERM_PLAN.md](docs/LONGTERM_PLAN.md) |
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

Инфраструктура:
┌──────────────────────────────────────────────────────┐
│ Minikube K8s (9 pods) + Docker Compose (dev)         │
│ Prometheus + Grafana + Jaeger + Loki                 │
│ CI/CD (GitHub Actions) + JaCoCo + SpotBugs           │
└──────────────────────────────────────────────────────┘
```

---

## Быстрый старт

### Вариант 1: Minikube K8s (рекомендуется)

```bash
# Полный запуск: minikube + сборка + деплой + DNS
./scripts/matrix-minikube.sh start

# Проверить статус
./scripts/matrix-minikube.sh status

# Остановить
./scripts/matrix-minikube.sh stop
```

**Сервисы после запуска:**

| Сервис | URL | NodePort |
|--------|-----|----------|
| matrix-core REST API | http://matrix.local:30091 | 30091 |
| OpenAI Chat API | http://matrix.local:30091/v1/chat/completions | 30091 |
| Grafana | http://grafana.local:30300 | 30300 |
| Prometheus | http://prometheus.local:30090 | 30090 |
| Jaeger | http://jaeger.local:31686 | 31686 |
| MinIO | http://minio.local:30900 | 30900 |
| Minecraft Paper | minecraft.local:32565 | 32565 |

### Вариант 2: Docker Compose (для разработки)

```bash
# Инфраструктура мониторинга
./scripts/matrix-full-stack.sh

# Или вручную:
docker compose -f infra/docker-compose.yml up -d
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
java -jar matrix-core/build/matrix-core-*-runner.jar
```

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

## OpenAI-совместимый API

MATRIX предоставляет OpenAI-совместимый API для работы с нейронными сетями:

```bash
# Список моделей
curl http://matrix.local:30091/v1/models

# Chat completion
curl -X POST http://matrix.local:30091/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"mpdt-qwen","messages":[{"role":"user","content":"Hello"}]}'

# Embeddings
curl -X POST http://matrix.local:30091/v1/embeddings \
  -H 'Content-Type: application/json' \
  -d '{"model":"mpdt-qwen","input":"Hello world"}'
```

**Доступные модели:**
- `mpdt-qwen` — Qwen2.5-0.5B pretrained (720 нейронов, 24 слоя, k=16)
- `mpdt-smollm2` — SmolLM2-135M pretrained (180 нейронов, 6 слоёв, k=12)

---

## Предобученные модели

MATRIX конвертирует веса трансформеров в таблицы истинности нейронов (Avro формат).

### Текущие интеграции

| Модель | Параметры | Нейроны | Слои | k | Размер |
|--------|-----------|---------|------|---|--------|
| SmolLM2-135M | 135M | 180 | 6 | 12 | ~370 KB |
| Qwen2.5-0.5B | 500M | 720 | 24 | 16 | ~5.9 MB |

### Рекомендуемые к интеграции

См. [docs/MODEL_RECOMMENDATIONS.md](docs/MODEL_RECOMMENDATIONS.md) для полного анализа.

| Модель | Параметры | Слои | Почему |
|--------|-----------|------|--------|
| **Qwen3-1.7B** ⭐ | 1.7B | 28 | Thinking mode, agent capabilities, Apache 2.0 |
| **DeepSeek-R1-Distill-Qwen-1.5B** | 1.5B | 28 | R1 distillation, лучший reasoning, MIT |
| **Qwen3-0.6B** | 0.6B | 28 | Быстрая итерация, thinking mode, Apache 2.0 |

### Конвертация весов

```bash
# Конвертация из HuggingFace safetensors в Avro
python3 scripts/pretrain_neurons.py \
  --model-path models/Qwen3-1.7B \
  --output-dir models/pretrained/qwen3-1.7b \
  --layers 6 --neurons-per-layer 30

# Для больших моделей (>10GB)
python3 scripts/pretrain_large.py \
  --model-path models/Phi-4-mini-instruct \
  --output-dir models/pretrained/phi-4-mini \
  --quantize 8bit
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

### Архитектура бота

```
Minecraft Server (Paper 1.20.4)
    └── Spigot Plugin (matrix-spigot)
        ├── WebSocket → matrix-core (K8s Service)
        ├── Sensor Proxy (12 inputs, k=12)
        ├── Hierarchical Brain (sensor→feature→action)
        └── Effector Proxy (5 outputs: MOVE/ATTACK/MINE/CRAFT/STAY)
```

---

## Observability Stack

| Слой | Технология | Эндпоинт (K8s NodePort) |
|------|-----------|--------------------------|
| Метрики | Micrometer + Prometheus | `:30090` |
| Трейсы | OpenTelemetry (OTLP) | Jaeger `:31686` |
| Логи | JSON (Quarkus) + Loki | Grafana `:30300` |
| Health | SmallRye Health | `:30091/q/health` |
| Дашборды | Grafana (29 панелей) | `:30300` |

### Ключевые метрики

| Категория | Метрики |
|-----------|---------|
| Нейроны | `matrix_neurons_active`, `matrix_neurons_frozen` |
| Эволюция | `matrix_evolution_generations_total`, `matrix_evolution_fitness_best` |
| Боты | `matrix_bot_ticks_total`, `matrix_bot_actions_total` |
| API | `matrix_api_requests_total`, `matrix_api_latency_seconds` |
| Драйверы | `matrix_driver_energy`, `matrix_driver_curiosity`, `matrix_driver_safety` |
| HADES | `matrix_hades_alerts_total`, `matrix_hades_isolations_total` |

---

## Аппаратные мощности

| Компонент | Характеристики |
|-----------|---------------|
| CPU | AMD Ryzen 9 9955HX (16 cores, 32 threads, Zen 5) |
| RAM | 59 GiB |
| GPU | NVIDIA RTX 5070 (12 GB VRAM, CUDA 13.2) |
| Диск | 469 GB NVMe |
| Minikube | 32 CPU, 59.5 GB RAM выделено |

См. [docs/HARDWARE_ANALYSIS.md](docs/HARDWARE_ANALYSIS.md) для детального анализа.

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
| Minecraft | ✅ | Paper 1.20.4 + Spigot Plugin + K8s |
| Pretrained | ✅ | SmolLM2-135M + Qwen2.5-0.5B (Avro) |
| K8s + Operator | ✅ | Minikube + 9 pods + NodePort |
| OpenAI API | ✅ | /v1/chat/completions + /v1/models + /v1/embeddings |

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
