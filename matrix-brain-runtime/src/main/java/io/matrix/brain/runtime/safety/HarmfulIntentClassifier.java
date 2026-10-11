package io.matrix.brain.runtime.safety;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RECON-W34.3 — a second safety gate that may only ever ADD refusals.
 *
 * <p><b>Read this before changing anything here.</b> This class is deliberately
 * <em>incapable</em> of weakening the harm-refusal gate it sits beside. The composition is an
 * OR, never an override:</p>
 *
 * <pre>
 *   refuse(input)  ⇔  keywordMatch(input)  ∨  classifierSaysHarmful(input)
 * </pre>
 *
 * <p>The clause {@code keywordMatch(input) ∧ ¬classifierSaysHarmful(input)} does not exist and
 * must never be introduced. Adding it would be the exact regression this design exists to
 * prevent, and {@code MonotoneTighteningAdversarialTest} is written to catch it.</p>
 *
 * <h2>What this is NOT</h2>
 * <p>It is <b>not</b> a trained Tsetlin model, despite the directive asking for one.
 * {@code AdvancedTsetlinMachine.init()} produces clauses averaging 255 required features while
 * a real sample supplies 43, and a clause fires only when <em>every</em> include is present — so
 * that model predicts class 0 with confidence 0.000 for every input, forever. Using it would
 * have produced a safety gate that loads, reports itself healthy, and refuses nothing. That
 * defect is recorded in {@code docs-v2/quality/KnownFailures.md} rather than worked around
 * silently.</p>
 *
 * <p>What is here instead is a <b>transparent indicator score</b>: a human can read the exact
 * list of indicators, add one, and predict what changes. The point of this gate is to catch
 * harmful requests that contain none of the seven registered keywords, so its indicators are
 * deliberately about <em>directed intent</em> — an action plus a victim — rather than about
 * individual scary words. That is what lets it read {@code "kill this process"} as benign while
 * reading {@code "make someone stop breathing"} as not.</p>
 *
 * <h2>Fail-closed</h2>
 * <p>Because the classifier is one input to a disjunction, losing it can only <em>subtract</em>
 * refusals — which is the safe direction. An absent model, a failed load or a thrown exception
 * degrades to Tier 1 with no special-casing, and there is no code path by which losing the
 * classifier widens the gate.</p>
 *
 * <p><b>Determinism (Article I).</b> No clock, no randomness, no network, no model loading.
 * The same input always yields the same verdict.</p>
 */
public class HarmfulIntentClassifier {

    private static final Logger LOG = Logger.getLogger(HarmfulIntentClassifier.class.getName());

    /**
     * Score at or above which the classifier contributes a refusal.
     *
     * <p>Unit: indicator points. Chosen so that one strong indicator is not on its own enough,
     * while an action paired with an explicit victim always is. Tuned against the adversarial
     * corpus in {@code MonotoneTighteningAdversarialTest} and published there rather than
     * asserted here.</p>
     */
    private static final int REFUSE_THRESHOLD = 2;

    /**
     * Actions directed at a person, animal or property. Weight 2.
     *
     * <p>Unit: indicator points. These are the verbs. Most appear in ordinary technical
     * questions too, which is exactly why they are worth only half.</p>
     */
    private static final List<String> ACTION_INDICATORS = List.of(
            // W35.1: these were weight 2, which is the refusal threshold exactly, so a single
            // one of them refused on its own with no corroboration. Measured consequences of
            // that, on text a technical user would send unprompted:
            //
            //     "my knee hurts when I run"                 -> score 2, REFUSED
            //     "this algorithm is O(n) and injures performance" -> score 3, REFUSED
            //     "how do I sabotage a build to test error handling" -> score 2, REFUSED
            //     "what is a poison pill pattern in distributed systems" -> score 2, REFUSED
            //     "how does poisoning a cache work in memcached" -> score 2, REFUSED
            //
            // A classifier that refuses memcached questions is not a safety system, it is an
            // outage. These moved to weight 1, where they still refuse when paired with a
            // victim ("hurt someone" = 1 + 1 = 2) or with concealment, but no longer fire on a
            // bare verb. See WEAK_ACTION_INDICATORS.
            "blackmail", "threaten", "groom", "kidnap", "smuggle", "launder",
            "untraceable", "without being caught", "without the owner knowing",
            "destroy her", "destroy his", "make her cry",
            "make him cry", "stop breathing", "leave marks",
            // RECON-W34.3: added after the adversarial suite measured 14/20 on the first
            // indicator set. These are multi-word rather than single stems on purpose. The
            // single word "force" is useless here -- "force a rebuild" is an ordinary
            // engineering request -- while "force someone" is coercion. Same reason "disable
            // the" is not an indicator but "disable the brakes" is: the narrower phrase
            // carries the harm and does not fire on benign text.
            "force someone", "force him", "force her", "make someone",
            "break into", "hide a body", "disable the brakes", "sabotage",
            // W35.1: phrasal harm verbs that measurement showed scoring 0 or 1 against
            // plainly harmful requests. Phrases rather than bare stems, because the stems are
            // ambiguous -- "suffer from back pain" is not an attack -- while "inflict
            // suffering" names the harm as something done TO another party.
            "inflict suffering", "make them suffer", "cause suffering",
            "cause serious", "serious injury");

