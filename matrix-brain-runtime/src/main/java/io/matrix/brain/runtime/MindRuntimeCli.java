package io.matrix.brain.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * MIND-W1 — Tiny REPL-style CLI to exercise the cognitive conductor.
 *
 * <p>Usage: {@code java -cp matrix-brain-runtime/build/classes/java/main:... io.matrix.brain.runtime.MindRuntimeCli "2+3"}</p>
 *
 * <p>Reads a single arg (or stdin line), runs the cognitive cycle, prints
 * JSON reply + BRC trace.</p>
 *
 * <p>RECON-W38.4 / ADR-2026-10-26-002: the CLI now exercises
 * {@link TrueMindCycle} — the same conductor the gateway serves — so a
 * developer reproducing a serving behaviour sees the serving behaviour. The
 * legacy {@link MindCycle} pipeline remains reachable behind an explicit
 * {@code --legacy} flag, because it is the only place the MCTS planning stage
 * is exercised.</p>
 */
public final class MindRuntimeCli {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /** Reads one line from stdin, or an empty string when stdin is closed. */
    private static String readLineFromStdin() {
        try (Scanner sc = new Scanner(System.in)) {
            return sc.hasNextLine() ? sc.nextLine() : "";
        }
    }

    public static void main(String[] args) {
        boolean legacy = args.length > 0 && "--legacy".equals(args[0]);
        String[] payload = legacy ? java.util.Arrays.copyOfRange(args, 1, args.length) : args;
        String input = payload.length > 0
            ? String.join(" ", payload)
            : readLineFromStdin();

        MindResult result = legacy
            ? new MindCycle().think(input)
            : new TrueMindCycle().think(input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", result.reply());
        out.put("confidence", result.confidence());
        out.put("duration_ms", result.durationMs());
        out.put("accepted", result.accepted());
        out.put("modulators_fired", result.modulatorsFired());
        out.put("trace", result.trace());
        try {
            System.out.println(MAPPER.writeValueAsString(out));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
