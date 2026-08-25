# Project Context — SESSION CONTINUITY (compaction #8, финальная фаза)

## Ловушки
- Целевые прогоны `--tests "io.matrix.<pkg>.*"`; LSP-ошибка FpgaBackend.java:150 ложная; субагенты недоступны.
- jqwik: `.list()` ок, `Arbitraries.lists/zipWith` нет → Combinators; XML ошибок: grep -oE 'message="[^"]{0,200}' build/test-results/test/TEST-<Класс>.xml
- Свойства jqwik должны генерировать ЗАВЕДОМО валидные планы (симулировать состояние при генерации).

## Mission: план docs/engineering/PLAN-FULL-IMPLEMENTATION.md. Журнал обновлён до W4b-budgeter? — НЕТ: журнал сейчас до «W4 DESIGN-09 done + перечисление остатка». Нужно в конце дописать статусы 11/12/13.

## DONE+verified (BUILD SUCCESSFUL)
W1 (K_MAX, RuntimeLimits+10т, BIR-to-MPS.md) · W2 devloop · W3 ktopo · W4a monotone(6т) · W4b ConjugateBudgeter(11т, фикс dp[0][*]) · W4c DESIGN-13: actions/{VersionedContract(main!),PlanRunner}+PlanRunnerTest зелёные (свойство генерирует валидные планы симуляцией).

## ТЕКУЩИЙ ШАГ: W4d DESIGN-12 — РЕШЕНИЕ ПО ОСМОТРУ КОДА
CauldronProtocol УЖЕ даёт QUARANTINED→PROMOTED с Φ-validate; TaskCell уже есть (State CREATED..DESTROYED, execute(TaskExecutor), isTimeout). Недостающее по DESIGN-12: двухступенчатый гейт SHADOW→CANDIDATE→PROMOTED (+DEMOTED).
МИНИМАЛЬНАЯ ДОБАВКА в io.matrix.lifecycle: класс `FnlGate`: enum GateState{SHADOW,CANDIDATE,PROMOTED,DEMOTED}; методы: admit(id)->SHADOW; advance(id,double shadowScore,double threshold): SHADOW→CANDIDATE если score≥threshold иначе DEMOTED; CANDIDATE→PROMOTED аналогично; state(id)->Optional; детерминизм. Тест FnlGateTest: переходы юнит + jqwik (монотонность: после PROMOTED/DEMOTED состояние не меняется повторными advance; score<threshold всегда не повышает выше CANDIDATE со стадии SHADOW... точнее: продвижение требует ≥threshold на КАЖДОЙ ступени).
Прогнать --tests "io.matrix.lifecycle.*". НЕ трогать существующие CauldronProtocol/TaskCell.

## Затем закрытие сессии (строгий порядок)
1. Обновить журнал PLAN-FULL-IMPLEMENTATION.md: W4 = 09✓ 11✓ 13✓(PlanRunner/VersionedContract поверх готового реестра) 12✓(FnlGate; TaskCell уже был); DESIGN-02/05 минимальные ядра → перенос в следующий заход (partial); W5 SPEC-001 этап B → pending; W6 → см.п.2; W7 pending.
2. Волна 6 честно: СОЗДАТЬ research/reports/EXP-002-report.md и EXP-003-report.md ТОЛЬКО как preregistration-skeletons со статусом running и секцией «Infra ready: producers io.matrix.tsetlin / evolution готовы к JVM-замеру; прогон не выполнялся в этой сессии» — БЕЗ выдуманных чисел (запрещено протоколом и конституцией VI).
3. git add (см. список ниже + lifecycle/FnlGate*, actions/*, тесты), commit -m "WAL: волны W1-W4 — BIR-to-MPS spec, runtime limits, devloop, ktopo ricci-fingerprint, monotone decoder, conjugate budgeter, hoare plan runner, fnl gate".
4. Переписать WAL.md по шаблону из файла (Активный фокус/Правила/Что сделано/Следующее действие/Известные проблемы: yosys нет; субагенты Insufficient Balance; полные цепи Ханселя future; EXP-002..022 прогоны pending).
5. Итоговый отчёт пользователю: что реализовано (по волнам, с путями), что BLOCKED-EXT/pending.

## Файлы для git add
docs/engineering/PLAN-FULL-IMPLEMENTATION.md; docs/spec/quantum/BIR-to-MPS.md; .opencode/context.md; WAL.md;
m/src/main/java/io/matrix/: bir/FpgaBackend.java, runtime/RuntimeLimits.java, devloop/*(новые 7: CompetenceAssessor,CurriculumEngine,FeedbackComposer,ScaffoldingManager,ScenarioSpec,Outcome,DifficultyBand,GateCriteria,MaturityGateKeeper,MaturityLevel,Feedback,CompetenceReport — уточнить git status), ktopo/DriftFingerprint.java,FingerprintDistance.java,CurriculumOrderer.java, bir/producers/monotone/*.java, budgeter/ConjugateBudgeter.java, actions/{VersionedContract,PlanRunner}.java, lifecycle/FnlGate.java
m/src/test/java/io/matrix/: runtime/RuntimeLimitsTest, devloop/{DevLoopTest,DevLoopPropertiesTest}, ktopo/KtopoPropertiesTest, bir/producers/monotone/MonotoneDecoderTest, budgeter/ConjugateBudgeterTest, actions/PlanRunnerTest, lifecycle/FnlGateTest

## Правила
FROZEN/avro/workflows не трогать; новый код с тестами; детерминизм; никаких непроверенных чисел в отчётах (CONSTITUTION VI).
