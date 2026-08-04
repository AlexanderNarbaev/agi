# Документация MATRIX — карта

## Нормативный каркас (корень)

| Документ | Статус | Назначение |
|---|---|---|
| [../CONSTITUTION.md](../CONSTITUTION.md) | FROZEN | Аксиомы, инварианты, governance |
| [../AGENTS.md](../AGENTS.md) | normative | Протокол работы агентов и разработчиков |
| [../WAL.md](../WAL.md) | ephemeral | Checkpoint сессий |
| [../README.md](../README.md) | normative | Обзор, быстрый старт, API |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | normative | Участие |

## Видение и архитектура

| Документ | Назначение |
|---|---|
| [vision/ARCHITECTURE.md](vision/ARCHITECTURE.md) | Целевая архитектура: BIR, Developmental Loop, MA-уровни, Noosphere, субстраты |
| [vision/OPEN_PROBLEMS.md](vision/OPEN_PROBLEMS.md) | Исследовательское видение без обещаний сроков |
| [vision/GOALS-REQUIREMENTS.md](vision/GOALS-REQUIREMENTS.md) | Консолидированные цели (G-A…G-G), функциональные (FR-01…15) и нефункциональные (NFR-01…14) требования с трассировкой к документам |

## Научные основания

| Документ | Назначение |
|---|---|
| [science/FOUNDATIONS.md](science/FOUNDATIONS.md) | Что заимствовано из математики, физики, биологии, химии, медицины, психологии, педагогики, социологии — с механизмами и метриками; что отвергнуто и почему |
| [science/SUBSTRATE-MODELS.md](science/SUBSTRATE-MODELS.md) | Субстратные модели: конечные автоматы, свёрточные схемы, WNN, комбинаторика памяти (SDM), FCA-классификация знаний, VSA-векторы, мозжечковый ReflexLayer, CMAC, композиция кластеров, советская школа алгоритмов (МГУА, потенциальные функции, АВО, алгебра автоматов Глушкова), физика вычислений (Ландауэр, реверсивная логика), VC-теория и MDL как основания Φ-гейтов, азиатские школы (неокогнитрон, инфо-геометрия Амари/Ченцова, метод У, Tianjic) и киевская APNN-школа — с переносом в BIR и метриками |
| [science/ALGORITHM-ATLAS.md](science/ALGORITHM-ATLAS.md) + волна 4: [§24–§25](science/ALGORITHM-ATLAS-WAVE4.md), [§26–§28](science/ALGORITHM-ATLAS-WAVE4B.md), [§29–§32](science/ALGORITHM-ATLAS-WAVE4C.md) + волна 5: [§33–§34](science/ALGORITHM-ATLAS-WAVE5.md), [§35–§37](science/ALGORITHM-ATLAS-WAVE5B.md) | Атлас алгоритмических школ, том 2 (шесть файлов: §1–§23 — волны 1–3; §24–§32 — волна 4; §33–§37 — волна 5): индийские школы (Navya-Nyāya, Махаланобис/ISI), категории, квантовая query-сложность (QuantumBackend), сотовые автоматы (Тоффоли–Марголус), резервуары (intESN), академии СНГ (Ташкент, Тбилиси, Марков), новосибирская школа (Мальцев–Ершов), казахская (Таиманов, Тайцлин), польская (многозначная логика), бразильская (параконсистентность), венгерская (энтропии Реньи), израильская (темпоральная логика Пнуэли); глубокие спецификации MonotoneDecoder (цепи Ханселя) и бинарного резервуара; волна 3: чешская (Вопенка, ГУХА Гайека–Гавранека), финская (Хинтикка: GTS/IF, логика вопрошания), французская (Кольмеро: Prolog/CLP), австрийская (Гёдель: полнота/неполнота/нумерация), армянская (Мергелян, Варшамов, Заславский), азербайджанская (Заде, АН АзССР); волна 4: китайская (моистский канон, Ву Вэньцзюнь, Цянь Сюэсэнь/HWME), японская (LAD Хаммер–Крама–Ибараки, субструктурные логики Оно–Комори), румынская (Мойсил: алгебра переключательных схем), московская (Колмогоров: сложность и суперпозиция; Левин: универсальный поиск), нидерландская (Брауэр–Хейтинг BHK, Дейкстра wp), британско-шотландская (Буль, Плоткин SOS, Бёрстолл–Гоген институции), индийская II (джайнская сьядвада, трайрупья Дхармакирти), американская (Пирс EG, Чомски, Михальски AQ); волна 5: немецкая (Фреге Begriffsschrift, Гильберт/финитаризм, Генцен Hauptsatz, Цузе Z3/Plankalkül), скандинавская (Нюгор–Даль Simula, Наур BNF/snapshots, Мартин-Лёф ITT), французская II + швейцарская (Кокан–Юэ–Полен CIC/Coq, Вирт Pascal/Oberon), теория управления (Понтрягин принцип максимума, Беллман DP, Винер кибернетика) — с переносом, честными границами, H-015…H-022 |
| [science/SYSTEM-SYNTHESIS.md](science/SYSTEM-SYNTHESIS.md) | Единая система: сшивка трёх томов атласа в стек механизмов L0–L10 с полной трассировкой школа → механизм → DESIGN → FR/NFR → гипотеза; сводные честные границы; порядок чтения для автономного агента |

