package io.matrix.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The authoring format for a knowledge package (owner directive §18, §32).
 *
 * <p>A package is what a human or a curator writes: concepts, sources, claims,
 * representations, prerequisites and revisions, with the cross-references
 * expressed as READABLE KEYS rather than opaque identifiers. The importer
 * resolves the keys into the deterministic identifiers of the store, so the
 * package stays authorable and the store stays stable.
 *
 * <p>The package format is also the exchange format: {@code exportWorkspace}
 * produces the store's own aggregate, and a curator can hand-edit a package
 * and import it back.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class KnowledgePack {

    @JsonProperty("workspace")
    public Workspace workspace;

    /** Free-text notes about the package: its curation policy, its provenance, its gaps. */
    @JsonProperty("notes")
    public List<String> notes = List.of();

    @JsonProperty("sources")
    public List<SourceEntry> sources = List.of();

    @JsonProperty("concepts")
    public List<ConceptEntry> concepts = List.of();

    @JsonProperty("claims")
    public List<ClaimEntry> claims = List.of();

    @JsonProperty("representations")
    public List<RepresentationEntry> representations = List.of();

    @JsonProperty("prerequisites")
    public List<PrerequisiteEntry> prerequisites = List.of();

    @JsonProperty("revisions")
    public List<RevisionEntry> revisions = List.of();

    /** The workspace the package populates. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Workspace {
        @JsonProperty("id")
        public String id;
        @JsonProperty("name")
        public String name;
    }

    /** A source: a document, observation, experiment or human statement. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SourceEntry {
        @JsonProperty("key")
        public String key;
        @JsonProperty("kind")
        public String kind;
        @JsonProperty("title")
        public String title;
        @JsonProperty("locator")
        public String locator;
        @JsonProperty("authority")
        public int authority = 50;
        @JsonProperty("freshness")
        public int freshness = 50;
        @JsonProperty("excerpt")
        public String excerpt;
    }

    /** A concept: a stable identity for an idea. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ConceptEntry {
        @JsonProperty("key")
        public String key;
        @JsonProperty("name")
        public String name;
        @JsonProperty("definition")
        public String definition;
        @JsonProperty("domain")
        public String domain;
    }

    /** A claim: a proposition about a concept, with its scope and status. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ClaimEntry {
        @JsonProperty("key")
        public String key;
        @JsonProperty("concept")
        public String concept;
        @JsonProperty("proposition")
        public String proposition;
        @JsonProperty("scope")
        public String scope;
        @JsonProperty("context")
        public String context;
        @JsonProperty("status")
        public String status = "DRAFT";
        @JsonProperty("confidence")
        public double confidence = 0.5;
        @JsonProperty("evidence")
        public List<EvidenceEntry> evidence = List.of();
    }

    /** Evidence: what a source says about a claim. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class EvidenceEntry {
        @JsonProperty("source")
        public String source;
        @JsonProperty("relation")
        public String relation = "SUPPORTS";
        @JsonProperty("excerpt")
        public String excerpt;
        @JsonProperty("quality")
        public int quality = 50;
    }

    /** A representation: an account at one abstraction level. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RepresentationEntry {
        @JsonProperty("key")
        public String key;
        @JsonProperty("concept")
        public String concept;
        @JsonProperty("level")
        public String level;
        @JsonProperty("audience")
        public String audience;
        @JsonProperty("statement")
        public String statement;
        @JsonProperty("scope")
        public String scope;
        @JsonProperty("assumptions")
        public List<String> assumptions = List.of();
        @JsonProperty("includedFacts")
        public List<String> includedFacts = List.of();
        @JsonProperty("omitted")
        public List<String> omitted = List.of();
        @JsonProperty("validityConditions")
        public List<String> validityConditions = List.of();
        @JsonProperty("prerequisites")
        public List<String> prerequisites = List.of();
        @JsonProperty("refines")
        public String refines;
        @JsonProperty("confidence")
        public double confidence = 0.5;
    }

    /** A prerequisite: what must be known before what. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class PrerequisiteEntry {
        @JsonProperty("concept")
        public String concept;
        @JsonProperty("requires")
        public String requires;
        @JsonProperty("required")
        public boolean required = true;
        @JsonProperty("note")
        public String note;
    }

    /** A revision: how a representation changed, and why. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RevisionEntry {
        @JsonProperty("target")
        public String target;
        @JsonProperty("kind")
        public String kind;
        @JsonProperty("summary")
        public String summary;
        @JsonProperty("addedFacts")
        public List<String> addedFacts = List.of();
        @JsonProperty("stillValid")
        public List<String> stillValid = List.of();
        @JsonProperty("noLongerValid")
        public List<String> noLongerValid = List.of();
        @JsonProperty("newlyExplained")
        public List<String> newlyExplained = List.of();
        @JsonProperty("evidence")
        public List<String> evidence = List.of();
    }
}
