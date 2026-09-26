package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * TRUE-W11 iteration #7 — Nyaya 5-membered syllogism.
 *
 * <p>Per Aksapada's Nyaya Sutra, a sound inference has 5 members:</p>
 * <ol>
 *   <li><b>Pratijna</b> — proposition ("X is Y")</li>
 *   <li><b>Hetu</b> — reason ("because Z")</li>
 *   <li><b>Drstanta</b> — example ("known from analogous cases")</li>
 *   <li><b>Upanaya</b> — application ("therefore in this case too")</li>
 *   <li><b>Nigamana</b> — conclusion ("hence X is Y")</li>
 * </ol>
 *
 * <p>A complete syllogism with all 5 members is more rigorous than
 * the Western 3-membered (major premise + minor premise + conclusion)
 * form because example + application force explicit analogical
 * reasoning.</p>
 */
public final class NyayaSyllogism {

    public record Member(String sanskrit, String role, String text) {
        public boolean isPresent() { return text != null && !text.isBlank(); }
    }

    public static final Member PRATIJNA = new Member("Pratijna",
        "proposition", "X is Y");
    public static final Member HETU     = new Member("Hetu",
        "reason",       "because Z");
    public static final Member DRSTANTA = new Member("Drstanta",
        "example",      "known from analogous cases");
    public static final Member UPANAYA  = new Member("Upanaya",
        "application",  "therefore in this case too");
    public static final Member NIGAMANA = new Member("Nigamana",
        "conclusion",   "hence X is Y");

    public static final List<Member> SCHEMA = List.of(
        PRATIJNA, HETU, DRSTANTA, UPANAYA, NIGAMANA);

    /** True iff all 5 members are non-blank. */
    public static boolean isComplete(List<Member> members) {
        if (members == null || members.size() < 5) return false;
        for (int i = 0; i < 5; i++) {
            if (!members.get(i).isPresent()) return false;
        }
        return true;
    }

    /** Number of present members (0..5). */
    public static int countPresent(List<Member> members) {
        if (members == null) return 0;
        int n = 0;
        for (int i = 0; i < Math.min(5, members.size()); i++) {
            if (members.get(i).isPresent()) n++;
        }
        return n;
    }

    /** Convenience: build an empty 5-member syllogism. */
    public static List<Member> empty() {
        List<Member> m = new ArrayList<>(5);
        for (Member s : SCHEMA) m.add(new Member(s.sanskrit(), s.role(), ""));
        return m;
    }
}
