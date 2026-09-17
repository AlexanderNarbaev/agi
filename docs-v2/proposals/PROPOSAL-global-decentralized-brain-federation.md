# PROPOSAL — Global Decentralized Brain Federation (GDBF)

**Статус:** proposal · draft v0.1  
**Дата:** 2026-09-17  
**Автор:** пользователь + MATRIX core team  
**Связанные документы:** CONSTITUTION.md, DESIGN-58, REQUEST-autonomy-impulses.md, REQUEST-decentralized-digests.md, DESIGN-08-federation.md, docs-v2/science/SUBSTRATE-MODELS.md (Cauldron)

---

## 🎯 Executive Summary

**Проблема:** Современная LLM-парадигма (GPU × статистика × центральные датацентры) упрётся в:
1. **Энергетический потолок** — тренировка GPT-уровня = углеродный след небольшой страны
2. **Вычислительный дефицит** — GPU не хватит на всех пользователей
3. **Персонализация vs приватность** — либо данные уходят в центр, либо модель остаётся глупой
4. **Хрупкость** — центральный сервис = single point of failure

**Решение:** Гибридная федерация локальных «цифровых мозгов» на домашних устройствах (CPU+GPU), которые:
- **Локально персонализированы** — учатся на ваших данных без отправки в центр
- **Глобально связаны** — обмениваются анонимизированными дайджестами знаний (noosphere M5)
- **Самоорганизуются** — Cauldron-протокол создаёт новые нейроны/навыки автономно
- **Биохимически модулированы** — гормоны/нейромедиаторы как управляющие сигналы между блоками
- **Этически фиксированы** — FROZEN-запреты (CONSTITUTION IV) не обходятся ни при каких условиях

**Аппаратная база (референс):**
```
AMD Ryzen 9 9955HX (32 ядра) + RTX 5070 Ti (12GB) + 64GB RAM + 2TB NVMe
→ целевая производительность: 100ms inference, 1M patterns/sec learning
```

---

## 🏗️ Архитектурное Видение

### 1. Трёхуровневая топология

```
┌─────────────────────────────────────────────────────────────┐
│                    GLOBAL NOOSPHERE (M5)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │   Federal    │  │   Research   │  │  Corporate    │       │
│  │   Hub #1     │  │   Cluster    │  │   Gateway     │       │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘       │
│         │                 │                 │                │
│         └─────────────────┼─────────────────┘                │
│                           ▼                                  │
│              ┌────────────────────────┐                      │
│              │  ELSP-signed Gossip    │                      │
│              │  (CRDT, k-anonymous)   │                      │
│              └────────────────────────┘                      │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ anonymized digests only
                            │ (no PII, DP-noised)
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│  HOME NODE #1 │   │  HOME NODE #2 │   │  EDGE DEVICE  │
│  (Ryzen+RTX)  │   │  (Intel+iGPU) │   │  (ESP32/RPi)  │
│               │   │               │   │               │
│ ┌───────────┐ │   │ ┌───────────┐ │   │ ┌───────────┐ │
│ │ Neuron    │ │   │ │ Neuron    │ │   │ │ Neuron    │ │
│ │ Block A   │ │   │ │ Block A   │ │   │ │ Block A   │ │
│ │ (Dialog)  │ │   │ │ (Dialog)  │ │   │ │ (Sensor)  │ │
│ └───────────┘ │   │ └───────────┘ │   │ └───────────┘ │
│ ┌───────────┐ │   │ ┌───────────┐ │   │ ┌───────────┐ │
│ │ Neuron    │ │   │ │ Neuron    │ │   │ │ Mediator  │ │
│ │ Block B   │ │   │ │ Block B   │ │   │ │ (Hormone) │ │
│ │ (Vision)  │ │   │ │ (Vision)  │ │   │ └───────────┘ │
│ └───────────┘ │   │ └───────────┘ │   │               │
│ ┌───────────┐ │   │ ┌───────────┐ │   │               │
│ │ Mediator  │ │   │ │ Anonymizer│ │   │               │
│ │ (Dopamine)│ │   │ │ (k-anon)  │ │   │               │
│ └───────────┘ │   │ └───────────┘ │   │               │
│ ┌───────────┐ │   │ ┌───────────┐ │   │               │
│ │ Cauldron  │ │   │ │ Cauldron  │ │   │               │
│ │ (Φ-gate)  │ │   │ │ (Φ-gate)  │ │   │               │
│ └───────────┘ │   │ └───────────┘ │   │               │
│ ┌───────────┐ │   │ ┌───────────┐ │   │               │
│ │ M0-M4     │ │   │ │ M0-M4     │ │   │               │
│ │ Memory    │ │   │ │ Memory    │ │   │               │
│ └───────────┘ │   │ └───────────┘ │   │               │
└───────────────┘   └───────────────┘   └───────────────┘
```

