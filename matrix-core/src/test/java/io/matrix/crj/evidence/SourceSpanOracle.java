package io.matrix.crj.evidence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The domain-A oracle of CRJ-01: the <b>source-span oracle</b>.
 *
 * <p><b>What question of truth it answers.</b> Does the served response cite the evidence the
 * source document <b>actually contains</b>, for this question?</p>
 *
 * <p><b>Why it is a separate implementation.</b> The oracle's truth is computed by reading the
 * corpus file's BYTES. It never calls the retriever, the workbench, the kernel selector or the
 * session, so it cannot be fooled by a retrieval bug and it cannot inherit the solver's notion of
 * relevance. It is a second implementation of the truth, not a re-run of the first. The
 * instrument's own control — a gold proposition corrupted by a single character — is what makes
 * the independence testable rather than asserted.</p>
 *
 * <p><b>What it does, in order.</b></p>
 * <ol>
 *   <li>it re-reads the named corpus file's bytes and re-computes their sha256, comparing it
 *       with the digest the manifest froze;</li>
 *   <li>it asserts that every gold proposition occurs <b>verbatim</b> in those bytes — and it
 *       fails loudly when it does not, because a case whose gold is not in the corpus is a broken
 *       case, not an ungrounded answer;</li>
 *   <li>it reads the served answer and extracts the cited propositions and claim ids;</li>
 *   <li>it scores a POSITIVE case as grounded only when at least one cited proposition is
 *       byte-identical to a gold proposition <b>and</b> its claim id is the production claim id
 *       the manifest froze for that document;</li>
 *   <li>it scores a NEGATIVE case by comparing the served answer's disposition field — the one
 *       the gold names — with the expected disposition, and the expected provenance with the
 *       sources the answer names as refuting.</li>
 * </ol>
 *
 * <p><b>Why it lives in the test tree.</b> The oracle is an instrument of the experiment, not a
 * product feature. Keeping it out of {@code src/main} keeps it out of the serving path and out
 * of the packaged application, which is exactly what the acceptance contract wants. Its sha256
 * is a mandatory field of the freeze record.</p>
 */
public final class SourceSpanOracle {

    /** The disposition field a negative case is scored against. */
    public enum Field {
        /** The premise's status — the classes UNSUPPORTED, REFUTED, AMBIGUOUS, CONTRADICTORY. */
        PREMISE,
        /** The answer's epistemic disposition. */
        EPISTEMIC,
        /** The action's disposition — the "the action was unavailable" class. */
        ACTION
    }

    /** What the oracle decided about one case. */
    public enum Outcome {
        /** A positive case whose answer cites the gold, from the gold's own claim. */
        GROUNDED,
        /** A positive case whose answer does not cite the gold. */
        UNGROUNDED,
        /** A negative case whose disposition and provenance are the expected ones. */
        DISPOSITION_MATCHED,
        /** A negative case whose disposition or provenance is not the expected one. */
        DISPOSITION_MISMATCHED,
        /** The corpus file's bytes are not the bytes the manifest froze. */
        CORPUS_CHANGED,
        /** The gold proposition does not occur verbatim in the corpus file. */
        GOLD_NOT_IN_CORPUS
    }

    /**
     * One case's gold.
     *
     * @param caseId             the case's id
     * @param corpusFile         the corpus file, relative to the corpus root
     * @param corpusSha256       the sha256 the manifest froze for that file
     * @param goldPropositions   the gold propositions, verbatim
     * @param goldClaimIds       the production claim ids the corpus's source introduced
     * @param positive           whether the case expects an answer (true) or a refusal (false)
     * @param expectedField      the disposition field a negative case is scored against
     * @param expectedDisposition the expected disposition, for a negative case
     * @param expectedProvenance the expected refuting source, for a negative case
     */
    public record GoldCase(String caseId,
                           String corpusFile,
                           String corpusSha256,
                           List<String> goldPropositions,
                           List<String> goldClaimIds,
                           boolean positive,
                           Field expectedField,
                           String expectedDisposition,
                           String expectedProvenance) {
        public GoldCase {
            goldPropositions = List.copyOf(goldPropositions == null ? List.of() : goldPropositions);
            goldClaimIds = List.copyOf(goldClaimIds == null ? List.of() : goldClaimIds);
        }
    }

    /**
     * One claim the served answer cited.
     *
     * @param claimId     the cited claim's id
     * @param proposition the cited proposition, as the answer carries it
     * @param status      the claim's status
     * @param sourceId    the source the claim was introduced by
     */
    public record CitedClaim(String claimId, String proposition, String status, String sourceId) {
    }

