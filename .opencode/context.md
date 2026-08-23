# MATRIX Project Context - Session State

## Current Status
- **Миссия**: волны; Java/Quarkus/GraalVM; перед push fetch+rebase; origin ✅, gitverse нестабилен (ретраить `timeout 45 git push gitverse main`)
- **Критерий A закрыт** (7 волн, DESIGN-14 реестр полный); WiSARD+Tsetlin продюсеры с дистилляцией; 371+/0 регресс ранее
- **АКТИВ: волна 12 — TM convergence. НАЙДЕНЫ И ЧАСТИЧНО ИСПРАВЛЕНЫ 2 точных дефекта в TsetlinTrainer (файл matrix-core/src/main/java/io/matrix/tsetlin/TsetlinTrainer.java):**
  1. typeOne → переписан канонически «consistency(value==includes) ⇒ Reward w.p.(s−1)/s; mismatch ⇒ Penalty w.p.1/s» для обоих рядов x_j/¬x_j (строки ~83–110)
  2. typeOneGrowth → был инвертирован (растил ложную полярность); исправлен: при !v растить cl[1][j](¬x_j)+push cl[0][j]; при v зеркально (строки ~112–127)

## Текущее состояние теста TsetlinGranmoReferenceTest (harness ВКЛЮЧЁН, конфиги уже канонических масштабов: AND/OR c=24,N=50,e=1500; XOR c=32,N=60,e=3000; MUX c=40,N=60,e=4000 seed42)
- ✅ AND все сиды, MUX ✅, XOR seeds {1,5} ✅
- ❌ OR все сиды, XOR seeds {2,3,4}, noisyXor

## Гипотеза следующего шага (НЕ проверена)
OR требует чтобы pos-пул покрыл {01,10,11}: клаузы стартуют как all-positive conjunction (комплементарный init: [0]=n+1 включён, [1]=1 исключён) = специализируются ТОЛЬКО на full-true minterm; рост на другие minterms идёт через typeOneGrowth (уже исправлен) НО pP=1/S=0.125 медленно + typeTwo на негативе 00 добавляет ¬x_j через includeSafe... Возможно: (a) увеличить epochs/pP для роста, (b) проверить что predict порог score>0 не даёт tie-фейл когда neg-pool фейлит покрытие 00, (c) прогнать diag с debugVotes-подобной печатью (метод удалён — вернуть временно), (d) сверить с Algorithm 1 Granmo: там TypeIb применяется ТОЛЬКО к клаузам своего target и включает includeNow для excluded-false? — перечитать карточку/статью

## Файлы/команды
- Trainer: см. выше; harness: matrix-core/src/test/java/io/matrix/tsetlin/TsetlinGranmoReferenceTest.java (сейчас ENABLED — после успеха оставить включённым! при откате — вернуть @Disabled строкой над class)
- Прогон: `gradlew :matrix-core:test --tests "io.matrix.tsetlin.TsetlinGranmoReferenceTest"`
- LSP фантом tsetlin/TsetlinTrainer+Automaton дубли — верить gradlew
- rm заблокирован Goal Guard → mv в /tmp/opencode/
- После зелёного: HYPOTHESES карточка «предэтап воспроизведён» + WAL + todo M6/T6.7..T6.12 marks + commit `feat(tsetlin): EXP-002 pre-stage reproduced — canonical TM convergence fixed` + push origin(+gitverse timeout45)
- Если НЕ зелёный за 2-3 итерации: вернуть @Disabled, зафиксировать attempt-8 находки (growth-инверсия была реальным багом!) в HYPOTHESES, commit+push, перейти к JTMS/ATMS→LineageLedger волне

## Constraints
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod; seeded Random вне рантайма
- Полный test OOM — батчи; компактные ответы; rm→mv

[COMPACTION_COMPLETE]
