---
layout: default
title: API Reference
nav_order: 3
parent: Ecosystem
permalink: /ecosystem/api/
---

# API Reference

MATRIX exposes a secure REST API at `https://api.matrix.ai`. All endpoints
return JSON. Authentication via Bearer token.

## Base URL

```
https://api.matrix.ai
```

## Authentication

Every request must include a Bearer token:

```bash
Authorization: Bearer sk-matrix-...
```

Get a token at the [console](https://api.matrix.ai/console). All requests are
TLS 1.3 encrypted.

## Endpoints

### `POST /v1/analyze`

Run hybrid inference on text, audio, or image input.

**Required role:** DEVELOPER or higher

**Request body:**

```json
{
  "input": "string (1 MiB max)",
  "content_type": "text | audio | image",
  "context": "string (64 KiB max, optional)",
  "model": "string (default: 'default')"
}
```

**Response 200:**

```json
{
  "reply": "Paris is the capital of France.",
  "confidence": 0.95,
  "duration_ms": 23,
  "accepted": true,
  "explain_id": "abc123def456",
  "modulators_fired": ["ETHICAL_FILTER", "CONSISTENCY_CHECKER"]
}
```

**Status codes:**
- `200` — Success
- `400` — Invalid input (size, encoding, content-type)
- `401` — Missing or invalid Bearer token
- `403` — Insufficient role (need DEVELOPER+)
- `429` — Rate limit exceeded

---

### `GET /v1/explain/{id}`

Retrieve the full XAI breakdown for a previous decision.

**Required role:** VIEWER or higher

**Path parameters:**
- `id` — opaque explain_id from a previous `analyze` response

**Response 200:**

```json
{
  "explain_id": "abc123def456",
  "steps": [
    {"stage": "INPUT_NORMALIZED", "action": "normalized", "duration_ms": 1},
    {"stage": "BIR_RULES_FIRED", "action": "rule-42, rule-87", "duration_ms": 3},
    {"stage": "HDC_MEMORY_RETRIEVED", "action": "kb-doc-42", "duration_ms": 5},
    {"stage": "MCTS_PLAN_SELECTED", "action": "plan-12", "duration_ms": 4},
    {"stage": "MODULATORS_APPLIED", "action": "ALL_PASSED", "duration_ms": 2}
  ],
  "modulator_snapshot": {
    "ethical_filter": 0.95,
    "safety_monitor": 0.92,
    "consistency_checker": 0.88,
    "lie_detector": 0.91
  },
  "hdc_memory_hits": ["kb-doc-42", "kb-doc-128"],
  "confidence_breakdown": {
    "bir_confidence": 0.85,
    "hdc_confidence": 0.78,
    "mcts_confidence": 0.72,
    "aggregate": 0.95
  }
}
```

**Status codes:**
- `200` — Success
- `404` — Unknown explain_id

---

### `POST /v1/federate`

Join a new federation node to the MATRIX liquid federation pool.

**Required role:** DEVELOPER or higher

**Request body:**

```json
{
  "region": "us-east | eu-west | ap-south | ...",
  "shard_capacity": 100
}
```

**Response 201:**

```json
{
  "node_id": "node_abc123def456",
  "region": "us-east",
  "shard_capacity": 100,
  "joined_at": "2026-09-21T17:00:00Z",
  "total_nodes": 42
}
```

**Status codes:**
- `201` — Node joined
- `400` — Invalid region or shard_capacity

---

### `GET /v1/federate`

List all federation nodes in the current pool.

**Required role:** VIEWER or higher

**Response 200:**

```json
[
  {"node_id": "node_abc", "region": "us-east", "shard_capacity": 100, "joined_at": "..."},
  {"node_id": "node_def", "region": "eu-west", "shard_capacity": 200, "joined_at": "..."}
]
```

---

### `GET /v1/audit/logs`

Retrieve the immutable action log (admin only).

**Required role:** ADMIN

**Query parameters:**
- `limit` — 1..1000 (default 100)

**Response 200:**

```json
[
  {
    "timestamp": "2026-09-21T17:00:00Z",
    "user_id": "user-1",
    "action": "POST /v1/analyze",
    "target": "alice@example.com",
    "status_code": 200
  }
]
```

## Rate limits

| Plan | Requests/hour |
|------|---------------|
| FREE | 100 |
| PRO | 1,000 |
| ENTERPRISE | unlimited |

Rate-limited responses include:

```
X-RateLimit-Plan: FREE
X-RateLimit-Used: 0.42
```

## Error responses

All errors return JSON in the form:

```json
{"error": "Human-readable error message"}
```

## OpenAPI spec

The full OpenAPI 3.0 spec is at
[`src/main/resources/openapi.yaml`](https://github.com/AlexanderNarbaev/agi/blob/develop/matrix-api-gateway/src/main/resources/openapi.yaml).

Interactive Swagger UI is at `/q/swagger-ui` when running the gateway locally.

---

**Last updated:** 2026-09-21 (Wave T-03)
