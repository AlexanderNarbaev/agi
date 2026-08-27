---
status: normative
---

Термины проекта с переводом на общепринятую терминологию. Брендовые имена сохранены, но каждая внешняя коммуникация обязана давать стандартный эквивалент.

| Термин | Стандартный эквивалент | Суть |
|---|---|---|
| **K_MAX** | Max neuron arity | Жёсткое ограничение k≤20 входов на BirUnit; не меняется без RFC. |
| **BirUnit** | Boolean function unit | Атомарная вычислительная единица: BIR-артефакт с контрактом evaluate; наследник термина «BirUnit». |
| **BirUnit** | Boolean function unit / truth-table neuron | Булева функция k≤20 входов как TT или дерево решений; наследник Маккалока–Питтса. |
| **BIR** | Boolean Intermediate Representation | Формы TT / CLAUSESET / BDD + заголовок (provenance, fidelity, Φ, хэш). |
| **TT** | Truth table | Плотная таблица истинности, k≤20, каноническая семантика; маппинг на FPGA LUT. |
| **CLAUSESET** | DNF/CNF clause set (Tsetlin-style) | Набор конъюнктивных клауз произвольной арности; канон — антицепь минимальных клауз. |
| **BDD** | Binary Decision Diagram | Канонична при фиксированном порядке переменных → точная проверка эквивалентности. |
| **BRC** | Boolean Reasoning Chain | Цепочка ≤5 шагов с witness-битами; аудируемое объяснение решения. |
| **BirNet** | Boolean network (DAG) | Исполняемая сеть единиц с топологической оценкой — «кластер». |
| **FNL** | Functional Neural Lobe | Кластер нейронов с единой функцией (макро-примитив). |
| **Capability** | Skill with contract | BirNet + ioSchema + метрики; минимальная единица обучения и замещения. |
| **Точка зрения** | Viewpoint (weighted ensemble) | Ансамбль Capabilities домена + роутер + калибратор уверенности. |
| **Профессионал** | Persona (certified viewpoint) | Точка зрения, сертифицированная в домене (MA-3+), с accessPolicy. |
| **Witness** | Witness bits / explanation | Подмножество входных битов, механически повлиявших на выход шага. |
| **PERCEPTION / DELIBERATION / RENDERING** | Sense → reason → act pipeline | Кодирование в биты → BRC-размышление → декодирование в действие. |
| **ФРОЗЕН-слой** | Immutable safety layer | Неизменяемые булевы артефакты, кодирующие Четыре запрета; hash-locked. |
| **Cauldron** | Self-creation protocol | Автономное рождение FNL: циклы рядов нарастающей сложности → Φ-гейт. |
| **TaskCell** | Task cell (ephemeral instance) | Детерминированный spawn, локальный контекст, смерть по бюджету. |
| **Route-drain** | Route drain (sleep) | Снятие роутинга на фазу сна; атомарный своп снапшотов. |
| **Eleutheria** | Controlled refusal ritual | Отказ от действия при конфликте требований с аксиомами. |
| **HADES** | Damage detection & recovery | Обнаружение повреждений кластеров и восстановление из журнала. |
| **Медиатор** | Homeostatic driver controller | Драйверы Energy/Curiosity/Safety; целевой — RequestMediator. |
| **Noosphere** | Federated knowledge commons | P2P-сеть: gossip + CRDT + византийский консенсус + кредитная модель. |
| **Developmental Loop** | Curriculum engine | Компетентность → задачи из ЗБР → затухание поддержки → MA-гейты. |
| **MA-уровни** | Staged autonomy levels | MA-0…MA-5: shadow → наставник; переходы через preregistered эксперименты. |
| **Φ (функционал)** | Monotone quality functional | Объявленная до запуска монотонная метрика цикла; ухудшение = откат. |
| **MDL-расчёт** | Minimum Description Length ratchet | Эталонный Φ: ΔL = L(модель)+L(данные‖модель) + штраф (k/2)·log n. |
| **ЗБР** | Zone of Proximal Development | Диапазон задач между «тривиально» и «невозможно». |
| **WAL** | Write-Ahead Log | Checkpoint сессии человек-агент. |
| **Genesis-протокол** | Authorized cloning protocol | Клонирование инстанса: авторизация + подпись + lineage ledger. |
| **Lineage ledger** | Causal-ancestry graph | Родословная всех инстансов и артефактов. |
| **Substrate Backend** | Hardware execution backend | Исполнитель BIR: JvmSimdBackend → FpgaBackend → QuantumBackend. |
| **Preregistration** | Registered report | Фиксация гипотезы и плана анализа до сбора данных. |
| **Консолидация (CLS-цикл)** | Sleep consolidation | Эпизоды → корпус → переобучение через Φ-гейт; сырьё забывается политикой. |
| **WNN / RAM-узел** | Weightless neural network (WiSARD/ULEEN) | Адресуемая таблица 2^n ячеек; однопроходовое обучение записью. |
| **SDM** | Sparse Distributed Memory (Kanerva) | Запись/чтение по радиусу Хэмминга через счётчики с порогом. |
| **VSA / HDC** | Vector Symbolic Architecture | Гипервекторы D=8192: bind=XOR, bundle=majority, ρ — порядок/роли. |
| **FCA** | Formal Concept Analysis (Ganter–Wille) | Решётка концептов над булевым контекстом «артефакт × атрибут». |
| **CMAC** | Cerebellar Model Articulation Controller (Albus) | Табличный аппроксиматор с перекрывающимися решётками. |
| **ReflexLayer** | Cerebellar reflex layer | «Мозжечковый» слой между PERCEPTION и DELIBERATION; FROZEN после сертификации. |
| **SignalModule** | Signal I/O module | Преобразователь «мысль ⇄ медиа»: контракт id/version/direction/mediaType/bitWidth/bitMeaning. |
| **ModuleRegistry** | Module registry (explicit, frozen) | Явная регистрация; детерминированный resolve; freeze после сборки. |
| **ELSP** | Element Link Security Profile | Ed25519, подпись сообщений, anti-replay seq, mTLS. |
| **EDGE-0…3** | Edge inference profile | Ноутбук → смартфон → очки/носимые → имплант; один BIR-артефакт. |
| **intESN** | Integer Echo State Network | Нейроны 3–4 бита; рекуррентность — циклический сдвиг; TT/WNN-readout. |
| **MonotoneDecoder** | Monotone function decoder | Идентификация монотонной функции по схеме максимального верхнего нуля. |
| **Hansel chains** | Hansel chains | Разбиение Bⁿ на C(n,⌊n/2⌋) цепей; дилворт-минимально. |
| **Three-valued verdict** | Kleene three-valued verdict | accept / reject / undecided; UNDECIDED заразителен по Клини. |
| **LTL-инвариант** | Linear temporal logic property | Проверяется model checking'ом в CI, не в рантайме. |
| **GUHA** | General Unary Hypotheses Automaton | Гайек–Гавранек, 1966: систематический обход пространства гипотез. |
| **LAD** | Logical Analysis of Data (Hammer 1986) | Бинаризация → паттерны (конъюнкции) → теория (ДНФ). |
| **Levin schedule** | Universal (Levin) search schedule | Чередование программ с долей 2^−l(p); оптимально для инверсии. |
| **Happened-before** | Lamport causal ordering (1978) | Частичный порядок: процесс + отправка→получение + транзитивность. |
| **ItemMemory** | Item memory (VSA) | u_HD(i) = splitmix64_expand(seed, i); seed входит в configHash. |
| **CPA-Event** | Hoare CSP event | Синхронная передача между процессами с блокировкой обеих сторон. |
| **Subsumption** | Brooks subsumption architecture | Слои: PROMOTED заморожен, suppress/inhibit без уничтожения. |
| **Episodic vs semantic memory** | Tulving 1972 | Эпизодическая — журнал M4; семантическая — верифицированный пул M3/FNL. |
| **FROZEN-FNL** | Immutable safety FNL | Хэш-locked; изменения только через RFC + консенсус. |

Next: при необходимости расширить список — открыть полный реестр или добавить термин сюда с переводом на стандартный эквивалент.