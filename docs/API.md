# MATRIX API Documentation

**Версия:** v3.35
**Дата:** 2026-07-16
**Base URL:** `http://localhost:8080` (Docker) | `http://matrix.local:30091` (K8s)

---

## Обзор

MATRIX предоставляет несколько API:

| API | Протокол | Путь | Описание |
|-----|----------|------|----------|
| OpenAI Chat | REST | `/v1/*` | OpenAI-совместимый API для chat/embeddings |
| Matrix REST | REST | `/api/v1/*` | Управление нейронами, эволюцией, агентами |
| Agent WebSocket | WebSocket | `/api/v1/agent/ws` | Real-time управление агентом |
| Telegram Bot | Telegram API | N/A | Интеграция через Telegram Bot API |
| Health | REST | `/q/health` | Health check (SmallRye Health) |
| Metrics | REST | `/q/metrics` | Prometheus метрики (Micrometer) |

---

## 1. OpenAI-Compatible API (`/v1`)

### 1.1 Chat Completions

**Endpoint:** `POST /v1/chat/completions`

Генерация ответов через MPDT нейронные сети. Полностью совместим с OpenAI Chat API.

**Request:**

```json
{
  "model": "M.A.T.R.I.X.",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello, how are you?"}
  ],
  "stream": false,
  "temperature": 0.7
}
```

**Параметры:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `model` | string | Нет | Модель (по умолчанию: `M.A.T.R.I.X.`) |
| `messages` | array | Да | Массив сообщений с role и content |
| `stream` | boolean | Нет | Включить SSE streaming (по умолчанию: false) |
| `temperature` | number | Нет | Не используется (reserved) |

**Response (non-streaming):**

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1625000000,
  "model": "M.A.T.R.I.X.",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "I'm doing well, thank you for asking!"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 8,
    "total_tokens": 18
  }
}
```

**Response (streaming):**

```
data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"I'm"},"finish_reason":null}]}

data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":" doing"},"finish_reason":null}]}

data: [DONE]
```

**Ошибки:**

| Код | Описание |
|-----|----------|
| 400 | Невалидный запрос (пустые messages, неизвестная модель) |
| 403 | Этический фильтр заблокировал запрос |

**Пример curl:**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "M.A.T.R.I.X.",
    "messages": [{"role": "user", "content": "What is MPDT?"}]
  }'
```

---

### 1.2 List Models

**Endpoint:** `GET /v1/models`

Возвращает список доступных моделей.

**Response:**

```json
{
  "object": "list",
  "data": [
    {
      "id": "M.A.T.R.I.X.",
      "object": "model",
      "created": 1625000000,
      "owned_by": "matrix"
    }
  ]
}
```

**Пример curl:**

```bash
curl http://localhost:8080/v1/models
```

---

### 1.3 Embeddings

**Endpoint:** `POST /v1/embeddings`

Генерация embeddings через Text2Vec + MPDT нейроны.

**Request:**

```json
{
  "model": "M.A.T.R.I.X.",
  "input": "Hello world"
}
```

**Параметры:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `model` | string | Нет | Модель (по умолчанию: `M.A.T.R.I.X.`) |
| `input` | string | Да | Текст для embedding |

**Response:**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0,
                    0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0],
      "index": 0
    }
  ],
  "model": "M.A.T.R.I.X.",
  "usage": {
    "prompt_tokens": 2,
    "total_tokens": 2
  }
}
```

**Примечание:** Embedding — 20-мерный float вектор (бинарный, 0.0 или 1.0).

**Пример curl:**

```bash
curl -X POST http://localhost:8080/v1/embeddings \
  -H 'Content-Type: application/json' \
  -d '{"model": "M.A.T.R.I.X.", "input": "Hello world"}'
