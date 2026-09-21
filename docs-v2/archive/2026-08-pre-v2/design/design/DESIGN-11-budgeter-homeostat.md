# DESIGN-11. Сопряжённый бюджетер рядов Cauldron и гомеостат коридоров

- **Статус:** proposed (ставка H-021/H-022; базовое расписание Левина — принято, ADR-004)
- **Уровень:** жизненный цикл / контур (L7 + L6 в SYSTEM-SYNTHESIS)
- **Трассировка:** ADR-004; H-019 → EXP-019, H-021 → EXP-021, H-022 → EXP-022; основания: атлас §27 (Левин), §36 (Понтрягин, Беллман, Винер); FR-01/07, NFR-01/04/07/10/13
- **Зависимости:** DESIGN-07 (Cauldron, ряды, Φ-гейт), DESIGN-03 (контур, witness), DESIGN-05 (журнал событий)

## 1. Назначение

Два связанных механизма:

1. **ConjugateBudgeter** — планировщик бюджетов рядов Cauldron: распределяет энергетический конверт цикла по рядам и очередям кандидатов. База — расписание Левина; ставка — сопряжённый DP с теневыми ценами.
2. **Homeostat** — контур отрицательной обратной связи над метриками узла: удерживает систему в версионируемых коридорах минимальными коррекциями из закрытого меню.

Оба механизма детерминированы (seed в configHash), работают только в офлайн-окнах и proactive-слотах (NFR-10), каждое решение пишется в witness (FR-03).

## 2. Структуры данных

```
BudgetEnvelope          // энергетический конверт цикла Cauldron
  totalJ: long          // джоули на цикл (замер энергосчётчиком, NFR-01)
  rowsMax: int          // число рядов цикла
  perRowCap: double     // потолок доли ряда (NFR-07: никакой ряд не блокирует)

RowState                // сводка состояния ряда (фиксируется до запуска, EXP-021)
  rowId: int
  descLen: int          // l — длина описания кандидатов ряда (биты)
  covered: long         // сколько эталонного множества уже покрыто
  remaining: long       // оценка остатка
  candCostJ: double     // средняя цена оценки кандидата (джоули)
  checkCost: double     // средняя стоимость проверки кандидата

KtScore                 // скоринг кандидата внутри ряда (H-019)
  descLen + log2(checkCost)

ShadowPrice             // ψ(s) — теневая цена состояния ряда (Понтрягин)
  rowId, dValue_dState  // производная финального покрытия по состоянию

ValueTable              // V(s) — таблица ценности (Беллман), малое пространство состояний
  states: RowState[] (квантованные), values: double[]

Corridor                // коридор метрики (версионируемый)
  metric: enum {ENERGY_PER_TICK, COVERAGE, UNDECIDED_RATE, GATE_REJECT_RATE}
  lo, hi: double
  version: int          // входит в configHash

CorrectionAction        // элемент закрытого меню коррекций
  enum {REBALANCE_ROWS, DOWNGRADE_FRAGMENT, SWITCH_SCHEDULE, NOOP_TELEMETRY_FIX}
  params: long[]        // детерминированные параметры

CorrectionEvent         // witness коррекции (ADR-005: вердикт + основание)
  metric, deviation, action, params, resultAfter, seed, ts
```

## 3. Псевдокод

### 3.1 Базовое расписание Левина (принято)

```
function levinSchedule(rows: RowState[], env: BudgetEnvelope) -> Plan:
    for r in rows:
        w[r] = 2^(-r.descLen)                      // доля Левина
    normalize(w)                                    // Σw = 1
    for r in rows:
        budget[r] = min(w[r], env.perRowCap) * env.totalJ
    redistribute_excess()                           // остаток — следующим по w
    for r in rows:
        queue[r] = sort(candidates[r], by=KtScore)  // Kt-очередь внутри ряда
    return Plan(budget, queue)                      // детерминирован по seed
```

### 3.2 Сопряжённый бюджетер (ставка H-021)

```
function conjugatePlan(rows: RowState[], env: BudgetEnvelope, V: ValueTable) -> Plan:
    // Backward value iteration (Беллман): от конца конверта к началу
    for t = T-1 downto 0:                           // T — квантование конверта
        for s in quantize(rows):
            V[t][s] = max over actions a:           // a — выделить бюджет ряду r
                reward(s, a) + V[t+1][next(s, a)]   // reward = Δпокрытия/джоуль
    // Теневые цены (Понтрягин): ψ = ∂V/∂s
    psi = diff(V[0], by=state)
    // Approximate DP: ветви действий обрезаны расписанием Левина (H-019)
    actions = prune(actions, by=levinWeights)
    plan = greedy_on(V[0], psi)                     // выбор по max ψ·Δпокрытия
    // Bang-bang дисциплина: при линейной стоимости — на границе конверта,
    // каждое переключение режима → событие в журнал (аудит, §36)
    log_switches(plan)
    return plan
```

