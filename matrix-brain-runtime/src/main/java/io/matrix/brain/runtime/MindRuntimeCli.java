package io.matrix.brain.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * MIND-W1 — Tiny REPL-style CLI to exercise {@link MindCycle}.
 *
 * <p>Usage: {@code java -cp matrix-brain-runtime/build/classes/java/main:... io.matrix.brain.runtime.MindRuntimeCli "2+3"}</p>
 *
 * <p>Reads a single arg (or stdin line), runs the cognitive cycle, prints
 * JSON reply + BRC trace.</p>
 */
public final class MindRuntimeCli {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        String input;
        if (args.length > 0) {
            input = String.join(" ", args);
        } else {
            try (Scanner sc = new Scanner(System.in)) {
                input = sc.hasNextLine() ? sc.nextLine() : "";
            }
        }
        MindCycle mind = new MindCycle();
        MindResult result = mind.think(input);
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
