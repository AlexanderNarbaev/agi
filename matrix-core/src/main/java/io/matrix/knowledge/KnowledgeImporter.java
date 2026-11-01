package io.matrix.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a knowledge package into a {@link KnowledgeSnapshot} (owner directive
 * §18, §32).
 *
 * <p>The package names things with readable keys; the store identifies them
 * with deterministic identifiers. The importer is the boundary between the
 * two: it resolves every key to an identifier, checks that every reference
 * resolves, and refuses a package that points at something that is not there.
 *
 * <p>A package is the unit of curation AND the unit of exchange. It carries
 * its sources with it, so a package imported on another machine still knows
 * where its claims came from.
 */
public final class KnowledgeImporter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String importedAt = Instant.now().toString();

    /** Import a package from a file. */
    public KnowledgeSnapshot importPack(Path file) {
        try {
            return importPack(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the knowledge package at " + file, e);
        }
    }

    /** Import a package from the classpath (the packs that ship with the app). */
    public KnowledgeSnapshot importResource(String resourcePath) {
        try (InputStream in = KnowledgeImporter.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("No knowledge package on the classpath at " + resourcePath);
            }
            return importPack(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the knowledge package at " + resourcePath, e);
        }
    }

    /** Import a package from JSON text. */
    public KnowledgeSnapshot importPack(String json) {
        KnowledgePack pack;
        try {
            pack = JSON.readValue(json, KnowledgePack.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("The knowledge package is not valid JSON: " + e.getMessage(), e);
        }
        if (pack.workspace == null || pack.workspace.id == null || pack.workspace.id.isBlank()) {
            throw new IllegalArgumentException("The knowledge package has no workspace id");
        }
        String workspaceId = pack.workspace.id;

        Map<String, String> conceptIds = new LinkedHashMap<>();
        Map<String, String> sourceIds = new LinkedHashMap<>();
        Map<String, String> claimIds = new LinkedHashMap<>();
        Map<String, String> representationIds = new LinkedHashMap<>();

        List<Concept> concepts = new ArrayList<>();
        for (KnowledgePack.ConceptEntry entry : pack.concepts) {
            requireKey(entry.key, "concept");
            String id = Ids.concept(workspaceId, entry.name);
            conceptIds.put(entry.key, id);
            concepts.add(new Concept(id, workspaceId, entry.name, entry.definition, entry.domain, importedAt));
        }

        List<Source> sources = new ArrayList<>();
        for (KnowledgePack.SourceEntry entry : pack.sources) {
            requireKey(entry.key, "source");
            SourceKind kind = parseKind(entry.kind);
            String id = Ids.source(workspaceId, kind, entry.locator == null ? entry.title : entry.locator);
            sourceIds.put(entry.key, id);
            // The source's provenance: the version of the material that was
            // read, its publication date, and a hash of it. A source whose
            // version cannot be named is a source that cannot be re-read.
            String contentHash = entry.contentHash != null && !entry.contentHash.isBlank()
                    ? entry.contentHash
                    : Ids.digest(entry.excerpt == null ? "" : entry.excerpt);
            sources.add(new Source(id, workspaceId, kind, entry.title, entry.locator,
                    entry.authority, entry.freshness, entry.excerpt, importedAt,
                    contentHash, entry.publishedAt));
        }

        List<Claim> claims = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        for (KnowledgePack.ClaimEntry entry : pack.claims) {
            requireKey(entry.key, "claim");
            String conceptId = resolve(conceptIds, entry.concept, "concept", entry.key);
            String id = Ids.claim(workspaceId, conceptId, entry.proposition);
            claimIds.put(entry.key, id);
            ClaimStatus status = parseStatus(entry.status);
            String introducedBy = null;
            // The source behind the claim's evidence, whatever the relation.
            // It sets the WARRANT: what we know about this claim - that it
            // holds, or that it does not - was established by that kind of
            // source. A claim whose evidence only contradicts it has no
            // source that introduced it, but it still has a warrant.
            String warrantSource = null;
            for (KnowledgePack.EvidenceEntry ev : entry.evidence) {
                String sourceId = resolve(sourceIds, ev.source, "source", entry.key);
                if (warrantSource == null) {
                    warrantSource = sourceId;
                }
                if (introducedBy == null && "SUPPORTS".equalsIgnoreCase(ev.relation)) {
                    introducedBy = sourceId;
                }
                evidence.add(new Evidence(Ids.evidence(workspaceId, id, sourceId), workspaceId, id, sourceId,
                        parseRelation(ev.relation), ev.excerpt, ev.quality, importedAt));
            }
            // How the claim was extracted and how it was validated. The
            // defaults describe exactly what the pack format can guarantee:
            // a proposition transcribed from a source excerpt and checked
            // against it. A pack that claims more must say so.
            String extraction = entry.extractionMethod != null && !entry.extractionMethod.isBlank()
                    ? entry.extractionMethod
                    : "transcribed from the cited source excerpt";
            String validation = entry.validationMethod != null && !entry.validationMethod.isBlank()
                    ? entry.validationMethod
                    : (evidence.isEmpty()
                            ? "none: no evidence link"
                            : "checked against the cited excerpt");
            // The warrant follows from the kind of the source behind the
            // claim: an observation established it by perception, a
            // publication by testimony. A claim with no source behind it
            // has no warrant.
            ClaimWarrant warrant = ClaimWarrant.NONE;
            if (warrantSource != null) {
                final String sourceId = warrantSource;
                SourceKind kind = pack.sources.stream()
                        .filter(candidate -> sourceId.equals(sourceIds.get(candidate.key)))
                        .map(candidate -> parseKind(candidate.kind))
                        .findFirst()
                        .orElse(null);
                warrant = KnowledgeStore.warrantOfKind(kind == null ? null : kind.name());
            }
            claims.add(new Claim(id, workspaceId, conceptId, entry.proposition, entry.scope, entry.context,
                    status, entry.confidence, 1, introducedBy, extraction, validation,
                    warrant, DerivationStatus.NOT_DERIVED,
                    importedAt, importedAt));
        }

        List<Representation> representations = new ArrayList<>();
        for (KnowledgePack.RepresentationEntry entry : pack.representations) {
            requireKey(entry.key, "representation");
            String conceptId = resolve(conceptIds, entry.concept, "concept", entry.key);
            AbstractionLevel level = parseLevel(entry.level);
            String id = Ids.representation(workspaceId, conceptId, level, entry.statement);
            representationIds.put(entry.key, id);
            List<String> prerequisites = new ArrayList<>();
            for (String prerequisite : entry.prerequisites) {
                prerequisites.add(resolve(conceptIds, prerequisite, "concept", entry.key));
            }
            String refines = entry.refines == null ? null
                    : resolve(representationIds, entry.refines, "representation", entry.key);
            representations.add(new Representation(id, workspaceId, conceptId, level, entry.audience,
                    entry.statement, entry.scope, entry.assumptions, entry.includedFacts, entry.omitted,
                    entry.validityConditions, prerequisites, refines, entry.confidence, importedAt));
        }

        List<Prerequisite> prerequisites = new ArrayList<>();
        for (KnowledgePack.PrerequisiteEntry entry : pack.prerequisites) {
            String conceptId = resolve(conceptIds, entry.concept, "concept", "prerequisite");
            String requiresId = resolve(conceptIds, entry.requires, "concept", "prerequisite");
            prerequisites.add(new Prerequisite(workspaceId, conceptId, requiresId, entry.required, entry.note));
        }

        List<KnowledgeRevision> revisions = new ArrayList<>();
        for (KnowledgePack.RevisionEntry entry : pack.revisions) {
            String targetId = resolve(conceptIds, entry.target, "concept", "revision");
            List<String> evidenceIds = new ArrayList<>();
            for (String claimKey : entry.evidence) {
                evidenceIds.add(resolve(claimIds, claimKey, "claim", "revision"));
            }
            revisions.add(new KnowledgeRevision(
                    Ids.revision(workspaceId, targetId, parseRevisionKind(entry.kind), entry.summary),
                    workspaceId, "concept", targetId, parseRevisionKind(entry.kind), entry.summary,
                    entry.addedFacts, entry.stillValid, entry.noLongerValid, entry.newlyExplained,
                    evidenceIds, importedAt));
        }

        return new KnowledgeSnapshot(workspaceId,
                pack.workspace.name == null ? workspaceId : pack.workspace.name,
                concepts, sources, claims, evidence, representations, prerequisites, revisions, importedAt);
    }

    // ── resolution ────────────────────────────────────────────────────────────

    private static void requireKey(String key, String what) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A " + what + " in the package has no key");
        }
    }

    private static String resolve(Map<String, String> keys, String key, String what, String owner) {
        if (key == null) {
            throw new IllegalArgumentException("A reference to a " + what + " is missing in " + owner);
        }
        String resolved = keys.get(key);
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "The package references an unknown " + what + " '" + key + "' from " + owner);
        }
        return resolved;
    }

    private static SourceKind parseKind(String raw) {
        try {
            return SourceKind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown source kind: " + raw, e);
        }
    }

    private static ClaimStatus parseStatus(String raw) {
        try {
            return ClaimStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown claim status: " + raw, e);
        }
    }

    private static EvidenceRelation parseRelation(String raw) {
        try {
            return EvidenceRelation.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown evidence relation: " + raw, e);
        }
    }

    private static AbstractionLevel parseLevel(String raw) {
        try {
            return AbstractionLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown abstraction level: " + raw, e);
        }
    }

    private static RevisionKind parseRevisionKind(String raw) {
        try {
            return RevisionKind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown revision kind: " + raw, e);
        }
    }

    /** The index of the packs that ship with the application. */
    public static final Map<String, String> BUNDLED_PACKS = Map.of(
            "photosynthesis", "knowledge/photosynthesis.pack.json");
}
