# ALGORITHM-ATLAS — указатель

**Статус: index** ().

Полные тексты **ALGORITHM-ATLAS-WAVE4..WAVE32** (§1..§112) и связанных алгоритмов сохранены в **archive** и **не** встроены компактно — каждое WAVE содержит детальные разборы конкретных алгоритмов (mini-Tsetlin, GUHA-reducer, fast-Hansel variants, обфусцирующие BDD-сжатия, схемы деквантизации SDM и т.д.).

## Полный архив

```
├── ALGORITHM-ATLAS.md # §1..§23 (мини-BirUnit, обучающие автоматы, федеративные схемы)
├── ALGORITHM-ATLAS-WAVE4.md # §24..§25
├── ALGORITHM-ATLAS-WAVE4B.md # §26..§28
├── ALGORITHM-ATLAS-WAVE4C.md # §29..§32
├── ALGORITHM-ATLAS-WAVE5.md # §33..§34
├── ALGORITHM-ATLAS-WAVE5B.md # §35..§37
├── ALGORITHM-ATLAS-WAVE6.md # §38..§40
├── ALGORITHM-ATLAS-WAVE7.md # §41..§43
├── ALGORITHM-ATLAS-WAVE8.md # §44..§46
├── ALGORITHM-ATLAS-WAVE9.md # §47..§49
├── ALGORITHM-ATLAS-WAVE10.md # §50..§52
├── ALGORITHM-ATLAS-WAVE11.md # §53..§55
├── ALGORITHM-ATLAS-WAVE12.md # §56..§58
├── ALGORITHM-ATLAS-WAVE13.md # §59..§61
├── ALGORITHM-ATLAS-WAVE14.md # §62..§64
├── ALGORITHM-ATLAS-WAVE15.md # §65..§67
├── ALGORITHM-ATLAS-WAVE16.md # §68..§70
├── ALGORITHM-ATLAS-WAVE17.md # §71..§73
├── ALGORITHM-ATLAS-WAVE18.md # §74..§76
├── ALGORITHM-ATLAS-WAVE19.md # §77..§79
├── ALGORITHM-ATLAS-WAVE20.md # §80..§82
├── ALGORITHM-ATLAS-WAVE21.md # §83..§85
├── ALGORITHM-ATLAS-WAVE22.md # §86..§88
├── ALGORITHM-ATLAS-WAVE23.md # §89..§91
├── ALGORITHM-ATLAS-WAVE24.md # §92..§94
├── ALGORITHM-ATLAS-WAVE25.md # §95..§97
├── ALGORITHM-ATLAS-WAVE26.md # §98..§100
├── ALGORITHM-ATLAS-WAVE27.md # §101..§103
├── ALGORITHM-ATLAS-WAVE28.md # §104..§106
├── ALGORITHM-ATLAS-WAVE29.md # §107..§109
├── ALGORITHM-ATLAS-WAVE30.md # §110..§112
├── ALGORITHM-ATLAS-WAVE31.md # (черновик)
├── ALGORITHM-ATLAS-WAVE32.md # (черновик)
├── SUBSTRATE-MODELS.md # (см. также docs-v2/science/SUBSTRATE-MODELS.md — inline)
└── FOUNDATIONS.md # (см. также docs-v2/science/FOUNDATIONS.md — inline)
```

Дубликаты в `staging-wave4/` устранены при архивировании.

## Почему не «inline» целиком

## Когда возвращаться

Алгоритм из ALGORITHM-ATLAS становится кандидатом на `designs/` или `research/PROTOCOL.md` когда:
1. Постановлена preregistered гипотеза (см. `research/HYPOTHESES.md`).
2. Реализован в коде (`bir/`, `evolution/`, `tsetlin/`, `reasoning/`).
3. JMH-gate (если applicable) выполнен.

Сейчас в реестре `OPEN-PROBLEMS.md` помечены алгоритмы, у которых нет пока гипотез: полные цепи Ханселя, GUHA-reducer, BRC-Step, ConjugateBudgeter DP, MCTS/LATS-convergence, Memory-M4-Causal — все это **отдельные WAVE-работы** для будущих сессий.