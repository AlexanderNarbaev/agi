# FOUNDATIONS — научные основания MATRIX

**Статус: living** (inline из `science/science/FOUNDATIONS.md`).

Единый реестр того, что именно проект заимствует из каждой дисциплины — в виде проверяемого механизма, алгоритма или метрики. Правило: заимствуется не метафора, а операционализируемое ядро.

## Математика

- **Булева алгебра**: TT / CLAUSESET / BDD как эквивалентные формы (SPEC-002); K_MAX=20; FCR F1/F2-минимизация (QUINE-MCCLUSKEY, ESPRESSO) для tooling.
- **Линейная алгебра над GF(2)**: Tsetlin сходимость по Гранмо (DESIGN-04) — операционная основа.
- **Расписание Левина**: `cauldron/LevinSchedule` — кандидаты scheduling без априорной сложности (DESIGN-11, RELATED-WORK).
- **Теория информации**: mutual information для feature selection в `brain/Viewpoint`.

## Физика (вычислительная)

- **Endianness / packed**: BIR-формы используют LSB-first packed long; верификация `bir/FpgaBackend` через byte-stride.
- **Энергия**: SPEC-001 Этап B заявляет 10⁴× меньше энергии — gating criterion H-009; честный замер требует wattmeter (отложено).

## Биология (информационная)

- **Hebbian learning**: local-signal Tsetlin rule approximation.
- **Cerebellum**: `signals/SignalModule` — быстрые LUT-преобразователи.
- **Memory M0..M4 иерархия**: вдохновлена моделью рабочей/эпизодической/семантической памяти; не отождествляется с ней биологически.

## Лингвистика и семантика

- **ТLA+ как формальный язык**: `formal/` каталог (см. `architecture/FORMAL-CONTRACTS.md`).
- **JSON-LD/OpenAPI**: `docs/openapi.yaml`, `api/ChatCompletionRequest/Response`, `EmbeddingRequest/Response`.

## Отвергнуто осознанно

- «AGI», «общий интеллект», «сверхразум» — запрещённые формулировки (CONSTITUTION VI).
- Чистый connectionism в рантайме — противоречит детерминизму.
- Random forest в путях решений — запрещён без seed-параметра.