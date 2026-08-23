# MATRIX Project Context - Session State

## Current Status
- **Миссия**: волны без остановки; перед push fetch+rebase (гонки бывают — всегда после пуша проверять `git status -sb` и `rev-list --count main..origin/main`)
- **Синхронизировано**: main=origin/main, HEAD `77d1224`, behind=0, дерево чистое. gitverse отстаёт (временно не отвечает на push; origin первичен; вернуть позже `timeout 60 git push gitverse main`)
- Тесты: все затронутые пакеты зелёные (api 163/0, explain 9/0 после фикса §4.1, cluster+bir 179/0 ранее)

## Волны Критерия A — ВЫПОЛНЕНО
- Wave 1: cluster/NeuronClusterActor ✅ (+equivalence test)
- Wave 2: api/MatrixResource /truth-table ✅
- Wave 3: bridge/NeuroSymbolicBridge ✅ (birEvaluate + weak cache)
- Wave 4: explain/BooleanExplainability ✅ (DecisionTreeAdapter+static cache; поймана ловушка BitSet.toLongArray()→пустой массив → правило §4.1 в DESIGN-14)
- Wave 5 (решение архитектора): PretrainedLoader=producer-side ⏭️, evaluateTreeFitness=training-side ⏭️ (вне Критерия A по CONSTITUTION II.2–3); **настоящий рантайм-фронт = NeuronLayer/HierarchicalBrain** (io/matrix/neuron/) — ЭПИК СЛЕДУЮЩЕЙ СЕССИИ

## Следующие шаги (новая сессия продолжит отсюда)
1. **gitverse догнать**: `timeout 60 git push gitverse main` (сейчас таймаутится; не блокирует)
2. **Эпик NeuronLayer/HierarchicalBrain**: grep `.evaluate(` в io/matrix/neuron/ (NeuronLayer, HierarchicalBrain) → дизайн кэша TtForm внутри NeuronLayer.fromTruthTables (формы строить ОДИН РАЗ при fromTruthTables, хранить рядом с таблицей) → миграция act()-пути → equivalence property → это закрывает основную массу рантайма Критерия A
3. Затем: WiSARD-унификация контракта продюсеров; запуск EXP-002 по пререгистрации (median-threshold зафиксирован); code-review волна новых BIR-точек; осторожная волна апгрейда зависимостей (Quarkus/Pekko pinned)
4. Пользователь просил «более качественные алгоритмы/новейшие протоколы» — кандидат: применить находки параллельного атласа (§95 доминанта/§96 деятельность/§97 ЗБР) к Cauldron/REFLEX-контуру — сначала прочитать эти секции в ALGORITHM-ATLAS

## Constraints / факты
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod; seeded Random вне рантайма
- LSP фантом tsetlin/TsetlinAutomaton — верить gradlew; полный test OOM — батчи; компактные ответы
- Гонки git: python-правки после fetch ломают rebase («unstaged changes») → порядок: правки→commit→pull --rebase→push→verify rev-list=0
- Коммиты сессии: f2b8874→4e3744a→515c0ae→0a9763a→(2ac6684чуж)→3d23aa2→(031a492,92e8c60,7841b13,6e98e6d чужие)→c00761f→c475c3c→77d1224

[COMPACTION_COMPLETE]
