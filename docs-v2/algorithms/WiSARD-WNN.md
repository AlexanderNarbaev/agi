# WiSARD WNN (RAM-гранулярность, однопроходное обучение)

## Что

Безвесовый нейрон (WNN) в стиле WiSARD (Aleksander, 1984): каждый «нейрон» — это массив RAM-таблиц с детерминированной бит-селекцией. Однопроходовое обучение: на каждом примере в RAM по вычисленному адресу инкрементируется счётчик `+1` (label=1) или `−1` (label=0). Источник: `matrix-core/src/main/java/io/matrix/tsetlin/WisardProducer.java`. Принят как EXP-010 (см. DESIGN-04): медианное ускорение обучения ×242 относительно TsetlinTrainer, точность 9/9 на синтетике.

## RAM-гранулярность

- `numRams` — число RAM-таблиц (параметр; «нейроны» WiSARD).
- `bitsPerRam = ⌈inputBits / numRams⌉` — длина адреса каждой RAM.
- `ramTables[r]` — `Map<Long, Integer>`, адрес → накопленный счётчик.
- Бит-селекция детерминирована: `bit b` RAM `r` соответствует входному биту `r·bitsPerRam + b`. Поддерживается многословный вход `long[]` через `wordIdx = bitPos >>> 6`, `bitIdx = bitPos & 63`.

## Запись по адресу (обучение)

```
train(input, label):
 for r in 0..numRams:
 addr = address(input, r)
 ramTables[r].merge(addr, label==1 ? +1 : -1, Integer::sum)
```

Сложность — `O(numRams)` на пример; батч — тот же цикл с внешним `for`. Никакого backprop, градиентов, эпох. Один проход по данным достаточен для детерминированной фиксации счётчиков.

## Классификация

```
classify(input) = (Σ_r ramTables[r].getOrDefault(addr(input,r), 0)) / numRams
```

Бинарное решение: `score >= 0` → label=1, иначе 0. Score ∈ [−1, +1] нормирован числом RAM. Калибровка: `accuracy(batch) = hits / N` — единственная метрика продюсера.

## Дистилляция в BIR

Продюсер — офлайн; рантайм исполняет только BIR-артефакт (CONSTITUTION II.2–3). `toDecisionClauseSet(provenance)` строит таблицу истинности по всему `2^inputBits` (требует `inputBits ≤ 20` = `K_MAX`) и компилирует через `BirCompiler.ttToClauseSet` (espresso-тип минимизатор).

## Метрики / гейты

- EXP-010: medianSpeedup=242×, accuracy 9/9 на синтетических датасетах.
- Покрытие юнит-тестами (`WisardProducerTest`): 100% публичного API.
- Детерминизм: seeded `Random` (для будущих аугментаций); адресация детерминирована.

## Открытые вопросы

- «Bleaching»/пороговая фильтрация (классический WiSARD): не используется — счётчик непрерывный.
- Per-RAM адаптивная гранулярность (битовая ёмкость по информационной плотности) — отложено.
- Multiclass-расширение через one-vs-all — отложено (сейчас бинарно).

Next: см. файл MPDT-GA.md в той же папке для следующей темы.