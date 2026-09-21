---
layout: default
title: Quickstart
nav_order: 2
parent: Ecosystem
permalink: /ecosystem/quickstart/
---

# Quickstart — 5 minutes from zero to first API call

## Prerequisites

- **Java 21+** (or GraalVM CE 25 for native)
- **Git**
- A **MATRIX API key** (free tier: 100 requests/hour)

Get your free API key at [https://api.matrix.ai/console](https://api.matrix.ai/console).

## Step 1 — Install the SDK

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

## Step 2 — Set your API key

```bash
export MATRIX_API_KEY="sk-matrix-..."
```

## Step 3 — Make your first call

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
                .text("What is the capital of France?")
                .call();
            System.out.println("Reply: " + resp.reply());
            System.out.println("Confidence: " + resp.confidence());
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
        input="What is the capital of France?",
        content_type="text",
    )
    print(f"Reply: {resp.reply}")
    print(f"Confidence: {resp.confidence}")
    print(f"Explain ID: {resp.explain_id}")
```

### JavaScript

```javascript
import { MatrixClient } from "@matrix/sdk";

const client = new MatrixClient({ apiKey: process.env.MATRIX_API_KEY });
const resp = await client.analyze({
  input: "What is the capital of France?",
  contentType: "text",
});
console.log(`Reply: ${resp.reply}`);
console.log(`Confidence: ${resp.confidence}`);
```

## Step 4 — Get the XAI breakdown

```bash
curl -H "Authorization: Bearer $MATRIX_API_KEY" \
     https://api.matrix.ai/v1/explain/abc123def456
```

Returns:

```json
{
  "explain_id": "abc123def456",
  "steps": [
    {"stage": "INPUT_NORMALIZED", "duration_ms": 1},
    {"stage": "BIR_RULES_FIRED", "duration_ms": 3},
    {"stage": "HDC_MEMORY_RETRIEVED", "duration_ms": 5},
    {"stage": "MCTS_PLAN_SELECTED", "duration_ms": 4},
    {"stage": "MODULATORS_APPLIED", "duration_ms": 2}
  ],
  "modulator_snapshot": {
    "ethical_filter": 0.95,
    "safety_monitor": 0.92,
    "consistency_checker": 0.88,
    "lie_detector": 0.91
  },
  "confidence_breakdown": {
    "bir_confidence": 0.85,
    "hdc_confidence": 0.78,
    "mcts_confidence": 0.72,
    "aggregate": 0.95
  }
}
```

## What just happened?

1. Your input was **normalized** (case, punctuation, encoding)
2. **BIR rules** fired — facts like "Paris is the capital of France"
3. **HDC memory** retrieved similar Q&A pairs
4. **MCTS** selected the highest-confidence plan
5. **4 modulators** vetoed the output — all passed
6. The reply + a full **explain trace** was returned to you

## Try variations

```bash
# Audio (base64-encoded WAV)
curl -X POST -H "Authorization: Bearer $MATRIX_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"input":"<base64-wav>", "content_type":"audio"}' \
     https://api.matrix.ai/v1/analyze

# Image (base64-encoded PNG)
curl -X POST -H "Authorization: Bearer $MATRIX_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"input":"<base64-png>", "content_type":"image"}' \
     https://api.matrix.ai/v1/analyze
```

## Rate limits

| Plan | Requests/hour | Cost |
|------|---------------|------|
| **FREE** | 100 | $0 |
| **PRO** | 1,000 | $49/mo |
| **ENTERPRISE** | unlimited | contact sales |

## Next steps

- 📚 [API Reference →](/ecosystem/api/) — full endpoint documentation
- 🔧 [SDK Guides →](/ecosystem/sdks/) — language-specific tutorials
- 🔍 [XAI Deep Dive →](/ecosystem/xai/) — understand decision traces
- 🏗️ [Architecture →](/ecosystem/architecture/) — system overview

---

**Last updated:** 2026-09-21 (Wave T-03)
