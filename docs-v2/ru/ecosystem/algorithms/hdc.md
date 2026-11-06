---
layout: default
title: HDC
parent: Алгоритмы
permalink: /ru/ecosystem/algorithms/hdc/
---

**Статус:** ACTIVE · **Обновлено:** 2026-11-06

# HDC — гиперразмерные вычисления

> **EN-документация является канонической.** Перевод добавляется постепенно.
> См. полную документацию: [HDC (EN)](/ecosystem/algorithms/hdc/)

HDC (Hyperdimensional Computing, также Vector Symbolic Architectures) — метод
представления и манипулирования концепциями как **высокоразмерными бинарными
векторами**: **1 024 бита** в этой реализации (`HdcEncoding.DIM`, 128 байт на вектор,
упакованных в 16 слов `long`), тогда как в литературе обычно используют 10 000. Даёт
нейросетевое обобщение чисто символьным системам.

Реальные классы: `HdcEncoding`, `HdcBinding`, `HdcConditioning`, `HdcBrain`,
`CodebookMemory` — все в `matrix-core/src/main/java/io/matrix/neuron/`.

Каноническая страница: [HDC (EN)](/ecosystem/algorithms/hdc/).
