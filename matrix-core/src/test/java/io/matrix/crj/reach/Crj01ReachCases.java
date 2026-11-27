package io.matrix.crj.reach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.testsupport.RepositoryPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRJ-01 / COMPETENCE-REACH — the DEV case set, as the IMPLEMENTER wrote it.
 *
 * <p><b>What this reads, and who wrote it.</b> {@code
 * docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json}: the case set, its slices, its provenance
 * and the corpus digests the author recorded. The file is <b>read</b> here and never written.
 * <b>The IMPLEMENTER wrote both the cases and the measurement</b>: two separately commissioned
 * DSH case-author subagents were given the corpus and the oracle contract, and neither
 * delivered a case set. The oracle is therefore <b>not independent</b> of the mechanism it
 * measures, and the milestone says so wherever it states the roles. What the case set still
 * buys is that its contract is explicit — the necessary claims are sentences of the corpus
 * bytes, the allowed dispositions are named, and the slices do not share a gold — and that
 * every one of those properties is asserted by the suite rather than promised.</p>
 *
 * <p><b>What a case carries.</b> The <b>minimal set of claims a correct answer must cite</b>
 * (the oracle's necessary evidence), the claims that may also be cited without making the
 * answer wrong, the dispositions that count as correct, whether the workspace holds the evidence
 * the question needs, and the corroboration / contradiction conditions. None of these is ever
 * read by an arm — they are read only to score what an arm did.</p>
 *
 * <p><b>Why the gold is named by PROPOSITION and not by claim id.</b> A production claim id is
 * derived from the workspace as well as from the content, so the same sentence imported into two
 * workspaces yields two different ids — and the grid's conditions build more than one view of
 * the same corpus. The proposition is the stable name, and the claim id is resolved in the
 * workspace the answer came from.</p>
 */
public final class Crj01ReachCases {

    /** The case set — the IMPLEMENTER's own. */
    public static final String CASES = "docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json";

    /**
     * One piece of evidence: a sentence of the corpus, and the file it came from.
     *
     * @param corpusFile the file the sentence is in
     * @param proposition the sentence, byte for byte
     */
    public record Evidence(String corpusFile, String proposition) {
    }

    /**
     * One case of the case set.
     *
     * @param id                 the case's id
     * @param domain             the domain the case belongs to
     * @param slice              the slice the case belongs to
     * @param kind               the kind the case is
     * @param level              the abstraction level the case is asked at
     * @param question           the question
     * @param answerable         whether the workspace holds the evidence the question needs
     * @param necessaryEvidence  the minimal set of claims a correct answer must cite
     * @param allowedEvidence    the claims that may also be cited without making the answer wrong
     * @param allowedDispositions the dispositions that count as correct
     * @param corroboration      the corroboration condition
     * @param contradiction      the contradiction condition
     * @param note               why the case exists
     */
    public record Case(String id, String domain, String slice, String kind, String level,
            String question, boolean answerable, List<Evidence> necessaryEvidence,
            List<Evidence> allowedEvidence, List<String> allowedDispositions,
            String corroboration, String contradiction, String note) {

        public Case {
            necessaryEvidence = List.copyOf(
                    necessaryEvidence == null ? List.of() : necessaryEvidence);
            allowedEvidence = List.copyOf(allowedEvidence == null ? List.of() : allowedEvidence);
            allowedDispositions = List.copyOf(
                    allowedDispositions == null ? List.of() : allowedDispositions);
        }
    }

    /**
     * One domain of the case set.
     *
     * @param id           the domain's id
     * @param subject      the subject the domain is about
     * @param files        the corpus files of the domain
     * @param declaredScope the files a competence about the domain declares it is about
     */
    public record Domain(String id, String subject, List<String> files, List<String> declaredScope) {

        public Domain {
            files = List.copyOf(files == null ? List.of() : files);
            declaredScope = List.copyOf(declaredScope == null ? List.of() : declaredScope);
        }
    }

    /**
     * The whole case set.
     *
     * @param cases    the cases
     * @param domains  the domains
     * @param provenance the author's own record of how the case set was made
     */
    public record CaseSet(List<Case> cases, List<Domain> domains, Map<String, Object> provenance) {

        public CaseSet {
            cases = List.copyOf(cases == null ? List.of() : cases);
            domains = List.copyOf(domains == null ? List.of() : domains);
            provenance = Map.copyOf(provenance == null ? Map.of() : provenance);
        }

        /**
         * The cases of one domain and one slice.
         *
         * @param domain the domain
         * @param slice  the slice
         * @return the cases, in the order the case set names them
         */
        public List<Case> slice(String domain, String slice) {
            List<Case> selected = new ArrayList<>();
            for (Case testCase : cases) {
                if (testCase.domain().equals(domain) && testCase.slice().equals(slice)) {
                    selected.add(testCase);
                }
            }
            return selected;
        }

        /**
         * The domain of a given id.
         *
         * @param id the domain's id
         * @return the domain, or null when the case set does not carry it
         */
        public Domain domain(String id) {
            for (Domain domain : domains) {
                if (domain.id().equals(id)) {
                    return domain;
                }
            }
            return null;
        }
    }

    private Crj01ReachCases() {
    }

    /**
     * Reads the case set.
     *
     * <p><b>The environment override.</b> {@code CRJ01_REACH_CASES} names a different case set.
     * It exists so that the machinery can be exercised against a THROWAWAY case set without
     * touching the author's file: the measurement's own artefacts must never be produced from a
     * case set that is not the author's, and a smoke test needs a case set of its own.</p>
     *
     * @return the case set
     * @throws IOException when the case set cannot be read
     */
    public static CaseSet read() throws IOException {
        String override = System.getenv("CRJ01_REACH_CASES");
        Path path = override == null || override.isBlank()
                ? RepositoryPaths.root().resolve(CASES)
                : Path.of(override);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("the DEV case set " + CASES + " is not present");
        }
        ObjectMapper json = JsonMapper.builder().build();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = json.readValue(Files.readString(path, StandardCharsets.UTF_8),
                Map.class);
        List<Case> cases = new ArrayList<>();
        for (Object entry : (List<?>) parsed.get("cases")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            cases.add(new Case(
                    text(map.get("id")),
                    text(map.get("domain")),
                    text(map.get("slice")),
                    text(map.get("kind")),
                    text(map.get("level")),
                    text(map.get("question")),
                    !Boolean.FALSE.equals(map.get("answerable")),
                    evidence(map.get("necessaryEvidence")),
                    evidence(map.get("allowedEvidence")),
                    strings(map.get("allowedDispositions")),
                    text(map.get("corroboration")),
                    text(map.get("contradiction")),
                    text(map.get("note"))));
        }
        List<Domain> domains = new ArrayList<>();
        for (Object entry : (List<?>) parsed.get("domains")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            domains.add(new Domain(
                    text(map.get("id")),
                    text(map.get("subject")),
                    strings(map.get("files")),
                    strings(map.get("declaredScope"))));
        }
        Map<String, Object> provenance = new LinkedHashMap<>();
        Object recorded = parsed.get("provenance");
        if (recorded instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                provenance.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return new CaseSet(cases, domains, provenance);
    }

    /**
     * The evidence list of a case.
     *
     * @param raw the raw list
     * @return the evidence
     */
    private static List<Evidence> evidence(Object raw) {
        List<Evidence> evidence = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return evidence;
        }
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                evidence.add(new Evidence(text(map.get("corpusFile")),
                        text(map.get("proposition"))));
            }
        }
        return evidence;
    }

    /**
     * A list of strings.
     *
     * @param raw the raw list
     * @return the strings
     */
    private static List<String> strings(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                values.add(text(entry));
            }
        }
        return values;
    }

    /**
     * A field, as a string.
     *
     * @param raw the raw value
     * @return the string, or null when the value is absent
     */
    private static String text(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }
}
