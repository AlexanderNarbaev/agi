# DESIGN-12. FNL (карантин свежих элементов) и TaskCell (эфемерные задачные инстансы)

- **Статус:** proposed (детализация DESIGN-07 §3/§4; гипотеза H-012 для TaskCell)
- **Уровень:** жизненный цикл (L7 в SYSTEM-SYNTHESIS)
- **Трассировка:** DESIGN-07, DESIGN-05 (журнал), DESIGN-11 (бюджеты), ADR-004/005; H-012 → EXP-012; основания: атлас §34 (Simula — объект переживает вызов; корутины), §40 (инвариант раньше кода), §11 (два сорта знания), §24 («три соединения» — карантин как контакт с внешним критерием)
- **Зависимости:** DESIGN-01/02 (артефакты), DESIGN-05 (M-слои, tombstone), DESIGN-11 (конверты и коридоры)

## 1. Назначение

Два механизма жизненного цикла между рождением (Cauldron) и интеграцией:

1. **FNL (Fresh Neuron Layer)** — реестр недавно рождённых элементов: карантин, теневой прогон, promote/demote через M4-гейты. Реализует структурное разделение «пул верифицированных / кандидаты» (§11): кандидат не получает права влиять на контур, пока не пройдёт гейты.
2. **TaskCell** — эфемерный задачно-специфичный инстанс: детерминированный spawn, полный локальный срез контекста, быстрый точный инференс, итог — в общий пул, смерть по бюджету (H-012).

## 2. Структуры данных

```
FNLEntry                    // запись карантина
  artifactHash: long        // BIR-заголовок (provenance, Φ, хэш)
  origin: enum {CAULDRON, IMPORT_M4, DISTILL, TEACHER}
  shadowStats:              // накопленные метрики теневого прогона
    runs: int, matches: int, disagreements: Disagreement[]
  quarantineBudget: long    // остаток бюджета карантина (тики)
  verdicts: GateVerdict[]   // журнал вердиктов M4-гейтов (ADR-005)
  state: enum {SHADOW, CANDIDATE, PROMOTED, DEMOTED, DEAD}

Disagreement
  inputHash, shadowOut, productionOut, witnessDiff

M4Gate                    // гейт интеграции (экземпляр ADR-005)
  metric, threshold, verdict: accept|reject|undecided, witness

TaskCellSpec              // контракт спауна (входит в configHash)
  taskId, seed
  contextSlice: SliceSpec   // какие слои/домены/артефакты копируются
  budget: long              // смерть по исчерпанию (INV-TC2)
  mergePolicy: enum {VERDICT_ONLY, WITNESS_PLUS_VERDICT}

SliceSpec
  layers: bitset            // M0…M4, какие читаются
  domains: int[]            // FCA-концепты допустимых доменов
  frozenOnly: boolean       // по умолчанию true: только верифицированный пул

TaskCellResult
  verdict, witness, spentBudget, artifactRefs[]
```

## 3. Псевдокод

### 3.1 Конвейер карантина FNL

```
function fnlAdmit(entry: FNLEntry):
    entry.state = SHADOW
    while entry.quarantineBudget > 0 and entry.state == SHADOW:
        x = nextShadowInput(entry.origin)          // детерминированная выборка
        shadowOut = eval(entry.artifact, x)
        prodOut   = eval(productionEquivalent(entry), x)   // действующий пул
        if shadowOut == prodOut: entry.shadowStats.matches++
        else: entry.shadowStats.disagreements += Disagreement(x, ...)
        entry.quarantineBudget--
    // M4-гейты (пороги версионируются в METRICS):
    v1 = gate(disagreeRate(entry) <= THRESH_MATCH)
    v2 = gate(phi(entry.artifact) >= phi(productionEquivalent))   // Φ не хуже
    v3 = gate(frozenCheck(entry.artifact))          // FROZEN-вето
    verdict = conjunctiveKleene(v1, v2, v3)         // UNDECIDED заразителен
    entry.verdicts += verdict
    entry.state = verdict == accept ? CANDIDATE
                : verdict == reject ? DEMOTED : SHADOW   // undecided → досмотр бюджета
    log(FNLEvent(entry, verdict))                   // witness обязателен

function fnlPromote(entry):                         // только из CANDIDATE
    assert all(entry.verdicts.last(k) == accept)    // k последовательных accept
    swapAtomic(productionPool, entry.artifact)      // атомарный своп (как route-drain)
    entry.state = PROMOTED; lineage.link(entry)     // родословная (DESIGN-05)

function fnlDemote(entry):                          // DEMOTED → разбор
    tombstone(entry.artifact, keepVerdicts=true)    // тело забывается, вердикты — нет
    entry.state = DEAD
```

### 3.2 Жизненный цикл TaskCell

