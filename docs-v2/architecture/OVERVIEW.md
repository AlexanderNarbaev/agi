# ARCHITECTURE — OVERVIEW

MATRIX — детерминированное нейро-символическое ядро: «знание» = булевы таблицы, обучение вне рантайма, исполнение — единая BIR-точка.

## Слои

```
+-------------------+    +-------------------+
|  producers (off) |    |  ingest/HF-Safe   |  ← обучение/загрузка
+-------------------+    +-------------------+
            |                    |
            v                    v
      +-------------------------------+
      |        io.matrix.bir          |  ← компилятор + 3 формы
      |  (Bir, BirForm, BooleanRuntime)|
      +-------------------------------+
            |
            v
      +-------------------------------+
      |  io.matrix.neuron / ktopo /    |
      |  brain / federated / mediator  |  ← рантайм-контур
      +-------------------------------+
            |
            v
      +-------------------------------+
      |  api/ OpenAI-compat, MCP,      |
      |  REST, WebSocket               |  ← фасады
      +-------------------------------+
            |
            v
      +-------------------------------+
      |  events/ Kafka, r2dbc/PG,      |
      |  redis/, snapshot/            |  ← журнал/кэш/снапшоты
      +-------------------------------+
```

## Ключевые принципы

1. **Детерминизм**: рантайм не вызывает LLM; решения — булева цепочка (BRC); обучение стохастично и отделено.
2. **Единая точка исполнения**: INV-1 source-scan страж (`bir.Inv1SourceGuardTest`) запрещает прямые легаси-вызовы вне BIR.
3. **FROZEN этика**: четыре запрета (Конституция Статья IV) математически вшиты в `ethics/frozen/FROZENFNLGuardian` с TLA+ спекой.
4. **Измеримость**: каждый переход (`BooleanRuntime`) сопровождается JMH-бенчмарком и результатами EXP-протокола в `research/reports/`.

## Конвейер запроса (L0)

1. **Perception**: энкодеры `signals/SignalModule` (text/lexicon, image, audio) → BitSet.
2. **Deliberation**: `reasoning/BrcChain` (BRC) + `mcts/Lats`/`Mcts` + `agent/AgentLoop` с гейтами MA-0..5.
3. **Guardrail**: `ethics/EthicalFilter` → `StructuralSafetyGuard` → `LieDetector` (FROZEN-логика).
4. **Rendering**: декодер `signals/` (модуль registry) → выход.
5. **Audit**: `audit/HashChain` append-only, hash-цепь к `x-matrix-trace` header.

## Внешние зависимости инфраструктуры

| Сервис | Назначение |
|---|---|
| Kafka 3.9 | `events/` журнал кластера |
| PostgreSQL via R2DBC | `M4` память, lineage |
| Redis | `NeuronCacheService` для TtForm-кэшей |
| Kubernetes + matrix-operator CRD | деплой SignalModule/TaskCell |

## Inlay из внешних источников

Подходы `LangChain` и `LangGraph` рассмотрены и не применены (внешние зависимости и недетерминированные LLM-цепочки). Принципы RAG (поиск-по-корпусу с порогами и явной трассировкой) частично отражены в `research/HYPOTHESES.md` через карточки экспедиций (BRC, retrieval-trace). Источник: домашний проект `/home/alexandr-narbaev/Projects/rag-system` (без ссылки, без копирования).
