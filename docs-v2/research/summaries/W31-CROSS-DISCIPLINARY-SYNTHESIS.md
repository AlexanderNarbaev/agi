# W31 — Cross-Disciplinary Synthesis Summary

**Wave:** W31 (Sep 11 2026). Закрывающая итерация большой исследовательской фазы.

## 1. Чему мы учились

В этой волне мы провели **4 параллельных deep-research волны** и не-посредственно получили
доступ к:

- BitNet b1.58 (Microsoft 2024), Mamba / Mamba-2, Hyena, RWKV-7, Jamba, DeepSeek-V2 / V3, Mixtral
  — современные алгоритмы DL.
- DPO, GRPO, Constitutional AI — современные alignment approaches.
- BinaryConnect, XNOR-Net, BitLinear — путь к bit-level neural networks.
- McCulloch-Pitts 1943, Hebb 1949, Muggleton ILP 1991, Payani & Fekri (NN-Logic) — Neural Logic.
- Plate HRR 1995, Kanerva SDM 1988 (Hyperdimensional Computing) — VSA traditions.
- Mordvintsev NCA 2020, Lenia 2018 — emergent computation школа.
- Anokhin functional systems, Bernstein levels, Глушков ОГАС, Zadeh fuzzy, Wu Wenjun,
  ICOT 5th-gen — non-Western кибернетика.
- Spelke core knowledge, Spitz habituation, Sokolov — infant cognition.

Подробные референсы: `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` §6.

## 2. Главный синтез: HDC × BitLinear × Boolean Brain

Результат этой волны — **рабочий план HDC+BitNet+Boolean hybrid brain** с:

- 8 RUNs, которые реализуют компоненты: HdcEncoding (437), HdcBinding (438),
  BitLinear (439), CodebookMemory (440), HebbianUpdater (441), HdcBrain (442),
  HdcConditioning (443), SpelkeCoreKnowledge (444), CrossModalPaired (445),
  NcaBrainSimulator (446), HdcAsLlmPreprocessor (447), LlmOutputDecoder (448),
  SyntheticGrammarExperiment (449), AdultHabituationExperiment (450).

- 6 дизайн-документов (см. рядом):
  - `DESIGN-54-hdc-bitnet-hybrid-brain.md`
  - `DESIGN-55-russian-asian-cybernetics-integration.md`
  - `DESIGN-56-fuzzy-bit-continuous-relaxation.md`
  - `DESIGN-57-nyaya-4-logic.md`
  - `DESIGN-58-capability-levels-roadmap.md`
  - `DESIGN-59-nca-brain.md`

## 3. Capability Levels 0-6

Мы сформулировали **measurable developmental milestones** на основе infant cognition literature:

| Level | Capability | Cross-reference | Target Weeks |
|---|---|---|---|
| 0 | Fabric (78 classes + brain) | (есть) | -- |
| 1 | Pavlov operant conditioning | Sokolov 1963, Spitz 1957 | 1-2 |
| 2 | Spelke core knowledge (4-set) | Spelke & Kinzler 2007 | 3-5 |
| 3 | Cross-modal HDC paired | Mithen 1996 | 6-8 |
| 4 | Piaget sensorimotor stage | Piaget 1954, Bernstein 1947 | 9-12 |
| 5 | Symbol grounding | Bloom 2000, Pinker 2007 | 13-18 |
| 6 | Compositional reasoning | Tomasello 2003, Vygotsky 1934 | 19-26 |

Each level measured: % accuracy, latency, memory, generalisation gap.

## 4. Compute reality

- На текущем machine (32 GB RAM, no GPU): **Levels 1-3 realistically achievable соло за 8-12 недель**.
- BitNet training с нуля: **нереалистично соло**, нужен GPU ($20K+).
- HDC capacity: ~10^308 patterns addressable for N=1024-bit codes.
- 1-month-old infant demonstrator + publishable research: **$0 - $300 budget achievable**.

## 5. Стратегия расходов

| Tier | Стоимость | Что даёт |
|---|---|---|
| $0 | бесплатно на текущей машине | Levels 1-3 demos |
| $30/мес | RunPod spot H100 | Medium-scale experiment (10hr/wk) |
| $300/мес | Lambda Labs H100 | Multiple experiments per week |
| $3K | TogetherAI / Modal credits | Train BitNet 700M с нуля |
| $30K | AWS spot | Publication-grade validation |

