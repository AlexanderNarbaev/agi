**Статус: LEGACY · normative (archive-completeness)** · пересмотр 2026-08-26 (brain wave v6; primary compute element now BirUnit).

# L1-BirUnit-Legacy — MPDT-Neuron historical definition

## ⚠ Корректировка

Этот документ сохранён для **исторической полноты архива**. Текущий первичный вычислительный элемент — **BirUnit** (см. спецификацию `specifications/SPEC-002-boolean-compute-layer.md` и дизайн `designs/DESIGN-01-units.md`).

В ранней архитектуре MATRIX атомарная вычислительная единица называлась **MPDT-нейрон** (McCulloch–Pitts Decision Tree Neuron). После волн миграции DESIGN-14 (Источник: `engineering/SDD-COVERAGE.md` + аннекс `engineering/DESIGN-14-call-site-audit.md`) этот термин **вытеснен** BirUnit: единая точка исполнения (`BooleanRuntime.evaluate`) гарантирует, что весь рантайм работает в одной из трёх BIR-форм (TT / CLAUSESET / BDD), а не в оригинальной MPDT-семантике.

## Содержание (для archive-completeness)

Историческая формальная модель (без изменений):

The **MPDT neuron** (McCulloch–Pitts Decision Tree Neuron) — атомарный вычислительный элемент; обобщение порогового нейрона 1943 года через замену линейной ступени на произвольную k-арную булеву функцию, кодируемую таблицей истинности или деревом решений.

Кортеж: N = (id, k, F, S, W, meta).

| Field | Type | Note |
|---|---|---|
| id | NeuronId | UUID + generation counter |
| k | int | 1 ≤ k ≤ K_MAX (K_MAX = 20) |
| F | BooleanFunction | truth table or decision tree |
| S | NeuronState | lifecycle stage |
| W | WeightVector | w_i ∈ {1,2,3} |
| meta | Metadata | lineage, accuracy stats |

## Соответствие актуальной системе

| Поле MPDT | Актуальный эквивалент | Где |
|---|---|---|
| id | Bir.id (UUID v7 + immutability через BirRegistry) | `bir/Bir.java` |
| k | Bir.k (≤ 20, CONSTITUTION II) | `bir/TruthTable.K_MAX` |
| F | BirForm (TT / CLAUSESET / BDD) | `bir/BirForm.java` |
| S | Stage жизненного цикла BirUnit (DRAFT → CANDIDATE → ACTIVE → FROZEN) | `lifecycle/FnlGate.java` |
| W | не нужно: веса не входят в компилированный артефакт (см. SPEC-002 §II) | — |
| meta | `bir/LineageLedger`, `audit/HashChain` | `audit/HashChain.java` |

## Куда смотреть дальше

- Реальное устройство атомарной единицы → `designs/DESIGN-01-units.md`.
- Формальная BIR-модель → `specifications/SPEC-002-boolean-compute-layer.md`.
- TLA+-контракты → `architecture/FORMAL-CONTRACTS.md` (`BIR-Step` — кандидат).
- Корректировки по всем документам → `vision/CONCEPT-CORRECTIONS.md`.

Next: для следующего шага чтения по архитектуре перейдите к `INDEX.md` (единая навигация) или к `vision/BRAIN-LIKE-SYSTEM.md` (нарративный синтез).