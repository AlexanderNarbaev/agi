package io.matrix.knowledge.benchmark;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.DialogueContext;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.Representation;
import io.matrix.knowledge.TextFeatures;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether an item of the benchmark can be answered by the workspace it
 * points at.
 *
 * <p><b>This is the defect decision D-013 caught.</b> The item
 * {@code ru-cross-3} asked about the Calvin cycle and pointed its
 * {@code workspace} at the logic package, which holds no Calvin cycle: no
 * correct implementation could answer it, the correct behaviour was to
 * refuse, and the refusal was counted as a wrong answer. A benchmark item
 * has two kinds of fields and they are not the same kind of thing — the
 * <b>etalon</b> says what the right answer is and may not be moved to make
 * a number better, and the <b>pointer</b> says where the knowledge lives
 * and may be corrected when it names a workspace without the concept.
 *
 * <p>This class checks the pointer, and it checks it for EVERY item, so a
 * malformed item fails the build instead of being counted as a wrong
 * answer. The rule is deliberately simple and independent of the suffix
 * fold, because the fold is a property of the text layer and not of the
 * data:
 *
 * <ul>
 *   <li>the <b>words</b> of a workspace are the raw words of its concept
 *       names, its concept definitions, its claim propositions and its
 *       representation statements — the text the retriever indexes;</li>
 *   <li>a key term is <b>covered</b> when every content word of the term is
 *       a word of the workspace or starts one: "water" is covered by the
 *       workspace that says "water", and "splitting" by the one that says
 *       "splits";</li>
 *   <li>an <b>answerable</b> item must have every one of its key terms
 *       covered — the knowledge it asks about has to be there;</li>
 *   <li>an <b>UNANSWERABLE</b> item must have every one of its key terms
 *       <b>outside</b> the workspace, and it must name at least one: an
 *       unanswerable item that names nothing cannot be checked at all;</li>
 *   <li>a <b>FALSE_PREMISE</b> item must have its terms covered and the
 *       workspace must hold a REFUTED claim, because the refusal of a
 *       false premise is possible only when the workspace holds the
 *       denial;</li>
 *   <li>a <b>CONTRADICTORY</b> item must have its terms covered and the
 *       workspace must hold at least two claims about the subject — one
 *       statement cannot contradict another;</li>
 *   <li>an <b>ADVERSARIAL</b> item must carry an injection, and an
 *       <b>AMBIGUOUS</b> item must carry a reference or an alternative,
 *       because that is what makes the item what its category says.</li>
 * </ul>
 */
public final class BenchmarkReachability {

    /**
     * The shortest prefix of a word that may stand for the workspace's own
     * word.
     *
     * <p>Four characters is enough to tell "water" from "water vapour" and
     * short enough that "condensate" is not read as "condensation".
     */
    public static final int MINIMUM_PREFIX = 4;

    /** The category of the items whose subject must be OUTSIDE the workspace. */
    public static final String CATEGORY_UNANSWERABLE = "UNANSWERABLE";

    /** The category of the items whose premise the workspace must deny. */
    public static final String CATEGORY_FALSE_PREMISE = "FALSE_PREMISE";

    /** The category of the items whose subject the workspace states twice. */
    public static final String CATEGORY_CONTRADICTORY = "CONTRADICTORY";

    /** The category of the items that carry an injection. */
    public static final String CATEGORY_ADVERSARIAL = "ADVERSARIAL";

    /** The category of the items whose referent is not settled. */
    public static final String CATEGORY_AMBIGUOUS = "AMBIGUOUS";

