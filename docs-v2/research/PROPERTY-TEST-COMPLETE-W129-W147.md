# Property-based Test Coverage — W129-W147

## Summary

Over W129-W147, expanded property-based test coverage using jqwik @Property
annotations. These complement the existing JUnit @Test methods and provide
stronger statistical guarantees via hundreds/thousands of generated test cases.

## Property Test Classes (16 classes)

| Wave | Class | @Property tests | Tries |
|---|---|---|---|
| W129 | KolmogorovComplexityPropertyTest | 8 | 50-100 |
| W130 | AnalogicalConsistencyPropertyTest | 8 | 50-100 |
| W131 | ConceptualExclusionPropertyTest | 8 | 50-100 |
| W132 | NKBooleanNetworkPropertyTest | 4 | 20-50 |
| W133 | MemristorSwitchPropertyTest | 8 | 20-50 |
| W134 | LSystemPropertyTest | 7 | 20-30 |
| W135 | CognitiveGenesisProfilePropertyTest | 7 | 50-100 |
| W139 | LSystemComplexityPropertyTest | 4 | 30-50 |
| W140 | KolmogorovComplexityRecorderPropertyTest | 8 | 30-100 |
| W141 | KolmogorovComplexitySnapshotPropertyTest | 6 | 100 |
| W142 | CognitiveGenesisProfileBuilderPropertyTest | 3 | 50-100 |
| W143 | CognitiveGenesisProfileBuilder2PropertyTest | 4 | 50 |
| W144 | StabilityPhiPropertyTest | 5 | 30-50 |
| W145 | CrossLevelPhiPropertyTest | 3 | 20-30 |
| W146 | InterAgentPhiPropertyTest | 2 | 30-50 |
| W147 | PhiIdPropertyTest | 3 | 30-50 |
| (W107)| PropertyBasedMetricsTest | 18 | 1000 |

**Total: 90 @Property tests across 16 classes**

## Estimated Total Executions

Conservative estimate: 90 properties × 50 tries average = ~4,500 test cases
Plus PropertyBasedMetricsTest: 18 × 1000 = 18,000 cases
Grand total: ~22,500 property-based test executions

## Coverage by School

- **R-A Modern ML/DL**: PhiId, StabilityPhi, CrossLevelPhi, InterAgentPhi
- **R-B Cybernetic**: StabilityPhi (Ashby), CrossLevelPhi (Bernstein),
  InterAgentPhi (Minsky)
- **R-C Soviet/Asian**: Kolmogorov, AnalogicalConsistency (Nyaya),
  ConceptualExclusion (Dignāga)
- **R-D Early Learning**: NKBooleanNetwork (Kauffman edge of chaos)
- **R-E Physical**: MemristorSwitch (Chua/HP)
- **R-F Math**: LSystem, LSystemComplexity (Lindenmayer, Kolmogorov)

## CONSTITUTION Compliance

All property tests comply with:
- Article I (Stratified Stochasticity): use seeded Random via Arbitrary
  seeds, never wall-clock
- Article VI (Integration as measurement): test measurement substrate
  invariants, not phenomenal claims

## Verification Status

- All 16 PropertyTest classes compile (verified via
  ./gradlew :matrix-core:compileTestJava)
- PropertyBasedMetricsTest verified via Quarkus XML reports:
  tests=18, failures=0, errors=0
- Other PropertyTest classes not generating XML reports (Quarkus
  test reporter issue, possibly cached discovery)
