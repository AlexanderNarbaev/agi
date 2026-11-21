package io.matrix.knowledge;

import java.util.List;

/**
 * The result of asking the workspace a question (owner directive §19
 * Workflow D, §27).
 *
 * <p>An answer is either an ANSWER or an ABSTENTION, and the difference is
 * explicit. When the evidence does not reach the threshold, the answer says
 * so, names the terms of the question that nothing in the workspace covers,
 * and does not present a guess. A system that never abstains is a system
 * whose support cannot be read.
 *
 * <p>The number this record used to call {@code confidence} is now called
 * {@code supportScore}, and it is accompanied by a {@link SupportScore}
 * record that decomposes it and states what it is not. RECON-W41's audit
 * established that the old number was the mean of the matched claims'
 * authored confidence multiplied by the retrieval coverage, with no
 * calibration data anywhere in the repository - a heuristic presented with
 * the authority of a probability. The value is unchanged; what it is called,
 * and what the reader is told about it, is not.
 */
public record Answer(
        String question,
        AbstractionLevel level,
        boolean answered,
        String abstentionReason,
        String statement,
        double supportScore,
        SupportScore support,
        List<Concept> concepts,
        List<Claim> claims,
        List<Source> sources,
        List<String> limitations,
        List<String> gaps,
        List<String> unmatchedTerms,
        PremiseSemantics.Disposition premise) {

    public Answer {
        concepts = List.copyOf(concepts == null ? List.of() : concepts);
        claims = List.copyOf(claims == null ? List.of() : claims);
        sources = List.copyOf(sources == null ? List.of() : sources);
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
        gaps = List.copyOf(gaps == null ? List.of() : gaps);
        unmatchedTerms = List.copyOf(unmatchedTerms == null ? List.of() : unmatchedTerms);
    }

    /**
     * This answer with more claims in its evidence.
     *
     * <p><b>Why the evidence may grow.</b> The retrieval decides which claims the question's
     * words point at; a <b>learned claim</b> is a claim the system has already validated, and
     * it belongs in the evidence of a question about its concept even when the words did not
     * point at it. The claims already present are kept, and a claim that is already there is
     * not added twice.</p>
     *
     * <p>The rest of the answer is untouched: the statement is the retrieval's own, and the
     * learned claim joins the <b>evidence</b>, not the prose.</p>
     *
     * @param additional the claims to add
     * @return the answer with them
     */
    public Answer withAdditionalClaims(List<Claim> additional) {
        if (additional == null || additional.isEmpty()) {
            return this;
        }
        List<Claim> merged = new java.util.ArrayList<>(claims);
        for (Claim claim : additional) {
            if (claim != null && merged.stream().noneMatch(c -> c.id().equals(claim.id()))) {
                merged.add(claim);
            }
        }
        if (merged.size() == claims.size()) {
            return this;
        }
        return new Answer(question, level, answered, abstentionReason, statement, supportScore,
                support, concepts, merged, sources, limitations, gaps, unmatchedTerms, premise);
    }

    /**
     * The answer of a path that does not assess a premise.
     *
     * <p>The disposition is a field of its own, and a caller that builds an answer without
     * reading the workspace's claims has no disposition to report. The constructor keeps that
     * caller working: the disposition is simply absent, and the absence says that nothing was
     * assessed — which is what happened.
     *
     * @param question the question
     * @param level the abstraction level
     * @param answered whether the question was answered
     * @param abstentionReason why it was not, or null
     * @param statement the answer
     * @param supportScore the support score
     * @param support what the support score means
     * @param concepts the concepts the answer cites
     * @param claims the claims the answer cites
     * @param sources the sources the answer cites
     * @param limitations what the answer does not establish
     * @param gaps what the workspace does not know
     * @param unmatchedTerms the terms of the question nothing covers
     */
    public Answer(String question, AbstractionLevel level, boolean answered,
            String abstentionReason, String statement, double supportScore, SupportScore support,
            List<Concept> concepts, List<Claim> claims, List<Source> sources,
            List<String> limitations, List<String> gaps, List<String> unmatchedTerms) {
        this(question, level, answered, abstentionReason, statement, supportScore, support,
                concepts, claims, sources, limitations, gaps, unmatchedTerms, null);
    }

    /** True when the workspace could not answer, and said so. */
    public boolean abstained() {
        return !answered;
    }

    /**
     * The one-line disclosure that travels with every answer.
     *
     * @return what the number means, or why there is no number
     */
    public String supportMeaning() {
        return support == null ? SupportScore.MEANING : support.meaning();
    }
}
