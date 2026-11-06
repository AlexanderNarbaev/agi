# W47 — THE END-OF-WAVE QUESTIONNAIRE

**Instrument:** the harness's structured questionnaire tool (`ask_user_question`) — the real
one, not a list of questions printed in a document. **Date:** 2026-11-05 · **Wave:** W47
**The answers are persisted** in `docs-v2/governance/DECISION-REGISTER.md` (the new
decisions), `docs-v2/governance/OPEN-QUESTIONS.md` (the closed questions) and
`docs-v2/engineering/W47-STATUS.md` §21.

**What is NOT asked here:** anything OD-001…OD-038 already answers, and anything that does
not need the owner. Six questions, each one with a decision behind it that the wave could
not make on its own.

---

## Q-W47-01 — The snapshot's bound

- **the question:** the snapshot's payload is still unbounded, and the observation tables
  inside the database still grow. How should the database's own growth be bounded?
- **why it matters:** a database that grows without bound is a disk that fills up, and the
  snapshot is the only recoverable copy of the workspace. The retention of 2026-11-05 (OD-035)
  governs the BACKUPS; it says nothing about what the live database keeps.
- **the current evidence:** the W45.2 residual, recorded in the contract's §10; the tables
  that grow are the observation tables and the snapshot payloads; the retention tool
  (`scripts/backup-retention.py`) already knows how to classify and prune a set of files by
  age, and it refuses to delete the last valid copy.
- **the recommended option:** a bound on the payload SIZE per snapshot, with the behaviour
  when the bound is exceeded being an explicit refusal (the snapshot is not written, the
  caller is told), plus a retention over the snapshot rows themselves — the same three-tier
  calendar, applied to the rows rather than to the files.
- **the alternatives:** (a) no bound, and the operator prunes by hand — honest, but the
  operator has to know the tables; (b) a bound on the number of snapshots per workspace;
  (c) a bound on the age of the observation rows — the most destructive, and it touches the
  epistemic history that OD-025 made durable.
- **the consequences:** the recommended option makes the refusal explicit and observable; (c)
  would destroy adjudication history, which the owner's own decisions protect.
- **the risks:** an explicit refusal can fail a write that used to succeed — that is the
  point, but it is a behaviour change on a serving path.
- **reversibility:** high — a bound is a configuration value.
- **does it block implementation:** no — W47 is complete without it; it is the first task
  of W48.
- **the related ids:** R-W46-18, the W45.2 residual, TASK-W46-17 (the half that is done).

## Q-W47-02 — The two refused package names

- **the question:** two of the four mismatched skill packages were refused in W47 because
  fixing them would touch something that is not the project's: `matt-pocock` (an owner-owned
  container in `.opencode/**` with seventeen sub-skills and no root manifest) and
  `plan.retired-backup` (a retired, untracked asset of the opencode-swarm plugin, whose
  declared name `plan` collides with a live `plan`). What should happen to them?
- **why it matters:** a package whose declared name differs from its directory is loaded
  under a name that does not correspond to its path; and `plan.retired-backup` claims the
  name of a live package.
- **the current evidence:** the identity census over all 137 packages went from three
  mismatches to one (the `plan` collision); the refusals and their reasons are recorded in
  OD-034; the harness's own rule forbids a dot in a skill name, so `plan.retired-backup`
  could not be loaded under that name even if the directory were kept.
- **the recommended option:** leave both exactly as they are, and record them as
  known-and-refused — the collision is with a RETIRED asset, and the container's meaning
  belongs to the owner.
- **the alternatives:** (a) add a root `SKILL.md` to `matt-pocock` — that turns a set of
  paths into a skill and changes what the container means; (b) rename the plugin's retired
  asset to `plan-retired-backup` — it is another bundle's file, it is untracked, and the
  plugin reinstalls it; (c) delete the retired asset — it is the plugin's to manage.
- **the consequences:** the recommended option leaves one known collision, documented and
  harmless in practice; the alternatives touch owner state or another project's bundle.
- **the risks:** if the plugin ever reinstalls `plan.retired-backup`, the collision returns
  — the refusal does not prevent that, it only records it.
- **reversibility:** high — nothing is changed.
- **does it block implementation:** no.
- **the related ids:** OD-034, TASK-W46-22, Q-W46-23.

## Q-W47-03 — The full `matrix-core` suite

- **the question:** the full `matrix-core` suite is still not run, because `AGENTS.md`
  reserves it for the owner's approval. Do you approve one run, and with what resource
  envelope?
- **why it matters:** until it runs, the project's total test population is unknown, and
  every number this project reports is a number about a SCOPE, not about the suite (the
  residual R-W46-10).
- **the current evidence:** the machine has 32 threads, 59 GiB of RAM (41 GiB available at
  the probe) and 30 GiB of tmpfs; the W46 hardware report measured the envelope at one test
  JVM at a time against ~38.7 GiB available; the targeted scopes of W47 ran green:
  269 classes / 2060 executions in matrix-core, 120 / 918 in the brain runtime, 43 / 240 in
  the gateway.
- **the recommended option:** one run, one test JVM at a time, with the heap capped at
  8 GiB and the other Gradle daemons stopped — on a copy of the working tree, not on the
  live databases.
- **the alternatives:** (a) no full run — the residual stays open; (b) a full run with the
  default heap — the OOM risk the rule exists to prevent; (c) a full run on a schedule.
- **the consequences:** the recommended option closes R-W46-10 for the current commit and
  gives the next wave a denominator instead of a set of scopes.
