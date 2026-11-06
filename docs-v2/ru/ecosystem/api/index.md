---
layout: default
title: Справочник API
nav_order: 3
parent: Экосистема
permalink: /ru/ecosystem/api/
---

**Статус:** ACTIVE · **Обновлено:** 2026-11-06

# Справочник API

> **Размещённого MATRIX API не существует.** Этот репозиторий обслуживает два
> приложения из исходников, на двух портах, и эта страница описывает маршруты, которые
> они действительно регистрируют. Прежнее описание размещённого адреса
> `api.matrix.ai` с ключами, тарифами и лимитами описывало несуществующий сервис и
> удалено, а не сохранено.

Всё ниже прочитано из кода и из переписи константного пула, на которой стоит
[`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../../../architecture/RUNTIME-TOPOLOGY.md).

## Две поверхности

| | **цепочка A** | **цепочка B** |
|---|---|---|
| адрес | `http://127.0.0.1:9091` | `http://127.0.0.1:8765` |
| процесс | Quarkus, `io.matrix.MatrixApplication` | JDK `HttpServer`, `io.matrix.api.MinimalHttpServer` |
| маршруты | **210** | **28** |
| аутентификация | режим безопасности развёртывания; в контейнерном стеке обязателен `MATRIX_SECURITY_TOKEN`, и приложение без него отказывает в открытых анонимных записях | `/v1/auth/login` возвращает bearer-токен |

## Цепочка A — эпистемическое приложение

| поверхность | маршрутов | ресурс |
|---|---|---|
| `/v1/workbench` | 57 | `LearningWorkbenchResource` — рабочий стол знаний: воркспейсы, документы, концепты, утверждения, источники, свидетельства, репрезентации, ревизии, деструктивная загрузка и восстановление |
| `/v1/cognitive` | 11 | `CognitiveResource` — observe, solve, summary, predictions, errors, procedures, tasks |
| `/v1` (chat) | 6 | `OpenAIChatResource` — `/v1/chat/completions`, `/v1/models`, `/v1/embeddings` и маршруты `/v1/brain/**` (`think`, `plan`, `subagent`) |
| ещё 36 ресурсов | 136 | `/api/v1/**` и `/v1/**` |

Health — по корневому пути SmallRye `/q/health`. **Проба готовности отвечает 503** — её
включает реактивный источник PostgreSQL, и производственный код её не читает.

## Цепочка B — мозговой рантайм

| маршрут | что делает |
|---|---|
| `/v1/analyze` | запрос вывода |
| `/v1/explain`, `/v1/explain/{id}` | число сохранённых объяснений и одно сохранённое объяснение |
| `/v1/audit`, `/v1/audit/logs`, `/v1/audit/promotions`, `/v1/audit/verify` | журнал аудита, решения о продвижении — включая отказы — и проверка хеш-цепочки |
| `/v1/bir` | поверхность булева вывода |
| `/v1/conflicts`, `/v1/distill`, `/v1/federate`, `/v1/generalize` | записанные конфликты, дистилляция, федерация, обобщение |
| `/v1/gpu` | состояние ускорителя — сообщает UNAVAILABLE при отсутствии GPU и никогда не имитирует успех |
| `/v1/goals`, `/v1/inbox/scan`, `/v1/status` | трекер целей, сканирование входящих, статус рантайма |
| `/v1/learn`, `/v1/teach` | цикл обучения — в режиме `stub` отвечают 503 |
| `/v1/sleep` | цикл сна |
| `/v1/transcode/audio`, `/v1/transcode/image` | транскодеры |
| `/v1/auth/login` | bearer-токен |
| `/health/live`, `/health/ready`, `/metrics`, `/q/openapi`, `/openapi.yaml`, `/` | пробы, метрики и схема |

**Два предела этой поверхности.** Объяснения хранятся **в памяти** процесса шлюза, поэтому
`explain_id` действителен, только пока живёт процесс, и ни в какой снапшот не входит. И
**режим по умолчанию — `stub`**: без `MATRIX_MODE=production` `/v1/analyze` отвечает из
87-строчного `StubBrainCycle`, а режим сообщает `GET /health/live`.

## Первый обмен, как он есть

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -X POST http://127.0.0.1:8765/v1/analyze \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input":"What is the capital of France?"}'
```

**Форма ответа зависит от режима.** Неизвестный `explain_id` отвечает **404**, а
отсутствующий или неверный токен — **401**.

## Чего здесь нет

- **Ни размещённого адреса, ни ключа, ни тарифа, ни лимита** — ничего из этого в
  репозитории не реализовано.
- **Девять объявленных JAX-RS-маршрутов шлюза не смонтированы**: `BrainQuarkusResource`
  (4), `AnalyzeResource`, `AuditResource`, `ExplainResource` (2) и `FederateResource` (2).
  Объявленный, но не смонтированный путь отвечает 404.

Полные страницы: [Быстрый старт](/ru/ecosystem/quickstart/) ·
[Топология рантайма](../../../architecture/RUNTIME-TOPOLOGY.html).
