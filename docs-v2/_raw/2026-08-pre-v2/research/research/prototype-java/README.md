# prototype-java — модули сигналов на BIR (Java, без зависимостей)

**Статус: living** · Дата: 2026-08-01 · Спека: `docs/design/DESIGN-06-signal-modules.md` · Платформенные правила: `docs/engineering/JAVA_NATIVE.md` · Протокол: `docs/agents/AGENTS-RESEARCH.md`.

Первый Java-прототип контура «медиа → биты → BIR-политика → вывод»: три IN-модуля (текст, число, аудио), реестр, CLAUSESET-политика с witness, OUT-рендер. Компилируется `javac` (проверено на OpenJDK 17; продакшн-линия — Java 25 + GraalVM native, JAVA_NATIVE).

## Структура

```
src/matrix/bir/        TtUnit (TT-форма), ClauseSet (DNF + witness + toHumanReadable)
src/matrix/io/         SignalModule (SPI), EncodedInput, ModuleRegistry (R1–R3, freeze)
src/matrix/io/modules/ TextLexiconEncoder, ThermometerEncoder, AudioBandEncoder, TextTemplateRenderer
src/matrix/demo/       SignalPipelineDemo — сквозной контур 3 модальностей
src/matrix/bench/      TtEvalBench — on-heap vs off-heap замер TT-eval
```

## Запуск

```bash
javac -encoding UTF-8 -d out $(find src -name '*.java')
java -Dfile.encoding=UTF-8 -cp out matrix.demo.SignalPipelineDemo
java -Dfile.encoding=UTF-8 -cp out matrix.bench.TtEvalBench
```

## Результаты (demo_output.txt, bench_output.txt)

- Контур: текст «Тревога давление насос стоп» + число 5.5 + тон 3-й полосы → все три клауза сработали, вердикт SHUTDOWN с witness по каждой модальности.
- Hamming-близость текстовых кодов: подмножество фраз → 4 бита (основа M1 recall, DESIGN-05 §3).
- TT eval: **0,77 нс** on-heap / **1,03 нс** off-heap direct ByteBuffer (20 млн оценок, k=16) — JVM-подтверждение оценки ANALYSIS §4.2 и цена обхода лимитов кучи JMM (+34%, JAVA_NATIVE §4).

## Границы прототипа

- Замеры без JMH (порядок величин); продакшн — JMH-гейты METRICS.md.
- AudioBandEncoder — naïve DFT O(N·bands); продакшн — FFT на Vector API.
- Продакшн-сборка — Maven/Gradle модули `signal-modules/**` по DESIGN-06 §6; здесь — чистый javac для воспроизводимости.