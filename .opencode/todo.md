# Mission: Реализовать все запрошенные изменения и планы (SPEC-002 этап A — BIR-ядро)

> Восстановлено из WAL.md (сессия 2026-08-22) и аудита рабочего дерева.
> Квота субагентов исчерпана (WAL «Известные проблемы») — выполнение и верификация ведутся напрямую с tool-evidence.

## M1: Закрытие пробелов SPEC-002 этап A | status: completed
### T1.1: FR-A1 — TT→CLAUSESET минимизация + Avro | agent:direct
- [x] S1.1.1: Espresso/QM-минимизация в `BirCompiler.ttToClauseSet` через `TruthTableMinimizer` (QM exact k≤12, Espresso k>12 с восстановлением точности) | size:M | evidence: git diff BirCompiler.java + TtToClauseSetConverter.java (делегирование)
- [x] S1.1.2: `BirAvroCodec` + схема `resources/avro/bir_artifact.avsc` (additive, backward-compatible) + round-trip тесты | size:M | evidence: файлы существуют; mpdt_neuron.avsc не тронут (FROZEN OK); BirAvroCodecTest присутствует

### T1.2: FR-A2 — BDD-пакет исправления + property-тесты | agent:direct
- [x] S1.2.1: Багфиксы `BddForm.eval` (обход по var[node], level-skip) и const-0 root; `Builder.build(inputBits, provenance, root)` с явным root | size:M | evidence: git diff BddForm.java; вызовы обновлены в BirCompiler/BirEvaluateBenchmark/тестах
- [x] S1.2.2: jqwik `BirPropertiesTest` (каноничность, eval(BDD)=eval(TT), round-trip) | size:S | evidence: файл существует, аннотации @Property посчитаны (121 всего по пакету)

### T1.3: Тестовое покрытие пакета bir | agent:direct
- [x] S1.3.1: Заполнены пустые `JvmSimdBackendTest`, `TtToBddConverterTest`; новые BirInvariantsTest, конвертер-тесты, адаптер-тесты, BirRegistryTest, LineageLedgerTest, BirAvroCodecTest | size:L | evidence: все файлы в git status (untracked/modified), 121 @Test/@Property в пакете
- [x] S1.3.2: Прогон пакета `io.matrix.bir.*` зелёный (0 failed, 0 skipped) | size:S | evidence: gradlew :matrix-core:test BUILD SUCCESSFUL (job_9f49c994); JUnit XML: 15 классов, tests=121 failed=0 skipped=0

### T1.4: INV-2…INV-5 инварианты | agent:direct
- [x] S1.4.1: INV-2 `BirLimits` (matrix.bir.max-literals / MATRIX_BIR_MAX_LITERALS, дефолт 4096) вместо хардкода; подключение в MatrixMetrics; литерал-валидация ClauseSetForm | size:M | evidence: BirLimits.java соответствует комментарию application.properties дословно; FpgaBackend делегирует
- [x] S1.4.2: INV-3 fidelity-валидация ([0,1], NaN) + lossy-фабрики TtForm.lossy / ClauseSetForm.lossy | size:S | evidence: git diff BirForm/TtForm/ClauseSetForm/ClauseSetToTtConverter
- [x] S1.4.3: INV-4 BirRegistry.register отвергает provenance null/blank/"unknown" | size:S | evidence: git diff BirRegistry.java
- [x] S1.4.4: INV-5 `BirCompilerMutationTest` (мутант-гейт в обычной сюите, workflows не тронуты) | size:M | evidence: файл существует; .github/workflows не изменён (FROZEN OK)

### T1.5: FR-A3/A4 — бенчмарк и deprecation | agent:direct
- [x] S1.5.1: JMH legacy vs BIR baseline (`legacyTruthTableEval`) | size:S | evidence: git diff BirEvaluateBenchmark.java; matrix-core/build/results/jmh/results.json существует
- [x] S1.5.2: `@Deprecated` на neuron.TruthTable и neuron.DecisionTree со ссылкой на адаптеры | size:S | evidence: grep подтверждает @Deprecated в обоих файлах

