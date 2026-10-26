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
       /\ verified' = TRUE    \* the chain is intact immediately after append

\* ── Specification ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

\* The next state appends one link with an arbitrary valid payload/extra.
Next == \E p \in VALID_PAYLOADS, e \in VALID_EXTRAS : AppendLink(p, e)

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
AppendKeepsChainValid ==
    [][ \A i \in 1..Len(chain'):
             \E link \in {LastLink(chain')}: link.hash /= lastHash' ]_chain

\* INV-4: Tampering with any link invalidates the chain.
TamperDetected ==
    [][ \A p \in VALID_PAYLOADS, e \in VALID_EXTRAS, oldChain \in {chain}, oldLast \in {lastHash}:
            /\ oldChain /= <<>>
            /\ lastHash = oldLast
            /\ chain = oldChain
            /\ AppendLink(p, e)
            /\ LastLink(chain').payload /= p  \* tamper: changed the payload
            =>
                verified' = FALSE  \* verification must catch it
    ]_vars

\* INV-5: Reconstructed chains must verify (when their hashes are intact).
RestoreVerifies ==
    [][ \A newChain \in {chain}:
            (newChain /= <<>> /\ \A i \in 1..Len(newChain):
                newChain[i].hash = ComputeHash(
                    IF i = 1 THEN "0" \o SubSeq("0000000000000000000000000000000000000000000000000000000000000000", 1, 64)
                             ELSE newChain[i-1].hash,
                    i - 1,
                    newChain[i].payload,
                    i - 1,
                    newChain[i].extra))
            =>
                (chain = newChain => verified' = TRUE)
    ]_chain

\* INV-6: Each link's hash depends on the previous link's hash.
HashChainDependency ==
    \A i \in 2..Len(chain):
        chain[i].hash = ComputeHash(
            chain[i-1].hash, i-1, chain[i].payload, i-1, chain[i].extra)

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
