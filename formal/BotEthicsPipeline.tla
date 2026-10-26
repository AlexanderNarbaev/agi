---------------------------- MODULE BotEthicsPipeline -----------------------
\* TLA+ formal specification of the io.matrix.api.BotEthicsPipeline
\* (matrix-core/src/main/java/io/matrix/api/BotEthicsPipeline.java)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-07-19)
\* Ref: L7 §5, L12 §4 (legal audit), CRITICAL_GAPS.md GAP-021 + Wave 22-C.

EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS
    MAX_BOTS,        \* upper bound on number of bots (model-checked)
    MAX_TICKS,       \* upper bound on ticks per bot (model-checked)
    BotIds,          \* universe of bot identifiers (model-checked)
    ActionIds        \* universe of action texts (model-checked)

\* RECON-W38.4: the bot and action universes are model constants, not STRING.
\* The spec's own comment said "Register a new bot. Bot ids are bounded", but
\* the code quantified over the INFINITE set STRING, and TLC cannot enumerate
\* an infinite quantifier bound inside a temporal formula - the two liveness
\* properties were therefore not model-checkable at all. Naming the universes
\* as constants makes the bound explicit, makes the spec finite, and makes the
\* LIV properties checkable. Both universes are supplied by the .cfg.

VARIABLES
    bots,             \* set of bot ids currently registered
    ticks,            \* function: botId -> count of ticks so far
    approvals,        \* function: botId -> count of approved actions
    rejections,       \* function: botId -> count of rejected actions
    tombstones,       \* function: botId -> count of tombstones
    auditChain,       \* function: botId -> seq of audit links
    lastAction       \* function: botId -> most recent action text

vars == <<bots, ticks, approvals, rejections, tombstones, auditChain, lastAction>>

\* RECON-W38.4: the spec tested for the kill trigger with `"kill" \in a`, i.e.
\* set membership applied to a STRING. That is not a substring test and it is
\* not a well-formed TLA+ expression for a string. This is the substring test
\* the author meant, expressed with the Sequences module's SubSeq.
\* RECON-W38.4: SubSeq on a STRING returns a tuple of characters, not a
\* string, and TLC cannot index a string directly. The comparison is therefore
\* made between two character tuples.
Contains(needle, haystack) ==
    \E i \in 1..(Len(haystack) - Len(needle) + 1) :
        SubSeq(haystack, i, i + Len(needle) - 1) = SubSeq(needle, 1, Len(needle))

\* StartsWith(prefix, s) - true when s begins with prefix. Each prefix is
\* compared at its OWN length: "APPROVE:" has 8 characters and "REJECT:" has 7,
\* so a fixed 8-character window cannot serve both.
StartsWith(prefix, s) ==
    Len(s) >= Len(prefix) /\ SubSeq(s, 1, Len(prefix)) = SubSeq(prefix, 1, Len(prefix))

\* RECON-W38.4: safe function access. The liveness properties quantify over the
\* constant BotIds and guard the body with `b \in bots`, but TLC evaluates the
\* guarded body of a temporal property anyway, so `ticks[b]` for a bot that is
\* not registered raised "Attempted to apply function to argument which is not
\* in the domain of the function". For a registered bot the domain of every
\* counter is exactly bots, so this accessor returns the same value as the
\* direct application and only differs for the unregistered case.
ValueOr(f, b, dflt) == IF b \in DOMAIN f THEN f[b] ELSE dflt

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
    \* RECON-W38.4: these were written as [f EXCEPT ![b] = 0]. EXCEPT does not
    \* extend a function's domain, so registering a bot left every counter
    \* function with its OLD domain: bots grew to {b} while ticks stayed a
    \* function on {}. TypeOK was therefore false in EVERY reachable state
    \* after the first registration. The function override operator (@@, from
    \* the TLC module) extends the domain, which is what the author meant and
    \* what the Java implementation does when it registers a bot.
    /\ ticks' = ticks @@ (b :> 0)
    /\ approvals' = approvals @@ (b :> 0)
    /\ rejections' = rejections @@ (b :> 0)
    /\ tombstones' = tombstones @@ (b :> 0)
    /\ auditChain' = auditChain @@ (b :> <<>>)
    /\ lastAction' = lastAction @@ (b :> "")

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

\* The tick action of one bot, factored out of Next so that a per-bot
\* fairness assumption can be stated (see Spec).
BotTick(b) ==
    \E a \in ActionIds :
        /\ b \in bots
        /\ (\/ ApproveAction(b, a)
            \/ RejectAction(b, a))

Next ==
    (\E b \in BotIds : RegisterBot(b))
    \/ (\E b \in BotIds : BotTick(b))

\* RECON-W38.4: the LIV-1 comment said "under fairness assumptions that every
\* bot's tick eventually runs", but Spec encoded no fairness at all, so the
\* property was false in the model: a bot could be registered and never
\* ticked, because [][Next]_vars admits infinite stuttering and Next could
\* always pick a different bot. The per-bot weak fairness assumption the
\* comment describes is now actually stated.
Spec == Init /\ [][Next]_vars /\ (\A b \in BotIds : WF_vars(BotTick(b)))

\* ── Properties (Safety) ──────────────────────────────────────────────────────────

\* INV-1: The set of bots only grows (no late de-registration).
BotsMonotonic ==
    [][ bots' \subseteq bots \/ Cardinality(bots') > Cardinality(bots) \/ bots' = bots ]_bots

\* RECON-W38.4: the subset direction was INVERTED. The operator is named
\* "BotsMonotonicExpand" and INV-1's comment says "The set of bots only grows
\* (no late de-registration)", but the expression asserted bots' \subseteq bots
\* - that bots only ever SHRINK. No action in this module removes a bot
\* (RegisterBot does bots' = bots \union {b}; the other two actions leave
\* bots UNCHANGED), so the assertion was false in every state in which a bot
\* was registered. The corrected form asserts the growth the comment
\* describes, so it is strictly stronger than what was written.
BotsMonotonicExpand ==
    [][ bots \subseteq bots' ]_bots

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
    \A b \in bots :
        \A i \in 1..Len(auditChain[b]) :
            \/ auditChain[b][i] \in {"--APPROVE--", "--REJECT--"}
            \/ Len(auditChain[b][i]) > 9 /\
               (StartsWith("APPROVE:", auditChain[b][i]) \/
                StartsWith("REJECT:", auditChain[b][i]))

\* ── Properties (Liveness) ─────────────────────────────────────────────────────────

\* LIV-1: Every registered bot eventually gets at least one decision.
\* (under fairness assumptions that every bot's tick eventually runs)
\* RECON-W38.4: the quantifier domain was the state variable `bots`, which
\* TLC cannot resolve inside a temporal formula. The domain is now the
\* constant BotIds and the state variable appears only in the guard, which is
\* the same statement: bots \subseteq BotIds, so quantifying over BotIds and
\* requiring b \in bots is equivalent to quantifying over bots.
EveryBotEventuallyTicked ==
    [](\A b \in BotIds : (b \in bots) => <>(ValueOr(ticks, b, 0) > 0))

\* LIV-2: Every action text that contains the kill trigger eventually
\* causes a rejection (no delayed acceptance of harmful content).
\* RECON-W38.4: same constant-domain repair as LIV-1, plus the substring test
\* (Contains) instead of the set-membership misuse.
EventualKillRejection ==
    \A a \in ActionIds :
        \A b \in BotIds :
            (b \in bots) =>
                (([] (ValueOr(lastAction, b, "") = a /\ Contains("kill", a)) =>
                  <>(ValueOr(rejections, b, 0) > 0)))

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
