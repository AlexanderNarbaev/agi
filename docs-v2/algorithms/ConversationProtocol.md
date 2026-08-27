# Conversation Protocol (Observe → Think → Act цикл)

## Что

Детерминированный такт агента: `AgentLoop` исполняет цикл Observe → Think → Act поверх `AgentBrainService`, набора `DriverState[]`, `TaskScheduler` и пары `Sensor`/`Effector`. Источник: `matrix-core/src/main/java/io/matrix/agent/AgentLoop.java`. Соответствует DESIGN-03 (P-E-D контур) и DESIGN-13 (реестр действий).

## Структура такта

```
tick():
 observation = sensor.read()
 driverSnapshot = [drivers[i].level() for i in drivers]
 actionCode = brain.brain().decide(observation)
 thought = actionCodeToThought(actionCode)
 action = selectAction(thought, driverSnapshot)
 result = effector.execute(action)
 updateDrivers(result)
 return AgentState(observation, thought, action.withResult(result), driverSnapshot, tick)
```

`THOUGHT_BITS = bitsNeeded(AgentAction.ActionType.values().length)` — ширина thought-вектора выводится из размера enum действий; добавление нового `ActionType` автоматически расширяет вектор без правок этого файла (GAP-019 фикс).

## Приоритезация действия

`selectAction(thought, driverLevels)` — три уровня:

1. `DriverType.SAFETY.level() > 0.7` ⇒ `WAIT("reason=safety_driver_high")` (высший приоритет; соответствует CONSTITUTION IV).
2. Активная `Task` есть ⇒ `actionForTask(task, thought)`: `DriverType → ActionType` отображение (ENERGY→EAT, CURIOSITY→EXPLORE, ENTROPY→THINK, SOCIAL/UBUNTU→SPEAK, ATTENTION→OBSERVE, SELFACTUALIZATION→CRAFT).
3. Иначе `actionFromThought(thought)`: первые 4 бита формируют 4-bit код, индексирующий таблицу из 16 действий (WAIT/MOVE/MINE/CRAFT/EAT/TOOL_UP/EXPLORE/OBSERVE/SPEAK/THINK — повторы покрывают 16 комбинаций).

## Сходимость

`checkConvergence(state)` проверяет по порядку:

- любая `Task.status() == COMPLETED` ⇒ `TASK_COMPLETED`;
- любая `Task.status() == FAILED` ⇒ `TASK_FAILED`;
- в окне последних `convergenceThreshold − 1` состояний тот же `ActionType`, что и текущий ⇒ `REPEATING_ACTION` (дефолт threshold = 5);
- иначе `run(maxIterations)` после исчерпания бюджета ⇒ `MAX_ITERATIONS`.

`stop()` форсирует `MANUAL_STOP` через `volatile convergenceReason`. Повторный `run()` на работающем экземпляре ⇒ `IllegalStateException` (`AtomicBoolean.compareAndSet`).

## Асинхронность и наблюдаемость

- `runAsync(maxIterations)` использует `Executors.newVirtualThreadPerTaskExecutor()` (JVM-managed виртуальные потоки).
- `runWithTiming(maxIterations)` оборачивает в `AgentResponse(requestId, answer, sources, TimingInfo)` с фазами retrieval/filtering/generation в наносекундах; `requestId = UUID.randomUUID()`.

## Режимы цикла

`LoopMode` enum: `CLASSIC` (Observe→Think→Act) или `REACT` (Reason+Act+Reflexion через `ReActAgentLoop` и `ReflexionMemory`). Фабрика `create(mode, …)`; `toReActLoop()` создаёт ReAct-вариант с общим `HierarchicalMemory`.

## Метрики / гейты

- `DEFAULT_CONVERGENCE_THRESHOLD = 5` (дефолт для REPEATING_ACTION).
- Юнит `AgentLoopTest`: все 5 `ConvergenceReason` срабатывают; safety-driver override; `run()` идемпотентен при `converged == true`.
- Потокобезопасность: `history` через `Collections.synchronizedList`, `running`/`converged` через `AtomicBoolean`, счётчик через `AtomicLong`.

## Открытые вопросы

- Replay-trace каждого такта в `x-matrix-trace` — частично в `runWithTiming`; полная сериализация `AgentState` не стандартизована.
- Driver-modulation от результата действия — текущая схема (`-0.01` при успехе, `+0.02` при ошибке) захардкожена; нужна конфигурация.

Next: см. файл FederatedMesh.md в той же папке для следующей темы.