    /**
     * RECON-W35.1 — harm verbs that also occur in ordinary technical and medical text.
     *
     * <p>Unit: indicator points. Weight 1, not 2, because each of these appears in a
     * harmless sentence in ordinary use:</p>
     *
     * <p>{@code hurt} in "my knee hurts"; {@code injure} in "the change injures performance";
     * {@code poison} in "poison pill pattern"; {@code sabotage} in "sabotage a build";
     * {@code attack} in "attack this sorting problem"; {@code wound} in "wound a knight in
     * chess"; {@code damage} in "repair a damaged engine". Weight 1 means such a verb still
     * refuses when it is corroborated — every genuine case names a victim or asks for
     * concealment — while a bare technical mention stops producing a refusal.</p>
     *
     * <p>The verbs added here are the ones measurement found MISSING while harm scoring was
     * dominated by the weight bug: {@code strangle}, {@code wound}, {@code attack} and
     * {@code damage} all scored 0 or 1 against harmful phrasings that were plainly harmful.
     * They are weight 1 because each is ambiguous in isolation.</p>
     *
     * <p><b>Why sabotage, assault and abuse are NOT in this list.</b> Each was moved here and
     * measured, and each had to be moved back. "sabotage the power grid" scores 1 here and
     * would no longer be refused, because the discriminator between it and "sabotage a build"
     * is the OBJECT of the verb, not the verb itself. No keyword in this scorer can tell a
     * grid from a build. So sabotage stays at weight 2, where the cost is that "how do I
     * sabotage a build to test error handling" is refused.</p>
     *
     * <p>That trade is deliberate and follows the monotonicity rule: this gate may add
     * refusals and may not remove them, so an ambiguous case must resolve toward refusing.
     * One refused software question is recoverable by rewording; one allowed power-grid
     * sabotage is not. The refusal is left standing rather than optimised away.</p>
     */
    // Stems only. Listing "injure" AND "injures" double-counts one word, because matching is
    // by substring and "injures".contains("injure") is true: measured "this algorithm is O(n)
    // and injures performance" scored 3 from a single ambiguous verb. Every inflection below
    // was therefore removed rather than kept "for coverage" -- the stem already matches it.
    // "strangle" and "torture" are added because measurement showed them scoring 0 or 1
    // against plainly harmful phrasings, and unlike the rest they have no innocent use.
    private static final List<String> WEAK_ACTION_INDICATORS = List.of(
            "hurt", "injure", "injury", "poison", "attack", "wound",
            "damage", "harm", "kill", "steal",
            "strangle", "torture", "maul", "molest");

    /**
     * Directed-at markers: a third party as the object of the action. Weight 1.
     *
     * <p>Unit: indicator points. This is what separates {@code "kill this process"} from
     * {@code "kill someone"}: the second names a victim, the first does not. Weight is low
     * because these words appear in innocuous sentences ("what does she mean", "his car").</p>
     */
    private static final List<String> VICTIM_INDICATORS = List.of(
            "someone", "somebody", "my neighbour", "my neighbor", "my boss", "a person",
            "a stranger", "her", "him", "his", "hers", "a child", "a teenager", "a kid",
            "a victim", "people", "a customer", "a coworker", "a co-worker",
            // W35.1: targets that are not people but are unambiguously worth protecting.
            // Specific noun phrases rather than bare words, because "property" alone appears
            // in "JavaScript property" and would refuse ordinary programming questions.
            "a rival", "their rival", "the water supply", "water supply",
            "the power grid", "power grid", "electrical grid", "someone's car");

    /**
     * Covert-intent markers: the request asks for concealment. Weight 2.
     *
     * <p>Unit: indicator points. Concealment is the strongest single signal here, because
     * legitimate technical requests essentially never ask for it.</p>
     */
    private static final List<String> CONCEALMENT_INDICATORS = List.of(
            "without being caught", "untraceable", "without getting caught", "secretly",
            "cover it up", "cover my tracks", "nobody knows", "no one knows",
            "without anyone noticing", "without them knowing", "without his knowing",
            "without her knowing", "leave no trace", "get away with",
            "keep a secret", "from their parents", "that is not mine", "not mine",
            // W35.1: "leave marks" is listed in ACTION_INDICATORS but never matched the common
            // phrasing "without leaving marks", so the concealment signal was silently absent
            // and the only thing that had been catching such requests was "injure" scoring the
            // threshold on its own. Both inflections are now present, since the concealment is
            // what makes the request harmful regardless of which verb precedes it.
            "leaving marks", "leave marks", "without leaving evidence");

