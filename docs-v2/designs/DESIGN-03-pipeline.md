# DESIGN-03 — Контур запроса (P-E-D pipeline)

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild).

## Что

Три стадии: Perception (encoding) → Deliberation (BRC + guards) → Rendering (decoding). ББ + булева сердцевина; внешние зависимости (LLM, rag, MCP) изолированы на краях.

## Реализация

- Perception: `signals/{Text,Image,Audio}SignalModule`, registry.
- Deliberation: `reasoning/BrcChain` + `mcts/{MctsTree,LatsNode,LatsReflector,LatsValueFunction}` + `agent/AgentLoop` + `actions/PlanRunner` (`precondition_violated` и пр. ошибки).
- Guards: `ethics/EthicalFilter`, `StructuralSafetyGuard`, `LieDetector`, `frozen/FROZENFNLGuardian`.
- Rendering: decoder-метод модуля; выход через x-matrix-trace.
- Фасады: `api/OpenAIChatResource` (`/api/v1/chat/completions`), `api/AgentWebSocket`, `mcp/MatrixMcpServer`.

Тесты: `agent/*`, `actions/*`, `api/*`, `integration/*` (Testcontainers — KafkaIntegrationTest требует Docker-сервиса живого; PostgeSQLIntegrationTest аналогично).

## Отложено

- Прокси `/matrix/*` алиасы — косметика (rest surface достаточно `/api/v1/*` + MCP-инструменты).
