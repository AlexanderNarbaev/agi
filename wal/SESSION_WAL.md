📍 v3.25 — Wave 2 COMPLETE (review fixes applied). Phase A: 5/5 DONE. 3 commits pushed.
🚀 Active: Wave 3 NEXT
🛑 Protected: Pekko 1.6.0, K_MAX=20, FROZEN-neurons, Quarkus 3.36.1, Java 25, AGPLv3+ethics, 82% coverage floor

## Session Summary — 4 commits pushed to origin+gitverse
1. 6d8d8d7 docs: Wave 1 — sync WAL, Quarkus 3.35.4→3.36.1, INDEX.md +11 docs
2. 6d145f3 feat: BooleanSchemaValidator A5 — SchemaDescriptor + TruthTable (28 tests)
3. 1d68d1e feat: Integrate SchemaDescriptor with BrcChain — outputSchema validation
4. 2afb67e fix: Guard review — weighted skip, BrcChain limit, AgentBrainService.validateSchema, import restore

## Guard Review Fixes (2afb67e)
- ✅ Weighted evaluate(): schema validation only runs when weights==null (index mismatch)
- ✅ BrcChain.validateOutput(): capped to outputSchema.k() 
- ✅ NeuralTextGenerator import restored (regression from edit)
- ✅ AgentBrainService.validateSchema(): traverses layers → validates all TT schemas

## Phase A Research Synthesis — 5/5 COMPLETE (100%)
A1 ExactTermGuard ✅ | A2 AgentResponse ✅ | A3 ParetoFitness ✅ | A4 Knee-Point RRF ✅ | A5 BooleanSchemaValidator ✅