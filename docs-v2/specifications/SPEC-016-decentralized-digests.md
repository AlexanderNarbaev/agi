# SPEC-016 — Decentralized Digests (normative)

**Status**: NORMATIVE · v1
**Package**: `io.matrix.federation`
**Related**: [DESIGN-08](../designs/DESIGN-08-federation.md), [SPEC-007](./SPEC-007-subconscious.md), [SPEC-011](./SPEC-011-memory-hierarchy.md), [DESIGN-73](../designs/DESIGN-73-consolidation-cycle.md)

**Why this document exists.** The series was renumbered and the specification of this
subject was lost; the directive `SPEC-009-decentralized-digests` <!-- no-id: no specification of that subject exists; SPEC-009 is the mediator spec; Q-W46-20 -->
was named by [REQUEST-decentralized-digests](../architecture/REQUEST-decentralized-digests.md) (`:5`)
and by [REQUEST-brain-overview](../architecture/REQUEST-brain-overview.md) (`:5`) and had no
counterpart under any number. The owner decided (OD-031, `docs-v2/governance/DECISION-REGISTER.md:71`)
that the subject is **written, not mapped**. The implementation is TASK-W46-19
(`docs-v2/governance/ROADMAP.md:99`).

**The status vocabulary of this document** is the vocabulary the owner set for the wave:
`DESIGNED` · `IMPLEMENTED` · `INTEGRATED` · `REACHABLE` · `SERVED` · `TESTED` ·
`BENCHMARKED` · `OPERATIONAL` · `PROPOSED`. **A class that has no caller is never called
`SERVED`.** Every claim below carries its evidence as `path:line`.

---

## Что

A **digest** is a *count of contributions to a content hash*, k-anonymised and
Laplace-noised, released to peers. A digest never carries a payload. The requirement is
`R11.1` — *"Decentralized digests (не сырые данные)"* — at
`docs-v2/paradigm/REQUIREMENTS.md:135`; the owner-level decision is `ND-005`
*"Decentralisation through anonymised digests"* at
`docs-v2/governance/DECISION-REGISTER.md:87`. The directive that defines the subject is
[REQUEST-decentralized-digests](../architecture/REQUEST-decentralized-digests.md) (147
lines): the neuron-block topology, the Anonymizer as the boundary between the local
context and the global pool, the `localPublish(record, policy) -> Either<AnonymizedDigest,
Rejection>` contract at `docs-v2/architecture/REQUEST-decentralized-digests.md:81-107`
(подпись — `:85`), и таблица угроз at `docs-v2/architecture/REQUEST-decentralized-digests.md:109-118`.

**Status: `DESIGNED`.** The requirement, the directive and the owner decision exist. The
implementation of the *release* path exists but is not reachable (§ FR).

---

## Архитектура

```
count → bucket → noise → emit → dispatch

Anonymizer                — счётчик k-анонимности      io.matrix.federation.Anonymizer
  recordContribution(hash, nodeId)                       Anonymizer.java:25
  isAnonymous(hash) / anonymityLevel(hash)                Anonymizer.java:30,35
  snapshotEntries()                                        Anonymizer.java:43
        │
        ▼
DecentralizedDigestPipeline — бакинг + DP-шум Laplace   io.matrix.federation.DecentralizedDigestPipeline
  emitDigests() → List<Digest(hash, noisyCount, shared>  DecentralizedDigestPipeline.java:71
  laplaceNoise(): b = 1/ε                                 DecentralizedDigestPipeline.java:83-87
        │
        ▼
KnowledgeShare            — диспетчер egress             io.matrix.federation.KnowledgeShare
  digestBundle()                                        KnowledgeShare.java:39
  dispatchToPeer(digest, peerId)                         KnowledgeShare.java:54
        │
        ▼
MessageBus                — транспорт                    io.matrix.federation.MessageBus
  FileSystemMessageBus (production) / InMemoryMessageBus MessageBus.java:32,61
        │
        ▼
FederationDigest          — подписанное тело            io.matrix.federation.FederationDigest
  compute(nodeId, ts, entries) → SHA-256 bodyHash        FederationDigest.java:36,49
  verify(digest)                                         FederationDigest.java:58
        │
        ▼
ElspChannelMlDsa          — канал подписи (ML-DSA)      io.matrix.federation.ElspChannelMlDsa
  sign(seq, payload) / verifyAndAccept(envelope)         ElspChannelMlDsa.java:43,54
```

