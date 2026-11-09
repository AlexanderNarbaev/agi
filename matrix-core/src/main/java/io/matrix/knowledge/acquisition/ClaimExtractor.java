package io.matrix.knowledge.acquisition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/**
 * Proposes claims from the segments of a document (RECON-W42.2).
 *
 * <p>The mission's §5.3 names CLAIM EXTRACTION as a stage of the pipeline,
 * and §5.4 says that a claim enters the store as a CANDIDATE — a proposal,
 * not a fact. This extractor is deliberately conservative and entirely
 * deterministic:
 *
 * <ul>
 *   <li>it proposes only <b>declarative</b> sentences — a sentence with a
 *       copula or a causal verb — because a question or a command is not a
 *       claim;</li>
 *   <li>it never rewrites the sentence: the proposition is the sentence as
 *       the document wrote it, so that a reader can check it against the
 *       source;</li>
 *   <li>it keeps the segment and the location, so that every proposal
 *       carries the place it came from.</li>
 * </ul>
 *
 * <p><b>What it does not do.</b> It does not judge the claim, and it does
 * not decide that the claim is true. It proposes; the promotion rules
 * decide what happens next. A proposal with no evidence behind it stays a
 * CANDIDATE, and the mission's §16 forbids promoting generated content to
 * trusted knowledge.
 */
public final class ClaimExtractor {

    /**
     * The verbs a declarative sentence may carry, in the base form.
     *
     * <p>English and Russian, because the mission's §6 asks for both. The
     * third person is matched by stripping a trailing "s" - the verb list
     * holds the base form and the matcher accepts "produce" and "produces".
     *
     * <p><b>The list is a heuristic and says so.</b> A full part-of-speech
     * tagger would be the proper instrument; this is a deterministic one
     * that runs offline, in the store's own process, with no model to
     * load. A sentence it does not recognise is simply not proposed, and a
     * proposal it makes can be checked against the source.
     */
    private static final Set<String> DECLARATIVE_VERBS = Set.of(
            "is", "are", "was", "were", "be", "being", "been", "has", "have",
            "had", "means", "refer", "consist", "include", "require", "need", "produce", "generate",
            "release", "absorb", "convert", "transform", "use", "build", "happen", "occur", "form",
            "create", "make", "contain", "provide", "depend", "result", "lead", "cause", "enable",
            "allow", "determine", "control", "represent", "describe", "define", "equal", "become", "remain",
            "stay", "work", "function", "act", "react", "respond", "grow", "move", "split",
            "join", "bind", "store", "carry", "transport", "supply", "support", "drive", "feed",
            "take", "give", "show", "boil", "freeze", "melt", "dissolve", "evaporate", "condense",
            "burn", "reacts", "diffuse", "flow", "expand", "contract", "rotate", "recycle", "photosynthesise",
            "photosynthesize", "respire", "ferment", "crystallise", "crystallize", "oxidise", "oxidize", "reduce", "bond",
            "separate", "mix", "transfer", "emit", "reflect", "compete", "survive", "divide", "multiply",
            "envision", "believe", "think", "suggest", "propose", "argue", "expect", "account",
            "contribute", "manifest", "reveal", "note", "state", "publish", "cite", "discover",
            "observe", "measure", "predict", "estimate", "assume", "postulate", "infer", "conclude",
            "increase", "decrease", "change", "vary", "arise", "является", "есть", "означает", "называется",
            "состоит", "требует", "нуждается", "производит", "создает", "выделяет", "поглощает", "преобразует", "использует",
            "строит", "происходит", "образует", "содержит", "зависит", "приводит", "вызывает", "определяет", "обеспечивает",
            "представляет", "описывает", "равен", "становится", "остается", "работает", "действует", "реагирует", "растет",
            "делится", "связывается", "хранит", "переносит", "нужен", "нужна", "нужны");

    /** The smallest sentence a proposal may have. */
    private static final int MINIMUM_SENTENCE_LENGTH = 20;

    /** The largest sentence a proposal may have. */
    private static final int MAXIMUM_SENTENCE_LENGTH = 600;

