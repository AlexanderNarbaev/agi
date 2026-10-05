package io.matrix.knowledge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * W477 — Simple Knowledge Base for RAG.
 * 
 * A minimal in-memory knowledge base that:
 * - Loads documents from data/knowledge/
 * - Performs simple keyword-based retrieval
 * - Returns relevant context for LLM augmentation
 * 
 * Per CONSTITUTION I: deterministic, no randomness in retrieval.
 * Per CONSTITUTION VI: provides grounding for LLM responses.
 */
public final class SimpleKnowledgeBase {
    
    public record Document(String id, String title, String content) {}
    public record RetrievalResult(Document doc, double score) {}
    
    private final Map<String, Document> documents = new HashMap<>();
    
    public SimpleKnowledgeBase() {
        loadFromDir(Paths.get("data/knowledge"));
    }
    
    public void loadFromDir(Path dir) {
        if (!Files.exists(dir)) {
            // RECON-W32.26: an ABSENT directory is a legitimate empty base, and it is
            // NOT a failure — conflating the two would cry wolf on every fresh install.
            // The counters are reset so a previous broken load cannot be mistaken for
            // this one, which is the difference this whole change is about.
            listingFailed = false;
            skippedDocs = 0;
            return;
        }
        listingFailed = false;
        skippedDocs = 0;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".txt"))
                 .forEach(p -> {
                     try {
                         String content = Files.readString(p);
                         String id = p.getFileName().toString();
                         String title = id.replaceAll("\\.(md|txt)$", "");
                         documents.put(id, new Document(id, title, content));
                     } catch (IOException e) {
                         // RECON-W32.26: was `// ignore`. An unreadable document was
                         // silently dropped from RAG grounding, and size() then reported
                         // a smaller knowledge base with no reason given.
                         noteSkippedDoc(p, e);
                     }
                 });
        } catch (IOException e) {
            // RECON-W32.26: the worst silent case found in this audit. A listing failure
            // leaves `documents` EMPTY, retrieve() returns nothing, buildContext() returns
            // "", and the brain answers with ZERO grounding while stats() reports a
            // perfectly plausible "KB: 0" — which is indistinguishable from a directory
            // that legitimately holds no documents. An empty memory and a broken memory
            // must not look the same.
            listingFailed = true;
            System.err.println("[SimpleKnowledgeBase] LISTING FAILED for " + dir
                + ": " + e + " - the knowledge base is EMPTY and every answer will be "
                + "ungrounded; check the path before trusting any reply");
        }
    }

    /**
     * Documents that could not be read, and whether the directory itself could be listed.
     *
     * <p>RECON-W32.26. Unit: files, plus a boolean. Both are non-zero/true only after a
     * real failure, and both are the difference between "no documents" and "documents
     * we cannot see".</p>
     */
    private int skippedDocs = 0;
    private boolean listingFailed = false;

    private void noteSkippedDoc(Path p, Exception e) {
        skippedDocs++;
        if (skippedDocs == 1) {
            System.err.println("[SimpleKnowledgeBase] skipped unreadable document " + p
                + ": " + e + " - it will NOT appear in retrieval; further skips are counted");
        }
    }

    /** Documents dropped because they could not be read. Unit: files. */
    public int skippedDocs() { return skippedDocs; }

    /**
     * True when the document directory could not be listed at all.
     *
     * <p>Unit: a boolean. This is the one to check before believing an empty knowledge
     * base, because an empty base and an unreadable directory look identical otherwise.</p>
     */
    public boolean listingFailed() { return listingFailed; }
    
    public void addDocument(String id, String title, String content) {
        documents.put(id, new Document(id, title, content));
    }
    
    /**
     * Retrieve top-K documents matching a query using simple TF-IDF-like scoring.
     */
    public List<RetrievalResult> retrieve(String query, int k) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        String[] queryTerms = query.toLowerCase().split("\\s+");
        
        List<RetrievalResult> results = new ArrayList<>();
        for (Document doc : documents.values()) {
            double score = 0.0;
            String content = doc.content.toLowerCase();
            for (String term : queryTerms) {
                if (term.length() < 3) continue;
                int count = 0;
                int idx = 0;
                while ((idx = content.indexOf(term, idx)) != -1) {
                    count++;
                    idx += term.length();
                }
                score += count;
            }
            if (score > 0) {
                results.add(new RetrievalResult(doc, score));
            }
        }
        
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results.subList(0, Math.min(k, results.size()));
    }
    
    /**
     * Build a context string from retrieved documents for LLM augmentation.
     */
    public String buildContext(String query, int k) {
        List<RetrievalResult> results = retrieve(query, k);
        if (results.isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder("Relevant context:\n");
        for (RetrievalResult r : results) {
            sb.append("- [").append(r.doc.id()).append("]\n");
            // Truncate long content
            String content = r.doc.content;
            if (content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }
            sb.append(content).append("\n\n");
        }
        return sb.toString();
    }
    
    public int size() { return documents.size(); }
    
    public static void main(String[] args) throws Exception {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        System.out.println("Knowledge base size: " + kb.size());
        if (args.length > 0) {
            String query = args[0];
            System.out.println("Query: " + query);
            System.out.println("Results:");
            for (RetrievalResult r : kb.retrieve(query, 3)) {
                System.out.println("  " + r.doc.id() + " (score=" + r.score() + ")");
            }
            System.out.println();
            System.out.println("Context:");
            System.out.println(kb.buildContext(query, 3));
        }
    }
}