### T1.6: Документация и исследование | agent:DocReviewer
- [x] S1.6.1: `docs/research/notes/BIR-THEORY-scan.md` (living-статус, источники L1–L4, рекомендации) | size:M | evidence: заголовок проверен, статус living, лестница источников на месте
- [x] S1.6.2: AGENTS.md переписан (normative, обзор, команды, ограничения, CI/CD) | size:M | evidence: git diff; все ссылки на документы проверены на существование; README «Конфигурация» есть
- [x] S1.6.3: WAL.md и context.md обновлены по протоколу | size:S | evidence: все 5 секций шаблона WAL присутствуют; claims сверены с реальностью (121 тест, results.json, deprecated)
- [x] S1.6.4: Doc Review верификация документации (claims↔reality, forbidden claims Art.VI, FROZEN-целостность) | size:S | evidence: Verdict: PASS — нарушений нет

## Известные ограничения (вне объёма этой миссии)
- Критерий A (миграция 61 call-site `.evaluate(`) — эпик на отдельные сессии (WAL «Следующее действие» #1)

## M2: BDD-алгебра + каноническая эквивалентность (WAL #2+#3) | status: completed
### T2.1: apply/ITE на computed-cache | agent:Worker(ses_fd4cffac)
- [x] S2.1.1: `apply(BddForm, Op)` / `not()` / `constant()` через рекурсивный ITE, локальный memo per-call, материализация через Builder.mk (reduction сохранён) | - | size:L | evidence: TsetlinAutomaton (fixed-direction reward/penalty+includeNow+compat API), TsetlinTrainer (pos/neg пары, TypeI Гранмо pR=(s-1)/s/pP=1/s, TypeII includeNow, export omit≠¬x) | size:L
- [x] S2.1.2: Удаление мёртвых полей uniqueTable/computedCache; честный javadoc | | evidence: seed-injected Random в конструкторе; same-seed → идентичные ClauseSet (тест) | | evidence: gradlew BUILD SUCCESSFUL; TOTAL XML tests=146 failed=0 (131 bir + 15 tsetlin) | size:S

### T2.2: equivalentTo независимо от порядка построения | agent:Worker
- [x] S2.2.1: Мемоизированное структурное сравнение (var-alignment), ограничение = одинаковый порядок переменных | | evidence: TsetlinExportPropertyTest.stateBoundsUnderArbitrarySequences (200×500 шагов reward/penalty/includeNow в [1..2N]) + их TsetlinTest | | evidence: exportedClausesetMatchesFiringSemantics — exhaustive 2^k сверка eval(CLAUSESET)==trainer.predict для случайных k∈[2..6],seed | size:M

### T2.3: Тесты и верификация | agent:Worker → Reviewer
- [x] S2.3.1: BirBooleanAlgebraTest: 7 операций vs TT-семантика, инволюция not, константы, order-independence эквивалентности + jqwik свойства | | evidence: TsetlinExportPropertyTest.stateBoundsUnderArbitrarySequences (200×500 шагов reward/penalty/includeNow в [1..2N]) + их TsetlinTest | | evidence: exportedClausesetMatchesFiringSemantics — exhaustive 2^k сверка eval(CLAUSESET)==trainer.predict для случайных k∈[2..6],seed | size:M
- [x] S2.3.2: Прогон io.matrix.bir.* зелёный (включая прежние 121 теста) | size:S | depends:S2.1.1,S2.2.1,S2.3.1 | evidence: gradlew BUILD SUCCESSFUL job_0c3be865 после чистки битого каталога test-results; XML: 16 классов, tests=131 failed=0 skipped=0

### T2.4: Исследование коннекторов (видение пользователя) | agent:Planner(ses_fd4cfddd)
- [x] S2.4.1: Карта концепций «коннекторов» в docs/: категории, реализовано vs spec-only, рекомендация следующего milestone | size:S | evidence: grep по docs/ = 0 совпадений «коннектор/connector/дендрит/синапс»; таксономии нет — ближайшие концепции: BDD-композиции apply/ITE (реализовано в M2), BRC-цепочки (reasoning/, max 5 шагов), голосование клауз (этап B tsetlin); рекомендация — этап B как следующий milestone


## Команда (волна 1) | PM:Commander(direct) · Architect:WAL-decisions · Developer:direct(code-делегация ненадёжна) · Tester:gradle+jqwik+Reviewer-agents · DevOps:dual-push(github+gitverse) · Security/DevSecOps:secrets-grep+FROZEN-gate · Data:avro-compat · Performance:JMH-existing · AI/ML:Tsetlin-design · Docs:WAL/GLOSSARY/SPEC-changelog

## M3: Этап B foundation — Tsetlin producer (FR-B1) | status: completed | agent:direct(AI/ML+Dev)
### T3.1: Ядро автоматов | agent:direct
- [x] S3.1.1: `io.matrix.tsetlin`: TsetlinAutomaton (2N состояний, reward/penalty, action include iff s>N), TsetlinClause (литералы ±x_j, eval=AND), Type I/II feedback по Гранмо, TsetlinTeam (T клауз, голосование sign) | - | size:L | evidence: TsetlinAutomaton (fixed-direction reward/penalty+includeNow+compat API), TsetlinTrainer (pos/neg пары, TypeI Гранмо pR=(s-1)/s/pP=1/s, TypeII includeNow, export omit≠¬x) | size:L
- [x] S3.1.2: Детерминизм обучения: seed-injected Random, обучение вне рантайм-контура (CONSTITUTION II.2-3) | | evidence: seed-injected Random в конструкторе; same-seed → идентичные ClauseSet (тест) | | evidence: gradlew BUILD SUCCESSFUL; TOTAL XML tests=146 failed=0 (131 bir + 15 tsetlin) | size:S

### T3.2: Свойства (jqwik, FR-B1) | agent:Tester
- [x] S3.2.1: границы состояний [1..2N] при любых последовательностях reward/penalty; инвариант действия от состояния; детерминизм same-seed | | evidence: TsetlinExportPropertyTest.stateBoundsUnderArbitrarySequences (200×500 шагов reward/penalty/includeNow в [1..2N]) + их TsetlinTest | | evidence: exportedClausesetMatchesFiringSemantics — exhaustive 2^k сверка eval(CLAUSESET)==trainer.predict для случайных k∈[2..6],seed | size:M

## M4: FR-B2 мост Tsetlin→BIR | status: completed
- [x] S4.1: Экспорт решения команды: TT(k≤20)→ClauseSetForm через BirCompiler (стохастика только в обучении, рантайм исполняет BIR); property: eval(CLAUSESET)(x) == (team.predict(x)==1) exhaustive | | evidence: TsetlinExportPropertyTest.stateBoundsUnderArbitrarySequences (200×500 шагов reward/penalty/includeNow в [1..2N]) + их TsetlinTest | | evidence: exportedClausesetMatchesFiringSemantics — exhaustive 2^k сверка eval(CLAUSESET)==trainer.predict для случайных k∈[2..6],seed | size:M
- [x] S4.2: Прогон tsetlin+bir пакетов зелёный → commit+push оба remote | | evidence: seed-injected Random в конструкторе; same-seed → идентичные ClauseSet (тест) | | evidence: gradlew BUILD SUCCESSFUL; TOTAL XML tests=146 failed=0 (131 bir + 15 tsetlin) | size:S

## M5: Документационная волна | status: completed | agent:Docs+PM
- [x] S5.1: GLOSSARY: Tsetlin automaton/clause/team-vote; SPEC-002 changelog строка (этап B начат, отклонение: пакет в matrix-core до анализа CI-влияния выделения модуля); README упоминание | | evidence: TsetlinExportPropertyTest.stateBoundsUnderArbitrarySequences (200×500 шагов reward/penalty/includeNow в [1..2N]) + их TsetlinTest | | evidence: exportedClausesetMatchesFiringSemantics — exhaustive 2^k сверка eval(CLAUSESET)==trainer.predict для случайных k∈[2..6],seed | size:M
- [x] S5.2: WAL rewrite + финальный commit+push оба remote | | evidence: seed-injected Random в конструкторе; same-seed → идентичные ClauseSet (тест) | | evidence: gradlew BUILD SUCCESSFUL; TOTAL XML tests=146 failed=0 (131 bir + 15 tsetlin) | size:S

## M6: Критерий A — миграция потребителей на BIR (DESIGN-14) | status: completed | note: эпик продолжается по реестру DESIGN-14 (living-doc), следующие волны — новые сессии
### T6.6: Волна 6 кейстоун NeuronLayer | agent:direct
- [x] S6.6.1: NeuronLayer.evaluate → BIR (ленивый weak-кэш форм на нейрон, нормализация слов); покрывает MultiBrainEnsemble/NeuralTextGenerator/act()-контур | size:M | evidence: io.matrix.neuron.* 244/0 BUILD SUCCESSFUL

### T6.15: Волна 15 JTMS/ATMS → LineageLedger | agent:direct
- [x] S6.15.1: Operation.RETRACT + retract() с justification-link (последний contentHash) + latestStatus()/isRetracted() (ATMS label) | size:M | evidence: LineageLedgerTest jtms-тесты зелёные; bir BUILD SUCCESSFUL
- [x] S6.15.2: commit+push оба remote после fetch-check | size:S

### T6.9: Волна 9 WiSARD унификация | agent:direct
- [x] S6.9.1: WisardProducer.toDecisionClauseSet (дистилляция ≤20) + WisardExportPropertyTest (exhaustive parity, single-pass memorization) | size:M | evidence: tsetlin BUILD SUCCESSFUL

### T6.7: Волна 7 Granmo-TM + предэтап EXP-002 | agent:direct
- [x] S6.7.1: Канонический TM (±голосование, TypeIa/Ib/II), дистилляция решения; предэтап EXP-002 зафиксирован как not-reproduced (@Disabled гарнесс, числа в карточке); tsetlin зелёный | size:L | evidence: canonical voting trainer + includeSafe guard + @Disabled harness + HYPOTHESES attempts ×3 + tsetlin/bir BUILD SUCCESSFUL

### T6.5: Волна 5 классификация целей | agent:Architect
- [x] S6.5.1: PretrainedLoader → producer-side (legacy ok); evaluateTreeFitness → training-side (вне Критерия A); runtime-эпик = NeuronLayer/HierarchicalBrain — следующая сессия | size:S | evidence: правки реестра DESIGN-14

### T6.4: Волна 4 explain | agent:direct
- [x] S6.4.1: BooleanExplainability (обе точки DecisionTree) → BIR через DecisionTreeAdapter+статический кэш; пойман и зафиксирован AIOOBE-ловушка BitSet.toLongArray → правило §4.1 в DESIGN-14 | size:M | evidence: прогон io.matrix.explain.* 9/0 после фикса

### T6.3: Волна 3 bridge | agent:direct
- [x] S6.3.1: NeuroSymbolicBridge оба call-site → birEvaluate через weak-кэш TtForm | size:S | evidence: правки строк 86/296 + прогон bridge+equivalence BUILD SUCCESSFUL

### T6.2: Волна 2 api | agent:direct
- [x] S6.2.1: MatrixResource /truth-table → BooleanRuntime (request-local form) | size:S | evidence: правка + прогон io.matrix.api.* 163/0 BUILD SUCCESSFUL
- [x] S6.2.2: DESIGN-14 реестр обновлён; fetch-check; commit+push оба remote | size:S

### T6.1: Волна 1 tracer | agent:direct
- [x] S6.1.1: DESIGN-14 стратегия (strangler-fig, кэш форм на принятом нейроне, реестр 127 sites, FROZEN-исключения) | size:M | evidence: docs/design/DESIGN-14-bir-consumer-migration.md
- [x] S6.1.2: NeuronClusterActor.longEvaluate → BooleanRuntime + WeakHashMap-кэш TtForm; equivalence-тест legacy-vs-BIR (64×64 seeded) | size:S | evidence: cluster/BirMigrationEquivalenceTest; прогон cluster+bir BUILD SUCCESSFUL 179/0
- [x] S6.1.3: EXP-002 фиксация бинаризации (median-threshold, заморозка до конца эксперимента) — пререгистрация закрыта | size:S | evidence: правка карточки EXP-002 в HYPOTHESES.md
- [x] S6.1.4: commit+push после fetch-проверки remote main (параллельная доки-волна!) | size:S
