package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds the conflicts among the claims that bear on a question, and reports them without
 * resolving them (EPISTEMIC-INTEGRITY P0.2).
 *
 * <p><b>Why this is a separate component.</b> {@link ContradictionDetector} finds the conflicts
 * that the STORE's own structure declares: two claims of the same concept, under the same
 * scope, with opposite polarity and the same terms. That rule is exact, and it is narrow — the
 * corpus of a real workspace holds claims of many concepts, and a conflict between two claims
 * of DIFFERENT concepts is invisible to it. The owner's directive asks for the conflicts the
 * CORPUS holds to be visible in the served answer, and the corpus's conflicts are not the
 * store's declared ones.
 *
 * <p><b>What a conflict is here.</b> Two accounts that are about the same subject and cannot
 * both hold:
 *
 * <ul>
 *   <li><b>Opposite polarity.</b> One account denies what the other asserts about that subject.
 *       The denial has to be SCOPED, exactly as {@link PremiseChecker} learned it to its cost: a
 *       denial counts only when it is about the other side's subject. A claim that mentions the
 *       same words without saying anything about what was denied does not deny it.</li>
 *   <li><b>Incompatible values.</b> One account states two different measured values for the
 *       same quantity — "the range is 65,000 km long ... and the total length is 80,000 km
 *       long". Two values of the same kind cannot both be the value of the quantity they
 *       measure.</li>
 * </ul>
 *
 * <p><b>What this does NOT do.</b> It does not decide which side is right, it does not count how
 * many sources agree with either side, and it does not change a claim's status. A conflict that
 * cannot be resolved stays unresolved and visible — which is the whole point. A false conflict
 * report is worse than a missed one, so the rules are conservative: two accounts must be about
 * the same SUBJECT before their incompatibility is read at all.
 *
 * <p><b>It is deterministic.</b> No model, no network, no random number and no wall-clock in any
 * decision path. The same claims produce the same conflicts, every time.
 */
public final class ClaimConflictDetector {

    /** One side of a conflict: an account, and what it asserts. */
    public record Side(String claimId, String proposition, String sourceId, String sourceTitle,
                       String sourceLocator, String detail) {
    }

    /** A conflict: accounts that cannot both hold, and why the reader cannot pick one. */
    public record Conflict(String reason, List<Side> sides) {

        public Conflict {
            sides = List.copyOf(sides == null ? List.of() : sides);
        }
    }

    /**
     * The number of CONTENT terms two accounts must share to be about the same thing.
     *
     * <p><b>Why the content terms and not the terms.</b> The bag of terms of two claims is
     * dominated by their FRAME: the variable name ("p"), the scope phrase ("in the assignment
     * under consideration") and the words that name the field ("propositional", "variable").
     * Two claims that share only the frame are not about the same thing — "the propositional
     * variable p implies the propositional variable q" and "the propositional variable p is not
     * true in the assignment under consideration" share every frame word and assert different
     * propositions.
     *
     * <p>The frame is excluded, not guessed: the corpus states the conditions of a claim in
     * its own SCOPE field, and a term of one letter is a symbol and not a word. What is left
     * has to be shared CONTENT, and three terms of it is the floor.
     */
    public static final int MINIMUM_SHARED_CONTENT_TERMS = 3;

    /**
     * The words that carry the POLARITY of a claim.
     *
     * <p>"the propositional variable p is false in the assignment" and "the propositional
     * variable p is not true in the assignment" are the SAME assertion — they deny the same
     * thing in two words — and a detector that reads only the denial marker ("not") sees one
     * of them as an assertion and the other as a denial, and reports a contradiction the
     * corpus does not contain. The negative predicate is a polarity word, and two claims whose
     * polarity is the same do not conflict.
     */
    private static final Set<String> NEGATIVE_POLARITY = Set.of(
            "not", "no", "never", "cannot", "nothing", "neither", "nor", "false", "without");

    /**
     * The words that carry the polarity of a claim and are therefore held apart from its
     * content.
     *
     * <p>"the propositional variable p is false in the assignment" and "the propositional
     * variable p is not true in the assignment" state the same thing in two words, and a
     * comparison that kept the words would see two different propositions.
     */
    private static final Set<String> POLARITY_WORDS = Set.of(
            "not", "no", "never", "cannot", "nothing", "neither", "nor", "false", "true",
            "without");

