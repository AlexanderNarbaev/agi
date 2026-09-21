# DESIGN-59 — Neural Cellular Automata Brain

**Status:** v1 design. Wave W31.

## 1. Что такое Neural Cellular Automaton?

В классическом клеточном автомате (Game of Life, Rule 30) **правило фиксированно**
(e.g. "Game of Life: B3/S23"). В **Neural CA** (Mordvintsev et al., 2020) правило — это
**маленькая нейросеть** (3-layer CNN для 16-канальной клетки), одинаковая для всех клеток,
обучена через backpropagation.

Результат: emergent self-organization. NCA может:
- **Расти** к target image.
- **Восстанавливаться** к target после повреждения (регенерация).
- **Устойчиво персистировать** (стабильное состояние).
- **Классифицировать** вход (Self-classifying MNIST NCA, Randazzo & Mordvintsev 2020).

## 2. Почему это концептуально близко к нашему Boolean brain

У нас есть:
- Boolean таблицы (наши «клетки»).
- SpikingCell массовая параллельность (наши «соединения»).
- `MultiChainEnsemble` (много независимых chains; NCA — много независимых cells).
- `KauffmanNetwork`, `GillespieSimulator` (Boolean dynamics; NCA — мягкая neural dynamics).
- Чтение через Hamming-distance lookup (parallel local reads).

Главное отличие:
- Классический NCA: network cells update by **маленькое CNN**.
- Наш brain: cells update by **маленькие Boolean-таблицы**.

Это существенно: Boolean-таблицы **более interpretable** чем CNN.

## 3. Архитектура

```
GRID of cells (2D initially, scalable to 3D, eventually to 1D sequence)
Each cell:
    state: 64-bit vector (currently we use TruthTable[k] for k=6 → 64 bits)
    neighbors: 8 adjacent cells (Moore neighborhood)
    update_rule: small TruthTable XOR chain → next state
    
Loop until convergence:
    for each cell:
        aggregate neighbors state via XOR-bundling
        apply update_rule(own_state, aggregated_neighbors)
        write new state atomically (asynchronous stochastic update)

Loss: Hamming distance between final grid pattern and target pattern.
Backprop: ???
```

**Проблема:** backprop через bit-tables не работает naturally. Используем **continuous relaxation** (Payani & Fekri):
1. Maintain continuous relaxation of bit-table (`μ ∈ [0,1]` per output).
2. Forward pass through relaxed network.
3. Quantize to bits for output.
4. Backward pass through relaxation: dL/dμ → dL/dtable via chain rule.
5. Update relaxed tables (LR=1e-4, Adam).

## 4. Как использовать существующие классы

| Класс | Применение |
|---|---|
| `KauffmanNetwork` | По сути, низко-уровневый NCA с random Boolean update; для baselines. |
| `GillespieSimulator` | Stochastic asynchronous update — альтернатива к Machine NCA. |
| `ConwayGameOfLife` | Classic CA нашего проекта — baseline. |
| `GrayScottSimulator` | Continuous-CA variant в нашем пространстве. |
| `BooleanChainRunner` | Каждая cell's update rule — это chain. |
| `HammingNative` | Быстрый Hamming distance между grid states. |
| `KdTree` | Cleanup memory для grid-pattern matches. |
| `TokenBucket` | Asynchronous update throttling. |
| `SpelkeCoreKnowledge` (DESIGN-58) | Тесты на object permanence и т.д. |

## 5. Spelke + NCA = NCA tests on Spelke tasks

Object permanence (DESIGN-58 Level 2) — natural fit for NCA:
- Grid = visual field.
- Cell state = pixel/object activation.
- Hidden object = temporarily occluded cell.
- Persistence of activation = object permanence.

**Бенчмарк:** train NCA on 100 grids where an object is visible then hidden and reappears. Test on held-out grids.

## 6. Routing and Sparse Activation

NCA cells are expensive: each step = neural network forward on every cell. To make it scale:

- **Sparse activation**: only update cells with state magnitude > threshold.
- **Patch-based NCA**: process grid patches not pixels.
- **Gradient checkpointing**: trade memory for compute.

For Boolean-style: **skip updates on cells where neighbor XOR is same as last step** (idempotent updates — no-op anyway).

## 7. Применение to brain simulator

Brain simulator's `EnrichedChainEvaluator` уже передаёт state forward through 24 blocks. Мы можем рассматривать каждый block как **causal** шаг NCA. **Alternative:** brain simulator с NCA-style internal updates.

```
class NcaBlock {
    TruthTable update_rule(TruthTable own_state, List<TruthTable> neighbor_states);
    void tick() {
        for cell in cells:
            cell.state = update_rule(cell.state, neighbors(cell))
    }
}
```

If each NCA tick == 1 forward pass through 24-block chain, then brain's forward pass
is essentially NCA-style update.

## 8. RUN-цель

- `NcaCell.java` — single cell update rule (TruthTable-based).
- `NcaGrid.java` — grid of cells, tick, async update, sparse activation.
- `ContinuousRelaxation.java` — continuous relaxation via Payani & Fekri.
- `NcaBrainSimulator.java` — integration с brain simulator.

## 9. Что НЕ делать

- **Не делать NCA как замену brain simulator'у** — это компонент.
- **Не гоняться за image quality metrics** (PSNR, SSIM) — это graphics, не AI.
- **Не использовать GPU даже если предложат** — наш CPU-bound дизайн принципиален.

## 10. Why NCA matters for our goal

Emergent computation from local rules: **if we can demonstrate that a population of small
Boolean tables, applying local XOR-bundling rules, can recover target grid patterns** —
then we've shown that **large-scale emergent behavior comes from simple local rules**.
This is the scientific claim of the project.

Documentation of this — at the right scale (10K cells, 100 update steps, target grid pattern
recognition) — is **publishable in an ALIFE or Cognitive Science workshop**.