**Куда это встраивается сегодня — и куда нет.** Единственный production-путь, который
касается дайджеста, — это **окно сна** chain B:
`io.matrix.sleep.SleepCycle` держит `Anonymizer` (`matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:31`)
и вызывает `anonymizer.recordContribution(...)` (`SleepCycle.java:81`).
`Anonymizer` создаётся гейтвеем при старте — `new io.matrix.federation.Anonymizer(2)`
(`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:263`).
`DecentralizedDigestPipeline`, `KnowledgeShare`, `FederationDigest`, `ElspChannelMlDsa` и
`ShareImpulseFirer` **не имеют ни одного production-вызывающего** — измерено поиском по
`matrix-core/src/main`, `matrix-brain-runtime/src/main`, `matrix-api-gateway/src/main`.
`DecentralizedDigestPipeline` создаётся только внутри `KnowledgeShare.digestBundle()`
(`matrix-core/src/main/java/io/matrix/federation/KnowledgeShare.java:40`), а
`KnowledgeShare` не создаётся нигде.

**Status: `IMPLEMENTED` (the four stages), `REACHABLE` only for the counting stage.**
`DecentralizedDigestPipeline`, `KnowledgeShare`, `FederationDigest`, `ElspChannelMlDsa`,
`ShareImpulseFirer` — **`IMPLEMENTED`, `TESTED`, но НЕ `REACHABLE` и НЕ `SERVED`**.
`Anonymizer` — **`IMPLEMENTED`, `REACHABLE`** (из окна сна), но **НЕ `SERVED`**: окно сна
считает, а не выпускает.

**Смежный механизм, который не является дайджестом.** `MeshFederation`
(`matrix-core/src/main/java/io/matrix/noosphere/MeshFederation.java:14`) публикует
`FnlPackage` (`:72`), а не дайджесты; `KnowledgeExchangeProtocol`
(`matrix-core/src/main/java/io/matrix/federated/KnowledgeExchangeProtocol.java:19`) делится
**дистиллированными правилами** с run-length сжатием (`:60`, `:67`), а не дайджестами;
`PrivacyMechanism` (`matrix-core/src/main/java/io/matrix/federated/PrivacyMechanism.java:10`)
шумит **локальные обновления** федеративного обучения (`addNoise` `:18`), а не дайджесты.
Три разных механизма; смешивать их в одной спецификации нельзя.

---

## FR (классы и интерфейсы)

- `federation/Anonymizer` — счётчик k-анонимности. `Anonymizer(int kThreshold)` —
  `k < 2` отвергается (`Anonymizer.java:20`). Методы: `recordContribution(contentHash,
  nodeId)` (`:25`), `isAnonymous(contentHash)` (`:30`), `anonymityLevel(contentHash)`
  (`:35`), `snapshotEntries()` (`:43`), `kThreshold()` (`:40`), `clear()` (`:48`).
  **Status: `IMPLEMENTED`, `REACHABLE` (из окна сна).**
- `federation/DecentralizedDigestPipeline` — бакинг + DP-шум.
  `DecentralizedDigestPipeline(Anonymizer, double epsilon)` (`:54`) и перегрузка с явным
  `Random` (`:58`); `epsilon <= 0` отвергается (`:59`). `emitDigests()` (`:71`) возвращает
  `List<Digest>`; `Digest(contentHash, noisyCount, shared)` (`:28`) отвергает `null` и
  отрицательный `noisyCount` (`:30-31`). Шум Laplace: `b = 1/ε`, uniform `u ∈ (-0.5, 0.5)`
  (`:83-87`). **Status: `IMPLEMENTED`, `TESTED`, НЕ `REACHABLE`.**
- `federation/KnowledgeShare` — egress. `digestBundle()` (`:39`) строит бандл с
  `epsilon = 1.0` (`:40`); `dispatchToPeer(digest, peerId)` (`:54`) **отбрасывает** дайджест
  с `shared == false` (`:56-59`) и считает отброшенные; сериализация — JSON с `hash`,
  `shared`, `noisyCount` (`:80-85`). **Status: `IMPLEMENTED`, `TESTED`, НЕ `REACHABLE`.**
- `federation/FederationDigest` — подписанное тело. `Entry(key, hash, tier)` (`:29`),
  `Digest(nodeId, timestampMillis, entries, bodyHash)` (`:31`), `compute` (`:36`),
  `sha256` (`:49`), `verify` (`:58`), `entriesForTier` (`:70`). Хеш — **SHA-256**
  (`java.security.MessageDigest`, `:51`). **Status: `IMPLEMENTED`, `TESTED`, НЕ `REACHABLE`.**
