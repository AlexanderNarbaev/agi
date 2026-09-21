# DESIGN-09 — MonotoneDecoder (Hansel-style)

**Статус: normative** · пересмотр (v2 rebuild).

## Что

Продюсер монотонной булевой цепочки: обучает монотонную f методом запросов к оракулу с замыканием по монотонности (вниз на 0, вверх на 1). Эта реализация — layerwise (Hansel-стиль), оптимальная граница C(k,⌊k/2⌋) для полных цепей Ханселя отложена в более строгую реализацию v2.

## Реализация

`bir/producers/monotone/{MembershipOracle, MonotoneDecoder}.java`:
- `MembershipOracle` — `@FunctionalInterface boolean eval(int vertex)`.
- `MonotoneDecoder.decode(k, oracle, VERIFY_LIMIT=16)` → `Result(Bir, queries, vertices)`.
- Propagation: f=1 → вверх supersets + проверка известных-0 сверху; f=0 → вниз subsets + проверка известных-1 снизу. При нарушении монотонности — `IllegalStateException`.
- Exhaustive verify для k≤16 → fidelity 1.0.

Тесты: `MonotoneDecoderTest` (AND/OR/XOR/const, arity limit, deterministic, monotonicity sanity >0.55).

## Метрики / гейты

- Exhaustive fidelity=1.0 для k≤16.
- Запросов ≤ n=2^k (точная граница C(k,⌊k/2⌋) — отложено в v2).

## Отложено

- Полные цепи Ханселя — research wave (reduce для H-016 и связанных).