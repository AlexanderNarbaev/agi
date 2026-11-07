package io.matrix.knowledge.acquisition;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeChanges;
import io.matrix.knowledge.KnowledgeRefiner;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.ModelRevisionDecider;
import io.matrix.knowledge.Source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * The bounded learning cycle: a gap, a declared source catalogue, an import,
 * a validation, and a report.
 *
 * <p><b>Why the cycle is bounded.</b> The cycle takes at most
 * {@link #MAXIMUM_DOCUMENTS} documents from the research pool for one
 * question. A learning loop that can pull an unbounded number of documents
 * is not a learning loop, it is a download.
 *
 * <p><b>Why the pool is declared.</b> The pool is a versioned, licensed set
 * of documents that ships with the application and is listed in a manifest
 * with the licence, the canonical URL, the snapshot revision and the sha256
 * of the imported bytes. No request leaves the machine. The report says so
 * plainly, because a reader must never mistake a pre-cached pool for a live
 * web search.
 */
public final class AcquisitionCycle {

    /** The seed corpus: the material the workspace starts with. */
    public static final String SEED_CORPUS = "corpus/plate-tectonics";

    /** The research pool: the material the cycle may acquire. */
    public static final String RESEARCH_POOL = "research-pool/plate-tectonics";

    /** How many documents one cycle may acquire. */
    public static final int MAXIMUM_DOCUMENTS = 3;

    /** What the pool is, stated where the owner can read it. */
    public static final String BOUNDARY = "a pre-cached, versioned and licensed research pool that ships "
            + "with the application; no request leaves the machine and no live web search is performed";

    /** One document the cycle touched. */
    public record Step(String file, String licence, String sourceUrl, String accessedAt, String revision,
                       int segments, int claimsProposed, int claimsAdded, int conceptsCreated,
                       List<String> skipped, String status) {
    }

    /** What the cycle did. */
    public record Report(String workspaceId, String question, List<String> uncoveredTerms,
                         List<String> questionTerms,
                         List<String> candidatesConsidered, List<String> acquired, List<Step> steps,
                         String boundary, int documentsAcquired, int claimsAdded, List<String> warnings) {
    }

    private AcquisitionCycle() {
    }

    /**
     * Runs one bounded acquisition cycle.
     *
     * @param workbench the workbench that owns the workspace
     * @param workspaceId the workspace to learn in
     * @param question the question whose gap drives the cycle
     * @param maximumDocuments how many documents the cycle may acquire
     * @return the report of the cycle
     * @throws IOException when the pool or a document cannot be read
     */
    public static Report run(KnowledgeWorkbench workbench, String workspaceId, String question,
                              int maximumDocuments) throws IOException {
        List<String> warnings = new ArrayList<>();
        List<String> uncovered = workbench.gapDetector(workspaceId).uncoveredTerms(question);
        List<String> questionTerms = termsOf(question);
        ClasspathCorpus pool = ClasspathCorpus.extract(RESEARCH_POOL);
        List<ClasspathCorpus.Entry> considered = new ArrayList<>(pool.entries());
        List<Map.Entry<ClasspathCorpus.Entry, Integer>> scored = new ArrayList<>();
        for (ClasspathCorpus.Entry entry : considered) {
            int hits = score(pool, entry, questionTerms, uncovered);
            if (hits > 0) {
                scored.add(Map.entry(entry, hits));
            }
        }
        // The order is the score first and the name second, so the same
        // question always selects the same documents.
        scored.sort((left, right) -> {
            int byScore = Integer.compare(right.getValue(), left.getValue());
            return byScore != 0 ? byScore : left.getKey().file().compareTo(right.getKey().file());
        });
        List<ClasspathCorpus.Entry> selected = new ArrayList<>();
        for (Map.Entry<ClasspathCorpus.Entry, Integer> item : scored) {
            if (selected.size() >= Math.max(0, maximumDocuments)) {
                break;
            }
            selected.add(item.getKey());
        }
        List<Step> steps = new ArrayList<>();
        List<String> acquired = new ArrayList<>();
        int claimsAdded = 0;
        for (ClasspathCorpus.Entry entry : selected) {
            DocumentImporter.ImportResult result =
                    workbench.importDocument(workspaceId, entry.path(), entry.file(), entry.language());
            steps.add(new Step(entry.file(), entry.licence(), entry.sourceUrl(), entry.accessedAt(),
                    entry.revision(), result.segments(), result.claimsProposed(), result.claimsAdded(),
                    result.conceptsCreated(), result.skipped(), "imported"));
            acquired.add(entry.file());
            claimsAdded += result.claimsAdded();
        }
        if (selected.isEmpty()) {
            warnings.add("no document of the research pool covers the terms this workspace is missing; "
                    + "nothing was acquired and nothing was invented");
        }
        List<String> consideredNames = new ArrayList<>();
        for (ClasspathCorpus.Entry entry : considered) {
            consideredNames.add(entry.file());
        }
        return new Report(workspaceId, question, uncovered, questionTerms, consideredNames, acquired, steps,
                BOUNDARY, acquired.size(), claimsAdded, warnings);
    }

    /**
     * Loads the seed corpus into a workspace.
     *
     * @param workbench the workbench that owns the workspace
     * @param workspaceId the workspace to load
     * @return the report of the import
     * @throws IOException when the corpus cannot be read
     */
    public static Report loadSeed(KnowledgeWorkbench workbench, String workspaceId) throws IOException {
        ClasspathCorpus corpus = ClasspathCorpus.extract(SEED_CORPUS);
        List<Step> steps = new ArrayList<>();
        List<String> acquired = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int claimsAdded = 0;
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            DocumentImporter.ImportResult result =
                    workbench.importDocument(workspaceId, entry.path(), entry.file(), entry.language());
            steps.add(new Step(entry.file(), entry.licence(), entry.sourceUrl(), entry.accessedAt(),
                    entry.revision(), result.segments(), result.claimsProposed(), result.claimsAdded(),
                    result.conceptsCreated(), result.skipped(), "imported"));
            acquired.add(entry.file());
            claimsAdded += result.claimsAdded();
        }
        return new Report(workspaceId, "seed corpus", List.of(), List.of(), acquired, acquired, steps,
                "the licensed seed corpus that ships with the application", acquired.size(), claimsAdded,
                warnings);
    }


    /** What the revision attempt did. */
    public record RevisionReport(String workspaceId, String file, String sourceId, int claimsAdded,
                                  List<ModelRevisionDecider.Decision> decisions, int revisionsApplied,
                                  List<String> warnings) {
    }

    /**
     * Acquires one document of the research pool and compares it with the model.
     *
     * <p>The new claims are compared with the claims that existed before the
     * import. When a new claim is about the same subject as an established
     * claim and is better supported, the established claim is superseded and
     * kept in the history; otherwise the new claim is only an addition.
     *
     * @param workbench the workbench that owns the workspace
     * @param workspaceId the workspace to learn in
     * @param file the name of the document of the research pool
     * @return the report of the attempt
     * @throws IOException when the pool cannot be read or the file is unknown
     */
    public static RevisionReport acquireAndRevise(KnowledgeWorkbench workbench, String workspaceId,
                                                   String file) throws IOException {
        ClasspathCorpus pool = ClasspathCorpus.extract(RESEARCH_POOL);
        ClasspathCorpus.Entry entry = null;
        for (ClasspathCorpus.Entry candidate : pool.entries()) {
            if (candidate.file().equals(file)) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) {
            throw new IOException("the research pool has no document called " + file);
        }
        KnowledgeSnapshot before = workbench.workspace(workspaceId);
        DocumentImporter.ImportResult result =
                workbench.importDocument(workspaceId, entry.path(), entry.file(), entry.language());
        KnowledgeSnapshot after = workbench.workspace(workspaceId);
        Source source = sourceOf(after, result.source().sourceId());
        List<ModelRevisionDecider.Decision> decisions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int applied = 0;
        for (Claim claim : after.claims()) {
            if (!result.source().sourceId().equals(claim.introducedBySourceId())) {
                continue;
            }
            ModelRevisionDecider.Decision decision = ModelRevisionDecider.decide(before,
                    claim.conceptId(), claim.proposition(), claim.confidence(), source);
            decisions.add(decision);
            KnowledgeRefiner.Refinement refinement =
                    ModelRevisionDecider.refinement(decision, before, claim, source);
            if (refinement == null) {
                continue;
            }
            KnowledgeChanges changes = new KnowledgeRefiner().refine(workspaceId, after, refinement);
            workbench.storeForValidation().applyChanges(workspaceId, changes, after.concepts());
            after = workbench.workspace(workspaceId);
            applied++;
        }
        if (source == null) {
            warnings.add("the imported source is not in the workspace; the new claims cannot be compared");
        }
        return new RevisionReport(workspaceId, entry.file(), result.source().sourceId(),
                result.claimsAdded(), decisions, applied, warnings);
    }

    /** The words a question is built from that say nothing about the subject. */
    private static final java.util.Set<String> QUESTION_STOP_WORDS = java.util.Set.of(
            "what", "which", "how", "does", "did", "the", "and", "for", "that", "this",
            "with", "from", "are", "was", "were", "have", "has", "been", "they", "their",
            "there", "here", "into", "about", "tell", "give", "explain", "describe",
            "much", "many", "more", "when", "where", "why", "will", "would");

    /**
     * The content terms of a question.
     *
     * <p>The selection of a document has to follow the question, not the
     * order the pool happens to be in: a document that talks about the
     * subject of the question is the one worth acquiring.
     *
     * @param question the question
     * @return the content terms, in the order they appear
     */
    private static List<String> termsOf(String question) {
        List<String> terms = new ArrayList<>();
        if (question == null) {
            return terms;
        }
        for (String word : question.toLowerCase(Locale.ROOT).split("[^a-z0-9-]+")) {
            if (word.length() < 4 || QUESTION_STOP_WORDS.contains(word) || terms.contains(word)) {
                continue;
            }
            terms.add(word);
        }
        return terms;
    }

    private static Source sourceOf(KnowledgeSnapshot snapshot, String sourceId) {
        for (Source source : snapshot.sources()) {
            if (source.id().equals(sourceId)) {
                return source;
            }
        }
        return null;
    }

    private static int score(ClasspathCorpus pool, ClasspathCorpus.Entry entry, List<String> questionTerms,
                             List<String> uncovered) throws IOException {
        String text = pool.textOf(entry).toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String term : questionTerms) {
            if (text.contains(term)) {
                // A term the workspace cannot cover weighs more than one
                // it already covers: the cycle is here to close a gap.
                hits += uncovered.contains(term) ? 3 : 1;
            }
        }
        return hits;
    }
}