**Уровни:**
1. **Edge (L0-L2)** — ESP32, Raspberry Pi, smart home: сенсоры, простые рефлексы
2. **Home (L3-L5)** — ноутбуки, десктопы, домашние серверы: полноценный мозг с памятью M0-M4
3. **Federal (L6-L7)** — облачные кластеры, корпоративные шлюзы: агрегация дайджестов, координация

### 2. Биохимическая Модуляция (Biochemical Modulation Layer)

**Концепция:** Каждый блок мозга (Neuron Block) получает **управляющие сигналы**, аналогичные гормонам/нейромедиаторам:

| «Гормон» | Функция | Источник | Мишени | Аналог в коде |
|----------|---------|----------|--------|---------------|
| **Dopamine** | reward prediction error | Mediator | все neuron blocks | `PredictionError` → Hebbian update |
| **Serotonin** | mood/stability baseline | Mediator | arousal dynamics | `ArousalDynamics.BASELINE` |
| **Norepinephrine** | vigilance/novelty | Mediator | attention gates | `CycleAdmission.noveltyThreshold` |
| **Acetylcholine** | learning rate modulation | Mediator | plasticity | `HebbianUpdater.eta` |
| **Cortisol** | stress/urgency signal | External events | resource budget | `ConjugateBudgeter.allocation` |
| **Oxytocin** | trust/social bonding | Peer digests | federation gate | `Anonymizer.trustScore` |
| **Endorphin** | pain relief/completion | Goal attainment | consolidation cycle | `ConsolidationCycle.trigger` |

**Реализация:**
```java
// mediator/BiochemicalSignal.java
public enum SignalType {
    DOPAMINE(0x01, "reward"),
    SEROTONIN(0x02, "stability"),
    NOREPINEPHRINE(0x03, "vigilance"),
    ACETYLCHOLINE(0x04, "plasticity"),
    CORTISOL(0x05, "stress"),
    OXYTOCIN(0x06, "trust"),
    ENDORPHIN(0x07, "completion");
    
    private final byte code;
    private final String function;
}

// mediator/Mediator.java
public class Mediator {
    private final Map<SignalType, Double> concentrations = new EnumMap<>(...);
    
    public void emit(SignalType type, double concentration) {
        // Broadcast to all registered neuron blocks
        for (NeuronBlock block : blocks) {
            block.receiveSignal(type, concentration);
        }
    }
}

// brain/NeuronBlock.java
public interface NeuronBlock {
    void receiveSignal(SignalType type, double concentration);
    // Each block implements its own response logic
}
```

**Преимущества:**
- **Типовые блоки** — один класс `NeuronBlock`, разные реакции на сигналы
- **Динамическая конфигурация** — концентрации меняются в рантайме
- **Эмерджентное поведение** — сложные паттерны из простых правил
- **Биологическая правдоподобность** — вдохновлено mammalian brain

### 3. Cauldron Protocol (Автономное Саморазвитие)

**Из SUBSTRATE-MODELS.md (§8.1 МГУА Ивахненко):**

```
Cauldron — это конвейер самоорганизации:
1. Ряды нарастающей сложности (k=2,3,4,... входов)
2. Внешний критерий (Φ-гейт) оценивает кандидатов
3. Лучшие promote в PROMOTED, худшие demote в SHADOW
4. Lineage фиксирует происхождение каждого артефакта
```

