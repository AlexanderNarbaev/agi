---------------------------- MODULE HashChain ---------------------------
\* TLA+ formal specification of the io.matrix.audit.HashChain
\* (matrix-core/src/main/java/io/matrix/audit/HashChain.java)
\*
\* Author: MATRIX SSC
\* Spec version: 1.0 (2026-07-19)
\* Ref: L7 §5 (FROZEN audit), CRITICAL_GAPS.md GAP-021 + Wave 16.

EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS
    MAX_LINKS,        \* upper bound on chain length (model-checked)
    VALID_PAYLOADS,   \* set of valid payload strings (model-checked)
    VALID_EXTRAS      \* set of valid extra tags (model-checked)

VARIABLES
    chain,            \* ordered sequence of links
    lastHash,         \* hash of the last link ("" if chain empty)
    verified          \* boolean: result of last verify() call

vars == <<chain, lastHash, verified>>

\* ── Type invariants ────────────────────────────────────────────────────────────

TypeOK ==
    /\ chain \in Seq([payload: VALID_PAYLOADS, extra: VALID_EXTRAS, hash: STRING])
    /\ lastHash \in STRING
    /\ verified \in BOOLEAN

Init ==
    /\ chain = <<>>
    /\ lastHash = "0000000000000000000000000000000000000000000000000000000000000000"
    /\ verified = TRUE

\* ── Abstract hash function (model-checked) ─────────────────────────────────────
\*
\* The actual implementation uses SHA-256. In TLA+ we model it as an opaque
\* function subject to the standard cryptographic assumption: given the same
\* inputs, the same hash; given different inputs, the hash differs (with
\* overwhelming probability). For the spec we use a deterministic function
\* that satisfies the same input/output relationship.
\*
\* This is sufficient to verify structural properties like chain integrity.

RECURSIVE ComputeHash(_, _, _, _, _)

ComputeHash(prevHash, seq, payload, ts, extra) ==
    \* Deterministic opaque hash. We use a simple encoding that TLA+ can
    \* reason about: prefix the inputs and concatenate.
    LET encoded == "H" \o prevHash \o ToString(seq) \o ":" \o payload \o "@" \o ToString(ts) \o "|" \o extra
    IN encoded

\* ── Operations ────────────────────────────────────────────────────────────────

\* The hash the first link chains from.
GENESIS == "0" \o SubSeq("0000000000000000000000000000000000000000000000000000000000000000", 1, 64)

\* ChainIntact(c) — the answer io.matrix.audit.HashChain.verify() gives for
\* the chain c: every link's hash matches its own fields AND the previous
\* link's hash (the genesis hash for the first link).
\*
\* RECON-W38.5: this predicate existed only inlined inside RestoreVerifies,
\* which is why the module could not express what it was actually about. The
\* Java's verify() computes exactly this.
ChainIntact(c) ==
    \A i \in 1..Len(c):
        c[i].hash = ComputeHash(
            IF i = 1 THEN GENESIS ELSE c[i-1].hash,
            i - 1, c[i].payload, i - 1, c[i].extra)

\* The most recent link of the append-only chain. The chain only grows at
\* the end, so the newest link is the last element of the sequence.
LastLink(s) == s[Len(s)]

\* Append a new link. Reuses the genesis-hash rule (no predecessor).
AppendLink(p, e) ==
    /\ Len(chain) < MAX_LINKS
    /\ p \in VALID_PAYLOADS
    /\ e \in VALID_EXTRAS
    /\ LET prev == IF Len(chain) = 0 THEN "0" \o SubSeq("0000000000000000000000000000000000000000000000000000000000000000", 1, 64)
                ELSE LastLink(chain).hash
       seq  == Len(chain)
       ts   == seq  \* simple deterministic ts for the spec
       newLink == [payload |-> p, extra |-> e, hash |-> ComputeHash(prev, seq, p, ts, e)]
       IN
       /\ chain' = Append(chain, newLink)
       /\ lastHash' = newLink.hash
       \* RECON-W38.5: the old value was the constant TRUE. It is not: the
       \* Java's class javadoc states that "any retroactive mutation
       \* invalidates every subsequent verify() check", so appending after a
       \* tampered link does NOT restore the chain's validity - the appended
       \* link simply chains from a broken predecessor. The verification
       \* result is therefore the integrity of the new chain, not TRUE.
       /\ verified' = ChainIntact(chain')

\* ── Specification ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

\* The next state appends one link with an arbitrary valid payload/extra.
\* A retroactive mutation of a link's payload - the attack the Java's design
\* anticipates ("any retroactive mutation invalidates every subsequent
\* verify() check"). The link's stored hash is NOT updated, which is what
\* makes it detectable.
\*
\* RECON-W38.5: the module had no way to tamper at all, so the "tampering
\* invalidates the chain" property had an unreachable antecedent and could
\* never fail - it was checked and proved nothing.
TamperLink(i, p) ==
    /\ i \in 1..Len(chain)
    /\ p \in VALID_PAYLOADS
    /\ p /= chain[i].payload
    /\ chain' = [chain EXCEPT ![i] = [chain[i] EXCEPT !.payload = p]]
    /\ UNCHANGED <<lastHash>>
    /\ verified' = ChainIntact(chain')

