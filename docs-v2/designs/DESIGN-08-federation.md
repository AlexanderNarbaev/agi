# DESIGN-08 — Федерация и ELSP

**Статус: normative** · пересмотр 2026-08-26 (v2 rebuild).

## Что

ELSP v1: Ed25519 сигнатура поверх `seq‖payload` со строго монотонным seq на стороне получателя (anti-replay). EDGE-0…EDGE-3 профили; EDGE-3 (имплант) вне горизонта. Аноним-экспорт с k-анонимностью + DP-noise; a-cyle среди пиров.

Профиль v2 — постквант **ML-DSA (FIPS 204, JEP 497)** без внешнего dep.

## Реализация

- `federation/ElspChannel` (Ed25519): `Envelope(seq, payload, signature)`, `sign/verifyAndAccept` со статическим CAS-циклом для lastAcceptedSeq.
- `federation/ElspChannelMlDsa` (эта серия): зеркальная семантика с `KeyPairGenerator("ML-DSA")` + `Signature("ML-DSA")`.
- `federation/ArtifactSigner` (Ed25519 sign/verify на артефактах знаний).
- `federation/Anonymizer` (k-анонимность).
- `noosphere/MeshFederation` (mesh-часть).

Тесты: `ElspChannelTest`/`ElspChannelMlDsaTest` (roundtrip/tamper/replay), `ArtifactSignerTest`, `AnonymizerTest`, `MeshFederationTest`.

## Метрики / гейты

- **ELSP готов**: roundtrip; tamper отвергается; replay отвергается; монотонная последовательность через CAS.
- **ML-DSA профиль v2** нативен в JDK25 — никаких внешних зависимостей.

## Отложено

- EDGE-3 (имплант) — вне горизонта.
- Постквант v2 **реализован** (эта серия); дальше — ансамблевая подпись + групповые (group signatures) — см. `architecture/FORMAL-CONTRACTS.md`.
- mTLS для peer-interconnect — внешняя зависимость.