    /** The words that carry the POSITIVE polarity of a claim. */
    private static final Set<String> POSITIVE_POLARITY = Set.of("true");

    /**
     * The share of the first account's terms the second must carry to be about the same
     * subject.
     *
     * <p>It is the same rule {@link PremiseAssessment} uses to decide that a claim is about a
     * premise's subject, and for the same reason: the two accounts must be about the same thing
     * before their incompatibility means anything.
     */
    public static final double MINIMUM_SUBJECT_OVERLAP = PremiseAssessment.MINIMUM_SUBJECT_OVERLAP;

    /**
     * The number of a measured value, its unit and what the value measures.
     *
     * <p>The predicate is what makes two values comparable: "2,600 meters deep" and "2,000
     * meters above" are two numbers in the same unit and they measure two DIFFERENT things,
     * and a rule that compared the numbers alone would report a conflict that the text does not
     * contain.
     */
    private record Measure(double number, String unit, String predicate) {
    }

    /**
     * The words that separate a measurement from the thing it measures.
     *
     * <p>They carry no measurement of their own, so they are skipped when the predicate of a
     * value is looked up. The list is a property of the language, not of a corpus.
     */
    private static final Set<String> PREDICATE_FILLERS = Set.of(
            "the", "a", "an", "of", "to", "in", "on", "at", "by", "for", "and", "or", "is", "are",
            "was", "were", "be", "been", "being", "about", "approximately", "roughly", "circa",
            "around", "over", "under", "that", "which", "it", "its", "this", "these", "those",
            "there", "here", "up", "down", "as", "than", "then", "so", "also", "only", "just",
            "though", "although", "whereas", "however", "but", "while", "who", "whose");

    /**
     * The words that scale a unit.
     *
     * <p>"50 million years" is one measurement and not two: the unit is "million years" and the
     * number beside it is 50. A rule that stopped at "million" would read the unit as
     * "million" and the measure as "year", and two different ages in one sentence would look
     * like two values of the same measurement.
     */
    private static final Set<String> SCALE_WORDS = Set.of(
            "thousand", "million", "billion", "trillion", "hundred");

    private ClaimConflictDetector() {
    }

    /**
     * The conflicts among the claims that bear on a question.
     *
     * @param claims the claims the answer cites, in the order they are cited
     * @param sources the workspace's sources, by identifier
     * @return the conflicts found, each naming both sides
     */
    public static List<Conflict> detect(List<Claim> claims, Map<String, Source> sources) {
        return detect(claims, sources, null);
    }

    /**
     * The conflicts among the claims that bear on a question, and the conflicts the corpus
     * holds among the claims it was built from.
     *
     * @param claims the claims the answer cites
     * @param sources the workspace's sources, by identifier
     * @param workspace the workspace the claims come from, for the vocabulary of the corpus
     * @return the conflicts found, each naming both sides
     */
    public static List<Conflict> detect(List<Claim> claims, Map<String, Source> sources,
            KnowledgeSnapshot workspace) {
        List<Conflict> found = new ArrayList<>();
        if (claims == null || claims.isEmpty()) {
            return found;
        }
        Map<String, Integer> frequency = Map.of();
        for (int i = 0; i < claims.size(); i++) {
            Claim claim = claims.get(i);
            addValueConflicts(claim, sources, found);
            for (int j = i + 1; j < claims.size(); j++) {
                Claim other = claims.get(j);
                Conflict conflict = polarityConflict(claim, other, sources);
                if (conflict != null) {
                    found.add(conflict);
                }
            }
        }
        return found;
    }

