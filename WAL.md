# WAL

**Статус: ephemeral.** Переписывается в конце каждой сессии.

## Активный фокус
Полная инвентаризация «пакет ↔ спека» завершена: `docs/engineering/SDD-COVERAGE.md` расширен до полного sweep всех 69 пакетов `io.matrix.*` (455 `.java`). Покрытие спеками/дизайнами/TLA+/гипотезами — 187 классов ≈ 41%; needs-spec бэклог — 92 класса ≈ 20%.

## Правила сессии
- НЕ ТРОГАТЬ: ethics/**, CONSTITUTION.md, существующие avro/**, .github/workflows/**
- models/pretrained|training_data — карантин снят по директиве владельца (удалены окончательно, см. Известные проблемы)
- Coverage gate ≥82% METHOD не понижен; каждый новый класс с тестами
- Значимые изменения → commit + push (директива владельца)

## Что сделано (сессия 2026-08-26)
### SDD full sweep (docs-only, код не тронут)
- `docs/engineering/SDD-COVERAGE.md`: три таблицы — Mapped (22 пакета/187 кл.), Research-experimental (10/69), Needs-spec+infra (37/194); каждый пакет проверен ls'ом, каждая связь — по javadoc-ссылкам SPEC/DESIGN/EXP в коде, шапкам `formal/*.tla` и карточкам HYPOTHESES.md
- Новые связи зафиксированы: `consensus↔formal/Consensus.tla`, `audit↔HashChain.tla`, `neuron↔MPDTNeuron.tla`, `ethics↔BotEthicsPipeline+FrozenEthicalFNL.tla`, `verification↔EXP-017`, `guardrail↔EXP-006`, `pilot↔EXP-005/H-005`
- Топ needs-spec бэклога: `reasoning` (BrcChain), `mediator`, `hades`, `memory`, `rag`
- Дубли зон отмечены: explain/explainability, knowledge/ktopo, federated/federation

## Следующее действие
Спеки для needs-spec-топа начиная с `reasoning/BrcChain` · архивация или карточки для research-experimental пакетов · DJL/ONNX учитель для Distiller (нужна зависимость) · JMH-гейт Batch*→evalBatch.

## Известные проблемы
- yosys/nextpnr отсутствуют — FPGA-синтез локально BLOCKED
- Субагенты «Insufficient Balance» — делегация недоступна
- LSP ложная ошибка bir/FpgaBackend.java:150 (компиляция чистая)
- MonotoneDecoder: граница ≤n запросов (полные цепи Ханселя — future)
- Инцидент карантина models/{pretrained,training_data} закрыт: владелец удалил с диска и индекса, агент закоммитил; `.gitignore` защищает (`/models/`)
