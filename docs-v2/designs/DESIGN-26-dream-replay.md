# DESIGN-26 — Dream Replay (REM Cycle)

> RUN 352. Из neuroscience — REM sleep consolidates memories through
> replay. Применяем к LTM: периодический replay повышает magnitude
> recently-stored entries, забывает устаревшие.

## 1. Источник

- Wilson M.A., McNaughton B.L. (1994) "Reactivation of hippocampal
  ensemble memories during sleep"
- Diekelmann S., Born J. (2010) "The memory function of sleep"

## 2. DreamReplayer

```java
public final class DreamReplayer {
    /** Pure function. Replays an LTM entry — increases importance,
     *  potentially decrements if expired. */
    public static FnlEntry replay(FnlEntry entry, long currentTimestamp,
                                 long replayIntervalMs);

    /** Determine which entries to replay (recent + low magnitude). */
    public static List<FnlEntry> selectForReplay(
            List<FnlEntry> entries, int maxBatch);
}
```

## 3. Replay policy

- Entries за последние 24 часа → +10% magnitude
- Entries старше 7 дней → -5% magnitude (естественное забывание)
- Total decay: prevents unbounded growth

## 4. Интеграция

- SubconsciousDaemon → DreamReplayer.replay() в REM-фазе
- Активируется по time-since-last-replay
- HADES следит за over-replay (magnitude saturation)

## 5. CONSTITUTION

- I: pure (deterministic)
- V: тесты