\* The next state appends one link with an arbitrary valid payload/extra, or
\* mutates a link's payload in place.
Next == (\E p \in VALID_PAYLOADS, e \in VALID_EXTRAS : AppendLink(p, e))
        \/ (\E i \in 1..Len(chain), p \in VALID_PAYLOADS : TamperLink(i, p))

\* The behaviour of the audited system: append-only, arbitrarily long.
Spec == Init /\ [][Next]_vars

\* ── Properties ─────────────────────────────────────────────────────────────────

\* INV-1: Chain length is monotonically non-decreasing.
ChainMonotonic ==
    [][ Len(chain') >= Len(chain) ]_chain

\* INV-2: lastHash is updated exactly when chain grows.
LastHashConsistent ==
    [][ (Len(chain') = Len(chain) + 1 /\ lastHash' = LastLink(chain').hash)
         \/ (Len(chain') = Len(chain) /\ lastHash' = lastHash) ]_<<chain, lastHash>>

\* INV-3: After any append, the chain is internally consistent.
\*
\* RECON-W38.5: the old formula said "the last link's hash differs from
\* lastHash'" - but lastHash' IS the last link's hash, so the formula was
\* FALSE in every state with a non-empty chain. It was excluded from the
\* configuration file, so the module carried a property that was simply
\* untrue and was never run. The property now says what the comment says:
\* an append neither breaks an intact chain nor repairs a broken one.
AppendKeepsChainValid ==
    [][ (Len(chain') = Len(chain) + 1)
          => (ChainIntact(chain') <=> ChainIntact(chain)) ]_chain

\* INV-4: Tampering with any link invalidates the chain.
\*
\* RECON-W38.5: the old formula's antecedent included `AppendLink(p, e)` AND
\* `LastLink(chain').payload /= p` - but an append always sets the payload to
\* p, so the antecedent was UNREACHABLE and the property could never fail.
\* It was checked and proved nothing. The property now says what the comment
\* says, and it can fail: if any link's payload changed, verification must
\* report the chain as broken.
\* RECON-W38.5 (second pass): the first version of this property read
\* "some link's payload changed => verified' = FALSE". TLC refuted it, and
\* it was TLC that was right: the module's toy hash is a plain encoding, so
\* changing a payload back to an earlier value restores the stored hash and
\* the chain verifies again. A one-way hash would not behave that way, but
\* the spec models a reversible encoding and must not pretend otherwise.
\*
\* The guarantee the Java actually gives is the DIRECTION that holds for any
\* hash: a chain that does not verify is never reported as verified. That is
\* the detection half, and it is what makes
\* io.matrix.audit.HashChain.restore() reject the chain.
TamperDetected ==
    [][ ~ChainIntact(chain') => verified' = FALSE ]_vars

\* INV-5: the verification result tracks the chain's integrity.
\*
\* RECON-W38.5: the old formula quantified `newChain \in {chain}` - a
\* singleton - and then asserted the same implication twice, so it said only
\* "an intact chain reports verified = TRUE", which the old AppendLink
\* guaranteed by fiat. The property now states the correspondence the Java
\* actually implements: `verified` IS the answer verify() would give, so a
\* chain that does not verify is reported as broken - which is what makes
\* io.matrix.audit.HashChain.restore() reject it.
RestoreVerifies ==
    [][ verified' = ChainIntact(chain') ]_chain

\* INV-6: Each link's hash depends on the previous link's hash.
\*
\* RECON-W38.5: this was an UNCONDITIONAL invariant over the current chain -
\* "the chain is always internally consistent" - and that is exactly what a
\* retroactive mutation breaks. TLC found the violation the moment the
\* tamper action was added, which is the tamper action working as intended.
\*
\* The guarantee the Java actually gives is NOT that a chain cannot be
\* broken, but that a broken chain is DETECTED: "any retroactive mutation
\* invalidates every subsequent verify() check". So the dependency is now the
\* definition of an intact chain (used by ChainIntact), and the system-level
\* guarantee is carried by TamperDetected and RestoreVerifies.
HashChainDependency(c) ==
    \A i \in 2..Len(c):
        c[i].hash = ComputeHash(
            c[i-1].hash, i - 1, c[i].payload, i - 1, c[i].extra)

\* INV-7: the system never reports a broken chain as verified.
VerifiedImpliesIntact ==
    verified => ChainIntact(chain)

=============================================================================
\* Modification History
\* v1.0 (2026-07-19) — initial spec for HashChain.
\* Mirrors the Java implementation in io.matrix.audit.HashChain.
\* Covers append-only, chain integrity, tamper detection, restore semantics.

Cfg (for TLC):
\* SPECIFICATION Spec
\* INVARIANTS TypeOK HashChainDependency
\* PROPERTIES ChainMonotonic LastHashConsistent TamperDetected RestoreVerifies
\* CHECK_DEADLOCK FALSE