```

---

## 2. Matrix REST API (`/api/v1`)

### 2.1 Health Check

**Endpoint:** `GET /api/v1/health`

**Response:**

```json
{
  "status": "UP",
  "version": "2.1.0",
  "tenantId": "system",
  "tenantName": "System",
  "activeLoops": 0
}
```

---

### 2.2 Tenants

#### List Tenants

**Endpoint:** `GET /api/v1/tenants`

**Response:**

```json
{
  "count": 2,
  "tenants": [
    {"tenantId": "default", "displayName": "Default Tenant"},
    {"tenantId": "test", "displayName": "Test Tenant"}
  ]
}
```

#### Create Tenant

**Endpoint:** `POST /api/v1/tenants`

**Request:**

```json
{
  "id": "my-tenant"
}
```

**Response:**

```json
{
  "tenantId": "my-tenant",
  "instanceId": "instance-abc123",
  "displayName": "my-tenant"
}
```

---

### 2.3 Simulate

**Endpoint:** `POST /api/v1/simulate`

Запуск синхронной эволюции GridWorld.

**Request:**

```json
{
  "generations": 20,
  "population": 30,
  "k": 8
}
```

**Параметры:**

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|--------------|----------|
| `generations` | int | 20 | Количество поколений |
| `population` | int | 30 | Размер популяции |
| `k` | int | 8 | Количество входов на нейрон |

**Response:**

```json
{
  "status": "completed",
  "tenantId": "default",
  "generations": 20,
  "population": 30,
  "k": 8,
  "bestFitness": 940
}
```

**Пример curl:**

```bash
curl -X POST http://localhost:8080/api/v1/simulate \
  -H 'Content-Type: application/json' \
  -d '{"generations": 50, "population": 50, "k": 12}'
