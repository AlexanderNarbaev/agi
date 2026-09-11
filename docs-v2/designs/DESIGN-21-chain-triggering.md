# DESIGN-21 — Chain Triggering: One Chain Activates Another

> Phase P.2 — RUN 337. Решает пробел: в docs-v2 нет механизма «chain A
> триггерит chain B». Существующий `Impulse → AttentionRouter` —
> один поток снизу-вверх, не cross-chain. DESIGN-21 вводит
> `ChainRegistry` с trigger predicates.

## 1. Пробел

Из аудита (subagent-2):

> ⛔ «Chain A triggers chain B» — НЕ специфицировано как механизм.
> Grep на `trigger.{0,30}chain|chain.{0,30}trigger` — zero matches.

Существующие «trigger»-механизмы:
1. `Impulse → AttentionRouter → ActionGate` (S-007, D-19, D-18) — поток
   снизу-вверх, не cross-chain
2. `PlanRunner` исполняет шаги последовательно в одном TaskCell
3. MCTS rollout использует BrcChain на 1-3 шага как action emulator

Отсутствует: «chain-of-reasoning triggering another chain-of-reasoning»,
«subroutine chain triggering side-effect chain», «logical implication
across chains».

## 2. Что такое ChainRegistry

Новый компонент `io.matrix.chain.ChainRegistry`:

```java
public final class ChainRegistry {
    // ChainId → описание цепочки
    private final Map<ChainId, ChainDescriptor> chains;
    // Predicate → target ChainId
    private final List<TriggerRule> rules;

    public ChainId register(ChainDescriptor desc);
    public void unregister(ChainId id);
    public void addRule(TriggerRule rule);
    public void removeRule(TriggerRule rule);

    // Вызывается после каждого enriched-форвард-пасса
    public List<ChainId> evaluateTriggers(ChainEnrichedOutput output);
}
```

### 2.1 ChainDescriptor

```java
public record ChainDescriptor(
    ChainId id,                   // уникальный ID
    String name,                  // "curiosity", "consolidation", "emergency-shutoff"
    BooleanChainRunner chain,     // сама цепочка
    Set<ChainId> dependsOn,       // явные зависимости (для cycle-detection)
    TriggerCondition activation,  // условие активации этой цепочки
    int priority                  // для arbitration при множественных триггерах
) {}
```

### 2.2 TriggerRule

```java
public record TriggerRule(
    TriggerPredicate predicate,   // когда срабатывает
    ChainId target,               // какую цепочку активировать
    int priority,                 // выше = важнее
    String rationale              // человекочитаемое обоснование
) {}

@FunctionalInterface
public interface TriggerPredicate {
    /** Pure function. CONSTITUTION I: no Random, no wall-clock. */
    boolean test(ChainEnrichedOutput output);
}
```

## 3. Конкретные предикаты (стартовый набор)

```java
// 1. Curiosity: высокая novelty + низкая certainty → запустить exploration
TriggerRule noveltyCuriosity = new TriggerRule(
    output -> {
        double avgNovelty = mean(output.chemicalPerLayer()[/* novel idx */]);
        double avgCertainty = mean(output.chemicalPerLayer()[/* certainty idx */]);
        return avgNovelty > 0.7 && avgCertainty < 0.4;
    },
    ChainId.of("curiosity-impulse"),
    priority = 100,
    rationale = "Novelty > 0.7 AND certainty < 0.4 → uncertain novelty needs exploration"
);

// 2. Consolidation: долгая работа без consolidation → запустить TR-phase
TriggerRule consolidationTrigger = new TriggerRule(
    output -> /* check time-since-last-consolidation */ false,
    ChainId.of("consolidation"),
    priority = 50,
    rationale = "Periodic TR-phase during sleep cycle"
);

// 3. FROZEN-shutoff: ethics violation → экстренная остановка
TriggerRule frozenShutoff = new TriggerRule(
    output -> /* check ethical-filter */ false,
    ChainId.of("emergency-shutoff"),
    priority = 1000,
    rationale = "FROZEN-gate violation → immediate shutoff"
);
```

