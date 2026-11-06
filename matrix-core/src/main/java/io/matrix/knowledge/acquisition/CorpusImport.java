package io.matrix.knowledge.acquisition;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The import of a corpus, and the report of what it did (RECON-W43.4).
 *
 * <p>The mission's §7 asks for an import that can be INSPECTED: the
 * structure, the locations, the warnings, the hashes, the deduplication, the
 * licensing and the traceability from a claim back to a span of a source.
 * This class produces that report and nothing else — it does not decide
 * what the corpus means, it says what happened to it.
 *
 * <p><b>What the report carries.</b> For every file: the format, the
 * licence the manifest records, whether the bytes matched the manifest, the
 * source the import created, how many segments and claims it produced, the
 * warnings the import itself raised, and the chain from each claim back to
 * the span of the file it came from.
 *
 * <p><b>What it does not do.</b> It does not promote anything, it does not
 * measure anything, and it does not decide that a claim is true. The
 * imported claims enter as CANDIDATE, exactly as they did before, and the
 * validation stage decides what they are worth.
 */
public final class CorpusImport {

    /** What happened to one file of the corpus. */
    public record FileReport(String file, String format, String licence, boolean hashMatches,
                             String sourceId, int segments, int claimsProposed, int claimsAdded,
                             int conceptsCreated, List<String> skipped, List<ClaimTrace> claims,
                             List<String> warnings) {

        public FileReport {
            skipped = List.copyOf(skipped == null ? List.of() : skipped);
            claims = List.copyOf(claims == null ? List.of() : claims);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }

    /** One claim and the span of the file it came from. */
    public record ClaimTrace(String claimId, String proposition, String sourceId, String span) {
    }

    /** The report of the whole import. */
    public record Report(List<FileReport> files, CorpusManifest.Report manifest,
                         List<String> warnings) {

        public Report {
            files = List.copyOf(files == null ? List.of() : files);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        /** The number of files imported. */
        public int filesImported() {
            return files.size();
        }

        /** The number of claims the corpus produced. */
        public int claimsAdded() {
            return files.stream().mapToInt(FileReport::claimsAdded).sum();
        }

        /** The number of segments the corpus produced. */
        public int segments() {
            return files.stream().mapToInt(FileReport::segments).sum();
        }

        /** The number of claims that can be traced back to a span. */
        public int claimsTraced() {
            return (int) files.stream()
                    .flatMap(file -> file.claims().stream())
                    .filter(trace -> trace.span() != null && !trace.span().isBlank())
                    .count();
        }

        /** The number of files whose licence the manifest records. */
        public int licensed() {
            return (int) files.stream()
                    .filter(file -> file.licence() != null && !file.licence().isBlank())
                    .count();
        }
    }

    private CorpusImport() {
    }

    /**
     * Imports a corpus into a workspace and reports what happened.
     *
     * @param workbench the workbench
     * @param workspaceId the workspace
     * @param corpusDirectory the corpus
     * @return the report
     * @throws IOException when the corpus or the manifest cannot be read
     */
    public static Report run(KnowledgeWorkbench workbench, String workspaceId,
                              Path corpusDirectory) throws IOException {
        CorpusManifest.Report manifest = CorpusManifest.verify(corpusDirectory);
        Map<String, CorpusManifest.EntryReport> byFile = new LinkedHashMap<>();
        for (CorpusManifest.EntryReport entry : manifest.entries()) {
            byFile.put(entry.file(), entry);
        }

        List<FileReport> reports = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path file : CorpusManifest.files(corpusDirectory)) {
            String name = file.getFileName().toString();
            CorpusManifest.EntryReport entry = byFile.get(name);
            List<String> fileWarnings = new ArrayList<>();
            if (entry == null || !entry.hashMatches()) {
                fileWarnings.add("the manifest does not describe this file, or its bytes differ "
                        + "from the manifest; the import is refused");
                warnings.addAll(fileWarnings);
                reports.add(new FileReport(name, null, entry == null ? null : entry.licence(),
                        entry != null && entry.hashMatches(), null, 0, 0, 0, 0, List.of(), List.of(),
                        fileWarnings));
                continue;
            }

            DocumentImporter.ImportResult result = workbench.importDocument(workspaceId, file,
                    entry.language());
            List<ClaimTrace> traces = traces(workbench, workspaceId, result.source().sourceId());
            List<String> skipped = new ArrayList<>(result.skipped());
            fileWarnings.addAll(skipped);
            reports.add(new FileReport(name, entry.format(), entry.licence(), true,
                    result.source().sourceId(), result.segments(), result.claimsProposed(),
                    result.claimsAdded(), result.conceptsCreated(), skipped, traces,
                    fileWarnings));
        }
        return new Report(reports, manifest, warnings);
    }

    /**
     * The claims of a source, and the span of the file each came from.
     *
     * <p>The span is the claim's own location, which the extraction stage
     * recorded: the segment of the document the claim was read from. A
     * claim without a span is a claim that cannot be traced, and it is
     * reported with an empty span rather than with a plausible one.
     */
    private static List<ClaimTrace> traces(KnowledgeWorkbench workbench, String workspaceId,
                                           String sourceId) {
        List<ClaimTrace> traces = new ArrayList<>();
        for (Claim claim : workbench.storeForValidation().claims(workspaceId)) {
            if (!sourceId.equals(claim.introducedBySourceId())) {
                continue;
            }
            traces.add(new ClaimTrace(claim.id(), claim.proposition(), claim.introducedBySourceId(),
                    claim.scope()));
        }
        return traces;
    }

    /**
     * Whether a source holds any evidence.
     *
     * @param workbench the workbench
     * @param workspaceId the workspace
     * @param sourceId the source
     * @return whether the source is linked to anything
     */
    public static boolean sourceIsUsed(KnowledgeWorkbench workbench, String workspaceId,
                                       String sourceId) {
        for (Evidence evidence : workbench.storeForValidation().evidence(workspaceId)) {
            if (sourceId.equals(evidence.sourceId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The source of a workspace, by its id.
     *
     * @param workbench the workbench
     * @param workspaceId the workspace
     * @param sourceId the source
     * @return the source, or null
     */
    public static Source source(KnowledgeWorkbench workbench, String workspaceId, String sourceId) {
        for (Source source : workbench.storeForValidation().sources(workspaceId)) {
            if (source.id().equals(sourceId)) {
                return source;
            }
        }
        return null;
    }
}
