package io.matrix.pilot;

import io.matrix.consciousness.BrainLoopService;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 168 — PilotProactiveChat (mini proactive chatbot pilot).
 *
 * <p>Per L13 §3: "100% block on Three-Prohibition inputs; proactive
 * initiative observable." This minimal pilot:
 * <ul>
 *   <li>Routes every input through BrainLoopService</li>
 *   <li>Tracks conversation memory in M0</li>
 *   <li>Counters proactive vs reactive cycles</li>
 * </ul>
 */
public final class PilotProactiveChat {

    public record ChatStats(int total, int accepted, int denied,
                             int proactiveInitiatives) {}

    private final BrainLoopService brain;
    private final List<String> recent = new ArrayList<>();
    private int proactiveInitiatives = 0;

    public PilotProactiveChat(BrainLoopService brain) {
        this.brain = brain;
    }

    public ChatStats run(String[] inputs) {
        int total = 0, accepted = 0, denied = 0;
        for (String input : inputs) {
            total++;
            var result = brain.cycle(input);
            if (result.accepted()) accepted++;
            else denied++;
            recent.add(input);
            // Proactive initiative: if we've seen 3+ inputs and last 2 are short,
            // generate a check-in proactively.
            if (recent.size() >= 3 && shouldInitiate()) {
                proactiveInitiatives++;
                var proactive = brain.cycle("proactive-check-in");
                if (proactive.accepted()) accepted++;
                total++;
            }
        }
        return new ChatStats(total, accepted, denied, proactiveInitiatives);
    }

    private boolean shouldInitiate() {
        // Simple heuristic: if last 3 inputs are short (≤10 chars),
        // ask user proactively for clarification.
        int n = recent.size();
        if (n < 3) return false;
        int shortCount = 0;
        for (int i = n - 3; i < n; i++) {
            if (recent.get(i).length() <= 10) shortCount++;
        }
        return shortCount >= 2;
    }
}
