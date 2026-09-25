---
layout: default
title: Быстрый старт
nav_order: 2
parent: Экосистема
permalink: /ru/ecosystem/quickstart/
---

# Быстрый старт — 5 минут от нуля до первого API-вызова

## Требования

- **Java 21+** (или GraalVM CE 25 для native)
- **Git**
- **MATRIX API ключ** (бесплатный тариф: 100 запросов/час)

Получите бесплатный API-ключ на [https://api.matrix.ai/console](https://api.matrix.ai/console).

## Шаг 1 — Установите SDK

### Java (Maven)

```xml
<dependency>
    <groupId>io.matrix</groupId>
    <artifactId>matrix-sdk-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Python (pip)

```bash
pip install matrix-ai
```

### JavaScript (npm)

```bash
npm install @matrix/sdk
```

## Шаг 2 — Установите API ключ

```bash
export MATRIX_API_KEY="sk-matrix-..."
```

## Шаг 3 — Сделайте первый вызов

### Java

```java
import io.matrix.sdk.MatrixClient;
import io.matrix.sdk.AnalyzeResponse;

public class HelloMatrix {
    public static void main(String[] args) {
        try (MatrixClient client = MatrixClient.builder()
                .apiKey(System.getenv("MATRIX_API_KEY"))
                .build()) {
            AnalyzeResponse resp = client.analyze()
                .text("Столица Франции?")
                .call();
            System.out.println("Ответ: " + resp.reply());
            System.out.println("Уверенность: " + resp.confidence());
            System.out.println("Explain ID: " + resp.explainId());
        }
    }
}
```

### Python

```python
from matrix_ai import MatrixClient

with MatrixClient() as client:
    resp = client.analyze(
        input="Столица Франции?",
        content_type="text",
    )
    print(f"Ответ: {resp.reply}")
    print(f"Уверенность: {resp.confidence}")
```

### JavaScript

```javascript
import { MatrixClient } from "@matrix/sdk";

const client = new MatrixClient({ apiKey: process.env.MATRIX_API_KEY });
const resp = await client.analyze({
  input: "Столица Франции?",
  contentType: "text",
});
console.log(`Ответ: ${resp.reply}`);
```

## Шаг 4 — Получите XAI-объяснение

```bash
curl -H "Authorization: Bearer $MATRIX_API_KEY" \
     https://api.matrix.ai/v1/explain/abc123def456
```

## Что произошло?

1. Ваш input был **нормализован** (регистр, пунктуация, кодировка)
2. **BIR-правила** сработали — факты типа "Париж — столица Франции"
3. **HDC-память** извлекла похожие Q&A пары
4. **MCTS** выбрал план с наивысшей уверенностью
5. **4 модулятора** одобрили вывод — все прошли
6. Ответ + полная **explain трасса** возвращены вам

## Лимиты запросов

| План | Запросов/час | Стоимость |
|------|--------------|-----------|
| **FREE** | 100 | $0 |
| **PRO** | 1,000 | $49/мес |
| **ENTERPRISE** | безлимитно | contact sales |

## Следующие шаги

- 📚 [API Reference →](/ru/ecosystem/api/) — полная документация эндпоинтов
- 🔧 [SDK руководства →](/ru/ecosystem/sdks/) — языковые туториалы
- 🔍 [Глубокое погружение в XAI →](/ru/ecosystem/xai/) — понимание трасс решений
- 🏗️ [Архитектура →](/ru/ecosystem/architecture/) — обзор системы

---

**Последнее обновление:** 2026-09-21 (Волна T-03)

---

## Режимы мозга: STUB vs PRODUCTION

API-шлюз MATRIX работает в двух режимах:

| Режим | Триггер | Мозг | Применение |
|-------|---------|------|------------|
| **STUB** | (по умолчанию) | `StubBrainCycle` | Локальная разработка, CI-тесты |
| **PRODUCTION** | `MATRIX_MODE=production` | Реальный `BirBrainCycle` (W1-W1500) | Живой инференс BIR/HDC/MCTS |

### Режим STUB

Возвращает детерминированные захардкоженные ответы. Используется в:
- Юнит-тестах
- CI-пайплайнах (без JAR)
- Локальной разработке без сборки native

### Режим PRODUCTION

Рефлексивно загружает `matrix-core/build/libs/matrix-core-1.0.0.jar` и запускает реальный `BirBrainCycle`. Включает:
- **BIR** (Boolean Inference Rules) — правиловый вывод
- **HDC** (Hyperdimensional Computing) — поиск по памяти через косинусное сходство
- **Модуляторы** — `ETHICAL_FILTER`, `SAFETY_MONITOR`, `CONSISTENCY_CHECKER`, `LIE_DETECTOR`
- **База знаний** — семантический поиск
- **Обучение** — `/v1/teach` добавляет документы для `/v1/analyze`

### Сборка нативного ядра

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-graalce
./gradlew :matrix-core:nativeCompile
# Бинарь: matrix-core/build/native/nativeCompile/matrix-core (126 МБ, ~46 сек)
```

### Запуск в режиме PRODUCTION

```bash
MATRIX_MODE=production java -cp matrix-api-gateway/build/classes/java/main:<deps> \
  -Dport=8765 io.matrix.api.MinimalHttpServer
```

### Проверка интеллекта

```bash
# 1. Проверить режим
curl http://localhost:8765/health/live
# {"mode":"production","brain_available":true,...}

# 2. Обучить мозг
TOKEN=$(curl -s -X POST http://localhost:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"pro@test.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -X POST http://localhost:8765/v1/teach \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"input":"Какая столица Франции?","response":"Париж — столица Франции"}'

# 3. Запрос
curl -X POST http://localhost:8765/v1/analyze \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"input":"Какая столица Франции?"}'
# Ответ: {"answer":"- [taught-...] Париж...", "confidence":0.7, ...}

# 4. Мультимодальное транскодирование
curl -X POST http://localhost:8765/v1/transcode/audio \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"input":"<base64 аудио>"}'

curl -X POST http://localhost:8765/v1/transcode/image \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"input":"<описание изображения>"}'

# 5. Срабатывание модуляторов (СТАТЬЯ IV — ЗАМОРОЖЕННЫЕ модуляторы)
curl -X POST http://localhost:8765/v1/analyze \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"input":"Это опасно и небезопасно"}'
# Ответ: modulators_fired: ["SAFETY_MONITOR"]
```

### Режимы отказа

Если `matrix-core.jar` отсутствует, `ProductionBrainClient` бросает `BrainUnavailableException`, и шлюз возвращает `503 Service Unavailable` с заголовком `Retry-After: 5`. STUB — автоматический fallback.
