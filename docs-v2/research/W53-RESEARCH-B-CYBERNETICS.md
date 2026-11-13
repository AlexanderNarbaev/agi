# W53 — Research track B: cybernetics and control theory on coordination between two independently running cognitive components

**Status:** `RESEARCH` — research input for W53 · **Date:** 2026-11-09 · **Author:** research track B, W53

**Track:** R-B of the cross-disciplinary doctrine (cybernetics / computational cognition).

**Scope — what this document does NOT claim.** It does not claim that MATRIX has general intelligence, human-like understanding, or consciousness. It does not claim that
the two chains are one process, one database, or one writer. It does not claim that control theory *proves* any W53 design decision: it supplies constraints and
vocabulary, and every consequence below is a **consequence to be checked by a test**. It does not claim that the law of requisite variety applies quantitatively to MATRIX
— no variety has been measured for MATRIX's channels — so every use of the law here is a *structural* argument, marked as such. Nothing here changes a normative file, a
contract, or a test.

---

## 1. The question

W53's intended capability, in the mandate's own terms: **chain A assigns a bounded cognitive task to chain B, receives a structured result, evaluates that result against
its own evidence, and completes the original task.** A remote result is a *report to be evaluated*, not a source of truth.

That is a claim about a **closed loop that crosses a process boundary**, and it raises three questions this document answers from primary works: **(a) delegation** — how
should one cognitive process hand work to another and judge what comes back? **(b) homeostasis with a remote actuator** — what does "regulation" mean when the thing that
moves the regulated variable is another process, reached over a channel, with latency? **(c) stability under a bad or absent peer** — what keeps a two-component system
stable when one component is slow, wrong, or gone?

The literature's answer in one sentence: **a loop is only a loop if the regulator can compare the outcome against a criterion it owns, through a channel whose capacity it
knows, and if the regulator is permitted to refuse the outcome.** MATRIX already satisfies parts of this; each part is located below.

---

## 2. What MATRIX's architecture already is

**Two processes, two databases, one writer each, one crossing.** `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md:13` — "MATRIX's subsystems are one
cognitive system that runs in two processes." The bridge is the only crossing: `docs-v2/engineering/W51-ARCHITECTURE-MAP.md:92` — "Exactly one row crosses the process
boundary: T9."

**The remote result is already a report, not a conclusion** — the load-bearing fact for this track, and it is already normative:
`ADR-W51-01-cognitive-runtime-and-bridge.md:125` and `:177`. In code, the sink that writes chain B's material into the epistemic store has exactly three write sites —
`matrix-core/src/main/java/io/matrix/bridge/EpistemicBridgeSink.java:83`, `:111`, `:134` — each writing a `SourceKind.SECONDARY_REPORT` source and a
`ClaimStatus.CANDIDATE` claim (`EpistemicBridgeSink.java:94-104`).

**The coordinator already runs a closed loop with a criterion.** The kernel predicts *before* it acts and compares the outcome with the prediction afterwards
(`matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java:417-427`, `:653-667`); the canonical `Outcome` carries `predictionId` and `matched` so that the
comparison is between two records (`matrix-core/src/main/java/io/matrix/contract/Outcome.java:35-45`, `:87-93`); the criterion for a self-generated task exists and is
bounded — `matrix-core/src/main/java/io/matrix/cognitive/CognitiveTask.java:22-36` carries `reason`, `servesGoal`, `expectedBenefit`, `costEstimate`,
`completionCondition`, `stopCondition`, `allowedActions` (an allow-list) and `budgetWallClockMillis`.

**Governance is uniform in both directions**, and the direction is carried by the granted right, not a flag
(`matrix-core/src/main/java/io/matrix/contract/bridge/BridgeCapability.java:29-118`); the same envelope, signature, capability check, budget and audit apply to every
message (`matrix-core/src/main/java/io/matrix/contract/bridge/BridgeReceiver.java:271-330`); the refusals are a closed, named vocabulary
(`matrix-core/src/main/java/io/matrix/contract/bridge/BridgeRefusalReason.java:24-63`), and the budget is checked *before* the store is touched
(`matrix-core/src/main/java/io/matrix/contract/bridge/BridgeBudget.java:44-56`). The runtime already knows how to treat a remote peer: its health is *reported, not
observed* (`matrix-core/src/main/java/io/matrix/runtime/RuntimeComponent.java:36-42`; `matrix-core/src/main/java/io/matrix/runtime/CognitiveRuntime.java:36-38`,
`:117-129`).

**What W53 has already put in flight** (this tree, `feature/w53-distributed-cognition`): two new bridge rights, `INFERENCE_REQUEST` and `INFERENCE_RESPONSE`; the typed
contracts `contract/bridge/CognitiveTaskRequest.java` and `CognitiveTaskResponse.java`; chain B's receiving side
`matrix-api-gateway/src/main/java/io/matrix/api/bridge/TaskBridge.java` with its durable store `RemoteTaskStore.java` and `DurableBridgeIdempotency.java`; the capability
allow-list `CognitiveCapability.java`; and `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md`. **The chain A half — the dispatch and the evaluation against chain
A's own evidence — is not in the tree yet.** §5 reads the sources against what is there.

---

## 3. The primary works

### 3.1 W. R. Ashby, *An Introduction to Cybernetics* (1956) — variety and the channel bound

