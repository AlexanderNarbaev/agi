**Статус: normative · singleton** · пересмотр 2026-08-27 (brain wave v6 corrections).

# CONCEPT-CORRECTIONS — актуальная архитектура vs. устаревшие формулировки

Этот документ фиксирует **коррекции концептуальной целостности** активной документации `docs-v2/`. Цель — предотвратить возврат к устаревшим именованиям/моделям при дальнейших правках.

## Актуальная архитектура (2026-08-27)

- **Атомарная вычислительная единица** = `BirUnit` (`bir/Bir` + одна из 3 форм TT/CLAUSESET/BDD), единственная точка исполнения `BooleanRuntime.evaluate`.
- **Рантайм**: детерминирован, K_MAX ≤ 20, FROZEN-этика неизменна.
- **Продюсеры знаний**: TsetlinTrainer (этап B FR-B1/B2 принят), WisardProducer, MpdtGaProducer (используется как baseline-сравнение для H-002/H-003).
- **Память**: m0/M0–M4/M5 иерархия (см. `architecture/REQUEST-memory-hierarchy.md`).
- **Федерация**: ElspChannel (Ed25519) + ElspChannelMlDsa (ML-DSA, постквант).
- **TLA+**: `FrozenEthicalFNL`, `BotEthicsPipeline`, `HashChain` формально специфицированы; `BRC-Step`, `ConjugateBudgeter-DP`, `Memory-M4-Causal`, `MCTS-LATS-Visit` — next-format-contracts.

## Таблица коррекций

| Устаревшая формулировка | Актуальная | Где исправлено |
|---|---|---|
| MPDT-нейрон = «атомарная вычислительная единица» | `BirUnit` (три формы TT/CLAUSESET/BDD, K_MAX ≤ 20) | `levels/L1-BirUnit-Legacy.md` (legacy-архив полнота); `specifications/SPEC-002-boolean-compute-layer.md` |
| «GA on MPDT chromosomes» | «GA на BIR clause-set genomes» (хромосома = `ClauseSetForm` через `evolution/MpdtGaProducer.java`) | `levels/L5-DNA.md`; `evolution/MpdtGaProducer.java` |
| «MPDT neurons (L1) compose» | BirUnit → NeuronClusterActor → FNL | `levels/L3-Neurocluster-Arch.md` |
| H-008: «MPDT proof memory batch mode» | BIR proof memory batch mode | `research/HYPOTHESES.md` (H-008) |
| «MPDT-форма» в обсуждениях рантайма | BirForm (TT/CLAUSESET/BDD) | везде при упоминании рантайм-контура |

## Файлы, сохраняющие устаревшее имя в качестве LEGACY (для архивной полноты)

- `levels/L1-BirUnit-Legacy.md` — превосходно оформляет: «LEGACY, BirUnit — primary atomic compute element since BIR migration; этот файл — archive-completeness».
- `algorithms/MPDT-GA.md` — название продюсера в коде (`evolution/MpdtGaProducer.java`); не путать с терминологией «MPDT-нейрон».

## Что НЕ нуждается в правке

- `algorithms/MPDT-GA.md` — корректное название baseline-продюсера (класс в коде), не термин.
- `levels/L5-DNA.md` после правки — заголовок «Genome» сохранён (приемлемо: геном — это сущность, носитель — BIR clause-set).
- `docs-v2/research/protocols/H-002-clauseset-vs-ga.md` — название соответствует карточке H-002 в HYPOTHESES, корректно.
- `docs-v2/research/reports/EXP-002-report.md` — термин «MPDT-GA» используется для обозначения baseline-сравнения, не для определения юнита.

## Принципы коррекций

1. **Имена классов в коде** остаются (это контракт кода; нельзя переименовать без миграции).
2. **Имена уровней / документов** — переименовываем явно с явной пометкой «LEGACY» при archive-полноте.
3. **Формулировки про рантайм** всегда про BIR, а не про MPDT-Neuron.
4. **Baseline-сравнения** (MpdtGaProducer) — допустимы и поощряются.
5. **Архив остаётся историческим** — формулировки в `docs-v2/archive/` не трогаем.

Next: для следующего шага чтения вернитесь к `INDEX.md` или к `vision/FINALSUMMARY.md`.