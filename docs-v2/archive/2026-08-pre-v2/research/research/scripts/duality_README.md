# DUALITY Protocol — Preregistered инструкция

**PREREGISTRATION TIMESTAMP:** 2026-08-10
**Статус:** proposed (preregistered; любые изменения после сбора данных = HARKing, запрещены)

## Быстрый запуск

```bash
cd docs/research
python scripts/duality_protocol.py
```

## Что делает

Генерирует **600 синтетических суждений** (3 культурных корпуса × 200 суждений каждый) с детерминированным seed-ом и вычисляет preregistered метрики для 4 гипотез:

| Гипотеза | Метрики | Seed |
|----------|---------|------|
| H-023 | χ² F-критерий, bootstrap CI Cohen's h, FDR (Benjamini-Hochberg) | 0xD04C |
| H-024 | Spearman ρ (RAT ~ conformity), ΔAccuracy (low-RAT vs high-RAT) | 0xD04C |
| H-025 | DerSimonian-Laird random-effects meta-analysis, I², Cochran's Q | 0xD04C |
| H-026 | Cosine similarity (one-hot рационализация × онтология), permutation test | 0xD04C |

## Генерируемые файлы

| Файл | Описание |
|------|----------|
| `reports/EXP-023-report.json` | Полные результаты (machine-readable) |
| `reports/EXP-023-report.md` | Skeleton отчёт H-023 (PENDING до реальных данных) |
| `reports/EXP-024-report.md` | Skeleton отчёт H-024 |
| `reports/EXP-025-report.md` | Skeleton отчёт H-025 |
| `reports/EXP-026-report.md` | Skeleton отчёт H-026 |

## Корпусы (preregistered)

| ID | Название | Суждений | Культурный контекст |
|----|----------|----------|---------------------|
| `modernist_western` | Западный индивидуалистический | 200 | personal agency, self-expression, autonomy |
| `collectivist_east` | Восточный коллективистский | 200 | group harmony, duty, interdependence |
| `traditional_african` | Африканский традиционный | 200 | ancestral wisdom, community solidarity, ritual |

## Preregistered seeds

- **Protocol seed:** `0xD04C` (фиксирован на 2026-08-10)
- **Bootstrap seed:** `default_rng(0xD04C)` — 10 000 ресамплов
- **Permutation seed:** `default_rng(0xD04C)` — 1 000 перестановок
- **Все варианты (v0–v9):** детерминированные SHA-256 → seed per item

## Зависимости

Python ≥ 3.10, numpy, scipy. Без ML-моделей, без внешних API, без LLM.

## ⚠ Предупреждение

Любые изменения ПОСЛЕ preregistration (2026-08-10) должны быть явно помечены как **DEVIATION** в changelog соответствующего .md файла. HARKing запрещён (CONSTITUTION VI.3).
