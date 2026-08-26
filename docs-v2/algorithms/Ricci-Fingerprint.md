# Ricci Fingerprint (Ollivier κ → 24-bin drift → Wasserstein-1)

**Статус: normative** · changelog 2026-08-26 — brain wave v2 algorithms.

## Что

Компактный сигнатурный дескриптор графа знаний: L1-нормированная гистограмма кривизн Ollivier-Ricci по 24 бинам. Используется для отслеживания структурной деградации knowledge graph между снимками. Источник: `matrix-core/src/main/java/io/matrix/ktopo/{Graph,OllivierRicciCalculator,DriftFingerprint,FingerprintDistance}.java`. Соответствует DESIGN-14 (Batch*-метрика структуры).

## Ollivier κ — определение

Для смежных `x, y` в графе `G`:

- мера `μ_x(v) = α` при `v = x`; `μ_x(v) = (1−α) / deg(x)` для каждого соседа `v`.
- `κ(x,y) = 1 − W1(μ_x, μ_y) / d(x,y)`, где `d` — кратчайший путь, `W1` — 1-Вasserstein.

Знаковая конвенция: `κ > 0` — плотные сообщества, `κ ≈ 0` — пути/деревья, `κ < 0` — мосты/ветвления. Дефолт `α = DEFAULT_ALPHA = 0.15` (классика из Ni et al. 2019).

## Точный W1 через SSAP

`OllivierRicciCalculator.wassersteinBetween` сужает носитель до `{x, y} ∪ N(x) ∪ N(y)` (≤ 33 вершин), затем решает min-cost transportation LP алгоритмом SSAP (Successive Shortest Augmenting Path) с Bellman-Ford в остаточной сети. Два ошибочных подхода отвергнуты явно:

- жадное «sorted-mass matching» — неоптимально на неметрических носителях;
- правило «North-West corner» — даёт только допустимое начальное решение, не оптимальное.

Граница: `MAX_DEGREE = 32` — вершины со степенью выше бросают `IllegalArgumentException` (сохраняет детерминизм и оценку сложности).

## 24-bin Drift Fingerprint

`DriftFingerprint.of(curvatures)` строит гистограмму по сетке:

- `KAPPA_MIN = −2.0`, `KAPPA_MAX = +1.0` — теоретические границы Ollivier-Ricci на простых графах.
- `BIN_WIDTH = (KAPPA_MAX − KAPPA_MIN) / 24 = 1/8`.
- значения вне диапазона зажимаются; пустой вход → uniform `1/24` (нейтральный baseline).
- `bins[i] /= |curvatures|` — L1-нормировка (сумма = 1).

Фиксированная схема из 24 бинов — контракт сопоставимости между снимками (DESIGN-14 invariant).

## Wasserstein-1 closed form

Для распределений на прямой (Vallender 1974): `W1(μ, ν) = ∫ |F_μ(t) − F_ν(t)| dt`. На равномерной сетке бинов это сводится к:

```
distance(a, b) = Σ_i |cumA_i − cumB_i| · BIN_WIDTH
```

где `cumA_i = Σ_{j≤i} a[j]`, `cumB_i = Σ_{j≤i} b[j]`. Один линейный проход по 24 элементам, без сортировок. Результат ∈ [0, KAPPA_MAX − KAPPA_MIN] — нормированный десинхронизатор.

## Метрики / гейты

- Детерминизм: массивы, фиксированный порядок итерации; соседи сортируются по индексу (`OllivierRicciCalculator.neighbors`).
- Юнит `OllivierRicciCalculatorTest`: известные значения на пути/клике/двух кликах с мостом.
- Curriculum-orderer использует плотность рёбер (не κ) для dense→periphery обхода (DESIGN-14, отдельный компонент).

## Открытые вопросы

- Расширение `MAX_DEGREE > 32` (потребует LP-солвера вместо SSAP) — отложено.
- Многомасштабный fingerprint (несколько α) для устойчивости — отложено.
- Связь с Ricci flow (`ktopo/RicciFlow.java`) для нормализации деградации — отложено.

Next: см. файл FROZEN-EthicalFNL.md в той же папке для следующей темы.