```

---

### 2.4 Evolve (Async)

#### Start Evolution

**Endpoint:** `POST /api/v1/evolve`

Запуск асинхронной эволюции.

**Request:**

```json
{
  "generations": 100,
  "population": 50,
  "k": 10
}
```

**Response:**

```json
{
  "loopId": "abc12345",
  "status": "started",
  "generations": 100
}
```

#### Get Evolution Status

**Endpoint:** `GET /api/v1/evolve/{loopId}`

**Response:**

```json
{
  "loopId": "abc12345",
  "status": "completed",
  "bestFitness": 980,
  "generations": 100
}
```

**Ошибки:**

| Код | Описание |
|-----|----------|
| 404 | Loop not found or not yet completed |

---

### 2.5 Cauldron

**Endpoint:** `POST /api/v1/cauldron`

Запуск протокола Cauldron для автономного рождения FNL.

**Request:**

```json
{
  "task": "navigation"
}
```

**Response:**

```json
{
  "status": "CONVERGED",
  "task": "navigation",
  "fnlName": "NAVIGATION",
  "fnlType": "FNL",
  "accuracy": 0.95,
  "generations": 42,
  "bestFitness": 850
}
```

---

### 2.6 Snapshots

#### Create Snapshot

**Endpoint:** `POST /api/v1/snapshot`

**Response:**

```json
{
  "snapshotId": "snap-20260713-001",
  "path": "/tmp/matrix-snapshots/snap-20260713-001.json",
  "neuronCount": 180,
  "brainNeurons": 45
}
```

#### Get Latest Snapshot

**Endpoint:** `GET /api/v1/snapshot/latest`

**Response:**

```json
{
  "snapshotId": "snap-20260713-001",
  "instanceId": "api-instance",
  "neuronCount": 180
}
```

**Ошибки:**

| Код | Описание |
|-----|----------|
| 404 | No snapshots found |

---

### 2.7 Truth Table Evaluation

**Endpoint:** `POST /api/v1/truth-table`

Evaluate a truth table with given input.

**Request:**

```json
{
  "k": 4,
  "input": 5,
  "tableBits": "1011010010110100"
}
```

**Параметры:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `k` | int | Да | Количество входов (1-20) |
| `input` | int | Да | Входное значение |
| `tableBits` | string | Нет | Бинарная строка таблицы истинности (если не указана — случайная) |

**Response:**

```json
{
  "k": 4,
  "input": 5,
  "output": true,
  "cardinality": 8
}
```

---

### 2.8 Agent Endpoints

#### Infer

**Endpoint:** `POST /api/v1/agent/infer`

Получить действие агента для заданных sensor bits.

**Request:**

```json
{
  "sensorBits": 12345
}
```

**Response:**

```json
{
  "action": "NORTH",
  "sensorBits": 12345
}
```

#### Train

**Endpoint:** `POST /api/v1/agent/train`

Запуск обучения агента (генетический алгоритм).

**Request:**

```json
{
  "generations": 20,
  "population": 30,
  "k": 8
}
```

**Response:**

```json
{
  "status": "completed",
  "generations": 20,
  "bestFitness": 750
}
```

#### Train Online

**Endpoint:** `POST /api/v1/agent/train-online`

Online обучение через feedback loop.

**Request:**

```json
{
  "iterations": 5
}
```

**Response:**

```json
{
  "status": "completed",
  "method": "online",
  "iterations": 5
}
```

#### Save Agent

**Endpoint:** `POST /api/v1/agent/save`

**Request:**

```json
{
  "path": "/tmp/my-brain.json"
}
```

**Response:**

```json
{
  "status": "saved",
  "path": "/tmp/my-brain.json"
}
```

#### Load Agent

**Endpoint:** `POST /api/v1/agent/load`

**Request:**

```json
{
  "path": "/tmp/my-brain.json"
}
```

**Response:**

```json
{
  "status": "loaded",
  "path": "/tmp/my-brain.json"
}
```

#### Share Neurons

**Endpoint:** `POST /api/v1/agent/share`

Поделиться нейронами с другими агентами через Noosphere.

**Request:**

```json
{
  "agentId": "agent-001",
  "role": "explorer",
  "neuronData": "...",
  "fitness": 0.95
}
```

**Response:**

```json
{
  "status": "shared",
  "role": "explorer",
  "totalShared": 15
}
```

#### Get Shared Neurons

**Endpoint:** `GET /api/v1/agent/neurons/{role}`

Получить shared нейроны для заданной роли.

**Response:**

```json
[
  {
    "agentId": "agent-001",
    "neuronData": "...",
    "fitness": 0.95,
    "timestamp": 1625000000000
  }
]
```

---

## 3. Agent WebSocket (`/api/v1/agent/ws`)

Real-time интерфейс для управления агентом.

### Подключение

```javascript
const ws = new WebSocket('ws://localhost:8080/api/v1/agent/ws');
```

### Сообщения (Client → Server)

#### Start Agent

```json
{
  "type": "start",
  "agentId": "my-agent-001"
}
```

#### Stop Agent

```json
{
  "type": "stop"
}
```

#### Send Sensors

```json
{
  "type": "sensors",
  "data": 12345,
  "role": "explorer"
}
```

#### Train

```json
{
  "type": "train",
  "generations": 20,
  "population": 30,
  "k": 8
}
```

#### Train Online

```json
{
  "type": "train-online",
  "iterations": 5
}
```

#### Save

```json
{
  "type": "save"
}
```

#### Feedback

```json
{
  "type": "feedback",
  "sensors": 12345,
  "success": true
}
```

### Сообщения (Server → Client)

#### Started

```json
{
  "type": "started",
  "agentId": "my-agent-001"
}
```

#### Stopped

```json
{
  "type": "stopped",
  "agentId": "my-agent-001"
}
```

#### Action

```json
{
  "type": "action",
  "data": "NORTH"
}
```

#### Training Complete

```json
{
  "type": "training_complete",
  "bestFitness": 750,
  "generations": 20
}
```

#### Saved

```json
{
  "type": "saved",
  "path": "/tmp/matrix-brain-my-agent-001.json"
}
```

#### Error

```json
{
  "type": "error",
  "message": "No agent started for this session"
}
```

### Ограничения

| Параметр | Значение |
|----------|----------|
| Max message rate | 100 messages per session |
| Max agentId length | 64 characters |
| AgentId format | `[a-zA-Z0-9_-]+` |

---

## 4. Telegram Bot

### Конфигурация

| Переменная | Описание |
|------------|----------|
| `TELEGRAM_BOT_TOKEN` | Токен от @BotFather |
| `TELEGRAM_BOT_NAME` | Имя бота (по умолчанию: `MATRIX_Bot`) |

### Команды

| Команда | Описание |
|---------|----------|
| `/start` | Приветствие и инструкция |
| `/help` | Список команд |
| `/why` | Объяснить последний ответ (reasoning chain) |
| `/learn <fact>` | Научить бота новому факту |
| `/status` | Показать состояния драйверов (Energy, Curiosity, Safety) |
| `/proactive on` | Включить проактивные сообщения |
| `/proactive off` | Выключить проактивные сообщения |

### Проактивность

Бот автоматически инициирует диалог когда:

- **Curiosity driver** обнаруживает интересную информацию
- **Social driver** обнаруживает длительное бездействие пользователя
- Обнаружены аномалии в системе
- Достигнуты важные milestones

**Адаптивность:** Проактивность автоматически отключается после 3 игнорирований.

### Этический фильтр

Все сообщения проходят через `EthicalFilter`:

- **Блокируется:** kill, torture, enslave, autonomous weapons, вредоносные запросы
- **Логируется:** все заблокированные запросы
- **Объясняется:** причина блокировки отправляется пользователю

---

## 5. Health & Metrics

### Health Check

**Endpoint:** `GET /q/health`

**Response:**

```json
{
  "status": "UP",
  "checks": [
    {"name": "DatabaseConnectionHealthCheck", "status": "UP"},
    {"name": "RedisConnectionHealthCheck", "status": "UP"},
    {"name": "KafkaConnectionHealthCheck", "status": "UP"}
  ]
}
```

### Prometheus Metrics

**Endpoint:** `GET /q/metrics`

Формат: Prometheus exposition format.

**Ключевые метрики:**

```
# Нейроны
matrix_neurons_active{instance="..."} 180
matrix_neurons_frozen{instance="..."} 5

