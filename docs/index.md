# MATRIX (MENTAT) — детерминированное нейро-символическое ядро

> Каждое решение — проверяемая булева цепочка (BRC). Этика — FROZEN-слой, верифицируемый TLA+.
> Одинаковое состояние и вход → одинаковый выход.

## Что это

Нейро-символическая система, где «знание» — таблицы истинности и их компилируемые формы
(TT / CLAUSESET / BDD), а не веса чёрного ящика. Обучение отделено от рантайма:
рантайм исполняет только BIR-артефакты — детерминированно, без LLM-вызовов и случайности.

## Карта документации

| Раздел | Где |
|---|---|
| Видение и архитектура | [vision/ARCHITECTURE.md](vision/ARCHITECTURE.md) |
| Спеки (SPEC-000…003, quantum) | [spec/](spec/) |
| Дизайны (DESIGN-01…15) | [design/](design/) |
| Реестр миграции на BIR | [engineering/DESIGN-14-call-site-audit.md](engineering/DESIGN-14-call-site-audit.md) |
| Мастер-план волн реализации | [engineering/PLAN-FULL-IMPLEMENTATION.md](engineering/PLAN-FULL-IMPLEMENTATION.md) |
| Гипотезы и эксперименты | [research/HYPOTHESES.md](research/HYPOTHESES.md), [research/reports/](research/reports/) |
| Формальные модели (TLA+) | репозиторий: `formal/` |

## Статус ядра (кратко)

- BIR-исполнение — единая точка для кластера, API, explain, neuron (INV-1 страж в тестах).
- Продюсеры знаний: Tsetlin (принят), WiSARD (**H-010 accepted**), MPDT-GA baseline (H-002 refuted-toy).
- Curriculum-стек SPEC-000: ассессор, движок задач ZPD, гейты MA-0…MA-5.
- Память M0–M4, ricci-топология знаний, ELSP-федерация с anti-replay и постквант-профилем ML-DSA.
- Полный реестр: [engineering/PLAN-FULL-IMPLEMENTATION.md](engineering/PLAN-FULL-IMPLEMENTATION.md).

## Быстрый старт

```bash
docker compose -f docker-compose.dev.yml up -d   # инфраструктура
./gradlew test                                    # юнит-тесты
./gradlew :matrix-core:quarkusDev                 # dev-режим :9091
```
