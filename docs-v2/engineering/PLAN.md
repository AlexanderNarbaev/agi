# PLAN

Что реализовано и что осталось (synth-only).

## Реализовано в коде ()

### Ядро (всё работает, тесты зелёные)
- BIR-компилятор + 3 формы (TT, CLAUSESET, BDD) + JvmSimd/Fpga-бэкенды.
- 37 сайтов мигрированы на BIR; INV-1 source-scan страж в CI.
- Продюсеры: TsetlinTrainer (этап B FR-B1/B2), WisardProducer (H-010 accepted), MpdtGaProducer (baseline).
- ELSP-федерация: ElspChannel (Ed25519) + ElspChannelMlDsa (ML-DSA, JEP 497 native).
- Curriculum: devloop 12 классов (CompetenceAssessor, CurriculumEngine ZPD, MaturityGateKeeper MA-0..5).
- Lifecycle: CauldronProtocol, FnlGate (SHADOW→PROMOTED), ConsolidationCycle, PlanRunner Hoare, PlanPreprocessor AC-3.
- RicCI-топология знаний (ktopo).
- BirClassifier, Distiller, OnnxActivationTeacher (onnxruntime 1.29.0).
- CRD SignalModule/TaskCell в operator.

### Эксперименты (реальные цифры)
- **H-010 accepted** (EXP-010, 9 прогонов, median ×242, 9/9 точность).
- **H-002/H-003 refuted-toy** (EXP-002/003, GA быстрее и точнее на синтетике).
- **EXP-009B/C**: дистиллят BIR ×149 быстрее ORT-CPU на синтетическом FFN, fidelity.999; GPU-нога (RTX 5070 Ti): батч 0.02 мс, per-call 17.25 µs; **MATRIX BIR ×276 быстрее GPU на точечных вызовах**.
- **JMH-гейт Batch\*** выполнен: 32–69M ops/s, решение «оставить как есть».

## Стек (актуальный)
Java 25 · Quarkus 3.38.3 · GraalVM plugin 1.1.10 · Avro 1.12.2 · ONNX Runtime 1.29.0 · Kafka-clients 4.3.1 · Testcontainers 1.21.3 · ML-DSA postquantum (JEP 497). См. [STANDARDS-MATRIX.md](STANDARDS-MATRIX.md).

## BLOCKED-EXT / отложено

| Блокер | Что нужно |
|---|---|
| DJL/ONNX экспорт **реального** LLM-среза (.onnx FFN Qwen-0.6B и т.п.) | python-тулчейн + веса; инфраструктура (OnnxActivationTeacher) уже готова |
| Доменные корпуса для полного EXP-002/003/009 вердикта | данные (удалены по директиве; могут быть восстановлены из git-истории) |
| **Energy-метрики** для гейта H-009 | wattmeter или модельное энергопотребление |
| Audio-events этап 3 | приоритизация DESIGN-06 |
| Квантовый код FR-D3 (BIR-to-MPS) | квантовый субстрат |
| FPGA-синтез | yosys/nextpnr |
| Полные цепи Ханселя (DESIGN-09 v2) | research wave |

## Следующее (минимально-ценностные шаги)

1. TLA+-спек-кандидаты (см. [architecture/FORMAL-CONTRACTS.md](../architecture/FORMAL-CONTRACTS.md)):
 - `BRC-Step` (закрывает пробел `reasoning/`),
 - `ConjugateBudgeter-DP`,
 - `Memory-M4-Causal`,
 - `MCTS-LATS-Visit`.
2. SDD-свип: спеки для топ-`needs-spec` (`reasoning/BrcChain`, `mediator/`, `hades/`, `memory/`, `rag/`).
3. Production-domain прогоны EXP-010/002/003 на восстановленных корпусах.
4. CUDA-нога EXP-009 через `onnxruntime_gpu` (Java) при доступности cuDNN-тулчейна.
5. Research-only Python: `scripts/bench_gpu_vs_bir.py` v2 с батчем (полные пороги).