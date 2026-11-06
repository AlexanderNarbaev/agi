---------------------------- MODULE PromotionStage ---------------------------
\* RECON-W42.9 / X.2 — the formal specification of the promotion machine.
\*
\* Subject: io.matrix.knowledge.acquisition.PromotionStage
\*          io.matrix.knowledge.acquisition.PromotionPolicy
\*          (matrix-core/src/main/java/io/matrix/knowledge/acquisition/)
\*
\* WHAT IS MODELLED
\*   The eight promotion layers of an acquired claim and the two rules that
\*   guard them:
\*
\*     - PromotionStage.successors(): the layers a claim may move to. RAW and
\*       EXTRACTED have exactly one successor each; SUPERSEDED and INVALIDATED
\*       are terminal.
\*
\*     - PromotionPolicy.mayPromote(from, to, hasEvidence, independentSources,
\*       disputed): the requirements of the TARGET layer. SUPPORTED needs
\*       evidence from at least one source; VERIFIED needs evidence from a
\*       second, INDEPENDENT source; DISPUTED needs the sources to disagree;
\*       EXTRACTED, CANDIDATE, SUPERSEDED and INVALIDATED need no evidence.
\*
\*   A move is legal when the layer graph allows it AND the policy's
\*   requirements for the target are met. The two are separate checks in the
\*   Java, and the model keeps them separate.
\*
\* THE RULE THIS SPECIFICATION EXISTS TO HOLD
\*   The mission's §5.4: "Ten documents copying the same source do not
\*   constitute ten independent confirmations." The model counts DISTINCT
\*   source hashes, exactly as PromotionPolicy.independentSources does, so a
\*   claim cannot reach VERIFIED on one source copied many times. The
\*   property VerifiedNeedsTwoIndependentSources is what holds that line, and
\*   it is falsifiable: a model that allowed VERIFIED on a single source
\*   would violate it, and TLC was run on exactly that mutation and rejected
\*   it (see the WAL of 2026-11-02).
\*
\* WHAT THIS SPECIFICATION IS NOT
\*   A proof that the Java is correct. It is a model of the rules, and it
\*   states the rules in a form that can fail. It does not model the
\*   extraction, the segmentation or the evidence itself.

EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS
    HASHES             \* the finite set of source hashes the model knows

VARIABLES
    stage,             \* the current promotion layer
    sources,           \* the set of DISTINCT source hashes behind the claim
    disputed           \* whether the sources disagree about the claim

vars == <<stage, sources, disputed>>

\* The layers the mission's §5.4 names. The SET, not the order: the order is
\* expressed by the successor relation below, which is what the Java states.
LADDER == {"RAW", "EXTRACTED", "CANDIDATE", "SUPPORTED", "VERIFIED", "DISPUTED", "SUPERSEDED", "INVALIDATED"}

TerminalStages == {"SUPERSEDED", "INVALIDATED"}
TrustedStages == {"SUPPORTED", "VERIFIED"}

\* The layer graph, as PromotionStage.successors() defines it.
Successors(s) ==
    CASE s = "RAW"            -> {"EXTRACTED"}
      [] s = "EXTRACTED"      -> {"CANDIDATE"}
      [] s = "CANDIDATE"      -> {"SUPPORTED", "INVALIDATED"}
      [] s = "SUPPORTED"      -> {"VERIFIED", "DISPUTED", "SUPERSEDED", "INVALIDATED"}
      [] s = "VERIFIED"       -> {"DISPUTED", "SUPERSEDED", "INVALIDATED"}
      [] s = "DISPUTED"       -> {"SUPPORTED", "INVALIDATED", "SUPERSEDED"}
      [] OTHER                -> {}

\* The policy's requirements for the TARGET layer, as
\* PromotionPolicy.mayPromote defines them.
MayPromote(to) ==
    CASE to = "EXTRACTED"  \/ to = "CANDIDATE" -> TRUE
      [] to = "SUPPORTED"                   -> Cardinality(sources) >= 1
      [] to = "VERIFIED"                    -> Cardinality(sources) >= 2
      [] to = "DISPUTED"                    -> disputed
      [] to = "SUPERSEDED" \/ to = "INVALIDATED" -> TRUE
      [] OTHER                               -> FALSE

Init ==
    /\ stage = "RAW"
    /\ sources = {}
    /\ disputed = FALSE

\* Evidence arrives. Copies of the same source do not add a distinct hash,
\* so a document that copies another source cannot manufacture independence.
AddSource ==
    /\ stage \notin TerminalStages
    /\ \E h \in HASHES \ sources : sources' = sources \cup {h}
    /\ stage' = stage
    /\ disputed' = disputed

\* The sources begin to disagree.
SetDisputed ==
    /\ stage \in {"SUPPORTED", "VERIFIED"}
    /\ disputed' = TRUE
    /\ stage' = stage
    /\ sources' = sources

\* The disagreement is resolved.
ClearDisputed ==
    /\ disputed
    /\ disputed' = FALSE
    /\ stage' = stage
    /\ sources' = sources

\* A promotion: the layer graph allows it AND the policy's requirements are
\* met. Both checks are here, because the Java makes both.
Promote ==
    /\ \E to \in Successors(stage) :
         /\ MayPromote(to)
         /\ stage' = to
    /\ sources' = sources
    /\ disputed' = disputed

Next == AddSource \/ SetDisputed \/ ClearDisputed \/ Promote

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ stage \in LADDER
    /\ sources \subseteq HASHES
    /\ disputed \in BOOLEAN

\* §5.4: a second, INDEPENDENT source. Distinct hashes only.
VerifiedNeedsTwoIndependentSources ==
    stage = "VERIFIED" => Cardinality(sources) >= 2

\* A supported claim has something behind it.
SupportedNeedsASource ==
    stage = "SUPPORTED" => Cardinality(sources) >= 1

\* A dispute needs evidence to be a dispute about something.
DisputeNeedsEvidence ==
    stage = "DISPUTED" => Cardinality(sources) >= 1

\* The terminal layers are final: a superseded or invalidated claim does not
\* come back. This is a temporal property, because it is about what happens
\* NEXT, not about the present state.
TerminalIsFinal ==
    [][Next => (stage \in TerminalStages => stage' = stage)]_vars

\* The two layers the Java calls trusted are exactly SUPPORTED and VERIFIED.
TrustedIsExactlyThePair ==
    (stage \in TrustedStages) = (stage = "SUPPORTED" \/ stage = "VERIFIED")

====
