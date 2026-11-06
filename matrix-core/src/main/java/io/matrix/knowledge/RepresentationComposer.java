package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Writes a representation for a concept that has none (RECON-W42.4).
 *
 * <p>The mission's §10.1 asks that "the system creates a representation for a
 * new concept", and the first practical objective says the system must
 * "improve its knowledge representation" and "demonstrate that the
 * improvement remains after a forced restart".
 *
 * <p>RECON-W41 could not do either: the representations were authored by
 * hand in the knowledge packages, and a concept that came from a document
 * had claims and evidence but **no authored account** — so the answer the
 * system gave about it was the claim itself, which is a different thing
 * from an explanation.
 *
 * <p><b>The composer does not write prose.</b> It assembles the account the
 * document already contains: the claim that states the simplest thing about
 * the concept is the introductory account; the claims that relate the
 * concept to others are the intermediate one; the claims that say what
 * causes what are the advanced one. The sentences are the document's own.
 *
 * <p><b>What it does not do.</b> It does not invent a fact, it does not
 * guess a mechanism the document does not state, and it does not write a
 * level the evidence does not support. A concept with one simple claim gets
 * one introductory account and no more: an advanced account with nothing in
 * it would be a fabrication wearing the clothes of a summary.
 */
public final class RepresentationComposer {

    /** The words that mark a claim as about a mechanism or a cause. */
    private static final List<String> MECHANISM_WORDS = List.of(
            "because", "causes", "caused", "leads", "results", "produces", "requires",
            "converts", "transforms", "releases", "absorbs", "splits", "uses",
            "enables", "determines", "controls", "depends", "contains",
            "потому", "вызывает", "приводит", "образует", "требует", "использует");

    /** The words that mark a claim as quantitative. */
    private static final List<String> QUANTITATIVE_WORDS = List.of(
            "molecule", "molecules", "atp", "nadph", "adp", "co2", "h2o", "o2",
            "percent", "calorie", "joule", "two", "three", "four", "six",
            "молекул", "молекулы", "процента");

    private RepresentationComposer() {
    }

    /**
     * The representations a concept needs.
     *
     * <p>The levels are written only when they have something the lower
     * level does not: a level that repeats the one below it is a second
     * label on the same sentence, and the reader would learn nothing from
     * it. A concept with one simple claim therefore gets one account, and
     * that is the honest outcome.
     *
     * @param concept the concept
     * @param claims the concept's claims, in the order they were recorded
     * @param otherConceptNames the names of the concepts of the workspace,
     *     which is how a claim is recognised as relating its concept to
     *     another
     * @param workspaceId the workspace
     * @param sourceId the source the claims came from
     * @param createdAt when the representations were written
     * @return the representations, from the simplest level up
     */
    public static List<Representation> compose(Concept concept, List<Claim> claims,
                                               List<String> otherConceptNames,
                                               String workspaceId, String sourceId,
                                               String createdAt) {
        List<Representation> representations = new ArrayList<>();
        if (concept == null || claims == null || claims.isEmpty()) {
            return representations;
        }
        List<Claim> ordered = new ArrayList<>(claims);
        ordered.sort(java.util.Comparator.comparingInt(
                claim -> claim.proposition().replaceAll("[^A-Za-zА-Яа-я]", "").length()));

        // The simplest claim is the introductory account.
        List<Claim> introductory = List.of(ordered.get(0));
        representations.add(level(concept, ordered, AbstractionLevel.INTRODUCTORY,
                workspaceId, sourceId, createdAt, introductory));

        // The claims that relate this concept to another are the
        // intermediate account; the claims that say what causes what are
        // the advanced one; the quantitative claims are the mechanistic one.
        List<Claim> structured = new ArrayList<>();
        List<Claim> mechanistic = new ArrayList<>();
        List<Claim> quantitative = new ArrayList<>();
        for (Claim claim : ordered) {
            String lower = claim.proposition().toLowerCase(Locale.ROOT);
            if (containsAny(lower, MECHANISM_WORDS)) {
                mechanistic.add(claim);
            }
            if (containsAny(lower, QUANTITATIVE_WORDS)) {
                quantitative.add(claim);
            }
            if (mentionsAnotherConcept(claim, concept, otherConceptNames)) {
                structured.add(claim);
            }
        }

        // A level is written only with claims the lower level does not have.
        List<Claim> intermediate = without(introductory, structured);
        if (!intermediate.isEmpty()) {
            representations.add(level(concept, ordered, AbstractionLevel.INTERMEDIATE,
                    workspaceId, sourceId, createdAt, intermediate));
        }
        List<Claim> advanced = without(introductory, without(intermediate, mechanistic));
        if (!advanced.isEmpty()) {
            representations.add(level(concept, ordered, AbstractionLevel.ADVANCED,
                    workspaceId, sourceId, createdAt, advanced));
        }
        List<Claim> mechanical = without(introductory,
                without(intermediate, without(advanced, quantitative)));
        if (!mechanical.isEmpty()) {
            representations.add(level(concept, ordered, AbstractionLevel.MECHANISTIC,
                    workspaceId, sourceId, createdAt, mechanical));
        }
        return representations;
    }

