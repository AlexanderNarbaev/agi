# H-010 — WiSARD vs TsetlinTrainer preregistration & gates (EXP-010)

Протокол preregistered EXP-010: single-pass WiSARD WNN vs TsetlinTrainer
на synthetic-scope. Текущий verdict `accepted (synthetic-scope
)` зафиксирован на 9 прогонах (3 датасета × 3 seed); карточка
preregistration фиксирует gate-критерии для возможного расширения на
дополнительные датасеты или повторного прогона при изменении продюсеров.

## ID и привязка

- H-ID: H-010.
- EXP-ID: EXP-010.
- Соответствующий дизайн/спека (text-only): DESIGN-04 (продюсеры,
 `WisardProducer`, `TsetlinTrainer`), CONSTITUTION II (K_MAX=20).
- Источник вердикта (text-only): research/HYPOTHESES.md row «H-010 —
 WiSARD WNN vs TsetlinTrainer», статус `accepted (synthetic-scope
 )`.
- Источник чисел (text-only): research/reports/EXP-010-report.md
 (9 прогонов, median speedup 242.43×, WiSARD 9/9 по точности, min
 advantage +5.00 п.п.).

## Метрики и gates (численные пороги preregistered)

| Метрика | Gate (accept) | Gate (refute) | Уровень доказательства |
|---|---|---|---|
| Median speedup (wall-clock train) | ≥ 10× | < 5× | multi-seed × multi-dataset |
| Min accuracy advantage (WiSARD − Tsetlin) | ≥ −2 п.п. | < −5 п.п. | multi-seed |
| Per-seed accuracy wins (WiSARD ≥ Tsetlin) | ≥ 7/9 | < 5/9 | multi-seed |
| Determinism | hash стабилен на каждом seed | любое расхождение | unit |
| BITS ≤ 20 (K_MAX-конформность) | BITS ∈ {16, 20} | BITS > 20 | unit |
| RAM-count grid | {8} по умолчанию; расширение — research | — | research |

Preliminary числа (см. EXP-010-report): median 242.43× (gate ≥10× ✅),
min advantage +5.00 п.п. (gate ≥−2 п.п. ✅, фактически WiSARD выше во
всех 9 прогонах).

## Methodology

- Артефакт: `matrix-core/.../tsetlin/{TsetlinTrainer, TsetlinAutomaton,
 WisardProducer}` + `Exp010ComparisonTest`.
- Корпус: синтетика seed 42/43/44; BITS=16 (K_MAX-конформная арность);
 10 информативных бит с классовым смещением p=0.7/0.3, 6 шумовых
 p=0.5; TRAIN=320, TEST=80; EPOCHS=5.
- Процедура: (1) grid-tuning Tsetlin по TRAIN-acc (4 конфигурации
 clauses×epochs×S); (2) замер wall-clock лучшей конфигурации на
 новой инстанции с `Random(SEED)`; (3) WiSARD 8 RAM, тот же seed,
 EPOCHS=5; (4) сравнение на holdout.
- Mini-протокол: 3 датасета × 3 seed = 9 прогонов; расширение до 5×5
 — отдельный research wave.

## Prereqs

- Реализованы `WisardProducer` и `TsetlinTrainer` (есть).
- `Exp010ComparisonTest` зелёный с 9 прогонами (см. EXP-010-report).
- JaCoCo gate ≥ 82% на `tsetlin/**` (CONSTITUTION V).
- Multi-seed grid: seeds {42, 43, 44} — фиксированы до изменения протокола.
- BITS=16 синтетика; для BITS>20 — отдельный preregistration-цикл.

## Methodology framework (text-only)

- Уровни доказательства — см. PROTOCOL.md в той же директории.
- Полный verdict (`accepted synthetic-scope`) уже зафиксирован; для
 полного `accepted` нужен production-domain (заблокировано: BLOCKED-
 EXT: данные — см. EXP-010-report раздел «Ограничения»).

## Чего здесь НЕ утверждается (CONSTITUTION VI)

- Synthetic-scope verdict не экстраполируется на prod: «WiSARD всегда
 быстрее Tsetlin» не публикуется.
- Grid-tuning Tsetlin минимален (4 конфигурации); исчерпывающий grid —
 отдельный research wave.
- Расширение на доменные корпуса (real images/text/tabular) — отдельный
 EXP с собственным preregistration.

Next: при изменении продюсеров (`WisardProducer`, `TsetlinTrainer`) —
повторный 3×3 прогон и проверка gate-таблицы; полный prod-domain verdict —
когда появятся доменные корпуса (BLOCKED-EXT: данные).