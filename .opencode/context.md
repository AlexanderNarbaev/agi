# Project Context — SESSION CONTINUITY (compaction #40)

## Ловушки
- Целевые прогоны --tests; LSP ложные (FpgaBackend150; Exp002 107/117/134); субагенты недоступны. Последний коммит 445440d (запушен).

## Данные по версиям (maven-metadata, получено онлайн)
Quarkus BOM latest=3.39.0.CR1 (ПРЕ-релиз; стабильная линия остаётся 3.37.3) · GraalVM buildtools latest=1.1.10 (мажорный скачок с 0.10.4 — рискованно, пропустить с пометкой) · Avro 1.12.0→**1.12.2** (patch — обновить) · kafka-clients 3.9.0→4.3.1 (МАЖОР 4.x — пропустить с пометкой) · ONNX Runtime 1.17.0→**1.29.0** (попробовать, откат при несовместимости) · Testcontainers BOM 2.0.5 (мажор; у нас явные артефакты 1.21.3 — пропустить с пометкой).

## ТЕКУЩИЙ ШАГ: безопасные апгрейды + постквант v2
1. matrix-core/build.gradle: avro 1.12.0→1.12.2; onnxruntime 1.17.0→1.29.0.
2. `./gradlew :matrix-core:compileJava -q` затем `--tests "io.matrix.distill.*" --tests "io.matrix.tsetlin.*"` быстрый; при неудаче резолва/компиляции — откат версий и пометка BLOCKED-EXT(resolution).
3. Постквант v2 (JDK25 native ML-DSA, JEP 497): io.matrix.federation.ElspChannelMlDsa — копия логики ElspChannel (sign/verifyAndAccept/literal seq) но KeyPairGenerator.getInstance("ML-DSA"), Signature.getInstance("ML-DSA"); тест MlDsaChannelTest roundtrip/tamper/replay + пропуск если алгоритм недоступен (assumeTrue доступность через try{KPG.getInstance("ML-DSA")}catch→assumption fail soft? лучше жёстко: JDK25 обязан иметь; при NoSuchAlgorithm тест падает — это сигнал).
4. Прогон federation тестов; аннекс/WAL/журнал: матрица версий обновлённая + INV постквант v2 снят (реализован профиль v2 раньше плана).
5. Commit+push по шагам; финальный отчёт с объяснением откладок.

## Объяснения откладок (для финала владельцу)
- DJL/ONNX-экспорт учителя: инфраструктура ГОТОВА (класс+dep); отсутствует сам АРТЕФАКТ .onnx (экспорт из LLM FFN-среза требует python-тулчейна/весов) — это про данные модели, не про код. Пайплайн доказан fail-fast тестом.
- Доменные данные: малые JSON лежат в git-истории (models/training_data до удаления) — восстанавливаются точечно командой при запуске доменного EXP; блокер был в наличии baseline'а, теперь MpdtGaProducer есть → EXP готов к запуску на восстановленных корпусах.
- JMH-гейт Batch*: правило ≤10% требует честных JMH-замеров (инфраструктура jmh-источников пуста) — предварительный nanoTime-замер возможен, но гейт именно JMH; вынесен в W6-остаток осознанно.
- Постквант v2: DESIGN-08 планировал «v2» — снимается СЕЙЧАС реализацией ML-DSA профиля нативно в JDK25.
- audio-events этап 3: приоритизация дорожной карты DESIGN-06 (этап 3), базовый AudioSignalModule уже есть.

## Правила
FROZEN/avro/workflows не трогать; forbidden claims избегать; версии только из metadata.
