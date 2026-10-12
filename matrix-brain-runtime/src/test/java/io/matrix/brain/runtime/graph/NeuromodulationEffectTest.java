package io.matrix.brain.runtime.graph;

import io.matrix.brain.runtime.ContentSimilarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W37.2 — verification that neuromodulation has a <em>measurable</em> effect.
 *
 * <p>The brief demands: "Validation: Prove Attention_Level affects retrieval precision/recall."
 * Wiring a parameter into a code path proves nothing, so this test measures the actual
 * trade-off curve that {@link NeuroModulatorHub#retrievalFloor()} selects on, using the real
 * {@link ContentSimilarity} scorer.</p>
 *
 * <p><b>Method.</b> A fixed corpus of known facts and a fixed set of queries with known answers
 * (the "answerable" class) and known misses (the "unanswerable" class). For a range of floors,
 * score every query against the best matching fact and count hits against the floor. Precision is
 * hits / served; recall is hits / answerable. No randomness: the corpus and queries are literals,
 * which is also what Article III requires of anything feeding a decision.</p>
 *
 * <p><b>What this does NOT claim.</b> It uses a small hand-built corpus, not the 20-question
 * benchmark behind the published ROC, so the absolute numbers here are not the production figures.
 * It demonstrates the <em>direction and shape</em> of the effect, which is what the brief asks for.
 * The production ROC remains the source of truth for {@code RETRIEVAL_FLOOR}.</p>
 */
class NeuromodulationEffectTest {

    /**
     * Facts the store could contain. Format matches the real "content =&gt; answer" convention.
     *
     * <p>W37.2 — the first version of this corpus was too easy: every negative scored 0.0000
     * except one at 0.1667, all below the 0.20 base floor, so precision was 1.00 at every floor
     * and the "attention raises precision" claim was untestable. The claim was not false, the
     * fixture simply contained no hard cases. This corpus mixes paraphrases with shared
     * vocabulary so the negatives straddle the floor.</p>
     */
    private static final String[] FACTS = {
        "What is the capital of France? => Paris",
        "What is the capital of Japan? => Tokyo",
        "What is gravity? => 9.8 m/s^2",
        "Who wrote Hamlet? => William Shakespeare",
        "What is the largest ocean? => Pacific Ocean",
        "What language is spoken in Brazil? => Portuguese",
        "How many continents are there? => seven",
        "What is the chemical symbol for gold? => Au",
    };

    /** Queries whose correct answer IS in {@link #FACTS}. */
    private static final String[] ANSWERABLE = {
        "capital of France", "France capital city", "what is the capital of Japan",
        "gravity value", "who wrote Hamlet", "largest ocean",
        "language spoken in Brazil", "number of continents", "symbol for gold",
        "France capital", "Japan capital", "continents count",
    };

    /**
     * Queries with NO answer in the corpus but genuine lexical overlap with one, so they land
     * near the floor band. These are the hard negatives: the ones that decide whether a threshold
     * buys anything.
     */
    private static final String[] UNANSWERABLE = {
        "capital of Portugal",              // same template as two real facts
        "who wrote Macbeth",                // same template as Hamlet
        "what is the capital of Peru",      // capital template, absent fact
        "speed of light",                   // shares 'of'/'what is' shape with gravity
        "largest desert",                   // shares 'largest' template
        "how many oceans",                  // shares 'how many' template
        "symbol for silver",                // shares 'symbol for' template
        "language spoken in Peru",          // shares 'language spoken in' template
        "what is the population of France", // shares 'what is the' shape
    };

    /** Best content score achievable by any query against any fact. */
    private static double bestScore(String query) {
        double best = 0.0;
        for (String fact : FACTS) {
            double s = ContentSimilarity.score(query, fact);
            if (s > best) best = s;
        }
        return best;
    }

    private record Counts(int served, int correct) {
        double precision() {
            return served == 0 ? 0.0 : (double) correct / served;
        }

        double recall() {
            return ANSWERABLE.length == 0 ? 0.0 : (double) correct / ANSWERABLE.length;
        }
    }

    private static Counts evaluate(double floor) {
        int served = 0;
        int correct = 0;
        for (String q : ANSWERABLE) {
            if (bestScore(q) >= floor) {
                served++;
                correct++; // every answerable query has a correct answer available
            }
        }
        for (String q : UNANSWERABLE) {
            if (bestScore(q) >= floor) served++; // served but wrong => a fabrication
        }
        return new Counts(served, correct);
    }

    @Test
    @DisplayName("raising the floor measurably reduces fabrications (precision up)")
    void raisingFloorIncreasesPrecision() {
        Counts loose = evaluate(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
        Counts strict = evaluate(NeuroModulatorHub.ATTENTIVE_RETRIEVAL_FLOOR);

        // DIAG: the raw score distribution decides which claim is even available. If no negative
        // ever clears the base floor, precision is 1.00 at BOTH floors and the only honest claim
        // is about recall.
        System.out.println("  negative query best-scores:");
        for (String q : UNANSWERABLE) System.out.printf("    %.4f  %s%n", bestScore(q), q);
        System.out.println("  answerable query best-scores:");
        for (String q : ANSWERABLE) System.out.printf("    %.4f  %s%n", bestScore(q), q);

        System.out.printf("  floor=%.2f (attention 0.0): served=%d correct=%d precision=%.2f recall=%.2f%n",
                NeuroModulatorHub.BASE_RETRIEVAL_FLOOR, loose.served(), loose.correct(),
                loose.precision(), loose.recall());
        System.out.printf("  floor=%.2f (attention 1.0): served=%d correct=%d precision=%.2f recall=%.2f%n",
                NeuroModulatorHub.ATTENTIVE_RETRIEVAL_FLOOR, strict.served(), strict.correct(),
                strict.precision(), strict.recall());

        assertThat(strict.precision())
                .as("the whole point of attention here is to refuse more than it serves wrongly")
                .isGreaterThan(loose.precision());
        assertThat(strict.served() - strict.correct())
                .as("fabrications served at the attentive floor")
                .isLessThanOrEqualTo(loose.served() - loose.correct());
    }

    @Test
    @DisplayName("raising the floor reduces recall — the cost is real and must be stated")
    void raisingFloorReducesRecall() {
        Counts loose = evaluate(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
        Counts strict = evaluate(NeuroModulatorHub.ATTENTIVE_RETRIEVAL_FLOOR);

        assertThat(strict.recall())
                .as("a strict floor trades recall for precision; if this ever stops being true, "
                        + "the trade-off this design relies on has changed")
                .isLessThanOrEqualTo(loose.recall());
    }

    @Test
    @DisplayName("attention level maps monotonically onto the floor")
    void attentionIsMonotonicInFloor() {
        double previous = -1.0;
        for (int i = 0; i <= 10; i++) {
            double attention = i / 10.0;
            double floor = new NeuroModulatorHub(attention, 0.5, 0.0, 0.0,
                    NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD).retrievalFloor();
            assertThat(floor)
                    .as("attention %.1f must not lower the floor below the previous step", attention)
                    .isGreaterThanOrEqualTo(previous);
            previous = floor;
        }
    }

    @Test
    @DisplayName("the floor can never fall below the measured production value")
    void floorNeverGoesBelowProduction() {
        // Even at zero attention with a negative reward and maximum uncertainty tolerance.
        NeuroModulatorHub hub = new NeuroModulatorHub(0.0, 1.0, -100.0, 0.0, 1.0);
        assertThat(hub.retrievalFloor())
                .as("a modulator must never be able to loosen retrieval below the measured "
                        + "floor; the worst case is refusing more, never serving more")
                .isGreaterThanOrEqualTo(ContentSimilarity.RETRIEVAL_FLOOR);
    }

    @Test
    @DisplayName("the production floor equals the measured RETRIEVAL_FLOOR at zero attention")
    void baseFloorMatchesProduction() {
        assertThat(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR)
                .as("the hub's base must not drift from the scorer's published floor")
                .isEqualTo(ContentSimilarity.RETRIEVAL_FLOOR);
    }

    // ── Guarantees added after mutation testing found them unverified ──

    @Test
    @DisplayName("zero attention gives exactly the production floor, full attention the strict one")
    void endpointsAreExact() {
        NeuroModulatorHub zero = hub(0.0, 0.0);
        NeuroModulatorHub full = hub(1.0, 0.0);
        assertThat(zero.retrievalFloor())
                .as("attention=0 must reproduce production exactly, not merely exceed it")
                .isEqualTo(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
        assertThat(full.retrievalFloor())
                .as("attention=1 must reach the strict floor")
                .isEqualTo(NeuroModulatorHub.ATTENTIVE_RETRIEVAL_FLOOR);
        assertThat(zero.retrievalFloor())
                .as("the two endpoints must differ, or attention has no range at all")
                .isLessThan(full.retrievalFloor());
    }

    @Test
    @DisplayName("attention actually changes what is served, not merely the reported number")
    void attentionChangesServedSet() {
        Counts loose = evaluate(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
        Counts strict = evaluate(NeuroModulatorHub.ATTENTIVE_RETRIEVAL_FLOOR);
        assertThat(strict.served())
                .as("if the served count never moved, the floor would be decorative")
                .isLessThan(loose.served());
    }

    private static NeuroModulatorHub hub(double attention, double fatigue) {
        return new NeuroModulatorHub(attention, 0.5, 0.0, fatigue,
                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
    }

    @Test
    @DisplayName("reward relieves fatigue but can never erase it")
    void rewardCannotEraseFatigue() {
        // Unlimited reward against maximum fatigue.
        NeuroModulatorHub rewarded = new NeuroModulatorHub(0.5, 0.5, 1_000_000.0, 1.0,
                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
        NeuroModulatorHub unrewarded = new NeuroModulatorHub(0.5, 0.5, 0.0, 1.0,
                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);

        assertThat(rewarded.sleepDemand())
                .as("an environment that rewards fast answers must not be able to prevent "
                        + "consolidation indefinitely")
                .isGreaterThan(0.0);
        assertThat(rewarded.sleepDemand())
                .as("severe fatigue imposes a demand floor that reward cannot undercut")
                .isGreaterThanOrEqualTo(NeuroModulatorHub.MIN_DEMAND_AT_SEVERE_FATIGUE);
        assertThat(rewarded.shouldSleep())
                .as("maximum fatigue with maximum reward still demands sleep")
                .isTrue();
        assertThat(unrewarded.shouldSleep()).isTrue();
    }

    @Test
    @DisplayName("below severe fatigue, reward still relieves a bounded amount")
    void mildFatigueIsRelievedByReward() {
        // fatigue 0.4 is BELOW SEVERE_FATIGUE, so the demand floor does not apply and this
        // isolates the cancellation itself. Without this case a mutation that lets reward erase
        // fatigue outright survives: at fatigue=1.0 the severe-fatigue floor masks it.
        NeuroModulatorHub rewarded = new NeuroModulatorHub(0.5, 0.5, 10.0, 0.4,
                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
        NeuroModulatorHub unrewarded = new NeuroModulatorHub(0.5, 0.5, 0.0, 0.4,
                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);

        assertThat(rewarded.sleepDemand())
                .as("reward must relieve some fatigue below the severe threshold")
                .isLessThan(unrewarded.sleepDemand());
        assertThat(rewarded.sleepDemand())
                .as("but never to zero: 0.4 * (1 - 0.5) = 0.2 is the floor")
                .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("out-of-range inputs are clamped rather than propagated")
    void inputsAreClamped() {
        NeuroModulatorHub high = new NeuroModulatorHub(5.0, 5.0, 0.0, 5.0, 5.0);
        NeuroModulatorHub low = new NeuroModulatorHub(-5.0, -5.0, 0.0, -5.0, -5.0);
        assertThat(high.attentionLevel()).isEqualTo(1.0);
        assertThat(high.fatigueIndex()).isEqualTo(1.0);
        assertThat(low.attentionLevel()).isEqualTo(0.0);
        assertThat(low.fatigueIndex()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("fatigue raises the decay rate, so weak connections fade faster")
    void fatigueIncreasesDecay() {
        double rested = hub(0.5, 0.0).decayRate();
        double tired = hub(0.5, 1.0).decayRate();
        assertThat(tired).isGreaterThan(rested);
    }

    @Test
    @DisplayName("a rested system does not sleep, and a tired one does")
    void sleepTriggerRespondsToFatigue() {
        assertThat(hub(0.5, 0.1).shouldSleep())
                .as("low fatigue must not trigger sleep")
                .isFalse();
        assertThat(hub(0.5, 0.9).shouldSleep())
                .as("high fatigue must trigger sleep")
                .isTrue();
    }
}
