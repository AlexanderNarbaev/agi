# W41 Research Stream A — Learning Science for Defensible Capability Measurement and Teach-Back Validity

**Wave:** MATRIX RECON-W41 (sub-stream A)
**Scope:** the learning science that governs (a) how capability may be measured and (b) how a teach-back may be validated; plus one falsifiable engineering hypothesis implementable in the Learning Workbench.
**Status of this document:** research input for W41.4/W41.5. It proposes; it does not implement.

---

## 0. Method, and how the citations in this document were verified

META-R3 forbids aggregator sources for significant claims. Every source below is a **book, peer-reviewed paper, or official document**. For each one I record how the bibliographic record was checked:

| Verification token | Meaning |
|---|---|
| `[XREF]` | DOI record retrieved from the Crossref REST API (`api.crossref.org/works/{DOI}`) and the title/author/venue/volume/page matched the citation. |
| `[PMID]` | Record retrieved from NCBI E-utilities `esummary`/`efetch`; title, authors, journal, volume, issue, pages, year, PMID and (where present) DOI matched, and the abstract was read directly. |
| `[OA]` | Record retrieved from the OpenAlex API; metadata matched. |
| `[FETCH]` | The document itself was retrieved and read at the given URL (ERIC record, AHRQ toolkit page, archive landing page). |
| `[UNVERIFIED]` | The claim or identifier could **not** be confirmed. Stated explicitly, never silently dropped. |

**Negative result worth recording:** six DOIs that are widely reproduced in secondary literature did **not** resolve to the papers usually attributed to them, and four other candidate DOIs did not resolve at all:

| Candidate DOI | Actual Crossref record |
|---|---|
| `10.1126/science.225.4661.463` | Abelson, "Indian Development Trends", *Science* 225(4661):463 — **not** Posner et al. 1982 |
| `10.1207/s15516709cog1702_2` | Airenti et al., "Conversation and Behavior Games…", *Cognitive Science* 17(2) — **not** diSessa 1993 |
| `10.1177/002248718703800604` | Thompson Manning, "Historical Lessons for Teacher Education" — **not** Guskey 1987 |
| `10.1186/s13643-016-0214-8` | Alderdice et al., BRIEF randomised trial, *Systematic Reviews* 5:40 — **not** Dinh et al. 2016 |
| `10.3102/0013189X12441288` | does not resolve (Day & Blunt 2012) — see §11 |
| `10.1080/09500690500338538` | does not resolve (Treagust 2006) — see §11 |
| `10.1037/0278-7393.26.1.69` | does not resolve (Slamecka & Graf 1978) — see §11 |

The correct identifiers that *were* confirmed are used below. Where the classic citation could not be confirmed, it is marked `[UNVERIFIED]` rather than repaired by guesswork.

**Repository constraint compliance:** per the task, **no repository source file was opened** for this study. Integration points are named only from the source-tree listing of `matrix-core/src/main/java/io/matrix/knowledge/` (`KnowledgeWorkbench`, `MasteryCriteria`, `LearningStage`, `LearningStateEvaluator`, `LearningState`, `LearningDimension`, `TeachBackEvaluator`, `SyntheticLearner`, `TeachingComposer`, `TeachingPlan`, `TeachingStep`, `LearnedRuleRegistrar`, `LearningWorkbenchResource`). No behavioural claim about their internals is made here beyond the W40 audit findings quoted in §8.

---

## 1. Mastery learning: what evidence actually justifies "competent"

**Bloom's own framing.** Bloom's 1968 *Evaluation Comment* states the thesis directly: *"A basic task is to determine what is meant by mastery of the subject and to search for methods and materials which will enable the largest proportion of students to attain such mastery."* It also lists the five variables any mastery strategy must handle: aptitude-for-learning (time to mastery), quality of instruction, ability to understand instruction, perseverance, and time allowed for learning — and notes that *"Most students, perhaps over 90 percent, can master what teachers have to teach them."* `[FETCH: https://eric.ed.gov/?id=ED053419]`
→ Bloom, B. S. (1968). *Learning for Mastery. Instruction and Curriculum.* Regional Education Laboratory for the Carolinas and Virginia, Topical Papers and Reprints, No. 1. ERIC **ED053419**. Stable URL: https://eric.ed.gov/?id=ED053419

**What "competent" therefore requires.** Four properties follow from the primary literature and are the ones that matter for MATRIX:

1. **Criterion is pre-specified, not derived from the measured score.** Mastery is a *criterion-referenced* claim: "the learner can do X to a stated standard", decided against a threshold fixed before the measurement. A stage label that is *computed from* a coverage score is descriptive, not a mastery claim — it cannot fail, so it certifies nothing.
2. **Evidence is at the level of the intended performance.** The revised taxonomy separates the **Knowledge Dimension** (factual, conceptual, procedural, metacognitive) from the **Cognitive Process Dimension** (Remember, Understand, Apply, Analyze, Evaluate, Create). Levels at or below *Understand* are recall/recognition; *Apply* is "using procedures in a new situation or problem". A mastery claim for a capability stage therefore has to name both the knowledge type and the process level.
   → Krathwohl, D. R. (2002). A Revision of Bloom's Taxonomy: An Overview. *Theory Into Practice*, 41(4), 212–218. DOI `10.1207/s15430421tip4104_2` `[XREF]`
   → Anderson, L. W., & Krathwohl, D. R. (Eds.) (2001). *A Taxonomy for Learning, Teaching, and Assessing: A Revision of Bloom's Taxonomy of Educational Objectives.* New York: Longman. ISBN 0321084055 / 9780321084057 (hardcover), 080131903X (abridged ed.) `[XREF via library catalogue records]`
   *Quoted wording of Krathwohl's "major criterion for mastery" is `[UNVERIFIED]` — the full text was not retrievable at the time of writing; the structural claims above are consistent with the 2001/2002 revision but are not quoted from the article.*
3. **Mastery learning as a *program* is evidenced by outcome, not by coverage.** The meta-analytic evidence is about mastery-learning *programmes* (criterion-referenced formative evaluation + reteaching), and it reports achievement effects for the programme, not for any single coverage metric.
   → Kulik, C. C., Kulik, L. J., & Bangert-Drowns, R. L. (1990). Effectiveness of Mastery Learning Programs: A Meta-Analysis. *Review of Educational Research*, 60(2), 265–299. DOI `10.3102/00346543060002265` `[XREF]`
   → Guskey, T. R. (1987). Rethinking Mastery Learning Reconsidered. *Review of Educational Research*, 57(2), 225–229. DOI `10.3102/00346543057002225` (also `10.2307/1170237`) `[XREF]`
4. **The classic group criterion is often mis-quoted.** Bloom, Hastings & Madaus (1971), *Handbook on Formative and Summative Evaluation of Student Learning*, McGraw-Hill, is the standard secondary source for numeric group-level mastery criteria (e.g. "85 % of the class must show 80 % efficiency"). That numeric criterion was **not** confirmed from the primary book; it is recorded here as `[UNVERIFIED — secondary quotation]`.
   → Bloom, B. S., Hastings, J. T., & Madaus, G. F. (1971). *Handbook on Formative and Summative Evaluation of Student Learning.* New York: McGraw-Hill. ISBN/record: multiple independent reference-list entries; primary text not retrieved.

