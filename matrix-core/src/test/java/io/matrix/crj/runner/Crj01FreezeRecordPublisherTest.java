package io.matrix.crj.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the FREEZE RECORD's PUBLISHER.
 *
 * <p><b>What this does.</b> It writes the freeze record to
 * {@code docs-v2/engineering/CRJ-01-FREEZE-RECORD.json}, so the reviewer has an artefact
 * to publish rather than a list to transcribe. The record is computed from the artefacts —
 * the split, the sources, the manifests, the HEAD — so every field in it is
 * <b>re-derivable</b> by a reader who was not there.</p>
 *
 * <p><b>And the roles are PLACEHOLDERS here, and that is deliberate.</b> The protocol's
 * §5.6 binds the order: the reviewer publishes the freeze record <b>before</b> the first
 * arm run. This publisher writes the record with the roles <b>unnamed</b> — a machine
 * cannot name a person, and a record that names a person it invented would be worse than
 * no record. The <b>sealed</b> freeze therefore has two steps: this publisher writes the
 * <b>digest record</b>, and the reviewer fills the two role fields and publishes it.</p>
 *
 * <p><b>Why the roles cannot be filled by the machine.</b> The protocol requires "the name
 * of the case author, the name of the freezing reviewer, and the statement that they are
 * different people and that neither wrote a solver". Those are facts about <b>people</b>,
 * and the programme is the one that must assert them — which is exactly why the record
 * <b>refuses</b> to be written without them, and why the publisher writes the placeholders
 * rather than a name.</p>
 */
class Crj01FreezeRecordPublisherTest {

    /** The record the publisher writes. */
    private static final String OUT = "docs-v2/engineering/CRJ-01-FREEZE-RECORD.json";

    @Test
    @DisplayName("the freeze record is published, with its digests and its roles unfilled")
    void theFreezeRecordIsPublished() throws Exception {
        // the roles are PLACEHOLDERS: a machine cannot name a person, and the protocol
        // binds the reviewer to publish the record before the first arm run
        Crj01FreezeRecord.Record record = Crj01FreezeRecord.compute(RepositoryPaths.root(),
                "docs-v2/engineering/CRJ-01-DEV-SPLIT.json", 20261121L,
                "UNNAMED — the case author, to be filled by the reviewer",
                "UNNAMED — the freezing reviewer, to be filled by the reviewer");

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("artefact", OUT);
        document.put("protocol", "docs-v2/engineering/CRJ-01-PROTOCOL.md §5.5 (the fields) and "
                + "§5.6 (the freeze procedure)");
        document.put("status", "DIGEST RECORD — the digests are final and re-derivable; the two "
                + "role fields are PLACEHOLDERS and the record is NOT a valid freeze until the "
                + "reviewer fills them and publishes it");
        document.put("fields", record.fields());

        ObjectMapper json = JsonMapper.builder().build();
        Path out = RepositoryPaths.root().resolve(OUT);
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.writerWithDefaultPrettyPrinter().writeValueAsString(document)
                + System.lineSeparator(), StandardCharsets.UTF_8);

        assertThat(out).as("the freeze record").exists();
        @SuppressWarnings("unchecked")
        Map<String, Object> written = json.readValue(Files.readString(out, StandardCharsets.UTF_8),
                Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) written.get("fields");
        assertThat(fields).as("the record's fields").isNotEmpty();
        assertThat(fields).containsKey("dataset.sha256");
        assertThat(fields).containsKey("branchSha");
        assertThat(fields).containsKey("instrumentCrosscheck");
        System.out.println("[CRJ-01] the freeze record: " + OUT);
        System.out.println("[CRJ-01] the dataset's digest: " + fields.get("dataset.sha256"));
        System.out.println("[CRJ-01] the branch SHA: " + fields.get("branchSha"));
    }
}
