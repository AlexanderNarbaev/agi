# SUBSTRATE-MODELS — реестр вычислительных теорий MATRIX

**Статус: living** (inline 2026-08-26 из `vision/vision/SUBSTRATE-MODELS.md` и `science/science/SUBSTRATE-MODELS.md` — единая консолидация).

## Системная рамка

Контур: «мир» = непрерывная проба сенсоров → преобразование (LUT-подобные модули DESIGN-06) → делиберация (DESIGN-13/15) → рендеринг (DESIGN-06) → обратная связь среды (events/, lineage). Соответствует DESIGN-03 pipeline.

## 1. Конечные автоматы — за пределами автоматов Цетлина

- Tsetlin Automaton (DESIGN-04, реализован в `tsetlin/TsetlinAutomaton`): Гранмо Type I/II с прогрессом w.p.(s−1)/s / 1/s; детерминированность при фиксированном seed.
- WiSARD WNN (DESIGN-04, `tsetlin/WisardProducer`): RAM-гранулярность + запись по адресу; один проход обучения.
- MpdtGaProducer (DESIGN-04, `evolution/`): элитизм 25%, турнир, per-clause кроссовер, мутация p=1/K, MDL-давление λ=0.1.

**Граница**: полиномиальные потери при нелинейно-разделимых функциях без латентного пространства; решается добавлением AND/OR-пространства (CLAUSESET).

## 2. Свёрточные схемы и ядра

- Дистилляция FFN в BIR (SPEC-001 Этап B, `distill/`): редукция точности пропорциональна объёму CLAUSESET-литералов; экспериментально .999 fidelity на синтетическом FFN16.
- Ядро `BooleanRuntime` (SPEC-002 Этап A): packed long-walks; SIMD-утилиты `Batch*` (32–69M ops/s JMH).

**Граница**: при свёртках высокой размерности оценки показывают, что ёмкость CLAUSESET растёт как ~claudes × literals²; на практике ищем минимальное покрытие.

## 3. Комбинаторика памяти

- `noosphere/Crdt`: коммутативная/ассоциативная/идемпотентная merge-семантика.
- `memory/HierarchicalMemory` (M0–M4): сериализация через hash-цепь `audit/HashChain` с append-only гарантиями.

**Граница**: eventual consistency M4 — нужен quorum R/W для causal-консистентности (отложено `Memory-M4-Causal` TLA+).

## 4. Числовая классификация знаний

- `ktopo/`: Ollivier-Ricci curvature, drift-fingerprint (24 bins), Wasserstein-1 расстояние (closed-form), curriculum ordering dense→periphery.

**Граница**: scale: O(V³ × E × d²) per Ricci snapshot; partial (sample) computation.

## 5. Мозжечковый слой

- `signals/SignalModule`: LUT-подобные encoder/decoder; FROZEN после stage3 (`audio-events`).
- `brain/Viewpoint`: weighted ensemble на уровне L2; tie-break по min-name (детерминированно).

## Известные ограничения (честно)

- LLM-вызовы не входят в вычислительный путь (CONSTITUTION VI + принцип).
- Random/wall-clock в рантайме запрещены (CONSTITUTION I).
- Квантовый субстрат вне этого горизонта (SPEC-002-quantum).