**Consequence for MATRIX.** A capability stage in the Workbench may only be labelled "competent" if (i) the criterion was fixed in advance, (ii) the evidence was produced by a task at a stated cognitive-process level (target: *Apply* or above), and (iii) the task had an outcome key so the criterion could fail. Key-term coverage of a lesson satisfies none of (i)–(iii): the criterion is computed after the fact, the process level is *Remember*, and there is no key.

---

## 2. Retrieval practice vs. restudy: why key-term overlap is not evidence of learning

**The testing effect, and its most important property for MATRIX: it is delay-dependent.** Roediger & Karpicke (2006), read from the abstract: students studied prose passages and either took 1 or 3 immediate free-recall tests or restudied the same number of times; the final retention test came *"5 min, 2 days, or 1 week later. When the final test was given after 5 min, repeated studying improved recall relative to repeated testing. However, on the delayed tests, prior testing produced substantially greater retention than studying, even though repeated studying increased students' confidence in their ability to remember the material."*
→ Roediger, H. L., & Karpicke, J. D. (2006). Test-enhanced learning: taking memory tests improves long-term retention. *Psychological Science*, 17(3), 249–255. DOI `10.1111/j.1467-9280.2006.01693.x` `[PMID 16507066]`

**Three consequences, each fatal to the W40 teach-back as designed:**

1. **A measurement taken immediately after teaching will *reward the lesson*, not the learner.** At the 5-minute interval, restudy beat testing. MATRIX's teach-back is currently evaluated against the lesson that was just delivered. That is the 5-minute condition, i.e. the condition in which the *containment* heuristic looks best. **Any defensible teach-back must include a delayed probe**, or it is structurally biased in favour of restatement.
2. **The effect requires *retrieval*, and free recall beats recognition.** Roediger & Karpicke used *free-recall* tests without feedback. The meta-analytic review states that *"initial recall tests yielding larger testing benefits than recognition tests"* and that effortful processing is supported over semantic-elaboration accounts.
   → Rowland, C. A. (2014). The effect of testing versus restudy on retention: a meta-analytic review of the testing effect. *Psychological Bulletin*, 140(6), 1432–1463. DOI `10.1037/a0037559` `[PMID 25150680]`
   → Slamecka, N. J., & Graf, P. (1978). The generation effect: Delineation of a phenomenon. *Journal of Experimental Psychology: Human Learning and Memory*, 4(6), 592–604. DOI `10.1037/0278-7393.4.6.592` `[XREF]`
   → Dunlosky, J., Rawson, K. A., Marsh, E. J., Nathan, M. J., & Willingham, D. T. (2013). Improving Students' Learning With Effective Learning Techniques. *Psychological Science in the Public Interest*, 14(1), 4–58. DOI `10.1177/1529100612453266` `[PMID 26173288]`
3. **Even *elaborative* study of the same material loses to retrieval.** In a direct comparison, retrieval practice produced more learning than elaborative studying with concept mapping — i.e. the "the material contains the ideas" activity is strictly inferior to "produce the answer".
   → Karpicke, J. D., & Blunt, J. R. (2011). Retrieval Practice Produces More Learning than Elaborative Studying with Concept Mapping. *Science*, 331(6018), 772–775. DOI `10.1126/science.1199327` `[XREF]`

**Retrieval improves *transfer*, not just retention.** Butler (2010) ran 4 experiments on prose passages: restudy vs. repeated testing, then a final test one week later containing either the same questions (Exp. 1a), **new inferential questions within the same knowledge domain** (Exps. 1b, 2), or **new inferential questions from different knowledge domains** (Exp. 3). *"Repeated testing produced superior retention and transfer on the final test relative to repeated studying… the mnemonic benefits of test-enhanced learning are not limited to the retention of the specific response tested during initial learning but rather extend to the transfer of knowledge in a variety of contexts."*
→ Butler, A. C. (2010). Repeated testing produces superior transfer of learning relative to repeated studying. *Journal of Experimental Psychology: Learning, Memory, and Cognition*, 36(5), 1118–1133. DOI `10.1037/a0019902` `[PMID 20804289]`

**Classroom-scale confirmation with real delays.** Roediger, Agarwal, McDaniel & McDermott (2011) report long-term classroom gains from quizzing; McDaniel, Agarwal, Huelser, McDermott & Roediger (2011) show quiz *frequency* and *spacing* effects in a middle-school science classroom.
→ Roediger, H. L., Agarwal, P. K., McDaniel, M. A., & McDermott, K. B. (2011). Test-enhanced learning in the classroom: Long-term improvements from quizzing. *Journal of Experimental Psychology: Applied*, 17(4), 382–395. DOI `10.1037/a0026252` `[XREF]`
→ McDaniel, M. A., Agarwal, P. K., Huelser, B. D., McDermott, K. B., & Roediger, H. L. (2011). Test-enhanced learning in a middle school science classroom: The effects of quiz frequency and spacing. *Journal of Educational Psychology*, 103(2), 399–414. DOI `10.1037/a0021782` `[XREF]`

**Corroborating and boundary-setting work.**
→ Soderstrom, N. C., Kerr, T. K., & Bjork, R. A. (2016). The Critical Importance of Retrieval—and Spacing—for Learning. *Psychological Science*, 27(2), 223–230. DOI `10.1177/0956797615617778` (online 16 Dec 2015) `[PMID 26674128]` — replicates Karpicke & Roediger (2008) and shows that once spacing is controlled within subjects, *both* repeated testing and restudying improve learning; i.e. the retrieval advantage is real but interacts with spacing, and naive between-subject designs confound the two.
→ Karpicke, J. D., & Roediger, H. L. III (2008). The critical importance of retrieval for learning. *Science*, 319(5865), 966–968. DOI `10.1126/science.1152408` `[PMID 18276894]`

**Consequence for MATRIX.** "The lesson contained the key terms of the item" is the *recognition* condition, evaluated at the *5-minute* delay, on a task that requires no retrieval. It is the exact configuration the literature identifies as the one in which restudy looks best and learning is not demonstrated. Replacing it requires: (a) a **response** produced by the learner, not a match against the lesson; (b) a **delayed** probe; (c) a task whose scoring is against an **answer key**, not against the lesson's vocabulary.

---

## 3. Spacing and delayed retention: what a defensible delayed assessment looks like

**The gap effect and the joint role of gap and retention interval.** Cepeda et al. (2006) meta-analysed 839 assessments in 317 experiments across 184 articles: *"Analyses suggest that ISI [inter-study interval] and retention interval operate jointly to affect final-test retention; specifically, the ISI producing maximal retention increased as retention interval increased."*
→ Cepeda, N. J., Pashler, H., Vul, E., Wixted, J. T., & Rohrer, D. (2006). Distributed practice in verbal recall tasks: A review and quantitative synthesis. *Psychological Bulletin*, 132(3), 354–380. DOI `10.1037/0033-2909.132.3.354` `[PMID 16719566]`
→ Cepeda, N. J., Vul, E., Rohrer, D., & Wixted, J. T. (2008). Spacing Effects in Learning. *Psychological Science*, 19(11), 1095–1102. DOI `10.1111/j.1467-9280.2008.02209.x` `[XREF]`

**The classical baseline.** Ebbinghaus (1885/1913) established the forgetting curve and the *savings* measure: retention is evidenced by what the learner can still produce relative to first learning, not by re-exposure.
→ Ebbinghaus, H. (1885/1913). *Memory: A Contribution to Experimental Psychology* (H. A. Ruger & C. E. Bussenius, Trans.). New York: Teachers College, Columbia University. Landing page: https://psychclassics.yorku.ca/Ebbinghaus/index.htm `[FETCH]`