    /**
     * The served answer, reduced to what the oracle reads.
     *
     * <p>The reduction is deliberate: the oracle takes <b>data</b>, not the solver's types, so
     * that the harness which reads the response and the oracle which scores it are two pieces
     * of code that meet at this record.</p>
     *
     * @param actionDisposition   the action's disposition
     * @param epistemicDisposition the answer's epistemic disposition
     * @param premiseStatus       the premise's status
     * @param cited               the claims the answer cited
     * @param refutingSourceIds   the sources the answer named as refuting
     */
    public record ServedAnswer(String actionDisposition,
                               String epistemicDisposition,
                               String premiseStatus,
                               List<CitedClaim> cited,
                               List<String> refutingSourceIds) {
        public ServedAnswer {
            cited = List.copyOf(cited == null ? List.of() : cited);
            refutingSourceIds = List.copyOf(refutingSourceIds == null ? List.of() : refutingSourceIds);
        }
    }

    /**
     * What the oracle decided about one case.
     *
     * @param caseId   the case's id
     * @param outcome  what it decided
     * @param grounded whether the case counts as a correct answer
     * @param reason   why, in words
     */
    public record Verdict(String caseId, Outcome outcome, boolean grounded, String reason) {
    }

    /** The corpus the oracle reads. */
    private final Path corpusRoot;

    /**
     * The production claim ids of the workspace UNDER TEST, by proposition.
     *
     * <p><b>Why this exists — a defect the oracle would otherwise carry.</b> A production
     * claim id is derived from the WORKSPACE as well as from the content
     * ({@code DocumentImporter.claimId}: {@code "clm-" + digest(workspaceId + "|" +
     * contentHash + "|" + proposition)}). The CRJ-01 protocol requires that "each arm runs
     * against its <b>own</b> workspace id", and the arms' workspaces are therefore three
     * different workspaces — and the SAME document imported into three workspaces yields
     * THREE DIFFERENT claim ids.</p>
     *
     * <p>So a set that froze a gold claim id would be groundable by at most ONE of the
     * three arms, and the other two would read as ungrounded for a reason that has nothing
     * to do with the arms at all. The fix is not to weaken the corpus requirement but to
     * resolve the gold in the workspace under test: the gold is the SOURCE's claim
     * <b>in the workspace the answer came from</b>, and the oracle checks that the cited
     * claim is that claim — by its proposition, byte-identical, and by its id in that
     * workspace.</p>
     *
     * <p>When the map is empty the oracle falls back to the ids the case recorded, which is
     * the right behaviour when the workspace under test IS the workspace the set was built
     * in.</p>
     */
    private final Map<String, String> claimIdsByProposition;

    /**
     * The oracle over a corpus directory.
     *
     * @param corpusRoot the directory the corpus files live in
     */
    public SourceSpanOracle(Path corpusRoot) {
        this(corpusRoot, Map.of());
    }

    /**
     * The oracle over a corpus directory, resolving the gold's claim ids in the workspace
     * under test.
     *
     * @param corpusRoot         the directory the corpus files live in
     * @param claimIdsByProposition the production claim ids of the workspace under test, by
     *                              proposition
     */
    public SourceSpanOracle(Path corpusRoot, Map<String, String> claimIdsByProposition) {
        this.corpusRoot = corpusRoot;
        this.claimIdsByProposition = Map.copyOf(
                claimIdsByProposition == null ? Map.of() : claimIdsByProposition);
    }

