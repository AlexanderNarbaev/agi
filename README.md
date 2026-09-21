# MATRIX — детерминированное нейро-символическое ядро

> Каждое решение — проверяемая булева цепочка. Этика — FROZEN-слой, верифицируемый TLA+.
> Одинаковые состояние и вход → одинаковый выход.

## Что это

MATRIX — нейро-символическая система, где «знание» — таблицы истинности и их компилируемые формы (TT / CLAUSESET / BDD), а не веса чёрного ящика. Обучение отделено от рантайма: рантайм исполняет только BIR-артефакты — детерминированно, без LLM-вызовов и случайности.

## Быстрый старт

```bash
docker compose -f docker-compose.dev.yml up -d   # инфраструктура
./gradlew test                                    # юнит-тесты
./gradlew :matrix-core:quarkusDev                 # dev :9091
```

## Где что

- Архитектура и обзор → [docs-v2/architecture/OVERVIEW.md](docs-v2/architecture/OVERVIEW.md)
- Карта документации v2 → [docs-v2/INDEX.md](docs-v2/INDEX.md)
- Нормы → [CONSTITUTION.md](CONSTITUTION.md), [AGENTS.md](AGENTS.md)
- Состояние проекта → [WAL.md](WAL.md)
- Экспериментальные гипотезы → [docs-v2/research/HYPOTHESES.md](docs-v2/research/HYPOTHESES.md)
- Гипотезы H-010 accepted (WiSARD ×242 vs Tsetlin), H-002/H-003 refuted-toy, EXP-009C GPU нога (RTX 5070 Ti) см. [docs-v2/research/reports/](docs-v2/research/reports/).

## Стек

Java 25 · Quarkus 3.38.3 · GraalVM plugin 1.1.10 · Avro 1.12.2 · Kafka-clients 4.3.1 · ONNX Runtime 1.29.0 · Testcontainers 1.21.3 · Postquantum ML-DSA (JEP 497). Полная матрица в [docs-v2/engineering/STANDARDS-MATRIX.md](docs-v2/engineering/STANDARDS-MATRIX.md).

## Статус ядра

- BIR-исполнение — единая точка для кластера, API, explain, neuron (INV-1 страж в CI).
- Продюсеры знаний: Tsetlin (принят), WiSARD (**H-010 accepted**), MPDT-GA baseline (H-002 refuted-toy).
- Curriculum-стек SPEC-000: ассессор, движок задач ZPD, гейты MA-0…MA-5.
- Память M0–M4, ricci-топология знаний, ELSP-федерация с anti-replay и постквант-профилем ML-DSA.
- Полный план и остатки: [docs-v2/engineering/PLAN.md](docs-v2/engineering/PLAN.md).
