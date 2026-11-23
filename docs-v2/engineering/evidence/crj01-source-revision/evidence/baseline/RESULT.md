# Source revision applicability baseline

Evaluated develop commit 3fcbfdc10ed32d6c7a5bc8856f309a2b55f4babe. Gradle compiled and ran one targeted JUnit test. The final applicability requirement failed on status and both refuting identities. The check used synthetic files and isolated storage only.

Both workspaces initially returned REFUTED with distinct claim and source identities validated against literal source bytes, import receipts, persisted source records and evidence links. A repeated V1 import in workspace A retained one source record and the original claim identity. Importing different bytes under the same name in A retained both versions and evidence links. Workspace B remained on V1. Both workspaces continued returning their own V1 refutation on fresh calls and after restart.

The application used standard additive import. The evaluator designated V2 independently, but the baseline journey had no way to receive an explicit V1 to V2 applicability instruction. Its observed use of V1 establishes the missing selection behavior. Automatic supersession by filename would violate the required control. Under explicit applicability, V2 would yield UNSUPPORTED because it contains no proof or refutation of the tested premise.

The test measured source and claim retention, evidence linkage and current route behavior. The historical selector, persisted supersession lineage, learned procedure dependencies, cached outputs and interrupted updates require separate evaluation.

Gradle elapsed time was 21.51 seconds, with 91.00 seconds user CPU, 3.31 seconds system CPU and 1,766,012 KiB peak RSS. Provider usage was zero. The local trace contains the synthetic requests and their typed responses.