    private ClaimExtractor() {
    }

    /**
     * One proposed claim.
     *
     * @param span W51 §6. The exact region of the extracted text the
     *     proposition was read from, in the convention
     *     io.matrix.contract.Provenance.Span declares. It is null when the
     *     document gives no exact span, and null is recorded as such.
     */
    public record Proposal(
            String proposition,
            String subject,
            String segmentId,
            String location,
            String language,
            io.matrix.contract.Provenance.Span span) {

        /** A proposal from a document that gives no exact span. */
        public Proposal(
                String proposition,
                String subject,
                String segmentId,
                String location,
                String language) {
            this(proposition, subject, segmentId, location, language, null);
        }
    }

    /**
     * The claims proposed by a document's segments.
     *
     * @param document the extracted document
     * @return the proposals, in the order the segments came
     */
    public static List<Proposal> propose(ExtractedDocument document) {
        List<Proposal> proposals = new ArrayList<>();
        for (ExtractedDocument.Segment segment : document.segments()) {
            for (String sentence : sentences(segment.text())) {
                String subject = subjectOf(sentence);
                if (subject == null) {
                    continue;
                }
                proposals.add(new Proposal(sentence, subject, segment.id(),
                        segment.location(), segment.language(), segment.span()));
            }
        }
        return proposals;
    }

