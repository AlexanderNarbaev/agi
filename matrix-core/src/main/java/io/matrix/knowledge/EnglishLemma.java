package io.matrix.knowledge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The lemma of an English word (RECON-W42.9).
 *
 * <p><b>The defect this exists for.</b> The English fold is ASYMMETRIC, and
 * it is asymmetric in both directions:
 *
 * <ul>
 *   <li>"release" folds to "release" and "releases" folds to "releas" — the
 *       "-es" rule fires before the "-s" rule, and the base's silent "e" is
 *       left behind on one side and not on the other;</li>
 *   <li>"process" folds to "proces" and "processes" folds to "process" —
 *       here the "-s" rule fires on a word that ends in "-ss", and the two
 *       forms do not meet either.</li>
 * </ul>
 *
 * <p>A question and a claim that differ only in their inflection therefore
 * never meet, and the workspace cannot reach a claim it holds. The measured
 * consequence was a RED TEST that predates W42.9: *"a refuted misconception
 * is never returned as an established claim"* — the question "Where does the
 * oxygen plants release come from?" never reached the claim "Oxygenic
 * photosynthesis releases oxygen as a byproduct of splitting water."
 *
 * <p><b>Why the fold itself was not changed.</b> Two global changes were
 * measured and rolled back: the W42.9 attempt restored the silent "e", and
 * that broke the rule application (which reads the stem "true", a word the
 * fold must leave alone) and the benchmark's transfer items. The fold's
 * behaviour is also pinned by W41's own tests
 * ({@code MultilingualTextTest}: {@code fold("molecules") == "molecul"}), and
 * changing it would rewrite a documented W41 behaviour to make a number
 * better.
 *
 * <p>So the fold is left exactly as it is, and the equivalence is applied
 * WHERE THE TERMS ARE COMPARED. This class computes the lemma; the retriever
 * carries a second, parallel channel of lemmas alongside the folded terms,
 * and a term matches when either channel agrees. The folded terms still
 * decide the ranking, the coverage threshold and the reported gaps — only
 * the REACH of a claim changes, not what the answer says.
 *
 * <p><b>The rule, and why there is only one.</b> The fold already removes a
 * plain "-s" ("plants" → "plant"), so the lemma does not repeat it. The one
 * rule the lemma adds is the "-es" plural, which the fold resolves wrongly
 * whenever the base ends in a silent "e":
 *
 * <ul>
 *   <li>after a SIBILANT — "ss", "x", "z", "ch", "sh" — the plural is
 *       "-es": "processes" → "process", "boxes" → "box", "watches" →
 *       "watch". This is the standard English rule, and the fold already
 *       does it;</li>
 *   <li>after anything else the base ends in a silent "e" and the plural is
 *       "-s": "releases" → "release", "phases" → "phase", "trees" →
 *       "tree". Dropping only the "s" keeps the base whole, and THIS is
 *       the rule whose absence made "release" and "releases" two different
 *       words.</li>
 * </ul>
 *
 * <p><b>What this does not claim.</b> A lemma is a morphological
 * approximation, not a dictionary: it does not know that "flies" is the
 * plural of "fly" and "ties" the plural of "tie", because the surface form
 * does not say. It is a retrieval aid in exactly the way the mission's §6
 * describes the bilingual lexicon — a search aid that never changes what a
 * claim asserts.
 *
 * <p><b>What it does not touch.</b> The Russian fold is exact (the case
 * endings are stripped and the stem is kept), so a Cyrillic word is left
 * alone: an English rule must not decide what a Russian word is.
 */
public final class EnglishLemma {

    /**
     * The endings after which the plural is "-es" and not "-s".
     *
     * <p>The sibilants of English. The list is the standard one, and it is
     * deliberately short: an ending that is not in it takes the "-s" rule,
     * which keeps the base's silent "e".
     */
    private static final List<String> SIBILANT_ENDINGS = List.of("ss", "x", "z", "ch", "sh");

    /** The shortest word the lemma touches. */
    private static final int MINIMUM_LENGTH = 3;

    private EnglishLemma() {
    }

    /**
     * The base of a word whose plural is "-es".
     *
     * <p>A word that does not end in "-es" is returned unchanged: the fold
     * already removes a plain "-s", and repeating it here would make the
     * lemma disagree with the fold on words such as "photosynthesis" for
     * no gain.
     *
     * @param word the word, as it was written
     * @return the base of the word
     */
    public static String base(String word) {
        if (word == null) {
            return null;
        }
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.length() < MINIMUM_LENGTH || TextProfile.isCyrillic(lower)) {
            return lower;
        }
        if (!lower.endsWith("es") || lower.length() <= MINIMUM_LENGTH + 1) {
            return lower;
        }
        String stem = lower.substring(0, lower.length() - 2);
        for (String ending : SIBILANT_ENDINGS) {
            if (stem.endsWith(ending)) {
                return stem;
            }
        }
        // A base that ends in a silent "e" takes "-s": "releases" is
        // "release" and not "releas".
        return lower.substring(0, lower.length() - 1);
    }

    /**
     * The lemma of an English word: its base, folded the way the index
     * folds.
     *
     * <p>The fold is applied AFTER the base, so that the lemma of
     * "releases" is the folded "release" and the lemma of "release" is the
     * folded "release" — the two agree, which is the whole point.
     *
     * @param word the word, as it was written
     * @return the lemma
     */
    public static String lemma(String word) {
        if (word == null) {
            return null;
        }
        String lower = word.toLowerCase(Locale.ROOT);
        if (TextProfile.isCyrillic(lower)) {
            return lower;
        }
        return TextProfiles.ENGLISH.fold(base(lower));
    }

    /**
     * The lemmas of the words of a text, folded the way the index folds.
     *
     * <p>The stopwords are left out: they carry no retrieval signal, and a
     * lemma of a stopword is a lemma of noise.
     *
     * @param text the text
     * @return the lemmas of its words
     */
    public static Set<String> lemmasOf(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        TextProfile profile = TextProfiles.ofText(text);
        for (String word : TextFeatures.rawWords(text)) {
            if (profile.isStopWord(word)) {
                continue;
            }
            out.add(profile.fold(base(word)));
        }
        return out;
    }
}
