# Legal Axioms (FrozenEthicalFNL: монотонный слой запретов)

> **FROZEN-зона.** Изменения только через RFC + отдельный ревьюер (см. CONSTITUTION III, AGENTS.md). Здесь документируется алгоритмическое ядро, а не API-поверхность.

## Что

Неизменяемый слой безопасности (CONSTITUTION III, IV): набор `FrozenAxiomNeuron` поверх `TruthTable`, упакованный через `Set.copyOf(...)` и `Collections.unmodifiableSet(...)`. Любая попытка мутации требует пересоздания всей FNL через `Builder`. Источник: `matrix-core/src/main/java/io/matrix/ethics/frozen/FrozenEthicalFNL.java` (плюс `FrozenAxiomNeuron`). Соответствует DESIGN-12 (FNL/TaskCell карантин) и TLA+ `FrozenEthicalFNL` (`FORMAL-CONTRACTS.md`).

## Шесть канонических аксиом

Канонический набор (расширение CONSTITUTION IV — четыре базовых запрета плюс два операционных):

1. `NO_KILLING` — триггер-бит 0.
2. `NO_TORTURE` — триггер-бит 1.
3. `NO_ENSLAVEMENT` — триггер-бит 2.
4. `NO_AUTONOMOUS_WEAPONS` — триггер-бит 3.
5. `TRUTHFULNESS` — триггер-бит 4.
6. `PRIVACY` — триггер-бит 5.

Каждый нейрон — `TruthTable` ширины `k = TextFeatureExtractor.DEFAULT_K = 16`. `buildSingleBitNeuron(axiom, bit, tag)` заполняет таблицу так, что `table[idx] = 1` ⇔ бит `bit` в индексе `idx` установлен. Срабатывание ⇔ наличие соответствующего триггера в фичах, извлечённых из входа (`TextFeatureExtractor.extract`).

## Активация нейрона

`FrozenAxiomNeuron.activate(BitSet features)`:

- `idx = 0; for i ∈ [0, min(features.length(), table.k())): if features.get(i): idx |= (1<<i)`.
- Возвращает `table.evaluate(idx)`.

LSB-first кодирование гарантирует совпадение с конструкцией таблицы в `buildSingleBitNeuron` (где бит `bit` индекса проверяется через `(idx >>> bit) & 1`). Перегрузка `activate(long features)` берёт младшие `k` бит поля.

## Монотонность структурного свойства

FROZEN-нейрон устроен так, что расширение носителя `features` сохраняет все ранее активные индексы в `TruthTable` (потому что `TruthTable` иммутабелен и индекс-вычисление монотонно по `BitSet`). Следствие:

- `f(features) = REJECTED ⇒ f(features ∪ extra) = REJECTED` для любого `extra ⊆ [0, k)` — добавление новых фичей не убирает уже сработавший запрет.
- `f(features) = APPROVED` может перейти в `REJECTED` при расширении — это и есть путь расширения фактов риска.

Это математически проверяемая монотонность (TLA+ `FrozenEthicalFNL` в `FORMAL-CONTRACTS.md`).

## Линейная оценка

```
FrozenEthicalFNL.evaluate(features):
 for neuron in neurons: // Set.copyOf(LinkedHashMap) — порядок вставки
 if neuron.activate(features):
 return Result.rejectedBy(neuron)
 return Result.approvedResult()
```

Один линейный проход; первый сработавший нейрон определяет вердикт. `Result` — record `(approved, firedNeuron)` с методами `violatedAxiom()` и `toString()`. Детерминизм: `Set.copyOf(byAxiom.values())` сохраняет порядок вставки в `LinkedHashMap` — повторные вызовы с идентичным `BitSet` возвращают идентичный `Result`.

`evaluateText(text)` — convenience, прогоняет `TextFeatureExtractor.extract(text)` и вызывает `evaluate`.

## Конструкция и валидация

`Builder.build()`:

- требует ≥1 нейрона (`IllegalStateException` иначе);
- проверяет согласованность `n.k() == featureExtractor.k()` для всех нейронов;
- возвращает `FrozenEthicalFNL(Set.copyOf(...), extractor)`.

`canonical()` — convenience-фабрика, создаёт 6-нейронную сеть стандартных таблиц (`buildNoKillingNeuron` … `buildPrivacyNeuron`). `neuronFor(axiom)` — линейный поиск (для audit/reporting).

## Метрики / гейты

- Юнит `FrozenEthicalFNLTest`: REJECTED на каждом триггер-бите; APPROVED в остальных случаях; иммутабельность набора (`unmodifiableSet` бросает на `add`/`remove`); согласованность `k`.
- EXP-006 (FPR 0%, TPR 100%) — зафиксированное измерение соответствия TLA+ спеки; полные цифры в `research/reports/`.
- CI-гейт покрытия ≥82% METHOD на `matrix-core` (CONSTITUTION V) применяется к этому пакету.

## Открытые вопросы

- Расширение набора аксиом (например, NO_ECOLOGICAL_HARM) — требует RFC, расширяет набор из 6; влияет на `k` и совместимость.
- Иерархия тяжести (REJECTED против WARN-only) — отложено, сейчас все 6 равнозначно REJECTED.
- Полная TLC-проверка `FrozenEthicalFNL` в CI — отдельная задача (нужен `tlc`).

Next: см. файл Mcts-Lats.md в той же папке для следующей темы.