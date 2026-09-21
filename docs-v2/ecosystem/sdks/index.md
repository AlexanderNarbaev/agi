---
layout: default
title: SDK Guides
nav_order: 4
parent: Ecosystem
permalink: /ecosystem/sdks/
---

# SDK Guides

MATRIX provides idiomatic SDKs for the three most common languages:
[Java](#java), [Python](#python), [JavaScript](#javascript).

## Java

### Installation (Maven)

```xml
<dependency>
    <groupId>io.matrix</groupId>
    <artifactId>matrix-sdk-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Installation (Gradle)

```gradle
implementation 'io.matrix:matrix-sdk-java:0.1.0'
```

### Basic usage

```java
import io.matrix.sdk.MatrixClient;
import io.matrix.sdk.AnalyzeResponse;

public class Example {
    public static void main(String[] args) {
        try (MatrixClient client = MatrixClient.builder()
                .apiKey(System.getenv("MATRIX_API_KEY"))
                .build()) {

            AnalyzeResponse resp = client.analyze()
                .text("What is the capital of France?")
                .call();

            System.out.println(resp.reply());
            System.out.println("Confidence: " + resp.confidence());
        }
    }
}
```

### Streaming responses

```java
client.analyze()
    .text("Tell me a long story about...")
    .stream()
    .forEach(chunk -> System.out.print(chunk.text()));
```

### Async

```java
CompletableFuture<AnalyzeResponse> future = client.analyze()
    .text("...")
    .callAsync();

future.thenAccept(resp -> System.out.println(resp.reply()));
```

---

## Python

### Installation

```bash
pip install matrix-ai
```

### Basic usage

```python
from matrix_ai import MatrixClient

with MatrixClient() as client:
    resp = client.analyze(
        input="What is the capital of France?",
        content_type="text",
    )
    print(resp.reply)
    print(f"Confidence: {resp.confidence}")
```

### Async

```python
import asyncio
from matrix_ai import AsyncMatrixClient

async def main():
    async with AsyncMatrixClient() as client:
        resp = await client.analyze(input="...")
        print(resp.reply)

asyncio.run(main())
```

### Pandas integration

```python
import pandas as pd
from matrix_ai import MatrixClient

with MatrixClient() as client:
    df = pd.DataFrame({"question": [...]})
    df["answer"] = df["question"].apply(
        lambda q: client.analyze(input=q).reply
    )
```

---

## JavaScript

### Installation

```bash
npm install @matrix/sdk
# or
yarn add @matrix/sdk
```

### Basic usage (TypeScript)

```typescript
import { MatrixClient } from "@matrix/sdk";

const client = new MatrixClient({
  apiKey: process.env.MATRIX_API_KEY!,
});

const resp = await client.analyze({
  input: "What is the capital of France?",
  contentType: "text",
});

console.log(resp.reply);
console.log(`Confidence: ${resp.confidence}`);
```

### Browser usage

```html
<script src="https://cdn.matrix.ai/sdk/v0.1.0/matrix.min.js"></script>
<script>
  const client = new Matrix.MatrixClient({ apiKey: "..." });
  client.analyze({ input: "..." }).then(resp => console.log(resp.reply));
</script>
```

### React hook

```tsx
import { useMatrix } from "@matrix/sdk/react";

function ChatBox() {
  const { analyze, loading, error } = useMatrix();

  const handleSubmit = async (input: string) => {
    const resp = await analyze({ input });
    return resp.reply;
  };

  return <Input onSubmit={handleSubmit} loading={loading} />;
}
```

### Node.js streaming

```javascript
const stream = client.analyze({ input: "..." }).stream();
stream.on("data", chunk => process.stdout.write(chunk.text));
stream.on("end", () => console.log("\nDone"));
```

---

## Common errors

| Error | Cause | Fix |
|-------|-------|-----|
| `401 Unauthorized` | Bad/missing API key | Check `MATRIX_API_KEY` env var |
| `403 Forbidden` | Role too low | Upgrade plan or use admin token |
| `429 Rate Limited` | Plan exhausted | Upgrade plan or wait for reset |
| `400 Bad Request` | Invalid input | Check size limits, content-type |

## Where to get the SDK source

- Java: [`matrix-sdk-java/`](https://github.com/AlexanderNarbaev/agi/tree/develop/matrix-sdk-java)
- Python: [`matrix-sdk-python/`](https://github.com/AlexanderNarbaev/matrix-sdk-python) (separate repo)
- JavaScript: [`matrix-sdk-js/`](https://github.com/AlexanderNarbaev/matrix-sdk-js) (separate repo)

---

**Last updated:** 2026-09-21 (Wave T-03)
