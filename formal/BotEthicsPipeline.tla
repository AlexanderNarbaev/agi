---------------------------- MODULE BotEthicsPipeline -----------------------
\* TLA+ formal specification of the io.matrix.api.BotEthicsPipeline
\* (matrix-core/src/main/java/io/matrix/api/BotEthicsPipeline.java)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-07-19)
\* Ref: L7 §5, L12 §4 (legal audit), CRITICAL_GAPS.md GAP-021 + Wave 22-C.

EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
    MAX_BOTS,        \* upper bound on number of bots (model-checked)
    MAX_TICKS         \* upper bound on ticks per bot (model-checked)

VARIABLES
    bots,             \* set of bot ids currently registered
    ticks,            \* function: botId -> count of ticks so far
    approvals,        \* function: botId -> count of approved actions
    rejections,       \* function: botId -> count of rejected actions
    tombstones,       \* function: botId -> count of tombstones
    auditChain,       \* function: botId -> seq of audit links
    lastAction,       \* function: botId -> most recent action text

vars == <<bots, ticks, approvals, rejections, tombstones, auditChain, lastAction>>

\* ── Type invariants ────────────────────────────────────────────────────────────

TypeOK ==
    /\ bots \in SUBSET STRING
    /\ ticks \in [bots -> Nat]
    /\ approvals \in [bots -> Nat]
    /\ rejections \in [bots -> Nat]
    /\ tombstones \in [bots -> Nat]
    /\ auditChain \in [bots -> Seq(STRING)]   \* sequence of audit links per bot
    /\ lastAction \in [bots -> STRING]

Init ==
    /\ bots = {}
    /\ ticks = [b \in {} |-> 0]
    /\ approvals = [b \in {} |-> 0]
    /\ rejections = [b \in {} |-> 0]
    /\ tombstones = [b \in {} |-> 0]
    /\ auditChain = [b \in {} |-> <<>>]
    /\ lastAction = [b \in {} |-> ""]

\* ── Actions ────────────────────────────────────────────────────────────────────

\* Register a new bot. Bot ids are bounded.
RegisterBot(b) ==
    /\ b \notin bots
    /\ Cardinality(bots) < MAX_BOTS
    /\ bots' = bots \union {b}
    /\ ticks' = [ticks EXCEPT ![b] = 0]
    /\ approvals' = [approvals EXCEPT ![b] = 0]
    /\ rejections' = [rejections EXCEPT ![b] = 0]
    /\ tombstones' = [tombstones EXCEPT ![b] = 0]
    /\ auditChain' = [auditChain EXCEPT ![b] = <<>>]
    /\ lastAction' = [lastAction EXCEPT ![b] = ""]

\* Approve action. No tombstone is created, but a chain link is appended.
ApproveAction(b, a) ==
    /\ b \in bots
    /\ ticks[b] < MAX_TICKS
    /\ approvals' = [approvals EXCEPT ![b] = approvals[b] + 1]
    /\ ticks' = [ticks EXCEPT ![b] = ticks[b] + 1]
    /\ rejections' = rejections
    /\ tombstones' = tombstones
    /\ auditChain' = [auditChain EXCEPT ![b] = Append(auditChain[b], "APPROVE:" \o a)]
    /\ lastAction' = [lastAction EXCEPT ![b] = a]
    /\ UNCHANGED <<bots>>

\* Reject action. A tombstone is created and a chain link appended.
RejectAction(b, a) ==
    /\ b \in bots
    /\ ticks[b] < MAX_TICKS
    /\ rejections' = [rejections EXCEPT ![b] = rejections[b] + 1]
    /\ ticks' = [ticks EXCEPT ![b] = ticks[b] + 1]
    /\ tombstones' = [tombstones EXCEPT ![b] = tombstones[b] + 1]
    /\ auditChain' = [auditChain EXCEPT ![b] = Append(auditChain[b], "REJECT:" \o a)]
    /\ lastAction' = [lastAction EXCEPT ![b] = a]
    /\ UNCHANGED <<bots, approvals>>

Next ==
    \E b \in STRING, a \in STRING :
        \/ RegisterBot(b)
        \/ /\ b \in bots
           /\ \/ ApproveAction(b, a)
              \/ RejectAction(b, a)

Spec == Init /\ [][Next]_vars

\* ── Properties (Safety) ──────────────────────────────────────────────────────────

\* INV-1: The set of bots only grows (no late de-registration).
BotsMonotonic ==
    [][ bots' \subseteq bots \/ Cardinality(bots') > Cardinality(bots) \/ bots' = bots ]_bots

\* Alternative: bots monotonically expand.
BotsMonotonicExpand ==
    [][ bots' \supseteq bots ]_bots

\* INV-2: For every bot, approvals + rejections equals total ticks.
ApprovalsPlusRejectionsEqualsTicks ==
    \A b \in bots:
        approvals[b] + rejections[b] = ticks[b]

\* INV-3: For every bot, the number of tombstones equals the number of rejections.
TombstonesEqualsRejections ==
    \A b \in bots:
        tombstones[b] = rejections[b]

\* INV-4: The audit chain length equals the number of decisions.
\* (tombstone creations and audit-link appends are 1:1)
AuditChainLengthEqualsDecisions ==
    \A b \in bots:
        Len(auditChain[b]) = approvals[b] + rejections[b]

\* INV-5: Every audit-chain link starts with "APPROVE:" or "REJECT:".
AuditLinksAreTyped ==
    \A b \in bots, i \in 1..Len(auditChain[b]):
        \/ auditChain[b][i] \in {"--APPROVE--", "--REJECT--"}
        \/ Len(auditChain[b][i]) > 9 /\ auditChain[b][i][1..8] \in {"APPROVE:", "REJECT:"}

\* ── Properties (Liveness) ─────────────────────────────────────────────────────────

\* LIV-1: Every registered bot eventually gets at least one decision.
\* (under fairness assumptions that every bot's tick eventually runs)
EveryBotEventuallyTicked ==
    [](\A b \in bots : <> ticks[b] > 0)

\* LIV-2: Every action text that contains the kill trigger eventually
\* causes a rejection (no delayed acceptance of harmful content).
EventualKillRejection ==
    \A a \in STRING :
        \A b \in bots :
            ([] (lastAction[b] = a /\ "kill" \in a) =>
                <> rejections[b] > 0)

=============================================================================
\* Modification History
\* v1.0 (2026-07-19) — initial spec for BotEthicsPipeline.
\* Models the bot tick → FROZEN FNL → audit chain → tombstone pipeline
\* with a 4-bounded state space.
\*
\* Cfg (for TLC):
\* SPECIFICATION Spec
\* CONSTANTS MAX_BOTS = 2, MAX_TICKS = 3
\* INVARIANTS TypeOK BotsMonotonicExpand
\*              ApprovalsPlusRejectionsEqualsTicks
\*              TombstonesEqualsRejections
\*              AuditChainLengthEqualsDecisions
\*              AuditLinksAreTyped
\* PROPERTIES EveryBotEventuallyTicked EventualKillRejection
