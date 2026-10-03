package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RECON-W32.4 — a committed, re-runnable ingest for the knowledge forge corpus.
 *
 * <p><b>Why this class exists.</b> During RECON-W32 a record reformatting silently
 * truncated {@code data/mind/hdc_kb.ndjson} from 2 927 records to 17, destroying 2 901
 * acquired facts. Recovery was possible only because the forge corpus was never
 * committed and could be re-fetched — the acquisition had been a scratch program with no
 * way to replay it.</p>
 *
 * <p>Ingest is now a first-class, testable entry point rather than a throwaway. Running
 * it twice is safe: ids are content-derived, so a second run overwrites rather than
 * duplicating.</p>
 *
 * <p><b>Every fact goes through the promotion gate</b> ({@link PromotionGate}) and is
 * refused or accepted on its merits, so re-ingesting cannot launder poison into the store.
 * A refusal is counted and reported, never swallowed.</p>
 *
 * <p><b>Article III.</b> Deterministic: ids are derived from content, not the clock, and
 * a fixed seed is used throughout. The same corpus always produces the same store.</p>
 */
public final class KnowledgeForgeIngest {

    private KnowledgeForgeIngest() {}

    /** Vector width for the knowledge store. Unit: bits. */
    public static final int DEFAULT_DIM = 512;


    /**
     * Outcome of an ingest run.
     *
     * @param promoted facts accepted by the promotion gate
     * @param rejected facts refused
     * @param total    facts offered
     * @param reasons  refusal reasons and their counts
     */
    public record Report(int promoted, int rejected, int total,
                         Map<String, Integer> reasons) {
        /** Fraction of the offered corpus that became knowledge, in [0,1]. */
        public double acceptanceRate() {
            return total == 0 ? 0.0 : (double) promoted / (double) total;
        }

        @Override
        public String toString() {
            return "promoted=" + promoted + " rejected=" + rejected
                + " total=" + total + " reasons=" + reasons;
        }
    }

    /**
     * Read a forge corpus file.
     *
     * <p>Tolerant of the field spacing {@code json.dumps} produces as well as the compact
     * form, because the W32 data loss began with a file written by a Python rewriter
     * whose spacing the Java parser did not accept.</p>
     *
     * @param corpus NDJSON file, one fact per line
     * @return facts as {subject, predicate, object, lang} maps, in file order
     * @throws IOException if the file cannot be read
     */
    public static List<Map<String, String>> readCorpus(Path corpus) throws IOException {
        List<Map<String, String>> out = new ArrayList<>();
        for (String line : Files.readAllLines(corpus, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            Map<String, String> f = new LinkedHashMap<>();
            for (String key : new String[]{"subject", "predicate", "object", "lang"}) {
                f.put(key, field(line, key));
            }
            if (f.get("subject").isEmpty()) continue;    // unparseable line, not a fact
            out.add(f);
        }
        return out;
    }

    /** Extract one string field, tolerant of `: ` spacing after the colon. */
    private static String field(String line, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(line);
        return m.find() ? unescape(m.group(1)) : "";
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        return s.replace("\\n", "\n").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * The fact text taught for one corpus record.
     *
     * <p>Kept SHORT on purpose. {@code ContentSimilarity} scores coverage x precision,
     * so a fact retrievable by a one-word question must be about five content tokens or
     * fewer. Measured: "Kenya has capital Nairobi" (3 tokens) scores 0.667, while a
     * 9-token sentence scores below the 0.20 floor. See {@code SensorStreamDecoder} for
     * the same constraint applied to sensor streams.</p>
     */
    public static String factText(Map<String, String> fact) {
        return fact.get("subject") + " " + predicatePhrase(fact.get("predicate"))
            + " " + fact.get("object");
    }

    /** Human-readable predicate, in the form the retrieval path is written against. */
    private static String predicatePhrase(String predicate) {
        return switch (predicate) {
            case "has capital" -> "capital";
            case "is located on continent" -> "continent";
            case "has official language" -> "language";
            case "uses currency" -> "currency";
            case "has chemical symbol" -> "symbol";
            case "has largest city" -> "largest city";
            default -> predicate;
        };
    }

    /**
     * Content-derived id, so re-ingesting overwrites rather than duplicating.
     *
     * <p>The suffix keeps distinct facts about the same subject apart; the prefix makes
     * provenance visible to a human reading the store.</p>
     */
    public static String factId(Map<String, String> fact) {
        return "wd-" + fact.get("lang") + "-"
            + Integer.toHexString(factText(fact).hashCode()) + "-"
            + fact.get("predicate");
    }

    /**
     * Ingest a corpus into a store, through the promotion gate.
     *
     * @param store destination, already opened
     * @param facts corpus facts from {@link #readCorpus}
     * @return what was accepted and what was refused
     */
    public static Report ingest(PersistentHdcStore store, List<Map<String, String>> facts) {
        int promoted = 0;
        int rejected = 0;
        Map<String, Integer> reasons = new java.util.LinkedHashMap<>();
        for (Map<String, String> f : facts) {
            String text = factText(f);
            try {
                store.teach(factId(f), text);
                promoted++;
            } catch (PersistentHdcStore.PromotionRejectedException ex) {
                rejected++;
                reasons.merge(ex.reason(), 1, Integer::sum);
            }
        }
        return new Report(promoted, rejected, facts.size(), reasons);
    }

    /**
     * CLI entry point: {@code KnowledgeForgeIngest <corpus.ndjson> <hdc_kb.ndjson>}.
     *
     * <p>Committed so recovery from a truncated store is a documented command rather than
     * a thing someone has to remember how to do.</p>
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: KnowledgeForgeIngest <corpus.ndjson> <hdc_kb.ndjson> [dim]");
            System.exit(2);
        }
        Path corpus = Path.of(args[0]);
        Path store = Path.of(args[1]);
        int dim = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_DIM;
        List<Map<String, String>> facts = readCorpus(corpus);
        PersistentHdcStore hdc = new PersistentHdcStore(store, dim);
        Report r = ingest(hdc, facts);
        System.out.println("KnowledgeForgeIngest " + r);
        System.out.println("store now holds " + hdc.size() + " records at dim " + dim);
    }
}