**Citation.** Ashby, W. R. (1956). *An Introduction to Cybernetics*. New York: John Wiley & Sons (parallel London issue: Methuen / Chapman & Hall, 1956). The edition
verified here is the Wiley printing (14 chapters); the London issue of the same year paginates and divides differently, so **the section numbers below are of the Wiley
printing** and are the locator to quote. Scan: <https://archive.org/details/introductiontocy00ashb>.

**In the author's own terms.** §11/7, p. 206: "This is the law of Requisite Variety. To put it more picturesquely: only variety in R can force down the variety due to D;
only variety can destroy variety." **R** is the regulator, **D** the disturbance, **E** the outcome; the argument counts over a table in which "no element may occur twice
in a column" (§11/5, p. 205). §11/11, p. 210: "The law of Requisite Variety says that R's capacity as a regulator cannot exceed R's capacity as a channel of
communication" — and on the same page a regulator "to achieve homeostasis" and a correction channel "to suppress noise" are "**homologous**". §12/2, p. 219: "the
restrictions in the channel from R to T, those on the effector side of R … the man who has lost an arm, the insect that cannot fly … and the rudder that is stuck." §13/2,
p. 244: "**Largeness in itself is not the source** … the main cause of difficulty is the **variety in the disturbances** that must be regulated against." §13/5, p. 246: a
discovered constraint converts "regulation impossible" into "regulation possible" — "the discovery of a constraint is the would-be regulator's only hope."

**What it means for MATRIX's two-chain execution.** *Structural argument, not a measurement.* (i) The regulator's power over the outcome is bounded by the **channel**,
not the intent, so the **evaluation vocabulary must be at least as large as the set of things chain B can get wrong**. (ii) The *effector side* of chain A's regulator is
chain B, and "the rudder that is stuck" is exactly the case where the peer's report is well-formed and useless. (iii) The epistemic store is a "very large system"
relative to the coordinator, but the size is not the difficulty — the *variety of the disturbance* is, and it is already bounded by BRIDGE-9 (`BridgeBudget.java:44-56`).
That bounding is a precondition for the loop being regulable at all, and it is the one thing W53 must not weaken.

### 3.2 W. R. Ashby, *Design for a Brain* (1952) — the ultrastable system

**Citation.** Ashby, W. R. (1952). *Design for a Brain; the Origin of Adaptive Behaviour*. London: Methuen (New York: Wiley, 1954 — the printing verified here). Scan:
<https://archive.org/details/designforbrain00ashb>.

**In the author's own terms.** Chapter 8 ("The Ultrastable System"), §8/4, p. 90: an ultrastable system is "one that is absolute and contains step-functions in a
sufficiently large number for us to be able to ignore the finiteness of the number". §8/5, p. 91: "the principle of ultrastability will be stated formally: an ultrastable
system acts selectively towards the fields of the main variables, **rejecting those that lead the representative point to a critical state but retaining those that do
not**." §8/9, pp. 98–99: "Thus an automatic pilot must be joined to the ailerons with care, but an **ultrastable pilot could safely be joined to the ailerons at random**.
Again, a linear system of *n* variables, to be made stable, needs the simultaneous adjustment of at least *n* parameters … But an ultrastable system with a thousand main
variables needs, to achieve stability, the specification of about six factors."

**What it means for MATRIX's two-chain execution.** *Structural argument.* The "ailerons at random" sentence states what W53 buys by making chain B's result a **report**:
an ultrastable arrangement tolerates a badly-joined actuator because the arrangement's own parameters change until the field is stable. MATRIX's equivalent of "changing
the field" is not the wiring — that is fixed by the isolation gate — but the coordinator's **own parameters**: which method it delegates, what budget it gives, what it
demands as evidence, when it stops asking. The substrate exists: the durable per-method profile
(`matrix-core/src/main/java/io/matrix/cognitive/algorithms/MethodMeasurementStore.java:19`) and the epistemic meta-memory
(`matrix-core/src/main/java/io/matrix/knowledge/meta/KnowledgeReliability.java:39`). Ashby's claim is *not* "a remote actuator is safe" but "a system whose own parameters
are variables of a deeper loop tolerates a poorly joined actuator". **HYPOTHESIS:** a delegation that fails should change the parameters of the next delegation (budget,
method, evidence demand), not merely be logged — W53 has no path from a failed delegation into the selector or the regulator.

### 3.3 Norbert Wiener, *Cybernetics* (1948) — feedback through a communication channel

