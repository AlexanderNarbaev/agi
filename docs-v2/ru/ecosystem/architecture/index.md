---
layout: default
title: Архитектура
nav_order: 6
parent: Экосистема
permalink: /ru/ecosystem/architecture/
---

# Архитектура

> **EN-документация является канонической.** Переводы добавляются постегенно.
> См. полную документацию: [Architecture (EN)](/ecosystem/architecture/)

## Краткий обзор

MATRIX — **monorepo** с 9 модулями в двух слоях:

**Слой 1 — Исследовательское ядро (FROZEN at W1500):**
- `matrix-core` — 1,039 Java-файлов, 1,119+ тестов
- `matrix-tools-distill` — ONNX distillation
- `matrix-spigot` — Minecraft Spigot bridge
- `matrix-operator` — Kubernetes operator

**Слой 2 — Экосистема (T-01 .. T-10):**
- `matrix-api-gateway` — REST API + auth + rate limit
- `matrix-sdk-java` — Java SDK
- `matrix-audit` — hash-chained audit logs
- `matrix-billing` — Stripe + credit ledger
- `matrix-observability` — Prometheus/Grafana

Подробная документация на английском: [Architecture →](/ecosystem/architecture/)

---

**Последнее обновление:** 2026-09-21 (Волна T-03)
