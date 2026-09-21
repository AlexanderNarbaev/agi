package io.matrix.consciousness;

import java.util.HashSet;
import java.util.Set;

/**
 * RUN 262 — CycleTag (categorical tagging).
 *
 * <p>Tags applied to a cycle for routing, analysis, or filtering.
 * Tags are simple strings; can be added/removed dynamically.
 */
public final class CycleTag {

    public record TagSet(Set<String> tags) {}

    public CycleTag() { this(new TagSet(new HashSet<>())); }

    public CycleTag(TagSet ts) {
        // Shallow copy
        this.tags = new HashSet<>(ts.tags);
    }

    private final Set<String> tags;

    public boolean add(String tag) { return tags.add(tag); }
    public boolean remove(String tag) { return tags.remove(tag); }
    public boolean contains(String tag) { return tags.contains(tag); }
    public int size() { return tags.size(); }
    public Set<String> all() { return new HashSet<>(tags); }

    public boolean hasAll(Set<String> required) {
        return tags.containsAll(required);
    }

    public boolean hasAny(Set<String> candidates) {
        for (String c : candidates) {
            if (tags.contains(c)) return true;
        }
        return false;
    }
}
