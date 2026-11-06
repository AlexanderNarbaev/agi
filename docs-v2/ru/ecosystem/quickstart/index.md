---
layout: default
title: Быстрый старт
nav_order: 2
parent: Экосистема
permalink: /ru/ecosystem/quickstart/
---

**Статус:** ACTIVE · **Обновлено:** 2026-11-06

# Быстрый старт — от клона до первого ответа

> **Размещённого MATRIX API не существует.** Нет ни `api.matrix.ai`, ни API-ключа, ни
> тарифа, ни лимита. Есть репозиторий, и два приложения запускаются из исходников.

## Что нужно

- **JDK 25** — сборка выставляет `sourceCompatibility = VERSION_25`, поэтому JDK, который
  видит сборка, должен быть 25, а не просто JRE в `PATH`. Проверьте `java -version`.
- **Git.**
- **Больше ничего.** Драйвер SQLite — объявленная зависимость, формальный инструмент
  лежит в репозитории под `tools/`, его дайджест записан в `tools/README.md`.

## Шаг 1 — получить

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi
```

## Шаг 2 — запустить цепочку A

```bash
./gradlew :matrix-core:quarkusDev
```

Отвечает на **http://127.0.0.1:9091**. Health — `/q/health`:

```bash
curl -s http://127.0.0.1:9091/q/health
```

**Проба готовности отвечает 503** — её включает реактивный источник PostgreSQL, и
производственный код её не читает. Пользуйтесь пробой живости.

## Шаг 3 — запустить цепочку B

```bash
./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain
bash scripts/start-mind.sh
```

Отвечает на **http://127.0.0.1:8765**. Лаунчер выбирает production-режим, поэтому
запускается настоящий мозг.

## Шаг 4 — проверить, какой мозг отвечает

Это первое, что стоит проверить. Запущенный **без** `MATRIX_MODE=production`, шлюз
отвечает из 87-строчной заглушки и сообщает `"brain_available": false`; запущенный через
`scripts/start-mind.sh` — настоящий цикл.

```bash
curl -s http://127.0.0.1:8765/health/live
```

## Шаг 5 — задать вопрос

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -X POST http://127.0.0.1:8765/v1/teach \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input":"What is the capital of France?","response":"Paris is the capital of France"}'

curl -s -X POST http://127.0.0.1:8765/v1/analyze \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input":"What is the capital of France?"}'
```

`/v1/teach` и `/v1/learn` в режиме `stub` отвечают **503 с указанием причины**: им нужен
настоящий мозг.

## Два режима

| режим | как выбирается | что обслуживает | что вы получаете |
|---|---|---|---|
| **`stub`** — по умолчанию | ничего задавать не нужно | `StubBrainCycle`, 87 строк | детерминированный ответ по ключевым словам; маршруты, которым нужен настоящий мозг, отвечают 503 с причиной |
| **`production`** | `MATRIX_MODE=production`, его выставляет `scripts/start-mind.sh` | `ProductionBrainClient` → `TrueMindCycle` → `BirBrainCycle` | настоящий мозг: BIR, HDC, модуляторы, база знаний, цикл обучения |

Режим сообщает `GET /health/live`. **Это открытый вопрос владельца F-01**: шлюз, который
по умолчанию обслуживает заглушку, — это шлюз, чья проверка здоровья может вводить в
заблуждение.

Если в production-режиме не найден JAR `matrix-core`, `ProductionBrainClient` бросает
`BrainUnavailableException`, и шлюз отвечает **503** с `Retry-After: 5`; заглушка
становится автоматической подменой.

## В контейнерах

```bash
export MATRIX_SECURITY_TOKEN="$(openssl rand -hex 32)"
docker compose up -d
```

Стек не стартует без токена, и приложение отказывает в открытых анонимных записях без
него. Профиль **`native` на машине разработчика не работает**: инструментарий GraalVM
отсутствует, native-scope отказывается запускаться, а возможность остаётся `BLOCKED`.

## Тесты и гейты

```bash
./gradlew :matrix-core:test --tests "io.matrix.knowledge.*"
bash scripts/quality-gate.sh
bash scripts/security-boundary-check.sh
```

Проверка свежего клона: `./scripts/fresh-clone-smoke.sh`.

## Дальше

- [Руководство по началу работы](../../guide/getting-started.html) — полная последовательность и существующая конфигурация.
- [Справочник API](/ru/ecosystem/api/) — реальные поверхности маршрутов.
- [Архитектура](/ru/ecosystem/architecture/) — граф модулей.
