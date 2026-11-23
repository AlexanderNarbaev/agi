# Source disclosure authorization request

Status: PENDING. The owner must approve the exact source payload below to
MiniMax model MiniMax-M3.1-Flash at https://api.minimax.io/anthropic for
MATRIX-CRJ01-INTEGRITY-2026-11-22. No other recipient or fallback provider.
The synthetic preflight and source-free kickoff have a separate payload.

Proposed first tranche, tied to base 0549d27ee561afdf94214cbabe661c2532e08bae
or the recorded candidate diff for those exact paths:

- matrix-core/src/test/java/io/matrix/crj/runner/Crj01Metrics.java
- matrix-core/src/test/java/io/matrix/crj/runner/Crj01MetricsTest.java
- matrix-core/src/test/java/io/matrix/crj/runner/Crj01EpistemicMetricsControlTest.java
- matrix-core/src/test/java/io/matrix/crj/runner/Crj01ArmRunner.java
- matrix-core/src/test/java/io/matrix/crj/runner/Crj01ProvenanceTest.java
- matrix-core/src/test/java/io/matrix/crj/runner/Crj01ProspectiveProbeTest.java
- matrix-core/src/test/java/io/matrix/crj/evidence/SourceSpanOracle.java
- matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java
- matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentImporter.java

Populate the outbound staging tree with verified exact paths and a SHA-256
manifest. Scan the staged bytes for secrets and private material, including
deferred tool output. Exclude .git, home, service configs, credentials, personal
information, private DBs, proprietary datasets, sealed questions and their gold,
unrelated files, raw internal traces, and repository-wide search. Run Gradle and
Git through local fixed commands or a restricted runner that returns only
approved, filtered evidence. Writable scope is test-tree task files in the
isolated branch and scratch evidence, never the owner checkout.

Verify the model/provider from observed session metadata, API destination, key
scope and no-fallback behavior. Inject the provider credential by environment
reference in the provider process. Do not propagate it to execution tools.
Keep local logs access-controlled and disable both session upload paths. Require
enforced read boundary and no tool egress before providing any source. A prompt
constraint, a read-only write policy, or a sparse Git checkout with shared objects
does not independently enforce that boundary. Record exact staged file digests,
approval, task, observed reads and recipient. Any scope expansion requires a
fresh owner approval.

PROPOSED-PAYLOAD.sha256 pins the exact bytes of the proposed tranche. No
source file was copied into the preflight workspace or sent externally.
