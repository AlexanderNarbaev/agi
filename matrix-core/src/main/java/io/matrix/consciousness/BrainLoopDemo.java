package io.matrix.consciousness;

import java.util.Scanner;

/**
 * RUN 159 — BrainLoopDemo (CLI runner).
 *
 * <p>Standalone command-line runner that exercises the full
 * cognitive cycle. Useful for:
 * <ul>
 *   <li>Manual smoke tests during Phase β development</li>
 *   <li>FPGA/edge/Pocket hardware probes</li>
 *   <li>Demonstrating deterministic behaviour to stakeholders</li>
 * </ul>
 *
 * <p>Usage: {@code java io.matrix.consciousness.BrainLoopDemo "your query"}
 * Reads from stdin if no args provided.
 */
public final class BrainLoopDemo {

    public static void main(String[] args) {
        BrainLoopService svc = new BrainLoopService();
        System.out.println("[brain-loop] MATRIX cognitive loop — deterministic");
        System.out.println("[brain-loop] type 'quit' to exit");

        if (args.length > 0) {
            for (String arg : args) {
                runOne(svc, arg);
            }
            return;
        }

        try (Scanner sc = new Scanner(System.in)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    break;
                }
                runOne(svc, line);
            }
        }
    }

    private static void runOne(BrainLoopService svc, String input) {
        var r = svc.cycle(input);
        System.out.printf("[%s] action=%s arousal=%.3f focus=%d err=%.3f%n",
                r.accepted() ? "ACCEPT" : "DENY ",
                r.action(),
                r.arousal(),
                r.focusCount(),
                r.predictionError());
    }
}