- **the risks:** a full run takes a long time and can still fail for reasons unrelated to
  the code (the race the register records); the result must be reported as it comes out.
- **reversibility:** high — nothing is written by a test run except the build directory.
- **does it block implementation:** no — it blocks a stronger claim.
- **the related ids:** R-W46-10, R-W46-12, TASK-W46-11.

## Q-W47-04 — The restore drill

- **the question:** the recovery runbook's §5 — a restore performed against a copy of the
  live database — has never been executed. Should W48 rehearse it?
- **why it matters:** a procedure nobody has run is a procedure nobody has tested. The
  runbook says what to type; it does not say that the typing works.
- **the current evidence:** `docs-v2/operations/RECOVERY-RUNBOOK.md` §5 states plainly that
  the drill is owed; the backup path and the retention are implemented and self-tested
  (25 checks); the restore path itself — `KnowledgeStore`'s snapshot restore — is covered
  by tests against temporary databases, but not by an operator-level drill.
- **the recommended option:** yes — a drill against a COPY of `data/knowledge/workbench.sqlite`
  in a temporary directory, with the elapsed time and the observed result written into the
  runbook. The live database is not touched, and nothing is restored over it.
- **the alternatives:** (a) no drill — the runbook stays untested; (b) a drill against the
  live database — refused: the directive forbids it and the owner has never asked for it.
- **the consequences:** the recommended option turns the runbook's §5 from an intention into
  a measurement, and it is the only way the recovery claim can be VERIFIED rather than
  IMPLEMENTED.
- **the risks:** a drill that writes into the live file by accident — which is why the drill
  must copy first, and why the writer lock must be held.
- **reversibility:** high — the copy is discarded.
- **does it block implementation:** no.
- **the related ids:** R-W46-02, TASK-W46-17, OD-029, OD-035.

## Q-W47-05 — The GPU benchmark

- **the question:** the 12.6× speed-up in the hardware report came from an ad-hoc probe that
  was never preserved. Should a committed benchmark be written, and at what cost?
- **why it matters:** a number nobody can re-run is not a measurement, and the registry row
  now says exactly that. The GPU path also cannot claim to be a capability until the
  benchmark exists.
- **the current evidence:** the hardware is an RTX 5070 Ti (12,227 MiB, driver 595.99.02);
  the ONNX Runtime 1.29.0 GPU build is on the classpath; the provider registers only with
  `LD_LIBRARY_PATH` exported, and the diagnostic now reports seven observed states; the
  historical figures are 191.367 ms on the CPU against 15.190 ms on CUDA, for one model,
  sequence length 32, ten iterations after one warm-up.
- **the recommended option:** yes — a committed benchmark script, the same model, the
  sequence length and the iteration count recorded in the script itself, a warm-up, a median
  with the dispersion, and the CPU and CUDA cases run under comparable conditions. The
  resource cost is one GPU session, not a sustained load.
- **the alternatives:** (a) no benchmark — the row keeps its "PROPOSED" condition, and the
  claim stays historical; (b) a longer run with a larger model — a bigger claim and a
  bigger risk of the owner's VRAM.
- **the consequences:** the recommended option makes the GPU row MEASURED or removes the
  claim, and either outcome is an improvement over "the report says so".
- **the risks:** a benchmark that runs while another heavy build is running would measure
  the contention instead of the GPU; the rule stays: one heavy thing at a time.
- **reversibility:** high.
- **does it block implementation:** no.
- **the related ids:** OD-028, TASK-W46-9, the `W46-GPU` registry row.

## Q-W47-06 — The curiosity drive's budget

- **the question:** the gateway's curiosity impulse is fired with an envelope of 50, and the
  scheduler's cost for `CURIOSITY` is 100, so the budget refuses it every time. Should the
  envelope be retuned, or should the drive stay refused?
- **why it matters:** the null-scheduler fix made the drive reach the budget and the frozen
  gate — which is what the fix was for. It did not make the drive fire. An impulse that is
  always refused is not a drive; it is a request that is declined on every cycle.
- **the current evidence:** `ImpulseScheduler.costFor(CURIOSITY) = 100`
  (`ImpulseScheduler.java:105`) against `cost > envelope` (`:83`); the idle drive (envelope
  100) and the integrity drive (envelope 25, cost 10) pass; SPEC-015 records "a budget
  deficit means delay, not starvation" as a PROPOSED item.
- **the recommended option:** the envelope is raised to the cost — 100 — so that the drive
  can be paid for, and the refusal path stays for the drives that should be refused. This
  is a one-line change in a serving path, and it changes what the gateway DOES.
- **the alternatives:** (a) leave it — the drive is expressed, refused, and counted, and the
  behaviour does not change; (b) raise the envelope to a value the owner chooses — the
  conjugate budget is the resource discipline of the system, so the number is the owner's;
  (c) lower the cost of the curiosity impulse — a change in the scheduler's pricing, which
  affects every consumer.
- **the consequences:** the recommended option makes the gateway's curiosity cycle spend
  budget, which is observable in the budgeter; (a) leaves a drive that can never fire.
- **the risks:** an envelope that is too large lets curiosity crowd out the other drives —
  which is exactly what the conjugate budget exists to prevent, and why the number is the
  owner's.
- **reversibility:** high — one constant.
- **does it block implementation:** no.
- **the related ids:** OD-038, TASK-W46-25, SPEC-015's I-entries.
