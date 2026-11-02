package io.matrix.knowledge.acquisition;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SourceKind;
import java.util.ArrayList;
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

    /** The registry of a workspace. */
    public record Registry(
            List<Entry> entries,
            int sources,
            int imported,
            int authored,
            int documents,
            int claims,
            int independentConfirmations,
            List<String> notes) {

        public Registry {
            entries = List.copyOf(entries == null ? List.of() : entries);
            notes = List.copyOf(notes == null ? List.of() : notes);
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

        List<String> notes = new ArrayList<>();
        notes.add("The independent confirmations are counted by the DISTINCT content hashes of "
                + "the sources behind a claim: two documents with the same content are one "
                + "source, and ten copies of one source are one confirmation.");
        notes.add("The authority and the freshness of a source are as they were authored. The "
                + "registry does not rank the sources, because a ranking nobody can check is "
                + "not information.");

        return new Registry(entries, sources.size(), imported, sources.size() - imported,
                documents, claims.size(), maximumConfirmations, notes);
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

    /** The format of a source. */
    private static String formatOf(Source source) {
        String format = source.format();
        if (format != null && !format.isBlank()) {
            return format.toLowerCase(java.util.Locale.ROOT);
        }
        String locator = source.locator();
        if (locator == null || locator.isBlank()) {
            return "unknown";
        }
        int dot = locator.lastIndexOf('.');
        return dot < 0 ? "unknown" : locator.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