**Расширение для GDBF:**
- **Локальные Cauldrons** — каждый узел растит свои нейроны
- **Федеративный Φ** — дайджесты лучших артефактов публикуются в noosphere
- **Peer validation** — другие узлы проверяют Φ-оценку перед adoption
- **Consensus growth** — если >66% узлов приняли артефакт, он становится global primitive

**Алгоритм:**
```text
LOOP (every τ_cauldron):
  FOR each row r IN rows[1..R]:
    Generate candidates C_r (mutations of PROMOTED from r-1)
    Evaluate Φ(c) for c IN C_r on held-out window
    SELECT top-K by Φ → PROMOTATED
    DEMOTE rest → SHADOW (or delete if Φ < Φ_min)
    
  IF ΔΦ_global < ε FOR 3 cycles:
    STOP growth (convergence)
  ELSE:
    PUBLISH digest(PROMOTED) to noosphere
    RECEIVE peer_digests
    ADOPT if Φ_peer > Φ_local AND consensus ≥ 0.66
```

---

## 🔐 Безопасность и Консенсус

### 1. Уровни Доверия (Trust Tiers)

| Tier | Описание | Требования | Права |
|------|----------|------------|-------|
| **T0: Untrusted** | новый пир, нет истории | ELSP-подпись обязательна | только gossip receive |
| **T1: Observed** | 10+ валидных дайджестов | k-anon + DP проверены | limited query |
| **T2: Validated** | 100+ дайджестов, 0 violations | reputation score > 0.8 | full query, vote on consensus |
| **T3: Guardian** | выбран консенсусом | multi-sig, audit trail | freeze rogue nodes, protocol upgrades |

### 2. Протокол Consensus Digest Adoption

```text
Предложение: узел A публикует digest(artifact_X, Φ=0.92)

Фаза 1: Validation (τ₁ = 10s)
  - Узлы B,C,D,... проверяют:
    • ELSP подпись (ML-DSA v2)
    • k-anonymity (bucket size ≥ K_MIN)
    • DP noise (ε ≤ ε_BUDGET)
    • Φ вычисление (reproducible на seed)

Фаза 2: Voting (τ₂ = 30s)
  - T2+ узлы голосуют: ACCEPT / REJECT / ABSTAIN
  - Требуется ≥66% ACCEPT от voting weight

Фаза 3: Adoption (τ₃ = 60s)
  - Если consensus reached:
    • artifact_X добавляется в global primitives library
    • узел A получает reputation boost
  - Иначе:
    • digest помечается REJECTED
    • узел A получает minor reputation penalty (если false positive)
```

### 3. Recovery после Partition/Disconnect

**Сценарий:** узел был офлайн 24 часа, пропустил 1000 дайджестов

**Автоматическое восстановление:**
1. **State Snapshot** — последняя сохранённая версия M0-M4 + lineage
2. **Delta Sync** — запросить у соседей дайджесты за период (CRDT LWW)
3. **Conflict Resolution** — если конфликты (редко):
   - Φ-приоритет (выше Φ побеждает)
   - Reputation tie-breaker
   - Manual operator override (крайний случай)
4. **Integrity Check** — HashChain аудит, Ricci-fingerprint diff
5. **Resume Normal Operation** — продолжение цикла

---

## 🧠 Когнитивная Архитектура (расширение DESIGN-58)

### Уровни Автономности (Capability Levels 0-7 + L8-L10)

| Уровень | Название | Описание | Статус |
|---------|----------|----------|--------|
| **L0** | Fabric | 78 алгоритмов + brain simulator | ✅ DONE |
| **L1** | Pavlov | стимул-реакция, habituation | ✅ DONE |
| **L2** | Spelke | core knowledge (объекты, агенты, числа) | ✅ DONE |
| **L3** | Cross-modal | audio↔visual binding | ✅ DONE |
| **L4** | Sensorimotor | действие-результат prediction | ✅ DONE |
| **L5** | Symbol grounding | слова ↔ объекты | ✅ DONE |
| **L6** | Compositional | 2-hop推理 X→Y→Z | ✅ DONE |
| **L7** | Conscious | Φ-метрики, self-model, wu-wei | ✅ DONE |
| **L8** | **Social** | **theory of mind, peer modeling** | 🟡 PROPOSED |
| **L9** | **Collective** | **swarm intelligence, stigmergy** | 🟡 PROPOSED |
| **L10** | **Planetary** | **noosphere-scale coordination** | 🟡 PROPOSED |

