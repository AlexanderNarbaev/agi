# Independent candidate evaluation of de3cb24caa249d8d70b2e02415cff59b5492c2da

All seven JUnit groups passed with the unchanged v2 evaluator. The primary two-workspace served scenario and all ten reliability controls were exercised in synthetic stores. The served call was an in-process invocation of ArtifactReportResource. The run retained 65 import, selection, served-response and diagnostic records.

The unchanged post-run provenance audit verified 12 REFUTED responses and seven UNSUPPORTED responses. One refutation correctly distinguished the canonical reasoning claim from the distinct local observation claim. The audit independently resolved observation claim and source identities, digest and literal sentence offsets, and verified that each unsupported response had an empty refuting observation list.

Gradle took 23.14 seconds wall time, 91.15 seconds user CPU, 3.33 seconds system CPU and 1,746,012 KiB peak RSS. The evaluator used zero provider requests or tokens. Earlier evidence directories remain intact.
