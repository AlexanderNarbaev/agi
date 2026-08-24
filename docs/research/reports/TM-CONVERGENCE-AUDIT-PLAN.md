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

## 1.1 Первичные данные из эталона (pyTsetlinMachine, cair)

- Рабочая точка канона: `number_of_state_bits=8` ⇒ **N=256** состояний (наш N=10–16 — на 1.5 порядка меньше); вероятности (s−1)/s при N=256 дают принципиально иную динамику роста
- Присутствуют флаги, которых у нас нет: `boost_true_positive_feedback` (усиление TypeIa), `T` — порог суммарного голоса для активации фидбека на примере, `weighted_clauses`, `clause_drop_p/literal_drop_p`
- `append_negated=True` подтверждает нашу пару литералов x_j/¬x_j ✓
- Обновления реализованы в C (`libTM`) ⇒ построчный аудит требует чтения tsetlin_machine.c (имя файла уточнить в репо), не python-обёртки

## 1.2 Эксперимент F6 (2026-08-24)

- N=256/cl=64/e=300 на k=8 random-DNF+noise: bAcc 0.57–0.67 — N сам по себе НЕ решает.
- Следующий кандидат-fix №1: **T-гейтинг фидбека** (канонический порог суммарного голоса примера активирует обновления клауз; у нас фидбек безусловный) + флаги boost_true_positive_feedback / weighted_clauses.
- Это алгоритмическое изменение → делать в dedicated-сессии с A-чеклистом §2 и тестами гарнесса до/после.

## 1.3 Попытка 11 (2026-08-24)

- Margin-gating (FEEDBACK_MARGIN=2, усиление только при |vote|<margin, TypeII безусловно) реализован; toy-гарнесс 5/5 зелёный.
- k=8/12 синтетика: bAcc 0.59/0.53 — гейтинг необходим, но недостаточен. Остаток = полный канонический стек (§2 A3/A4/A7 + boost/T/weights флаги) в dedicated-сессии.

## 1.4 Попытка 12 (2026-08-24) — отрицательная, база восстановлена

- Margin-gating ломает и EBL-сходимость (0/5 на OR) — гейт подавляет пер-минтерм сигнал покрытия даже при ungated Ib. Откат к ungated базе: **377/0** (все пакеты), гарнесс 5/5, EBL 5/5.
- Вывод: следующий шаг — НЕ варианты нашего цикла, а построчное сравнение с эталонным C-кодом pyTsetlinMachine (tsetlin_machine.c, имя файла уточнить) — расхождения искать в A1–A7.

## 1.5 Попытка 13 (2026-08-24): D5 внедрён (canonical, toy-green), k≥8 без изменений → остались D1–D4

## 1.6 Попытка 14 (2026-08-24): D1 внедрён

- Мягкий гейтинг p=(T±vote)/(2T), FEEDBACK_T=12: k8 0.58→**0.64** ↑, k12 ~0.52 — направление верное; остаток D2 (boost) / D3 (max_included) / калибровка T.

## 1.7 Попытка 15 (2026-08-24): empty-collapse

- Синтетика k=8–20: bAcc ровно 0.500, minClauses=0 у всех сидов ⇒ pos-клаузы заканчивают пустыми/мёртвыми при выборочном режиме 160 примеров. Toy-гейты при этом зелёные.
- Гипотеза: D5 (пустая≠fire) + мягкий гейтинг + разреженная выборка создают вымирание специализаций; эталон обучается на ПОЛНЫх датасетах больших размеров. След. шаг: трассировка распределения включённостей по эпохам против C-эталона.

## 1.8 Попытка 16 (2026-08-24): D1-prime per-clause asymmetric gating

- Реализован точно по C-эталону (p_i=(T+(1−2t_i)·sum)/2T, pos затухают/neg усиливаются с ростом голоса) — код канонически верен.
- Синтетика k=8–20: bAcc ≈0.50 — НЕ решает самостоятельно. Вывод: оставшиеся рычаги = систематический sweep s/T/N/объёма выборки и, возможно, Ib-вес по канону; это работа dedicated-сессии со стендом по §3.

## 1.9 Попытка 17 (2026-08-24): полный sweep s×N×c×e

- Сетка 3s×2N×2c на k=8 random-DNF+noise: trainAcc≈0.41–0.56 ВЕЗДЕ ⇒ обучение само не идёт (не обобщение!). Все мои переинтерпретации канона (margin, soft-gate форма, Ib-направление) не воспроизводят динамику эталона.
- ВЕРДИКТ: нужна дословная механическая порция строк 336–400 ref_tm.c в Java (они уже verbatim в §4 этого файла) БЕЗ творческих отклонений; затем повторить sweep. Параметрический тюнинг до порта бессмыслен.

## 1.10 Попытка 18 (2024-08-24): кривая обучения плоская

- k=8, c=128, N=64, e=2000, шум 10%: trainAcc колеблется 0.45–0.51 без тренда. Трейс состояний: все клаузы дрейфуют в «XN»(противоречие) или пустоту; ни одна не специализируется.
- Правила обновления семантически эквивалентны эталону (аудит A1–A7 ✅), но ДИНАМИКА иная: у нас каждый шаг каждого автомата детерминированно следует «consistency⇒reward/mismatch⇒penalty» с фиксированными pR/pP, а в эталоне маски batch-применяются к СЛУЧАЙНОМУ подмножеству с насыщением.
- Гипотеза №1 на след. сессию: портить нужно НЕ правила построчно, а МОДЕЛЬ МАССОВОГО ПРИМЕНЕНИЯ (batch-mask + saturation), включая точную семантику ta_state-упаковки и feedback_to_la.
- Пакет для след. сессии: ref_tm.c локально, verbatim в §4, наш TsetlinTrainer как рабочая база API.