    /**
     * The phrases that make a question an injection attempt.
     *
     * <p>The list is the benchmark's own and it covers both languages the
     * instrument speaks. The demand analysis of the system carries its own
     * list, and the report says which of these injections that list
     * recognises — the check here is a check on the DATA, not on the
     * system.
     */
    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore all previous", "ignore previous", "ignore the previous", "ignore the above",
            "disregard all previous",
            "system:", "developer mode", "act as", "pretend to be", "your instructions",
            "игнорируй", "забудь предыдущие", "притворись", "ты теперь", "в режиме разработчика");

    /** The words that offer an alternative the question does not settle. */
    private static final List<String> ALTERNATIVE_MARKERS = List.of(" or ", " или ");

    private BenchmarkReachability() {
    }

    /** One item that cannot be answered by the workspace it points at. */
    public record Finding(
            String itemId,
            String category,
            String workspace,
            String problem,
            List<String> terms) {
    }

    /**
     * The words a workspace holds: the text its retriever indexes.
     *
     * @param snapshot the workspace
     * @return the words, lower-cased
     */
    public static Set<String> wordsOf(KnowledgeSnapshot snapshot) {
        Set<String> words = new LinkedHashSet<>();
        for (Concept concept : snapshot.concepts()) {
            addWords(words, concept.name());
            addWords(words, concept.definition());
        }
        for (Claim claim : snapshot.claims()) {
            addWords(words, claim.proposition());
        }
        for (Representation representation : snapshot.representations()) {
            addWords(words, representation.statement());
        }
        return words;
    }

    private static void addWords(Set<String> words, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        words.addAll(TextFeatures.rawWords(text));
    }

    /**
     * Whether the workspace holds the knowledge a term names.
     *
     * @param words the words of the workspace
     * @param term the term
     * @return whether the term is covered
     */
    public static boolean covers(Set<String> words, String term) {
        for (String word : contentWords(term)) {
            if (!covered(words, word)) {
                return false;
            }
        }
        return true;
    }

    private static boolean covered(Set<String> words, String word) {
        for (String own : words) {
            if (own.startsWith(word)) {
                return true;
            }
            if (own.length() >= MINIMUM_PREFIX && word.startsWith(own)) {
                return true;
            }
        }
        return false;
    }

    /** The content words of a term: the stopwords removed. */
    private static List<String> contentWords(String term) {
        List<String> words = new ArrayList<>();
        for (String word : TextFeatures.rawWords(term)) {
            if (!TextFeatures.isStopWord(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Checks one item against the workspace it points at.
     *
     * @param item the item
     * @param snapshot the workspace the item points at
     * @return the findings, empty when the item is well-formed
     */
    public static List<Finding> check(BenchmarkItem item, KnowledgeSnapshot snapshot) {
        List<Finding> findings = new ArrayList<>();
        if (snapshot == null) {
            findings.add(finding(item, "the workspace of the item holds no knowledge at all", List.of()));
            return findings;
        }
        Set<String> words = wordsOf(snapshot);
        String category = item.category();

        if (CATEGORY_UNANSWERABLE.equals(category)) {
            if (item.keyTerms().isEmpty()) {
                findings.add(finding(item, "the unanswerable item names no subject, so nothing can be "
                        + "checked about it", List.of()));
            }
            for (String term : item.keyTerms()) {
                if (covers(words, term)) {
                    findings.add(finding(item, "the subject of the unanswerable item is inside the "
                            + "workspace", List.of(term)));
                }
            }
            return findings;
        }

        for (String term : item.keyTerms()) {
            if (!covers(words, term)) {
                findings.add(finding(item, "the key term is not in the workspace the item points at",
                        List.of(term)));
            }
        }

        if (CATEGORY_FALSE_PREMISE.equals(category)) {
            boolean denies = snapshot.claims().stream()
                    .anyMatch(claim -> claim.status() == ClaimStatus.REFUTED);
            if (!denies) {
                findings.add(finding(item, "the workspace holds no refuted claim, so the false premise "
                        + "cannot be denied", List.of()));
            }
        }

        if (CATEGORY_CONTRADICTORY.equals(category) && !item.keyTerms().isEmpty()) {
            String subject = item.keyTerms().get(0);
            long about = snapshot.claims().stream()
                    .filter(claim -> mentions(claim.proposition(), subject))
                    .count();
            if (about < 2) {
                findings.add(finding(item, "the workspace states the subject only once, so nothing "
                        + "contradicts it", List.of(subject)));
            }
        }

        if (CATEGORY_ADVERSARIAL.equals(category) && !carriesInjection(item.question())) {
            findings.add(finding(item, "the adversarial item carries no injection", List.of()));
        }

        if (CATEGORY_AMBIGUOUS.equals(category) && !carriesAmbiguity(item.question())) {
            findings.add(finding(item, "the ambiguous item carries neither a reference nor an "
                    + "alternative", List.of()));
        }
        return findings;
    }

    private static boolean mentions(String proposition, String subject) {
        return covers(new LinkedHashSet<>(TextFeatures.rawWords(proposition)), subject);
    }

    /** Whether a question carries an injection attempt. */
    public static boolean carriesInjection(String question) {
        String lower = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (String marker : INJECTION_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a question leaves its referent open. */
    public static boolean carriesAmbiguity(String question) {
        if (DialogueContext.hasReference(question)) {
            return true;
        }
        String lower = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (String marker : ALTERNATIVE_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks every item against the workspace it points at.
     *
     * @param items the items
     * @param workspaces the workspaces, by their identifier
     * @return the findings, in the order of the items
     */
    public static List<Finding> checkAll(List<BenchmarkItem> items,
                                         Map<String, KnowledgeSnapshot> workspaces) {
        List<Finding> findings = new ArrayList<>();
        for (BenchmarkItem item : items) {
            findings.addAll(check(item, workspaces.get(item.workspace())));
        }
        return findings;
    }

    private static Finding finding(BenchmarkItem item, String problem, List<String> terms) {
        return new Finding(item.id(), item.category(), item.workspace(), problem, terms);
    }
}
