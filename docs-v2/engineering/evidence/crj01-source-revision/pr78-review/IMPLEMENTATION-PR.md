## Source revision applicability

Add a workspace-local, append-only SQLite source selection history. The Chain A report uses a pinned current or explicit historical document view. Procedure selection, forced use, and replay consult applicable evidence. Typed premise refutations distinguish the canonical reasoning claim from the applicable source observation and its local provenance.

## Verification

- Review head: `f68ec498a6a6a50a34eecf0653a64385ed9d6c78`.
- 43 targeted local tests passed, including synthetic schema-21 upgrade, restart, snapshot compatibility, report regressions, and invalid observation spans.
- The preceding candidate passed all seven independent evaluator groups and the typed provenance audit. Exact-head re-run is in progress.
- The migration is additive and does not backfill lineage. Historical views override one document revision while other documents stay current.
- No evaluator fixtures or gold are included. No live database was opened.

Independent AI source review accepted the exact head contingent on the sealed run and required GitHub checks. The extra regression checks historical trace exclusion in both learners.
