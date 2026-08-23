# MATRIX Project Context - Session State

## Current Status
- **Mission**: волны команды до полной реализации документированных функций; каждая волна = commit+push в оба remote (github origin + gitverse) ✅ настроено и работает
- **Wave 0 DONE**: commit f2b8874 (M1+M2, 122 файла) запушен в ОБА remote. Тесты: bir 131/0/0 зелёные
- **Wave 1 DONE**: план M3-M5 записан в todo.md (7 unchecked: S3.1.1,S3.1.2,S3.2.1,S4.1,S4.2,S5.1,S5.2); роли команды зафиксированы в todo.md

## Wave 2 IN PROGRESS — Tsetlin (FR-B1), конфликт разрешается
- Пакет io.matrix.tsetlin содержит РОЙ файлов от гонки агентов + мои:
  - `TsetlinAutomaton.java` — НА ДИСКЕ ИХ версия + мой дубль-блок методов в конце → **СИНТАКСИЧЕСКАЯ ОШИБКА (лишняя `}`), надо переписать файл ЦЕЛИКОМ** как надмножество API:
    ctor( int n ) init state=n; ctor(int n,int initialState) с проверкой [1..2N]; state(); includes(); reward() (вглубь стороны); penalty() (к противоположной); includeNow() (state=n+1); compat: action()==includes, penalize()==penalty, feedbackTypeI(present){present?reward:penalty}, feedbackTypeII(present){present&&includes→penalty; !present&&!includes→includeNow}
  - `TsetlinClause.java` — МОЙ, дублирует логику Trainer → **УДАЛИТЬ**
  - `TsetlinTrainer.java` — их версия с СЕМАНТИЧЕСКИМ БАГОМ экспорта (excluded→neg-mask вместо omit; evaluate игнорирует ¬x) → **ПЕРЕПИСАТЬ**: публичный API сохранить (ctor(int inputBits,int nClauses,int nStates,Random), trainStep(long[] words,boolean pos), trainBatch(long[][],boolean[],int epochs), toClauseSet(String)→ClauseSetForm, clauseCount(), inputBits()); внутри на клаузу пары автоматов pos[j]/neg[j] (init spread как у них: offset=(seed+31j)%n, neg offset+n/2); evaluate: pos-incl&&bit=0→false, neg-incl&&bit=1→false; TypeI(target1,fired): included→reward w.p.(s-1)/s, excluded&&litTrue→penalty w.p.1/s (s фикс 8.0 или поле); TypeII(target0,fired): excluded&&litFalse→includeNow(); export маски: pos-bit=included pos, neg-bit=included neg, omitted иначе
  - `WisardProducer.java` + тесты `Exp002ComparisonTest/Exp003ProducerComparisonTest/TsetlinTest` — их, трогать НЕ надо (после фикса Automaton/Trainer должны компилироваться)
- После правок: `gradlew :matrix-core:test --tests "io.matrix.tsetlin.*"` → зелёный; затем добавить МОЙ TsetlinExportPropertyTest (jqwik: same-seed детерминизм; bounds-fuzz; export≡fires exhaustive k≤6) — S3.2.1/S4.1

## Дальше
- S4.2: прогон tsetlin+bir → commit+push оба remote
- Wave 3 (S5.x): GLOSSARY (Tsetlin automaton/clause/vote), SPEC-002 changelog строка (этап B начат; отклонение: пакет io.matrix.tsetlin в matrix-core, выделение модуля — после анализа CI; FROZEN не тронуты), README строка; WAL rewrite; финальный commit+push
- Затем следующий фронт из WAL «Следующее действие» (Критерий A эпик / EXP-002 пререгистрация)

## Key IDs / факты
- Remote: github.com:AlexanderNarbaev/agi (origin), gitverse.ru:AlexandrNarbaev/agi — оба пушатся
- Инфра: полный gradle test OOM (батчи); битый test-results каталог лечится переносом в /tmp; jqwik генераторы seeded
- Агент-гонки: code-делегации создают параллельные файлы молча (task_9364db4a правил BddForm/тест; кто-то создал tsetlin-рой) — после каждой делегации сверять git status; приоритет прямому коду
- Constraints: FROZEN ethics/CONSTITUTION/avro/workflows; K_MAX≤20; coverage ≥82%; Java-only; seeded-Random только вне рантайм-контура (обучение)

[COMPACTION_COMPLETE]
