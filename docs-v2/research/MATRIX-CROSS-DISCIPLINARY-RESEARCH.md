# Cross-Disciplinary Research Doctrine (CDRD) — MATRIX

**Status:** v1, зафиксировано 2026-09-11 в рамках cross-disciplinary research wave (W31).

## 1. Назначение документа

MATRIX — brain simulator, объединяющий Boolean-таблицы, HDC, BitNet quantization, Hebbian learning, NCA-style emergent computation. Проект не имеет права замыкаться в собственной вычислительной эпохе: наши задачи — мышление, обучение, социальная координация — исторически исследовались в десятках школ мысли. Этот документ фиксирует **обязательную многоотраслевую методологию research**, чтобы алгоритмическая работа не вырождалась в локальный оптимизим.

## 2. Метаправило (META-R1) — Parallel Cross-Disciplinary Research

> При любом архитектурном изменении ядра (≥5 классов / новая capability level / новый тип вычислений) **параллельно поднимаются ≥3 research-волны** из разных школ.

| Волна | Школа / Источник | Зачем |
|---|---|---|
| **R-A** | Современные SOTA ML / DL (post-transformer, BitNet b1.58, MoE, Mamba, DPO, Constitutional AI) | algorithm state-of-the-art |
| **R-B** | Cybernetics / computational cognition (Anokhin, Bernstein, Ashby, Wiener, Mesarovic, Minsky, Simon) | теория функциональных систем, requisite variety, levels of construction |
| **R-C** | Soviet / Russian / Asian schools (Глушков, Anokhin functional systems, ICOT 5th-gen, Zadeh fuzzy, Wu Wenjun char. sets, Nyaya 4-logic) | non-Western computation paradigms, что предвосхитили наши идеи |
| **R-D** | Neuroscience early-cognition (Spelke core knowledge, Spitz-Sokolov habituation, Gibbs, Thomas-Spillane brain mechanisms) | comparison to biological benchmarks |
| **R-E** | Physics / chemistry / biology of computation (DNA computing, memristors, neuromorphic hardware, Perceptron 1958 lineage) | hardware & physical substrates |
| **R-F** | Mathematics of creativity (Gödel incompleteness, Kolmogorov complexity, L-systems, cellular automata Rule 30, frame problem) | вычислимые границы и emergence |

## 3. Конкретные гипотезы волны W31, которые уже дали результат

| H | Утверждение | Подтверждающая школа | Артефакт |
|---|---|---|---|
| **H-051** | BitNet b1.58 absmean квантизация (W ∈ {-1,0,+1}) при масштабе ≥3B даёт паритет с FP16 | Microsoft 2024 (arxiv 2402.17764) | DESIGN-54 |
| **H-052** | HDC binding через XOR + cleanup через Hamming-distance lookup — практически жизнеспособная edge-AI архитектура | Kanerva 1988 SDM, Plate 1995 HRR, Intel Loihi HDC | DESIGN-54 |
| **H-053** | Объединение HDC + BitLinear + Boolean tables даёт **новый** архитектурный класс — никто такого гибрида не строил | собственный synthesis | DESIGN-54 |
| **H-054** | Anokhin "функциональная система" = forward-model + feedback comparator — реализуемо как thin wrapper над brain simulator без переделки ядра | Anokhin functional-system theory (1935-1974) | DESIGN-55 |
| **H-055** | Bernstein levels of construction A→D = естественная иерархия routing в brain simulator | Bernstein 1947 | DESIGN-55 |
| **H-056** | Zadeh fuzzy ∈ [0,1] — может дополнить наши 3-state {-1,0,+1} BitNet до непрерывного диапазона без потери сигнала | Zadeh fuzzy sets 1965 | DESIGN-56 |
| **H-057** | Nyaya 4-fold логика (Svatantra / Paratantra / Anekata / Dvaya-anekata) = генерал-4-state better uncertainty чем бинарная BitNet | Nyaya / Dignaga 5-6 век н.э., перевод Stcherbatsky 1930-е | DESIGN-57 |
| **H-058** | Capability Levels 0-6 — measurable milestones от fabric до symbol grounding. Каждый реализуем за 1-3 недели соло | Spelke, Spitz, Piaget, Vygotsky | roadmap в DESIGN-58 |
| **H-059** | HDC-as-LLM-preprocessor (text→1024-bit compressed code) может быть **в 30× memory-efficient** чем dense-embedding baselines (Mem0, MemGPT) | Kanerva + современный memory LLM literature | DESIGN-54 §6 |
| **H-060** | Mordvintsev Neural Cellular Automata — реализуемо через наши существующие `KauffmanNetwork`, `GillespieSimulator`, `ConwayGameOfLife` | Mordvintsev 2020, Sudhakaran 2021 | DESIGN-59 |