- `federation/ElspChannelMlDsa` — канал подписи v2: `KeyPairGenerator("ML-DSA")` и
  `Signature("ML-DSA")` (`:32-33`), нативно в JDK 25 без внешней зависимости (`:18`);
  `sign` (`:43`), `verifyAndAccept` (`:54`). **Status: `IMPLEMENTED`, `TESTED`, НЕ `REACHABLE`.**
- `federation/MessageBus` — транспорт; `FileSystemMessageBus` (`:32`) — production-реализация,
  `InMemoryMessageBus` (`:61`) — тестовая. **Status: `IMPLEMENTED`, НЕ `REACHABLE` (только
  из `KnowledgeShare`).**
- `federation/ShareImpulseFirer` — порог шэринга: `DEFAULT_THRESHOLD = 0.5` (`:25`),
  `observe(utility, accepted, groundTruth)` (`:53`), `precision()` (`:79`), `recall()` (`:87`).
  **Status: `IMPLEMENTED`, `TESTED`, НЕ `REACHABLE`.**

---

## Инварианты

1. **Только счёт, никогда payload.** Дайджест несёт `contentHash` и `noisyCount`
   (`DecentralizedDigestPipeline.java:28`); сериализованное тело — `hash`, `shared`,
   `noisyCount` (`KnowledgeShare.java:80-85`). Сырые данные наружу не уходят:
   `docs-v2/paradigm/REQUIREMENTS.md:135`. **Status: `IMPLEMENTED`, `REACHABLE` (счётчик),
   но выпуск — НЕ `REACHABLE`.**
2. **Порог проверяется по зашумлённому счёту.** `shared = noisy >= kThreshold`
   (`DecentralizedDigestPipeline.java:76`). **Status: `IMPLEMENTED`.**
3. **Подавленный дайджест не отправляется.** `dispatchToPeer` возвращает `false` и
   увеличивает `digestsDropped` (`KnowledgeShare.java:56-59`). **Status: `IMPLEMENTED`.**
4. **RNG на egress-пути явный и засеянный по умолчанию.** `DEFAULT_SEED = 0x5EED_C0DEL`
   (`DecentralizedDigestPipeline.java:52`); предыдущий `new SecureRandom()` был
   незасеянным источником на пути egress и делал `KnowledgeShareTest` флаки — причина
   зафиксирована в javadoc (`:39-51`). **Status: `IMPLEMENTED`.**
5. **Уровень чувствительности = 1 на вклад.** `b = 1/ε` при `sensitivity = 1`
   (`DecentralizedDigestPipeline.java:84`), что совпадает с протоколом
   `docs-v2/research/protocols/H-043.md:8`. **Status: `IMPLEMENTED`, `BENCHMARKED`
   (см. § Метрики).**
6. **Бюджет ε на peer и его исчерпание.** Требование: *"Side-channel через
   DP-budget exhaustion → ε-budget — per-peer counter; exhaustion → reject до конца
   окна"* (`docs-v2/architecture/REQUEST-decentralized-digests.md:114`). В коде счётчика
   бюджета нет. **Status: `PROPOSED` — не реализовано.**
7. **Схема обязательна.** Требование *"Schema-binding — digest знает свою схему (ioSchema);
   дайджесты без схемы не принимаются"* (`docs-v2/architecture/REQUEST-decentralized-digests.md:56`).
   В коде поля схемы нет. **Status: `PROPOSED` — не реализовано.**

---

## Схема дайджеста

Дайджест не привязан к таблице; его **зерно** задаёт сам record.
`FederationDigest.Entry` — это тройка `(key, hash, tier)` (`FederationDigest.java:29`), а
тело хешируется как `node=…;ts=…;key=hash|tier;` (`FederationDigest.java:38-45`).