## Спецификации фич

| Спека | Компонент | Этап ROADMAP |
|---|---|---|
| [spec/SPEC-002-boolean-compute-layer.md](spec/SPEC-002-boolean-compute-layer.md) | **Keystone.** BIR: формы TT/CLAUSESET/BDD, компилятор, верификатор, producers, субстратные бэкенды | 1 |
| [spec/SPEC-001-weight-conversion.md](spec/SPEC-001-weight-conversion.md) | Карантин случайной конвертации → дистилляция по активациям в BIR | 0, 1 |
| [spec/SPEC-000-developmental-loop.md](spec/SPEC-000-developmental-loop.md) | Curriculum engine, MA-гейты, scaffolding с затуханием | 3 |
| [spec/SPEC-003-knowledge-topology.md](spec/SPEC-003-knowledge-topology.md) | Ricci-анализ графов знаний, drift-fingerprint, curriculum-ordering | 3 |

## Проектные спецификации (как реализовать)

Алгоритмический слой между спеками и кодом: структуры данных, псевдокод, сложности, бюджеты, приёмочные тесты. Спека говорит «что и зачем», design — «как именно».

| Документ | Уровень | Содержание |
|---|---|---|
| [design/DESIGN-01-units.md](design/DESIGN-01-units.md) | атом | Гибридная вычислительная единица: TT/CLAUSESET/BDD — представления, алгоритмы evaluate, компилятор форм, макет памяти |
| [design/DESIGN-02-composition.md](design/DESIGN-02-composition.md) | сеть → персона | BirNet (DAG), Capability, точка зрения (ансамбль+калибровка), профессионал (Persona + MA), разрешение конфликтов, карантин |
| [design/DESIGN-03-pipeline.md](design/DESIGN-03-pipeline.md) | сквозной контур | Ввод → биты (кодировщики), размышление (BRC-цикл с бюджетами), вывод (декодеры); OpenAI-фасад, прокси, MCP; изоляция внешних зависимостей |
| [design/DESIGN-04-learning.md](design/DESIGN-04-learning.md) | офлайн-обучение | TsetlinTrainer (формулы), дистилляция (конвейер TREPAN), интерактивное обучение с учителем, предобучение Persona, fading |
| [design/DESIGN-05-memory.md](design/DESIGN-05-memory.md) | состояние | Слои M0–M4, recall, консолидация («сон»), забывание/tombstone, журнал событий, дрейф, коллективная память |
| [design/DESIGN-06-signal-modules.md](design/DESIGN-06-signal-modules.md) | периферия | Модули входящих/исходящих сигналов: контракт SignalModule, реестр, медиа-линейки (текст/число/аудио/видео/сетки/VSA), формы деплоя, измерения Java-прототипа |
| [design/DESIGN-07-lifecycle.md](design/DESIGN-07-lifecycle.md) | жизненный цикл | Жизненный цикл элементов: Cauldron (самосоздание, МГУА-ряды + Φ-гейт, off-heap арены), FNL, TaskCell (эфемерные задачные инстансы), постоянное обучение и proactive-активность, сон с route-drain, Kubernetes Operator (CRD) |
| [design/DESIGN-08-federation.md](design/DESIGN-08-federation.md) | федерация/безопасность | Глобальная федерация: эдж-профили EDGE-0…3, криптография протокола (подписи, anti-MitM/replay, ротация), обезличенный экспорт в пул (k-анонимность, DP, opt-in), импорт свежих нейронов через M4-гейт |
| [design/DESIGN-09-monotone-decoder.md](design/DESIGN-09-monotone-decoder.md) | офлайн-обучение | MonotoneDecoder: идентификация монотонной булевой функции по цепям Ханселя (минимакс-оптимально, φ(n) запросов), детерминированный оракул, resume при PARTIAL, инварианты INV-MD1/2/3, приёмочные тесты AT-MD1…MD5 (H-016) |
| [design/DESIGN-10-binary-reservoir.md](design/DESIGN-10-binary-reservoir.md) | состояние/периферия | Бинарный резервуар (intESN-линия): состояние D=8192 бит (1 КБ), шаг bundle(Sh(x,1), u_HD), ItemMemory с фиксированным seed, TT/WNN-readout однопроходово, INV-BR1…BR4, тесты AT-BR1…BR6 (H-015) |