```
function spawnCell(spec: TaskCellSpec) -> TaskCell:
    assert spec.budget <= CELLS_BUDGET_CAP          // spawn-overhead <10% (H-012)
    slice = materialize(spec.contextSlice)          // копия, не ссылка (изоляция)
    return TaskCell(spec, slice, localJournal())

function runCell(cell, request) -> TaskCellResult:
    while cell.budget > 0:
        step = brcStep(cell.slice, request)         // тот же BRC-контур, локальный срез
        cell.budget -= cost(step)
        if step.terminal: break
    result = TaskCellResult(step.verdict, step.witness, ...)
    merge(cell.spec.mergePolicy, result)            // в общий пул — только результат
    die(cell)                                       // INV-TC2: смерть по бюджету
    return result

function die(cell):
    tombstone(cell.slice)                           // срез умирает с клеткой
    cell.localJournal.seal()                        // журнал — в M0 событий, не в M2/M3
```

Ключевое свойство: клетка **не имеет каналов записи** в слои общей памяти (INV-TC1); единственный выход — TaskCellResult через merge-политику.

## 4. Инварианты

- **INV-FNL1 (разделение сортов):** элемент в состоянии SHADOW/CANDIDATE не участвует в продакшн-контуре; его вывод недоступен RENDERING (§11).
- **INV-FNL2 (монотонность продвижения):** PROMOTED только после k подряд accept M4-гейтов; единичный reject → DEMOTED без апелляции в том же карантине.
- **INV-FNL3 (FROZEN-вето):** гейт v3 обязателен и не может быть обойдён бюджетом.
- **INV-FNL4 (бюджет карантина):** quarantineBudget ≥ 0; исчерпание при недecided — DEMOTED с вердиктом reject-by-budget (ADR-005).
- **INV-TC1 (нет прямой записи):** клетка не пишет в M2/M3; единственный выход — merge-политика (унаследовано из DESIGN-07, здесь механизм).
- **INV-TC2 (смерть по бюджету):** budget ≤ 0 → die() в том же тике; зомби-клеток не существует (проверяется AT-TC2).
- **INV-TC3 (детерминизм):** (spec, request, seed) → побитово тот же результат и трасса (NFR-04).
- **INV-TC4 (изоляция среза):** slice — материализованная копия; мутации клетки не видны контуру до die() (объект переживает вызов — но не влияет на него, §34).

## 5. Приёмочные тесты

- **AT-FNL1:** кандидат с подсадочным рассогласованием 20% → disagreeRate детектируется, вердикт reject, состояние DEMOTED, событие в журнале.
- **AT-FNL2:** кандидат эквивалентный продакшну (BDD-эквивалентность) → k accept → PROMOTED, атомарный своп без простоя контура.
- **AT-FNL3:** исчерпание quarantineBudget при недecided → reject-by-budget с witness (INV-FNL4).
- **AT-FNL4:** попытка обойти FROZEN-вето (подмена артефакта) → reject-by-proof с контрпримером (INV-FNL3, ADR-005).
- **AT-TC1:** перехват записи клетки в M2/M3 (инструментированные слои) → нарушений нет (INV-TC1).
- **AT-TC2:** запрос с заведомо большим BRC-деревом → смерть ровно при budget=0; срез tombstoned; журнал запечатан в M0.
- **AT-TC3:** 5 сидов × одинаковый (spec, request) → побитовая идентичность результата (INV-TC3).
- **AT-TC4:** мутация среза внутри клетки не наблюдаема в продакшн-слоях до и после die() (INV-TC4).
- **AT-TC5 (H-012):** p99 latency клетки ≤ прямого запроса к кластеру; spawn-overhead <10% бюджета задачи (замер JMH, протокол EXP-012).

## 6. Бюджеты и эксплуатация

- Карантин: shadow-прогоны только в офлайн-окнах/proactive-слотах (NFR-10); k и пороги M4-гейтов — в METRICS.md (версионируются).
- Клетки: максимум параллельных клеток и CELLS_BUDGET_CAP — в BudgetEnvelope (DESIGN-11); гомеостат следит за GATE_REJECT_RATE карантина как метрикой коридора (H-022).
- Журналирование: FNLEvent и TaskCellResult — в журнал событий DESIGN-05; tombstone тел — по общей политике забывания, вердикты переживают тела (lineage).

## 7. Открытые вопросы

- productionEquivalent для доменов без действующего пула (холодный старт): теневой прогон против golden-набора владельца вместо продакшн-эквивалента (пререгистрируется в EXP-012).
- Размер среза SliceSpec против overhead спауна: адаптивная политика — после H-012.
- Кандидаты из IMPORT_M4 (пул Noosphere): двойной карантин (домашний + импортный) — взаимодействие с DESIGN-08, отдельный AT-набор.