### L8: Social Cognition (Theory of Mind)

**Цель:** Модель убеждений других агентов (peers в федерации)

**Benchmark:**
- False-belief task (Sally-Anne test) — 80%+ accuracy
- Intentionality attribution — distinguish accident vs purpose
- Deception detection — catch lies via inconsistency

**Реализация:**
```java
// social/TheoryOfMind.java
public class TheoryOfMind {
    private final Map<PeerId, BeliefModel> models = new HashMap<>();
    
    public BeliefModel getModel(PeerId peer) {
        // Track what peer knows, believes, wants
        return models.computeIfAbsent(peer, ...);
    }
    
    public boolean detectDeception(PeerId peer, Statement stmt) {
        // Compare stmt against peer's known beliefs
        // Inconsistency > threshold → possible lie
        return inconsistencyScore(peer, stmt) > DECEPTION_THRESHOLD;
    }
}
```

### L9: Collective Intelligence (Stigmergy)

**Цель:** Косвенная координация через среду (как муравьи феромонами)

**Механизм:**
- Узлы оставляют «феромоны» в noosphere (временные дайджесты)
- Другие узлы следуют по градиенту феромонов к решению
- Феромоны испаряются (TTL), предотвращая застревание в локальных оптимумах

**Benchmark:**
- Swarm optimization task — лучше индивидуального на 30%+
- Path finding in dynamic graph — адаптация к изменениям
- Resource allocation — fair distribution без центра

### L10: Planetary Coordination

**Цель:** Глобальная оптимизация (climate, energy, knowledge)

**Механизм:**
- Иерархия федераций (home → city → region → planet)
- Multi-scale Φ-метрики (локальное + глобальное качество)
- Democratic governance (weighted voting by reputation)

---

## ⚙️ Аппаратная Оптимизация (CPU+GPU Гибрид)

### Распределение Нагрузки

| Компонент | CPU (32 ядра) | GPU (RTX 5070 Ti) | Обоснование |
|-----------|---------------|-------------------|-------------|
| **BIR execution** | ✅ 100% | ❌ | Булевы операции — integer ALU, latency critical |
| **HDC encoding** | ✅ 80% | ✅ 20% | XOR/popcount — CPU SIMD, batch — GPU parallel |
| **Memory M1/M2** | ✅ 100% | ❌ | Random access, low latency |
| **Memory M3/M4** | ✅ 50% | ✅ 50% | Batch compression — GPU, retrieval — CPU |
| **Φ-метрики** | ✅ 70% | ✅ 30% | Matrix ops — GPU, control logic — CPU |
| **Cauldron rows** | ✅ 40% | ✅ 60% | Candidate evaluation — GPU massively parallel |
| **Ethics filters** | ✅ 100% | ❌ | Deterministic, auditable, no branching |
| **HashChain audit** | ✅ 100% | ❌ | Sequential, cryptographic |

### CUDA Integration Plan

**Текущая проблема:** `Failed to find CUDA shared provider` (ONNX Runtime)

**Решение:**
1. **System-wide CUDA 13.2** (уже есть в nvidia-smi)
2. **cuDNN 9.x** установка
3. **Java bindings** через JNI или JNA
4. **Fallback режим** — если GPU недоступен, всё на CPU

**Код:**
```java
// gpu/CudaContext.java
public class CudaContext {
    private static final Logger LOG = LoggerFactory.getLogger(CudaContext.class);
    private final boolean available;
    
    public CudaContext() {
        this.available = tryLoadCuda();
    }
    
    private boolean tryLoadCuda() {
        try {
            System.loadLibrary("cudart"); // CUDA runtime
            System.loadLibrary("cudnn");  // cuDNN
            LOG.info("CUDA 13.2 + cuDNN loaded successfully");
            return true;
        } catch (UnsatisfiedLinkError e) {
            LOG.warn("CUDA not available, falling back to CPU: {}", e.getMessage());
            return false;
        }
    }
    
    public boolean isAvailable() { return available; }
}

// gpu/GpuAcceleratedTask.java
public interface GpuAcceleratedTask<T> {
    T executeOnCpu();
    T executeOnGpu(); // throws if CUDA unavailable
    
    default T execute() {
        if (CudaContext.getInstance().isAvailable()) {
            try {
                return executeOnGpu();
            } catch (Exception e) {
                LOG.error("GPU execution failed, fallback to CPU", e);
                return executeOnCpu();
            }
        } else {
            return executeOnCpu();
        }
    }
}
```