Все H-051…H-060 — running с кодом, target = publication-grade результаты за 3-6 месяцев.

## 3.1 Волна W41 — три оси эпистемики (2026-11-01)

Три параллельные research-волны по META-R1 (A — learning science, B —
cognitive systems & cybernetics, C — knowledge engineering & epistemology) дали
**один и тот же вывод независимо**: основание утверждения, обоснованность
вывода и состояние конфликта — это **три ортогональные оси**, и сводить их в
одно число нельзя.

| H | Утверждение | Школа | Артефакт |
|---|---|---|---|
| **H-100** | Слой (`ClaimStatus`), основание (`ClaimWarrant`: pratyakṣa/śabda/anumāna/upamāna) и обоснованность вывода (`DerivationStatus`) независимы; вывод может быть валиден при ложных посылках | Ньяя (четыре праманы, «тарка» — отдельный инструмент), TMS (Doyle 1979), PROV-DM (W3C 2013) | DESIGN-70 |
| **H-101** | Энтейлмент решается **точно** ограниченным энумератором ground-пропозициональных моделей (VALID / INVALID / UNDECIDED), без балла, без счёта, без времени | Буль (1854), Черч (1956 §7), PROV-CONSTRAINTS | DESIGN-70, `EntailmentChecker` |
| **H-102** | Измерение обучаемости по лексикальному сдерживанию урока невалидно: W40-teach-back не проходит 7 из 7 опубликованных требований валидности; нужен независимый ученик, скрытый ключ и adversarial-пак с привязкой к заблуждениям | Roediger & Karpicke 2006, Karpicke & Roediger 2008, Butler 2010, AHRQ Tool 5, Bloom 1968 | W41-RESEARCH-A, готовится W41.5 |

**Что из этого уже в коде.** `ClaimWarrant` и `DerivationStatus` — оси;
`Derivation` — записываемый вывод; `EntailmentChecker` — точный чекер;
проверка согласия с независимой реализацией на 1000 псевдослучайных теориях
(расхождений 0 — после того, как в самой эталонной реализации найден
`HashMap`, теряющий порядок посылок).

**Чего это НЕ доказывает.** Ни одно из трёх утверждений не говорит о том, что
MATRIX «умеет думать». H-102 в частности требует adversarial-пака, который
ещё не написан; без ключа и привязок к заблуждениям эксперимент нетестируем,
а значит и неопровержим — это записано и в отчёте research, и в бэклоге.

**Урок волны.** Три волны дали один вывод — но нашли его независимо, и
каждая нашла его своим первоисточником. Это и есть смысл META-R1: не
«собрать мнения», а получить одну архитектурную форму из трёх разных школ.

## 4. Knowledge Persistence

**META-R2 — обязательная фиксация многоотраслевых выводов в `docs-v2/research/`.**

Структура (по приоритету):
- `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` — этот файл, доктрина
- `docs-v2/designs/DESIGN-54-...` — HDC+BitNet hybrid, IBM+MSR синтез
- `docs-v2/designs/DESIGN-55-...` — Russian/Asian cybernetics integration
- `docs-v2/designs/DESIGN-56-fuzzy-bits.md` — Zadeh fuzzy as continuous relaxation
- `docs-v2/designs/DESIGN-57-nyaya-4-logic.md` — 4-state classification
- `docs-v2/designs/DESIGN-58-capability-levels.md` — measurable milestones
- `docs-v2/designs/DESIGN-59-nca-brain.md` — Neural Cellular Automata integration
- `docs-v2/research/summaries/W31-SYNTHESIS.md` — точка-входа для новых читателей
- `docs-v2/research/HYPOTHESES-NEW.md` — расширенный список гипотез

## 5. Когда НЕ запускать дополнительные research-волны

- Точечные bug fixes (≤1 файл).
- Рефакторинг (без новой capability).
- Расширение существующего алгоритма новым параметром (без новой архитектурной идеи).
- Изменения конфигурации / CI/CD.

