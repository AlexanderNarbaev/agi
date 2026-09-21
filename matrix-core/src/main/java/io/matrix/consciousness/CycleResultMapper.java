package io.matrix.consciousness;

import java.util.function.Function;

/**
 * RUN 289 — CycleResultMapper (result transformation).
 *
 * <p>Transforms BrainLoopService.CycleResult into different
 * output formats.
 */
public final class CycleResultMapper {

    /** Map to a simple accepted/denied string. */
    public static String toStatusString(BrainLoopService.CycleResult r) {
        return r.accepted() ? "accepted" : "denied";
    }

    /** Map to a status code. */
    public static int toStatusCode(BrainLoopService.CycleResult r) {
        return r.accepted() ? 200 : 403;
    }

    /** Map to a summary string. */
    public static String toSummary(BrainLoopService.CycleResult r) {
        return String.format("status=%s arousal=%.2f focus=%d",
                r.accepted() ? "ok" : "denied",
                r.arousal(),
                r.focusCount());
    }

    /** Apply a custom mapper. */
    public static <T> T map(BrainLoopService.CycleResult r,
                            Function<BrainLoopService.CycleResult, T> mapper) {
        return mapper.apply(r);
    }
}
