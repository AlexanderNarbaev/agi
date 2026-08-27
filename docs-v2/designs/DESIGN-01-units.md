# DESIGN-01 — Вычислительная единица

**Статус: normative** · пересмотр (v2 rebuild).

## Что

Атомарная неизменяемая вычислительная единица MATRIX — `BirUnit = (id, arityIn k, arityOut m, form, payload, header)` в одной из трёх BIR-форм (TT/CLAUSESET/BDD). Контракт `evaluate(Bir, long[]) → long[]` для m выходов.

## Реализация

`bir/`:
- `Bir` (sealed interface) и `BirForm` (abstract base class) с тремя permits: TtForm/ClauseSetForm/BddForm.
- `BirCompiler` — компиляция между формами; lineage в `LineageLedger`.
- `BooleanRuntime` — единая точка инференса.

Тесты: `bir/*` (включая property-тесты на переходы forms и эквивалентность).

## Метрики / гейты

`evaluate` время ≤1.2 нс на packed word (real: 0.642 нс JMH-протокол DESIGN-14#§волна6). FROZEN `ethics/frozen/`.

## Отложено

См. [architecture/FORMAL-CONTRACTS.md](../architecture/FORMAL-CONTRACTS.md) — формальные свойства TtForm/ClauseSetForm/BddForm.