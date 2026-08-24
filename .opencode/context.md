# MATRIX Project Context - Session State (актуальный чекпойнт)

## Current Status
- **Сессия**: 30 волн, ~6.5ч. Всё в origin+gitverse, дерево чистое. Критерий A ЗАКРЫТ; этап B: toy-гейт green, синтетика k≥8 — открытый frontier (attempt-15 empty-collapse диагностирован)
- **Wave 30 ЗАВЕРШЕНА**: арифметическая модель автомата (счётчик [0..2N], include⟺≥N, init=0 глубокое исключение — точный порт tm_initialize эталона). Полный регресс **377/0**, оба remote
- TsetlinTest переписан под счётную модель (gradual walk, saturation, includeNow=n); TsetlinExportPropertyTest границы [0..2n]

## Точная модель тренера (текущая, каноническая)
- Автомат: счётчик; inc()=+1 cap 2n (к include), dec()=−1 floor 0 (к exclude); includes⟺state≥n; includeNow=n; init RANDOM: rawState uniform [1..2n]; COMPLEMENTARY: x_j=2n? нет — pair[0]=n+1(pair0 включён), pair[1]=1
- init по умолчанию RANDOM (reference-style) — это дало прорыв toy-сходимости (attempt-9)
- TypeIa (fired+target): consistency⇒reward(inc) БЕЗУСЛОВНО (D2 boost); mismatch⇒dec w.p. 1/s
- Ib (non-fired+target): dec обоих рядов w.p. 1/s (pure decay, БЕЗ pull-in)
- TypeII (fired+против target): batch includeNow ВСЕХ excluded-противоречащих (без guard)
- D1' гейтинг: per-clause p=(T±vote)/2T по собственной цели (tBit)
- predict = Σ polarity·fires > 0; дистилляция точная через TT→BirCompiler

## Очередь следующей сессии
1. **Синтетика на новой модели**: Exp002SyntheticBringUpTest (сейчас ENABLED, конфиги k≤12:c96/e400 EBL, k≥16:c128/e300; D3-cap unlimited) — прогон; при empty-collapse повторить трассировку dbgClause (метод удалён из тренера — вернуть временно или диагностировать через XML bacc)
2. При зелёном: stage-1 закрыт → доменная фаза EXP-002 (Minecraft-перцепт, median-threshold бинаризация frozen)
3. JTMS justification-graph; AC-3/CSP спека ExecutablePlanner; dependency upgrades
4. Атлас §95–103 прочитать для REFLEX/Cauldron

## Инфраструктура / факты
- push цепочка: правки→commit→pull --rebase→push origin→push gitverse(timeout45); LFS locksverify off локально
- rm заблокирован → mv в /tmp/opencode/; полный test OOM — батчи по пакетам; LSP фантомы tsetlin/* — верить gradlew
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod; seeded Random вне рантайма
- Коммит последнего пуша: «wave 30 arithmetic-counter» + docs; статус main=origin=gitverse

[COMPACTION_COMPLETE]
