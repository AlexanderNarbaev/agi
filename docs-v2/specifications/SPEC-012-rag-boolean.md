# SPEC-012 — Boolean RAG (normative)

**Status**: v1 · singleton normative
**Package**: `io.matrix.rag`
**Related**: [SPEC-002](./SPEC-002-boolean-compute-layer.md), [DESIGN-14](../designs/DESIGN-14-boolean-rag.md)

## Что

`rag/` — индексно-ориентированный retrieval поверх boolean-векторов.
Хранит документы в `BooleanIndex`, ищет через inverted-bit-positions
и поддерживает несколько режимов: чистый boolean, гибридный (float
embeddings), скелетный (SkeletonTreeParser для иерархий), RR-fusion.

## Архитектура

```
BooleanIndex                 — inverted-index поверх boolean-документов
BooleanIndexPersistence      — сохранение/загрузка индекса
BooleanRag                   — основной API
BooleanRagBenchmark          — микробенчмарки
EmbeddingVector              — обёртка для float-embedding
ExactTermGuard               — гарантия exact-term match для важных запросов
FloatEmbeddingIndex          — float-side индекса (для гибрида)
HybridBooleanRag             — комбинация boolean + float
IndexedBooleanRag            — обёртка над BooleanIndex для удобного API
QueryExpander                — раскрытие boolean-query (synonyms, hypernyms)
RrfFusion                    — Reciprocal Rank Fusion для multi-index
SkeletonNode                 — узел скелетного дерева
SkeletonTreeParser           — парсер иерархий (YAML/JSON)
```

## FR (классы и интерфейсы)

- `rag/BooleanIndex` — основной inverted-index.
  - `add(docId, booleanVector)` — добавить документ.
  - `search(query, topK)` → `List<SearchResult>` ranked by score.
  - `size()` → количество документов.
- `rag/BooleanRag` — высокоуровневый API поверх `BooleanIndex`
  с поддержкой метаданных, фильтров, top-K.
- `rag/IndexedBooleanRag` — обёртка с `init(BooleanIndex)` для DI.
- `rag/HybridBooleanRag` — комбинированный поиск
  (boolean score * α + float score * (1-α)).
- `rag/QueryExpander` — расширяет boolean-query
  (synonyms из таксономии, hypernyms).
- `rag/ExactTermGuard` — гарантирует, что critical-токены
  (имена, цифры, ID) присутствуют в результате.
- `rag/RrfFusion` — Reciprocal Rank Fusion для слияния
  нескольких индексов с разными scoring.

## BooleanIndex

Представление:

```
class BooleanIndex {
  List<boolean[]> docs;             // документы (length = docCount)
  Map<Integer, List<Integer>> pos;  // bit-position -> list of docIds
  int totalDocs;
  int totalBits;
}
```

`search(query, topK)` использует bitwise-AND для подсчёта
overlap (быстрее, чем Jaccard на длинных документах).
Score = `overlap_count / max(|query|, |doc|)`.

## Инварианты

1. **Детерминизм (CONSTITUTION I)**: `BooleanIndex.search(query, topK)`
   возвращает один и тот же top-K для одного и того же индекса +
   запроса. Random допустим только при tie-break для equal scores.
2. **FROZEN-гейт (CONSTITUTION III)**: документ, помеченный
   `tagged("frozen")`, не может быть удалён через `BooleanIndex.remove`.
   Только через RFC.
3. **K_MAX=20 (CONSTITUTION II)**: `BooleanIndex` использует bit-positions
   до 2^K_MAX = 1 048 576. Каждый документ — boolean[] фиксированной
   длины (задаётся при создании индекса).
4. **Coverage gate ≥82%** на всех rag-классах.
5. **ExactTermGuard**: для запросов, содержащих protected-токены
   (имена собственные, цифры, ID), top-K ДОЛЖЕН содержать документ,
   покрывающий все protected-токены. INV-RAG-1 в [INVARIANTS](../engineering/INVARIANTS.md).

## HybridBooleanRag

Слияние boolean + float embeddings через взвешенную сумму:

```
finalScore = alpha * booleanScore + (1 - alpha) * floatScore
```

`alpha` настраивается (default = 0.6 — boolean-side доминирует).
Float embeddings хранятся в `FloatEmbeddingIndex` (отдельный
HNSW или brute-force в зависимости от размера корпуса).

## RrfFusion

Reciprocal Rank Fusion для слияния N индексов:

```
finalScore(doc) = sum( 1 / (k + rank_i(doc)) )  for i in [0, N)
```

где `rank_i(doc)` — позиция документа в i-ом индексе (0-based,
+inf если документ не найден). `k` — smoothing constant (default = 60).

Детерминированно — нет random, чистая функция.

## Скелетный режим

`SkeletonTreeParser` парсит иерархические документы (YAML/JSON):

```yaml
section: knowledge_base
children:
  - section: math
    children:
      - leaf: "Pythagoras theorem"
  - section: biology
```

`BooleanRag` использует `SkeletonNode` для оценки section-match:
score документа в section S = score leaf + bonus за section-match.

## Связь с существующим

- `api/QaCorpusIndex` ([api/QaCorpusIndex](../../matrix-core/src/main/java/io/matrix/api/QaCorpusIndex.java))
  — другой retrieval-механизм (IDF + n-gram), не часть `rag/`.
- `agent/LongHorizonPlanner` использует `HybridBooleanRag` для
  retrieval плановых контекстов.
- `brain/BrainPipeline` ([DESIGN-13](../designs/DESIGN-13-brain-pipeline.md))
  использует `BooleanRag` для world-model retrieval.
- `OpenAIChatResource` использует `IndexedBooleanRag` для
  document-grounded ответов (W7.3+).

## Тесты

`BooleanIndexTest` (16 тестов), `BooleanRagTest` (12),
`HybridBooleanRagTest` (9), `RrfFusionTest` (7),
`QueryExpanderTest` (11), `ExactTermGuardTest` (6),
`BooleanIndexPersistenceTest` (10). Total 71 тест.

## TLA+ формализация

`BooleanRag-Search` — кандидат на TLA+ в следующей волне
([FORMAL-CONTRACTS](../architecture/FORMAL-CONTRACTS.md)).
Формализует инвариант top-K + ExactTermGuard coverage.
