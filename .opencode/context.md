# MATRIX Project Context - Session State

## Current Status
- **Миссия**: автономный оптимальный ИИ по документации; волны; перед КАЖДЫМ push: `git fetch origin && git fetch gitverse` + проверка новых коммитов в main (параллельно идёт чужая доки-волна!) → при расхождении rebase, затем push origin + gitverse
- База коммитов: f2b8874 → 4e3744a → 515c0ae → 0a9763a. Тесты последний прогон: 179/0 (cluster+bir)

## Волна Критерий A — ГОТОВА К КОММИТУ (остался 1 unchecked todo S6.1.4 = сам commit+push)
Сделано и верифицировано:
1. `cluster/NeuronClusterActor.java` → BIR-путь (`BooleanRuntime.evaluate(TruthTableAdapter.toBir(table),packed)`) + кэш `formCache` (synchronized WeakHashMap)
2. Тест `matrix-core/src/test/java/io/matrix/cluster/BirMigrationEquivalenceTest.java` (legacy vs BIR, seed 20260823, 64×64) — зелёный
3. `docs/design/DESIGN-14-bir-consumer-migration.md` создан (стратегия strangler-fig, кэш-правило §3 — AgentBrainService только с кэшем, реестр прогресса, FROZEN FrozenAxiomNeuron исключён)
4. EXP-002 в docs/research/HYPOTHESES.md: фиксация бинаризации median-threshold ДО запуска
5. todo.md: M6 добавлен, S6.1.1–S6.1.3 [x], S6.1.4 [ ] ; WAL.md дополнен (волна Критерий A + обновлённое Следующее действие)

## Финальный шаг этой волны (выполнить немедленно после compaction)
```
git fetch origin && git fetch gitverse && git status -sb   # есть ли новые main-коммиты?
# если origin/main впереди: git pull --rebase origin main (разрешить конфликты, особенно в docs/*)
python3 - <<'PY'  # отметить S6.1.4 [x] в .opencode/todo.md после успеха
PY
git add -A && git commit -m "feat(cluster): migrate NeuronClusterActor to BIR execution path (Критерий A wave 1)

- DESIGN-14 consumer migration strategy + progress registry
- longEvaluate via BooleanRuntime with weak TtForm cache; equivalence test legacy-vs-BIR
- EXP-002 binarization preregistration fixed (median-threshold)"
git push origin main && git push gitverse main
```
Затем обновить .opencode/status.md (волна завершена, счётчики).

## Реестр миграции (DESIGN-14) — следующие цели
api/MatrixResource:243 → bridge/NeuroSymbolicBridge:86,296 → explain/BooleanExplainability:57,171 → agent/PretrainedLoader:149,166 → agent/AgentBrainService:801 ⚠️hot-loop(только кэш форм) → 🔒 ethics/frozen/FrozenAxiomNeuron НЕ ТРОГАТЬ. Прочие ~118 `.evaluate(` вне bir/ — аудит семантики (многие не булевы: FROZENFNLGuardian и пр.)

## Constraints / факты
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod; seeded Random вне рантайма
- LSP фантом tsetlin/TsetlinAutomaton — верить gradlew (146+33=179 тестов зелёные фактически)
- Полный gradle test OOM — батчи; компактные ответы
- Адаптеры: io.matrix.bir.TruthTableAdapter.toBir/fromBir; DecisionTreeAdapter; BooleanRuntime.evaluate(Bir,long[])→long[]

[COMPACTION_COMPLETE]