    /**
     * The conflict between two accounts, or null when they do not conflict.
     *
     * <p>The order matters: the account that DENIES is the second side, because the denial is
     * what the reader has to see against the assertion it denies.
     *
     * @param left the first account
     * @param right the second account
     * @param sources the workspace's sources, by identifier
     * @return the conflict, or null
     */
    private static Conflict polarityConflict(Claim left, Claim right, Map<String, Source> sources) {
        Set<String> leftContent = contentTerms(left);
        Set<String> rightContent = contentTerms(right);
        // THE TWO ACCOUNTS MUST STATE THE SAME PROPOSITION.
        //
        // Two accounts whose words are the same and whose polarity differs contradict each
        // other; two accounts whose words differ do not, however many words they share. The
        // logic package is the case in point: "the propositional variable p is false in the
        // assignment" and "the propositional variable p is not true in the assignment" are the
        // SAME assertion in two words (the polarity words are held apart from the content), and
        // "the propositional variable p implies the propositional variable q" is a different
        // proposition altogether. A detector that compared the words and the polarity alone
        // reported both as contradictions, and a false contradiction in a served answer is
        // worse than a missed one.
        if (!leftContent.equals(rightContent)) {
            return null;
        }
        if (!sameScope(left, right)) {
            // Two accounts under different conditions can both hold, and the corpus says which
            // conditions each of them holds under.
            return null;
        }
        int leftPolarity = polarityOf(left);
        int rightPolarity = polarityOf(right);
        if (leftPolarity == rightPolarity || leftPolarity == 0 || rightPolarity == 0) {
            return null;
        }
        String denied = leftPolarity < 0 ? left.proposition() : right.proposition();
        String asserted = leftPolarity < 0 ? right.proposition() : left.proposition();
        return new Conflict(
                "the two accounts are about the same subject and one of them denies what the "
                        + "other asserts: \"" + denied.strip() + "\"",
                List.of(side(leftPolarity < 0 ? right : left, sources,
                                "asserts the subject the other side denies"),
                        side(leftPolarity < 0 ? left : right, sources,
                                "denies it: \"" + denied.strip() + "\"")));
    }

    /**
     * The terms of the conditions an account holds under.
     *
     * @param claim the account
     * @return the terms of its scope
     */
    private static Set<String> scopeTerms(Claim claim) {
        Set<String> terms = new LinkedHashSet<>();
        String scope = claim.scope();
        if (scope == null || scope.isBlank()) {
            return terms;
        }
        for (String word : TextFeatures.rawWords(scope)) {
            terms.add(TextProfiles.ofLanguage(TextProfiles.detect(word)).fold(word));
        }
        return terms;
    }

    /**
     * Whether a denial is ABOUT the other side's subject.
     *
     * <p><b>Why the subject and not the terms.</b> A denial that shares most of its terms with
     * another account is not about that account when it denies a DIFFERENT subject: "the
     * propositional variable p is not true in the assignment under consideration" shares six
     * of its seven terms with "the propositional variable q is true in the assignment under
     * consideration", and the two are about different variables and can both hold. The subject
     * of the denial is what it is about, and the subject is the part of the clause before its
     * first auxiliary.
     *
     * @param denial the denying clause
     * @param otherStems the other side's terms
     * @return whether the denial is about the other side
     */
    private static boolean denialIsAbout(String denial, Set<String> otherStems) {
        Set<String> subject = subjectStems(denial);
        if (subject.isEmpty()) {
            // A clause without an auxiliary has no subject this rule can find, and a rule that
            // guesses would report conflicts the corpus does not contain.
            return false;
        }
        return otherStems.containsAll(subject);
    }

    /**
     * The terms of a clause's subject: the words before its first auxiliary.
     *
     * @param clause the clause
     * @return the subject's terms, or an empty set when the clause has no auxiliary
     */
    /**
     * The content terms of an account: its own terms, without the conditions it holds under
     * and without the symbols it uses.
     *
     * @param claim the account
     * @return its content terms
     */
    static Set<String> contentTerms(Claim claim) {
        Set<String> terms = new LinkedHashSet<>(PremiseAssessment.distinctiveStems(
                claim.proposition()));
        terms.removeAll(scopeTerms(claim));
        terms.removeAll(POLARITY_WORDS);
        terms.removeIf(term -> term.length() < 2);
        return terms;
    }

    /**
     * The polarity of an account: negative when it denies, positive when it asserts, and zero
     * when it says nothing about the polarity at all.
     *
     * @param claim the account
     * @return the polarity
     */
    static int polarityOf(Claim claim) {
        boolean negative = false;
        boolean positive = false;
        for (String word : TextFeatures.rawWords(claim.proposition())) {
            String folded = TextProfiles.ofLanguage(TextProfiles.detect(word)).fold(word);
            if (NEGATIVE_POLARITY.contains(folded)) {
                negative = true;
            }
            if (POSITIVE_POLARITY.contains(folded)) {
                positive = true;
            }
        }
        if (negative) {
            return -1;
        }
        if (positive) {
            return 1;
        }
        return 0;
    }