## 6. Источники первоисточников (canonical reference, **обязательные при цитировании**)

### LLM / DL алгоритмы
- **BitNet b1.58**: Ma et al. 2024 (arxiv 2402.17764)
- **BitNet 1.0**: Wang & Ma et al. 2023 (arxiv 2310.11453)
- **Mamba**: Gu & Dao 2023 (arxiv 2312.00752)
- **Mamba-2/SSD**: Dao & Gu ICML 2024 (arxiv 2405.21060)
- **Hyena**: Poli et al. 2023 (arxiv 2302.10866)
- **RWKV-7 "Goose"**: Peng et al. 2025 (arxiv 2503.14456)
- **Jamba**: Lieber et al. 2024 (arxiv 2403.19887)
- **DeepSeek-V2**: DeepSeek-AI 2024 (arxiv 2405.04434)
- **DeepSeek-V3**: DeepSeek-AI 2024 (arxiv 2412.19437)
- **Mixtral**: Jiang et al. 2024 (arxiv 2401.04088)
- **DPO**: Rafailov et al. 2023 (arxiv 2305.18290)
- **GRPO**: Shao et al. 2024 (arxiv 2402.03300)
- **Chinchilla**: Hoffmann et al. 2022 (arxiv 2203.15556)
- **Emergent mirage**: Schaeffer et al. 2023 (arxiv 2304.15004)
- **Constitutional AI**: Bai et al. 2022 (arxiv 2212.08073)

### Cybernetics школа
- **Anokhin functional systems**: «Теория функциональных систем» (1935-1974)
- **Bernstein levels of construction**: «О построении движений» (1947)
- **Ashby requisite variety + homeostat**: «Introduction to Cybernetics» (1956)
- **Wiener cybernetics feedback**: «Cybernetics» (1948, переизд. 1961)
- **Колмогоров information**, алгоритмическая сложность (1956, совместно с Solomonoff)
- **Глушков ОГАС**: доклад 1962, книга «Кибернетика» (1964)

### Asia / non-Western
- **ICOT Fifth Generation**: «ICOT Strategic Plan» (1982)
- **Zadeh fuzzy sets**: «Fuzzy Sets» (Information and Control, 1965)
- **Wu Wenjun char. sets** (метод характеристических сечений, 1950-2000)
- **Amari information geometry**: Springer monographs (1985-2020)
- **Nyaya 4-fold логика**: Dignāga, Dharmakīrti (5-6 век н.э.), Stcherbatsky перевод

### Neuroscience / developmental
- **Spelke core knowledge**: Spelke & Kinzler 2007 (Topics in Cognitive Science)
- **Spitz habituation**: «Hospitalism» (1945), «No and Yes» (1957)
- **Sokolov**: «Higher Nervous Functions» (1963)
- **Hubel & Wiesel**: «Receptive fields, binocular interaction» (1962)
- **Watts & Strogatz**: «Small-world networks» Nature 1998
- **Markram STDP**: «Regulation of synaptic efficacy» (1997)
- **Mordvintsev NCA**: distill.pub/2020/growing-ca/

### Mathematics of creativity
- **Wolfram Rule 30**: «A New Kind of Science» (2002)
- **Lenia**: Chan B.-Y., Complex Systems 2018
- **NCA Distill**: Mordvintsev et al. 2020

### Hardware / physical
- **Intel Loihi 2**: Davies 2021 (Tech. Brief)
- **IBM TrueNorth**: Merolla et al. Science 2014
- **Akida**: Brainchip product brief 2023

## 6.1 Волна W42 — измерение обучения, консолидация, которая предлагает (2026-11-02)

Волна W42 подняла **три параллельные research-волны** (META-R1) — по одной на каждую
школу, — и каждая проверяла первоисточники сама (META-R3):

| поток | школа | что проверялось | документ |
|---|---|---|---|
| **A** | learning science + современный ML | что можно назвать калиброванной уверенностью; что консолидация имеет право делать с быстро обучающимся; что значит измерение обучения | `docs-v2/research/W42-RESEARCH-A-LEARNING-SCIENCE.md` |
| **B** | кибернетика и вычислительная когнитивистика | зачем консолидация нужна; что такое акцептор результата; почему забывание — это пометка, а не удаление | `docs-v2/research/W42-RESEARCH-B-COGNITIVE-SYSTEMS.md` |
| **C** | knowledge engineering + азиатская школа логики | что может служить базой лексического ранжирования; как править опечатку, не меняя смысла; что значит «то же самое утверждение» на двух языках | `docs-v2/research/W42-RESEARCH-C-KNOWLEDGE-ENGINEERING.md` |