## 6. Реальные точки приложения

| Путь | Сложность | ROI |
|---|---|---|
| HDC-as-LLM-preprocessor | средняя | Mem0 / MemGPT benchmark, publishable |
| BitLinear + Boolean BrainLevel 1 | низкая | Pavlov demo, HN-worthy video |
| Spelke core knowledge tests | средняя | Cognitive Science workshop |
| Fuzzy-bit continuous relaxation | средняя | ICML workshop on novel quantization |
| NCA-Brain integration | высокая | ALIFE workshop publication |

## 7. Стратегия найма инвесторов / партнеров

- Собрать working demo "Pavlov habituation + 1-shot pattern recognition" на текущем machine (4-6 недель)
- Записать 5-мин explainer video и 30-сек результат GIF
- Сделать arXiv preprint "Boolean+HDC+BitNet hybrid brain on CPU"
- Apply к: NeurIPS Workshop on Cognitive Modeling, ICML Workshop on Innovative Hardware,
  ALIFE, BICA.
- Outreach к: Microsoft Research (BitNet авторы), Intel Neuromorphic Lab (Loihi HDC),
  ETH / MIT / Stanford cognitive science labs.

## 8. Open questions, не нашедшие full answers

- Точные arXiv ссылки на: Payani & Fekri Differentiable Boolean (UNVERIFIED),
  IBM Logical Neural Networks (UNVERIFIED), Liu BiT Binarized Transformer (UNVERIFIED),
  Microsoft BinaryMoE (UNVERIFIED, mentioned in BitNet b1.58 future work).
- Independent reproductions BitNet b1.58 at scale 7B+ (UNVERIFIED). Это самый важный
  вопрос для уверенности в направлении.
- HDC chip benchmarks 2024-2025 — Intel Loihi 2 production benchmarks (UNVERIFIED).
- Flow Lenia (Plantec 2022) — точные arXiv UNVERIFIED.

## 9. Сохраняемые ссылки

Все найденные первоисточники зафиксированы в `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` §6.
Каждый future reference должен использовать этот список для проверки — никаких новых
агрегаторов общего уровня.

## 10. Уроки для будущих research-волн

1. **4 параллельных волны — оптимум** (saturated на 4 для этого размера project).
2. **Deep-research agents зависают** на >30 min — нужны shorter scoped или sub-agent decomposition.
3. **Без явной commitment к реальному demo** — research alone не работает.
4. **Cross-disciplinary всегда даёт неожиданные мосты** — между Anokhin forward-model и нашим
   EnrichedChainEvaluator; между Nyaya 4-state и BitNet b1.58 {+1, 0, -1}; между Mamba-3
   selective state space и нашим `EnrichedVectorOps`.
5. **Пользователь человеческий intuition** — самая ценная часть в research. Конкретные формулы
   приходят в результате обсуждения с пользователем, не из одиночного web search.

## 11. Запуск следующих RUNs

| RUN | Что | Time budget |
|---|---|---|
| 437 | `HdcEncoding.java` + tests | 1 day |
| 438 | `HdcBinding.java` + tests | 1 day |
| 439 | `BitLinear.java` + tests | 1 day |
| 440 | `CodebookMemory.java` + tests + `Exp440HdcRoundTripTest` | 1 day |
| 441 | `HebbianUpdater.java` + tests + Pavlov demo | 2 days |
| 442 | `HdcBrain.java` integrating 437-441 + tests | 2 days |
| 443 | `HdcConditioning.java` + Pavlov habituation test | 1 week |
| 444 | `SpelkeCoreKnowledge.java` + 4 test suites | 2 weeks |
| 445 | `CrossModalPaired.java` + 100×100 pairing test | 1 week |
| 446 | `NcaBrainSimulator.java` + regen test | 2 weeks |
| 447 | `HdcAsLlmPreprocessor.java` + 100k compression | 1 week |
| 448 | `LlmOutputDecoder.java` + concept extraction | 1 week |
| 449 | `SyntheticGrammarExperiment.java` + Pinker test | 2 weeks |
| 450 | `AdultHabituationExperiment.java` + Sokolov curve | 1 week |

Total: ~3 months to Publication-grade demo.