**What is defensible for MATRIX — and the honesty constraint.** A "delayed retention assessment" in the literature means: a **retrieval** test, administered after a **retention interval that is non-trivial relative to the material's shelf life**, on material whose retention is known to decay. In the studies cited above, the defensible intervals are **2 days to 1 week** (Roediger & Karpicke 2006: 5 min / 2 days / 1 week) and classroom-delayed measures (Roediger et al. 2011).

**Critical constraint for this repository.** The Workbench is deterministic and forbids wall-clock in decision paths (`AGENTS.md`: *"Не использовать без явного RFC: Random/wall-clock в рантайм-путях решений"*). Therefore delay **must be logical**: a `logicalDay` / probe-index counter owned by the harness, supplied as data, never read from a clock. Two honest consequences must be stated in the design:

- A logical-delay probe is a **simulation of delay for the measurement machinery**. It tests that the instrument can administer and score a delayed probe, and it exposes the *shape* of the scoring logic under decay. It does **not** demonstrate human retention over real time, and no report may claim it does.
- Because the synthetic learner's memory is confined to the episode, a logical-delay probe cannot by itself exhibit a forgetting curve. Any genuine retention claim requires either (a) a learner with durable state, or (b) a decay model applied as data. Both are out of scope for W41.4/5 (see §10, FH1).

---

## 4. Transfer of training: what constitutes a transfer test, and how transfer is separated from recall

**The 100-year problem is specification, not measurement.** Barnett & Ceci (2002) argue that *"arguments surrounding the question of whether far transfer occurs have made little progress toward resolution"* because of *"a failure to specify various dimensions along which transfer can occur, resulting in comparisons of 'apples and oranges'"*; they provide a framework of **9 relevant dimensions** and conclude that *"Estimation of a single effect size for far transfer is misguided in view of this complexity."*
→ Barnett, S. M., & Ceci, S. J. (2002). When and where do we apply what we learn? A taxonomy for far transfer. *Psychological Bulletin*, 128(4), 612–637. DOI `10.1037/0033-2909.128.4.612` `[PMID 12081085]`