    /**
     * The number of content terms two accounts share.
     *
     * @param left the first account
     * @param right the second account
     * @return the shared content terms
     */
    static Set<String> sharedContentTerms(Claim left, Claim right) {
        Set<String> shared = new LinkedHashSet<>(contentTerms(left));
        shared.retainAll(contentTerms(right));
        return shared;
    }

    static Set<String> subjectStems(String clause) {
        List<String> words = TextFeatures.rawWords(clause);
        List<String> before = new ArrayList<>();
        for (String word : words) {
            if (AUXILIARIES.contains(word)) {
                break;
            }
            before.add(word);
        }
        if (before.size() == words.size()) {
            return Set.of();
        }
        return PremiseAssessment.distinctiveStems(String.join(" ", before));
    }

    /**
     * The auxiliaries a clause's subject ends at.
     *
     * <p>They are a property of the language, not of any corpus.
     */
    private static final Set<String> AUXILIARIES = Set.of(
            "is", "are", "was", "were", "be", "been", "being", "has", "have", "had", "do", "does",
            "did", "will", "would", "can", "could", "may", "might", "must", "should");

    /**
     * Whether two accounts hold under the same conditions.
     *
     * <p>The corpus states the conditions in each claim's own scope, and two claims about
     * different conditions can both hold. The rule is the one
     * {@link ContradictionDetector} applies, and it is applied here for the same reason.
     *
     * @param left the first account
     * @param right the second account
     * @return whether their scopes agree
     */
    private static boolean sameScope(Claim left, Claim right) {
        return scopeOf(left).equals(scopeOf(right));
    }

    /** The conditions an account holds under, as the corpus states them. */
    private static String scopeOf(Claim claim) {
        String scope = claim.scope() == null ? "" : claim.scope().trim().toLowerCase(Locale.ROOT);
        return scope;
    }

    /**
     * Whether two accounts are about the same subject.
     *
     * @param leftStems the first account's terms
     * @param rightStems the second account's terms
     * @return whether they are about one subject
     */
    private static boolean sameSubject(Set<String> leftStems, Set<String> rightStems) {
        if (leftStems.isEmpty() || rightStems.isEmpty()) {
            return false;
        }
        Set<String> shared = new LinkedHashSet<>(leftStems);
        shared.retainAll(rightStems);
        if (shared.size() < MINIMUM_SHARED_CONTENT_TERMS) {
            return false;
        }
        return (double) shared.size() / leftStems.size() >= MINIMUM_SUBJECT_OVERLAP;
    }

