# Hash-Chain Audit (SHA-256 tamper-evident append-only log)

## Что

Append-only реестр с tamper-evident гарантиями: каждый `HashLink` ссылается на хеш предыдущего; SHA-256 поверх канонической упаковки полей; любая ретроактивная мутация инвалидирует все последующие ссылки. Источник: `matrix-core/src/main/java/io/matrix/audit/HashChain.java` (плюс `HashLink`). Соответствует CONSTITUTION VIII (явность и аудит) и `FORMAL-CONTRACTS.md` (HashChain TLA+ CFG).

## Структура ссылки

`HashLink` — immutable record `(sequence, previousHash, payloadHash, timestampMs, extra, hash)`. Каноническое кодирование для SHA-256:

```
[prevLen | prevHash | seq(8B) | payLen | payloadHash | ts(8B) | extraLen | extra]
```

Каждое поле length-prefixed (4 байта) — исключает неоднозначность между границами. `sha256Hex(...)` — дайджест payload-байт отдельно (для быстрой выборки без полного verify). `verify()` использует `constantTimeEquals` против timing-side-channels.

## Конструкция

```
HashLink.extend(previous, payload, extra):
 previousHash = previous?.hash() ?? "0".repeat(64) // genesis
 sequence = (previous?.sequence ?? -1) + 1
 timestampMs = clock.get() // System.currentTimeMillis
 payloadHash = sha256Hex(payload.utf8Bytes)
 hash = computeHash(prevHash, seq, payHash, ts, extra)
 return new HashLink(...)
```

`genesisHash() = "0".repeat(64)` — известный предшественник для первого звена.

## API цепочки

`HashChain`:

- `append(payload, extra)` — под `ReentrantLock` создаёт `HashLink.extend(latest, …)`, добавляет в `links`, обновляет `volatile HashLink latest`.
- `verify()` — под тем же lock: для каждого звена проверяется `link.previousHash == expectedPrev` и `link.verify()`. Начальное `expectedPrev = HashLink.genesisHash()`.
- `restore(list)` — устанавливает цепочку из сохранённого списка; предварительно валидирует всю последовательность; `IllegalArgumentException("Broken chain at seq=…")` при нарушении.
- `snapshot()` — защитная копия всех звеньев для персистентности (Postgres, Kafka, S3 — паттерн см. `privacy/storage/TombstoneStorage`).

## Потокобезопасность

Все мутирующие операции под `ReentrantLock`. Чтение `latest()` — lock-free через `volatile`. `links` (`List<HashLink>`) — внутренний `ArrayList`, не thread-safe для итерации (только под lock через `snapshot()`/`verify()`/`restore()`). `clock` — `Supplier<Long>`, инжектируется (для тестов с детерминированным временем).

## Контракт целостности

`verify()` проверяет три инварианта:

1. Каждый `link.hash` соответствует каноническому кодированию полей — нет in-place мутации.
2. Каждый `link.previousHash` совпадает с предыдущим `link.hash` — нет gap и нет fork.
3. Первое звено ссылается на genesis — цепь начинается корректно.

`summary()` возвращает компактное `HashChain[size=N latest=HashLink[seq=… ts=… hash=…]]` для audit-log строк.

## Метрики / гейты

- Юнит `HashChainTest`: append + verify на длинной цепи; tamper одного среднего звена ⇒ `verify() == false`; `restore` с битой последовательностью ⇒ `IllegalArgumentException`; детерминизм SHA-256.
- TLA+ спека `HashChain` (CFG) — соответствие коду подтверждено вручную; полная TLC-проверка отложена.
- Применения в кодовой базе: FROZEN-FNL attestations, GDPR tombstones, lightweight "blockchain" без консенсуса (см. комментарий `HashLink.java`).

## Открытые вопросы

- Merkle-anchor на snapshot (батч-включение N звеньев в одно) — отложено.
- Параллельный append (lock-free через CAS на `latest`) — нужен benchmark на горячих путях.
- Полная TLC-проверка `HashChain` TLA+ в CI — отдельная задача (нужен `tlc` в pipeline).

Next: см. файл Legal-Axioms.md в той же папке для следующей темы.