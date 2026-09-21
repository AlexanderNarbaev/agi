# Расширение исследования дуальности: социо-когнитивные механизмы

**Статус: living** · Preregistered design для H-031…H-034 (EXP-031…EXP-034).
**Дата:** 2026-08-10
**Preregistration timestamp:** 2026-08-10 (до сбора данных — CONSTITUTION VI.3)
**Назначение:** Расширить исследовательскую программу H-023…H-030 на четыре новых измерения: (1) размер социальной группы как ограничитель D-асимметрии (Dunbar), (2) рационализация как защита от когнитивного диссонанса (Festinger), (3) онтологическая позиция как предиктор моральных оснований (Haidt), (4) нейропластичность как модератор когортного сдвига (H-030). Все operational definitions — на синтетических данных с известным preregistered ground truth.

---

## 1. H-031: Размер социальной группы и D-осевая асимметрия (Dunbar layers)

### 1.1 Фундаментальная проблема

Существующая программа H-023…H-030 исследует дуальность на индивидуальном и культурном уровнях. Однако не исследован фундаментальный вопрос: модулируется ли D-осевая асимметрия суждений РАЗМЕРОМ социальной группы? Теория Данбара (Dunbar, 1992, 1998) предсказывает, что когнитивные ограничения неокортекса определяют максимальный размер стабильной социальной группы (~150 для Homo sapiens), и это накладывает структурные ограничения на все формы социального познания, включая гендерно-типированные суждения.

**Конкурирующие гипотезы:**
- **Гипотеза A (Dunbar-ограничение):** В малых группах (5–15 — «clique of support») гендерная дифференциация УСИЛЕНА, поскольку выживание группы зависит от специализации ролей. В больших группах (150+ — «clan», 500+ — «megaband») асимметрия ОСЛАБЛЕНА, поскольку индивидуумы взаимодействуют с более разнообразными социальными ролями, размывающими бинарную D-ось.
- **Гипотеза B (социальная сложность):** В больших группах асимметрия УСИЛЕНА, поскольку социальная навигация в сложных иерархиях требует более выраженных сигналов гендерной идентичности для снижения когнитивной нагрузки.
- **Нулевая гипотеза:** Размер группы не связан с D-осевой асимметрией.

