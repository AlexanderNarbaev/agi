---------------------------- MODULE LearningStateMachine ---------------------------
\* RECON-W42.9 / X.2 — the formal specification of the learning state machine.
\*
\* Subject: io.matrix.knowledge.LearningStateMachine
\*          (matrix-core/src/main/java/io/matrix/knowledge/LearningStateMachine.java)
\*
\* WHAT IS MODELLED
\*   The seven-rung ladder and the two rules the Java enforces:
\*
\*     - legal(from, to): a move UP is legal only one rung at a time (the
\*       stages are evidence thresholds, and a jump means the intermediate
\*       evidence was never gathered); a move DOWN is legal from any stage,
\*       because the evidence may contradict any model; a move to the SAME
\*       stage is not legal.
\*
\*     - reconcile(...): a transition is recorded only when the stage actually
\*       changed, and the FIRST evidence may carry the capability several
\*       rungs up at once — the Java records that as one transition and says
\*       in its reason that the evidence carried it several stages at once.
\*
\* WHAT THIS SPECIFICATION IS NOT
\*   A restatement of the code. The two safety properties below are
\*   falsifiable, and they were checked to be falsifiable: a mutated model
\*   that allows a two-rung jump violates AdvanceIsOneStep, and a mutated
\*   model that records an unchanged stage violates NoSelfTransition. Both
\*   mutations were run through TLC and were rejected (see the WAL of
\*   2026-11-02). A property that cannot fail is not a check.
\*
\* BOUNDING
\*   The recorded transitions are capped by MAX_TRANSITIONS, so the state
\*   space is finite. CHECK_DEADLOCK is FALSE: a bounded model may run out
\*   of room, and that is not a defect of the machine being specified.

EXTENDS Naturals, Sequences, TLC

CONSTANTS
    MAX_TRANSITIONS    \* the bound on the recorded transitions

\* The ladder is FIXED in the Java (LearningStateMachine.LADDER), so it is
\* defined here rather than supplied as a model constant.
LADDER == <<"UNKNOWN", "EXPLORATION", "INITIAL_LEARNING", "PRACTICE", "COMPETENCE", "UNDERSTANDING", "KNOWLEDGE">>

VARIABLES
    stage,             \* the current rung; 0 means "no state recorded yet"
    recorded           \* the sequence of the recorded (from, to) pairs

vars == <<stage, recorded>>

NoState == 0
TopStage == Len(LADDER)

Init ==
    /\ stage = NoState
    /\ recorded = <<>>

\* The first evidence may place the capability at any rung: the Java records
\* one transition from UNKNOWN and says in the reason that the evidence
\* carried it several stages at once.
FirstEvidence ==
    /\ stage = NoState
    /\ Len(recorded) < MAX_TRANSITIONS
    /\ \E to \in 1..TopStage :
         /\ stage' = to
         /\ recorded' = Append(recorded, <<NoState, to>>)

\* Moving up is legal one rung at a time.
Advance ==
    /\ stage \in 1..(TopStage - 1)
    /\ Len(recorded) < MAX_TRANSITIONS
    /\ stage' = stage + 1
    /\ recorded' = Append(recorded, <<stage, stage + 1>>)

\* Moving down is legal from any stage: the evidence may contradict any
\* model, so the capability falls back — but it never falls back past the
\* floor of the ladder.
FallBack ==
    /\ stage \in 2..TopStage
    /\ Len(recorded) < MAX_TRANSITIONS
    /\ \E to \in 1..(stage - 1) :
         /\ stage' = to
         /\ recorded' = Append(recorded, <<stage, to>>)

Next == FirstEvidence \/ Advance \/ FallBack

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ stage \in 0..TopStage
    /\ \A i \in 1..Len(recorded) :
         /\ recorded[i][1] \in 0..TopStage
         /\ recorded[i][2] \in 0..TopStage

\* A recorded transition never keeps the capability where it was.
NoSelfTransition ==
    \A i \in 1..Len(recorded) : recorded[i][1] # recorded[i][2]

\* After the first evidence, every recorded move up is exactly one rung.
AdvanceIsOneStep ==
    \A i \in 1..Len(recorded) :
        (recorded[i][1] # NoState /\ recorded[i][2] > recorded[i][1])
            => recorded[i][2] = recorded[i][1] + 1

\* The ladder is finite: a capability never rises above the top rung.
NeverAboveTheTop ==
    \A i \in 1..Len(recorded) : recorded[i][2] <= TopStage

====
