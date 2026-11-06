---
layout: default
title: SDK Guides
nav_order: 4
parent: Ecosystem
permalink: /ecosystem/sdks/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# SDK Guides

> **There is no published SDK distribution and no hosted service to point one at.** This page
> describes the client code that is in this repository, and what it takes to use it.

| the SDK | the state |
|---|---|
| **Java** — `matrix-sdk-java` | real code in the repository: `io.matrix.sdk.MatrixClient` and its DTOs, published by the Gradle build as **`io.matrix:matrix-sdk-java:0.1.0-T08-SNAPSHOT`** — a snapshot version, not a release on Maven Central |
| **Python** — `matrix-sdk-python` | in the tree with `src/` and its own tests, but **outside Gradle**; the capability registry calls it planned, which the tree contradicts |
| **JavaScript** — `matrix-sdk-js` | the same: in the tree with `src/` and its own tests, outside Gradle, and called planned by the registry |

## The Java client

It is **pure transport** — it builds HTTP requests, sends them and parses the answers. It
holds no logic of its own, makes no LLM call, and adds no inference of its own.

### The base URL is the thing to set

The client's default base URL is **`https://api.matrix.ai`**, and **there is no service at
that address** — MATRIX is not hosted. Point it at the gateway you started yourself:

```java
try (MatrixClient client = MatrixClient.builder()
        .baseUrl("http://127.0.0.1:8765")
        .apiKey(token)          // the bearer token from POST /v1/auth/login
        .timeout(Duration.ofSeconds(30))
        .build()) {

    AnalyzeResponse response = client.analyze()
            .text("What is the capital of France?")
            .call();

    System.out.println(response.reply);
    System.out.println(response.confidence);
    System.out.println(response.explainId);
}
```

`AnalyzeResponse` is a plain data holder with public fields — `reply`, `confidence`,
`durationMs`, `accepted`, `explainId`, `modulatorsFired` — not a record, so the fields are
read directly and not through accessors.

**The version is part of the API.** `MatrixClient.VERSION` is `0.1.0-T08`, and the branch
validation gate requires every module's public facade to declare one: a client that cannot
say which version it is cannot be pinned by the application that uses it.

### What the client can call

| the call | the route |
|---|---|
| `client.analyze()` → `AnalyzeCall` → `.text(...)`, `.call()`, `.callAsync()` | `POST /v1/analyze` |
| `client.explain(explainId)` | `GET /v1/explain/{id}` — the explanation is held **in memory** by the gateway, so the id is valid only while that process lives |
| `client.federate(FederateRequest)` | `POST /v1/federate` |

## The gateway's own authentication

There is no MATRIX-hosted key. The token comes from the gateway you are running:

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
```

## And if you would rather use plain HTTP

The gateway speaks JSON over HTTP, and every route it registers is in
[the API reference](/ecosystem/api/). The SDK saves a little boilerplate; it is not a
requirement.

## Next

- [Quickstart](/ecosystem/quickstart/) — start the gateway the client talks to.
- [API reference](/ecosystem/api/) — the routes.
- [The runtime topology](../../architecture/RUNTIME-TOPOLOGY.html) — what the gateway really serves, and in which mode.