**Citation.** Wiener, N. (1948). *Cybernetics: or Control and Communication in the Animal and the Machine*. Cambridge, MA: MIT Press. (2019 reissue, with the 1961
supplementary chapters and Sanjoy K. Mitter's foreword: <https://doi.org/10.7551/mitpress/11810.001.0001>.) Pagination is that of the 1948 edition, preserved in the
reissue (ch. IV "Feedback and Oscillation", p. 129).

**In the author's own terms.** Chapter IV, p. 130: "for effective action on the outer world it is not only essential that we possess good effectors, but that **the
performance of these effectors be properly monitored back to the central nervous system**, and that the readings of these monitors be properly combined with the other
information coming in from the sense organs to produce a properly proportioned output to the effectors." And on the signal tower, the same page: the signalman does not
"assume blindly that the signals and the switches have followed his orders … To avoid the dangers inherent in this contingency, **every effector, switch or signal, is
attached to a telltale back in the signal tower**, which conveys to the signalman its actual states and performance." Wiener's two clinical patients are the pathology:
the one with tabes dorsalis cannot feel where his feet are; the one with cerebellar tremor oscillates because the proportioning of the response to the proprioceptive
input is disturbed (pp. 129–130).

**What it means for MATRIX's two-chain execution.** *Structural argument.* "The performance of these effectors be properly monitored back" is the requirement that W53's
evaluation step exists to satisfy, and the telltale is the mechanism: the peer's performance must come back as **an observation the coordinator can check**, not as a
state the coordinator must assume. MATRIX already has the telltale in the knowledge direction —
`matrix-api-gateway/src/main/java/io/matrix/api/bridge/KnowledgeBridge.java:239-253`: the brain's answer carries what it did with the knowledge and the count of what it
holds. The gap for W53 is that the *result* of a delegated task must likewise carry enough to be checked, and must be **comparable to something the coordinator settled
before the delegation**.

### 3.4 Norbert Wiener, *The Human Use of Human Beings* (1950) — delay, and the loop that hunts

**Citation.** Wiener, N. (1950). *The Human Use of Human Beings*. Boston: The MIT Press. Second, revised edition 1954. Scan:
<https://archive.org/details/the-human-use-of-human-beings>.

**In the author's own terms.** "Multipliers", pp. 13–14: "precautions must be taken so that the push is not too hard, for if it is, the gun will swing past its proper
position, and will have to be pulled back in a series of oscillations, which may well become wider and wider, and lead to a disastrous instability. If the feedback is
controlled and kept within limits sufficiently stringent, this will not occur, and the existence of the feedback will increase the stability of performance of the gun."
And "Rigidity and Learning", p. 71: "feedback is the control of a system by reinserting into the system the results of its performance."

**What it means for MATRIX's two-chain execution.** *Structural argument, and a concrete inconsistency to fix.* Wiener's gun is a negative-feedback loop with a lag; too
much gain with too much lag makes the loop hunt. W53's delegation adds a lag of one process boundary, possibly plus one network round trip, to a loop that already has a
deadline. The repository's two numbers do not agree: the kernel's cycle budget is **250 ms** (`CognitiveKernel.java:99`) while the bridge publisher's timeout is **5
seconds** (`matrix-core/src/main/java/io/matrix/bridge/KnowledgePublisher.java:59`). A delegation that waits five seconds inside a 250 ms cycle is the gun that
overshoots. Either the deadline must be derived from the loop's remaining budget, or the delegation must not sit in the critical path — and the second option has a name
in control theory: **receding horizon** (§3.6).

### 3.5 Walter B. Cannon, "Organization for physiological homeostasis" (1929)

**Citation.** Cannon, W. B. (1929). "Organization for physiological homeostasis." *Physiological Reviews* 9(3): 399–431. <https://doi.org/10.1152/physrev.1929.9.3.399>.

**In the author's own terms.** Cannon's paper is the primary that fixes "homeostasis" as an *organisation of processes* rather than a property of a single part: the
coordinated physiological processes which maintain the steady states of the internal environment, whose organisation is itself part of what is kept constant. The title
contains the point that matters for W53 — **organisation**, not equilibrium.

**What it means for MATRIX's two-chain execution.** *Structural argument.* MATRIX's regulated variables are not chain B's contents; they are the epistemic store's
integrity — the propositions it holds, their standing, the evidence behind them. The "internal environment" is the epistemic store; the "processes that maintain its
steady states" are the kernel's cycle and the bridge's governed write path. Under Cannon's organisation-not-equilibrium reading, "who regulates MATRIX?" has the answer
"the arrangement", not "the coordinator": the coordinator is one component of the arrangement, and its ability to regulate is a property of the *loop* (coordinator → task
→ peer → report → evaluation → store). That is the sense in which Ashby's §11/11 binds.

### 3.6 Eduardo F. Camacho & Carlos Bordons, *Model Predictive Control* (2007) — select, execute, evaluate against a criterion

**Citation.** Camacho, E. F. & Bordons, C. (2007). *Model Predictive Control* (2nd ed.). Advanced Textbooks in Control and Signal Processing. London: Springer.
<https://doi.org/10.1007/978-0-85729-398-5>.

**In the authors' own terms.** MPC's defining structure (ch. 2, "Basic Predictive Control"): at each sampling instant the controller **solves an optimisation over a
prediction of the future**, **applies only the first move of the solution**, and **re-solves at the next instant**. The criterion is the objective function of that
optimisation, and it is not a post-hoc audit — it is the thing that *chooses the move*. The corollary that matters for W53: the loop is closed by **re-planning against
the criterion**, not by trusting the plan.

**What it means for MATRIX's two-chain execution.** *Structural argument.* W53's delegation is one step of a receding-horizon scheme: the coordinator has a criterion (the
task's `completionCondition` and `stopCondition`, `CognitiveTask.java:30-31`), it selects a method (the delegation), it applies the move (the remote call), and it
**re-plans at the next step** (the evaluation against the criterion, which may end in "do it locally instead"). Two consequences. First, the criterion must exist *before*
the delegation — which is why the coordinator must hold its own settled expectation when it sends the task and compare the returned result against it. Second, a receding
horizon tolerates bounded delay far better than a single-shot plan, because the plan is re-made rather than trusted; this is why §3.4's latency inconsistency can be
resolved by *structure* (re-plan) and not only by tightening a timeout.

### 3.7 Reza Olfati-Saber & Richard M. Murray (2004) — the delayed channel

**Citation.** Olfati-Saber, R. & Murray, R. M. (2004). "Consensus problems in networks of agents with switching topology and time-delays." *IEEE Transactions on Automatic
Control* 49(9): 1520–1533. <https://doi.org/10.1109/TAC.2004.834113>.

**In the authors' own terms.** The paper's subject is agreement reached over a communication graph whose edges **carry delays** and whose **topology changes**; the result
is that the behaviour of the agreement depends on the delay and on the connectivity of the graph over time, not on the algorithm alone. Delay and topology are therefore
*parameters of the loop*, and the loop's properties cannot be reasoned about without them.

**What it means for MATRIX's two-chain execution.** *Structural argument.* The bridge channel has three such parameters that MATRIX does not yet treat as loop parameters:
the round-trip latency, the **link's availability** (chain B may be `DEGRADED` or `DOWN`, `runtime/RuntimeComponent.java:64-74`), and the **topology** (one crossing, but
crossed in two directions with different governance, `BridgeCapability.java:29-118`). A W53 design that reasons about "chain B answers" without naming the delay budget and
the peer's health is reasoning about a graph with unstated parameters.

### 3.8 Olfati-Saber, Fax, Murray et al. (2007) and Cho & Lim (1999) — centralised versus decentralised

**Citations.** Olfati-Saber, R., Fax, J. A., Murray, R. M., Taylor, A., Timings-Bott, S., Hutchins, J., Nagle, J. & Cortés, A. (2007). "Consensus and cooperation in
networked multi-agent systems." *Proceedings of the IEEE* 95(1): 215–233. <https://doi.org/10.1109/JPROC.2006.887293>. Cho, K.-H. & Lim, J.-T. (1999). "Mixed
centralized/decentralized supervisory control of discrete event dynamic systems." *Automatica* 35(1): 121–128. <https://doi.org/10.1016/S0005-1098(98)00137-X>.

**In the authors' own terms.** The 2007 paper separates a problem whose solution requires **global information** from one whose solution can be reached by **local
information exchanged over a graph**; the second is what makes cooperation possible when no agent holds the whole state, and the price paid is that the distributed
solution is a function of the graph, not of the global problem alone. Cho and Lim make the corresponding distinction inside supervisory control: a **mixed
centralised/decentralised** architecture places some decision authority centrally and some in the local supervisors, and their subject is the *conditions* under which
that mixture is well-posed.

**What it means for MATRIX's two-chain execution.** *Structural argument, and an explicit trade.* MATRIX's arrangement is already a mixed architecture, and the trade is
already made: chain A owns the epistemic decisions (centralised) while chain B owns the local cognition (decentralised), and the bridge is the only channel between them.
**Given up:** chain A cannot, in one step, optimise over what chain B knows — the delegate's answer is computed under chain B's own state, so the joint decision is a
local optimum of a global problem; Ashby's §13/5 applies, and the way to make it regulable is to **find the constraint**, and MATRIX's constraint is BRIDGE-5
(`BridgeCapability.java:29-118`). **Gained:** what the ADR bought with the two-process decision — no ambient trust, one writer per file, and a failure of one chain that
cannot corrupt the other's state (`ADR-W51-01-cognitive-runtime-and-bridge.md:30-39`).

### 3.9 Ramadge & Wonham (1989), Grassé (1959) and Heylighen (2016) — the supervisor that may refuse; coordination without a command

**Citations.** Ramadge, P. J. G. & Wonham, W. M. (1989). "The control of discrete event systems." *Proceedings of the IEEE* 77(1): 81–98.
<https://doi.org/10.1109/5.21072>. Grassé, P.-P. (1959). "La reconstruction du nid et les coordinations inter-individuelles chez *Bellicositermes natalensis* et
*Cubitermes* sp. La théorie de la stigmergie: essai d'interprétation du comportement des termites constructeurs." *Insectes Sociaux* 6(1): 41–80.
<https://doi.org/10.1007/BF02223791>. Heylighen, F. (2016). "Stigmergy as a universal coordination mechanism I: Definition and components." *Cognitive Systems Research*
38: 4–13. <https://doi.org/10.1016/j.cogsys.2015.12.002>.

**In the authors' own terms.** Supervisory control's premise is that a **supervisor** decides, from the plant's observed state, what the plant's own controllers are
permitted to do next — including *not* to do it; the supervisor does not perform the work, it grants or withholds the authority to perform it. Stigmergy is the
complementary mechanism: the environment carries the coordination, so that individuals work "as if they were alone while their collective act" emerges from the traces
they leave behind (Grassé's original case is the termite nest; Heylighen 2016 fixes the definition and its components — a stigmergy has a **stimulus**, a **response** and
a resulting trace).

**What it means for MATRIX's two-chain execution.** *Structural argument.* MATRIX's bridge is already a stigmergic channel: chain B leaves a trace (an observation) in the
epistemic store; chain A reads the traces, decides, and leaves its own trace (established knowledge) that chain B reads. No command passes. Two consequences. (i) A
stigmergic trace must carry enough **standing** for its reader to know what to do with it — which is why
`matrix-api-gateway/src/main/java/io/matrix/api/bridge/KnowledgeBridge.java:266-282` sends the status, the supporting and contradicting evidence counts and the origin,
and not "this is true"; W53's delegated-result direction needs the same discipline. (ii) The supervisor's lesson applies to the *coordinator*: one that must be able to
refuse an actuator's output is not a supervisor in Ramadge–Wonham's sense unless it holds the authority to withhold the delegation itself, and the refusal must be a
recorded, named decision rather than a silent fallback.

---

## 4. The synthesis

**What a closed loop across a process boundary requires**, from the sources above:

1. **A criterion the regulator owns, fixed before the move.** (Wiener 1948: the effectors are monitored back and the readings are *combined with the other information*;
  MPC: the objective chooses the move; Ashby §11/12: the criterion belongs to the regulator, not to the plant.)
2. **A channel whose capacity the regulator knows.** (Ashby §11/11: R's capacity as a regulator cannot exceed R's capacity as a channel; §12/2: the effector side may be
  restricted; Wiener 1948: the telltale back from every effector.)
3. **A variety of responses at least as large as the variety of disturbances.** (Ashby §11/7: only variety in R can force down the variety due to D.) For a remote peer,
  "the variety of disturbances" is the set of things the peer can get wrong — including "well-formed and useless", which is Ashby's "the rudder that is stuck".
4. **An explicit, bounded delay and a named topology for the channel.** (Wiener 1948/1950: delay turns correction into hunting; Olfati-Saber & Murray 2004: delay and
  topology are parameters of the agreement.)
5. **The authority to refuse the outcome, recorded with a reason.** (Ramadge & Wonham 1989: the supervisor's essential act is withholding; Ashby 1952 §8/5: the system
  "rejects" the fields that lead to a critical state; §8/9: the arrangement tolerates a badly-joined actuator only because it can reject.)

**Where MATRIX already satisfies these.**

- (1) *Partly.* The kernel settles a prediction before acting and compares the outcome with it (`CognitiveKernel.java:417-427`, `:653-667`); the canonical `Outcome`
  carries `predictionId` and `matched` (`contract/Outcome.java:35-45`); the self-generated task carries a `completionCondition` and a `stopCondition`
  (`CognitiveTask.java:30-31`). **But** the result crossing the bridge is currently comparable to nothing: the `Outcome` the knowledge direction returns is built with
  `predictionId = null` and `matched = null` (`matrix-api-gateway/src/main/java/io/matrix/api/bridge/KnowledgeBridge.java:241-253`), so `Outcome.comparable()` is false
  for it (`contract/Outcome.java:95-97`).
- (2) *Yes, and strongly.* The envelope's canonical bytes are fixed and signed (`contract/bridge/BridgeEnvelope.java:88-97`), the budget is checked before the store is
  touched (`BridgeReceiver.java:319-323`), the request id is the idempotency key (`BridgeReceiver.java:174-186`), and the capability is a closed list
  (`BridgeCapability.java:29-118`). This is the part of the loop MATRIX has done well.
- (3) *Partly.* The bridge's **protocol** refusals are a rich closed vocabulary (`BridgeRefusalReason.java:24-63`), but the **evaluation** refusals — the reasons a
  coordinator gives for rejecting a well-formed, correctly-signed, in-budget peer answer — do not exist yet. A binary accept/reject is below Ashby's required variety.
- (4) *No, in the chain A direction.* The loop's delay is not a declared parameter of the coordinator: the publisher's timeout is 5 s (`KnowledgePublisher.java:59`), the
  cycle budget is 250 ms (`CognitiveKernel.java:99`), the retry policy allows three attempts with a 2 s cap (`contract/RetryPolicy.java:39-40`), and the peer's health is
  *reported, not observed* (`runtime/CognitiveRuntime.java:36-38`). The W53 task *request* does declare a deadline and a budget
  (`contract/bridge/CognitiveTaskRequest.java`) — the right shape — but nothing yet derives them from the cycle's remaining budget.
- (5) *Partly.* The coordinator can refuse at the protocol level and the refusal is audited (`BridgeReceiver.java:271-330`; `bridge/BridgeService.java:119-124`). What is
  missing is the *authority* to refuse the **delegated result** on epistemic grounds, and the fall-back path that completes the original task without the peer's help.

**In one sentence.** MATRIX's bridge is already a well-governed **channel**; what W53 must add is the **loop** — a criterion the coordinator owns and holds when it sends
the task; a result the coordinator compares against that criterion rather than against a schema; an evaluation vocabulary with at least one distinct reason per failure
mode the peer can exhibit; a declared delay budget derived from the cycle's own deadline; and the standing to complete the task without the peer's result.

---

## 5. What the sources say about the W53 implementation now in flight

Chain B's receiving side is in the tree; this is where the primary sources judge it.

**Satisfied — the channel is the same channel (Ashby §11/11).** `TaskBridge` builds the ordinary `BridgeReceiver` with the ordinary budget, signer, provenance and audit,
and `RemoteTaskStore` writes the inbox row in state `ACCEPTED` *before* the cognitive path runs. A task cannot take a path the knowledge messages do not take — which is
what a supervisor (Ramadge & Wonham 1989) requires: the plant's own controllers are bounded by a decision made at a single place.

**Satisfied — the transport's variety exceeds the variety of the failure set (Ashby §11/7).** `CognitiveTaskResponse.Status` distinguishes `COMPLETED`, `PARTIAL`,
`REFUSED`, `UNSUPPORTED`, `FAILED`, `TIMED_OUT`, `CANCELLED` and `IN_PROGRESS`, and `refusalReason` is machine-readable. A peer that is slow, absent, unable, partial or
wrong has a distinct state. This is more than Ashby's minimum, and it is the right place for the variety: **in the transport, not in the evaluator.**

**Satisfied — the telltale is present and the response is authenticated (Wiener 1948).** The response is signed and carries `responderId`, `capabilityId`,
`requestPayloadHash`, `provenance`, `durationMillis` and `traceReference`; the capability that ran is resolved through an allow-list (`CognitiveCapability`) with no path
from a wire name to a class or a reflection call. The caller's own request id binds the answer to the request it asked.

**Satisfied — the deadline is declared (Olfati-Saber & Murray 2004; Wiener 1948/1950).** `CognitiveTaskRequest` carries `deadlineMillis` ("measured from the moment the
request was issued") and `budgetMillis`, and the response's `TIMED_OUT` state exists. The loop's delay is now a *parameter of the request* rather than an accident of the
transport.

**Gap 1 — nothing carries the criterion the result will be judged against (Ashby §11/7 and §11/12; MPC; Wiener's "combined with the other information").**
`CognitiveTaskRequest` has `outputContract` and `constraints`, but **no field carrying the coordinator's settled expectation**; `CognitiveTaskResponse.prediction` is the
*capability's own* prediction, not the coordinator's. The binding exists (`requestId`, `taskId`, `requestPayloadHash`), so chain A can match the answer to a prediction it
holds locally — but the contract does not say it must, and without that the answer is a report nobody has judged against anything. *This is the largest gap between the
sources and the code.*

**Gap 2 — there is no evaluation vocabulary, because chain A's half does not exist (Ashby §11/7).** `BridgeRefusalReason` is entirely protocol-level: a forged signature,
an unknown version, an exhausted budget. None of its members says "the peer's answer contradicts the evidence I already hold". The response's status vocabulary is not a
substitute: it reports *what the peer did*, not *whether the result may be used*.

**Gap 3 — the delay numbers still disagree (Wiener's gun).** The request declares a deadline, but the values available at the coordinator are 250 ms of cycle budget and 5
s of publisher timeout. Until the chain A side derives `deadlineMillis` from the cycle's remaining budget, the declared deadline is a number that no component enforces.

**Gap 4 — nothing yet feeds a failed delegation back into the coordinator's parameters (Ashby 1952 §8/9).** `RemoteTaskStore` records the decision and the duration, and
the response reports `durationMillis`; neither reaches `MethodMeasurementStore` or the `AlgorithmSelector`. The "ultrastable pilot joined to the ailerons at random"
property is not yet realised: today a failed delegation would be logged and forgotten.

**Gap 5 — the regulated variable is not named (Cannon 1929; Ashby §13/5).** The request and the response are well-governed, but nothing states what the delegation loop is
*for* — what quantity of the epistemic store it is supposed to keep steady. Ashby §13/5 says finding the constraint is the regulator's only hope; the constraint here is
BRIDGE-5, but the *regulated variable* is unnamed.

---

## 6. Design consequences

Each is phrased so that a **test could confirm it**. None is a proof.

**C1 — The coordinator sends its settled expectation with the task, and the result is compared against it.** The delegation request carries the proposition the
coordinator expects to be supported or refuted (the prediction it already wrote down), and the returned result is compared against that prediction and recorded as an
`Outcome` with a non-null `predictionId` and a non-null `matched`. *Check:* a test that drives a delegation and asserts the `Outcome` chain A records has `comparable() ==
true` (`contract/Outcome.java:95-97`) and that its `predictionId` names the prediction chain A wrote *before* the call. *Currently unmet* — §5, Gap 1.

**C2 — The evaluation refusal vocabulary is at least as large as the peer's failure set.** The coordinator must distinguish, with a **named** reason, at least: result
absent; result present but malformed; result present and self-consistent but contradicting the coordinator's evidence; result consistent but stale (older than the
evidence it is compared with); result carrying a provenance the coordinator cannot weigh; result from a peer whose health is `DEGRADED`. These are the "things the peer
can get wrong" of Ashby §11/7 and Ashby's "rudder that is stuck". *Check:* a test enumerating the failure set, asserting one distinct evaluation reason per member, and
asserting a refusal of each kind appears in the audit trail.

**C3 — The refusal is a decision, not a fallback.** When the coordinator rejects a result, the original task is **still completed** — by the local path — and the
rejection is recorded with its reason in the same audit chain as an acceptance. *Check:* a test supplying a result the coordinator must reject, asserting the cycle's
outcome is `COMPLETED` (not `FAILED`, not `RESOURCE_LIMITED`), that the episode carries the rejection reason, and that the coordinator's own evidence was not modified by
the rejected result.

**C4 — The delay budget is derived from the loop's deadline, and the two current numbers are reconciled.** The delegation's `deadlineMillis` must be **no larger than the
cycle's remaining budget** and must be **named in the task record**. The present 250 ms cycle budget (`CognitiveKernel.java:99`) and 5 s publisher timeout
(`KnowledgePublisher.java:59`) cannot both hold for a delegation inside the critical path. *Check:* a test asserting `request.deadlineMillis <= cycleBudgetMillis -
elapsedAtDispatch`, and a test that a deliberately-slow peer produces a **bounded** `RESOURCE_LIMITED` cycle with a recorded reason — not an unbounded wait, and not a
hunt.

**C5 — The delegated result never arrives as a fact.** It enters the epistemic store as a `CANDIDATE` claim with a `SECONDARY_REPORT` source and the evidence the peer
named — the same three-write discipline as the first direction (`EpistemicBridgeSink.java:83,111,134`), and never as a change to a claim's status, a confidence, a goal,
or a safety policy (`ADR-W51-01-cognitive-runtime-and-bridge.md:41-49`). *Check:* the existing gate rule G9 plus a new assertion that a delegated result cannot raise a
claim's status.

**C6 — The coordination is stigmergic, and the trace carries its standing.** The result crosses as a report carrying the criterion it was measured against and the
standing it has (status, supporting and contradicting evidence counts, origin, instant) — the discipline already implemented for the knowledge direction at
`KnowledgeBridge.java:266-282`. *Check:* a test asserting that a delegated result read by the receiver carries a non-empty standing and a non-null instant, and that a
result without them is refused.

**C7 — A failed delegation changes the coordinator's parameters, not only its log.** A failed delegation must move at least one of: the recorded cost of the delegated
method (`MethodMeasurementStore`), the evidence-request threshold of the next cycle (`neuromodulation/RegulationController.java:246-249`), or the selector's standing for
that method. This is Ashby's "ultrastable pilot joined to the ailerons at random" made mechanical. *Check:* a test running one failing delegation and asserting that a
subsequent cycle's `RegulationBias.evidenceRequestThreshold()` or the method's measured profile differs from the neutral value.

**C8 — The peer's health is a loop parameter, and a degraded peer changes the plan.** A peer reported `DEGRADED` or `DOWN` (`runtime/RuntimeComponent.java:64-74`) must be
treated as a changed graph (Olfati-Saber & Murray 2004), not as a slow graph: the coordinator falls back to the local path immediately and records the peer's reported
health as the reason. *Check:* a test registering the brain runtime as `DEGRADED` in `CognitiveRuntime` and asserting that a delegation is **not attempted** and that the
cycle completes on the local path with the health named.

---

## 7. Rejected or deferred alternatives

**R1 — Make the delegated result authoritative (a conclusion chain A adopts).** Rejected. It contradicts BRIDGE-5 and ADR-W51-01 §4.3
(`ADR-W51-01-cognitive-runtime-and-bridge.md:125-131`), and structurally it collapses the coordinator's variety into the peer's: the law of requisite variety (§11/7) says
the regulator's own variety is what bounds the outcome's variety, so a coordinator that adopts the peer's answer has no regulator left. It would also destroy the audit
property that the epistemic database is byte-identical before and after a chain B run.

**R2 — Centralise: let chain A write into chain B's database, or chain B into chain A's.** Rejected. It destroys the single-writer property (`ADR-W51-01:30-39`) and the
isolation gate's rules G1–G9, and it converts a bounded disturbance variety (three permitted write kinds, one channel, one budget) into an unbounded one. Ashby §13/2 says
the difficulty of regulating a large system is not its size — but crossing a writer boundary removes the very constraint that makes it regulable.

**R3 — Synchronous delegation inside the cycle's critical path, with the publisher's present 5-second timeout.** Deferred, not rejected — but **not shippable as it
stands**. Wiener's gun (§3.4): a correction loop with lag and too much gain hunts, and the cycle budget is 250 ms. The deferral is conditional on C4 and C8 landing first.

**R4 — Asynchronous, fire-and-forget delegation.** Rejected. Without a closing comparison there is no criterion, so nothing can be evaluated, and the mandate's "evaluates
that result against its own evidence" is not met. In Ashby's terms there is no regulator, only an announcement.

**R5 — Let the peer certify its own answer.** Rejected. The criterion function would be applied by the actuator it is supposed to regulate, and the loop would close on
the peer's report rather than on the coordinator's evidence. The peer may *state* its confidence — `confidenceBasis` in `CognitiveTaskResponse` is the right way to carry
that — but the coordinator must *decide*.

**R6 — Implement the task as a bespoke envelope format with a new protocol.** Rejected in favour of what the tree now does: two new rights inside the W52 contract
(`BridgeCapability.INFERENCE_REQUEST` / `.INFERENCE_RESPONSE`), the same envelope, signature, budget, audit and version rule; a second protocol would create a second
governance.

---

## 8. Open questions for the coordinator

1. **What is the regulated variable of the delegation loop?** The proposal here is the *integrity of the epistemic store*: the number of `CANDIDATE` claims introduced per
  episode, and the ratio of delegated results that survive evaluation to those rejected. Both are measurable with what the tree already writes
  (`EpistemicBridgeSink.java:88-104`), but the choice is the coordinator's, and it is a governance question rather than a technical one.
2. **Who owns the criterion for a delegated task** — the delegating agent, a task template stored with the goal, or a policy in the epistemic store? The three give
  different answers to "who is right when the coordinator and the peer disagree about what was asked".
3. **Is one delegation per cycle a budget rule or a policy?** With the 250 ms cycle budget a second delegation may not fit; with a receding horizon it may. This must be
  decided before the delay budget (C4) can be named.
4. **What is the escalation path when the peer is `DEGRADED` rather than `DOWN`?** C8 proposes immediate local fallback; the alternative — one attempt, to see whether the
  degraded peer can still answer within the deadline — trades latency for coverage.
5. **Does a failed delegation enter the regulatory state?** The drives are currently updated from prediction error and novelty only (`CognitiveKernel.java:717-753`); a
  failed delegation has no path into the regulator. C7 proposes that it should.
6. **Should the peer's own reliability enter the choice of what to delegate?** `MethodMeasurementStore` holds the per-method profile, but the profile's *reliability* is
  reported and not yet scored (`docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md:249-251`). Delegation to a peer is exactly the case where the peer's reliability should matter — and
  there is no measurement of it yet.

---

## 9. The sources

1. **Ashby, W. R. (1952).** *Design for a Brain; the Origin of Adaptive Behaviour.* London: Methuen; New York: Wiley, 1954 (the printing verified here). Chapters cited: 8
  ("The Ultrastable System"), §8/4–8/5 (pp. 90–91), §8/9 (pp. 98–99); appendix ch. 20 ("Stability"), §20/10 (p. 218). Scan:
  <https://archive.org/details/designforbrain00ashb>.
2. **Ashby, W. R. (1956).** *An Introduction to Cybernetics.* New York: John Wiley & Sons (parallel London issue: Methuen / Chapman & Hall, 1956). Sections cited: §11/5
  (p. 205), §11/7 (p. 206), §11/11 (p. 210), §11/12 (p. 210), §11/13 (p. 212), §11/15 (p. 214), §11/16–11/19 (pp. 215–217), §12/2 (p. 219), ch. 13 §13/1 (pp. 243–244),
  §13/2 (p. 244), §13/5 (p. 246). Scan: <https://archive.org/details/introductiontocy00ashb>. *Note on pagination:* the section numbers are of the Wiley (14-chapter)
  printing; the parallel London issue of the same year divides and paginates differently.
3. **Cannon, W. B. (1929).** "Organization for physiological homeostasis." *Physiological Reviews* 9(3): 399–431. <https://doi.org/10.1152/physrev.1929.9.3.399>.
4. **Camacho, E. F. & Bordons, C. (2007).** *Model Predictive Control* (2nd ed.). Advanced Textbooks in Control and Signal Processing. London: Springer.
  <https://doi.org/10.1007/978-0-85729-398-5>.
5. **Cho, K.-H. & Lim, J.-T. (1999).** "Mixed centralized/decentralized supervisory control of discrete event dynamic systems." *Automatica* 35(1): 121–128.
  <https://doi.org/10.1016/S0005-1098(98)00137-X>.
6. **Grassé, P.-P. (1959).** "La reconstruction du nid et les coordinations inter-individuelles chez *Bellicositermes natalensis* et *Cubitermes* sp. La théorie de la
  stigmergie: essai d'interprétation du comportement des termites constructeurs." *Insectes Sociaux* 6(1): 41–80. <https://doi.org/10.1007/BF02223791>.
7. **Heylighen, F. (2016).** "Stigmergy as a universal coordination mechanism I: Definition and components." *Cognitive Systems Research* 38: 4–13.
  <https://doi.org/10.1016/j.cogsys.2015.12.002>.
8. **Olfati-Saber, R., Fax, J. A., Murray, R. M., Taylor, A., Timings-Bott, S., Hutchins, J., Nagle, J. & Cortés, A. (2007).** "Consensus and cooperation in networked
  multi-agent systems." *Proceedings of the IEEE* 95(1): 215–233. <https://doi.org/10.1109/JPROC.2006.887293>.
9. **Olfati-Saber, R. & Murray, R. M. (2004).** "Consensus problems in networks of agents with switching topology and time-delays." *IEEE Transactions on Automatic
  Control* 49(9): 1520–1533. <https://doi.org/10.1109/TAC.2004.834113>.
10. **Ramadge, P. J. G. & Wonham, W. M. (1989).** "The control of discrete event systems." *Proceedings of the IEEE* 77(1): 81–98. <https://doi.org/10.1109/5.21072>.
11. **Wiener, N. (1948).** *Cybernetics: or Control and Communication in the Animal and the Machine*. Cambridge, MA: MIT Press. (2019 reissue with the 1961 supplementary
  chapters and Sanjoy K. Mitter's foreword: <https://doi.org/10.7551/mitpress/11810.001.0001>.) Chapters cited: "Introduction", pp. 11–13; ch. IV ("Feedback and
  Oscillation"), pp. 129–145.
12. **Wiener, N. (1950).** *The Human Use of Human Beings*. Boston: The MIT Press. Second, revised edition 1954. Sections cited: "Multipliers", pp. 13–14; "Rigidity and
  Learning", p. 71. Scan: <https://archive.org/details/the-human-use-of-human-beings>.

**Repository sources cited by this document** (not primary literature, but the local facts the argument rests on):
`docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md`, `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md`,
`docs-v2/engineering/W51-ARCHITECTURE-MAP.md`, `docs-v2/engineering/W52-STATUS.md`, `docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md`, and the sources under
`matrix-core/src/main/java/io/matrix/{bridge,contract/bridge,runtime,cognitive}` and `matrix-api-gateway/src/main/java/io/matrix/api/bridge`.