**Operational consequence.** A claim of "transfer" is meaningless until the *dimension that varies* is named. The minimally acceptable taxonomy for MATRIX (adapted from Barnett & Ceci's dimensions, restricted to what a synthetic Workbench can hold constant):
- **Content domain** — same domain vs. new domain (Butler 2010 Exp. 3).
- **Task form** — same item vs. new inferential item requiring inference (Butler 2010 Exps. 1b, 2).
- **Surface form** — same wording vs. re-worded item.
- **Context** — same context vs. new context.
- **Learner** — same learner vs. independent learner (this is MATRIX's use of "teachability": the learner is a *different instance* from the one that received the lesson).

**Transfer vs. recall.** Butler (2010) is the cleanest operational discriminator available: the final test contained **new inferential questions** in the same and in different domains, and repeated *testing* transferred better than repeated *studying*. A test that re-presents the taught claim's wording measures recall. A test that asks the learner to apply a taught relation to a novel item measures transfer. The discriminating feature is **inference over a novel item**, not item novelty alone.

**What "near" and "far" mean in this repo.** Near transfer = new item, same domain, new surface form, requiring the taught relation. Far transfer = new item, different domain, requiring the same *relational schema*. Anything less (same item, or same item with paraphrased options) is recall.

**Supporting literature.**
→ Day, S. B., & Goldstone, R. L. (2012). The Import of Knowledge Export: Connecting Findings and Theories of Transfer of Learning. *Educational Psychologist*, 47(3), 153–176. DOI `10.1080/00461520.2012.696438` `[XREF]`
→ Rohrer, D., & Taylor, K. (2007). The shuffling of mathematics problems improves learning. *Instructional Science*, 35(6), 481–498. DOI `10.1007/s11251-007-9015-8` `[XREF]` — mixing of items (rather than blocked presentation) supports discrimination between confusable alternatives.

**Consequence for MATRIX.** "The lesson contains the key terms" is the **zero-transfer** condition (identical surface form, no inference). It must be labelled as such in the report and replaced by a specified near-transfer condition.

---

## 5. Conceptual change and the accommodation of misconceptions

**Accommodation requires diagnosing the learner's model, not scoring an error.** Posner, Strike, Hewson & Gertzog (1982) set out the conditions under which a conception is *accommodated* (revised) rather than merely supplemented: the learner must find the old conception **unsatisfactory**, the new one **intelligible**, **plausible**, and **fruitful**. Diagnosis therefore has to identify *which conception the learner currently holds* — an error count cannot do that.
→ Posner, G. J., Strike, K. A., Hewson, P. W., & Gertzog, W. A. (1982). Accommodation of a scientific conception: Toward a theory of conceptual change. *Science Education*, 66(2), 211–227. DOI `10.1002/sce.3730660207` `[XREF]`
*Note: the frequently quoted Science-magazine article of the same title has a different DOI; see §0 — this citation is to the verified *Science Education* article.*

**Learner conceptions are fragmented, not unitary theories.** diSessa (1993) argues for a "knowledge in pieces" account: learners hold naive, context-bound phenomenological primitives rather than a single coherent theory — which means "the learner holds misconception M" is itself a claim that must be earned.
→ diSessa, A. A. (1993). Toward an Epistemology of Physics. *Cognition and Instruction*, 10(2–3), 105–225. DOI `10.1080/07370008.1985.9649008` `[XREF]`
→ Chi, M. T. H. (1992). Conceptual change within and across ontological categories: Examples from learning and discovery in science. In R. N. Giere (Ed.), *Cognitive Models of Science* (Minnesota Studies in Philosophy of Science, Vol. 15), pp. 129–186. Minneapolis: University of Minnesota Press. (2010 reprint DOI `10.5749/9780816683963-007` `[XREF]`) — conceptual change can be a re-categorisation rather than replacement.

**Misconception diagnosis is an instrument-design discipline.** Treagust (1988) builds diagnostic *tests* to *evaluate students' misconceptions*, i.e. the instrument is designed to elicit the alternative conception, not to allocate a mark. Tsui & Treagust (2009) describe a **two-tier** instrument: tier 1 records the learner's reasoning, tier 2 judges it. Distractors in such instruments are not padding; each one is a *named* alternative conception.
→ Treagust, D. F. (1988). Development and use of diagnostic tests to evaluate students' misconceptions in science. *International Journal of Science Education*, 10(2), 159–169. DOI `10.1080/0950069880100204` `[XREF]`
→ Tsui, C.-Y., & Treagust, D. F. (2009). Evaluating Secondary Students' Scientific Reasoning in Genetics Using a Two-Tier Diagnostic Instrument. *International Journal of Science Education*, 32(8), 1073–1098. DOI `10.1080/09500690902951429` `[XREF]`
→ Liu, X. (2001). Synthesizing research on student conceptions in science. *International Journal of Science Education*, 23(1), 55–81. DOI `10.1080/09500690150198205` `[XREF]`

**A warning about the diagnosis itself.** Larkin (2012) documents *"misconceptions about 'misconceptions'"* — the risk that a learner holding a fragmented or over-generalised intuition is mislabelled as holding a stable alternative theory. Practical rule for MATRIX: a distractor bound to a misconception id must be **distinguishable from the correct alternative by evidence in the learner's own response**, otherwise the "diagnosis" is a restatement of "wrong".
→ Larkin, D. (2012). Misconceptions about "misconceptions": Preservice secondary science teachers' views. *Science Education*, 96(5), 927–959. DOI `10.1002/sce.21022` `[XREF]`
→ Taber, K. S. (2017). The Nature of Student Conceptions in Science. In K. S. Taber & W. Akpan (Eds.), *Science Education*, pp. 119–131. SensePublishers. DOI `10.1007/978-94-6300-749-8_9` `[XREF]`

**Errors are data.** Metcalfe (2017) synthesises error-driven learning: errors carry diagnostic information and are the substrate of learning, and errors committed with high confidence are "hypercorrected".
→ Metcalfe, J. (2017). Learning from Errors. *Annual Review of Psychology*, 68, 465–489. DOI `10.1146/annurev-psych-010416-044022` `[PMID 27648988]`

**Consequence for MATRIX.** An assessment item that can only yield "right/wrong" is a *scoring* device, not a *diagnostic* one. To support conceptual-change handling, each item needs (i) a keyed correct alternative, (ii) at least one alternative bound to a **named misconception id**, and (iii) a mapping from the learner's selection to a **model state** ("holds M1", "holds M2", "holds correct conception C"), so that re-teaching can be targeted at the diagnosed model rather than re-issued blindly.

---

## 6. Metacognition and calibration: what a meaningful confidence measure is

**The audit's core problem, stated in the literature's terms.** The W40 "confidence" is an authored field multiplied by lexical coverage. That is not a probability: it has (i) no outcome to be scored against, (ii) no elicitation ordering, (iii) no scoring rule, and (iv) no notion of what a "correct" confidence would even mean. Four results make the substitution impossible:

- *"even though repeated studying increased students' confidence in their ability to remember the material"* while *testing* produced greater retention (Roediger & Karpicke 2006) `[PMID 16507066]` → **confidence can move opposite to retention**.
- *"students' predictions of their performance were uncorrelated with actual performance"* (Karpicke & Roediger 2008) `[PMID 18276894]` → **stated confidence is not self-evident evidence of competence**.
- Flavell's construct of metacognition requires *monitoring* of one's own cognition against a standard — it is a two-sided relation (state ↔ accuracy), not a scalar label attached to a record.
  → Flavell, J. H. (1979). Metacognition and cognitive monitoring: A new area of cognitive-developmental inquiry. *American Psychologist*, 34(10), 906–911. DOI `10.1037/0003-066X.34.10.906` `[XREF]`

**What a defensible confidence measure requires (four components).**

1. **Forecast semantics.** The number must be a probability of a *stated, binary or categorical outcome*, elicited **before** that outcome is revealed. "Confidence in the answer" without a named outcome is not a forecast.
2. **A strictly proper scoring rule for aggregation.** Brier's score is the canonical strictly proper rule for binary events; Gneiting & Raftery (2007) give the general treatment and the conditions under which rules are strictly proper.
   → Brier, G. W. (1950). Verification of forecasts expressed in terms of probability. *Monthly Weather Review*, 78(1), 1–3. DOI `10.1175/1520-0493(1950)078<0001:VOFEIT>2.0.CO;2` `[XREF]`
   → Gneiting, T., & Raftery, A. E. (2007). Strictly Proper Scoring Rules, Prediction, and Estimation. *Journal of the American Statistical Association*, 102(477), 359–378. DOI `10.1198/016214506000001437` `[XREF]`
3. **Decomposition that separates calibration from discrimination.** Murphy (1973) partitions the Brier score into **reliability** (calibration), **resolution** (ability to discriminate outcomes), and **uncertainty** (base rate). This decomposition is the decisive one for MATRIX: a system can be perfectly calibrated and completely uninformative (high reliability, zero resolution). The legacy coverage×authored-field score has an undefined resolution, and the audit's concern is exactly that it conflated the two.
   → Murphy, A. H. (1973). A New Vector Partition of the Probability Score. *Journal of Applied Meteorology*, 12(4), 595–600. DOI `10.1175/1520-0450(1973)012<0595:ANVPOT>2.0.CO;2` `[XREF]`
4. **Reporting that makes calibration checkable.** Calibration plot, calibration slope and intercept, and expected calibration error; plus the explicit caution that slope/intercept alone can flatter a model with no resolution.
   → Van Calster, B., McLernon, D. J., van Smeden, M., Wynants, L., & Steyerberg, E. W. (2019). Calibration: the Achilles heel of predictive analytics. *BMC Medicine*, 17, 230. DOI `10.1186/s12916-019-1466-7` `[XREF]`

**Consequence for MATRIX.** The confidence channel must be re-specified as: *a per-item probability of a defined outcome, emitted by the learner before the answer is scored, aggregated by Brier, and reported with Murphy's reliability/resolution decomposition plus a calibration slope.* The legacy value must be retained only as a descriptive field of the pack, explicitly relabelled as *not* a probability, so that existing reports cannot keep quoting it as one.

---

## 7. Teach-back: what makes it valid versus a restatement test

**The official instrument definition — and it is exactly MATRIX's failure mode.** The AHRQ *Health Literacy Universal Precautions Toolkit*, 3rd ed., Tool 5, "Use the Teach-Back Method", states:

> "Remember teach-back is not a test of patients' memory. You can allow patients to refer to handouts when asking for a teach-back, but make sure they use their own words and are not reading the material back verbatim."

> "If they parrot your words back to you, they may not have understood."

> "Do not ask questions that can be answered with a 'yes' or 'no.' 'Do you understand?' and 'Does that make sense?' are NOT teach-back questions."

> "If teach-back uncovers a misunderstanding, explain things using a different approach, and ask patients to teach-back again. Repeat the cycle of reteaching and asking for a teach-back until they are able to describe the information in their own words correctly."

→ Agency for Healthcare Research and Quality (AHRQ). *Health Literacy Universal Precautions Toolkit*, 3rd Edition — Tool 5: Use the Teach-Back Method. https://www.ahrq.gov/health-literacy/improve/precautions/tool5.html (page last reviewed April 2024) `[FETCH]`

Applying this to the W40 finding — "the teach-back measures whether a lesson **contained** the key terms of an assessment item" — the W40 teach-back is, by the AHRQ definition, **verbatim parroting** of the lesson's wording. It is the *anti-pattern* the official instrument warns about, not an instance of it. Note also the AHRQ framing of purpose: the teacher is *checking whether the explanation landed*, and the remedy on failure is a **re-teach with a different approach** — which maps onto conceptual change (§5) rather than onto re-asking the same item.

**Evidence that teach-back works (and that it is an intervention, not only a measurement).** Dinh et al. (2016) systematically reviewed the effectiveness of the teach-back method on adherence and self-management in health education.
→ Dinh, T. T. H., Bonner, A., Clark, R., & Ramsbotham, J. (2016). The effectiveness of the teach-back method on adherence and self-management in health education for people with chronic disease: a systematic review. *JBI Database of Systematic Reviews and Implementation Reports*, 14(1), 210–247. DOI `10.11124/jbisrir-2016-2296` `[XREF]`

**"Learning by teaching" and the protégé effect: the learner-side rationale for a teach-back probe.** Chi, Bassok, Lewis & Reimann (1989) established the self-explanation/proteégé effect: generating self-explanations while studying improves learning beyond the explanation being in the learner's head. Chase, Chin, Oppezzo & Schwartz (2009) turn it into a directly usable instrument — a *teachable agent* whose learning is measured by the effort the learner expends explaining to it — the closest published analogue to MATRIX's "teach a synthetic learner and measure what it learned".
→ Chi, M. T. H., Bassok, M., Lewis, M. W., & Reimann, P. (1989). Self-Explanations: How Students Study and Use Examples in Learning to Solve Problems. *Cognitive Science*, 13(2), 145–182. DOI `10.1207/s15516709cog1302_1` `[XREF]`
→ Chase, C. C., Chin, D. B., Oppezzo, M. A., & Schwartz, D. L. (2009). Teachable Agents and the Protégé Effect: Increasing the Effort Towards Learning. *Journal of Science Education and Technology*, 18(4), 334–352. DOI `10.1007/s10956-009-9180-4` `[XREF]`
→ Fiorella, L., & Mayer, R. E. (2013). The relative benefits of learning by teaching and teaching expectancy. *Contemporary Educational Psychology*, 38(4), 281–288. DOI `10.1016/j.cedpsych.2013.06.001` `[XREF]`

**Teaching-effectiveness instruments already validated (reusable templates).** Tschannen-Moran & Hoy (2001) is the standard published instrument for *teacher* self-efficacy; the companion "Teacher Efficacy Scale" (TES) is the instrument to imitate if MATRIX wants a validated "teaching quality" channel rather than an ad-hoc one.
→ Tschannen-Moran, M., & Hoy, A. W. (2001). Teacher efficacy: capturing an elusive construct. *Teaching and Teacher Education*, 17(7), 783–805. DOI `10.1016/s0742-051x(01)00036-1` `[XREF]` — also published as *Educational and Psychological Measurement*, 61(4), 783–802; the EPM DOI `10.1177/00131644010637704` did **not** resolve in Crossref `[UNVERIFIED]`.

**Machine teaching: the formal frame for the MATRIX teachability experiment.** Zhu's *machine teaching* framing defines the problem as choosing a teaching sequence to optimise a *learner's* learning progress; the learner must be an entity with an initial state that the teaching changes — which is precisely the structure MATRIX's synthetic learner lacks, since it "ignores the question".
→ Zhu, X. (2009). Machine Teaching. *Annual Review of Computer Science*, 1, 315–341. Journal DOI `10.1146/annurev.cs-070308-120104` **did not resolve** `[UNVERIFIED]`. Verified preprint: Zhu, X. (2018). An Overview of Machine Teaching. arXiv:1801.05927. DOI `10.48550/arxiv.1801.05927` `[OA]`.

**Validity checklist distilled from the above — what makes a teach-back test valid rather than a restatement test.**

| # | Requirement | Authority |
|---|---|---|
| T1 | The learner must **produce** content, not select it from the presented material; the probe must not be answerable from the lesson's surface wording | AHRQ Tool 5; Rowland 2014; Slamecka & Graf 1978 |
| T2 | The learner must answer **in its own words/structure**, not read back the lesson verbatim; verbatim copying is a *failure mode to be detected*, not a pass | AHRQ Tool 5 (explicit) |
| T3 | The probe must be **cue-independent with respect to the answer**: the item's key-term list must not be supplied to the learner, or containment becomes trivially satisfiable | §2 (recognition vs. recall) |
| T4 | The probe must be scored against an **answer key**, and the key must include **alternatives bound to named misconceptions**, so that an incorrect answer is a diagnosis | Treagust 1988; Tsui & Treagust 2009; Posner et al. 1982 |
| T5 | The probe must be **delayed** (logical delay suffices for the machinery), because the retrieval advantage emerges with delay and not at 5 minutes | Roediger & Karpicke 2006; Soderstrom et al. 2016 |
| T6 | The task must require **inference on a novel item** to count as transfer; same-item re-presentation is recall | Butler 2010; Barnett & Ceci 2002 |
| T7 | The learner must be an **independent instance** (separate from the one that received the lesson), so that "teachability" is a property of the lesson + claim store, not of a session transcript | AHRQ Tool 5 ("independent learner" framing); Zhu (machine teaching) |

**MATRIX status against T1–T7 (from the W40 audit as described in the task brief):**

| Req | W40 status | Verdict |
|---|---|---|
| T1 | lesson containment check | **FAIL** — the probe never asks the learner |
| T2 | lesson containment check | **FAIL** — verbatim containment is the *pass condition* |
| T3 | key terms supplied to the evaluator | **FAIL** |
| T4 | "key-term coverage" score | **FAIL** — no key, no alternatives |
| T5 | immediate (same-episode) | **FAIL** — the 5-minute condition, which favours restudy |
| T6 | same key terms | **FAIL** — recall, not transfer |
| T7 | synthetic learner ignores the question | **FAIL** — the learner's model is never exercised |

Seven of seven fail. This is the strongest form of the audit's conclusion and it is entirely derived from primary sources.

---

## 8. Consolidated mapping: W40 artifact → learning-science requirement → what MATRIX can measure

| W40 artifact (from the audit) | What the literature requires | Minimal defensible replacement inside the Workbench |
|---|---|---|
| "confidence" = authored field × lexical coverage | forecast semantics + strictly proper rule + reliability/resolution decomposition (§6) | per-item probability emitted **before** scoring, aggregated by Brier, reported with Murphy decomposition + calibration slope; legacy field retained but relabelled "descriptive, not a probability" |
| teach-back = "lesson contains the item's key terms" | production, own words, key-scored, delayed, inference, independent learner (§2, §7) | closed-book probe on an independent learner instance; answer-key scoring over misconception-bound alternatives; verbatim-leakage guard; logical-delay re-probe |
| capability stages derived from key-term coverage | pre-specified criterion at a stated cognitive-process level (§1) | stage thresholds fixed in advance against a keyed task at *Apply*+ level; stage assignment is a **decision against a fixed criterion**, not a projection of a coverage number |
| lesson "contains" ⇒ learner knows | retrieval practice, not restudy (§2) | the lesson is the *study* phase; the probe is the *test* phase; the two must not share the scoring function |

---

## 9. ENGINEERING HYPOTHESIS

> ### ENGINEERING HYPOTHESIS H41-A — *Structured-claim discrimination is a measure of teachability; lexical containment is not.*
>
> **Mechanism.** The Learning Workbench's claim store (claims with sources and evidence) can support an answer that a bag-of-words match cannot: if the store carries the **relations, polarity, conditions and evidential backing** of the taught material, then a learner that must **discriminate between alternatives whose surface forms overlap** can answer correctly and reject misconception-bound alternatives — while a lexical-overlap baseline that merely matches key terms to the lesson cannot. Concretely, a new probe instrument (all deterministic, no LLM, no external service, no wall-clock) with five parts:
>
> - **M1 — Adversarial item construction (data, authored).** Each assessment item carries, in addition to its key terms: a keyed correct alternative; ≥1 alternative bound to a **named misconception id**; and a **lexical-overlap statistic** `ovl(alternative, lesson)` computed deterministically. An item is **adversarial** iff at least one wrong alternative satisfies `ovl ≥ τ`, where τ is fixed in the pack before the run. The adversarial subset is therefore *defined by overlap*, so a containment measure provably cannot separate it.
> - **M2 — Independent closed-book learner.** A probe runner receives **only** the item's question text, the alternatives, and the learner's internal claim store. It must **not** receive the lesson text nor the item's key-term list (T3). It answers by **entailment-style matching over structured claims** (subject–predicate–object with polarity/conditions and evidence), not by term counting, and emits: (i) a selected alternative id; (ii) a **stated confidence** p ∈ [0,1] emitted **before** scoring; (iii) a **produced teach-back utterance** regenerated from its internal claims.
> - **M3 — Verbatim-leakage guard.** The produced teach-back utterance is scored for token overlap against the lesson text. If overlap exceeds the pack's fixed leakage threshold, the probe outcome for that item is recorded as **RESTATEMENT**, not as competence — the instrument self-audits, exactly as the AHRQ rule ("not reading the material back verbatim") demands. This is the deterministic, LLM-free stand-in for "in the learner's own words".
> - **M4 — Logical delay.** The same probe is re-administered after a **logical** delay (`logicalDay`, a counter supplied by the harness as data — never a clock). Primary prediction uses delay ≥ 1 so the probe is not in the 5-minute regime in which restudy wins.
> - **M5 — Scoring and reporting.** Item outcome ∈ {0,1} (correct alternative chosen). Session score = mean outcome. Confidence is aggregated with the **Brier score** and decomposed by **Murphy's** reliability / resolution / uncertainty; calibration slope and intercept are reported. A **lexical-overlap baseline model** (choose the alternative with maximal key-term overlap with the lesson) is computed on every item as the reference competitor.
>
> **Measurable prediction (primary, pre-registered).**
> **P1.** On the adversarial subset, the structured-claim learner's **discrimination accuracy** exceeds the lexical-overlap baseline's accuracy by **≥ 0.20 absolute** (exact paired permutation test over items; the permutation is over a fixed, deterministically seeded item ordering — no `Random` in the runtime path, per `AGENTS.md`), **while on the clean subset the two accuracies differ by ≤ 0.05**.
> Formally: let `Acc_struc(A)` and `Acc_lex(A)` be the two models' accuracy on adversarial items, `Acc_struc(C)` and `Acc_lex(C)` on clean items.
> `Δ_A = Acc_struc(A) − Acc_lex(A) ≥ 0.20` **and** `|Acc_struc(C) − Acc_lex(C)| ≤ 0.05`.
> *Rationale, stated as the falsifiable mechanism rather than as an assumption:* P1 holds **iff** the claim store's structure (relations, polarity, conditions, evidence) carries information that the lesson's vocabulary does not. If the vocabulary alone determines the answer, the Workbench's teaching substrate is lexical, and the W40 audit's central criticism stands unchanged.
>
> **Secondary (reported, but NOT part of the falsification criterion for this wave).**
> - **S1 (delay shape).** `Acc` at logical delay ≥ 1 is expected to be ≤ `Acc` at delay 0 for a within-episode learner, and the *gap* to the overlap baseline is expected to widen with delay (T5). Reported, not decisive — a within-episode synthetic learner cannot show real forgetting (§3).
> - **S2 (calibration).** The learner's stated confidence is expected to show Murphy **resolution > 0** on the adversarial subset and a calibration slope inside [0.8, 1.2]; the legacy authored×coverage field is expected to fail both. Reported, not decisive — sample-size and binning constraints for calibration are a backlog item (§10, FH2).
> - **S3 (restatement rate).** M3's leakage guard should classify a non-trivial share of the legacy "teach-back" passes as RESTATEMENT, quantifying the W40 defect rather than merely asserting it.
>
> **Experiment that would refute it (E1).**
> 1. Author the adversarial pack: for each existing item, add alternatives bound to the misconceptions already implied by the pack's claims, choosing alternatives whose key-term overlap with the lesson is maximal (deterministic selection: highest `ovl`, ties broken by authored alternative index). Freeze τ and the pack before any measurement.
> 2. Run, in one deterministic harness pass, over the frozen pack and a fixed logical-delay schedule: (a) the legacy coverage teach-back; (b) the legacy coverage score; (c) the M2 structured-claim learner with the M5 lexical-overlap baseline; (d) the M3 leakage audit of the legacy passes.
> 3. Compute `Acc_struc`, `Acc_lex` on the adversarial and clean subsets, the paired permutation p-value for `Δ_A`, and the Brier/Murphy/calibration statistics.
> 4. **Refutation criterion (any one suffices).**
>    - **R1:** `Δ_A < 0.05` — the structured-claim learner does not beat the overlap baseline on the very items designed to separate them. Then the claim store carries no exploitable structure; the W40 lexical measurement was not "the wrong instrument", it was **the only** instrument, and the teachability claim must be withdrawn rather than re-instrumented.
>    - **R2:** `|Acc_struc(C) − Acc_lex(C)| > 0.15` — the new measure differs from the overlap baseline on items where overlap was already sufficient. Then the new measure is measuring *something other* than teachability (e.g. pack artefacts), and is not a valid substitute.
>    - **R3:** `Acc_struc` is ≤ 0 on the clean subset — the probe fails to answer any item it should answer. This is not a scientific refutation but it **is** a falsification of the hypothesis *as stated for this repository*: it would show the W41 pack lacks the answer key and misconception bindings that the mechanism requires, i.e. the hypothesis is not yet testable here and the data-pack authoring is the real blocker.
>
> **What the hypothesis would settle (and what it would not).**
> If P1 holds, the Workbench gains a *measurable* teachability claim: "an independent learner, given only the question and the taught claim store, discriminates the correct alternative from misconception-bound alternatives whose vocabulary is identical to the lesson's, at a rate the lexical baseline cannot reach." If P1 fails (R1), the Workbench has **no** defensible teachability measurement, and W41.5 must stop claiming one.
> It does **not** claim human learning, human retention, or a real forgetting curve: the learner is synthetic, the delay is logical (§3), and the confidence channel is validated only as a forecast of the probe outcome, not as a self-report of understanding.
>
> **Constraints satisfied.** No LLM (all matching, ranking and scoring are lexical/structural over the existing claim store). No external services (all inputs are the in-repo data pack). No wall-clock in decision paths (delay is a supplied logical counter; no `Random` — permutation uses a fixed deterministic ordering). Deterministic execution (all tie-breaks are by authored index; the same pack and schedule yield byte-identical results). No change to FROZEN zones; the proposal is additive to `matrix-core/src/main/java/io/matrix/knowledge/` (new probe component + new pack fields), leaving the legacy path intact so the two can be compared in the same run.

---

## 10. Further hypotheses (research-indicated, out of scope for W41.4/5)

Recorded so the backlog grows slowly; each names the blocker that keeps it out of this wave.

- **FH1 — Logical-delay retention shape.** *If the Workbench gains a learner with durable state across logical days, then the closed-book probe score will decay over logical delay and the gap to a restudy baseline will widen with delay, reproducing the direction of Roediger & Karpicke (2006) and the ISI×retention-interval interaction of Cepeda et al. (2006).* **Blocker:** no durable learner state; a within-episode learner cannot forget, so any "decay" would be an artefact of a decay model applied as data. Requires an explicit RFC on learner persistence before it can be anything but simulation.
- **FH2 — Calibration is learnable, and the legacy confidence is uncalibrated by construction.** *If the learner's stated confidence is a genuine forecast emitted before scoring, then its Brier score will show Murphy resolution > 0 and a calibration slope in [0.8, 1.2] on items with a non-degenerate base rate; the legacy authored×coverage field will show resolution ≈ 0 and slope outside that band, i.e. it is uninformative rather than merely imprecise.* **Blocker:** insufficient items per confidence band for a stable calibration curve; the pack must be extended with items of varying difficulty and known base rates before the claim is testable.
- **FH3 — Accommodation beats correction.** *If a lesson supplies the four Posner-style conditions — dissatisfaction with the current model, intelligibility, plausibility, fruitfulness — then the subsequent misconception-bound alternative selection rate will fall more than under a lesson that merely states the correct claim, even though the two lessons contain identical key terms.* **Blocker:** requires authored counter-claims and a re-teaching episode in the data pack; this is a teaching-strategy experiment, not a measurement experiment, and belongs to W42+.

---

## 11. Open items, UNVERIFIED material, and what was deliberately not claimed

**UNVERIFIED (recorded, not repaired):**
- Krathwohl's exact wording of the "major criterion for mastery" — full text not retrievable; structural claims about the Knowledge × Cognitive-Process dimensions are stated at the level supported by the 2001 book and the 2002 overview's abstract-level description. The *quoted* wording is not claimed.
- Bloom, Hastings & Madaus (1971) numeric group-level mastery criterion ("85 % at 80 % efficiency") — found only in a secondary document; the primary book was not retrieved.
- Day & Blunt (2012) "Knowing About Knowing: Near Transfer, Far Transfer, and the Honeycomb", *Educational Researcher* 41(4), 37–41 — the candidate DOI `10.3102/0013189X12441288` does not resolve and the record is absent from Crossref. **Not cited as a source anywhere above**; the transfer argument uses Barnett & Ceci (2002), Butler (2010) and Day & Goldstone (2012) instead, all verified.
- Treagust (2006) "Development and use of two-tier diagnostic questions…", *International Journal of Science Education* 28(6), 631–650 — candidate DOI `10.1080/09500690500338538` does not resolve; **not cited**; two-tier instrumentation is cited via Treagust (1988) and Tsui & Treagust (2009), both verified.
- Slamecka & Graf (1978) "The effects of recall versus recognition on the retention of meaningful information" — candidate DOI `10.1037/0278-7393.26.1.69` does not resolve; **not cited**; the generation-effect paper (verified DOI) and Rowland (2014) carry the recall-vs-recognition claim.
- Zhu (2009) *Machine Teaching* journal DOI `10.1146/annurev.cs-070308-120104` does not resolve; the verified 2018 arXiv preprint (`10.48550/arxiv.1801.05927`) is cited instead.
- Tschannen-Moran & Hoy (2001) *Educational and Psychological Measurement* DOI `10.1177/00131644010637704` does not resolve in Crossref; the co-published *Teaching and Teacher Education* version (verified DOI) is cited.

**Deliberately not claimed anywhere in this document:** any effect size, any recall percentage, any "X % of students master X", any numeric mastery threshold, and any claim about the current contents of any repository file. Every quoted experimental detail above was read from a verified abstract or record, and every numeric claim is either absent or explicitly sourced.

**Follow-up verification task (cheap, worth doing before W41.5 closes):** obtain and read the full text of Roediger & Karpicke (2006) and Karpicke & Roediger (2008) to extract the per-condition retention figures, and re-check the four unresolved DOIs against the publishers directly — Crossref coverage for 1978–1993 APA and 2006–2012 Taylor & Francis/SAGE content is demonstrably incomplete, so absence from Crossref is not evidence of absence.

---

## 12. Primary source list

All identifiers below were verified during this study; the token after each entry states how. Sources are listed by the section that relies on them.

**§1 Mastery learning**
- Bloom, B. S. (1968). *Learning for Mastery. Instruction and Curriculum.* Regional Education Laboratory for the Carolinas and Virginia, Topical Papers and Reprints, No. 1. ERIC **ED053419**. https://eric.ed.gov/?id=ED053419 `[FETCH]`
- Krathwohl, D. R. (2002). A Revision of Bloom's Taxonomy: An Overview. *Theory Into Practice*, 41(4), 212–218. DOI `10.1207/s15430421tip4104_2` `[XREF]`
- Anderson, L. W., & Krathwohl, D. R. (Eds.) (2001). *A Taxonomy for Learning, Teaching, and Assessing: A Revision of Bloom's Taxonomy of Educational Objectives.* New York: Longman. ISBN 0321084055 / 9780321084057; abridged 080131903X. `[XREF via catalogues]`
- Kulik, C. C., Kulik, L. J., & Bangert-Drowns, R. L. (1990). Effectiveness of Mastery Learning Programs: A Meta-Analysis. *Review of Educational Research*, 60(2), 265–299. DOI `10.3102/00346543060002265` `[XREF]`
- Guskey, T. R. (1987). Rethinking Mastery Learning Reconsidered. *Review of Educational Research*, 57(2), 225–229. DOI `10.3102/00346543057002225`; JSTOR `10.2307/1170237` `[XREF]`
- Bloom, B. S., Hastings, J. T., & Madaus, G. F. (1971). *Handbook on Formative and Summative Evaluation of Student Learning.* New York: McGraw-Hill. `[UNVERIFIED — primary text not retrieved; numeric criteria not claimed]`

**§2 Retrieval practice vs. restudy**
- Roediger, H. L., & Karpicke, J. D. (2006). Test-enhanced learning: taking memory tests improves long-term retention. *Psychological Science*, 17(3), 249–255. DOI `10.1111/j.1467-9280.2006.01693.x`; PMID 16507066 `[PMID]`
- Karpicke, J. D., & Roediger, H. L. III (2008). The critical importance of retrieval for learning. *Science*, 319(5865), 966–968. DOI `10.1126/science.1152408`; PMID 18276894 `[PMID]`
- Karpicke, J. D., & Blunt, J. R. (2011). Retrieval Practice Produces More Learning than Elaborative Studying with Concept Mapping. *Science*, 331(6018), 772–775. DOI `10.1126/science.1199327` `[XREF]`
- Butler, A. C. (2010). Repeated testing produces superior transfer of learning relative to repeated studying. *Journal of Experimental Psychology: Learning, Memory, and Cognition*, 36(5), 1118–1133. DOI `10.1037/a0019902`; PMID 20804289 `[PMID]`
- Rowland, C. A. (2014). The effect of testing versus restudy on retention: a meta-analytic review of the testing effect. *Psychological Bulletin*, 140(6), 1432–1463. DOI `10.1037/a0037559`; PMID 25150680 `[PMID]`
- Dunlosky, J., Rawson, K. A., Marsh, E. J., Nathan, M. J., & Willingham, D. T. (2013). Improving Students' Learning With Effective Learning Techniques. *Psychological Science in the Public Interest*, 14(1), 4–58. DOI `10.1177/1529100612453266`; PMID 26173288 `[PMID]`
- Slamecka, N. J., & Graf, P. (1978). The generation effect: Delineation of a phenomenon. *Journal of Experimental Psychology: Human Learning and Memory*, 4(6), 592–604. DOI `10.1037/0278-7393.4.6.592` `[XREF]`
- Soderstrom, N. C., Kerr, T. K., & Bjork, R. A. (2016). The Critical Importance of Retrieval—and Spacing—for Learning. *Psychological Science*, 27(2), 223–230. DOI `10.1177/0956797615617778`; PMID 26674128 `[PMID]`
- Roediger, H. L., Agarwal, P. K., McDaniel, M. A., & McDermott, K. B. (2011). Test-enhanced learning in the classroom: Long-term improvements from quizzing. *Journal of Experimental Psychology: Applied*, 17(4), 382–395. DOI `10.1037/a0026252` `[XREF]`
- McDaniel, M. A., Agarwal, P. K., Huelser, B. D., McDermott, K. B., & Roediger, H. L. (2011). Test-enhanced learning in a middle school science classroom: The effects of quiz frequency and spacing. *Journal of Educational Psychology*, 103(2), 399–414. DOI `10.1037/a0021782` `[XREF]`

**§3 Spacing and delayed retention**
- Cepeda, N. J., Pashler, H., Vul, E., Wixted, J. T., & Rohrer, D. (2006). Distributed practice in verbal recall tasks: A review and quantitative synthesis. *Psychological Bulletin*, 132(3), 354–380. DOI `10.1037/0033-2909.132.3.354`; PMID 16719566 `[PMID]`
- Cepeda, N. J., Vul, E., Rohrer, D., & Wixted, J. T. (2008). Spacing Effects in Learning. *Psychological Science*, 19(11), 1095–1102. DOI `10.1111/j.1467-9280.2008.02209.x` `[XREF]`
- Ebbinghaus, H. (1885/1913). *Memory: A Contribution to Experimental Psychology* (H. A. Ruger & C. E. Bussenius, Trans.). New York: Teachers College, Columbia University. https://psychclassics.yorku.ca/Ebbinghaus/index.htm `[FETCH]`

**§4 Transfer**
- Barnett, S. M., & Ceci, S. J. (2002). When and where do we apply what we learn? A taxonomy for far transfer. *Psychological Bulletin*, 128(4), 612–637. DOI `10.1037/0033-2909.128.4.612`; PMID 12081085 `[PMID]`
- Day, S. B., & Goldstone, R. L. (2012). The Import of Knowledge Export: Connecting Findings and Theories of Transfer of Learning. *Educational Psychologist*, 47(3), 153–176. DOI `10.1080/00461520.2012.696438` `[XREF]`
- Rohrer, D., & Taylor, K. (2007). The shuffling of mathematics problems improves learning. *Instructional Science*, 35(6), 481–498. DOI `10.1007/s11251-007-9015-8` `[XREF]`

**§5 Conceptual change and misconception diagnosis**
- Posner, G. J., Strike, K. A., Hewson, P. W., & Gertzog, W. A. (1982). Accommodation of a scientific conception: Toward a theory of conceptual change. *Science Education*, 66(2), 211–227. DOI `10.1002/sce.3730660207` `[XREF]`
- diSessa, A. A. (1993). Toward an Epistemology of Physics. *Cognition and Instruction*, 10(2–3), 105–225. DOI `10.1080/07370008.1985.9649008` `[XREF]`
- Chi, M. T. H. (1992). Conceptual change within and across ontological categories. In R. N. Giere (Ed.), *Cognitive Models of Science*, pp. 129–186. Univ. of Minnesota Press. 2010 reprint DOI `10.5749/9780816683963-007` `[XREF]`
- Treagust, D. F. (1988). Development and use of diagnostic tests to evaluate students' misconceptions in science. *International Journal of Science Education*, 10(2), 159–169. DOI `10.1080/0950069880100204` `[XREF]`
- Tsui, C.-Y., & Treagust, D. F. (2009). Evaluating Secondary Students' Scientific Reasoning in Genetics Using a Two-Tier Diagnostic Instrument. *International Journal of Science Education*, 32(8), 1073–1098. DOI `10.1080/09500690902951429` `[XREF]`
- Liu, X. (2001). Synthesizing research on student conceptions in science. *International Journal of Science Education*, 23(1), 55–81. DOI `10.1080/09500690150198205` `[XREF]`
- Larkin, D. (2012). Misconceptions about "misconceptions": Preservice secondary science teachers' views. *Science Education*, 96(5), 927–959. DOI `10.1002/sce.21022` `[XREF]`
- Taber, K. S. (2017). The Nature of Student Conceptions in Science. In K. S. Taber & W. Akpan (Eds.), *Science Education*, pp. 119–131. SensePublishers. DOI `10.1007/978-94-6300-749-8_9` `[XREF]`
- Metcalfe, J. (2017). Learning from Errors. *Annual Review of Psychology*, 68, 465–489. DOI `10.1146/annurev-psych-010416-044022`; PMID 27648988 `[PMID]`

**§6 Metacognition and calibration**
- Flavell, J. H. (1979). Metacognition and cognitive monitoring: A new area of cognitive-developmental inquiry. *American Psychologist*, 34(10), 906–911. DOI `10.1037/0003-066X.34.10.906` `[XREF]`
- Brier, G. W. (1950). Verification of forecasts expressed in terms of probability. *Monthly Weather Review*, 78(1), 1–3. DOI `10.1175/1520-0493(1950)078<0001:VOFEIT>2.0.CO;2` `[XREF]`
- Murphy, A. H. (1973). A New Vector Partition of the Probability Score. *Journal of Applied Meteorology*, 12(4), 595–600. DOI `10.1175/1520-0450(1973)012<0595:ANVPOT>2.0.CO;2` `[XREF]`
- Gneiting, T., & Raftery, A. E. (2007). Strictly Proper Scoring Rules, Prediction, and Estimation. *Journal of the American Statistical Association*, 102(477), 359–378. DOI `10.1198/016214506000001437` `[XREF]`
- Van Calster, B., McLernon, D. J., van Smeden, M., Wynants, L., & Steyerberg, E. W. (2019). Calibration: the Achilles heel of predictive analytics. *BMC Medicine*, 17, 230. DOI `10.1186/s12916-019-1466-7` `[XREF]`

**§7 Teach-back and teaching effectiveness**
- Agency for Healthcare Research and Quality. *Health Literacy Universal Precautions Toolkit*, 3rd ed. — Tool 5: Use the Teach-Back Method. https://www.ahrq.gov/health-literacy/improve/precautions/tool5.html `[FETCH]`
- Dinh, T. T. H., Bonner, A., Clark, R., & Ramsbotham, J. (2016). The effectiveness of the teach-back method on adherence and self-management in health education for people with chronic disease: a systematic review. *JBI Database of Systematic Reviews and Implementation Reports*, 14(1), 210–247. DOI `10.11124/jbisrir-2016-2296` `[XREF]`
- Chi, M. T. H., Bassok, M., Lewis, M. W., & Reimann, P. (1989). Self-Explanations: How Students Study and Use Examples in Learning to Solve Problems. *Cognitive Science*, 13(2), 145–182. DOI `10.1207/s15516709cog1302_1` `[XREF]`
- Chase, C. C., Chin, D. B., Oppezzo, M. A., & Schwartz, D. L. (2009). Teachable Agents and the Protégé Effect: Increasing the Effort Towards Learning. *Journal of Science Education and Technology*, 18(4), 334–352. DOI `10.1007/s10956-009-9180-4` `[XREF]`
- Fiorella, L., & Mayer, R. E. (2013). The relative benefits of learning by teaching and teaching expectancy. *Contemporary Educational Psychology*, 38(4), 281–288. DOI `10.1016/j.cedpsych.2013.06.001` `[XREF]`
- Tschannen-Moran, M., & Hoy, A. W. (2001). Teacher efficacy: capturing an elusive construct. *Teaching and Teacher Education*, 17(7), 783–805. DOI `10.1016/s0742-051x(01)00036-1` `[XREF]`
- Zhu, X. (2018). An Overview of Machine Teaching. arXiv:1801.05927. DOI `10.48550/arxiv.1801.05927` `[OA]` — preprint of Zhu, X. (2009). *Machine Teaching.* *Annual Review of Computer Science*, 1, 315–341 (journal DOI `[UNVERIFIED — does not resolve]`)

---

*Prepared by Research Stream A, MATRIX RECON-W41. Primary-source verification performed against Crossref, NCBI E-utilities, OpenAlex, ERIC and publisher pages on 2026-11-01. No repository source file was modified; the only file created is this one.*
