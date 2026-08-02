# Глоссарий MATRIX

Термины проекта с переводом на общепринятую терминологию. Брендовые имена сохранены, но каждая внешняя коммуникация обязана давать стандартный эквивалент.

| Термин | Стандартный эквивалент | Суть |
|---|---|---|
| **MPDT-нейрон** | Boolean function unit / truth-table neuron | Вычислительный примитив: булева функция k≤20 входов как таблица истинности или дерево решений. Наследник порогового нейрона Маккалока–Питтса. В целевой архитектуре — TT-форма BIR |
| **BIR** | Boolean Intermediate Representation | Единое представление булевых артефактов: формы TT / CLAUSESET / BDD + заголовок (provenance, fidelity, Φ, хэш). См. SPEC-002 |
| **TT** | Truth table | Плотная таблица истинности, k≤20, каноническая семантика, прямой маппинг на FPGA LUT |
| **CLAUSESET** | DNF/CNF clause set (Tsetlin-style) | Набор конъюнктивных клауз произвольной арности; первичная форма обучения (автоматы Цетлина) |
| **BDD** | Binary Decision Diagram | Решающая диаграмма; канонична при фиксированном порядке переменных → точная проверка эквивалентности |
| **BRC** | Boolean Reasoning Chain | Многошаговая цепочка булевых выводов (≤5 шагов) с witness-битами; аудируемое объяснение решения |
| **FNL** | Functional Neural Lobe | Кластер нейронов с единой функцией (макро-примитив: счётчик, компаратор, автомат) |
| **Cauldron** | Genesis/compression protocol | Автономное рождение FNL: сжатие кластеров в переиспользуемые функции |
| **HADES** | Damage detection & recovery | Обнаружение повреждений и восстановление кластеров из журнала событий |
| **Eleutheria** | Controlled refusal ritual | Механизм отказа от действия при конфликте требований с аксиомами |
| **FROZEN-слой** | Immutable safety layer | Неизменяемые булевы артефакты, кодирующие Четыре запрета; hash-locked |
| **Медиатор (InstanceMediator)** | Homeostatic driver controller | Существующий компонент (`mediator/InstanceMediator.java`): драйверы Energy/Curiosity/Safety; в целевой архитектуре — детерминированный RequestMediator (DESIGN-02 §6), драйверы — только сигналы куррикулума |
| **Noosphere** | Federated knowledge commons | P2P-сеть обмена знаниями: gossip + CRDT + византийский консенсус + кредитная модель; governance по принципам Остром |
| **Developmental Loop** | Curriculum engine | Управление развитием: оценка компетентности → задачи из ЗБР → затухание поддержки → гейты уровней зрелости |
| **MA-уровни** | Staged autonomy levels | Уровни зрелости MA-0…MA-5: от shadow-режима до наставника; переходы — через preregistered эксперименты |
| **Φ (функционал)** | Monotone quality functional | Объявленная до запуска монотонная метрика обучающего цикла; ухудшение = откат (CONSTITUTION, Статья III) |
| **MDL-рачет** | Minimum Description Length ratchet | Эталонный Φ: обновление знаний принимается только при уменьшении суммарной длины описания |
| **ЗБР** | Zone of Proximal Development (Выготский) | Диапазон задач между «тривиально» и «невозможно»; источник задач curriculum |
| **WAL** | Write-Ahead Log | Checkpoint сессии человек-агент (фокус, решения, следующее действие) |
| **Genesis-протокол** | Authorized cloning protocol | Клонирование инстанса: авторизация + подпись + lineage ledger + проверка конституции (Запрет 4) |
| **Lineage ledger** | Causal-ancestry graph | Родословная всех инстансов и артефактов; основа доверия в Noosphere |
| **Substrate Backend** | Hardware execution backend | Исполнитель BIR: JvmSimdBackend → FpgaBackend → QuantumBackend |
| **Preregistration** | Preregistration / registered report | Фиксация гипотезы и плана анализа до сбора данных (защита от HARKing) |
| **BirUnit** | Boolean function unit | Атомарная вычислительная единица: BIR-артефакт с контрактом evaluate (DESIGN-01); наследник термина «MPDT-нейрон» |
| **BirNet** | Boolean network (DAG) | Исполняемая сеть единиц с топологической оценкой — «кластер» (DESIGN-02) |
| **Capability** | Skill with contract | Именованный BirNet + ioSchema + метрики; минимальная единица обучения и замещения |
| **Точка зрения** | Viewpoint (weighted ensemble) | Взвешенный ансамбль Capabilities домена + роутер + калибратор уверенности (DESIGN-02 §4) |
| **Профессионал** | Persona (certified viewpoint) | Точка зрения, сертифицированная в домене (MA-3+), с профилем взаимодействия и accessPolicy (DESIGN-02 §5) |
| **BRC** | Boolean Reasoning Chain | Цепочка шагов размышления: каждый шаг — оценка BIR-узла с witness-битами и читаемым правилом (DESIGN-03 §3) |
| **Witness** | Witness bits / explanation | Подмножество входных битов, механически повлиявших на выход шага; основа объяснимости и отказов |
| **PERCEPTION / DELIBERATION / RENDERING** | Sense → reason → act pipeline | Три стадии контура запроса: кодирование в биты → BRC-размышление → декодирование в действие (DESIGN-03) |
| **Консолидация (CLS-цикл)** | Sleep consolidation | Ночное офлайн-окно: эпизоды → корпус → переобучение через Φ-гейт; забывание сырья политикой (DESIGN-05 §4) |
| **WNN / RAM-узел** | Weightless neural network (WiSARD/ULEEN) | Безвесовая сеть: узел — адресуемая таблица 2^n ячеек, структурно тождественна TT BirUnit; однопроходовое обучение записью по адресу (SUBSTRATE-MODELS §2.2) |
| **SDM** | Sparse Distributed Memory (Kanerva) | Разреженная распределённая память: запись/чтение по радиусу Хэмминга через счётчики с порогом; апгрейд recall в M1 (SUBSTRATE-MODELS §3, DESIGN-05 §3) |
| **VSA / HDC** | Vector Symbolic Architecture / Hyperdimensional Computing | Гипервекторы D=8192 бит: bind=XOR, bundle=majority, перестановка ρ для порядка/ролей; числовое хранение направленных знаний и сигнатур BRC-трасс (SUBSTRATE-MODELS §4.2) |
| **FCA** | Formal Concept Analysis (Ganter–Wille) | Формальный анализ понятий: решётка концептов над булевым контекстом «артефакт × атрибут»; иерархия доменов и кандидаты в инварианты (SUBSTRATE-MODELS §4.1) |
| **CMAC** | Cerebellar Model Articulation Controller (Albus) | Табличный аппроксиматор функций с перекрывающимися решётками; бинарный вариант квантуется в BIR с fidelity 1.0 — лёгкая модель мира (SUBSTRATE-MODELS §5.2) |
| **ReflexLayer** | Cerebellar reflex layer | «Мозжечковый» слой между PERCEPTION и DELIBERATION: expansion-перекодирование + µs-рефлексы, обучение по сигналу ошибки офлайн, FROZEN после сертификации (SUBSTRATE-MODELS §5.1) |
| **Модуль сигналов (SignalModule)** | Signal I/O module (codec with contract) | Изолированный преобразователь «мысль ⇄ медиа»: контракт id/version/direction/mediaType/bitWidth/bitMeaning; BIR внутри, биты+witness снаружи (DESIGN-06) |
| **ModuleRegistry** | Module registry (explicit, frozen) | Реестр модулей сигналов: явная регистрация (R1–R4), детерминированный resolve, freeze после сборки; без ServiceLoader/рефлексии — GraalVM-совместимо (DESIGN-06 §3, JAVA_NATIVE §2) |