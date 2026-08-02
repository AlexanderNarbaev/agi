package matrix.io;

/**
 * Контракт модуля сигналов (DESIGN-06 §2): изолированный преобразователь
 * «мысль ⇄ медиа». Модуль сам использует BIR внутри, но снаружи виден только
 * контракт: направление, медиа-тип, схема битов, версия.
 *
 * Правила (AGENTS-MODULES): без рефлексии и classpath-сканирования (GraalVM),
 * детерминизм при фиксированной версии, чистые функции оценки.
 */
public interface SignalModule {

    enum Direction { IN, OUT }            // PERCEPTION / RENDERING
    enum MediaType { TEXT, NUMERIC, AUDIO, VIDEO, GRID, BINARY }

    /** Устойчивый идентификатор, напр. "text-lexicon". */
    String id();

    /** Семантическая версия артефакта ("1.0.0"); смена битовой схемы = major. */
    String version();

    Direction direction();

    MediaType mediaType();

    /** Размер выходного битового вектора (для IN) / входного (для OUT). */
    int bitWidth();

    /** Имена битов для INV-P3 (обратимая диагностика); null-бит запрещён к выставлению. */
    String bitMeaning(int bit);
}