    /**
     * The conflicts a single account holds about itself: two values of the same kind that
     * cannot both be the value of the quantity they measure.
     *
     * @param claim the account
     * @param sources the workspace's sources, by identifier
     * @param found the conflicts found so far
     */
    private static void addValueConflicts(Claim claim, Map<String, Source> sources,
            List<Conflict> found) {
        List<Measure> measures = measuresOf(claim.proposition());
        if (measures.size() < 2) {
            return;
        }
        Map<String, List<Measure>> grouped = new LinkedHashMap<>();
        for (Measure measure : measures) {
            grouped.computeIfAbsent(measure.unit() + "" + measure.predicate(),
                    key -> new ArrayList<>()).add(measure);
        }
        for (Map.Entry<String, List<Measure>> entry : grouped.entrySet()) {
            List<Measure> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }
            boolean different = false;
            for (int i = 1; i < group.size() && !different; i++) {
                different = Double.compare(group.get(i).number(), group.get(0).number()) != 0;
            }
            if (!different) {
                continue;
            }
            List<Side> sides = new ArrayList<>();
            for (Measure measure : group) {
                sides.add(side(claim, sources, valueText(measure)));
            }
            found.add(new Conflict(
                    "the account states two different values for the same measurement ("
                            + entry.getKey().replace('', ' ') + "): "
                            + valueText(group.get(0)) + " and " + valueText(group.get(1)),
                    sides));
        }
    }

    /** The text of a measured value. */
    private static String valueText(Measure measure) {
        return format(measure.number()) + " " + measure.unit() + " " + measure.predicate();
    }

    /** A number without a fractional part when it has none. */
    private static String format(double number) {
        if (number == Math.rint(number) && !Double.isInfinite(number)) {
            return Long.toString((long) number);
        }
        return Double.toString(number);
    }

    /**
     * The measured values a proposition states.
     *
     * <p>A value is a number, the unit it is measured in, and the word that says what it
     * measures. A number in parentheses is a CONVERSION of the value beside it and not a second
     * value, and a number followed by another number is a RANGE and not a value at all.
     *
     * @param proposition the proposition to read
     * @return the values it states
     */
    static List<Measure> measuresOf(String proposition) {
        List<Measure> measures = new ArrayList<>();
        if (proposition == null || proposition.isBlank()) {
            return measures;
        }
        List<Token> tokens = tokenize(proposition);
        for (int i = 0; i < tokens.size(); i++) {
            Token number = tokens.get(i);
            if (number.depth() > 0 || !number.numeric()) {
                continue;
            }
            if (i + 1 >= tokens.size() || tokens.get(i + 1).depth() > 0
                    || tokens.get(i + 1).numeric()) {
                continue;
            }
            Token unit = tokens.get(i + 1);
            int afterUnit = i + 2;
            String unitText = fold(unit.text());
            if (SCALE_WORDS.contains(unitText) && afterUnit < tokens.size()
                    && tokens.get(afterUnit).depth() == 0 && !tokens.get(afterUnit).numeric()) {
                unitText = unitText + " " + fold(tokens.get(afterUnit).text());
                afterUnit++;
            }
            String predicate = predicateAfter(tokens, afterUnit);
            if (predicate == null) {
                continue;
            }
            measures.add(new Measure(number.value(), unitText, predicate));
        }
        return measures;
    }

    /**
     * The word that says what the value beside it measures.
     *
     * @param tokens the tokens of the proposition
     * @param from the index to start at
     * @return the predicate, or null when the value measures nothing
     */
    private static String predicateAfter(List<Token> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.depth() > 0) {
                continue;
            }
            String word = fold(token.text());
            if (word.isEmpty() || PREDICATE_FILLERS.contains(word)
                    || TextFeatures.isStopWord(token.text())) {
                continue;
            }
            return word;
        }
        return null;
    }

    /** One word of a proposition, with the depth of the parentheses it stands in. */
    private record Token(String text, int depth, boolean numeric, double value) {
    }

    /**
     * The words of a proposition, with the depth of the parentheses each stands in.
     *
     * @param proposition the proposition
     * @return its words
     */
    private static List<Token> tokenize(String proposition) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        String lower = proposition.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '(') {
                emit(current, depth, tokens);
                depth++;
                continue;
            }
            if (c == ')') {
                emit(current, depth, tokens);
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (c == ',' && isThousandsSeparator(lower, i, current)) {
                // A comma between digits separates the thousands of a number and is part of
                // it: "65,000" is one value and not two.
                current.append(c);
            } else {
                emit(current, depth, tokens);
            }
        }
        emit(current, depth, tokens);
        return tokens;
    }

    /** The word that has been read, as a token. */
    private static void emit(StringBuilder current, int depth, List<Token> tokens) {
        if (current.length() == 0) {
            return;
        }
        String text = current.toString();
        current.setLength(0);
        boolean numeric = true;
        double value = 0.0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ',') {
                continue;
            }
            if (!Character.isDigit(c)) {
                numeric = false;
                break;
            }
        }
        if (numeric) {
            try {
                value = Double.parseDouble(text.replace(",", ""));
            } catch (NumberFormatException notANumber) {
                numeric = false;
            }
        }
        tokens.add(new Token(text, depth, numeric, value));
    }

    /** The folded form of a word, the way the index folds it. */
    private static String fold(String word) {
        return TextProfiles.ofLanguage(TextProfiles.detect(word)).fold(word);
    }

    /**
     * Whether a comma separates the thousands of a number.
     *
     * @param text the text being read
     * @param at the place of the comma
     * @param current the word read so far
     * @return whether the comma belongs to the number
     */
    private static boolean isThousandsSeparator(String text, int at, StringBuilder current) {
        if (current.length() == 0 || !Character.isDigit(current.charAt(current.length() - 1))) {
            return false;
        }
        return at + 1 < text.length() && Character.isDigit(text.charAt(at + 1));
    }

    /** The side of a conflict that a claim is. */
    private static Side side(Claim claim, Map<String, Source> sources, String detail) {
        Source source = sources == null ? null : sources.get(claim.introducedBySourceId());
        return new Side(claim.id(), claim.proposition(),
                source == null ? claim.introducedBySourceId() : source.id(),
                source == null ? null : source.title(),
                source == null ? null : source.locator(),
                detail);
    }
}