# Эволюция
matrix_evolution_generations_total{instance="..."} 1500
matrix_evolution_fitness_best{instance="..."} 940

# API
matrix_api_requests_total{endpoint="/v1/chat/completions"} 1234
matrix_api_latency_seconds{endpoint="/v1/chat/completions"} 0.05

# Агент
matrix_agent_ticks_total{agentId="agent-001"} 5000
matrix_agent_converged_total{agentId="agent-001"} 3

# Боты (Minecraft)
matrix_bot_ticks_total{agentId="miner-001"} 10000
matrix_bot_actions_total{agentId="miner-001",action="MINE"} 500

# Драйверы
matrix_driver_energy{instance="..."} 0.75
matrix_driver_curiosity{instance="..."} 0.60
matrix_driver_safety{instance="..."} 0.95

# HADES
matrix_hades_alerts_total{instance="..."} 2
matrix_hades_isolations_total{instance="..."} 0

# BRC
matrix_brc_steps_total{instance="..."} 500
matrix_brc_converged_total{instance="..."} 450

# RAG
matrix_rag_queries_total{instance="..."} 200
matrix_rag_hits_total{instance="..."} 180

# MCTS
matrix_mcts_iterations_total{instance="..."} 10000
matrix_mcts_best_reward{instance="..."} 0.95
```

---

## 6. Ошибки

### Формат ошибок

```json
{
  "error": {
    "message": "Human-readable error message",
    "type": "error_type",
    "code": "error_code"
  }
}
```

### Коды ошибок

| HTTP | Тип | Код | Описание |
|------|-----|-----|----------|
| 400 | `invalid_request_error` | `missing_messages` | Пустой массив messages |
| 400 | `invalid_request_error` | `unknown_model` | Неизвестная модель |
| 400 | `invalid_request_error` | `missing_input` | Отсутствует input |
| 403 | `ethical_violation` | `blocked` | Этический фильтр заблокировал |
| 404 | `not_found` | `loop_not_found` | Evolution loop не найден |
| 404 | `not_found` | `no_snapshots` | Нет снапшотов |
| 429 | `rate_limit_exceeded` | `too_many_messages` | Превышен лимит сообщений WebSocket |
| 500 | `internal_error` | `processing_failed` | Внутренняя ошибка обработки |

---

## 7. Примеры интеграции

### Python (OpenAI client)

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"  # MATRIX не требует API key
)

response = client.chat.completions.create(
    model="M.A.T.R.I.X.",
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

### JavaScript (WebSocket)

```javascript
const ws = new WebSocket('ws://localhost:8080/api/v1/agent/ws');

