---
layout: default
title: Руководства по SDK
nav_order: 4
parent: Экосистема
permalink: /ru/ecosystem/sdks/
---

**Статус:** ACTIVE · **Обновлено:** 2026-11-06

# Руководства по SDK

> **Публикуемого дистрибутива SDK не существует**, как и размещённого сервиса, на который
> его можно было бы направить. Эта страница описывает код клиента, который лежит в этом
> репозитории, и то, что нужно, чтобы им пользоваться.

| SDK | состояние |
|---|---|
| **Java** — `matrix-sdk-java` | реальный код: `io.matrix.sdk.MatrixClient` и его DTO; Gradle публикует **`io.matrix:matrix-sdk-java:0.1.0-T08-SNAPSHOT`** — снапшот, а не релиз в Maven Central |
| **Python** — `matrix-sdk-python` | есть в дереве, с `src/` и своими тестами, но **вне Gradle**; реестр называет его плановым, чему дерево противоречит |
| **JavaScript** — `matrix-sdk-js` | то же самое: в дереве, вне Gradle, назван плановым |

## Клиент на Java

Он **чистый транспорт**: собирает HTTP-запросы, отправляет и разбирает ответы. Никакой
логики внутри, никаких вызовов LLM.

**Базовый URL — то, что нужно задать.** По умолчанию клиент указывает на
**`https://api.matrix.ai`**, а **сервиса по этому адресу не существует** — MATRIX не
размещён. Направьте его на запущенный вами шлюз:

```java
try (MatrixClient client = MatrixClient.builder()
        .baseUrl("http://127.0.0.1:8765")
        .apiKey(token)          // bearer-токен из POST /v1/auth/login
        .timeout(Duration.ofSeconds(30))
        .build()) {

    AnalyzeResponse response = client.analyze()
            .text("What is the capital of France?")
            .call();

    System.out.println(response.reply);
    System.out.println(response.confidence);
}
```

`AnalyzeResponse` — простой держатель данных с публичными полями (`reply`, `confidence`,
`durationMs`, `accepted`, `explainId`, `modulatorsFired`), а не record, поэтому поля
читаются напрямую. Версия — часть API: `MatrixClient.VERSION` равна `0.1.0-T08`.

| вызов | маршрут |
|---|---|
| `client.analyze()` → `AnalyzeCall` → `.text(...)`, `.call()`, `.callAsync()` | `POST /v1/analyze` |
| `client.explain(explainId)` | `GET /v1/explain/{id}` — объяснение живёт в памяти шлюза |
| `client.federate(FederateRequest)` | `POST /v1/federate` |

Токен берётся у самого шлюза, а не у размещённого сервиса:

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
```

Полные страницы: [Справочник API](/ru/ecosystem/api/) ·
[Быстрый старт](/ru/ecosystem/quickstart/).
