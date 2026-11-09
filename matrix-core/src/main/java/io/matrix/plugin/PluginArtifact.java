package io.matrix.plugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * W51 — the artefact a plugin consists of.
 *
 * <p><b>An artefact is data, and it says so.</b> The artefact carries a manifest, a program
 * and the cases the program is supposed to satisfy, and it carries the digest of those
 * contents. The digest is what ties a validation to a specific artefact: a plugin that was
 * validated against one byte sequence cannot be activated with another, because the
 * {@link io.matrix.contract.Plugin#artifactDigest()} of the activated record is compared
 * against the digest of what is about to run.</p>
 *
 * <p><b>The declared cases are part of the artefact, not of the test suite.</b> A plugin that
 * declares what it should do carries that declaration with it, so the validation can be
 * re-run against the artefact itself rather than against a test that may have drifted away
 * from it.</p>
 *
 * <p><b>The program is text and the text is the whole of the executable surface.</b> The
 * artefact declares which {@link io.matrix.contract.Plugin.Isolation} the program needs, and
 * the sandbox that runs it is chosen by that declaration — not by what the program turns out
 * to contain. A plugin that declares {@code PROCESS} but is handed to the declarative
 * sandbox is refused.</p>
 *
 * @param id           the identity of the artefact
 * @param version      the revision number of the artefact
 * @param program      the program of the artefact
 * @param declaredCases the cases the artefact says it satisfies
 * @param isolation    the isolation the program needs
 */
public record PluginArtifact(
        String id,
        int version,
        String program,
        List<Case> declaredCases,
        io.matrix.contract.Plugin.Isolation isolation) {

    /**
     * One declared case: an input and the output the plugin promises for it.
     *
     * @param name     the name of the case
     * @param input    the input of the case
     * @param expected the output the plugin promises
     */
    public record Case(String name, Map<String, Object> input, Map<String, Object> expected) {
        public Case {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("case name must not be blank");
            }
            input = input == null ? Map.of() : Map.copyOf(input);
            expected = expected == null ? Map.of() : Map.copyOf(expected);
        }
    }

    public PluginArtifact {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("artifact id must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("artifact version must be at least one");
        }
        if (program == null) {
            program = "";
        }
        declaredCases = declaredCases == null ? List.of() : List.copyOf(declaredCases);
        if (isolation == null) {
            throw new IllegalArgumentException("artifact isolation must not be null");
        }
    }

    /**
     * The digest of the artefact's contents.
     *
     * <p>The digest covers the identity, the version, the isolation and the program, so that
     * a change to any of them changes the digest. It does NOT cover the declared cases: the
     * cases are what the artefact is checked against, and a change to them is a change to
     * what was promised rather than to what was built — the validation records the cases it
     * ran for that reason.</p>
     *
     * @return the digest, as {@code sha256:<hex>}
     */
    public String digest() {
        StringBuilder material = new StringBuilder();
        material.append(id).append('\n').append(version).append('\n')
                .append(isolation.name()).append('\n').append(program);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(material.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }

    @Override
    public String toString() {
        return "PluginArtifact[" + id + " v" + version + " " + isolation + "]";
    }
}