ws.onopen = () => {
  ws.send(JSON.stringify({ type: 'start', agentId: 'my-agent' }));
};

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  console.log('Received:', msg);
  
  if (msg.type === 'started') {
    ws.send(JSON.stringify({
      type: 'sensors',
      data: 0b1010101010,
      role: 'explorer'
    }));
  }
};
```

### curl (Evolution)

```bash
# Start async evolution
LOOP_ID=$(curl -s -X POST http://localhost:8080/api/v1/evolve \
  -H 'Content-Type: application/json' \
  -d '{"generations": 100, "population": 50}' | jq -r '.loopId')

# Poll for completion
while true; do
  STATUS=$(curl -s http://localhost:8080/api/v1/evolve/$LOOP_ID | jq -r '.status')
  if [ "$STATUS" = "completed" ]; then
    curl -s http://localhost:8080/api/v1/evolve/$LOOP_ID | jq
    break
  fi
  sleep 1
done
```

---

## Noosphere API (`/api/v1/noosphere`) — v3.30+

### Publish FNL
**`POST /api/v1/noosphere/publish`**

```json
// Request
{ "name": "navigation-v3", "type": "navigation", "accuracy": 0.92, "tags": ["spatial"] }

// Response
{ "success": true, "entryId": "abc123...", "fnlName": "navigation-v3", "timestamp": "..." }
```

### Search FNLs
**`GET /api/v1/noosphere/search?q=navigation&limit=10`**

```json
{ "query": "navigation", "totalResults": 3, "returned": 3,
  "results": [{ "name": "nav-v1", "type": "navigation", "relevance": 0.85, ... }] }
```

### Registry Stats
**`GET /api/v1/noosphere/stats`**

```json
{ "totalEntries": 42, "activeEntries": 42, "indexedDocuments": 42, "topTypes": ["navigation","vision"] }
```

---

## Explainability Trace (`/api/v1/explain/trace`) — v3.30+

### BRC Reasoning Trace
**`GET /api/v1/explain/trace`**

Returns a 3-step PERCEIVE→REASON→DECIDE trace with SHAP feature importance per step.

```json
{ "chainName": "Demo Reasoning Chain", "totalSteps": 3,
  "steps": [{ "step": 0, "name": "PERCEIVE", "k": 4,
    "shapImportance": [{ "bitIndex": 0, "shapValue": 0.25, "explanation": "..." }] }] }
```

Static HTML explorer: `/explain/index.html`

---

## SCADA Safety Monitor — v3.34+

SCADA operations are gated through `StructuralSafetyGuard`:
- `scada.shutdown` → `REQUIRES_APPROVAL` (HIGH risk, gated)
- `scada.valve.control` → `MEDIUM` risk
- `scada.sensor.read` → `LOW` risk (auto-approved)

```json
// Guard verdict for scada.shutdown:
{ "decision": "REQUIRES_APPROVAL", "riskLevel": "HIGH", "gateId": "scada.shutdown-..." }
```

---

*Конец API.md — v3.35, 2026-07-16*