### Гипотезы волны W42

| H | утверждение | школа | артефакт |
|---|---|---|---|
| **H-104** | Support score, вычисленный из структуры импликаций, — ординальная величина, и её нельзя предъявлять как вероятность; утверждение о калибровке требует строго собственного правила оценки и достаточной выборки | Gneiting & Raftery 2007 | `SupportScore.UNCALIBRATED`; Brier по прогнозам teach-back |
| **H-105** | Консолидация принимается только если НЕЗАВИСИМОЕ измерение по отложенным пунктам не падает; иначе пакет откатывается | van de Ven et al. 2024; обзор TPAMI 2024 | `KnowledgeConsolidator.Acceptance`; откат через `reviveQuestion` |
| **H-106** | Измерение обучения недействительно, если его инструмент переиспользует материал, породивший ответ; проверка — аудит утечки | методология teach-back и transfer | счётчик `restatements`; лексический baseline |
| **H-107** | Консолидация переносит структуру в медленную систему, не переписывая быструю; поэтому она работает по памяти диалога и связям ученика, и никогда по истинности утверждения | McClelland, McNaughton & O'Reilly 1995 | `KnowledgeConsolidator`; инвариант «No claim was changed» |
| **H-108** | Изменение, затрагивающее быструю систему, есть ПРЕДЛОЖЕНИЕ, пока измерение, сформированное до изменения, не покажет, что система не деградировала; откат восстанавливает весь пакет | акцептор результата действия (Анохин) | `KnowledgeConsolidator.Acceptance`; `reviveQuestion` |
| **H-109** | Забывание в инспектируемой системе — это пометка, а не удаление: содержание записи и её основания сохраняются, путь отката существует | дополнительные системы: структурированное приобретение | метка `AskedQuestion.forgotten`; счётчик откатов |
| **H-110** | Лексическое ранжирование — измерение извлечения, никогда не замена воротам ответа; перекрытие измеряется одним и тем же инструментом с обеих сторон | Robertson & Zaragoza 2009 | D-004; перекрытие 0.608; отказы 0.900 |
| **H-111** | Исправление опечатки — гипотеза о слове пользователя: оригинал сохраняется, исправление добавляется, неоднозначное исправление отклоняется, а мера успеха — сохранённое НАМЕРЕНИЕ против вопроса, который имелся в виду | Damerau 1964; Levenshtein 1966 | `TypoCorrector`; синтетический бенчмарк опечаток, 24 из 24 |
| **H-112** | Тождество понятия не зависит от языка, а перевод есть утверждение об инференциальном отношении, которое должно быть обосновано, — и не свидетельство эквивалентности двух утверждений | Ньяя-сутры; «Tarka Sangraha» | D-011; примечание о языке в ответе |

**Сводка волны.** Ни одна из девяти гипотез не требует нового механизма: каждая либо
обосновывает уже принятое решение, либо уточняет, что именно обещает измерение. Три из них
(H-104, H-105, H-106) закрывают места, где прежние формулировки обещали больше, чем давали;
две (H-110, H-111) привязывают измерительные приборы к их источникам; четыре (H-107, H-108,
H-109, H-112) переносят смысл волны в архитектуру.

## 7. Anti-patterns (что НЕ делать при cross-disciplinary research)

1. **Wikipedia-only reviews.** Каждое утверждение ≥1 первоисточник.
2. **Выдумывать числа.** UNVERIFIED — помечать.
3. **Carrying over без применения.** Любая research-находка должна мотивировать хотя бы 1 класс в проекте (или явно сказано почему не мотивирует).
4. **Историческо-эпистемологический narcissus.** Не «подтверждаем, что у нас идеи ТРИЗ и проч.» — а «используем ТРИЗ там-то».

## 8. Cross-references

- Конкретные архитектурные дизайны см. в `docs-v2/designs/DESIGN-54..59-*.md`.
- Текущие research-волны: `WAL.md`, `docs-v2/research/summaries/W*.md`.
- Hypotheses реестр: `docs-v2/research/HYPOTHESES.md` (H-001..H-050) + `HYPOTHESES-NEW.md` (H-051..).
- Человеческий-machine contract: см. AGENTS.md §1.

