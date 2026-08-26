# MPDT-GA (эволюционный baseline с MDL-давлением)

**Статус: normative** · changelog 2026-08-26 — brain wave v2 algorithms.

## Что

Генетический алгоритм на DNF-популяции clause-масок. Baseline для EXP-002 (H-002/H-003 refuted-toy): GA быстрее ×5–10 и точнее на синтетике, чем TsetlinTrainer. Источник: `matrix-core/src/main/java/io/matrix/evolution/MpdtGaProducer.java`. Согласовано с DESIGN-04.

## Геном и фитнес

- Геном = `long[][] clauses`, каждая строка `[pos, neg]` — две бит-маски на `inputBits` входов.
- Clause срабатывает, если `(x & pos) == pos` AND `(x & neg) == 0` (при ненулевых масках); пустая clause не срабатывает.
- `fitness = accuracy − 0.1 · literals / maxLiterals` — классический MDL-штраф λ=0.1 за длину артефакта.
- Возвращается пара `[score, rawAccuracy]` для отслеживания обеих метрик раздельно.

## Элитизм и турнир

- Элитизм: топ-25% популяции (`elite = max(1, pop/4)`) копируются в следующее поколение без изменений.
- Остальное заполняется детёнышами от турнирной селекции (размер турнира = 2: два случайных индекса, побеждает больший `fitness`).
- Детерминизм: `Random(seed)` — единственный источник стохастики; никаких wall-clock.

## Per-clause кроссовер и мутация

```
crossover(p1, p2):
  point = rng.nextInt(clauses+1)
  child[c][0/1] = mutate(c < point ? p1[c][0/1] : p2[c][0/1])

mutate(word):
  if rng.nextDouble() < 1/inputBits:
    word ^= 1L << rng.nextInt(inputBits)
  return word & mask()
```

Кроссовер — по индексу clause (не по битам): каждый clause ребёнка — целиком от одного из родителей, затем мутируется. Это сохраняет clause-целостность и облегчает интерпретацию. Вероятность мутации — `1/inputBits` на слово (равноценно одному flip'у за поколение на clause в среднем).

## Границы обучения

- `inputBits ∈ [1..20]` (`K_MAX`).
- `clauses ≥ 1`, `populationSize ≥ 4`.
- `generations ≥ 1`; `inputs.length == labels.length`.
- Финал: лучший индивид по `fitness` выбирается, его `predictGenome` публикуется.
- Дистилляция в BIR: `ClauseSetForm.lossy(..., provenance + ":ga", accuracy)` — lossy с измеренной train-accuracy.

## Метрики / гейты

- H-002/H-003 (см. EXP-002/003-report): GA быстрее ×5–10 на toy-уровне.
- Артефакт-размер: `literalCount()` — суммарное число бит в pos/neg масках лучшего генома (источник метрики размера для DESIGN-14).
- Юнит `MpdtGaProducerTest`: детерминизм seed, невозрастание лучшего `fitness`, валидность `ClauseSetForm`.

## Открытые вопросы

- Адаптивный `λ` (MDL-давление) по ходу эволюции — отложено.
- Fitness-sharing/ниши для multiclass — отложено.
- Миграция на GPU-ногу через SIMD-битмаски — отложено (см. DESIGN-14 Batch*).

Next: см. файл Hansel-Chains.md в той же папке для следующей темы.