**Теоретическая рамка:**
- Dunbar (1992, 1998, #36): «Dunbar's number» ≈ 150 — когнитивный предел числа стабильных социальных отношений; группы разного размера формируют качественно различные социальные структуры (layers: 5, 15, 50, 150, 500, 1500).
- Dunbar (2004, #37): Gossip theory — язык эволюционировал как механизм поддержания социальных связей в больших группах («social grooming at a distance»); гендерные различия в gossip style.
- David-Barrett & Dunbar (2013, #38): Социальная сложность растёт с размером группы; гендерные роли дифференцируются в ответ на структурные требования группы.
- Zhou et al. (2005, #39): Discrete hierarchical organisation of social group sizes — эмпирическая валидация Dunbar layers на данных коммуникаций.
- Geary (1998, #14 в DUALITY-sources.md): эволюционная теория — социальная конкуренция как драйвер полового диморфизма в познании.

### 1.2 Preregistered дизайн (EXP-031)

**Синтетические Dunbar layers (preregistered proxy):**

| Layer | Размер | Социальная структура (ground truth) | Ожидаемая D-асимметрия |
|---|---|---|---|
| L1: Support clique | 5 | Ближайшие emotional supporters, высокая intimacy | Сильная (ролевая специализация) |
| L2: Sympathy group | 15 | Регулярные социальные контакты, mutual aid | Сильная |
| L3: Active network | 50 | Активная социальная сеть, но меньше intimacy | Умеренная |
| L4: Clan / Band | 150 | Dunbar's number — предел стабильных отношений | Умеренная → слабая |
| L5: Megaband | 500 | Эпизодические контакты, общий язык/культура | Слабая |
| L6: Linguistic group | 1500 | Общий язык, минимальные personal contacts | Слабая |

**Схема синтетической генерации:**
1. Для каждого размера группы (6 уровней) генерируется синтетический «контекст суждения» — социальная задача, реалистичная для данного размера группы (e.g., L1: распределение домашних обязанностей; L6: голосование за национальную политику).
2. Для каждого контекста генерируется n ≥ 200 синтетических «суждений» с preregistered ground truth D-полярности (Cohen's h для каждого слоя).
3. Ground truth: монотонное УМЕНЬШЕНИЕ |Cohen's h| от L1 → L6 (ρ_gt = −0.5, preregistered).

**Метрики (preregistered):**
- Spearman ρ между номером Dunbar layer (1–6) и силой D-асимметрии (|Cohen's h|)
- Permutation test (n = 10⁴): H₀ — ρ = 0
- FDR-коррекция Бенджамини–Хохберга на 6 слоёв
- Тест на монотонность: знак ρ между соседними слоями одинаков (L1→L2, L2→L3, …, L5→L6)
- Проверка калибровки: способность протокола восстановить ρ_gt = −0.5

**Критерии:**
- **Подтверждение:** Spearman ρ ≤ −0.3 (p_FDR < 0.05) на ≥3 из 6 Dunbar layers ИЛИ общий тренд (all-layer ρ ≤ −0.3, p < 0.05) И тренд монотонен (ρ не меняет знака между соседними слоями).
- **Опровержение:** |ρ| < 0.1 на всех 6 Dunbar layers (p_FDR ≥ 0.05) — отсутствие монотонной связи размера группы с D-асимметрией.

**Риски:**
- Синтетические «социальные контексты» для каждого Dunbar layer — идеализация; реальные группы не изолированы друг от друга (индивидуум одновременно принадлежит всем слоям).
- Размер группы — не единственный фактор социальной структуры (гетерогенность, иерархия, кооперация vs конкуренция).
- Dunbar's number ≈ 150 — эмпирически оспариваемый параметр; синтетический дизайн не валидирует само число, только гипотетическую связь.

---

## 2. H-032: Рационализация как защита от когнитивного диссонанса (Festinger)

### 2.1 Фундаментальная проблема

H-024 ввела RAT-индекс как меру рационализаторского зерна (расстояние между pre-decision confidence и post-hoc rationalization). Но ФУНКЦИЯ рационализации остаётся непрояснённой: является ли рационализация «когнитивной роскошью» (Система 2, post-hoc оправдание без функциональной нагрузки) или ЗАЩИТНЫМ МЕХАНИЗМОМ против когнитивного диссонанса (Festinger, 1957)?

Классическая теория когнитивного диссонанса (Festinger, 1957; Festinger & Carlsmith, 1959) предсказывает: когда поведение расходится с убеждениями, возникает негативное аффективное состояние (диссонанс), мотивирующее рационализацию для восстановления когнитивной консистентности. Если RAT-индекс измеряет именно этот процесс, то в ситуациях ВЫСОКОГО диссонанса RAT должен быть систематически ВЫШЕ, чем в ситуациях низкого.

**Конкурирующие гипотезы:**
- **Гипотеза A (Festinger-защита):** RAT-индекс растёт с диссонансом → рационализация — копинг-стратегия.
- **Гипотеза B (Kahneman-постфактум):** RAT-индекс НЕ зависит от диссонанса → рационализация — случайный побочный продукт Системы 2.
- **Нулевая гипотеза:** ΔRAT = 0 между high и low dissonance.

**Теоретическая рамка:**
- Festinger (1957, #40): A Theory of Cognitive Dissonance — первичный источник; диссонанс мотивирует изменение убеждений или рационализацию поведения.
- Festinger & Carlsmith (1959, #41): Классический эксперимент с $1/$20 — недостаточное оправдание (insufficient justification) усиливает диссонанс и последующую рационализацию.
- Aronson (1969, #42): Ревизия теории — диссонанс возникает, когда поведение угрожает self-concept («я хороший/рациональный человек»).
- Cooper & Fazio (1984, #43): «New Look» theory — диссонанс требует perceived freedom of choice + aversive consequence.
- Tavris & Aronson (2007, #16 в DUALITY-sources.md): связь диссонанса с самооправданием — прямой теоретический мост к RAT-индексу.

### 2.2 Preregistered дизайн (EXP-032)

**Синтетическая манипуляция диссонансом (within-subject, n ≥ 200 синтетических «респондентов»):**

| Condition | Dissonance score | Операциональное определение | Ожидаемый RAT (ground truth) |
|---|---|---|---|
| Low dissonance | < 0.3 | Суждение соответствует prior belief (cosine similarity ≥ 0.9). Нет конфликта. | RAT ≈ 0.15 |
| Medium dissonance | [0.3, 0.7) | Суждение частично расходится с prior (cosine ∈ [0.3, 0.7)). Умеренный конфликт. | RAT ≈ 0.30 |
| High dissonance | ≥ 0.7 | Суждение ПРОТИВОПОЛОЖНО prior (cosine similarity ≤ 0.1). Сильный конфликт. | RAT ≈ 0.45 |

**Dissonance score:** dissonance_score = 1 − cosine_similarity(judgment_vector, prior_belief_vector), ∈ [0,1].

**RAT-индекс (из H-024):** RAT = |Rat_post − Conf_pre| / max(Rat_post, Conf_pre), ∈ [0,1].

**Схема синтетической генерации:**
1. Для каждого респондента генерируется «prior belief vector» (D1–D4 по H-023, + synthetic belief dimensions).
2. Для каждого condition — набор синтетических суждений с preregistered dissonance по отношению к prior.
3. Измеряется Conf_pre (до exposure к диссонансу) и Rat_post (после).
4. Ground truth: линейный рост RAT с dissonance_score (β_gt = 0.4, preregistered).

**Метрики (preregistered):**
- Paired t-test: RAT_high_dissonance vs RAT_low_dissonance (within-subject)
- Cohen's d для paired comparison
- Линейный смешанный эффект: RAT ~ dissonance_score + (1 | respondent)
- Bootstrap CI 95% (n = 10⁴) для ΔRAT
- FDR-коррекция на 3 сравнения (high vs low, high vs medium, medium vs low)
- Проверка калибровки: восстановление β_gt = 0.4 в синтетической регрессии

**Критерии:**
- **Подтверждение:** ΔRAT(high − low) ≥ 0.2, Cohen's d ≥ 0.5 (medium effect size по Cohen), p_FDR < 0.05 в within-subject comparison.
- **Опровержение:** |ΔRAT| < 0.05, p ≥ 0.5 (статистически неразличимо от нуля) И slope dissonance_score → RAT < 0.05.

**Риски:**
- Синтетический «диссонанс» — упрощение; реальный диссонанс имеет аффективный компонент (негативное возбуждение), отсутствующий в синтетических данных.
- RAT-индекс зависит от шкалы измерения Conf_pre/Rat_post; метрика чувствительна к калибровке.
- Within-subject дизайн на синтетических данных: риск артефактов из-за идеализированной генерации.

---

## 3. H-033: Онтологическая позиция предсказывает моральные основания (Haidt)

### 3.1 Фундаментальная проблема

H-026 установила связь между онтологической позицией (essentialist vs constructionist) и вектором рационализации (натурализующая vs социализирующая). Но связь с МОРАЛЬНЫМИ основаниями остаётся непроверенной. Согласно Moral Foundations Theory (Haidt, 2012; Graham et al., 2013), моральные суждения опираются на 6 врождённых «вкусовых рецепторов» (foundations: care, fairness, loyalty, authority, sanctity, liberty), чья культурная настройка варьирует.

Фундаментальный вопрос: ПРЕДСКАЗЫВАЕТ ли онтологическая позиция (essentialist: «мораль врождённа/универсальна» vs constructionist: «мораль культурно сконструирована») силу endorsement каждого из 6 moral foundations?

**Конкурирующие гипотезы:**
- **Гипотеза A (Essentialist → binding foundations):** Эссенциализм предсказывает более сильный endorsement «binding» foundations (loyalty, authority, sanctity), поскольку они воспринимаются как «естественные» и универсальные, а не культурно-специфичные.
- **Гипотеза B (Essentialist → individualising foundations):** Эссенциализм не дифференцирует moral foundations — он предсказывает более сильный endorsement ВСЕХ оснований (восприятие морали как объективной реальности).
- **Нулевая гипотеза:** Онтологическая позиция не предсказывает endorsement moral foundations (accuracy ≤ 0.55).

**Теоретическая рамка:**
- Haidt (2012, #26 в DUALITY-sources.md): Moral Foundations Theory — 6 оснований: care/harm, fairness/cheating, loyalty/betrayal, authority/subversion, sanctity/degradation, liberty/oppression.
- Graham, Haidt & Nosek (2009, #44): Liberals and conservatives rely on different sets of moral foundations — empirical baseline.
- Graham et al. (2013, #45): Chapter 3 in Advances in Experimental Social Psychology — comprehensive review of MFT.
- Gelman (2003, #29 в DUALITY-sources.md): essentialism as default cognitive mode — essentialist ontology предсказывает более сильную моральную интуицию (moral realism).
- Rhodes, Leslie & Tworek (2012, #33 в DUALITY-sources.md): cultural transmission of essentialism через generic language — связь essentialism → moral stereotyping.
- Haslam, Rothschild & Ernst (2000, #30 в DUALITY-sources.md): структура essentialist beliefs — naturalness, stability, immutability dimensions.

### 3.2 Preregistered дизайн (EXP-033)

**Синтетические moral foundations (на уровне синтетического «респондента»):**

| Foundation | Тип (Graham et al.) | Операциональное определение (синтетическое, ∈ [0,1]) |
|---|---|---|
| Care/Harm | Individualising | endorsement_care ∈ [0,1] — важность защиты уязвимых |
| Fairness/Cheating | Individualising | endorsement_fairness ∈ [0,1] — важность справедливости и пропорциональности |
| Loyalty/Betrayal | Binding | endorsement_loyalty ∈ [0,1] — важность верности группе |
| Authority/Subversion | Binding | endorsement_authority ∈ [0,1] — важность уважения к иерархии |
| Sanctity/Degradation | Binding | endorsement_sanctity ∈ [0,1] — важность чистоты и сакральности |
| Liberty/Oppression | Individualising | endorsement_liberty ∈ [0,1] — важность свободы от доминирования |

**Онтологическая позиция (из H-026):** дихотомия essentialist [1,0] vs constructionist [0,1] (one-hot, бинаризация по медиане continuous score).

**Target (per foundation):** бинарная классификация endorsement уровня (high vs low, медианный split).

**Метрики (preregistered):**
- 5-fold кросс-валидация (stratified по онтологической позиции + endorsement уровню)
- Multi-output логистическая регрессия (6 бинарных классификаторов)
- Accuracy per foundation + macro-averaged accuracy
- AUC per foundation (ROC)
- Binomial test vs chance (0.5): p < 0.05 для foundations с accuracy > 0.5
- FDR-коррекция на 6 foundations
- Feature importance: essentialism → binding vs individualising gradient (preregistered контраст: loyalty+authority+sanctity vs care+fairness+liberty)
- Проверка калибровки: восстановление β_gt (preregistered vector) в синтетической регрессии

**Критерии:**
- **Подтверждение:** accuracy ≥ 0.7 (p < 0.05 vs 0.5 chance, binomial test) на ≥3 из 6 moral foundations с FDR-коррекцией. Дополнительный exploratory критерий: gradient essentialism → binding > individualising (описательная статистика, не preregistered gate).
- **Опровержение:** accuracy ≤ 0.55 на всех 6 moral foundations (не лучше случайного).

**Риски:**
- Синтетические MFT-шкалы — упрощение; реальные moral foundations многомерны (Moral Foundations Questionnaire содержит ≥2 items per foundation).
- Малый размер синтетической выборки (n ≈ 200): при 6 выходах + кросс-валидации риск переобучения.
- Дихотомия essentialist/constructionist теряет нюансы (continuous score был бы информативнее).
- Синтетическая генерация может искусственно усилить essentialism → binding gradient.

---

## 4. H-034: Нейропластичность как модератор когортного сдвига D-оси

### 4.1 Фундаментальная проблема

H-030 установила, что D-осевая асимметрия суждений изменяется между поколениями (cohort shift). Но остаётся открытым вопрос: ЧТО модулирует величину когортного сдвига? Если предположить, что культурные изменения требуют нейропластичности для усвоения новых социальных норм, то поколения с БОЛЕЕ высокой пластичностью должны демонстрировать БОЛЕЕ сильный сдвиг D-оси между когортами.

Фундаментальный вопрос: модулирует ли нейропластичность (способность к реорганизации нейронных связей в ответ на опыт) скорость и величину культурного сдвига D-оси?

**Конкурирующие гипотезы:**
- **Гипотеза A (Plasticity Amplifier):** Высокая пластичность УСИЛИВАЕТ cohort shift — молодые поколения с более пластичной когнитивной архитектурой быстрее адаптируются к меняющимся культурным нормам, что увеличивает межкогортный разрыв D-оси.
- **Гипотеза B (Plasticity Buffer):** Высокая пластичность СГЛАЖИВАЕТ cohort shift — пластичные индивидуумы менее фиксированы на гендерно-типированных схемах, что уменьшает асимметрию в целом и, следовательно, межкогортный контраст.
- **Нулевая гипотеза:** Пластичность не взаимодействует с cohort (interaction β ≈ 0).

**Теоретическая рамка:**
- Draganski et al. (2004, #46): Структурная нейропластичность — серое вещество изменяется в ответ на обучение (juggling study).
- Maguire et al. (2000, #47): Нейропластичность гиппокампа у лондонских таксистов — experience-dependent structural change.
- Kolb & Whishaw (1998, #48): «Brain Plasticity and Behavior» — обзор механизмов нейропластичности.
- Lindenberger (2014, #49): Когнитивное старение и пластичность — снижение plasticity с возрастом как объяснение когнитивных изменений.
- Maccoby (1998, #3 в DUALITY-sources.md): онтогенез гендерных различий — взаимодействие биологии и социализации; пластичность критична для усвоения гендерно-типированного поведения.
- Twenge (1997, #50): Мета-анализ временных трендов в гендерных различиях — evidence for cohort shifts; пластичность как possible mediator.

### 4.2 Preregistered дизайн (EXP-034)

**Синтетический plasticity_score (на уровне синтетической когорты):**

| Когорта | Год рождения | Plasticity score (ground truth) | Обоснование |
|---|---|---|---|
| C1: Silent Generation | 1950 | 0.30 | Низкая (поздняя взрослость/старение → сниженная plasticity на момент культурного сдвига 1960s–70s) |
| C2: Baby Boomers | 1975 | 0.55 | Средняя (молодость во время культурных изменений 1960s–70s → moderate plasticity) |
| C3: Millennials | 2000 | 0.70 | Высокая (детство/юность в период быстрых культурных изменений 1980s–2000s → высокая plasticity) |
| C4: Gen Z | 2025 | 0.85 | Очень высокая (юность в эпоху digital media, non-binary recognition → максимальная plasticity) |
| C5: Preregistered Future | 2050_preregistered | 0.90 | Экстраполяция тренда — preregistered prediction, НЕ данные |

**Plasticity score:** синтетический параметр ∈ [0,1], моделирующий способность к реорганизации гендерно-типированных когнитивных схем. Ground truth: монотонный рост от C1 → C5.

**D-осевая асимметрия per cohort (из EXP-030):** Cohen's h между «маскулинным» и «феминным» полюсами для каждой когорты.

**Схема синтетической генерации:**
1. Для каждой когорты генерируется синтетический plasticity_score (preregistered ground truth).
2. D-осевая асимметрия генерируется как Cohen's h(cohort, plasticity_score + interaction).
3. Ground truth: значимое plasticity × cohort interaction (β_gt = −0.35, preregistered), где пластичность УСИЛИВАЕТ сдвиг для молодёжных когорт ИЛИ уменьшает асимметрию для традиционных.

**Метрики (preregistered):**
- Mixed-effects model: Cohen's h ~ cohort × plasticity_score + (1 | domain), REML estimation
- Interaction term: plasticity_score × cohort (Type III test, Kenward-Roger df)
- Standardised β для interaction term + bootstrap CI 95% (n = 10⁴)
- Cohen's d для контраста high-plasticity когорт (C4+C5) vs low-plasticity (C1+C2)
- Тест на простые эффекты: slope plasticity_score → Cohen's h в каждой когорте отдельно
- Проверка калибровки: восстановление β_gt = −0.35

**Критерии:**
- **Подтверждение:** значимое plasticity × cohort interaction (p < 0.01) И |d(high_plasticity, low_plasticity)| ≥ 0.3 (Cohen's d, small-to-medium effect).
- **Опровержение:** |interaction β| < 0.1 (standardised), p ≥ 0.5 (незначимо) И все simple slopes plasticity → Cohen's h незначимы (p ≥ 0.05).

**Риски:**
- Синтетический plasticity_score — прокси; реальная нейропластичность не измеряется на уровне когорты (только индивидуально).
- Конфаундинг plasticity × cohort × age (возрастное снижение пластичности неотличимо от когортного эффекта).
- C5 (2050) — чистая экстраполяция.
- Малый размер выборки на уровне когорт (n = 5) ограничивает мощность для тестирования interaction в mixed-effects модели.
- Направление effect: не preregistered (гипотеза A vs B — exploratory).

---

## Сводка новых гипотез

| Гипотеза | Измерение | Связь с существующими | Тип механизма | Ключевая теория |
|---|---|---|---|---|
| H-031 | Размер социальной группы | Расширяет H-023: структурный ограничитель D-оси | Ограничитель | Dunbar's number (1992) |
| H-032 | Когнитивный диссонанс | Расширяет H-024: функция рационализации | Медиатор | Festinger (1957) |
| H-033 | Moral foundations | Расширяет H-026: онтология → мораль | Предиктор | Haidt (2012) |
| H-034 | Нейропластичность | Расширяет H-030: модератор cohort shift | Модератор | Draganski et al. (2004) |

## Связь с предыдущей программой (H-023…H-030)

```
H-023 (D-axis asymmetry) ←── H-031 (social group size — Dunbar constraint)
     │                           ←── H-027 (neurophysiological substrate)
     │                           
     ├── H-024 (RAT-index) ←── H-032 (cognitive dissonance — Festinger function)
     │                           ←── H-028 (socio-economic modulation)
     │                           
     ├── H-025 (cross-culture) ←── H-029 (cultural distance prediction)
     │                           
     ├── H-026 (ontology) ←── H-033 (moral foundations — Haidt prediction)
     │                           
     └── H-030 (cohort shift) ←── H-034 (neuroplasticity — amplifier?)
```

## Дополнительные источники (не входят в DUALITY-sources.md)

| # | Автор(ы) | Год | Суть | Релевантность |
|---|---|---|---|---|
| 36 | Dunbar, R.I.M. | 1992 | Neocortex size as a constraint on group size in primates. Journal of Human Evolution, 22(6), 469–493. | H-031: foundational source for Dunbar's number |
| 37 | Dunbar, R.I.M. | 2004 | Gossip in evolutionary perspective. Review of General Psychology, 8(2), 100–110. | H-031: language as social grooming — gender differences in gossip |
| 38 | David-Barrett, T. & Dunbar, R.I.M. | 2013 | Processing power limits social group size. Biology Letters, 9(6), 20130674. | H-031: cognitive constraints on group size |
| 39 | Zhou, W.X., Sornette, D., Hill, R.A., & Dunbar, R.I.M. | 2005 | Discrete hierarchical organization of social group sizes. Proceedings of the Royal Society B, 272(1561), 439–444. | H-031: empirical validation of Dunbar layers |
| 40 | Festinger, L. | 1957 | A Theory of Cognitive Dissonance. Stanford University Press. | H-032: foundational source |
| 41 | Festinger, L. & Carlsmith, J.M. | 1959 | Cognitive consequences of forced compliance. Journal of Abnormal and Social Psychology, 58(2), 203–210. | H-032: классический эксперимент $1/$20 |
| 42 | Aronson, E. | 1969 | The theory of cognitive dissonance: A current perspective. Advances in Experimental Social Psychology, 4, 1–34. | H-032: self-concept revision of dissonance theory |
| 43 | Cooper, J. & Fazio, R.H. | 1984 | A new look at dissonance theory. Advances in Experimental Social Psychology, 17, 229–266. | H-032: «New Look» — aversive consequence requirement |
| 44 | Graham, J., Haidt, J., & Nosek, B.A. | 2009 | Liberals and conservatives rely on different sets of moral foundations. Journal of Personality and Social Psychology, 96(5), 1029–1046. | H-033: empirical baseline for MFT |
| 45 | Graham, J. et al. | 2013 | Moral Foundations Theory: The pragmatic validity of moral pluralism. Advances in Experimental Social Psychology, 47, 55–130. | H-033: comprehensive MFT review |
| 46 | Draganski, B. et al. | 2004 | Neuroplasticity: Changes in grey matter induced by training. Nature, 427(6972), 311–312. | H-034: juggling study — structural plasticity |
| 47 | Maguire, E.A. et al. | 2000 | Navigation-related structural change in the hippocampi of taxi drivers. PNAS, 97(8), 4398–4403. | H-034: experience-dependent plasticity |
| 48 | Kolb, B. & Whishaw, I.Q. | 1998 | Brain plasticity and behavior. Annual Review of Psychology, 49(1), 43–64. | H-034: plasticity mechanisms overview |
| 49 | Lindenberger, U. | 2014 | Human cognitive aging: Corriger la fortune? Science, 346(6209), 572–578. | H-034: aging × plasticity interaction |
| 50 | Twenge, J.M. | 1997 | Changes in masculine and feminine traits over time: A meta-analysis. Sex Roles, 36(5–6), 305–325. | H-034: cohort shifts in gender traits |

---

## Методологическое примечание

**Все операциональные определения, метрики и критерии зафиксированы ДО сбора данных** (CONSTITUTION VI.3). HARKing запрещён. Если в процессе дизайна EXP-031…EXP-034 будут обнаружены новые confounding факторы, они будут добавлены в preregistration до запуска эксперимента с пометкой «amendment» и временной меткой (2026-08-10).

**Запрещённые claims (CONSTITUTION VI.1):**
- Не утверждается, что размер социальной группы ПРИЧИННО обусловливает D-асимметрию (только корреляция синтетических прокси).
- Не утверждается, что RAT-индекс измеряет реальный когнитивный диссонанс (только синтетическую корреляцию с dissonance_score).
- Не утверждается, что Moral Foundations Theory объясняет реальную моральную психологию (только predictive accuracy на синтетических аналогах).
- Не утверждается, что нейропластичность ИЗМЕРЯЕТСЯ на синтетических когортах (только preregistered proxy).
- Не утверждается величина реальных эффектов (только preregistered comparison синтетических данных).
- Термин «AGI» не используется.
- Термин «не лжёт» не используется.

## Статус preregistration

| Гипотеза | Эксперимент | Статус | Preregistration date | Amendment |
|---|---|---|---|---|
| H-031 | EXP-031 | proposed | 2026-08-10 | — |
| H-032 | EXP-032 | proposed | 2026-08-10 | — |
| H-033 | EXP-033 | proposed | 2026-08-10 | — |
| H-034 | EXP-034 | proposed | 2026-08-10 | — |

Отрицательный результат любой из H-031…H-034 публикуется наравне с положительным (CONSTITUTION VI.3), поскольку опровержение связи D-оси с размером группы, когнитивным диссонансом, моральными основаниями или нейропластичностью — самостоятельный научный результат, ограничивающий пространство гипотез о механизмах дуальности.
