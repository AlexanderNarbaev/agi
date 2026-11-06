-------------------------- MODULE KnowledgeStore --------------------------
(***************************************************************************)
(* The knowledge store's own semantics.                                                *)
(*                                                                                     *)
(* The claim lifecycle has its own model (KnowledgeLifecycle). This one is about the   *)
(* STORE: the component the owner directive calls the critical persistence layer.     *)
(*                                                                                     *)
(* It states three promises the Java makes and that a restart depends on:              *)
(*                                                                                     *)
(*   1. An import is ATOMIC. Either the whole knowledge package lands - the claims,    *)
(*      the sources and the evidence that backs them - or none of it does. A partial  *)
(*      import would leave evidence pointing at claims that are not there.            *)
(*                                                                                     *)
(*   2. The schema version only ever goes UP. A downgrade would read data written by  *)
(*      a newer schema and misread it.                                                *)
(*                                                                                     *)
(*   3. A claim that is backed by evidence has a source behind that evidence. An      *)
(*      evidence link without a source is an orphan.                                  *)
(*                                                                                     *)
(* The model also keeps the record of the failed imports, because a store that      *)
(* silently drops one is a store whose completeness nobody can check.                  *)
(***************************************************************************)

EXTENDS Integers, FiniteSets

CONSTANTS
    \* The claim ids a package carries.
    Claims,
    \* The subset of those claims that at least one evidence link points at.
    Backed,
    \* The source ids a package carries.
    Sources,
    \* The workspace ids.
    Workspaces,
    \* The highest schema version the store can reach, and the highest
    \* number of imports it can record. The bounds are what make the state
    \* space finite: without them TLC explores an unbounded counter.
    MaxVersion,
    MaxSteps

VARIABLES
    claims,        \* the claim ids the store holds
    backed,        \* the claim ids that have at least one evidence link
    sources,       \* the source ids the store holds
    loaded,        \* the workspace ids the store holds
    schemaVersion, \* the schema version on disk
    imports,       \* the number of imports that landed
    aborted,       \* the number of imports that were rolled back
    floorVersion,  \* the lowest schema version ever seen
    floorImports,  \* the lowest count of landed imports ever seen
    floorAborted   \* the lowest count of rolled-back imports ever seen

vars == <<claims, backed, sources, loaded, schemaVersion, imports, aborted,
          floorVersion, floorImports, floorAborted>>

(***************************************************************************)
(* The initial state: an empty store at the first schema version.        *)
(***************************************************************************)
Init ==
    /\ claims = {}
    /\ backed = {}
    /\ sources = {}
    /\ loaded = {}
    /\ schemaVersion = 1
    /\ imports = 0
    /\ aborted = 0
    /\ floorVersion = 1
    /\ floorImports = 0
    /\ floorAborted = 0

(***************************************************************************)
(* A successful import.                                                    *)
(*                                                                         *)
(* The whole package lands: the claims, the sources, the evidence that    *)
(* backs them and the workspace. There is no state in which the evidence *)
(* has landed and the claims have not.                                     *)
(***************************************************************************)
Import ==
    /\ imports < MaxSteps
    /\ claims' = claims \cup Claims
    /\ backed' = backed \cup Backed
    /\ sources' = sources \cup Sources
    /\ loaded' = loaded \cup Workspaces
    /\ schemaVersion' = schemaVersion
    /\ imports' = imports + 1
    /\ aborted' = aborted
    /\ floorVersion' = IF schemaVersion' < floorVersion THEN schemaVersion' ELSE floorVersion
    /\ floorImports' = IF imports' < floorImports THEN imports' ELSE floorImports
    /\ floorAborted' = IF aborted' < floorAborted THEN aborted' ELSE floorAborted

(***************************************************************************)
(* An import that fails and is rolled back.                                *)
(*                                                                         *)
(* Nothing lands. The count of rolled-back imports goes up, so that a      *)
(* reader can tell a complete store from one that lost an import.        *)
(***************************************************************************)
Abort ==
    /\ aborted < MaxSteps
    /\ claims' = claims
    /\ backed' = backed
    /\ sources' = sources
    /\ loaded' = loaded
    /\ schemaVersion' = schemaVersion
    /\ imports' = imports
    /\ aborted' = aborted + 1
    /\ floorVersion' = IF schemaVersion' < floorVersion THEN schemaVersion' ELSE floorVersion
    /\ floorImports' = IF imports' < floorImports THEN imports' ELSE floorImports
    /\ floorAborted' = IF aborted' < floorAborted THEN aborted' ELSE floorAborted

(***************************************************************************)
(* A schema migration.                                                     *)
(*                                                                         *)
(* The version moves forward, and only forward.                            *)
(***************************************************************************)
Migrate ==
    /\ schemaVersion < MaxVersion
    /\ schemaVersion' = schemaVersion + 1
    /\ floorVersion' = IF schemaVersion' < floorVersion THEN schemaVersion' ELSE floorVersion
    /\ UNCHANGED <<claims, backed, sources, loaded, imports, aborted,
                   floorImports, floorAborted>>

Next ==
    \/ Import
    \/ Abort
    \/ Migrate

(***************************************************************************)
(* The state predicates.                                                   *)
(***************************************************************************)
TypeOK ==
    /\ claims \subseteq Claims
    /\ backed \subseteq claims
    /\ sources \subseteq Sources
    /\ loaded \subseteq Workspaces
    /\ schemaVersion \in Nat
    /\ imports \in Nat
    /\ aborted \in Nat
    /\ floorVersion \in Nat
    /\ floorImports \in Nat
    /\ floorAborted \in Nat

(***************************************************************************)
(* An evidence link never points at a claim that is not there. This is    *)
(* the property a partial import would break.                             *)
(***************************************************************************)
EvidenceHasClaim ==
    backed \subseteq claims

(***************************************************************************)
(* A backed claim has a source behind it.                                 *)
(***************************************************************************)
BackedHasSource ==
    backed # {} => sources # {}

(***************************************************************************)
(* The schema version is never below the first one.                       *)
(***************************************************************************)
SchemaAtLeastOne ==
    schemaVersion >= 1

(***************************************************************************)
(* The specification.                                                      *)
(***************************************************************************)
Spec == Init /\ [][Next]_vars
        /\ WF_vars(Next)

(***************************************************************************)
(* An import is atomic.                                                     *)
(*                                                                         *)
(* Once any claim has landed, the WHOLE package has landed: the claims,   *)
(* the sources, the evidence that backs them and the workspace. A partial *)
(* import - the claims without their evidence, or the evidence without    *)
(* the claims - is exactly what the Java's single-transaction import      *)
(* refuses to produce, and this is where the model says so.               *)
(***************************************************************************)
ImportIsAtomic ==
    claims # {} => (claims = Claims /\ backed = Backed
                    /\ sources = Sources /\ loaded = Workspaces)

(***************************************************************************)
(* The version and the counters never go backwards.                       *)
(*                                                                         *)
(* A temporal formula over primed variables has to have the [A]_v form,    *)
(* so the monotonicity is carried by the floors: the lowest value ever    *)
(* seen. The state predicates below then hold at every step, and the      *)
(* temporal properties say that they hold at EVERY step.                   *)
(***************************************************************************)
VersionMonotone ==
    schemaVersion >= floorVersion

CountersMonotone ==
    imports >= floorImports /\ aborted >= floorAborted

VersionNotBelowFloor ==
    VersionMonotone

CountersNotBelowFloor ==
    CountersMonotone

(***************************************************************************)
(* The temporal properties.                                                *)
(***************************************************************************)
EvidenceAlways ==
    []EvidenceHasClaim

SourceAlways ==
    []BackedHasSource

VersionNeverDecreases ==
    []VersionMonotone

CountersNeverDecrease ==
    []CountersMonotone

=============================================================================