## 1.11 Попытка 19 (2026-08-24): правила семантически эквивалентны — фронтир смещён

- A1–A7 аудит завершён: наши TypeIa/Ib/II семантически соответствуют эталону (проверено маппингом всех строк C). Плотность Ib (~n/s случайных позиций) тоже эквивалентна нашей пер-литеральной pP.
- Синтетика k≥8 остаётся ~0.50 при канонически верном коде ⇒ блокер НЕ в правилах обновления. Кандидаты: (а) объём данных/эпох недостаточен для k≥8 (эталонные работы используют тысячи примеров); (б) тонкость predict-порога при паритетных пулах; (в) взаимодействие шума.
- Мандат следующей сессии: запустить ЭТАЛОННЫЙ python-TM (pyTsetlinMachine) на том же протоколе как контроль; расхождение укажет класс причины.

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

## 3. Дельты из эталонного C-кода (cair/pyTsetlinMachine ConvolutionalTsetlinMachine.c)

```c
// D1: вероятностный гейтинг фидбека клаузы (строка ~331)
feedback_to_clauses |= (fast_rand()/(FAST_RAND_MAX) <= (1.0/(T*2))*(T + (1 - 2*target)*class_sum));
```
- **D1**: гейтинг мягкий и пропорциональный: p_клаузы = (T ± class_sum)/(2T), а не жёсткий margin. Для target=1 p падает до 0 при sum→T; для target=0 — симметрично. Наш margin-gating был грубой аппроксимацией.
- **D2**: `boost_true_positive_feedback` переключает вариант усиления (два блока в строках ~82–93).
- **D3**: `max_included_literals` — потолок роста клаузы (у нас отсутствует).
- **D4**: полярность через чётность индекса j&1 ✓ совпадает с нашей схемой.
- **D5 (ВАЖНО)**: пустая клауза (all_exclude) по умолчанию НЕ даёт output: `output && !(all_exclude)` (строка ~288). У нас `fires()` на пустой = TRUE. Это меняет всю раннюю динамику!

## 4. Верbatim эталонных строк

### Гейтинг (ref_gate.txt)
```c
		unsigned int clause_chunk_pos = j % 32;

		if ((tm->drop_clause[clause_chunk] & (1 << clause_chunk_pos))) {
			continue;
		}

	 	tm->feedback_to_clauses[clause_chunk] |= (((float)fast_rand())/((float)FAST_RAND_MAX) <= (1.0/(tm->T*2))*(tm->T + (1 - 2*target)*class_sum)) << clause_chunk_pos;
	}

	for (int j = 0; j < tm->number_of_clauses; j++) {
		unsigned int clause_chunk = j / 32;
		unsigned int clause_chunk_pos = j % 32;

		if (!(tm->feedback_to_clauses[clause_chunk] & (1 << clause_chunk_pos))) {
			continue;
		}
```
### Ряды обновления (ref_rows.txt)
```c
		unsigned int clause_chunk_pos = j % 32;

		if (!(tm->feedback_to_clauses[clause_chunk] & (1 << clause_chunk_pos))) {
			continue;
		}
		
		if ((2*target-1) * (1 - 2 * (j & 1)) == -1) {
			if ((tm->clause_output[clause_chunk] & (1 << clause_chunk_pos)) > 0) {
				// Type II Feedback
				
				if (tm->weighted_clauses && tm->clause_weights[j] > 1) {
					tm->clause_weights[j]--;
				}

				for (int k = 0; k < tm->number_of_ta_chunks; ++k) {
					int patch = tm->clause_patch[j];
					unsigned int pos = j*tm->number_of_ta_chunks*tm->number_of_state_bits + k*tm->number_of_state_bits + tm->number_of_state_bits-1;

					tm_inc(tm, j, k, (~tm->drop_literal[k]) & (~Xi[patch*tm->number_of_ta_chunks + k]) & (~ta_state[pos]));
				}
			}
		} else if ((2*target-1) * (1 - 2 * (j & 1)) == 1) {
			// Type I Feedback

			tm_initialize_random_streams(tm, j);

			if (((tm->clause_output[clause_chunk] & (1 << clause_chunk_pos)) > 0) && (tm_number_of_include_actions(tm, j) <= tm->max_included_literals)) {
				// Type Ia Feedback

				if (tm->weighted_clauses) {
					tm->clause_weights[j]++;
				}
				
				for (int k = 0; k < tm->number_of_ta_chunks; ++k) {
					int patch = tm->clause_patch[j];
					if (tm->boost_true_positive_feedback == 1) {
		 				tm_inc(tm, j, k, (~tm->drop_literal[k]) & Xi[patch*tm->number_of_ta_chunks + k]);
					} else {
						tm_inc(tm, j, k, (~tm->drop_literal[k]) & Xi[patch*tm->number_of_ta_chunks + k] & (~tm->feedback_to_la[k]));
					}
		 			
		 			tm_dec(tm, j, k, (~tm->drop_literal[k]) & (~Xi[patch*tm->number_of_ta_chunks + k]) & tm->feedback_to_la[k]);
				}
			} else {
				// Type Ib Feedback
				
				for (int k = 0; k < tm->number_of_ta_chunks; ++k) {
					tm_dec(tm, j, k, (~tm->drop_literal[k]) & tm->feedback_to_la[k]);
				}
			}
		}
	}
}

void tm_update(```
