# MATRIX Project Context - Session State

## Current Status
- **Миссия**: волны; перед push всегда `git fetch` обеих remote → при новых коммитах в main: rebase. Параллельная чужая волна (практики/алгоритмы) может прийти в любой момент
- База: main = origin/main = gitverse/main, локально 3d23aa2 + один новый незакоммиченный правка
- Тесты базовые: 179/0 (cluster+bir прогон)

## Незакрыто ПРЯМО СЕЙЧАС (одна правка на диске, не закоммичена)
1. `api/MatrixResource.java`: `/truth-table` endpoint переведён на BIR — заменён блок:
   старый: `boolean result = table.evaluate(input);`
   новый: `var birForm = io.matrix.bir.TruthTableAdapter.toBir(table); boolean result = io.matrix.bir.BooleanRuntime.evaluate(birForm, new long[]{input})[0] == 1L;`
   (кэш не нужен — таблица строится per-request; комментарий DESIGN-14 на месте)
2. ДАЛЕЕ: скомпилировать и прогнать тесты api: `gradlew :matrix-core:test --tests "io.matrix.api.*"` — ВНИМАНИЕ: возможны QuarkusTest/env-фолы; если падения выглядят средовыми (Docker/port), сравнить с baseline: `git stash && gradlew ... те же тесты ... && git stash pop` и сопоставить список упавших. Мои правки затрагивают только evaluateTruthTable
3. Обновить DESIGN-14 реестр: строка api/MatrixResource → ✅ wave 2
4. todo.md M6: добавить S6.2.x пункты (MatrixResource [x], commit+push [ ]) или отметить в S6.1.4-стиле; WAL «Что сделано» дополнить; status.md
5. fetch обеих remote → если новые коммиты: `git pull --rebase origin main`; затем `git add -A && git commit -m "feat(api): migrate /truth-table endpoint to BIR runtime (Критерий A wave 2)" && git push origin main && git push gitverse main`

## Реестр миграции остаток (DESIGN-14)
bridge/NeuroSymbolicBridge:86,296 → explain/BooleanExplainability:57,171 → agent/PretrainedLoader:149,166 → agent/AgentBrainService:801 ⚠️hot-loop(только кэш форм на принятых) → 🔒ethics/frozen/FrozenAxiomNeuron НЕ ТРОГАТЬ. ~118 прочих `.evaluate(` вне bir/neuron — аудит семантики (не булевы — вне scope)

## Constraints / факты
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod
- LSP фантом tsetlin/TsetlinAutomaton — верить gradlew
- Полный gradle test OOM — батчи; компактные ответы
- Адаптеры: TruthTableAdapter.toBir/fromBir; BooleanRuntime.evaluate(Bir,long[])→long[]
- gitverse push: bypass-warning но проходит

[COMPACTION_COMPLETE]
