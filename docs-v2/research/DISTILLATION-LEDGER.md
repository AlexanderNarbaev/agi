# DISTILLATION-LEDGER.md

> Date: 2026-09-27
> Version: 1.0
> Status: Distillation pipeline integrated (RECON-W5)

## What This Ledger Records

Every distillation run produces an entry with:
- sourceId: identifier for the source (model or dataset)
- datasetOrPattern: dataset name (BoolQ/LogiQA/CLUTRR) or teacher pattern (and/or/xor/...)
- inputBits: Article II guard K_MAX <= 20
- samplesUsed: number of (input, activation) pairs captured
- hdcPromoted: number of HDC entries promoted
- birClausesSynthesized: number of BIR clauses emitted by Distiller
- tsetlinLiterals: number of Tsetlin literals (placeholder for W6)
- fidelity: held-out fidelity score (0..1)
- durationMs: distillation wall time
- timestampMs: epoch millis
- artifactHash: SHA-256 of source + counts + duration (for audit)

## Super-Additivity Proof (RECON-W5 Step 2)

> Reproducible command:
> java -cp <classpath> SuperAdditivityDemo

The distillation pipeline demonstrates structural super-additivity:
running source-A alone yields a registry that can answer A-pattern queries;
running source-B alone yields a registry that can answer B-pattern queries;
running A+B yields a registry that can answer both.

| Source   | Pattern | inputBits | samples | fidelity | durationMs | registry size |
|----------|---------|-----------|---------|----------|-------------|----------------|
| source-A | and     | 8         | 16      | 1.0000   | 17          | 1              |
| source-B | or      | 8         | 16      | 1.0000   | 0           | (after A+B) 2  |

score(A+B) >= max(score(A), score(B))  [STRUCTURAL]

The super-additivity is structural (set inclusion) rather than numerical
(each Bir is independently queryable via BirRegistryBridge.tryInfer), but
Article III reproducibility is preserved: same seed + same source =>
same provenance + same Bir content.

## Article VIII Engine Markers (Every Run Emits)

- engine=Distiller.synthesize: clause synthesis via core Distiller
- engine=BirRegistry.register: Bir registration with provenance lineage hash
- engine=DistillationLedger.record: ledger entry written to NDJSON
- engine=DatasetConnectorV2.generateSamples: local dataset sampling (BoolQ/LogiQA/CLUTTR)

## Sample Entries (Run on 2026-09-27)

```
{"sourceId":"source-A","datasetOrPattern":"and","inputBits":8,"samplesUsed":16,"hdcPromoted":0,"birClausesSynthesized":1,"tsetlinLiterals":0,"fidelity":1.0000,"durationMs":17,"timestampMs":1790501351836,"artifactHash":"8b697655"}
{"sourceId":"source-B","datasetOrPattern":"or","inputBits":8,"samplesUsed":16,"hdcPromoted":0,"birClausesSynthesized":1,"tsetlinLiterals":0,"fidelity":1.0000,"durationMs":0,"timestampMs":1790501351837,"artifactHash":"b1420cf5"}
```

## Honest Limitations

1. No real ONNX model distillation. The pipeline uses synthetic teachers
   (and/or/xor/imply/transitive) and local dataset generation via
   DatasetConnectorV2. Real ONNX integration requires user consent for
   downloading a tiny public model (per Q-3 default), and is queued as a
   follow-up commit.
2. No rollback removal. DistillationMerge.rollback() is a soft-rollback
   (caller is responsible for not adding new rules after rollback). A full
   BirRegistry.remove(ruleId) method is an Article VII RFC.
3. Distilled clauses may overlap with rules already in the registry;
   CONSISTENCY_CHECKER rejects duplicates by provenance but does not
   detect semantically equivalent rules. Article VII RFC for semantic
   contradiction detection.
4. Super-additivity is structural, not numerical. The ledger records
   fidelity scores but a real numerical super-additivity study (A vs A+B
   score delta on a held-out query set) is queued for W9.

## Next Steps

- W5 continued: real ONNX integration (requires user consent)
- W5 continued: super-additivity numerical proof (W9)
- W5 continued: federation-aware distillation (W7)