    /** The claims of the second list that the first list does not hold. */
    private static List<Claim> without(List<Claim> first, List<Claim> second) {
        List<Claim> out = new ArrayList<>();
        for (Claim claim : second) {
            if (!first.contains(claim)) {
                out.add(claim);
            }
        }
        return out;
    }

    /**
     * The representation of one level.
     *
     * @param concept the concept
     * @param claims all the concept's claims
     * @param level the level
     * @param workspaceId the workspace
     * @param sourceId the source the claims came from
     * @param createdAt when the representation was written
     * @param preferred the claims that suit this level
     * @return the representation, or null when there is nothing to say
     */
    private static Representation level(Concept concept, List<Claim> claims,
                                        AbstractionLevel level, String workspaceId,
                                        String sourceId, String createdAt,
                                        List<Claim> preferred) {
        List<Claim> chosen = preferred.isEmpty() ? List.of(claims.get(0)) : preferred;
        StringBuilder statement = new StringBuilder();
        List<String> facts = new ArrayList<>();
        for (Claim claim : chosen) {
            if (statement.length() > 0) {
                statement.append(' ');
            }
            statement.append(claim.proposition());
            facts.add(claim.proposition());
        }
        // The facts that did NOT suit this level are recorded as omitted, so
        // that a reader can see what the account leaves out.
        List<String> omitted = new ArrayList<>();
        for (Claim claim : claims) {
            if (!facts.contains(claim.proposition())) {
                omitted.add(claim.proposition());
            }
        }
        double confidence = chosen.stream().mapToDouble(Claim::confidence).average().orElse(0.0);
        return new Representation(
                Ids.digest(workspaceId + "|representation|" + concept.id() + "|"
                        + level.name() + "|" + sourceId),
                workspaceId, concept.id(), level,
                "a reader who asks what this is", statement.toString(), sourceId,
                List.of(), facts, omitted, List.of(), List.of(), null,
                confidence, createdAt);
    }

    /**
     * Whether the claim relates its concept to another one.
     *
     * <p>The check is against the other concepts' NAMES, not against the
     * claim's length: a length test was the first version of this, and it
     * called a long sentence about water a structural account.
     */
    private static boolean mentionsAnotherConcept(Claim claim, Concept concept,
                                                   List<String> otherConceptNames) {
        if (otherConceptNames == null) {
            return false;
        }
        String lower = claim.proposition().toLowerCase(Locale.ROOT);
        for (String name : otherConceptNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String candidate = name.toLowerCase(Locale.ROOT);
            if (!candidate.equals(concept.name().toLowerCase(Locale.ROOT))
                    && lower.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** The concepts a representation depends on. */
    public static Set<String> prerequisitesOf(Representation representation) {
        return new LinkedHashSet<>(representation.prerequisiteConceptIds());
    }
}
