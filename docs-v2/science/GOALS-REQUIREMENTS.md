# GOALS-REQUIREMENTS — цели и функциональные требования MATRIX

**Статус: normative** (inline 2026-08-26 из `vision/vision/GOALS-REQUIREMENTS.md`).

## Миссия

Энергоэффективная, верифицируемая, честная вычислительная система класса ИИ, разворачиваемая от ноутбука до кластера и федерации узлов, с развитием как **последовательное расширение компетенций через preregistered эксперименты** (CONSTITUTION VI запрещает декларации AGI).

## Цели (high-level)

| G | Цель | Критерий | Источник |
|---|---|---|---|
| G-1 | Детерминизм рантайма: любые вход → одинаковый выход | tests + INV-1 source-scan | CONSTITUTION I; DESIGN-14 |
| G-2 | Этический FROZEN-слой независим от обучения | TLA+ `FrozenEthicalFNL`; EXP-006 (FPR 0, TPR 100) | CONSTITUTION IV |
| G-3 | K_MAX ≤ 20 в ядре | compiles; FpgaBackend K_MAX-конформно | CONSTITUTION II |
| G-4 | Покрытие тестами ≥82% METHOD (matrix-core) | JaCoCo gate | CONSTITUTION V |
| G-5 | Все эксперименты preregistered | HYPOTHESES.md запись до запуска; отчёты с числами | `research/PROTOCOL.md` |
| G-6 | Числовые отчёты — только реальные замеры | Конституция VI; никаких «AGI» и абсолютных утверждений | CONSTITUTION VI |

## Функциональные требования

| FR | Требование | Пакет/класс | Тест |
|---|---|---|---|
| FR-B1 | Tsetlin producer с детерминированным обучением | `tsetlin/TsetlinTrainer` | `tsetlin/*` юнит + `Exp010ComparisonTest` |
| FR-B2 | Экспорт решения как BIR (CLAUSESET) | `toDecisionClauseSet` | эквивалентность exhaustive k≤6 |
| FR-C1 | GA baseline (для сравнений) | `evolution/MpdtGaProducer` | `Exp002ComparisonTest` |
| FR-D1 | JvmSimd-бэкенд BIR | `bir/JvmSimdBackend` | JMH-гейты |
| FR-D2 | Fpga-бэкенд (compile-only, синтез BLOCKED) | `bir/FpgaBackend.java` | unit compile |
| FR-D3 | Quantum-бэкенд (только спека) | `docs/spec/quantum/BIR-to-MPS.md` | BLOCKED-EXT |
| FR-Ethical | FROZEN четыре запрета | `ethics/frozen/FrozenEthicalFNL*`, `LieDetector` | TLA+; EXP-006 |
| FR-Fed | ELSP v1 (Ed25519) | `federation/ElspChannel` | unit |
| FR-Fed2 | ELSP v2 (ML-DSA) | `federation/ElspChannelMlDsa` | unit (JDK25 native) |
| FR-Hierarchy | Curriculum + maturity gates | `devloop/*` | `DevLoopTest`, `DevLoopPropertiesTest` |

## Нефункциональные требования

| NFR | Требование | Цель | Метод |
|---|---|---|---|
| NFR-1 | BIR latency (per-call, packed word) | ≤1 µs | ~62 нс EXP-009C |
| NFR-2 | GPU latency per-call (RTX 5070 Ti, fp32 FFN) | reference | 17.25 µs EXP-009C |
| NFR-3 | Coverage gate | ≥82% METHOD | JaCoCo |
| NFR-4 | First-batch latency | <100ms @ N=2000 | тесты EXP-009 |
| NFR-5 | Субстрат-переносимость | JVM 25 / GraalVM native / k8s | matrix-operator CRD |

## Изменения

Консолидированный changelog в шапке; любые попытки нарушить CONSTITUTION.md блокируются.