| элемент дайджеста | источник | статус |
|---|---|---|
| `key` | `memory.id` — колонка `id TEXT PRIMARY KEY` таблицы `memory` (`matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java:44`) | `PROPOSED` (зерно выведено из record'а, никогда не записано) |
| `hash` | SHA-256 от `memory.content` — колонка `content TEXT NOT NULL` (`SqliteMemoryBackend.java:46`) | `PROPOSED` |
| `tier` | `memory.level` — колонка `level INTEGER NOT NULL` (`SqliteMemoryBackend.java:45`); уровни `L0_ARTIFACT`…`L4_KERNEL` (`matrix-core/src/main/java/io/matrix/memory/HierarchicalMemory.java:45-55`) | `PROPOSED` |
| `nodeId` | идентификатор узла, подписью (`FederationDigest.java:31`) | `IMPLEMENTED` |

**База знаний — не дом дайджеста.** Таблицы `data/knowledge/workbench.sqlite` (27 таблиц,
`SCHEMA_VERSION = 19`, `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:90`)
**не имеют** зерна дайджеста: код, который считает дайджест, к ним не обращается.
Единственное место, где дайджест и база знаний встречаются, — `SleepCycle`, и то по
объекту памяти, а не по строке таблицы (`SleepCycle.java:79`).
**Status: `PROPOSED` — зерно для базы знаний не определено.**

---

## Подпись и транспорт

- **ELSP v1** — Ed25519 поверх `seq‖payload` со строго монотонным `seq` на стороне
  получателя (anti-replay): `docs-v2/designs/DESIGN-08-federation.md:7`. Класс
  `federation/ElspChannel` (`matrix-core/src/main/java/io/matrix/federation/ElspChannel.java:20`).
- **Профиль v2** — постквантовый **ML-DSA (FIPS 204, JEP 497)** нативно в JDK 25 без
  внешней зависимости (`ElspChannelMlDsa.java:11-12,18`). Статус: `IMPLEMENTED`, `TESTED`,
  **НЕ `REACHABLE`** — 0 production-ссылок.
- **Транспорт** — `MessageBus`; production-реализация `FileSystemMessageBus`
  (`MessageBus.java:32`), то есть обмен через файловую систему, **а не через сеть**.
  Статус: `IMPLEMENTED`, НЕ `REACHABLE` (только из `KnowledgeShare`).
- **mTLS для peer-interconnect** отложен
  (`docs-v2/designs/DESIGN-08-federation.md:30`). Статус: `PROPOSED`.

---

## Связь с существующим

- [DESIGN-08-federation](../designs/DESIGN-08-federation.md) — нормативный sibling: ELSP,
  ML-DSA v2, k-анонимность. **Его § Тесты называет `AnonymizerTest` и `ArtifactSignerTest`
  (`DESIGN-08-federation.md:19`) — обоих файлов в репозитории нет.** Это расхождение
  фиксируется здесь и не исправляется в этой волне (§ Отложено).
- [SPEC-007-subconscious](./SPEC-007-subconscious.md) — гейт, через который проходит
  шэринг: *"Gossip (share): M3-артефакты, прошедшие IntegrityCheck, отправляются в
  `noosphere/MeshFederation` для quorum M4"* (`SPEC-007-subconscious.md:47`).
- [SPEC-011-memory-hierarchy](./SPEC-011-memory-hierarchy.md) — уровни памяти, которые
  дайджест несёт как `tier`.
- [DESIGN-73-consolidation-cycle](../designs/DESIGN-73-consolidation-cycle.md) — окно сна,
  единственное место, где дайджест вообще производится сегодня.
- `noosphere/MeshFederation` (`matrix-core/src/main/java/io/matrix/noosphere/MeshFederation.java:14`)
  — транспорт M4; публикует `FnlPackage`, **не** дайджесты.
- `federated/KnowledgeExchangeProtocol` (`matrix-core/src/main/java/io/matrix/federated/KnowledgeExchangeProtocol.java:19`)
  — другой механизм обмена (дистиллированные правила); `INTEGRATED` в
  `FederatedLearningEngine` (`:48`), который отдаётся через
  `federated/FederatedResource` (`@Path("/api/v1/federated")`,
  `matrix-core/src/main/java/io/matrix/federated/FederatedResource.java:17`). Статус:
  `IMPLEMENTED`, `INTEGRATED`; `SERVED` **не заявляется** — перепись маршрутов
  (`docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md:55-68`) считает `/api/v1/**` = 73
  маршрута на 16 ресурсах, не называя их поимённо.
- `federated/PrivacyMechanism` (`matrix-core/src/main/java/io/matrix/federated/PrivacyMechanism.java:10`)
  — отдельный DP-механизм федеративного обучения; `INTEGRATED` в `FederatedResource:31`.
- `ND-005` — решение владельца (`docs-v2/governance/DECISION-REGISTER.md:87`).
- `H-043` — гипотеза (`docs-v2/research/HYPOTHESES-NEW.md:16`).

---

## Тесты

| тест | файл | `@Test` | что покрывает | статус |
|---|---|---|---|---|
| `DecentralizedDigestPipelineTest` | `matrix-core/src/test/java/io/matrix/federation/DecentralizedDigestPipelineTest.java` | 6 | бакинг, шум, порог | `TESTED` |
| `FederationDigestTest` | `matrix-core/src/test/java/io/matrix/federation/FederationDigestTest.java` | 8 | `compute`/`verify`, SHA-256 | `TESTED` |
| `KnowledgeShareTest` | `matrix-core/src/test/java/io/matrix/federation/KnowledgeShareTest.java` | 4 | бандл, отбрасывание, отправка | `TESTED` |
| `ElspChannelTest` | `matrix-core/src/test/java/io/matrix/federation/ElspChannelTest.java` | — | roundtrip / tamper / replay | `TESTED` |
| `ElspChannelMlDsaTest` | `matrix-core/src/test/java/io/matrix/federation/ElspChannelMlDsaTest.java` | — | профиль v2 | `TESTED` |
| `Exp048H049ShareImpulseTest` | `matrix-core/src/test/java/io/matrix/federation/Exp048H049ShareImpulseTest.java` | — | порог шэринга | `TESTED` |
| `TwoJvmFederationSmokeTest` | `matrix-core/src/test/java/io/matrix/federation/TwoJvmFederationSmokeTest.java` | 4 | **не 2 JVM** — один процесс, in-memory транспорт; признано в javadoc `:34-37` | `TESTED` (одного процесса) |
| `AnonymizerTest` | **файла нет** — но назван в `docs-v2/designs/DESIGN-08-federation.md:19` | — | — | `PROPOSED` |
| `ArtifactSignerTest` | **файла нет** — но назван там же | — | — | `PROPOSED` |

**Честно о покрытии.** Нет ни одного теста, который проверяет, что **k-порог
открывается**: ни один тест не строит сценарий «k разных узлов прислали один и тот же
хеш». Тест на двух JVM — это один процесс (`TwoJvmFederationSmokeTest.java:34-37`), и
`docs-v2/vision/USAGE.md:230` фиксирует то же: *"the M3→M4 gossip pipeline exists but
hasn't been tested between 2 JVMs"*.

---

## Метрики / гейты

**Гейт H-043 — функция от (k, ε), а не точка.** Протокол зафиксировал
*"Utility ≥0.7 × baseline при k=100, ε=1.0 (synthetic-scope)"*
(`docs-v2/research/HYPOTHESES-NEW.md:16`, `docs-v2/research/protocols/H-043.md:4`).

| (k, ε) | корпус | метрика | результат | вердикт | источник |
|---|---|---|---|---|---|
| k=100, ε=1.0 | 1000 хешей, счётчики Pareto-подобны: 70 % в [1,9], 25 % в [10,99], 5 % в [100,500]; downstream = «shared iff noisy ≥ k»; utility = **F1** | F1 | **0.035** | **REFUTED** | `docs-v2/research/reports/EXP-043-tuning-report.md:4` |
| k=5, ε=5.0 | тот же | F1 | **0.913** (медиана по 5 seed'ам) | **ACCEPTED** (гейт 0.7) | `docs-v2/research/reports/EXP-043-tuning-report.md:8,11` |
| k=100, ε=1.0 | 1000 дайджестов в **10 tenant-группах** (в каждой ≥ 100 участников) | **survival** (дайджест дожил до бакета) | **1.000** | **ACCEPTED** | `docs-v2/research/reports/EXP-MATRIX.14-h043-digest-utility.md:30,39-42` |
| k=50, ε=1.0 | тот же | survival | 1.000 | — | `EXP-MATRIX.14-h043-digest-utility.md:31` |
| k=200, ε=1.0 | тот же | survival | **0.000** (нет группы ≥ 200) | — | `EXP-MATRIX.14-h043-digest-utility.md:32` |
| k=500, ε=1.0 | тот же | survival | **0.000** | — | `EXP-MATRIX.14-h043-digest-utility.md:33` |
| k=100, ε=0.1 | тот же | средний \|шум\| | **10.500** (меньше ε → больше шума) | — | `EXP-MATRIX.14-h043-digest-utility.md:35` |

**Почему в одном корпусе (k=100, ε=1.0) два числа — 0.035 и 1.000.** Корпуса и метрики
разные: `EXP-043` меряет F1 классификатора на Pareto-распределении счётчиков
(`docs-v2/research/reports/EXP-043-report.md:11-15`), а `EXP-MATRIX.14` меряет **survival**
на 10 группах, каждая из которых не меньше k (`EXP-MATRIX.14-h043-digest-utility.md:30`).
Сам `EXP-MATRIX.14` это признаёт: *"The '1.000 utility' is a survival metric, not a
content-fidelity metric"* (`:44-47`). **Вывод: полезность дайджеста — функция от (k, ε,
формы корпуса, определения метрики), и гейт нельзя записывать одной точкой.**
**Status: `BENCHMARKED` (synthetic-scope), `PROPOSED` (гейт как функция).**

**Гейт конституции.** Ни один из этих замеров не проверяет, что дайджест не несёт
payload, и ни один не проверяет, что k считает **узлы** (см. § Дефекты).

---

## Два дефекта, которые эта спецификация фиксирует

**(a) `Anonymizer.recordContribution` игнорирует свой аргумент `nodeId`.**
`matrix-core/src/main/java/io/matrix/federation/Anonymizer.java:25-27`:
```java
public void recordContribution(String contentHash, String nodeId) {
    nodeCounts.merge(contentHash, 1, Integer::sum);
}
```
`nodeId` не используется. Счётчик растёт **по хешу**, а не по узлам. При этом javadoc
класса обещает обратное: *"Ensures that any published telemetry data is shared by at least
k **distinct nodes**"* (`Anonymizer.java:9-11`). То есть k-анонимный контракт, который
класс заявляет, — не тот контракт, который класс реализует.
**Status: `PROPOSED` (требование «k различных узлов») против `IMPLEMENTED` (k вкладов).**
Дефект зафиксирован, код **не** исправлен в этой волне.

**(b) Дайджест окна сна — это `identityHash + wallClock`, и он нов на каждом цикле.**
`matrix-core/src/main/java/io/matrix/sleep/SleepCycle.java:79-81`:
```java
String contentHash = Integer.toHexString(memory.hashCode())
        + "-" + System.currentTimeMillis();
anonymizer.recordContribution(contentHash, "sleep-cycle");
```
Это **не хеш по содержимому**: `memory.hashCode()` — identity-хеш объекта в JVM, а
`System.currentTimeMillis()` — стенное время. Из этого следуют два следствия:
1. **k-порог не может открыться для такого дайджеста.** Счётчик `Anonymizer` растёт по
   хешу (`Anonymizer.java:26`), а хеш нов на каждом цикле из-за времени в нём. Счётчик
   такого хеша навсегда равен 1, `isAnonymous` (`Anonymizer.java:30`) возвращает `false`,
   и `DecentralizedDigestPipeline.emitDigests()` никогда не поставит такому дайджесту
   `shared = true` (`DecentralizedDigestPipeline.java:76`) — то есть
   `KnowledgeShare.dispatchToPeer` его отбросит (`KnowledgeShare.java:56-59`).
2. **`System.currentTimeMillis()` стоит на пути решения.** `AGENTS.md` запрещает
   wall-clock в рантайм-пути решений без явного RFC-мандата. Здесь время участвует в
   решении «выпускать или подавить дайджест».
**Status: `IMPLEMENTED` (как есть), дефект зафиксирован, код **не** исправлен в этой волне.**

---

## Исследовательские перспективы (META-R1)

Субъект — **децентрализованный выпуск**, а не «децентрализация вообще». Три разных
перспективы, каждая с первоисточником, уже записанным в репозитории:

| перспектива | школа | первоисточник | где он в репозитории | статус |
|---|---|---|---|---|
| **R-B кибернетика** — контур с обратной связью и ограничением канала: узел публикует счёт, а не содержимое, и приёмник решает по порогу, а не по факту | Ashby / Bernstein | **Rasch, B. & Born, J. (2013), «About sleep's role in memory», *Physiological Reviews* 93(2), DOI `10.1152/physrev.00032.2012`** — «сон — активная системная консолидация» | `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:346` | `PROPOSED` (первоисточник доступен; применение к дайджестам не записано) |
| **R-D нейронаука раннего обучения** — порог и окно вместо непрерывного потока: консолидация не является безусловным благом | Spelke / Kauffman | **Schaul, Quan, Antonoglou & Silver (2015), «Prioritized Experience Replay», arXiv `1511.05952`**; Tadros et al. (2022), sleep-like unsupervised replay | `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md:218` | `PROPOSED` |
| **R-E биология вычислений** — stigmergy: «pheromone markers» на путях, которые используются сильнее, как routing вместо централизованной таблицы | Bonabeau et al. (1999), *Swarm Intelligence* | `docs-v2/designs/DESIGN-NN-stigmergy-routing.md:4` | `PROPOSED` — **кода нет**: пакета `io/matrix/federation/stigmergy/` в репозитории не существует |

**META-R3 — пробел, названный прямо.** По всей документации репозитория (вне
`docs-v2/_raw/`) **нет ни одного первоисточника** по дифференциальной приватности,
k-анонимности, gossip-протоколу или деревьям Меркла. Поиск по `dwork|nissim|sweeney|
epidemic gossip|merkle` даёт только нерелевантные совпадения по слову «calibration».
Единственный источник темы — собственный текст
[REQUEST-decentralized-digests](../architecture/REQUEST-decentralized-digests.md) и
протокол `docs-v2/research/protocols/H-043.md`. **Это нарушение META-R3, и оно не
исправлено в этой волне.** Источники, которые должны быть добавлены (и которые **сейчас
не цитируются** — это список к добавлению, а не список прочитанного):
Dwork & Nissim — определение (ε, δ)-DP и механизм Лапласа; Sweeney — k-анонимность;
эпидемический gossip (Demirbas et al. и Barabási–Albert) — распределённый выпуск без
центра; деревья Меркла и transparency logs — современное значение слова «дайджест».

---

## Отложено

Ничего из перечисленного **не исправлено** в этой волне: это волна T4/T3 — она пишет
документы, а не код.

- **Дефект (a)** — `nodeId` игнорируется в `Anonymizer.recordContribution`
  (`Anonymizer.java:25-27`). Требуется решение владельца: считать k **узлами** или
  **вкладами**; от него зависит смысл всей спецификации. **Не исправлено.**
- **Дефект (b)** — дайджест окна сна есть `identityHash + wallClock`
  (`SleepCycle.java:79-81`); k-порог для него не открывается, и wall-clock стоит на пути
  решения. **Не исправлено.**
- **Выпуск дайджеста не `REACHABLE`.** `KnowledgeShare` не создаётся нигде; чтобы
  выпуск стал `SERVED`, нужен вызывающий на маршруте — это отдельное решение и отдельная
  волна. **Не исправлено.**
- **Транспорт — файловый.** `FileSystemMessageBus` (`MessageBus.java:32`); сеть и mTLS
  отложены (`docs-v2/designs/DESIGN-08-federation.md:30`). **Не исправлено.**
- **Бюджет ε на peer** (`REQUEST-decentralized-digests.md:134`) и **schema-binding**
  (`REQUEST-decentralized-digests.md:74`) — в коде нет. **Не реализовано.**
- **TLA+-контракт M4.** `Memory-M4-Causal` — `NO_PRODUCTION_CALLER` в
  `formal/spec-implementation-map.yml`; quorum остаётся на CRDT поверх `FnlPackage`, а не
  поверх дайджестов. **Не реализовано.**
- **Stigmergy-routing** (`DESIGN-NN-stigmergy-routing.md`) — кода нет. **Не реализовано.**
- **Расхождение `DESIGN-08-federation.md:19`** — документ называет `AnonymizerTest` и
  `ArtifactSignerTest`, которых нет. Исправление документа — не в моей зоне записи
  (реестры и индексы принадлежат координатору). **Не исправлено.**
- **Первоисточники (META-R3)** — см. § Исследовательские перспективы. **Не добавлены.**
- **Зерно дайджеста над базой знаний** не определено. **Не определено.**

---

См. [architecture/MODULES.md](../architecture/MODULES.md),
[architecture/DATA-AND-MEMORY-ARCHITECTURE.md](../architecture/DATA-AND-MEMORY-ARCHITECTURE.md),
[research/HYPOTHESES-NEW.md](../research/HYPOTHESES-NEW.md),
[research/protocols/H-043.md](../research/protocols/H-043.md),
[research/RESEARCH-INDEX.md](../research/RESEARCH-INDEX.md),
[research/NEGATIVE-RESULTS-INDEX.md](../research/NEGATIVE-RESULTS-INDEX.md).
