---
layout: default
title: Глубокое погружение в XAI
nav_order: 5
parent: Экосистема
permalink: /ru/ecosystem/xai/
---

**Статус:** ACTIVE · **Обновлено:** 2026-11-06

# Объяснение решения

> **EN-документация является канонической.** Переводы добавляются постепенно.
> См. полную документацию: [XAI Deep Dive (EN)](/ecosystem/xai/)

Объяснимость здесь не «фича поверх»: конституция (статья VIII) требует, чтобы каждое
решение в рантайме было **булевой цепочкой (BRC)**, аудируемой через заголовок
`x-matrix-trace` и hash-chain.

**Настоящие имена стадий** те, что выдаёт рантайм: `INPUT`, `REFLEX`, `HARMFUL_INTENT`,
`SIGNAL`, `SALIENCE`, `ARITHMETIC`, `ANALOGY`, `BIR`, `HDC_MEMORY`, `TSETLIN`,
`RELATIONAL`, `MCTS`, `BILINGUAL_FACTS`, `MODULATORS`, а также четыре модуляторные стадии
`ETHICAL_FILTER`, `SAFETY_MONITOR`, `LIE_DETECTOR`, `CONSISTENCY_CHECKER`. Раньше эта
страница перечисляла пять выдуманных стадий (`INPUT_NORMALIZED` и другие) — их в коде
нет.

Каждая стадия — это `BrcStep(stage, fired, confidence, evidence)`.

**Объяснение живёт в памяти шлюза**, поэтому `explain_id` действителен, только пока живёт
процесс, и в снапшот не входит. И главное: **трасса объясняет решение, но не
сертифицирует его** — неверное правило даёт неверный ответ с вполне читаемой трассой.

Подробная документация на английском: [XAI Deep Dive →](/ecosystem/xai/).
