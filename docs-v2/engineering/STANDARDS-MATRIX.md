# STANDARDS-MATRIX

Версии зависимостей и причины пропусков. Проверено онлайн (maven-metadata).

| Компонент | Текущее | Источник | Решение |
|---|---|---|---|
| Java | 25.0.4 LTS | `gradle.properties` | ✅ новейшая LTS |
| Quarkus BOM | 3.38.3 | `matrix-core/build.gradle` | ✅ стабильный максимум (3.39 — только CR1) |
| GraalVM plugin | 1.1.10 | `matrix-core/build.gradle` | ✅ совместим |
| Avro | 1.12.2 | dep | ✅ патч-апгрейд (было 1.12.0) |
| ONNX Runtime | 1.29.0 | dep | ✅ minor-апгрейд (было 1.17.0), API совместим |
| kafka-clients | 4.3.1 | dep | ✅ мажор 4.x, миграция чистая (было 3.9.0) |
| Testcontainers | 1.21.3 | BOM | ⏸️ мажор 2.x требует пересборки явных артефактов |
| Postquantum ML-DSA | JEP 497 (JDK25) | native | ✅ без внешнего dep |
| ONNX Runtime GPU (Java) | — | — | ❌ требует системного CUDA 12 + cuDNN9 (нет в окружении) |
| **W31 Brain (HDC + BitLinear + NCA)** | — | pure-Java + JEP 424 Panama FFM | ✅ новый архитектурный примитив (ADR-2026-09-12-001) |
| **libtruthy_hamming.so** | built Sep 2026 | `matrix-core/src/main/c/libtruthy/` | ✅ C-extension для 32× быстрее Hamming через JEP 424; Java fallback гарантирован |
| **BitNet b1.58 reference** | arxiv 2402.17764 | external | 📚 алгоритмический референс; не runtime dep |
| **HdcEncoding / BitLinear / NCA** | — | pure-Java | ✅ 14 классов, 258 тестов, edge-AI positioning (37.9M ops/sec) |

## Пропуски осознанные

- **Testcontainers 2.x**: мажор координат; наш build ссылается на `org.testcontainers:kafka:1.21.3`/`org.testcontainers:postgresql:1.21.3` явно — апгрейд требует пересмотра всех тестовых деклараций; запланировано на отдельный RFC.
- **Quarkus 3.39.0.CR1**: пре-релиз; остаёмся на стабильной 3.38.3.
- **GraalVM toolchain**: `gu install graalpy-graalvm` / `gu install native-image` не запускали в этом окружении — Mandrel-контейнер используется на CI; локально native-сборка не проверялась.

## Методика обновления

Мажорные апгрейды только с:
1. Проверкой maven-metadata (curl `<dep>/maven-metadata.xml`),
2. compileJava + целевые прогоны тестов,
3. при падении — откат + пометка BLOCKED.

Patch/minor — то же, но без RFC.
## Архитектурные решения (ADR)

| ADR | Дата | Решение | Статус |
|---|---|---|---|
| ADR-2026-09-12-001 | 2026-09-12 | Принять HDC × BitLinear hybrid brain как архитектурный примитив | ✅ Accepted |
| ADR-2026-10-26-002 | 2026-10-26 | Единый когнитивный проводник (`TrueMindCycle`) | ✅ Accepted |
| ADR-2026-10-30-003 | 2026-10-30 | Модель доверия `HashChain`: внедрённые часы, валидация, правило sequence, tamper-**evidence**, а не tamper-proof | ✅ Accepted |
| ADR-2026-10-30-004 | 2026-10-30 | Семантика CRDT в Noosphere: одна семантика удаления на класс; `EpochAwareCausalSet` — явная epoch-aware реализация; tombstone-ы переживают сериализацию | ✅ Accepted |

Полные ADR в `docs-v2/architecture/`.

## Формальный контур (RECON-W39)

| Компонент | Версия | Решение |
|---|---|---|
| tla2tools | 1.8.0 (sha256 `fc6878c6…d6bdd63e`) | ✅ версия **и** контрольная сумма проверяются перед запуском гейта; jar не в исходниках (`*.jar` в `.gitignore`) |
| jackson-dataformat-yaml | 2.18.3 | ✅ test-scope в `matrix-quality`: реестр `formal/spec-implementation-map.yml` разбирается Jackson-YAML; артефакт уже присутствует в графе зависимостей reactor'а (Quarkus тянет snakeyaml), новой загрузки для reactor'а нет |
| PyYAML | — | ❌ не используется в CI: реестр и его валидатор — Java, поэтому проверка не зависит от того, есть ли PyYAML на раннере |

Реестр соответствия и harness мутаций описаны в
[`docs-v2/engineering/TLA-STATUS.md`](TLA-STATUS.md) и
[`docs-v2/engineering/MUTATION-REPORT.md`](MUTATION-REPORT.md).