### Бенчмарк Цели (для референс-железа)

| Задача | CPU-only | CPU+GPU | Target |
|--------|----------|---------|--------|
| BIR forward pass | 50ns | 50ns | ✅ 90ns (DONE) |
| HDC encode 1M vectors | 200ms | 50ms | 🟡 100ms |
| Φ_binary calculation | 10μs | 5μs | 🟡 7μs |
| Cauldron row eval (1000 candidates) | 500ms | 100ms | 🟡 200ms |
| Sleep consolidation (M1→M2) | 10s | 2s | 🟡 5s |
| Native image startup | 2-5s (JVM) | 100ms | ✅ DONE |

---

## 📊 План Развития (Roadmap)

### Фаза 0: Foundation (2026 Q4) — 3 месяца

**Цель:** Адаптация текущего ядра под домашние узлы

- [ ] Исправить CUDA integration (GPU ONNX runtime)
- [ ] Biochemical signals framework (Mediator v2)
- [ ] Neuron Block abstraction (refactor current brain classes)
- [ ] Local Cauldron tuning (Φ-gate calibration)
- [ ] Anonymizer v2 (k-anon + DP noise injection)
- [ ] Trust tier implementation (T0-T3)

**Deliverables:**
- `matrix-home` пакет (легковесная сборка для ноутбуков)
- Benchmark suite для CPU+GPU hybrid
- Documentation: «Запуск домашнего узла»

### Фаза 1: Federation (2027 Q1-Q2) — 6 месяцев

**Цель:** P2P сеть между домашними узлами

- [ ] ELSP gossip protocol (ML-DSA signatures)
- [ ] CRDT sync для M5 noosphere
- [ ] Consensus digest adoption (66% rule)
- [ ] Reputation system (decay, boosting, penalties)
- [ ] Partition recovery automation
- [ ] Privacy audits (DP budget tracking)

**Deliverables:**
- Testnet (10-20 узлов, добровольцы)
- Security audit report
- Privacy impact assessment

### Фаза 2: Autonomy (2027 Q3-Q4) — 6 месяцев

**Цель:** L8-L10 capability levels

- [ ] TheoryOfMind implementation (L8)
- [ ] Stigmergic coordination (L9)
- [ ] Multi-scale Φ-метрики (L10)
- [ ] Biochemical modulation experiments
- [ ] Autonomous goal generation (curiosity-driven)

**Deliverables:**
- Research paper: «L8-L10 Capabilities in Decentralized Brains»
- Open dataset: federated learning benchmarks
- Demo: swarm task completion

### Фаза 3: Scale (2028+) — ongoing

**Цель:** Production deployment, community growth

- [ ] Kubernetes operator для федерации
- [ ] Hardware partners (pre-built appliances)
- [ ] Certification (ethics, security, privacy)
- [ ] Business model (resource sharing marketplace)
- [ ] Governance DAO (community-led upgrades)

---

## 🔬 Открытые Вопросы и Риски

### Научные Неопределённости

1. **Биохимическая аналогия** — насколько точно гормоны моделируют управление?
   - **Эксперимент:** Ablation study — отключать по одному сигналу, смотреть деградацию
   - **Метрика:** Performance drop % vs biological lesion studies

2. **Φ-гейт порог** — какое значение Φ достаточно для promotion?
   - **Эксперимент:** Sweep Φ_threshold от 0.5 до 0.95
   - **Метрика:** Precision/recall trade-off, MDL score

3. **Consensus размер** — сколько узлов нужно для надёжного consensus?
   - **Эксперимент:** Simulate network 10-10000 nodes, measure convergence time
   - **Метрика:** Time-to-consensus, false positive rate

