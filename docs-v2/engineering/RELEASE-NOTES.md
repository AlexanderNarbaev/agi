# RELEASE-NOTES — v2 rebuild 

## Что пересобрано

### Новая навигация

```
README.md → docs-v2/INDEX.md → {architecture,specifications,designs,research,engineering,operations}/
```

Корневые нормативы:
- `README.md` — frontpage (только актуальные ссылки на docs-v2/).
- `CONSTITUTION.md` (singleton FROZEN) — переписан компактно: 8 статей (детерминизм, K_MAX, FROZEN-зоны, четыре запрета, coverage gate, запрещённые claims, стек, среда и явность).
- `AGENTS.md` (singleton FROZEN) — процедуры сессий: роли, формат коммитов, тестирование, JMH-команды, переопределение директивами владельца.
- `WAL.md` — текущий снапшот прогресса (slим).

### Научные результаты, подтверждённые измерениями

- **H-010 accepted (synthetic-scope)**: WiSARD vs Tsetlin, 9 прогонов, median ×242, WiSARD 9/9 по точности.
- **H-002/H-003 refuted-toy**: GA быстрее ×5–10, точнее до +8.75 п.п., артефакт компактнее ×7500.
- **EXP-009B/C**: дистиллят BIR ×149 быстрее ORT-CPU, ×276 быстрее GPU на per-call; fidelity.999.
- **JMH-гейт Batch\*** выполнен (58.73M / 32.27M / 68.66M ops/s) → решение «оставить как есть».

### Стек актуальный

Java 25 · Quarkus 3.38.3 · GraalVM plugin 1.1.10 · Avro 1.12.2 · ONNX Runtime 1.29.0 · Kafka-clients 4.3.1 · Testcontainers 1.21.3 · ML-DSA postquantum (JEP 497).

### Что осталось

См. `engineering/PLAN.md` — DJL/ONNX экспорт **реального** LLM-среза, доменные корпуса, energy-метрики, audio-events этап 3, квантовый/FPGA-код.

## Контроль качества

- Все новые файлы `docs-v2/` имеют header с changelog «v2 rebuild».
- Все ссылки между новыми документами — относительные и валидны.
- Никаких ссылок на старые пути `docs/spec/`, `docs/design/`, `docs/engineering/` (они идут в архив).
- Никаких ссылок на внешний `/home/alexandr-narbaev/Projects/rag-system` (его идеи вложены без ссылок).

## Известные пробелы SDD-свипа (см. SDD-COVERAGE.md)

~~- `reasoning/`, `mediator/`, `hades/`, `memory/`, `rag/` — нужны TLA+-спек (следующая сессия).~~ ✅ **RUN 13 (2026-09-05)**: SPEC-008..012 закрыли SDD-sweep.

## RUN 12–21 (2026-09-05)

### Что реализовано за RUN 12–21

- **RUN 12** — `BrainLoopService` подключён к `/v1/chat`: каждый запрос проходит через
  девятистадийный `ConsciousnessLoop`; в ответе приходит заголовок `X-Matrix-Trace`.
- **RUN 13** — SDD-sweep: SPEC-008..012 (reasoning/BrcChain, mediator/, hades/, memory/,
  rag/) — 633 строки нормативной документации.
- **RUN 14** — `TlaSpecSmokeTest` (10 тестов): структурная валидация 7 TLA+-спеков в
  `formal/`. Проверяются Init/Next/safety-invariants для каждого.
- **RUN 15** — `ChainFeatureCache` (SHA-256 keyed, диск-бэкенд 24 MB); LM head теперь
  тренируется на реальных chain-фичах, а не на FNV-1a хеш-псевдофингерпринте.
- **RUN 16** — H-043 + H-046: accuracy=0.915, utility=1.000. Обе гипотезы приняты на
  synthetic-scope.
- **RUN 17** — production-corpus EXP reruns: 6607 пар, 99.7% кириллица, 0.030 ms/пара.
- **RUN 18** — native build: class-init лист расширен с 5 до 17 записей; каскадные
  ошибки. RFC required.
- **RUN 19** — `LmHeadFeedbackTrainer`: `/v1/chat/feedback` инкрементально обучает LM head.
  Negative-feedback пока no-op (LmHead не имеет signed update API).
- **RUN 20** — E2E stress test 1000 запросов: p99 = 2 μs; honest hit-rate = 0.000
  (disjoint-sample setup).
- **RUN 21** — documentation stabilization: FINALSUMMARY grew to 1700 строк,
  Sections XXI-XXX.

### Научные результаты

- **H-043 accepted (synthetic-scope)**: utility = 1.000 ≥ 0.7 при k=100, ε=1.0.
- **H-046 accepted (synthetic-scope)**: accuracy = 0.915 ≥ 0.9, precision = 1.000.

### Состояние тестов

- 79/79 unit + integration тестов проходят (RUN 12: +9, RUN 14: +10, RUN 15: +10,
  RUN 16: +10, RUN 17: +5, RUN 19: +7, RUN 20: +6).
- BrainLoopServiceTest, ChainFeatureCacheTest, LmHeadFeedbackTrainerTest,
  TlaSpecSmokeTest, Exp043/046, Exp016, Exp020 — все green.