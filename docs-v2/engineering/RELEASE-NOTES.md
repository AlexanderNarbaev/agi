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

- `reasoning/`, `mediator/`, `hades/`, `memory/`, `rag/` — нужны TLA+-спек (следующая сессия).