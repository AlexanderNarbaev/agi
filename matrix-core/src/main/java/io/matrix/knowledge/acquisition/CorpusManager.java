package io.matrix.knowledge.acquisition;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SourceKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The corpus registry of a workspace (RECON-W42.7).
 *
 * <p>The mission's §5.5 asks for a corpus manager: the record of what the
 * workspace knows, where it came from, and how much of it is independent.
 *
 * <p><b>What the registry says.</b> For every source: what it is, where it
 * came from, its content hash, how many claims it supports, and — the
 * point of the whole thing — <b>how many INDEPENDENT confirmations</b> the
 * workspace holds for each claim. Two documents with the same content are
 * one source, so a claim that ten copies support has one confirmation and
 * not ten.
 *
 * <p><b>What the registry refuses to do.</b> It does not rank the sources,
 * and it does not guess a source's authority or freshness: the
 * {@link Source} record carries the authority and the freshness as they
 * were authored, and the registry reports them. A registry that invented a
 * ranking would be a ranking nobody could check.
 */
public final class CorpusManager {

    /** One source, as the registry holds it. */
    public record Entry(
            String sourceId,
            SourceKind kind,
            String title,
            String locator,
            String contentHash,
            String language,
            String format,
            int claimsSupported,
            int independentConfirmations,
            boolean imported,
            String note) {
    }

    /**
     * One title and the versions filed under it.
     *
     * <p>RECON-W42.7. The registry could report that two sources share a
     * title, and could not say how many versions of that title the
     * workspace holds — so a workspace could not answer "is this the
     * second edition?".
     *
     * <p><b>A version is a distinct content under one title.</b> Two
     * documents with the same content are one source, so the versions of a
     * title are the sources whose content hashes differ.
     *
     * @param title the title
     * @param versions the versions, oldest authored publication first
     * @param versionCount how many versions there are
     * @param latestPublishedAt the latest authored publication date, or
     *     "unknown" when no version carries one
     * @param latestSourceId the source of that latest version, or null
     * @param note what the registry says about the group
     */
    public record TitleGroup(
            String title,
            List<Entry> versions,
            int versionCount,
            String latestPublishedAt,
            String latestSourceId,
            String note) {

        public TitleGroup {
            versions = List.copyOf(versions == null ? List.of() : versions);
        }
    }

    /** The registry of a workspace. */
    public record Registry(
            List<Entry> entries,
            int sources,
            int imported,
            int authored,
            int documents,
            int claims,
            int independentConfirmations,
            List<String> notes,
            List<TitleGroup> titles) {

        public Registry {
            entries = List.copyOf(entries == null ? List.of() : entries);
            notes = List.copyOf(notes == null ? List.of() : notes);
            titles = List.copyOf(titles == null ? List.of() : titles);
        }

        /**
         * The versions of one title.
         *
         * @param title the title
         * @return the group, or null when the workspace holds no such title
         */
        public TitleGroup title(String title) {
            for (TitleGroup group : titles) {
                if (group.title().equals(title)) {
                    return group;
                }
            }
            return null;
        }
    }

    private CorpusManager() {
    }

    /**
     * The registry of a workspace.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @return the registry
     */
    public static Registry registry(KnowledgeWorkbench workbench, String workspaceId) {
        List<Source> sources = workbench.workspace(workspaceId).sources();
        List<Claim> claims = workbench.workspace(workspaceId).claims();
        List<Evidence> evidence = workbench.workspace(workspaceId).evidence();

        // The content hash of every source, by id.
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Source source : sources) {
            hashes.put(source.id(), source.contentHash());
        }
        // The evidence of every source.
        Map<String, List<Evidence>> bySource = new LinkedHashMap<>();
        for (Evidence item : evidence) {
            bySource.computeIfAbsent(item.sourceId(), key -> new ArrayList<>()).add(item);
        }
        // The independent confirmations of every claim: the DISTINCT
        // content hashes of the sources behind it.
        Map<String, Set<String>> confirmations = new LinkedHashMap<>();
        for (Evidence item : evidence) {
            String hash = hashes.get(item.sourceId());
            if (hash != null) {
                confirmations.computeIfAbsent(item.claimId(), key -> new LinkedHashSet<>())
                        .add(hash);
            }
        }

        List<Entry> entries = new ArrayList<>();
        int imported = 0;
        int documents = 0;
        int maximumConfirmations = 0;
        for (Source source : sources) {
            List<Evidence> sourceEvidence = bySource.getOrDefault(source.id(), List.of());
            int confirmationsFor = 0;
            for (Evidence item : sourceEvidence) {
                confirmationsFor = Math.max(confirmationsFor,
                        confirmations.getOrDefault(item.claimId(), Set.of()).size());
            }
            maximumConfirmations = Math.max(maximumConfirmations, confirmationsFor);
            boolean isImported = source.id().startsWith("src-");
            if (isImported) {
                imported++;
                documents++;
            }
            entries.add(new Entry(source.id(), source.kind(), source.title(), source.locator(),
                    source.contentHash(), source.language(), formatOf(source),
                    sourceEvidence.size(), confirmationsFor, isImported,
                    sourceEvidence.isEmpty()
                            ? "the source supports no claim in this workspace"
                            : "the source supports " + sourceEvidence.size() + " claim(s), "
                                    + "and the best supported of them has "
                                    + confirmationsFor + " independent confirmation(s)"));
        }

