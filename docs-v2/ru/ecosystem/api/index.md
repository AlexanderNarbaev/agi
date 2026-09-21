---
layout: default
title: API Reference
nav_order: 3
parent: Экосистема
permalink: /ru/ecosystem/api/
---

# API Reference

> **EN-документация является канонической.** Переводы добавляются постепенно.
> См. полную документацию: [API Reference (EN)](/ecosystem/api/)

## Краткий обзор

MATRIX предоставляет защищённый REST API по адресу `https://api.matrix.ai`.
Все эндпоинты возвращают JSON. Аутентификация через Bearer-токен.

## Эндпоинты

- `POST /v1/analyze` — гибридный inference (требуется роль DEVELOPER+)
- `GET /v1/explain/{id}` — XAI разбор (требуется VIEWER+)
- `POST /v1/federate` — присоединить federation узел (DEVELOPER+)
- `GET /v1/federate` — список узлов (VIEWER+)
- `GET /v1/audit/logs` — immutable action log (только ADMIN)

## Лимиты

| План | Запросов/час |
|------|--------------|
| FREE | 100 |
| PRO | 1,000 |
| ENTERPRISE | безлимитно |

Подробная документация на английском: [API Reference →](/ecosystem/api/)

---

**Последнее обновление:** 2026-09-21 (Волна T-03)
