---
layout: default
title: Алгоритмы
nav_order: 8
parent: Экосистема
permalink: /ru/ecosystem/algorithms/
---

**Статус:** ACTIVE · **Обновлено:** 2026-11-06

# Алгоритмы

> **EN-документация является канонической.** Переводы добавляются постепенно.
> См. полную документацию: [Algorithms (EN)](/ecosystem/algorithms/)

## Основные алгоритмы

| алгоритм | где работает |
|---|---|
| [BIR](/ecosystem/algorithms/bir/) — булев вывод | стадия 6 десятистадийного цикла; логический субстрат |
| [HDC](/ecosystem/algorithms/hdc/) — гиперразмерные вычисления | стадия 7; память образов, `HdcEncoding.DIM = 1024` |
| [MCTS](/ecosystem/algorithms/mcts/) — поиск по дереву Монте-Карло | стадия 9; планирование |
| [Sobel](/ecosystem/algorithms/sobel/) — детекция границ | видео-транскодер, `io.matrix.transcoders.VisionEdgeEncoder` |
| [FFT](/ecosystem/algorithms/fft/) — спектральный анализ | аудио-транскодер, `io.matrix.transcoders.AudioFFTEncoder` |
| [Биохимическая сеть](/ecosystem/algorithms/biochemical/) — динамика модуляторов | `io.matrix.federation.liquid.biochemistry` — **остров, без обслуживающего пути** |
| [Стегимергия](/ecosystem/algorithms/stigmergy/) — непрямая координация | `io.matrix.neuron.StigmergicFederation` |

**Оговорка.** Раньше эта страница утверждала, что эти алгоритмы «питают каждый вызов
`/v1/analyze`». Это не так: режим шлюза по умолчанию — `stub`, а настоящий цикл работает
только при `MATRIX_MODE=production`; и даже там не каждая стадия срабатывает на каждом
входе.

Полные страницы на английском: [Algorithms →](/ecosystem/algorithms/) ·
реестр возможностей: `docs-v2/architecture/CAPABILITY-REGISTRY.yml` ·
индекс отрицательных результатов: `docs-v2/research/NEGATIVE-RESULTS-INDEX.md`.