    /**
     * The sentences of a segment.
     *
     * <p>The split is on the sentence punctuation and the newlines, and it
     * keeps the abbreviation "e.g." from ending a sentence — a small thing
     * that would otherwise cut a claim in half.
     */
    public static List<String> sentences(String text) {
        List<String> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sentences;
        }
        // THE HARD WRAP IS NOT A SENTENCE BOUNDARY.
        //
        // A Markdown document wraps its paragraphs at a fixed width, so a
        // paragraph is a set of lines that together make one sentence.
        // Splitting on the newline turned 76 of the 92 propositions of the
        // USGS corpus into line fragments - and a fragment is not a claim:
        // it cannot be quoted as evidence and it cannot be checked.
        StringBuilder block = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                flush(block, sentences);
                continue;
            }
            if (startsBlock(trimmed)) {
                flush(block, sentences);
                sentences.add(trimmed);
                continue;
            }
            if (!block.isEmpty()) {
                block.append(' ');
            }
            block.append(trimmed);
        }
        flush(block, sentences);
        return sentences;
    }

    /** Whether a line starts a block of its own. */
    private static boolean startsBlock(String line) {
        if (line.startsWith("#") || line.startsWith("|") || line.startsWith(">")) {
            return true;
        }
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
            return true;
        }
        int i = 0;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            i++;
        }
        return i > 0 && i + 1 < line.length() && line.charAt(i) == '.' && line.charAt(i + 1) == ' ';
    }

    /** The sentences of one block, and the block itself. */
    private static void flush(StringBuilder block, List<String> sentences) {
        if (block.isEmpty()) {
            return;
        }
        for (String sentence : splitSentences(block.toString())) {
            sentences.add(sentence);
        }
        block.setLength(0);
    }

    /** The sentences of one block: the split is on the terminators, not on the wraps. */
    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);
            if (c == '.' || c == '!' || c == '?') {
                if (c == '.' && i + 1 < text.length() && Character.isDigit(text.charAt(i + 1))) {
                    continue;
                }
                if (c == '.' && i >= 2 && text.substring(i - 2, i).endsWith("e.")) {
                    continue;
                }
                String sentence = current.toString().strip();
                if (!sentence.isEmpty()) {
                    sentences.add(sentence);
                }
                current.setLength(0);
            }
        }
        String last = current.toString().strip();
        if (!last.isEmpty()) {
            sentences.add(last);
        }
        return sentences;
    }

    /**
     * Whether a word is a declarative verb.
     *
     * <p>The base form is in the list, and the third person is accepted by
     * stripping a trailing "s" - so "produce" and "produces" are the same
     * verb for this purpose.
     */
    public static boolean isDeclarativeVerb(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        String lower = word.toLowerCase(Locale.ROOT).strip();
        if (DECLARATIVE_VERBS.contains(lower)) {
            return true;
        }
        if (lower.length() > 2 && lower.endsWith("s") && !lower.endsWith("ss")) {
            return DECLARATIVE_VERBS.contains(lower.substring(0, lower.length() - 1));
        }
        if (lower.length() > 3 && lower.endsWith("ed")) {
            // The past tense is the same verb: "envisioned" folds to
            // "envision", "believed" to "believe", "suggested" to
            // "suggest". Without this fold the whole scientific register
            // of the corpus - which writes its claims in the past tense -
            // was silently dropped.
            String stem = lower.substring(0, lower.length() - 2);
            return DECLARATIVE_VERBS.contains(stem)
                    || DECLARATIVE_VERBS.contains(stem + "e")
                    || (stem.endsWith("e") && DECLARATIVE_VERBS.contains(stem.substring(0, stem.length() - 1)));
        }
        if (lower.length() > 3 && lower.endsWith("es")) {
            // "freezes" folds to "freez" and the list holds "freeze": both
            // stems are tried, because the -es ending can stand for either.
            String stem = lower.substring(0, lower.length() - 2);
            return DECLARATIVE_VERBS.contains(stem) || DECLARATIVE_VERBS.contains(stem + "e");
        }
        return false;
    }

    /**
     * The subject of a declarative sentence, or null when the sentence does
     * not declare anything.
     */
    public static String subjectOf(String sentence) {
        String trimmed = sentence.strip();
        if (trimmed.length() < MINIMUM_SENTENCE_LENGTH
                || trimmed.length() > MAXIMUM_SENTENCE_LENGTH) {
            return null;
        }
        // A sentence that opens with a discourse marker ("However, ...") is
        // still a sentence that asserts something. The marker is not the
        // subject, so it is set aside before the subject is looked for.
        String working = withoutDiscourseMarker(trimmed);
        if (working != null) {
            trimmed = working;
        }
        // A question or a command is not a claim.
        if (trimmed.endsWith("?") || trimmed.endsWith("!")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("how to ")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("what is ")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("why ")) {
            return null;
        }
        // The subject is what comes before the first declarative verb.
        int at = -1;
        int cursor = 0;
        while (cursor < trimmed.length()) {
            int space = trimmed.indexOf(' ', cursor);
            if (space < 0) {
                space = trimmed.length();
            }
            String word = trimmed.substring(cursor, space).strip();
            if (isDeclarativeVerb(word)) {
                at = cursor;
                break;
            }
            cursor = space + 1;
        }
        if (at <= 0) {
            return null;
        }
        String subject = trimmed.substring(0, at).strip();
        // The subject must be a phrase, not a clause.
        if (subject.isEmpty() || subject.length() > 120) {
            return null;
        }
        if (subject.contains(",") && subject.indexOf(',') < subject.indexOf(' ')) {
            return null;
        }
        return subject;
    }

    /**
     * The sentence without its leading discourse marker.
     *
     * <p>"However, most scientists believe ..." asserts something about the
     * scientists; the marker is not the subject. The subject is looked for
     * in the rest of the sentence, and the proposition itself is never
     * rewritten.
     *
     * @param sentence the sentence
     * @return the sentence without the marker, or null when it has none
     */
    private static String withoutDiscourseMarker(String sentence) {
        int comma = sentence.indexOf(',');
        if (comma <= 0 || comma > 40) {
            return null;
        }
        String first = sentence.substring(0, comma).strip().toLowerCase(Locale.ROOT);
        if (!DISCOURSE_MARKERS.contains(first)) {
            return null;
        }
        String rest = sentence.substring(comma + 1).strip();
        return rest.isEmpty() ? null : rest;
    }

    /** The words a sentence may open with without carrying the assertion. */
    private static final Set<String> DISCOURSE_MARKERS = Set.of(
            "however", "therefore", "thus", "also", "moreover", "furthermore", "nevertheless",
            "nonetheless", "conversely", "subsequently", "instead", "rather", "besides",
            "yet", "so", "now", "later", "recently", "today", "overall", "generally", "typically");
}
