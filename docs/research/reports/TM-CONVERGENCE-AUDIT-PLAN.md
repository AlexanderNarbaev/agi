# TM Convergence Audit Plan — EXP-002 pre-stage closeout

**Статус:** living (план dedicated-сессии; результат аудита дописывается сюда же)
**Основание:** HYPOTHESES.md карточка EXP-002, попытки 3–9; WAL волны 16–20.

## 1. Что уже известно (не переоткрывать)

| # | Факт | Доказательство |
|---|---|---|
| F1 | Синхронный комплементарный init создаёт ловушку «все клаузы = специалист full-true minterm» | попытки ≤8; диаграмма состояний wave-16 |
| F2 | Random init автоматов решает toy-гейты (AND/OR/XOR/MUX3/noisyXOR, N=10–12, e≤2000) | attempt-9, GranmoReferenceTest 5/5 |
| F3 | TypeIa pairing: consistency(value==includes) ⇒ Reward w.p.(s−1)/s; mismatch ⇒ Penalty w.p.1/s — частично чинит XOR | попытка 6 |
| F4 | На random-DNF k=8/12 (160 train, 10% noise) обобщение ~0.5–0.6 даже без шума; НЕ лечится T/N/s/эпохами/init-стратегией/EBL | попытки 7–10 |
| F5 | Growth-механика изолированно корректна (5 раундов сдвигают состояние) | wave-12 микро-тест |

## 2. Аудит-чеклист (построчно против Algorithm 1, Granmo 2018 / pyTsetlinBooster)

Сверять КАЖДУЮ строку текущего `typeOne/typeOneGrowth/typeTwo` с эталоном; фиксировать дельты сюда:

- [ ] A1. Направление Reward/Penalty на границах сторон (state=N+1 reward? state=1 penalty?)
- [ ] A2. Тип Ia: применяется ли к ОБЕМ полярностям литерала симметрично (x_j и ¬x_j)?
- [ ] A3. Тип Ib (non-firing, target=1): канонический вес — 1/s или s-зависимый? Применяется ли push-out included-FALSE?
- [ ] A4. Тип II: канонично includeNow ТОЛЬКО excluded-FALSE-at-x первого встречного ИЛИ все? Вероятностный вариант?
- [ ] A5. Порядок фидбеков внутри примера: Ia→II или II→Ia? Влияет ли на равновесие?
- [ ] A6. Порог предсказания: sum>0 vs margin≥⌈T/4⌉ vs argmax по классам
- [ ] A7. Инициализация: у эталона automata стартуют со state=N+1 (include) при РАНДОМНОЙ полярности клауз?

## 3. Экспериментальная матрица после фикса A-дельт

- Гейты: AND/OR/XOR/MUX3/noisyXOR (гарнесс включён) — критерий 5/5 сидов ==1.0 (noisyXor mean ≥0.75)
- Synthetic: k=8/12 random 6×3-DNF, 160 train / 80 holdout, шум 10% — критерий bAcc ≥0.85 mean по 5 сидам
- Регресс: tsetlin+bir пакеты зелёные; GranmoReferenceTest остаётся включённым гейтом

## 4. Правила сессии

- Один фикс-кандидат за раз; прогон гарнесса до/после; числа — в этот файл и карточку
- Откат через git (attempt-бэкапы в /tmp/opencode/)
- Стоп-условие тюнинга: 3 неудачных кандидата → вернуться к A-чеклисту