## 4. Активация downstream chain

После `BooleanChainRunner.evaluateEnriched()` возвращает `ChainEnrichedOutput`:

```
1. Forward pass on chain-A
2. enrichedOutput = chainA.evaluateEnriched(input)
3. triggeredChainIds = registry.evaluateTriggers(enrichedOutput)
4. For each triggeredChainId (по priority descending):
     a. downstream = registry.get(triggeredChainId)
     b. newInput = transform(enrichedOutput)   // default: enrichedOutput.bits
     c. downstreamOut = downstream.chain.evaluateEnriched(newInput)
     d. log to audit (HashChain) — кто кого запустил
5. Continue with primary output (chain-A bits)
```

**Цикл-detection**: `ChainDescriptor.dependsOn` строится явно; рекурсия
depth-limited (default 5, configurable). Cycle → immediate abort +
audit log + DEMOTE rule (DESIGN-12 §INV-FNL3).

## 5. Интеграция с существующим кодом

- `BooleanChainRunner.evaluateEnriched()` (DESIGN-20) — вызывающий код
- `TriggerEvaluator` — новый класс, обёртка над `ChainRegistry.evaluateTriggers`
- `BrainLoopService` — точка вставки (после каждого forward pass)
- `PlanRunner` (DESIGN-13) — расширение: `PlanStep` теперь может ссылаться
  на `ChainId` (action = "run this chain")
- `ActionArena` (DESIGN-17) — каскадные actions теперь first-class

## 6. CONSTITUTION compliance

| Article | Compliance |
|---|---|
| I (determinism) | ✅ predicates — pure functions, evaluation order по priority детерминирован |
| II (K_MAX=20) | ✅ predicates оперируют над агрегатами, не над bit positions |
| III (FROZEN) | ✅ FROZEN-shutoff trigger — обязательный |
| IV (prohibitions) | ✅ triggers не могут обойти этику |
| V (coverage) | требует тесты |
| VI (no forbidden claims) | ✅ это не «emergence», это explicit predicate |
| VII (audit) | ✅ каждая активация chain-X-from-Y логируется в HashChain |
| VIII (substrate-neutrality) | ✅ JVM-only |

## 7. Что НЕ входит

- ❌ Автоматическое обнаружение триггеров (всё явно через registry)
- ❌ ML-обучение предикатов (отдельный design)
- ❌ Динамическое создание chains в runtime (FROZEN-zones запрещают)
- ❌ Cross-process trigger (отдельный design — DESIGN-08 federation)

## 8. Acceptance Criteria

| # | Criterion | Evidence |
|---|---|---|
| 1 | `ChainRegistry.register/unregister/addRule` работают | unit tests |
| 2 | `evaluateTriggers(output)` возвращает правильные ChainId | 3-rule test |
| 3 | FROZEN-shutoff срабатывает первым (priority 1000) | ordering test |
| 4 | Cycle detection (A→B→A) aborts + logs | cycle test |
| 5 | Audit trail каждой cross-chain активации | HashChain inspection |
| 6 | End-to-end: chain-A forward → trigger → chain-B forward → output | E2E test |

## 9. Implementation Plan (Phase R.1-R.3, RUN 340-342)

```
io/matrix/chain/
  ChainId.java             // UUID wrapper
  ChainDescriptor.java     // record
  TriggerRule.java         // record
  TriggerPredicate.java    // functional interface
  ChainRegistry.java       // register + evaluateTriggers
  TriggerEvaluator.java    // вызывается из BrainLoopService
```

## 10. References

- CONSTITUTION.md Art. I, III, IV, VII
- DESIGN-01-units.md
- DESIGN-13-action-registry.md (PlanStep расширение)
- DESIGN-17-action-arena.md (cascade actions)
- DESIGN-18-consciousness-loop.md (AttentionRouter — parallel concept)
- DESIGN-20-enriched-neurons.md (predicates operate over enriched output)
