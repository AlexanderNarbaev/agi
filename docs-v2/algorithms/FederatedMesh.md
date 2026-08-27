# Federated Mesh (Noosphere M4: CRDT + Quorum + Kafka)

## Что

Узел федеративной сети Noosphere (M4): `MeshFederation` объединяет CRDT-состояние, real-time exchange и Kafka-канал для распределённой публикации `FnlPackage`-ов между инстансами MATRIX. Источник: `matrix-core/src/main/java/io/matrix/noosphere/MeshFederation.java` (плюс `Crdt`, `QuorumChecker`, `FnlPackage`, `GrowOnlySet`). Соответствует DESIGN-08 (федерация и ELSP).

## Конструкция узла

```
MeshFederation(nodeId, quorumThreshold, kafkaBootstrap, kafkaTopic):
 knownPeers = ConcurrentHashMap.newKeySet() // + nodeId при init
 exchange = new RealTimeExchange(nodeId) // in-process pub/sub
 kafkaProducer = createKafkaProducer(bootstrap) // null если bootstrap пуст
 localState = ConcurrentHashMap<name, FnlPackage>
 listeners = CopyOnWriteArrayList<MeshListener>
 quorumThreshold
```

`join()` / `leave()` / `addPeer(peerId)` рассылают уведомления через `notifyListeners(event, data)`. Исключения в листенерах не прерывают рассылку (только WARN-лог).

## Публикация FNL-пакета

`publish(FnlPackage pkg)`:

1. `localState.put(pkg.name(), pkg)` — локальная фиксация (CRDT-семантика через `GrowOnlySet`).
2. `exchange.publish(pkg)` — in-process fan-out подписчикам (канал `channel`).
3. Если `kafkaProducer != null` и `kafkaTopic != null` — асинхронная отправка в Kafka (`ProducerRecord(topic, pkg.name(), pkg.name())`; сериализация упрощённая до JSON-имени).
4. `notifyListeners("publish", pkg.name())`.

`hasQuorum()` делегирует `QuorumChecker.hasQuorum(knownPeers.size(), quorumThreshold)`. Дефолтное отношение `3/5 = 60%` задано в `QuorumChecker.DEFAULT_QUORUM_RATIO`.

## CRDT-инварианты

`Crdt<T>` интерфейс требует три алгебраических свойства:

- `a.merge(b) ≡ b.merge(a)` (коммутативность);
- `(a.merge(b)).merge(c) ≡ a.merge(b.merge(c))` (ассоциативность);
- `a.merge(a) ≡ a` (идемпотентность).

Эти свойства гарантируют, что реплики сходятся к одному состоянию вне зависимости от порядка доставки сообщений в mesh-е. `FnlPackage` иммутабельный record — `GrowOnlySet` поверх него удовлетворяет всем трём (добавление элемента идемпотентно, порядок несущественен).

## Потокобезопасность

- `knownPeers`, `localState` — `ConcurrentHashMap` (atomic add/replace).
- `listeners` — `CopyOnWriteArrayList` (итерация без блокировок; добавление дешёвое).
- `exchange` — внутренняя синхронизация в `RealTimeExchange`.
- Kafka producer — потокобезопасен по контракту библиотеки; `close()` освобождает ресурсы.

`peerCount()` и `localState()` возвращают немедленно-consistent снимки (через `size()` / `Collections.unmodifiableMap`). Subscribe через `subscribe(channel, handler)` — канал-уровень (мультикаст по типу пакета).

## Метрики / гейты

- Юнит `MeshFederationTest`: roundtrip `publish` → `subscribe` через exchange; `hasQuorum` на 3/5 peer-ов; `close()` без Kafka не бросает.
- Kafka-интеграция `KafkaIntegrationTest` помечена флейк на медленном хосте (см. WAL «известные проблемы»); замеры откладываются.
- Дизайн-инвариант DESIGN-08: ELSP anti-replay обеспечивается парным `ElspChannel` поверх mesh-узла (отдельный модуль).

## Открытые вопросы

- Сериализация `FnlPackage` сейчас `String(json)` — полноценный Avro/Protobuf контракт — отдельная задача (для K_MAX=20 см. CONSTITUTION II).
- mTLS peer-interconnect — внешняя зависимость, отложено (DESIGN-08).
- Связь с `Memory-M4-Causal` eventual consistency при quorum R/W — черновик в `noosphere/p2p/`.

Next: см. файл HashChain-Audit.md в той же папке для следующей темы.