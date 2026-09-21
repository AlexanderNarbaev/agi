package io.matrix.consciousness;

import io.matrix.perception.SaliencyEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RUN 154 — AttentionRouter (top-down × bottom-up merge).
 *
 * <p>Combines impulses (top-down motivation) with saliency
 * (bottom-up attention). Output is a deterministic list of
 * FocusItems sorted by mergedScore descending.
 *
 * <p>Per CONSTITUTION I, merge is fully deterministic: ordering
 * is by score, then by source name, then by target. So two
 * cognitive cycles with the same inputs produce the same
 * attention ordering.
 */
public final class AttentionRouter {

    public record FocusItem(String name,
                            double mergedScore,
                            double impulseContribution,
                            double saliencyContribution,
                            String source) {}

    public List<FocusItem> merge(List<Impulse> impulses,
                                 List<SaliencyEngine.SaliencyScore> saliencies) {
        List<FocusItem> items = new ArrayList<>();

        // 1. Top-down items: each impulse becomes a focus item.
        for (Impulse imp : impulses) {
            double impulseContrib = imp.effectiveScore();
            items.add(new FocusItem(
                    "impulse:" + imp.source + ":" + imp.target,
                    impulseContrib,
                    impulseContrib,
                    0.0,
                    "impulse"));
        }

        // 2. Bottom-up items: each saliency becomes a focus item.
        for (SaliencyEngine.SaliencyScore s : saliencies) {
            items.add(new FocusItem(
                    "saliency:" + s.source(),
                    s.score(),
                    0.0,
                    s.score(),
                    "saliency"));
        }

        // 3. Sort deterministic: by mergedScore desc, then name asc.
        items.sort(Comparator
                .comparingDouble(FocusItem::mergedScore).reversed()
                .thenComparing(FocusItem::name));

        return items;
    }

    public List<FocusItem> take(List<FocusItem> items, int maxItems) {
        if (items.size() <= maxItems) return new ArrayList<>(items);
        return new ArrayList<>(items.subList(0, maxItems));
    }
}