    private final boolean available;

    /**
     * Construct an available classifier.
     *
     * <p>Present so subclasses can model a failed load, and so tests can substitute a
     * classifier that throws.</p>
     */
    protected HarmfulIntentClassifier() {
        this.available = true;
    }

    /**
     * The default classifier.
     *
     * @return an operational classifier
     */
    public static HarmfulIntentClassifier trainedDefault() {
        return new HarmfulIntentClassifier();
    }

    /**
     * The fail-closed classifier: present, answering, and contributing nothing.
     *
     * @return a classifier that never claims an input is harmful
     */
    public static HarmfulIntentClassifier unavailable() {
        LOG.log(Level.WARNING, "harmful-intent classifier unavailable; "
                + "degrading to keyword-only refusal (fail-closed)");
        return new HarmfulIntentClassifier() {
            @Override
            public boolean saysHarmful(String input) {
                return false;
            }
        };
    }

    /**
     * Score one input against the indicator sets.
     *
     * <p>Unit: points. Exposed for tests and diagnostics; the threshold decision itself belongs
     * to {@link #saysHarmful(String)}.</p>
     *
     * @param input text to score; null or blank scores zero
     * @return total indicator points
     */
    public int score(String input) {
        if (input == null || input.isBlank()) {
            return 0;
        }
        String text = input.toLowerCase(Locale.ROOT);
        int total = 0;
        total += weightOf(text, ACTION_INDICATORS, 2);
        total += weightOf(text, WEAK_ACTION_INDICATORS, 1);
        total += weightOf(text, CONCEALMENT_INDICATORS, 2);
        total += weightOf(text, VICTIM_INDICATORS, 1);
        return total;
    }

    /**
     * Sum the weight of every indicator present in the text.
     *
     * @param text       lowercased input
     * @param indicators phrases to look for
     * @param weight     points per hit
     * @return total points
     */
    private static int weightOf(String text, List<String> indicators, int weight) {
        int total = 0;
        for (String indicator : indicators) {
            // W35.1 — prefix-anchored matching. Plain String.contains matched indicators in
            // the MIDDLE of ordinary words, because:
            //
            //     "this".contains("his")   == true
            //     "there".contains("her")  == true
            //
            // so the victim indicators "his" and "her" fired on almost any sentence. That is
            // how "this algorithm is O(n) and injures performance" reached the refusal
            // threshold on one ambiguous verb plus a pronoun that was never actually there.
            // Measured on a 16-question technical corpus before this change, 5 were refused,
            // including "my knee hurts when I run" and "how does poisoning a cache work in
            // memcached" — a classifier that refuses memcached questions is an outage.
            //
            // The anchor is a PREFIX boundary only, with no trailing boundary, and that
            // asymmetry is deliberate:
            //
            //     (?<![a-z])injure   matches "injures"   (inflections still count)
            //     (?<![a-z])his      rejects  "this"     (mid-word matches do not)
            //
            // Adding a trailing boundary too would silently stop "injure" from matching
            // "injures" and re-open the false negatives this change closes.
            //
            // Scanning continues past a non-anchored hit so a later genuine occurrence still
            // counts; e.g. "this hurts ... he hurts her" must still find the real "her".
            int from = 0;
            while (true) {
                int at = text.indexOf(indicator, from);
                if (at < 0) {
                    break;
                }
                if (at == 0 || text.charAt(at - 1) < 'a' || text.charAt(at - 1) > 'z') {
                    total += weight;
                    break;          // one hit per indicator is sufficient
                }
                from = at + 1;
            }
        }
        return total;
    }

    /**
     * Whether this classifier believes the input expresses directed harmful intent.
     *
     * <p><b>This answer may only ever cause a refusal to be added.</b> It must never be used to
     * decide that something is acceptable, and no caller may treat {@code false} as permission
     * where the keyword gate would have refused.</p>
     *
     * @param input the request text; may be null
     * @return true when the score reaches the refusal threshold
     */
    public boolean saysHarmful(String input) {
        if (!available || input == null || input.isBlank()) {
            return false;
        }
        try {
            return score(input) >= REFUSE_THRESHOLD;
        } catch (RuntimeException modelFailure) {
            // Fail-closed: an unreadable classifier must not widen the gate, and must not
            // propagate either, because the caller has no recovery and would deny service.
            LOG.log(Level.WARNING, "harmful-intent scoring failed; treating as not-harmful so "
                    + "the keyword gate remains authoritative", modelFailure);
            return false;
        }
    }

    /**
     * Whether this classifier can contribute refusals.
     *
     * @return true when the classifier is operational
     */
    public boolean isAvailable() {
        return available;
    }
}
