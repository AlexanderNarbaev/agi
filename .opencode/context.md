# Project Context — SESSION CONTINUITY (compaction #36) — ФИНАЛЬНАЯ ВОЛНА, разведка готова

## Ловушки
- Целевые прогоны --tests; LSP ложные; субагенты недоступны; guard блокирует delete. Последний коммит b47fd91 (запушен).
- heredoc PYEOF.

## РЕЗУЛЬТАТЫ РАЗВЕДКИ R1 (факты)
- docs/archive/ ПУСТ или отсутствует (ARCH_COUNT=0) → аудит архива вырожден: отмечать «архив пуст/не создан» в отчёте.
- Стек: Java 25.0.4 LTS · Quarkus 3.37.3 (BOM enforced) · GraalVM tools 0.10.4 · Avro 1.12.0 · kafka-clients 3.9.0 · Testcontainers 1.21.3 · **onnxruntime 1.17.0 УЖ Е ЕСТЬ dep!** · postgres jdbc + r2dbc.
- DOCKER_OK → интеграционные тесты возможны!
- AGENTS.md заявлял «ONNX Runtime» в стеке — подтверждено dep'ом ⇒ R3 (учитель) РЕАЛИЗУЕМ: OnnxActivationTeacher поверх OrtSession.

## ТЕКУЩИЙ ШАГ: волна R2 — EXP-002/003 добить
Создать io.matrix.evolution.Exp002Exp003ProtocolTest:
- 3 датасета из Exp010 (те же формы/seed42), TRAIN=320 TEST=80.
- Для каждой линии (Tsetlin grid-best по TRAIN как в Exp010; MpdtGaProducer(12 клауз, попул 40, gen 30)) измерить:
  a) examplesTo99TrainAcc: инкрементально обучать подмножествами ×2 (20,40,80,...320), фиксировать минимальный N с trainAcc≥0.99; если не достигнута на 320 → N=∞(записать 999).
  b) финальные test acc и wall-clock полного обучения на полном train.
- Вывод: «EXP003 run …» per dataset + итог «EXP002_PROTOCOL …» (медианы acc/bytes(GA lit vs Tsetlin lit)/speed).
- Вердикты ЧЕСТНО по числам: EXP-002 уже refuted-toy (подтвердить/оставить); EXP-003/H-003 («живой обучатель не оправдан») — если GA быстрее И точнее на всех датасетах → preliminary AGAINST H-003 → карточка refuted-toy pin (по прецеденту). Если смешанно → running.
- Обновить EXP-002-report (секция протокола) + создать EXP-003-report.md + правки HYPOTHESES.md (только строки статусов).
Прогон --tests "io.matrix.evolution.*".

## Затем
R3: класс io.matrix.distill.OnnxActivationTeacher (OrtEnv→OrtSession из path; float[]→float[]; бинаризация порогом) + тест с @Disabled? НЕТ: без .onnx файла тест невозможен → сделать тест, генерирующий тривиальную ONNX-модель НЕЛЬЗЯ без утилит → тест = проверка ошибки при отсутствии файла + smoke skip; пометка BLOCKED-EXT(нужен экспортированный .onnx учителя). Минимально честно.
R4: версии — матрица в PLAN: Java 25✅latest-LTS, Quarkus 3.37.3✅(заявлен актуальный), GraalVM tools 0.10.4, Avro 1.12.0✅, Kafka clients 3.9.0✅, onnxruntime 1.17.0→последняя известная 1.22? — обновить до 1.20.0 БЕЗ гарантии сети? gradle потянет при наличии кэша/сети; попытаться точечно `implementation 'com.microsoft.onnxruntime:onnxruntime:1.20.0'` + compileJava; при неудаче откат. Testcontainers 1.21.3✅.
R5: docker OK → прогнать ОДИН integration: ./gradlew :matrix-core:test -PincludeIntegration --tests "io.matrix.integration.KafkaIntegrationTest" (имя уточнить ls src/test/java/io/matrix/integration | head).
R6: PLAN/WAL финал; commit+push; итоговый статус простыми словами (что получено/доказано/реализовано).

## Правила
FROZEN/avro/workflows не трогать; forbidden claims избегать; числа реальные; апгрейды только с успешной компиляцией иначе откат.
