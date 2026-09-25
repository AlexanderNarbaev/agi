package io.matrix.brain.runtime;

/**
 * MIND-W7 — License tier gate.
 *
 * <p>Per-tier feature gating (FREE / PRO / ENTERPRISE). Used by the
 * gateway to decide which operations a given user may invoke.</p>
 *
 * <p>CONSTITUTION Article VI — tiers are documented; no hidden gating.</p>
 */
public final class LicenseManager {

    public enum Tier { FREE, PRO, ENTERPRISE }

    public enum Feature {
        ANALYZE(true,    1,   1.0),
        TEACH(true,       2,   1.0),
        THINK_WITH_MCTS(true,  3,   5.0),
        LEARN(false,      2,   10.0),       // PRO+
        SLEEP(false,      2,   3.0),        // PRO+
        DISTILL(false,    3,   100.0),      // ENTERPRISE
        FEDERATE(false,   3,   50.0);       // ENTERPRISE

        public final boolean free;
        public final int minTier;  // 1=FREE, 2=PRO, 3=ENTERPRISE
        public final double creditCost;

        Feature(boolean free, int minTier, double creditCost) {
            this.free = free;
            this.minTier = minTier;
            this.creditCost = creditCost;
        }
    }

    public record Decision(boolean allowed, String reason) {
        public static Decision allow() { return new Decision(true, "ok"); }
        public static Decision deny(String reason) { return new Decision(false, reason); }
    }

    /** Decide whether {@code tier} can invoke {@code feature}. */
    public static Decision check(Tier tier, Feature feature) {
        if (feature.free) return Decision.allow();
        int userLevel = tierLevel(tier);
        if (userLevel >= feature.minTier) return Decision.allow();
        return Decision.deny("feature " + feature + " requires tier > " + tier);
    }

    private static int tierLevel(Tier t) {
        return switch (t) {
            case FREE -> 1;
            case PRO -> 2;
            case ENTERPRISE -> 3;
        };
    }
}