Сложность: |S| = Π квантований RowState — малым (≤10⁴ состояний по постановке EXP-021); обрезание Левина удерживает ветвление ≤ O(rowsMax). Память: ValueTable ≤ 1 МБ. Фолбэк: при нестабильности V (дисперсия ψ между окнами > порога из METRICS) — откат на levinSchedule (зафиксирован в ADR-004).

### 3.3 Гомеостат (ставка H-022)

```
function homeostatTick(metrics: MetricSample[], corridors: Corridor[]) -> CorrectionEvent?:
    // Дисциплина телеметрии (Винер): сначала целостность сигнала
    if !telemetryConsistent(metrics):
        return CorrectionEvent(NOOP_TELEMETRY_FIX, ...)   // чиним сигнал, не механизм
    for c in corridors:
        m = sample(c.metric)
        if m < c.lo or m > c.hi:
            deviation = min(|m - c.lo|, |m - c.hi|) signed
            action = selectMinimal(correctionMenu, c.metric, deviation)
            // минимальная достаточная коррекция из закрытого меню
            apply(action)
            return CorrectionEvent(c.metric, deviation, action, ...)
    return null                                            // всё в коридорах
```

Меню коррекций (закрытое, версионируется с коридорами):
- `REBALANCE_ROWS` — пересчёт плана бюджетером с текущими RowState;
- `DOWNGRADE_FRAGMENT` — понижение логического фрагмента домена (карта разрешимости, §8) — снижает цену проверок ценой выразительности;
- `SWITCH_SCHEDULE` — смена расписания (Левин ↔ равномерное ↔ жадное) по таблице профилей;
- `NOOP_TELEMETRY_FIX` — коррекция канала телеметрии без троганья механизма.

Частота: не чаще одной коррекции на тик; квота коррекций на цикл — в BudgetEnvelope (антикаскад).

## 4. Инварианты

- **INV-CB1 (бюджет):** Σ budget[r] ≤ env.totalJ; ни один ряд не превышает perRowCap (NFR-07).
- **INV-CB2 (детерминизм):** одинаковые (RowState, env, seed) → побитово одинаковый Plan (NFR-04).
- **INV-CB3 (непрерывность покрытия):** смена плана не удаляет уже покрытые клаузы; план затрагивает только будущие выделения.
- **INV-CB4 (фолбэк):** при отсутствии/нестабильности ValueTable действует levinSchedule; переход логируется как CorrectionEvent.
- **INV-HS1 (закрытость меню):** применяются только действия из версионируемого меню; новое действие — только через ADR (Статья VI).
- **INV-HS2 (антикаскад):** ≤1 коррекции на тик; после коррекции — карантинный тик наблюдения.
- **INV-HS3 (телеметрия прежде механизма):** при несогласованных метриках запрещены коррекции механизмов (только NOOP_TELEMETRY_FIX).
- **INV-HS4 (witness):** каждая коррекция — CorrectionEvent с metric/deviation/action/result (FR-03, ADR-005).

## 5. Приёмочные тесты

- **AT-CB1:** случайные RowState/env (10⁴ сэмплов, 5 сидов) → INV-CB1/2 на каждом плане.
- **AT-CB2:** на синтетическом домене с известным эталоном — план Левина покрывает ≥ равномерного при равном бюджете (прогон EXP-019 как тест после accepted).
- **AT-CB3:** фолбэк: повреждённая ValueTable → план ≡ levinSchedule, событие INV-CB4 в журнале.
- **AT-CB4:** bang-bang: линейная стоимость → все выделения на границах; каждое переключение залогировано.
- **AT-HS1:** инъекция отклонения метрики (UNDECIDED_RATE выше hi) → ровно одна коррекция, правильный тип, witness полон.
- **AT-HS2:** каскад-инъекция (3 метрики вне коридоров) → ≤1 коррекции/тик, карантинный тик соблюдён (INV-HS2).
- **AT-HS3:** повреждённая телеметрия (метрики противоречат друг другу) → только NOOP_TELEMETRY_FIX (INV-HS3).
- **AT-HS4:** попытка применить действие вне меню → отказ + событие нарушения (INV-HS1, reject-by-proof, ADR-005).

## 6. Бюджеты и эксплуатация

- Бюджетер: вызов ≤1 раза на цикл Cauldron; время планирования ≤ 1% конверта (иначе фолбэк на Левина).
- Гомеостат: тик в proactive-слотах; хранение — последние N CorrectionEvent (журнал DESIGN-05, политика забывания сырья сохраняет вердикты).
- Метрики и пороги фолбэка — в METRICS.md (версионируются); коридоры входят в configHash запуска (воспроизводимость EXP-022).

## 7. Открытые вопросы (не блокируют реализацию базы)

- Квантование RowState для ValueTable: начальная сетка фиксируется в EXP-021 пререгистрации; адаптивное квантование — отдельная ставка, не ядро.
- Оценка `remaining` в RowState на живых доменах: эвристика по истории циклов; при нестабильности — режим только Левина.
- Связка с LTL-контуром (H-017): INV-HS1…4 кандидаты в темпоральные инварианты model checking'а.
