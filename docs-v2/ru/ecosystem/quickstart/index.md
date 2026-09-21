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