### Технические Риски

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|-----------|
| GPU драйверы нестабильны | Средняя | Высокое | CPU fallback, Docker с предсказуемой средой |
| DP noise уничтожает полезность | Низкая | Среднее | Adaptive ε (меньше для важных дайджестов) |
| Sybil-атака на consensus | Средняя | Критичное | Stake-based voting (reputation as stake) |
| Regulatory blocking (privacy laws) | Высокая | Высокое | Legal review, GDPR compliance by design |
| Energy consumption слишком высокий | Низкая | Среднее | Dynamic frequency scaling, idle consolidation |

### Этические Дилеммы

1. **Исследования vs безопасность** — можно ли временно отключать этику для гипотез?
   - **Ответ:** Да, но ТОЛЬКО в изолированном sandbox с оператор-approval
   - **Механизм:** `--experimental-mode --operator-signature=<key>`

2. **Приватность vs полезность дайджестов** — где граница?
   - **Ответ:** K_MIN=5 по умолчанию, настраивается оператором
   - **Механизм:** Slider в UI: «Privacy ←→ Utility»

3. **Централизация богатых узлов** — что если у кого-то 1000x больше GPU?
   - **Ответ:** Quadratic voting (reputation^0.5)
   - **Механизм:** `vote_weight = sqrt(reputation_score)`

---

## 📝 Изменения в Существующие Документы

### CONSTITUTION.md (предлагаемые amendments)

**Добавить Article IX: Federation Principles**
```markdown
## IX. Federation Principles

Система MATRIX участвует в децентрализованной федерации (noosphere) на условиях:

1. **Локальный суверенитет** — каждый узел контролирует свои данные (M0-M4)
2. **Анонимный обмен** — только k-анонимные дайджесты покидают узел
3. **Консенсусное принятие** — артефакты принимаются при ≥66% согласии
4. **Право на забвение** — tombstone propagation для удаления данных
5. **Открытый аудит** — все действия логируются в HashChain

Нарушение этих принципов требует RFC и 75% consensus guardians.
```

### DESIGN-58 (extension)

**Добавить раздел 8: Social & Collective Capabilities (L8-L10)**
- L8: Theory of Mind benchmark
- L9: Stigmergy coordination metrics
- L10: Planetary-scale Φ

### REQUEST-autonomy-impulses.md (amendment)

**Добавить 5-й импульс: `social-bonding`**
```markdown
| 5 | **social-bonding** | peer reputation Δ > threshold | send trust signal, update oxytocin | network + reputation-budget |
```

---

## 🎯 Следующие Шаги (Immediate Actions)

1. **[ ] Создать GitHub issue** с этим proposal для обсуждения
2. **[ ] Назначить working group** (3-5 человек, 2 недели на review)
3. **[ ] Построить прототип** Biochemical Mediator (1 неделя)
4. **[ ] Исправить CUDA integration** (приоритет #1, blocker для GPU)
5. **[ ] Написать тестовый сценарий** для L8 (Theory of Mind pilot)
6. **[ ] Обновить WAL.md** с этим proposal (RUN XXX)

---

## 📚 Ссылки на Архивные Документы

- **Cauldron Protocol:** `docs-v2/archive/2026-08-pre-v2/science/science/SUBSTRATE-MODELS.md §8.1`
- **Autonomy Levels:** `docs-v2/designs/DESIGN-58-capability-levels-roadmap.md`
- **Federation Design:** `docs-v2/designs/DESIGN-08-federation.md`
- **Decentralized Digests:** `docs-v2/architecture/REQUEST-decentralized-digests.md`
- **Biochemical Inspiration:** `docs-v2/designs/DESIGN-64-controlled-stochasticity.md` (hormonal state mention)
- **Arousal Dynamics:** `matrix-core/src/main/java/io/matrix/consciousness/ArousalDynamics.java`

---

**Заключение:** Этот proposal расширяет MATRIX из одиночного «цифрового мозга» в **глобальную федерацию персональных интеллектов**, сохраняя этические гарантии, приватность и детерминизм ядра. Аппаратная база (домашние GPU+CPU) делает развёртывание доступным, а биохимическая модуляция и Cauldron-протокол обеспечивают эмерджентное саморазвитие.

**Статус:** Ожидает review community → working group → implementation plan.
