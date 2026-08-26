# FROZEN Ethical FNL (монотонность запретов)

**Статус: normative (FROZEN)** · changelog 2026-08-26 — brain wave v2 algorithms.

> **FROZEN-зона.** Изменения только через RFC + отдельный ревьюер (см. CONSTITUTION + `AGENTS.md`). Здесь документируется алгоритмическое ядро, а не API-поверхность.

## Что

Неизменяемый слой безопасности (L7 §3.1): набор `FrozenAxiomNeuron` поверх `TruthTable`, упакованный через `Collections.unmodifiableSet(Set.copyOf(...))`. Любая попытка мутации набора требует пересоздания всей FNL через `Builder`. Источник: `matrix-core/src/main/java/io/matrix/ethics/frozen/FrozenEthicalFNL.java`. Соответствует DESIGN-12 (FNL/TaskCell карантин).

## Шесть аксиом (канонический набор)

1. `NO_KILLING` — триггер-бит 0.
2. `NO_TORTURE` — триггер-бит 1.
3. `NO_ENSLAVEMENT` — триггер-бит 2.
4. `NO_AUTONOMOUS_WEAPONS` — триггер-бит 3.
5. `TRUTHFULNESS` — триггер-бит 4.
6. `PRIVACY` — триггер-бит 5.

Каждый нейрон — `TruthTable` ширины `k = TextFeatureExtractor.DEFAULT_K = 16`, где бит `idx` установлен ⇔ «бит `bit` ∈ `idx`» равен 1. Тем самым срабатывание = наличие соответствующего триггера в фичах, извлечённых из входа.

## Монотонность запретов

Запрет «fire ⇒ REJECTED» — структурное свойство FROZEN-нейрона: добавление новых фичей в `BitSet features` не может УБРАТЬ срабатывание нейрона (расширение носителя сохраняет все ранее активные индексы в `TruthTable`). Следствие:

- если `f(features) = REJECTED`, то `f(features ∪ extra) = REJECTED` для любого `extra ⊆ [0, k)`. Это и есть монотонность запретов: «ослабить» входной фильтр нельзя.
- если `f(features) = APPROVED`, расширение может перевести его в REJECTED — это и есть безопасный путь (добавление факта риска).

## Линейная оценка

```
evaluate(BitSet features):
  for neuron in neurons:                // порядок фиксирован Set.copyOf(LinkedHashMap)
    if neuron.activate(features):
      return Result.rejectedBy(neuron)
  return Result.approvedResult()
```

Один линейный проход; первый сработавший нейрон определяет вердикт. Детерминизм: `Set.copyOf(byAxiom.values())` сохраняет порядок вставки в `LinkedHashMap`, повторные вызовы с одинаковым `BitSet` возвращают идентичный `Result`.

`evaluateText(text)` — convenience, прогоняет `TextFeatureExtractor.extract(text)` и вызывает `evaluate`.

## TLA+ sketch (формализация инварианта)

```tla
VARIABLES features, verdict

Init == features = {} /\ verdict = "APPROVED"

Evaluate ==
  /\ \E n \in Neurons : n.activate(features)
  /\ verdict' = [n \in Neurons |-> IF n.activate(features) THEN "REJECTED_BY_" \o n.tag ELSE "APPROVED"]
  /\ UNCHANGED features

Monotonicity ==
  \A f \in SUBSET Features :
    verdict(f) = "REJECTED" =>
      \A extra \in SUBSET Features :
        verdict(f \cup extra) = "REJECTED"

Safety == [](verdict # "REJECTED_BY_KILLING" => ~KillAction)
```

Это эскиз; полная TLA+ спецификация в `engineering/INVARIANTS.md` (отдельная задача, не блокирует текущее использование).

## Метрики / гейты

- `FrozenEthicalFNL.canonical()` создаёт 6-нейронную сеть; `Builder.build()` требует ≥1 нейрон.
- Проверка согласованности: `n.k() == featureExtractor.k()` (иначе `IllegalStateException`).
- Юнит `FrozenEthicalFNLTest`: REJECTED на каждом триггер-бите, APPROVED в остальных случаях, иммутабельность набора (`unmodifiableSet` бросает на `add`/`remove`).
- Покрытие этического кода — отдельный CI-гейт (CONSTITUTION: ≥82% по всему пакету).

## Открытые вопросы

- Расширение набора аксиом (например, NO_ECOLOGICAL_HARM) — требует RFC.
- Иерархия запретов (разные уровни тяжести, не только REJECTED) — отложено.
- Полная TLA+-верификация — отдельная задача, требует `tlc` в CI.

Next: конец серии алгоритмов brain wave v2. Дальнейшие документы — в `science/ALGORITHM-ATLAS-INDEX.md`.
