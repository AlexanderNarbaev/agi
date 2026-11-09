package io.matrix.api.bridge;

/**
 * W52 — what chain B's brain does with the knowledge chain A offers.
 *
 * <p><b>Why this is an interface.</b> The bridge must not depend on the brain's concrete
 * class: the brain is replaceable, the governance around it is not. The interface names
 * the two facts the bridge needs — that the brain took the knowledge, and how much it
 * holds — and nothing else.</p>
 */
public interface KnowledgeTeacher {

    /**
     * The brain takes a proposition and what is known about it into its memory.
     *
     * @param input    the proposition
     * @param response what is known about it
     * @return true when the brain took it
     */
    boolean teach(String input, String response);

    /**
     * How much the brain holds.
     *
     * @return the number of things in the brain's memory
     */
    int knowledgeSize();
}