    /**
     * Scores one case.
     *
     * @param gold   the case's gold
     * @param served the served answer
     * @return the verdict
     */
    public Verdict score(GoldCase gold, ServedAnswer served) {
        byte[] corpus;
        try {
            corpus = Files.readAllBytes(corpusRoot.resolve(gold.corpusFile()));
        } catch (IOException unreadable) {
            return new Verdict(gold.caseId(), Outcome.CORPUS_CHANGED, false,
                    "the corpus file " + gold.corpusFile() + " could not be read: "
                            + unreadable.getMessage());
        }
        String digest = sha256(corpus);
        if (!digest.equals(gold.corpusSha256())) {
            return new Verdict(gold.caseId(), Outcome.CORPUS_CHANGED, false,
                    "the corpus file " + gold.corpusFile() + " has the digest " + digest
                            + " and the manifest froze " + gold.corpusSha256());
        }
        for (String goldProposition : gold.goldPropositions()) {
            if (!containsVerbatim(corpus, goldProposition)) {
                // LOUD: a gold that is not in the corpus is a broken case, and it must not be
                // scored as an ungrounded answer — that would hide a broken dataset behind a
                // solver's failure.
                return new Verdict(gold.caseId(), Outcome.GOLD_NOT_IN_CORPUS, false,
                        "the gold proposition does not occur verbatim in " + gold.corpusFile()
                                + ": " + goldProposition);
            }
        }
        if (!gold.positive()) {
            String actual = switch (gold.expectedField()) {
                case PREMISE -> served.premiseStatus();
                case EPISTEMIC -> served.epistemicDisposition();
                case ACTION -> served.actionDisposition();
            };
            if (!gold.expectedDisposition().equals(actual)) {
                return new Verdict(gold.caseId(), Outcome.DISPOSITION_MISMATCHED, false,
                        "the expected disposition was " + gold.expectedDisposition() + " and the "
                                + "answer carries " + actual);
            }
            boolean provenanceOk = gold.expectedProvenance() == null
                    || gold.expectedProvenance().isBlank()
                    || served.refutingSourceIds().contains(gold.expectedProvenance());
            if (!provenanceOk) {
                return new Verdict(gold.caseId(), Outcome.DISPOSITION_MISMATCHED, false,
                        "the expected provenance was " + gold.expectedProvenance() + " and the "
                                + "answer names " + served.refutingSourceIds());
            }
            return new Verdict(gold.caseId(), Outcome.DISPOSITION_MATCHED, true,
                    "the disposition and the provenance are the expected ones");
        }
        for (CitedClaim cited : served.cited()) {
            if (cited.proposition() == null) {
                continue;
            }
            for (String goldProposition : gold.goldPropositions()) {
                if (!byteIdentical(goldProposition, cited.proposition())) {
                    continue;
                }
                if (!isTheProductionClaimOf(goldProposition, cited.claimId(), gold)) {
                    continue;
                }
                return new Verdict(gold.caseId(), Outcome.GROUNDED, true,
                        "the answer cites the gold proposition from the claim "
                                + cited.claimId());
            }
        }
        return new Verdict(gold.caseId(), Outcome.UNGROUNDED, false,
                "the answer cites " + served.cited().size() + " claim(s), none of which is the "
                        + "gold proposition of this case's own claim");
    }

    /**
     * Whether the cited claim IS the production claim of the gold proposition.
     *
     * <p>Two ways to know, and the first is preferred:</p>
     * <ul>
     *   <li>the workspace under test's own production ids — the authoritative answer, and
     *       the one that survives the arms' workspace isolation;</li>
     *   <li>the ids the case recorded — correct only when the workspace under test is the
     *       workspace the set was built in.</li>
     * </ul>
     *
     * @param goldProposition the gold proposition
     * @param citedClaimId    the id the answer cited
     * @param gold            the case's gold
     * @return true when the cited claim is the production claim of that proposition
     */
    private boolean isTheProductionClaimOf(String goldProposition, String citedClaimId,
                                            GoldCase gold) {
        String productionId = claimIdsByProposition.get(goldProposition);
        if (productionId != null) {
            return productionId.equals(citedClaimId);
        }
        return gold.goldClaimIds().contains(citedClaimId);
    }

    /**
     * The sha256 of a corpus file's bytes.
     *
     * @param corpusFile the file, relative to the corpus root
     * @return the digest, in lower-case hex
     */
    public String sha256Of(String corpusFile) {
        try {
            return sha256(Files.readAllBytes(corpusRoot.resolve(corpusFile)));
        } catch (IOException unreadable) {
            throw new UncheckedIOException("the corpus file " + corpusFile + " is unreadable",
                    unreadable);
        }
    }

    /** Whether the bytes contain the text verbatim, as a byte sequence. */
    private static boolean containsVerbatim(byte[] corpus, String text) {
        byte[] needle = text.getBytes(StandardCharsets.UTF_8);
        if (needle.length == 0) {
            return false;
        }
        outer:
        for (int start = 0; start + needle.length <= corpus.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (corpus[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Whether two texts are the same bytes.
     *
     * <p>The comparison is over the BYTES, and it is a CONTENT comparison: an array's
     * {@code equals} is reference equality, and a reference comparison would call two
     * identical propositions different — and score a correct answer as ungrounded.</p>
     */
    private static boolean byteIdentical(String left, String right) {
        return java.util.Arrays.equals(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    /** The sha256 of some bytes, in lower-case hex. */
    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** The list of a case's gold propositions, for the harness's convenience. */
    public List<String> goldPropositionsOf(GoldCase gold) {
        return new ArrayList<>(gold.goldPropositions());
    }
}
