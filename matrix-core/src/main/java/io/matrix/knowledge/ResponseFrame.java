package io.matrix.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The frame of the answer, in the language of the question (RECON-W42.7).
 *
 * <p>The owner's decision: <em>translate the frame of the answer into the
 * language of the question</em>.
 *
 * <p><b>What this is.</b> Every answer carries two kinds of text. The
 * first is the workspace's own knowledge — the statements, the claims, the
 * limitations that quote a claim. The second is the system's own prose:
 * the sentences MATRIX itself writes around the answer, such as "No claim
 * in this workspace covers enough of the question to answer it." The
 * second kind is a fixed set of sentences the system owns, and those can
 * be said in the user's language without translating anything the
 * workspace knows.
 *
 * <p><b>What this is NOT.</b> It is not a translation of the knowledge.
 * The statements stay in the language the knowledge is written in, and the
 * answer says so — a word-by-word translation of a scientific explanation
 * would be gibberish, and gibberish is worse than a clear boundary. The
 * mission's §16 forbids making an external service mandatory, so a real
 * translation would need the owner to authorise one; this is the honest
 * part that needs no service.
 *
 * <p><b>Why a table and not a translator.</b> The frames are a closed set
 * of sentences the system writes itself. Their translations are exact,
 * not approximate, and a sentence with no translation is left in English
 * rather than mangled. That is the difference between a table and a
 * translator, and it is the difference the mission's honesty rules ask
 * for.
 */
public final class ResponseFrame {

    /** One frame sentence, with its translation. */
    private record Frame(String english, String russian) {
    }

    /** The frames the system owns. */
    private static final Map<String, Frame> FRAMES = new LinkedHashMap<>();

    static {
        frame("No claim in this workspace covers enough of the question to answer it.",
                "Ни одно утверждение этого workspace не покрывает вопрос достаточно, чтобы ответить на него.");
        frame("The question contains no searchable terms.",
                "В вопросе нет искомых терминов.");
        frame("The question is in {question} and the knowledge is in {knowledge}.",
                "Вопрос на {question} языке, а знание — на {knowledge}.");
        frame("The reference was resolved against the conversation: the question was read as being "
                        + "about \"{concept}\".",
                "Ссылка разрешена по разговору: вопрос прочитан как вопрос о «{concept}».");
        frame("This workspace has no {level} account of {concept}; the answer uses the {used} one.",
                "В этом workspace нет {level}-описания понятия «{concept}»; ответ использует {used}.");
        frame("The sources disagree about: {proposition}",
                "Источники расходятся насчёт: {proposition}");
        frame("The premise of the question is contradicted by the workspace: \"{premise}\".",
                "Посылка вопроса противоречит тому, что есть в workspace: «{premise}».");
        frame("The claim that contradicts it: \"{proposition}\".",
                "Утверждение, которое ей противоречит: «{proposition}».");
    }

    private ResponseFrame() {
    }

    /** Registers a frame sentence. */
    private static void frame(String english, String russian) {
        FRAMES.put(english, new Frame(english, russian));
    }

    /**
     * The frame's sentence in the language of the question.
     *
     * @param language the language of the question: "ru" or "en"
     * @param sentence the frame's sentence, in English
     * @return the sentence in the user's language, or the original when it
     *     has no translation
     */
    public static String in(String language, String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return sentence;
        }
        if (!"ru".equals(language)) {
            return sentence;
        }
        Frame frame = FRAMES.get(sentence);
        return frame == null ? sentence : frame.russian();
    }

    /**
     * The frame's template, with its placeholders filled in, in the
     * language of the question.
     *
     * <p>The template is matched on its English form with the
     * placeholders still in place, so the translation keeps the values the
     * caller supplied.
     *
     * @param language the language of the question: "ru" or "en"
     * @param template the English template with {name} placeholders
     * @param values the values for the placeholders
     * @return the sentence in the user's language
     */
    public static String template(String language, String template, Map<String, String> values) {
        if (!"ru".equals(language)) {
            return fill(template, values);
        }
        Frame frame = FRAMES.get(template);
        return fill(frame == null ? template : frame.russian(), values);
    }

    /** Whether the system has a translation for this frame. */
    public static boolean knows(String english) {
        return FRAMES.containsKey(english);
    }

    /** How many frames the system owns. */
    public static int size() {
        return FRAMES.size();
    }

    /** Fills the placeholders. */
    private static String fill(String template, Map<String, String> values) {
        if (template == null || values == null || values.isEmpty()) {
            return template;
        }
        String out = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return out;
    }
}
