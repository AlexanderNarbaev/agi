# Independent candidate evaluation of f68ec498a6a6a50a34eecf0653a64385ed9d6c78

All seven JUnit groups passed with the unchanged v2 evaluator, including the two-workspace synthetic served scenario and all ten reliability controls. The served call was an in-process invocation of ArtifactReportResource. The results contain 65 import, selection, served-response and diagnostic records.

The unchanged post-run provenance audit verified every one of the 12 REFUTED responses and seven UNSUPPORTED responses. One refutation correctly distinguished the canonical reasoning claim from the distinct local observation claim. The audit independently resolved observation claim and source identities, digest and literal sentence offsets. Each unsupported response had an empty refuting observation list.

Gradle took 23.41 seconds wall time, 89.77 seconds user CPU, 3.50 seconds system CPU and 2,053,860 KiB peak RSS. The evaluator used zero provider requests and tokens. Earlier evidence directories remain intact.
