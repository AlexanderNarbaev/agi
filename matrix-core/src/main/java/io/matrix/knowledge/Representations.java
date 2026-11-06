package io.matrix.knowledge;

import java.util.List;

/**
 * Which representation of a concept is the active one at a given level.
 *
 * <p>The refinement chain has two kinds of link, and they must not be
 * confused:
 *
 * <ul>
 *   <li>a link BETWEEN levels - "the intermediate account refines the
 *       introductory one" - says that the concept has a more detailed
 *       account. Both are current, at their own levels.</li>
 *   <li>a link WITHIN a level - "the corrected account refines the earlier
 *       one at the introductory level" - says that the earlier account at
 *       that level is no longer the one to read.</li>
 * </ul>
 *
 * <p>The active account at a level is therefore the one that nothing at the
 * SAME level refines. An account that has been corrected stays in the chain
 * and stays visible, but it is not the one the reader is given.
 */
public final class Representations {

    private Representations() {
    }

    /** The representation at a level that nothing at that level refines. */
    public static Representation activeAt(List<Representation> available, AbstractionLevel level) {
        List<Representation> atLevel = available.stream()
                .filter(r -> r.level() == level)
                .toList();
        if (atLevel.isEmpty()) {
            return null;
        }
        List<String> superseded = atLevel.stream()
                .filter(r -> r.refinesRepresentationId() != null)
                .map(Representation::refinesRepresentationId)
                .toList();
        List<Representation> active = atLevel.stream()
                .filter(r -> !superseded.contains(r.id()))
                .toList();
        List<Representation> candidates = active.isEmpty() ? atLevel : active;
        Representation newest = candidates.get(0);
        for (Representation candidate : candidates) {
            if (candidate.createdAt().compareTo(newest.createdAt()) >= 0) {
                newest = candidate;
            }
        }
        return newest;
    }

    /** The representation the retriever should read for a requested level. */
    public static Representation forLevel(List<Representation> available, AbstractionLevel wanted) {
        Representation best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (AbstractionLevel level : AbstractionLevel.values()) {
            if (level.ordinal() > wanted.ordinal()) {
                continue;
            }
            Representation candidate = activeAt(available, level);
            if (candidate == null) {
                continue;
            }
            int distance = wanted.ordinal() - level.ordinal();
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best != null) {
            return best;
        }
        // Nothing at or below the wanted level: take the closest above it.
        for (AbstractionLevel level : AbstractionLevel.values()) {
            if (level.ordinal() < wanted.ordinal()) {
                continue;
            }
            Representation candidate = activeAt(available, level);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
