package io.matrix.knowledge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W47 — what a replacement of a workspace would erase.
 *
 * <p><b>Why a preview exists.</b> The consent a caller gives — {@code ?replace=true} or
 * {@code replaceExisting: true} — is a statement that the caller means to destroy what the
 * workspace holds. A statement like that is only meaningful if the caller can see what it
 * is about to destroy, and the caller can only see it if the server can say it. This record
 * is that answer, and it is computed by the same declaration the deletion uses, so the two
 * cannot disagree about what is at stake.</p>
 *
 * <p><b>What the preview promises.</b> It changes nothing. It writes no row, no snapshot,
 * no revision and no counter; the only thing it reads is the content. A caller that asks
 * what a replacement would erase and then does not replace has changed nothing at all.</p>
 *
 * <p><b>What the counts are.</b> One number per table, in the order the tables are erased,
 * so that a reader can see the order as well as the size. The total is the number of rows
 * the replacement would remove.</p>
 *
 * @param workspaceId  the workspace the preview is about
 * @param wouldReplace whether the workspace holds anything at all: a replacement of an
 *                     empty workspace is not a replacement, and it needs no consent
 * @param counts       how many rows each erased table holds
 * @param caseIds      the identifiers of the epistemic cases that would be erased, so that
 *                     the caller can see WHICH cases are at stake and not only how many
 * @param totalRecords how many rows the replacement would remove
 */
public record ReplacementPreview(
        String workspaceId,
        boolean wouldReplace,
        Map<String, Integer> counts,
        List<String> caseIds,
        int totalRecords) {

    public ReplacementPreview {
        counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        caseIds = List.copyOf(caseIds);
    }
}