## Исследования

| Документ | Назначение |
|---|---|
| [research/HYPOTHESES.md](research/HYPOTHESES.md) | Пререгистрированные гипотезы H-001…H-022 и карточки экспериментов EXP-001…022 |
| [research/METRICS.md](research/METRICS.md) | Реестр метрик: формула, источник, команда, порог; теоретические и прототипные значения |
| [research/ANALYSIS-laptop-feasibility.md](research/ANALYSIS-laptop-feasibility.md) | Пробелы/возможности + расчёт ноутбучной реализуемости vs локальные LLM |
| [research/prototype/](research/prototype/) | Воспроизводимый прототип BIR (код + результаты, Python/numpy) |
| [research/prototype-java/](research/prototype-java/) | Java-прототип модулей сигналов на BIR (javac, без зависимостей; замеры on/off-heap) |
| [research/papers/](research/papers/) | Научные публикации (автогенерация из артефактов) + протокол |
| research/reports/ | Отчёты экспериментов (включая отрицательные) |

## Инженерия

| Документ | Назначение |
|---|---|
| [engineering/ROADMAP.md](engineering/ROADMAP.md) | Цели G1–G6, этапы 0–5 с критериями выхода, стратегия миграции |
| [engineering/C4.md](engineering/C4.md) | C4-модель: контекст системы, контейнеры узла, компоненты BIR-ядра и Cauldron, развёртывание по эдж-профилям (Mermaid); соглашения трассировки блоков |
| [engineering/JAVA_STACK.md](engineering/JAVA_STACK.md) | Технологический стек: принято/отвергнуто, ONNX-контракт |
| [engineering/ADR-001-matrix-vs-rag-system-roles.md](engineering/ADR-001-matrix-vs-rag-system-roles.md) | rag-system как внешняя зависимость: заимствование принципов, интеграция по контракту |
| [engineering/ADR-002-bir-canonical-representation.md](engineering/ADR-002-bir-canonical-representation.md) | BIR как единое каноническое представление: TT/CLAUSESET/BDD + заголовок; верификатор на каждой границе |
| [engineering/ADR-003-tsetlin-producers-over-living-mechanisms.md](engineering/ADR-003-tsetlin-producers-over-living-mechanisms.md) | Автоматы Цетлина как базовые producers; ГА/трёхфакторные — только через H-002/H-003 |
| [engineering/ADR-004-cauldron-budget-scheduling.md](engineering/ADR-004-cauldron-budget-scheduling.md) | Бюджетер рядов Cauldron: расписание Левина (база), сопряжённый DP (H-021), гомеостат коридоров (H-022), фолбэк-матрица |
| [engineering/ADR-005-three-valued-gate-verdicts.md](engineering/ADR-005-three-valued-gate-verdicts.md) | Трёхзначный вердикт гейтов accept/reject/undecided: witness обязателен, UNDECIDED заразителен, два класса отказа |
| [engineering/JAVA_NATIVE.md](engineering/JAVA_NATIVE.md) | GraalVM native-image: правила кода; ограничения JMM и кучи (off-heap, публикация, false sharing); путь субстратов JVM→native→FPGA→quantum |
| [agents/](agents/) | Специализированные протоколы для агентов: модули сигналов (AGENTS-MODULES), исследования и статьи (AGENTS-RESEARCH) |

## Операционные документы

[API.md](API.md) · [DEPLOYMENT.md](DEPLOYMENT.md) · [GLOSSARY.md](GLOSSARY.md)

## Архив

`archive/` — прежние спецификации (L0–L23), долгосрочные планы, синтезы исследований. Сохранены как история; нормативной силы не имеют. При противоречии действует текущий каркас.
