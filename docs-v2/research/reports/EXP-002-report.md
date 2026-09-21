# EXP-002 Report — CLAUSESET (Tsetlin) vs MPDT-GA producer

**Статус: refuted-toy** · Гипотеза: H-002 (CLAUSESET бьёт MPDT-GA на quality×bytes при ≥10× скорости).

## Артефакты

- `evolution/MpdtGaProducer` — детерминированный GA-продюсер (элитизм 25%, турнир, per-clause кроссовер, мутация p=1/K, MDL-давление λ=0.1). API: `trainBatch(long[] inputs, boolean[] labels, int generations)`, `predict(long packed)`, `toDecisionClauseSet(provenance)`, `literalCount()`.
- `Exp002ComparisonTest` и `Exp002Exp003ProtocolTest` (io.matrix.evolution).
- Прогон сходимости к 99% trainAcc (удвоение подвыборок 20→320).

## Измерения / (3 синтетических датасета, seed 42)

| Датасет | Tsetlin мс / acc / литералов | MPDT-GA мс / acc / литералов |
|---|---|---|
| 16/10 | 70.63 / 0.7375 / 741 828 | **12.93 / 0.8250 / 98** |
| 16/12 | 68.76 / 0.9375 / 482 434 | **8.70 / 0.9500 / 96** |
| 20/14 | 41.53 / 0.7375 / 9 414 838 | **9.05 / 0.8125 / 107** |

**Средние**: Tsetlin acc.7833 / GA acc.8625; артефакт Tsetlin в тысячи–миллионы раз больше.

## Сходимость к 99% train-acc

| Датасет | Tsetlin to99 | GA to99 |
|---|---|---|
| 16/10 | 999 (не достигнута на 320) | **20** |
| 16/12 | 20 | 20 |
| 20/14 | 999 | 999 |
| среднее | 672.7 | 346.3 |

`reached99`: Tsetlin 1/3, GA 2/3.

## Вердикт H-002: **refuted-toy**

Гипотеза опровергнута на toy-шкале по всем трём критериям:
- Скорость: GA в среднем ×5.5 быстрее,
- Точность: GA выше на +7.9 п.п.,
- Артефакт: GA компактнее в тысячи раз (литералов меньше на 3–4 порядка).

Пины: `Exp002ComparisonTest`, `Exp002Exp003ProtocolTest`.

## Ограничения

- 3 крошечных синтетических датасета; тюнинг Tsetlin (балансы пар автоматов, S-параметр) выполняется минимально.
- Полный verdict — на доменных корпусах (`needs-domain-data`).