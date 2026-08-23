# MATRIX Project Context - Session State

## Current Status
- **Миссия**: волны без остановки (юзер оффлайн); перед push fetch→rebase; gitverse ВРЕМЕННО висит на push — origin первичен, gitverse ретраить с малым таймаутом
- HEAD: `c00761f` (=origin/main), локально НЕЗАКОММИЧЕНО: bridge/NeuroSymbolicBridge.java переведён на BIR

## Волна 3 (bridge) — правки НА ДИСКЕ, осталось верифицировать и запушить
1. ✅ Правки: строка 86 `extractDNF` → `birEvaluate(table,input)`; строка 296 `evaluateSample` → `birEvaluate`; добавлены поле `birFormCache` (synchronized WeakHashMap<TruthTable,TtForm>) + метод `birEvaluate` (полные имена io.matrix.bir.*, импорты не требуются). grep подтвердил: table.evaluate в файле больше нет, birEvaluate×3
2. СЛЕДУЮЩИЙ ШАГ: `gradlew :matrix-core:test --tests "io.matrix.bridge.*"` (если тестов пакета нет — просто compile; тогда прогнать хотя бы `--tests "io.matrix.cluster.BirMigrationEquivalenceTest"` для санити компиляции всего)
3. DESIGN-14 реестр: bridge строка → `✅ wave 3 (2 sites через birEvaluate+weak cache)`
4. WAL «Что сделано» += wave 2 api ✅163/0; wave 3 bridge; «Известные проблемы» += «gitverse push временно таймаутится (>7 мин), origin первичен»
5. todo M6 += T6.3 волна 3 пункты [x]
6. ФИНАЛ ВОЛНЫ: `git fetch origin -q; R=$(git rev-list --count main..origin/main); [ "$R" != 0 ] && git pull --rebase origin main`; `git add -A && git commit -m "feat(bridge): route NeuroSymbolicBridge evaluation through BIR (Критерий A wave 3)"`; `git push origin main`; gitverse: `timeout 60 git push gitverse main || echo GITVERSE_DEFERRED`

## Реестр остаток (DESIGN-14)
explain/BooleanExplainability:57,171 (tree.evaluate! — это DecisionTree, использовать DecisionTreeAdapter) → agent/PretrainedLoader:149,166 (table.evaluate внутри построения DecisionTree.Leaf/constant — конверсия при загрузке, кэш не нужен) → agent/AgentBrainService:801 ⚠️hot-loop GA fitness (ТОЛЬКО кэш форм на принятых нейронах; наив=2^20 eval) → 🔒ethics/frozen/FrozenAxiomNeuron FROZEN НЕ ТРОГАТЬ

## Constraints / факты
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod
- LSP фантом tsetlin/TsetlinAutomaton (дубли) — верить gradlew; полный test OOM — батчи; компактные ответы
- Адаптеры: TruthTableAdapter.toBir/fromBir; DecisionTreeAdapter есть для DecisionTree; BooleanRuntime.evaluate(Bir,long[])→long[]
- Юзер просил после реализации тщательно ревьюить код и переходить на более качественные алгоритмы/новейшие версии протоколов — очередь на следующий цикл: code-review волна по новым BIR-точкам + апгрейд зависимостей отдельной осторожной волной (Quarkus/Pekko pinned в build.gradle)

[COMPACTION_COMPLETE]
