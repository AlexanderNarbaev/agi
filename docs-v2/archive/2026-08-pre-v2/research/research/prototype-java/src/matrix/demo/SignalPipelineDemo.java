package matrix.demo;

import java.util.List;
import matrix.bir.ClauseSet;
import matrix.io.EncodedInput;
import matrix.io.ModuleRegistry;
import matrix.io.SignalModule;
import matrix.io.modules.AudioBandEncoder;
import matrix.io.modules.TextLexiconEncoder;
import matrix.io.modules.TextTemplateRenderer;
import matrix.io.modules.ThermometerEncoder;

/**
 * Сквозной демо-контур (DESIGN-06 §5): регистрация модулей → кодирование
 * входов (текст, число, аудио) → BIR-политика (CLAUSESET) → рендер с witness.
 * Детерминизм: один прогон = один вывод; без RNG, без рефлексии.
 */
public final class SignalPipelineDemo {
    public static void main(String[] args) {
        // 1. Реестр модулей (явная регистрация, R1–R3)
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new TextLexiconEncoder("1.0.0", List.of(
                "тревога", "давление", "насос", "стоп", "норма", "запуск",
                "давление_насос", "насос_стоп", "тревога_давление")));
        registry.register(new ThermometerEncoder("1.0.0", new double[]{1.0, 2.0, 4.0, 8.0}));
        registry.register(new AudioBandEncoder("0.1.0", 4, new double[]{0.01, 0.1, 0.5}));
        registry.register(new TextTemplateRenderer("1.0.0"));
        registry.freeze();

        System.out.println("== Реестр модулей ==");
        for (SignalModule m : registry.list(SignalModule.Direction.IN))
            System.out.printf("  IN  %s@%s [%s] %d бит%n", m.id(), m.version(), m.mediaType(), m.bitWidth());
        for (SignalModule m : registry.list(SignalModule.Direction.OUT))
            System.out.printf("  OUT %s@%s [%s]%n", m.id(), m.version(), m.mediaType());

        // 2. PERCEPTION: кодируем входы разными модулями
        TextLexiconEncoder text = (TextLexiconEncoder) registry.resolve("text-lexicon", null).orElseThrow();
        ThermometerEncoder num = (ThermometerEncoder) registry.resolve("numeric-thermometer", null).orElseThrow();
        AudioBandEncoder audio = (AudioBandEncoder) registry.resolve("audio-bands", null).orElseThrow();
        TextTemplateRenderer renderer = (TextTemplateRenderer) registry.resolve("text-template", null).orElseThrow();

        EncodedInput eText = text.encode("Тревога давление насос стоп");
        EncodedInput eNum = num.encode(5.5);
        double[] window = new double[256];
        for (int i = 0; i < window.length; i++)
            window[i] = 0.8 * Math.sin(2 * Math.PI * 3 * i / 256.0); // тон полосы 3
        EncodedInput eAudio = audio.encode(window);

        System.out.println("\n== PERCEPTION (witness активных битов) ==");
        System.out.println("  текст : " + eText.activeBits);
        System.out.println("  число : " + eNum.activeBits);
        System.out.println("  аудио : " + eAudio.activeBits);

        // 3. DELIBERATION: BIR-политика. Доменный пример SCADA-рефлекса:
        // «тревога И давление» (текст) ИЛИ «x > 4.0» (число) ИЛИ «полоса 2 энергия > e2» (аудио) → SHUTDOWN
        ClauseSet policy = new ClauseSet(64, List.of(
                clause(new int[]{0, 1}, new int[]{}),   // x0=тревога, x1=давление (схема текст-модуля)
                clause(new int[]{3}, new int[]{})       // резервный клауз
        ));
        // Честная сборка контура: биты разных модулей НЕ смешиваются в одном векторе
        // без ioSchema (INV-P2). Здесь оцениваем политику покомпонентно:
        boolean textFire = policy.clauses().get(0).fires(eText.bits);
        boolean numFire = new ClauseSet.Clause(
                new long[]{1L << 2}, new long[]{0L}).fires(eNum.bits); // бит «x > 4.0»
        boolean audioFire = new ClauseSet.Clause(
                new long[]{1L << 8}, new long[]{0L}).fires(eAudio.bits); // полоса 2, энергия>e2

        boolean shutdown = textFire || numFire || audioFire;
        long[] verdict = { shutdown ? 2L : 1L }; // код 2 = SHUTDOWN, 1 = CONTINUE

        // 4. RENDERING: вердикт + witness
        List<String> witness = new java.util.ArrayList<>();
        if (textFire) witness.add("текст:тревога И давление");
        if (numFire) witness.add("число:x > 4.0");
        if (audioFire) witness.add("аудио:полоса2 энергия > e2");

        System.out.println("\n== DELIBERATION ==");
        System.out.printf("  textFire=%b numFire=%b audioFire=%b → вердикт=%d%n",
                textFire, numFire, audioFire, verdict[0]);
        System.out.println("\n== RENDERING ==");
        System.out.println("  " + renderer.render(verdict, witness, false));

        // 5. Hamming-близость кодов (основа M1 recall, DESIGN-05 §3)
        EncodedInput eText2 = text.encode("тревога давление");
        System.out.printf("%nHamming(«тревога давление насос стоп», «тревога давление») = %d бит%n",
                eText.hamming(eText2));
    }

    private static ClauseSet.Clause clause(int[] pos, int[] neg) {
        long[] p = {0L}, n = {0L};
        for (int b : pos) p[0] |= 1L << b;
        for (int b : neg) n[0] |= 1L << b;
        return new ClauseSet.Clause(p, n);
    }
}