        // THE VERSIONS, GROUPED BY TITLE.
        //
        // The versions of a title are the sources filed under it whose
        // contents differ. They are ordered by the AUTHORED publication
        // date, and the sources that carry no date go last — the registry
        // does not guess a date, and it does not order by the moment the
        // document happened to be imported.
        Map<String, List<Entry>> byTitle = new LinkedHashMap<>();
        Map<String, List<Source>> sourcesByTitle = new LinkedHashMap<>();
        for (Source source : sources) {
            String title = source.title() == null || source.title().isBlank()
                    ? "(untitled)" : source.title();
            sourcesByTitle.computeIfAbsent(title, key -> new ArrayList<>()).add(source);
        }
        for (Entry entry : entries) {
            String title = entry.title() == null || entry.title().isBlank()
                    ? "(untitled)" : entry.title();
            byTitle.computeIfAbsent(title, key -> new ArrayList<>()).add(entry);
        }
        List<TitleGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<Source>> group : sourcesByTitle.entrySet()) {
            String title = group.getKey();
            List<Source> groupSources = new ArrayList<>(group.getValue());
            groupSources.sort(Comparator
                    .comparing((Source source) -> publishedAtOf(source), Comparator.nullsLast(
                            Comparator.naturalOrder()))
                    .thenComparing(Source::id));
            List<Entry> versions = new ArrayList<>();
            for (Source source : groupSources) {
                for (Entry entry : byTitle.getOrDefault(title, List.of())) {
                    if (entry.sourceId().equals(source.id())) {
                        versions.add(entry);
                    }
                }
            }
            String latestPublished = "unknown";
            String latestId = null;
            for (Source source : groupSources) {
                String published = publishedAtOf(source);
                if (published != null && !published.isBlank()) {
                    latestPublished = published;
                    latestId = source.id();
                }
            }
            groups.add(new TitleGroup(title, versions, versions.size(), latestPublished, latestId,
                    versions.size() == 1
                            ? "the workspace holds one version of this title"
                            : "the workspace holds " + versions.size() + " versions of this "
                                    + "title, and they differ in content"));
        }
        groups.sort(Comparator.comparing(TitleGroup::title));

        List<String> notes = new ArrayList<>();
        notes.add("The independent confirmations are counted by the DISTINCT content hashes of "
                + "the sources behind a claim: two documents with the same content are one "
                + "source, and ten copies of one source are one confirmation.");
        notes.add("The authority and the freshness of a source are as they were authored. The "
                + "registry does not rank the sources, because a ranking nobody can check is "
                + "not information.");
        notes.add("The versions of a title are the sources filed under it whose contents differ. "
                + "They are ordered by the AUTHORED publication date, and a version without one "
                + "goes last: the registry does not guess a date, and the moment a document "
                + "happened to be imported is not a publication date.");

        return new Registry(entries, sources.size(), imported, sources.size() - imported,
                documents, claims.size(), maximumConfirmations, notes, groups);
    }

    /**
     * The authored publication date of a source.
     *
     * @param source the source
     * @return the date, or null when it was not authored
     */
    private static String publishedAtOf(Source source) {
        String published = source.publishedAt();
        if (published == null || published.isBlank() || published.equalsIgnoreCase("unknown")) {
            return null;
        }
        return published;
    }

    /**
     * Whether the workspace already holds a source with this content.
     *
     * <p>The import is idempotent: the same document imported twice is one
     * source, and the second import adds nothing.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @param contentHash the content hash
     * @return the source, or null when the content is new
     */
    public static Source known(KnowledgeWorkbench workbench, String workspaceId,
                                String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return null;
        }
        for (Source source : workbench.workspace(workspaceId).sources()) {
            if (contentHash.equals(source.contentHash())) {
                return source;
            }
        }
        return null;
    }

    /**
     * The format of a source.
     *
     * <p>The format is what the importer detected. The first version of
     * this guessed it from the locator's extension, and the guess was
     * garbage for the authored sources: the locator of a Wikipedia
     * reference is "org/wiki/cellular_respiration (revision 1382176743,
     * ...)", and the "extension" of that is "9-5, pmcid pmc7886073,
     * retrieved 2026-10-31)". A guess nobody can check is not a format,
     * so the guess is gone and the answer is "unknown" when the format
     * was not recorded.
     */
    private static String formatOf(Source source) {
        String format = source.format();
        if (format != null && !format.isBlank()) {
            return format.toLowerCase(java.util.Locale.ROOT);
        }
        return "unknown";
    }
}
