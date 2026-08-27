# SPEC-003 — Knowledge Topology

**Статус: normative** · пересмотр (v2 rebuild).

## Что

Инструменты структурного анализа графов знаний:
- Ollivier–Ricci curvature κ на рёбрах (распределения мер по соседям + транспортные стоимости).
- Drift-fingerprint: гистограмма кривизн 24 bins κ∈[-2,1], L1-нормализованная, расстояние = exact 1D Wasserstein-1 (closed-form Σ|ΔCDF|·binWidth).
- Curriculum-ordering dense→periphery: компоненты по плотности, детерминированный порядок.

## Реализация в коде

Пакет `ktopo/` (`io.matrix.ktopo.*`):
- `Graph(n, u[], v[], w[])`, `Graph.of(double[][])`.
- `OllivierRicciCalculator.computeCurvatures(Graph)`.
- `DriftFingerprint.of(double[])` → 24-bin массив.
- `FingerprintDistance.wasserstein1(a, b, binWidth)`, `distance(a, b)`.
- `CurriculumOrderer.order(Graph, names)` → `List<List<String>>` (компоненты dense-first, внутри lexicographic).

Тесты: `KtopoPropertiesTest` (unit + jqwik: W1≥0, симметрия, Σbins≈1, d(f,f)=0, порядок покрывает все вершины).

## BLOCKED-EXT

- Кросс-аналитика с реальными доменными графами (rag-system корпуса были удалены).
- Полный EXP-004 на benchmark-графе (READ-открытое, BLOOM-уравнения графа) — следующая сессия.

См. [architecture/MODULES.md](../architecture/MODULES.md), [research/HYPOTHESES.md](../research/HYPOTHESES.md).